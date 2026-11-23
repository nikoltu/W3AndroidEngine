package com.etheller.warsmash.movie;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.Log;

/**
 * The full screen movies: the campaign openings and endings, the logos and the movies a map asks for with
 * PlayCinematic. The game data keeps each one as Movies\name_width.avi (VP9 at 640, 1280 and 1920 pixels wide) with
 * its sound in Movies\Audio\name.ogg (.flac in some releases) and its captions in Movies\Captions\name.srt, the sound
 * and the captions in the language folder when they are spoken.
 */
public final class Movies {
	/** Plays movies on the platform's own video decoder. */
	public interface Player {
		/**
		 * Plays the movies one after another, then calls done (on any thread). Each one can be skipped.
		 */
		void play(List<Movie> movies, Runnable done);
	}

	private static final int[] WIDTHS = { 1920, 1280, 640 };
	private static final String[] AUDIO_EXTENSIONS = { ".ogg", ".flac" };
	private static volatile Player player;

	private Movies() {
	}

	public static void setPlayer(final Player player) {
		Movies.player = player;
	}

	/** Lets go of the player if it is still this one. */
	public static void removePlayer(final Player player) {
		if ((player != null) && (Movies.player == player)) {
			Movies.player = null;
		}
	}

	public static boolean canPlay() {
		return player != null;
	}

	/**
	 * @param screenWidth the width the movie will fill; the smallest copy at least as wide is chosen, else the
	 *                    widest there is
	 * @return the movies of these names that are in the data, in the same order; names without a movie (the
	 *         openings Blizzard.j names that were never made, say) are left out
	 */
	public static List<Movie> find(final DataSource data, final List<String> names, final int screenWidth) {
		final List<Movie> found = new ArrayList<>();
		for (final String name : names) {
			final Movie movie = find(data, name, screenWidth);
			if (movie != null) {
				found.add(movie);
			}
		}
		return found;
	}

	public static Movie find(final DataSource data, final String name, final int screenWidth) {
		if ((data == null) || (name == null) || name.isEmpty()) {
			return null;
		}
		final String base = "Movies\\" + name;
		File video = null;
		for (int i = WIDTHS.length - 1; i >= 0; i--) {
			final File copy = file(data, base + "_" + WIDTHS[i] + ".avi");
			if (copy != null) {
				video = copy;
				if (WIDTHS[i] >= screenWidth) {
					break;
				}
			}
		}
		if (video == null) {
			return null;
		}
		File audio = null;
		for (final String extension : AUDIO_EXTENSIONS) {
			audio = file(data, "Movies\\Audio\\" + name + extension);
			if (audio != null) {
				break;
			}
		}
		final File captions = file(data, "Movies\\Captions\\" + name + ".srt");
		return new Movie(name, video, audio, captions, () -> captionFonts(data));
	}

