package com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.MenuItem;
import com.etheller.warsmash.parsers.fdf.frames.BackdropFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.MenuFrame;
import com.etheller.warsmash.parsers.fdf.frames.MenuFrame.MenuClickListener;
import com.etheller.warsmash.parsers.fdf.frames.PopupMenuFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.mdx.ReplaceableIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.ai.AIDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRaceManagerEntry;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRacePreference;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

import net.warsmash.uberserver.LobbyPlayerType;

public class PlayerSlotPane {
	private final SimpleFrame playerSlotFrame;
	private final StringFrame downloadValue;
	private final PopupMenuFrame nameMenu;
	private final PopupMenuFrame raceMenu;
	private final GlueButtonFrame teamButtonFrame;
	private final MenuFrame teamMenu;
	private final GlueButtonFrame colorButtonFrame;
	private final MenuFrame colorMenu;
	private final List<UIFrame> colorMenuCells = new ArrayList<>();
	private final IntPredicate colorTaken;
	private int color;
	private final BackdropFrame colorButtonValueFrame;
	private final StringFrame teamButtonTitle;
	private final PopupMenuFrame handicapMenu;
	private static final float COLOR_CELL_SIZE = 0.016f;
	private static final float COLOR_SWATCH_INSET = 0.002f;
	private static final float COLOR_MENU_BORDER = 0.009f;
	private static final int COLOR_MENU_COLUMNS = 6;
	private int teamCount = 1;
	private final int index;
	private final PlayerSlotPaneListener playerSlotPaneListener;

	public PlayerSlotPane(final GameUI rootFrame, final Viewport uiViewport, final SimpleFrame container,
			final int index, PlayerSlotPaneListener playerSlotPaneListener, final IntPredicate colorTaken) {
		this.index = index;
		this.colorTaken = colorTaken;
		this.playerSlotPaneListener = playerSlotPaneListener;
		this.playerSlotFrame = (SimpleFrame) rootFrame.createFrameByType("SIMPLEFRAME", "PlayerSlot", container,
				"WITHCHILDREN", index);
		container.add(this.playerSlotFrame);

		this.downloadValue = (StringFrame) rootFrame.getFrameByName("DownloadValue", index);
		this.nameMenu = (PopupMenuFrame) rootFrame.getFrameByName("NameMenu", index);
		this.raceMenu = (PopupMenuFrame) rootFrame.getFrameByName("RaceMenu", index);
		this.teamButtonFrame = (GlueButtonFrame) rootFrame.getFrameByName("TeamButton", index);
		this.colorButtonFrame = (GlueButtonFrame) rootFrame.getFrameByName("ColorButton", index);

		// TODO this is a hole in my API to need to do this -- instead it should
		// probably inflate all frames within a frame by default
		this.colorButtonValueFrame = (BackdropFrame) rootFrame.createFrame("ColorButtonValue", this.colorButtonFrame, 0,
				index);
		this.playerSlotFrame.add(this.colorButtonValueFrame);
		this.teamButtonTitle = (StringFrame) rootFrame.createFrame("TeamButtonTitle", this.teamButtonFrame, 0, index);
		this.playerSlotFrame.add(this.teamButtonTitle);
		final UIFrame teamButtonArrow = rootFrame.createFrame("TeamButtonArrow", this.teamButtonFrame, 0, index);
		this.playerSlotFrame.add(teamButtonArrow);
		this.teamMenu = (MenuFrame) rootFrame.createFrame("StandardPopupMenuMenuTemplate", this.teamButtonFrame, 0,
				index);
		this.teamMenu.addSetPoint(
				new SetPoint(FramePoint.TOPLEFT, this.teamButtonFrame, FramePoint.BOTTOMLEFT, 0, 0));
		this.teamMenu.addSetPoint(
				new SetPoint(FramePoint.TOPRIGHT, this.teamButtonFrame, FramePoint.BOTTOMRIGHT, 0, 0));
		this.teamMenu.setVisible(false);
		this.teamMenu.setOnClick((button, menuItemIndex) -> {
			this.teamMenu.setVisible(false);
			this.playerSlotPaneListener.setPlayerTeam(this.index, menuItemIndex);
		});
		rootFrame.add(this.teamMenu);
		this.teamButtonFrame.setOnClick(() -> {
			if (!this.teamMenu.isVisible()) {
				this.teamMenu.positionBounds(rootFrame, uiViewport);
			}
			this.teamMenu.setVisible(!this.teamMenu.isVisible());
		});
		final UIFrame colorButtonArrow = rootFrame.createFrame("ColorButtonArrow", this.colorButtonFrame, 0, index);
		this.playerSlotFrame.add(colorButtonArrow);
		this.colorMenu = (MenuFrame) rootFrame.createFrame("StandardPopupMenuMenuTemplate", this.colorButtonFrame, 0,
				index);
		this.colorMenu.addSetPoint(
				new SetPoint(FramePoint.TOPLEFT, this.colorButtonFrame, FramePoint.BOTTOMLEFT, 0, 0));
		this.colorMenu.setVisible(false);
		rootFrame.add(this.colorMenu);
		this.colorButtonFrame.setOnClick(() -> {
			if (!this.colorMenu.isVisible()) {
				fillColorMenu(rootFrame, uiViewport);
				this.colorMenu.positionBounds(rootFrame, uiViewport);
			}
			this.colorMenu.setVisible(!this.colorMenu.isVisible());
		});
		this.handicapMenu = (PopupMenuFrame) rootFrame.getFrameByName("HandicapMenu", index);
		// Ping is measured only in network games.
		rootFrame.getFrameByName("PingValue", index).setVisible(false);
	}

