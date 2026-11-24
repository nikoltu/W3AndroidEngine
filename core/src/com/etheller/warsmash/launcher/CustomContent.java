package com.etheller.warsmash.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.etheller.warsmash.datasources.CompoundDataSource;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.MapsFolderDataSource;
import com.etheller.warsmash.parsers.w3x.War3Map;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;

/**
 * The player's own content folder. Its Maps folder is read as part of the game data, so the maps in it are listed with
 * the game's own and need no copying; the other folders are for the campaigns, replays and saves the player keeps
 * there. The folder is named by the engine configuration's CustomContent section; without it nothing changes.
 */
public final class CustomContent {
	public static final String SECTION = "CustomContent";
	public static final String KEY_FOLDER = "Folder";
	/** The type of the data source that offers the folder's Maps folder, in the engine configuration. */
	public static final String SOURCE_TYPE = "Maps";
	/** The folders made inside the content folder: melee maps, other maps (subfolders allowed), campaigns, replays, saves. */
	public static final String[] FOLDERS = { "Maps/Melee", "Maps/Custom", "Campaigns", "Replays", "Saves" };
	/** Where an imported map is put. */
	public static final String IMPORT_FOLDER = "Maps/Custom";
	private static final int MAX_NAME = 120;

	/** Why a map was not imported. */
	public enum Problem {
		/** The folder cannot be written to. */
		NO_FOLDER,
		/** The file is not named like a map (.w3x or .w3m). */
		NOT_A_MAP_NAME,
		/** The file is not a map archive, or it is damaged. */
		UNREADABLE,
		/** The archive has no map information. */
		NO_INFORMATION,
		/** The file could not be copied. */
		IO
	}

	public static final class ImportException extends IOException {
		private static final long serialVersionUID = 1L;
		public final Problem problem;

		ImportException(final Problem problem, final Throwable cause) {
			super(problem.toString(), cause);
			this.problem = problem;
		}
	}

	private CustomContent() {
	}

	/** The folder the configuration names, or null when it names none. */
	public static File folder(final DataTable ini) {
		final Element section = ini == null ? null : ini.get(SECTION);
		return section == null ? null : parse(section.getField(KEY_FOLDER));
	}

	/** A configuration value as a folder: quotes around the path are left off, an empty value is no folder. */
	public static File parse(final String value) {
		if (value == null) {
			return null;
		}
		String path = value.trim();
		if ((path.length() > 1) && path.startsWith("\"") && path.endsWith("\"")) {
			path = path.substring(1, path.length() - 1).trim();
		}
		return path.isEmpty() ? null : new File(path);
	}

	/**
	 * Makes the folders of the structure that are missing, without making the content folder itself.
	 *
	 * @return whether the structure is all there
	 */
	public static boolean ensureStructure(final File root) {
		if ((root == null) || !root.isDirectory()) {
			return false;
		}
		boolean all = true;
		for (final String folder : FOLDERS) {
			final File made = new File(root, folder);
			all &= made.isDirectory() || made.mkdirs() || made.isDirectory();
		}
		return all;
	}

	/**
	 * Adds the content folder's maps to the data sources of a configuration just read, after the game data: the
	 * custom game list and the games read them like the game's own maps. A folder that is gone adds nothing.
	 */
	public static void apply(final DataTable ini) {
		final File root = folder(ini);
		final Element sources = ini.get("DataSources");
		if ((root == null) || (sources == null) || !root.isDirectory()) {
			return;
		}
		ensureStructure(root);
		int count = 0;
		for (int i = 0; i < sources.size(); i++) {
			final String id = String.format(Locale.ROOT, "%02d", i);
			if (!sources.getField("Type" + id).isEmpty()) {
				count = i + 1;
				if (SOURCE_TYPE.equals(sources.getField("Type" + id))) {
					return;
				}
			}
		}
		final String id = String.format(Locale.ROOT, "%02d", count);
		sources.setField("Type" + id, SOURCE_TYPE);
		sources.setField("Path" + id, root.getAbsolutePath());
		sources.setField("Count", Integer.toString(count + 1));
	}

	/**
	 * Makes a content folder the one a running game uses, in the configuration as it was read and in the data sources
	 * built from it: the folder's Maps folder is read again at once, so nothing waits for a restart. The folder's
	 * structure is made first; a folder that cannot be used changes nothing.
	 *
	 * @param ini the configuration in memory, which later menus and restarts are built from
	 * @param data the game data of the running menu, or null when only the configuration is to change
	 * @param folder the folder, or null for no folder
	 * @return whether the folder was taken
	 */
	public static boolean use(final DataTable ini, final DataSource data, final File folder) {
		if ((folder != null) && (!folder.isDirectory() || !ensureStructure(folder))) {
			return false;
		}
		Element section = ini.get(SECTION);
		if (section == null) {
			section = new Element(SECTION, ini);
			ini.put(SECTION, section);
		}
		section.setField(KEY_FOLDER, folder == null ? "" : folder.getAbsolutePath());
		final Element sources = ini.get("DataSources");
		if (sources != null) {
			int count = 0;
			int found = -1;
			for (int i = 0; i < sources.size(); i++) {
				final String id = String.format(Locale.ROOT, "%02d", i);
				final String type = sources.getField("Type" + id);
				if (!type.isEmpty()) {
					count = i + 1;
					if (SOURCE_TYPE.equals(type)) {
						found = i;
					}
				}
			}
			if (found >= 0) {
				final String id = String.format(Locale.ROOT, "%02d", found);
				sources.setField("Type" + id, folder == null ? "" : SOURCE_TYPE);
				sources.setField("Path" + id, folder == null ? "" : folder.getAbsolutePath());
			}
			else if (folder != null) {
				final String id = String.format(Locale.ROOT, "%02d", count);
				sources.setField("Type" + id, SOURCE_TYPE);
				sources.setField("Path" + id, folder.getAbsolutePath());
				sources.setField("Count", Integer.toString(count + 1));
			}
		}
		if (data != null) {
			attach(data, folder);
		}
		return true;
	}

