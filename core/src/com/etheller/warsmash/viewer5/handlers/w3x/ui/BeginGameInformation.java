package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.IntIntMap;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplay;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup.CurrentNetGameMapLookup;

public class BeginGameInformation {
	public CurrentNetGameMapLookup gameMapLookup;
	public byte[] hostInetAddress;
	public int hostUdpPort;
	public long sessionToken;
	public int localPlayerIndex;
	public IntIntMap serverSlotToMapSlot;
	public IntIntMap mapSlotToServerSlot;
	public boolean loadingStarted = false;
	/** The replay this game plays, or null for a game that is played. */
	public CReplay replay;
	/** The file the replay was read from, which Restart plays again. */
	public FileHandle replayFile;
	/** The step the replay plays on to without drawing before it is shown, or 0. */
	public int replayStartTick;
	/** The replay is played although this engine would refuse it. */
	public boolean replayForced;
}
