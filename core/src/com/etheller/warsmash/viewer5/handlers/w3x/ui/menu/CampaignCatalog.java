package com.etheller.warsmash.viewer5.handlers.w3x.ui.menu;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

		private Entry(final CampaignMenuData data, final int number, final String section) {
			this.data = data;
			this.number = number;
			this.section = section;
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
	 * @param campaignFile the skin's CampaignFile field
	 */
	public static CampaignCatalog load(final DataSource dataSource, final String campaignFile) throws IOException {
		final DataTable strings = new DataTable(StringBundle.EMPTY);
		if (dataSource.has(campaignFile)) {
			try (InputStream stream = dataSource.getResourceAsStream(campaignFile)) {
				strings.readTXT(stream, true);
			}
			final boolean classicList = !campaignFile.toLowerCase().contains("_exp");
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
	 * else the one the campaign list names; null for no such mission.
	 */
	public String missionMapFilename(final Entry entry, final int mission, final boolean reforged) {
		final List<CampaignMission> missions = entry.data.getMissions();
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
	 * A mission the player has won: recorded at its victory, else known from what the victory opened, the next mission
	 * or, after the last one, the campaign's ending cinematic.
	 */
	public static boolean isMissionWon(final Entry entry, final int mission, final CCampaignProgress.Snapshot progress) {
		if (progress.isMissionWon(entry.number, mission)) {
			return true;
		}
		final int last = entry.data.getMissions().size() - 1;
		return mission < last ? isMissionOpen(entry, mission + 1, progress)
				: (mission == last) && progress.isEndingCinematicAvailable(entry.number);
	}

	/** Whether the player has won the campaign's last mission. */
	public static boolean isCompleted(final Entry entry, final CCampaignProgress.Snapshot progress) {
		return isMissionWon(entry, entry.data.getMissions().size() - 1, progress);
	}

	/** The campaign the menu opens on: the one the last mission chose, else none (the caller keeps its own). */
	public int menuCampaignNumber(final CCampaignProgress.Snapshot progress) {
		return this.classicList ? progress.getClassicMenuCampaignIndex() : progress.getMenuCampaignIndex();
	}
}
