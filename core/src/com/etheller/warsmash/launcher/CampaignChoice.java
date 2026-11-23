package com.etheller.warsmash.launcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMission;

/**
 * The launcher's campaign choices: which campaign and mission it offers, and the scene and pictures shown with them.
 */
public final class CampaignChoice {
	private CampaignChoice() {
	}

	public static final int SECTION_REIGN = 0;
	public static final int SECTION_FROZEN = 1;
	public static final int SECTION_FORSAKEN = 2;
	/** The campaigns of Forsaken Kingdom (Reforged 3.0), which the engine cannot play yet. */
	public static final int FORSAKEN_CAMPAIGNS = 2;

	/** A line of the campaign list: a gap, a section's header, an open campaign or a campaign still to come. */
	public static final class ListLine {
		public static final int GAP = 0;
		public static final int SECTION = 1;
		public static final int CAMPAIGN = 2;
		public static final int COMING = 3;

		public final int kind;
		/** The section of a header; the place among {@link #FORSAKEN_CAMPAIGNS} of a campaign to come. */
		public final int index;
		public final CampaignCatalog.Entry entry;

		private ListLine(final int kind, final int index, final CampaignCatalog.Entry entry) {
			this.kind = kind;
			this.index = index;
			this.entry = entry;
		}
	}

	/** The section of a campaign: the expansion's campaigns are named with "Ex". */
	public static int section(final CampaignCatalog.Entry entry) {
		return entry.getSection().toLowerCase(Locale.ROOT).endsWith("ex") ? SECTION_FROZEN : SECTION_REIGN;
	}

	/**
	 * The campaign list in sections, each under its header: Reign of Chaos, The Frozen Throne, the gaps of the data's
	 * list kept within them; then, when the data has its campaigns, Forsaken Kingdom still to come.
	 */
	public static List<ListLine> campaignList(final CampaignCatalog catalog,
			final CCampaignProgress.Snapshot progress) {
		final List<ListLine> lines = new ArrayList<>();
		boolean gap = true;
		int section = -1;
		int number = 0;
		for (final Object data : catalog.getDatas()) {
			if (data == null) {
				if (!gap) {
					lines.add(new ListLine(ListLine.GAP, 0, null));
					gap = true;
				}
				continue;
			}
			final CampaignCatalog.Entry entry = catalog.getEntry(number++);
			if (!CampaignCatalog.isOpen(entry, progress)) {
				continue;
			}
			if (section(entry) != section) {
				section = section(entry);
				if (!gap) {
					lines.add(new ListLine(ListLine.GAP, 0, null));
				}
				lines.add(new ListLine(ListLine.SECTION, section, null));
			}
			lines.add(new ListLine(ListLine.CAMPAIGN, 0, entry));
			gap = false;
		}
		if (lines.isEmpty()) {
			return lines;
		}
		if (catalog.hasForsakenKingdom()) {
			if (!gap) {
				lines.add(new ListLine(ListLine.GAP, 0, null));
			}
			lines.add(new ListLine(ListLine.SECTION, SECTION_FORSAKEN, null));
			for (int i = 0; i < FORSAKEN_CAMPAIGNS; i++) {
				lines.add(new ListLine(ListLine.COMING, i, null));
			}
		}
		else if (gap) {
			lines.remove(lines.size() - 1);
		}
		return lines;
	}

	/**
	 * The campaign the progress last chose; else the last one a mission opened or got into; else the first open one.
	 */
	public static CampaignCatalog.Entry playedCampaign(final CampaignCatalog catalog,
			final CCampaignProgress.Snapshot progress) {
		final CampaignCatalog.Entry chosen = catalog.getEntry(catalog.menuCampaignNumber(progress));
		if ((chosen != null) && CampaignCatalog.isOpen(chosen, progress)) {
			return chosen;
		}
		CampaignCatalog.Entry played = null;
		CampaignCatalog.Entry first = null;
		for (final CampaignCatalog.Entry entry : catalog.getEntries()) {
			if (!CampaignCatalog.isOpen(entry, progress)) {
				continue;
			}
			if (first == null) {
				first = entry;
			}
			if (started(entry, progress)) {
				played = entry;
			}
		}
		return played != null ? played : first;
	}

