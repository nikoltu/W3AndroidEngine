package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.SkinArt.FONT;
import static com.etheller.warsmash.launcher.SkinArt.FONT_BOLD;
import static com.etheller.warsmash.launcher.SkinArt.FONT_GLOBAL;
import static com.etheller.warsmash.launcher.SkinArt.FONT_MASTER;
import static com.etheller.warsmash.launcher.SkinArt.MISSIONS;
import static com.etheller.warsmash.launcher.SkinArt.MISSION_FOLDER;
import static com.etheller.warsmash.launcher.SkinArt.REFORGED_SKIN;
import static com.etheller.warsmash.launcher.SkinArt.SCENES;
import static com.etheller.warsmash.launcher.SkinArt.STRINGS;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.util.Log;

import com.etheller.warsmash.WarsmashGdxMapScreen;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.GameLanguage;
import com.etheller.warsmash.datasources.GraphicsSet;
import com.etheller.warsmash.launcher.CampaignChoice;
import com.etheller.warsmash.launcher.CustomContent;
import com.etheller.warsmash.launcher.GlueText;
import com.etheller.warsmash.launcher.LauncherImages;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.ArgbImage;

/**
 * The launcher's own copy of the game art it draws with ({@link SkinArt}). The APK carries no game pixels, so the
 * preparation step decodes the art from the player's game data into PNG files under the cache folder, which Android
 * may clear; a cleared or outdated cache is simply prepared again.
 */
final class SkinCache {
	private static final String TAG = "Warsmash";
	/** Raised whenever the list of cached files or the way they are made changes. */
	private static final int FORMAT = 14;
	private static final String STAMP = "stamp";
	/** The game language the glue strings were read in; they alone are made again when it changes. */
	private static final String STRINGS_LANGUAGE = "strings.lang";
	/** Changes when the way the glue strings are made does, so that the ones kept are made again. */
	private static final String STRINGS_RULES = "|own1";
	private static final String PENDING = "pending";

	interface Progress {
		void step(int done, int total, String name);
	}

	private interface Maker {
		/** @return false if the game data does not have the file */
		boolean make(DataSource data, File target) throws IOException;
	}

	private static final class Item {
		final String name;
		final String shown;
		final Maker maker;

		Item(final String name, final String shown, final Maker maker) {
			this.name = name;
			this.shown = shown;
			this.maker = maker;
		}
	}

	private final File folder;
	private final String key;
	private final DataTable ini;
	private final String language;
	private final boolean reforged;

	private SkinCache(final File folder, final String key, final boolean reforged, final DataTable ini) {
		this.folder = folder;
		this.key = key;
		this.reforged = reforged;
		this.ini = ini;
		this.language = GameLanguage.configured(ini);
	}

	static File iniFile(final Context context) {
		return new File(EngineResources.appFolder(context), AndroidLauncher.INI_FILE_NAME);
	}

	/** The cache of a skin for the data sources the engine configuration names, or null without a configuration. */
	static SkinCache forConfiguration(final Context context, final DataTable ini, final String skin) {
		final Element sources = ini.get("DataSources");
		if (sources == null) {
			return null;
		}
		final boolean reforged = REFORGED_SKIN.equals(skin);
		final String name = reforged ? REFORGED_SKIN : SkinArt.CLASSIC_SKIN;
		final StringBuilder key = new StringBuilder().append(FORMAT).append('|')
				.append(Integer.toHexString(SkinArt.revision(reforged))).append('|').append(name);
		for (int i = 0; i < sources.size(); i++) {
			final String id = String.format(Locale.ROOT, "%02d", i);
			// The player's maps are not part of the art, so the art is not made again when they change.
			if (CustomContent.SOURCE_TYPE.equals(sources.getField("Type" + id))) {
				continue;
			}
			key.append('|').append(sources.getField("Type" + id)).append(':').append(sources.getField("Path" + id));
		}
		return new SkinCache(new File(new File(context.getCacheDir(), "skin"), name), key.toString(), reforged,
				ini);
	}

