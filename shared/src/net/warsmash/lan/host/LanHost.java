package net.warsmash.lan.host;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import net.warsmash.lan.LanGameSetup;
import net.warsmash.lan.RejoinKey;
import net.warsmash.lan.TurnArchive;
import net.warsmash.lan.maps.MapRules;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanData.ChecksumGroup;
import net.warsmash.lan.protocol.LanData.CmdEntry;
import net.warsmash.lan.protocol.LanData.GameInfo;
import net.warsmash.lan.protocol.LanData.LagEntry;
import net.warsmash.lan.protocol.LanData.PresenceEntry;
import net.warsmash.lan.protocol.LanData.SlotInfo;
import net.warsmash.lan.protocol.LanData.TurnData;
import net.warsmash.lan.protocol.LanMessage;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.protocol.LanProtocolException;
import net.warsmash.lan.transport.LanConnection;
import net.warsmash.lan.transport.LanDiscovery;
import net.warsmash.lan.transport.LanLog;
import net.warsmash.lan.transport.LanLoop;

/**
 * The authority of one game: lobby, START / GO, the turn clock and its sync limit, presence, checksum comparison.
 * Everything runs on the loop thread; the host's own player joins like any other participant through a loopback
 * connection. Participants fetch the map from here ({@link MapServer}) and may not be ready before they have it,
 * verified. When the host is gone a participant takes the game over with {@link #LanHost(LanLoop, Config, Takeover)}.
 */
public final class LanHost {
	public interface CommandValidator {
		/**
		 * Returns false for bytes that must never reach the simulation. The slot is the player's seat, or, from a
		 * referee, {@link LanProtocol#WATCHER_SLOT_BASE} and up: only what a referee may give is then to be accepted.
		 */
		boolean accept(int slot, byte[] command);
	}

	/** Callbacks on the loop thread. */
	public interface Listener {
		void onStateChanged(int state);

		void onDesync(int tick, List<ChecksumGroup> groups);

		/**
		 * A participant's game is played again ({@code RESYNC_DESYNC}, {@code RESYNC_TURNS}) or the participant is
		 * dropped ({@code RESYNC_GIVE_UP}).
		 */
		default void onResync(final int participant, final int kind, final int tick) {
		}
	}

	/** Callbacks on the loop thread about map transfers, for the host's lobby UI. */
	public interface MapListener {
		/** Bytes handed to the participant's connection so far. */
		void onMapSending(int participantId, long sent, int total);

		/** What the participant reported: a {@code MAP_STATUS_*} value and its percentage. */
		void onMapStatus(int participantId, int status, int percent);
	}

	public static final class Config {
		public String engineVersion = "";
		public String buildCommit = "";
		public boolean officialBuild;
		public long dataChecksum;
		public LanData.Options options = new LanData.Options();
		public LanData.MapInfo map = new LanData.MapInfo();
		/** The file behind {@link #map}, served to participants that lack it; null serves nothing. */
		public File mapFile;
		public boolean syncWarning;
		public int maxSlots = 4;
		public int port = LanProtocol.DEFAULT_PORT;
		public int portTries = LanProtocol.PORT_TRIES;
		public int turnSteps = 2;
		public int syncLimit = 10;
		/** Wall time of one turn; 100 ms is two 50 ms steps at normal speed. */
		public int turnIntervalMs = 100;
		public int countdownMs = 5000;
		public CommandValidator commandValidator;
		/** The opaque replay header fields of the game, made when the game starts; null for none. */
		public java.util.function.Supplier<byte[]> header;
		/** Where the turn archive is mirrored; null keeps it in memory only. */
		public File archiveFile;
		public SecureRandom random = new SecureRandom();

		/** A copy to make another host from: the lobby's own settings are copied, the rest is shared. */
		public Config copy() {
			final Config c = new Config();
			c.engineVersion = this.engineVersion;
			c.buildCommit = this.buildCommit;
			c.officialBuild = this.officialBuild;
			c.dataChecksum = this.dataChecksum;
			c.options = this.options.copy();
			c.map = this.map;
			c.mapFile = this.mapFile;
			c.syncWarning = this.syncWarning;
			c.maxSlots = this.maxSlots;
			c.port = this.port;
			c.portTries = this.portTries;
			c.turnSteps = this.turnSteps;
			c.syncLimit = this.syncLimit;
			c.turnIntervalMs = this.turnIntervalMs;
			c.countdownMs = this.countdownMs;
			c.commandValidator = this.commandValidator;
			c.header = this.header;
			c.random = this.random;
			return c;
		}
	}

	private static final class Participant {
		int id;
		byte[] token;
		/** SHA-256 of the token, when the token itself is not known (a host that took the game over). */
		byte[] tokenHash;
		String name;
		String portrait;
		int role;
		int slot = LanProtocol.NO_SLOT;
		int controlScheme;
		String address;
		int listenPort;
		LanConnection conn;
		boolean ready;
		boolean loaded;
		int loadPercent;
		long loadProgressMs;
		int state = LanProtocol.PRESENCE_PLAYING;
		int appState;
		/** Last MAP_STATUS value, -1 until the participant reported one for the current map. */
		int mapStatus = -1;
		int mapPercent;
		int ackTurn = -1;
		int lastSeq;
		int queuedCommands;
		int strikes;
		long lastPlayerState;
		final ArrayDeque<Long> chatTimes = new ArrayDeque<Long>();
		LanLoop.Timer absentTimer;
		/** The next archived turn to send to a participant being brought up to date, or -1 when none is. */
		int streamNext = -1;
		LanConnection streamConn;
		LanLoop.Timer streamTimer;
		/** Set when the participant came back from the start and plays the archive through before it counts again. */
		boolean rejoinStarted;
		/** Sums a rejoining participant reported for steps the others had not reported yet. */
		final TreeMap<Integer, Integer> rejoinChecks = new TreeMap<Integer, Integer>();
		/** The last turn the participant said it holds when it came; it is not sent the ones up to it again. */
		int holdsThrough = -1;
		/** Whether this host has had a connection of the participant since it took the game over. */
		boolean sawHost;
		boolean fillAsked;
		boolean connectedSinceTakeover;
		/** Whether a host that took the game over waits for this participant to come before it goes on. */
		boolean awaited;
		/** Its sums are ignored until it answers a resync: they are those of a game it has dropped. */
		boolean awaitAck;
		long awaitAckSince;
		/** Playing the game again after its sums differed; a difference in that run means it cannot stay in sync. */
		boolean recovering;
		int resyncs;
		int lastResyncTick = -1;
	}

	/**
	 * What a participant that becomes the host has of the game it takes over: the client's copy of the lobby, or of the
	 * start and every turn so far. The others come to the new host one by one; see {@link #finishTakeover}.
	 */
	public static final class Takeover {
		public long gameId;
		public int gameState;
		public int selfId;
		public byte[] selfToken;
		/** The participant that was the host. */
		public int previousHost = LanProtocol.NO_PARTICIPANT;
		public LanMessages.LobbyState lobby;
		/** The start of a running game, null in a lobby. */
		public LanGameSetup setup;
		/** Every turn of the game so far, as the taking client received them. */
		public TurnArchive archive;
		/** The sums the taking client reported, which stand for the old host's log of the steps compared so far. */
		public Map<Integer, Integer> checksums = new TreeMap<Integer, Integer>();
		/** What each participant's presence was in the last turn this participant got. */
		public Map<Integer, Integer> presence = new HashMap<Integer, Integer>();
		/** The file the map is served from, or null when it is not served. */
		public File mapFile;
	}

	/** Turns the archive is cut into when it is sent to a participant who comes back. */
	private static final int BLOCK_TURNS = 256;
	private static final int STREAM_RETRY_MS = 5;
	public static final int MAX_CHECKSUM_LOG = 200000;
	private static final int RESYNC_ACK_MS = 30000;
	/** A participant that differs this many times is not brought back into step any more. */
	private static final int MAX_RESYNCS = 3;

	/** The shortest wall time between the turns of a game that stands still. */
	private static final int PAUSED_TURN_MS = 100;

	private final class Handler implements LanConnection.Listener {
		Participant participant;
		LanLoop.Timer helloTimer;

		@Override
		public void onMessage(final LanConnection c, final LanMessage m) {
			if (this.participant == null) {
				if (m instanceof LanMessages.Hello) {
					if (this.helloTimer != null) {
						this.helloTimer.cancel();
					}
					onHello(c, this, (LanMessages.Hello) m);
				}
				else if (m instanceof LanMessages.MigrateHello) {
					if (this.helloTimer != null) {
						this.helloTimer.cancel();
					}
					onMigrateHello(c, this, (LanMessages.MigrateHello) m);
				}
				else {
					c.close("expected HELLO");
				}
				return;
			}
			if (this.participant.conn != c) {
				return;
			}
			try {
				onParticipantMessage(this.participant, c, m);
			}
			catch (final LanProtocolException e) {
				c.close("protocol violation: " + e.getMessage());
			}
		}

		@Override
		public void onClosed(final LanConnection c, final String reason) {
			if (this.helloTimer != null) {
				this.helloTimer.cancel();
			}
			LanHost.this.connections.remove(c);
			if (this.participant != null && this.participant.conn == c) {
				onDisconnected(this.participant, reason);
			}
		}
	}

	private final LanLoop loop;
	private final Config config;
	private long gameId;
	private final Set<LanConnection> connections = new HashSet<LanConnection>();
	private final Map<Integer, Participant> participants = new TreeMap<Integer, Participant>();
	private final List<SlotInfo> slots = new ArrayList<SlotInfo>();
	private final Set<String> kickedAddresses = new HashSet<String>();
	private final ArrayDeque<CmdEntry> incoming = new ArrayDeque<CmdEntry>();
	private final List<PresenceEntry> pendingPresence = new ArrayList<PresenceEntry>();
	private final Map<Integer, Map<Integer, Integer>> checksums = new TreeMap<Integer, Map<Integer, Integer>>();
	private final TreeSet<Integer> reportedDesync = new TreeSet<Integer>();
	/** The sum the players reported for each checked step, kept for the whole game to verify a rejoining player. */
	private final TreeMap<Integer, Integer> checksumLog = new TreeMap<Integer, Integer>();
	private LanLoop.ServerHandle server;
	private LanDiscovery discovery;
	private Listener listener;
	private MapListener mapListener;
	private final MapServer maps;
	private TurnArchive archive;
	private LanGameSetup setup;
	private int state = LanProtocol.STATE_LOBBY;
	private int rev;
	private int ownerId = LanProtocol.NO_PARTICIPANT;
	private int nextId = 1;
	private int nextTurn;
	private LanLoop.Timer countdownTimer;
	private LanLoop.Timer loadTimer;
	private long loadStartedMs;
	private static final int LOAD_HARD_LIMIT_FACTOR = 5;
	private LanLoop.Timer turnTimer;
	private boolean countingDown;
	private long nextTurnDue;
	private long stalledSince;
	private long lastLagBroadcast;
	private boolean lagActive;
	/** The players whose game stands still; turns carry no steps while there is one. */
	private final Set<Integer> simPausedBy = new HashSet<Integer>();
	private final Set<Integer> continueVotes = new HashSet<Integer>();
	/** True from the moment this host took a game over until the others had a moment to come (see finishTakeover). */
	private boolean migrating;
	private LanLoop.Timer migrationTimer;
	/** The number of turns this host had when it began making turns itself; turns beyond it that someone holds are not its. */
	private int migrationBase = Integer.MAX_VALUE;
	private int previousHost = LanProtocol.NO_PARTICIPANT;
	private final List<Object[]> heldCommands = new ArrayList<Object[]>();
	private final Set<Integer> fillOutstanding = new HashSet<Integer>();
	private File takenMapFile;
	private boolean retired;
	private int filled;

