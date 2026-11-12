package com.etheller.warsmash.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.etheller.warsmash.BuildInfo;
import com.etheller.warsmash.launcher.LauncherKeys;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.launcher.UpdateCheck;

/**
 * The offer of a newer engine release, shown before the data folder screen: the release check runs beside the window
 * for a moment, and the window then goes on at once when there is nothing to offer. Its state outlives the window,
 * which Android makes again when the screen turns over; the window shows what the state says.
 */
final class UpdatePrompt {
	private static final String TAG = "Warsmash";
	private static final String ACTION_RESULT = "com.etheller.warsmash.android.UPDATE_RESULT";
	private static final int CHECK_TIMEOUT_MILLIS = 2000;
	/** How long the window waits for the release check before it goes on without it. */
	private static final long CHECK_WAIT_MILLIS = 2200;
	private static final long POLL_MILLIS = 150;

	private static final int RED = Color.rgb(0xE2, 0x9A, 0x9A);

	private enum State {
		CHECKING, OFFER, DOWNLOADING, PERMISSION, MANUAL, FAILED, INSTALLING, DONE
	}

	private static final Object LOCK = new Object();
	private static boolean started;
	private static long startedAt;
	private static volatile UpdateCheck.Release release;
	private static volatile boolean checked;
	private static volatile State state = State.CHECKING;
	private static volatile long bytes;
	private static volatile String problem;
	private static volatile boolean cancelled;
	private static volatile boolean downloading;
	private static File apk;

