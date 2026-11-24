package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.Arrays;

import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui.CommandCardModel;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui.PlayerPanelState;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui.SelectedUnitModel;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui.TooltipModel;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui.UnitDetailsModel;

/**
 * The detailed part of a frame that only the interface needs: the local player's selection, heroes, control groups, the
 * active unit's details and command card, the hovered unit's details and the player panel. The interface reads nothing
 * else of the simulation, so it always shows the same step as the world does.
 * <p>
 * Writer: the simulation thread, at the end of a step, from the simulation's own objects (the card logic must only
 * call methods that do not change state). Reader: the render thread, after the frame is published. The unit to detail
 * for hover comes from {@link LocalUiSnapshot#hoveredUnitHandle}, so it is one frame late.
 */
public final class UiFrameState {
	public static final int MAX_SELECTED = 24;
	public static final int MAX_HEROES = 7;
	public static final int CONTROL_GROUPS = 10;
	public static final int MAX_IDLE_WORKERS = 256;
	/** Players the relations below cover (bit masks of an int). */
	public static final int PLAYER_SLOTS = 32;

	/** Bit j of entry i is set when player i is allied (passive) with player j. */
	public final int[] alliancePassive = new int[PLAYER_SLOTS];
	/** Bit j of entry i is set when player i shares control with player j. */
	public final int[] allianceSharedControl = new int[PLAYER_SLOTS];
	/** Bit i is set for a player that is a human controlled, playing slot. */
	public int userPlayingMask;
	/** Bit i is set for a player Replay Vision offers: one who played, not a neutral one. */
	public int replayPlayerMask;
	/** Bits of {@link #localPlayerEvents}: the local player has a script trigger for the input event. */
	public static final int EVENT_KEY_DOWN = 1;
	public static final int EVENT_KEY_UP = 1 << 1;
	public static final int EVENT_MOUSE_DOWN = 1 << 2;
	public static final int EVENT_MOUSE_UP = 1 << 3;
	public static final int EVENT_MOUSE_MOVE = 1 << 4;
	/** The input events the local player's triggers listen for, so the interface sends only those to the script. */
	public int localPlayerEvents;
	/** Bit i is set for a neutral aggressive player (the creeps' owner). */
	public int neutralAggressiveMask;
	/** Player colour index of each player, -1 for no such player. */
	public final int[] playerColor = new int[PLAYER_SLOTS];
	/** Name of each player, null for no such player. */
	public final String[] playerName = new String[PLAYER_SLOTS];
	/** Whether the relations above were written (not before a local player is known). */
	public boolean playersWritten;

	public int selectedCount;
	public final int[] selected = new int[MAX_SELECTED];
	/** Icon, texts and life of each selected unit, parallel to {@link #selected}. */
	public final SelectedUnitModel[] selectedDetails = new SelectedUnitModel[MAX_SELECTED];
	/**
	 * The hero bar in its order: the local player's heroes, then, after the slots kept for them, those of players that
	 * share their units; a slot kept but empty has handle 0 (and a cleared details model).
	 */
	public int heroCount;
	public final int[] heroes = new int[MAX_HEROES];
	/** First unit of each control group, 0 for an empty group. */
	public final int[] controlGroupFirst = new int[CONTROL_GROUPS];
	/** Number of living units of each control group. */
	public final int[] controlGroupSize = new int[CONTROL_GROUPS];
	/** The unit each group is shown by (a hero before others, then level, then cost), 0 for an empty group. */
	public final int[] controlGroupLeader = new int[CONTROL_GROUPS];
	public final int[] controlGroupLeaderType = new int[CONTROL_GROUPS];
	public final String[] controlGroupLeaderIcon = new String[CONTROL_GROUPS];
	/** The local player's idle workers (standing without an order, not inside a mine or burrow). */
	public int idleWorkerCount;
	public final int[] idleWorkers = new int[MAX_IDLE_WORKERS];
	/** The text over the widget under the pointer (a unit, an item, a tree), null for none. */
	public String hoverTip;
	/** Handle of the widget {@link #hoverTip} is for, 0 for none (the request is one frame older than the render side's). */
	public int hoverHandle;
	/** The tip of the console part under the pointer, valid when {@link #panelTipShown}. */
	public final TooltipModel panelTip = new TooltipModel();
	public boolean panelTipShown;
	/** The console part {@link #panelTip} was asked for, -1 for none (the request is one frame older than the render side's). */
	public int panelTipIndex = -1;
	/** Handle of the active (sub-group leader) unit of the selection, 0 when nothing is selected. */
	public int activeUnit;
	public final UnitDetailsModel active = new UnitDetailsModel();
	public final UnitDetailsModel hovered = new UnitDetailsModel();
	public final UnitDetailsModel[] heroDetails = new UnitDetailsModel[MAX_HEROES];
	public final CommandCardModel commandCard = new CommandCardModel();
	public final PlayerPanelState panel = new PlayerPanelState();

