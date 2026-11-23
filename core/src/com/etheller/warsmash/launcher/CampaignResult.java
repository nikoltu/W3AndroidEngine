package com.etheller.warsmash.launcher;

import java.util.List;
import java.util.Locale;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMission;

/**
 * The campaign result the launcher shows after a mission: the player's score, the mission's name, the time played and
 * the difficulty, and Continue to go on. The Reforged skin hangs it on the campaign's banner, in the places of the
 * Reforged interface in parts of the banner's height; the classic skin sets the same in a panel of its own frame where
 * the mission list stands.
 */
public final class CampaignResult {
	/** The banner pictures' own size. */
	public static final float BANNER_ASPECT = 812f / 1470f;

	// The Reforged style sheet lays the banner out at 600 x 1086.
	private static final float UNIT = 1f / 1086f;
	public static final float CONTENT_WIDTH = 0.75f * 600 * UNIT;
	/** The banner hangs above the screen's top by this much of the style sheet's units; its crown is cut off. */
	private static final float BANNER_RISE = 40;
	/** A mission name too long for the banner goes on in a second line, which pushes what follows down by this. */
	private static final float NAME_LINE = 36;
	/** The share of the banner's width a line of the name may fill; the style sheet's text box has room around it. */
	private static final float NAME_FILL = 0.95f;

	public static final int HEADING_GOLD = 0xFFFFD428;
	public static final int LABEL_GREY = 0xFFD8D8D8;

	/** The three rows of numbers: their icon, the heading's glue name and the second column's glue name. */
	public static final String[][] ROWS = { { SkinArt.RESULT_HEROES, "SCORE_TAB_HEROES", "KILLED" },
		{ SkinArt.RESULT_UNITS, "SCORE_TAB_UNITS", "KILLED" },
		{ SkinArt.RESULT_STRUCTURES, "SCORE_TAB_STRUCTURES", "RAZED" } };

	private CampaignResult() {
	}

	/**
	 * Where everything of the banner goes. The banner hangs from the top of the screen as high as it is, around the
	 * middle of the chapter list it stands in for; each line of text is a box its text is centred in.
	 */
	public static final class Layout {
		public final float x;
		/** The banner's top, above the screen's. */
		public final float bannerY;
		public final float w;
		public final float h;
		public final float contentX;
		public final float contentW;
		public final float titleY;
		public final float titleH;
		public final float titleSize;
		public final float dividerX;
		public final float dividerW;
		public final float dividerH;
		public final float[] dividerY = new float[2];
		public final float[] rowY = new float[ROWS.length];
		public final float iconX;
		public final float icon;
		public final float detailsX;
		public final float columnW;
		/** Below a row's top: the heading's, the labels' and the values' boxes. */
		public final float headingY;
		public final float headingH;
		public final float headingSize;
		public final float labelY;
		public final float labelH;
		public final float labelSize;
		public final float valueY;
		public final float valueH;
		public final float valueSize;
		public final float nameY;
		public final float nameH;
		/** From one line of the name to the next. */
		public final float nameStep;
		public final float nameSize;
		public final float durationY;
		public final float durationH;
		public final float durationSize;
		public final float difficultyY;
		public final float difficultyH;
		public final float difficultySize;
		public final float difficultyIconX;
		public final float difficultyIconY;
		public final float difficultyIcon;

		/**
		 * @param centreX the middle of the place the banner stands in
		 * @param screenW the screen's width, the banner kept inside it
		 * @param screenH the screen's height, the banner's
		 */
		public Layout(final float centreX, final float screenW, final float screenH) {
			this(centreX, screenW, screenH, 1);
		}

