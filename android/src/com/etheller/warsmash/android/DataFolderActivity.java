package com.etheller.warsmash.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.etheller.warsmash.launcher.DataFolderCheck;
import com.etheller.warsmash.launcher.DataPackage;

/**
 * Lets the player choose the folder with the game data before the engine starts, and writes the engine configuration
 * for it. Once a saved folder holds valid data the window is skipped and the launcher opens at once; the "Game data"
 * app shortcut (or the {@link #EXTRA_OPEN} extra) opens it again.
 */
public class DataFolderActivity extends Activity {
	static final String EXTRA_OPEN = "openLauncher";
	private static final String TAG = "Warsmash";
	private static final String PREFERENCES = "launcher";
	private static final String KEY_DATA_FOLDER = "dataFolder";
	private static final String INI_TEMPLATE = "warsmash-template.ini";
	private static final int REQUEST_FOLDER = 1;
	private static final int REQUEST_ZIP = 2;
	/** The unreadable files named in the status; the count is always whole. */
	private static final int UNREADABLE_NAMES = 3;
	/** A chosen folder as large as a whole storage is not walked to its end. */
	private static final int MAX_WALKED_FILES = 200_000;
	private static final long PROGRESS_MILLIS = 250;

	private static final int GOLD = Color.rgb(0xE8, 0xC7, 0x6A);
	private static final int BRONZE = Color.rgb(0x73, 0x59, 0x0A);
	private static final int BRONZE_INNER = Color.rgb(0x46, 0x41, 0x2D);
	private static final int TEXT = Color.rgb(0xEC, 0xE3, 0xCF);
	private static final int TEXT_DIM = Color.rgb(0xA9, 0xA1, 0x90);
	private static final int DISABLED = Color.rgb(0x5C, 0x5A, 0x52);
	private static final int GREEN = Color.rgb(0x8F, 0xD4, 0x9E);
	private static final int RED = Color.rgb(0xE2, 0x9A, 0x9A);

	private enum Status {
		WAITING, CHECKING, FOUND, FOUND_UNREADABLE, MISSING, NEEDS_ACCESS, GONE, UNREADABLE, WRITE_FAILED, IMPORTING,
		IMPORT_FAILED
	}

	private enum Look {
		NEXT, NORMAL, DISABLED, IN_USE
	}

	/**
	 * Gate layout in design pixels: the tablet board is 2960 x 1848, the phone board 1600 x 720; the panel keeps these
	 * proportions on any screen.
	 */
	private static final class Board {
		final float width, height;
		final float panelW, panelH, inner;
		final float logo, logoY, titleGap, title;
		final float fieldY, fieldH, path;
		final float halfY, buttonH, half, gap, buttonText;
		final float statusY, statusH, statusPad, icon, iconGap, statusTitle, statusDetail, action;
		final float bottomY;

		Board(final boolean tablet) {
			if (tablet) {
				this.width = 2960;
				this.height = 1848;
				this.panelW = 1840;
				this.panelH = 1120;
				this.inner = 96;
				this.logo = 120;
				this.logoY = 64;
				this.titleGap = 40;
				this.title = 60;
				this.fieldY = 256;
				this.fieldH = 104;
				this.path = 34;
				this.halfY = 392;
				this.buttonH = 112;
				this.half = 808;
				this.gap = 32;
				this.buttonText = 36;
				this.statusY = 544;
				this.statusH = 232;
				this.statusPad = 38;
				this.icon = 88;
				this.iconGap = 36;
				this.statusTitle = 40;
				this.statusDetail = 28;
				this.action = 560;
				this.bottomY = 944;
			}
			else {
				this.width = 1600;
				this.height = 720;
				this.panelW = 1568;
				this.panelH = 688;
				this.inner = 40;
				this.logo = 64;
				this.logoY = 28;
				this.titleGap = 24;
				this.title = 40;
				this.fieldY = 140;
				this.fieldH = 80;
				this.path = 24;
				this.halfY = 236;
				this.buttonH = 96;
				this.half = 732;
				this.gap = 24;
				this.buttonText = 28;
				this.statusY = 348;
				this.statusH = 156;
				this.statusPad = 22;
				this.icon = 60;
				this.iconGap = 20;
				this.statusTitle = 28;
				this.statusDetail = 19;
				this.action = 420;
				this.bottomY = 568;
			}
		}
	}

	/**
	 * An import of a data package into the app folder. It runs on its own thread and outlives the window, which Android
	 * makes again when the screen turns over; the window that shows reads its progress.
	 */
	private static final class ImportJob implements DataPackage.Progress {
		final String name;
		volatile long bytes;
		volatile long totalBytes;
		volatile int files;
		volatile int totalFiles;
		volatile boolean opened;
		volatile boolean cancelled;
		volatile boolean finished;
		volatile DataPackage.ImportException failure;
		/** What the package says of itself, once it is read. */
		volatile String summary;

		ImportJob(final String name) {
			this.name = name;
		}

		@Override
		public void update(final long bytes, final long totalBytes, final int files, final int totalFiles) {
			this.bytes = bytes;
			this.totalBytes = totalBytes;
			this.files = files;
			this.totalFiles = totalFiles;
		}
	}

