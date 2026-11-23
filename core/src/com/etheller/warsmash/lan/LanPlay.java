package com.etheller.warsmash.lan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import net.warsmash.lan.LanGameSetup;
import net.warsmash.lan.protocol.LanData.LagEntry;
import net.warsmash.lan.protocol.LanData.ParticipantInfo;
import net.warsmash.lan.protocol.LanData.SlotInfo;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.protocol.LanTimings;

/**
 * What the players of a running LAN game know of each other: who is waited for and why, who is absent or gone, and
 * whether the host is lost. It holds the facts and the rules of the waiting window (when "Continue without them" and
 * "Claim victory" are open); the window itself is drawn by the game's interface, and the host decides every vote
 * again. The host's events and the game's calls come on different threads, so every method locks.
 */
public final class LanPlay {
	/** Each player may pause this many times in a game. */
	public static final int PAUSES_PER_PLAYER = 3;
	/** A loading player repeats its percentage every 10 seconds; one silent for this long is taken as gone. */
	static final long LOAD_REPORT_FRESH_MS = 30000;

	/** Sends the player's votes to the host. */
	public interface Actions {
		void vote(int vote);
	}

	public static final class Row {
		public final int participant;
		public final String name;
		/** The player's color index, which the window paints the name in. */
		public final int color;
		/** A {@code LanProtocol.LAG_*} reason. */
		public final int reason;
		public final long waitedMs;
		/** How far a player that is still loading the game has got, 0 to 99; -1 for a player that is waited for. */
		public final int loadPercent;

		Row(final int participant, final String name, final int color, final int reason, final long waitedMs) {
			this(participant, name, color, reason, waitedMs, -1);
		}

		Row(final int participant, final String name, final int color, final int reason, final long waitedMs,
				final int loadPercent) {
			this.participant = participant;
			this.name = name;
			this.color = color;
			this.reason = reason;
			this.waitedMs = waitedMs;
			this.loadPercent = loadPercent;
		}
	}

	/** What the waiting window shows at one moment. */
	public static final class Window {
		public final List<Row> rows;
		public final boolean hostLost;
		/** Opponents are all gone but nobody is waited for: only the claim is offered. */
		public final boolean absentOnly;
		/** The rows are players that are still loading the game: the game goes on without them, nothing is voted. */
		public boolean loading;
		/** Seconds until "Continue without them" opens, 0 when it is open; -1 when it is not offered. */
		public final int continueInSeconds;
		/** Seconds until "Claim victory" opens, 0 when it is open; -1 when it is not offered. */
		public final int claimInSeconds;
		/** The host is gone and the game is looking for a new one; the game goes on when it has found it. */
		public final boolean migrating;
		/** The game cannot be kept in step with the others any more; this player takes no further part. */
		public boolean cannotSync;
		/** How far this player's game has been played through to the present, 0 to 99; -1 when it is not. */
		public final int rejoinPercent;
		/** The game time played through and the time to reach, as "12:40 / 18:05". */
		public final String rejoinTime;

		Window(final List<Row> rows, final boolean hostLost, final boolean absentOnly, final int continueInSeconds,
				final int claimInSeconds) {
			this(rows, hostLost, absentOnly, continueInSeconds, claimInSeconds, -1, "", false);
		}

		Window(final List<Row> rows, final boolean hostLost, final boolean absentOnly, final int continueInSeconds,
				final int claimInSeconds, final int rejoinPercent, final String rejoinTime) {
			this(rows, hostLost, absentOnly, continueInSeconds, claimInSeconds, rejoinPercent, rejoinTime, false);
		}

		Window(final List<Row> rows, final boolean hostLost, final boolean absentOnly, final int continueInSeconds,
				final int claimInSeconds, final int rejoinPercent, final String rejoinTime, final boolean migrating) {
			this.migrating = migrating;
			this.rejoinPercent = rejoinPercent;
			this.rejoinTime = rejoinTime;
			this.rows = rows;
			this.hostLost = hostLost;
			this.absentOnly = absentOnly;
			this.continueInSeconds = continueInSeconds;
			this.claimInSeconds = claimInSeconds;
		}

		public boolean canContinue() {
			return this.continueInSeconds == 0;
		}

		public boolean canClaim() {
			return this.claimInSeconds == 0;
		}
	}

