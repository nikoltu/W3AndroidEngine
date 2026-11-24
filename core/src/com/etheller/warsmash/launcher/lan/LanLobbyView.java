package com.etheller.warsmash.launcher.lan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.launcher.CustomGame;

import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;

/**
 * The lobby as a page draws it: one row per slot of the map, then the people who watch. It is made from the host's
 * lobby state and knows who this player is, so a page only draws and sends the choices it offers.
 */
public final class LanLobbyView {
	public static final int PLAYER = 0;
	public static final int COMPUTER = 1;
	public static final int OPEN = 2;
	public static final int CLOSED = 3;

	/** A slot of the map. */
	public static final class Row {
		public int kind;
		public int slot;
		public int participantId = LanProtocol.NO_PARTICIPANT;
		public String name = "";
		public boolean host;
		public boolean you;
		public int race;
		public int team;
		public int color;
		public int handicap = 100;
		public int computerLevel;
		public boolean ready;
		public int pingMs;
		/** The profile portrait the player chose (its number in the skin's portraits), or -1 when none is known. */
		public int portrait = -1;
		/** Plays with a keyboard and mouse (the mark after the name). */
		public boolean keyboardAndMouse;
		/** The percent of the map this human has received or sent, or -1 when the map is not in transfer. */
		public int mapPercent = -1;
		/** True when the percent is the host sending to another player. */
		public boolean mapSending;
		/** Race, team, color and handicap can be changed by this player. */
		public boolean editable;
		/** The host can open the slot's menu (Open, Closed, Computer, Kick). */
		public boolean hostMenu;
		public boolean kickable;
	}

	/** Somebody who watches. */
	public static final class Watcher {
		public int participantId;
		public String name = "";
		public int role;
		public boolean you;
		public boolean keyboardAndMouse;
		public int pingMs;
		/** As {@link Row#portrait}. */
		public int portrait = -1;
		/** The host can remove this watcher. */
		public boolean kickable;
	}

	public final List<Row> rows = new ArrayList<>();
	public final List<Watcher> watchers = new ArrayList<>();
	public LanData.Options options = new LanData.Options();
	public LanData.MapInfo map = new LanData.MapInfo();
	public boolean syncWarning;
	public int ownerId = LanProtocol.NO_PARTICIPANT;
	public int myId = LanProtocol.NO_PARTICIPANT;
	public boolean iAmHost;
	public int humans;
	public int readyHumans;
	public int computers;
	/** A slot can be taken or the watchers' place is open. */
	public boolean canWatch;
	public int myRole = LanProtocol.ROLE_PLAYER;
	public Row mine;

	/**
	 * @param transfers the percent the host has sent to each participant, by participant id (host only), or null
	 * @param myMapPercent the percent of the map this player has received, or -1
	 */
	public static LanLobbyView of(final LanMessages.LobbyState state, final int myId,
			final Map<Integer, Integer> transfers, final int myMapPercent) {
		return of(state, myId, transfers, myMapPercent, false);
	}

	/** @param reforgedSkin whether the portraits are shown from the Reforged skin (else the classic one) */
	public static LanLobbyView of(final LanMessages.LobbyState state, final int myId,
			final Map<Integer, Integer> transfers, final int myMapPercent, final boolean reforgedSkin) {
		final LanLobbyView view = new LanLobbyView();
		view.options = state.options;
		view.map = state.map;
		view.syncWarning = state.syncWarning;
		view.ownerId = state.ownerId;
		view.myId = myId;
		view.iAmHost = (myId != LanProtocol.NO_PARTICIPANT) && (state.ownerId == myId);
		view.canWatch = state.options.observers != LanProtocol.OBSERVERS_NONE;
		for (final LanData.SlotInfo s : state.slots) {
			final Row row = new Row();
			row.slot = s.index;
			row.race = s.race;
			// A team is a number up to the slot count, whatever a host that made the lobby before a map change left.
			row.team = Math.min(s.team, Math.max(0, state.slots.size() - 1));
			row.color = s.color;
			row.handicap = s.handicap;
			switch (s.kind) {
			case LanProtocol.SLOT_HUMAN:
				row.kind = PLAYER;
				row.participantId = s.participantId;
				row.name = s.name;
				row.host = s.participantId == state.ownerId;
				row.you = s.participantId == myId;
				row.ready = s.ready;
				row.pingMs = s.pingMs;
				row.keyboardAndMouse = s.controlScheme == LanProtocol.CONTROL_KBM;
				row.portrait = portrait(state, s.participantId, reforgedSkin);
				row.editable = row.you;
				row.hostMenu = view.iAmHost && !row.you;
				row.kickable = row.hostMenu;
				view.humans++;
				if (s.ready) {
					view.readyHumans++;
				}
				if (row.you) {
					view.mine = row;
				}
				if (row.you && (myMapPercent >= 0)) {
					row.mapPercent = myMapPercent;
				}
				else if (!row.you && (transfers != null) && transfers.containsKey(s.participantId)) {
					row.mapPercent = transfers.get(s.participantId);
					row.mapSending = true;
				}
				break;
			case LanProtocol.SLOT_COMPUTER:
				row.kind = COMPUTER;
				row.computerLevel = s.computerLevel;
				row.name = LanText.computer(s.computerLevel);
				row.hostMenu = view.iAmHost;
				row.editable = view.iAmHost;
				view.computers++;
				break;
			case LanProtocol.SLOT_CLOSED:
				row.kind = CLOSED;
				row.name = LanText.slotClosed();
				row.hostMenu = view.iAmHost;
				break;
			default:
				row.kind = OPEN;
				row.name = LanText.slotOpen();
				row.hostMenu = view.iAmHost;
				break;
			}
			view.rows.add(row);
		}
		for (final LanData.ObserverInfo o : state.observers) {
			final Watcher w = new Watcher();
			w.participantId = o.participantId;
			w.name = o.name;
			w.role = o.role;
			w.you = o.participantId == myId;
			w.keyboardAndMouse = o.controlScheme == LanProtocol.CONTROL_KBM;
			w.pingMs = o.pingMs;
			w.portrait = portrait(state, o.participantId, reforgedSkin);
			w.kickable = view.iAmHost && !w.you;
			view.watchers.add(w);
			if (w.you) {
				view.myRole = o.role;
			}
		}
		return view;
	}

