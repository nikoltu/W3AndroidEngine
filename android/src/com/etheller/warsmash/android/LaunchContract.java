package com.etheller.warsmash.android;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;


import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.etheller.warsmash.launcher.GameResult;
import com.etheller.warsmash.launcher.LaunchRequest;

/**
 * What the launcher and the engine tell each other: the Intent extras that start a game without the engine's menu,
 * and the result file the engine leaves when it gives the screen back. A game started from the launcher carries a
 * one-time token, so that Android restoring the engine's activity later does not start the game again by itself.
 */
final class LaunchContract {
	private static final String TAG = "Warsmash";
	/** Campaign number as scripts count campaigns (the named entries of the campaign list). */
	static final String EXTRA_CAMPAIGN = "campaign";
	static final String EXTRA_MISSION = "mission";
	/** easy, normal or hard. */
	static final String EXTRA_DIFFICULTY = "difficulty";
	/** Which version of the campaign maps is played: {@link #CAMPAIGN_MAPS_REFORGED} or classic (the default). */
	static final String EXTRA_CAMPAIGN_MAPS = "campaignmaps";
	static final String CAMPAIGN_MAPS_REFORGED = LaunchRequest.CAMPAIGN_MAPS_REFORGED;
	static final String CAMPAIGN_MAPS_CLASSIC = LaunchRequest.CAMPAIGN_MAPS_CLASSIC;
	/** A map of the custom game list, played with {@link #EXTRA_SLOTS} and {@link #EXTRA_OPTIONS}. */
	static final String EXTRA_CUSTOM_MAP = "custommap";
	static final String EXTRA_SLOTS = "slots";
	static final String EXTRA_OPTIONS = "options";
	/** A saved game of the current profile, by its name without the extension. */
	static final String EXTRA_SAVE_FILE = "savefile";
	/** A replay of the Replay folder, by its full path. */
	static final String EXTRA_REPLAY_FILE = "replayfile";
	/** The replay is played although this engine would refuse it. */
	static final String EXTRA_REPLAY_FORCED = "replayforced";
	/** The map the game starts on, whose loading screen the engine shows from its first frame on. */
	static final String EXTRA_MAP_FILE = "mapfile";
	/** The launcher's skin: {@link #SKIN_REFORGED} shows the Reforged loading screens of maps. */
	static final String EXTRA_SKIN = "skin";
	static final String SKIN_REFORGED = LaunchRequest.SKIN_REFORGED;
	/** The profile's Reforged portrait, from 1, which the Reforged loading screens show for the local player. */
	static final String EXTRA_PORTRAIT = "portrait";
	/** The player profile the game is played with, by its name. */
	static final String EXTRA_PROFILE = "profile";
	static final String EXTRA_TOKEN = "launchToken";
	/** Given to the launcher when a game returns to it. */
	static final String EXTRA_RETURNED = "returned";

	static final String RESULT_FILE = "launcher-result.json";
	private static final String TOKEN_FILE = "launch-token";

	private LaunchContract() {
	}

	/** The game of a request in the extras of the engine's activity. */
	static void put(final Intent intent, final LaunchRequest request) {
		if (request.campaign >= 0) {
			intent.putExtra(EXTRA_CAMPAIGN, request.campaign);
			intent.putExtra(EXTRA_MISSION, request.mission);
		}
		putString(intent, EXTRA_DIFFICULTY, request.difficulty);
		putString(intent, EXTRA_CAMPAIGN_MAPS, request.campaignMaps);
		putString(intent, EXTRA_CUSTOM_MAP, request.customMap);
		putString(intent, EXTRA_SLOTS, request.slots);
		putString(intent, EXTRA_OPTIONS, request.options);
		putString(intent, EXTRA_SAVE_FILE, request.saveFile);
		putString(intent, EXTRA_REPLAY_FILE, request.replayFile);
		if (request.replayForced) {
			intent.putExtra(EXTRA_REPLAY_FORCED, true);
		}
		putString(intent, EXTRA_MAP_FILE, request.mapFile);
		putString(intent, EXTRA_SKIN, request.skin);
		if (request.portrait > 0) {
			intent.putExtra(EXTRA_PORTRAIT, request.portrait);
		}
		putString(intent, EXTRA_PROFILE, request.profile);
	}

	private static void putString(final Intent intent, final String key, final String value) {
		if (value != null) {
			intent.putExtra(key, value);
		}
	}