	/**
	 * Points the maps source of the game data at a folder, or at none, and makes the data forget which source had
	 * which file. A game data without a maps source gets one when there is a folder.
	 */
	static void attach(final DataSource data, final File folder) {
		final Path path = folder == null ? null : folder.toPath();
		if (!repoint(data, path) && (path != null)) {
			final CompoundDataSource base = baseOf(data);
			if (base != null) {
				base.addSource(new MapsFolderDataSource(path));
			}
		}
		forget(data);
	}

	private static boolean repoint(final DataSource data, final Path path) {
		if (data instanceof MapsFolderDataSource) {
			((MapsFolderDataSource) data).setFolder(path);
			return true;
		}
		boolean found = false;
		if (data instanceof CompoundDataSource) {
			for (final DataSource source : ((CompoundDataSource) data).getSources()) {
				found |= repoint(source, path);
			}
		}
		return found;
	}

	private static void forget(final DataSource data) {
		if (data instanceof CompoundDataSource) {
			for (final DataSource source : ((CompoundDataSource) data).getSources()) {
				forget(source);
			}
			((CompoundDataSource) data).sourcesChanged();
		}
	}

	/** The source the configured sources were put together in: the last of a data made with prefixes, else the data. */
	private static CompoundDataSource baseOf(final DataSource data) {
		if (!(data instanceof CompoundDataSource)) {
			return null;
		}
		final List<DataSource> sources = ((CompoundDataSource) data).getSources();
		final DataSource last = sources.isEmpty() ? null : sources.get(sources.size() - 1);
		return last instanceof CompoundDataSource ? (CompoundDataSource) last : (CompoundDataSource) data;
	}

	/** The file name a picked file is kept under: its name without folders, or null when it is not named like a map. */
	public static String mapFileName(final String displayName) {
		if (displayName == null) {
			return null;
		}
		String name = displayName.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1);
		final StringBuilder clean = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			final char c = name.charAt(i);
			clean.append((c < ' ') || ("<>:\"|?*".indexOf(c) >= 0) ? '_' : c);
		}
		name = clean.toString().trim();
		final String lower = name.toLowerCase(Locale.ROOT);
		if (((name.length() - 4) < 1) || (name.length() > MAX_NAME) || !(lower.endsWith(".w3x") || lower.endsWith(".w3m"))
				|| name.startsWith(".")) {
			return null;
		}
		return name;
	}

	/**
	 * Copies a map into the content folder's Maps/Custom after checking it can be read as a map; a file that fails
	 * the check is not kept. A map of the same name is replaced.
	 *
	 * @param displayName the name of the picked file
	 * @return the map's file in the folder
	 */
	public static File importMap(final File root, final String displayName, final InputStream in)
			throws ImportException {
		final String name = mapFileName(displayName);
		if (name == null) {
			throw new ImportException(Problem.NOT_A_MAP_NAME, null);
		}
		if (!ensureStructure(root)) {
			throw new ImportException(Problem.NO_FOLDER, null);
		}
		final File folder = new File(root, IMPORT_FOLDER);
		// Not named like a map until it is checked, so that a list made meanwhile never offers half a file.
		final File partial = new File(folder, name + ".part");
		try {
			Files.copy(in, partial.toPath(), StandardCopyOption.REPLACE_EXISTING);
			check(partial);
			final File map = new File(folder, name);
			Files.move(partial.toPath(), map.toPath(), StandardCopyOption.REPLACE_EXISTING);
			return map;
		}
		catch (final ImportException e) {
			partial.delete();
			throw e;
		}
		catch (final IOException | RuntimeException e) {
			partial.delete();
			throw new ImportException(Problem.IO, e);
		}
	}

	/** The maps put into the content folder's Maps/Custom by importing, the files directly in it named like maps. */
	public static List<File> importedMaps(final File root) {
		final List<File> maps = new ArrayList<>();
		final File[] files = root == null ? null : new File(root, IMPORT_FOLDER).listFiles();
		if (files != null) {
			for (final File file : files) {
				if (file.isFile() && (mapFileName(file.getName()) != null)) {
					maps.add(file);
				}
			}
		}
		return maps;
	}

	/**
	 * Deletes the imported maps of the content folder; nothing else in it is touched.
	 *
	 * @return how many maps were deleted
	 */
	public static int deleteImportedMaps(final File root) {
		int deleted = 0;
		for (final File map : importedMaps(root)) {
			if (map.delete()) {
				deleted++;
			}
		}
		return deleted;
	}

	/** Opens the file as the custom game list would and reads its information. */
	static void check(final File file) throws ImportException {
		final War3Map map;
		try {
			map = new War3Map(new CompoundDataSource(Collections.emptyList()), file);
		}
		catch (final RuntimeException e) {
			throw new ImportException(Problem.UNREADABLE, e);
		}
		try {
			final War3MapW3i info = map.readMapInformation();
			if (info.getPlayers().isEmpty()) {
				throw new ImportException(Problem.NO_INFORMATION, null);
			}
		}
		catch (final IOException | RuntimeException e) {
			throw new ImportException(Problem.NO_INFORMATION, e);
		}
	}
}
