package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.TextJustify;
import com.etheller.warsmash.parsers.fdf.frames.AbstractUIFrame;
import com.etheller.warsmash.parsers.fdf.frames.CheckBoxFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.ScrollBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SpriteFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextureFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.util.WarsmashConstants;
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
	private static final String BAR_PATH = "UI\\Glues\\ScoreScreen\\ScoreScreen-%1$sBar\\ScoreScreen-%1$sBar.mdl";
	// The bar models' frames are their bounds (0.0373, 0.0328 and 0.0375 high); the 0.032 high bar sits at the
	// bottom of the top and center ones and at the top of the bottom one.
	private static final float[] BAR_FRAME_HEIGHT = { 0.0373f, 0.0328f, 0.0375f };
	private static final float[] BAR_CENTER = { 0.0213f, 0.0168f, 0.016f };
	private static final float FIRST_ROW_TOP = 0.11f;
	private static final float HEADER_CENTER = 0.09125f;
	// Measured on the original screen (1.29): the name banner, the race icon with the team number, the name.
	private static final float BANNER_WIDTH = 0.199f;
	private static final float BANNER_HEIGHT = 0.0497f;
	private static final float ICON_SIZE = 0.028f;
	private static final float TEAM_LEFT = 0.0225f;
	private static final float NAME_LEFT = 0.0307f;
	private static final Color GOLD = new Color(1.0f, 0.86f, 0.28f, 1.0f);
	private static final int VISIBLE_ROWS = 12;
	private static final float TEAM_COLOR_ICON_INSET = 0.004f;
	private static final float VALUE_SHADOW_OFFSET = 0.001f;

	private final GameUI rootFrame;
	private final Viewport uiViewport;
	private final CSimulation simulation;
	private final Runnable onOk;
	private final int localPlayerIndex;
	private AbstractUIFrame frame;
	private final List<CPlayer> players = new ArrayList<>();
	private final List<Row> rows = new ArrayList<>();
	private int firstShownPlayer;
	private final Map<String, StringFrame> headers = new HashMap<>();
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
		final String raceArt = raceArt(this.simulation.getPlayer(this.localPlayerIndex));
		final SpriteFrame raceArtFrame = (SpriteFrame) root.getFrameByName("ScoreScreenRaceArt", 0);
		if (raceArt != null) {
			root.setSpriteFrameModel(raceArtFrame, raceArt);
		}
		// The 2D interface is one layer of the UI scene; these models go behind it, as the frame order has them in
		// the original, with the bars over the race art.
		raceArtFrame.setZDepth(-1.0f);
		((SpriteFrame) root.getFrameByName("ScoreScreenBackground", 0)).setZDepth(-1.0f);
		((SpriteFrame) root.getFrameByName("ScoreScreenPageTab", 0)).setZDepth(-1.0f);

		final List<CPlayer> players = this.players;
		for (int i = 0; i < (WarsmashConstants.MAX_PLAYERS - 4); i++) {
			final CPlayer player = this.simulation.getPlayer(i);
			if ((player.getSlotState() == CPlayerSlotState.PLAYING) || (player.getSlotState() == CPlayerSlotState.LEFT)) {
				players.add(player);
			}
		}
		players.sort(Comparator.comparingInt(CPlayer::getTeam).thenComparingInt(CPlayer::getId));
		// The rows beyond the twelfth are reached with the scroll bar: the shown rows take the next players.
		final int shownRows = Math.min(players.size(), VISIBLE_ROWS);
		float rowTop = FIRST_ROW_TOP;
		UIFrame previousBar = null;
		for (int i = 0; i < shownRows; i++) {
			final int kind = i == 0 ? 0 : (i == (shownRows - 1) ? 2 : 1);
			final Row row = new Row(i, kind, rowTop, previousBar);
			this.rows.add(row);
			previousBar = row.bar;
			rowTop += BAR_FRAME_HEIGHT[kind];
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
		final GlueTextButtonFrame okButton = (GlueTextButtonFrame) root.getFrameByName("ScoreScreenOkButton", 0);
		okButton.setOnClick(this.onOk);
		okButton.setKeyboardFocus(true);
		setupTeamColorButton();
		// Sorting by another column is not done yet; a click must not check its header.
		for (int i = 0; i < 5; i++) {
			for (final String columnFrame : new String[] { "ScoreScreen4Column", "ScoreScreen5Column" }) {
				final UIFrame button = root.getFrameByName(columnFrame + "Button" + i, 0);
				if (button instanceof CheckBoxFrame) {
					((CheckBoxFrame) button).setOnClick(() -> selectTab(this.tab));
				}
			}
		}
		// No replays yet.
		root.getFrameByName("ScoreScreenSaveReplayButton", 0).setVisible(false);
		root.getFrameByName("ScoreScreenReplaySavedButton", 0).setVisible(false);
		// The original shows the scroll bar only when the rows do not fit.
		final ScrollBarFrame scrollBar = (ScrollBarFrame) root.getFrameByName("ScoreScreenScrollBar", 0);
		scrollBar.setVisible(players.size() > VISIBLE_ROWS);
		if (players.size() > VISIBLE_ROWS) {
			// The scroll bar's value is a percentage, 100 at the top.
			scrollBar.setChangeListener((gameUI, viewport, value) -> {
				final int first = Math.round(((100 - value) / 100f) * (this.players.size() - VISIBLE_ROWS));
				if (first != this.firstShownPlayer) {
					this.firstShownPlayer = first;
					selectTab(this.tab);
				}
			});
			scrollBar.setValue(root, this.uiViewport, 100);
		}
		final StringFrame teams = this.rootFrame.createStringFrame("SmashScoreScreenTeamsText", this.frame,
				Color.WHITE, TextJustify.CENTER, TextJustify.MIDDLE, 0.009f);
		teams.addSetPoint(new SetPoint(FramePoint.CENTER, root.getFrameByName("ScoreScreenTeamsButton", 0),
				FramePoint.CENTER, 0, 0));
		root.setText(teams, root.getTemplates().getDecoratedString("TEAMS"));
		own(teams);
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
		// The original centers the selected title on its tab button, above where the plain title sits.
		selected.clearFramePointAssignments();
		selected.addSetPoint(new SetPoint(FramePoint.CENTER,
				this.rootFrame.getFrameByName("ScoreScreenTabButton" + tab, 0), FramePoint.CENTER, 0, 0));
		this.rootFrame.setText(selected, tabText.getText());
		// The tabs model has one sequence per selected tab; without one it draws every tab's art at once.
		((SpriteFrame) this.rootFrame.getFrameByName("ScoreScreenPageTab", 0)).setSequence(tab);
		final String[] keys = COLUMN_KEYS[tab];
		final String columnFrame = keys.length == 4 ? "ScoreScreen4Column" : "ScoreScreen5Column";
		this.rootFrame.getFrameByName("ScoreScreen4ColumnFrame", 0).setVisible(keys.length == 4);
		this.rootFrame.getFrameByName("ScoreScreen5ColumnFrame", 0).setVisible(keys.length == 5);
		for (int i = 0; i < keys.length; i++) {
			this.rootFrame.setText(header(columnFrame, i), this.rootFrame.getTemplates().getDecoratedString(keys[i]));
		}
		// The original shows the sort column's header checked: the total score on the Overview.
		for (int i = 0; i < keys.length; i++) {
			((CheckBoxFrame) this.rootFrame.getFrameByName(columnFrame + "Button" + i, 0))
					.setChecked((tab == 0) && (i == (keys.length - 1)));
		}
		// The original draws each column's best value (above zero) in gold and the others in white.
		final int[] best = new int[keys.length];
		for (final CPlayer player : this.players) {
			for (int i = 0; i < keys.length; i++) {
				best[i] = Math.max(best[i], number(columnValue(player, i)));
			}
		}
		for (int i = 0; i < this.rows.size(); i++) {
			final Row row = this.rows.get(i);
			row.setPlayer(this.players.get(this.firstShownPlayer + i));
			row.update(columnFrame, keys.length, best);
		}
		this.frame.positionBounds(this.rootFrame, this.uiViewport);
	}

	// The original shows the minimap's ally color button art in the box, inside the backdrop's insets.
	private void setupTeamColorButton() {
		final GlueButtonFrame button = (GlueButtonFrame) this.rootFrame.getFrameByName("ScoreScreenTeamColorButton",
				0);
		button.setControlBackdrop(teamColorIcon(button, "MiniMapAllyButtonInactiveEnabled"));
		button.setControlPushedBackdrop(teamColorIcon(button, "MiniMapAllyButtonInactivePushed"));
		button.setControlDisabledBackdrop(teamColorIcon(button, "MiniMapAllyButtonInactiveDisabled"));
		button.setEnabled(true);
	}

	private TextureFrame teamColorIcon(final GlueButtonFrame button, final String skinKey) {
		final TextureFrame icon = this.rootFrame.createTextureFrame(button.getName() + skinKey, button, false, null);
		// The button draws only its active backdrop; drawn by the root as well, the last one would cover it.
		this.rootFrame.remove(icon);
		icon.setTexture(this.rootFrame.getSkinField(skinKey), this.rootFrame);
		final float insetX = GameUI.convertX(this.uiViewport, TEAM_COLOR_ICON_INSET);
		final float insetY = GameUI.convertY(this.uiViewport, TEAM_COLOR_ICON_INSET);
		icon.addSetPoint(new SetPoint(FramePoint.TOPLEFT, button, FramePoint.TOPLEFT, insetX, -insetY));
		icon.addSetPoint(new SetPoint(FramePoint.BOTTOMRIGHT, button, FramePoint.BOTTOMRIGHT, -insetX, insetY));
		return icon;
	}

	// GameUI adds the frames it creates to the root as well, which would draw them a second time over the screen.
	private void own(final UIFrame child) {
		this.rootFrame.remove(child);
		this.frame.add(child);
	}

	// The headers are inside the column check boxes, whose children the engine does not register by name.
	private StringFrame header(final String columnFrame, final int column) {
		final String name = columnFrame + "Header" + column;
		final UIFrame existing = this.rootFrame.getFrameByName(name, 0);
		if (existing instanceof StringFrame) {
			return (StringFrame) existing;
		}
		if (this.headers.containsKey(name)) {
			return this.headers.get(name);
		}
		final UIFrame button = this.rootFrame.getFrameByName(columnFrame + "Button" + column, 0);
		final StringFrame header = this.rootFrame.createStringFrame(name, this.frame, Color.WHITE, TextJustify.CENTER,
				TextJustify.MIDDLE, 0.009f);
		header.addSetPoint(new SetPoint(FramePoint.CENTER, button, FramePoint.CENTER, 0, 0));
		own(header);
		this.headers.put(name, header);
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

	private static int number(final String value) {
		return Integer.parseInt(value.endsWith("%") ? value.substring(0, value.length() - 1) : value);
	}

	private static String score(final CPlayer player, final CPlayerScore score) {
		return Integer.toString(player.getScore(score));
	}

	private static String elapsedTime(final float seconds) {
		final int total = (int) seconds;
		return String.format("%d:%02d:%02d", total / 3600, (total / 60) % 60, total % 60);
	}

	private static String raceIcon(final CPlayer player) {
		if (player.getRace() == null) {
			return null;
		}
		switch (player.getRace().getId()) {
		case 1:
			return "human";
		case 2:
			return "orc";
		case 3:
			return "undead";
		case 4:
			return "nightelf";
		default:
			return null;
		}
	}

	private final class Row {
		private CPlayer player;
		private final float barCenter;
		private final SpriteFrame bar;
		private final TextureFrame banner;
		private final TextureFrame icon;
		private final StringFrame team;
		private final StringFrame name;
		private final StringFrame[] values = new StringFrame[5];

		private Row(final int index, final int kind, final float top, final UIFrame previousBar) {
			this.barCenter = top + BAR_CENTER[kind];
			final GameUI root = MeleeScoreScreen.this.rootFrame;
			final Viewport viewport = MeleeScoreScreen.this.uiViewport;
			final AbstractUIFrame parent = MeleeScoreScreen.this.frame;
			if (kind == 0) {
				this.bar = (SpriteFrame) root.getFrameByName("ScoreScreenBarTop", 0);
			}
			else {
				this.bar = (SpriteFrame) root.createFrameByType("MODEL", "SmashScoreScreenBar" + index, parent, "", 0);
				root.setSpriteFrameModel(this.bar, String.format(BAR_PATH, kind == 1 ? "Center" : "Bottom"));
				this.bar.addSetPoint(new SetPoint(FramePoint.TOPLEFT, previousBar, FramePoint.BOTTOMLEFT, 0, 0));
				parent.add(this.bar);
			}
			this.bar.setZDepth(-0.9f);
			final float centerY = -BAR_CENTER[kind];

			this.banner = root.createTextureFrame("SmashScoreScreenBanner" + index, parent, false, null);
			this.banner.setWidth(GameUI.convertX(viewport, BANNER_WIDTH));
			this.banner.setHeight(GameUI.convertY(viewport, BANNER_HEIGHT));
			this.banner.addSetPoint(new SetPoint(FramePoint.LEFT, this.bar, FramePoint.TOPLEFT, 0,
					GameUI.convertY(viewport, centerY)));
			own(this.banner);

			this.icon = root.createTextureFrame("SmashScoreScreenRace" + index, parent, false, null);
			this.icon.setWidth(GameUI.convertX(viewport, ICON_SIZE));
			this.icon.setHeight(GameUI.convertY(viewport, ICON_SIZE));
			this.icon.addSetPoint(new SetPoint(FramePoint.LEFT, this.bar, FramePoint.TOPLEFT, 0,
					GameUI.convertY(viewport, centerY)));
			own(this.icon);

			this.team = root.createStringFrame("SmashScoreScreenTeam" + index, parent, GOLD, TextJustify.LEFT,
					TextJustify.MIDDLE, 0.010f);
			this.team.addSetPoint(new SetPoint(FramePoint.LEFT, this.bar, FramePoint.TOPLEFT,
					GameUI.convertX(viewport, TEAM_LEFT), GameUI.convertY(viewport, centerY)));
			own(this.team);

			this.name = root.createStringFrame("SmashScoreScreenName" + index, parent, Color.WHITE, TextJustify.LEFT,
					TextJustify.MIDDLE, 0.012f);
			this.name.addSetPoint(new SetPoint(FramePoint.LEFT, this.bar, FramePoint.TOPLEFT,
					GameUI.convertX(viewport, NAME_LEFT), GameUI.convertY(viewport, centerY)));
			own(this.name);
			for (int i = 0; i < this.values.length; i++) {
				this.values[i] = root.createStringFrame("SmashScoreScreenValue" + index + "_" + i, parent, GOLD,
						TextJustify.CENTER, TextJustify.MIDDLE, 0.012f);
				this.values[i].setFontShadowColor(Color.BLACK);
				this.values[i].setFontShadowOffsetX(GameUI.convertX(viewport, VALUE_SHADOW_OFFSET));
				this.values[i].setFontShadowOffsetY(GameUI.convertY(viewport, -VALUE_SHADOW_OFFSET));
				own(this.values[i]);
			}
		}

		private void setPlayer(final CPlayer player) {
			if (player == this.player) {
				return;
			}
			this.player = player;
			final GameUI root = MeleeScoreScreen.this.rootFrame;
			// The bar sequences are in player color order; their names do not match the colors they show.
			this.bar.setSequence(player.getColor());
			final boolean winner = player.getRemovedResult() == CPlayerGameResult.VICTORY;
			this.banner.setTexture("UI\\Glues\\ScoreScreen\\scorescreen-" + (winner ? "gold" : "silver")
					+ "bannerborder.blp", root);
			final String race = raceIcon(player);
			this.icon.setVisible(race != null);
			if (race != null) {
				this.icon.setTexture("UI\\Glues\\ScoreScreen\\scorescreen-player-" + race + ".blp", root);
			}
			root.setText(this.team, Integer.toString(player.getTeam() + 1));
			root.setText(this.name, player.getName());
			this.name.setColor(winner ? GOLD : Color.WHITE);
		}

		private void update(final String columnFrame, final int columns, final int[] best) {
			final GameUI root = MeleeScoreScreen.this.rootFrame;
			final Viewport viewport = MeleeScoreScreen.this.uiViewport;
			final float below = this.barCenter - HEADER_CENTER;
			for (int i = 0; i < this.values.length; i++) {
				final StringFrame value = this.values[i];
				value.setVisible(i < columns);
				if (i >= columns) {
					continue;
				}
				value.clearFramePointAssignments();
				value.addSetPoint(new SetPoint(FramePoint.CENTER, root.getFrameByName(columnFrame + "Button" + i, 0),
						FramePoint.CENTER, 0, GameUI.convertY(viewport, -below)));
				final String text = columnValue(this.player, i);
				root.setText(value, text);
				value.setColor(((best[i] > 0) && (number(text) == best[i])) ? GOLD : Color.WHITE);
			}
		}
	}
}
