package com.etheller.warsmash.android;

import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.etheller.warsmash.launcher.CustomGame;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.launcher.lan.LanLauncherModel;
import com.etheller.warsmash.launcher.lan.LanLobbyView;
import com.etheller.warsmash.launcher.LauncherText;
import com.etheller.warsmash.launcher.lan.LanText;

import net.warsmash.lan.protocol.LanProtocol;

/**
 * The LAN lobby: the slots of the map with their choices, the people who watch, the map and the chat, and the
 * ready / start / leave actions. Built once per lobby; the slots, the facts and the chat are filled again when the
 * model's version changes.
 */
final class LobbyPage extends LauncherViews {
	private static final int YELLOW = Color.rgb(0xFF, 0xD2, 0x4A);
	private static final long TOOLTIP_MILLIS = 2000;

	private LinearLayout slotRows;
	private FrameLayout facts;
	private LinearLayout chatLines;
	private ScrollView chatScroll;
	private EditText chatField;
	private TextView title;
	private TextView syncNote;
	private TextView hostTip;
	private LauncherKit.GlueButton secondary;
	private LauncherKit.GlueButton primary;
	private int shownVersion = -1;
	private int shownChat;
	/** The map the minimap was asked for, and the picture that came for {@link #minimapPath}. */
	private String minimapAsked;
	private String minimapPath;
	private Bitmap minimapBitmap;
	private TextView tooltip;
	private boolean shown;

	LobbyPage(final LauncherActivity activity) {
		super(activity);
	}

	/** Whether the page on screen is the lobby (the LAN page shows the lobby or the lists, never both). */
	boolean isShown() {
		return this.shown;
	}

	void hide() {
		this.shown = false;
	}