		/**
		 * @param nameLines the lines the mission's name takes in the banner's width, see {@link #nameLines}
		 */
		public Layout(final float centreX, final float screenW, final float screenH, final int nameLines) {
			final float u = screenH * UNIT;
			this.h = screenH;
			this.bannerY = -BANNER_RISE * u;
			this.w = screenH * BANNER_ASPECT;
			this.x = Math.max(0, Math.min(screenW - this.w, centreX - (this.w / 2)));
			this.contentW = CONTENT_WIDTH * screenH;
			this.contentX = this.x + ((this.w - this.contentW) / 2);
			this.titleSize = 70 * u;
			this.titleY = (80 - BANNER_RISE) * u;
			this.titleH = 76 * u;
			// The style sheet moves the line right by 41 of its own width, which the banner's cut hides; half of it
			// keeps the line under the title.
			this.dividerX = this.contentX + (20 * u);
			this.dividerW = this.contentW;
			this.dividerH = 20 * u;
			this.dividerY[0] = this.titleY + this.titleH;
			final float padX = 20 * u;
			this.icon = 100 * u;
			this.iconX = this.contentX + padX;
			this.detailsX = this.iconX + this.icon + (10 * u);
			this.columnW = ((this.contentX + this.contentW) - padX - this.detailsX) / 2;
			this.headingSize = 24 * u;
			this.labelSize = 18 * u;
			this.valueSize = 30 * u;
			this.headingH = this.headingSize;
			this.labelH = this.labelSize * 1.2f;
			this.valueH = this.valueSize * 1.125f;
			final float details = this.headingH + (10 * u) + this.labelH + this.valueH;
			this.headingY = (this.icon - details) / 2;
			this.labelY = this.headingY + this.headingH + (10 * u);
			this.valueY = this.labelY + this.labelH;
			float y = this.dividerY[0] + this.dividerH + (30 * u);
			for (int i = 0; i < ROWS.length; i++) {
				this.rowY[i] = y;
				y += this.icon + (i < (ROWS.length - 1) ? 20 * u : 0);
			}
			y += (30 + 20) * u;
			this.nameSize = 36 * u;
			this.nameY = y;
			this.nameH = 40 * u;
			this.nameStep = NAME_LINE * u;
			this.dividerY[1] = this.nameY + (this.nameStep * (Math.max(1, nameLines) - 1)) + this.nameH + (10 * u);
			this.durationSize = 24 * u;
			this.durationY = this.dividerY[1] + this.dividerH + (10 * u);
			this.durationH = 28 * u;
			this.difficultySize = 18 * u;
			this.difficultyY = this.durationY + this.durationH + (20 * u);
			this.difficultyH = 20 * u;
			this.difficultyIcon = 80 * u;
			this.difficultyIconX = this.contentX + ((this.contentW - this.difficultyIcon) / 2);
			this.difficultyIconY = this.difficultyY + this.difficultyH + (5 * u);
		}
	}

	/**
	 * Where everything of the classic result goes: a framed panel in the mission list's place, the title, the three
	 * rows of numbers in a heading and two columns, the mission's name, the time and the difficulty, and Continue at
	 * the panel's foot. The text is sized to the panel's height and narrowed when the panel is slim.
	 */
	public static final class ClassicLayout {
		/** The text block's own height, in its units. */
		private static final float BLOCK = 640;
		/** The narrowest the panel's inside may be in units before the text is made smaller. */
		private static final float NARROWEST = 420;

		public final float x;
		public final float y;
		public final float w;
		public final float h;
		public final float contentX;
		public final float contentW;
		public final float titleY;
		public final float titleH;
		public final float titleSize;
		public final float[] lineY = new float[2];
		public final float lineH;
		public final float[] rowY = new float[ROWS.length];
		public final float rowH;
		public final float headingW;
		public final float headingSize;
		public final float[] columnX = new float[2];
		public final float columnW;
		/** Below a row's top: the labels' and the values' boxes. */
		public final float labelY;
		public final float labelH;
		public final float labelSize;
		public final float valueY;
		public final float valueH;
		public final float valueSize;
		public final float nameY;
		public final float nameH;
		public final float nameSize;
		public final float durationY;
		public final float durationH;
		public final float durationSize;
		public final float difficultyY;
		public final float difficultyH;
		public final float difficultySize;
		public final float buttonX;
		public final float buttonY;
		public final float buttonW;
		public final float buttonH;

