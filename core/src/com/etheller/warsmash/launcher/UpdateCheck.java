package com.etheller.warsmash.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Looks for a newer engine release on GitHub and downloads its APK. The platforms show the prompt and do the install;
 * the version comparison, the release lookup and the download are the same everywhere.
 */
public final class UpdateCheck {
	public static final String RELEASES_URL = "https://api.github.com/repos/nikoltu/W3AndroidEngine/releases/latest";
	/**
	 * A system property that makes the check answer with a release of this tag, without the network (for testing).
	 * "ratelimit" answers as when the API limit is reached and the web route fails too, "ratelimit:v0.4.99" as when
	 * only the API limit is reached and the web route names that tag.
	 */
	public static final String FAKE_PROPERTY = "warsmash.fakeUpdate";
	private static final String FAKE_RATE_LIMIT = "ratelimit";
	private static final String RELEASES_PAGE = ProjectInfo.SOURCE_URL + "/releases";
	/** Redirects to the page of the latest release's tag; the web route has no API limit. */
	private static final String LATEST_PAGE = RELEASES_PAGE + "/latest";
	private static final String TAG_MARKER = "/releases/tag/";
	private static final String USER_AGENT = "W3AndroidEngine-launcher";
	private static final int BUFFER = 64 * 1024;
	/** The API's answer when its limit is reached, before the web route is tried. */
	private static final Release RATE_LIMITED = unknownRelease();
	/** Set when the player has closed the prompt of an unknown version; it is not offered again in this session. */
	private static volatile boolean unknownDismissed;

	/**
	 * A published release: its tag, the page that shows it, and its APK if it has one. A release of unknown version
	 * has an empty tag and the releases page: the check could not learn the version, so the player is sent to GitHub.
	 */
	public static final class Release {
		public final String tag;
		public final String pageUrl;
		public final String apkName;
		public final String apkUrl;
		public final long apkSize;
		private final boolean unknown;
		/** Learned from the web page: no APK is known, but the page is worth opening for any build. */
		private final boolean pageOnly;

		Release(final String tag, final String pageUrl, final String apkName, final String apkUrl,
				final long apkSize) {
			this(tag, pageUrl, apkName, apkUrl, apkSize, false, false);
		}

		private Release(final String tag, final String pageUrl, final String apkName, final String apkUrl,
				final long apkSize, final boolean unknown, final boolean pageOnly) {
			this.tag = tag;
			this.pageUrl = pageUrl;
			this.apkName = apkName;
			this.apkUrl = apkUrl;
			this.apkSize = apkSize;
			this.unknown = unknown;
			this.pageOnly = pageOnly;
		}

		public boolean hasApk() {
			return (this.apkUrl != null) && (this.apkSize > 0);
		}

		/** Whether the version of the release could not be learned (it cannot be compared or skipped). */
		public boolean isUnknown() {
			return this.unknown;
		}
	}

	static Release unknownRelease() {
		return new Release("", RELEASES_PAGE, null, null, 0, true, false);
	}

	/** The player closed the prompt of an unknown version: it is not offered again until the app starts anew. */
	public static void dismissUnknown() {
		unknownDismissed = true;
	}

	static void resetSession() {
		unknownDismissed = false;
	}

	/** Download progress; the download stops when {@link #cancelled()} says so. */
	public interface Progress {
		void update(long bytes, long totalBytes);

		boolean cancelled();
	}

	private UpdateCheck() {
	}

	/**
	 * The numbers of a version such as v0.3.6, 0.3.6-debug or 0.3: a leading v and anything after the numbers is
	 * ignored (the hotfix suffix is read by {@link #hotfix}). Null when the text has no number.
	 */
	public static int[] parse(final String version) {
		if (version == null) {
			return null;
		}
		int at = 0;
		final String text = version.trim();
		if ((text.length() > 0) && ((text.charAt(0) == 'v') || (text.charAt(0) == 'V'))) {
			at = 1;
		}
		final int[] numbers = new int[3];
		int count = 0;
		while ((count < 3) && (at < text.length()) && Character.isDigit(text.charAt(at))) {
			long number = 0;
			while ((at < text.length()) && Character.isDigit(text.charAt(at))) {
				number = Math.min(Integer.MAX_VALUE, (number * 10) + (text.charAt(at) - '0'));
				at++;
			}
			numbers[count++] = (int) number;
			if ((at < text.length()) && (text.charAt(at) == '.') && ((at + 1) < text.length())
					&& Character.isDigit(text.charAt(at + 1))) {
				at++;
			}
			else {
				break;
			}
		}
		return count == 0 ? null : numbers;
	}