	/** The fonts the captions can be drawn in: the game's own, then the computer's. */
	private static List<File> captionFonts(final DataSource data) {
		// The game's own fonts, the interface font first and then the ones its other languages use (Cyrillic, Chinese,
		// Korean); the Latin one lacks their letters.
		final String[] gameFonts = { "Fonts\\FRIZQT__.TTF", "Fonts\\NIM_____.TTF",
				"Fonts\\ARHEIU20M.TTF", "Fonts\\ARHEIU20_MD.TTF", "Fonts\\ARHEIGB_BD.TTF", "Fonts\\GHEI00B_T.TTF",
				"Fonts\\DFHEIMD.TTF", "Fonts\\DFHEILT.TTF", "Fonts\\DFST-M3U.TTF", "Fonts\\TT5500M_.TTF",
				"Fonts\\2002.TTF", "Fonts\\2002B.TTF", "Fonts\\DFKGOTHICMD.TTF", "Fonts\\ULEI00M.TTF",
				"Fonts\\BlizzardGlobal-v5_4.ttf" };
		// Fonts of the computer that cover the scripts the game's own fonts miss: Windows, macOS, then Linux.
		final String[] computerFonts = { "C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/malgun.ttf",
				"C:/Windows/Fonts/msjh.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/YuGothR.ttc",
				"C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc", "C:/Windows/Fonts/BIZ-UDGothicR.ttc",
				"C:/Windows/Fonts/simsun.ttc", "C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/arial.ttf",
				"/System/Library/Fonts/PingFang.ttc", "/System/Library/Fonts/AppleSDGothicNeo.ttc",
				"/System/Library/Fonts/Hiragino Sans GB.ttc",
				"/System/Library/Fonts/\u30d2\u30e9\u30ae\u30ce\u89d2\u30b4\u30b7\u30c3\u30af W3.ttc",
				"/System/Library/Fonts/Hiragino Sans W3.ttc",
				"/System/Library/Fonts/Supplemental/Arial Unicode.ttf", "/Library/Fonts/Arial Unicode.ttf",
				"/System/Library/Fonts/Helvetica.ttc", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
				"/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
				"/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
				"/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc",
				"/usr/share/fonts/google-noto-sans-cjk-fonts/NotoSansCJK-Regular.ttc",
				"/usr/share/fonts/truetype/fonts-japanese-gothic.ttf", "/usr/share/fonts/opentype/ipafont-gothic/ipag.ttf",
				"/usr/share/fonts/truetype/takao-gothic/TakaoGothic.ttf", "/usr/share/fonts/vlgothic/VL-Gothic-Regular.ttf",
				"/usr/share/fonts/truetype/wqy/wqy-microhei.ttc", "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
				"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/dejavu/DejaVuSans.ttf",
				"/usr/share/fonts/TTF/DejaVuSans.ttf", "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf" };
		final List<File> fonts = new ArrayList<>();
		for (final String path : gameFonts) {
			final File font = file(data, path);
			if (font != null) {
				fonts.add(font);
			}
		}
		final String windows = System.getenv("WINDIR");
		for (final String path : computerFonts) {
			File font = new File(path);
			if (!font.isFile() && (windows != null) && path.startsWith("C:/Windows/")) {
				font = new File(windows, path.substring("C:/Windows/".length()));
			}
			if (font.isFile()) {
				fonts.add(font);
			}
		}
		if (fonts.isEmpty()) {
			// A Linux with its fonts elsewhere (other distribution layouts, the user's own folders).
			for (final String root : new String[] { "/usr/share/fonts", "/usr/local/share/fonts",
					System.getProperty("user.home", "") + "/.local/share/fonts", System.getProperty("user.home", "") + "/.fonts" }) {
				scanFonts(new File(root), 0, fonts);
			}
		}
		return fonts;
	}

	private static void scanFonts(final File folder, final int depth, final List<File> found) {
		final File[] children = folder.listFiles();
		if ((children == null) || (depth > 4) || (found.size() >= 8)) {
			return;
		}
		Arrays.sort(children);
		for (final File child : children) {
			if (child.isDirectory()) {
				scanFonts(child, depth + 1, found);
			}
			else {
				final String name = child.getName().toLowerCase(Locale.ROOT);
				if ((name.startsWith("notosanscjk") || name.startsWith("notosansjp") || name.startsWith("sourcehansansjp")
						|| name.startsWith("ipag") || name.startsWith("takao") || name.startsWith("vl-gothic")
						|| name.startsWith("dejavusans.") || name.startsWith("liberationsans-regular"))
						&& (name.endsWith(".ttf") || name.endsWith(".ttc") || name.endsWith(".otf"))) {
					found.add(child);
				}
			}
		}
	}

	private static File file(final DataSource data, final String path) {
		if (!data.has(path)) {
			return null;
		}
		try {
			final File file = data.getFile(path);
			return (file != null) && file.isFile() ? file : null;
		}
		catch (final IOException | RuntimeException e) {
			Log.error("Unable to open the movie file " + path + ": " + e);
			return null;
		}
	}

	/** Plays the movies on the platform's player, or calls done at once when there are none or no player. */
	public static void play(final List<Movie> movies, final Runnable done) {
		final Player current = player;
		if (movies.isEmpty() || (current == null)) {
			if (!movies.isEmpty()) {
				Log.warn("No movie player on this platform, skipped: " + movies);
			}
			done.run();
			return;
		}
		current.play(movies, done);
	}
}
