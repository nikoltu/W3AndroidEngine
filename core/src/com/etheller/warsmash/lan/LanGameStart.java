package com.etheller.warsmash.lan;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.IntConsumer;

import com.etheller.warsmash.BuildInfo;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.w3x.War3Map;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.GameReplays;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.ai.AIDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.CBasePlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfig;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRace;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRaceManager;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

import net.warsmash.lan.LanGameSetup;
import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanData.MapInfo;
import net.warsmash.lan.protocol.LanData.SlotInfo;
import net.warsmash.lan.protocol.LanProtocol;

/**
 * The LAN branch of a game start: what the host's START message says about the game (map, seed, slots), put in the
 * form the single player start code takes, and the game's side of the loading handshake (LOADED, then GO).
 * Everything here runs on the game thread, except {@link #onGo()}, which the client's event executor calls.
 */
public final class LanGameStart {
	private final LanClient client;
	private final LanGameSetup setup;
	private final String mapFile;
	private final int localSlot;
	private final boolean referee;
	/** The player seat nobody plays that a watcher's view and a referee's pause stand on, or -1. */
	private int watchSlot = -1;
	private volatile boolean go;
	private final java.util.concurrent.ConcurrentHashMap<Integer, Integer> loadPercents = new java.util.concurrent.ConcurrentHashMap<>();
	private final boolean rejoin;
	/** Why this game starts over, a {@code RESYNC_} kind, or -1. */
	private final int resyncKind;
	private final int resyncTick;
	private Runnable restarter;
	private boolean loadedSent;
	private int reportedPercent = -5;
	/** When the last progress went to the host; a long step still repeats its percent so the host sees it alive. */
	private long reportedNanos = System.nanoTime();
	private static final long PROGRESS_REPEAT_NANOS = 10_000_000_000L;
	private LanCommandSender sender;
	private LockstepTurnManager turns;
	private LanPlay play;
	private IntConsumer tickListener;
	private Runnable pausedListener;

	/**
	 * @param mapFile where this machine has the map the setup names
	 */
	public LanGameStart(final LanClient client, final LanGameSetup setup, final String mapFile) {
		this.client = client;
		this.setup = setup;
		this.mapFile = mapFile;
		this.resyncKind = client.resyncKind();
		this.resyncTick = client.resyncTick();
		// A game started over is a rejoin whether or not the host's answer has come yet.
		this.rejoin = client.isRunningJoin() || this.resyncKind == LanProtocol.RESYNC_DESYNC
				|| this.resyncKind == LanProtocol.RESYNC_TURNS;
		final LanData.ParticipantInfo me = setup.participant(client.participantId());
		this.localSlot = (me == null) || (me.role != LanProtocol.ROLE_PLAYER) || (me.slot == LanProtocol.NO_SLOT) ? -1
				: me.slot;
		this.referee = me != null && me.role == LanProtocol.ROLE_REFEREE;
	}

	public LanGameSetup setup() {
		return this.setup;
	}

	public String mapFile() {
		return this.mapFile;
	}

	/** The player slot this machine's player has, or -1 for a watcher. */
	public int localSlot() {
		return this.localSlot;
	}

	/** The seat a watcher watches from; set when the game starts. Every machine finds the same one. */
	public void setWatchSlot(final int watchSlot) {
		this.watchSlot = watchSlot;
	}

	public int watchSlot() {
		return this.watchSlot;
	}

	/** The player of the simulation this machine is, or the seat it watches from; -1 before a watcher's game starts. */
	public int viewSlot() {
		return this.localSlot >= 0 ? this.localSlot : this.watchSlot;
	}

	/** The time the game started, which every player's replay of it records. */
	public long recordedAt() {
		return this.setup.header.length >= 8 ? ByteBuffer.wrap(this.setup.header).getLong()
				: System.currentTimeMillis();
	}

	/** The setup header a host sends: what every player's replay records the same. */
	public static byte[] header(final long recordedAt) {
		return ByteBuffer.allocate(8).putLong(recordedAt).array();
	}

	public int checksumInterval() {
		return GameReplays.CHECKSUM_INTERVAL;
	}

	/**
	 * What starts this game again when the host says it is not the others' game: it ends the running game without
	 * leaving the LAN game and starts a new one from the session's turns. Called on the game thread.
	 */
	public void setRestarter(final Runnable restarter) {
		this.restarter = restarter;
	}

	/** The host released the game; it starts as soon as this machine has loaded it. */
	public void onGo() {
		this.go = true;
	}

