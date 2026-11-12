package com.etheller.warsmash.android;

import android.app.Activity;
import android.os.Build;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/**
 * Back for activities that handle it themselves. From Android 13 the system delivers Back to a callback instead of
 * a key event or onBackPressed, so the handler is registered there; older versions keep calling onBackPressed.
 */
final class BackCallbacks {
	private BackCallbacks() {
	}

	/** Registers the handler on Android 13 and later; true when it did, false when onBackPressed still delivers Back. */
	static boolean register(final Activity activity, final Runnable handler) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			return false;
		}
		Registration.register(activity, handler);
		return true;
	}

	private static final class Registration {
		static void register(final Activity activity, final Runnable handler) {
			final OnBackInvokedCallback callback = handler::run;
			activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,
					callback);
		}
	}
}
