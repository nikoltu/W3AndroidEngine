package com.etheller.warsmash.desktop.launcher;

import java.io.File;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import com.etheller.warsmash.input.touch.ControlScheme;
import com.etheller.warsmash.lan.LanGameStart;
import com.etheller.warsmash.lan.LanSessions;
import com.etheller.warsmash.launcher.CustomGame;
import com.etheller.warsmash.launcher.EngineSettings;
import com.etheller.warsmash.launcher.GameCatalog;
import com.etheller.warsmash.launcher.GlueText;
import com.etheller.warsmash.launcher.LaunchGuard;
import com.etheller.warsmash.launcher.MapIssue;
import com.etheller.warsmash.launcher.LaunchRequest;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.launcher.lan.LanLauncherModel;
import com.etheller.warsmash.launcher.lan.LanLobbyView;
import com.etheller.warsmash.launcher.lan.LanPortraitId;
import com.etheller.warsmash.launcher.LauncherText;
import com.etheller.warsmash.launcher.lan.LanText;

import net.warsmash.lan.FileRejoinStore;
import net.warsmash.lan.RejoinStore;
import net.warsmash.lan.maps.LanMapIndex;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.transport.LanNetwork;

/**
 * The launcher's Local Area Network pages on the desktop: Join Game, Create Game, Join by IP and the lobby, drawn
 * from {@link LanLauncherModel}. Layout follows the approved mockup, in the launcher's board units.
 */
final class LanPages {
	private final LauncherScreen s;
	final LanLauncherModel model;
	private final LanNetwork network = new com.etheller.warsmash.desktop.DesktopLanNetwork();
	private LanMapIndex index;
	private volatile long dataChecksum;
	private final StringBuilder ip = new StringBuilder();
	private final StringBuilder gameName = new StringBuilder();
	private final StringBuilder chat = new StringBuilder();
	private long selected;
	private int speed = 2;
	private int observers = LanProtocol.OBSERVERS_NONE;
	private final boolean[] options = { true, true, false, false };
	private boolean creating;
	private boolean nameEdited;
	private String noticeTitle = "";
	private String noticeLine = "";
	private boolean blockedHosting;
	private long tipUntil;
	private Object tipFor;

	private final DesktopEnv env = new DesktopEnv();
	private RejoinStore rejoinStore;

	LanPages(final LauncherScreen screen) {
		this.s = screen;
		this.model = new LanLauncherModel(this.env);
		this.model.attention().setReducedMotion(Boolean.getBoolean("warsmash.reducedMotion"));
	}

	void dataOpened() {
		this.s.catalog.dataChecksum(sum -> this.dataChecksum = sum);
		this.env.mapIndex().setVirtualMaps(this.s.catalog.virtualMaps());
	}

	// -------------------------------------------------------------------- env

	private final class DesktopEnv implements LanLauncherModel.Env {
		@Override
		public LanNetwork network() {
			return LanPages.this.network;
		}

		@Override
		public synchronized LanMapIndex mapIndex() {
			if (LanPages.this.index == null) {
				final File folder = new File("LanMaps");
				final File download = new File(folder, "Download");
				download.mkdirs();
				final LanMapIndex made = new LanMapIndex(new File(folder, "index.txt"), download);
				made.addRoot(new File("Maps"));
				made.cleanStaleParts();
				LanPages.this.index = made;
			}
			return LanPages.this.index;
		}

		@Override
		public String playerName() {
			return LanPages.this.s.profiles.current();
		}

		@Override
		public String portrait() {
			return LanPortraitId.encode(LanPages.this.s.profiles.portrait(LanPages.this.s.profiles.current()),
					reforgedSkin());
		}

		@Override
		public boolean reforgedSkin() {
			return (LanPages.this.s.art != null) && LanPages.this.s.art.reforged;
		}

		@Override
		public int controlScheme() {
			return LanSessions.protocolScheme(new EngineSettings(LanPages.this.s.saves, LanPages.this.s.profiles
					.current(), LanPages.this.s.iniFile).controlScheme(ControlScheme.MOUSE_KEYBOARD));
		}

		@Override
		public String engineVersion() {
			return LanGameStart.localVersion();
		}

		@Override
		public String buildCommit() {
			return LanGameStart.localCommit();
		}

		@Override
		public boolean official() {
			return LanGameStart.localOfficial();
		}

		@Override
		public long dataChecksum() {
			return LanPages.this.dataChecksum;
		}

		@Override
		public java.util.concurrent.Executor ui() {
			return Gdx.app::postRunnable;
		}

		@Override
		public void launch(final LaunchRequest request) {
			LanPages.this.s.launchGame(request, LauncherScreen.Page.LAN);
		}

		@Override
		public void mapIssue(final File map, final java.util.function.Consumer<MapIssue> done) {
			if ((map == null) || (LanPages.this.s.catalog == null)) {
				done.accept(MapIssue.NONE);
				return;
			}
			LanPages.this.s.catalog.mapIssue(map.getAbsolutePath(), done);
		}