	public LanHost(final LanLoop loop, final Config config) {
		this.loop = loop;
		this.config = config;
		this.gameId = config.random.nextLong();
		this.maps = new MapServer(loop, new MapServer.Listener() {
			@Override
			public void onSending(final int participantId, final long sent, final int total) {
				final MapListener l = LanHost.this.mapListener;
				if (l != null) {
					l.onMapSending(participantId, sent, total);
				}
			}
		});
		if (config.mapFile != null) {
			this.maps.setMap(config.mapFile, config.map.size);
		}
		final int n = Math.max(1, Math.min(config.maxSlots, LanProtocol.MAX_SLOTS));
		for (int i = 0; i < n; i++) {
			final SlotInfo s = new SlotInfo();
			s.index = i;
			s.color = i;
			s.team = i;
			this.slots.add(s);
		}
	}

	public void setListener(final Listener listener) {
		this.listener = listener;
	}

	public void setMapListener(final MapListener listener) {
		this.mapListener = listener;
	}

	/**
	 * Changes the lobby's map: the new one is announced, running transfers stop and everybody has to report again
	 * (and get ready again) for it. {@code file} is served to those who lack it.
	 */
	public void setMap(final LanData.MapInfo info, final File file) {
		changeMap(info, file, this.config.maxSlots, this.config.syncWarning);
	}

	/**
	 * Changes the lobby's map after it was made: the slots follow the new map's player count (players in the slots
	 * that go move to open ones), the new map is announced, running transfers stop and everybody has to report again
	 * (and get ready again) for it. Returns false, and changes nothing, when the lobby is no longer one or the players
	 * would not fit.
	 */
	public boolean changeMap(final LanData.MapInfo info, final File file, final int maxSlots,
			final boolean syncWarning) {
		if (file != null && file.length() != info.size) {
			throw new IllegalArgumentException("map file does not have the announced size");
		}
		if (file != null && MapRules.checkInfo(info) != null) {
			throw new IllegalArgumentException("map cannot be shared: " + MapRules.checkInfo(info));
		}
		return query(new Callable<Boolean>() {
			@Override
			public Boolean call() {
				if (LanHost.this.state != LanProtocol.STATE_LOBBY) {
					return false;
				}
				final int count = Math.max(1, Math.min(maxSlots, LanProtocol.MAX_SLOTS));
				if (!resizeSlots(count)) {
					return false;
				}
				LanHost.this.config.map = info;
				LanHost.this.config.mapFile = file;
				LanHost.this.config.maxSlots = count;
				LanHost.this.config.syncWarning = syncWarning;
				LanHost.this.maps.setMap(file, info.size);
				for (final Participant p : LanHost.this.participants.values()) {
					p.mapStatus = -1;
					p.mapPercent = 0;
					p.ready = false;
				}
				if (LanHost.this.countingDown) {
					cancelCountdown();
				}
				LanHost.this.rev++;
				broadcastLobby();
				return true;
			}
		});
	}

	/** Gives the lobby this many slots; the players of the slots that go move to open ones. False when they do not fit. */
	private boolean resizeSlots(final int count) {
		if (count < this.slots.size()) {
			final List<SlotInfo> spare = new ArrayList<SlotInfo>();
			for (int i = 0; i < count; i++) {
				if (this.slots.get(i).kind == LanProtocol.SLOT_OPEN) {
					spare.add(this.slots.get(i));
				}
			}
			int moving = 0;
			for (int i = count; i < this.slots.size(); i++) {
				if (this.slots.get(i).kind == LanProtocol.SLOT_HUMAN) {
					moving++;
				}
			}
			if (moving > spare.size()) {
				return false;
			}
			for (int i = count; i < this.slots.size(); i++) {
				final SlotInfo gone = this.slots.get(i);
				if (gone.kind != LanProtocol.SLOT_HUMAN) {
					continue;
				}
				final SlotInfo to = spare.remove(0);
				to.kind = LanProtocol.SLOT_HUMAN;
				to.participantId = gone.participantId;
				to.race = gone.race;
				to.team = Math.min(gone.team, count - 1);
				to.handicap = gone.handicap;
				to.color = colorFree(gone.color, to);
				final Participant p = this.participants.get(gone.participantId);
				if (p != null) {
					p.slot = to.index;
				}
			}
			while (this.slots.size() > count) {
				this.slots.remove(this.slots.size() - 1);
			}
			// A team is named by a number up to the slot count; the computers keep theirs through the change.
			for (final SlotInfo s : this.slots) {
				s.team = Math.min(s.team, count - 1);
			}
		}
		else {
			while (this.slots.size() < count) {
				final SlotInfo s = new SlotInfo();
				s.index = this.slots.size();
				s.color = colorFree(s.index, null);
				s.team = s.index;
				this.slots.add(s);
			}
		}
		return true;
	}

	/** The wanted color when no other slot in play has it, else the lowest one nobody has. */
	private int colorFree(final int wanted, final SlotInfo except) {
		for (int c = wanted;; c = c == wanted ? 0 : c + 1) {
			boolean taken = false;
			for (final SlotInfo s : this.slots) {
				if (s != except && s.kind != LanProtocol.SLOT_OPEN && s.kind != LanProtocol.SLOT_CLOSED
						&& s.color == c) {
					taken = true;
				}
			}
			if (!taken) {
				return c;
			}
			if (c >= LanProtocol.MAX_SLOTS) {
				return wanted;
			}
		}
	}

	/** The participant's last reported {@code MAP_STATUS_*}, or -1. */
	public int mapStatusOf(final int participantId) {
		return query(new Callable<Integer>() {
			@Override
			public Integer call() {
				final Participant p = LanHost.this.participants.get(participantId);
				return p == null ? -1 : p.mapStatus;
			}
		});
	}

