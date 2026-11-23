package com.etheller.warsmash.viewer5.handlers.w3x.ui.menu;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntFunction;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.StringBundle;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;

/**
 * The campaigns of the game data as the campaign screen lists them, read from the skin's campaign file or, when the
 * data has none, from the combined list of later versions.
 */
public final class CampaignCatalog {
	private static final String COMBINED_FILE = "UI\\CampaignInfoClassic.txt";
	/**
	 * Since 1.32 the campaigns also come as the remade maps of Reforged, listed apart with the same campaigns and
	 * missions; the game's campaign screen chooses between the two versions.
	 */
	private static final String REFORGED_FILE = "UI\\CampaignInfoReforged.txt";
	/**
	 * The first maps of the two Forsaken Kingdom campaigns, which only Reforged 3.0 brought; no campaign file of the
	 * data lists them, the game's web menu does.
	 */
	private static final String[] FORSAKEN_KINGDOM_MAPS = { "Campaign\\ForsakenKingdom\\HumanRe01.w3xd",
		"Campaign\\ForsakenKingdom\\UndeadRe01.w3xd", "Campaign\\ForsakenKingdom\\HumanRe01.w3x",
		"Campaign\\ForsakenKingdom\\UndeadRe01.w3x" };

	/** A campaign with the number scripts know it by. */
	public static final class Entry {
		private final CampaignMenuData data;
		private final int number;
		private final String section;
		private List<CampaignMission> reforgedMissions;

		private Entry(final CampaignMenuData data, final int number, final String section) {
			this.data = data;
			this.number = number;
			this.section = section;
			this.reforgedMissions = data.getMissions();
		}

		/**
		 * The missions of a version of the campaign maps. The Reforged list may go on past the classic one (its
		 * prologue has three chapters more); the missions both lists have are the same, so the classic missions are
		 * always the first ones of the Reforged list.
		 */
		public List<CampaignMission> getMissions(final boolean reforged) {
			return reforged ? this.reforgedMissions : this.data.getMissions();
		}

		public CampaignMenuData getData() {
			return this.data;
		}

		public int getNumber() {
			return this.number;
		}

		public String getSection() {
			return this.section;
		}
	}

	private final DataTable strings;
	private final CCampaignProgress.CampaignList list;
	private final boolean classicList;
	private final boolean unified;
	private final String[] campaignList;
	private final CampaignMenuData[] datas;
	private final List<Entry> entries;
	private DataSource dataSource;
	private DataTable reforgedStrings;
	private boolean forsakenKingdom;

	private CampaignCatalog(final DataTable strings, final CCampaignProgress.CampaignList list,
			final boolean classicList, final boolean unified) {
		this.strings = strings;
		this.list = list;
		this.classicList = classicList;
		this.unified = unified;
		final Element index = strings.get("Index");
		if (index == null) {
			this.campaignList = new String[0];
			this.datas = new CampaignMenuData[0];
			this.entries = Collections.emptyList();
			return;
		}
		// Read entry by entry: the joined field string drops the blank entries at the start of the list.
		this.campaignList = index.getFieldAsList("CampaignList").toArray(new String[0]);
		this.datas = new CampaignMenuData[this.campaignList.length];
		final List<Entry> named = new ArrayList<>();
		for (int i = 0; i < this.campaignList.length; i++) {
			final Element element = strings.get(this.campaignList[i]);
			if (element != null) {
				this.datas[i] = new CampaignMenuData(element);
				// Scripts number the campaigns by their place among the list's named entries (SetMissionAvailable,
				// SetCampaignAvailable); a blank entry is a gap between groups of campaigns on the screen.
				named.add(new Entry(this.datas[i], named.size(), this.campaignList[i]));
			}
		}
		this.entries = Collections.unmodifiableList(named);
	}

	/**
	 * The campaign file the skin names for a game version (0 Reign of Chaos, 1 The Frozen Throne): the data of these
	 * versions list their campaigns apart. When the data lacks that version's file the other version's is taken, and
	 * data with neither (the combined list of later versions) keeps the field as it is.
	 *
	 * @param skinField the skin's CampaignFile field for a game version
	 */
	public static String campaignFile(final DataSource dataSource, final IntFunction<String> skinField,
			final int version) {
		final String file = skinField.apply(version);
		if (dataSource.has(file)) {
			return file;
		}
		final String other = skinField.apply(1 - version);
		return dataSource.has(other) ? other : file;
	}