	public void setTeamCount(final GameUI rootFrame, final Viewport uiViewport, final int teamCount) {
		this.teamCount = Math.max(1, teamCount);
		final List<MenuItem> teamMenuItems = new ArrayList<>();
		for (int team = 0; team < this.teamCount; team++) {
			teamMenuItems.add(new MenuItem(
					String.format(rootFrame.getTemplates().getDecoratedString("TEAM_FORMAT"), team + 1), -2));
		}
		this.teamMenu.setItems(uiViewport, teamMenuItems);
	}

	private void fillColorMenu(final GameUI rootFrame, final Viewport uiViewport) {
		for (final UIFrame cell : this.colorMenuCells) {
			this.colorMenu.remove(cell);
		}
		this.colorMenuCells.clear();
		final List<Integer> colors = new ArrayList<>();
		for (int playerColor = 0; playerColor < (WarsmashConstants.MAX_PLAYERS - 4); playerColor++) {
			if ((playerColor == this.color) || !this.colorTaken.test(playerColor)) {
				colors.add(playerColor);
			}
		}
		final int columns = Math.min(COLOR_MENU_COLUMNS, colors.size());
		final int rows = (colors.size() + COLOR_MENU_COLUMNS - 1) / COLOR_MENU_COLUMNS;
		this.colorMenu.setWidth(GameUI.convertX(uiViewport, (COLOR_MENU_BORDER * 2) + (columns * COLOR_CELL_SIZE)));
		this.colorMenu.setHeight(GameUI.convertY(uiViewport, (COLOR_MENU_BORDER * 2) + (rows * COLOR_CELL_SIZE)));
		for (int i = 0; i < colors.size(); i++) {
			final int playerColor = colors.get(i);
			final GlueButtonFrame cell = new GlueButtonFrame(null, this.colorMenu);
			cell.setWidth(GameUI.convertX(uiViewport, COLOR_CELL_SIZE));
			cell.setHeight(GameUI.convertY(uiViewport, COLOR_CELL_SIZE));
			cell.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.colorMenu, FramePoint.TOPLEFT,
					GameUI.convertX(uiViewport, COLOR_MENU_BORDER + ((i % COLOR_MENU_COLUMNS) * COLOR_CELL_SIZE)),
					-GameUI.convertY(uiViewport, COLOR_MENU_BORDER + ((i / COLOR_MENU_COLUMNS) * COLOR_CELL_SIZE))));
			final BackdropFrame swatch = (BackdropFrame) rootFrame.createFrame("ColorButtonValue", cell, 0, this.index);
			swatch.clearFramePointAssignments();
			swatch.setSetAllPoints(true, GameUI.convertX(uiViewport, COLOR_SWATCH_INSET));
			swatch.setBackground(rootFrame.loadTexture("ReplaceableTextures\\" + ReplaceableIds.getPathString(1)
					+ ReplaceableIds.getIdString(playerColor) + ".blp"));
			cell.setControlBackdrop(swatch);
			cell.setOnClick(() -> {
				this.colorMenu.setVisible(false);
				this.playerSlotPaneListener.setPlayerColor(this.index, playerColor);
			});
			this.colorMenu.add(cell);
			this.colorMenuCells.add(cell);
		}
	}

	public void setForPlayer(final GameUI rootFrame, final Viewport uiViewport, final CPlayerJass player,
			boolean fixedPlayerSettings) {
		final List<MenuItem> nameMenuItems = new ArrayList<>();
		nameMenuItems.add(new MenuItem(rootFrame.getTemplates().getDecoratedString("OPEN"), -2));
		nameMenuItems.add(new MenuItem(rootFrame.getTemplates().getDecoratedString("CLOSED"), -2));
		nameMenuItems.add(new MenuItem(rootFrame.getTemplates().getDecoratedString("COMPUTER_NEWBIE"), -2));
		nameMenuItems.add(new MenuItem(rootFrame.getTemplates().getDecoratedString("COMPUTER_NORMAL"), -2));
		nameMenuItems.add(new MenuItem(rootFrame.getTemplates().getDecoratedString("COMPUTER_INSANE"), -2));
		setNameMenuTextByPlayer(rootFrame, player, nameMenuItems, fixedPlayerSettings);
		rootFrame.setText(this.downloadValue, "");
		this.downloadValue.setVisible(false);
		setTextFromRacePreference(rootFrame, player);
		this.color = player.getColor();
		this.colorButtonValueFrame.setBackground(rootFrame.loadTexture("ReplaceableTextures\\"
				+ ReplaceableIds.getPathString(1) + ReplaceableIds.getIdString(player.getColor()) + ".blp"));

		((MenuFrame) this.nameMenu.getPopupMenuFrame()).setItems(uiViewport, nameMenuItems);
		this.nameMenu.getPopupMenuFrame().positionBounds(rootFrame, uiViewport);
		this.nameMenu.setMenuClickListener(new MenuClickListener() {
			@Override
			public void onClick(final int button, final int menuItemIndex) {
				switch (menuItemIndex) {
				case 0:
					// open
					PlayerSlotPane.this.playerSlotPaneListener.setPlayerSlot(PlayerSlotPane.this.index,
							LobbyPlayerType.OPEN);
					break;
				case 1:
					// close
					PlayerSlotPane.this.playerSlotPaneListener.setPlayerSlot(PlayerSlotPane.this.index,
							LobbyPlayerType.CLOSED);
					break;
				case 2:
					PlayerSlotPane.this.playerSlotPaneListener.setPlayerSlot(PlayerSlotPane.this.index,
							LobbyPlayerType.COMPUTER_NEWBIE);
					break;
				case 3:
					PlayerSlotPane.this.playerSlotPaneListener.setPlayerSlot(PlayerSlotPane.this.index,
							LobbyPlayerType.COMPUTER_NORMAL);
					break;
				case 4:
					PlayerSlotPane.this.playerSlotPaneListener.setPlayerSlot(PlayerSlotPane.this.index,
							LobbyPlayerType.COMPUTER_INSANE);
					break;
				}
			}
		});

		this.raceMenu.setMenuClickListener(new MenuClickListener() {
			@Override
			public void onClick(final int button, final int menuItemIndex) {
				PlayerSlotPane.this.playerSlotPaneListener.setPlayerRace(PlayerSlotPane.this.index, menuItemIndex);
			}
		});
		this.raceMenu.setEnabled(player.isRaceSelectable());

		final int team = player.getTeam();
		rootFrame.setText(this.teamButtonTitle,
				String.format(rootFrame.getTemplates().getDecoratedString("TEAM_FORMAT"), team + 1));

		final MenuFrame handicapItems = (MenuFrame) this.handicapMenu.getPopupMenuFrame();
		final int handicapItem = Math.round((1.0f - player.getHandicap()) * 10);
		if ((handicapItem >= 0) && (handicapItem < handicapItems.getMenuItemCount())) {
			rootFrame.setText(
					((StringFrame) ((GlueTextButtonFrame) this.handicapMenu.getPopupTitleFrame()).getButtonText()),
					handicapItems.getMenuItem(handicapItem).getText());
		}
		this.handicapMenu.setMenuClickListener((button, menuItemIndex) -> PlayerSlotPane.this.playerSlotPaneListener
				.setPlayerHandicap(PlayerSlotPane.this.index, 1.0f - (menuItemIndex / 10f)));
		this.handicapMenu.setEnabled(player.getSlotState() == CPlayerSlotState.PLAYING);
		this.teamButtonFrame.setEnabled(player.isRaceSelectable() && (this.teamCount > 1));
		if (!this.teamButtonFrame.isEnabled()) {
			this.teamMenu.setVisible(false);
		}
		this.colorButtonFrame.setEnabled(player.isRaceSelectable());
		if (!this.colorButtonFrame.isEnabled()) {
			this.colorMenu.setVisible(false);
		}
	}

	public void setNameMenuTextByPlayer(final GameUI rootFrame, final CPlayerJass player,
			final List<MenuItem> nameMenuItems, boolean fixedPlayerSettings) {
		String name = player.getName();
		final CPlayerSlotState slotState = player.getSlotState();
		final CMapControl controller = player.getController();
		AIDifficulty aiDifficulty = player.getAIDifficulty();
		this.nameMenu.setEnabled(true);
		if (slotState == CPlayerSlotState.EMPTY) {
			name = nameMenuItems.get(0).getText();
		}
		else if (slotState == CPlayerSlotState.PLAYING) {
			if (controller == CMapControl.USER) {
				nameMenuItems.add(new MenuItem(name, -2));
				this.nameMenu.setEnabled(false);
			}
			else if (controller == CMapControl.NONE) {
				name = nameMenuItems.get(1).getText();
			}
			else if (controller == CMapControl.COMPUTER) {
				if (aiDifficulty == null) {
					aiDifficulty = AIDifficulty.NORMAL;
				}
				switch (aiDifficulty) {
				case NEWBIE:
					name = nameMenuItems.get(2).getText();
					break;
				case INSANE:
					name = nameMenuItems.get(4).getText();
					break;
				case NORMAL:
					name = nameMenuItems.get(3).getText();
				default:
					break;
				}
				if (fixedPlayerSettings) {
					this.nameMenu.setEnabled(false);
				}
			}
		}
		else {
			name = rootFrame.getTemplates().getDecoratedString("UNKNOWN");
		}
		rootFrame.setText(((StringFrame) ((GlueTextButtonFrame) this.nameMenu.getPopupTitleFrame()).getButtonText()),
				name);
	}

	public void setTextFromRacePreference(final GameUI rootFrame, final CPlayerJass player) {
		final MenuFrame menuFrame = (MenuFrame) this.raceMenu.getPopupMenuFrame();
		if (player.isRacePrefSet(WarsmashConstants.RACE_MANAGER.getRandomRacePreference())
				&& (menuFrame.getMenuItemCount() > 0)) {
			rootFrame.setText(
					((StringFrame) ((GlueTextButtonFrame) this.raceMenu.getPopupTitleFrame()).getButtonText()),
					menuFrame.getMenuItem(0).getText());
		}
		else {
			for (int i = 0; i < WarsmashConstants.RACE_MANAGER.getEntryCount(); i++) {
				CRaceManagerEntry entry = WarsmashConstants.RACE_MANAGER.get(i);
				CRacePreference racePreference = WarsmashConstants.RACE_MANAGER
						.getRacePreferenceById(entry.getRacePrefId());
				int menuItemIndex = entry.getRaceId();
				if (player.isRacePrefSet(racePreference) && (menuFrame.getMenuItemCount() > menuItemIndex)) {
					rootFrame.setText(
							((StringFrame) ((GlueTextButtonFrame) this.raceMenu.getPopupTitleFrame()).getButtonText()),
							menuFrame.getMenuItem(menuItemIndex).getText());
					break;
				}
			}
		}
	}

	public UIFrame getPlayerSlotFrame() {
		return this.playerSlotFrame;
	}
}
