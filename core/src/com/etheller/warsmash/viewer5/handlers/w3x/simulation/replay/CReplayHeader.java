package com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.ai.AIDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.CBasePlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfig;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRaceManager;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRacePreference;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CMapDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

/**
 * What a game started from, beside the map itself: the map's script sets up the rest again the same way. The player
 * slots are kept as the game setup left them, because the setup draws start locations without the game's seed.
 */
public final class CReplayHeader {
	private final String engineVersion;
	private final String buildCommit;
	private final boolean officialBuild;
	private final String mapPath;
	private final int mapChecksum;
	private final long mapSize;
	private final int dataChecksum;
	private final long recordedAt;
	private final int seed;
	private final int localPlayerIndex;
	private final float gameSpeed;
	private final CMapDifficulty difficulty;
	private final int checksumInterval;
	private final List<Slot> slots;
	private final byte[] gameCaches;

	/**
	 * @param dataChecksum the checksum of the game data the game ran on (see {@code GameReplays#dataChecksum})
	 */
	public CReplayHeader(final String engineVersion, final String buildCommit, final boolean officialBuild,
			final String mapPath, final int mapChecksum, final long mapSize,
			final int dataChecksum, final long recordedAt, final int seed, final int localPlayerIndex,
			final float gameSpeed, final CMapDifficulty difficulty, final int checksumInterval, final List<Slot> slots,
			final byte[] gameCaches) {
		this.engineVersion = engineVersion;
		this.buildCommit = buildCommit;
		this.officialBuild = officialBuild;
		this.mapPath = mapPath;
		this.mapChecksum = mapChecksum;
		this.mapSize = mapSize;
		this.dataChecksum = dataChecksum;
		this.recordedAt = recordedAt;
		this.seed = seed;
		this.localPlayerIndex = localPlayerIndex;
		this.gameSpeed = gameSpeed;
		this.difficulty = difficulty;
		this.checksumInterval = checksumInterval;
		this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
		this.gameCaches = gameCaches;
	}

	/**
	 * @param gameCaches the game caches the game started with (see {@code CGameCache#snapshot}), or null when none
	 *                   were read from the disk yet
	 */
	public static CReplayHeader of(final String engineVersion, final String buildCommit, final boolean officialBuild,
			final String mapPath, final int mapChecksum,
			final long mapSize, final int dataChecksum, final long recordedAt, final int seed,
			final int localPlayerIndex, final float gameSpeed, final War3MapConfig config, final int checksumInterval,
			final byte[] gameCaches) {
		final List<Slot> slots = new ArrayList<>();
		for (int i = 0; i < config.getMaxPlayers(); i++) {
			slots.add(Slot.of(config.getPlayer(i)));
		}
		return new CReplayHeader(engineVersion, buildCommit, officialBuild, mapPath, mapChecksum, mapSize,
				dataChecksum, recordedAt, seed,
				localPlayerIndex, gameSpeed, config.getGameDifficulty(), checksumInterval, slots, gameCaches);
	}

	/** Puts the recorded game setup over the one the map's own setup made. */
	public void applyTo(final War3MapConfig config, final CRaceManager raceManager) {
		config.setGameDifficulty(this.difficulty);
		for (final Slot slot : this.slots) {
			if ((slot.id >= 0) && (slot.id < config.getMaxPlayers())) {
				slot.applyTo(config.getPlayer(slot.id), raceManager);
			}
		}
	}

	public String getEngineVersion() {
		return this.engineVersion;
	}

	/** The short git commit of the build that recorded the game, or "unknown". */
	public String getBuildCommit() {
		return this.buildCommit;
	}

	/** Whether the recording build was an official release. */
	public boolean isOfficialBuild() {
		return this.officialBuild;
	}

	public String getMapPath() {
		return this.mapPath;
	}

	public int getMapChecksum() {
		return this.mapChecksum;
	}

	public long getMapSize() {
		return this.mapSize;
	}

	public int getDataChecksum() {
		return this.dataChecksum;
	}

	public long getRecordedAt() {
		return this.recordedAt;
	}

	public int getSeed() {
		return this.seed;
	}

	public int getLocalPlayerIndex() {
		return this.localPlayerIndex;
	}

	public float getGameSpeed() {
		return this.gameSpeed;
	}

	public CMapDifficulty getDifficulty() {
		return this.difficulty;
	}

	public int getChecksumInterval() {
		return this.checksumInterval;
	}

	public List<Slot> getSlots() {
		return this.slots;
	}

	public byte[] getGameCaches() {
		return this.gameCaches == null ? null : this.gameCaches.clone();
	}

	void write(final DataOutputStream out) throws IOException {
		out.writeUTF(this.engineVersion);
		out.writeUTF(this.buildCommit);
		out.writeBoolean(this.officialBuild);
		out.writeUTF(this.mapPath);
		out.writeInt(this.mapChecksum);
		out.writeLong(this.mapSize);
		out.writeInt(this.dataChecksum);
		out.writeLong(this.recordedAt);
		out.writeInt(this.seed);
		out.writeByte(this.localPlayerIndex);
		out.writeFloat(this.gameSpeed);
		out.writeByte(this.difficulty.ordinal());
		out.writeInt(this.checksumInterval);
		out.writeByte(this.slots.size());
		for (final Slot slot : this.slots) {
			slot.write(out);
		}
		if (this.gameCaches == null) {
			out.writeInt(-1);
		}
		else {
			out.writeInt(this.gameCaches.length);
			out.write(this.gameCaches);
		}
	}