	/** Announces the game through the given discovery for as long as the host lives. */
	public void setDiscovery(final LanDiscovery discovery) {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				LanHost.this.discovery = discovery;
				updateAnnouncement();
			}
		});
	}

	public long gameId() {
		return this.gameId;
	}

	/** Starts listening; returns the TCP port actually bound. */
	public int open() throws IOException {
		this.server = this.loop.listen(this.config.port, this.config.portTries, new LanLoop.AcceptListener() {
			@Override
			public void onAccept(final LanConnection c) {
				accepted(c);
			}
		});
		this.loop.addShutdownHook(new Runnable() {
			@Override
			public void run() {
				closeOnLoop();
			}
		});
		return this.server.port();
	}

	public int port() {
		return this.server.port();
	}

	public void close() {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				closeOnLoop();
			}
		});
	}

	/** Snapshot helpers for tests and UI, evaluated on the loop thread. */
	public int state() {
		return query(new Callable<Integer>() {
			@Override
			public Integer call() {
				return LanHost.this.state;
			}
		});
	}

	public int participantCount() {
		return query(new Callable<Integer>() {
			@Override
			public Integer call() {
				return LanHost.this.participants.size();
			}
		});
	}

	/** Whether a player other than the host's own is in the running game, connected. */
	public boolean hasOtherPlayersInGame() {
		return query(new Callable<Boolean>() {
			@Override
			public Boolean call() {
				if (LanHost.this.state != LanProtocol.STATE_IN_GAME && LanHost.this.state != LanProtocol.STATE_LOADING) {
					return false;
				}
				for (final Participant p : LanHost.this.participants.values()) {
					if (p.id != LanHost.this.ownerId && p.role == LanProtocol.ROLE_PLAYER && p.conn != null
							&& (p.state == LanProtocol.PRESENCE_PLAYING || p.state == LanProtocol.PRESENCE_REJOINING)) {
						return true;
					}
				}
				return false;
			}
		});
	}

	public int presenceOf(final int participantId) {
		return query(new Callable<Integer>() {
			@Override
			public Integer call() {
				final Participant p = LanHost.this.participants.get(participantId);
				return p == null ? -1 : p.state;
			}
		});
	}

	public TurnArchive archive() {
		return this.archive;
	}

	private <T> T query(final Callable<T> c) {
		try {
			return this.loop.call(c);
		}
		catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	// ---------------------------------------------------------------- takeover

	/**
	 * A host made from what a participant knows of the game its host has left (see {@link Takeover}); it runs on the
	 * participant's own network thread. The others come to it (MIGRATE_HELLO), the turns only some of them hold are
	 * handed over, and then it makes the turns: see {@link #finishTakeover}.
	 */
	public LanHost(final LanLoop loop, final Config config, final Takeover from) {
		this(loop, config);
		takeOver(from);
	}

	private void takeOver(final Takeover t) {
		this.gameId = t.gameId;
		this.state = t.gameState;
		this.previousHost = t.previousHost;
		this.ownerId = t.selfId;
		this.takenMapFile = t.mapFile;
		this.slots.clear();
		this.migrating = true;
		int highest = 0;
		if (t.gameState == LanProtocol.STATE_LOBBY) {
			final LanMessages.LobbyState l = t.lobby;
			for (final SlotInfo s : l.slots) {
				final SlotInfo c = s.copy();
				if (c.kind == LanProtocol.SLOT_HUMAN && c.participantId == t.previousHost) {
					c.kind = LanProtocol.SLOT_OPEN;
					c.participantId = LanProtocol.NO_PARTICIPANT;
					c.ready = false;
				}
				this.slots.add(c);
				if (c.kind == LanProtocol.SLOT_HUMAN) {
					final Participant p = new Participant();
					p.id = c.participantId;
					p.name = c.name;
					p.portrait = l.portraits.getOrDefault(c.participantId, "");
					p.role = LanProtocol.ROLE_PLAYER;
					p.slot = c.index;
					p.controlScheme = c.controlScheme;
					p.ready = c.ready;
					p.address = "";
					this.participants.put(p.id, p);
					highest = Math.max(highest, p.id);
				}
			}
			for (final LanData.ObserverInfo o : l.observers) {
				if (o.participantId == t.previousHost) {
					continue;
				}
				final Participant p = new Participant();
				p.id = o.participantId;
				p.name = o.name;
				p.portrait = l.portraits.getOrDefault(o.participantId, "");
				p.role = o.role;
				p.controlScheme = o.controlScheme;
				p.address = "";
				this.participants.put(p.id, p);
				highest = Math.max(highest, p.id);
			}
			this.config.options = l.options.copy();
			this.config.map = l.map;
			this.config.syncWarning = l.syncWarning;
			this.config.maxSlots = Math.max(1, this.slots.size());
			this.config.mapFile = t.mapFile;
			if (t.mapFile != null) {
				this.maps.setMap(t.mapFile, l.map.size);
			}
			this.rev = l.rev + 1;
		}
		else {
			this.setup = t.setup;
			this.archive = t.archive;
			this.checksumLog.putAll(t.checksums);
			this.config.options = t.setup.options.copy();
			this.config.map = t.setup.map;
			this.config.mapFile = t.mapFile;
			this.config.maxSlots = Math.max(1, t.setup.slots.size());
			if (t.mapFile != null) {
				this.maps.setMap(t.mapFile, t.setup.map.size);
			}
			for (final SlotInfo s : t.setup.slots) {
				this.slots.add(s.copy());
			}
			for (final LanData.ParticipantInfo pi : t.setup.participants) {
				final Participant p = new Participant();
				p.id = pi.id;
				p.name = pi.name;
				p.portrait = "";
				p.role = pi.role;
				p.slot = pi.slot;
				p.controlScheme = pi.controlScheme;
				p.address = pi.address;
				p.listenPort = pi.listenPort;
				p.tokenHash = pi.tokenHash;
				if (pi.id == t.selfId) {
					p.token = t.selfToken.clone();
				}
				final Integer known = t.presence.get(pi.id);
				p.state = known == null ? LanProtocol.PRESENCE_PLAYING : known;
				p.loaded = false;
				this.participants.put(p.id, p);
				highest = Math.max(highest, p.id);
			}
			final int size = this.archive.size();
			this.nextTurn = size;
			try {
				for (final TurnData turn : this.archive.range(0, size)) {
					for (final CmdEntry e : turn.cmds) {
						final Participant p = participantInSlot(e.slot);
						if (p != null && e.seq > p.lastSeq) {
							p.lastSeq = e.seq;
						}
					}
				}
			}
			catch (final LanProtocolException e) {
				LanLog.warn("turn archive unreadable at takeover", e);
			}
			for (final Participant p : this.participants.values()) {
				p.ackTurn = size - 1;
			}
		}
		this.nextId = highest + 1;
		for (final Participant p : this.participants.values()) {
			p.awaited = p.id != t.previousHost
					&& (t.gameState == LanProtocol.STATE_LOBBY || p.state == LanProtocol.PRESENCE_PLAYING);
		}
		if (t.gameState == LanProtocol.STATE_LOBBY) {
			this.participants.remove(t.previousHost);
		}
		this.migrationTimer = this.loop.schedule(this.loop.timings().migrateWaitMs, new Runnable() {
			@Override
			public void run() {
				LanHost.this.migrationTimer = null;
				finishTakeover();
			}
		});
	}

	/** Uses a listening socket the process already has; its connections come in through {@link #adopt}. */
	public void attachServer(final LanLoop.ServerHandle handle) {
		this.server = handle;
		this.loop.addShutdownHook(new Runnable() {
			@Override
			public void run() {
				closeOnLoop();
			}
		});
	}

	/** A connection somebody else accepted whose first message is already read. Loop thread. */
	public void adopt(final LanConnection c, final LanMessage first) {
		this.connections.add(c);
		final Handler h = new Handler();
		c.setListener(h);
		h.onMessage(c, first);
	}

	/** The start with the addresses the players have now: a host that took over knows them better than the start. */
	private LanGameSetup currentSetup() {
		if (this.previousHost == LanProtocol.NO_PARTICIPANT || this.setup == null) {
			return this.setup;
		}
		final LanGameSetup g = new LanGameSetup();
		g.gameId = this.setup.gameId;
		g.seed = this.setup.seed;
		g.turnSteps = this.setup.turnSteps;
		g.syncLimit = this.setup.syncLimit;
		g.options = this.setup.options;
		g.map = this.setup.map;
		g.slots.addAll(this.setup.slots);
		g.hostOrder = this.setup.hostOrder;
		g.header = this.setup.header;
		for (final LanData.ParticipantInfo old : this.setup.participants) {
			final Participant p = this.participants.get(old.id);
			if (p == null || p.address == null || p.address.isEmpty()) {
				g.participants.add(old);
				continue;
			}
			final LanData.ParticipantInfo pi = new LanData.ParticipantInfo();
			pi.id = old.id;
			pi.name = old.name;
			pi.role = old.role;
			pi.slot = old.slot;
			pi.address = p.address;
			pi.listenPort = p.listenPort;
			pi.controlScheme = p.controlScheme;
			pi.tokenHash = old.tokenHash;
			g.participants.add(pi);
		}
		return g;
	}

	/** Asks the participant for the turns it holds and this host lacks. */
	private void askFill(final Participant p) {
		if (p.conn == null || this.archive == null || p.holdsThrough < this.archive.size()) {
			return;
		}
		p.fillAsked = true;
		this.fillOutstanding.add(p.id);
		p.conn.send(new LanMessages.TurnFillRequest(this.archive.size(), p.holdsThrough + 1));
	}

	private void onFill(final Participant p, final LanMessages.TurnFill fill) {
		p.fillAsked = false;
		this.fillOutstanding.remove(p.id);
		if (this.archive == null || this.state != LanProtocol.STATE_IN_GAME) {
			return;
		}
		boolean grew = false;
		for (final TurnData t : fill.turns) {
			if (t.n < this.archive.size()) {
				continue;
			}
			if (t.n != this.archive.size() || t.n > p.holdsThrough) {
				break;
			}
			try {
				this.archive.append(t);
			}
			catch (final LanProtocolException e) {
				LanLog.warn("cannot archive handed over turn", e);
				break;
			}
			this.nextTurn = this.archive.size();
			this.filled++;
			grew = true;
			for (final CmdEntry e : t.cmds) {
				final Participant q = participantInSlot(e.slot);
				if (q != null && e.seq > q.lastSeq) {
					q.lastSeq = e.seq;
				}
			}
			for (final PresenceEntry e : t.presence) {
				final Participant q = this.participants.get(e.participant);
				if (q != null) {
					q.state = e.state;
					if (e.state == LanProtocol.PRESENCE_LEFT || e.state == LanProtocol.PRESENCE_ABSENT) {
						this.simPausedBy.remove(q.id);
					}
				}
			}
			broadcastTurn(new LanMessages.Turn(t));
		}
		if (grew) {
			askFill(p);
		}
		maybeFinishTakeover();
	}

	/** The others have come, or the time for it is up, and every turn anyone brought is in: the game goes on. */
	private void maybeFinishTakeover() {
		if (!this.migrating || !this.fillOutstanding.isEmpty()) {
			return;
		}
		for (final Participant p : this.participants.values()) {
			if (p.awaited && p.conn == null) {
				return;
			}
		}
		finishTakeover();
	}

	private void finishTakeover() {
		if (!this.migrating) {
			return;
		}
		this.migrating = false;
		if (this.migrationTimer != null) {
			this.migrationTimer.cancel();
			this.migrationTimer = null;
		}
		if (this.archive != null) {
			this.migrationBase = this.archive.size();
		}
		if (this.state == LanProtocol.STATE_LOBBY) {
			for (final Participant p : new ArrayList<Participant>(this.participants.values())) {
				if (p.conn == null) {
					removeFromLobby(p);
				}
			}
			this.rev++;
			broadcast(new LanMessages.HostChanged(this.ownerId, 0));
			broadcast(new LanMessages.CountdownCancel());
			broadcastLobby();
			return;
		}
		final Participant gone = this.participants.get(this.previousHost);
		if (gone != null
				&& (gone.state == LanProtocol.PRESENCE_PLAYING || gone.state == LanProtocol.PRESENCE_REJOINING)) {
			setPresence(gone, LanProtocol.PRESENCE_ABSENT);
		}
		for (final Participant p : this.participants.values()) {
			if (p.conn == null && p.state == LanProtocol.PRESENCE_PLAYING && p.absentTimer == null) {
				startAbsentTimer(p);
			}
		}
		final List<Object[]> held = new ArrayList<Object[]>(this.heldCommands);
		this.heldCommands.clear();
		for (final Object[] h : held) {
			final Participant p = (Participant) h[0];
			if (p.conn == null) {
				continue;
			}
			try {
				onCommand(p, p.conn, (LanMessages.Cmd) h[1]);
			}
			catch (final LanProtocolException e) {
				p.conn.close("protocol violation: " + e.getMessage());
			}
		}
		broadcast(new LanMessages.HostChanged(this.ownerId, this.archive.size()));
		if (this.state == LanProtocol.STATE_IN_GAME) {
			this.nextTurnDue = LanLoop.nowMs() + this.config.turnIntervalMs;
			scheduleTurn();
		}
		else if (this.state == LanProtocol.STATE_LOADING) {
			startLoadTimer();
			maybeGo();
		}
		updateAnnouncement();
	}

	/** How many turns were handed to this host by participants that had them (a host that took the game over). */
	public int filledTurns() {
		return query(new Callable<Integer>() {
			@Override
			public Integer call() {
				return LanHost.this.filled;
			}
		});
	}

	public int ownerId() {
		return query(new Callable<Integer>() {
			@Override
			public Integer call() {
				return LanHost.this.ownerId;
			}
		});
	}

	/**
	 * The host's own player is done and the game goes on without it: its leaving goes into a turn that everybody gets,
	 * then the connections are closed, which is what sends the others to the next host. {@code done} runs after that,
	 * on the loop thread.
	 */
	public void retire(final Runnable done) {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				final Participant me = LanHost.this.participants.get(LanHost.this.ownerId);
				if (me != null && LanHost.this.state == LanProtocol.STATE_IN_GAME
						&& me.state != LanProtocol.PRESENCE_LEFT) {
					setPresence(me, LanProtocol.PRESENCE_LEFT);
				}
				retireWhenSent(done, LanLoop.nowMs() + 1500);
			}
		});
	}

	private void retireWhenSent(final Runnable done, final long deadline) {
		boolean busy = !this.pendingPresence.isEmpty() && this.state == LanProtocol.STATE_IN_GAME;
		for (final LanConnection c : this.connections) {
			if (c.pendingBytes() > 0) {
				busy = true;
			}
		}
		if (busy && LanLoop.nowMs() < deadline) {
			this.loop.schedule(10, new Runnable() {
				@Override
				public void run() {
					retireWhenSent(done, deadline);
				}
			});
			return;
		}
		// nobody may come back to a host that has left: the others go to the next one
		this.retired = true;
		if (this.server != null) {
			this.server.close();
			this.server = null;
		}
		for (final LanConnection c : new ArrayList<LanConnection>(this.connections)) {
			c.closeAfterFlush("host left");
		}
		this.loop.schedule(200, done);
	}

	// ------------------------------------------------------------- connections

	private void accepted(final LanConnection c) {
		if (this.retired) {
			c.close("host left");
			return;
		}
		if (this.connections.size() >= LanProtocol.MAX_CONNECTIONS) {
			reject(c, new LanMessages.Reject(LanProtocol.REJECT_FULL, "Too many connections"));
			return;
		}
		this.connections.add(c);
		final Handler h = new Handler();
		c.setListener(h);
		h.helloTimer = this.loop.schedule(this.loop.timings().helloTimeoutMs, new Runnable() {
			@Override
			public void run() {
				if (h.participant == null) {
					c.close("hello timeout");
				}
			}
		});
	}

	private void reject(final LanConnection c, final LanMessages.Reject r) {
		r.hostVersion = this.config.engineVersion;
		c.send(r);
		c.closeAfterFlush("rejected");
	}

	private void onHello(final LanConnection c, final Handler h, final LanMessages.Hello hello) {
		if (hello.protocolVersion != LanProtocol.PROTOCOL_VERSION) {
			reject(c, new LanMessages.Reject(LanProtocol.REJECT_VERSION, "Different protocol version"));
			return;
		}
		if (!hello.engineVersion.equals(this.config.engineVersion)
				|| !hello.buildCommit.equals(this.config.buildCommit)) {
			reject(c, new LanMessages.Reject(LanProtocol.REJECT_VERSION, "Different version"));
			return;
		}
		if (hello.dataChecksum != this.config.dataChecksum) {
			reject(c, new LanMessages.Reject(LanProtocol.REJECT_DATA, "Different game data"));
			return;
		}
		if (hello.playerName.trim().isEmpty()) {
			reject(c, new LanMessages.Reject(LanProtocol.REJECT_BAD_NAME, "Invalid name"));
			return;
		}
		final String address = c.remoteAddress() == null ? "" : c.remoteAddress().getAddress().getHostAddress();
		if (hello.rejoin != null) {
			onRejoinHello(c, h, hello, address);
			return;
		}
		if (this.kickedAddresses.contains(address)) {
			reject(c, new LanMessages.Reject(LanProtocol.REJECT_KICKED, "You were kicked from the game"));
			return;
		}
		if (this.state != LanProtocol.STATE_LOBBY) {
			reject(c, new LanMessages.Reject(LanProtocol.REJECT_STARTED, "Game has started"));
			return;
		}
		if (this.participants.size() >= LanProtocol.MAX_PARTICIPANTS) {
			reject(c, new LanMessages.Reject(LanProtocol.REJECT_FULL, "Game is full"));
			return;
		}
		int role = hello.wantedRole;
		SlotInfo free = null;
		if (role == LanProtocol.ROLE_PLAYER) {
			for (final SlotInfo s : this.slots) {
				if (s.kind == LanProtocol.SLOT_OPEN) {
					free = s;
					break;
				}
			}
			if (free == null) {
				reject(c, new LanMessages.Reject(LanProtocol.REJECT_FULL, "Game is full"));
				return;
			}
		}
		else if (!observerRoleAllowed(role)) {
			reject(c, new LanMessages.Reject(LanProtocol.REJECT_FULL, "Observers not allowed"));
			return;
		}
		else if (this.participants.size() + openSlots() >= LanProtocol.MAX_PARTICIPANTS || !watchSlotFree()) {
			// the open seats stay for the players, and a watcher needs a seat of the game's players that is not taken
			reject(c, new LanMessages.Reject(LanProtocol.REJECT_FULL, "Game is full"));
			return;
		}
		final Participant p = new Participant();
		p.id = newParticipantId();
		p.token = new byte[LanProtocol.TOKEN_BYTES];
		this.config.random.nextBytes(p.token);
		p.name = hello.playerName;
		p.portrait = hello.portrait;
		p.role = role;
		p.controlScheme = hello.controlScheme;
		p.address = address;
		p.listenPort = hello.listenPort;
		p.conn = c;
		h.participant = p;
		c.attachment = p;
		this.participants.put(p.id, p);
		if (free != null) {
			free.kind = LanProtocol.SLOT_HUMAN;
			free.participantId = p.id;
			p.slot = free.index;
		}
		if (this.ownerId == LanProtocol.NO_PARTICIPANT) {
			this.ownerId = p.id;
		}
		this.rev++;
		final LanMessages.Welcome w = new LanMessages.Welcome();
		w.participantId = p.id;
		w.slotToken = p.token.clone();
		w.gameId = this.gameId;
		w.ownerId = this.ownerId;
		w.gameState = this.state;
		w.lobby = lobbyState();
		c.send(w);
		broadcastLobby();
	}

	/** The game watches from a player seat no one has: there is one unless every seat of the largest game is taken. */
	private boolean watchSlotFree() {
		if (this.slots.size() < LanProtocol.MAX_SLOTS) {
			return true;
		}
		for (final SlotInfo s : this.slots) {
			if (s.kind == LanProtocol.SLOT_OPEN || s.kind == LanProtocol.SLOT_CLOSED) {
				return true;
			}
		}
		return false;
	}

	private int openSlots() {
		int open = 0;
		for (final SlotInfo s : this.slots) {
			if (s.kind == LanProtocol.SLOT_OPEN) {
				open++;
			}
		}
		return open;
	}

	/** The next number, or, when a long lobby has used up those the messages can hold, the lowest one free. */
	private int newParticipantId() {
		if (this.nextId < 250) {
			return this.nextId++;
		}
		for (int id = 0; id < 250; id++) {
			if (!this.participants.containsKey(id)) {
				return id;
			}
		}
		throw new IllegalStateException("no participant number is free");
	}

	private boolean observerRoleAllowed(final int role) {
		final int mode = this.config.options.observers;
		return (role == LanProtocol.ROLE_OBSERVER && mode >= LanProtocol.OBSERVERS_OBSERVERS)
				|| (role == LanProtocol.ROLE_REFEREE && mode >= LanProtocol.OBSERVERS_REFEREES);
	}

	private static boolean tokenMatches(final Participant p, final byte[] token) {
		if (p.token != null) {
			return RejoinKey.tokenEquals(p.token, token);
		}
		if (p.tokenHash != null && java.security.MessageDigest.isEqual(RejoinKey.hash(token), p.tokenHash)) {
			p.token = token.clone();
			return true;
		}
		return false;
	}

	private void onRejoinHello(final LanConnection c, final Handler h, final LanMessages.Hello hello,
			final String address) {
		if (this.state != LanProtocol.STATE_LOADING && this.state != LanProtocol.STATE_IN_GAME) {
			reject(c, new LanMessages.Reject(LanProtocol.REJECT_BAD_HELLO, "No game to rejoin"));
			return;
		}
		Participant found = null;
		if (hello.rejoin.gameId == this.gameId) {
			for (final Participant p : this.participants.values()) {
				if (tokenMatches(p, hello.rejoin.token)) {
					found = p;
				}
			}
		}
		if (found == null || found.state == LanProtocol.PRESENCE_LEFT) {
			reject(c, new LanMessages.Reject(LanProtocol.REJECT_BAD_HELLO, "Unknown rejoin key"));
			return;
		}
		resumeInGame(c, h, found, address, hello.listenPort, hello.controlScheme, hello.rejoin.haveTurns);
	}

	/**
	 * A participant of a game (or of a lobby, for a host that took it over) comes to this host without a HELLO: it was
	 * playing with the host that is gone, or is being moved to this one. Only the participants the lobby or the start
	 * named can; a token this host never saw is taken on the first time it is shown, or checked against the hash the
	 * start carried.
	 */
	private void onMigrateHello(final LanConnection c, final Handler h, final LanMessages.MigrateHello m) {
		final Participant p = this.participants.get(m.participantId);
		final String address = c.remoteAddress() == null ? "" : c.remoteAddress().getAddress().getHostAddress();
		final boolean checkable = p != null && (p.token != null || p.tokenHash != null);
		if (p == null || this.state == LanProtocol.STATE_ENDED
				|| (checkable ? !tokenMatches(p, m.token) : this.state != LanProtocol.STATE_LOBBY)) {
			reject(c, new LanMessages.Reject(LanProtocol.REJECT_BAD_HELLO, "Unknown participant"));
			return;
		}
		if (p.token == null) {
			p.token = m.token.clone();
		}
		if (this.state == LanProtocol.STATE_LOBBY) {
			resumeInLobby(c, h, p, address, m.listenPort);
			return;
		}
		if (p.state == LanProtocol.PRESENCE_LEFT) {
			reject(c, new LanMessages.Reject(LanProtocol.REJECT_BAD_HELLO, "Unknown participant"));
			return;
		}
		resumeInGame(c, h, p, address, m.listenPort, p.controlScheme, m.lastTurn + 1);
	}

	/** Gives the participant this connection, closing the one it had. */
	private void bind(final LanConnection c, final Handler h, final Participant p, final String address,
			final int listenPort) {
		if (p.conn != null) {
			p.conn.attachment = null;
			this.maps.cancel(p.id);
			final LanConnection old = p.conn;
			p.conn = null;
			old.close("replaced by new connection");
		}
		stopStream(p);
		if (p.absentTimer != null) {
			p.absentTimer.cancel();
			p.absentTimer = null;
		}
		p.conn = c;
		p.awaitAck = false;
		p.address = address;
		if (listenPort != 0) {
			p.listenPort = listenPort;
		}
		p.sawHost = true;
		h.participant = p;
		c.attachment = p;
	}

	private LanMessages.Welcome welcomeFor(final Participant p) {
		final LanMessages.Welcome w = new LanMessages.Welcome();
		w.participantId = p.id;
		w.slotToken = p.token.clone();
		w.gameId = this.gameId;
		w.ownerId = this.ownerId;
		w.gameState = this.state;
		w.lobby = lobbyState();
		return w;
	}

	private void resumeInLobby(final LanConnection c, final Handler h, final Participant p, final String address,
			final int listenPort) {
		bind(c, h, p, address, listenPort);
		c.send(welcomeFor(p));
		p.connectedSinceTakeover = true;
		if (this.migrating) {
			maybeFinishTakeover();
		}
		else {
			this.rev++;
			broadcastLobby();
		}
	}

	private void resumeInGame(final LanConnection c, final Handler h, final Participant p, final String address,
			final int listenPort, final int controlScheme, final int requestedHave) {
		final boolean loading = this.state == LanProtocol.STATE_LOADING;
		// Turns beyond this host's own that a participant holds are the old host's, unless they were handed over
		// while this host was gathering them: a player that never came meanwhile starts over.
		int haveTurns = requestedHave;
		final boolean holdsStrangeTurns = !p.sawHost && haveTurns > this.migrationBase;
		if (holdsStrangeTurns) {
			haveTurns = 0;
		}
		bind(c, h, p, address, listenPort);
		p.controlScheme = controlScheme;
		p.holdsThrough = haveTurns - 1;
		p.connectedSinceTakeover = true;
		c.send(welcomeFor(p));
		if (holdsStrangeTurns && !loading) {
			// Its game has applied turns nobody else has: that game is dropped and the real one played from the start.
			c.send(new LanMessages.Resync(LanProtocol.RESYNC_TURNS, -1));
			awaitAck(p);
			p.recovering = true;
			if (this.listener != null) {
				this.listener.onResync(p.id, LanProtocol.RESYNC_TURNS, -1);
			}
		}
		if (loading) {
			// nothing to catch up yet: it just loads again
			p.loaded = false;
			if (this.previousHost == LanProtocol.NO_PARTICIPANT) {
				c.send(new LanMessages.Start(currentSetup()));
			}
			maybeFinishTakeover();
			return;
		}
		final int size = this.archive == null ? 0 : this.archive.size();
		final int have = Math.min(haveTurns, size);
		// A link that only broke (the client kept its turns) is a short wait for the others, not an absence; a client
		// that starts over, or was already absent, plays the game from the first turn while the others go on.
		final boolean cold = have == 0 || (p.state != LanProtocol.PRESENCE_PLAYING
				&& p.state != LanProtocol.PRESENCE_REJOINING);
		if (cold) {
			setPresence(p, LanProtocol.PRESENCE_REJOINING);
			p.rejoinStarted = true;
		}
		p.rejoinChecks.clear();
		// what it holds beyond this host's turns it has already acknowledged to the host it came from
		p.ackTurn = Math.max(have, haveTurns) - 1;
		sendCatchUp(p, have);
		if (haveTurns > size && this.state == LanProtocol.STATE_IN_GAME) {
			askFill(p);
		}
		maybeFinishTakeover();
	}

	/** REJOIN, then the archived turns from {@code from} in paced, compressed blocks; live turns follow them. */
	private void sendCatchUp(final Participant p, final int from) {
		final int total = this.archive.size();
		p.conn.send(new LanMessages.Rejoin(currentSetup(), from, total, p.lastSeq,
				(int) Math.min(Integer.MAX_VALUE, this.archive.totalSteps())));
		p.streamNext = from;
		p.streamConn = p.conn;
		streamMore(p);
	}

	private void stopStream(final Participant p) {
		p.streamNext = -1;
		p.streamConn = null;
		if (p.streamTimer != null) {
			p.streamTimer.cancel();
			p.streamTimer = null;
		}
	}

	/**
	 * Sends the next blocks while the connection's queue is short, so a long game never piles up in memory and small
	 * messages never wait behind it. The turns made meanwhile are not broadcast to the participant (see
	 * {@link #broadcastTurn}); the last block is cut at the archive's end at that moment, and from then on it is live.
	 */
	private void streamMore(final Participant p) {
		p.streamTimer = null;
		final LanConnection c = p.streamConn;
		if (p.streamNext < 0 || c == null || p.conn != c || !c.isOpen()) {
			stopStream(p);
			return;
		}
		try {
			while (p.streamNext >= 0 && c.pendingBytes() <= LanConnection.DRAIN_LEVEL) {
				final int size = this.archive.size();
				if (p.streamNext >= size) {
					p.streamNext = -1;
					p.streamConn = null;
					return;
				}
				int count = Math.min(BLOCK_TURNS, size - p.streamNext);
				LanMessages.TurnBlock block = null;
				List<TurnData> turns = null;
				while (count >= 1) {
					turns = this.archive.range(p.streamNext, p.streamNext + count);
					block = LanMessages.TurnBlock.of(turns);
					if (block != null || count == 1) {
						break;
					}
					count /= 2;
				}
				if (block != null) {
					c.send(block);
				}
				else {
					c.send(new LanMessages.Turn(turns.get(0)));
				}
				p.streamNext += count;
			}
		}
		catch (final LanProtocolException e) {
			stopStream(p);
			c.close("archive unreadable");
			return;
		}
		if (p.streamNext >= 0) {
			p.streamTimer = this.loop.schedule(STREAM_RETRY_MS, new Runnable() {
				@Override
				public void run() {
					streamMore(p);
				}
			});
		}
	}

	private void onDisconnected(final Participant p, final String reason) {
		p.conn = null;
		stopStream(p);
		this.maps.cancel(p.id);
		p.fillAsked = false;
		this.fillOutstanding.remove(p.id);
		if (this.state == LanProtocol.STATE_LOBBY) {
			if (this.migrating) {
				// still expected: the lobby waits for it until the time for coming is up
				return;
			}
			removeFromLobby(p);
			return;
		}
		if (p.state == LanProtocol.PRESENCE_LEFT || p.state == LanProtocol.PRESENCE_ABSENT) {
			return;
		}
		if (p.state == LanProtocol.PRESENCE_REJOINING) {
			setPresence(p, LanProtocol.PRESENCE_ABSENT);
			return;
		}
		startAbsentTimer(p);
		if (this.state == LanProtocol.STATE_LOADING) {
			maybeGo();
		}
		maybeFinishTakeover();
	}

	private void startAbsentTimer(final Participant p) {
		if (p.absentTimer != null) {
			p.absentTimer.cancel();
		}
		p.absentTimer = this.loop.schedule(this.loop.timings().absentAfterMs, new Runnable() {
			@Override
			public void run() {
				if (p.conn == null && p.state == LanProtocol.PRESENCE_PLAYING) {
					setPresence(p, LanProtocol.PRESENCE_ABSENT);
				}
			}
		});
	}

	// ------------------------------------------------------------------- lobby

	private void removeFromLobby(final Participant p) {
		this.participants.remove(p.id);
		if (p.slot != LanProtocol.NO_SLOT) {
			final SlotInfo s = this.slots.get(p.slot);
			s.kind = LanProtocol.SLOT_OPEN;
			s.participantId = LanProtocol.NO_PARTICIPANT;
			s.ready = false;
		}
		if (this.ownerId == p.id) {
			this.ownerId = nextOwner();
		}
		if (this.countingDown) {
			cancelCountdown();
		}
		this.rev++;
		broadcastLobby();
	}

	/** The lobby's next owner: the first player by seat, then the watchers; the order the game's hosts go in too. */
	private int nextOwner() {
		int best = LanProtocol.NO_PARTICIPANT;
		int bestKey = Integer.MAX_VALUE;
		for (final Participant q : this.participants.values()) {
			final int key = LanProtocol.hostKey(q.role, q.slot, q.id);
			if (key < bestKey) {
				bestKey = key;
				best = q.id;
			}
		}
		return best;
	}

	private LanMessages.LobbyState lobbyState() {
		final LanMessages.LobbyState ls = new LanMessages.LobbyState();
		ls.rev = this.rev;
		ls.ownerId = this.ownerId;
		for (final SlotInfo s : this.slots) {
			final SlotInfo c = s.copy();
			final Participant p = s.kind == LanProtocol.SLOT_HUMAN ? this.participants.get(s.participantId) : null;
			if (p != null) {
				c.name = p.name;
				c.ready = p.ready;
				c.pingMs = p.conn == null ? 0 : Math.max(0, Math.min(65535, p.conn.rttMs()));
				c.controlScheme = p.controlScheme;
			}
			ls.slots.add(c);
		}
		for (final Participant p : this.participants.values()) {
			if (p.role != LanProtocol.ROLE_PLAYER) {
				final LanData.ObserverInfo o = new LanData.ObserverInfo();
				o.participantId = p.id;
				o.name = p.name;
				o.role = p.role;
				o.controlScheme = p.controlScheme;
				o.pingMs = p.conn == null ? 0 : Math.max(0, Math.min(65535, p.conn.rttMs()));
				ls.observers.add(o);
			}
		}
		for (final Participant p : this.participants.values()) {
			if ((p.portrait != null) && !p.portrait.isEmpty()) {
				ls.portraits.put(p.id, p.portrait);
			}
		}
		for (final Participant p : this.participants.values()) {
			if (p.listenPort != 0 && p.address != null && !p.address.isEmpty() && p.conn != null) {
				ls.members.add(new LanData.MemberInfo(p.id, p.address, p.listenPort));
			}
		}
		ls.options = this.config.options.copy();
		ls.map = this.config.map;
		ls.syncWarning = this.config.syncWarning;
		return ls;
	}

	private void broadcastLobby() {
		broadcast(lobbyState());
		updateAnnouncement();
	}

	private void broadcast(final LanMessage m) {
		final byte[] frame = LanConnection.encode(m);
		for (final Participant p : this.participants.values()) {
			if (p.conn != null) {
				p.conn.sendFrame(frame);
			}
		}
	}

	/** A turn goes to everyone except those still being sent the archive, who get it from there. */
	private void broadcastTurn(final LanMessages.Turn turn) {
		final byte[] frame = LanConnection.encode(turn);
		for (final Participant p : this.participants.values()) {
			if (p.conn != null && p.streamNext < 0 && turn.turn.n > p.holdsThrough) {
				p.conn.sendFrame(frame);
			}
		}
	}

	/** A map that clients can verify is announced, so nobody is ready before reporting that they have it. */
	private boolean mapRequired() {
		return this.config.map.size > 0 && this.config.map.sha256.length == LanProtocol.SHA256_BYTES;
	}

	private void onMapRequest(final Participant p, final LanConnection c, final LanMessages.MapRequest r) {
		if (r.offset >= this.config.map.size || !this.maps.serve(p.id, c, r.offset)) {
			c.close("map request refused");
		}
	}

	private void onMapStatus(final Participant p, final LanConnection c, final LanMessages.MapStatus s) {
		p.mapStatus = s.status;
		p.mapPercent = s.percent;
		if (s.status != LanProtocol.MAP_STATUS_DOWNLOADING) {
			this.maps.cancel(p.id);
		}
		if (s.status == LanProtocol.MAP_STATUS_FAILED) {
			c.closeAfterFlush("map download failed");
		}
		if (s.status != LanProtocol.MAP_STATUS_HAVE && p.ready) {
			p.ready = false;
			this.rev++;
			broadcastLobby();
		}
		final MapListener l = this.mapListener;
		if (l != null) {
			l.onMapStatus(p.id, s.status, s.percent);
		}
	}

	private void clearReady() {
		for (final Participant p : this.participants.values()) {
			p.ready = false;
		}
	}

	private void onParticipantMessage(final Participant p, final LanConnection c, final LanMessage m)
			throws LanProtocolException {
		switch (m.type()) {
		case LanProtocol.PLAYER_STATE: {
			final long now = LanLoop.nowMs();
			if (now - p.lastPlayerState < 1000 && p.lastPlayerState != 0) {
				return;
			}
			p.lastPlayerState = now;
			p.controlScheme = ((LanMessages.PlayerState) m).controlScheme;
			if (this.state == LanProtocol.STATE_LOBBY) {
				this.rev++;
				broadcastLobby();
			}
			else {
				broadcast(new LanMessages.PlayerState(p.id, p.controlScheme));
			}
			return;
		}
		case LanProtocol.CHAT:
			onChat(p, (LanMessages.Chat) m);
			return;
		case LanProtocol.LEAVE:
			onLeave(p, c, (LanMessages.Leave) m);
			return;
		case LanProtocol.MAP_REQUEST:
			onMapRequest(p, c, (LanMessages.MapRequest) m);
			return;
		case LanProtocol.MAP_STATUS:
			onMapStatus(p, c, (LanMessages.MapStatus) m);
			return;
		default:
			break;
		}
		if (this.state == LanProtocol.STATE_LOBBY) {
			onLobbyMessage(p, m);
		}
		else if (this.state == LanProtocol.STATE_LOADING || this.state == LanProtocol.STATE_IN_GAME) {
			onGameMessage(p, c, m);
		}
	}

	private void onLobbyMessage(final Participant p, final LanMessage m) {
		switch (m.type()) {
		case LanProtocol.SLOT_REQUEST:
			onSlotRequest(p, (LanMessages.SlotRequest) m);
			return;
		case LanProtocol.READY:
			if (((LanMessages.Ready) m).ready && mapRequired() && p.mapStatus != LanProtocol.MAP_STATUS_HAVE) {
				// sent back so its UI drops a checkmark the host will not honour
				p.conn.send(lobbyState());
				return;
			}
			p.ready = ((LanMessages.Ready) m).ready;
			this.rev++;
			broadcastLobby();
			return;
		case LanProtocol.HOST_SLOT:
			if (p.id == this.ownerId) {
				onHostSlot((LanMessages.HostSlot) m);
			}
			return;
		case LanProtocol.HOST_SETUP:
			if (p.id == this.ownerId) {
				onHostSetup((LanMessages.HostSetup) m);
			}
			return;
		case LanProtocol.HOST_OPTIONS:
			if (p.id == this.ownerId) {
				final LanData.Options o = ((LanMessages.HostOptions) m).options;
				this.config.options = o;
				clearReady();
				this.rev++;
				broadcastLobby();
			}
			return;
		case LanProtocol.START_REQUEST:
			if (p.id == this.ownerId && !this.countingDown) {
				requestStart();
			}
			return;
		case LanProtocol.COUNTDOWN_CANCEL:
			if (p.id == this.ownerId && this.countingDown) {
				cancelCountdown();
			}
			return;
		default:
			p.conn.close("unexpected message " + m.type());
		}
	}

	private void onSlotRequest(final Participant p, final LanMessages.SlotRequest r) {
		if (r.slot == LanProtocol.REQUEST_OBSERVER || r.slot == LanProtocol.REQUEST_REFEREE) {
			final int role = r.slot == LanProtocol.REQUEST_OBSERVER ? LanProtocol.ROLE_OBSERVER
					: LanProtocol.ROLE_REFEREE;
			if (!observerRoleAllowed(role)) {
				return;
			}
			freeSlot(p);
			p.role = role;
			p.ready = false;
		}
		else if (r.slot < this.slots.size()) {
			final SlotInfo target = this.slots.get(r.slot);
			final boolean own = target.participantId == p.id && target.kind == LanProtocol.SLOT_HUMAN;
			if (!own) {
				if (target.kind != LanProtocol.SLOT_OPEN) {
					return;
				}
				freeSlot(p);
				target.kind = LanProtocol.SLOT_HUMAN;
				target.participantId = p.id;
				p.slot = target.index;
				p.role = LanProtocol.ROLE_PLAYER;
			}
			applySetup(target, r.race, r.team, r.color, r.handicap);
			p.ready = false;
		}
		else {
			return;
		}
		this.rev++;
		broadcastLobby();
	}

	private void applySetup(final SlotInfo target, final int race, final int team, final int color,
			final int handicap) {
		boolean colorTaken = false;
		for (final SlotInfo s : this.slots) {
			if (s != target && s.kind != LanProtocol.SLOT_OPEN && s.kind != LanProtocol.SLOT_CLOSED
					&& s.color == color) {
				colorTaken = true;
			}
		}
		target.race = race;
		target.team = Math.min(team, this.slots.size() - 1);
		if (!colorTaken) {
			target.color = color;
		}
		else {
			// Two players asking for one color in the same moment: the first request got it; the other keeps its
			// own, or gets the lowest free one when that was taken in between.
			target.color = colorFree(target.color, target);
		}
		target.handicap = Math.max(50, Math.min(100, handicap));
	}

	/** The host decides a computer's race, team, color and handicap; the choices of the players are their own. */
	private void onHostSetup(final LanMessages.HostSetup a) {
		if (a.slot >= this.slots.size() || this.slots.get(a.slot).kind != LanProtocol.SLOT_COMPUTER) {
			return;
		}
		applySetup(this.slots.get(a.slot), a.race, a.team, a.color, a.handicap);
		this.rev++;
		broadcastLobby();
	}

	private void freeSlot(final Participant p) {
		if (p.slot != LanProtocol.NO_SLOT) {
			final SlotInfo s = this.slots.get(p.slot);
			s.kind = LanProtocol.SLOT_OPEN;
			s.participantId = LanProtocol.NO_PARTICIPANT;
			p.slot = LanProtocol.NO_SLOT;
		}
	}

	private void onHostSlot(final LanMessages.HostSlot a) {
		if (a.action == LanProtocol.ACTION_KICK_WATCHER) {
			final Participant victim = this.participants.get(a.slot);
			if (victim != null && victim.id != this.ownerId && victim.role != LanProtocol.ROLE_PLAYER) {
				kick(victim);
			}
			return;
		}
		if (a.slot >= this.slots.size()) {
			return;
		}
		final SlotInfo s = this.slots.get(a.slot);
		switch (a.action) {
		case LanProtocol.ACTION_OPEN:
			if (s.kind == LanProtocol.SLOT_CLOSED || s.kind == LanProtocol.SLOT_COMPUTER) {
				s.kind = LanProtocol.SLOT_OPEN;
				s.name = "";
			}
			break;
		case LanProtocol.ACTION_CLOSE:
			if (s.kind == LanProtocol.SLOT_OPEN || s.kind == LanProtocol.SLOT_COMPUTER) {
				s.kind = LanProtocol.SLOT_CLOSED;
				s.name = "";
			}
			break;
		case LanProtocol.ACTION_COMPUTER:
			if (s.kind == LanProtocol.SLOT_OPEN || s.kind == LanProtocol.SLOT_CLOSED
					|| s.kind == LanProtocol.SLOT_COMPUTER) {
				s.kind = LanProtocol.SLOT_COMPUTER;
				s.computerLevel = a.level;
				s.name = "Computer";
				s.color = colorFree(s.color, s);
			}
			break;
		case LanProtocol.ACTION_KICK: {
			final Participant victim = s.kind == LanProtocol.SLOT_HUMAN ? this.participants.get(s.participantId)
					: null;
			if (victim != null && victim.id != this.ownerId) {
				kick(victim);
			}
			return;
		}
		default:
			return;
		}
		clearReady();
		this.rev++;
		broadcastLobby();
	}

	private void kick(final Participant victim) {
		if (victim.address != null) {
			this.kickedAddresses.add(victim.address);
		}
		if (victim.conn != null) {
			victim.conn.send(new LanMessages.Kicked("You were kicked from the game"));
			victim.conn.closeAfterFlush("kicked");
		}
	}

	private void onChat(final Participant p, final LanMessages.Chat chat) {
		final long now = LanLoop.nowMs();
		while (!p.chatTimes.isEmpty() && now - p.chatTimes.peek() > 5000) {
			p.chatTimes.poll();
		}
		if (p.chatTimes.size() >= 10) {
			return;
		}
		p.chatTimes.add(now);
		// An observer is heard by the observers only; a referee, like a player, by whom it chooses.
		final int channel = p.role == LanProtocol.ROLE_OBSERVER ? LanProtocol.CHAT_OBSERVERS : chat.channel;
		final byte[] frame = LanConnection.encode(new LanMessages.Chat(channel, p.id, chat.text));
		for (final Participant q : this.participants.values()) {
			if (q.conn == null) {
				continue;
			}
			if (channel == LanProtocol.CHAT_OBSERVERS && q.role == LanProtocol.ROLE_PLAYER) {
				continue;
			}
			if (channel == LanProtocol.CHAT_ALLIES && q.role == LanProtocol.ROLE_PLAYER
					&& p.role == LanProtocol.ROLE_PLAYER && p.slot != LanProtocol.NO_SLOT
					&& q.slot != LanProtocol.NO_SLOT && this.slots.get(q.slot).team != this.slots.get(p.slot).team) {
				continue;
			}
			q.conn.sendFrame(frame);
		}
	}

	private void onLeave(final Participant p, final LanConnection c, final LanMessages.Leave leave) {
		if (this.state == LanProtocol.STATE_LOBBY) {
			c.close("left");
			return;
		}
		if (p.state != LanProtocol.PRESENCE_LEFT) {
			setPresence(p, LanProtocol.PRESENCE_LEFT);
		}
		c.close("left");
	}

	// ------------------------------------------------------------ start and load

	private void requestStart() {
		boolean anyPlayer = false;
		for (final Participant p : this.participants.values()) {
			if (p.role == LanProtocol.ROLE_PLAYER) {
				anyPlayer = true;
				if (!p.ready) {
					return;
				}
			}
		}
		if (!anyPlayer || (hasWatchers() && !watchSlotFree())) {
			return;
		}
		if (this.config.countdownMs <= 0) {
			beginLoading();
			return;
		}
		this.countingDown = true;
		broadcast(new LanMessages.Countdown((this.config.countdownMs + 999) / 1000));
		this.countdownTimer = this.loop.schedule(this.config.countdownMs, new Runnable() {
			@Override
			public void run() {
				LanHost.this.countdownTimer = null;
				LanHost.this.countingDown = false;
				beginLoading();
			}
		});
	}

	private boolean hasWatchers() {
		for (final Participant p : this.participants.values()) {
			if (p.role != LanProtocol.ROLE_PLAYER) {
				return true;
			}
		}
		return false;
	}

	private void cancelCountdown() {
		this.countingDown = false;
		if (this.countdownTimer != null) {
			this.countdownTimer.cancel();
			this.countdownTimer = null;
		}
		broadcast(new LanMessages.CountdownCancel());
	}

	private void beginLoading() {
		final LanGameSetup g = new LanGameSetup();
		g.gameId = this.gameId;
		g.seed = this.config.random.nextLong();
		g.turnSteps = this.config.turnSteps;
		g.syncLimit = this.config.syncLimit;
		g.options = this.config.options.copy();
		g.map = this.config.map;
		if (this.config.header != null) {
			g.header = this.config.header.get();
		}
		for (final SlotInfo s : this.slots) {
			final SlotInfo c = s.copy();
			final Participant p = s.kind == LanProtocol.SLOT_HUMAN ? this.participants.get(s.participantId) : null;
			if (p != null) {
				c.name = p.name;
				c.controlScheme = p.controlScheme;
			}
			c.ready = false;
			g.slots.add(c);
		}
		final List<Integer> order = new ArrayList<Integer>();
		final TreeMap<Integer, Integer> byKey = new TreeMap<Integer, Integer>();
		for (final Participant p : this.participants.values()) {
			byKey.put(LanProtocol.hostKey(p.role, p.slot, p.id), p.id);
		}
		order.addAll(byKey.values());
		g.hostOrder = new int[order.size()];
		for (int i = 0; i < g.hostOrder.length; i++) {
			g.hostOrder[i] = order.get(i);
		}
		for (final Participant p : this.participants.values()) {
			final LanData.ParticipantInfo pi = new LanData.ParticipantInfo();
			pi.id = p.id;
			pi.name = p.name;
			pi.role = p.role;
			pi.slot = p.slot;
			pi.address = p.address;
			pi.listenPort = p.listenPort;
			pi.controlScheme = p.controlScheme;
			pi.tokenHash = RejoinKey.hash(p.token);
			g.participants.add(pi);
		}
		this.setup = g;
		try {
			this.archive = this.config.archiveFile != null ? TurnArchive.create(this.config.archiveFile)
					: new TurnArchive();
		}
		catch (final IOException e) {
			LanLog.warn("cannot create turn archive file, keeping turns in memory", e);
			this.archive = new TurnArchive();
		}
		setState(LanProtocol.STATE_LOADING);
		broadcast(new LanMessages.Start(g));
		startLoadTimer();
		maybeGo();
	}

	/**
	 * The load timeout counts the time without progress: a participant that still reports a rising load percentage
	 * (a cold Reforged load takes minutes) is waited for, up to a hard limit, instead of being sent on as absent.
	 */
	private void startLoadTimer() {
		final long now = LanLoop.nowMs();
		this.loadStartedMs = now;
		for (final Participant p : this.participants.values()) {
			p.loadProgressMs = now;
		}
		scheduleLoadTimer();
	}

	private void scheduleLoadTimer() {
		final int timeout = this.loop.timings().loadTimeoutMs;
		this.loadTimer = this.loop.schedule(timeout, new Runnable() {
			@Override
			public void run() {
				LanHost.this.loadTimer = null;
				if (LanHost.this.state != LanProtocol.STATE_LOADING) {
					return;
				}
				final long now = LanLoop.nowMs();
				if (now - LanHost.this.loadStartedMs < LOAD_HARD_LIMIT_FACTOR * (long) timeout) {
					for (final Participant p : LanHost.this.participants.values()) {
						if (p.conn != null && !p.loaded && p.state == LanProtocol.PRESENCE_PLAYING
								&& now - p.loadProgressMs < timeout) {
							scheduleLoadTimer();
							return;
						}
					}
				}
				go();
			}
		});
	}

	private void maybeGo() {
		if (this.state != LanProtocol.STATE_LOADING) {
			return;
		}
		for (final Participant p : this.participants.values()) {
			if (p.conn != null && !p.loaded && p.state == LanProtocol.PRESENCE_PLAYING) {
				return;
			}
		}
		go();
	}

	private void go() {
		if (this.state != LanProtocol.STATE_LOADING) {
			return;
		}
		if (this.loadTimer != null) {
			this.loadTimer.cancel();
			this.loadTimer = null;
		}
		setState(LanProtocol.STATE_IN_GAME);
		for (final Participant p : this.participants.values()) {
			if (!p.loaded && p.state == LanProtocol.PRESENCE_PLAYING) {
				// not loaded in time: it enters later through the rejoin path, nobody waits for it
				setPresence(p, LanProtocol.PRESENCE_ABSENT);
			}
		}
		broadcast(new LanMessages.Go());
		this.nextTurnDue = LanLoop.nowMs() + this.config.turnIntervalMs;
		scheduleTurn();
	}

	private void setState(final int s) {
		this.state = s;
		updateAnnouncement();
		if (this.listener != null) {
			this.listener.onStateChanged(s);
		}
	}

	// -------------------------------------------------------------------- game

	private void onGameMessage(final Participant p, final LanConnection c, final LanMessage m)
			throws LanProtocolException {
		switch (m.type()) {
		case LanProtocol.LOAD_PROGRESS:
			final int percent = ((LanMessages.LoadProgress) m).percent;
			// A loader repeats its percent while it works through a long step, so any report counts as progress.
			p.loadProgressMs = LanLoop.nowMs();
			p.loadPercent = percent;
			broadcast(new LanMessages.LoadProgress(p.id, p.loadPercent));
			return;
		case LanProtocol.LOADED:
			p.loaded = true;
			if (this.state == LanProtocol.STATE_LOADING) {
				maybeGo();
			}
			else if (!p.rejoinStarted
					&& (p.state == LanProtocol.PRESENCE_ABSENT || p.state == LanProtocol.PRESENCE_REJOINING)) {
				setPresence(p, LanProtocol.PRESENCE_REJOINING);
				p.rejoinStarted = true;
				p.ackTurn = -1;
				sendCatchUp(p, 0);
			}
			return;
		case LanProtocol.CMD:
			if (this.state == LanProtocol.STATE_IN_GAME) {
				if (this.migrating) {
					// numbered against turns that may still be handed over
					this.heldCommands.add(new Object[] { p, m });
				}
				else {
					onCommand(p, c, (LanMessages.Cmd) m);
				}
			}
			return;
		case LanProtocol.TURN_FILL:
			onFill(p, (LanMessages.TurnFill) m);
			return;
		case LanProtocol.TURN_DONE: {
			final int n = ((LanMessages.TurnDone) m).turn;
			if (n >= this.nextTurn) {
				if (n <= p.holdsThrough) {
					// turns a taking-over host is still being handed
					return;
				}
				throw new LanProtocolException("ack for a turn that was never sent");
			}
			if (n > p.ackTurn) {
				p.ackTurn = n;
			}
			return;
		}
		case LanProtocol.CAUGHT_UP:
			if (p.state == LanProtocol.PRESENCE_REJOINING && p.streamNext < 0) {
				setPresence(p, LanProtocol.PRESENCE_PLAYING);
				p.rejoinStarted = false;
				p.recovering = false;
				// Sums for steps the others had not reached when the player reported them are compared now.
				final TreeMap<Integer, Integer> late = new TreeMap<Integer, Integer>(p.rejoinChecks);
				p.rejoinChecks.clear();
				for (final Map.Entry<Integer, Integer> e : late.entrySet()) {
					onChecksum(p, new LanMessages.Checksum(e.getKey(), e.getValue()));
				}
			}
			return;
		case LanProtocol.CHECKSUM:
			onChecksum(p, (LanMessages.Checksum) m);
			return;
		case LanProtocol.RESYNC:
			if (((LanMessages.Resync) m).kind == LanProtocol.RESYNC_ACK) {
				p.awaitAck = false;
			}
			return;
		case LanProtocol.STATE: {
			final int reported = ((LanMessages.AppState) m).state;
			if (reported == LanProtocol.SIM_PAUSED) {
				this.simPausedBy.add(p.id);
			}
			else if (reported == LanProtocol.SIM_RESUMED) {
				this.simPausedBy.remove(p.id);
			}
			else {
				p.appState = reported;
			}
			return;
		}
		case LanProtocol.SLOT_REQUEST:
		case LanProtocol.READY:
		case LanProtocol.HOST_SLOT:
		case LanProtocol.HOST_SETUP:
		case LanProtocol.HOST_OPTIONS:
		case LanProtocol.START_REQUEST:
		case LanProtocol.COUNTDOWN_CANCEL:
			// lobby traffic that was in flight when the game started
			return;
		case LanProtocol.VOTE:
			onVote(p, ((LanMessages.Vote) m).vote);
			return;
		default:
			c.close("unexpected message " + m.type());
		}
	}

	private void onCommand(final Participant p, final LanConnection c, final LanMessages.Cmd cmd)
			throws LanProtocolException {
		// A referee has no seat: its commands go under a number above the seats, and the validator takes only those
		// a referee may give (the pause).
		final int slot = this.setup == null ? LanProtocol.NO_SLOT : this.setup.commandSlot(p.id);
		if (p.state == LanProtocol.PRESENCE_LEFT || slot == LanProtocol.NO_SLOT
				|| (p.role == LanProtocol.ROLE_PLAYER && p.slot != slot)
				|| (p.role == LanProtocol.ROLE_REFEREE && this.config.commandValidator == null)) {
			throw new LanProtocolException("commands only from active players");
		}
		if (cmd.seq <= p.lastSeq) {
			return;
		}
		if (cmd.seq != p.lastSeq + 1) {
			throw new LanProtocolException("command sequence gap");
		}
		if (this.config.commandValidator != null && !this.config.commandValidator.accept(slot, cmd.bytes)) {
			if (++p.strikes >= 3) {
				throw new LanProtocolException("repeated invalid commands");
			}
			p.lastSeq = cmd.seq;
			return;
		}
		if (p.queuedCommands >= 1024) {
			throw new LanProtocolException("command flood");
		}
		p.lastSeq = cmd.seq;
		p.queuedCommands++;
		this.incoming.add(new CmdEntry(slot, cmd.seq, cmd.bytes));
	}

	private void setPresence(final Participant p, final int newState) {
		if (p.state == newState) {
			return;
		}
		p.state = newState;
		if (newState == LanProtocol.PRESENCE_LEFT || newState == LanProtocol.PRESENCE_ABSENT) {
			this.simPausedBy.remove(p.id);
			this.continueVotes.remove(p.id);
		}
		if (p.absentTimer != null) {
			p.absentTimer.cancel();
			p.absentTimer = null;
		}
		if (this.state == LanProtocol.STATE_IN_GAME || this.state == LanProtocol.STATE_LOADING) {
			this.pendingPresence.add(new PresenceEntry(p.id, newState));
		}
	}

	private void scheduleTurn() {
		final long delay = Math.max(0, this.nextTurnDue - LanLoop.nowMs());
		this.turnTimer = this.loop.schedule(delay, new Runnable() {
			@Override
			public void run() {
				turnTick();
			}
		});
	}

	private void turnTick() {
		this.turnTimer = null;
		if (this.state != LanProtocol.STATE_IN_GAME) {
			return;
		}
		final long now = LanLoop.nowMs();
		if (canRelease(this.nextTurn)) {
			emitTurn();
			if (this.lagActive) {
				this.lagActive = false;
				this.continueVotes.clear();
				broadcast(new LanMessages.LagEnd());
			}
			this.stalledSince = 0;
			// A game that stands still needs turns only for the commands that end the stop.
			this.nextTurnDue += this.simPausedBy.isEmpty() ? this.config.turnIntervalMs
					: Math.max(this.config.turnIntervalMs, PAUSED_TURN_MS);
			if (now - this.nextTurnDue > 2L * this.config.turnIntervalMs) {
				this.nextTurnDue = now + this.config.turnIntervalMs;
			}
		}
		else {
			if (this.stalledSince == 0) {
				this.stalledSince = now;
			}
			if (now - this.stalledSince >= this.loop.timings().lagAfterMs && now - this.lastLagBroadcast >= 1000) {
				this.lastLagBroadcast = now;
				this.lagActive = true;
				broadcast(new LanMessages.Lag(lagEntries(now)));
			}
			this.nextTurnDue = now + Math.min(this.config.turnIntervalMs, 20);
		}
		if (this.state == LanProtocol.STATE_IN_GAME) {
			scheduleTurn();
		}
	}

	private boolean canRelease(final int n) {
		for (final Participant p : this.participants.values()) {
			if (p.role == LanProtocol.ROLE_PLAYER && p.state == LanProtocol.PRESENCE_PLAYING
					&& p.ackTurn < n - this.setup.syncLimit) {
				return false;
			}
		}
		return true;
	}

	private List<LagEntry> lagEntries(final long now) {
		final List<LagEntry> list = new ArrayList<LagEntry>();
		final int waited = (int) Math.min(Integer.MAX_VALUE, now - this.stalledSince);
		for (final Participant p : this.participants.values()) {
			if (isLaggard(p)) {
				final int reason = p.conn == null ? LanProtocol.LAG_RECONNECTING
						: (p.appState == LanProtocol.APP_BACKGROUND ? LanProtocol.LAG_BACKGROUND
								: LanProtocol.LAG_LAGGING);
				list.add(new LagEntry(p.id, reason, waited));
			}
		}
		return list;
	}

	private boolean isLaggard(final Participant p) {
		return p.role == LanProtocol.ROLE_PLAYER && p.state == LanProtocol.PRESENCE_PLAYING
				&& p.ackTurn < this.nextTurn - this.setup.syncLimit;
	}

	private void onVote(final Participant p, final int vote) {
		if (this.state != LanProtocol.STATE_IN_GAME || p.role != LanProtocol.ROLE_PLAYER
				|| p.state != LanProtocol.PRESENCE_PLAYING) {
			return;
		}
		if (vote == LanProtocol.VOTE_CONTINUE_WITHOUT) {
			if (!this.lagActive || isLaggard(p)
					|| LanLoop.nowMs() - this.stalledSince < this.loop.timings().continueAfterMs) {
				return;
			}
			this.continueVotes.add(p.id);
			if (p.id == this.ownerId || continueMajority()) {
				continueWithoutLaggards();
			}
		}
		else if (vote == LanProtocol.VOTE_CLAIM_VICTORY) {
			claimVictory(p);
		}
	}

	/** The host decides alone; the others by more than half of the players who are not waited for. */
	private boolean continueMajority() {
		int voters = 0;
		int votes = 0;
		for (final Participant q : this.participants.values()) {
			if (q.role == LanProtocol.ROLE_PLAYER && q.state == LanProtocol.PRESENCE_PLAYING && q.conn != null
					&& !isLaggard(q)) {
				voters++;
				if (this.continueVotes.contains(q.id)) {
					votes++;
				}
			}
		}
		return votes * 2 > voters;
	}

	/**
	 * The opponents of the claimant are all gone (absent, or waited for longer than the claim time): they leave the
	 * game, and the map's rules give the claimant the win. A computer opponent plays on, so nothing is claimed then.
	 */
	private void claimVictory(final Participant claimant) {
		if (claimant.slot == LanProtocol.NO_SLOT || claimant.slot >= this.slots.size()) {
			return;
		}
		final int team = this.slots.get(claimant.slot).team;
		for (final SlotInfo s : this.slots) {
			if (s.kind == LanProtocol.SLOT_COMPUTER && s.team != team) {
				return;
			}
		}
		final List<Participant> opponents = new ArrayList<Participant>();
		for (final Participant q : this.participants.values()) {
			if (q != claimant && q.role == LanProtocol.ROLE_PLAYER && q.slot != LanProtocol.NO_SLOT
					&& q.slot < this.slots.size() && q.state != LanProtocol.PRESENCE_LEFT
					&& this.slots.get(q.slot).team != team) {
				opponents.add(q);
			}
		}
		if (opponents.isEmpty()) {
			return;
		}
		final long waited = this.lagActive ? LanLoop.nowMs() - this.stalledSince : 0;
		for (final Participant q : opponents) {
			final boolean gone = q.state == LanProtocol.PRESENCE_ABSENT || (this.lagActive && isLaggard(q)
					&& waited >= this.loop.timings().claimAfterMs);
			if (!gone) {
				return;
			}
		}
		for (final Participant q : opponents) {
			setPresence(q, LanProtocol.PRESENCE_LEFT);
		}
	}

	private void continueWithoutLaggards() {
		for (final Participant p : new ArrayList<Participant>(this.participants.values())) {
			if (isLaggard(p)) {
				setPresence(p, LanProtocol.PRESENCE_ABSENT);
			}
		}
	}

	private void emitTurn() {
		final List<CmdEntry> cmds = new ArrayList<CmdEntry>();
		final List<CmdEntry> leftover = new ArrayList<CmdEntry>();
		final Map<Integer, int[]> usage = new HashMap<Integer, int[]>();
		int estimated = 32 + this.pendingPresence.size() * 2;
		final Set<Integer> blocked = new HashSet<Integer>();
		while (!this.incoming.isEmpty()) {
			final CmdEntry e = this.incoming.poll();
			if (blocked.contains(e.slot)) {
				leftover.add(e);
				continue;
			}
			int[] u = usage.get(e.slot);
			if (u == null) {
				u = new int[2];
				usage.put(e.slot, u);
			}
			final int size = 7 + e.bytes.length;
			if (u[0] >= LanProtocol.MAX_COMMANDS_PER_TURN
					|| u[1] + e.bytes.length > LanProtocol.MAX_COMMAND_BYTES_PER_TURN
					|| estimated + size > LanProtocol.MAX_TURN_BODY) {
				blocked.add(e.slot);
				leftover.add(e);
				continue;
			}
			u[0]++;
			u[1] += e.bytes.length;
			estimated += size;
			cmds.add(e);
		}
		this.incoming.addAll(leftover);
		for (final CmdEntry e : cmds) {
			final Participant p = participantInSlot(e.slot);
			if (p != null && p.queuedCommands > 0) {
				p.queuedCommands--;
			}
		}
		final TurnData t = new TurnData(this.nextTurn, this.simPausedBy.isEmpty() ? this.setup.turnSteps : 0, cmds,
				new ArrayList<PresenceEntry>(this.pendingPresence));
		this.pendingPresence.clear();
		try {
			this.archive.append(t);
		}
		catch (final LanProtocolException e) {
			LanLog.warn("cannot archive turn", e);
			endGame();
			return;
		}
		this.nextTurn++;
		broadcastTurn(new LanMessages.Turn(t));
		checkEnd();
	}

	private Participant participantInSlot(final int slot) {
		if (slot >= LanProtocol.WATCHER_SLOT_BASE) {
			final LanData.ParticipantInfo who = this.setup == null ? null : this.setup.commandSender(slot);
			return who == null ? null : this.participants.get(who.id);
		}
		for (final Participant p : this.participants.values()) {
			if (p.slot == slot) {
				return p;
			}
		}
		return null;
	}

	private void checkEnd() {
		for (final Participant p : this.participants.values()) {
			if (p.state == LanProtocol.PRESENCE_PLAYING || p.state == LanProtocol.PRESENCE_REJOINING
					|| p.state == LanProtocol.PRESENCE_ABSENT) {
				return;
			}
		}
		endGame();
	}

	private void endGame() {
		if (this.turnTimer != null) {
			this.turnTimer.cancel();
			this.turnTimer = null;
		}
		broadcast(new LanMessages.GameEnd(0));
		setState(LanProtocol.STATE_ENDED);
	}

	/** A rejoining player's sums are checked against what the players reported when the step was played. */
	private void verifyRejoining(final Participant p, final LanMessages.Checksum cs) {
		final Integer known = this.checksumLog.get(cs.tick);
		if (known == null) {
			if (p.rejoinChecks.size() < 4096) {
				p.rejoinChecks.put(cs.tick, cs.crc);
			}
			return;
		}
		if (known.intValue() != cs.crc) {
			LanLog.warn("rejoining participant " + p.id + " differs at step " + cs.tick);
			if (p.recovering) {
				// The same game from its first turn gave a different state again: no second try will do better.
				giveUp(p, cs.tick);
				return;
			}
			final List<ChecksumGroup> groups = new ArrayList<ChecksumGroup>();
			groups.add(new ChecksumGroup(known, new int[] { this.ownerId }));
			groups.add(new ChecksumGroup(cs.crc, new int[] { p.id }));
			if (p.conn != null) {
				p.conn.send(new LanMessages.Desync(cs.tick, groups));
			}
		}
	}

	private void awaitAck(final Participant p) {
		p.awaitAck = true;
		p.awaitAckSince = LanLoop.nowMs();
	}

	private void onChecksum(final Participant p, final LanMessages.Checksum cs) {
		if (p.awaitAck) {
			// A participant that never answers a resync (an older build, a lost message) must not be ignored for good.
			if (LanLoop.nowMs() - p.awaitAckSince < RESYNC_ACK_MS) {
				return;
			}
			p.awaitAck = false;
		}
		if (p.state == LanProtocol.PRESENCE_REJOINING) {
			verifyRejoining(p, cs);
			return;
		}
		if (this.reportedDesync.contains(cs.tick)) {
			// The step the participant was out of step at, again, in the game it played anew.
			final Integer agreed = this.checksumLog.get(cs.tick);
			if (p.resyncs > 0 && p.lastResyncTick == cs.tick && agreed != null && agreed.intValue() != cs.crc) {
				giveUp(p, cs.tick);
			}
			return;
		}
		Map<Integer, Integer> byParticipant = this.checksums.get(cs.tick);
		if (byParticipant == null) {
			byParticipant = new TreeMap<Integer, Integer>();
			this.checksums.put(cs.tick, byParticipant);
		}
		byParticipant.put(p.id, cs.crc);
		int expected = 0;
		for (final Participant q : this.participants.values()) {
			if (q.state == LanProtocol.PRESENCE_PLAYING) {
				expected++;
			}
		}
		if (byParticipant.size() < expected) {
			trimChecksums();
			return;
		}
		final Map<Integer, List<Integer>> groups = new TreeMap<Integer, List<Integer>>();
		for (final Map.Entry<Integer, Integer> e : byParticipant.entrySet()) {
			List<Integer> g = groups.get(e.getValue());
			if (g == null) {
				g = new ArrayList<Integer>();
				groups.put(e.getValue(), g);
			}
			g.add(e.getKey());
		}
		this.checksums.remove(cs.tick);
		final int agreed = groups.size() > 1 ? majority(groups) : groups.keySet().iterator().next();
		// A rejoining player's run is held against what most of the players had, not against who reported first.
		if (this.checksumLog.size() < MAX_CHECKSUM_LOG || this.checksumLog.containsKey(cs.tick)) {
			this.checksumLog.put(cs.tick, agreed);
		}
		for (final Participant q : this.participants.values()) {
			final Integer waiting = q.rejoinChecks.remove(cs.tick);
			if (waiting != null && waiting.intValue() != agreed && q.conn != null) {
				verifyRejoining(q, new LanMessages.Checksum(cs.tick, waiting));
			}
		}
		if (groups.size() > 1) {
			this.reportedDesync.add(cs.tick);
			final List<ChecksumGroup> list = new ArrayList<ChecksumGroup>();
			for (final Map.Entry<Integer, List<Integer>> e : groups.entrySet()) {
				final int[] ids = new int[e.getValue().size()];
				for (int i = 0; i < ids.length; i++) {
					ids[i] = e.getValue().get(i);
				}
				list.add(new ChecksumGroup(e.getKey(), ids));
			}
			LanLog.warn("the players differ at step " + cs.tick + ": " + groups);
			broadcast(new LanMessages.Desync(cs.tick, list));
			if (this.listener != null) {
				this.listener.onDesync(cs.tick, list);
			}
			for (final Map.Entry<Integer, List<Integer>> e : groups.entrySet()) {
				if (e.getKey().intValue() == agreed) {
					continue;
				}
				for (final int id : e.getValue()) {
					final Participant q = this.participants.get(id);
					if (q != null) {
						resync(q, cs.tick);
					}
				}
			}
		}
		trimChecksums();
	}

	/**
	 * The sum most of the players have; players count, watchers only follow. A tie goes to the group of the host (the
	 * game's authority), else to the one with the lowest participant.
	 */
	private int majority(final Map<Integer, List<Integer>> groups) {
		int best = 0;
		int bestWeight = -1;
		boolean bestHasOwner = false;
		int bestLowest = Integer.MAX_VALUE;
		for (final Map.Entry<Integer, List<Integer>> e : groups.entrySet()) {
			int weight = 0;
			int lowest = Integer.MAX_VALUE;
			boolean hasOwner = false;
			for (final int id : e.getValue()) {
				final Participant q = this.participants.get(id);
				if (q != null && q.role == LanProtocol.ROLE_PLAYER) {
					weight++;
				}
				hasOwner |= id == this.ownerId;
				lowest = Math.min(lowest, id);
			}
			final boolean better = weight > bestWeight
					|| (weight == bestWeight && ((hasOwner && !bestHasOwner)
							|| (hasOwner == bestHasOwner && lowest < bestLowest)));
			if (better) {
				best = e.getKey();
				bestWeight = weight;
				bestHasOwner = hasOwner;
				bestLowest = lowest;
			}
		}
		return best;
	}

	/**
	 * The participant's game is not the majority's: it drops its game and plays the archive from the first turn, while
	 * the others go on. The same difference at the same step again, or too many in all, drops the participant.
	 */
	private void resync(final Participant p, final int tick) {
		if (p.conn == null || p.state != LanProtocol.PRESENCE_PLAYING) {
			return;
		}
		if (p.resyncs >= MAX_RESYNCS || (p.lastResyncTick >= 0 && tick <= p.lastResyncTick)) {
			giveUp(p, tick);
			return;
		}
		p.resyncs++;
		p.lastResyncTick = tick;
		p.recovering = true;
		LanLog.warn("participant " + p.id + " is out of step at " + tick + ": playing the game again");
		startOver(p, LanProtocol.RESYNC_DESYNC, tick);
		if (this.listener != null) {
			this.listener.onResync(p.id, LanProtocol.RESYNC_DESYNC, tick);
		}
	}

	/** Tells the participant to drop its game and sends it every archived turn, as for a player who comes back. */
	private void startOver(final Participant p, final int kind, final int tick) {
		p.conn.send(new LanMessages.Resync(kind, tick));
		awaitAck(p);
		for (final Map<Integer, Integer> sums : this.checksums.values()) {
			sums.remove(p.id);
		}
		setPresence(p, LanProtocol.PRESENCE_REJOINING);
		p.rejoinStarted = true;
		p.rejoinChecks.clear();
		p.holdsThrough = -1;
		p.ackTurn = -1;
		stopStream(p);
		sendCatchUp(p, 0);
	}

	private void giveUp(final Participant p, final int tick) {
		LanLog.warn("participant " + p.id + " cannot stay in sync (step " + tick + ")");
		p.recovering = false;
		awaitAck(p);
		setPresence(p, LanProtocol.PRESENCE_ABSENT);
		stopStream(p);
		for (final Map<Integer, Integer> sums : this.checksums.values()) {
			sums.remove(p.id);
		}
		if (p.conn != null) {
			p.conn.send(new LanMessages.Resync(LanProtocol.RESYNC_GIVE_UP, tick));
			p.conn.closeAfterFlush("cannot stay in sync");
		}
		if (this.listener != null) {
			this.listener.onResync(p.id, LanProtocol.RESYNC_GIVE_UP, tick);
		}
	}

	private void trimChecksums() {
		while (this.checksums.size() > 100) {
			this.checksums.remove(this.checksums.keySet().iterator().next());
		}
		while (this.reportedDesync.size() > 100) {
			this.reportedDesync.pollFirst();
		}
	}

	// ----------------------------------------------------------- announcement

	private void updateAnnouncement() {
		if (this.discovery == null) {
			return;
		}
		final GameInfo g = new GameInfo();
		g.gameId = this.gameId;
		g.engineVersion = this.config.engineVersion;
		g.buildCommit = this.config.buildCommit;
		final Participant owner = this.participants.get(this.ownerId);
		g.hostName = owner == null ? "" : owner.name;
		g.portrait = (owner == null) || (owner.portrait == null) ? "" : owner.portrait;
		g.gameName = this.config.options.gameName;
		g.mapName = this.config.map.name;
		g.mapTitle = this.config.map.title;
		g.mapSize = this.config.map.size;
		g.mapWidth = this.config.map.playableWidth;
		g.mapHeight = this.config.map.playableHeight;
		g.mapSha256 = this.config.map.sha256;
		g.mapCrc32 = this.config.map.crc32;
		g.observers = this.config.options.observers;
		g.lockTeams = this.config.options.lockTeams;
		g.teamsTogether = this.config.options.teamsTogether;
		g.fullSharedControl = this.config.options.fullSharedControl;
		g.randomRaces = this.config.options.randomRaces;
		int humans = 0;
		int open = 0;
		for (final SlotInfo s : this.slots) {
			if (s.kind == LanProtocol.SLOT_HUMAN || s.kind == LanProtocol.SLOT_COMPUTER) {
				humans++;
			}
			if (s.kind != LanProtocol.SLOT_CLOSED) {
				open++;
			}
		}
		g.players = humans;
		g.maxPlayers = open;
		g.state = this.state;
		g.hostId = this.ownerId;
		g.tcpPort = this.server == null ? 0 : this.server.port();
		g.speed = this.config.options.speed;
		this.discovery.announce(g);
	}

	// ----------------------------------------------------------------- closing

	private void closeOnLoop() {
		if (this.turnTimer != null) {
			this.turnTimer.cancel();
		}
		if (this.countdownTimer != null) {
			this.countdownTimer.cancel();
		}
		if (this.loadTimer != null) {
			this.loadTimer.cancel();
		}
		if (this.migrationTimer != null) {
			this.migrationTimer.cancel();
		}
		for (final Participant p : this.participants.values()) {
			if (p.absentTimer != null) {
				p.absentTimer.cancel();
			}
			stopStream(p);
		}
		if (this.discovery != null) {
			this.discovery.stopAnnouncing();
			this.discovery = null;
		}
		if (this.server != null) {
			this.server.close();
			this.server = null;
		}
		this.maps.cancelAll();
		this.reportedDesync.clear();
		this.checksums.clear();
		for (final LanConnection c : new ArrayList<LanConnection>(this.connections)) {
			c.close("host closed");
		}
		if (this.archive != null) {
			this.archive.close();
		}
	}

}
