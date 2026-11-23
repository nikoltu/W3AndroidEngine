package com.etheller.warsmash.android;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;

import com.badlogic.gdx.backends.android.AndroidApplication;

/** The manifest asks for OpenGL ES 3.0, but a sideloaded install is not checked against it. */
final class GlesSupport {
	private static final int ES_3_0 = 0x30000;

	private GlesSupport() {
	}

	static boolean supported(final Context context) {
		final int version = version(context);
		return (version == 0) || (version >= ES_3_0);
	}

	/** 0 when the device does not say. */
	private static int version(final Context context) {
		final ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		return manager == null ? 0 : manager.getDeviceConfigurationInfo().reqGlEsVersion;
	}

	/** Shows the message and closes the activity; true when the caller must stop starting. */
	static boolean refuse(final Activity activity) {
		if (supported(activity)) {
			return false;
		}
		final int version = version(activity);
		final String has = (version >>> 16) + "." + (version & 0xFFFF);
		new AlertDialog.Builder(activity).setTitle(R.string.gles3_title)
				.setMessage(activity.getString(R.string.gles3_required, has))
				.setCancelable(false).setPositiveButton(android.R.string.ok, (dialog, which) -> activity.finish())
				.show();
		return true;
	}

	/** The entry window shows the message; any other window that would use GL30 hands over to it and closes. */
	static boolean redirect(final Activity activity) {
		if (supported(activity)) {
			return false;
		}
		if (activity instanceof AndroidApplication) {
			AndroidLauncher.skipKeyboardHeightClose((AndroidApplication) activity);
		}
		activity.startActivity(new Intent(activity, DataFolderActivity.class));
		activity.finish();
		return true;
	}
}