	/**
	 * The hotfix number of a version such as 0.3.72-hotfix2: the digits after "-hotfix" (plain "-hotfix" is 1), zero
	 * for a version without that suffix. Any other suffix (-rc1, -debug, -hotfixx) counts for nothing.
	 */
	public static int hotfix(final String version) {
		if (parse(version) == null) {
			return 0;
		}
		final String text = version.trim().toLowerCase(java.util.Locale.ROOT);
		final int at = text.indexOf("-hotfix");
		if (at < 0) {
			return 0;
		}
		int i = at + "-hotfix".length();
		if ((i < text.length()) && Character.isLetter(text.charAt(i))) {
			return 0;
		}
		long number = 0;
		boolean digits = false;
		while ((i < text.length()) && Character.isDigit(text.charAt(i))) {
			number = Math.min(Integer.MAX_VALUE, (number * 10) + (text.charAt(i) - '0'));
			digits = true;
			i++;
		}
		return digits ? (int) Math.max(1, number) : 1;
	}

	/** The full ordering key: major, minor, patch, hotfix; null when the text has no number. */
	private static int[] rank(final String version) {
		final int[] numbers = parse(version);
		return numbers == null ? null : new int[] { numbers[0], numbers[1], numbers[2], hotfix(version) };
	}

	private static int compareRanks(final int[] left, final int[] right) {
		for (int i = 0; i < left.length; i++) {
			if (left[i] != right[i]) {
				return Integer.compare(left[i], right[i]);
			}
		}
		return 0;
	}

	/**
	 * Positive when {@code a} is the newer version; zero when either has no version. A "-hotfixN" build is newer than
	 * the same numbers without it and older than the next version.
	 */
	public static int compare(final String a, final String b) {
		final int[] left = rank(a);
		final int[] right = rank(b);
		if ((left == null) || (right == null)) {
			return 0;
		}
		return compareRanks(left, right);
	}

	/**
	 * The build number of a version as the APK carries it: minor * 100 + patch (0.3.11 is 311), with the major number
	 * above them. Zero when the text has no number. Valid while minor and patch stay below 100. A hotfix keeps the
	 * code of its base version, so the code alone never tells a hotfix from its base.
	 */
	public static int versionCode(final String version) {
		final int[] numbers = parse(version);
		return numbers == null ? 0 : (numbers[0] * 10000) + (numbers[1] * 100) + numbers[2];
	}

	/** Whether the prompt shows: the release is newer than the installed version and not the one the player skipped. */
	public static boolean offers(final String installed, final Release release, final String skipped) {
		return offers(installed, release, skipped, true);
	}

	/**
	 * Whether the prompt shows for an installed build. Only a release with a higher version (hotfix number included)
	 * than the installed build is ever offered, so a build of the release's own version (a debug build included) is
	 * never told to update to itself. An official build with an unknown version gets nothing; an unofficial one (built
	 * by someone else, signed with another key) whose version is unknown is offered a release that has an APK, or one
	 * known from the web page alone.
	 * Skipping a release silences it either way. A release of unknown version is offered to every build, once per
	 * session.
	 */
	public static boolean offers(final String installed, final Release release, final String skipped,
			final boolean officialBuild) {
		return offers(rank(installed), release, skipped, officialBuild);
	}

	/**
	 * The same by the installed build's version code, which has no hotfix number; zero means the build does not know
	 * its version. Prefer the overload that takes the version name.
	 */
	public static boolean offers(final int installedCode, final Release release, final String skipped,
			final boolean officialBuild) {
		return offers(codeRank(installedCode), release, skipped, officialBuild);
	}

	/**
	 * The same by the installed version name, with the code the APK carries as the fallback when the name has no
	 * numbers.
	 */
	public static boolean offers(final String installedName, final int installedCode, final Release release,
			final String skipped, final boolean officialBuild) {
		final int[] installed = rank(installedName);
		return offers(installed != null ? installed : codeRank(installedCode), release, skipped, officialBuild);
	}

	private static int[] codeRank(final int code) {
		return code > 0 ? new int[] { code / 10000, (code / 100) % 100, code % 100, 0 } : null;
	}

