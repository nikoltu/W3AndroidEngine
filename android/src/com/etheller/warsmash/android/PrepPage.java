package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.LauncherKeys.KEY_SKIN;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.etheller.warsmash.launcher.DataContentCheck;
import com.etheller.warsmash.launcher.DataFolderCheck;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.units.DataTable;

/** Prep: the art of the skin is prepared from the game data once, with its progress shown. */
final class PrepPage extends LauncherViews {
	private static final long FADE_MILLIS = 250;
	private boolean prepDrawnWithArt;
	private TextView prepLabel;
	private TextView prepDetail;
	private LauncherKit.BarDrawable prepBar;
	private View prepBarView;
	private View prepButtons;
	private View prepRetry;
	private View prepOther;
	private float prepInner;
	private float prepButtonW;
	int prepDone;
	int prepTotal;

	PrepPage(final LauncherActivity activity) {
		super(activity);
	}

	void showPrep() {
		buildPrep();
		startPrep();
	}

	private void buildPrep() {
		this.activity.art = new SkinCache.Art(this.activity.cache);
		this.prepDrawnWithArt = this.activity.art.has(SkinArt.FONT);
		final LauncherBoard b = this.activity.board;
		final FrameLayout prep = new FrameLayout(this.activity);
		final Bitmap background = this.activity.art.bitmap(SkinArt.BACKGROUND);
		if (background != null) {
			prep.setBackground(new LauncherKit.CoverDrawable(background, 0.5f, Color.argb(0x59, 0, 0, 0)));
		}
		else {
			final GradientDrawable plain = new GradientDrawable();
			plain.setGradientType(GradientDrawable.RADIAL_GRADIENT);
			plain.setColors(new int[] { Color.rgb(0x12, 0x1A, 0x3A), Color.rgb(0x07, 0x0A, 0x18),
				Color.rgb(0x03, 0x04, 0x09) });
			plain.setGradientRadius(px(b.width) * 0.75f);
			plain.setGradientCenter(0.5f, 0.3f);
			prep.setBackground(plain);
		}
		final float x = (b.width - b.panelW) / 2;
		final float y = (b.height - b.panelH) / 2;
		final FrameLayout panel = new FrameLayout(this.activity);
		panel.setBackground(panelFrame(b.panelFrame, Color.argb(0xCC, 0, 0, 0)));
		place(prep, panel, x, y, b.panelW, b.panelH);

		final TextView title = text(getString(R.string.launcher_preparing), b.title, this.activity.art.gold());
		title.setGravity(Gravity.CENTER_HORIZONTAL);
		place(panel, title, 0, b.tablet ? 60 : 36, b.panelW, -1);
		final float inner = b.panelW - (2 * b.barInset);
		final float labelY = b.tablet ? 170 : 110;
		this.prepLabel = text("", b.body, this.activity.art.text());
		place(panel, this.prepLabel, b.barInset, labelY, inner, -1);
		this.prepBar = new LauncherKit.BarDrawable(this.activity.art.bitmap(SkinArt.BAR_BACKGROUND),
				this.activity.art.bitmap(SkinArt.BAR_FILL), this.activity.art.bitmap(SkinArt.BAR_GLASS), px(4));
		this.prepBarView = new View(this.activity);
		this.prepBarView.setBackground(this.prepBar);
		place(panel, this.prepBarView, b.barInset, labelY + (b.tablet ? 56 : 44), inner, b.barH);
		this.prepDetail = text("", b.body * 0.85f, this.activity.art.text2());
		place(panel, this.prepDetail, b.barInset, labelY + (b.tablet ? 170 : 128), inner, -1);

		final FrameLayout buttons = new FrameLayout(this.activity);
		final float buttonW = (inner - (b.tablet ? 32 : 24)) / 2;
		this.prepInner = inner;
		this.prepButtonW = buttonW;
		this.prepOther = button(getString(R.string.launcher_other_source), b.buttonH, b.buttonText,
				view -> this.activity.openGameData());
		place(buttons, this.prepOther, 0, 0, buttonW, b.buttonH);
		this.prepRetry = button(getString(R.string.data_folder_retry), b.buttonH, b.buttonText, view -> startPrep());
		place(buttons, this.prepRetry, inner - buttonW, 0, buttonW, b.buttonH);
		buttons.setVisibility(View.GONE);
		place(panel, buttons, b.barInset, b.panelH - b.panelFrame - b.buttonH - (b.tablet ? 8 : 4), inner,
				b.buttonH);
		this.prepButtons = buttons;

		this.activity.setPage(prep, LauncherActivity.PAGE_PREP);
		showStep(this.prepDone, this.prepTotal, null);
	}

	private void startPrep() {
		this.prepButtons.setVisibility(View.GONE);
		this.prepDetail.setTextColor(this.activity.art.text2());
		this.prepDetail.setText(R.string.launcher_reading);
		this.prepDetail.setSingleLine(true);
		this.prepDetail.setOnLongClickListener(null);
		this.prepDetail.setLongClickable(false);
		final DataTable ini = this.activity.ini;
		if (!DataFolderCheck.hasGameData(ini.get("DataSources"))) {
			// Trying again cannot find a folder that moved or lost files, so only the choice of a source is offered.
			prepFailed(new NoSuchFileException(sourceProblem(ini)));
			return;
		}
		final SkinCache cache = this.activity.cache;
		final boolean[] cancelled = this.activity.cancelled;
		new Thread(() -> {
			try {
				// Listing a large folder can take seconds, so this is not done on the interface thread.
				final String lacking = lackingFile(ini);
				if (lacking != null) {
					throw new NoSuchFileException(lacking);
				}
				cache.prepare(ini, (done, total, name) -> this.activity.runOnUiThread(() -> {
					if (!this.activity.isFinishing() && (cache == this.activity.cache)) {
						showStep(done, total, name);
					}
				}), cancelled);
				this.activity.runOnUiThread(() -> {
					if (!cancelled[0] && !this.activity.isFinishing() && (cache == this.activity.cache)) {
						prepared();
					}
				});
			}
			catch (final IOException | RuntimeException e) {
				Log.e(LauncherActivity.TAG, "Unable to prepare the launcher art", e);
				this.activity.runOnUiThread(() -> {
					if (!this.activity.isFinishing() && (cache == this.activity.cache)) {
						prepFailed(e);
					}
				});
			}
		}, "LauncherPrep").start();
	}