	/** A line a referee or an observer said, which travels outside the game's turns. */
	public static final class WatcherChat {
		public final String name;
		/** A {@code LanProtocol.CHAT_*} channel. */
		public final int channel;
		public final String text;

		WatcherChat(final String name, final int channel, final String text) {
			this.name = name;
			this.channel = channel;
			this.text = text;
		}
	}

	private final LanGameSetup setup;
	private final int me;
	private final LanTimings timings;
	private final Actions actions;
	private final LongSupplier clock;
	private final Map<Integer, Integer> presence = new HashMap<Integer, Integer>();
	private final Map<Integer, Integer> controlSchemes = new HashMap<Integer, Integer>();
	private final ArrayDeque<String> messages = new ArrayDeque<String>();
	private final ArrayDeque<WatcherChat> chats = new ArrayDeque<WatcherChat>();
	private List<LagEntry> lag = Collections.emptyList();
	private boolean lagging;
	private long lagReceivedAt;
	private long lastFedAt;
	private boolean starving;
	private volatile boolean hostLost;
	private volatile boolean migrating;
	private boolean hostLossHandled;
	/** The participant whose network thread makes the turns; the first of the start's order until a host change. */
	private int currentHost;
	private String hostLostReason = "";
	private boolean restart;
	private volatile boolean gaveUp;
	private boolean catchingUp;
	private int catchTick;
	private long catchSteps;
	private final Map<Integer, Integer> loadPercents = new HashMap<Integer, Integer>();
	private final Map<Integer, Long> loadReportedAt = new HashMap<Integer, Long>();

	/**
	 * @param me this machine's participant id
	 * @param clock milliseconds, from any starting point
	 */
	public LanPlay(final LanGameSetup setup, final int me, final LanTimings timings, final Actions actions,
			final LongSupplier clock) {
		this.setup = setup;
		this.me = me;
		this.timings = timings;
		this.actions = actions;
		this.clock = clock;
		this.lastFedAt = clock.getAsLong();
		this.currentHost = setup.hostOrder.length > 0 ? setup.hostOrder[0] : -1;
		for (final ParticipantInfo p : setup.participants) {
			this.presence.put(p.id, LanProtocol.PRESENCE_PLAYING);
			this.controlSchemes.put(p.id, p.controlScheme);
		}
	}

	/**
	 * A player said how far it has loaded the game. The host marks a player that did not load in time absent and goes
	 * on without it; such a player keeps reporting while it loads, which tells it from one that is gone.
	 */
	public synchronized void onLoadProgress(final int participant, final int percent) {
		if (participant == this.me) {
			return;
		}
		this.loadPercents.put(participant, percent);
		this.loadReportedAt.put(participant, this.clock.getAsLong());
	}

	/** Whether the player is absent because it is still loading the game: it reported a load below 100 lately. */
	private boolean isLoading(final int participant) {
		final Integer percent = this.loadPercents.get(participant);
		final Long at = this.loadReportedAt.get(participant);
		return presenceOf(participant) == LanProtocol.PRESENCE_ABSENT && percent != null && percent < 100 && at != null
				&& this.clock.getAsLong() - at <= LOAD_REPORT_FRESH_MS;
	}

	/** A participant changed how it plays (touch, or keyboard and mouse); only the score screen shows it. */
	public synchronized void onControlScheme(final int participant, final int controlScheme) {
		this.controlSchemes.put(participant, controlScheme);
	}

	/** Whether the human in this player slot plays with a keyboard and mouse now. */
	public synchronized boolean usesKeyboardAndMouse(final int slot) {
		for (final ParticipantInfo p : this.setup.participants) {
			if (p.slot == slot) {
				final Integer scheme = this.controlSchemes.get(p.id);
				return (scheme != null) && (scheme == LanProtocol.CONTROL_KBM);
			}
		}
		return false;
	}

	/** The player slots (bit i: slot i) whose humans play with a keyboard and mouse; a replay header keeps them. */
	public synchronized int keyboardAndMouseSlots() {
		int mask = 0;
		for (final ParticipantInfo p : this.setup.participants) {
			final Integer scheme = this.controlSchemes.get(p.id);
			if ((p.slot >= 0) && (p.slot < 32) && (scheme != null) && (scheme == LanProtocol.CONTROL_KBM)) {
				mask |= 1 << p.slot;
			}
		}
		return mask;
	}

	// ------------------------------------------------------------------ input

