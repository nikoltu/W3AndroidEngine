package com.etheller.warsmash.launcher;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.w3x.War3Map;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMission;

/**
 * Why a map listed by the launcher cannot be played by this engine, found before the game is started so that the
 * launcher can say so in a few words. Each value names the launcher string that tells it.
 */
public enum MapIssue {
	NONE(null, null, null),
	/** The map's script is Lua; the engine runs JASS only. */
	LUA("map_issue_lua", "map_tag_lua", "This map uses a Lua script, which is not supported yet."),
	/** The map carries no script of either kind. */
	NO_SCRIPT("map_issue_no_script", "map_tag_no_script", "This map has no script and cannot be played."),
	/** The archive is encrypted (Forsaken Kingdom's .w3xd files). */
	PROTECTED("map_issue_protected", "map_tag_protected", "This map is protected and cannot be opened."),
	/** A campaign names a map the game data does not have. */
	MISSING("map_issue_missing", "map_tag_missing", "Map not in the game data");

	/** The string of the full sentence. */
	public final String message;
	/** The string of the few words that stand beside the map in a list. */
	public final String tag;

	/** The sentence in English, for the engine's own dialogs and logs, which have no launcher strings. */
	public final String english;

	MapIssue(final String message, final String tag, final String english) {
		this.message = message;
		this.tag = tag;
		this.english = english;
	}

	/** The issue a failed map start was about: the one an {@link MapIssueException} carries, else NONE. */
	public static MapIssue ofFailure(final Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
			if (cause instanceof MapIssueException) {
				return ((MapIssueException) cause).issue();
			}
		}
		return NONE;
	}

	/** What the file name alone tells: encrypted map archives have their own extension. */
	public static MapIssue ofName(final String path) {
		return (path != null) && path.toLowerCase(Locale.ROOT).endsWith(".w3xd") ? PROTECTED : NONE;
	}

	/** The issue of an opened map's script. */
	public static MapIssue of(final War3Map map) {
		if (map.hasJassScript()) {
			return NONE;
		}
		return map.hasLuaScript() ? LUA : NO_SCRIPT;
	}

	/** The issue of a map named by a campaign list, without opening it more than the data needs. */
	public static MapIssue ofData(final DataSource data, final String path) {
		if ((path == null) || path.isEmpty()) {
			return MISSING;
		}
		// A mission entry that names a model is an in-engine cinematic, not a map; it is not checked here.
		if (CampaignMission.isModelEntry(path)) {
			return NONE;
		}
		if (!data.has(path)) {
			return MISSING;
		}
		return ofName(path);
	}

	/**
	 * The issue of a map about to be started, by its name and then by the script of the archive. A map that cannot be
	 * found or opened is {@link #NONE} here: the engine's own start tells why.
	 */
	public static MapIssue ofFile(final DataSource data, final String path) {
		return check(data, path, false);
	}

	/**
	 * Like {@link #ofFile}, but a map that is neither in the game data nor a file is {@link #MISSING}: for a replay or
	 * a save, which name the map they were made on.
	 */
	public static MapIssue ofRecordedMap(final DataSource data, final String path) {
		return check(data, path, true);
	}

	private static MapIssue check(final DataSource data, final String path, final boolean missingCounts) {
		if ((path == null) || path.isEmpty() || CampaignMission.isModelEntry(path)) {
			return NONE;
		}
		final MapIssue named = ofName(path);
		if (named != NONE) {
			return named;
		}
		if (missingCounts && !MapFiles.isAbsolute(path) && !data.has(path) && !new File(path).exists()) {
			return MISSING;
		}
		final War3Map map;
		try {
			map = War3MapViewer.beginLoadingMap(data, path);
		}
		catch (final IOException | RuntimeException e) {
			return missingCounts && (e instanceof IllegalArgumentException) ? MISSING : NONE;
		}
		try {
			return of(map);
		}
		finally {
			try {
				map.close();
			}
			catch (final IOException e) {
				LauncherLog.w("Unable to close " + path, e);
			}
		}
	}
}
