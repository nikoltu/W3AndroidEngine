package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.TextJustify;
import com.etheller.warsmash.parsers.fdf.frames.AbstractUIFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SpriteFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextureFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.mdx.ReplaceableIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerGameResult;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerScore;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

/**
 * The score screen the original game shows when a game ends with EndGame(true): the Overview, Units, Heroes and
 * Resources tabs with one row per player, the elapsed time and OK.
 */
public class MeleeScoreScreen {
	private static final String[][] COLUMN_KEYS = {
			{ "OVERVIEW_COLUMN0", "OVERVIEW_COLUMN1", "OVERVIEW_COLUMN2", "OVERVIEW_COLUMN3" },
			{ "UNITS_COLUMN0", "UNITS_COLUMN1", "UNITS_COLUMN2", "UNITS_COLUMN3", "UNITS_COLUMN4" },
			{ "HEROES_COLUMN0", "HEROES_COLUMN1", "HEROES_COLUMN2", "HEROES_COLUMN3", "HEROES_COLUMN4" },
			{ "RESOURCES_COLUMN0", "RESOURCES_COLUMN1", "RESOURCES_COLUMN2", "RESOURCES_COLUMN3",
					"RESOURCES_COLUMN4" } };
	// Measured on the original screen: the first row is centered 0.133 below the top, about 0.031 apart.
	private static final float FIRST_ROW_CENTER = 0.133f;
	private static final float ROW_STEP = 0.031f;
	private static final float ROW_HEIGHT = 0.029f;
	private static final float ROW_WIDTH = 0.733f;
	private static final float NAME_LEFT = 0.03f;
	private static final float HEADER_CENTER = 0.0907f;
	private static final int VISIBLE_ROWS = 12;

	private final GameUI rootFrame;
	private final Viewport uiViewport;
	private final CSimulation simulation;
	private final Runnable onOk;
	private final int localPlayerIndex;
	private AbstractUIFrame frame;
	private final List<Row> rows = new ArrayList<>();
	private int tab;

	public MeleeScoreScreen(final GameUI rootFrame, final Viewport uiViewport, final CSimulation simulation,
			final int localPlayerIndex, final Runnable onOk) {
		this.localPlayerIndex = localPlayerIndex;
		this.rootFrame = rootFrame;
		this.uiViewport = uiViewport;
		this.simulation = simulation;
		this.onOk = onOk;
	}

	public boolean isVisible() {
		return (this.frame != null) && this.frame.isVisible();
	}

	public void show() {
		if (this.frame != null) {
			return;
		}
		final GameUI root = this.rootFrame;
		for (final UIFrame child : new ArrayList<>(root.getChildFrames())) {
			child.setVisible(false);
		}
		this.frame = (AbstractUIFrame) root.createFrame("ScoreScreenFrame", root, 0, 0);
		this.frame.setSetAllPoints(true);
		final UIFrame background = root.getFrameByName("ScoreScreenBackground", 0);
		final String raceArt = raceArt(this.simulation.getPlayer(this.localPlayerIndex));
		if (raceArt != null) {
			root.setSpriteFrameModel((SpriteFrame) root.getFrameByName("ScoreScreenRaceArt", 0), raceArt);
		}

		final List<CPlayer> players = new ArrayList<>();
		for (int i = 0; i < (WarsmashConstants.MAX_PLAYERS - 4); i++) {
			final CPlayer player = this.simulation.getPlayer(i);
			if ((player.getSlotState() == CPlayerSlotState.PLAYING) || (player.getSlotState() == CPlayerSlotState.LEFT)) {
				players.add(player);
			}
		}
		players.sort(Comparator.comparingInt(CPlayer::getTeam).thenComparingInt(CPlayer::getId));
		for (int i = 0; i < players.size(); i++) {
			this.rows.add(new Row(players.get(i), i, background));
		}

		for (int i = 0; i < COLUMN_KEYS.length; i++) {
			final int tabIndex = i;
			root.setText((StringFrame) root.getFrameByName("ScoreScreenTabText" + i, 0),
					root.getTemplates().getDecoratedString("SCORESCREEN_TAB" + i));
			((GlueButtonFrame) root.getFrameByName("ScoreScreenTabButton" + i, 0))
					.setOnClick(() -> selectTab(tabIndex));
		}
		root.setText((StringFrame) root.getFrameByName("ScoreScreenElapsedTimeValue", 0),
				elapsedTime(this.simulation.getGameTurnTick() * WarsmashConstants.SIMULATION_STEP_TIME));
		((GlueButtonFrame) root.getFrameByName("ScoreScreenOkButton", 0)).setOnClick(this.onOk);
		// No replays yet.
		root.getFrameByName("ScoreScreenSaveReplayButton", 0).setVisible(false);
		root.getFrameByName("ScoreScreenReplaySavedButton", 0).setVisible(false);
		// The original shows the scroll bar only when the rows do not fit.
		root.getFrameByName("ScoreScreenScrollBar", 0).setVisible(this.rows.size() > VISIBLE_ROWS);
		selectTab(0);
		this.frame.positionBounds(root, this.uiViewport);
	}