	/** The host says someone is waited for (again every second while it lasts). */
	public synchronized void onLag(final List<LagEntry> entries) {
		this.lag = new ArrayList<LagEntry>(entries);
		this.lagging = true;
		this.lagReceivedAt = this.clock.getAsLong();
	}

	public synchronized void onLagEnd() {
		this.lag = Collections.emptyList();
		this.lagging = false;
		this.starving = false;
		this.lastFedAt = this.clock.getAsLong();
	}

	/** The connection to the host is gone. */
	public synchronized void onDisconnected(final String reason) {
		if (net.warsmash.lan.client.LanClient.LEFT_REASON.equals(reason) || this.gaveUp) {
			return;
		}
		this.hostLost = true;
		this.hostLostReason = reason == null ? "" : reason;
	}

	/**
	 * The host says this game is not the game of the others: it is to be dropped and played again from the first turn
	 * (the engine does that, see {@link #takeRestart}), or, when it cannot be kept in step, given up.
	 */
	public synchronized void onResync(final int kind, final int tick) {
		if (kind == LanProtocol.RESYNC_GIVE_UP) {
			this.gaveUp = true;
			this.hostLost = false;
			this.migrating = false;
		}
		else {
			this.restart = true;
		}
	}

	/** Whether the game has to be played again from its start: true once per request. */
	public synchronized boolean takeRestart() {
		final boolean r = this.restart;
		this.restart = false;
		return r;
	}

	public boolean isGivenUp() {
		return this.gaveUp;
	}

	/** A line for the message area, from outside the events above. */
	public synchronized void notice(final String line) {
		this.messages.add(line);
	}

	/** The host cannot be reached any more and the game looks for the next one to take it over. */
	public synchronized void onMigrating() {
		this.migrating = true;
		this.starving = false;
		this.lastFedAt = this.clock.getAsLong();
	}

	/**
	 * Another participant makes the turns now. Nothing about the old host is decided here: its leaving, or its being
	 * gone, comes in the new host's first turn, the same for every player.
	 */
	public synchronized void onHostChanged(final int newHost) {
		final boolean changed = newHost != this.currentHost;
		this.migrating = false;
		this.hostLost = false;
		this.hostLossHandled = false;
		this.hostLostReason = "";
		this.starving = false;
		this.lastFedAt = this.clock.getAsLong();
		if (changed) {
			this.currentHost = newHost;
			if (!this.catchingUp && newHost != this.me) {
				this.messages.add(nameOf(newHost) + " is now the host.");
			}
		}
	}

	/** The link to the host broke in a running game; the client is trying to get back. */
	public synchronized void onReconnecting() {
		this.starving = true;
	}

	/**
	 * The host answered again. A loss that was only reported (the game has not acted on it) is taken back; once the
	 * game has made the host's player leave, only playing the game again from the start undoes that.
	 */
	public synchronized void onReconnected() {
		if (!this.hostLossHandled) {
			this.hostLost = false;
			this.hostLostReason = "";
		}
		this.lastFedAt = this.clock.getAsLong();
	}

	/**
	 * The game is being played through to the present: {@code steps} is the length of the part received so far. While
	 * it lasts the old events (absences, comings back) are not told as news.
	 */
	public synchronized void setCatchUp(final boolean catchingUp, final int tick, final long steps) {
		this.catchingUp = catchingUp;
		this.catchTick = tick;
		this.catchSteps = steps;
		if (catchingUp) {
			this.lastFedAt = this.clock.getAsLong();
			this.starving = false;
		}
	}

	public synchronized boolean isCatchingUp() {
		return this.catchingUp;
	}

	/** A turn brought a presence change. */
	public synchronized void onPresence(final int participant, final int state) {
		final Integer before = this.presence.put(participant, state);
		if (before != null && before == state) {
			return;
		}
		if (participant == this.me || this.catchingUp) {
			return;
		}
		final String name = nameOf(participant);
		switch (state) {
		case LanProtocol.PRESENCE_LEFT:
			this.messages.add(name + " has left the game.");
			break;
		case LanProtocol.PRESENCE_ABSENT:
			this.messages.add(name + (isLoading(participant) ? " is still loading the game." : " is absent."));
			break;
		case LanProtocol.PRESENCE_PLAYING:
			if (before != null && before != LanProtocol.PRESENCE_PLAYING) {
				this.messages.add(name + " is back in the game.");
			}
			break;
		default:
			break;
		}
	}

