package com.etheller.warsmash.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.etheller.warsmash.datasources.CompoundDataSource;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.FolderDataSource;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;

/**
 * Whether game data that {@link DataFolderCheck} found holds what a game needs: the scripts, the campaign list with
 * a playable campaign map, and a melee map. Incomplete data (prepared with "Melee only", a partly unpacked zip, the
 * wrong version) is found whole by that check and then fails on every map. It only asks the data source whether
 * files exist, so it is as fast as the data source's index.
 */
public final class DataContentCheck {
	/** What the data lacks; each value names the launcher string that tells it. */
	public enum Problem {
		UNSUPPORTED_VERSION("data_content_old_version"),
		SCRIPTS("data_content_scripts"),
		REQUIRED_FILES("data_content_required"),
		NO_CAMPAIGN_LIST("data_content_no_campaigns"),
		NO_CAMPAIGN_MAPS("data_content_no_campaign_maps"),
		NO_MELEE_MAPS("data_content_no_melee_maps");

		public final String message;

		Problem(final String message) {
			this.message = message;
		}
	}

	private static final String[] SCRIPTS = { "Scripts\\common.j", "Scripts\\Blizzard.j" };
	/**
	 * Files the game and the launcher open without a fallback: the scripts, the skin list and its misc data, and the
	 * command texts. Without one of them the launcher art step or the first map fails.
	 */
	private static final String[] REQUIRED = { "Scripts\\common.j", "Scripts\\Blizzard.j", "UI\\war3skins.txt",
		"UI\\MiscData.txt", "Units\\CommandFunc.txt", "Units\\CommandStrings.txt" };
	private static final String DEFAULT_CAMPAIGN_FILE = "UI\\CampaignInfo.txt";
	/** The stock melee maps are named "(2)Name.w3x" by their player count. */
	private static final Pattern MELEE_MAP = Pattern.compile("^maps\\\\(?:[^\\\\]+\\\\)*\\(\\d+\\)[^\\\\]*\\.w3[xm]$");

	private DataContentCheck() {
	}

	/**
	 * Checks the data in the folder before any engine configuration exists. An installed game (CASC) is whole by
	 * its nature and is not opened here, only its version is read; data that cannot be read has its own message.
	 *
	 * @return the problems, none for complete data and for data that this cannot judge
	 */
	public static List<Problem> check(final DataFolderCheck.Kind kind, final File folder) {
		if (kind == DataFolderCheck.Kind.CASC) {
			return DataFolderCheck.isUnsupportedCasc(folder) ? Collections.singletonList(Problem.UNSUPPORTED_VERSION)
					: Collections.emptyList();
		}
		if (kind == DataFolderCheck.Kind.NONE) {
			return Collections.emptyList();
		}
		final List<String> kept = DataFolderCheck.cachedProblems(folder);
		if (kept != null) {
			try {
				final List<Problem> problems = new ArrayList<>();
				for (final String name : kept) {
					problems.add(Problem.valueOf(name));
				}
				return problems;
			}
			catch (final IllegalArgumentException e) {
				// A name this version does not know: the data is checked again.
			}
		}
		try {
			final List<Problem> problems = check(folderSource(kind, folder), (String) null);
			// Melee only data was prepared without campaign maps on purpose; that is not a fault of the data.
			if (preparedMeleeOnly(folder)) {
				problems.remove(Problem.NO_CAMPAIGN_MAPS);
			}
			final List<String> names = new ArrayList<>();
			for (final Problem problem : problems) {
				names.add(problem.name());
			}
			DataFolderCheck.cacheProblems(folder, names);
			return problems;
		}
		catch (final RuntimeException e) {
			Log.error(e);
			return Collections.emptyList();
		}
	}

	private static DataSource folderSource(final DataFolderCheck.Kind kind, final File folder) {
		final List<DataSource> sources = new ArrayList<>();
		sources.add(new FolderDataSource(folder.toPath()));
		final File layer = DataFolderCheck.localeLayerOf(kind, folder);
		if (layer != null) {
			sources.add(new FolderDataSource(layer.toPath()));
		}
		return new CompoundDataSource(sources);
	}

