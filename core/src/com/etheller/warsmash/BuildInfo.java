package com.etheller.warsmash;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

/**
 * Which build of the engine this is. The build writes the numbers into a resource; whether the build is an official
 * one is decided where it runs: on Android by the certificate the APK is signed with, on the desktop by a flag the
 * build was given. A build made from a tree with uncommitted changes, or without git, is never official.
 */
public final class BuildInfo {
	/** The resource the build writes. */
	public static final String RESOURCE = "/com/etheller/warsmash/build.properties";
	/**
	 * Raised whenever a change makes games of the same version and commit unable to play together (rules data,
	 * message formats); builds with another number are never offered to each other.
	 */
	public static final int SIMULATION_DATA_VERSION = 1;
	public static final String UNKNOWN_COMMIT = "unknown";

	private static volatile BuildInfo current;

	private final String versionName;
	private final int versionCode;
	private final String commit;
	private final boolean dirty;
	private final boolean official;
	private final String officialCertificate;

	private BuildInfo(final String versionName, final int versionCode, final String commit, final boolean dirty,
			final boolean official, final String officialCertificate) {
		this.versionName = versionName;
		this.versionCode = versionCode;
		this.commit = commit;
		this.dirty = dirty;
		this.official = official && !dirty && !UNKNOWN_COMMIT.equals(commit);
		this.officialCertificate = officialCertificate;
	}

	/** The build running now. */
	public static BuildInfo current() {
		BuildInfo info = current;
		if (info == null) {
			synchronized (BuildInfo.class) {
				info = current;
				if (info == null) {
					info = load();
					current = info;
				}
			}
		}
		return info;
	}

	/**
	 * Android tells here which certificate its APK is signed with; the build is official when that is the project's
	 * certificate the build was given. The build's own official flag does not count there.
	 */
	public static void setSigningCertificate(final String sha256) {
		final BuildInfo info = current();
		final boolean match = (info.officialCertificate != null) && !info.officialCertificate.isEmpty()
				&& info.officialCertificate.equals(normalizeFingerprint(sha256));
		current = new BuildInfo(info.versionName, info.versionCode, info.commit, info.dirty, match,
				info.officialCertificate);
	}

	private static BuildInfo load() {
		try (InputStream in = BuildInfo.class.getResourceAsStream(RESOURCE)) {
			if (in != null) {
				final Properties properties = new Properties();
				properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
				return parse(properties);
			}
		}
		catch (final IOException | RuntimeException e) {
			// A build without the resource is a build of unknown origin.
		}
		return parse(new Properties());
	}

	/** Missing or unreadable entries make the build "dev", commit "unknown", not official. */
	public static BuildInfo parse(final Properties properties) {
		final String version = properties.getProperty("versionName", "").trim();
		int code = 0;
		try {
			code = Integer.parseInt(properties.getProperty("versionCode", "0").trim());
		}
		catch (final NumberFormatException e) {
			code = 0;
		}
		String commit = properties.getProperty("commit", "").trim().toLowerCase(Locale.ROOT);
		if (!commit.matches("[0-9a-f]{4,40}")) {
			commit = UNKNOWN_COMMIT;
		}
		return new BuildInfo(version.isEmpty() ? "dev" : version, code, commit,
				Boolean.parseBoolean(properties.getProperty("dirty", "false").trim()),
				Boolean.parseBoolean(properties.getProperty("official", "false").trim()),
				normalizeFingerprint(properties.getProperty("officialCertSha256", "")));
	}

	/** Lower-case hex without separators, so "AB:CD" and "abcd" are the same fingerprint. */
	public static String normalizeFingerprint(final String fingerprint) {
		return fingerprint == null ? "" : fingerprint.replaceAll("[^0-9A-Fa-f]", "").toLowerCase(Locale.ROOT);
	}

	public String getVersionName() {
		return this.versionName;
	}

	public int getVersionCode() {
		return this.versionCode;
	}

	/** The short git commit the build was made from, or {@link #UNKNOWN_COMMIT}. */
	public String getCommit() {
		return this.commit;
	}

	public boolean isDirty() {
		return this.dirty;
	}

	public boolean isOfficial() {
		return this.official;
	}

	/** Such as "v0.3.12 (abc1234)"; with "+" after the commit when the tree had uncommitted changes. */
	public String displayVersion() {
		final String text = "v" + this.versionName;
		if (UNKNOWN_COMMIT.equals(this.commit)) {
			return text;
		}
		return text + " (" + this.commit + (this.dirty ? "+" : "") + ")";
	}

	/**
	 * What two peers compare before they play together: only identical builds are compatible. A build without a
	 * known commit or with uncommitted changes cannot be told from another one, so it has no id to match.
	 */
	public String getNetworkCompatibilityId() {
		return this.versionName + "-" + this.commit + (this.dirty ? "-dirty" : "") + "-s" + SIMULATION_DATA_VERSION;
	}

	/** Whether {@link #getNetworkCompatibilityId()} identifies the build; false for builds nothing can vouch for. */
	public boolean hasNetworkIdentity() {
		return !this.dirty && !UNKNOWN_COMMIT.equals(this.commit);
	}

	/** Whether a peer's id is the same build as this one. */
	public boolean isNetworkCompatible(final String peerId) {
		return hasNetworkIdentity() && getNetworkCompatibilityId().equals(peerId);
	}

	@Override
	public String toString() {
		return displayVersion() + (this.official ? " official" : " unofficial");
	}
}