	public boolean isGo() {
		return this.go || this.rejoin;
	}

	/** A player reported how far it has loaded; for the loading screen's list. */
	public void onLoadProgress(final int participant, final int percent) {
		this.loadPercents.put(participant, percent);
		final LanPlay current;
		synchronized (this) {
			current = this.play;
		}
		if (current != null) {
			current.onLoadProgress(participant, percent);
		}
	}

	/**
	 * How far the player of a slot has loaded, 0 to 100; -1 for a slot no one loads. A computer loads where the host
	 * does.
	 */
	public int loadPercentOfSlot(final int slot) {
		for (final SlotInfo s : this.setup.slots) {
			if (s.index != slot) {
				continue;
			}
			final int participant = s.kind == LanProtocol.SLOT_HUMAN ? s.participantId
					: s.kind == LanProtocol.SLOT_COMPUTER && this.setup.hostOrder.length > 0
							? this.setup.hostOrder[0] : -1;
			if (participant < 0) {
				return -1;
			}
			if (participant == this.client.participantId() && this.loadedSent) {
				return 100;
			}
			// Everyone else is in the game already when this machine comes back to it.
			if (this.rejoin && participant != this.client.participantId()) {
				return 100;
			}
			final Integer percent = this.loadPercents.get(participant);
			return percent == null ? 0 : percent;
		}
		return -1;
	}

	/** Tells the host this machine has the game loaded; once. */
	public void loaded() {
		if (this.rejoin) {
			this.loadedSent = true;
			return;
		}
		if (!this.loadedSent) {
			this.loadedSent = true;
			this.client.loadProgress(100);
			this.client.loaded();
		}
	}

	public void loadProgress(final float ratio) {
		final int percent = (int) (ratio * 100);
		if (this.rejoin) {
			// Nobody waits for a rejoining player to load: its own state is for its own loading screen only.
			this.loadPercents.put(this.client.participantId(), percent);
			return;
		}
		final long now = System.nanoTime();
		if ((percent >= (this.reportedPercent + 5)) || ((now - this.reportedNanos) >= PROGRESS_REPEAT_NANOS)) {
			this.reportedPercent = Math.max(this.reportedPercent, percent);
			this.reportedNanos = now;
			this.client.loadProgress(this.reportedPercent);
		}
	}

	/**
	 * Puts the lobby's slots over the setup the map's script made: who plays each player, their race, team, color and
	 * handicap. A slot is the player of the same number. Names come from the lobby, so every machine has the same.
	 */
	public void applyTo(final War3MapConfig config, final CRaceManager raceManager) {
		for (final SlotInfo slot : this.setup.slots) {
			if (slot.index >= config.getMaxPlayers()) {
				continue;
			}
			final CBasePlayer player = config.getPlayer(slot.index);
			if ((slot.kind == LanProtocol.SLOT_HUMAN) || (slot.kind == LanProtocol.SLOT_COMPUTER)) {
				final boolean human = slot.kind == LanProtocol.SLOT_HUMAN;
				player.setController(human ? CMapControl.USER : CMapControl.COMPUTER);
				player.setSlotState(CPlayerSlotState.PLAYING);
				player.setAIDifficulty(human ? null : AIDifficulty.VALUES[Math.min(slot.computerLevel, 2)]);
				player.setName(slot.name);
				final CRace race = slot.race == 0 ? null : raceManager.getRace(slot.race);
				player.setRacePref(race == null ? raceManager.getRandomRacePreference()
						: raceManager.getRacePreferenceForRace(race));
				player.setTeam(slot.team);
				player.setColor(slot.color);
				player.setHandicap(slot.handicap / 100f);
			}
			else {
				player.setController(CMapControl.NONE);
				player.setSlotState(CPlayerSlotState.EMPTY);
				player.setAIDifficulty(null);
			}
		}
	}

	/** What the game knows of the other players: who is waited for, absent or gone. */
	public synchronized LanPlay play() {
		if (this.play == null) {
			final LanSession session = LanSessions.current();
			this.play = new LanPlay(this.setup, this.client.participantId(),
					session == null ? new net.warsmash.lan.protocol.LanTimings() : session.timings(), this.client::vote,
					System::currentTimeMillis);
			// What the others reported while the game loaded, so that a player still loading is known from the start.
			for (final java.util.Map.Entry<Integer, Integer> e : this.loadPercents.entrySet()) {
				this.play.onLoadProgress(e.getKey(), e.getValue());
			}
			if (this.resyncKind == LanProtocol.RESYNC_DESYNC) {
				this.play.notice(com.etheller.warsmash.launcher.lan.LanText.outOfSync(this.resyncTick));
			}
			else if (this.resyncKind == LanProtocol.RESYNC_TURNS) {
				this.play.notice(com.etheller.warsmash.launcher.lan.LanText.missedTurns());
			}
		}
		return this.play;
	}