		/**
		 * @param x the panel's left
		 * @param y the panel's top
		 * @param w the panel's width
		 * @param h the panel's height
		 * @param frame the width of the panel's frame
		 * @param buttonH the height of the skin's buttons
		 */
		public ClassicLayout(final float x, final float y, final float w, final float h, final float frame,
				final float buttonH) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			final float pad = frame * 0.75f;
			this.contentX = x + frame + pad;
			this.contentW = w - (2 * (frame + pad));
			this.buttonH = buttonH;
			this.buttonW = Math.min(this.contentW, Math.max(this.contentW * 0.6f, buttonH * 3.5f));
			this.buttonX = this.contentX + ((this.contentW - this.buttonW) / 2);
			this.buttonY = ((y + h) - frame - pad) - buttonH;
			final float top = y + frame + pad;
			final float room = this.buttonY - pad - top;
			final float u = Math.max(0, Math.min(room / BLOCK, this.contentW / NARROWEST));
			final float block = top + ((room - (BLOCK * u)) / 2);
			this.titleSize = 60 * u;
			this.titleY = block;
			this.titleH = 72 * u;
			this.lineH = Math.max(1, 2 * u);
			this.lineY[0] = block + (84 * u);
			this.rowH = 96 * u;
			for (int i = 0; i < ROWS.length; i++) {
				this.rowY[i] = block + ((110 + (112 * i)) * u);
			}
			this.headingW = this.contentW * 0.42f;
			this.headingSize = 30 * u;
			this.columnW = (this.contentW - this.headingW) / 2;
			this.columnX[0] = this.contentX + this.headingW;
			this.columnX[1] = this.columnX[0] + this.columnW;
			this.labelSize = 22 * u;
			this.labelY = 12 * u;
			this.labelH = 26 * u;
			this.valueSize = 36 * u;
			this.valueY = 40 * u;
			this.valueH = 42 * u;
			this.nameSize = 34 * u;
			this.nameY = block + (460 * u);
			this.nameH = 44 * u;
			this.lineY[1] = block + (520 * u);
			this.durationSize = 28 * u;
			this.durationY = block + (540 * u);
			this.durationH = 34 * u;
			this.difficultySize = 22 * u;
			this.difficultyY = block + (586 * u);
			this.difficultyH = 28 * u;
		}
	}

	/**
	 * The mission's name cut into the lines the banner's width allows, at the spaces; a word wider than the banner
	 * keeps a line of its own.
	 *
	 * @param width the width of a text in the size and font the name is set in
	 */
	public static List<String> nameLines(final String name, final java.util.function.ToDoubleFunction<String> width,
			final float maxWidth) {
		final List<String> lines = new java.util.ArrayList<>();
		final StringBuilder line = new StringBuilder();
		for (final String word : name.trim().split("\\s+")) {
			if (word.isEmpty()) {
				continue;
			}
			final String joined = line.length() == 0 ? word : line + " " + word;
			if ((line.length() > 0) && (width.applyAsDouble(joined) > (maxWidth * NAME_FILL))) {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
			else {
				line.setLength(0);
				line.append(joined);
			}
		}
		if (line.length() > 0) {
			lines.add(line.toString());
		}
		return lines;
	}

	/** The result's title: its glue name. */
	public static String title(final GameResult result) {
		return "victory".equals(result.result) ? "VICTORY" : "DEFEAT";
	}

	/** The result's button: Continue after a victory, Restart (the mission again) after a loss; its glue key. */
	public static String buttonLabel(final GameResult result) {
		return "victory".equals(result.result) ? "CONTINUE" : "RESTART";
	}

	/** Whether a game's end brings the result: a campaign mission won, lost or left, with its score. */
	public static boolean shows(final GameResult result) {
		return (result != null) && LaunchRequest.SOURCE_CAMPAIGN.equals(result.source) && !result.failed()
				&& (result.score != null) && ("victory".equals(result.result) || result.lost());
	}

	/** The row's two numbers, as the Reforged result counts them: the score and the killed or razed. */
	public static int[] numbers(final GameResult.Score score, final int row) {
		switch (row) {
		case 0:
			return new int[] { score.heroScore, score.heroesKilled };
		case 1:
			return new int[] { score.unitScore, score.unitsKilled };
		default:
			// The Reforged result's structure score is the number of structures built.
			return new int[] { score.structuresBuilt, score.structuresRazed };
		}
	}

	/** The banner of a campaign by its section in the campaign strings, as the Reforged interface picks it. */
	public static String banner(final String section) {
		switch (section == null ? "" : section.toLowerCase(Locale.ROOT)) {
		case "human":
		case "humanex":
		case "humanre":
			return SkinArt.RESULT_BANNER_HUMAN;
		case "orc":
		case "orcex":
		case "tutorial":
			return SkinArt.RESULT_BANNER_HORDE;
		case "undead":
		case "undeadex":
			return SkinArt.RESULT_BANNER_UNDEAD;
		case "nightelf":
		case "nightelfex":
			return SkinArt.RESULT_BANNER_NIGHTELF;
		case "undeadre":
			return SkinArt.RESULT_BANNER_FK;
		default:
			return SkinArt.RESULT_BANNER_RANDOM;
		}
	}

	/** The difficulty's icon and its glue name; null when not told. */
	public static String[] difficulty(final String difficulty) {
		switch (difficulty == null ? "" : difficulty) {
		case "easy":
			return new String[] { SkinArt.DIFFICULTY_EASY, "EASY" };
		case "normal":
			return new String[] { SkinArt.DIFFICULTY_NORMAL, "NORMAL" };
		case "hard":
		case "insane":
			return new String[] { SkinArt.DIFFICULTY_HARD, "HARD" };
		default:
			return null;
		}
	}

	/**
	 * The Reforged chapter icon of a mission: the medal of the difficulty it was won on, the normal one for a win
	 * recorded without one, the open icon when not won.
	 */
	public static String chapterIcon(final boolean won, final int difficulty) {
		if (!won) {
			return SkinArt.CHAPTER_OPEN;
		}
		switch (difficulty) {
		case 0:
			return SkinArt.DIFFICULTY_EASY;
		case 2:
		case 3:
			return SkinArt.DIFFICULTY_HARD;
		case 1:
			return SkinArt.DIFFICULTY_NORMAL;
		default:
			return SkinArt.CHAPTER_WON;
		}
	}

	/** The campaign's mission whose map was played, in either version of the maps; -1 when none is. */
	public static int playedMission(final CampaignCatalog catalog, final CampaignCatalog.Entry entry,
			final String mapFile) {
		if ((mapFile == null) || mapFile.isEmpty()) {
			return -1;
		}
		final String played = normal(mapFile);
		final List<CampaignMission> missions = entry.getMissions(true);
		for (int i = 0; i < missions.size(); i++) {
			for (final boolean reforged : new boolean[] { false, true }) {
				final String map = catalog.missionMapFilename(entry, i, reforged);
				if ((map != null) && normal(map).equals(played)) {
					return i;
				}
			}
		}
		final String name = name(played);
		for (int i = 0; i < missions.size(); i++) {
			final String map = missions.get(i).getMapFilename();
			if ((map != null) && name(normal(map)).equals(name)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * The campaign a map is a mission of, by its path in either version of the campaign maps; null when no campaign
	 * or more than one lists it. For saves that did not keep their campaign.
	 */
	public static CampaignCatalog.Entry campaignOfMap(final CampaignCatalog catalog, final String mapFile) {
		if ((catalog == null) || (mapFile == null) || mapFile.isEmpty()) {
			return null;
		}
		final String played = normal(mapFile);
		CampaignCatalog.Entry found = null;
		for (final CampaignCatalog.Entry entry : catalog.getEntries()) {
			boolean lists = false;
			final int count = entry.getMissions(true).size();
			for (int i = 0; (i < count) && !lists; i++) {
				for (final boolean reforged : new boolean[] { false, true }) {
					final String map = catalog.missionMapFilename(entry, i, reforged);
					lists |= (map != null) && normal(map).equals(played);
				}
			}
			if (lists) {
				if (found != null) {
					return null;
				}
				found = entry;
			}
		}
		return found;
	}

	/**
	 * The mission Continue starts: after a victory the one after the mission played, after a loss the same one again,
	 * else the furthest open; -1 when there is none to start (the campaign's last mission won), and the campaign's
	 * missions are shown instead.
	 */
	public static int continueMission(final CampaignCatalog.Entry entry, final CCampaignProgress.Snapshot progress,
			final int played, final boolean victory, final boolean reforged) {
		final int count = entry.getMissions(reforged).size();
		if (played >= 0) {
			final int next = victory ? played + 1 : played;
			return (next < count) && CampaignCatalog.isMissionOpen(entry, next, progress) ? next : -1;
		}
		return CampaignChoice.furthestMission(entry, progress, reforged);
	}

	private static String normal(final String path) {
		return path.replace('/', '\\').toLowerCase(Locale.ROOT);
	}

	private static String name(final String normalPath) {
		final int slash = normalPath.lastIndexOf('\\');
		final String file = normalPath.substring(slash + 1);
		final int dot = file.lastIndexOf('.');
		return dot > 0 ? file.substring(0, dot) : file;
	}
}
