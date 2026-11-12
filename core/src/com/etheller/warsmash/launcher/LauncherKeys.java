package com.etheller.warsmash.launcher;

/** What the launcher keeps in its own store, by key, and the choices it stores. */
public final class LauncherKeys {
	public static final String PREFERENCES = "launcher";
	public static final String KEY_EDITION = "edition";
	public static final String KEY_MUSIC = "music";
	public static final String KEY_SKIN = "skin";
	public static final String KEY_ANIMATED = "animatedBackground";
	/** Whether the launcher looks for a newer engine release when it starts (on by default), and the one skipped. */
	public static final String KEY_UPDATE_CHECK = "updateCheck";
	public static final String KEY_UPDATE_SKIPPED = "updateSkipped";
	/** The desktop's in-game graphics and texture size limit; Android keeps them in the engine configuration. */
	public static final String KEY_GRAPHICS = "graphics";
	public static final String KEY_TEXTURE_SIZE = "textureSize";
	public static final String KEY_CAMPAIGN = "campaign";
	public static final String KEY_MISSION = "mission";
	public static final String KEY_DIFFICULTY = "difficulty";
	public static final String KEY_MAP = "customMap";
	public static final String KEY_MAP_FOLDER = "customFolder";
	public static final String KEY_OPTIONS = "customOptions";
	public static final String KEY_SAVE = "save";
	public static final String KEY_REPLAY = "replay";
	public static final String KEY_RETURN_PAGE = "returnPage";
	/** What the Continue Campaign banner showed last, so that it shows at once: scene, mission, campaign line. */
	public static final String KEY_BANNER = "banner";
	/** The race the player chose for the custom game started last (lobby race index), for the statistics. */
	public static final String KEY_LAUNCH_RACE = "launchRace";
	/** Set while a game started from here runs; a launcher that finds it without a result knows the game died. */
	public static final String KEY_LAUNCHED = "launched";
	/** The difficulties the engine's campaign screen offers (1.29 measured: Normal and Hard). */
	public static final String[][] DIFFICULTIES = { { "normal", "NORMAL", "Normal" }, { "hard", "HARD", "Hard" } };
	/** The campaign screen's choice of maps since 1.32; the first is the default, as there. */
	public static final String[][] CAMPAIGN_MAPS = {
		{ LaunchRequest.CAMPAIGN_MAPS_REFORGED, "CAMPAIGN_VERSION_REFORGED", "REFORGED" },
		{ LaunchRequest.CAMPAIGN_MAPS_CLASSIC, "CAMPAIGN_VERSION_CLASSIC", "CLASSIC" } };

	private LauncherKeys() {
	}
}
