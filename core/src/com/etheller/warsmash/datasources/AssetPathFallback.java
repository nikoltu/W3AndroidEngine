package com.etheller.warsmash.datasources;

import java.util.Locale;

/**
 * Finds an asset that a data source does not have under the path it was asked for.
 * <p>
 * Reforged campaign maps, the 3.0 skin tables and even some Classic models name files that only the HD packages
 * have (units such as the Chaplain, effects, cinematic doodads, bridge textures); the game shows those in Classic
 * graphics too. The packages are layers of the Classic data (war3.w3mod\_de.w3mod, war3.w3mod\_hd.w3mod in CASC, the
 * same folders in a prepared data folder), so a path below them resolves through the usual prefixes. Scripts also
 * write paths with doubled separators or with the absolute path of the editor's import folder.
 */
public final class AssetPathFallback {
	// The DE package holds lighter versions of the same models.
	private static final String[] GRAPHICS_PACKAGES = { "_de.w3mod", "_hd.w3mod" };
	private static final String IMPORT_FOLDER = "war3mapimported\\";
	private static final String DIFFUSE_SUFFIX = "_diffuse";
	private static final String ART_FOLDER = "\\assets\\art\\";
	private static final String BUILD_FOLDER = "\\bz\\";
	private static final String GAME_FOLDER = "\\war3.w3mod\\";

	private AssetPathFallback() {
	}

	/**
	 * Returns a path that the data source has for the asset, or null. Call it only for a path the data source does not
	 * have, so that a Classic file always wins over the packages.
	 */
	public static String find(final DataSource dataSource, final String path) {
		if ((path == null) || path.isEmpty()) {
			return null;
		}
		final String normalized = normalize(path);
		final String[] candidates = textureNames(normalized);
		for (final String candidate : candidates) {
			if (!candidate.equals(path) && dataSource.has(candidate)) {
				return candidate;
			}
		}
		for (final String graphicsPackage : GRAPHICS_PACKAGES) {
			for (final String candidate : candidates) {
				final String packaged = graphicsPackage + "\\" + candidate;
				if (dataSource.has(packaged)) {
					return packaged;
				}
			}
		}
		return null;
	}

	/**
	 * The names a texture can have in 2.0 and later data: the Classic name, the same name as .dds, and the .dds with the
	 * "_diffuse" suffix of the new material textures (some Classic bridge and tileset textures too).
	 */
	private static String[] textureNames(final String path) {
		final int dotIndex = path.lastIndexOf('.');
		if ((dotIndex == -1) || (dotIndex < path.lastIndexOf('\\'))) {
			return new String[] { path };
		}
		final String extension = path.substring(dotIndex).toLowerCase(Locale.US);
		if (!".blp".equals(extension) && !".dds".equals(extension) && !".tga".equals(extension)) {
			return new String[] { path };
		}
		final String base = path.substring(0, dotIndex);
		if (base.toLowerCase(Locale.US).endsWith(DIFFUSE_SUFFIX)) {
			return new String[] { path, base + ".dds" };
		}
		return new String[] { path, base + ".dds", base + DIFFUSE_SUFFIX + ".dds" };
	}

	/**
	 * Backslash separated path without doubled separators, and relative to the map for an absolute path into the
	 * editor's import folder.
	 */
	public static String normalize(final String path) {
		final StringBuilder builder = new StringBuilder(path.length());
		for (int i = 0; i < path.length(); i++) {
			char c = path.charAt(i);
			if (c == '/') {
				c = '\\';
			}
			if ((c == '\\') && ((builder.length() == 0) || (builder.charAt(builder.length() - 1) == '\\'))) {
				continue;
			}
			builder.append(c);
		}
		String normalized = builder.toString();
		if ((normalized.length() > 2) && (normalized.charAt(1) == ':')) {
			final String lower = normalized.toLowerCase(Locale.US);
			final int importFolder = lower.lastIndexOf(IMPORT_FOLDER);
			final int gameFolder = lower.lastIndexOf(GAME_FOLDER);
			int packageStart = lower.lastIndexOf(ART_FOLDER);
			if (packageStart != -1) {
				packageStart += ART_FOLDER.length();
			}
			else if (lower.startsWith(BUILD_FOLDER, 2)) {
				// A path on the artists' build drive (h:\bz\de\buildings\...) names the package the same way.
				packageStart = 2 + BUILD_FOLDER.length();
			}
			if (importFolder != -1) {
				normalized = normalized.substring(importFolder);
			}
			else if (gameFolder != -1) {
				// A path into a checkout of the game data (...\war3.w3mod\_de.w3mod\...) is relative to war3.w3mod.
				normalized = normalized.substring(gameFolder + GAME_FOLDER.length());
			}
			else if ((packageStart != -1) && (lower.length() > (packageStart + 3))
					&& (lower.charAt(packageStart + 2) == '\\')) {
				// Some HD and DE models name other models by their path in the artists' source tree
				// (...\assets\art\de\buildings\...); the folder after "art" is the graphics package.
				final String quality = lower.substring(packageStart, packageStart + 2);
				final String rest = normalized.substring(packageStart + 3);
				if ("sd".equals(quality)) {
					normalized = rest;
				}
				else if ("de".equals(quality) || "hd".equals(quality)) {
					normalized = "_" + quality + ".w3mod\\" + rest;
				}
			}
		}
		return normalized;
	}
}
