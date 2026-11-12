package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.CustomGame.PLAYER_COLORS;
import static com.etheller.warsmash.launcher.CustomGame.RACES;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_REPLAY;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.etheller.warsmash.launcher.CampaignChoice;
import com.etheller.warsmash.launcher.LaunchRequest;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.GameReplays;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CSaveStorage;

/** View Replay: the replays, the facts and players of the one chosen, renaming and deleting them. */
final class ReplayPage extends LauncherViews {
	private List<GameCatalog.ReplayEntry> replays;
	private FrameLayout replayInfo;
	private LauncherKit.GlueButton viewReplayButton;
	private LauncherKit.GlueButton deleteReplayButton;

	ReplayPage(final LauncherActivity activity) {
		super(activity);
	}

	/** View Replay: laid out as Load Saved Game, as the original's two screens are alike. */
	void showReplays() {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final FrameLayout view = this.activity.homePage.shell(LauncherActivity.RAIL_REPLAY);
		final FrameLayout content = content(view, 0);
		final float listW = t ? 1300 : 700;
		final FrameLayout listPanel = framed(content, 0, 0, listW, b.contentH);
		final float infoX = t ? 1340 : 716;
		this.replayInfo = framed(content, infoX, 0, b.contentW - infoX, b.contentH);
		final float frame = b.pageInset;
		final TextView note = text(getString(R.string.game_loading_list), b.body, this.activity.art.text2());
		place(listPanel, note, frame, frame, listW - (2 * frame), -1);
		action(view, 0, glueLabel("BACK", getString(R.string.launcher_back)), v -> this.activity.homePage.showHome());
		this.deleteReplayButton = action(view, 1, glueLabel("DELETE", "Delete"), v -> askDeleteReplay());
		this.deleteReplayButton.setTone(LauncherKit.GlueButton.TONE_DANGER);
		this.viewReplayButton = action(view, 2,
				this.activity.art.reforged() ? glueLabel("WATCH_REPLAY", "Watch")
				: glueLabel("KEY_VIEW_REPLAY", "View Replay"),
				v -> startReplay());
		this.viewReplayButton.setTone(LauncherKit.GlueButton.TONE_CONFIRM);
		this.deleteReplayButton.setEnabled(false);
		this.viewReplayButton.setEnabled(false);
		this.activity.setPage(view, LauncherActivity.PAGE_REPLAY);
		final int generation = this.activity.generation;
		this.activity.catalog.replays(found -> {
			if (generation != this.activity.generation) {
				return;
			}
			if (found == null) {
				note.setTextColor(ERROR);
				note.setText(R.string.game_unreadable);
				return;
			}
			this.replays = found;
			if (found.isEmpty()) {
				note.setText(R.string.replay_none);
				return;
			}
			listPanel.removeView(note);
			fillReplays(listPanel, listW - (2 * frame));
		});
	}

	private GameCatalog.ReplayEntry chosenReplay() {
		final String name = this.activity.preferences.getString(KEY_REPLAY, null);
		for (final GameCatalog.ReplayEntry entry : this.replays) {
			if (entry.file.getName().equals(name)) {
				return entry;
			}
		}
		return this.replays.isEmpty() ? null : this.replays.get(0);
	}

	private String raceName(final GameCatalog.ReplayPlayer player) {
		return glue(RACES[player.raceIndex()][0], RACES[player.raceIndex()][1]);
	}

	/** Races as the list's second line names them: "Orc vs Human", or the number of players. */
	private String replayRaces(final GameCatalog.ReplayEntry entry) {
		if (entry.players.size() == 1) {
			return raceName(entry.players.get(0));
		}
		if (entry.players.size() == 2) {
			return raceName(entry.players.get(0)) + " vs " + raceName(entry.players.get(1));
		}
		return entry.players.size() + " " + glue("PLAYERS", "players").toLowerCase(Locale.ROOT);
	}