	/**
	 * The first file that the game and the launcher need and an extracted folder lacks, as the path inside the
	 * game data ("UI\\war3skins.txt"); null when all are there and for an installed game, which is not opened here.
	 */
	public static String missingRequiredFile(final DataFolderCheck.Kind kind, final File folder) {
		if ((kind != DataFolderCheck.Kind.FOLDER) && (kind != DataFolderCheck.Kind.LOCAL_FILES)) {
			return null;
		}
		try {
			return missingRequiredFile(folderSource(kind, folder));
		}
		catch (final RuntimeException e) {
			Log.error(e);
			return null;
		}
	}

	public static String missingRequiredFile(final DataSource data) {
		for (final String path : REQUIRED) {
			if (!data.has(path)) {
				return path;
			}
		}
		return null;
	}

	private static boolean preparedMeleeOnly(final File folder) {
		final File marker = new File(folder, DataPackage.MARKER);
		if (!marker.isFile()) {
			return false;
		}
		try {
			for (final String line : Files.readAllLines(marker.toPath(), StandardCharsets.UTF_8)) {
				final int equals = line.indexOf('=');
				if ((equals > 0) && "profile".equalsIgnoreCase(line.substring(0, equals).trim())) {
					return line.substring(equals + 1).trim().toLowerCase(Locale.ROOT).startsWith("melee");
				}
			}
		}
		catch (final IOException | RuntimeException e) {
			// An unreadable marker says nothing about the profile.
		}
		return false;
	}

	/** @param campaignFile the skin's CampaignFile field; null reads it from the data's skin */
	public static List<Problem> check(final DataSource data, final String campaignFile) {
		CampaignCatalog catalog = null;
		try {
			catalog = CampaignCatalog.load(data, campaignFile != null ? campaignFile : skinCampaignFile(data));
		}
		catch (final IOException | RuntimeException e) {
			Log.warn("No campaign list in the game data: " + e);
		}
		return check(data, catalog);
	}

	/** @param catalog the data's campaign list, null when it has none */
	public static List<Problem> check(final DataSource data, final CampaignCatalog catalog) {
		final List<Problem> problems = new ArrayList<>();
		for (final String script : SCRIPTS) {
			if (!data.has(script)) {
				problems.add(Problem.SCRIPTS);
				break;
			}
		}
		for (final String path : REQUIRED) {
			if (!data.has(path) && !isScript(path)) {
				problems.add(Problem.REQUIRED_FILES);
				break;
			}
		}
		if ((catalog == null) || catalog.getEntries().isEmpty()) {
			problems.add(Problem.NO_CAMPAIGN_LIST);
		}
		else if (!hasCampaignMap(data, catalog)) {
			problems.add(Problem.NO_CAMPAIGN_MAPS);
		}
		if (!hasMeleeMap(data)) {
			problems.add(Problem.NO_MELEE_MAPS);
		}
		return problems;
	}

	private static boolean isScript(final String path) {
		for (final String script : SCRIPTS) {
			if (script.equals(path)) {
				return true;
			}
		}
		return false;
	}

	private static String skinCampaignFile(final DataSource data) {
		try {
			final String file = GameUI.trySkinField(
					GameUI.loadSkin(data, WarsmashConstants.GAME_VERSION).getSkin(), "CampaignFile");
			if (!file.isEmpty()) {
				return file;
			}
		}
		catch (final RuntimeException e) {
			// A data folder without a skin file has no campaign list either; the default name is tried.
		}
		return DEFAULT_CAMPAIGN_FILE;
	}

	/** Whether one mission of the list has a map the game can open (an encrypted .w3xd or a model cinematic is none). */
	private static boolean hasCampaignMap(final DataSource data, final CampaignCatalog catalog) {
		for (final CampaignCatalog.Entry entry : catalog.getEntries()) {
			final int count = entry.getMissions(true).size();
			for (int mission = 0; mission < count; mission++) {
				for (final boolean reforged : new boolean[] { false, true }) {
					final String file = catalog.missionMapFilename(entry, mission, reforged);
					if ((file != null) && isMapName(file) && (MapIssue.ofData(data, file) == MapIssue.NONE)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean isMapName(final String file) {
		final String lower = file.toLowerCase(Locale.ROOT);
		return lower.endsWith(".w3x") || lower.endsWith(".w3m");
	}

	private static boolean hasMeleeMap(final DataSource data) {
		final Collection<String> found = data.getListfile(path -> MELEE_MAP
				.matcher(path.replace('/', '\\').toLowerCase(Locale.ROOT)).matches());
		return (found != null) && !found.isEmpty();
	}
}
