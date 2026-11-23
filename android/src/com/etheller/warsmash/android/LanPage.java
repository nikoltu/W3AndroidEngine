package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.LauncherKeys.KEY_MAP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.etheller.warsmash.launcher.CampaignChoice;
import com.etheller.warsmash.launcher.CustomGame;
import com.etheller.warsmash.launcher.LaunchGuard;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.MapSections;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.launcher.lan.LanLauncherModel;
import com.etheller.warsmash.launcher.LauncherText;
import com.etheller.warsmash.launcher.lan.LanText;

import net.warsmash.lan.protocol.LanProtocol;

/**
 * Local Area Network, the Join Game and Create Game pages with the Join by IP dialog: the tabs, the games found on the
 * network and the host's settings. The lobby is {@link LobbyPage}; both draw what {@link LanLauncherModel} knows.
 */
final class LanPage extends LauncherViews {
	private static final int RED = Color.rgb(0xFF, 0x60, 0x60);
	private static final int NOTIFICATION_REQUEST = 6112;
	private static boolean notificationsAsked;

	private List<GameCatalog.MapEntry> maps;
	private GameCatalog.MapDetails mapDetails;
	private int shownVersion = -1;
	private LinearLayout gameRows;
	private FrameLayout summary;
	private LauncherKit.GlueButton joinButton;
	private LauncherKit.GlueButton createButton;
	private long selected;
	private int speed = 2;
	private int observers = LanProtocol.OBSERVERS_NONE;
	private final boolean[] options = { true, true, false, false };
	private String gameName = "";
	private boolean nameEdited;
	private boolean creating;

	LanPage(final LauncherActivity activity) {
		super(activity);
	}

	/** Android 13+: the session service's notification needs the permission; asked on the first visit only. */
	private void askNotificationsOnce() {
		if (!notificationsAsked) {
			notificationsAsked = true;
			LanSessionService.requestNotificationPermission(this.activity, NOTIFICATION_REQUEST);
		}
	}

	/** The rail's Local Area Network entry: the lobby while there is one, else the page last used. */
	void showLan() {
		final LanLauncherModel model = this.activity.lan();
		this.activity.askLanChecksum();
		askNotificationsOnce();
		model.pageShown();
		if (model.inSession()) {
			this.activity.lobbyPage.showLobby();
		}
		else if (model.mode() == LanLauncherModel.Mode.CREATE) {
			showCreate();
		}
		else {
			showJoin();
		}
	}

