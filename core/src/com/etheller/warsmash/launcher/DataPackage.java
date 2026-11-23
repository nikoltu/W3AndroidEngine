package com.etheller.warsmash.launcher;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import com.etheller.warsmash.datasources.FolderDataSource;
import com.etheller.warsmash.datasources.GameLanguage;
import com.etheller.warsmash.datasources.LayeredDataSource;

/**
 * A game data package as the data packer hands it out: a zip of an extracted game data folder with the packer's marker
 * file, which says the tool, the game version and how many files the package holds. Importing unpacks it next to the
 * target folder, checks every file against the zip's checksums and the count against the marker, and only then puts
 * it in place of the target, so a cancelled or failed import leaves the old data as it was.
 * <p>
 * The packer hands a graphics set (the DE or HD package) out as a zip of its own, without a marker, whose files all lie
 * in the package folders. Such a zip is added to Classic data already in the target rather than replacing it, and the
 * graphics sets of the target are kept when a newer Classic package replaces it.
 * <p>
 * A language pack has a marker of its own and all its files in one "_locales/xx.w3mod" folder. It is added to the
 * Classic data like a graphics set, and the "_locales" folder of the target is kept like the sets are.
 */
public final class DataPackage implements Closeable {
	/** The packer's marker file, the last file it writes into a folder it made. */
	public static final String MARKER = "w3-datapacker.txt";
	/** The marker of a language pack, whose files all lie in one "_locales/xx.w3mod" folder. */
	public static final String LANGUAGE_MARKER = "w3-datapacker-language.txt";
	/** The folder of the language layers inside the Classic data. */
	private static final String LOCALES = "_locales";
	private static final Pattern LANGUAGE_FOLDER = Pattern.compile("(?i)_locales/([a-z]{4})\\.w3mod/.+");
	/** The folders of the graphics sets inside the Classic data, as the engine reads them. */
	private static final String[] SET_FOLDERS = { LayeredDataSource.DE_PACKAGE, LayeredDataSource.HD_PACKAGE };
	/** The packer puts this into the folder of the set a set zip is made for, and only there. */
	private static final String NOMEDIA = ".nomedia";
	/** Kept free beside the unpacked files, for the engine's own files and the system. */
	public static final long SPARE_BYTES = 64L << 20;
	private static final int BUFFER = 1 << 16;

	public enum Problem {
		/** Not a zip, or a damaged one. */
		NOT_ZIP,
		/** A zip without the packer's marker. */
		NO_MARKER,
		/** The marker counts other files than the zip holds: a package that was cut short. */
		INCOMPLETE,
		/** A file would land outside the folder. */
		UNSAFE_PATH,
		/** A file uses a compression this reader does not have. */
		UNSUPPORTED,
		NO_SPACE,
		/** A file does not match the zip's checksum. */
		DAMAGED,
		/** The unpacked folder is not game data. */
		NO_GAME_DATA,
		/** A graphics set, and the target has no Classic data to add it to. */
		NO_CLASSIC,
		/** A language pack, and the target has no Classic data to add it to. */
		NO_CLASSIC_LANGUAGE,
		/** A graphics set or language pack, and the Classic folder in use cannot be written to. */
		NOT_WRITABLE,
		/** A file could not be read or written. */
		IO,
		CANCELLED
	}

	/** Why an import stopped, with the file or the sizes it is about. */
	public static final class ImportException extends IOException {
		private static final long serialVersionUID = 1L;
		public final Problem problem;
		/** The file concerned, or empty. */
		public final String file;

		public ImportException(final Problem problem, final String file, final Throwable cause) {
			super(problem + (file.isEmpty() ? "" : ": " + file), cause);
			this.problem = problem;
			this.file = file;
		}

		public ImportException(final Problem problem, final String file) {
			this(problem, file, null);
		}
	}

	/** Called while unpacking, from the unpacking thread. */
	public interface Progress {
		void update(long bytes, long totalBytes, int files, int totalFiles);
	}

