package com.etheller.warsmash.launcher;

import com.etheller.warsmash.BuildInfo;

/**
 * Whether an Android build carries the project's own signature. The certificate's fingerprint is public and sits in
 * code on purpose: a copy signed with any other key says so on the launcher's home screen and in About, however it was
 * built.
 */
public final class BuildOrigin {
	/** The SHA-256 of the certificate the project's releases are signed with. */
	public static final String OFFICIAL_CERT_SHA256 = "c1133934fbf93ac25abd6f3a2ee144a577c59386435259991a33a2da7be273e2";
	/** The labels are the names of the launcher's texts; {@link #text} and {@link #notice} give the words. */
	public static final String UNOFFICIAL = "build_not_official";
	/** A debug build is always signed with a local debug key, so a mismatch would say nothing; it names the kind of build instead. */
	public static final String DEBUG = "build_debug";

	private BuildOrigin() {
	}

	/** The words of a label from {@link #label}, in the game language. */
	public static String text(final String label) {
		return label.isEmpty() ? label : LauncherText.get(label);
	}

	/** The longer notice Home shows under the menu, with where the official build is. */
	public static String notice(final String label) {
		return UNOFFICIAL.equals(label) ? LauncherText.get("build_not_official_notice", ProjectInfo.SOURCE_NAME)
				: text(label);
	}

	/** The label to show, or an empty text for a build signed with the project's key. */
	public static String label(final boolean debuggable, final String certSha256) {
		if (debuggable) {
			return DEBUG;
		}
		final String signer = BuildInfo.normalizeFingerprint(certSha256);
		return !signer.isEmpty() && signer.equals(OFFICIAL_CERT_SHA256) ? "" : UNOFFICIAL;
	}
}
