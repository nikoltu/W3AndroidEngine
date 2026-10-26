package com.etheller.warsmash.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Lets the player choose the folder with the game data before the engine starts, and writes the engine configuration
 * for it. Once a saved folder holds valid data the window is skipped and the game starts at once; the "Game data"
 * app shortcut (or the {@link #EXTRA_OPEN} extra) opens it again.
 */
public class DataFolderActivity extends Activity {
	static final String EXTRA_OPEN = "openLauncher";
	private static final String TAG = "Warsmash";
	private static final String PREFERENCES = "launcher";
	private static final String KEY_DATA_FOLDER = "dataFolder";
	private static final String INI_TEMPLATE = "warsmash-template.ini";
	private static final int REQUEST_FOLDER = 1;

	private static final int GOLD = Color.rgb(0xE8, 0xC7, 0x6A);
	private static final int GOLD_DARK = Color.rgb(0x8A, 0x6E, 0x2F);
	private static final int TEXT = Color.rgb(0xEC, 0xE3, 0xCF);
	private static final int TEXT_DIM = Color.rgb(0xA9, 0xA1, 0x90);
	private static final int TEXT_FOOT = Color.rgb(0x8C, 0x85, 0x76);
	private static final int FIELD = Color.rgb(0x0D, 0x10, 0x16);
	private static final int FIELD_BORDER = Color.rgb(0x2E, 0x36, 0x44);

	private enum Status {
		WAITING, FOUND, MISSING, NEEDS_ACCESS
	}

	private File dataFolder;
	private TextView folderField;
	private Button chooseButton;
	private LinearLayout statusBox;
	private TextView statusText;
	private TextView statusDetail;
	private Button accessButton;
	private Button continueButton;
	private TextView footRight;
	private DataFolderCheck.Kind kind = DataFolderCheck.Kind.NONE;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final File appData = new File(getExternalFilesDir(null), "data");
		final String saved = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getString(KEY_DATA_FOLDER, null);
		this.dataFolder = saved != null ? new File(saved) : appData;
		if ((saved == null) && (DataFolderCheck.detect(appData) == DataFolderCheck.Kind.NONE)) {
			this.dataFolder = null;
		}
		final boolean open = getIntent().getBooleanExtra(EXTRA_OPEN, false);
		if (!open && (this.dataFolder != null) && (saved != null)
				&& (DataFolderCheck.detect(this.dataFolder) != DataFolderCheck.Kind.NONE)) {
			this.kind = DataFolderCheck.detect(this.dataFolder);
			if (start()) {
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
			refresh();
		}
	}

	private int dp(final float value) {
		return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
				getResources().getDisplayMetrics()));
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
		view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
		view.setTextColor(color);
		view.setTypeface(face);
		return view;
	}

	private Button styledButton(final String label, final float size) {
		final Button button = new Button(this);
		button.setText(label);
		button.setAllCaps(false);
		button.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
		button.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
		button.setLetterSpacing(0.06f);
		button.setStateListAnimator(null);
		return button;
	}

	private View buildLayout() {
		final FrameLayout root = new FrameLayout(this);
		final GradientDrawable background = new GradientDrawable();
		background.setGradientType(GradientDrawable.RADIAL_GRADIENT);
		background.setColors(new int[] { Color.rgb(0x1A, 0x1F, 0x2A), Color.rgb(0x0D, 0x10, 0x16) });
		background.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.7f);
		background.setGradientCenter(0.5f, 0.4f);
		root.setBackground(background);

		// The panel's thin outer outline sits 5 dp outside its gold border.
		final FrameLayout outline = new FrameLayout(this);
		outline.setBackground(box(Color.TRANSPARENT, Color.rgb(0x3A, 0x33, 0x22), dp(1)));
		outline.setPadding(dp(6), dp(6), dp(6), dp(6));
		final LinearLayout panel = new LinearLayout(this);
		panel.setOrientation(LinearLayout.VERTICAL);
		panel.setBackground(box(Color.rgb(0x16, 0x1B, 0x24), GOLD_DARK, dp(2)));
		panel.setPadding(dp(48), dp(36), dp(48), dp(32));
		outline.addView(panel, new FrameLayout.LayoutParams(dp(680), ViewGroup.LayoutParams.WRAP_CONTENT));
		root.addView(outline, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

		final ImageView icon = new ImageView(this);
		icon.setImageResource(R.mipmap.ic_launcher);
		final LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(72), dp(72));
		iconParams.gravity = Gravity.CENTER_HORIZONTAL;
		panel.addView(icon, iconParams);
		final View rule = new View(this);
		rule.setBackgroundColor(GOLD_DARK);
		final LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(dp(220), dp(1));
		ruleParams.gravity = Gravity.CENTER_HORIZONTAL;
		ruleParams.topMargin = dp(8);
		panel.addView(rule, ruleParams);
		final TextView title = text(getString(R.string.data_folder_title), 26, TEXT,
				Typeface.create(Typeface.SERIF, Typeface.NORMAL));
		title.setLetterSpacing(0.08f);
		title.setGravity(Gravity.CENTER_HORIZONTAL);
		final LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		titleParams.topMargin = dp(10);
		panel.addView(title, titleParams);

		final TextView hint = text(getString(R.string.data_folder_hint), 17, TEXT_DIM, Typeface.SANS_SERIF);
		hint.setGravity(Gravity.CENTER_HORIZONTAL);
		hint.setLineSpacing(0, 1.3f);
		panel.addView(hint, spaced(24));

		final TextView label = text(getString(R.string.data_folder_label).toUpperCase(Locale.ROOT), 13, TEXT_DIM,
				Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
		label.setLetterSpacing(0.12f);
		panel.addView(label, spaced(24));
		final LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		this.folderField = text("", 15, TEXT, Typeface.MONOSPACE);
		this.folderField.setSingleLine(true);
		this.folderField.setEllipsize(TextUtils.TruncateAt.START);
		this.folderField.setGravity(Gravity.CENTER_VERTICAL);
		this.folderField.setPadding(dp(16), 0, dp(16), 0);
		this.folderField.setBackground(box(FIELD, FIELD_BORDER, dp(1)));
		row.addView(this.folderField, new LinearLayout.LayoutParams(0, dp(52), 1));
		this.chooseButton = styledButton("", 16);
		this.chooseButton.setTextColor(GOLD);
		this.chooseButton.setBackground(box(Color.rgb(0x2A, 0x24, 0x18), Color.rgb(0xC9, 0xA2, 0x4A), dp(1)));
		this.chooseButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_folder, 0, 0, 0);
		this.chooseButton.setCompoundDrawablePadding(dp(10));
		this.chooseButton.setPadding(dp(22), 0, dp(22), 0);
		this.chooseButton.setOnClickListener(view -> chooseFolder());
		final LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, dp(52));
		chooseParams.leftMargin = dp(12);
		row.addView(this.chooseButton, chooseParams);
		panel.addView(row, spaced(8));

		this.statusBox = new LinearLayout(this);
		this.statusBox.setOrientation(LinearLayout.HORIZONTAL);
		this.statusBox.setGravity(Gravity.CENTER_VERTICAL);
		this.statusBox.setPadding(dp(16), dp(12), dp(16), dp(12));
		this.statusText = text("", 15, TEXT_DIM, Typeface.SANS_SERIF);
		this.statusBox.addView(this.statusText, new LinearLayout.LayoutParams(0,
				ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		this.statusDetail = text("", 14, Color.rgb(0xB9, 0xE3, 0xC2), Typeface.MONOSPACE);
		this.statusBox.addView(this.statusDetail);
		this.accessButton = styledButton(getString(R.string.data_folder_allow), 14);
		this.accessButton.setTextColor(GOLD);
		this.accessButton.setBackground(box(Color.rgb(0x2A, 0x24, 0x18), Color.rgb(0xC9, 0xA2, 0x4A), dp(1)));
		this.accessButton.setPadding(dp(14), 0, dp(14), 0);
		this.accessButton.setOnClickListener(view -> requestAllFilesAccess());
		this.statusBox.addView(this.accessButton, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
		panel.addView(this.statusBox, spaced(24));

		this.continueButton = styledButton(getString(R.string.data_folder_continue), 20);
		this.continueButton.setLetterSpacing(0.15f);
		this.continueButton.setOnClickListener(view -> {
			if (this.kind != DataFolderCheck.Kind.NONE) {
				start();
			}
		});
		final LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(dp(280), dp(56));
		continueParams.gravity = Gravity.CENTER_HORIZONTAL;
		continueParams.topMargin = dp(28);
		panel.addView(this.continueButton, continueParams);

		final TextView footLeft = text(getString(R.string.data_folder_engine, versionName()), 13, TEXT_FOOT,
				Typeface.SANS_SERIF);
		final FrameLayout.LayoutParams leftParams = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
		leftParams.setMargins(dp(24), 0, 0, dp(18));
		root.addView(footLeft, leftParams);
		this.footRight = text("", 13, TEXT_FOOT, Typeface.SANS_SERIF);
		final FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
		rightParams.setMargins(0, 0, dp(24), dp(18));
		root.addView(this.footRight, rightParams);
		return root;
	}

	private LinearLayout.LayoutParams spaced(final int top) {
		final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		params.topMargin = dp(top);
		return params;
	}

	private String versionName() {
		try {
			return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		}
		catch (final PackageManager.NameNotFoundException e) {
			return "";
		}
	}

	private void refresh() {
		this.kind = this.dataFolder == null ? DataFolderCheck.Kind.NONE : DataFolderCheck.detect(this.dataFolder);
		final Status status;
		if (this.dataFolder == null) {
			status = Status.WAITING;
		}
		else if (this.kind != DataFolderCheck.Kind.NONE) {
			status = Status.FOUND;
		}
		else if (!this.dataFolder.canRead() && needsAllFilesAccess()) {
			status = Status.NEEDS_ACCESS;
		}
		else {
			status = Status.MISSING;
		}
		this.folderField.setText(this.dataFolder == null ? getString(R.string.data_folder_none)
				: displayPath(this.dataFolder));
		this.folderField.setTextColor(this.dataFolder == null ? TEXT_DIM : TEXT);
		this.chooseButton.setText(this.dataFolder == null ? R.string.data_folder_choose : R.string.data_folder_change);
		this.accessButton.setVisibility(status == Status.NEEDS_ACCESS ? View.VISIBLE : View.GONE);
		this.statusDetail.setText("");
		switch (status) {
		case FOUND:
			this.statusBox.setBackground(box(Color.rgb(0x16, 0x28, 0x1C), Color.rgb(0x2F, 0x5A, 0x3A), dp(1)));
			this.statusText.setTextColor(Color.rgb(0x8F, 0xD4, 0x9E));
			this.statusText.setText(this.kind == DataFolderCheck.Kind.CASC ? R.string.data_folder_casc
					: R.string.data_folder_extracted);
			this.statusDetail.setText(DataFolderCheck.describe(this.kind, this.dataFolder));
			break;
		case NEEDS_ACCESS:
			this.statusBox.setBackground(box(Color.rgb(0x2A, 0x20, 0x12), Color.rgb(0x6A, 0x54, 0x26), dp(1)));
			this.statusText.setTextColor(GOLD);
			this.statusText.setText(R.string.data_folder_needs_access);
			break;
		case MISSING:
			this.statusBox.setBackground(box(Color.rgb(0x2A, 0x16, 0x16), Color.rgb(0x6A, 0x2E, 0x2E), dp(1)));
			this.statusText.setTextColor(Color.rgb(0xE2, 0x9A, 0x9A));
			this.statusText.setText(R.string.data_folder_missing);
			break;
		default:
			this.statusBox.setBackground(box(FIELD, FIELD_BORDER, dp(1)));
			this.statusText.setTextColor(TEXT_DIM);
			this.statusText.setText(R.string.data_folder_waiting);
			break;
		}
		final boolean ready = status == Status.FOUND;
		this.continueButton.setEnabled(ready);
		this.continueButton.setTextColor(ready ? Color.rgb(0xF3, 0xDA, 0x92) : Color.rgb(0x6E, 0x6A, 0x60));
		this.continueButton.setBackground(ready ? box(Color.rgb(0x3A, 0x2E, 0x12), Color.rgb(0xC9, 0xA2, 0x4A), dp(2))
				: box(Color.rgb(0x1C, 0x20, 0x29), Color.rgb(0x3A, 0x33, 0x22), dp(1)));
		this.footRight.setText(ready ? R.string.data_folder_foot_ready : R.string.data_folder_foot_waiting);
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
		return path;
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
		if ((requestCode != REQUEST_FOLDER) || (resultCode != RESULT_OK) || (data == null) || (data.getData() == null)) {
			return;
		}
		final File folder = folderOfTree(data.getData());
		if (folder != null) {
			this.dataFolder = folder;
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

	private void copyAssetTree(final String assetPath, final File target) throws IOException {
		final String[] children = getAssets().list(assetPath);
		if ((children == null) || (children.length == 0)) {
			target.getParentFile().mkdirs();
			try (InputStream in = getAssets().open(assetPath); OutputStream out = new FileOutputStream(target)) {
				final byte[] buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) != -1) {
					out.write(buffer, 0, read);
				}
			}
			return;
		}
		target.mkdirs();
		for (final String child : children) {
			copyAssetTree(assetPath + "/" + child, new File(target, child));
		}
	}

	private boolean start() {
		final File appFolder = getExternalFilesDir(null);
		try {
			copyAssetTree("engineResources", new File(appFolder, "resources"));
			writeIni(new File(appFolder, AndroidLauncher.INI_FILE_NAME), DataFolderCheck.dataSourcesSection(this.kind,
					this.dataFolder, new File(appFolder, "resources"), new File(appFolder, "engine")));
		}
		catch (final IOException e) {
			Log.e(TAG, "Unable to write the engine configuration", e);
			if (this.statusText != null) {
				this.statusText.setText(R.string.data_folder_write_failed);
			}
			return false;
		}
		final SharedPreferences.Editor editor = getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit();
		editor.putString(KEY_DATA_FOLDER, this.dataFolder.getAbsolutePath());
		editor.apply();

		final Intent intent = new Intent(this, AndroidLauncher.class);
		final Bundle extras = getIntent().getExtras();
		if (extras != null) {
			intent.putExtras(extras);
			intent.removeExtra(EXTRA_OPEN);
		}
		startActivity(intent);
		finish();
		return true;
	}

	private void writeIni(final File iniFile, final String dataSources) throws IOException {
		try (InputStream template = getAssets().open(INI_TEMPLATE);
				OutputStream out = new FileOutputStream(iniFile)) {
			out.write(dataSources.getBytes(StandardCharsets.UTF_8));
			out.write('\n');
			final byte[] buffer = new byte[4096];
			int read;
			while ((read = template.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
		}
	}
}