	/** The package is read through a source; a picked document is opened again on the import's thread. */
	private interface PackageSource {
		DataPackage open() throws IOException;
	}

	private static volatile ImportJob importJob;

	private final Handler handler = new Handler(Looper.getMainLooper());
	private final Runnable importWatch = this::watchImport;
	private UpdatePrompt updatePrompt;
	private File dataFolder;
	private File chosenFolder;
	private boolean writeFailed;
	private float scale;
	private TextView folderField;
	private Button chooseButton;
	private LinearLayout statusBox;
	private TextView statusIcon;
	private TextView statusText;
	private TextView statusDetail;
	private Button accessButton;
	private Button appFolderButton;
	private Button continueButton;
	private DataFolderCheck.Kind kind = DataFolderCheck.Kind.NONE;
	private int checks;
	private boolean appHasData;
	private String zip;
	/** A data package lying in the chosen folder, which Import ZIP takes without asking. */
	private File packageZip;
	private boolean unreadable;
	/** The files of the chosen folder the game cannot read, when it could be walked. */
	private DataFolderCheck.Readability readability;
	private String description;
	private Button importButton;
	private Button cancelButton;
	private ProgressBar progressBar;
	/** Why the last import stopped, shown until the next choice. */
	private String importProblem;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (AndroidLauncher.showRunningGame(this)) {
			finish();
			return;
		}
		// The window of a map asked for by name or opened from the launcher goes on without the release check.
		final boolean direct = getIntent().getBooleanExtra(EXTRA_OPEN, false)
				|| getIntent().hasExtra(AndroidLauncher.EXTRA_LOAD_FILE);
		if (!direct && UpdatePrompt.pending(this)) {
			this.updatePrompt = new UpdatePrompt(this, this::proceed);
			this.updatePrompt.show();
			return;
		}
		proceed();
	}

	private void proceed() {
		final File appData = appDataFolder();
		final String saved = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getString(KEY_DATA_FOLDER, null);
		this.dataFolder = saved != null ? new File(saved) : appData;
		if ((saved == null) && (DataFolderCheck.detect(appData) == DataFolderCheck.Kind.NONE)) {
			this.dataFolder = null;
		}
		this.chosenFolder = this.dataFolder;
		this.appHasData = DataFolderCheck.detect(appData) != DataFolderCheck.Kind.NONE;
		final boolean open = getIntent().getBooleanExtra(EXTRA_OPEN, false);
		if (!open && (importJob == null) && (this.dataFolder != null) && (saved != null)) {
			this.kind = DataFolderCheck.detect(this.dataFolder);
			if ((this.kind != DataFolderCheck.Kind.NONE) && start()) {
				return;
			}
		}
		setContentView(buildLayout());
		refresh();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (this.folderField != null) {
			if (importJob != null) {
				watchImport();
			}
			else {
				refresh();
			}
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (this.updatePrompt != null) {
			this.updatePrompt.detach();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		this.handler.removeCallbacks(this.importWatch);
	}

	@Override
	public void onWindowFocusChanged(final boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && (this.folderField != null)) {
			hideSystemBars();
		}
	}

	@SuppressWarnings("deprecation")
	private void hideSystemBars() {
		getWindow().getDecorView()
				.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
						| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
						| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
	}

	private int px(final float design) {
		return Math.round(design * this.scale);
	}

	private int line(final float design) {
		return Math.max(1, px(design));
	}

	private static GradientDrawable box(final int fill, final int stroke, final int strokeWidth) {
		final GradientDrawable drawable = new GradientDrawable();
		drawable.setColor(fill);
		drawable.setStroke(strokeWidth, stroke);
		return drawable;
	}

	private TextView text(final String value, final float size, final int color, final Typeface face) {
		final TextView view = new TextView(this);
		view.setText(value);
		view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * this.scale);
		view.setTextColor(color);
		view.setTypeface(face);
		view.setIncludeFontPadding(false);
		return view;
	}

	private Button button(final float size, final View.OnClickListener listener) {
		final Button button = new Button(this);
		button.setAllCaps(false);
		button.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * this.scale);
		button.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
		button.setLetterSpacing(0.04f);
		button.setStateListAnimator(null);
		button.setPadding(0, 0, 0, 0);
		button.setOnClickListener(listener);
		return button;
	}

	/** One blue button is always the next step; the others keep their place and only change colour. */
	private void look(final Button button, final Look look) {
		button.setEnabled((look == Look.NEXT) || (look == Look.NORMAL));
		switch (look) {
		case NEXT:
			final GradientDrawable blue = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
					new int[] { Color.rgb(0x14, 0x23, 0x9A), Color.rgb(0x00, 0x00, 0x39) });
			blue.setStroke(line(3), Color.rgb(0x2B, 0x3F, 0xBF));
			button.setBackground(blue);
			button.setTextColor(GOLD);
			break;
		case NORMAL:
			button.setBackground(box(Color.rgb(0x05, 0x06, 0x0C), BRONZE, line(3)));
			button.setTextColor(GOLD);
			break;
		case IN_USE:
			button.setBackground(box(Color.rgb(0x10, 0x24, 0x16), Color.rgb(0x2F, 0x5A, 0x3A), line(3)));
			button.setTextColor(GREEN);
			break;
		default:
			button.setBackground(box(Color.rgb(0x0A, 0x0B, 0x10), Color.rgb(0x2C, 0x2B, 0x27), line(3)));
			button.setTextColor(DISABLED);
			break;
		}
	}

	private void place(final FrameLayout parent, final View view, final float x, final float y, final float w,
			final float h) {
		final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
				w < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : px(w), h < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : px(h));
		params.leftMargin = px(x);
		params.topMargin = px(y);
		parent.addView(view, params);
	}

	private View buildLayout() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
		}
		hideSystemBars();
		// The navigation bar is hidden, so the panel is sized for the whole screen as the launcher's pages are.
		final DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
		final float screenW = Math.max(metrics.widthPixels, metrics.heightPixels);
		final float screenH = Math.min(metrics.widthPixels, metrics.heightPixels);
		// Phones are much wider than they are high; tablets come close to 16:10.
		final Board board = new Board((screenW / screenH) < 1.9f);
		this.scale = Math.min(screenW / board.width, screenH / board.height);

		final FrameLayout root = new FrameLayout(this);
		final GradientDrawable background = new GradientDrawable();
		background.setGradientType(GradientDrawable.RADIAL_GRADIENT);
		background.setColors(new int[] { Color.rgb(0x12, 0x1A, 0x3A), Color.rgb(0x07, 0x0A, 0x18),
			Color.rgb(0x03, 0x04, 0x09) });
		background.setGradientRadius(screenW * 0.75f);
		background.setGradientCenter(0.5f, 0.3f);
		root.setBackground(background);

		final FrameLayout panel = new FrameLayout(this);
		final LayerDrawable frame = new LayerDrawable(new Drawable[] {
			box(Color.argb(0xEB, 0x03, 0x04, 0x0A), BRONZE, line(4)), box(Color.TRANSPARENT, BRONZE_INNER, line(2)) });
		frame.setLayerInset(1, px(10), px(10), px(10), px(10));
		panel.setBackground(frame);
		root.addView(panel, new FrameLayout.LayoutParams(px(board.panelW), px(board.panelH), Gravity.CENTER));

		final float x = board.inner;
		final float w = board.panelW - (2 * board.inner);
		final ImageView logo = new ImageView(this);
		logo.setImageResource(R.mipmap.ic_launcher);
		place(panel, logo, x, board.logoY, board.logo, board.logo);
		final float textX = x + board.logo + board.titleGap;
		final TextView title = text(getString(R.string.data_folder_title), board.title, GOLD,
				Typeface.create(Typeface.SERIF, Typeface.NORMAL));
		title.setLetterSpacing(0.06f);
		title.setGravity(Gravity.CENTER_VERTICAL);
		place(panel, title, textX, board.logoY, w - board.logo - board.titleGap, board.logo);

		this.folderField = text("", board.path, TEXT, Typeface.MONOSPACE);
		this.folderField.setSingleLine(true);
		this.folderField.setEllipsize(TextUtils.TruncateAt.START);
		this.folderField.setGravity(Gravity.CENTER_VERTICAL);
		this.folderField.setPadding(px(board.gap), 0, px(board.gap), 0);
		this.folderField.setBackground(box(Color.rgb(0x0D, 0x10, 0x16), Color.rgb(0x2E, 0x36, 0x44), line(2)));
		this.folderField.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_folder, 0, 0, 0);
		this.folderField.setCompoundDrawablePadding(px(board.gap / 2));
		place(panel, this.folderField, x, board.fieldY, w, board.fieldH);

		this.chooseButton = button(board.buttonText, view -> chooseFolder());
		place(panel, this.chooseButton, x, board.halfY, board.half, board.buttonH);
		// Android 11+ folder pickers do not show Android/data, so the app folder cannot be picked again.
		this.appFolderButton = button(board.buttonText, view -> {
			this.dataFolder = appDataFolder();
			this.chosenFolder = this.dataFolder;
			this.writeFailed = false;
			this.importProblem = null;
			refresh();
		});
		place(panel, this.appFolderButton, x + board.half + board.gap, board.halfY, board.half, board.buttonH);

		this.statusBox = new LinearLayout(this);
		this.statusBox.setOrientation(LinearLayout.HORIZONTAL);
		this.statusBox.setGravity(Gravity.CENTER_VERTICAL);
		this.statusBox.setPadding(px(board.statusPad), 0, px(board.gap), 0);
		this.statusIcon = text("", board.icon * 0.7f, TEXT_DIM, Typeface.DEFAULT_BOLD);
		this.statusIcon.setGravity(Gravity.CENTER);
		this.statusBox.addView(this.statusIcon, new LinearLayout.LayoutParams(px(board.icon), px(board.icon)));
		final LinearLayout lines = new LinearLayout(this);
		lines.setOrientation(LinearLayout.VERTICAL);
		lines.setPadding(px(board.iconGap), 0, px(board.gap), 0);
		this.statusText = text("", board.statusTitle, TEXT, Typeface.create(Typeface.SERIF, Typeface.BOLD));
		lines.addView(this.statusText);
		this.statusDetail = text("", board.statusDetail, TEXT_DIM, Typeface.SANS_SERIF);
		this.statusDetail.setMaxLines(2);
		this.statusDetail.setEllipsize(TextUtils.TruncateAt.END);
		final LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		detailParams.topMargin = px(board.statusDetail / 2);
		lines.addView(this.statusDetail, detailParams);
		this.progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
		this.progressBar.setMax(1000);
		this.progressBar.setProgressTintList(ColorStateList.valueOf(GOLD));
		this.progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(BRONZE_INNER));
		final LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		progressParams.topMargin = px(board.statusDetail / 2);
		lines.addView(this.progressBar, progressParams);
		this.statusBox.addView(lines, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		this.accessButton = button(board.buttonText, view -> requestAllFilesAccess());
		this.accessButton.setText(R.string.data_folder_allow);
		this.statusBox.addView(this.accessButton, new LinearLayout.LayoutParams(px(board.action), px(board.buttonH)));
		this.cancelButton = button(board.buttonText, view -> {
			final ImportJob job = importJob;
			if (job != null) {
				job.cancelled = true;
				watchImport();
			}
		});
		this.cancelButton.setText(R.string.data_folder_import_cancel);
		this.statusBox.addView(this.cancelButton, new LinearLayout.LayoutParams(px(board.action), px(board.buttonH)));
		place(panel, this.statusBox, x, board.statusY, w, board.statusH);

		this.continueButton = button(board.buttonText * 1.12f, view -> {
			if (this.writeFailed || (this.kind != DataFolderCheck.Kind.NONE)) {
				this.writeFailed = false;
				start();
			}
		});
		this.continueButton.setLetterSpacing(0.12f);
		place(panel, this.continueButton, (x + w) - board.action, board.bottomY, board.action, board.buttonH);
		// Android 11+ file managers cannot write into the app folder, so the app unpacks the toolkit's zip itself.
		this.importButton = button(board.buttonText, view -> {
			this.importProblem = null;
			if ((this.packageZip != null) && this.packageZip.canRead()) {
				final File zipFile = this.packageZip;
				startImport(zipFile.getName(), () -> DataPackage.open(zipFile));
			}
			else {
				chooseZip();
			}
		});
		this.importButton.setText(R.string.data_folder_import);
		place(panel, this.importButton, x, board.bottomY, board.action, board.buttonH);
		return root;
	}

	/** Searches the folder off the main thread; a large folder would otherwise freeze the window. */
	private void refresh() {
		final int generation = ++this.checks;
		final File folder = this.dataFolder;
		// Without access the files are hidden, and searching thousands of their folders only stalls the window.
		final boolean search = (folder != null) && folder.isDirectory()
				&& (!needsAllFilesAccess() || isInAppFolder(folder));
		if (search) {
			show(Status.CHECKING);
		}
		new Thread(() -> {
			File found = folder;
			if (search) {
				final File located = DataFolderCheck.locate(folder);
				if (located != null) {
					found = located;
				}
			}
			final DataFolderCheck.Kind foundKind = found == null ? DataFolderCheck.Kind.NONE
					: DataFolderCheck.detect(found);
			final boolean appHasData = DataFolderCheck.detect(appDataFolder()) != DataFolderCheck.Kind.NONE;
			final String zip = foundKind == DataFolderCheck.Kind.NONE ? DataFolderCheck.zipName(found) : null;
			final List<File> packages = zip != null ? DataPackage.packagesIn(found) : null;
			final boolean unreadable = (foundKind == DataFolderCheck.Kind.NONE) && isUnreadable(found);
			// Without access every file of another folder looks unreadable; that case has its own status. A folder
			// found whole before is not walked again each time the window comes back.
			final DataFolderCheck.Readability known = found == null ? null : DataFolderCheck.lastReadability(found);
			final DataFolderCheck.Readability readability = (known != null) && (known.unreadable == 0)
					&& !known.partial ? known
							: (found != null) && found.isDirectory() && !unreadable
									&& (!needsAllFilesAccess() || isInAppFolder(found))
											? DataFolderCheck.readability(found, MAX_WALKED_FILES, UNREADABLE_NAMES)
											: null;
			String description = null;
			if (foundKind != DataFolderCheck.Kind.NONE) {
				description = DataFolderCheck.describe(foundKind, found) + " · "
						+ DataFolderCheck.checkedFile(foundKind);
				final String locale = DataFolderCheck.locale(foundKind, found);
				if (!locale.isEmpty()) {
					description += " · " + locale;
				}
			}
			final File result = found;
			final String details = description;
			runOnUiThread(() -> {
				if ((generation != this.checks) || isFinishing()) {
					return;
				}
				this.dataFolder = result;
				this.kind = foundKind;
				this.appHasData = appHasData;
				this.zip = zip;
				this.packageZip = (packages != null) && !packages.isEmpty() ? packages.get(0) : null;
				this.unreadable = unreadable;
				this.readability = readability;
				this.description = details;
				show(null);
			});
		}, "DataFolderCheck").start();
	}

	private void show(final Status checking) {
		final Status status;
		final ImportJob job = importJob;
		if (job != null) {
			status = Status.IMPORTING;
		}
		else if (checking != null) {
			status = checking;
		}
		else if (this.importProblem != null) {
			status = Status.IMPORT_FAILED;
		}
		else if (this.dataFolder == null) {
			status = Status.WAITING;
		}
		else if (this.writeFailed) {
			status = Status.WRITE_FAILED;
		}
		else if (this.kind != DataFolderCheck.Kind.NONE) {
			status = hasUnreadableFiles() ? Status.FOUND_UNREADABLE : Status.FOUND;
		}
		else if (needsAllFilesAccess() && !isInAppFolder(this.dataFolder)) {
			status = Status.NEEDS_ACCESS;
		}
		else if (!this.dataFolder.exists()) {
			status = Status.GONE;
		}
		else if (this.unreadable || hasUnreadableFiles()) {
			status = Status.UNREADABLE;
		}
		else {
			status = Status.MISSING;
		}
		showFolder();
		final boolean found = (status == Status.FOUND) || (status == Status.FOUND_UNREADABLE);
		final boolean importing = status == Status.IMPORTING;
		final boolean appInUse = found && isInAppFolder(this.dataFolder);
		// A package in the folder, or files the game cannot read, make the import the next step.
		final boolean importNext = (this.packageZip != null) || (status == Status.UNREADABLE)
				|| (status == Status.FOUND_UNREADABLE) || (status == Status.IMPORT_FAILED);
		this.chooseButton.setText(this.dataFolder == null ? R.string.data_folder_choose : R.string.data_folder_change);
		look(this.chooseButton, importing ? Look.DISABLED
				: !importNext && ((status == Status.WAITING) || (status == Status.MISSING) || (status == Status.GONE))
						? Look.NEXT
						: Look.NORMAL);
		this.appFolderButton.setText(appInUse ? R.string.data_folder_app_in_use
				: this.appHasData ? R.string.data_folder_use_app : R.string.data_folder_use_app_empty);
		look(this.appFolderButton, importing ? Look.DISABLED
				: appInUse ? Look.IN_USE : this.appHasData ? Look.NORMAL : Look.DISABLED);
		this.accessButton.setVisibility(status == Status.NEEDS_ACCESS ? View.VISIBLE : View.GONE);
		look(this.accessButton, Look.NEXT);
		this.cancelButton.setVisibility(importing ? View.VISIBLE : View.GONE);
		look(this.cancelButton, (job != null) && job.cancelled ? Look.DISABLED : Look.NORMAL);
		this.progressBar.setVisibility(importing ? View.VISIBLE : View.GONE);
		look(this.importButton, importing ? Look.DISABLED : importNext ? Look.NEXT : Look.NORMAL);
		this.continueButton.setText(status == Status.WRITE_FAILED ? R.string.data_folder_retry
				: R.string.data_folder_continue);
		// A stopped import keeps the data that was there, which the game can still go on with.
		final boolean usable = found || ((status == Status.IMPORT_FAILED) && (this.kind != DataFolderCheck.Kind.NONE));
		look(this.continueButton,
				usable || (status == Status.WRITE_FAILED) ? (importNext ? Look.NORMAL : Look.NEXT) : Look.DISABLED);

		final int failFill = Color.rgb(0x2A, 0x14, 0x14);
		final int failStroke = Color.rgb(0x6A, 0x2E, 0x2E);
		switch (status) {
		case CHECKING:
			showStatus("↻", TEXT_DIM, Color.rgb(0x0B, 0x12, 0x30), Color.rgb(0x2B, 0x3F, 0xBF),
					getString(R.string.data_folder_checking), getString(R.string.data_folder_checking_detail));
			break;
		case FOUND:
			showStatus("✓", GREEN, Color.rgb(0x10, 0x22, 0x16), Color.rgb(0x2F, 0x5A, 0x3A),
					getString(this.kind == DataFolderCheck.Kind.CASC ? R.string.data_folder_casc
							: R.string.data_folder_extracted),
					this.description);
			break;
		case FOUND_UNREADABLE:
			showStatus("!", GOLD, Color.rgb(0x2A, 0x20, 0x12), Color.rgb(0x6A, 0x54, 0x26),
					getString(R.string.data_folder_found_unreadable, this.readability.unreadable),
					getString(R.string.data_folder_found_unreadable_detail, this.description,
							this.readability.firstNames()));
			break;
		case IMPORTING:
			showImport(job);
			break;
		case IMPORT_FAILED:
			showStatus("✗", RED, failFill, failStroke, getString(R.string.data_folder_import_failed),
					this.importProblem);
			break;
		case NEEDS_ACCESS:
			showStatus("!", GOLD, Color.rgb(0x2A, 0x20, 0x12), Color.rgb(0x6A, 0x54, 0x26),
					getString(R.string.data_folder_needs_access), getString(R.string.data_folder_needs_access_detail));
			break;
		case MISSING:
			showStatus("✗", RED, failFill, failStroke, getString(R.string.data_folder_missing),
					this.packageZip != null ? getString(R.string.data_folder_zip_package, this.packageZip.getName())
							: this.zip != null ? getString(R.string.data_folder_zip, this.zip)
									: getString(R.string.data_folder_missing_detail));
			break;
		case GONE:
			showStatus("✗", RED, failFill, failStroke, getString(R.string.data_folder_gone),
					getString(R.string.data_folder_gone_detail));
			break;
		case UNREADABLE:
			if (hasUnreadableFiles()) {
				showStatus("✗", RED, failFill, failStroke,
						getString(this.readability.partial ? R.string.data_folder_unreadable_files_more
								: R.string.data_folder_unreadable_files, this.readability.unreadable),
						getString(R.string.data_folder_unreadable_files_detail, this.readability.firstNames()));
				break;
			}
			showStatus("✗", RED, failFill, failStroke, getString(R.string.data_folder_unreadable),
					getString(R.string.data_folder_unreadable_detail));
			break;
		case WRITE_FAILED:
			showStatus("✗", RED, failFill, failStroke, getString(R.string.data_folder_write_failed),
					getString(R.string.data_folder_write_failed_detail));
			break;
		default:
			showStatus("○", TEXT_DIM, Color.rgb(0x0B, 0x12, 0x30), Color.rgb(0x2B, 0x3F, 0xBF),
					getString(R.string.data_folder_waiting), getString(R.string.data_folder_waiting_detail));
			break;
		}
	}

	private boolean hasUnreadableFiles() {
		return (this.readability != null) && (this.readability.unreadable > 0);
	}

	private void showImport(final ImportJob job) {
		final String detail;
		final int permille;
		if (job.cancelled) {
			detail = getString(R.string.data_folder_import_cancelling);
			permille = this.progressBar.getProgress();
		}
		else if (!job.opened) {
			detail = getString(R.string.data_folder_importing_open);
			permille = 0;
		}
		else if ((job.totalFiles > 0) && (job.files >= job.totalFiles)) {
			detail = getString(R.string.data_folder_importing_check);
			permille = 1000;
		}
		else {
			permille = job.totalBytes > 0 ? (int) ((job.bytes * 1000) / job.totalBytes) : 0;
			detail = getString(R.string.data_folder_importing_detail, permille / 10,
					Formatter.formatShortFileSize(this, job.bytes), Formatter.formatShortFileSize(this, job.totalBytes),
					job.files, job.totalFiles);
		}
		this.progressBar.setProgress(permille);
		showStatus("↻", GOLD, Color.rgb(0x0B, 0x12, 0x30), Color.rgb(0x2B, 0x3F, 0xBF),
				getString(R.string.data_folder_importing), job.summary != null ? job.summary + "\n" + detail : detail);
	}

	private void chooseZip() {
		final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		// File managers name zips with any of these.
		intent.putExtra(Intent.EXTRA_MIME_TYPES,
				new String[] { "application/zip", "application/x-zip-compressed", "application/octet-stream" });
		startActivityForResult(intent, REQUEST_ZIP);
	}

	private String displayName(final Uri uri) {
		try (Cursor cursor = getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null,
				null, null)) {
			if ((cursor != null) && cursor.moveToFirst() && !cursor.isNull(0)) {
				return cursor.getString(0);
			}
		}
		catch (final RuntimeException e) {
			Log.w(TAG, "No name for " + uri, e);
		}
		return uri.getLastPathSegment();
	}

	/**
	 * The zip is read in place through the picked document; its file table lies at its end, so the document must be a
	 * file that can be read at any position, which a file in the device storage is.
	 */
	private void importDocument(final Uri uri) {
		final String name = displayName(uri);
		final Context app = getApplicationContext();
		startImport(name, () -> {
			final ParcelFileDescriptor descriptor = app.getContentResolver().openFileDescriptor(uri, "r");
			if (descriptor == null) {
				throw new DataPackage.ImportException(DataPackage.Problem.IO, name);
			}
			if (descriptor.getStatSize() < 0) {
				descriptor.close();
				throw new UnseekableException();
			}
			final FileChannel channel = new ParcelFileDescriptor.AutoCloseInputStream(descriptor).getChannel();
			return DataPackage.open(channel);
		});
	}

	/** A picked document that is a stream rather than a file (a cloud drive's, for one). */
	private static final class UnseekableException extends IOException {
		private static final long serialVersionUID = 1L;
	}

	private void startImport(final String name, final PackageSource source) {
		if (importJob != null) {
			return;
		}
		final ImportJob job = new ImportJob(name);
		importJob = job;
		final File target = appDataFolder();
		// The thread outlives this window; it keeps only the application.
		final Context app = getApplicationContext();
		new Thread(() -> {
			try (DataPackage dataPackage = source.open()) {
				job.summary = dataPackage.graphicsSet != null
						? app.getString(R.string.data_folder_import_set, name, dataPackage.setName(),
								dataPackage.fileCount(), Formatter.formatShortFileSize(app, dataPackage.totalBytes))
						: app.getString(R.string.data_folder_import_package, name,
								dataPackage.gameVersion.isEmpty() ? "?" : dataPackage.gameVersion,
								dataPackage.fileCount(), Formatter.formatShortFileSize(app, dataPackage.totalBytes));
				job.update(0, dataPackage.totalBytes, 0, dataPackage.fileCount());
				job.opened = true;
				dataPackage.importInto(target, job, () -> job.cancelled);
				Log.i(TAG, "Game data imported from " + name + ": " + dataPackage.fileCount() + " files");
			}
			catch (final DataPackage.ImportException e) {
				Log.w(TAG, "Game data import from " + name + " stopped", e);
				job.failure = e;
			}
			catch (final UnseekableException e) {
				job.failure = new DataPackage.ImportException(DataPackage.Problem.IO, "", e);
			}
			catch (final IOException | RuntimeException e) {
				Log.w(TAG, "Game data import from " + name + " failed", e);
				job.failure = new DataPackage.ImportException(DataPackage.Problem.NOT_ZIP, "", e);
			}
			finally {
				job.finished = true;
			}
		}, "DataImport").start();
		watchImport();
	}

	/** Shows the import's progress while it runs and its outcome once it ends. */
	private void watchImport() {
		this.handler.removeCallbacks(this.importWatch);
		final ImportJob job = importJob;
		if ((job == null) || (this.folderField == null)) {
			return;
		}
		if (!job.finished) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			show(null);
			this.handler.postDelayed(this.importWatch, PROGRESS_MILLIS);
			return;
		}
		importJob = null;
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		this.importProblem = job.failure == null ? null : problemText(job.name, job.failure);
		if (job.failure == null) {
			this.dataFolder = appDataFolder();
			this.chosenFolder = this.dataFolder;
			this.writeFailed = false;
		}
		refresh();
	}

	private String problemText(final String name, final DataPackage.ImportException failure) {
		switch (failure.problem) {
		case NO_MARKER:
			return getString(R.string.data_folder_import_no_marker, name);
		case INCOMPLETE:
			return getString(R.string.data_folder_import_incomplete, name, failure.file);
		case UNSAFE_PATH:
			return getString(R.string.data_folder_import_unsafe, name, failure.file);
		case UNSUPPORTED:
			return getString(R.string.data_folder_import_unsupported, name, failure.file);
		case NO_SPACE: {
			final String[] sizes = failure.file.split(" / ");
			if (sizes.length == 2) {
				return getString(R.string.data_folder_import_no_space,
						Formatter.formatShortFileSize(this, Long.parseLong(sizes[1])),
						Formatter.formatShortFileSize(this, Long.parseLong(sizes[0])));
			}
			return getString(R.string.data_folder_import_io, name, failure.file);
		}
		case DAMAGED:
			return getString(R.string.data_folder_import_damaged, name, failure.file);
		case NO_GAME_DATA:
			return getString(R.string.data_folder_import_no_game_data, name);
		case NO_CLASSIC:
			return getString(R.string.data_folder_import_no_classic, name, failure.file);
		case CANCELLED:
			return getString(R.string.data_folder_import_cancelled);
		case IO:
			if (failure.getCause() instanceof UnseekableException) {
				return getString(R.string.data_folder_import_unseekable, name);
			}
			return getString(R.string.data_folder_import_io, name, failure.file.isEmpty() ? name : failure.file);
		default:
			return getString(R.string.data_folder_import_not_zip, name);
		}
	}

	private void showStatus(final String icon, final int color, final int fill, final int stroke, final String title,
			final String detail) {
		this.statusBox.setBackground(box(fill, stroke, line(2)));
		this.statusIcon.setText(icon);
		this.statusIcon.setTextColor(color);
		this.statusText.setText(title);
		this.statusText.setTextColor(color);
		this.statusDetail.setText(detail);
	}

	/** The chosen folder as the system names it, and in green the subfolder the data was found in. */
	private void showFolder() {
		if (this.dataFolder == null) {
			this.folderField.setText(R.string.data_folder_none);
			this.folderField.setTextColor(TEXT_DIM);
			return;
		}
		this.folderField.setTextColor(TEXT);
		final File chosen = this.chosenFolder != null ? this.chosenFolder : this.dataFolder;
		final String chosenPath = chosen.getAbsolutePath();
		final String foundPath = this.dataFolder.getAbsolutePath();
		if (!foundPath.startsWith(chosenPath + File.separator)) {
			this.folderField.setText(displayPath(this.dataFolder));
			return;
		}
		final SpannableStringBuilder shown = new SpannableStringBuilder(displayPath(chosen));
		final int start = shown.length();
		shown.append(" › ").append(foundPath.substring(chosenPath.length() + 1).replace("/", " › "));
		shown.setSpan(new ForegroundColorSpan(GREEN), start, shown.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		this.folderField.setText(shown);
	}

	/** Shows a folder as the system names it: "Internal storage / WC3" rather than the raw path. */
	private String displayPath(final File folder) {
		final String path = folder.getAbsolutePath();
		final String appFolder = getExternalFilesDir(null).getAbsolutePath();
		if (path.startsWith(appFolder)) {
			return "App folder" + path.substring(appFolder.length()).replace("/", " / ");
		}
		final String primary = Environment.getExternalStorageDirectory().getAbsolutePath();
		if (path.startsWith(primary)) {
			final String rest = path.substring(primary.length()).replace("/", " / ");
			return "Internal storage" + rest;
		}
		final String[] volume = path.split("/", 4);
		if ((volume.length == 4) && volume[1].equals("storage")) {
			return "SD card" + ("/" + volume[3]).replace("/", " / ");
		}
		return path;
	}

	/**
	 * Files that another user (adb, a computer, another app) unpacked into the app's folder keep that user as owner
	 * and are shared only with the storage group, which the app's process is not in; such a folder is there but cannot
	 * be listed or read.
	 */
	static boolean isUnreadable(final File folder) {
		return (folder != null) && folder.isDirectory() && (!folder.canRead() || (folder.list() == null));
	}

	private File appDataFolder() {
		return new File(getExternalFilesDir(null), "data");
	}

	/** The app's own folder is readable without "All files access". */
	private boolean isInAppFolder(final File folder) {
		final String appFolder = getExternalFilesDir(null).getAbsolutePath();
		final String path = folder.getAbsolutePath();
		return path.equals(appFolder) || path.startsWith(appFolder + File.separator);
	}

	private static boolean needsAllFilesAccess() {
		return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) && !Environment.isExternalStorageManager();
	}

	private void requestAllFilesAccess() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			final Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
					Uri.parse("package:" + getPackageName()));
			startActivity(intent);
		}
	}

	private void chooseFolder() {
		final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		startActivityForResult(intent, REQUEST_FOLDER);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if ((resultCode != RESULT_OK) || (data == null) || (data.getData() == null)) {
			return;
		}
		if (requestCode == REQUEST_ZIP) {
			importDocument(data.getData());
			return;
		}
		if (requestCode != REQUEST_FOLDER) {
			return;
		}
		final File folder = folderOfTree(data.getData());
		if (folder != null) {
			this.dataFolder = folder;
			this.chosenFolder = folder;
			this.writeFailed = false;
			this.importProblem = null;
			refresh();
		}
	}

	/**
	 * The engine reads its data through plain files, so a picked tree is turned back into the path it stands for:
	 * "primary:Games/WC3" is internal storage, "1234-ABCD:WC3" a removable card.
	 */
	static File folderOfTree(final Uri treeUri) {
		final String documentId = DocumentsContract.getTreeDocumentId(treeUri);
		final int colon = documentId.indexOf(':');
		if (colon < 0) {
			return null;
		}
		final String volume = documentId.substring(0, colon);
		final String relative = documentId.substring(colon + 1);
		final File base = "primary".equalsIgnoreCase(volume) ? Environment.getExternalStorageDirectory()
				: new File("/storage/" + volume);
		return relative.isEmpty() ? base : new File(base, relative);
	}

	private boolean start() {
		final File appFolder = getExternalFilesDir(null);
		try {
			EngineResources.ensureCurrent(this);
			// The engine folder is listed as a data source only if it exists; scripts and maps are placed there later.
			new File(appFolder, "engine").mkdirs();
			writeIni(new File(appFolder, AndroidLauncher.INI_FILE_NAME), DataFolderCheck.dataSourcesSection(this.kind,
					this.dataFolder, EngineResources.folder(this), new File(appFolder, "engine")));
		}
		catch (final IOException e) {
			Log.e(TAG, "Unable to write the engine configuration", e);
			this.writeFailed = true;
			if (this.folderField != null) {
				refresh();
			}
			return false;
		}
		final SharedPreferences.Editor editor = getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit();
		editor.putString(KEY_DATA_FOLDER, this.dataFolder.getAbsolutePath());
		editor.apply();

		final Bundle extras = getIntent().getExtras();
		// A map asked for by name starts at once; otherwise the launcher opens.
		final boolean map = (extras != null) && extras.containsKey(AndroidLauncher.EXTRA_LOAD_FILE);
		final Intent intent = new Intent(this, map ? AndroidLauncher.class : LauncherActivity.class);
		if (extras != null) {
			intent.putExtras(extras);
			intent.removeExtra(EXTRA_OPEN);
		}
		startActivity(intent);
		finish();
		return true;
	}

	private void writeIni(final File iniFile, final String dataSources) throws IOException {
		// The touch thresholds and the in-game graphics are the player's own (Options) and outlive a change of game data.
		final String touch = EngineSettings.ownSections(iniFile);
		try (InputStream template = getAssets().open(INI_TEMPLATE);
				OutputStream out = new FileOutputStream(iniFile)) {
			out.write(dataSources.getBytes(StandardCharsets.UTF_8));
			out.write('\n');
			final byte[] buffer = new byte[4096];
			int read;
			while ((read = template.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			if (!touch.isEmpty()) {
				out.write(("\n" + touch).getBytes(StandardCharsets.UTF_8));
			}
		}
	}
}
