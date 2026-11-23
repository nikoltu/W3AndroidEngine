package com.etheller.warsmash.launcher.lan;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.etheller.warsmash.lan.LanCommandValidator;
import com.etheller.warsmash.lan.LanGameStart;
import com.etheller.warsmash.lan.LanSession;
import com.etheller.warsmash.lan.LanSessions;
import com.etheller.warsmash.launcher.LaunchRequest;
import com.etheller.warsmash.launcher.MapIssue;
import com.etheller.warsmash.launcher.LauncherLog;
import com.etheller.warsmash.launcher.LauncherSlot;

import net.warsmash.lan.LanGameSetup;
import net.warsmash.lan.RejoinKey;
import net.warsmash.lan.RejoinStore;
import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.client.MapDownload;
import net.warsmash.lan.host.LanHost;
import net.warsmash.lan.maps.LanMapIndex;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.protocol.LanTimings;
import net.warsmash.lan.transport.LanDiscovery;
import net.warsmash.lan.transport.LanNetwork;

/**
 * What the LAN pages of the launcher are about, the same on every platform: finding games, joining and creating one,
 * the lobby with its chat and its countdown, the hand over to the engine's loading screen and the way back. Pages
 * ask it what to draw and tell it what the player chose; it talks to the network classes and to nothing visual.
 * <p>
 * Everything here runs on the thread the {@link Env#ui()} executor names (the launcher's), including the network
 * events, which are posted to it.
 */
public final class LanLauncherModel {
	/** Where the player is in the LAN pages. */
	public enum Mode {
		/** Join Game: the games found on the network. */
		JOIN,
		/** Create Game: the maps and the settings of the game to host. */
		CREATE,
		/** Waiting in a lobby, as host or guest. */
		LOBBY,
		/** The host started the game: the countdown runs. */
		COUNTDOWN,
		/** The game is loading in the engine. */
		LOADING,
		/** Catching up with a game already under way. */
		REJOIN
	}

	/** What the platform contributes. */
	public interface Env {
		LanNetwork network();

		/** Finds maps on this device and keeps the ones received. */
		LanMapIndex mapIndex();

		String playerName();

		/** The identity the local profile's portrait travels under; see {@link LanPortraitId}. */
		String portrait();

		/** Whether this device shows the Reforged skin's portraits (else the classic skin's). */
		default boolean reforgedSkin() {
			return false;
		}

		/** {@link LanProtocol#CONTROL_TOUCH} or {@link LanProtocol#CONTROL_KBM}. */
		int controlScheme();

		String engineVersion();

		String buildCommit();

		boolean official();

		long dataChecksum();

		/** Runs events on the thread the pages are drawn from. */
		Executor ui();

		/** Starts the engine on the LAN game; the guard has already let it pass. */
		void launch(LaunchRequest request);

		/**
		 * Tells why the engine cannot play the map file ({@link MapIssue#NONE} when it can) through {@code done}, on
		 * the thread of {@link #ui}; null when the check could not be made (the game data was closed meanwhile).
		 */
		default void mapIssue(final File map, final java.util.function.Consumer<MapIssue> done) {
			done.accept(MapIssue.NONE);
		}

		/** Whether a map of that name is among this device's maps (a game on another map says "map from host"). */
		boolean hasMapNamed(String name);

		/** Where this device keeps the keys of the games it can come back to; null for none. */
		RejoinStore rejoinStore();

		/** Whether a leaving host hands the game to another player (else it ends for all). */
		boolean hostMigration();
	}

	/** A line of the lobby's chat. */
	public static final class ChatLine {
		public final String from;
		public final String text;
		public final boolean system;

		ChatLine(final String from, final String text, final boolean system) {
			this.from = from;
			this.text = text;
			this.system = system;
		}
	}

	/** A game found on the network, as the list shows it. */
	public static final class GameRow {
		public final LanDiscovery.Found found;
		public final String name;
		public final String line;
		public final String tag;
		public final boolean otherVersion;
		public final boolean rejoin;
		public final boolean mapFromHost;
		/** The map's own name, else its file name. */
		public final String mapLabel;
		/** The host's portrait in this device's skin, or -1 when it is unknown or the other skin's. */
		public int portrait = -1;

		GameRow(final LanDiscovery.Found found, final String mapLabel, final String line, final String tag,
				final boolean otherVersion, final boolean rejoin, final boolean mapFromHost) {
			this.found = found;
			this.mapLabel = mapLabel;
			this.name = found.info.gameName;
			this.line = line;
			this.tag = tag;
			this.otherVersion = otherVersion;
			this.rejoin = rejoin;
			this.mapFromHost = mapFromHost;
		}

		public long id() {
			return this.found.info.gameId;
		}

		public boolean joinable() {
			return !this.otherVersion;
		}
	}

	/** The choices of the Create Game page. */
	public static final class CreateSettings {
		public File mapFile;
		public LanData.MapInfo map;
		public int maxSlots = 2;
		public int port = LanProtocol.DEFAULT_PORT;
		public int countdownMs = 5000;
		public boolean syncWarning;
		public String gameName = "";
		public int speed = 2;
		public int observers = LanProtocol.OBSERVERS_NONE;
		public boolean lockTeams;
		public boolean teamsTogether;
		public boolean fullSharedControl;
		public boolean randomRaces;
		/** Why the engine cannot play the map; the lobby is then not made. */
		public MapIssue issue = MapIssue.NONE;
	}