	private void showStep(final int done, final int total, final String name) {
		this.prepDone = done;
		this.prepTotal = total;
		this.prepLabel.setText(getString(R.string.launcher_art, done, Math.max(total, this.activity.cache.total())));
		this.prepBar.setProgress(total == 0 ? 0 : done / (float) total);
		if (name != null) {
			this.prepDetail.setText(name);
		}
		// The backdrop, panel, bar and font are the first files made; from then on the screen draws with them.
		if (!this.prepDrawnWithArt && this.activity.cache.file(SkinArt.FONT).isFile()) {
			buildPrep();
		}
	}

	private void prepFailed(final Throwable error) {
		boolean missing = false;
		for (Throwable cause = error; (cause != null) && !missing; cause = cause.getCause()) {
			missing = (cause instanceof NoSuchFileException) || (cause instanceof FileNotFoundException);
		}
		String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
		if (missing) {
			message = getString(R.string.launcher_source_missing, message);
		}
		else {
			message = getString(R.string.launcher_failed, message);
		}
		this.prepDetail.setTextColor(ERROR);
		this.prepDetail.setSingleLine(false);
		this.prepDetail.setEllipsize(null);
		this.prepDetail.setMaxLines(6);
		this.prepDetail.setText(message);
		final String copied = message;
		this.prepDetail.setOnLongClickListener(view -> {
			final ClipboardManager clipboard = (ClipboardManager) this.activity.getSystemService(Context.CLIPBOARD_SERVICE);
			if (clipboard != null) {
				clipboard.setPrimaryClip(ClipData.newPlainText("", copied));
				Toast.makeText(this.activity, R.string.launcher_copied, Toast.LENGTH_SHORT).show();
			}
			return true;
		});
		// Another try reads the same missing file, so it is left out and the other source takes the whole row.
		this.prepRetry.setVisibility(missing ? View.GONE : View.VISIBLE);
		final ViewGroup.LayoutParams params = this.prepOther.getLayoutParams();
		params.width = px(missing ? this.prepInner : this.prepButtonW);
		this.prepOther.setLayoutParams(params);
		this.prepButtons.setVisibility(View.VISIBLE);
	}

	private static String sourcePath(final DataTable ini) {
		String path = ini.get("DataSources").getField("Path00");
		if ((path.length() > 1) && path.startsWith("\"") && path.endsWith("\"")) {
			path = path.substring(1, path.length() - 1);
		}
		return path;
	}

	/** A file that an extracted folder lacks and the launcher or the game cannot do without; null when none. */
	private static String lackingFile(final DataTable ini) {
		if (!"Folder".equalsIgnoreCase(ini.get("DataSources").getField("Type00"))) {
			return null;
		}
		final File folder = new File(sourcePath(ini));
		final String lacking = DataContentCheck.missingRequiredFile(DataFolderCheck.detect(folder), folder);
		return lacking == null ? null : new File(folder, lacking.replace('\\', '/')).getPath();
	}

	/** The file of the configured source that the engine would open first and cannot find. */
	private static String sourceProblem(final DataTable ini) {
		final String path = sourcePath(ini);
		final File folder = new File(path);
		final boolean casc = "CASC".equalsIgnoreCase(ini.get("DataSources").getField("Type00"));
		final String missing = casc && folder.isDirectory() ? DataFolderCheck.missingCascFile(folder) : null;
		return missing == null ? path : new File(folder, missing).getPath();
	}

	/** The one transition of the launcher: Prep darkens to black and the next page lights up from it. */
	private void prepared() {
		// Game data without the Reforged interface cannot give that skin; the classic one takes over.
		if (this.activity.cache.reforged() && !this.activity.cache.file(SkinArt.REFORGED).isFile()) {
			this.activity.preferences.edit().putString(KEY_SKIN, SkinArt.CLASSIC_SKIN).apply();
			this.activity.cache = SkinCache.forConfiguration(this.activity, this.activity.ini, SkinArt.CLASSIC_SKIN);
			if (!this.activity.cache.isCurrent()) {
				this.prepDone = 0;
				this.prepTotal = 0;
				showPrep();
				return;
			}
		}
		if (!ValueAnimator.areAnimatorsEnabled()) {
			this.activity.showAfterPrep();
			return;
		}
		final View curtain = new View(this.activity);
		curtain.setBackgroundColor(Color.BLACK);
		curtain.setAlpha(0);
		curtain.setClickable(true);
		this.activity.root.addView(curtain, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		curtain.animate().alpha(1).setDuration(FADE_MILLIS).withEndAction(() -> {
			this.activity.showAfterPrep();
			curtain.bringToFront();
			curtain.animate().alpha(0).setDuration(FADE_MILLIS)
					.withEndAction(() -> this.activity.root.removeView(curtain));
		});
	}
}