	/** Whether the game data has the Reforged interface, as the last preparation of either skin found it. */
	static boolean hasReforged(final Context context) {
		final File skins = new File(context.getCacheDir(), "skin");
		return new File(new File(skins, SkinArt.CLASSIC_SKIN), SkinArt.REFORGED).isFile()
				|| new File(new File(skins, REFORGED_SKIN), SkinArt.REFORGED).isFile();
	}

	/** Whether the game data has the package of these in-game graphics, as the last preparation found it. */
	static boolean hasGraphics(final Context context, final GraphicsSet graphics) {
		if (graphics == GraphicsSet.CLASSIC) {
			return true;
		}
		final String mark = graphics == GraphicsSet.DE ? SkinArt.GRAPHICS_DE : SkinArt.GRAPHICS_HD;
		final File skins = new File(context.getCacheDir(), "skin");
		return new File(new File(skins, SkinArt.CLASSIC_SKIN), mark).isFile()
				|| new File(new File(skins, REFORGED_SKIN), mark).isFile();
	}

	boolean reforged() {
		return this.reforged;
	}

	/** How many portraits this skin offers; the files the game data lacks are left out where they are listed. */
	int portraitCount() {
		return SkinArt.portraitCount(this.reforged);
	}

	/** The portraits as the picker shows them. */
	List<SkinArt.PortraitGroup> portraitGroups() {
		return SkinArt.portraitGroups(this.reforged);
	}

	/** The recording this skin plays for a scene: the "_sd" one for the classic skin. */
	String sceneName(final String scene) {
		return SkinArt.sceneName(scene, this.reforged);
	}

	/** Makes the next start prepare everything again from the game data, as Re-check asks. */
	void invalidate() {
		deleteTree(this.folder);
	}

	File file(final String name) {
		return new File(this.folder, name);
	}

	File scene(final String name, final boolean poster) {
		return new File(new File(this.folder, "webms"), name + (poster ? ".jpg" : ".webm"));
	}

	/** Whether every file of the last finished preparation is still there for the same game data. */
	boolean isCurrent() {
		if (!this.key.equals(read(file(STAMP))) || !stringsInLanguage()) {
			return false;
		}
		final String list = read(file(STAMP + ".files"));
		if (list == null) {
			return false;
		}
		for (final String name : list.split("\n")) {
			if (!name.isEmpty() && !file(name).isFile()) {
				return false;
			}
		}
		return true;
	}

	private boolean stringsInLanguage() {
		final String made = read(file(STRINGS_LANGUAGE));
		return (this.language + STRINGS_RULES).equals(made == null ? "" : made);
	}

	int total() {
		return items().size();
	}

	/**
	 * Makes every file that is missing. An interrupted run is resumed from the files it already made; a run for other
	 * game data starts over.
	 */
	void prepare(final DataTable ini, final Progress progress, final boolean[] cancelled) throws IOException {
		final String pending = read(file(PENDING));
		if (!this.key.equals(pending) && !this.key.equals(read(file(STAMP)))) {
			deleteTree(this.folder);
		}
		this.folder.mkdirs();
		new File(this.folder, "webms").mkdirs();
		file(STAMP).delete();
		if (!stringsInLanguage()) {
			file(STRINGS).delete();
		}
		write(file(PENDING), this.key);
		final List<Item> items = items();
		progress.step(0, items.size(), null);
		final DataSource data = WarsmashGdxMapScreen.parseDataSources(ini);
		final StringBuilder made = new StringBuilder();
		try {
			for (int i = 0; i < items.size(); i++) {
				if (cancelled[0]) {
					return;
				}
				final Item item = items.get(i);
				final File target = item.name.startsWith("webms/") ? new File(this.folder, item.name)
						: file(item.name);
				boolean present = target.isFile();
				if (!present) {
					try {
						present = item.maker.make(data, target);
					}
					catch (final IOException | RuntimeException e) {
						// A file the decoders cannot read is drawn with its fallback, like a missing one.
						Log.w(TAG, "Launcher art " + item.shown + " not prepared", e);
						present = false;
					}
					if (!present) {
						Log.i(TAG, "Launcher art not in the game data: " + item.shown);
					}
				}
				if (present && !item.name.startsWith("webms/")) {
					made.append(item.name).append('\n');
				}
				progress.step(i + 1, items.size(), item.shown);
			}
		}
		finally {
			data.close();
		}
		write(file(STRINGS_LANGUAGE), this.language + STRINGS_RULES);
		write(file(STAMP + ".files"), made.toString());
		write(file(STAMP), this.key);
		file(PENDING).delete();
	}

