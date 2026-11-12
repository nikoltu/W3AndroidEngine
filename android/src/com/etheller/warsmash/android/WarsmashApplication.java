package com.etheller.warsmash.android;

import java.security.MessageDigest;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import com.etheller.warsmash.BuildInfo;

/** Tells the engine which certificate this APK is signed with, so that it knows whether it is an official build. */
public class WarsmashApplication extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		BuildInfo.setSigningCertificate(signingCertificateSha256());
	}

	/** The SHA-256 of the certificate the APK is signed with (the signer, not an earlier one it rotated from). */
	private String signingCertificateSha256() {
		try {
			final PackageManager manager = getPackageManager();
			Signature[] signatures;
			if (Build.VERSION.SDK_INT >= 28) {
				final PackageInfo info = manager.getPackageInfo(getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
				signatures = (info.signingInfo == null) ? null : info.signingInfo.getApkContentsSigners();
			}
			else {
				@SuppressWarnings("deprecation")
				final PackageInfo info = manager.getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
				@SuppressWarnings("deprecation")
				final Signature[] legacy = info.signatures;
				signatures = legacy;
			}
			// An APK signed by several keys is not signed by the project alone.
			if ((signatures == null) || (signatures.length != 1)) {
				return "";
			}
			final byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
			final StringBuilder hex = new StringBuilder();
			for (final byte b : digest) {
				hex.append(String.format("%02x", b & 0xFF));
			}
			return hex.toString();
		}
		catch (final Exception e) {
			return "";
		}
	}
}