	private static final Pattern ADDRESS = Pattern
			.compile("^\\s*(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?::(\\d{1,5}))?\\s*$");
	private static final int MAX_CHAT_LINES = 200;
	private static final long LIST_REFRESH_MS = 500;
	private static final long PROBE_MS = 3000;
	private static final long HOST_TIP_MS = 10000;
	/** Hashing the maps of the device for games in the list: a few at a time, however many games are announced. */
	private static final int MAX_MAP_CHECKS = 2;

	private final Env env;
	private final LanAttention attention = new LanAttention();
	private final Listener listener = new Listener();
	private final List<ChatLine> chat = new ArrayList<>();
	private final Map<Integer, Integer> loadStates = new HashMap<>();
	private final Map<Integer, Integer> sending = new HashMap<>();
	private final Map<String, Boolean> mapChecks = new HashMap<>();
	private final java.util.Set<String> mapChecking = new java.util.HashSet<>();
	private Mode mode = Mode.JOIN;
	private LanBrowser browser;
	private String browseError;
	private List<GameRow> games = Collections.emptyList();
	private long lastRefresh;
	private LanSession session;
	private boolean hosting;
	private boolean connecting;
	private boolean welcomed;
	private String error;
	private String ipError;
	private LanMessages.LobbyState lobbyState;
	private LanLobbyView lobby;
	private boolean ownReadyAsked;
	private float countdownLeft;
	private int countdownShown;
	private LanGameSetup setup;
	private int mapState = LanClient.MAP_CHECKING;
	private long mapDone;
	private int mapTotal;
	private String mapFailedText;
	private String mapFailedTitle;
	private String mapFailureTitle;
	/** What the engine cannot play of the received map; null while that is being found out. */
	private MapIssue mapIssue;
	private int mapIssueCheck;
	private int rejoinPercent;
	private long lastProbe;
	private final java.util.Set<Long> seenGames = new java.util.HashSet<>();
	private long hostingSince;
	private boolean launched;
	private int version;
	private String gamesSignature = "";

	public LanLauncherModel(final Env env) {
		this.env = env;
	}

	// ------------------------------------------------------------------ queries

	public Mode mode() {
		return this.mode;
	}

	/** Grows with every change a page would draw; pages that are not redrawn every frame compare it. */
	public int version() {
		return this.version;
	}

	private void touch() {
		this.version++;
	}

	private void setMode(final Mode next) {
		if (this.mode != next) {
			this.mode = next;
			touch();
		}
	}

	public LanAttention attention() {
		return this.attention;
	}

	public boolean networkAvailable() {
		return this.env.network().isNetworkAvailable();
	}

	/** True from the moment a lobby is joined or hosted until the way back to the pages. */
	public boolean inSession() {
		return this.session != null;
	}

	public boolean isHosting() {
		return this.hosting;
	}

	public boolean isConnecting() {
		return this.connecting;
	}

	public LanSession session() {
		return this.session;
	}

	public LanLobbyView lobby() {
		return this.lobby;
	}

	public List<ChatLine> chat() {
		return Collections.unmodifiableList(this.chat);
	}

	public List<GameRow> games() {
		return this.games;
	}

	/** Why the search could not run (no socket), or null. */
	public String browseError() {
		return this.browseError;
	}

	/** The last problem to show on the page (a refused join, a lost host); the page clears it when it is read. */
	public String takeError() {
		final String e = this.error;
		this.error = null;
		return e;
	}

	public String error() {
		return this.error;
	}

	/** What the Join by IP dialog shows in red, or null. */
	public String ipError() {
		return this.ipError;
	}

	public void clearIpError() {
		this.ipError = null;
	}

	public int mapState() {
		return this.mapState;
	}

	/** The part of the lobby's map this player has received, 0 to 100, or -1 when it is not being received. */
	public int mapPercent() {
		return this.mapState == LanClient.MAP_DOWNLOADING || this.mapState == LanClient.MAP_VERIFYING
				? (this.mapTotal <= 0 ? 0 : (int) Math.min(100, (this.mapDone * 100) / this.mapTotal)) : -1;
	}

	public String mapProgressText() {
		return LanText.mapProgress(this.mapDone, this.mapTotal);
	}

	public boolean mapReady() {
		return this.mapState == LanClient.MAP_READY;
	}

	/** Set after a download failed; the page shows it in a dialog and calls {@link #takeMapFailure}. */
	public String takeMapFailure() {
		final String t = this.mapFailedText;
		this.mapFailedText = null;
		this.mapFailureTitle = this.mapFailedTitle;
		this.mapFailedTitle = null;
		return t;
	}

	/** The title of the dialog for the failure last taken. */
	public String mapFailureTitle() {
		return this.mapFailureTitle == null ? LanText.mapFailedTitle() : this.mapFailureTitle;
	}

	/** Why the engine cannot play the lobby's map, once it is there and has been looked at. */
	public MapIssue mapIssue() {
		return this.mapIssue == null ? MapIssue.NONE : this.mapIssue;
	}

	/** Seconds left of the countdown, rounded up; 0 outside it. */
	public int countdownSeconds() {
		return this.mode == Mode.COUNTDOWN ? (int) Math.ceil(this.countdownLeft) : 0;
	}

	public int rejoinPercent() {
		return this.rejoinPercent;
	}

	public LanGameSetup setup() {
		return this.setup;
	}

	/** The loading percent of a participant, or -1. */
	public int loadPercent(final int participantId) {
		final Integer p = this.loadStates.get(participantId);
		return p == null ? -1 : p;
	}

	/** Whether the host's lobby has gone a while unnoticed: the tip about the firewall. */
	public boolean showHostTip() {
		return this.hosting && (this.mode == Mode.LOBBY) && (this.lobby != null) && (this.lobby.humans <= 1)
				&& ((System.currentTimeMillis() - this.hostingSince) > HOST_TIP_MS);
	}