	void showLobby() {
		this.shown = true;
		final LanLauncherModel model = this.activity.lan();
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final FrameLayout view = this.activity.homePage.shell(LauncherActivity.RAIL_LAN);
		final FrameLayout content = content(view, 0);
		final float frame = b.pageInset;
		final float gap = t ? 36 : 10;
		final float rightW = t ? 760 : 470;
		final float leftW = b.contentW - rightW - gap;
		final FrameLayout left = framed(content, 0, 0, leftW, b.contentH);
		this.title = text("", t ? 34 : 20, this.activity.art.text());
		place(left, this.title, frame + 12, frame - 8, leftW - (2 * frame) - 24, -1);
		final float headY = frame + (t ? 60 : 28);
		if (t) {
			// The column heads sit over the slots' choices.
			final float xr = frame + 8 + 84 + 20 + 260;
			final float[] hx = { xr, xr + 312, xr + 312 + 142, xr + 312 + 142 + 112 };
			final String[] heads = { glue("RACE"), glue("TEAM"), glue("COLOR"), LauncherText.get("lan_handicap") };
			for (int i = 0; i < heads.length; i++) {
				place(left, text(heads[i], 22, this.activity.art.text2()), hx[i], headY - 4, 200, -1);
			}
		}
		this.slotRows = scrollList(left, frame, headY + (t ? 32 : 0), leftW - (2 * frame), b.contentH - (2 * frame) - headY - (t ? 32 : 0));
		final float rx = leftW + gap;
		final float ms = t ? 300 : 130;
		final float th = ms + (2 * frame) + (t ? 16 : 4);
		this.facts = new FrameLayout(this.activity);
		place(content, this.facts, rx, 0, rightW, th);
		final float cy = th + gap;
		final float ch = b.contentH - cy;
		final FrameLayout chat = framed(content, rx, cy, rightW, ch);
		final float eh = t ? 90 : 56;
		this.chatField = nameField(t ? 26 : 15);
		this.chatField.setHint(LauncherText.get("lan_chat_hint"));
		this.chatField.setInputType(InputType.TYPE_CLASS_TEXT);
		this.chatField.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
		this.chatField.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(120) });
		this.chatField.setOnEditorActionListener((v, action, event) -> {
			if (action == EditorInfo.IME_ACTION_SEND) {
				model.sendChat(this.chatField.getText().toString());
				this.chatField.setText("");
				return true;
			}
			return false;
		});
		place(chat, this.chatField, frame + 4, ch - frame - eh, rightW - (2 * frame) - 8, eh);
		this.syncNote = lines("⚠ " + LanText.syncWarning(), t ? 24 : 13, YELLOW, 3);
		this.syncNote.setBackgroundColor(Color.argb(0x8C, 0x50, 0x3C, 0x00));
		this.syncNote.setPadding(px(8), px(4), px(8), px(4));
		this.syncNote.setVisibility(View.GONE);
		place(chat, this.syncNote, frame + 6, ch - frame - eh - (t ? 100 : 56), rightW - (2 * frame) - 12, -1);
		// The tip stands above the chat lines in the same column, so it takes its own room instead of covering them.
		this.hostTip = lines(this.activity.getString(R.string.lan_host_tip_wifi), (t ? 26 : 15) * 0.9f,
				this.activity.art.text2(), 4);
		this.hostTip.setVisibility(View.GONE);
		this.hostTip.setPadding(px(4), 0, px(4), px(t ? 10 : 4));
		this.chatScroll = new ScrollView(this.activity);
		this.chatScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		this.chatLines = new LinearLayout(this.activity);
		this.chatLines.setOrientation(LinearLayout.VERTICAL);
		this.chatScroll.addView(this.chatLines, new FrameLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT));
		final LinearLayout chatColumn = new LinearLayout(this.activity);
		chatColumn.setOrientation(LinearLayout.VERTICAL);
		chatColumn.addView(this.hostTip, new LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT));
		chatColumn.addView(this.chatScroll, new LinearLayout.LayoutParams(MATCH, 0, 1));
		place(chat, chatColumn, frame + 6, frame + (t ? 10 : 4), rightW - (2 * frame) - 12,
				ch - (2 * frame) - eh - (t ? 120 : 70));
		action(view, 0, LauncherText.get("lan_leave"), v -> leave());
		this.secondary = action(view, 1, LauncherText.get("lan_ready"), v -> readyOrCancel());
		this.primary = action(view, 2, glue("KEY_START_GAME"), v -> this.activity.lan().hostPrimary());
		this.shownVersion = -1;
		this.shownChat = 0;
		this.activity.setPage(view, LauncherActivity.PAGE_LAN);
		refresh();
	}

	/** Called every 50 ms while the LAN page is shown. */
	void onTick() {
		final LanLauncherModel model = this.activity.lan();
		if (!model.inSession() || (model.mode() == LanLauncherModel.Mode.JOIN)) {
			this.activity.lanPage.showLan();
			return;
		}
		if ((this.shownVersion != model.version())
				|| (model.showHostTip() != (this.hostTip.getVisibility() == View.VISIBLE))) {
			refresh();
		}
		else if (model.mode() == LanLauncherModel.Mode.COUNTDOWN) {
			refreshButtons(model);
		}
		final String failed = model.takeMapFailure();
		if (failed != null) {
			this.activity.dialogs.showMessage(model.mapFailureTitle(), failed);
		}
		final String error = model.takeError();
		if (error != null) {
			this.activity.dialogs.showMessage("LAN", error);
		}
	}

	private void readyOrCancel() {
		final LanLauncherModel model = this.activity.lan();
		final LanLobbyView lobby = model.lobby();
		if (lobby == null) {
			return;
		}
		if (lobby.iAmHost) {
			if (model.mode() == LanLauncherModel.Mode.COUNTDOWN) {
				model.cancelStart();
			}
			else {
				this.activity.lanPage.showChangeMapDialog();
			}
		}
		else {
			model.setReady((lobby.mine == null) || !lobby.mine.ready);
		}
	}

	/** Leave: a host is asked once more; the lobby is left, not the game's own start. */
	void leave() {
		final LanLauncherModel model = this.activity.lan();
		final String confirm = model.leaveConfirmation();
		if (confirm == null) {
			model.leave();
			this.activity.lanPage.showJoin();
			return;
		}
		this.activity.dialogs.showQuestion(LanText.leaveTitle(), confirm,
				new CharSequence[] { glueLabel("CANCEL"), LauncherText.get("lan_leave_lobby") }, new Runnable[] { null, () -> {
					model.leave();
					this.activity.lanPage.showJoin();
				} });
	}

	private void refresh() {
		final LanLauncherModel model = this.activity.lan();
		final LanLobbyView view = model.lobby();
		this.shownVersion = model.version();
		if (view == null) {
			return;
		}
		this.title.setText(view.title() + "  ·  " + view.readyText());
		fillSlots(view);
		fillFacts(view);
		this.syncNote.setVisibility(view.syncWarning ? View.VISIBLE : View.GONE);
		this.hostTip.setVisibility(model.showHostTip() ? View.VISIBLE : View.GONE);
		fillChat(model);
		refreshButtons(model);
	}

	private void refreshButtons(final LanLauncherModel model) {
		final LanLobbyView view = model.lobby();
		if (view == null) {
			return;
		}
		final boolean countdown = model.mode() == LanLauncherModel.Mode.COUNTDOWN;
		if (view.iAmHost) {
			this.secondary.setText(countdown ? glue("CANCEL") : LanText.changeMap());
			this.secondary.setEnabled(true);
			this.secondary.setVisibility(View.VISIBLE);
			this.primary.setVisibility(View.VISIBLE);
			final LanLobbyView.HostAction action = view.hostAction();
			this.primary.setText(countdown ? LauncherText.get("lan_starting", model.countdownSeconds())
					: action == LanLobbyView.HostAction.START ? glue("KEY_START_GAME")
							: action == LanLobbyView.HostAction.NOT_READY ? LauncherText.get("lan_not_ready") : LauncherText.get("lan_ready"));
			this.primary.setSelected(action == LanLobbyView.HostAction.NOT_READY);
			this.primary.setEnabled(!countdown && ((action == LanLobbyView.HostAction.START) || model.canReady()));
		}
		else {
			final boolean ready = (view.mine != null) && view.mine.ready;
			this.secondary.setVisibility(View.VISIBLE);
			this.secondary.setText(ready ? LauncherText.get("lan_not_ready") : LauncherText.get("lan_ready"));
			this.secondary.setSelected(ready);
			this.secondary.setEnabled(model.canReady());
			// Only the host starts the game: the guest's page has no button for it.
			this.primary.setVisibility(View.GONE);
		}
	}

	/** The minimap of the lobby's map, once this machine has the map and has read it; null until then or without one. */
	private Bitmap lobbyMinimap() {
		final java.io.File file = this.activity.lan().lobbyMapFile();
		if (file == null) {
			return null;
		}
		final String path = file.getPath();
		if (path.equals(this.minimapPath)) {
			return this.minimapBitmap;
		}
		if (!path.equals(this.minimapAsked)) {
			this.minimapAsked = path;
			final int generation = this.activity.generation;
			this.activity.catalog.mapDetails(path, details -> {
				if ((generation != this.activity.generation) || !path.equals(this.minimapAsked)) {
					return;
				}
				this.minimapPath = path;
				this.minimapBitmap = (details == null) || (details.minimap == null) ? null
						: GameCatalog.bitmap(details.minimap);
				if (this.shown) {
					refresh();
				}
			});
		}
		return null;
	}

	private void fillChat(final LanLauncherModel model) {
		final boolean t = this.activity.board.tablet;
		final List<LanLauncherModel.ChatLine> lines = model.chat();
		if (lines.size() < this.shownChat) {
			this.chatLines.removeAllViews();
			this.shownChat = 0;
		}
		for (int i = this.shownChat; i < lines.size(); i++) {
			final LanLauncherModel.ChatLine line = lines.get(i);
			final TextView text;
			if (line.system) {
				text = lines(line.text, t ? 26 : 15, this.activity.art.text2(), 3);
			}
			else {
				final android.text.SpannableStringBuilder spans = new android.text.SpannableStringBuilder();
				spans.append(line.from + ": ", new android.text.style.ForegroundColorSpan(this.activity.art.gold()),
						android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append(line.text);
				text = lines(spans, t ? 26 : 15, this.activity.art.text(), 3);
			}
			this.chatLines.addView(text, new LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		if (this.shownChat != lines.size()) {
			this.shownChat = lines.size();
			this.chatScroll.post(() -> this.chatScroll.fullScroll(View.FOCUS_DOWN));
		}
	}

	private void fillFacts(final LanLobbyView view) {
		this.facts.removeAllViews();
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float frame = b.pageInset;
		final float rightW = t ? 760 : 470;
		final float ms = t ? 300 : 130;
		final FrameLayout panel = framed(this.facts, 0, 0, rightW, ms + (2 * frame) + (t ? 16 : 4));
		final ImageView minimap = new ImageView(this.activity);
		minimap.setScaleType(ImageView.ScaleType.FIT_XY);
		minimap.setBackground(minimapFrame(t ? 8 : 4));
		final Bitmap mapPicture = lobbyMinimap();
		minimap.setImageBitmap(mapPicture != null ? mapPicture : this.activity.art.bitmap(SkinArt.MINIMAP_UNKNOWN));
		place(panel, minimap, frame + (t ? 8 : 2), frame + (t ? 8 : 2), ms, ms);
		final float fx = frame + (t ? 8 : 2) + ms + (t ? 30 : 12);
		final float fw = rightW - fx - frame;
		place(panel, text(view.mapLabel(), t ? 34 : 18, this.activity.art.text()), fx, frame - 4, fw, -1);
		final float fs = t ? 28 : 15;
		final String[][] rows = { { LauncherText.get("lan_game_speed_colon"), LanText.speed(view.options.speed) },
			{ LanText.observersLabel(), LanText.observers(view.options.observers) },
			{ LauncherText.get("lan_lock_teams_colon"), view.options.lockTeams ? LanText.yes() : LanText.no() },
			{ LauncherText.get("lan_teams_together_colon"), view.options.teamsTogether ? LanText.yes() : LanText.no() } };
		for (int i = 0; i < (t ? 4 : 3); i++) {
			fact(panel, rows[i][0], rows[i][1], fx, frame + (t ? 56 : 28) + (i * fs * 1.55f), fw, fs);
		}
	}

	private void fillSlots(final LanLobbyView view) {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		this.slotRows.removeAllViews();
		final float rowH = t ? 104 : 92;
		final float leftW = b.contentW - (t ? 760 : 470) - (t ? 36 : 10);
		final float rowW = leftW - (2 * b.pageInset);
		for (final LanLobbyView.Row row : view.rows) {
			this.slotRows.addView(slotRow(view, row, rowW, rowH), new LinearLayout.LayoutParams(MATCH, px(rowH)));
		}
		final int watching = view.watchers.size();
		if (watching > 0 || view.canWatch) {
			final TextView head = text(LauncherText.get("lan_observers_referees_head"), t ? 28 : 17, this.activity.art.gold());
			head.setGravity(Gravity.CENTER_VERTICAL);
			head.setPadding(px(12), 0, 0, 0);
			this.slotRows.addView(head, new LinearLayout.LayoutParams(MATCH, px(rowH * 0.6f)));
		}
		for (final LanLobbyView.Watcher w : view.watchers) {
			this.slotRows.addView(watcherRow(w, rowH), new LinearLayout.LayoutParams(MATCH, px(rowH)));
		}
		if (view.canWatch && (view.myRole == LanProtocol.ROLE_PLAYER)) {
			final boolean referees = view.options.observers == LanProtocol.OBSERVERS_REFEREES;
			final TextView watch = text(referees ? LauncherText.get("lan_watch_referee") : LauncherText.get("lan_watch_observer"), t ? 30 : 18,
					this.activity.art.text2());
			watch.setGravity(Gravity.CENTER_VERTICAL);
			watch.setPadding(px(12), 0, 0, 0);
			onTap(watch, false, v -> this.activity.lan().watch(referees));
			this.slotRows.addView(watch, new LinearLayout.LayoutParams(MATCH, px(rowH)));
		}
	}

	private View watcherRow(final LanLobbyView.Watcher w, final float rowH) {
		final boolean t = this.activity.board.tablet;
		final LinearLayout row = new LinearLayout(this.activity);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(px(8), 0, px(8), 0);
		final float pp = t ? 84 : 66;
		row.addView(LanPage.portraitSquare(this.activity, w.portrait, 0xFF777777, w.name, pp),
				new LinearLayout.LayoutParams(px(pp), px(pp)));
		final LinearLayout texts = new LinearLayout(this.activity);
		texts.setOrientation(LinearLayout.VERTICAL);
		final LinearLayout nameLine = new LinearLayout(this.activity);
		nameLine.setGravity(Gravity.CENTER_VERTICAL);
		if (w.kickable) {
			final int who = w.participantId;
			final View menu = dropdown(new String[] { w.name, LauncherText.get("lan_kick") }, 0, true, rowH * 0.46f, t ? 30 : 17, choice -> {
				if (choice == 1) {
					this.activity.lan().kickWatcher(who);
				}
			});
			nameLine.addView(menu, new LinearLayout.LayoutParams(px(t ? 360 : 220), px(rowH * 0.46f)));
		}
		else {
			nameLine.addView(text(w.name + (w.you ? " (you)" : ""), t ? 34 : 19, this.activity.art.text()));
		}
		if (w.keyboardAndMouse) {
			nameLine.addView(kbmMark(t ? 36 : 24));
		}
		texts.addView(nameLine);
		texts.addView(text(LanText.role(w.role) + (w.role == LanProtocol.ROLE_REFEREE ? " · can pause, chats with all" : ""),
				t ? 22 : 14, this.activity.art.gold()));
		final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		params.leftMargin = px(t ? 20 : 8);
		row.addView(texts, params);
		return row;
	}

	private View slotRow(final LanLobbyView view, final LanLobbyView.Row row, final float w, final float rowH) {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final FrameLayout cell = new FrameLayout(this.activity);
		final float gp = t ? 12 : 6;
		final float ph = t ? 84 : 72;
		final float pp = t ? 84 : 66;
		final float raceW = t ? 300 : 136;
		// The drop-down keeps its rim and arrow clear of the text; a narrower box leaves the number no room.
		final float teamW = t ? 130 : 104;
		final float colW = t ? 100 : 62;
		final float hcW = t ? 180 : 0;
		final float x1 = 8 + pp + (t ? 20 : 8);
		final float nameW = t ? 260 : 136;
		final float xr = x1 + nameW;
		final float py = (rowH - ph) / 2;
		final float size = t ? 30 : 17;
		final boolean human = row.kind == LanLobbyView.PLAYER;
		final boolean player = human || (row.kind == LanLobbyView.COMPUTER);
		if (player) {
			place(cell, LanPage.portraitSquare(this.activity, row.portrait,
					CustomGame.PLAYER_COLORS[Math.floorMod(row.color, CustomGame.PLAYER_COLORS.length)],
					row.kind == LanLobbyView.COMPUTER ? "C" : row.name, pp), 8, (rowH - pp) / 2, pp, pp);
		}
		else {
			final View empty = new View(this.activity);
			final android.graphics.drawable.GradientDrawable dashed = new android.graphics.drawable.GradientDrawable();
			dashed.setStroke(px(3), Color.rgb(0x4A, 0x40, 0x30), px(8), px(6));
			empty.setBackground(dashed);
			place(cell, empty, 8, (rowH - pp) / 2, pp, pp);
		}
		final int slot = row.slot;
		if (row.hostMenu) {
			final String[] items;
			final int selected;
			if (human) {
				items = new String[] { row.name, LauncherText.get("lan_kick") };
				selected = 0;
			}
			else {
				items = new String[] { glue("OPEN"), glue("CLOSED"), LanText.computer(0), LanText.computer(1), LanText.computer(2) };
				selected = row.kind == LanLobbyView.OPEN ? 0 : row.kind == LanLobbyView.CLOSED ? 1 : 2 + row.computerLevel;
			}
			place(cell, dropdown(items, selected, true, ph, size, choice -> {
				final LanLauncherModel model = this.activity.lan();
				if (human) {
					if (choice == 1) {
						model.hostSlot(slot, LanProtocol.ACTION_KICK, 0);
					}
				}
				else if (choice == 0) {
					model.hostSlot(slot, LanProtocol.ACTION_OPEN, 0);
				}
				else if (choice == 1) {
					model.hostSlot(slot, LanProtocol.ACTION_CLOSE, 0);
				}
				else {
					model.hostSlot(slot, LanProtocol.ACTION_COMPUTER, choice - 2);
				}
			}), x1, py, nameW - 8, ph);
		}
		else if (row.kind == LanLobbyView.OPEN) {
			final TextView open = text(row.name, size, this.activity.art.text2());
			open.setGravity(Gravity.CENTER_VERTICAL);
			onTap(open, false, v -> this.activity.lan().takeSlot(slot));
			place(cell, open, x1, py, nameW - 8, ph);
			return cell;
		}
		else {
			final LinearLayout nameLine = new LinearLayout(this.activity);
			nameLine.setGravity(Gravity.CENTER_VERTICAL);
			final TextView name = text(row.name, t ? 34 : 19, player ? this.activity.art.text() : this.activity.art.text2());
			nameLine.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
					ViewGroup.LayoutParams.WRAP_CONTENT));
			if (row.keyboardAndMouse) {
				nameLine.addView(kbmMark(t ? 36 : 24));
			}
			place(cell, nameLine, x1, rowH * 0.06f, nameW - 8, -1);
			final String tag = row.host && row.you ? "HOST · you" : row.host ? "HOST" : row.you ? "you" : "";
			if (!tag.isEmpty()) {
				place(cell, text(tag, t ? 22 : 14, this.activity.art.gold()), x1, rowH * 0.58f, nameW - 8, -1);
			}
		}
		if (!player) {
			return cell;
		}
		if (row.mapPercent >= 0) {
			final float bw = raceW + teamW + colW + hcW + (3 * gp) + (t ? gp : 0);
			final float bh = t ? 32 : 24;
			final LanLauncherModel model = this.activity.lan();
			place(cell, text(row.mapSending ? LanText.sending(row.mapPercent) : model.mapProgressText(), t ? 26 : 15,
					row.mapSending ? this.activity.art.text2() : this.activity.art.gold()), xr, py, bw, -1);
			final FrameLayout bar = new FrameLayout(this.activity);
			bar.setBackgroundColor(Color.rgb(0x1A, 0x1A, 0x1A));
			final View fill = new View(this.activity);
			fill.setBackgroundColor(row.mapSending ? Color.rgb(0x4A, 0x78, 0xC0) : Color.rgb(0xD8, 0xA8, 0x20));
			bar.addView(fill, new FrameLayout.LayoutParams(Math.max(1, px(bw * row.mapPercent / 100f)), MATCH));
			place(cell, bar, xr, py + ph - bh - 2, bw, bh);
			return cell;
		}
		final String[] races = glueList(CustomGame.RACES, 0);
		final int teamCount = Math.max(1, view.rows.size());
		final String[] teams = new String[teamCount];
		for (int i = 0; i < teamCount; i++) {
			teams[i] = Integer.toString(i + 1);
		}
		final String[] colors = new String[CustomGame.COLOR_NAMES.length];
		for (int i = 0; i < colors.length; i++) {
			colors[i] = glue(CustomGame.COLOR_NAMES[i][0]);
		}
		final boolean edit = row.editable;
		place(cell, dropdown(races, Math.min(row.race, races.length - 1), edit, ph, size,
				c -> send(row, c, row.team, row.color, row.handicap)), xr, py, raceW, ph);
		place(cell, dropdown(teams, Math.min(row.team, teams.length - 1), edit, ph, size,
				c -> send(row, row.race, c, row.color, row.handicap)), xr + raceW + gp, py, teamW, ph);
		place(cell, dropdown(colors, Math.floorMod(row.color, CustomGame.PLAYER_COLORS.length), edit, ph, size,
				c -> send(row, row.race, row.team, c, row.handicap), CustomGame.PLAYER_COLORS),
				xr + raceW + teamW + (2 * gp), py, colW, ph);
		if (t) {
			final String[] handicaps = { "100%", "90%", "80%", "70%", "60%", "50%" };
			place(cell, dropdown(handicaps, Math.max(0, Math.min(5, (100 - row.handicap) / 10)), edit, ph, size,
					c -> send(row, row.race, row.team, row.color, 100 - (c * 10))), xr + raceW + teamW + colW + (3 * gp), py,
					hcW, ph);
		}
		if (human) {
			final float cx = xr + raceW + teamW + colW + hcW + ((t ? 4 : 3) * gp) + 6;
			final float box = t ? 44 : 30;
			final View check = new View(this.activity);
			final LauncherKit.CheckDrawable drawable = new LauncherKit.CheckDrawable(this.activity.art);
			drawable.setChecked(row.ready);
			check.setBackground(drawable);
			place(cell, check, cx, (rowH - box) / 2, box, box);
			if (t && (row.pingMs > 0)) {
				place(cell, text(row.pingMs + " ms", 24, this.activity.art.text2()), cx + 72, (rowH - 30) / 2, 150, -1);
			}
		}
		return cell;
	}

	private void send(final LanLobbyView.Row row, final int race, final int team, final int color, final int handicap) {
		final LanLauncherModel model = this.activity.lan();
		if (model.lobby() != null) {
			model.lobby().choose(model, row, race, team, color, handicap);
		}
	}

	// ------------------------------------------------- keyboard and mouse mark

	/** The mark of a keyboard and mouse player; hover, a tap or a long press shows what it means for two seconds. */
	private View kbmMark(final float size) {
		final float touch = Math.max(size * 1.5f, 48 * this.activity.getResources().getDisplayMetrics().density
				/ this.activity.scale);
		final FrameLayout box = new FrameLayout(this.activity);
		final KbmIcon icon = new KbmIcon(this.activity);
		box.addView(icon, new FrameLayout.LayoutParams(px(size * 1.5f), px(size), Gravity.CENTER));
		box.setLayoutParams(new LinearLayout.LayoutParams(px(touch), px(Math.max(size, 48 * this.activity.getResources()
				.getDisplayMetrics().density / this.activity.scale))));
		box.setContentDescription(LanText.kbmTooltip());
		onTap(box, false, v -> showTooltip(box));
		box.setOnLongClickListener(v -> {
			showTooltip(box);
			return true;
		});
		box.setOnHoverListener((v, event) -> {
			if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
				v.postDelayed(() -> {
					if (v.isHovered()) {
						showTooltip(v);
					}
				}, 500);
			}
			else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
				hideTooltip();
			}
			return false;
		});
		return box;
	}

	private void showTooltip(final View anchor) {
		hideTooltip();
		final int[] at = new int[2];
		anchor.getLocationInWindow(at);
		final int[] origin = new int[2];
		this.activity.root.getLocationInWindow(origin);
		final boolean t = this.activity.board.tablet;
		final TextView tip = text(LanText.kbmTooltip(), t ? 26 : 16, Color.WHITE);
		tip.setBackgroundColor(Color.argb(0xE6, 0x10, 0x10, 0x18));
		tip.setPadding(px(14), px(8), px(14), px(8));
		final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		params.leftMargin = Math.max(0, at[0] - origin[0] - px(8));
		params.topMargin = at[1] - origin[1] + anchor.getHeight() + px(8);
		this.activity.root.addView(tip, params);
		this.tooltip = tip;
		tip.postDelayed(this::hideTooltip, TOOLTIP_MILLIS);
	}

	private void hideTooltip() {
		if (this.tooltip != null) {
			this.activity.root.removeView(this.tooltip);
			this.tooltip = null;
		}
	}

	/** A keyboard and a mouse, drawn small beside a name. */
	private static final class KbmIcon extends View {
		private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final RectF rect = new RectF();

		KbmIcon(final Context context) {
			super(context);
			final int c = Color.rgb(0xE8, 0xD9, 0xA8);
			this.line.setColor(c);
			this.line.setStyle(Paint.Style.STROKE);
			this.fill.setColor(c);
		}

		@Override
		protected void onDraw(final Canvas canvas) {
			final float w = getWidth();
			final float h = getHeight();
			this.line.setStrokeWidth(Math.max(2, h * 0.08f));
			this.rect.set(w * 0.02f, h * 0.2f, w * 0.66f, h * 0.82f);
			canvas.drawRoundRect(this.rect, h * 0.08f, h * 0.08f, this.line);
			for (int i = 0; i < 4; i++) {
				canvas.drawRect(w * 0.1f + (i * w * 0.13f), h * 0.34f, w * 0.17f + (i * w * 0.13f), h * 0.46f, this.fill);
			}
			canvas.drawRect(w * 0.16f, h * 0.56f, w * 0.52f, h * 0.68f, this.fill);
			this.rect.set(w * 0.74f, h * 0.16f, w * 0.98f, h * 0.84f);
			canvas.drawRoundRect(this.rect, w * 0.12f, w * 0.12f, this.line);
			canvas.drawLine(w * 0.86f, h * 0.16f, w * 0.86f, h * 0.42f, this.line);
		}
	}
}
