package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Campaign state scripts change as missions end: what the campaign menu shows. It is the player's profile file, kept
 * in memory and written as it changes, the same for every game of the profile; the map that comes next is the ending
 * game's own ({@link CGameExit}).
 */
public final class CCampaignProgress {
	/**
	 * Scripts number campaigns by their place in the campaign list of the game data, and the lists differ: Reign of
	 * Chaos and The Frozen Throne each start at 0 (1.29 keeps their progress apart), the combined list of later
	 * versions numbers them all at once. Each list therefore keeps its own progress.
	 */
	public enum CampaignList {
		COMBINED, REIGN_OF_CHAOS, FROZEN_THRONE
	}

	private static final class Progress {
		private final Set<Long> missions = new HashSet<>();
		private final Set<Integer> campaigns = new HashSet<>();
		private final Set<Integer> openingCinematics = new HashSet<>();
		private final Set<Integer> endingCinematics = new HashSet<>();
		private final Set<Long> wonMissions = new HashSet<>();
		private boolean tutorialCleared;
		private int menuCampaignIndex = -1;
		private int classicMenuCampaignIndex = -1;
		/** SetDefaultDifficulty, or -1 while the game difficulty is the default. */
		private int defaultDifficulty = -1;
		private final Set<Integer> customCampaignButtons = new HashSet<>();

		private void write(final DataOutputStream out) throws IOException {
			writeMissions(out, this.missions);
			writeFlags(out, this.campaigns);
			writeFlags(out, this.openingCinematics);
			writeFlags(out, this.endingCinematics);
			out.writeBoolean(this.tutorialCleared);
			out.writeInt(this.menuCampaignIndex);
			out.writeInt(this.classicMenuCampaignIndex);
			writeMissions(out, this.wonMissions);
			out.writeInt(this.defaultDifficulty);
			writeFlags(out, this.customCampaignButtons);
		}

		private void read(final DataInputStream in, final int version) throws IOException {
			readMissions(in, this.missions);
			readFlags(in, this.campaigns);
			readFlags(in, this.openingCinematics);
			readFlags(in, this.endingCinematics);
			this.tutorialCleared = in.readBoolean();
			this.menuCampaignIndex = in.readInt();
			this.classicMenuCampaignIndex = version >= 2 ? in.readInt() : -1;
			if (version >= 4) {
				readMissions(in, this.wonMissions);
			}
			if (version >= 5) {
				this.defaultDifficulty = in.readInt();
				readFlags(in, this.customCampaignButtons);
			}
		}
	}

	private static final Map<CampaignList, Progress> PROGRESS = new EnumMap<>(CampaignList.class);
	private static CampaignList campaignList = CampaignList.COMBINED;
	private static final String STORE_FILE = "Campaigns.w3v";
	private static final int STORE_VERSION = 5;
	private static boolean loaded;

	private CCampaignProgress() {
	}

	/** Set by the menu from the campaign list the game data has, before any progress is read or written. */
	public static void setCampaignList(final CampaignList list) {
		campaignList = list;
	}

	private static Progress current() {
		loadOnce();
		return PROGRESS.computeIfAbsent(campaignList, list -> new Progress());
	}

	private static void loadOnce() {
		if (!loaded) {
			loaded = true;
			CSaveStorage.read(CSaveStorage.profileFile(STORE_FILE), CCampaignProgress::readAll);
		}
	}

	private static void save() {
		CSaveStorage.writeAtomically(CSaveStorage.profileFile(STORE_FILE), CCampaignProgress::writeAll);
	}

	/** Another player profile has its own progress. */
	static void forgetLoaded() {
		PROGRESS.clear();
		loaded = false;
	}

	public static void writeAll(final DataOutputStream out) throws IOException {
		out.writeInt(STORE_VERSION);
		out.writeInt(PROGRESS.size());
		for (final Map.Entry<CampaignList, Progress> entry : PROGRESS.entrySet()) {
			out.writeUTF(entry.getKey().name());
			entry.getValue().write(out);
		}
	}

	public static void readAll(final DataInputStream in) throws IOException {
		final Map<CampaignList, Progress> read = parse(in, campaignList);
		PROGRESS.clear();
		PROGRESS.putAll(read);
		loaded = true;
	}

	private static Map<CampaignList, Progress> parse(final DataInputStream in, final CampaignList olderFileList)
			throws IOException {
		final int version = in.readInt();
		if ((version < 1) || (version > STORE_VERSION)) {
			throw new IOException("Unknown campaign progress version");
		}
		final Map<CampaignList, Progress> read = new EnumMap<>(CampaignList.class);
		if (version < 3) {
			// Older files kept one progress, written with the campaign list this game data still has.
			final Progress progress = new Progress();
			progress.read(in, version);
			read.put(olderFileList, progress);
		}
		else {
			final int lists = in.readInt();
			for (int i = 0; i < lists; i++) {
				final String listName = in.readUTF();
				final Progress progress = new Progress();
				progress.read(in, version);
				try {
					read.put(CampaignList.valueOf(listName), progress);
				}
				catch (final IllegalArgumentException e) {
					// A list a later version knows about; this one cannot show it.
				}
			}
		}
		return read;
	}

	/**
	 * The progress of one campaign list as a profile's file holds it, read without touching the progress the game is
	 * playing with (the launcher reads it beside the engine).
	 */
	public static final class Snapshot {
		private final Progress progress;