	/** The game's turn manager; made once the map is loaded and the host has said go. */
	public LockstepTurnManager createTurnManager(final War3MapViewer viewer) {
		final LanPlay play = play();
		this.turns = new LockstepTurnManager(viewer, this.client, this);
		this.turns.setRestarter(this.restarter);
		this.turns.setTickListener(this.tickListener);
		this.turns.setPausedListener(this.pausedListener);
		final LanSession session = LanSessions.current();
		if (session != null) {
			session.setCatchUp(this.turns::catchUp);
			session.addListener(new LanClient.Listener() {
				@Override
				public void onLag(final java.util.List<LanData.LagEntry> entries) {
					play.onLag(entries);
				}

				@Override
				public void onLagEnd() {
					play.onLagEnd();
				}

				@Override
				public void onDisconnected(final String reason) {
					play.onDisconnected(reason);
				}

				@Override
				public void onPlayerState(final int participant, final int controlScheme) {
					play.onControlScheme(participant, controlScheme);
				}

				@Override
				public void onReconnecting() {
					play.onReconnecting();
				}

				@Override
				public void onResync(final int kind, final int tick) {
					play.onResync(kind, tick);
				}

				@Override
				public void onMigrating() {
					play.onMigrating();
				}

				@Override
				public void onHostChanged(final int newHost, final int firstTurn) {
					play.onHostChanged(newHost);
				}

				@Override
				public void onReconnected() {
					play.onReconnected();
				}

				@Override
				public void onChat(final net.warsmash.lan.protocol.LanMessages.Chat chat) {
					play.onWatcherChat(chat.from, chat.channel, chat.text);
				}
			});
		}
		return this.turns;
	}

	/** Told on each frame the game stands still, once it has started. */
	public void setPausedListener(final Runnable pausedListener) {
		this.pausedListener = pausedListener;
	}

	/** Told the step count after each step of the game, once it has started. */
	public void setTickListener(final IntConsumer tickListener) {
		this.tickListener = tickListener;
	}

	public LockstepTurnManager turnManager() {
		return this.turns;
	}

	/** What the local player's interface sends commands to; a watcher's sends none. */
	public CPlayerUnitOrderListener commandSender() {
		if (this.sender == null) {
			this.sender = new LanCommandSender(this.client, this.localSlot == -1, this.referee);
		}
		return this.sender;
	}

	public LanClient client() {
		return this.client;
	}

	// ------------------------------------------------------------ this machine

	/** The engine version players compare before they play together. */
	public static String localVersion() {
		return GameReplays.engineVersion();
	}

	public static String localCommit() {
		return BuildInfo.current().getBuildId();
	}

	public static boolean localOfficial() {
		return BuildInfo.current().isOfficial();
	}

	/** The checksum of the game data the rules come from, as an unsigned number. */
	public static long dataChecksum(final DataSource dataSource) throws IOException {
		return GameReplays.dataChecksum(dataSource) & 0xFFFFFFFFL;
	}

	/** What a host tells the others about the map, and how many players it has. */
	public static final class MapDetails {
		public final MapInfo info = new MapInfo();
		public int playerSlots;
	}

	public static MapDetails mapDetails(final DataSource dataSource, final String mapFile) throws IOException {
		final MapDetails details = new MapDetails();
		final long[] checksum = GameReplays.mapChecksum(dataSource, mapFile);
		final String name = mapFile.replace('\\', '/');
		details.info.name = name.substring(name.lastIndexOf('/') + 1);
		details.info.size = (int) checksum[1];
		details.info.crc32 = (int) checksum[0];
		final War3Map map = War3MapViewer.beginLoadingMap(dataSource, mapFile);
		try {
			details.playerSlots = map.readMapInformation().getPlayers().size();
		}
		finally {
			map.close();
		}
		return details;
	}

	/** Whether the map this machine has is the one the host names. */
	public static boolean sameMap(final DataSource dataSource, final String mapFile, final MapInfo expected)
			throws IOException {
		final long[] checksum = GameReplays.mapChecksum(dataSource, mapFile);
		return ((int) checksum[0] == expected.crc32) && (checksum[1] == expected.size);
	}
}