	boolean hasMapNamed(final String name) {
		if (this.maps == null) {
			return true;
		}
		for (final GameCatalog.MapEntry entry : this.maps) {
			final String path = entry.path.replace('\\', '/');
			if (path.substring(path.lastIndexOf('/') + 1).equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	/** Called every 50 ms while the LAN page is shown: moves on to the lobby, redraws a list that changed. */
	void onTick() {
		final LanLauncherModel model = this.activity.lan();
		if (model.inSession() && (model.lobby() != null)) {
			this.activity.lobbyPage.showLobby();
			return;
		}
		if ((this.shownVersion != model.version()) && (model.mode() == LanLauncherModel.Mode.JOIN)
				&& (this.gameRows != null)) {
			fillGames();
		}
		final String error = model.takeError();
		if (error != null) {
			this.activity.dialogs.showMessage("LAN", error);
		}
	}

	private float tabsHeight() {
		return 96 + (this.activity.board.tablet ? 24 : 8);
	}

	private void tabs(final FrameLayout content, final boolean create) {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float w = t ? 380 : 214;
		final float gap = t ? 16 : 8;
		final boolean net = this.activity.lan().networkAvailable();
		final String[] labels = { LauncherText.get("lan_join_game"), LauncherText.get("lan_create_game"), LanText.joinByIp() };
		for (int i = 0; i < 3; i++) {
			final int tab = i;
			final LauncherKit.GlueButton button = button(labels[i], 96, t ? 32 : 22, v -> {
				if (tab == 0) {
					showJoin();
				}
				else if (tab == 1) {
					showCreate();
				}
				else {
					showIpDialog();
				}
			});
			button.setSelected((i < 2) && (i == (create ? 1 : 0)));
			button.setEnabled(net);
			place(content, button, i * (w + gap), 0, w, 96);
		}
	}

	// -------------------------------------------------------------------- join

	void showJoin() {
		this.activity.lobbyPage.hide();
		final LanLauncherModel model = this.activity.lan();
		model.showJoin();
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final FrameLayout view = this.activity.homePage.shell(LauncherActivity.RAIL_LAN);
		final FrameLayout content = content(view, 0);
		tabs(content, false);
		final float top = tabsHeight();
		final float h = b.contentH - top;
		final float gap = t ? 36 : 10;
		final float listW = t ? 620 : 420;
		final FrameLayout listPanel = framed(content, 0, top, listW, h);
		final float frame = b.pageInset;
		final boolean net = model.networkAvailable();
		this.gameRows = null;
		if (!net) {
			place(listPanel, text(LanText.noWifi(), t ? 44 : 26, this.activity.art.gold()), frame + 10, frame, listW - (2 * frame),
					-1);
			place(listPanel, lines(LanText.noWifiHelp(), t ? 28 : 17, this.activity.art.text2(), 4), frame + 10,
					frame + (t ? 90 : 60), listW - (2 * frame) - 20, -1);
		}
		else {
			this.gameRows = scrollList(listPanel, frame, frame, listW - (2 * frame), h - (2 * frame));
		}
		this.summary = new FrameLayout(this.activity);
		place(content, this.summary, listW + gap, top, b.contentW - listW - gap, h);
		action(view, 0, glueLabel("BACK"), v -> this.activity.homePage.showHome());
		this.joinButton = action(view, 2, LauncherText.get("lan_join_game"), v -> joinSelected());
		this.joinButton.setEnabled(false);
		this.shownVersion = -1;
		this.activity.setPage(view, LauncherActivity.PAGE_LAN);
		if (net) {
			fillGames();
		}
	}

	private LanLauncherModel.GameRow chosen() {
		final List<LanLauncherModel.GameRow> games = this.activity.lan().games();
		LanLauncherModel.GameRow first = null;
		for (final LanLauncherModel.GameRow row : games) {
			if (row.id() == this.selected) {
				return row;
			}
			if (first == null) {
				first = row;
			}
		}
		return first;
	}

	private void joinSelected() {
		final LanLauncherModel.GameRow row = chosen();
		if ((row != null) && row.joinable()) {
			this.activity.lan().join(row);
		}
	}

	private void fillGames() {
		final LanLauncherModel model = this.activity.lan();
		this.shownVersion = model.version();
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float listW = t ? 620 : 420;
		final float rowW = listW - (2 * b.pageInset);
		final float rowH = t ? 150 : 96;
		this.gameRows.removeAllViews();
		final LanLauncherModel.GameRow chosen = chosen();
		if (chosen != null) {
			this.selected = chosen.id();
		}
		final List<View> views = new ArrayList<>();
		for (final LanLauncherModel.GameRow row : model.games()) {
			final View view = gameRow(row, rowW, rowH);
			markRow(view, row == chosen);
			onTap(view, false, v -> {
				this.selected = row.id();
				for (int i = 0; i < views.size(); i++) {
					markRow(views.get(i), views.get(i) == v);
				}
				fillSummary(row);
				this.joinButton.setEnabled(row.joinable());
				this.joinButton.setText(row.rejoin ? LauncherText.get("lan_rejoin") : LauncherText.get("lan_join_game"));
			});
			views.add(view);
			this.gameRows.addView(view, new LinearLayout.LayoutParams(MATCH, px(rowH)));
		}
		final String line = model.browseError() != null ? model.browseError()
				: model.games().isEmpty() ? LanText.searching() : "";
		if (!line.isEmpty()) {
			final TextView note = text(line, t ? 26 : 15, this.activity.art.text2());
			note.setPadding(px(12), px(12), 0, 0);
			this.gameRows.addView(note, new LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		fillSummary(chosen);
		this.joinButton.setEnabled((chosen != null) && chosen.joinable() && !model.isConnecting());
		this.joinButton.setText(chosen != null && chosen.rejoin ? LauncherText.get("lan_rejoin") : LauncherText.get("lan_join_game"));
	}

	static View colorSquare(final LauncherActivity activity, final int argb, final String name, final float size) {
		final FrameLayout square = new FrameLayout(activity);
		square.setBackgroundColor(argb);
		final TextView initial = new TextView(activity);
		initial.setText(name == null || name.isEmpty() ? "" : name.substring(0, 1).toUpperCase());
		initial.setGravity(Gravity.CENTER);
		initial.setTextColor(Color.WHITE);
		initial.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size * activity.scale * 0.5f);
		initial.setTypeface(activity.art != null ? activity.art.font() : null);
		square.addView(initial, new FrameLayout.LayoutParams(MATCH, MATCH));
		return square;
	}

	/**
	 * A participant's profile portrait framed in the player's color; the colored initial when the portrait is not known
	 * or this skin lacks it.
	 */
	static View portraitSquare(final LauncherActivity activity, final int portrait, final int argb, final String name,
			final float size) {
		final android.graphics.Bitmap image = portrait < 0 || !activity.art.has(SkinArt.portraitFile(portrait)) ? null
				: activity.art.bitmap(SkinArt.portraitFile(portrait));
		if (image == null) {
			return colorSquare(activity, argb, name, size);
		}
		final FrameLayout square = new FrameLayout(activity);
		square.setBackgroundColor(argb);
		final android.widget.ImageView picture = new android.widget.ImageView(activity);
		picture.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
		picture.setImageBitmap(image);
		final int frame = Math.max(2, Math.round(size * activity.scale * 0.05f));
		final FrameLayout.LayoutParams inset = new FrameLayout.LayoutParams(MATCH, MATCH);
		inset.setMargins(frame, frame, frame, frame);
		square.addView(picture, inset);
		return square;
	}

	private View gameRow(final LanLauncherModel.GameRow row, final float w, final float rowH) {
		final boolean t = this.activity.board.tablet;
		final LinearLayout view = new LinearLayout(this.activity);
		view.setGravity(Gravity.CENTER_VERTICAL);
		view.setPadding(px(12), 0, px(8), 0);
		final float p = rowH * 0.74f;
		final View portrait = portraitSquare(this.activity, row.portrait,
				CustomGame.PLAYER_COLORS[(int) Math.floorMod(row.id(), (long) CustomGame.PLAYER_COLORS.length)],
				row.found.info.hostName, p);
		portrait.setAlpha(row.otherVersion ? 0.5f : 1);
		view.addView(portrait, new LinearLayout.LayoutParams(px(p), px(p)));
		final LinearLayout texts = new LinearLayout(this.activity);
		texts.setOrientation(LinearLayout.VERTICAL);
		texts.addView(text(row.name, rowH * 0.24f,
				row.otherVersion ? this.activity.art.text2() : this.activity.art.text()));
		texts.addView(text(row.line, rowH * 0.19f, this.activity.art.text2()));
		if (!row.tag.isEmpty()) {
			texts.addView(text(row.tag, rowH * 0.17f,
					row.rejoin || row.mapFromHost ? this.activity.art.gold() : this.activity.art.text2()));
		}
		final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		params.leftMargin = px(t ? 22 : 10);
		view.addView(texts, params);
		view.setContentDescription(row.name + ", " + row.line + (row.tag.isEmpty() ? "" : ", " + row.tag));
		return view;
	}

	private void fillSummary(final LanLauncherModel.GameRow row) {
		this.summary.removeAllViews();
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float frame = b.pageInset;
		final float w = b.contentW - (t ? 620 : 420) - (t ? 36 : 10);
		final float ms = t ? 300 : 150;
		final FrameLayout panel = framed(this.summary, 0, 0, w, ms + (2 * frame) + (t ? 16 : 4));
		if (row == null) {
			return;
		}
		final ImageView minimap = new ImageView(this.activity);
		minimap.setScaleType(ImageView.ScaleType.FIT_XY);
		minimap.setBackground(minimapFrame(t ? 8 : 4));
		minimap.setImageBitmap(this.activity.art.bitmap(SkinArt.MINIMAP_UNKNOWN));
		place(panel, minimap, frame + (t ? 8 : 2), frame + (t ? 8 : 2), ms, ms);
		final float ix = frame + (t ? 8 : 2) + ms + (t ? 36 : 14);
		final float iw = w - ix - frame;
		place(panel, text(row.mapLabel, t ? 40 : 22, this.activity.art.text()), ix, frame, iw, -1);
		final float fs = t ? 30 : 17;
		final net.warsmash.lan.protocol.LanData.GameInfo info = row.found.info;
		final boolean sized = (info.mapWidth > 0) && (info.mapHeight > 0);
		final String[][] facts = { { LauncherText.get("lan_host"), info.hostName }, { LauncherText.get("lan_players"), info.players + " / " + info.maxPlayers },
			{ LauncherText.get("lan_game_speed_colon"), LanText.speed(info.speed) },
			{ LanText.mapSize(), sized ? info.mapWidth + " x " + info.mapHeight : "" } };
		for (int i = 0; i < (sized ? facts.length : facts.length - 1); i++) {
			fact(panel, facts[i][0], facts[i][1], ix, frame + (t ? 66 : 34) + (i * fs * 1.5f), iw, fs);
		}
		final float ah = t ? 220 : 120;
		final FrameLayout more = framed(this.summary, 0, ms + (2 * frame) + (t ? 16 : 4) + (t ? 36 : 10), w, ah);
		final float kx = t ? 220 : 118;
		fact(more, LanText.observersLabel(), LanText.observers(info.observers), frame + (t ? 12 : 6), frame, w - (2 * frame),
				fs);
		place(more, text(LanText.advancedOptions(), fs, this.activity.art.gold()), frame + (t ? 12 : 6),
				frame + (fs * 1.6f), w - (2 * frame), -1);
		place(more, lines(LanText.advanced(info.lockTeams, info.teamsTogether, info.fullSharedControl,
				info.randomRaces), fs, this.activity.art.text(), 4), frame + (t ? 12 : 6), frame + (fs * 3.1f),
				w - (2 * frame) - 24, -1);
	}

	// ------------------------------------------------------------- change map

	private String changeChosen;

	/** The host picks another map for the lobby it made. */
	void showChangeMapDialog() {
		if (this.maps == null) {
			this.activity.catalog.maps(found -> {
				if (found != null) {
					this.maps = found;
					showChangeMapDialog();
				}
			});
			return;
		}
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float w = Math.min(b.width - 80, t ? 1500 : 1100);
		final float h = Math.min(b.height - 60, t ? 1100 : 700);
		final FrameLayout box = this.activity.dialogs.dialogBox(w, h);
		if (box == null) {
			return;
		}
		final float inset = t ? 60 : 36;
		place(box, text(LanText.changeMapTitle(), t ? 52 : 30, this.activity.art.gold()), inset, t ? 40 : 20, w, -1);
		final float buttonH = t ? 128 : 96;
		final float top = t ? 120 : 70;
		final LinearLayout rows = scrollList(box, inset, top, w - (2 * inset), h - top - buttonH - (t ? 80 : 50));
		this.changeChosen = null;
		final LauncherKit.GlueButton ok = button(glueLabel("OK", getString(R.string.game_ok)), buttonH, t ? 36 : 26,
				v -> changeMapTo(this.changeChosen));
		ok.setEnabled(false);
		fillChangeList(rows, ok, t ? 100 : 72);
		place(box, button(glueLabel("CANCEL"), buttonH, t ? 36 : 26, v -> this.activity.dialogs.closeDialog()), inset,
				h - buttonH - (t ? 40 : 26), t ? 420 : 300, buttonH);
		place(box, ok, w - inset - (t ? 420 : 300), h - buttonH - (t ? 40 : 26), t ? 420 : 300, buttonH);
	}

	private void fillChangeList(final LinearLayout rows, final LauncherKit.GlueButton ok, final float rowH) {
		rows.removeAllViews();
		final List<View> mapRows = new ArrayList<>();
		for (final MapSections.Line line : MapSections.lines(this.maps, Collections.emptyList())) {
			if (line.heading != null) {
				rows.addView(headingRow(line.heading, rowH), new LinearLayout.LayoutParams(MATCH, px(rowH * 0.6f)));
			}
			else if (line.map != null) {
				final GameCatalog.MapEntry entry = line.map;
				final View row = mapRow(entry, rowH);
				onTap(row, false, v -> {
					for (final View other : mapRows) {
						markRow(other, other == v);
					}
					this.changeChosen = entry.path;
					ok.setEnabled(true);
				});
				markRow(row, entry.path.equals(this.changeChosen));
				mapRows.add(row);
				rows.addView(row, new LinearLayout.LayoutParams(MATCH, px(rowH)));
			}
		}
	}

	private void changeMapTo(final String path) {
		if ((path == null) || this.creating) {
			return;
		}
		this.creating = true;
		this.activity.dialogs.closeDialog();
		this.activity.catalog.lanMap(path, LauncherActivity.lanIndex(this.activity), shared -> {
			this.creating = false;
			if (shared == null) {
				this.activity.dialogs.showMessage(LanText.changeMap(), getString(R.string.lan_map_cannot_be_shared));
				return;
			}
			final LanLauncherModel.CreateSettings settings = new LanLauncherModel.CreateSettings();
			settings.mapFile = shared.file;
			settings.map = shared.info;
			settings.maxSlots = shared.playerSlots;
			settings.syncWarning = shared.usesSync;
			settings.issue = shared.issue;
			final String problem = this.activity.lan().changeMap(settings);
			if (problem != null) {
				this.activity.dialogs.showMessage(LanText.changeMap(), problem);
			}
		});
	}

	// ------------------------------------------------------------------ create

	void showCreate() {
		this.activity.lobbyPage.hide();
		final LanLauncherModel model = this.activity.lan();
		model.showCreate();
		if (this.maps == null) {
			final int generation = loadingPage(LauncherActivity.RAIL_LAN, LauncherActivity.PAGE_LAN);
			this.activity.catalog.maps(found -> {
				if (generation != this.activity.generation) {
					return;
				}
				if (found == null) {
					unreadable();
					return;
				}
				this.maps = found;
				showCreate();
			});
			return;
		}
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final FrameLayout view = this.activity.homePage.shell(LauncherActivity.RAIL_LAN);
		final FrameLayout content = content(view, 0);
		tabs(content, true);
		final float top = tabsHeight();
		final float h = b.contentH - top;
		final float gap = t ? 36 : 10;
		final float listW = t ? 700 : 430;
		final float frame = b.pageInset;
		final FrameLayout listPanel = framed(content, 0, top, listW, h);
		final float rowW = listW - (2 * frame);
		final float rowH = t ? 100 : 72;
		final LinearLayout rows = scrollList(listPanel, frame, frame, rowW, h - (2 * frame));
		final String chosen = CampaignChoice.chosenMap(this.activity.preferences.getString(KEY_MAP, null), this.maps);
		final List<View> mapRows = new ArrayList<>();
		View chosenRow = null;
		for (final MapSections.Line line : MapSections.lines(this.maps, Collections.emptyList())) {
			if (line.heading != null) {
				rows.addView(headingRow(line.heading, rowH), new LinearLayout.LayoutParams(MATCH, px(rowH * 0.6f)));
			}
			else if (line.map != null) {
				final GameCatalog.MapEntry entry = line.map;
				final View row = mapRow(entry, rowH);
				onTap(row, false, v -> {
					for (final View other : mapRows) {
						markRow(other, other == v);
					}
					this.activity.preferences.edit().putString(KEY_MAP, entry.path).apply();
					loadMapDetails(entry.path);
				});
				markRow(row, entry.path.equals(chosen));
				if (entry.path.equals(chosen)) {
					chosenRow = row;
				}
				mapRows.add(row);
				rows.addView(row, new LinearLayout.LayoutParams(MATCH, px(rowH)));
			}
		}
		if (chosenRow != null) {
			reveal(chosenRow);
		}
		final float rx = listW + gap;
		final float rw = b.contentW - rx;
		final float ms = t ? 320 : 150;
		final float th = t ? ms + (2 * frame) + 190 : ms + (2 * frame) + 4;
		this.summary = new FrameLayout(this.activity);
		place(content, this.summary, rx, top, rw, th);
		settings(content, rx, top + th + gap, rw, h - th - gap);
		action(view, 0, glueLabel("BACK"), v -> this.activity.homePage.showHome());
		if (!t) {
			action(view, 1, LauncherText.get("lan_advanced"), v -> showAdvancedSheet());
		}
		this.createButton = action(view, 2, LauncherText.get("lan_create_game"), v -> create());
		this.createButton.setEnabled(false);
		this.mapDetails = null;
		this.activity.setPage(view, LauncherActivity.PAGE_LAN);
		if (chosen != null) {
			loadMapDetails(chosen);
		}
	}

	private View mapRow(final GameCatalog.MapEntry entry, final float rowH) {
		final LinearLayout row = new LinearLayout(this.activity);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(px(6), 0, px(8), 0);
		final float line = Math.min(rowH * 0.86f, 84);
		final float icon = line * 0.82f;
		final FrameLayout mark = new FrameLayout(this.activity);
		mark.addView(image(entry.melee ? SkinArt.ICON_MELEE : SkinArt.ICON_UMS, ImageView.ScaleType.FIT_CENTER),
				new FrameLayout.LayoutParams(MATCH, MATCH));
		final TextView players = text(Integer.toString(entry.players), icon * 0.5f, this.activity.art.gold());
		players.setGravity(Gravity.CENTER);
		mark.addView(players, new FrameLayout.LayoutParams(MATCH, MATCH));
		row.addView(mark, new LinearLayout.LayoutParams(px(icon), px(icon)));
		final CharSequence name = LauncherKit.glueText(entry.label);
		final TextView title = text(name, line * 0.56f, this.activity.art.text());
		final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		params.leftMargin = px(18);
		row.addView(title, params);
		row.setContentDescription(name);
		return row;
	}

	private void loadMapDetails(final String path) {
		this.mapDetails = null;
		this.createButton.setEnabled(false);
		final int generation = this.activity.generation;
		this.activity.catalog.mapDetails(path, details -> {
			if ((generation != this.activity.generation) || (details == null)) {
				return;
			}
			this.mapDetails = details;
			fillMapSummary(details);
			this.createButton.setEnabled(!this.creating && this.activity.lan().networkAvailable());
		});
	}

	private void fillMapSummary(final GameCatalog.MapDetails details) {
		this.summary.removeAllViews();
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float frame = b.pageInset;
		final float rw = b.contentW - (t ? 700 : 430) - (t ? 36 : 10);
		final float ms = t ? 320 : 150;
		final float th = t ? ms + (2 * frame) + 190 : ms + (2 * frame) + 4;
		final FrameLayout panel = framed(this.summary, 0, 0, rw, th);
		final ImageView minimap = new ImageView(this.activity);
		minimap.setScaleType(ImageView.ScaleType.FIT_XY);
		minimap.setBackground(minimapFrame(t ? 8 : 4));
		minimap.setImageBitmap(details.minimap != null ? GameCatalog.bitmap(details.minimap)
				: this.activity.art.bitmap(SkinArt.MINIMAP_UNKNOWN));
		place(panel, minimap, frame + (t ? 8 : 2), frame + (t ? 8 : 2), ms, ms);
		final float ix = frame + (t ? 8 : 2) + ms + (t ? 36 : 14);
		final float iw = rw - ix - frame;
		place(panel, text(LauncherKit.glueText(details.name), t ? 40 : 22, this.activity.art.text()), ix, frame, iw, -1);
		final float fs = t ? 28 : 16;
		final String[][] facts = { { LauncherText.get("lan_players"), LauncherKit.glueText(details.suggested).toString() },
			{ LanText.mapSize(), details.size }, { LauncherText.get("lan_tileset"), details.tileset } };
		for (int i = 0; i < facts.length; i++) {
			fact(panel, facts[i][0], facts[i][1], ix, frame + (t ? 66 : 34) + (i * fs * 1.5f), iw, fs);
		}
		if (t) {
			place(panel, lines(LauncherKit.glueText(details.description), 26, this.activity.art.text2(), 4), frame + 8,
					frame + ms + 40, rw - (2 * frame) - 16, 150);
		}
	}

	private void settings(final FrameLayout content, final float x, final float y, final float w, final float h) {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float frame = b.pageInset;
		final FrameLayout panel = framed(content, x, y, w, h);
		final String profile = GameCatalog.currentProfile(this.activity);
		if (!this.nameEdited && this.gameName.isEmpty()) {
			this.gameName = LanText.defaultGameName(profile);
		}
		final EditText name = nameField(t ? 28 : 20);
		name.setHint(LanText.defaultGameName(profile));
		name.setText(this.gameName);
		name.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(LanProtocol.MAX_GAME_NAME) });
		afterTextChanged(name, () -> {
			this.gameName = name.getText().toString();
			this.nameEdited = true;
		});
		final String[] speeds = LanText.speeds();
		final String[] observerModes = LanText.observerModes();
		if (t) {
			place(panel, text(LauncherText.get("lan_game_settings"), 30, this.activity.art.gold()), frame + 8, frame - 4, 600, -1);
			final float fx = frame + 330;
			final float fw = w - (2 * frame) - 340;
			place(panel, text(LauncherText.get("lan_game_name"), 28, this.activity.art.text()), frame + 8, frame + 66, 300, -1);
			place(panel, name, fx, frame + 48, fw, 80);
			place(panel, text(LauncherText.get("lan_game_speed"), 28, this.activity.art.text()), frame + 8, frame + 166, 300, -1);
			place(panel, dropdown(speeds, this.speed, true, 80, 28, c -> this.speed = c), fx, frame + 146, 420, 80);
			place(panel, text(LauncherText.get("lan_observers"), 28, this.activity.art.text()), frame + 8, frame + 258, 300, -1);
			place(panel, dropdown(observerModes, this.observers, true, 80, 28, c -> this.observers = c), fx,
					frame + 238, 420, 80);
			place(panel, text(glue("ADVANCED_OPTIONS"), 28, this.activity.art.gold()), frame + 8, frame + 340, 600, -1);
			final String[] labels = { LauncherText.get("lan_option_lock_teams"), LauncherText.get("lan_option_teams_together"), LauncherText.get("lan_option_full_shared_control"), LauncherText.get("lan_option_random_races") };
			final float colw = (w - (2 * frame) - 16) / 2;
			for (int i = 0; i < labels.length; i++) {
				final int option = i;
				place(panel, checkLine(labels[i], this.options[i], 66, 26, on -> this.options[option] = on),
						frame + 8 + ((i % 2) * colw), frame + 392 + ((i / 2) * 66), colw - 10, 66);
			}
		}
		else {
			place(panel, text(LauncherText.get("lan_game_name"), 18, this.activity.art.gold()), frame + 6, frame + 4, 140, -1);
			place(panel, name, frame + 150, frame - 8, w - (2 * frame) - 160, 56);
			place(panel, text(LauncherText.get("lan_speed"), 18, this.activity.art.gold()), frame + 6, frame + 72, 140, -1);
			place(panel, dropdown(speeds, this.speed, true, 56, 20, c -> this.speed = c), frame + 150, frame + 54, 200, 56);
			place(panel, text(LauncherText.get("lan_observers"), 18, this.activity.art.gold()), frame + 372, frame + 72, 110, -1);
			place(panel, dropdown(observerModes, this.observers, true, 56, 20, c -> this.observers = c), frame + 480,
					frame + 54, w - (2 * frame) - 490, 56);
		}
	}

	private void showAdvancedSheet() {
		final FrameLayout box = this.activity.dialogs.dialogBox(1120, 560);
		if (box == null) {
			return;
		}
		place(box, text(glue("ADVANCED_OPTIONS"), 32, this.activity.art.gold()), 40, 26, 1000, -1);
		final String[] labels = { LauncherText.get("lan_option_lock_teams"), LauncherText.get("lan_option_teams_together"), LauncherText.get("lan_option_full_shared_control"), LauncherText.get("lan_option_random_races") };
		for (int i = 0; i < labels.length; i++) {
			final int option = i;
			place(box, checkLine(labels[i], this.options[i], 90, 28, on -> this.options[option] = on), 40, 90 + (i * 90),
					1000, 90);
		}
		place(box, button(glueLabel("OK"), 96, 28, v -> this.activity.dialogs.closeDialog()), 1120 - 28 - 40 - 300,
				560 - 28 - 30 - 96, 300, 96);
	}

	private void create() {
		final GameCatalog.MapDetails details = this.mapDetails;
		if ((details == null) || this.creating) {
			return;
		}
		this.creating = true;
		this.createButton.setEnabled(false);
		final LanLauncherModel model = this.activity.lan();
		this.activity.catalog.lanMap(details.path, LauncherActivity.lanIndex(this.activity), shared -> {
			this.creating = false;
			if (shared == null) {
				this.activity.dialogs.showMessage(LauncherText.get("lan_create_game"), getString(R.string.lan_map_cannot_be_shared));
				return;
			}
			final LanLauncherModel.CreateSettings settings = new LanLauncherModel.CreateSettings();
			settings.mapFile = shared.file;
			settings.map = shared.info;
			settings.maxSlots = shared.playerSlots;
			settings.syncWarning = shared.usesSync;
			settings.issue = shared.issue;
			settings.gameName = this.gameName;
			settings.speed = this.speed;
			settings.observers = this.observers;
			settings.lockTeams = this.options[0];
			settings.teamsTogether = this.options[1];
			settings.fullSharedControl = this.options[2];
			settings.randomRaces = this.options[3];
			final String problem = model.create(settings);
			if (problem != null) {
				this.activity.dialogs.showMessage(LauncherText.get("lan_create_game"), problem);
			}
		});
	}

	// --------------------------------------------------------------- join by IP

	void showIpDialog() {
		final LanLauncherModel model = this.activity.lan();
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float w = t ? 1400 : 1080;
		final float h = t ? 900 : 404;
		final FrameLayout box = this.activity.dialogs.dialogBox(w, h);
		if (box == null) {
			return;
		}
		final float inset = t ? 80 : 40;
		final float inner = w - (2 * inset);
		final TextView title = text(LanText.joinByIp(), t ? 52 : 30, this.activity.art.gold());
		title.setGravity(Gravity.CENTER);
		place(box, title, 0, t ? 40 : 14, w, -1);
		place(box, text(LanText.joinByIpHint(), t ? 30 : 19, this.activity.art.text2()), inset, t ? 140 : 66, inner, -1);
		final EditText field = nameField(t ? 40 : 26);
		field.setHint("192.168.1.40");
		field.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
		field.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(21) });
		place(box, field, inset, t ? 200 : 100, inner, t ? 120 : 90);
		final TextView problem = text("", t ? 30 : 19, RED);
		place(box, problem, inset, t ? 344 : 200, inner, -1);
		place(box, text(LanText.joinByIpPort(), t ? 26 : 17, this.activity.art.text2()), inset, t ? 400 : 236, inner, -1);
		final float buttonY = h - (t ? 28 : 14) - (t ? 128 : 96) - (t ? 40 : 14);
		final float buttonW = t ? 420 : 300;
		final float buttonH = t ? 128 : 96;
		place(box, button(glueLabel("CANCEL"), buttonH, t ? 36 : 26,
				v -> this.activity.dialogs.closeDialog()), inset, buttonY, buttonW, buttonH);
		final LauncherKit.GlueButton connect = button(LauncherText.get("lan_connect"), buttonH, t ? 36 : 26, null);
		connect.setTone(LauncherKit.GlueButton.TONE_CONFIRM);
		connect.setOnClickListener(v -> {
			model.joinByIp(field.getText().toString());
			final String error = model.ipError();
			problem.setText(error == null ? "" : error);
			if (error == null) {
				connect.setEnabled(false);
				watchIp(box, problem, connect);
			}
		});
		place(box, connect, w - inset - buttonW, buttonY, buttonW, buttonH);
		doneTaps(field, connect);
		this.activity.dialogs.typeInDialog(field);
		this.activity.dialogs.showKeyboard(field);
	}