	/** A turn was applied. */
	public synchronized void fed() {
		this.lastFedAt = this.clock.getAsLong();
		this.starving = false;
	}

	/** The game wanted a turn and none was there. */
	public synchronized void starved() {
		this.starving = true;
	}

	/** The game stands still on purpose, so nothing is waited for. */
	public synchronized void idle() {
		this.lastFedAt = this.clock.getAsLong();
		this.starving = false;
	}

	// ------------------------------------------------------------------ facts

	public synchronized int presenceOf(final int participant) {
		final Integer state = this.presence.get(participant);
		return state == null ? LanProtocol.PRESENCE_LEFT : state;
	}

	public synchronized boolean isHostLost() {
		return this.hostLost;
	}

	public synchronized boolean isMigrating() {
		return this.migrating;
	}

	public synchronized int currentHost() {
		return this.currentHost;
	}

	public synchronized String hostLostReason() {
		return this.hostLostReason;
	}

	/** The participant that was the host, once, when its connection has just been lost; else -1. */
	public synchronized int takeHostLoss() {
		if (!this.hostLost || this.hostLossHandled) {
			return -1;
		}
		this.hostLossHandled = true;
		return this.currentHost;
	}

	/** Lines for the game's message area: players who left, are absent or came back. */
	public synchronized String pollMessage() {
		return this.messages.poll();
	}

	/**
	 * A line from a referee or an observer. A player's lines are commands in the turns; only watchers' arrive this
	 * way, and the sender's own echo is dropped (its interface showed the line when it was typed).
	 */
	public synchronized void onWatcherChat(final int from, final int channel, final String text) {
		final ParticipantInfo p = this.setup.participant(from);
		if (p == null || from == this.me || this.catchingUp
				|| (p.role != LanProtocol.ROLE_REFEREE && p.role != LanProtocol.ROLE_OBSERVER)) {
			return;
		}
		this.chats.add(new WatcherChat(p.name, channel, text));
	}

	public synchronized WatcherChat pollChat() {
		return this.chats.poll();
	}

	/** Whether this machine only watches: a referee or an observer. */
	public boolean isWatcher() {
		final ParticipantInfo p = this.setup.participant(this.me);
		return p != null && (p.role == LanProtocol.ROLE_REFEREE || p.role == LanProtocol.ROLE_OBSERVER);
	}

	/** Whether this machine watches without a say in the game (no pause); a referee may pause. */
	public boolean isObserver() {
		final ParticipantInfo p = this.setup.participant(this.me);
		return p != null && p.role == LanProtocol.ROLE_OBSERVER;
	}

	public String localName() {
		return nameOf(this.me);
	}

	public String nameOf(final int participant) {
		final ParticipantInfo p = this.setup.participant(participant);
		return p == null ? "Player" : p.name;
	}

	// ----------------------------------------------------------------- window

	/** The window to show now, or null when none is due. */
	public synchronized Window window() {
		final long now = this.clock.getAsLong();
		if (this.gaveUp) {
			final Window w = new Window(Collections.<Row>emptyList(), false, false, -1, -1);
			w.cannotSync = true;
			return w;
		}
		if (this.catchingUp) {
			return new Window(Collections.<Row>emptyList(), false, false, -1, -1,
					LanRejoin.percent(this.catchTick, this.catchSteps),
					LanRejoin.clock(this.catchTick) + " / " + LanRejoin.clock(this.catchSteps));
		}
		if (this.hostLost) {
			return new Window(Collections.<Row>emptyList(), true, false, -1, -1);
		}
		if (this.migrating) {
			return new Window(Collections.<Row>emptyList(), false, false, -1, -1, -1, "", true);
		}
		final List<Row> rows = new ArrayList<Row>();
		if (this.lagging) {
			final long since = now - this.lagReceivedAt;
			for (final LagEntry e : this.lag) {
				final ParticipantInfo p = this.setup.participant(e.participant);
				if (p == null) {
					continue;
				}
				rows.add(new Row(e.participant, p.name, colorOf(p), e.reason, e.waitedMs + since));
			}
		}
		else if (this.starving && (now - this.lastFedAt) >= this.timings.lagAfterMs) {
			// Turns stopped coming and the host has not said why: its own network thread stands still.
			final ParticipantInfo host = this.setup.participant(this.currentHost);
			if (host != null) {
				rows.add(new Row(host.id, host.name, colorOf(host), LanProtocol.LAG_LAGGING, now - this.lastFedAt));
			}
		}
		if (rows.isEmpty()) {
			final List<Row> loadingRows = loadingRows();
			if (!loadingRows.isEmpty()) {
				final Window w = new Window(loadingRows, false, false, -1, -1);
				w.loading = true;
				return w;
			}
			return claimableAbsent() ? new Window(rows, false, true, -1, 0) : null;
		}
		long longest = 0;
		for (final Row r : rows) {
			longest = Math.max(longest, r.waitedMs);
		}
		final boolean iAmWaitedFor = waitedFor(rows, this.me);
		final int continueIn = iAmWaitedFor || !isPlayer(this.me) ? -1
				: seconds(this.timings.continueAfterMs - longest);
		int claimIn = -1;
		if (!iAmWaitedFor && opponentsGone(rows)) {
			long shortest = Long.MAX_VALUE;
			boolean any = false;
			for (final Row r : rows) {
				if (isOpponent(r.participant)) {
					shortest = Math.min(shortest, r.waitedMs);
					any = true;
				}
			}
			claimIn = any ? seconds(this.timings.claimAfterMs - shortest) : 0;
		}
		return new Window(rows, false, false, continueIn, claimIn);
	}