	// The frame has no art of its own: the local player's victory scene for its race, or the defeat scene.
	private static String raceArt(final CPlayer player) {
		final CPlayerGameResult result = player.getRemovedResult();
		if (result == CPlayerGameResult.DEFEAT) {
			return "UI\\Glues\\ScoreScreen\\ScoreScreen-Defeat\\ScoreScreen-Defeat.mdl";
		}
		if ((result != CPlayerGameResult.VICTORY) || (player.getRace() == null)) {
			return null;
		}
		final String race;
		switch (player.getRace().getId()) {
		case 1:
			race = "Human";
			break;
		case 2:
			race = "Orc";
			break;
		case 3:
			race = "Undead";
			break;
		case 4:
			race = "NightElf";
			break;
		default:
			return null;
		}
		final String name = "ScoreScreen-" + race + "Victory" + (WarsmashConstants.GAME_VERSION == 0 ? "" : "Expansion");
		return "UI\\Glues\\ScoreScreen\\" + name + "\\" + name + ".mdl";
	}

	private void selectTab(final int tab) {
		this.tab = tab;
		// The selected tab's title is drawn with the selected text template in place of its own.
		final StringFrame selected = (StringFrame) this.rootFrame.getFrameByName("ScoreScreenTabTextSelected", 0);
		final StringFrame tabText = (StringFrame) this.rootFrame.getFrameByName("ScoreScreenTabText" + tab, 0);
		for (int i = 0; i < COLUMN_KEYS.length; i++) {
			this.rootFrame.getFrameByName("ScoreScreenTabText" + i, 0).setVisible(i != tab);
		}
		selected.clearFramePointAssignments();
		selected.addSetPoint(new SetPoint(FramePoint.CENTER, tabText, FramePoint.CENTER, 0, 0));
		this.rootFrame.setText(selected, tabText.getText());
		final String[] keys = COLUMN_KEYS[tab];
		final String columnFrame = keys.length == 4 ? "ScoreScreen4Column" : "ScoreScreen5Column";
		this.rootFrame.getFrameByName("ScoreScreen4ColumnFrame", 0).setVisible(keys.length == 4);
		this.rootFrame.getFrameByName("ScoreScreen5ColumnFrame", 0).setVisible(keys.length == 5);
		for (int i = 0; i < keys.length; i++) {
			this.rootFrame.setText(header(columnFrame, i), this.rootFrame.getTemplates().getDecoratedString(keys[i]));
		}
		for (final Row row : this.rows) {
			row.update(columnFrame, keys.length);
		}
		this.frame.positionBounds(this.rootFrame, this.uiViewport);
	}

	// The headers are inside the column check boxes, whose children the engine does not register by name.
	private StringFrame header(final String columnFrame, final int column) {
		final String name = columnFrame + "Header" + column;
		final UIFrame existing = this.rootFrame.getFrameByName(name, 0);
		if (existing instanceof StringFrame) {
			return (StringFrame) existing;
		}
		final UIFrame button = this.rootFrame.getFrameByName(columnFrame + "Button" + column, 0);
		final StringFrame header = this.rootFrame.createStringFrame(name, this.frame, Color.WHITE, TextJustify.CENTER,
				TextJustify.MIDDLE, 0.009f);
		header.addSetPoint(new SetPoint(FramePoint.CENTER, button, FramePoint.CENTER, 0, 0));
		this.frame.add(header);
		return header;
	}