	/** While the connection is made: the lobby opens (the page moves on), or the refusal shows in red. */
	private void watchIp(final View box, final TextView problem, final LauncherKit.GlueButton connect) {
		final LanLauncherModel model = this.activity.lan();
		box.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (box.getParent() == null) {
					return;
				}
				if (model.inSession() && !model.isConnecting()) {
					LanPage.this.activity.dialogs.closeDialog();
					return;
				}
				final String error = model.ipError();
				if (!model.inSession() && (error != null)) {
					problem.setText(error);
					connect.setEnabled(true);
					return;
				}
				box.postDelayed(this, 150);
			}
		}, 150);
	}

	// ---------------------------------------------------------- exclusivity

	/** A game was refused because this device is in a LAN lobby. */
	void showBlocked(final LaunchGuard.Blocked blocked) {
		if (blocked.lanSession == null) {
			this.activity.dialogs.showMessage(getString(R.string.game_not_started), "");
			return;
		}
		final boolean hosting = blocked.hosting();
		this.activity.dialogs.showQuestion(LanText.blockTitle(), LanText.blockText(hosting),
				new CharSequence[] { glueLabel("CANCEL"), LauncherText.get("lan_leave_lobby"), LauncherText.get("lan_back_to_lobby") },
				new Runnable[] { null, () -> this.activity.lobbyPage.leave(), () -> showLan() });
	}
}