	static CReplayHeader read(final DataInputStream in) throws IOException {
		final String engineVersion = in.readUTF();
		final String buildCommit = in.readUTF();
		final boolean officialBuild = in.readBoolean();
		final String mapPath = in.readUTF();
		final int mapChecksum = in.readInt();
		final long mapSize = in.readLong();
		final int dataChecksum = in.readInt();
		final long recordedAt = in.readLong();
		final int seed = in.readInt();
		final int localPlayerIndex = in.readByte();
		final float gameSpeed = in.readFloat();
		final CMapDifficulty difficulty = CReplay.enumValue(CMapDifficulty.VALUES, in.readUnsignedByte());
		final int checksumInterval = in.readInt();
		final int slotCount = in.readUnsignedByte();
		final List<Slot> slots = new ArrayList<>(slotCount);
		for (int i = 0; i < slotCount; i++) {
			slots.add(Slot.read(in));
		}
		final int cachesLength = in.readInt();
		byte[] gameCaches = null;
		if (cachesLength >= 0) {
			gameCaches = new byte[cachesLength];
			in.readFully(gameCaches);
		}
		return new CReplayHeader(engineVersion, buildCommit, officialBuild, mapPath, mapChecksum, mapSize,
				dataChecksum, recordedAt, seed,
				localPlayerIndex, gameSpeed, difficulty, checksumInterval, slots, gameCaches);
	}

	/** A player slot as the game setup left it. */
	public static final class Slot {
		private static final int NO_VALUE = -1;
		private final int id;
		private final String name;
		private final CMapControl controller;
		private final CPlayerSlotState slotState;
		private final AIDifficulty aiDifficulty;
		private final int racePreferences;
		private final int raceSkin;
		private final int team;
		private final int color;
		private final int startLocationIndex;
		private final float handicap;

		private Slot(final int id, final String name, final CMapControl controller, final CPlayerSlotState slotState,
				final AIDifficulty aiDifficulty, final int racePreferences, final int raceSkin, final int team,
				final int color, final int startLocationIndex, final float handicap) {
			this.id = id;
			this.name = name;
			this.controller = controller;
			this.slotState = slotState;
			this.aiDifficulty = aiDifficulty;
			this.racePreferences = racePreferences;
			this.raceSkin = raceSkin;
			this.team = team;
			this.color = color;
			this.startLocationIndex = startLocationIndex;
			this.handicap = handicap;
		}

		static Slot of(final CBasePlayer player) {
			final CRacePreference skin = player.getRaceSkin();
			return new Slot(player.getId(), player.getName() == null ? "" : player.getName(), player.getController(),
					player.getSlotState(), player.getAIDifficulty(), player.getRacePreferences().getBitFlags(),
					skin == null ? NO_VALUE : skin.getBitMaskValue(), player.getTeam(), player.getColor(),
					player.getStartLocationIndex(), player.getHandicap());
		}

		void applyTo(final CBasePlayer player, final CRaceManager raceManager) {
			player.setName(this.name);
			player.setController(this.controller);
			player.setSlotState(this.slotState);
			player.setAIDifficulty(this.aiDifficulty);
			player.getRacePreferences().setBitFlags(this.racePreferences);
			player.setRaceSkin(this.raceSkin == NO_VALUE ? null
					: raceManager.getRacePreference(this.raceSkin));
			player.setTeam(this.team);
			player.setColor(this.color);
			player.setStartLocationIndex(this.startLocationIndex);
			player.setHandicap(this.handicap);
		}

		public int getId() {
			return this.id;
		}

		public String getName() {
			return this.name;
		}

		public CMapControl getController() {
			return this.controller;
		}

		public CPlayerSlotState getSlotState() {
			return this.slotState;
		}

		public AIDifficulty getAIDifficulty() {
			return this.aiDifficulty;
		}

		public int getRacePreferences() {
			return this.racePreferences;
		}

		public int getTeam() {
			return this.team;
		}

		public int getColor() {
			return this.color;
		}

		public int getStartLocationIndex() {
			return this.startLocationIndex;
		}

		private void write(final DataOutputStream out) throws IOException {
			out.writeByte(this.id);
			out.writeUTF(this.name);
			out.writeByte(this.controller.ordinal());
			out.writeByte(this.slotState.ordinal());
			out.writeByte(this.aiDifficulty == null ? NO_VALUE : this.aiDifficulty.ordinal());
			out.writeInt(this.racePreferences);
			out.writeInt(this.raceSkin);
			out.writeInt(this.team);
			out.writeInt(this.color);
			out.writeInt(this.startLocationIndex);
			out.writeFloat(this.handicap);
		}

		private static Slot read(final DataInputStream in) throws IOException {
			final int id = in.readByte();
			final String name = in.readUTF();
			final CMapControl controller = CReplay.enumValue(CMapControl.VALUES, in.readUnsignedByte());
			final CPlayerSlotState slotState = CReplay.enumValue(CPlayerSlotState.VALUES, in.readUnsignedByte());
			final int aiDifficulty = in.readByte();
			return new Slot(id, name, controller, slotState,
					aiDifficulty == NO_VALUE ? null : CReplay.enumValue(AIDifficulty.VALUES, aiDifficulty),
					in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readFloat());
		}
	}
}