		@Override
		public boolean hasMapNamed(final String name) {
			final List<GameCatalog.MapEntry> maps = LanPages.this.s.maps;
			if (maps == null) {
				return true;
			}
			for (final GameCatalog.MapEntry entry : maps) {
				final String path = entry.path.replace('\\', '/');
				if (path.substring(path.lastIndexOf('/') + 1).equalsIgnoreCase(name)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public RejoinStore rejoinStore() {
			if (LanPages.this.rejoinStore == null) {
				LanPages.this.rejoinStore = new FileRejoinStore(new File("LanMaps/Rejoin"));
				LanSessions.setRejoinStore(LanPages.this.rejoinStore);
			}
			return LanPages.this.rejoinStore;
		}

		@Override
		public boolean hostMigration() {
			return true;
		}
	}

	// ------------------------------------------------------------- page state

	/** The rail's LAN entry was chosen. */
	void opened() {
		this.model.pageShown();
		if (!this.model.inSession() && (this.model.mode() != LanLauncherModel.Mode.CREATE)) {
			this.model.showJoin();
		}
	}

	/** Enter: Join or Create Game, Ready / Start Game for the host, Ready for a guest. */
	void mainAction() {
		switch (this.model.mode()) {
		case JOIN:
			for (final LanLauncherModel.GameRow row : this.model.games()) {
				if ((row.id() == this.selected) && row.joinable() && !this.model.isConnecting()) {
					this.model.join(row);
				}
			}
			break;
		case CREATE:
			if ((this.s.mapDetails != null) && !this.creating && this.model.networkAvailable()) {
				create(this.s.mapDetails);
			}
			break;
		case LOBBY: {
			final LanLobbyView view = this.model.lobby();
			if (view == null) {
				break;
			}
			if (view.iAmHost) {
				this.model.hostPrimary();
			}
			else if (this.model.canReady()) {
				this.model.setReady(view.mine == null || !view.mine.ready);
			}
			break;
		}
		default:
			break;
		}
	}

	void closed() {
		this.model.pageHidden();
	}

	void update(final float delta) {
		this.model.update(delta);
	}

	void dispose() {
		if (this.model.inSession()) {
			this.model.leave();
		}
		this.model.pageHidden();
	}

	/** A game started from the lobby ended. */
	void gameEnded() {
		this.model.gameEnded();
	}

	boolean lit() {
		return this.model.attention().isLit();
	}

	boolean chatDot() {
		return this.model.attention().hasChatDot();
	}

	/** A game was refused by the guard: the dialog that says why. */
	void blocked(final LaunchGuard.Blocked blocked) {
		if (blocked.lanSession == null) {
			this.noticeTitle = this.s.s("game_not_started");
			this.noticeLine = "";
			this.s.dialog = LauncherScreen.Dialog.LAN_NOTICE;
			return;
		}
		this.blockedHosting = blocked.hosting();
		// A start made with Enter must not also answer the dialog it opens.
		this.s.gui.keys.remove(Integer.valueOf(Input.Keys.ENTER));
		this.s.dialog = LauncherScreen.Dialog.LAN_BLOCK;
	}

	// -------------------------------------------------------------------- page

	private static final int RED = 0xFFFF6060;

	void page() {
		final Gui g = this.s.gui;
		final LauncherBoard b = this.s.board;
		final boolean t = b.tablet;
		final LanLauncherModel.Mode mode = this.model.mode();
		final String error = this.model.error();
		if ((error != null) && (this.s.dialog == LauncherScreen.Dialog.NONE)) {
			this.noticeTitle = LanText.noWifi().equals(error) ? error : "LAN";
			this.noticeLine = error;
			this.model.takeError();
			this.s.dialog = LauncherScreen.Dialog.LAN_NOTICE;
		}
		final String failed = this.model.takeMapFailure();
		if ((failed != null) && (this.s.dialog == LauncherScreen.Dialog.NONE)) {
			this.noticeTitle = this.model.mapFailureTitle();
			this.noticeLine = failed;
			this.s.dialog = LauncherScreen.Dialog.LAN_NOTICE;
		}
		switch (mode) {
		case JOIN:
		case CREATE:
			tabs(g, b);
			if (mode == LanLauncherModel.Mode.JOIN) {
				joinPage(g, b, t);
			}
			else {
				createPage(g, b, t);
			}
			break;
		case LOBBY:
		case COUNTDOWN:
			lobbyPage(g, b, t);
			break;
		default:
			g.text(mode == LanLauncherModel.Mode.REJOIN
					? LanText.rejoining(this.model.rejoinPercent(), "0:00") : LanText.waitingForPlayers(),
					b.contentX, b.contentY, b.contentW, b.contentH, b.title, g.palette.gold, Align.center);
			break;
		}
	}

	private float tabsHeight(final LauncherBoard b) {
		return 96 + (b.tablet ? 24 : 8);
	}

	private void tabs(final Gui g, final LauncherBoard b) {
		final boolean t = b.tablet;
		final float w = t ? 380 : 214;
		final float gap = t ? 16 : 8;
		final boolean net = this.model.networkAvailable();
		final String[] labels = { LauncherText.get("lan_join_game"), LauncherText.get("lan_create_game"), LanText.joinByIp() };
		final boolean create = this.model.mode() == LanLauncherModel.Mode.CREATE;
		for (int i = 0; i < 3; i++) {
			final int tone = !net ? Gui.TONE_OFF : (i == (create ? 1 : 0)) && (i < 2) ? Gui.TONE_DOWN : Gui.TONE_NORMAL;
			if (g.button("lanTab" + i, labels[i], b.contentX + (i * (w + gap)), b.contentY, w, 96, t ? 32 : 22, tone)) {
				this.s.playClick();
				if (i == 0) {
					this.model.showJoin();
				}
				else if (i == 1) {
					this.model.showCreate();
				}
				else {
					this.ip.setLength(0);
					this.model.clearIpError();
					this.s.gui.focus = "lanIp";
					this.s.dialog = LauncherScreen.Dialog.LAN_IP;
				}
			}
		}
	}

	// -------------------------------------------------------------------- join

	private void joinPage(final Gui g, final LauncherBoard b, final boolean t) {
		final float top = b.contentY + tabsHeight(b);
		final float h = b.contentH - tabsHeight(b);
		final float pf = b.pageFrame;
		final float gap = t ? 36 : 10;
		final float listW = t ? 620 : 420;
		final float x = b.contentX;
		g.panel(x, top, listW, h, pf);
		final boolean net = this.model.networkAvailable();
		final float lx = x + pf + 8;
		final float lw = listW - (2 * pf) - 16;
		if (!net) {
			g.text(LanText.noWifi(), lx, top + pf, lw, 80, t ? 44 : 26, g.palette.gold, Align.left);
			g.lines(LanText.noWifiHelp(), lx, top + pf + (t ? 90 : 60), lw, 200, t ? 28 : 17, g.palette.text2, Align.left);
		}
		else {
			final float rowH = t ? 150 : 96;
			final List<LanLauncherModel.GameRow> games = this.model.games();
			final float listH = h - (2 * pf);
			final float offset = g.beginList("lanGames", lx, top + pf, lw, listH, Math.max(games.size() * rowH, 1));
			float y = (top + pf) - offset;
			LanLauncherModel.GameRow chosen = null;
			for (final LanLauncherModel.GameRow row : games) {
				if (row.id() == this.selected) {
					chosen = row;
				}
			}
			if ((chosen == null) && !games.isEmpty()) {
				chosen = games.get(0);
				this.selected = chosen.id();
			}
			for (final LanLauncherModel.GameRow row : games) {
				final int state = g.hit("lanGame" + row.id(), lx, y, lw, rowH);
				final boolean isChosen = row == chosen;
				if (isChosen || ((state & Gui.HOVER) != 0)) {
					g.image(this.s.art.image(SkinArt.ROW_HIGHLIGHT), lx, y, lw, rowH, isChosen ? 1 : 0.5f);
				}
				final float p = rowH * 0.74f;
				final int hostColor = CustomGame.PLAYER_COLORS[(int) Math.floorMod(row.id(), CustomGame.PLAYER_COLORS.length)];
				if (row.otherVersion) {
					portrait(g, lx + 12, y + ((rowH - p) / 2), p, hostColor, row.found.info.hostName, true);
				}
				else {
					portrait(g, lx + 12, y + ((rowH - p) / 2), p, row.portrait, hostColor, row.found.info.hostName);
				}
				final float tx = lx + 12 + p + (t ? 22 : 10);
				final float tw = lw - p - (t ? 40 : 24);
				g.text(row.name, tx, y + (rowH * 0.04f), tw, rowH * 0.34f, rowH * 0.24f,
						row.otherVersion ? g.palette.text2 : g.palette.text, Align.left);
				g.text(row.line, tx, y + (rowH * 0.36f), tw, rowH * 0.28f, rowH * 0.19f, g.palette.text2, Align.left);
				if (!row.tag.isEmpty()) {
					g.text(row.tag, tx, y + (rowH * 0.64f), tw, rowH * 0.28f, rowH * 0.17f,
							row.rejoin || row.mapFromHost ? g.palette.gold : g.palette.text2, Align.left);
				}
				if ((state & Gui.CLICK) != 0) {
					this.s.playClick();
					this.selected = row.id();
					if (((state & Gui.DOUBLE) != 0) && row.joinable()) {
						this.model.join(row);
					}
				}
				y += rowH;
			}
			g.endList();
			if (games.isEmpty()) {
				final String line = this.model.browseError() != null ? this.model.browseError() : LanText.searching();
				g.text(line, lx + 12, top + pf + 8, lw - 12, t ? 60 : 40, t ? 26 : 15, g.palette.text2, Align.left);
			}
			else {
				g.text(LanText.searching(), lx + 12, Math.min(top + pf + (games.size() * rowH) + 10 - offset,
						(top + h) - pf - 60), lw - 12, t ? 50 : 36, t ? 26 : 15, g.palette.text2, Align.left);
			}
		}
		final float rx = x + listW + gap;
		final float rw = b.contentW - listW - gap;
		final float ms = t ? 300 : 150;
		final float th = ms + (2 * pf) + (t ? 16 : 4);
		g.panel(rx, top, rw, th, pf);
		LanLauncherModel.GameRow chosen = null;
		for (final LanLauncherModel.GameRow row : this.model.games()) {
			if (row.id() == this.selected) {
				chosen = row;
			}
		}
		if (net && (chosen != null)) {
			final float mx = rx + pf + (t ? 8 : 2);
			final float my = top + pf + (t ? 8 : 2);
			g.fill(mx - 3, my - 3, ms + 6, ms + 6, 0xFF555555);
			g.fill(mx, my, ms, ms, 0xFF000000);
			g.image(SkinArt.MINIMAP_UNKNOWN, mx, my, ms, ms);
			final float ix = mx + ms + (t ? 36 : 14);
			final float iw = (rx + rw) - ix - pf;
			final LanData.GameInfo info = chosen.found.info;
			g.text(chosen.mapLabel, ix, my - 4, iw, t ? 50 : 30, t ? 40 : 22, g.palette.text, Align.left);
			final float fs = t ? 30 : 17;
			final float kx = t ? 220 : 118;
			final boolean sized = (info.mapWidth > 0) && (info.mapHeight > 0);
			final String[][] facts = { { LauncherText.get("lan_host"), info.hostName },
				{ LauncherText.get("lan_players"), info.players + " / " + info.maxPlayers }, { LauncherText.get("lan_game_speed_colon"), LanText.speed(info.speed) },
				{ LanText.mapSize(), sized ? info.mapWidth + " x " + info.mapHeight : "" } };
			for (int i = 0; i < (sized ? facts.length : facts.length - 1); i++) {
				final float fy = my + (t ? 60 : 32) + (i * fs * 1.5f);
				g.text(facts[i][0], ix, fy, kx, fs * 1.4f, fs, g.palette.gold, Align.left);
				g.text(facts[i][1], ix + kx, fy, iw - kx, fs * 1.4f, fs, g.palette.text, Align.left);
			}
			final float ay = top + th + gap;
			final float ah = (t ? 220 : 120);
			g.panel(rx, ay, rw, ah, pf);
			final float ax = rx + pf + (t ? 12 : 6);
			g.text(LanText.observersLabel(), ax, ay + pf, kx + 40, fs * 1.4f, fs, g.palette.gold, Align.left);
			g.text(LanText.observers(info.observers), ax + kx + 40, ay + pf, rw - (2 * pf) - kx - 60, fs * 1.4f, fs,
					g.palette.text, Align.left);
			g.text(LanText.advancedOptions(), ax, ay + pf + (fs * 1.6f), rw - (2 * pf), fs * 1.4f, fs, g.palette.gold,
					Align.left);
			g.lines(LanText.advanced(info.lockTeams, info.teamsTogether, info.fullSharedControl, info.randomRaces),
					ax, ay + pf + (fs * 3.1f), rw - (2 * pf) - 24, ah - pf - (fs * 3.1f), fs, g.palette.text,
					Align.left);
		}
		final float[] back = { b.backX, b.backW };
		if (g.button("lanBack", glueBack(), back[0], b.backY, back[1], b.backH, t ? 36 : 30, Gui.TONE_NORMAL)) {
			this.s.playClick();
			this.s.showPage(LauncherScreen.Page.HOME);
		}
		final float[] go = b.actionSlot(2);
		final boolean canJoin = net && (chosen != null) && chosen.joinable() && !this.model.isConnecting();
		if (g.button("lanJoin", chosen != null && chosen.rejoin ? LauncherText.get("lan_rejoin") : LauncherText.get("lan_join_game"), go[0], b.backY, go[1], b.backH,
				t ? 36 : 30, !canJoin ? Gui.TONE_OFF : this.s.art.reforged ? Gui.TONE_CONFIRM : Gui.TONE_DOWN)) {
			this.s.playClick();
			this.model.join(chosen);
		}
	}

	private String glueBack() {
		return this.s.glue("BACK");
	}

	/** A participant's profile portrait framed in the player's color; the colored initial when it is not known. */
	private void portrait(final Gui g, final float x, final float y, final float size, final int portrait,
			final int argb, final String name) {
		final TextureRegion image = portrait < 0 ? null : this.s.art.portrait(portrait);
		if (image == null) {
			portrait(g, x, y, size, argb, name, false);
			return;
		}
		g.fill(x - 3, y - 3, size + 6, size + 6, argb);
		g.cover(image, x, y, size, size, 0.5f);
	}

	private void portrait(final Gui g, final float x, final float y, final float size, final int argb,
			final String name, final boolean dim) {
		final int c = dim ? Gui.muted(argb) : argb;
		g.fill(x - 3, y - 3, size + 6, size + 6, 0xFF6D6658);
		g.fill(x, y, size, size, c);
		g.gradient(x, y, size, size, 0x00000000, 0x99000000, true);
		if ((name != null) && !name.isEmpty()) {
			g.text(name.substring(0, 1).toUpperCase(), x, y, size, size, size * 0.5f, 0xFFFFFFFF, Align.center);
		}
	}

	// ------------------------------------------------------------------ create

	private void createPage(final Gui g, final LauncherBoard b, final boolean t) {
		final float top = b.contentY + tabsHeight(b);
		final float h = b.contentH - tabsHeight(b);
		final float pf = b.pageFrame;
		final float gap = t ? 36 : 10;
		final float listW = t ? 700 : 430;
		final float x = b.contentX;
		g.panel(x, top, listW, h, pf);
		if (this.s.maps == null) {
			g.text(this.s.s("game_loading_list"), x + pf + 8, top + pf + 12, listW, t ? 112 : 96, b.body,
					g.palette.text2, Align.left);
		}
		else {
			this.s.lanMapList = true;
			try {
				this.s.mapList(x + pf + 8, top + pf + 12, listW - (2 * pf) - 16, h - (2 * pf) - 24, t ? 100 : 72);
			}
			finally {
				this.s.lanMapList = false;
			}
		}
		final GameCatalog.MapDetails details = this.s.mapDetails;
		final float rx = x + listW + gap;
		final float rw = b.contentW - listW - gap;
		final float ms = t ? 320 : 150;
		final float th = t ? ms + (2 * pf) + 190 : ms + (2 * pf) + 4;
		g.panel(rx, top, rw, th, pf);
		if (details != null) {
			final float mx = rx + pf + (t ? 8 : 2);
			final float my = top + pf + (t ? 8 : 2);
			g.fill(mx - 3, my - 3, ms + 6, ms + 6, 0xFF555555);
			g.fill(mx, my, ms, ms, 0xFF000000);
			g.image(this.s.minimap != null ? this.s.minimap : this.s.art.image(SkinArt.MINIMAP_UNKNOWN), mx, my, ms, ms);
			final float ix = mx + ms + (t ? 36 : 14);
			final float iw = (rx + rw) - ix - pf;
			final float fs = t ? 28 : 16;
			final float kx = t ? 200 : 110;
			g.text(this.s.mapTitle(details), ix, my - 4, iw, t ? 50 : 30, t ? 40 : 22, g.palette.text, Align.left);
			final String[][] facts = { { LauncherText.get("lan_players"), details.suggested }, { LanText.mapSize(), details.size },
				{ LauncherText.get("lan_tileset"), details.tileset } };
			for (int i = 0; i < facts.length; i++) {
				final float fy = my + (t ? 60 : 32) + (i * fs * 1.5f);
				g.text(facts[i][0], ix, fy, kx, fs * 1.4f, fs, g.palette.gold, Align.left);
				g.text(facts[i][1], ix + kx, fy, iw - kx, fs * 1.4f, fs, g.palette.text, Align.left);
			}
			if (t) {
				g.lines(details.description, mx, my + ms + 24, rw - (2 * pf) - 16, 150, 26, g.palette.text2, Align.left);
			}
		}
		final float sy = top + th + gap;
		final float sh = h - th - gap;
		g.panel(rx, sy, rw, sh, pf);
		final float lx = rx + pf + 8;
		final String[] speeds = LanText.speeds();
		final String[] observerModes = LanText.observerModes();
		if (t) {
			final float fx = rx + pf + 330;
			final float fw = rw - (2 * pf) - 340;
			g.text(LauncherText.get("lan_game_settings"), lx, sy + pf - 4, 600, 50, 30, g.palette.gold, Align.left);
			g.text(LauncherText.get("lan_game_name"), lx, sy + pf + 48, 300, 80, 28, g.palette.text, Align.left);
			gameNameField("lanName", fx, sy + pf + 48, fw, 80, 28);
			g.text(LauncherText.get("lan_game_speed"), lx, sy + pf + 146, 300, 80, 28, g.palette.text, Align.left);
			g.dropdown("lanSpeed", speeds, this.speed, fx, sy + pf + 146, 420, 80, 28, true, null,
					choice -> this.speed = choice);
			g.text(LauncherText.get("lan_observers"), lx, sy + pf + 238, 300, 80, 28, g.palette.text, Align.left);
			g.dropdown("lanObservers", observerModes, this.observers, fx, sy + pf + 238, 420, 80, 28, true, null,
					choice -> this.observers = choice);
			g.text(this.s.glue("ADVANCED_OPTIONS"), lx, sy + pf + 340, 600, 50, 28, g.palette.gold, Align.left);
			final String[] labels = { LauncherText.get("lan_option_lock_teams"), LauncherText.get("lan_option_teams_together"), LauncherText.get("lan_option_full_shared_control"), LauncherText.get("lan_option_random_races") };
			final float colw = (rw - (2 * pf) - 16) / 2;
			for (int i = 0; i < labels.length; i++) {
				final float ox = lx + ((i % 2) * colw);
				final float oy = sy + pf + 392 + ((i / 2) * 66);
				if (g.check("lanOpt" + i, ox, oy, 46, this.options[i])
						| ((g.hit("lanOptL" + i, ox + 60, oy, colw - 70, 46) & Gui.CLICK) != 0)) {
					this.options[i] = !this.options[i];
				}
				g.text(labels[i], ox + 60, oy, colw - 70, 46, 26, g.palette.text, Align.left);
			}
		}
		else {
			g.text(LauncherText.get("lan_game_name"), lx, sy + pf - 8, 140, 56, 18, g.palette.gold, Align.left);
			gameNameField("lanName", rx + pf + 150, sy + pf - 8, rw - (2 * pf) - 160, 56, 20);
			g.text(LauncherText.get("lan_speed"), lx, sy + pf + 54, 140, 56, 18, g.palette.gold, Align.left);
			g.dropdown("lanSpeed", speeds, this.speed, rx + pf + 150, sy + pf + 54, 200, 56, 20, true, null,
					choice -> this.speed = choice);
			g.text(LauncherText.get("lan_observers"), rx + pf + 372, sy + pf + 54, 110, 56, 18, g.palette.gold, Align.left);
			g.dropdown("lanObservers", observerModes, this.observers, rx + pf + 480, sy + pf + 54,
					rw - (2 * pf) - 490, 56, 20, true, null, choice -> this.observers = choice);
		}
		if (g.button("lanBack", glueBack(), b.backX, b.backY, b.backW, b.backH, t ? 36 : 30, Gui.TONE_NORMAL)) {
			this.s.playClick();
			this.s.showPage(LauncherScreen.Page.HOME);
		}
		final float[] go = b.actionSlot(2);
		final boolean ready = (details != null) && !this.creating && this.model.networkAvailable();
		if (g.button("lanCreate", LauncherText.get("lan_create_game"), go[0], b.backY, go[1], b.backH, t ? 36 : 30,
				!ready ? Gui.TONE_OFF : this.s.art.reforged ? Gui.TONE_CONFIRM : Gui.TONE_DOWN)) {
			this.s.playClick();
			create(details);
		}
	}

	private void gameNameField(final Object id, final float x, final float y, final float w, final float h,
			final float size) {
		if (!this.nameEdited && (this.gameName.length() == 0)) {
			this.gameName.append(LanText.defaultGameName(this.s.profiles.current()));
		}
		final String before = this.gameName.toString();
		this.s.gui.field(id, this.gameName, x, y, w, h, size, LanProtocol.MAX_GAME_NAME,
				LanText.defaultGameName(this.s.profiles.current()));
		if (!before.equals(this.gameName.toString())) {
			this.nameEdited = true;
		}
	}

	private void create(final GameCatalog.MapDetails details) {
		this.creating = true;
		final String path = details.path;
		this.s.catalog.lanMap(path, this.env.mapIndex(), shared -> {
			this.creating = false;
			if (shared == null) {
				this.noticeTitle = LauncherText.get("lan_create_game");
				this.noticeLine = LauncherText.get("lan_map_cannot_be_shared");
				this.s.dialog = LauncherScreen.Dialog.LAN_NOTICE;
				return;
			}
			final LanLauncherModel.CreateSettings settings = new LanLauncherModel.CreateSettings();
			settings.mapFile = shared.file;
			settings.map = shared.info;
			settings.maxSlots = shared.playerSlots;
			settings.syncWarning = shared.usesSync;
			settings.issue = shared.issue;
			settings.gameName = this.gameName.toString();
			settings.speed = this.speed;
			settings.observers = this.observers;
			settings.lockTeams = this.options[0];
			settings.teamsTogether = this.options[1];
			settings.fullSharedControl = this.options[2];
			settings.randomRaces = this.options[3];
			final String problem = this.model.create(settings);
			if (problem != null) {
				this.noticeTitle = LauncherText.get("lan_create_game");
				this.noticeLine = problem;
				this.s.dialog = LauncherScreen.Dialog.LAN_NOTICE;
			}
		});
	}

	// ------------------------------------------------------------------- lobby

	private void lobbyPage(final Gui g, final LauncherBoard b, final boolean t) {
		final LanLobbyView view = this.model.lobby();
		if (view == null) {
			g.text(LanText.searching(), b.contentX, b.contentY, b.contentW, b.contentH, b.title, g.palette.gold, Align.center);
			return;
		}
		final float pf = b.pageFrame;
		final float gap = t ? 36 : 10;
		final float rightW = t ? 760 : 470;
		final float leftW = b.contentW - rightW - gap;
		final float x = b.contentX;
		final float top = b.contentY;
		g.panel(x, top, leftW, b.contentH, pf);
		final boolean countdown = this.model.mode() == LanLauncherModel.Mode.COUNTDOWN;
		g.text(view.title() + "  ·  " + view.readyText(), x + pf + 12, top + pf - 8, leftW - (2 * pf) - 24, 50,
				t ? 34 : 20, g.palette.text, Align.left);
		final float gp = t ? 12 : 6;
		final float rowH = t ? 104 : 92;
		final float ph = t ? 84 : 72;
		final float pp = t ? 84 : 66;
		final float raceW = t ? 300 : 150;
		final float teamW = t ? 130 : 76;
		final float colW = t ? 100 : 62;
		final float hcW = t ? 180 : 0;
		final float rdW = t ? 60 : 34;
		final float x0 = x + pf + 8;
		final float x1 = x0 + pp + (t ? 20 : 8);
		final float nameW = t ? 260 : 150;
		final float xr = x1 + nameW;
		final float listTop = top + pf + (t ? 96 : 28);
		final float listH = b.contentH - pf - (listTop - top) - pf;
		if (t) {
			final float hy = listTop - 34;
			final String[] heads = { this.s.glue("RACE"), this.s.glue("TEAM"), this.s.glue("COLOR"), LauncherText.get("lan_handicap") };
			final float[] hx = { xr, xr + raceW + gp, xr + raceW + teamW + (2 * gp), xr + raceW + teamW + colW + (3 * gp) };
			for (int i = 0; i < heads.length; i++) {
				g.text(heads[i], hx[i], hy, 200, 28, 22, g.palette.text2, Align.left);
			}
		}
		final int watchRows = view.watchers.size() + (view.canWatch ? 2 : 0);
		final float contentH = ((view.rows.size() + (watchRows > 0 ? 1 : 0) + watchRows) * (rowH + gp));
		final float offset = g.beginList("lanSlots", x0 - 4, listTop, leftW - (2 * pf) - 4, listH, contentH);
		float y = listTop - offset;
		final String[] races = this.s.glueList(CustomGame.RACES, 0);
		final String[] colorNames = new String[CustomGame.COLOR_NAMES.length];
		for (int i = 0; i < colorNames.length; i++) {
			colorNames[i] = this.s.glue(CustomGame.COLOR_NAMES[i][0]);
		}
		final String[] teams = new String[Math.max(1, view.rows.size())];
		for (int i = 0; i < teams.length; i++) {
			teams[i] = Integer.toString(i + 1);
		}
		final String[] handicaps = { "100%", "90%", "80%", "70%", "60%", "50%" };
		for (final LanLobbyView.Row row : view.rows) {
			final float ry = y + ((rowH - ph) / 2);
			drawSlot(g, view, row, x0, x1, xr, y, ry, rowH, ph, pp, nameW, raceW, teamW, colW, hcW, rdW, gp, t, races,
					teams, colorNames, handicaps);
			y += rowH + gp;
		}
		if (watchRows > 0) {
			g.text(LauncherText.get("lan_observers_referees_head"), x0, y + (rowH * 0.3f), 600, rowH * 0.4f, t ? 28 : 17, g.palette.gold,
					Align.left);
			y += rowH + gp;
			for (final LanLobbyView.Watcher w : view.watchers) {
				portrait(g, x0, y + ((rowH - pp) / 2), pp, w.portrait, 0xFF777777, w.name);
				if (w.kickable) {
					final int who = w.participantId;
					g.dropdown("lanWatcher" + who, new String[] { w.name, LauncherText.get("lan_kick") }, 0, x1, y + (rowH * 0.05f),
							nameW - 8, rowH * 0.46f, t ? 30 : 17, true, null, choice -> {
								if (choice == 1) {
									this.model.kickWatcher(who);
								}
							});
				}
				else {
					g.text(w.name + (w.you ? " (you)" : ""), x1, y + (rowH * 0.1f), nameW * 2, rowH * 0.4f,
							t ? 34 : 19, g.palette.text, Align.left);
				}
				if (w.keyboardAndMouse) {
					kbm(g, w.kickable ? x1 + nameW : x1 + Math.min(nameW * 1.8f, g.width(w.name, t ? 34 : 19) + 14),
							y + (rowH * 0.12f), t ? 36 : 24, "lanKbmW" + w.participantId);
				}
				g.text(LanText.role(w.role) + (w.role == LanProtocol.ROLE_REFEREE ? " · can pause, chats with all" : ""),
						x1, y + (rowH * 0.52f), nameW * 3, rowH * 0.36f, t ? 22 : 14, g.palette.gold, Align.left);
				y += rowH + gp;
			}
			if (view.canWatch && (view.myRole == LanProtocol.ROLE_PLAYER)) {
				final boolean referees = view.options.observers == LanProtocol.OBSERVERS_REFEREES;
				final int state = g.hit("lanWatch", x0, y, nameW * 3, rowH);
				if ((state & Gui.HOVER) != 0) {
					g.image(this.s.art.image(SkinArt.ROW_HIGHLIGHT), x0, y, nameW * 3, rowH, 0.5f);
				}
				g.text(referees ? LauncherText.get("lan_watch_referee") : LauncherText.get("lan_watch_observer"), x1, y, nameW * 3, rowH, t ? 30 : 18,
						g.palette.text2, Align.left);
				if ((state & Gui.CLICK) != 0) {
					this.s.playClick();
					this.model.watch(referees);
				}
			}
		}
		g.endList();

		// map summary, chat
		final float rx = x + leftW + gap;
		final float ms = t ? 300 : 130;
		final float th = ms + (2 * pf) + (t ? 16 : 4);
		g.panel(rx, top, rightW, th, pf);
		final float mx = rx + pf + (t ? 8 : 2);
		final float my = top + pf + (t ? 8 : 2);
		g.fill(mx - 3, my - 3, ms + 6, ms + 6, 0xFF555555);
		g.fill(mx, my, ms, ms, 0xFF000000);
		final boolean known = (this.s.mapDetails != null) && this.model.isHosting() && (this.s.minimap != null);
		g.image(known ? this.s.minimap : this.s.art.image(SkinArt.MINIMAP_UNKNOWN), mx, my, ms, ms);
		final float fx = mx + ms + (t ? 30 : 12);
		final float fw = (rx + rightW) - fx - pf;
		g.text(view.mapLabel(), fx, my - 4, fw, t ? 46 : 26, t ? 34 : 18, g.palette.text, Align.left);
		final float fs = t ? 28 : 15;
		final float kx = t ? 250 : 120;
		final String[][] facts = { { LauncherText.get("lan_game_speed_colon"), LanText.speed(view.options.speed) },
			{ LanText.observersLabel(), LanText.observers(view.options.observers) },
			{ LauncherText.get("lan_lock_teams_colon"), view.options.lockTeams ? LanText.yes() : LanText.no() },
			{ LauncherText.get("lan_teams_together_colon"), view.options.teamsTogether ? LanText.yes() : LanText.no() } };
		for (int i = 0; i < (t ? 4 : 3); i++) {
			final float fy = my + (t ? 56 : 28) + (i * fs * 1.55f);
			g.text(facts[i][0], fx, fy, kx, fs * 1.5f, fs, g.palette.gold, Align.left);
			g.text(facts[i][1], fx + kx, fy, fw - kx, fs * 1.5f, fs, g.palette.text, Align.left);
		}
		final float cy = top + th + gap;
		final float ch = b.contentH - th - gap;
		g.panel(rx, cy, rightW, ch, pf);
		final float eh = t ? 90 : 56;
		final float lf = t ? 26 : 15;
		final float lh = t ? 42 : 22;
		final float inner = rightW - (2 * pf) - 20;
		float chatBottom = (cy + ch) - pf - eh - 12;
		if (view.syncWarning) {
			final float sh = t ? 90 : 48;
			g.fill(rx + pf + 6, chatBottom - sh, rightW - (2 * pf) - 12, sh, 0x8C503C00);
			g.outline(rx + pf + 6, chatBottom - sh, rightW - (2 * pf) - 12, sh, 2, 0xFF8A6A10);
			g.lines("⚠ " + LanText.syncWarning(), rx + pf + 14, chatBottom - sh + 6, rightW - (2 * pf) - 28, sh - 8,
					t ? 24 : 13, 0xFFFFD24A, Align.left);
			chatBottom -= sh + 6;
		}
		final List<LanLauncherModel.ChatLine> lines = this.model.chat();
		final int shown = Math.max(0, (int) ((chatBottom - (cy + pf)) / lh));
		float ly = chatBottom - (Math.min(shown, lines.size()) * lh);
		for (int i = Math.max(0, lines.size() - shown); i < lines.size(); i++) {
			final LanLauncherModel.ChatLine line = lines.get(i);
			if (line.system) {
				g.text(line.text, rx + pf + 10, ly, inner, lh, lf, g.palette.text2, Align.left);
			}
			else {
				final float nameWidth = g.width(line.from + ": ", lf);
				g.text(line.from + ":", rx + pf + 10, ly, nameWidth, lh, lf, g.palette.gold, Align.left);
				g.text(line.text, rx + pf + 10 + nameWidth, ly, inner - nameWidth, lh, lf, g.palette.text, Align.left);
			}
			ly += lh;
		}
		if (this.model.showHostTip()) {
			g.lines(LanText.hostNotFoundTip(), rx + pf + 10, cy + pf, inner, 120, lf * 0.9f, g.palette.text2, Align.left);
		}
		final float ey = (cy + ch) - pf - eh;
		g.field("lanChat", this.chat, rx + pf + 4, ey, rightW - (2 * pf) - 8, eh, lf, 120, LauncherText.get("lan_chat_hint"));
		if (g.keys.contains(Input.Keys.ENTER) && "lanChat".equals(g.focus) && (this.chat.length() > 0)) {
			this.model.sendChat(this.chat.toString());
			this.chat.setLength(0);
		}

		// actions
		if (g.button("lanLeave", LauncherText.get("lan_leave"), b.backX, b.backY, b.backW, b.backH, t ? 36 : 30, Gui.TONE_NORMAL)) {
			this.s.playClick();
			leave();
		}
		final float[] sec = b.actionSlot(1);
		final float[] pri = b.actionSlot(2);
		if (view.iAmHost) {
			if (countdown) {
				if (g.button("lanCancel", this.s.glue("CANCEL"), sec[0], b.backY, sec[1], b.backH, t ? 36 : 30, Gui.TONE_NORMAL)) {
					this.s.playClick();
					this.model.cancelStart();
				}
			}
			else if (g.button("lanChangeMap", LanText.changeMap(), sec[0], b.backY, sec[1], b.backH, t ? 32 : 24,
					Gui.TONE_NORMAL)) {
				this.s.playClick();
				this.s.dialog = LauncherScreen.Dialog.LAN_MAP;
			}
			final LanLobbyView.HostAction action = view.hostAction();
			final String label = countdown ? LauncherText.get("lan_starting", this.model.countdownSeconds())
					: action == LanLobbyView.HostAction.START ? this.s.glue("KEY_START_GAME")
							: action == LanLobbyView.HostAction.NOT_READY ? LauncherText.get("lan_not_ready") : LauncherText.get("lan_ready");
			final boolean usable = !countdown && ((action == LanLobbyView.HostAction.START) || this.model.canReady());
			if (g.button("lanStart", label, pri[0], b.backY, pri[1], b.backH, t ? 36 : 30,
					!usable ? Gui.TONE_OFF
							: action == LanLobbyView.HostAction.START
									? (this.s.art.reforged ? Gui.TONE_CONFIRM : Gui.TONE_DOWN)
									: action == LanLobbyView.HostAction.NOT_READY ? Gui.TONE_DOWN : Gui.TONE_NORMAL)) {
				this.s.playClick();
				if (usable) {
					this.model.hostPrimary();
				}
			}
		}
		else {
			final boolean mine = (view.mine != null) && view.mine.ready;
			if (g.button("lanReady", mine ? LauncherText.get("lan_not_ready") : LauncherText.get("lan_ready"), sec[0], b.backY, sec[1], b.backH, t ? 36 : 30,
					!this.model.canReady() ? Gui.TONE_OFF : mine ? Gui.TONE_DOWN : Gui.TONE_NORMAL)) {
				this.s.playClick();
				this.model.setReady(!mine);
			}
			g.button("lanStartOff", this.s.glue("KEY_START_GAME"), pri[0], b.backY, pri[1], b.backH, t ? 36 : 30, Gui.TONE_OFF);
		}
	}

	private void drawSlot(final Gui g, final LanLobbyView view, final LanLobbyView.Row row, final float x0,
			final float x1, final float xr, final float y, final float ry, final float rowH, final float ph,
			final float pp, final float nameW, final float raceW, final float teamW, final float colW, final float hcW,
			final float rdW, final float gp, final boolean t, final String[] races, final String[] teams,
			final String[] colorNames, final String[] handicaps) {
		final int slot = row.slot;
		final float size = t ? 30 : 17;
		final boolean human = row.kind == LanLobbyView.PLAYER;
		final boolean player = human || row.kind == LanLobbyView.COMPUTER;
		if (player) {
			portrait(g, x0, y + ((rowH - pp) / 2), pp, row.portrait,
					CustomGame.PLAYER_COLORS[Math.floorMod(row.color, CustomGame.PLAYER_COLORS.length)],
					row.kind == LanLobbyView.COMPUTER ? "C" : row.name);
		}
		else {
			g.outline(x0, y + ((rowH - pp) / 2), pp, pp, 3, 0xFF4A4030);
		}
		if (row.hostMenu) {
			final String[] items;
			final int selected;
			if (human) {
				items = new String[] { row.name, LauncherText.get("lan_kick") };
				selected = 0;
			}
			else {
				items = new String[] { this.s.glue("OPEN"), this.s.glue("CLOSED"), LanText.computer(0), LanText.computer(1), LanText.computer(2) };
				selected = row.kind == LanLobbyView.OPEN ? 0 : row.kind == LanLobbyView.CLOSED ? 1 : 2 + row.computerLevel;
			}
			g.dropdown("lanSlot" + slot, items, selected, x1, ry, nameW - 8, ph, size, true, null, choice -> {
				if (human) {
					if (choice == 1) {
						this.model.hostSlot(slot, LanProtocol.ACTION_KICK, 0);
					}
				}
				else if (choice == 0) {
					this.model.hostSlot(slot, LanProtocol.ACTION_OPEN, 0);
				}
				else if (choice == 1) {
					this.model.hostSlot(slot, LanProtocol.ACTION_CLOSE, 0);
				}
				else {
					this.model.hostSlot(slot, LanProtocol.ACTION_COMPUTER, choice - 2);
				}
			});
		}
		else if (row.kind == LanLobbyView.OPEN) {
			final int state = g.hit("lanOpen" + slot, x1, ry, nameW - 8, ph);
			if ((state & Gui.HOVER) != 0) {
				g.image(this.s.art.image(SkinArt.ROW_HIGHLIGHT), x1 - 4, ry, nameW, ph, 0.5f);
			}
			g.text(row.name, x1, ry, nameW - 8, ph, size, g.palette.text2, Align.left);
			if (((state & Gui.CLICK) != 0) && (view.myRole == LanProtocol.ROLE_PLAYER || view.mine != null || true)) {
				this.s.playClick();
				this.model.takeSlot(slot);
			}
			return;
		}
		else {
			final boolean kbmShown = row.keyboardAndMouse;
			final float nameMax = nameW - 8 - (kbmShown ? (t ? 66 : 44) : 0);
			g.text(row.name, x1, y + (rowH * 0.08f), nameMax, rowH * 0.46f, t ? 34 : 19,
					player ? g.palette.text : g.palette.text2, Align.left);
			if (kbmShown) {
				final float kx = x1 + Math.min(nameMax, g.width(row.name, t ? 34 : 19) + (t ? 10 : 6));
				kbm(g, kx, y + (rowH * 0.12f), t ? 36 : 24, "lanKbm" + slot);
			}
			final String tag = row.host && row.you ? "HOST · you" : row.host ? "HOST" : row.you ? "you" : "";
			if (!tag.isEmpty()) {
				g.text(tag, x1, y + (rowH * 0.56f), nameMax, rowH * 0.34f, t ? 22 : 14, g.palette.gold, Align.left);
			}
		}
		if (!player) {
			return;
		}
		if (row.mapPercent >= 0) {
			final float bw = raceW + teamW + colW + hcW + (3 * gp) + (t ? gp : 0);
			final float bh = t ? 32 : 24;
			g.text(row.mapSending ? LanText.sending(row.mapPercent)
					: this.model.mapProgressText(), xr, ry, bw, ph - bh - 4, t ? 26 : 15,
					row.mapSending ? g.palette.text2 : g.palette.gold, Align.left);
			g.fill(xr, (ry + ph) - bh - 2, bw, bh, 0xFF1A1A1A);
			g.fill(xr + 2, (ry + ph) - bh, Math.max(0, (bw - 4) * row.mapPercent / 100f), bh - 4,
					row.mapSending ? 0xFF4A78C0 : 0xFFD8A820);
			g.outline(xr, (ry + ph) - bh - 2, bw, bh, 2, 0xFF6D6658);
			return;
		}
		final boolean edit = row.editable;
		final int tone = edit ? 0 : 1;
		g.dropdown("lanRace" + slot, races, Math.min(row.race, races.length - 1), xr, ry, raceW, ph, size, edit, null,
				choice -> sendMine(view, row, choice, row.team, row.color, row.handicap));
		g.dropdown("lanTeam" + slot, teams, Math.min(row.team, teams.length - 1), xr + raceW + gp, ry, teamW, ph, size,
				edit, null, choice -> sendMine(view, row, row.race, choice, row.color, row.handicap));
		g.dropdown("lanColor" + slot, colorNames, Math.floorMod(row.color, CustomGame.PLAYER_COLORS.length),
				xr + raceW + teamW + (2 * gp), ry, colW, ph, size, edit, CustomGame.PLAYER_COLORS,
				choice -> sendMine(view, row, row.race, row.team, choice, row.handicap));
		if (t) {
			g.dropdown("lanHandicap" + slot, handicaps, Math.max(0, Math.min(5, (100 - row.handicap) / 10)),
					xr + raceW + teamW + colW + (3 * gp), ry, hcW, ph, size, edit, null,
					choice -> sendMine(view, row, row.race, row.team, row.color, 100 - (choice * 10)));
		}
		if (human) {
			final float cx = xr + raceW + teamW + colW + hcW + ((t ? 4 : 3) * gp) + 6;
			final float box = t ? 44 : 30;
			g.image(SkinArt.CHECKBOX, cx, y + ((rowH - box) / 2), box, box);
			if (row.ready) {
				g.image(SkinArt.CHECKBOX_CHECK, cx, y + ((rowH - box) / 2), box, box);
			}
			if (t && (row.pingMs > 0)) {
				g.text(row.pingMs + " ms", cx + rdW + gp, y, 150, rowH, 24, g.palette.text2, Align.left);
			}
		}
	}

	/** The choices of this player's own row go to the host; the host's of a computer's row too. */
	private void sendMine(final LanLobbyView view, final LanLobbyView.Row row, final int race, final int team,
			final int color, final int handicap) {
		view.choose(this.model, row, race, team, color, handicap);
	}

	/** The keyboard and mouse mark after a name; hover (or a tap) shows what it means. */
	private void kbm(final Gui g, final float x, final float y, final float size, final Object id) {
		final float w = size * 1.5f;
		final int state = g.hit(id, x - 6, y - 6, w + 12, size + 12);
		final int c = 0xFFE8D9A8;
		g.outline(x, y + (size * 0.2f), size, size * 0.62f, Math.max(2, size * 0.07f), c);
		for (int i = 0; i < 4; i++) {
			g.fill(x + (size * 0.12f) + (i * size * 0.2f), y + (size * 0.34f), size * 0.1f, size * 0.12f, c);
		}
		g.fill(x + (size * 0.22f), y + (size * 0.56f), size * 0.56f, size * 0.12f, c);
		g.outline(x + (size * 1.12f), y + (size * 0.16f), size * 0.38f, size * 0.66f, Math.max(2, size * 0.07f), c);
		g.fill(x + (size * 1.3f) - 1, y + (size * 0.16f), 2, size * 0.26f, c);
		if ((state & Gui.CLICK) != 0) {
			this.tipFor = id;
			this.tipUntil = System.currentTimeMillis() + 2000;
		}
		if (((state & Gui.HOVER) != 0) || (id.equals(this.tipFor) && (System.currentTimeMillis() < this.tipUntil))) {
			g.tip(LanText.kbmTooltip());
		}
	}

	private void leave() {
		final String confirm = this.model.leaveConfirmation();
		if (confirm != null) {
			this.s.dialog = LauncherScreen.Dialog.LAN_LEAVE;
		}
		else {
			this.model.leave();
		}
	}

	// ----------------------------------------------------------------- dialogs

	void dialog(final LauncherScreen.Dialog dialog) {
		switch (dialog) {
		case LAN_IP:
			ipDialog();
			break;
		case LAN_NOTICE:
			this.s.question(this.noticeTitle, this.noticeLine, new String[] { this.s.s("game_ok") }, choice -> {
			}, 0);
			break;
		case LAN_BLOCK:
			this.s.question(LanText.blockTitle(), LanText.blockText(this.blockedHosting),
					new String[] { this.s.glue("CANCEL"), LauncherText.get("lan_leave_lobby"), LauncherText.get("lan_back_to_lobby") }, choice -> {
						if (choice == 1) {
							leave();
						}
						else if (choice == 2) {
							this.s.showPage(LauncherScreen.Page.LAN);
						}
					}, 2);
			break;
		case LAN_MAP:
			mapDialog();
			break;
		case LAN_LEAVE:
			this.s.question(LanText.leaveTitle(), this.model.leaveConfirmation() == null ? "" : this.model.leaveConfirmation(),
					new String[] { this.s.glue("CANCEL"), LauncherText.get("lan_leave_lobby") }, choice -> {
						if (choice == 1) {
							this.model.leave();
						}
					}, 0);
			break;
		default:
			break;
		}
	}

	/** The host picks another map for the lobby it made. */
	private void mapDialog() {
		final Gui g = this.s.gui;
		final LauncherBoard b = this.s.board;
		final boolean t = b.tablet;
		final float edge = t ? 48 : 32;
		final float w = Math.min(b.width - 80, t ? 1900 : 1400);
		final float h = Math.min(b.height - 60, t ? 1250 : 820);
		final float x = (b.width - w) / 2;
		final float y = (b.height - h) / 2;
		g.dialog(x, y, w, h);
		g.text(LanText.changeMapTitle(), x + edge + 20, y + edge, w, t ? 70 : 44, t ? 52 : 30, g.palette.gold,
				Align.left);
		final float bw = t ? 420 : 300;
		final float bh = t ? 128 : 96;
		final float by = (y + h) - edge - bh;
		final float top = y + edge + (t ? 90 : 56);
		if (this.s.maps == null) {
			g.text(this.s.s("game_loading_list"), x + edge + 20, top, w, t ? 112 : 96, b.body, g.palette.text2,
					Align.left);
		}
		else {
			final float listW = (w * 0.56f);
			this.s.lanMapList = true;
			try {
				this.s.mapList(x + edge + 20, top, listW, (by - top) - 20, t ? 100 : 72);
			}
			finally {
				this.s.lanMapList = false;
			}
			final GameCatalog.MapDetails details = this.s.mapDetails;
			if (details != null) {
				final float rx = x + edge + 40 + listW;
				final float rw = (x + w) - edge - 20 - rx;
				final float ms = Math.min(rw, t ? 320 : 150);
				g.fill(rx - 3, top - 3, ms + 6, ms + 6, 0xFF555555);
				g.fill(rx, top, ms, ms, 0xFF000000);
				g.image(this.s.minimap != null ? this.s.minimap : this.s.art.image(SkinArt.MINIMAP_UNKNOWN), rx, top,
						ms, ms);
				final float fs = t ? 28 : 16;
				g.text(this.s.mapTitle(details), rx, top + ms + 14, rw, fs * 1.6f, fs * 1.3f, g.palette.text, Align.left);
				final String[][] facts = { { LauncherText.get("lan_players"), details.suggested }, { LanText.mapSize(), details.size } };
				for (int i = 0; i < facts.length; i++) {
					final float fy = top + ms + 14 + (fs * 2) + (i * fs * 1.5f);
					g.text(facts[i][0], rx, fy, rw * 0.45f, fs * 1.4f, fs, g.palette.gold, Align.left);
					g.text(facts[i][1], rx + (rw * 0.45f), fy, rw * 0.55f, fs * 1.4f, fs, g.palette.text, Align.left);
				}
			}
		}
		boolean close = false;
		if (g.button("lanMapCancel", this.s.glue("CANCEL"), x + edge + 20, by, bw, bh, t ? 36 : 26,
				Gui.TONE_NORMAL)) {
			close = true;
		}
		final boolean ready = (this.s.mapDetails != null) && !this.creating;
		if (g.button("lanMapSelect", this.s.s("game_ok"), (x + w) - edge - 20 - bw, by, bw, bh, t ? 36 : 26,
				ready ? Gui.TONE_CONFIRM : Gui.TONE_OFF) && ready) {
			this.s.playClick();
			changeMap(this.s.mapDetails);
			close = true;
		}
		if ((this.model.mode() != LanLauncherModel.Mode.LOBBY) || !this.model.isHosting()) {
			close = true;
		}
		if (close) {
			this.s.playClick();
			this.s.dialog = LauncherScreen.Dialog.NONE;
		}
	}

	private void changeMap(final GameCatalog.MapDetails details) {
		this.creating = true;
		this.s.catalog.lanMap(details.path, this.env.mapIndex(), shared -> {
			this.creating = false;
			String problem;
			if (shared == null) {
				problem = LauncherText.get("lan_map_cannot_be_shared");
			}
			else {
				final LanLauncherModel.CreateSettings settings = new LanLauncherModel.CreateSettings();
				settings.mapFile = shared.file;
				settings.map = shared.info;
				settings.maxSlots = shared.playerSlots;
				settings.syncWarning = shared.usesSync;
				settings.issue = shared.issue;
				problem = this.model.changeMap(settings);
			}
			if (problem != null) {
				this.noticeTitle = LanText.changeMap();
				this.noticeLine = problem;
				this.s.dialog = LauncherScreen.Dialog.LAN_NOTICE;
			}
		});
	}

	private void ipDialog() {
		final Gui g = this.s.gui;
		final LauncherBoard b = this.s.board;
		final boolean t = b.tablet;
		final float w = t ? 1400 : 1080;
		final float h = t ? 900 : 404;
		final float x = (b.width - w) / 2;
		final float y = t ? 344 : 16;
		final float edge = t ? 48 : 32;
		g.dialog(x, y, w, h);
		g.text(LanText.joinByIp(), x, y + edge, w, t ? 70 : 44, t ? 52 : 30, g.palette.gold, Align.center);
		g.text(LanText.joinByIpHint(), x + edge + 20, y + edge + (t ? 90 : 52), w, t ? 44 : 30, t ? 30 : 19,
				g.palette.text2, Align.left);
		g.focus = "lanIp";
		g.field("lanIp", this.ip, x + edge + 20, y + edge + (t ? 140 : 84), w - (2 * edge) - 40, t ? 120 : 90,
				t ? 40 : 26, 21, "192.168.1.40");
		final String problem = this.model.ipError();
		g.text(problem != null ? problem : "", x + edge + 20, y + edge + (t ? 270 : 168), w - (2 * edge) - 40,
				t ? 44 : 30, t ? 30 : 19, RED, Align.left);
		g.text(LanText.joinByIpPort(), x + edge + 20, y + edge + (t ? 320 : 196), w, t ? 44 : 30, t ? 26 : 17,
				g.palette.text2, Align.left);
		final float bw = t ? 420 : 300;
		final float bh = t ? 128 : 96;
		final float by = (y + h) - edge - bh;
		boolean close = false;
		if (g.button("lanIpCancel", this.s.glue("CANCEL"), x + edge + 20, by, bw, bh, t ? 36 : 26,
				Gui.TONE_NORMAL)) {
			close = true;
		}
		final boolean connecting = this.model.isConnecting();
		if ((g.button("lanIpConnect", LauncherText.get("lan_connect"), (x + w) - edge - 20 - bw, by, bw, bh, t ? 36 : 26,
				connecting ? Gui.TONE_OFF : Gui.TONE_CONFIRM) || g.keys.contains(Input.Keys.ENTER)) && !connecting) {
			this.s.playClick();
			this.model.joinByIp(this.ip.toString());
		}
		if (this.model.mode() != LanLauncherModel.Mode.JOIN) {
			close = true;
		}
		if (close) {
			this.s.playClick();
			g.focus = null;
			this.s.dialog = LauncherScreen.Dialog.NONE;
		}
	}

	String plain(final String text) {
		return GlueText.plain(text);
	}
}
