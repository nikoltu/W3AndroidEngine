package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import java.util.HashSet;
import java.util.Set;

/**
 * Campaign state scripts change as missions end: what the campaign menu shows and which map comes next. Like the game
 * cache it lives as long as the process and is not saved to a profile yet (D-100).
 */
public final class CCampaignProgress {
	private static final Set<Long> MISSIONS = new HashSet<>();
	private static final Set<Integer> CAMPAIGNS = new HashSet<>();
	private static final Set<Integer> OPENING_CINEMATICS = new HashSet<>();
	private static final Set<Integer> ENDING_CINEMATICS = new HashSet<>();
	private static boolean tutorialCleared;
	private static int menuCampaignIndex = -1;
	private static boolean campaignSelectScreenForced;
	private static String pendingLevel;

	private CCampaignProgress() {
	}

	public static void setMissionAvailable(final int campaign, final int mission, final boolean available) {
		final long key = (((long) campaign) << 32) | (mission & 0xFFFFFFFFL);
		if (available) {
			MISSIONS.add(key);
		}
		else {
			MISSIONS.remove(key);
		}
	}

	public static boolean isMissionAvailable(final int campaign, final int mission) {
		return MISSIONS.contains((((long) campaign) << 32) | (mission & 0xFFFFFFFFL));
	}

	public static void setCampaignAvailable(final int campaign, final boolean available) {
		setFlag(CAMPAIGNS, campaign, available);
	}

	public static boolean isCampaignAvailable(final int campaign) {
		return CAMPAIGNS.contains(campaign);
	}

	public static void setOpeningCinematicAvailable(final int campaign, final boolean available) {
		setFlag(OPENING_CINEMATICS, campaign, available);
	}

	public static void setEndingCinematicAvailable(final int campaign, final boolean available) {
		setFlag(ENDING_CINEMATICS, campaign, available);
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
		tutorialCleared = cleared;
	}

	public static boolean isTutorialCleared() {
		return tutorialCleared;
	}

	public static void setMenuCampaignIndex(final int campaignIndex) {
		menuCampaignIndex = campaignIndex;
	}

	public static int getMenuCampaignIndex() {
		return menuCampaignIndex;
	}

	public static void forceCampaignSelectScreen() {
		campaignSelectScreenForced = true;
	}

	public static boolean consumeCampaignSelectScreenForced() {
		final boolean forced = campaignSelectScreenForced;
		campaignSelectScreenForced = false;
		return forced;
	}

	public static void setPendingLevel(final String levelFile) {
		pendingLevel = levelFile;
	}

	public static String consumePendingLevel() {
		final String level = pendingLevel;
		pendingLevel = null;
		return level;
	}
}