	/** The game the extras of the engine's activity ask for. */
	static LaunchRequest request(final Intent intent) {
		final LaunchRequest request = new LaunchRequest();
		request.campaign = intent.hasExtra(EXTRA_CAMPAIGN) ? intent.getIntExtra(EXTRA_CAMPAIGN, -1) : -1;
		request.mission = intent.getIntExtra(EXTRA_MISSION, -1);
		request.difficulty = intent.getStringExtra(EXTRA_DIFFICULTY);
		request.campaignMaps = intent.getStringExtra(EXTRA_CAMPAIGN_MAPS);
		request.customMap = intent.getStringExtra(EXTRA_CUSTOM_MAP);
		request.slots = intent.getStringExtra(EXTRA_SLOTS);
		request.options = intent.getStringExtra(EXTRA_OPTIONS);
		request.saveFile = intent.getStringExtra(EXTRA_SAVE_FILE);
		request.replayFile = intent.getStringExtra(EXTRA_REPLAY_FILE);
		request.replayForced = intent.getBooleanExtra(EXTRA_REPLAY_FORCED, false);
		request.mapFile = intent.getStringExtra(EXTRA_MAP_FILE);
		request.skin = intent.getStringExtra(EXTRA_SKIN);
		request.portrait = intent.getIntExtra(EXTRA_PORTRAIT, 0);
		request.profile = intent.getStringExtra(EXTRA_PROFILE);
		return request;
	}

	static String newToken(final Context context) {
		final byte[] bytes = new byte[12];
		new SecureRandom().nextBytes(bytes);
		final StringBuilder token = new StringBuilder();
		for (final byte b : bytes) {
			token.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
		}
		try {
			writeAtomically(new File(context.getFilesDir(), TOKEN_FILE), token.toString());
		}
		catch (final IOException e) {
			Log.e(TAG, "Unable to store the launch token", e);
		}
		return token.toString();
	}

	/** @return whether the token is the one the launcher gave out last; it cannot be used again */
	static boolean consumeToken(final Context context, final String token) {
		final File file = new File(context.getFilesDir(), TOKEN_FILE);
		if ((token == null) || !file.isFile()) {
			return false;
		}
		try {
			final String stored = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
			return token.equals(stored);
		}
		catch (final IOException e) {
			return false;
		}
		finally {
			file.delete();
		}
	}

	static void writeResult(final Context context, final GameResult result) {
		try {
			writeAtomically(new File(context.getFilesDir(), RESULT_FILE), result.toJson());
		}
		catch (final IOException e) {
			Log.e(TAG, "Unable to write the game result", e);
		}
	}

	static boolean hasResult(final Context context) {
		return new File(context.getFilesDir(), RESULT_FILE).isFile();
	}

	/** Reads and removes the result file; null when there is none or it cannot be read. */
	static GameResult takeResult(final Context context) {
		final File file = new File(context.getFilesDir(), RESULT_FILE);
		if (!file.isFile()) {
			return null;
		}
		try {
			return GameResult.fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
		}
		catch (final IOException | IllegalArgumentException e) {
			Log.w(TAG, "Unable to read the game result", e);
			return null;
		}
		finally {
			file.delete();
		}
	}

	/**
	 * Whether the program ended by a crash after the given time, when a game left no result. A force stop, a swipe from
	 * the recent apps or the system reclaiming memory leave no result either, and are no crash; Android tells them
	 * apart from 11 on. Before that only a crash the engine caught itself (a crash result) can tell.
	 */
	static boolean crashedSince(final Context context, final long since) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			return false;
		}
		final ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		if (manager == null) {
			return false;
		}
		try {
			final List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(null, 0, 1);
			if (exits.isEmpty() || (exits.get(0).getTimestamp() < since)) {
				return false;
			}
			switch (exits.get(0).getReason()) {
			case ApplicationExitInfo.REASON_CRASH:
			case ApplicationExitInfo.REASON_CRASH_NATIVE:
			case ApplicationExitInfo.REASON_ANR:
			case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
				return true;
			default:
				return false;
			}
		}
		catch (final RuntimeException e) {
			Log.w(TAG, "Unable to read how the game ended", e);
			return false;
		}
	}

	private static void writeAtomically(final File file, final String text) throws IOException {
		final File partial = new File(file.getParentFile(), file.getName() + ".part");
		Files.write(partial.toPath(), text.getBytes(StandardCharsets.UTF_8));
		if (!partial.renameTo(file)) {
			throw new IOException("Unable to store " + file);
		}
	}
}