	private void fillReplays(final FrameLayout listPanel, final float rowW) {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float frame = b.pageInset;
		final float rowH = t ? 150 : 96;
		final LinearLayout rows = scrollList(listPanel, frame, frame + (t ? 8 : 4), rowW,
				b.contentH - (2 * frame) - (t ? 8 : 4));
		final GameCatalog.ReplayEntry chosen = chosenReplay();
		final List<View> replayRows = new ArrayList<>();
		final DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
		View chosenRow = null;
		for (final GameCatalog.ReplayEntry entry : this.replays) {
			final boolean playable = entry.playable();
			final LinearLayout row = new LinearLayout(this.activity);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setPadding(px(t ? 16 : 10), 0, px(t ? 24 : 8), 0);
			final float icon = rowH * (t ? 0.55f : 0.7f);
			final ImageView file = image(entry.melee == null ? SkinArt.ICON_FILE
					: entry.melee ? SkinArt.ICON_MELEE : SkinArt.ICON_UMS, ImageView.ScaleType.FIT_CENTER);
			file.setAlpha(playable ? 1f : 0.4f);
			row.addView(file, new LinearLayout.LayoutParams(px(icon), px(icon)));
			final LinearLayout texts = new LinearLayout(this.activity);
			texts.setOrientation(LinearLayout.VERTICAL);
			final TextView name = text(entry.name, t ? 46 : 28,
					playable ? this.activity.art.text() : LauncherKit.DISABLED);
			name.setSingleLine(true);
			name.setEllipsize(TextUtils.TruncateAt.END);
			texts.addView(name);
			final String second = entry.damaged ? getString(R.string.replay_damaged)
					: t ? entry.mapName() + " · " + replayRaces(entry) + " · " + CampaignChoice.elapsed(entry.length)
							+ " · " + entry.version
							: entry.mapName() + " · " + entry.version;
			final TextView detail = text(second, t ? 30 : 19,
					playable ? this.activity.art.text2() : LauncherKit.DISABLED);
			detail.setSingleLine(true);
			detail.setEllipsize(TextUtils.TruncateAt.END);
			texts.addView(detail);
			final LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
					ViewGroup.LayoutParams.WRAP_CONTENT, 1);
			textParams.leftMargin = px(16);
			row.addView(texts, textParams);
			final TextView when = text(t ? format.format(new Date(entry.recorded)) : CampaignChoice.elapsed(entry.length),
					t ? 30 : 19, playable ? this.activity.art.text2() : LauncherKit.DISABLED);
			final LinearLayout.LayoutParams whenParams = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			whenParams.leftMargin = px(12);
			row.addView(when, whenParams);
			row.setContentDescription(entry.name);
			onTap(row, false, v -> {
				for (final View other : replayRows) {
					markRow(other, other == v);
				}
				this.activity.preferences.edit().putString(KEY_REPLAY, entry.file.getName()).apply();
				showReplayInfo(entry);
			});
			markRow(row, entry == chosen);
			if (entry == chosen) {
				chosenRow = row;
			}
			replayRows.add(row);
			rows.addView(row, new LinearLayout.LayoutParams(MATCH, px(rowH)));
		}
		if (chosenRow != null) {
			reveal(chosenRow);
			showReplayInfo(chosen);
		}
	}

	/**
	 * Map Info: the replay's name, the map's minimap, the facts and the players; a replay this engine cannot play says
	 * why instead (the original's Incompatible Replay).
	 */
	private void showReplayInfo(final GameCatalog.ReplayEntry entry) {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final FrameLayout info = this.replayInfo;
		info.removeAllViews();
		final boolean playable = entry.playable();
		this.viewReplayButton.setEnabled(playable || entry.canTryAnyway());
		this.deleteReplayButton.setEnabled(true);
		final float frame = b.pageInset;
		final float w = b.contentW - (t ? 1340 : 716);
		final float inner = w - (2 * frame);
		final TextView title = text(entry.name, t ? 44 : 28, this.activity.art.text());
		title.setGravity(t ? Gravity.CENTER_HORIZONTAL : Gravity.START);
		title.setSingleLine(true);
		title.setEllipsize(TextUtils.TruncateAt.END);
		place(info, title, frame, frame, inner, -1);
		final float picture = t ? 400 : 150;
		final ImageView minimap = new ImageView(this.activity);
		minimap.setScaleType(ImageView.ScaleType.FIT_XY);
		minimap.setBackground(minimapFrame(t ? 10 : 6));
		final int rim = px(t ? 10 : 6);
		minimap.setPadding(rim, rim, rim, rim);
		minimap.setImageBitmap(this.activity.art.bitmap(SkinArt.MINIMAP_UNKNOWN));
		if (!playable) {
			minimap.setAlpha(0.5f);
		}
		final float pictureY = frame + (t ? 76 : 48);
		place(info, minimap, t ? (w - picture) / 2 : frame, pictureY, picture, picture);
		final float factsX = t ? frame + 12 : frame + picture + 14;
		final float factsY = t ? pictureY + picture + 32 : pictureY;
		final float factsW = t ? inner - 24 : inner - picture - 14;
		final float size = t ? 30 : 19;
		final float step = size * 1.6f;
		float y = factsY;
		if (entry.damaged) {
			final TextView reason = lines(getString(R.string.replay_damaged), size, ERROR, 3);
			place(info, reason, factsX, y, factsW, -1);
			return;
		}
		if (t) {
			fact(info, getString(R.string.replay_map), entry.mapName(), factsX, y, factsW, size);
			y += step;
		}
		fact(info, getString(R.string.replay_length), CampaignChoice.elapsed(entry.length), factsX, y, factsW, size);
		y += step;
		fact(info, getString(R.string.replay_recorded),
				DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(entry.recorded)),
				factsX, y, factsW, size);
		y += step;
		final TextView version = fact(info, getString(R.string.replay_version), entry.version, factsX, y, factsW,
				size);
		y += step;
		if (!playable) {
			version.setTextColor(ERROR);
			final String why = entry.warcraft ? getString(R.string.replay_warcraft)
					: entry.otherVersion ? glue("REPLAY_CONFIRM_DESC", getString(R.string.replay_other_version))
							: getString(R.string.replay_unplayable, entry.unplayable);
			final TextView reason = lines(glue("REPLAY_CONFIRM", "Incompatible Replay!") + "\n" + why, size, ERROR,
					4);
			place(info, reason, factsX, y + (step * 0.3f), factsW, -1);
			y += step * 3.5f;
		}
		else {
			y = Math.max(y, pictureY + picture) + (step * 0.3f);
		}
		final float playersX = t ? factsX : frame;
		final float playersW = t ? factsW : inner;
		final float square = size * 0.8f;
		for (final GameCatalog.ReplayPlayer player : entry.players) {
			if ((y + step) > (b.contentH - frame - (t ? 120 : 100))) {
				break;
			}
			final View color = new View(this.activity);
			color.setBackgroundColor(PLAYER_COLORS[Math.abs(player.color) % PLAYER_COLORS.length]);
			place(info, color, playersX, y + ((step - square) / 2) - (size * 0.15f), square, square);
			final String name = player.name != null ? player.name
					: getString(R.string.replay_computer) + " (" + glue("PLAYER", "Player") + " " + (player.slot + 1)
							+ ")";
			final TextView shown = text(name, size, this.activity.art.text());
			shown.setSingleLine(true);
			shown.setEllipsize(TextUtils.TruncateAt.END);
			final boolean symbol = player.raceIndex() > 0;
			place(info, shown, playersX + square + 14, y, (playersW * 0.6f) - (symbol ? size * 1.5f : 0), -1);
			if (symbol) {
				final float mark = size * 1.3f;
				place(info, image(SkinArt.RACE_SYMBOLS[player.raceIndex() - 1], ImageView.ScaleType.FIT_CENTER),
						playersX + (playersW * 0.6f) - (size * 1.4f), y - (size * 0.15f), mark, mark);
			}
			final TextView race = text(raceName(player), size, this.activity.art.text2());
			race.setGravity(Gravity.END);
			place(info, race, playersX + (playersW * 0.6f), y, playersW * 0.4f, -1);
			y += step;
		}
		final float renameW = t ? 320 : 220;
		final float renameH = t ? 112 : 96;
		final LauncherKit.GlueButton rename = button(getString(R.string.replay_rename), renameH, b.buttonText,
				v -> showRenameReplay(entry));
		place(info, rename, w - frame - renameW, b.contentH - frame - renameH, renameW, renameH);
		if (entry.mapFile == null) {
			return;
		}
		final int generation = this.activity.generation;
		this.activity.catalog.mapDetails(entry.mapFile, details -> {
			if ((generation != this.activity.generation) || (details == null) || (details.minimap == null)
					|| (chosenReplay() != entry)) {
				return;
			}
			minimap.setImageBitmap(GameCatalog.bitmap(details.minimap));
		});
	}

	private void askDeleteReplay() {
		final GameCatalog.ReplayEntry entry = this.replays == null ? null : chosenReplay();
		if (entry == null) {
			return;
		}
		// Destructive: No comes first and is the plain way out.
		this.activity.dialogs.showQuestion(getString(R.string.replay_delete, entry.name), null,
				new CharSequence[] { glueLabel("NO", getString(R.string.game_no)),
					glueLabel("YES", getString(R.string.game_yes)) },
				new Runnable[] { null, () -> {
					if (!entry.file.delete()) {
						Log.w(LauncherActivity.TAG, "Unable to delete " + entry.file);
					}
					showReplays();
				} });
	}

	private void startReplay() {
		final GameCatalog.ReplayEntry entry = this.replays == null ? null : chosenReplay();
		if (entry == null) {
			return;
		}
		if (entry.playable()) {
			this.activity.launchGame(LaunchRequest.replay(entry), LauncherActivity.PAGE_REPLAY);
		}
		else if (entry.canTryAnyway()) {
			// The original asks before it plays a replay of another version, ignoring the recorded checksums.
			this.activity.dialogs.showQuestion(glue("REPLAY_CONFIRM", "Incompatible Replay!") + "\n"
					+ glue("REPLAY_CONFIRM_DESC", getString(R.string.replay_other_version)), null,
					new CharSequence[] { glueLabel("NO", getString(R.string.game_no)),
						glueLabel("YES", getString(R.string.game_yes)) },
					new Runnable[] { null, () -> this.activity.launchGame(LaunchRequest.replay(entry, true),
							LauncherActivity.PAGE_REPLAY) });
		}
	}

	/** Rename Replay: in the top half on the phone, so that it shows above the keyboard. */
	private void showRenameReplay(final GameCatalog.ReplayEntry entry) {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float w = t ? 1400 : 1080;
		final float h = t ? 780 : 404;
		final FrameLayout box = this.activity.dialogs.dialogBox(w, h);
		if (box == null) {
			return;
		}
		final float inset = t ? 80 : 40;
		final float inner = w - (2 * inset);
		place(box, text(getString(R.string.replay_rename_title), b.title, this.activity.art.gold()), inset, t ? 70 : 30,
				inner,
				-1);
		final EditText field = nameField(t ? 36 : 26);
		field.setText(entry.name);
		field.setSelection(entry.name.length());
		place(box, field, inset, t ? 180 : 90, inner, t ? 120 : 96);
		final TextView path = text("", t ? 26 : 18, this.activity.art.text2());
		path.setSingleLine(true);
		path.setEllipsize(TextUtils.TruncateAt.START);
		place(box, path, inset, t ? 316 : 194, inner, -1);
		final float buttonY = h - (t ? 72 : 28) - b.buttonH;
		final float buttonW = t ? 400 : 320;
		place(box, button(glueLabel("CANCEL", "Cancel"), b.buttonH, b.buttonText,
				v -> this.activity.dialogs.closeDialog()),
				w - inset - (2 * buttonW) - 24, buttonY, buttonW, b.buttonH);
		final LauncherKit.GlueButton ok = button(glueLabel("OK", getString(R.string.game_ok)), b.buttonH,
				b.buttonText, v -> {
					final String typed = field.getText().toString().trim();
					this.activity.dialogs.closeDialog();
					final File target = GameCatalog.replayFile(entry, typed);
					if (target.equals(entry.file)) {
						return;
					}
					final Runnable rename = () -> {
						final File renamed = GameCatalog.renameReplay(entry, typed);
						if (renamed != null) {
							this.activity.preferences.edit().putString(KEY_REPLAY, renamed.getName()).apply();
						}
						showReplays();
					};
					if (target.exists()) {
						this.activity.dialogs.showQuestion(glue("OVERWRITE_REPLAY_MESSAGE",
								getString(R.string.replay_overwrite)), null,
								new CharSequence[] { glueLabel("NO", getString(R.string.game_no)),
									glueLabel("YES", getString(R.string.game_yes)) },
								new Runnable[] { null, rename });
					}
					else {
						rename.run();
					}
				});
		place(box, ok, w - inset - buttonW, buttonY, buttonW, b.buttonH);
		final Runnable check = () -> {
			final String typed = field.getText().toString().trim();
			final boolean usable = !CSaveStorage.safeName(typed).isEmpty() && (typed.length() <= 64);
			ok.setEnabled(usable);
			path.setText(usable ? GameReplays.FOLDER + "/" + GameCatalog.replayFile(entry, typed).getName() : "");
		};
		afterTextChanged(field, check);
		check.run();
		doneTaps(field, ok);
		this.activity.dialogs.typeInDialog(field);
		this.activity.dialogs.showKeyboard(field);
	}
}