	public UiFrameState() {
		for (int i = 0; i < MAX_SELECTED; i++) {
			selectedDetails[i] = new SelectedUnitModel();
		}
		for (int i = 0; i < MAX_HEROES; i++) {
			heroDetails[i] = new UnitDetailsModel();
		}
	}

	public void clear() {
		playersWritten = false;
		selectedCount = 0;
		heroCount = 0;
		activeUnit = 0;
		Arrays.fill(controlGroupFirst, 0);
		Arrays.fill(controlGroupSize, 0);
		Arrays.fill(controlGroupLeader, 0);
		Arrays.fill(controlGroupLeaderType, 0);
		Arrays.fill(controlGroupLeaderIcon, null);
		idleWorkerCount = 0;
		hoverTip = null;
		hoverHandle = 0;
		panelTip.clear();
		panelTipShown = false;
		panelTipIndex = -1;
		active.clear();
		hovered.clear();
		for (final UnitDetailsModel hero : heroDetails) {
			hero.clear();
		}
		commandCard.clear();
		panel.clear();
	}

	public void copyFrom(final UiFrameState other) {
		playersWritten = other.playersWritten;
		System.arraycopy(other.alliancePassive, 0, alliancePassive, 0, PLAYER_SLOTS);
		System.arraycopy(other.allianceSharedControl, 0, allianceSharedControl, 0, PLAYER_SLOTS);
		System.arraycopy(other.playerColor, 0, playerColor, 0, PLAYER_SLOTS);
		System.arraycopy(other.playerName, 0, playerName, 0, PLAYER_SLOTS);
		userPlayingMask = other.userPlayingMask;
		replayPlayerMask = other.replayPlayerMask;
		localPlayerEvents = other.localPlayerEvents;
		neutralAggressiveMask = other.neutralAggressiveMask;
		selectedCount = other.selectedCount;
		System.arraycopy(other.selected, 0, selected, 0, MAX_SELECTED);
		heroCount = other.heroCount;
		System.arraycopy(other.heroes, 0, heroes, 0, MAX_HEROES);
		System.arraycopy(other.controlGroupFirst, 0, controlGroupFirst, 0, CONTROL_GROUPS);
		System.arraycopy(other.controlGroupSize, 0, controlGroupSize, 0, CONTROL_GROUPS);
		System.arraycopy(other.controlGroupLeader, 0, controlGroupLeader, 0, CONTROL_GROUPS);
		System.arraycopy(other.controlGroupLeaderType, 0, controlGroupLeaderType, 0, CONTROL_GROUPS);
		System.arraycopy(other.controlGroupLeaderIcon, 0, controlGroupLeaderIcon, 0, CONTROL_GROUPS);
		idleWorkerCount = other.idleWorkerCount;
		System.arraycopy(other.idleWorkers, 0, idleWorkers, 0, Math.min(idleWorkerCount, MAX_IDLE_WORKERS));
		hoverTip = other.hoverTip;
		hoverHandle = other.hoverHandle;
		panelTip.copyFrom(other.panelTip);
		panelTipShown = other.panelTipShown;
		panelTipIndex = other.panelTipIndex;
		for (int i = 0; i < selectedCount; i++) {
			selectedDetails[i].copyFrom(other.selectedDetails[i]);
		}
		activeUnit = other.activeUnit;
		active.copyFrom(other.active);
		hovered.copyFrom(other.hovered);
		for (int i = 0; i < MAX_HEROES; i++) {
			heroDetails[i].copyFrom(other.heroDetails[i]);
		}
		commandCard.copyFrom(other.commandCard);
		panel.copyFrom(other.panel);
	}
}