	/** A campaign a script opened, or one whose second mission is open. */
	public static boolean started(final CampaignCatalog.Entry entry, final CCampaignProgress.Snapshot progress) {
		return CampaignCatalog.isOpen(entry, progress)
				&& (!entry.getData().isDefaultOpen() || CampaignCatalog.isMissionOpen(entry, 1, progress));
	}

	/** The furthest open mission of a campaign in a version of the campaign maps, -1 for none. */
	public static int furthestMission(final CampaignCatalog.Entry entry, final CCampaignProgress.Snapshot progress,
			final boolean reforged) {
		final List<CampaignMission> missions = entry.getMissions(reforged);
		int furthest = -1;
		for (int i = 0; i < missions.size(); i++) {
			if (CampaignCatalog.isMissionOpen(entry, i, progress)) {
				furthest = i;
			}
		}
		return furthest;
	}

	/** The campaign the page shows chosen: the one chosen last while it is open, else the progress's, else the first. */
	public static CampaignCatalog.Entry chosenCampaign(final CampaignCatalog catalog,
			final CCampaignProgress.Snapshot progress, final Integer stored) {
		final int menu = catalog.menuCampaignNumber(progress);
		for (final int number : new int[] { stored != null ? stored : menu, menu }) {
			final CampaignCatalog.Entry entry = catalog.getEntry(number);
			if ((entry != null) && CampaignCatalog.isOpen(entry, progress)) {
				return entry;
			}
		}
		for (final CampaignCatalog.Entry entry : catalog.getEntries()) {
			if (CampaignCatalog.isOpen(entry, progress)) {
				return entry;
			}
		}
		return null;
	}

	/**
	 * The skin field of a campaign's background model for an edition: game data with a campaign list per edition keep a
	 * model per edition under the same Background name (HumanBackdrop_V0 is the Reign of Chaos one, _V1 the Frozen Throne
	 * one), and the engine's own game version would pick the wrong one for the other edition's list. The plain name is
	 * to be given as the fallback, for data that name it without a version.
	 */
	public static String backdropField(final String background, final String edition) {
		final int version = LauncherKeys.editionGameVersion(edition, -1);
		return version < 0 ? background : background + "_V" + version;
	}

	/** The scene behind a campaign's pages: the recorded model its Background names. */
	public static String campaignScene(final String section) {
		switch (section.toLowerCase(Locale.ROOT)) {
		case "human":
			return "roc_human";
		case "undead":
			return "roc_undead";
		case "orc":
			return "roc_orc";
		case "nightelf":
			return "roc_nightelf";
		case "nightelfex":
			return "tft_nightelf";
		case "humanex":
			return "tft_human";
		case "undeadex":
			return "tft_undead";
		case "orcex":
			return "tft_orc";
		default:
			return "roc_prologue";
		}
	}

	/** Where each classic scene's model stands, as a part of the recording's width (measured from its frames). */
	public static float modelX(final String scene) {
		switch (scene) {
		case "roc_prologue":
			return 0.32f;
		case "roc_human":
			return 0.33f;
		case "roc_undead":
			return 0.40f;
		case "roc_orc":
			return 0.31f;
		case "roc_nightelf":
		case "tft_nightelf":
			return 0.34f;
		case "tft_human":
			return 0.26f;
		case "tft_undead":
			return 0.23f;
		case "tft_orc":
			return 0.15f;
		default:
			return 0.5f;
		}
	}

	/** The recordings are 1920 x 810 pixels with a 3:4 pixel shape, so they play at 16:9 (like their posters). */
	public static final float RECORDING_ASPECT = 16f / 9f;

	/**
	 * Where the head of each classic scene's model stands in the recording, as parts of its width and height
	 * (measured from the frames, so that the pages with a free middle can place it exactly).
	 */
	public static float[] bandModel(final String scene) {
		switch (scene) {
		case "roc_prologue":
			return new float[] { 0.31f, 0.21f };
		case "roc_human":
			return new float[] { 0.33f, 0.53f };
		case "roc_undead":
			return new float[] { 0.33f, 0.30f };
		case "roc_orc":
			return new float[] { 0.32f, 0.23f };
		case "roc_nightelf":
			return new float[] { 0.31f, 0.12f };
		case "tft_nightelf":
			return new float[] { 0.35f, 0.20f };
		case "tft_human":
			return new float[] { 0.255f, 0.34f };
		case "tft_undead":
			return new float[] { 0.24f, 0.32f };
		case "tft_orc":
			return new float[] { 0.15f, 0.35f };
		default:
			return new float[] { 0.5f, 0.35f };
		}
	}

