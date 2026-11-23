package com.etheller.warsmash.launcher.lan;

import java.util.Locale;

import com.etheller.warsmash.launcher.LauncherStrings;
import com.etheller.warsmash.launcher.LauncherText;
import com.etheller.warsmash.launcher.MapIssue;

import net.warsmash.lan.client.MapDownload;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;

/**
 * What the LAN pages say: the lines the lobby writes, the reasons a game is refused and the dialogs' words. The words
 * are the launcher's string resources (the Android strings file, which the desktop launcher reads as it is); a
 * platform that does not have the file on its class path tells where they come from with {@link #use}.
 */
public final class LanText {
	private LanText() {
	}

	public static void use(final LauncherText.Source words) {
		LauncherText.use(words);
	}

	public static void use(final LauncherStrings words) {
		LauncherText.use(words);
	}

	private static String t(final String name, final Object... arguments) {
		return LauncherText.get(name, arguments);
	}

	public static String searchFailed() {
		return t("lan_search_failed");
	}

	public static String leaveFirst() {
		return t("lan_leave_first");
	}

	public static String createFailed(final String reason) {
		return t("lan_create_failed", reason);
	}

	public static String launchFailed() {
		return t("lan_launch_failed");
	}

	public static String slotOpen() {
		return t("lan_slot_open");
	}

	public static String slotClosed() {
		return t("lan_slot_closed");
	}

	public static String noWifi() {
		return t("lan_no_wifi");
	}

	public static String noWifiHelp() {
		return t("lan_no_wifi_help");
	}

	public static String searching() {
		return t("lan_searching");
	}

	public static String syncWarning() {
		return t("lan_sync_warning");
	}

	public static String waitingForPlayers() {
		return t("lan_waiting_for_players");
	}

	public static String mapFromHost() {
		return t("lan_map_from_host");
	}

	public static String kbmTooltip() {
		return t("lan_kbm_tooltip");
	}

	public static String blockTitle() {
		return t("lan_block_title");
	}

	public static String leaveTitle() {
		return t("lan_leave_title");
	}

	public static String joinByIp() {
		return t("lan_join_by_ip");
	}

	public static String joinByIpHint() {
		return t("lan_join_by_ip_hint");
	}

	public static String joinByIpPort() {
		return t("lan_join_by_ip_port");
	}

	public static String hostNotFoundTip() {
		return t("lan_host_not_found_tip");
	}

	public static String mapChangeFailed() {
		return t("lan_map_change_failed");
	}

	public static String changeMap() {
		return t("lan_change_map");
	}

	public static String changeMapTitle() {
		return t("lan_change_map_title");
	}

	public static String mapSize() {
		return t("lan_map_size");
	}

	public static String advancedOptions() {
		return t("lan_advanced_options");
	}

	public static String observersLabel() {
		return t("lan_observers_label");
	}

	public static String yes() {
		return t("lan_yes");
	}

	public static String no() {
		return t("lan_no");
	}

	/** The Advanced Options a game is set up with, comma separated; "none" when it has none. */
	public static String advanced(final LanData.Options options) {
		return advanced(options.lockTeams, options.teamsTogether, options.fullSharedControl, options.randomRaces);
	}

	public static String advanced(final boolean lockTeams, final boolean teamsTogether,
			final boolean fullSharedControl, final boolean randomRaces) {
		final StringBuilder text = new StringBuilder();
		final boolean[] on = { lockTeams, teamsTogether, fullSharedControl, randomRaces };
		final String[] names = { "lan_option_lock_teams", "lan_option_teams_together",
			"lan_option_full_shared_control", "lan_option_random_races" };
		for (int i = 0; i < on.length; i++) {
			if (on[i]) {
				text.append(text.length() == 0 ? "" : ", ").append(t(names[i]));
			}
		}
		return text.length() == 0 ? t("lan_advanced_none") : text.toString();
	}

	public static String speed(final int speed) {
		return speeds()[Math.max(0, Math.min(4, speed))];
	}

	public static String[] speeds() {
		return new String[] { t("lan_speed_slow"), t("lan_speed_normal"), t("lan_speed_fast"), t("lan_speed_faster"),
			t("lan_speed_fastest") };
	}

	public static String observers(final int mode) {
		return observerModes()[Math.max(0, Math.min(2, mode))];
	}

	public static String[] observerModes() {
		return new String[] { t("lan_observers_none"), t("lan_observers_observers"), t("lan_observers_referees") };
	}

	public static String computer(final int level) {
		return t("lan_computer", computerLevels()[Math.max(0, Math.min(2, level))]);
	}

	public static String[] computerLevels() {
		return new String[] { t("lan_level_easy"), t("lan_level_normal"), t("lan_level_insane") };
	}

	public static String role(final int role) {
		return new String[] { "", t("lan_role_observer"), t("lan_role_referee") }[Math.max(0, Math.min(2, role))];
	}

	/** The name a profile's game gets: "Name's Game", cut to the protocol's length. */
	public static String defaultGameName(final String profile) {
		final String name = (profile == null) || profile.trim().isEmpty() ? t("lan_default_player") : profile.trim();
		final String game = t("lan_default_game_name", name);
		return game.length() <= LanProtocol.MAX_GAME_NAME ? game : game.substring(0, LanProtocol.MAX_GAME_NAME);
	}