		private Snapshot(final Progress progress) {
			this.progress = progress != null ? progress : new Progress();
		}

		public boolean isMissionAvailable(final int campaign, final int mission) {
			return this.progress.missions.contains(missionKey(campaign, mission));
		}

		public boolean isCampaignAvailable(final int campaign) {
			return this.progress.campaigns.contains(campaign);
		}

		public int getMenuCampaignIndex() {
			return this.progress.menuCampaignIndex;
		}

		public boolean isOpeningCinematicAvailable(final int campaign) {
			return this.progress.openingCinematics.contains(campaign);
		}

		/** A mission the player has won; scripts do not tell for every campaign's last mission. */
		public boolean isMissionWon(final int campaign, final int mission) {
			return this.progress.wonMissions.contains(missionKey(campaign, mission));
		}

		/** Scripts open a campaign's ending cinematic when its last mission is won. */
		public boolean isEndingCinematicAvailable(final int campaign) {
			return this.progress.endingCinematics.contains(campaign);
		}

		public int getClassicMenuCampaignIndex() {
			return this.progress.classicMenuCampaignIndex;
		}
	}

	/** An empty progress, as a new profile has. */
	public static Snapshot emptySnapshot() {
		return new Snapshot(null);
	}

	public static Snapshot readSnapshot(final DataInputStream in, final CampaignList list) throws IOException {
		return new Snapshot(parse(in, list).get(list));
	}

	private static void writeMissions(final DataOutputStream out, final Set<Long> missions) throws IOException {
		out.writeInt(missions.size());
		for (final long mission : missions) {
			out.writeLong(mission);
		}
	}

	private static void readMissions(final DataInputStream in, final Set<Long> missions) throws IOException {
		final int count = in.readInt();
		for (int i = 0; i < count; i++) {
			missions.add(in.readLong());
		}
	}

	private static void writeFlags(final DataOutputStream out, final Set<Integer> flags) throws IOException {
		out.writeInt(flags.size());
		for (final int flag : flags) {
			out.writeInt(flag);
		}
	}

	private static void readFlags(final DataInputStream in, final Set<Integer> flags) throws IOException {
		flags.clear();
		final int count = in.readInt();
		for (int i = 0; i < count; i++) {
			flags.add(in.readInt());
		}
	}

	private static long missionKey(final int campaign, final int mission) {
		return (((long) campaign) << 32) | (mission & 0xFFFFFFFFL);
	}

	public static void setMissionAvailable(final int campaign, final int mission, final boolean available) {
		final Set<Long> missions = current().missions;
		if (available) {
			missions.add(missionKey(campaign, mission));
		}
		else {
			missions.remove(missionKey(campaign, mission));
		}
		save();
	}

	public static boolean isMissionAvailable(final int campaign, final int mission) {
		return current().missions.contains(missionKey(campaign, mission));
	}

	/** The player won a campaign's mission, as the campaign screens mark it. */
	public static void setMissionWon(final int campaign, final int mission) {
		if (current().wonMissions.add(missionKey(campaign, mission))) {
			save();
		}
	}

	public static void setCampaignAvailable(final int campaign, final boolean available) {
		setFlag(current().campaigns, campaign, available);
		save();
	}

	public static boolean isCampaignAvailable(final int campaign) {
		return current().campaigns.contains(campaign);
	}

	public static void setOpeningCinematicAvailable(final int campaign, final boolean available) {
		setFlag(current().openingCinematics, campaign, available);
		save();
	}

	public static void setEndingCinematicAvailable(final int campaign, final boolean available) {
		setFlag(current().endingCinematics, campaign, available);
		save();
	}

	private static void setFlag(final Set<Integer> flags, final int index, final boolean flag) {
		if (flag) {
			flags.add(index);
		}
		else {
			flags.remove(index);
		}
	}

	public static void setTutorialCleared(final boolean cleared) {
		current().tutorialCleared = cleared;
		save();
	}

	public static boolean isTutorialCleared() {
		return current().tutorialCleared;
	}

	public static void setMenuCampaignIndex(final int campaignIndex) {
		current().menuCampaignIndex = campaignIndex;
		save();
	}

	public static int getMenuCampaignIndex() {
		return current().menuCampaignIndex;
	}

	/**
	 * SetCampaignMenuRace: Reign of Chaos missions pick their campaign on the classic campaign screen by race; the
	 * expansion screen keeps its own choice (SetCampaignMenuRaceEx).
	 */
	public static void setClassicMenuCampaignIndex(final int campaignIndex) {
		current().classicMenuCampaignIndex = campaignIndex;
		save();
	}

	public static int getClassicMenuCampaignIndex() {
		return current().classicMenuCampaignIndex;
	}

	/** The campaign difficulty the menu starts on, as an ordinal of the difficulties; -1 when scripts never set one. */
	public static int getDefaultDifficulty() {
		return current().defaultDifficulty;
	}

	public static void setDefaultDifficulty(final int difficulty) {
		current().defaultDifficulty = difficulty;
		save();
	}

	public static boolean isCustomCampaignButtonVisible(final int button) {
		return current().customCampaignButtons.contains(button);
	}

	public static void setCustomCampaignButtonVisible(final int button, final boolean visible) {
		setFlag(current().customCampaignButtons, button, visible);
		save();
	}
}