	private static boolean offers(final int[] installed, final Release release, final String skipped,
			final boolean officialBuild) {
		if (release == null) {
			return false;
		}
		if (release.unknown) {
			return !unknownDismissed;
		}
		if ((skipped != null) && !skipped.isEmpty() && (compare(release.tag, skipped) == 0)) {
			return false;
		}
		final int[] latest = rank(release.tag);
		if ((latest == null) || ((latest[0] | latest[1] | latest[2] | latest[3]) == 0)) {
			return false;
		}
		if (officialBuild) {
			return (installed != null) && (compareRanks(latest, installed) > 0);
		}
		return (release.hasApk() || release.pageOnly) && ((installed == null) || (compareRanks(latest, installed) > 0));
	}

	/** The latest release from the API's answer; null when it is a draft, a prerelease or not a release. */
	public static Release parseRelease(final String json) {
		final JsonValue root = new JsonReader().parse(json);
		if ((root == null) || !root.has("tag_name") || root.getBoolean("draft", false)
				|| root.getBoolean("prerelease", false) || (parse(root.getString("tag_name", null)) == null)) {
			return null;
		}
		final String tag = root.getString("tag_name");
		String name = null;
		String url = null;
		long size = 0;
		final JsonValue assets = root.get("assets");
		if (assets != null) {
			for (JsonValue asset = assets.child; asset != null; asset = asset.next) {
				final String assetName = asset.getString("name", "");
				// The name becomes a file name in the download folder.
				if (assetName.toLowerCase(Locale.ROOT).endsWith(".apk") && (assetName.indexOf('/') < 0)
						&& (assetName.indexOf('\\') < 0) && !assetName.startsWith(".")) {
					name = assetName;
					url = asset.getString("browser_download_url", null);
					size = asset.getLong("size", 0);
					break;
				}
			}
		}
		return new Release(tag, root.getString("html_url", ""), name, url, size);
	}

	private static HttpURLConnection open(final String address, final int timeoutMillis) throws IOException {
		final HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
		connection.setConnectTimeout(timeoutMillis);
		connection.setReadTimeout(timeoutMillis);
		connection.setRequestProperty("User-Agent", USER_AGENT);
		return connection;
	}

	/** Whether the answer of the API means its request limit is reached. */
	static boolean isRateLimited(final int code, final String remaining, final String body) {
		if (code == 429) {
			return true;
		}
		return (code == 403) && (((remaining != null) && "0".equals(remaining.trim()))
				|| ((body != null) && body.toLowerCase(Locale.ROOT).contains("rate limit")));
	}

	/**
	 * The release a redirect to {@code .../releases/tag/<tag>} names, without APK information; null when the address
	 * has no usable tag.
	 */
	static Release releaseFromLocation(final String location) {
		if (location == null) {
			return null;
		}
		final int at = location.indexOf(TAG_MARKER);
		if (at < 0) {
			return null;
		}
		final String rest = location.substring(at + TAG_MARKER.length());
		int end = rest.length();
		for (final char stop : new char[] { '?', '#', '/' }) {
			final int found = rest.indexOf(stop);
			if (found >= 0) {
				end = Math.min(end, found);
			}
		}
		final String raw = rest.substring(0, end);
		if (raw.isEmpty()) {
			return null;
		}
		try {
			final String tag = URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8");
			return parse(tag) == null ? null : new Release(tag, RELEASES_PAGE + "/tag/" + raw, null, null, 0, false, true);
		}
		catch (final IOException | IllegalArgumentException e) {
			return null;
		}
	}

	/** The latest release, or null on any failure (no network, no release); the launcher goes on. */
	public static Release fetchLatest(final int timeoutMillis) {
		final String fake = System.getProperty(FAKE_PROPERTY);
		if ((fake != null) && !fake.isEmpty()) {
			if (fake.startsWith(FAKE_RATE_LIMIT)) {
				final Release named = fake.startsWith(FAKE_RATE_LIMIT + ":")
						? releaseFromLocation(TAG_MARKER + fake.substring(FAKE_RATE_LIMIT.length() + 1))
						: null;
				return named != null ? named : unknownRelease();
			}
			return parse(fake) == null ? null : new Release(fake, RELEASES_PAGE, null, null, 0);
		}
		final long started = System.currentTimeMillis();
		final Release api = fetchFromApi(timeoutMillis);
		if (api != RATE_LIMITED) {
			return api;
		}
		// The limit is per address and lasts up to an hour; the player is still told, from the web page or at least
		// by a pointer to GitHub.
		LauncherLog.i("Update check: the API limit is reached");
		final int left = (int) Math.max(500, timeoutMillis - (System.currentTimeMillis() - started));
		final Release web = fetchFromWebPage(left);
		return web != null ? web : unknownRelease();
	}

