package com.etheller.warsmash.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Looks for a newer engine release on GitHub and downloads its APK. The platforms show the prompt and do the install;
 * the version comparison, the release lookup and the download are the same everywhere.
 */
public final class UpdateCheck {
	public static final String RELEASES_URL = "https://api.github.com/repos/nikoltu/W3AndroidEngine/releases/latest";
	/** A system property that makes the check answer with a release of this tag, without the network (for testing). */
	public static final String FAKE_PROPERTY = "warsmash.fakeUpdate";
	private static final String RELEASES_PAGE = "https://github.com/nikoltu/W3AndroidEngine/releases";
	private static final String USER_AGENT = "W3AndroidEngine-launcher";
	private static final int BUFFER = 64 * 1024;

	/** A published release: its tag, the page that shows it, and its APK if it has one. */
	public static final class Release {
		public final String tag;
		public final String pageUrl;
		public final String apkName;
		public final String apkUrl;
		public final long apkSize;

		Release(final String tag, final String pageUrl, final String apkName, final String apkUrl,
				final long apkSize) {
			this.tag = tag;
			this.pageUrl = pageUrl;
			this.apkName = apkName;
			this.apkUrl = apkUrl;
			this.apkSize = apkSize;
		}

		public boolean hasApk() {
			return (this.apkUrl != null) && (this.apkSize > 0);
		}
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
	 * ignored. Null when the text has no number.
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

	/** Positive when {@code a} is the newer version; zero when either has no version. */
	public static int compare(final String a, final String b) {
		final int[] left = parse(a);
		final int[] right = parse(b);
		if ((left == null) || (right == null)) {
			return 0;
		}
		for (int i = 0; i < left.length; i++) {
			if (left[i] != right[i]) {
				return Integer.compare(left[i], right[i]);
			}
		}
		return 0;
	}

	/**
	 * The build number of a version as the APK carries it: minor * 100 + patch (0.3.11 is 311), with the major number
	 * above them. Zero when the text has no number. Valid while minor and patch stay below 100.
	 */
	public static int versionCode(final String version) {
		final int[] numbers = parse(version);
		return numbers == null ? 0 : (numbers[0] * 10000) + (numbers[1] * 100) + numbers[2];
	}

	/** Whether the prompt shows: the release is newer than the installed version and not the one the player skipped. */
	public static boolean offers(final String installed, final Release release, final String skipped) {
		return offers(versionCode(installed), release, skipped, true);
	}

	/**
	 * Whether the prompt shows for an installed build. Nothing older than the installed build is ever offered, and an
	 * official build is offered only a release with a higher version code. An unofficial build (one built by someone
	 * else, signed with another key) is not any release, so it is also offered the release of its own version, as long
	 * as that has an APK; skipping a release silences it either way.
	 */
	public static boolean offers(final String installed, final Release release, final String skipped,
			final boolean officialBuild) {
		return offers(versionCode(installed), release, skipped, officialBuild);
	}

	/** The same by the installed build's version code; zero means the build does not know its version. */
	public static boolean offers(final int installedCode, final Release release, final String skipped,
			final boolean officialBuild) {
		if ((release == null) || ((skipped != null) && !skipped.isEmpty() && (compare(release.tag, skipped) == 0))) {
			return false;
		}
		final int releaseCode = versionCode(release.tag);
		if (releaseCode <= 0) {
			return false;
		}
		if (officialBuild) {
			return (installedCode > 0) && (releaseCode > installedCode);
		}
		return release.hasApk() && (releaseCode >= installedCode);
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
				if (assetName.toLowerCase().endsWith(".apk")) {
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

	/** The latest release, or null on any failure (no network, rate limit, no release); the launcher goes on. */
	public static Release fetchLatest(final int timeoutMillis) {
		final String fake = System.getProperty(FAKE_PROPERTY);
		if ((fake != null) && !fake.isEmpty()) {
			return parse(fake) == null ? null : new Release(fake, RELEASES_PAGE, null, null, 0);
		}
		HttpURLConnection connection = null;
		try {
			connection = open(RELEASES_URL, timeoutMillis);
			connection.setRequestProperty("Accept", "application/vnd.github+json");
			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				return null;
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
			return "The release has no APK.";
		}
		if (!release.apkUrl.startsWith("https://")) {
			return "The release's APK is not on a secure address.";
		}
		HttpURLConnection connection = null;
		try {
			final File folder = target.getAbsoluteFile().getParentFile();
			if ((folder != null) && !folder.isDirectory() && !folder.mkdirs()) {
				return "The download folder cannot be made.";
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
					return "The server answered " + code + ".";
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
						out.write(buffer, 0, read);
						have += read;
						progress.update(have, release.apkSize);
					}
				}
			}
			if (part.length() != release.apkSize) {
				part.delete();
				return "The download has the wrong size.";
			}
			if (target.exists() && !target.delete()) {
				return "The old download cannot be replaced.";
			}
			if (!part.renameTo(target)) {
				return "The download cannot be kept.";
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
