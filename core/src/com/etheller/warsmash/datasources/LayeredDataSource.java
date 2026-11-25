package com.etheller.warsmash.datasources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Game data with graphics packages laid over the Classic files: a path the package has is read from the package, every
 * other path from the Classic data. The in-game interface (the "ui" folder) and the command button icons come from
 * the same packages as the models: the menus have a look of their own, which the game does not follow.
 * <p>
 * A package keeps the textures of the new materials as .dds, many with a "_diffuse" suffix (the terrain tiles), and
 * the speech of its models in its own locale folder; both are looked for under the names the Classic data asks for.
 */
public final class LayeredDataSource implements DataSource {
	public static final String HD_PACKAGE = "_hd.w3mod";
	public static final String DE_PACKAGE = "_de.w3mod";
	private static final String INTERFACE_FOLDER = "ui\\";
	/**
	 * Interface files whose package version is another picture under the same name: the HD cinematic border is the
	 * stone frame of the portrait, the Classic one the edge strip of the letterbox bars. The Reforged cinematic panel
	 * asks for the package's file by its packaged path.
	 */
	private static final String[] OTHER_PICTURES = { "-cinematic-border." };
	private static final String DIFFUSE_SUFFIX = "_diffuse";
	private static final int NOT_LAYERED = -1;
	private static final int UNKNOWN = -2;

	private final DataSource data;
	private final GraphicsSet graphics;
	private final String[] layers;
	/** For each asked path the layer it reads from, twice over, plus one for the "_diffuse" name; or NOT_LAYERED. */
	private final PathCache resolved = new PathCache();

	/**
	 * @param graphics the in-game graphics; its packages the data lacks are left out
	 * @param locales  the language folders of the data, such as "enus", the one that wins first
	 */
	private LayeredDataSource(final DataSource data, final GraphicsSet graphics, final String[] locales) {
		this.data = data;
		this.graphics = graphics;
		this.layers = layers(data, graphics.getPackages(), locales);
	}

	/** The data as a game reads it with these graphics: the data itself for Classic, or for data without them. */
	public static DataSource wrap(final DataSource data, final GraphicsSet graphics, final String... locales) {
		if ((graphics == GraphicsSet.CLASSIC) || !graphics.isAvailable(data)) {
			return data;
		}
		return new LayeredDataSource(data, graphics, locales);
	}

	@Override
	public DataSource graphicsIndependent() {
		return this.data.graphicsIndependent();
	}

	/** The graphics a data source shows: its own when it is layered, else Classic. */
	public static GraphicsSet graphicsOf(final DataSource data) {
		return data instanceof LayeredDataSource ? ((LayeredDataSource) data).graphics : GraphicsSet.CLASSIC;
	}

	private static String[] layers(final DataSource data, final String[] packages, final String[] locales) {
		final List<String> layers = new ArrayList<>();
		for (final String graphicsPackage : packages) {
			if (data.has(graphicsPackage + "\\" + GraphicsSet.PACKAGE_MARK)) {
				// The package's speech wins over its other files, as a locale layer does over the Classic data.
				for (final String locale : locales) {
					if ((locale != null) && !locale.isEmpty()) {
						layers.add(graphicsPackage + "\\_locales\\" + locale + ".w3mod\\");
					}
				}
				layers.add(graphicsPackage + "\\");
			}
		}
		return layers.toArray(new String[0]);
	}

	/** The path in the data that is read for this path. */
	String resolve(final String filepath) {
		if (filepath == null) {
			return null;
		}
		int found = this.resolved.get(filepath, UNKNOWN);
		if (found == UNKNOWN) {
			found = findLayered(filepath);
			this.resolved.put(filepath, found);
		}
		if (found == NOT_LAYERED) {
			return filepath;
		}
		final String path = filepath.replace('/', '\\');
		final String layer = this.layers[found >> 1];
		return (found & 1) == 0 ? layer + path : layer + diffuseName(path);
	}

	private static String diffuseName(final String path) {
		return path.substring(0, path.length() - 4) + DIFFUSE_SUFFIX + ".dds";
	}

	/** @return twice the index of the layer that has the path, plus one for its "_diffuse" name; or NOT_LAYERED */
	private int findLayered(final String filepath) {
		final String path = filepath.replace('/', '\\');
		final String lower = path.toLowerCase(Locale.US);
		if (lower.startsWith(HD_PACKAGE) || lower.startsWith(DE_PACKAGE)) {
			return NOT_LAYERED;
		}
		if (lower.startsWith(INTERFACE_FOLDER) && isOtherPicture(lower)) {
			return NOT_LAYERED;
		}
		final String diffuse = lower.endsWith(".dds") && !lower.endsWith(DIFFUSE_SUFFIX + ".dds") ? diffuseName(path)
				: null;
		for (int i = 0; i < this.layers.length; i++) {
			final String layer = this.layers[i];
			if (this.data.has(layer + path)) {
				return i << 1;
			}
			if ((diffuse != null) && this.data.has(layer + diffuse)) {
				return (i << 1) | 1;
			}
		}
		return NOT_LAYERED;
	}

	private static boolean isOtherPicture(final String lowerPath) {
		for (final String name : OTHER_PICTURES) {
			if (lowerPath.contains(name)) {
				return true;
			}
		}
		return false;
	}

	private boolean isLayered(final String filepath) {
		if (filepath == null) {
			return false;
		}
		int found = this.resolved.get(filepath, UNKNOWN);
		if (found == UNKNOWN) {
			found = findLayered(filepath);
			this.resolved.put(filepath, found);
		}
		return found != NOT_LAYERED;
	}

	@Override
	public InputStream getResourceAsStream(final String filepath) throws IOException {
		return this.data.getResourceAsStream(resolve(filepath));
	}

	@Override
	public IndependentStream.Opener opener(final String filepath) {
		return this.data.opener(resolve(filepath));
	}

	@Override
	public File getFile(final String filepath) throws IOException {
		return this.data.getFile(resolve(filepath));
	}

	@Override
	public File fileOnDisk(final String filepath) {
		return this.data.fileOnDisk(resolve(filepath));
	}

	@Override
	public File getDirectory(final String filepath) throws IOException {
		return this.data.getDirectory(filepath);
	}

	@Override
	public ByteBuffer read(final String path) throws IOException {
		return this.data.read(resolve(path));
	}

	/** The Classic file of this path, which a package's file of the same name replaces; null when there is none. */
	public ByteBuffer readClassic(final String filepath) throws IOException {
		return this.data.has(filepath) ? this.data.read(filepath) : null;
	}

	@Override
	public boolean has(final String filepath) {
		return isLayered(filepath) || this.data.has(filepath);
	}

	/** A path a package has wins over the Classic files, whatever its place in the list. */
	@Override
	public String findFirst(final String... filepaths) {
		for (final String filepath : filepaths) {
			if (isLayered(filepath)) {
				return filepath;
			}
		}
		return this.data.findFirst(filepaths);
	}

	@Override
	public Collection<String> getListfile() {
		return this.data.getListfile();
	}

	@Override
	public Collection<String> getListfile(final Predicate<String> accept) {
		return this.data.getListfile(accept);
	}

	@Override
	public Collection<String> getListfileByExtension(final String extension) {
		return this.data.getListfileByExtension(extension);
	}

	@Override
	public void close() throws IOException {
		this.data.close();
	}
}