	/** Ready can be pressed: this player plays, has the map, and is in a lobby. */
	public boolean canReady() {
		return (this.mode == Mode.LOBBY) && (this.lobby != null) && (this.lobby.mine != null) && mapReady()
				&& (this.mapIssue == MapIssue.NONE);
	}

	// ------------------------------------------------------------ page changes

	/** The Join Game page is shown: the search runs. */
	public void showJoin() {
		if (inSession()) {
			return;
		}
		setMode(Mode.JOIN);
		this.ipError = null;
		startBrowsing();
	}

	public void showCreate() {
		if (inSession()) {
			return;
		}
		setMode(Mode.CREATE);
		stopBrowsing();
	}

	/** Another page of the launcher is shown (or the launcher closes): the search stops, a lobby stays. */
	public void pageHidden() {
		this.attention.setPageShown(false);
		if (!inSession()) {
			stopBrowsing();
		}
	}

	/** The LAN page is shown: the flash on the rail's button ends. */
	public void pageShown() {
		this.attention.setPageShown(true);
		if (!inSession() && ((this.mode == Mode.LOBBY) || (this.mode == Mode.COUNTDOWN))) {
			setMode(Mode.JOIN);
		}
		if (!inSession() && (this.mode == Mode.JOIN)) {
			startBrowsing();
		}
	}

	private void startBrowsing() {
		if (this.browser != null) {
			return;
		}
		if (!networkAvailable()) {
			this.games = Collections.emptyList();
			return;
		}
		try {
			final LanBrowser b = new LanBrowser(this.env.network(), new LanTimings());
			b.start();
			this.browser = b;
			this.browseError = null;
			this.lastProbe = 0;
			probeRejoinHosts(System.currentTimeMillis());
		}
		catch (final IOException | RuntimeException e) {
			LauncherLog.w("The search for LAN games cannot run", e);
			this.browseError = LanText.searchFailed();
		}
	}

	/**
	 * The games this device holds a key to are asked for at the hosts they were last played at, so a running game is
	 * found when broadcasts do not reach (a hotspot, a filtering router); until it is found, every few seconds.
	 */
	private void probeRejoinHosts(final long now) {
		final RejoinStore store = this.env.rejoinStore();
		if (store == null || this.browser == null || (now - this.lastProbe) < PROBE_MS) {
			return;
		}
		this.lastProbe = now;
		final java.util.Set<java.net.InetAddress> hosts = new java.util.HashSet<>();
		for (final RejoinKey key : store.all()) {
			if (key.host() != null && !this.seenGames.contains(key.gameId)) {
				final InetSocketAddress address = parseAddress(key.host());
				if (address != null) {
					hosts.add(address.getAddress());
				}
			}
		}
		if (!hosts.isEmpty()) {
			this.browser.probe(hosts);
		}
	}

	private void stopBrowsing() {
		if (this.browser != null) {
			this.browser.close();
			this.browser = null;
		}
		this.games = Collections.emptyList();
	}

	/** Once per frame: the clock of the flash, the countdown, the list of games. */
	public void update(final float deltaTime) {
		this.attention.update(deltaTime);
		if (this.mode == Mode.COUNTDOWN) {
			this.countdownLeft = Math.max(0, this.countdownLeft - deltaTime);
			final int shown = (int) Math.ceil(this.countdownLeft);
			if ((shown != this.countdownShown) && (shown > 0)) {
				this.countdownShown = shown;
				addSystem(LanText.countdown(shown));
			}
		}
		if (this.browser != null) {
			final long now = System.currentTimeMillis();
			this.browser.poll(now);
			probeRejoinHosts(now);
			if ((now - this.lastRefresh) >= LIST_REFRESH_MS) {
				this.lastRefresh = now;
				refreshGames();
			}
		}
	}

	private void refreshGames() {
		final List<GameRow> rows = new ArrayList<>();
		this.seenGames.clear();
		for (final LanDiscovery.Found found : this.browser.games()) {
			final LanData.GameInfo info = found.info;
			this.seenGames.add(info.gameId);
			final boolean other = (info.protocolVersion != LanProtocol.PROTOCOL_VERSION)
					|| !info.engineVersion.equals(this.env.engineVersion());
			boolean rejoin = false;
			if (info.state == LanProtocol.STATE_IN_GAME) {
				rejoin = rejoinKeyOf(info.gameId) != null;
				if (!rejoin) {
					continue;
				}
			}
			else if (info.state != LanProtocol.STATE_LOBBY) {
				continue;
			}
			final boolean fromHost = !other && !haveMap(info);
			final String label = info.mapTitle.isEmpty() ? info.mapName : info.mapTitle;
			final GameRow row = new GameRow(found, label, LanText.gameLine(label, info.players, info.maxPlayers),
					LanText.tag(rejoin, fromHost, other, other ? info.engineVersion : null), other, rejoin, fromHost);
			row.portrait = LanPortraitId.decode(info.portrait, this.env.reforgedSkin());
			rows.add(row);
		}
		Collections.sort(rows, new Comparator<GameRow>() {
			@Override
			public int compare(final GameRow a, final GameRow b) {
				return a.name.compareToIgnoreCase(b.name);
			}
		});
		final StringBuilder signature = new StringBuilder();
		for (final GameRow row : rows) {
			signature.append(row.id()).append('|').append(row.name).append('|').append(row.line).append('|')
					.append(row.tag).append('|').append(row.portrait).append(';');
		}
		if (!signature.toString().equals(this.gamesSignature)) {
			this.gamesSignature = signature.toString();
			touch();
		}
		this.games = rows;
	}