	private static Release fetchFromWebPage(final int timeoutMillis) {
		HttpURLConnection connection = null;
		try {
			connection = open(LATEST_PAGE, timeoutMillis);
			connection.setInstanceFollowRedirects(false);
			final int code = connection.getResponseCode();
			if ((code < 300) || (code > 399)) {
				return null;
			}
			return releaseFromLocation(connection.getHeaderField("Location"));
		}
		catch (final IOException | RuntimeException e) {
			LauncherLog.i("Update check by the web page failed: " + e);
			return null;
		}
		finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	private static String errorText(final HttpURLConnection connection) {
		try (InputStream in = connection.getErrorStream()) {
			if (in == null) {
				return null;
			}
			final char[] buffer = new char[4096];
			final int read = new InputStreamReader(in, StandardCharsets.UTF_8).read(buffer);
			return read > 0 ? new String(buffer, 0, read) : null;
		}
		catch (final IOException e) {
			return null;
		}
	}

	/** The API's latest release; null on any failure; {@link #RATE_LIMITED} when its request limit is reached. */
	private static Release fetchFromApi(final int timeoutMillis) {
		HttpURLConnection connection = null;
		try {
			connection = open(RELEASES_URL, timeoutMillis);
			connection.setRequestProperty("Accept", "application/vnd.github+json");
			final int code = connection.getResponseCode();
			if (code != HttpURLConnection.HTTP_OK) {
				final String remaining = connection.getHeaderField("X-RateLimit-Remaining");
				return isRateLimited(code, remaining, code == 403 ? errorText(connection) : null) ? RATE_LIMITED
						: null;
			}
			try (InputStream in = connection.getInputStream()) {
				final StringBuilder text = new StringBuilder();
				final InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
				final char[] buffer = new char[8192];
				int read;
				while ((read = reader.read(buffer)) != -1) {
					text.append(buffer, 0, read);
					if (text.length() > (4 << 20)) {
						return null;
					}
				}
				return parseRelease(text.toString());
			}
		}
		catch (final IOException | RuntimeException e) {
			LauncherLog.i("Update check failed: " + e);
			return null;
		}
		finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	/**
	 * Downloads the release's APK into {@code target}, through {@code target}.part: a part left by an earlier try is
	 * continued when the server allows, and a file of the wrong size is never left as the target.
	 *
	 * @return null on success, else why it failed (a cancelled download gives an empty text)
	 */
	public static String download(final Release release, final File target, final Progress progress) {
		final File part = new File(target.getPath() + ".part");
		if (!release.hasApk()) {
			return LauncherText.get("update_error_no_apk");
		}
		if (!release.apkUrl.startsWith("https://")) {
			return LauncherText.get("update_error_insecure");
		}
		HttpURLConnection connection = null;
		try {
			final File folder = target.getAbsoluteFile().getParentFile();
			if ((folder != null) && !folder.isDirectory() && !folder.mkdirs()) {
				return LauncherText.get("update_error_folder");
			}
			if (part.length() > release.apkSize) {
				part.delete();
			}
			long have = part.length();
			if (have < release.apkSize) {
				connection = open(release.apkUrl, 15000);
				if (have > 0) {
					connection.setRequestProperty("Range", "bytes=" + have + "-");
				}
				final int code = connection.getResponseCode();
				if ((code != HttpURLConnection.HTTP_OK) && (code != HttpURLConnection.HTTP_PARTIAL)) {
					return LauncherText.get("update_error_server", code);
				}
				if (code == HttpURLConnection.HTTP_OK) {
					have = 0;
				}
				try (InputStream in = connection.getInputStream();
						RandomAccessFile out = new RandomAccessFile(part, "rw")) {
					out.setLength(have);
					out.seek(have);
					final byte[] buffer = new byte[BUFFER];
					int read;
					while ((read = in.read(buffer)) != -1) {
						if (progress.cancelled()) {
							return "";
						}
						if ((have + read) > release.apkSize) {
							break;
						}
						out.write(buffer, 0, read);
						have += read;
						progress.update(have, release.apkSize);
					}
				}
			}
			if (part.length() != release.apkSize) {
				part.delete();
				return LauncherText.get("update_error_size");
			}
			if (target.exists() && !target.delete()) {
				return LauncherText.get("update_error_replace");
			}
			if (!part.renameTo(target)) {
				return LauncherText.get("update_error_keep");
			}
			return null;
		}
		catch (final IOException | RuntimeException e) {
			LauncherLog.i("Update download failed: " + e);
			return String.valueOf(e.getMessage());
		}
		finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}
}