	public static String gameLine(final String map, final int players, final int maxPlayers) {
		return t("lan_game_line", map, players, maxPlayers);
	}

	/** The line under a game of the list when something sets it apart; empty for a plain one. */
	public static String tag(final boolean rejoin, final boolean mapFromHost, final boolean otherVersion,
			final String hostVersion) {
		if (otherVersion) {
			return hostVersion == null || hostVersion.isEmpty() ? t("lan_tag_other_version")
					: t("lan_tag_other_version_host", hostVersion);
		}
		return rejoin ? t("lan_tag_rejoin") : mapFromHost ? t("lan_tag_map_from_host") : "";
	}

	/** Why the host sent a player away, as the Join by IP dialog and the page say it. */
	public static String reject(final LanMessages.Reject reject) {
		switch (reject.reason) {
		case LanProtocol.REJECT_VERSION:
			return reject.hostVersion == null || reject.hostVersion.isEmpty() ? t("lan_reject_version")
					: t("lan_reject_version_host", reject.hostVersion);
		case LanProtocol.REJECT_DATA:
			return t("lan_reject_data");
		case LanProtocol.REJECT_FULL:
			return t("lan_reject_full");
		case LanProtocol.REJECT_STARTED:
			return t("lan_reject_started");
		case LanProtocol.REJECT_KICKED:
			return t("lan_reject_kicked");
		case LanProtocol.REJECT_BAD_NAME:
			return t("lan_reject_bad_name");
		default:
			return reject.text == null || reject.text.isEmpty() ? t("lan_reject_other") : reject.text;
		}
	}

	public static String connectFailed() {
		return t("lan_connect_failed");
	}

	public static String badAddress() {
		return t("lan_bad_address");
	}

	public static String kicked() {
		return t("lan_reject_kicked");
	}

	public static String lostHost() {
		return t("lan_lost_host");
	}

	public static String mapFailed(final int failure) {
		switch (failure) {
		case MapDownload.FAIL_HASH:
		case MapDownload.FAIL_SIZE:
			return t("lan_map_failed_hash");
		case MapDownload.FAIL_BAD_HEADER:
		case MapDownload.FAIL_BAD_INFO:
			return t("lan_map_failed_header");
		case MapDownload.FAIL_TIMEOUT:
			return t("lan_map_failed_timeout");
		default:
			return t("lan_map_failed_other");
		}
	}

	/** Why the engine cannot play a map, in the words the Custom Game page uses. */
	public static String mapIssue(final MapIssue issue) {
		return issue.message == null ? "" : t(issue.message);
	}

	public static String mapIssueTitle() {
		return t("lan_map_unplayable_title");
	}

	public static String mapFailedTitle() {
		return t("lan_map_failed_title");
	}

	/** "Map from host · 63 % · 1.9 / 3.1 MB" */
	public static String mapProgress(final long done, final int total) {
		final int percent = total <= 0 ? 0 : (int) Math.min(100, ((done * 100) + (total / 2)) / total);
		return t("lan_map_progress", mapFromHost(), percent, megabytes(done), megabytes(total));
	}

	public static String sending(final int percent) {
		return t("lan_sending", percent);
	}

	private static String megabytes(final long bytes) {
		return String.format(Locale.ROOT, "%.1f", bytes / (1024f * 1024f));
	}

	public static String joined(final String name) {
		return t("lan_joined", name);
	}

	public static String left(final String name) {
		return t("lan_left", name);
	}

	public static String newHost(final String left, final String now) {
		return t("lan_new_host", left, now);
	}

	public static String countdown(final int seconds) {
		return t("lan_countdown", seconds);
	}

	public static String countdownCancelled() {
		return t("lan_countdown_cancelled");
	}

	public static String downloading() {
		return t("lan_downloading");
	}

	public static String mapReceived() {
		return t("lan_map_received");
	}

	public static String controlSchemeNow(final String name, final boolean keyboardAndMouse) {
		return t(keyboardAndMouse ? "lan_control_kbm" : "lan_control_touch", name);
	}

	public static String leaveConfirm(final String nextHost) {
		return nextHost == null ? t("lan_leave_closes") : t("lan_leave_hands_over", nextHost);
	}

	public static String blockText(final boolean hosting) {
		return hosting ? t("lan_block_hosting") : t("lan_block_in_lobby");
	}

	/**
	 * The loading screen's per-player state in words: the percent while loading, "OK" once loaded (a screen that can
	 * draw a mark draws it instead).
	 */
	public static String loadState(final int percent) {
		return percent >= 100 ? t("lan_load_ready") : t("lan_load_percent", percent);
	}

	public static String readyCount(final int ready, final int humans) {
		return t("lan_ready_count", ready, humans);
	}

	public static String lobbyTitle(final String game, final String map) {
		return t("lan_lobby_title", game, map);
	}

	public static String rejoining(final int percent, final String caughtUp) {
		return t("lan_rejoining", percent, caughtUp);
	}

	public static String outOfSync(final int tick) {
		return t("lan_out_of_sync", tick);
	}

	public static String missedTurns() {
		return t("lan_missed_turns");
	}

	public static String cannotSync() {
		return t("lan_cannot_sync");
	}
}