	private final Activity activity;
	private final Runnable proceed;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final Runnable poll = this::poll;
	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			onInstallResult(intent);
		}
	};
	private boolean receiving;
	private float scale;
	private boolean tablet;
	private FrameLayout root;
	private TextView title;
	private TextView detail;
	private ProgressBar progressBar;
	/** The skin art when the launcher has prepared it; without it the prompt keeps the plain launcher look. */
	private SkinCache.Art art;
	private int gold;
	private int text;
	private int textDim;
	private int line;
	private TextView first;
	private TextView second;
	private TextView third;
	private State shown;
	private long shownBytes = -1;

	UpdatePrompt(final Activity activity, final Runnable proceed) {
		this.activity = activity;
		this.proceed = proceed;
	}

	/** Whether the window has to wait for the release check (at most one check per launch, unless turned off). */
	static boolean pending(final Activity activity) {
		final SharedPreferences preferences = activity.getSharedPreferences(LauncherKeys.PREFERENCES,
				Context.MODE_PRIVATE);
		synchronized (LOCK) {
			if (state == State.DONE) {
				return false;
			}
			if (!started) {
				if (!preferences.getBoolean(LauncherKeys.KEY_UPDATE_CHECK, true)) {
					state = State.DONE;
					return false;
				}
				started = true;
				startedAt = System.currentTimeMillis();
				final Thread thread = new Thread(() -> {
					release = UpdateCheck.fetchLatest(CHECK_TIMEOUT_MILLIS);
					checked = true;
				}, "UpdateCheck");
				thread.setDaemon(true);
				thread.start();
			}
			return true;
		}
	}

	private static String installedVersion(final Context context) {
		try {
			return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
		}
		catch (final PackageManager.NameNotFoundException e) {
			return "";
		}
	}

	/** The installed build's version code, which a release has to exceed; zero when it cannot be read. */
	@SuppressWarnings("deprecation")
	private static int installedCode(final Context context) {
		try {
			final PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? (int) info.getLongVersionCode() : info.versionCode;
		}
		catch (final PackageManager.NameNotFoundException e) {
			return 0;
		}
	}

	/**
	 * The art of the skin the launcher last prepared, or null before the data folder is chosen and prepared (or when
	 * anything about it cannot be read).
	 */
	private SkinCache.Art loadArt() {
		try {
			final File ini = SkinCache.iniFile(this.activity);
			if (!ini.isFile()) {
				return null;
			}
			final String chosen = this.activity.getSharedPreferences(LauncherKeys.PREFERENCES, Context.MODE_PRIVATE)
					.getString(LauncherKeys.KEY_SKIN, SkinArt.CLASSIC_SKIN);
			final String skin = SkinArt.REFORGED_SKIN.equals(chosen) && SkinCache.hasReforged(this.activity)
					? SkinArt.REFORGED_SKIN
					: SkinArt.CLASSIC_SKIN;
			final SkinCache cache = SkinCache.forConfiguration(this.activity, AndroidLauncher.loadWarsmashIni(ini), skin);
			return (cache != null) && cache.isCurrent() ? new SkinCache.Art(cache) : null;
		}
		catch (final IOException | RuntimeException e) {
			Log.w(TAG, "The launcher art is not available for the update prompt", e);
			return null;
		}
	}

	private static File updateFolder(final Context context) {
		return new File(context.getCacheDir(), "update");
	}

	/** Deletes the downloads and parts of other releases, or all of them when there is nothing to offer. */
	private static void cleanUp(final Context context, final String keep) {
		final File[] files = updateFolder(context).listFiles();
		if (files != null) {
			for (final File file : files) {
				if ((keep == null) || !file.getName().startsWith(keep)) {
					file.delete();
				}
			}
		}
	}

	/** Shows the window's content and waits for the check; the window goes on through {@code proceed}. */
	void show() {
		final DisplayMetrics metrics = new DisplayMetrics();
		this.activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
		final float screenW = Math.max(metrics.widthPixels, metrics.heightPixels);
		final float screenH = Math.min(metrics.widthPixels, metrics.heightPixels);
		this.tablet = (screenW / screenH) < 1.9f;
		this.scale = Math.min(screenW / (this.tablet ? 2960 : 1600), screenH / (this.tablet ? 1848 : 720));
		this.art = loadArt();
		final SkinArt.Palette palette = SkinArt.Palette.of((this.art != null) && this.art.reforged());
		this.gold = palette.gold;
		this.text = palette.text;
		this.textDim = palette.text2;
		this.line = palette.line;
		this.root = build(screenW);
		this.activity.setContentView(this.root);
		hideSystemBars();
		attach();
		this.shown = null;
		poll();
	}

	@SuppressWarnings("deprecation")
	private void hideSystemBars() {
		this.root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
				| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
				| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
	}

	void detach() {
		this.handler.removeCallbacks(this.poll);
		if (this.receiving) {
			this.receiving = false;
			try {
				this.activity.unregisterReceiver(this.receiver);
			}
			catch (final IllegalArgumentException e) {
				Log.w(TAG, "Update receiver was not registered", e);
			}
		}
	}

	private void attach() {
		if (this.receiving) {
			return;
		}
		final IntentFilter filter = new IntentFilter(ACTION_RESULT);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			this.activity.registerReceiver(this.receiver, filter, Context.RECEIVER_NOT_EXPORTED);
		}
		else {
			this.activity.registerReceiver(this.receiver, filter);
		}
		this.receiving = true;
	}

	private void poll() {
		if (this.activity.isFinishing()) {
			return;
		}
		if (state == State.CHECKING) {
			final boolean late = (System.currentTimeMillis() - startedAt) > CHECK_WAIT_MILLIS;
			if (checked || late) {
				final UpdateCheck.Release latest = checked ? release : null;
				final SharedPreferences preferences = this.activity.getSharedPreferences(LauncherKeys.PREFERENCES,
						Context.MODE_PRIVATE);
				if (UpdateCheck.offers(installedCode(this.activity), latest,
						preferences.getString(LauncherKeys.KEY_UPDATE_SKIPPED, ""), BuildInfo.current().isOfficial())) {
					cleanUp(this.activity, latest.apkName);
					state = State.OFFER;
				}
				else {
					if (checked) {
						cleanUp(this.activity, null);
					}
					finish();
					return;
				}
			}
		}
		// Back from Android's settings with the permission given, the install goes on.
		if ((state == State.PERMISSION) && this.activity.hasWindowFocus() && canInstall()) {
			install();
		}
		if ((this.shown != state) || (this.shownBytes != bytes)) {
			render();
		}
		this.handler.postDelayed(this.poll, POLL_MILLIS);
	}

	private void finish() {
		synchronized (LOCK) {
			state = State.DONE;
		}
		detach();
		this.proceed.run();
	}

	private int px(final float design) {
		return Math.round(design * this.scale);
	}

	private int line(final float design) {
		return Math.max(1, px(design));
	}

	private TextView text(final float size, final int color, final Typeface face) {
		final TextView view = new TextView(this.activity);
		view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * this.scale);
		view.setTextColor(color);
		view.setTypeface(this.art != null ? this.art.font() : face);
		view.setIncludeFontPadding(false);
		return view;
	}

	private TextView button() {
		final float height = px(this.tablet ? 112 : 96);
		final float size = (this.tablet ? 36 : 28) * this.scale;
		if (this.art != null) {
			return new LauncherKit.GlueButton(this.activity, this.art, height, size);
		}
		final Button button = new Button(this.activity);
		button.setAllCaps(false);
		button.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
		button.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
		button.setLetterSpacing(0.04f);
		button.setStateListAnimator(null);
		button.setPadding(0, 0, 0, 0);
		button.setTextColor(this.gold);
		return button;
	}

	private void style(final TextView button, final boolean next) {
		if (button instanceof LauncherKit.GlueButton) {
			final LauncherKit.GlueButton glue = (LauncherKit.GlueButton) button;
			glue.setTone(next ? LauncherKit.GlueButton.TONE_CONFIRM : LauncherKit.GlueButton.TONE_PLAIN);
			glue.setMain(next);
			return;
		}
		final GradientDrawable drawable = next
				? new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
						new int[] { Color.rgb(0x14, 0x23, 0x9A), Color.rgb(0x00, 0x00, 0x39) })
				: new GradientDrawable();
		if (!next) {
			drawable.setColor(Color.rgb(0x05, 0x06, 0x0C));
		}
		drawable.setStroke(line(3), next ? Color.rgb(0x2B, 0x3F, 0xBF) : this.line);
		button.setBackground(drawable);
	}

	private FrameLayout build(final float screenW) {
		final FrameLayout view = new FrameLayout(this.activity);
		final GradientDrawable background = new GradientDrawable();
		background.setGradientType(GradientDrawable.RADIAL_GRADIENT);
		background.setColors(new int[] { Color.rgb(0x12, 0x1A, 0x3A), Color.rgb(0x07, 0x0A, 0x18),
			Color.rgb(0x03, 0x04, 0x09) });
		background.setGradientRadius(screenW * 0.75f);
		background.setGradientCenter(0.5f, 0.3f);
		view.setBackground(background);

		final LinearLayout panel = new LinearLayout(this.activity);
		panel.setOrientation(LinearLayout.VERTICAL);
		final float edge = px(this.tablet ? 40 : 28);
		panel.setBackground(new LauncherKit.FrameDrawable(this.art != null ? this.art.bitmap(SkinArt.DIALOG_BORDER) : null, edge)
				.tiled(this.art != null ? this.art.bitmap(SkinArt.DIALOG_BACKGROUND) : null, px(this.tablet ? 256 : 128))
				.fillInset(edge * 0.4f).fallback(Color.argb(0xEB, 0x03, 0x04, 0x0A)).line(this.line));
		final int pad = px(this.tablet ? 96 : 40);
		panel.setPadding(pad, px(this.tablet ? 72 : 28), pad, px(this.tablet ? 72 : 28));
		view.addView(panel, new FrameLayout.LayoutParams(px(this.tablet ? 1840 : 1568), ViewGroup.LayoutParams.WRAP_CONTENT,
				Gravity.CENTER));

		this.title = text(this.tablet ? 60 : 40, this.gold, Typeface.create(Typeface.SERIF, Typeface.NORMAL));
		this.title.setLetterSpacing(0.06f);
		panel.addView(this.title);
		this.detail = text(this.tablet ? 38 : 26, this.text, Typeface.SANS_SERIF);
		this.detail.setLineSpacing(0, 1.15f);
		final LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		detailParams.topMargin = px(this.tablet ? 40 : 18);
		panel.addView(this.detail, detailParams);
		this.progressBar = new ProgressBar(this.activity, null, android.R.attr.progressBarStyleHorizontal);
		this.progressBar.setMax(1000);
		this.progressBar.setProgressTintList(ColorStateList.valueOf(this.gold));
		this.progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(0x46, 0x41, 0x2D)));
		final LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		progressParams.topMargin = px(this.tablet ? 28 : 14);
		panel.addView(this.progressBar, progressParams);

		final LinearLayout buttons = new LinearLayout(this.activity);
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		final LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		buttonsParams.topMargin = px(this.tablet ? 56 : 26);
		panel.addView(buttons, buttonsParams);
		this.first = button();
		this.second = button();
		this.third = button();
		for (final TextView button : new TextView[] { this.first, this.second, this.third }) {
			final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, px(this.tablet ? 112 : 96), 1);
			params.rightMargin = px(this.tablet ? 32 : 24);
			buttons.addView(button, params);
		}
		return view;
	}

	private void setButton(final TextView button, final int label, final boolean next, final Runnable action) {
		if (label == 0) {
			button.setVisibility(View.GONE);
			return;
		}
		button.setVisibility(View.VISIBLE);
		button.setText(label);
		style(button, next);
		button.setOnClickListener(v -> action.run());
	}

	private void render() {
		final State now = state;
		this.shown = now;
		this.shownBytes = bytes;
		final UpdateCheck.Release latest = release;
		final String size = latest != null ? Formatter.formatShortFileSize(this.activity, latest.apkSize) : "";
		this.progressBar.setVisibility(View.GONE);
		this.title.setTextColor(this.gold);
		this.detail.setTextColor(this.text);
		switch (now) {
		case OFFER:
			this.title.setText(R.string.update_title);
			this.detail.setText(this.activity.getString(
					latest.hasApk() ? (BuildInfo.current().isOfficial() ? R.string.update_offer : R.string.update_offer_unofficial)
							: R.string.update_offer_page, latest.tag,
					installedVersion(this.activity), size));
			setButton(this.first, latest.hasApk() ? R.string.update_now : R.string.update_open_page, true,
					latest.hasApk() ? this::download : this::openPage);
			setButton(this.second, R.string.update_later, false, this::finish);
			setButton(this.third, R.string.update_skip, false, this::skip);
			break;
		case DOWNLOADING:
			this.title.setText(this.activity.getString(R.string.update_downloading, latest.tag));
			this.detail.setText(this.activity.getString(R.string.update_progress,
					Formatter.formatShortFileSize(this.activity, bytes), size));
			this.progressBar.setVisibility(View.VISIBLE);
			this.progressBar.setProgress(latest.apkSize > 0 ? (int) ((bytes * 1000) / latest.apkSize) : 0);
			setButton(this.first, R.string.update_cancel, false, () -> cancelled = true);
			setButton(this.second, 0, false, null);
			setButton(this.third, 0, false, null);
			break;
		case PERMISSION:
			this.title.setText(R.string.update_permission_title);
			this.detail.setText(R.string.update_permission_detail);
			setButton(this.first, R.string.update_allow, true, this::requestPermission);
			setButton(this.second, R.string.update_later, false, this::finish);
			setButton(this.third, 0, false, null);
			break;
		case MANUAL:
			this.title.setText(R.string.update_manual_title);
			this.detail.setText(R.string.update_manual_detail);
			setButton(this.first, R.string.update_open_page, true, this::openPage);
			setButton(this.second, R.string.update_continue, false, this::finish);
			setButton(this.third, 0, false, null);
			break;
		case FAILED:
			this.title.setText(R.string.update_failed_title);
			this.title.setTextColor(RED);
			this.detail.setText(this.activity.getString(R.string.update_failed_detail, String.valueOf(problem)));
			this.detail.setTextColor(this.textDim);
			setButton(this.first, R.string.update_retry, true, this::download);
			setButton(this.second, R.string.update_continue, false, this::finish);
			setButton(this.third, 0, false, null);
			break;
		case INSTALLING:
			this.title.setText(R.string.update_installing);
			this.detail.setText(R.string.update_installing_detail);
			setButton(this.first, 0, false, null);
			setButton(this.second, 0, false, null);
			setButton(this.third, 0, false, null);
			break;
		default:
			this.title.setText(R.string.update_checking);
			this.detail.setText("");
			setButton(this.first, 0, false, null);
			setButton(this.second, 0, false, null);
			setButton(this.third, 0, false, null);
			break;
		}
	}

	private void skip() {
		this.activity.getSharedPreferences(LauncherKeys.PREFERENCES, Context.MODE_PRIVATE).edit()
				.putString(LauncherKeys.KEY_UPDATE_SKIPPED, release.tag).apply();
		finish();
	}

	private void openPage() {
		final UpdateCheck.Release latest = release;
		try {
			this.activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(latest.pageUrl)));
		}
		catch (final RuntimeException e) {
			Log.w(TAG, "Unable to open the release page", e);
		}
	}

	private void download() {
		if (downloading) {
			return;
		}
		final UpdateCheck.Release latest = release;
		cancelled = false;
		bytes = 0;
		downloading = true;
		state = State.DOWNLOADING;
		final File target = new File(updateFolder(this.activity), latest.apkName);
		final Context context = this.activity.getApplicationContext();
		final Thread thread = new Thread(() -> {
			final String failure = UpdateCheck.download(latest, target, new UpdateCheck.Progress() {
				@Override
				public void update(final long done, final long total) {
					bytes = done;
				}

				@Override
				public boolean cancelled() {
					return cancelled;
				}
			});
			downloading = false;
			if (failure == null) {
				apk = target;
				state = verify(context, target);
			}
			else if (failure.isEmpty()) {
				state = State.OFFER;
			}
			else {
				problem = failure;
				state = State.FAILED;
			}
		}, "UpdateDownload");
		thread.setDaemon(true);
		thread.start();
	}

	/** The signature of the downloaded APK against the installed app's; a debug build cannot take a release. */
	private static State verify(final Context context, final File file) {
		try {
			if (!sameSigner(context, file)) {
				cleanUp(context, null);
				return State.MANUAL;
			}
		}
		catch (final RuntimeException | PackageManager.NameNotFoundException e) {
			Log.w(TAG, "Unable to read the signatures of the update", e);
			problem = "The downloaded file is not a usable APK.";
			file.delete();
			return State.FAILED;
		}
		return State.PERMISSION;
	}

	@SuppressWarnings("deprecation")
	private static Set<Signature> signers(final PackageInfo info) {
		final Set<Signature> set = new HashSet<>();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			if (info.signingInfo != null) {
				set.addAll(Arrays.asList(info.signingInfo.getApkContentsSigners()));
			}
		}
		else if (info.signatures != null) {
			set.addAll(Arrays.asList(info.signatures));
		}
		return set;
	}

	@SuppressWarnings("deprecation")
	private static boolean sameSigner(final Context context, final File file)
			throws PackageManager.NameNotFoundException {
		final PackageManager packages = context.getPackageManager();
		final int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? PackageManager.GET_SIGNING_CERTIFICATES
				: PackageManager.GET_SIGNATURES;
		final PackageInfo installed = packages.getPackageInfo(context.getPackageName(), flags);
		final PackageInfo downloaded = packages.getPackageArchiveInfo(file.getAbsolutePath(), flags);
		if ((downloaded == null) || !context.getPackageName().equals(downloaded.packageName)) {
			throw new IllegalStateException("The file is not this app's APK");
		}
		final Set<Signature> mine = signers(installed);
		return !mine.isEmpty() && mine.equals(signers(downloaded));
	}

	private boolean canInstall() {
		return (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
				|| this.activity.getPackageManager().canRequestPackageInstalls();
	}

	private void requestPermission() {
		try {
			this.activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
					Uri.parse("package:" + this.activity.getPackageName())));
		}
		catch (final RuntimeException e) {
			Log.w(TAG, "Unable to open the install permission settings", e);
			problem = String.valueOf(e.getMessage());
			state = State.FAILED;
		}
	}

	/** Writes the APK into an install session; Android then asks the player to confirm. */
	private void install() {
		final Context context = this.activity.getApplicationContext();
		final File file = apk;
		state = State.INSTALLING;
		final Thread thread = new Thread(() -> {
			try {
				final PackageInstaller installer = context.getPackageManager().getPackageInstaller();
				final PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
						PackageInstaller.SessionParams.MODE_FULL_INSTALL);
				params.setAppPackageName(context.getPackageName());
				final int id = installer.createSession(params);
				try (PackageInstaller.Session session = installer.openSession(id)) {
					try (InputStream in = new FileInputStream(file);
							OutputStream out = session.openWrite("update.apk", 0, file.length())) {
						final byte[] buffer = new byte[64 * 1024];
						int read;
						while ((read = in.read(buffer)) != -1) {
							out.write(buffer, 0, read);
						}
						session.fsync(out);
					}
					final Intent intent = new Intent(ACTION_RESULT).setPackage(context.getPackageName());
					final int flags = PendingIntent.FLAG_UPDATE_CURRENT
							| (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
					session.commit(PendingIntent.getBroadcast(context, id, intent, flags).getIntentSender());
				}
				catch (final IOException | RuntimeException e) {
					installer.abandonSession(id);
					throw e;
				}
			}
			catch (final IOException | RuntimeException e) {
				Log.w(TAG, "Unable to install the update", e);
				problem = String.valueOf(e.getMessage());
				state = State.FAILED;
			}
		}, "UpdateInstall");
		thread.setDaemon(true);
		thread.start();
	}

	private void onInstallResult(final Intent intent) {
		final int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
		if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
			final Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
			if (confirm != null) {
				confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				this.activity.startActivity(confirm);
			}
		}
		else if (status == PackageInstaller.STATUS_SUCCESS) {
			state = State.INSTALLING;
		}
		else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
			state = State.OFFER;
		}
		else {
			problem = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
			state = State.FAILED;
		}
	}
}