	/**
	 * Whether this device has the game's map: by its content once that is checked (in the background, the first time
	 * a map is seen), by its name until then.
	 */
	private boolean haveMap(final LanData.GameInfo info) {
		if (info.mapSha256.length == 0) {
			return this.env.hasMapNamed(info.mapName);
		}
		final String key = net.warsmash.lan.maps.MapHash.hex(info.mapSha256) + "/" + info.mapSize;
		final Boolean known = this.mapChecks.get(key);
		if (known != null) {
			return known;
		}
		if ((this.mapChecking.size() < MAX_MAP_CHECKS) && this.mapChecking.add(key)) {
			final LanData.MapInfo wanted = new LanData.MapInfo();
			wanted.name = info.mapName;
			wanted.size = info.mapSize;
			wanted.sha256 = info.mapSha256;
			wanted.crc32 = info.mapCrc32;
			final Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					Boolean found;
					try {
						found = LanLauncherModel.this.env.mapIndex().find(wanted) != null;
					}
					catch (final IOException | RuntimeException e) {
						found = null;
					}
					final Boolean checked = found;
					LanLauncherModel.this.env.ui().execute(new Runnable() {
						@Override
						public void run() {
							final boolean result = checked != null ? checked
									: LanLauncherModel.this.env.hasMapNamed(wanted.name);
							LanLauncherModel.this.mapChecks.put(key, result);
							LanLauncherModel.this.mapChecking.remove(key);
							LanLauncherModel.this.lastRefresh = 0;
						}
					});
				}
			}, "lan-map-check");
			t.setDaemon(true);
			t.start();
		}
		return this.env.hasMapNamed(info.mapName);
	}

	// ----------------------------------------------------------------- joining

	/** Joins a game of the list. */
	public void join(final GameRow row) {
		if (!row.joinable()) {
			return;
		}
		this.ipError = null;
		connect(new InetSocketAddress(row.found.address, row.found.info.tcpPort),
				row.rejoin ? rejoinKeyOf(row.id()) : null);
	}

	private RejoinKey rejoinKeyOf(final long gameId) {
		final RejoinStore store = this.env.rejoinStore();
		return store == null ? null : store.load(gameId);
	}

	/** "192.168.1.40" or "192.168.1.40:6112"; null when the text is not an address. */
	public static InetSocketAddress parseAddress(final String text) {
		if (text == null) {
			return null;
		}
		final Matcher m = ADDRESS.matcher(text);
		if (!m.matches()) {
			return null;
		}
		final byte[] octets = new byte[4];
		for (int i = 0; i < 4; i++) {
			final int v = Integer.parseInt(m.group(i + 1));
			if (v > 255) {
				return null;
			}
			octets[i] = (byte) v;
		}
		int port = LanProtocol.DEFAULT_PORT;
		if (m.group(5) != null) {
			port = Integer.parseInt(m.group(5));
			if ((port < 1) || (port > 65535)) {
				return null;
			}
		}
		try {
			return new InetSocketAddress(InetAddress.getByAddress(octets), port);
		}
		catch (final java.net.UnknownHostException e) {
			return null;
		}
	}

	/** Join by IP: the dialog's Connect. The outcome is the lobby, or {@link #ipError()}. */
	public void joinByIp(final String text) {
		final InetSocketAddress address = parseAddress(text);
		if (address == null) {
			this.ipError = LanText.badAddress();
			return;
		}
		this.ipError = null;
		connect(address, null);
	}

	private void connect(final InetSocketAddress address, final RejoinKey rejoinKey) {
		if (inSession()) {
			return;
		}
		stopBrowsing();
		try {
			final LanSession s = newSession();
			s.setHostTemplate(hostTemplate());
			this.connecting = true;
			this.hosting = false;
			final LanClient.Config config = clientConfig();
			config.rejoinKey = rejoinKey;
			s.connect(config, address);
		}
		catch (final IOException | RuntimeException e) {
			LauncherLog.w("Unable to connect", e);
			endSession();
			this.ipError = LanText.connectFailed();
			startBrowsing();
		}
	}

	private LanSession newSession() {
		final LanSession s = new LanSession(new LanTimings(), this.env.network());
		this.session = s;
		this.welcomed = false;
		this.launched = false;
		this.lobbyState = null;
		this.lobby = null;
		this.chat.clear();
		this.loadStates.clear();
		this.sending.clear();
		this.mapState = LanClient.MAP_CHECKING;
		this.mapDone = 0;
		this.mapTotal = 0;
		this.setup = null;
		LanSessions.begin(s);
		s.setRejoinStore(this.env.rejoinStore());
		s.addListener(this.listener);
		s.holdNetwork();
		return s;
	}

	/** What the host of a game this machine joined is like if this machine has to take the game over. */
	private LanHost.Config hostTemplate() {
		final LanHost.Config hc = new LanHost.Config();
		hc.engineVersion = this.env.engineVersion();
		hc.buildCommit = this.env.buildCommit();
		hc.officialBuild = this.env.official();
		hc.dataChecksum = this.env.dataChecksum();
		hc.commandValidator = new LanCommandValidator();
		hc.header = new java.util.function.Supplier<byte[]>() {
			@Override
			public byte[] get() {
				return LanGameStart.header(System.currentTimeMillis());
			}
		};
		return hc;
	}

	private LanClient.Config clientConfig() {
		final LanClient.Config c = new LanClient.Config();
		c.engineVersion = this.env.engineVersion();
		c.buildCommit = this.env.buildCommit();
		c.officialBuild = this.env.official();
		c.dataChecksum = this.env.dataChecksum();
		final String name = this.env.playerName();
		c.playerName = name.length() > LanProtocol.MAX_PLAYER_NAME ? name.substring(0, LanProtocol.MAX_PLAYER_NAME)
				: name;
		c.portrait = this.env.portrait() == null ? "" : this.env.portrait();
		c.controlScheme = this.env.controlScheme();
		c.mapIndex = this.env.mapIndex();
		c.events = this.env.ui();
		return c;
	}

	// ----------------------------------------------------------------- hosting

	/**
	 * Hosts a game and joins it. Returns null, or the reason it cannot be done (shown by the page); a game that no
	 * one can find because announcing failed still runs, and Join by IP reaches it.
	 */
	public String create(final CreateSettings settings) {
		if (inSession()) {
			return LanText.leaveFirst();
		}
		if (settings.issue != MapIssue.NONE) {
			return LanText.mapIssue(settings.issue);
		}
		if (!networkAvailable()) {
			return LanText.noWifi();
		}
		stopBrowsing();
		try {
			final LanSession s = newSession();
			this.hosting = true;
			this.connecting = true;
			final LanHost.Config hc = new LanHost.Config();
			hc.engineVersion = this.env.engineVersion();
			hc.buildCommit = this.env.buildCommit();
			hc.officialBuild = this.env.official();
			hc.dataChecksum = this.env.dataChecksum();
			hc.options.gameName = settings.gameName.trim().isEmpty() ? LanText.defaultGameName(this.env.playerName())
					: settings.gameName.trim();
			hc.options.speed = settings.speed;
			hc.options.observers = settings.observers;
			hc.options.lockTeams = settings.lockTeams;
			hc.options.teamsTogether = settings.teamsTogether;
			hc.options.fullSharedControl = settings.fullSharedControl;
			hc.options.randomRaces = settings.randomRaces;
			hc.map = settings.map;
			hc.mapFile = settings.mapFile;
			hc.syncWarning = settings.syncWarning;
			hc.maxSlots = settings.maxSlots;
			hc.port = settings.port;
			hc.countdownMs = settings.countdownMs;
			hc.commandValidator = new LanCommandValidator();
			hc.header = new java.util.function.Supplier<byte[]>() {
				@Override
				public byte[] get() {
					return LanGameStart.header(System.currentTimeMillis());
				}
			};
			if (settings.mapFile != null) {
				this.env.mapIndex().addRoot(settings.mapFile.getParentFile());
			}
			final int port = s.openHost(hc);
			watchMapSending(s.host());
			try {
				s.announce();
			}
			catch (final IOException e) {
				LauncherLog.w("The game cannot be announced on the network", e);
			}
			this.hostingSince = System.currentTimeMillis();
			s.connect(clientConfig(), new InetSocketAddress("127.0.0.1", port));
			return null;
		}
		catch (final IOException | RuntimeException e) {
			LauncherLog.w("Unable to host", e);
			endSession();
			return LanText.createFailed(e.getMessage());
		}
	}

	/** The lobby shows how far the maps this machine's host sends have got. */
	private void watchMapSending(final LanHost host) {
		host.setMapListener(new LanHost.MapListener() {
			@Override
			public void onMapSending(final int participantId, final long sent, final int total) {
				final int percent = total <= 0 ? 0 : (int) Math.min(100, (sent * 100) / total);
				LanLauncherModel.this.env.ui().execute(new Runnable() {
					@Override
					public void run() {
						LanLauncherModel.this.sending.put(participantId, percent);
						rebuildLobby();
					}
				});
			}

			@Override
			public void onMapStatus(final int participantId, final int status, final int percent) {
				if (status != LanProtocol.MAP_STATUS_DOWNLOADING) {
					LanLauncherModel.this.env.ui().execute(new Runnable() {
						@Override
						public void run() {
							LanLauncherModel.this.sending.remove(participantId);
							rebuildLobby();
						}
					});
				}
			}
		});
	}

	// ------------------------------------------------------------ lobby actions

	public void setReady(final boolean ready) {
		final LanClient client = client();
		if ((client != null) && canReady()) {
			this.ownReadyAsked = true;
			client.setReady(ready);
		}
	}

	/**
	 * The host's big button: starts the game once everyone is ready, else toggles the host's own ready mark.
	 * Pressing while the start is not allowed never starts anything.
	 */
	public void hostPrimary() {
		if (this.lobby == null) {
			return;
		}
		if (this.lobby.hostAction() == LanLobbyView.HostAction.START) {
			startGame();
		}
		else {
			setReady((this.lobby.mine == null) || !this.lobby.mine.ready);
		}
	}

	public void slotRequest(final int slot, final int race, final int team, final int color, final int handicap) {
		final LanClient client = client();
		if (client != null) {
			client.slotRequest(slot, race, team, color, handicap);
		}
	}

	/** Moves this player to a free slot, keeping its choices. */
	public void takeSlot(final int slot) {
		final LanLobbyView.Row mine = this.lobby == null ? null : this.lobby.mine;
		slotRequest(slot, mine == null ? 0 : mine.race, mine == null ? slot : mine.team,
				mine == null ? slot : mine.color, mine == null ? 100 : mine.handicap);
	}

	public void watch(final boolean referee) {
		final LanClient client = client();
		if (client != null) {
			client.slotRequest(referee ? LanProtocol.REQUEST_REFEREE : LanProtocol.REQUEST_OBSERVER, 0, 0, 0, 100);
		}
	}

	public void hostSlot(final int slot, final int action, final int level) {
		final LanClient client = client();
		if (client != null) {
			client.hostSlot(slot, action, level);
		}
	}

	/** The host removes an observer or a referee from the lobby. */
	public void kickWatcher(final int participantId) {
		hostSlot(participantId, LanProtocol.ACTION_KICK_WATCHER, 0);
	}

	/** The host sets a computer's race, team, color and handicap. */
	public void hostSetup(final int slot, final int race, final int team, final int color, final int handicap) {
		final LanClient client = client();
		if (client != null) {
			client.hostSetup(slot, race, team, color, handicap);
		}
	}

	/**
	 * The host plays another map in the lobby it made. Null when it was done, else why not (the players would not fit
	 * in the map's slots).
	 */
	public String changeMap(final CreateSettings map) {
		if (map.issue != MapIssue.NONE) {
			return LanText.mapIssue(map.issue);
		}
		final LanSession s = this.session;
		if (!this.hosting || (s == null) || (s.host() == null) || (this.mode != Mode.LOBBY) || (map.map == null)) {
			return LanText.mapChangeFailed();
		}
		if (map.mapFile != null) {
			this.env.mapIndex().addRoot(map.mapFile.getParentFile());
		}
		return s.host().changeMap(map.map, map.mapFile, map.maxSlots, map.syncWarning) ? null
				: LanText.mapChangeFailed();
	}

	public void hostOptions(final LanData.Options options) {
		final LanClient client = client();
		if (client != null) {
			client.hostOptions(options);
		}
	}

	public void startGame() {
		final LanClient client = client();
		if ((client != null) && (this.lobby != null) && this.lobby.canStart() && (this.mode == Mode.LOBBY)) {
			client.requestStart();
		}
	}

	public void cancelStart() {
		final LanClient client = client();
		if ((client != null) && this.hosting && (this.mode == Mode.COUNTDOWN)) {
			client.cancelCountdown();
		}
	}

	public void sendChat(final String text) {
		final LanClient client = client();
		final String trimmed = text == null ? "" : text.trim();
		if ((client != null) && !trimmed.isEmpty()) {
			client.chat(LanProtocol.CHAT_ALL, trimmed.length() > LanProtocol.MAX_CHAT
					? trimmed.substring(0, LanProtocol.MAX_CHAT) : trimmed);
		}
	}

	/** The words of the confirmation a host sees before leaving, or null when leaving needs none. */
	public String leaveConfirmation() {
		if (!this.hosting || (this.lobby == null)) {
			return null;
		}
		return LanText.leaveConfirm(this.env.hostMigration() ? this.lobby.nextHostName() : null);
	}

	/** Leaves the lobby (the host closes the game for those who stay unless the game moves on without it). */
	public void leave() {
		final LanClient client = client();
		if (client != null) {
			client.leave(LanProtocol.LEAVE_NORMAL);
		}
		endSession();
		setMode(Mode.JOIN);
		startBrowsing();
	}

	/** The game ended and the launcher is back: the session closes and the Join Game page shows. */
	public void gameEnded() {
		endSession();
		setMode(Mode.JOIN);
	}

	/** The game started from the lobby could not be started by the engine. */
	public void launchFailed() {
		this.error = LanText.launchFailed();
		this.launched = false;
		leave();
	}

	/** The launcher found the map of the game started from the lobby unplayable. */
	public void launchRefused(final MapIssue issue) {
		this.error = LanText.mapIssue(issue);
		this.launched = false;
		leave();
	}

	private void endSession() {
		touch();
		final LanSession s = this.session;
		this.session = null;
		this.hosting = false;
		this.connecting = false;
		this.welcomed = false;
		this.lobby = null;
		this.lobbyState = null;
		this.attention.clear();
		this.sending.clear();
		this.countdownLeft = 0;
		this.countdownShown = 0;
		if (s != null) {
			s.removeListener(this.listener);
			LanSessions.end(s);
		}
	}

	private LanClient client() {
		return this.session == null ? null : this.session.client();
	}

	/** Where this machine has the lobby's map once it is there (found or downloaded); null before. */
	public File lobbyMapFile() {
		final LanClient client = client();
		return (client == null) || (this.mapState != LanClient.MAP_READY) ? null : client.mapFile();
	}

	// ------------------------------------------------------------------ events

	private void addSystem(final String text) {
		addChat(null, text, true);
	}

	private void addChat(final String from, final String text, final boolean system) {
		touch();
		this.chat.add(new ChatLine(from, text, system));
		while (this.chat.size() > MAX_CHAT_LINES) {
			this.chat.remove(0);
		}
	}

	private int myId() {
		final LanClient client = client();
		return client == null ? LanProtocol.NO_PARTICIPANT : client.participantId();
	}

	private void rebuildLobby() {
		touch();
		if (this.lobbyState != null) {
			this.lobby = LanLobbyView.of(this.lobbyState, myId(), this.hosting ? this.sending : null,
					mapPercent(), this.env.reforgedSkin());
		}
	}

	private static Map<Integer, String> names(final LanMessages.LobbyState state) {
		final Map<Integer, String> names = new HashMap<>();
		if (state != null) {
			for (final LanData.SlotInfo s : state.slots) {
				if (s.kind == LanProtocol.SLOT_HUMAN) {
					names.put(s.participantId, s.name);
				}
			}
			for (final LanData.ObserverInfo o : state.observers) {
				names.put(o.participantId, o.name);
			}
		}
		return names;
	}

	private void lobbyChanged(final LanMessages.LobbyState state) {
		final LanMessages.LobbyState before = this.lobbyState;
		final int me = myId();
		final List<LanAttention.Event> events = LanAttention.diff(before, state, me, this.ownReadyAsked);
		this.ownReadyAsked = false;
		if (before != null) {
			final Map<Integer, String> was = names(before);
			final Map<Integer, String> now = names(state);
			for (final Map.Entry<Integer, String> e : now.entrySet()) {
				if (!was.containsKey(e.getKey()) && (e.getKey() != me)) {
					addSystem(LanText.joined(e.getValue()));
				}
			}
			for (final Map.Entry<Integer, String> e : was.entrySet()) {
				if (!now.containsKey(e.getKey())) {
					if (state.ownerId != before.ownerId) {
						final String next = now.get(state.ownerId);
						addSystem(next == null ? LanText.left(e.getValue()) : LanText.newHost(e.getValue(), next));
					}
					else {
						addSystem(LanText.left(e.getValue()));
					}
				}
			}
			controlSchemeLines(before, state, now);
		}
		this.lobbyState = state;
		rebuildLobby();
		for (final LanAttention.Event e : events) {
			this.attention.onEvent(e);
		}
	}

	private void controlSchemeLines(final LanMessages.LobbyState before, final LanMessages.LobbyState after,
			final Map<Integer, String> names) {
		final Map<Integer, Integer> was = new HashMap<>();
		for (final LanData.SlotInfo s : before.slots) {
			if (s.kind == LanProtocol.SLOT_HUMAN) {
				was.put(s.participantId, s.controlScheme);
			}
		}
		for (final LanData.ObserverInfo o : before.observers) {
			was.put(o.participantId, o.controlScheme);
		}
		for (final LanData.SlotInfo s : after.slots) {
			if ((s.kind == LanProtocol.SLOT_HUMAN) && was.containsKey(s.participantId)
					&& (was.get(s.participantId) != s.controlScheme)) {
				addSystem(LanText.controlSchemeNow(s.name, s.controlScheme == LanProtocol.CONTROL_KBM));
			}
		}
		for (final LanData.ObserverInfo o : after.observers) {
			if (was.containsKey(o.participantId) && (was.get(o.participantId) != o.controlScheme)) {
				addSystem(LanText.controlSchemeNow(o.name, o.controlScheme == LanProtocol.CONTROL_KBM));
			}
		}
	}

	private final class Listener extends LanClient.Listener {
		@Override
		public void onWelcome(final LanMessages.Welcome welcome) {
			LanLauncherModel.this.connecting = false;
			LanLauncherModel.this.welcomed = true;
			setMode(welcome.gameState == LanProtocol.STATE_LOBBY ? Mode.LOBBY : Mode.REJOIN);
			LanLauncherModel.this.ipError = null;
			if (!LanLauncherModel.this.hosting) {
				LanLauncherModel.this.hostingSince = System.currentTimeMillis();
			}
		}

		@Override
		public void onLobby(final LanMessages.LobbyState lobby) {
			lobbyChanged(lobby);
		}

		@Override
		public void onPromoted(final LanHost host) {
			// The host of the game is gone and this machine took it over: it hosts the lobby from here on.
			if (LanLauncherModel.this.session == null) {
				return;
			}
			LanLauncherModel.this.hosting = true;
			LanLauncherModel.this.hostingSince = System.currentTimeMillis();
			watchMapSending(host);
			rebuildLobby();
		}

		@Override
		public void onChat(final LanMessages.Chat chat) {
			final String name = LanLauncherModel.this.lobby == null ? null
					: LanLauncherModel.this.lobby.nameOf(chat.from);
			addChat(name == null ? "?" : name, chat.text, false);
			if (chat.from != myId()) {
				LanLauncherModel.this.attention.onEvent(LanAttention.Event.CHAT);
			}
		}

		@Override
		public void onReject(final LanMessages.Reject reject) {
			final String text = LanText.reject(reject);
			final boolean fromIp = LanLauncherModel.this.mode == Mode.JOIN;
			endSession();
			LanLauncherModel.this.setMode(Mode.JOIN);
			LanLauncherModel.this.ipError = text;
			if (!fromIp) {
				LanLauncherModel.this.error = text;
			}
			startBrowsing();
		}

		@Override
		public void onKicked(final String reason) {
			endSession();
			LanLauncherModel.this.setMode(Mode.JOIN);
			LanLauncherModel.this.error = LanText.kicked();
			startBrowsing();
		}

		@Override
		public void onDisconnected(final String reason) {
			if (LanLauncherModel.this.session == null) {
				return;
			}
			final boolean before = !LanLauncherModel.this.welcomed;
			if ((LanLauncherModel.this.mode == Mode.LOADING) && LanLauncherModel.this.launched) {
				// The engine plays on; its own side handles the lost host.
				return;
			}
			endSession();
			LanLauncherModel.this.setMode(Mode.JOIN);
			if (before) {
				LanLauncherModel.this.ipError = LanText.connectFailed();
			}
			else {
				LanLauncherModel.this.error = LanText.lostHost();
			}
			startBrowsing();
		}

		@Override
		public void onCountdown(final int seconds) {
			LanLauncherModel.this.setMode(Mode.COUNTDOWN);
			LanLauncherModel.this.countdownLeft = seconds;
			LanLauncherModel.this.countdownShown = seconds;
			addSystem(LanText.countdown(seconds));
			LanLauncherModel.this.attention.onEvent(LanAttention.Event.COUNTDOWN);
		}

		@Override
		public void onCountdownCancel() {
			if (LanLauncherModel.this.mode == Mode.COUNTDOWN) {
				LanLauncherModel.this.setMode(Mode.LOBBY);
				LanLauncherModel.this.countdownLeft = 0;
				addSystem(LanText.countdownCancelled());
			}
		}

		@Override
		public void onStart(final LanGameSetup start) {
			LanLauncherModel.this.setup = start;
			LanLauncherModel.this.setMode(Mode.LOADING);
			startGame(start);
		}

		@Override
		public void onLoadProgress(final int participant, final int percent) {
			LanLauncherModel.this.loadStates.put(participant, percent);
		}

		@Override
		public void onRejoin(final LanGameSetup start, final int from, final int total) {
			LanLauncherModel.this.setup = start;
			LanLauncherModel.this.setMode(Mode.REJOIN);
			LanLauncherModel.this.rejoinPercent = 0;
			startGame(start);
		}

		@Override
		public void onMapState(final int state, final LanData.MapInfo map, final File file, final int failure) {
			final int old = LanLauncherModel.this.mapState;
			LanLauncherModel.this.mapState = state;
			if (state != LanClient.MAP_READY) {
				LanLauncherModel.this.mapIssue = null;
				LanLauncherModel.this.mapIssueCheck++;
			}
			if (state == LanClient.MAP_DOWNLOADING) {
				if (old != LanClient.MAP_DOWNLOADING) {
					addSystem(LanText.downloading());
				}
			}
			else if (state == LanClient.MAP_READY) {
				if ((old == LanClient.MAP_DOWNLOADING) || (old == LanClient.MAP_VERIFYING)) {
					addSystem(LanText.mapReceived());
					LanLauncherModel.this.attention.onEvent(LanAttention.Event.MAP_DOWNLOADED);
				}
				if (old != LanClient.MAP_READY) {
					checkMapIssue(file);
				}
			}
			else if (state == LanClient.MAP_FAILED) {
				LanLauncherModel.this.mapFailedText = LanText.mapFailed(failure);
				LanLauncherModel.this.attention.onEvent(LanAttention.Event.MAP_FAILED);
			}
			rebuildLobby();
			}

		@Override
		public void onMapProgress(final long done, final int total) {
			LanLauncherModel.this.mapDone = done;
			LanLauncherModel.this.mapTotal = total;
			rebuildLobby();
		}
	}

	/**
	 * Looks at the received map before anyone can ready up on it: a map the engine cannot play keeps this player out of
	 * the start, so no game begins that this device cannot join.
	 */
	private void checkMapIssue(final File file) {
		final int check = ++this.mapIssueCheck;
		if (file == null) {
			// startGame fails on the missing file when the game starts
			this.mapIssue = MapIssue.NONE;
			return;
		}
		this.mapIssue = null;
		this.env.mapIssue(file, issue -> {
			if ((check != this.mapIssueCheck) || (this.session == null)) {
				return;
			}
			if (issue == null) {
				// Without an answer the map stays unchecked, so this player cannot ready up on it.
				final String text = LanText.mapFailed(MapDownload.FAIL_IO);
				addSystem(text);
				this.mapFailedText = text;
				this.mapFailedTitle = LanText.mapFailedTitle();
				this.attention.onEvent(LanAttention.Event.MAP_FAILED);
				if ((this.setup != null) && !this.launched
						&& ((this.mode == Mode.LOADING) || (this.mode == Mode.REJOIN))) {
					launchFailed();
				}
				rebuildLobby();
				return;
			}
			this.mapIssue = issue;
			if (issue != MapIssue.NONE) {
				addSystem(LanText.mapIssue(issue));
				this.mapFailedText = LanText.mapIssue(issue);
				this.mapFailedTitle = LanText.mapIssueTitle();
				this.attention.onEvent(LanAttention.Event.MAP_FAILED);
			}
			// A game started or joined while running waited for this answer and for the map.
			if ((this.setup != null) && !this.launched && ((this.mode == Mode.LOADING) || (this.mode == Mode.REJOIN))) {
				startGame(this.setup);
			}
			rebuildLobby();
		});
	}

	/** The host started the game: the engine takes over with the LAN request. */
	private void startGame(final LanGameSetup start) {
		final LanClient client = client();
		final File file = client == null ? null : client.mapFile();
		if ((file == null) || this.launched) {
			// A rejoin finds its map after the answer came; it starts when the map is there.
			if ((file == null) && ((this.mode != Mode.REJOIN) || (this.mapState == LanClient.MAP_FAILED))) {
				launchFailed();
			}
			return;
		}
		if (this.mapIssue == null) {
			return;
		}
		if (this.mapIssue != MapIssue.NONE) {
			launchRefused(this.mapIssue);
			return;
		}
		this.launched = true;
		final LaunchRequest request = LaunchRequest.lan(this.session, file.getAbsolutePath(), slotsOf(start));
		request.mapChecked = true;
		this.env.launch(request);
	}

	/** The lobby's slots in the form the loading screen's preview takes. */
	static String slotsOf(final LanGameSetup start) {
		final StringBuilder slots = new StringBuilder();
		for (final LanData.SlotInfo s : start.slots) {
			final String type;
			if (s.kind == LanProtocol.SLOT_HUMAN) {
				type = "USER";
			}
			else if (s.kind == LanProtocol.SLOT_COMPUTER) {
				type = s.computerLevel == 0 ? "COMPUTER_NEWBIE" : s.computerLevel == 2 ? "COMPUTER_INSANE"
						: "COMPUTER_NORMAL";
			}
			else {
				continue;
			}
			new LauncherSlot(s.index, type, s.race, s.team, s.color).appendTo(slots);
		}
		return slots.toString();
	}
}
