package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.LauncherKeys.KEY_SAVE;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.graphics.Bitmap;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.etheller.warsmash.launcher.CampaignChoice;
import com.etheller.warsmash.launcher.LaunchRequest;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedGame;

/** Load Saved Game: the saves of the current profile and what is known of the one chosen. */
final class LoadPage extends LauncherViews {
	List<GameCatalog.SaveEntry> saves;
	private FrameLayout saveInfo;
	private LauncherKit.GlueButton loadButton;
	private LauncherKit.GlueButton deleteButton;

	LoadPage(final LauncherActivity activity) {
		super(activity);
	}

	String loadFailureText(final String failure) {
		try {
			return loadabilityText(SavedGame.Loadability.valueOf(failure));
		}
		catch (final IllegalArgumentException e) {
			return failure;
		}
	}

	void showLoad() {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final FrameLayout view = this.activity.homePage.shell(LauncherActivity.RAIL_LOAD);
		final FrameLayout content = content(view, 0);
		final float listW = t ? 1300 : 700;
		final FrameLayout listPanel = framed(content, 0, 0, listW, b.contentH);
		final float infoX = t ? 1340 : 716;
		this.saveInfo = framed(content, infoX, 0, b.contentW - infoX, b.contentH);
		final float frame = b.pageInset;
		final TextView note = text(getString(R.string.game_loading_list), b.body, this.activity.art.text2());
		place(listPanel, note, frame, frame, listW - (2 * frame), -1);
		action(view, 0, glueLabel("BACK", getString(R.string.launcher_back)), v -> this.activity.homePage.showHome());
		this.deleteButton = action(view, 1, glueLabel("DELETE"), v -> askDelete());
		this.deleteButton.setTone(LauncherKit.GlueButton.TONE_DANGER);
		this.loadButton = action(view, 2, glueLabel("KEY_LOAD_GAME"), v -> startLoad());
		this.deleteButton.setEnabled(false);
		this.loadButton.setEnabled(false);
		this.activity.setPage(view, LauncherActivity.PAGE_LOAD);
		final int generation = this.activity.generation;
		this.activity.catalog.saves(found -> {
			if (generation != this.activity.generation) {
				return;
			}
			if (found == null) {
				note.setTextColor(ERROR);
				note.setText(R.string.game_unreadable);
				return;
			}
			this.saves = found;
			if (found.isEmpty()) {
				note.setText(R.string.game_no_saves);
				return;
			}
			listPanel.removeView(note);
			fillSaves(listPanel, listW - (2 * frame));
		});
	}

	private GameCatalog.SaveEntry chosenSave() {
		final String name = this.activity.preferences.getString(KEY_SAVE, null);
		for (final GameCatalog.SaveEntry entry : this.saves) {
			if (entry.name.equals(name)) {
				return entry;
			}
		}
		return this.saves.isEmpty() ? null : this.saves.get(0);
	}

	private void fillSaves(final FrameLayout listPanel, final float rowW) {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float frame = b.pageInset;
		float top = frame + (t ? 8 : 4);
		EditText filter = null;
		if (this.activity.art.reforged()) {
			// The Reforged list of saved games has a field above it that keeps the saves whose name has its text.
			final float fieldH = 96;
			filter = filterField(t ? 34 : 26);
			place(listPanel, filter, frame, top, rowW, fieldH);
			final float glass = fieldH * 0.5f;
			place(listPanel, image(SkinArt.ICON_SEARCH, ImageView.ScaleType.FIT_CENTER),
					(frame + rowW) - glass - (fieldH * 0.3f), top + ((fieldH - glass) / 2), glass, glass);
			top += fieldH + (t ? 16 : 8);
		}
		final LinearLayout rows = scrollList(listPanel, frame, top, rowW, b.contentH - frame - top);
		listSaves(rows, "");
		if (filter != null) {
			final EditText field = filter;
			afterTextChanged(field, () -> listSaves(rows, field.getText().toString()));
		}
	}