	/**
	 * @param campaignFile the skin's CampaignFile field
	 */
	public static CampaignCatalog load(final DataSource dataSource, final String campaignFile) throws IOException {
		final DataTable strings = new DataTable(StringBundle.EMPTY);
		if (dataSource.has(campaignFile)) {
			try (InputStream stream = dataSource.getResourceAsStream(campaignFile)) {
				strings.readTXT(stream, true);
			}
			final boolean classicList = !campaignFile.toLowerCase(Locale.ROOT).contains("_exp");
			return new CampaignCatalog(strings, classicList ? CCampaignProgress.CampaignList.REIGN_OF_CHAOS
					: CCampaignProgress.CampaignList.FROZEN_THRONE, classicList, false).withReforged(dataSource);
		}
		try (InputStream stream = dataSource.getResourceAsStream(COMBINED_FILE)) {
			strings.readTXT(stream, true);
		}
		return new CampaignCatalog(strings, CCampaignProgress.CampaignList.COMBINED, false, true)
				.withReforged(dataSource);
	}

	private CampaignCatalog withReforged(final DataSource dataSource) throws IOException {
		this.dataSource = dataSource;
		this.forsakenKingdom = hasForsakenKingdom(dataSource);
		if (dataSource.has(REFORGED_FILE)) {
			this.reforgedStrings = new DataTable(StringBundle.EMPTY);
			try (InputStream stream = dataSource.getResourceAsStream(REFORGED_FILE)) {
				this.reforgedStrings.readTXT(stream, true);
			}
			for (final Entry entry : this.entries) {
				final Element element = this.reforgedStrings.get(entry.section);
				if (element == null) {
					continue;
				}
				final List<CampaignMission> classic = entry.data.getMissions();
				final List<CampaignMission> remade = new CampaignMenuData(element).getMissions();
				if (remade.size() > classic.size()) {
					final List<CampaignMission> missions = new ArrayList<>(classic);
					missions.addAll(remade.subList(classic.size(), remade.size()));
					entry.reforgedMissions = Collections.unmodifiableList(missions);
				}
			}
		}
		return this;
	}

	/** Whether the game data has the Forsaken Kingdom campaigns. */
	public static boolean hasForsakenKingdom(final DataSource dataSource) {
		for (final String map : FORSAKEN_KINGDOM_MAPS) {
			if (dataSource.has(map)) {
				return true;
			}
		}
		return false;
	}

	public boolean hasForsakenKingdom() {
		return this.forsakenKingdom;
	}

	/** Whether the data has the Reforged version of the campaign maps. */
	public boolean hasReforgedMaps() {
		return this.reforgedStrings != null;
	}

	/**
	 * The map a mission is played on: with reforged, the Reforged version of the mission when the data has its map,
	 * else the one the campaign list names; null for no such mission in that version.
	 */
	public String missionMapFilename(final Entry entry, final int mission, final boolean reforged) {
		final List<CampaignMission> missions = entry.getMissions(reforged);
		if ((mission < 0) || (mission >= missions.size())) {
			return null;
		}
		if (reforged && (this.reforgedStrings != null)) {
			final Element element = this.reforgedStrings.get(entry.section);
			if (element != null) {
				final String remade = element.getField("Mission" + mission, 2);
				if (!remade.isEmpty() && this.dataSource.has(remade)) {
					return remade;
				}
			}
		}
		return missions.get(mission).getMapFilename();
	}

	public DataTable getStrings() {
		return this.strings;
	}

	public CCampaignProgress.CampaignList getList() {
		return this.list;
	}

	/** Reign of Chaos alone: its missions pick the campaign the menu shows by race (SetCampaignMenuRace). */
	public boolean isClassicList() {
		return this.classicList;
	}

	public boolean isUnified() {
		return this.unified;
	}

	/** The list as the file gives it, blank entries included. */
	public String[] getCampaignList() {
		return this.campaignList;
	}

	/** One per entry of {@link #getCampaignList()}, null for the blank ones. */
	public CampaignMenuData[] getDatas() {
		return this.datas;
	}

