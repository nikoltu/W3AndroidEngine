package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
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
		/** The hardest difficulty (an ordinal of the difficulties) each won mission was won on. */
		private final Map<Long, Integer> wonDifficulties = new HashMap<>();
		private boolean tutorialCleared;
		private int menuCampaignIndex = -1;
		private int classicMenuCampaignIndex = -1;
		/** Which of the two menu choices a script made last: {@link #MENU_CLASSIC}, {@link #MENU_EXPANSION}, else 0. */
		private int lastMenuChoice;
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
			out.writeInt(this.wonDifficulties.size());
			for (final Map.Entry<Long, Integer> won : this.wonDifficulties.entrySet()) {
				out.writeLong(won.getKey());
				out.writeInt(won.getValue());
			}
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
			if (version >= 6) {
				final int count = in.readInt();
				for (int i = 0; i < count; i++) {
					final long key = in.readLong();
					this.wonDifficulties.put(key, in.readInt());
				}
			}
		}
	}

	private static final Map<CampaignList, Progress> PROGRESS = new EnumMap<>(CampaignList.class);
	private static CampaignList campaignList = CampaignList.COMBINED;
	private static final String STORE_FILE = "Campaigns.w3v";
	private static final int STORE_VERSION = 6;
	/**
	 * After the lists a file may hold the menu choice order of each list: builds that know only the version 6 layout stop
	 * reading after the lists and lose nothing but this order.
	 */
	private static final int MENU_ORDER_MARK = 0x4D454E55;
	private static final int MENU_CLASSIC = 1;
	private static final int MENU_EXPANSION = 2;
	private static boolean loaded;
	/** Test builds open every mission and campaign in the menus; the saved progress stays as it is. */
	private static volatile boolean allMissionsOpen;

	public static void setAllMissionsOpen(final boolean open) {
		allMissionsOpen = open;
	}

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
		out.writeInt(MENU_ORDER_MARK);
		out.writeInt(PROGRESS.size());
		for (final Map.Entry<CampaignList, Progress> entry : PROGRESS.entrySet()) {
			out.writeUTF(entry.getKey().name());
			out.writeInt(entry.getValue().lastMenuChoice);
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
			readMenuOrder(in, read);
		}
		return read;
	}

	private static void readMenuOrder(final DataInputStream in, final Map<CampaignList, Progress> read)
			throws IOException {
		try {
			if (in.readInt() != MENU_ORDER_MARK) {
				return;
			}
			final int lists = in.readInt();
			for (int i = 0; i < lists; i++) {
				final String listName = in.readUTF();
				final int choice = in.readInt();
				try {
					final Progress progress = read.get(CampaignList.valueOf(listName));
					if (progress != null) {
						progress.lastMenuChoice = choice;
					}
				}
				catch (final IllegalArgumentException e) {
					// A list this version cannot show.
				}
			}
		}
		catch (final EOFException e) {
			// A file written before the order was kept, or by a build that drops it.
		}
	}

	/**
	 * The progress of one campaign list as a profile's file holds it, read without touching the progress the game is
	 * playing with (the launcher reads it beside the engine).
	 */
	public static final class Snapshot {
		private final Progress progress;
		/** Campaigns opened by hand ({@link CampaignUnlock}), by number: shown as played through, never saved here. */
		private final Set<Integer> unlocked;

		private Snapshot(final Progress progress) {
			this(progress, Collections.emptySet());
		}

		private Snapshot(final Progress progress, final Set<Integer> unlocked) {
			this.progress = progress != null ? progress : new Progress();
			this.unlocked = unlocked;
		}

		/** The same progress with these campaigns opened by hand; the progress itself is not touched. */
		public Snapshot unlocking(final Set<Integer> campaigns) {
			return new Snapshot(this.progress, new HashSet<>(campaigns));
		}

		/** What the profile has reached, without the campaigns opened by hand. */
		public Snapshot real() {
			return this.unlocked.isEmpty() ? this : new Snapshot(this.progress);
		}

		public boolean isUnlocked(final int campaign) {
			return this.unlocked.contains(campaign);
		}

		public boolean isMissionAvailable(final int campaign, final int mission) {
			return allMissionsOpen || this.unlocked.contains(campaign)
					|| this.progress.missions.contains(missionKey(campaign, mission));
		}

		public boolean isCampaignAvailable(final int campaign) {
			return allMissionsOpen || this.unlocked.contains(campaign) || this.progress.campaigns.contains(campaign);
		}

		public int getMenuCampaignIndex() {
			return this.progress.menuCampaignIndex;
		}

		public boolean isOpeningCinematicAvailable(final int campaign) {
			return this.unlocked.contains(campaign) || this.progress.openingCinematics.contains(campaign);
		}

		/** A mission the player has won; scripts do not tell for every campaign's last mission. */
		public boolean isMissionWon(final int campaign, final int mission) {
			return this.progress.wonMissions.contains(missionKey(campaign, mission));
		}

		/** The ordinal of the hardest difficulty a mission was won on; -1 when none is recorded (older files). */
		public int getMissionWonDifficulty(final int campaign, final int mission) {
			return this.progress.wonDifficulties.getOrDefault(missionKey(campaign, mission), -1);
		}

		/** Scripts open a campaign's ending cinematic when its last mission is won. */
		public boolean isEndingCinematicAvailable(final int campaign) {
			return this.unlocked.contains(campaign) || this.progress.endingCinematics.contains(campaign);
		}

		public int getClassicMenuCampaignIndex() {
			return this.progress.classicMenuCampaignIndex;
		}

		/**
		 * The campaign a combined campaign list opens on: the menu choice a mission made last, whichever of the two
		 * the mission's campaign makes. A file from before the order was kept has the expansion's choice, as it always
		 * had.
		 */
		public int getCombinedMenuCampaignIndex() {
			final Progress shown = this.progress;
			if ((shown.lastMenuChoice == MENU_CLASSIC) && (shown.classicMenuCampaignIndex >= 0)) {
				return shown.classicMenuCampaignIndex;
			}
			return shown.menuCampaignIndex >= 0 ? shown.menuCampaignIndex : shown.classicMenuCampaignIndex;
		}
	}

	/** What the playing profile has reached in the campaign list the game plays, as the menus read a file. */
	public static Snapshot currentSnapshot() {
		return new Snapshot(current());
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
		return allMissionsOpen || current().missions.contains(missionKey(campaign, mission));
	}

	/** The player won a campaign's mission, as the campaign screens mark it. */
	public static void setMissionWon(final int campaign, final int mission, final int difficulty) {
		final Progress progress = current();
		final long key = missionKey(campaign, mission);
		boolean changed = progress.wonMissions.add(key);
		if ((difficulty >= 0) && (difficulty > progress.wonDifficulties.getOrDefault(key, -1))) {
			progress.wonDifficulties.put(key, difficulty);
			changed = true;
		}
		if (changed) {
			save();
		}
	}

	public static void setCampaignAvailable(final int campaign, final boolean available) {
		setFlag(current().campaigns, campaign, available);
		save();
	}

	public static boolean isCampaignAvailable(final int campaign) {
		return allMissionsOpen || current().campaigns.contains(campaign);
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

	public static void setMenuCampaignIndex(final int campaignIndex) {
		final Progress progress = current();
		progress.menuCampaignIndex = campaignIndex;
		progress.lastMenuChoice = MENU_EXPANSION;
		save();
	}

	/**
	 * SetCampaignMenuRace: Reign of Chaos missions pick their campaign on the classic campaign screen by race; the
	 * expansion screen keeps its own choice (SetCampaignMenuRaceEx).
	 */
	public static void setClassicMenuCampaignIndex(final int campaignIndex) {
		final Progress progress = current();
		progress.classicMenuCampaignIndex = campaignIndex;
		progress.lastMenuChoice = MENU_CLASSIC;
		save();
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