	private static int portrait(final LanMessages.LobbyState state, final int participantId, final boolean reforgedSkin) {
		return LanPortraitId.decode(state.portraits.get(participantId), reforgedSkin);
	}

	/** What the host's main button does now. */
	public enum HostAction {
		READY, NOT_READY, START
	}

	/** Start once everyone is ready; until then the button is the host's own ready mark, like a guest's. */
	public HostAction hostAction() {
		if (canStart()) {
			return HostAction.START;
		}
		return (this.mine != null) && this.mine.ready ? HostAction.NOT_READY : HostAction.READY;
	}

	/** The host may start: every person who plays is ready (the host's own mark counts), and someone plays. */
	public boolean canStart() {
		return this.iAmHost && (this.humans > 0) && (this.readyHumans == this.humans);
	}

	public String title() {
		return LanText.lobbyTitle(this.options.gameName, mapLabel());
	}

	/** The map's own name, else its file name. */
	public String mapLabel() {
		return this.map.title.isEmpty() ? this.map.name : this.map.title;
	}

	/** The colours the players and computers of the other rows have, by colour number. */
	public boolean[] takenColors(final int slot) {
		final boolean[] taken = new boolean[CustomGame.PLAYER_COLORS.length];
		for (final Row r : this.rows) {
			if ((r.slot != slot) && ((r.kind == PLAYER) || (r.kind == COMPUTER))) {
				taken[Math.floorMod(r.color, taken.length)] = true;
			}
		}
		return taken;
	}

	/** The player's choices on a row go to the host as a request; the host's on a computer's row as its setup. */
	public void choose(final LanLauncherModel model, final Row row, final int race, final int team, final int color,
			final int handicap) {
		// A taken colour is not sent: the host would keep the old one anyway, and two requests for one colour race.
		if ((color != row.color) && takenColors(row.slot)[Math.floorMod(color, CustomGame.PLAYER_COLORS.length)]) {
			choose(model, row, race, team, row.color, handicap);
			return;
		}
		if (row.you) {
			model.slotRequest(row.slot, race, team, color, handicap);
		}
		else if ((row.kind == COMPUTER) && this.iAmHost) {
			model.hostSetup(row.slot, race, team, color, handicap);
		}
	}

	public String readyText() {
		return LanText.readyCount(this.readyHumans, this.humans);
	}

	/** The name of a participant, or null when the lobby does not know it. */
	public String nameOf(final int participantId) {
		for (final Row r : this.rows) {
			if ((r.kind == PLAYER) && (r.participantId == participantId)) {
				return r.name;
			}
		}
		for (final Watcher w : this.watchers) {
			if (w.participantId == participantId) {
				return w.name;
			}
		}
		return null;
	}

	/** Who would lead the game if the host left: the players by seat, then the referees, then the observers. */
	public String nextHostName() {
		String best = null;
		int bestKey = Integer.MAX_VALUE;
		for (final Row r : this.rows) {
			if ((r.kind == PLAYER) && !r.host) {
				final int key = LanProtocol.hostKey(LanProtocol.ROLE_PLAYER, r.slot, r.participantId);
				if (key < bestKey) {
					bestKey = key;
					best = r.name;
				}
			}
		}
		for (final Watcher w : this.watchers) {
			if (w.participantId != this.ownerId) {
				final int key = LanProtocol.hostKey(w.role, LanProtocol.NO_SLOT, w.participantId);
				if (key < bestKey) {
					bestKey = key;
					best = w.name;
				}
			}
		}
		return best;
	}

}