	private String columnValue(final CPlayer player, final int column) {
		switch (this.tab) {
		case 0:
			switch (column) {
			case 0:
				return score(player, CPlayerScore.UNIT_TOTAL);
			case 1:
				return score(player, CPlayerScore.HERO_TOTAL);
			case 2:
				return score(player, CPlayerScore.RESOURCE_TOTAL);
			default:
				return score(player, CPlayerScore.TOTAL);
			}
		case 1:
			switch (column) {
			case 0:
				return score(player, CPlayerScore.UNITS_TRAINED);
			case 1:
				return score(player, CPlayerScore.UNITS_KILLED);
			case 2:
				return score(player, CPlayerScore.STRUCT_BUILT);
			case 3:
				return score(player, CPlayerScore.STRUCT_RAZED);
			default:
				return score(player, CPlayerScore.FOOD_MAXUSED);
			}
		case 2:
			switch (column) {
			case 0:
				return Integer.toString(player.getHeroesUsed());
			case 1:
				return score(player, CPlayerScore.HEROES_KILLED);
			case 2:
				return score(player, CPlayerScore.ITEMS_GAINED);
			case 3:
				return score(player, CPlayerScore.MERCS_HIRED);
			default:
				return Integer.toString(player.getExperienceGained());
			}
		default:
			switch (column) {
			case 0:
				return score(player, CPlayerScore.GOLD_MINED_TOTAL);
			case 1:
				return score(player, CPlayerScore.LUMBER_TOTAL);
			case 2:
				return Integer.toString(player.getScore(CPlayerScore.GOLD_GIVEN)
						+ player.getScore(CPlayerScore.LUMBER_GIVEN));
			case 3:
				return player.getScore(CPlayerScore.TECH_PERCENT) + "%";
			default:
				return score(player, CPlayerScore.GOLD_LOST_UPKEEP);
			}
		}
	}

	private static String score(final CPlayer player, final CPlayerScore score) {
		return Integer.toString(player.getScore(score));
	}

	private static String elapsedTime(final float seconds) {
		final int total = (int) seconds;
		return String.format("%d:%02d:%02d", total / 3600, (total / 60) % 60, total % 60);
	}

	private final class Row {
		private final CPlayer player;
		private final int index;
		private final StringFrame[] values = new StringFrame[5];

		private Row(final CPlayer player, final int index, final UIFrame background) {
			this.player = player;
			this.index = index;
			final GameUI root = MeleeScoreScreen.this.rootFrame;
			final Viewport viewport = MeleeScoreScreen.this.uiViewport;
			final float top = FIRST_ROW_CENTER + (index * ROW_STEP) - (ROW_HEIGHT / 2);
			final TextureFrame bar = root.createTextureFrame("SmashScoreScreenRow" + index,
					MeleeScoreScreen.this.frame, false, null);
			bar.setTexture("ReplaceableTextures\\" + ReplaceableIds.getPathString(1)
					+ ReplaceableIds.getIdString(player.getColor()) + ".blp", root);
			bar.setColor(0.55f, 0.55f, 0.55f, 1f);
			bar.setWidth(GameUI.convertX(viewport, ROW_WIDTH));
			bar.setHeight(GameUI.convertY(viewport, ROW_HEIGHT));
			bar.addSetPoint(new SetPoint(FramePoint.TOPLEFT, background, FramePoint.TOPLEFT, 0,
					GameUI.convertY(viewport, -top)));
			MeleeScoreScreen.this.frame.add(bar);
			final StringFrame name = root.createStringFrame("SmashScoreScreenName" + index,
					MeleeScoreScreen.this.frame, Color.WHITE, TextJustify.LEFT, TextJustify.MIDDLE, 0.012f);
			name.addSetPoint(new SetPoint(FramePoint.LEFT, bar, FramePoint.LEFT, GameUI.convertX(viewport, NAME_LEFT),
					0));
			root.setText(name, player.getName());
			MeleeScoreScreen.this.frame.add(name);
			for (int i = 0; i < this.values.length; i++) {
				this.values[i] = root.createStringFrame("SmashScoreScreenValue" + index + "_" + i,
						MeleeScoreScreen.this.frame, Color.WHITE, TextJustify.CENTER, TextJustify.MIDDLE, 0.012f);
				MeleeScoreScreen.this.frame.add(this.values[i]);
			}
		}

		private void update(final String columnFrame, final int columns) {
			final GameUI root = MeleeScoreScreen.this.rootFrame;
			final Viewport viewport = MeleeScoreScreen.this.uiViewport;
			final float below = (FIRST_ROW_CENTER + (this.index * ROW_STEP)) - HEADER_CENTER;
			for (int i = 0; i < this.values.length; i++) {
				final StringFrame value = this.values[i];
				value.setVisible(i < columns);
				if (i >= columns) {
					continue;
				}
				value.clearFramePointAssignments();
				value.addSetPoint(new SetPoint(FramePoint.CENTER, root.getFrameByName(columnFrame + "Button" + i, 0),
						FramePoint.CENTER, 0, GameUI.convertY(viewport, -below)));
				root.setText(value, columnValue(this.player, i));
			}
		}
	}
}