	private EditText filterField(final float size) {
		final boolean t = this.activity.board.tablet;
		final EditText field = new EditText(this.activity);
		field.setBackground(editFrame(t ? 14 : 8));
		field.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * this.activity.scale);
		field.setTextColor(this.activity.art.text());
		field.setHintTextColor(LauncherKit.DISABLED);
		field.setHint(R.string.launcher_filter_saves);
		field.setTypeface(this.activity.art.font());
		field.setSingleLine(true);
		field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
		final int pad = px(t ? 24 : 14);
		field.setPadding(pad, 0, px(96), 0);
		field.setGravity(Gravity.CENTER_VERTICAL);
		return field;
	}

	/** The saves whose name has the filter's text, the one chosen marked. */
	private void listSaves(final LinearLayout rows, final String filterText) {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float rowH = t ? 123 : 96;
		rows.removeAllViews();
		final String filter = filterText.trim().toLowerCase(Locale.ROOT);
		final GameCatalog.SaveEntry chosen = chosenSave();
		final List<View> saveRows = new ArrayList<>();
		final DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
		View chosenRow = null;
		for (final GameCatalog.SaveEntry entry : this.saves) {
			if (!entry.name.toLowerCase(Locale.ROOT).contains(filter)) {
				continue;
			}
			final boolean loadable = entry.loadability == SavedGame.Loadability.LOADABLE;
			final LinearLayout row = new LinearLayout(this.activity);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setPadding(px(t ? 16 : 10), 0, px(t ? 24 : 8), 0);
			final float icon = rowH * 0.7f;
			final ImageView file = image(SkinArt.ICON_FILE, ImageView.ScaleType.FIT_CENTER);
			file.setAlpha(loadable ? 1f : 0.4f);
			row.addView(file, new LinearLayout.LayoutParams(px(icon), px(icon)));
			final TextView name = text(entry.name, rowH * (t ? 0.42f : 0.3f),
					loadable ? this.activity.art.text() : LauncherKit.DISABLED);
			final LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
					ViewGroup.LayoutParams.WRAP_CONTENT, 1);
			nameParams.leftMargin = px(16);
			row.addView(name, nameParams);
			final TextView date = text(format.format(new Date(entry.saved)), rowH * (t ? 0.34f : 0.2f),
					loadable ? this.activity.art.text2() : LauncherKit.DISABLED);
			final LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			dateParams.leftMargin = px(12);
			row.addView(date, dateParams);
			row.setContentDescription(entry.name);
			onTap(row, false, v -> {
				for (final View other : saveRows) {
					markRow(other, other == v);
				}
				this.activity.preferences.edit().putString(KEY_SAVE, entry.name).apply();
				showSaveInfo(entry);
			});
			markRow(row, entry == chosen);
			if (entry == chosen) {
				chosenRow = row;
			}
			saveRows.add(row);
			rows.addView(row, new LinearLayout.LayoutParams(MATCH, px(rowH)));
		}
		if (chosenRow != null) {
			reveal(chosenRow);
			showSaveInfo(chosen);
		}
	}

	private String loadabilityText(final SavedGame.Loadability loadability) {
		switch (loadability) {
		case DAMAGED:
			return getString(R.string.game_save_damaged);
		case OTHER_VERSION:
			return getString(R.string.game_save_other_version);
		case OTHER_SCRIPTS:
			return getString(R.string.game_save_other_scripts);
		default:
			return "";
		}
	}

	/**
	 * Map Info: the save's name, its picture (a campaign's mission picture, else the map's minimap) and the facts as
	 * gold labels with their values on the right, or why this engine cannot load it.
	 */
	private void showSaveInfo(final GameCatalog.SaveEntry entry) {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final FrameLayout info = this.saveInfo;
		info.removeAllViews();
		final boolean loadable = entry.loadability == SavedGame.Loadability.LOADABLE;
		this.loadButton.setEnabled(loadable);
		this.deleteButton.setEnabled(true);
		final float frame = b.pageInset;
		final float w = b.contentW - (t ? 1340 : 716);
		final float inner = w - (2 * frame);
		final TextView title = text(entry.name, t ? 44 : 28, this.activity.art.text());
		title.setGravity(t ? Gravity.CENTER_HORIZONTAL : Gravity.START);
		place(info, title, frame, frame, inner, -1);
		final float picture = t ? w * 0.6f : 150;
		// A campaign save shows its mission's picture, a wider one.
		final boolean campaign = (entry.mapFile != null) && this.activity.art.reforged()
				&& entry.mapFile.toLowerCase(Locale.ROOT).contains("campaign");
		final float pictureH = campaign ? picture * 0.75f : picture;
		final ImageView minimap = new ImageView(this.activity);
		minimap.setScaleType(campaign ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.FIT_XY);
		minimap.setBackground(minimapFrame(t ? 10 : 6));
		final int rim = px(t ? 10 : 6);
		minimap.setPadding(rim, rim, rim, rim);
		minimap.setImageBitmap(this.activity.art.bitmap(SkinArt.MINIMAP_UNKNOWN));
		if (!loadable) {
			minimap.setAlpha(0.5f);
		}
		final float pictureY = frame + (t ? 76 : 48);
		place(info, minimap, t ? (w - picture) / 2 : frame, pictureY, picture, pictureH);
		final float factsX = t ? frame + 12 : frame + picture + 14;
		final float factsY = t ? pictureY + pictureH + 40 : pictureY;
		final float factsW = t ? inner - 24 : inner - picture - 14;
		final float size = t ? 30 : 19;
		final float step = size * 1.6f;
		if (!loadable) {
			final TextView reason = lines(loadabilityText(entry.loadability), size, ERROR, 3);
			reason.setGravity(t ? Gravity.CENTER_HORIZONTAL : Gravity.START);
			place(info, reason, factsX, factsY, factsW, -1);
			return;
		}
		final TextView players = fact(info, glue("COLON_PLAYERS"), "", factsX, factsY, factsW, size);
		fact(info, glue("COLON_ELAPSED_TIME"), CampaignChoice.elapsed(entry.gameTime), factsX,
				factsY + step, factsW, size);
		if (t) {
			fact(info, glue("COLON_SAVED"),
					DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(entry.saved)),
					factsX, factsY + (2 * step), factsW, size);
		}
		if (entry.mapFile == null) {
			return;
		}
		final int generation = this.activity.generation;
		this.activity.catalog.mapDetails(entry.mapFile, details -> {
			if ((generation != this.activity.generation) || (details == null) || (chosenSave() != entry)) {
				return;
			}
			final String mission = campaign ? missionPicture(details.name) : null;
			final Bitmap missionArt = mission != null ? this.activity.art.bitmap(mission) : null;
			if (missionArt != null) {
				minimap.setScaleType(ImageView.ScaleType.CENTER_CROP);
				minimap.setImageBitmap(missionArt);
			}
			else if (details.minimap != null) {
				minimap.setImageBitmap(GameCatalog.bitmap(details.minimap));
			}
			int count = 0;
			for (final GameCatalog.Slot slot : details.slots) {
				if ((slot.type == 1) || (slot.type == 2)) {
					count++;
				}
			}
			players.setText(Integer.toString(count));
		});
	}

	/** The Reforged picture of the campaign mission with the name given, or null. */
	private String missionPicture(final String mapName) {
		return CampaignChoice.missionPicture(LauncherKit.glueText(mapName).toString(),
				this.activity.art.missionPictures());
	}

	private void askDelete() {
		final GameCatalog.SaveEntry entry = this.saves == null ? null : chosenSave();
		if (entry == null) {
			return;
		}
		// Destructive: No comes first and is the plain way out.
		this.activity.dialogs.showQuestion(getString(R.string.game_delete_save, entry.name), null,
				new CharSequence[] { glueLabel("NO", getString(R.string.game_no)),
					glueLabel("YES", getString(R.string.game_yes)) },
				new Runnable[] { null, () -> {
					if (!entry.file.delete()) {
						Log.w(LauncherActivity.TAG, "Unable to delete " + entry.file);
					}
					showLoad();
				} });
	}

	private void startLoad() {
		final GameCatalog.SaveEntry entry = this.saves == null ? null : chosenSave();
		if ((entry == null) || (entry.loadability != SavedGame.Loadability.LOADABLE)) {
			return;
		}
		this.activity.launchGame(LaunchRequest.load(entry,
				this.activity.campaigns == null ? null : this.activity.campaigns.catalog), LauncherActivity.PAGE_LOAD);
	}
}