	private List<Row> loadingRows() {
		final List<Row> rows = new ArrayList<Row>();
		for (final ParticipantInfo p : this.setup.participants) {
			if (p.id != this.me && isLoading(p.id)) {
				rows.add(new Row(p.id, p.name, colorOf(p), LanProtocol.LAG_LAGGING, 0, this.loadPercents.get(p.id)));
			}
		}
		return rows;
	}

	public void continueWithout() {
		this.actions.vote(LanProtocol.VOTE_CONTINUE_WITHOUT);
	}

	public void claimVictory() {
		this.actions.vote(LanProtocol.VOTE_CLAIM_VICTORY);
	}

	private static int seconds(final long ms) {
		return ms <= 0 ? 0 : (int) ((ms + 999) / 1000);
	}

	private static boolean waitedFor(final List<Row> rows, final int participant) {
		for (final Row r : rows) {
			if (r.participant == participant) {
				return true;
			}
		}
		return false;
	}

	private boolean isPlayer(final int participant) {
		final ParticipantInfo p = this.setup.participant(participant);
		return p != null && p.role == LanProtocol.ROLE_PLAYER && p.slot != LanProtocol.NO_SLOT;
	}

	private int teamOf(final ParticipantInfo p) {
		for (final SlotInfo s : this.setup.slots) {
			if (s.index == p.slot) {
				return s.team;
			}
		}
		return -1;
	}

	private int colorOf(final ParticipantInfo p) {
		for (final SlotInfo s : this.setup.slots) {
			if (s.index == p.slot) {
				return s.color;
			}
		}
		return 0;
	}

	private boolean isOpponent(final int participant) {
		final ParticipantInfo mine = this.setup.participant(this.me);
		final ParticipantInfo other = this.setup.participant(participant);
		return mine != null && other != null && isPlayer(this.me) && isPlayer(participant)
				&& teamOf(mine) != teamOf(other);
	}

	/** Every human opponent is waited for or absent, there is one at least, and no computer plays against us. */
	private boolean opponentsGone(final List<Row> rows) {
		final ParticipantInfo mine = this.setup.participant(this.me);
		if (mine == null || !isPlayer(this.me)) {
			return false;
		}
		final int team = teamOf(mine);
		for (final SlotInfo s : this.setup.slots) {
			if (s.kind == LanProtocol.SLOT_COMPUTER && s.team != team) {
				return false;
			}
		}
		boolean any = false;
		for (final ParticipantInfo p : this.setup.participants) {
			if (p.id == this.me || !isOpponent(p.id) || presenceOf(p.id) == LanProtocol.PRESENCE_LEFT) {
				continue;
			}
			any = true;
			if (presenceOf(p.id) != LanProtocol.PRESENCE_ABSENT && !waitedFor(rows, p.id)) {
				return false;
			}
		}
		return any;
	}

	/** All human opponents are absent (and none is still playing), so the claim is open without a wait. */
	private boolean claimableAbsent() {
		return opponentsGone(Collections.<Row>emptyList());
	}
}