	private List<Item> items() {
		final List<Item> items = new ArrayList<>();
		for (final SkinArt.Item art : SkinArt.items(this.reforged)) {
			items.add(item(art));
		}
		return items;
	}

	private Item item(final SkinArt.Item art) {
		switch (art.kind) {
		case PLAIN:
			return image(art, Shape.PLAIN);
		case EDGE:
			return image(art, Shape.EDGE);
		case HOLDER:
			return image(art, Shape.HOLDER);
		case TILES:
			return new Item(art.name, art.shown, (data, target) -> tiles(data, art.path, target));
		case WEB:
			return web(art);
		case STRINGS:
			return new Item(art.name, art.shown, (data, target) -> strings(this.ini, data, target));
		case MARK:
			return new Item(art.name, art.shown, (data, target) -> {
				if (!data.has(art.path)) {
					return false;
				}
				write(target, "");
				return true;
			});
		case SCENE:
			return new Item(art.name, art.shown, (data, target) -> scene(data, art.path, target));
		case MISSIONS:
			return new Item(art.name, art.shown, SkinCache::missionPictures);
		default:
			return new Item(art.name, art.shown, (data, target) -> copy(data, art.path, target));
		}
	}

	/** Every mission loading picture of the Reforged campaigns, scaled down to the size of a list row. */
	private static boolean missionPictures(final DataSource data, final File index) throws IOException {
		final Collection<String> listed = data.getListfile();
		if (listed == null) {
			return false;
		}
		final File folder = index.getParentFile();
		folder.mkdirs();
		final StringBuilder lines = new StringBuilder();
		int number = 0;
		for (final String listedPath : listed) {
			final String path = listedPath.replace('/', '\\');
			final String lower = path.toLowerCase(Locale.ROOT);
			if (!CampaignChoice.missionPicture(lower)) {
				continue;
			}
			final ByteBuffer file = data.read(path);
			if (file == null) {
				continue;
			}
			final ByteBuffer bytes = file.duplicate();
			bytes.position(0);
			final byte[] encoded = new byte[bytes.remaining()];
			bytes.get(encoded);
			final BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeByteArray(encoded, 0, encoded.length, options);
			options.inJustDecodeBounds = false;
			options.inSampleSize = 1;
			while ((Math.min(options.outWidth, options.outHeight) / (options.inSampleSize * 2)) >= 256) {
				options.inSampleSize *= 2;
			}
			final Bitmap bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.length, options);
			if (bitmap == null) {
				continue;
			}
			final String name = String.format(Locale.ROOT, "m%03d.jpg", number++);
			save(bitmap, new File(folder, name), Bitmap.CompressFormat.JPEG);
			bitmap.recycle();
			lines.append(name).append('\t').append(lower.substring(MISSION_FOLDER.length())).append('\n');
		}
		if (number == 0) {
			return false;
		}
		write(index, lines.toString());
		return true;
	}

	private enum Shape {
		PLAIN, EDGE, HOLDER
	}

	private static Item image(final SkinArt.Item art, final Shape shape) {
		return new Item(art.name, art.shown, (data, target) -> {
			final ArgbImage image = LauncherImages.decode(data, art.path);
			if (image == null) {
				return false;
			}
			final ArgbImage shaped;
			switch (shape) {
			case EDGE:
				shaped = LauncherImages.edgeNine(image);
				break;
			case HOLDER:
				shaped = LauncherImages.holder(image);
				break;
			default:
				shaped = image;
				break;
			}
			save(GameCatalog.bitmap(shaped), target, Bitmap.CompressFormat.PNG);
			return true;
		});
	}

	/**
	 * A PNG or JPEG of the Reforged interface. With a slice the image is a whole frame and becomes the 3 x 3 image the
	 * frame drawing takes; pictures larger than a screen are scaled down.
	 */
	private static Item web(final SkinArt.Item art) {
		final String name = art.name;
		final String path = art.path;
		final int slice = art.slice;
		final boolean centre = art.centre;
		return new Item(name, art.shown, (data, target) -> {
			if (!data.has(path)) {
				return false;
			}
			final ByteBuffer file = data.read(path);
			if (file == null) {
				return false;
			}
			final ByteBuffer bytes = file.duplicate();
			bytes.position(0);
			final byte[] encoded = new byte[bytes.remaining()];
			bytes.get(encoded);
			final BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeByteArray(encoded, 0, encoded.length, options);
			options.inJustDecodeBounds = false;
			options.inSampleSize = 1;
			while ((slice == 0) && ((Math.max(options.outWidth, options.outHeight) / options.inSampleSize) > 2048)) {
				options.inSampleSize *= 2;
			}
			final Bitmap bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.length, options);
			if (bitmap == null) {
				return false;
			}
			save(slice > 0 ? sliced(bitmap, slice, centre) : bitmap, target,
					name.endsWith(".jpg") ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG);
			return true;
		});
	}

	/** Corners kept, edges and middle scaled to squares of the slice size; without centre the middle stays empty. */
	private static Bitmap sliced(final Bitmap frame, final int slice, final boolean centre) {
		final int s = Math.min(slice, Math.min(frame.getWidth(), frame.getHeight()) / 3);
		final int[] xs = { 0, s, frame.getWidth() - s, frame.getWidth() };
		final int[] ys = { 0, s, frame.getHeight() - s, frame.getHeight() };
		final Bitmap nine = Bitmap.createBitmap(s * 3, s * 3, Bitmap.Config.ARGB_8888);
		final Canvas canvas = new Canvas(nine);
		final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				if ((row == 1) && (column == 1) && !centre) {
					continue;
				}
				canvas.drawBitmap(frame, new Rect(xs[column], ys[row], xs[column + 1], ys[row + 1]),
						new Rect(column * s, row * s, (column + 1) * s, (row + 1) * s), paint);
			}
		}
		return nine;
	}

	/** The image at a path given without extension: DDS in data from 1.30 on, BLP before. */
	static Bitmap decode(final DataSource data, final String path) throws IOException {
		return GameCatalog.bitmap(LauncherImages.decode(data, path));
	}

	/** Loading screens are four tiles: two square ones above and two half-height ones below. */
	private static boolean tiles(final DataSource data, final String prefix, final File target) throws IOException {
		final ArgbImage joined = LauncherImages.tiles(data, prefix);
		if (joined == null) {
			return false;
		}
		save(GameCatalog.bitmap(joined), target, Bitmap.CompressFormat.JPEG);
		return true;
	}

	private static boolean copy(final DataSource data, final String path, final File target) throws IOException {
		if (!data.has(path)) {
			return false;
		}
		try (InputStream in = data.getResourceAsStream(path)) {
			if (in == null) {
				return false;
			}
			store(in, target);
		}
		return true;
	}

	/** The scene recordings and a first frame of each, shown until the recording plays. */
	private static boolean scene(final DataSource data, final String scene, final File target) throws IOException {
		if (!copy(data, "webui\\webms\\" + scene + ".webm", target)) {
			return false;
		}
		final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
		try {
			retriever.setDataSource(target.getAbsolutePath());
			final Bitmap frame = retriever.getFrameAtTime(0);
			if (frame != null) {
				save(frame, new File(target.getParentFile(), scene + ".jpg"), Bitmap.CompressFormat.JPEG);
			}
		}
		catch (final RuntimeException e) {
			Log.w(TAG, "No poster frame for " + scene, e);
		}
		finally {
			try {
				retriever.release();
			}
			catch (final IOException | RuntimeException e) {
				Log.w(TAG, "Unable to release the metadata reader", e);
			}
		}
		return true;
	}

	/** The glue strings the launcher shows, so that it speaks as the installed game does. */
	private static boolean strings(final DataTable ini, final DataSource data, final File target)
			throws IOException {
		final Map<String, String> found = GlueText.read(ini, data);
		if (found == null) {
			return false;
		}
		final StringBuilder text = new StringBuilder();
		for (final Map.Entry<String, String> entry : found.entrySet()) {
			text.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
		}
		write(target, text.toString());
		return true;
	}

	private static void save(final Bitmap bitmap, final File target, final Bitmap.CompressFormat format)
			throws IOException {
		final File partial = new File(target.getParentFile(), target.getName() + ".part");
		try (OutputStream out = new FileOutputStream(partial)) {
			if (!bitmap.compress(format, 90, out)) {
				throw new IOException("Unable to encode " + target.getName());
			}
		}
		if (!partial.renameTo(target)) {
			throw new IOException("Unable to store " + target);
		}
	}

	private static byte[] bytes(final InputStream in) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final byte[] buffer = new byte[65536];
		int read;
		while ((read = in.read(buffer)) != -1) {
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	private static void store(final InputStream in, final File target) throws IOException {
		final File partial = new File(target.getParentFile(), target.getName() + ".part");
		try (OutputStream out = new FileOutputStream(partial)) {
			final byte[] buffer = new byte[65536];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
		}
		if (!partial.renameTo(target)) {
			throw new IOException("Unable to store " + target);
		}
	}

	private static void write(final File file, final String text) throws IOException {
		file.getParentFile().mkdirs();
		final File partial = new File(file.getParentFile(), file.getName() + ".part");
		Files.write(partial.toPath(), text.getBytes(StandardCharsets.UTF_8));
		if (!partial.renameTo(file)) {
			throw new IOException("Unable to store " + file);
		}
	}

	private static String read(final File file) {
		if (!file.isFile()) {
			return null;
		}
		try {
			return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		}
		catch (final IOException e) {
			return null;
		}
	}

	private static void deleteTree(final File file) {
		final File[] children = file.listFiles();
		if (children != null) {
			for (final File child : children) {
				deleteTree(child);
			}
		}
		file.delete();
	}

	/** The cached art loaded for drawing. Missing files give null, and the drawings fall back. */
	static final class Art {
		private final SkinCache cache;
		private final Map<String, Bitmap> bitmaps = new HashMap<>();
		private final Map<String, String> strings = new HashMap<>();
		private Typeface font;
		private Typeface fontBold;

		Art(final SkinCache cache) {
			this.cache = cache;
			final String text = read(cache.file(STRINGS));
			if (text != null) {
				for (final String line : text.split("\n")) {
					final int tab = line.indexOf('\t');
					if (tab > 0) {
						this.strings.put(line.substring(0, tab), line.substring(tab + 1));
					}
				}
			}
		}

		Bitmap bitmap(final String name) {
			if (!this.bitmaps.containsKey(name)) {
				final File file = this.cache.file(name);
				this.bitmaps.put(name, file.isFile() ? BitmapFactory.decodeFile(file.getAbsolutePath()) : null);
			}
			return this.bitmaps.get(name);
		}

		/** A scene's first frame in this skin, or null when the game data has no recording of it. */
		Bitmap poster(final String scene) {
			final String recorded = this.cache.sceneName(scene);
			final String name = "webms/" + recorded + ".jpg";
			if (!this.bitmaps.containsKey(name)) {
				final File file = this.cache.scene(recorded, true);
				this.bitmaps.put(name, file.isFile() ? BitmapFactory.decodeFile(file.getAbsolutePath()) : null);
			}
			return this.bitmaps.get(name);
		}

		/** The scene's recording in this skin, or null when the game data has none. */
		File recording(final String scene) {
			final File file = this.cache.scene(this.cache.sceneName(scene), false);
			return file.isFile() && (file.length() > 0) ? file : null;
		}

		/** How many of the menu and campaign recordings the game data had. */
		int recordings() {
			int count = 0;
			for (final String scene : SCENES) {
				if (recording(scene) != null) {
					count++;
				}
			}
			return count;
		}

		boolean reforged() {
			return this.cache.reforged;
		}

		int gold() {
			return SkinArt.Palette.of(this.cache.reforged).gold;
		}

		int text() {
			return SkinArt.Palette.of(this.cache.reforged).text;
		}

		int text2() {
			return SkinArt.Palette.of(this.cache.reforged).text2;
		}

		/** Row lines and the plain frames drawn when an edge file is missing. */
		int line() {
			return SkinArt.Palette.of(this.cache.reforged).line;
		}

		boolean has(final String name) {
			return this.cache.file(name).isFile();
		}

		/** The small mission pictures: their cached name by the lower case path below the campaign pictures. */
		Map<String, String> missionPictures() {
			final Map<String, String> pictures = new LinkedHashMap<>();
			final String index = read(this.cache.file(MISSIONS));
			if (index != null) {
				final String folder = MISSIONS.substring(0, MISSIONS.indexOf('/') + 1);
				for (final String line : index.split("\n")) {
					final int tab = line.indexOf('\t');
					if (tab > 0) {
						pictures.put(line.substring(tab + 1), folder + line.substring(0, tab));
					}
				}
			}
			return pictures;
		}

		/** The skin's text font: the Reforged interface's MasterFont there, Friz Quadrata on the classic skin. */
		Typeface font() {
			if (this.font == null) {
				this.font = load(this.cache.reforged && has(FONT_MASTER) ? FONT_MASTER : FONT);
			}
			return this.font;
		}

		/** MasterFontBold of the Reforged interface, for headings and names; the text font where the data lacks it. */
		Typeface fontBold() {
			if (this.fontBold == null) {
				this.fontBold = this.cache.reforged && has(FONT_BOLD) ? load(FONT_BOLD) : font();
			}
			return this.fontBold;
		}

		private Typeface load(final String name) {
			final File file = this.cache.file(name);
			Typeface loaded = null;
			if (file.isFile()) {
				try {
					loaded = Typeface.createFromFile(file);
				}
				catch (final RuntimeException e) {
					Log.w(TAG, "Unable to load " + file, e);
				}
			}
			if ((loaded != null) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) {
				loaded = withGlobalFallback(file, loaded);
			}
			return loaded != null ? loaded : Typeface.SERIF;
		}

		private Typeface withGlobalFallback(final File main, final Typeface plain) {
			final File global = this.cache.file(FONT_GLOBAL);
			if (!global.isFile()) {
				return plain;
			}
			try {
				return new Typeface.CustomFallbackBuilder(new FontFamily.Builder(new Font.Builder(main).build()).build())
						.addCustomFallback(new FontFamily.Builder(new Font.Builder(global).build()).build())
						// A custom list has no system fonts behind it unless named; they bring the signs the data lacks.
						.setSystemFallback("sans-serif").build();
			}
			catch (final IOException | RuntimeException e) {
				Log.w(TAG, "Unable to add " + global, e);
				return plain;
			}
		}

		String string(final String key, final String fallback) {
			final String value = this.strings.get(key);
			return value != null ? value : fallback;
		}
	}
}