	/**
	 * How a recorded scene is framed behind a page whose middle is free (between a rail and a list): {focus, zoom,
	 * pivotY}. The scene is enlarged by the zoom (at most 1.5) about the left edge at pivotY (a part of the height)
	 * until the model's head stands at wantedY from the top, or as near as a modest zoom brings it (a picture not enlarged has pivotY 0), and cropped so that it stands at bandMid (a part of the
	 * screen width); a model near the left edge of the recording is brought right by the zoom as far as it needs.
	 * The enlarged picture always covers the frame.
	 */
	public static float[] bandFrame(final String scene, final float frameAspect, final float sceneAspect,
			final float bandMid, final float wantedY) {
		final float[] model = bandModel(scene);
		final float visible = Math.min(1, frameAspect / sceneAspect);
		float zoom = 1;
		if (visible < 1) {
			zoom = Math.max(zoom, visible * bandMid / model[0]);
		}
		// A model high in its recording is lowered only a little: a large zoom would crop the picture too much.
		zoom = Math.max(zoom, model[1] > wantedY ? (1 - wantedY) / (1 - model[1]) : Math.min(wantedY / model[1], 1.2f));
		zoom = Math.min(zoom, 1.5f);
		final float pivotY = zoom > 1.001f ? Math.max(0, Math.min(1, (wantedY - (model[1] * zoom)) / (1 - zoom))) : 0;
		return new float[] { bandFocus(scene, frameAspect, sceneAspect, bandMid, zoom), zoom, pivotY };
	}

	/** The crop of a picture of the given aspect that puts the scene's model at bandMid once enlarged by the zoom. */
	public static float bandFocus(final String scene, final float frameAspect, final float pictureAspect,
			final float bandMid, final float zoom) {
		final float visible = Math.min(1, frameAspect / pictureAspect);
		if (visible >= 1) {
			return 0.5f;
		}
		final float focus = (bandModel(scene)[0] - (visible * bandMid / zoom)) / (1 - visible);
		return Math.max(0, Math.min(1, focus));
	}

	/**
	 * Where to crop a recorded scene so that its model stands in the middle of a narrower frame: the model's x (as a
	 * part of the recording's width) moved as far as the recording allows.
	 */
	public static float modelFocus(final float modelX, final float frameAspect, final float sceneAspect) {
		final float visible = frameAspect / sceneAspect;
		if (visible >= 1) {
			return 0.5f;
		}
		return Math.max(0, Math.min(1, (modelX - (visible / 2)) / (1 - visible)));
	}

	/** A Reforged mission picture of the game data: a jpg in a campaign's own folder (lower case path). */
	public static boolean missionPicture(final String lowerPath) {
		// The credits and crests lie right in the campaign folder.
		return lowerPath.startsWith(SkinArt.MISSION_FOLDER) && lowerPath.endsWith(".jpg")
				&& (lowerPath.indexOf('\\', SkinArt.MISSION_FOLDER.length()) >= 0);
	}