	public List<Entry> getEntries() {
		return this.entries;
	}

	/** The numbers scripts know these campaigns by, for the campaigns of this list among the section names given. */
	public Set<Integer> numbersOf(final Set<String> sections) {
		final Set<Integer> numbers = new HashSet<>();
		for (final Entry entry : this.entries) {
			if (sections.contains(entry.section)) {
				numbers.add(entry.number);
			}
		}
		return numbers;
	}

	public Entry getEntry(final int number) {
		return (number >= 0) && (number < this.entries.size()) ? this.entries.get(number) : null;
	}

	/** The campaign screens list a campaign that is open by default or made available by a script. */
	public static boolean isOpen(final Entry entry, final CCampaignProgress.Snapshot progress) {
		return entry.data.isDefaultOpen() || progress.isCampaignAvailable(entry.number);
	}

	/** In an open campaign the first mission and those a script made available. */
	public static boolean isMissionOpen(final Entry entry, final int mission, final CCampaignProgress.Snapshot progress) {
		return isOpen(entry, progress) && ((mission == 0) || progress.isMissionAvailable(entry.number, mission));
	}

	/**
	 * Whether the campaign screen lists a mission of a version of the campaign maps: it is among that version's
	 * missions and its campaign is open, the first mission and those a script made available show.
	 *
	 * @param opened the player opened the campaign by hand, which lists every mission of it
	 */
	public static boolean isMissionListed(final Entry entry, final int mission, final boolean reforged,
			final boolean opened, final CCampaignProgress.Snapshot progress) {
		return (mission >= 0) && (mission < entry.getMissions(reforged).size())
				&& (opened || isMissionOpen(entry, mission, progress));
	}

	/**
	 * A mission the player has won: recorded at its victory, else known from what the victory opened, the next mission
	 * or, after the last one, the campaign's ending cinematic or the campaign after it in the list.
	 */
	public static boolean isMissionWon(final Entry entry, final int mission, final CCampaignProgress.Snapshot shown) {
		return isMissionWon(entry, mission, shown, false);
	}

	/** Whether a mission is won, in the mission list of a version of the campaign maps. */
	public static boolean isMissionWon(final Entry entry, final int mission, final CCampaignProgress.Snapshot shown,
			final boolean reforged) {
		// A campaign opened by hand is not a campaign won.
		final CCampaignProgress.Snapshot progress = shown.real();
		if (progress.isMissionWon(entry.number, mission)) {
			return true;
		}
		final int last = entry.getMissions(reforged).size() - 1;
		if (mission < last) {
			return isMissionOpen(entry, mission + 1, progress);
		}
		// Victories were not always recorded (older profiles, missions started from the engine's own menu). A last
		// mission without an ending cinematic tells its victory only by opening the next campaign, which a script
		// records even when that campaign is open by default; the mission itself must have been reached in this
		// version, as the other version's list may end earlier.
		return (mission == last) && (progress.isEndingCinematicAvailable(entry.number)
				|| (progress.isCampaignAvailable(entry.number + 1) && isMissionOpen(entry, mission, progress)));
	}

	/** Whether the player has won the campaign's last mission. */
	public static boolean isCompleted(final Entry entry, final CCampaignProgress.Snapshot progress) {
		return isCompleted(entry, progress, false);
	}

	/** Whether the player has won the last mission of a version of the campaign maps. */
	public static boolean isCompleted(final Entry entry, final CCampaignProgress.Snapshot progress,
			final boolean reforged) {
		return isMissionWon(entry, entry.getMissions(reforged).size() - 1, progress, reforged);
	}

	/**
	 * The campaign the menu opens on: the one the last mission chose, else none (the caller keeps its own). Reign of
	 * Chaos missions choose by race and the expansion's by number; a combined list holds both kinds of campaigns, so
	 * the choice of the mission played last counts, whichever campaign it was.
	 */
	public int menuCampaignNumber(final CCampaignProgress.Snapshot progress) {
		if (this.unified) {
			return progress.getCombinedMenuCampaignIndex();
		}
		return this.classicList ? progress.getClassicMenuCampaignIndex() : progress.getMenuCampaignIndex();
	}
}