	private final ZipFile zip;
	/** A folder the whole package lies in ("w3-data-classic/"), or empty. */
	private final String root;
	private final List<ZipArchiveEntry> files = new ArrayList<>();
	/** Null for a graphics set. */
	private final ZipArchiveEntry marker;
	/** The package folder of the graphics set this zip holds ("_de.w3mod"); null for a Classic package. */
	public final String graphicsSet;
	/** The code of the language a language pack holds ("esES"); empty for the other kinds. */
	public final String language;
	/** Bytes of the unpacked files. */
	public final long totalBytes;
	public final String tool;
	public final String gameVersion;
	public final String profile;
	public final String locale;

	private DataPackage(final ZipFile zip) throws IOException {
		this.zip = zip;
		ZipArchiveEntry found = null;
		boolean languageMarker = false;
		final List<ZipArchiveEntry> all = Collections.list(zip.getEntriesInPhysicalOrder());
		for (final ZipArchiveEntry entry : all) {
			final String name = entry.getName().replace('\\', '/');
			final String lower = name.toLowerCase(Locale.ROOT);
			final boolean isLanguage = lower.endsWith(LANGUAGE_MARKER);
			if (!entry.isDirectory() && (lower.endsWith(MARKER) || isLanguage)
					&& (name.indexOf('/') == name.lastIndexOf('/'))
					&& ((found == null) || (name.length() < found.getName().length()))) {
				found = entry;
				languageMarker = isLanguage;
			}
		}
		if (found == null) {
			this.marker = null;
			this.root = setRoot(all);
			this.graphicsSet = setFolder(all, this.root);
			this.language = "";
		}
		else {
			this.marker = found;
			this.graphicsSet = null;
			final String markerName = found.getName().replace('\\', '/');
			this.root = markerName.substring(0,
					markerName.length() - (languageMarker ? LANGUAGE_MARKER : MARKER).length());
			if (!this.root.isEmpty() && !this.root.endsWith("/")) {
				throw new ImportException(Problem.NO_MARKER, "");
			}
			this.language = languageMarker ? languageCode(all, this.root, markerName) : "";
		}
		long bytes = 0;
		for (final ZipArchiveEntry entry : all) {
			final String name = entry.getName().replace('\\', '/');
			if (entry.isDirectory()) {
				continue;
			}
			if (!name.startsWith(this.root) || !isSafe(name.substring(this.root.length()))) {
				throw new ImportException(Problem.UNSAFE_PATH, name);
			}
			if (!zip.canReadEntryData(entry)) {
				throw new ImportException(Problem.UNSUPPORTED, name);
			}
			this.files.add(entry);
			bytes += Math.max(0, entry.getSize());
		}
		this.totalBytes = bytes;
		if (found == null) {
			this.tool = "";
			this.gameVersion = "";
			this.profile = "";
			this.locale = "";
			return;
		}
		final List<String> lines = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(zip.getInputStream(found), StandardCharsets.UTF_8))) {
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				lines.add(line);
			}
		}
		this.tool = field(lines, "tool");
		this.gameVersion = field(lines, "gameVersion");
		this.profile = field(lines, "profile");
		this.locale = field(lines, "locale");
		if (this.tool.isEmpty() || (!this.language.isEmpty() && !this.locale.isEmpty()
				&& !this.language.equals(GameLanguage.canonical(this.locale)))) {
			throw new ImportException(Problem.NO_MARKER, "");
		}
		final String entries = field(lines, "entries");
		try {
			if (!entries.isEmpty() && (Integer.parseInt(entries) != this.files.size())) {
				throw new ImportException(Problem.INCOMPLETE, this.files.size() + " / " + entries);
			}
		}
		catch (final NumberFormatException e) {
			throw new ImportException(Problem.NO_MARKER, "");
		}
	}

	/** Reads a package from a seekable channel, such as a file a document picker opened; closed with the package. */
	public static DataPackage open(final SeekableByteChannel channel) throws IOException {
		final ZipFile zip;
		try {
			zip = new ZipFile(channel);
		}
		catch (final IOException e) {
			channel.close();
			throw new ImportException(Problem.NOT_ZIP, "", e);
		}
		return open(zip);
	}

	public static DataPackage open(final File file) throws IOException {
		final ZipFile zip;
		try {
			zip = new ZipFile(file);
		}
		catch (final IOException e) {
			throw new ImportException(Problem.NOT_ZIP, file.getName(), e);
		}
		return open(zip);
	}

	private static DataPackage open(final ZipFile zip) throws IOException {
		try {
			return new DataPackage(zip);
		}
		catch (final IOException | RuntimeException e) {
			zip.close();
			if (e instanceof ImportException) {
				throw (ImportException) e;
			}
			throw new ImportException(Problem.NOT_ZIP, "", e);
		}
	}

	public int fileCount() {
		return this.files.size();
	}

	/** Bytes the import needs free where the target lies. */
	public long neededBytes() {
		return this.totalBytes + SPARE_BYTES;
	}

	/** Bytes free where the target would be unpacked; the nearest existing folder above it is asked. */
	public static long freeBytesFor(final File target) {
		File folder = target.getAbsoluteFile().getParentFile();
		while ((folder != null) && !folder.exists()) {
			folder = folder.getParentFile();
		}
		return folder == null ? 0 : folder.getUsableSpace();
	}

	private static String field(final List<String> lines, final String name) {
		for (final String line : lines) {
			final int equals = line.indexOf('=');
			if ((equals > 0) && name.equalsIgnoreCase(line.substring(0, equals).trim())) {
				return line.substring(equals + 1).trim();
			}
		}
		return "";
	}

	private static String setFolderOf(final String path) {
		final int slash = path.indexOf('/');
		if (slash > 0) {
			final String first = path.substring(0, slash);
			for (final String folder : SET_FOLDERS) {
				if (folder.equalsIgnoreCase(first)) {
					return folder;
				}
			}
		}
		return null;
	}

	/**
	 * The folder a zip without a marker lies in when all its files are in graphics set folders, either at its top or in
	 * one folder it was zipped with; a zip of anything else is not a package.
	 */
	private static String setRoot(final List<ZipArchiveEntry> all) throws ImportException {
		String root = null;
		for (final ZipArchiveEntry entry : all) {
			if (entry.isDirectory()) {
				continue;
			}
			final String name = entry.getName().replace('\\', '/');
			if (root == null) {
				final int slash = name.indexOf('/');
				root = (setFolderOf(name) != null) || (slash < 0) ? "" : name.substring(0, slash + 1);
			}
			if (!name.startsWith(root) || (setFolderOf(name.substring(root.length())) == null)) {
				throw new ImportException(Problem.NO_MARKER, "");
			}
		}
		if (root == null) {
			throw new ImportException(Problem.NO_MARKER, "");
		}
		return root;
	}

	/**
	 * The set a set zip is made for. The zip of one set also carries the few files of the other set that its graphics
	 * take, so the set is the one with the packer's .nomedia file, or else the one with the most files.
	 */
	private static String setFolder(final List<ZipArchiveEntry> all, final String root) {
		final int[] counts = new int[SET_FOLDERS.length];
		for (final ZipArchiveEntry entry : all) {
			if (entry.isDirectory()) {
				continue;
			}
			final String name = entry.getName().replace('\\', '/').substring(root.length());
			final String folder = setFolderOf(name);
			for (int i = 0; i < SET_FOLDERS.length; i++) {
				if (SET_FOLDERS[i].equals(folder)) {
					if (name.substring(folder.length() + 1).equalsIgnoreCase(NOMEDIA)) {
						return folder;
					}
					counts[i]++;
				}
			}
		}
		int best = 0;
		for (int i = 1; i < counts.length; i++) {
			if (counts[i] > counts[best]) {
				best = i;
			}
		}
		return SET_FOLDERS[best];
	}

	/** The language every file of a language pack lies in, but for the marker; a pack of two languages is none. */
	private static String languageCode(final List<ZipArchiveEntry> all, final String root, final String markerName)
			throws ImportException {
		String code = null;
		for (final ZipArchiveEntry entry : all) {
			final String name = entry.getName().replace('\\', '/');
			if (entry.isDirectory() || name.equals(markerName)) {
				continue;
			}
			final Matcher matcher = LANGUAGE_FOLDER.matcher(name.startsWith(root) ? name.substring(root.length()) : name);
			if (!matcher.matches()) {
				throw new ImportException(Problem.NO_MARKER, "");
			}
			final String found = GameLanguage.canonical(matcher.group(1));
			if ((code != null) && !code.equals(found)) {
				throw new ImportException(Problem.NO_MARKER, "");
			}
			code = found;
		}
		if (code == null) {
			throw new ImportException(Problem.NO_MARKER, "");
		}
		return code;
	}

	/** Whether the package is added to the Classic data of the target: a graphics set or a language pack. */
	public boolean addsToClassic() {
		return (this.graphicsSet != null) || !this.language.isEmpty();
	}

	/** "DE" or "HD" for a graphics set, empty for a Classic package. */
	public String setName() {
		if (this.graphicsSet == null) {
			return "";
		}
		final String name = this.graphicsSet.startsWith("_") ? this.graphicsSet.substring(1) : this.graphicsSet;
		final int dot = name.indexOf('.');
		return (dot < 0 ? name : name.substring(0, dot)).toUpperCase(Locale.ROOT);
	}

	/** A relative path that stays inside the folder it is unpacked into. */
	private static boolean isSafe(final String name) {
		if (name.isEmpty() || name.startsWith("/") || (name.indexOf(':') >= 0) || (name.indexOf('\0') >= 0)) {
			return false;
		}
		for (final String part : name.split("/")) {
			if (part.equals("..")) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Unpacks the package and puts it in place of the target folder; the old folder is removed afterwards. The marker is
	 * written last, as the packer does, so a folder with it is whole. The caller chooses a target that may be replaced.
	 * The graphics sets of the old folder are kept. A graphics set is added to the Classic data of the target instead.
	 *
	 * @param cancelled asked between blocks; true stops the import and removes what was unpacked
	 * @return the target folder
	 */
	public File importInto(final File target, final Progress progress, final BooleanSupplier cancelled)
			throws ImportException {
		final File parent = target.getAbsoluteFile().getParentFile();
		parent.mkdirs();
		final File staging = new File(parent, target.getName() + ".importing");
		final File old = new File(parent, target.getName() + ".old");
		deleteTree(staging);
		// Old data left by an import that stopped between the two renames is given back first, and the graphics sets
		// of one that stopped before taking them over are taken over now.
		if (!target.exists() && old.isDirectory()) {
			old.renameTo(target);
		}
		keepGraphicsSets(old, target);
		deleteTree(old);
		if (addsToClassic()) {
			return importSet(target, staging, progress, cancelled);
		}
		final long free = parent.getUsableSpace();
		if (free < neededBytes()) {
			throw new ImportException(Problem.NO_SPACE, free + " / " + neededBytes());
		}
		boolean done = false;
		try {
			unpack(staging, progress, cancelled);
			if (DataFolderCheck.detect(staging) == DataFolderCheck.Kind.NONE) {
				throw new ImportException(Problem.NO_GAME_DATA, "");
			}
			if (target.exists() && !target.renameTo(old)) {
				throw new ImportException(Problem.IO, target.getName());
			}
			if (!staging.renameTo(target)) {
				old.renameTo(target);
				throw new ImportException(Problem.IO, target.getName());
			}
			done = true;
		}
		finally {
			if (!done) {
				deleteTree(staging);
			}
		}
		keepGraphicsSets(old, target);
		deleteTree(old);
		DataFolderCheck.forget(target);
		FolderDataSource.reindex(target);
		return target;
	}

	/**
	 * Unpacks a graphics set beside the target and then moves its files into the Classic data of the target, over the
	 * files of the same names; the files of the target that the set does not have stay.
	 */
	private File importSet(final File target, final File staging, final Progress progress,
			final BooleanSupplier cancelled) throws ImportException {
		if (DataFolderCheck.detect(target) == DataFolderCheck.Kind.NONE) {
			throw this.language.isEmpty() ? new ImportException(Problem.NO_CLASSIC, setName())
					: new ImportException(Problem.NO_CLASSIC_LANGUAGE, this.language);
		}
		final File stagingParent = staging.getAbsoluteFile().getParentFile();
		if (!target.canWrite() || !stagingParent.canWrite()) {
			throw new ImportException(Problem.NOT_WRITABLE, target.getName());
		}
		final long free = stagingParent.getUsableSpace();
		if (free < neededBytes()) {
			throw new ImportException(Problem.NO_SPACE, free + " / " + neededBytes());
		}
		// What was kept of the folder is checked now, while nothing of the import is in it yet.
		DataFolderCheck.beforeImport(target);
		final DataFolderCheck.Tally added = new DataFolderCheck.Tally();
		boolean done = false;
		try {
			unpack(staging, progress, cancelled);
			final File[] folders = staging.listFiles();
			if (folders != null) {
				for (final File folder : folders) {
					// The marker of a language pack says what the pack is; the Classic marker of the target stays.
					if (!folder.isDirectory()) {
						continue;
					}
					final File into = existingChild(target, folder.getName());
					if (!move(folder, into, true, added)) {
						throw new ImportException(Problem.IO, folder.getName());
					}
				}
			}
			done = true;
		}
		finally {
			deleteTree(staging);
			if (!done) {
				DataFolderCheck.forget(target);
				FolderDataSource.dropIndex(target);
			}
		}
		// What was read of the target stays; it is not counted over again for the few files added to it.
		DataFolderCheck.added(target, added);
		// The list of its files is made anew: the files added are few, but the list is a file of its own.
		FolderDataSource.reindex(target);
		return target;
	}

	/**
	 * Moves the graphics set folders and the language layers of old data into the data that replaces it, without the
	 * files it has itself.
	 */
	private static void keepGraphicsSets(final File old, final File target) {
		final File[] children = old.isDirectory() && target.isDirectory() ? old.listFiles() : null;
		if (children == null) {
			return;
		}
		for (final File child : children) {
			boolean kept = child.isDirectory() && LOCALES.equalsIgnoreCase(child.getName());
			for (final String folder : SET_FOLDERS) {
				kept |= child.isDirectory() && folder.equalsIgnoreCase(child.getName());
			}
			if (kept) {
				move(child, existingChild(target, child.getName()), false, null);
			}
		}
	}

	/** The child of that name in any case, as the engine finds it, or a new one with the name as given. */
	private static File existingChild(final File folder, final String name) {
		final File[] children = folder.listFiles();
		if (children != null) {
			for (final File child : children) {
				if (child.getName().equalsIgnoreCase(name)) {
					return child;
				}
			}
		}
		return new File(folder, name);
	}

	/**
	 * Moves a file or a folder; a folder that is there already gets the files moved into it one by one, and a file
	 * that is there already is replaced or, when not, kept.
	 *
	 * @return whether everything that was to be moved was moved
	 */
	private static boolean move(final File from, final File to, final boolean replace,
			final DataFolderCheck.Tally added) {
		if (!to.exists()) {
			// Counted before the move, while the files are still where they were unpacked.
			final DataFolderCheck.Tally moving = added == null ? null : DataFolderCheck.tally(from, to);
			if (from.renameTo(to)) {
				if (moving != null) {
					added.add(moving);
					added.touch(to.getParentFile());
				}
				return true;
			}
			if (!from.isDirectory()) {
				return false;
			}
		}
		if (from.isDirectory() && to.isDirectory()) {
			boolean moved = true;
			final File[] children = from.listFiles();
			if (children != null) {
				for (final File child : children) {
					moved &= move(child, existingChild(to, child.getName()), replace, added);
				}
			}
			return moved;
		}
		if (from.isDirectory() && !to.exists()) {
			// The rename failed; the folder's files are moved one by one.
			if (!to.mkdirs()) {
				return false;
			}
			if (added != null) {
				added.entries++;
				added.touch(to);
				added.touch(to.getParentFile());
			}
			return move(from, to, replace, added);
		}
		if (!replace || from.isDirectory() || to.isDirectory()) {
			return !replace;
		}
		if (added != null) {
			added.touch(to.getParentFile());
		}
		return to.delete() && from.renameTo(to);
	}

	private void unpack(final File staging, final Progress progress, final BooleanSupplier cancelled)
			throws ImportException {
		final byte[] buffer = new byte[BUFFER];
		final CRC32 crc = new CRC32();
		final List<ZipArchiveEntry> ordered = new ArrayList<>(this.files);
		if (this.marker != null) {
			ordered.remove(this.marker);
			ordered.add(this.marker);
		}
		long bytes = 0;
		int count = 0;
		progress.update(0, this.totalBytes, 0, ordered.size());
		for (final ZipArchiveEntry entry : ordered) {
			final String name = entry.getName().replace('\\', '/').substring(this.root.length());
			final File file = new File(staging, name);
			file.getParentFile().mkdirs();
			crc.reset();
			try (InputStream in = this.zip.getInputStream(entry); OutputStream out = new FileOutputStream(file)) {
				for (int read = in.read(buffer); read != -1; read = in.read(buffer)) {
					if (cancelled.getAsBoolean()) {
						throw new ImportException(Problem.CANCELLED, "");
					}
					out.write(buffer, 0, read);
					crc.update(buffer, 0, read);
					bytes += read;
					progress.update(bytes, this.totalBytes, count, ordered.size());
				}
			}
			catch (final ImportException e) {
				throw e;
			}
			catch (final IOException | RuntimeException e) {
				if (staging.getUsableSpace() < BUFFER) {
					throw new ImportException(Problem.NO_SPACE, staging.getUsableSpace() + " / " + neededBytes(), e);
				}
				throw new ImportException(Problem.IO, name, e);
			}
			if ((entry.getCrc() != -1) && (crc.getValue() != entry.getCrc())) {
				throw new ImportException(Problem.DAMAGED, name);
			}
			count++;
			progress.update(bytes, this.totalBytes, count, ordered.size());
		}
	}

	/** Removes a folder and everything in it; what cannot be removed stays. Links are removed, not followed. */
	public static void deleteTree(final File file) {
		final File[] children = file.isDirectory() && !Files.isSymbolicLink(file.toPath()) ? file.listFiles() : null;
		if (children != null) {
			for (final File child : children) {
				deleteTree(child);
			}
		}
		file.delete();
	}

	/**
	 * The zip files directly in a folder that hold a data package, by name, the Classic packages before the graphics
	 * sets and language packs, which need them; empty when there are none.
	 */
	public static List<File> packagesIn(final File folder) {
		final List<File> found = new ArrayList<>();
		final List<File> sets = new ArrayList<>();
		final File[] children = folder == null ? null : folder.listFiles();
		if (children == null) {
			return found;
		}
		for (final File child : children) {
			if (child.isFile() && child.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
				try (DataPackage dataPackage = open(child)) {
					(dataPackage.addsToClassic() ? sets : found).add(child);
				}
				catch (final IOException e) {
					// Not a data package.
				}
			}
		}
		Collections.sort(found);
		Collections.sort(sets);
		found.addAll(sets);
		return found;
	}

	@Override
	public void close() throws IOException {
		this.zip.close();
	}
}