	/**
	 * The Reforged picture of each mission, by the words of its name: the campaign's folder is the one most names
	 * point to, then each mission takes the best match in it. The pictures are keyed by their path below the mission
	 * folder; the values come back as given.
	 */
	public static <T> List<T> missionPictures(final List<String> missionNames, final Map<String, T> pictures) {
		final List<T> found = new ArrayList<>();
		for (int i = 0; i < missionNames.size(); i++) {
			found.add(null);
		}
		if (pictures.isEmpty()) {
			return found;
		}
		final List<List<String>> names = new ArrayList<>();
		for (final String mission : missionNames) {
			names.add(words(mission));
		}
		final Map<String, Integer> votes = new HashMap<>();
		for (final List<String> name : names) {
			final String best = bestPicture(name, pictures.keySet(), null);
			if (best != null) {
				votes.merge(best.substring(0, best.lastIndexOf('\\') + 1), 1, Integer::sum);
			}
		}
		String folder = null;
		for (final Map.Entry<String, Integer> vote : votes.entrySet()) {
			if ((folder == null) || (vote.getValue() > votes.get(folder))) {
				folder = vote.getKey();
			}
		}
		if (folder == null) {
			return found;
		}
		for (int i = 0; i < found.size(); i++) {
			final String best = bestPicture(names.get(i), pictures.keySet(), folder);
			found.set(i, best == null ? null : pictures.get(best));
		}
		return found;
	}

	/** The Reforged picture of a mission by its name alone, or null; keyed as {@link #missionPictures}. */
	public static <T> T missionPicture(final String missionName, final Map<String, T> pictures) {
		final String best = bestPicture(words(missionName), pictures.keySet(), null);
		return best == null ? null : pictures.get(best);
	}

	private static String bestPicture(final List<String> name, final Collection<String> paths, final String folder) {
		String best = null;
		int bestScore = 0;
		for (final String path : paths) {
			if ((folder != null) && !(path.startsWith(folder) && (path.indexOf('\\', folder.length()) < 0))) {
				continue;
			}
			final List<String> file = words(path.substring(path.lastIndexOf('\\') + 1));
			int score = 0;
			for (final String word : name) {
				if (file.contains(word)) {
					score++;
				}
			}
			if (score > bestScore) {
				best = path;
				bestScore = score;
			}
		}
		return best;
	}

	private static List<String> words(final String text) {
		final List<String> words = new ArrayList<>();
		for (final String word : text.toLowerCase(Locale.ROOT).replace(".jpg", "").split("[^a-z]+")) {
			if ((word.length() >= 3) && !"the".equals(word) && !"and".equals(word)) {
				words.add(word);
			}
		}
		return words;
	}

	/** The folder of a map of the list, below Maps, with forward slashes; "" for the top. */
	public static String mapFolder(final String path) {
		String folder = path.replace('\\', '/');
		final int slash = folder.lastIndexOf('/');
		folder = slash < 0 ? "" : folder.substring(0, slash);
		if (folder.regionMatches(true, 0, "maps", 0, 4) && ((folder.length() == 4) || (folder.charAt(4) == '/'))) {
			folder = folder.substring(Math.min(folder.length(), 5));
		}
		return folder;
	}

	/** The game's own folders as the original list names them; a lower-cased one of the data gets a capital. */
	public static String folderName(final String name) {
		switch (name) {
		case "frozenthrone":
			return "FrozenThrone";
		case "scenario":
			return "Scenario";
		case "download":
			return "Download";
		default:
			return "..".equals(name) || !name.equals(name.toLowerCase(Locale.ROOT)) ? name
					: Character.toUpperCase(name.charAt(0)) + name.substring(1);
		}
	}

	/** How many players a map has: its user and computer slots. */
	public static int players(final GameCatalog.MapDetails details) {
		int players = 0;
		for (final GameCatalog.Slot slot : details.slots) {
			if ((slot.type == 1) || (slot.type == 2)) {
				players++;
			}
		}
		return players;
	}

	/** The chosen map while the list has it, else the first. */
	public static String chosenMap(final String stored, final List<GameCatalog.MapEntry> maps) {
		for (final GameCatalog.MapEntry entry : maps) {
			if (entry.path.equals(stored)) {
				return stored;
			}
		}
		return maps.isEmpty() ? null : maps.get(0).path;
	}

	/** A game time as the load page shows it. */
	public static String elapsed(final float seconds) {
		final int total = Math.max(0, (int) seconds);
		return String.format(Locale.ROOT, "%02d:%02d:%02d", total / 3600, (total / 60) % 60, total % 60);
	}

	/** A campaign header without its " Campaign" ending, as the profile page lists it. */
	public static String shortCampaign(final CharSequence header) {
		final String text = header.toString();
		return text.endsWith(" Campaign") ? text.substring(0, text.length() - " Campaign".length()) : text;
	}
}
