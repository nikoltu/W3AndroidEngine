package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.io.IOException;
import java.util.Locale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.etheller.warsmash.input.touch.TouchInputRouter;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.AnchorDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.frames.EditBoxFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.SpriteFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedGame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CSaveStorage;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.FocusableFrame;

/**
 * The upper part of the console: the resource bar, the upper buttons with their dialogs and
 * chat, the clock, and the game menu with saving, loading and restarting.
 */
final class MeleeUIMenus {
	private FocusableFrame chatFocus;
	private SimpleButtonFrame menuButton;
	private SimpleButtonFrame alliesButton;
	private boolean alliesButtonAvailable = true;
	private SimpleButtonFrame chatButton;
	private boolean savingFromMenu;
	private Runnable closeMenuAction;
	private GlueTextButtonFrame[] escMenuButtons;
	private Runnable escMenuBackAction;

	final MeleeUI ui;

	MeleeUIMenus(final MeleeUI ui) {
		this.ui = ui;
	}

	void createTopBar() {
		// Resource bar is a 3 part bar with Gold, Lumber, and Food.
		// Its template does not specify where to put it, so we must
		// put it in the "TOPRIGHT" corner.
		this.ui.resourceBar = this.ui.rootFrame.createSimpleFrame("ResourceBarFrame", this.ui.consoleUI, 0);
		this.ui.resourceBar.addSetPoint(new SetPoint(FramePoint.TOPRIGHT, this.ui.consoleUI, FramePoint.TOPRIGHT, 0,
				0));
		this.ui.resourceBarGoldText = (StringFrame) this.ui.rootFrame.getFrameByName("ResourceBarGoldText", 0);
		goldChanged();
		this.ui.resourceBarLumberText = (StringFrame) this.ui.rootFrame.getFrameByName("ResourceBarLumberText", 0);
		lumberChanged();
		this.ui.resourceBarSupplyText = (StringFrame) this.ui.rootFrame.getFrameByName("ResourceBarSupplyText", 0);
		foodChanged();
		this.ui.resourceBarUpkeepText = (StringFrame) this.ui.rootFrame.getFrameByName("ResourceBarUpkeepText", 0);
		upkeepChanged();
		this.ui.resourceBarSupplyText.setWidth(this.ui.resourceBarUpkeepText.getAssignedWidth());
		this.ui.resourceBarLumberText.setWidth(this.ui.resourceBarUpkeepText.getAssignedWidth());
		this.ui.resourceBarGoldText.setWidth(this.ui.resourceBarUpkeepText.getAssignedWidth());

		this.ui.upperButtonBar = this.ui.rootFrame.createSimpleFrame("UpperButtonBarFrame", this.ui.consoleUI, 0);
		this.ui.upperButtonBar.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.consoleUI, FramePoint.TOPLEFT, 0,
				0));

		this.ui.questsButton = (SimpleButtonFrame) this.ui.rootFrame.getFrameByName("UpperButtonBarQuestsButton", 0);
		this.ui.questDialog = new MeleeQuestDialog(this.ui.rootFrame, this.ui.uiViewport,
				this.ui.war3MapViewer.simulation.getQuestLog());
		this.ui.questsButton.setOnClick(this::toggleQuestDialog);
		this.menuButton = (SimpleButtonFrame) this.ui.rootFrame.getFrameByName("UpperButtonBarMenuButton", 0);
		this.alliesButton = (SimpleButtonFrame) this.ui.rootFrame.getFrameByName("UpperButtonBarAlliesButton", 0);
		this.ui.allianceDialog = new MeleeAllianceDialog(this.ui.rootFrame, this.ui.uiViewport,
				this.ui.war3MapViewer.simulation, this.ui.localPlayer, this.ui.war3MapViewer.getMapFileName(),
				this.ui.unitOrderListener);
		this.ui.scoreScreen = new MeleeScoreScreen(this.ui.rootFrame, this.ui.uiViewport,
				this.ui.war3MapViewer.simulation, this.ui.war3MapViewer.getAllObjectData(),
				this.ui.war3MapViewer.getLocalPlayerIndex(), this.ui::quitMission);
		this.alliesButton.setOnClick(this::toggleAllianceDialog);
		this.chatButton = (SimpleButtonFrame) this.ui.rootFrame.getFrameByName("UpperButtonBarChatButton", 0);
		this.ui.chat = new MeleeChat(this.ui.rootFrame, this.ui.uiViewport, this.ui.war3MapViewer.simulation,
				this.ui.localPlayer, chatFontHeight(), new MeleeChat.Host() {
					@Override
					public void showChatMessage(final String line) {
						showChatLine(line);
					}

					@Override
					public void focusOn(final EditBoxFrame box) {
						setChatFocus(box);
					}

					@Override
					public void dialogClosed() {
						// A touch screen has no Enter key: the dialog's OK is where its player starts typing.
						if (TouchInputRouter.isActive() && MeleeUIMenus.this.ui.chatOpenEntryAfterDialog) {
							MeleeUIMenus.this.ui.chat.openEntry(null);
						}
					}

					@Override
					public void sendChatMessage(final int recipient, final int recipientPlayerIndex,
							final String message) {
						MeleeUIMenus.this.ui.unitOrderListener.chatMessage(recipient, recipientPlayerIndex, message);
					}

					@Override
					public boolean isCheatGame() {
						return MeleeUIMenus.this.ui.isSinglePlayerGame()
								&& !MeleeUIMenus.this.ui.war3MapViewer.isReplayPlayback();
					}

					@Override
					public void sendCheat(final String cheat) {
						MeleeUIMenus.this.ui.unitOrderListener.cheat(cheat);
					}

					@Override
					public void playCampaignMission(final String race, final int mission) {
						final String level = campaignMissionFile(race, mission);
						if (level != null) {
							final War3MapViewer viewer = MeleeUIMenus.this.ui.war3MapViewer;
							viewer.runAfterPendingSave(() -> {
								viewer.getGameExit().setNextLevel(level, null);
								MeleeUIMenus.this.ui.endGame(false);
							});
						}
					}
				});
		this.ui.messageLog = new MeleeMessageLog(this.ui.rootFrame, this.ui.uiViewport);
		if (this.ui.isSinglePlayerGame() && TouchInputRouter.isActive() && !this.ui.war3MapViewer.isReplayPlayback()) {
			// A touch screen has no Enter key, so the button keeps opening the chat line that scripts listen to.
			this.chatButton.setOnClick(this::toggleChatEntry);
		}
		else if (this.ui.isSinglePlayerGame()) {
			// The original's single player bar has the Message Log in the Chat button's place.
			setUpperButtonLabels(this.chatButton, "KEY_LOG", "LOG");
			this.chatButton.setOnClick(this::toggleMessageLog);
		}
		else {
			this.chatButton.setOnClick(this::toggleChatDialog);
		}
		if (TouchInputRouter.isActive()) {
			// A touch screen has no F9 to F12, so the hotkey hint in parentheses is dropped from the labels.
			for (final SimpleButtonFrame button : new SimpleButtonFrame[] { this.ui.questsButton, this.menuButton,
					this.alliesButton, this.chatButton }) {
				for (final UIFrame label : new UIFrame[] { button.getButtonText(), button.getHighlightText() }) {
					if ((label instanceof StringFrame) && (((StringFrame) label).getText() != null)) {
						this.ui.rootFrame.setText((StringFrame) label,
								((StringFrame) label).getText().replaceAll("\\s*\\([^()]*\\)\\s*$", ""));
					}
				}
			}
		}
	}

	void createEscMenu() {
		this.ui.smashEscMenu = (SimpleFrame) this.ui.rootFrame.createSimpleFrame("SmashEscMenu", this.ui.rootFrame, 0);
		this.ui.smashEscMenu.addAnchor(new AnchorDefinition(FramePoint.TOP, 0, escMenuTop()));
		final UIFrame escMenuBackdrop = this.ui.rootFrame.createFrame("EscMenuBackdrop", this.ui.smashEscMenu, 0, 0);
		escMenuBackdrop.setVisible(false);
		final UIFrame escMenuMainPanel = this.ui.rootFrame.createFrame("EscMenuMainPanel", this.ui.smashEscMenu, 0, 0);
		escMenuMainPanel.setVisible(false);
		this.ui.smashEscMenu.add(escMenuBackdrop);
		this.ui.smashEscMenu.add(escMenuMainPanel);
		this.ui.smashEscMenu.setVisible(false);

		final UIFrame escMenuInnerMainPanel = this.ui.rootFrame.getFrameByName("MainPanel", 0);
		final GlueTextButtonFrame pauseButton = (GlueTextButtonFrame) this.ui.rootFrame.getFrameByName("PauseButton",
				0);
		final StringFrame pauseButtonText = (StringFrame) this.ui.rootFrame.getFrameByName("PauseButtonText", 0);
		final GlueTextButtonFrame saveGameButton = (GlueTextButtonFrame) this.ui.rootFrame
				.getFrameByName("SaveGameButton", 0);
		final GlueTextButtonFrame loadGameButton = (GlueTextButtonFrame) this.ui.rootFrame
				.getFrameByName("LoadGameButton", 0);
		final GlueTextButtonFrame helpButton = (GlueTextButtonFrame) this.ui.rootFrame.getFrameByName("HelpButton", 0);
		final GlueTextButtonFrame tipsButton = (GlueTextButtonFrame) this.ui.rootFrame.getFrameByName("TipsButton", 0);
		final GlueTextButtonFrame endGameButton = (GlueTextButtonFrame) this.ui.rootFrame
				.getFrameByName("EndGameButton", 0);
		final GlueTextButtonFrame returnButton = (GlueTextButtonFrame) this.ui.rootFrame.getFrameByName("ReturnButton",
				0);

		final UIFrame escMenuInnerEndGamePanel = this.ui.rootFrame.getFrameByName("EndGamePanel", 0);
		final GlueTextButtonFrame endGamePreviousButton = (GlueTextButtonFrame) this.ui.rootFrame
				.getFrameByName("PreviousButton", 0);
		final GlueTextButtonFrame endGameQuitButton = (GlueTextButtonFrame) this.ui.rootFrame
				.getFrameByName("QuitButton", 0);
		final GlueTextButtonFrame endGameRestartButton = (GlueTextButtonFrame) this.ui.rootFrame
				.getFrameByName("RestartButton", 0);
		endGameRestartButton.setEnabled(!MeleeUIMenus.this.ui.war3MapViewer.isNetworkGame());
		endGameRestartButton.setOnClick(new Runnable() {
			@Override
			public void run() {
				restartMission();
			}
		});
		final GlueTextButtonFrame endGameExitButton = (GlueTextButtonFrame) this.ui.rootFrame
				.getFrameByName("ExitButton", 0);

		final UIFrame escMenuInnerConfirmQuitPanel = this.ui.rootFrame.getFrameByName("ConfirmQuitPanel", 0);
		final GlueTextButtonFrame confirmQuitCancelButton = (GlueTextButtonFrame) this.ui.rootFrame
				.getFrameByName("ConfirmQuitCancelButton", 0);
		final GlueTextButtonFrame confirmQuitQuitButton = (GlueTextButtonFrame) this.ui.rootFrame
				.getFrameByName("ConfirmQuitQuitButton", 0);
		final UIFrame escMenuInnerHelpPanel = this.ui.rootFrame.getFrameByName("HelpPanel", 0);
		final UIFrame escMenuInnerTipsPanel = this.ui.rootFrame.getFrameByName("TipsPanel", 0);
		escMenuInnerMainPanel.setVisible(false);
		escMenuInnerEndGamePanel.setVisible(false);
		escMenuInnerConfirmQuitPanel.setVisible(false);
		escMenuInnerHelpPanel.setVisible(false);
		escMenuInnerTipsPanel.setVisible(false);

		pauseButton.setOnClick(new Runnable() {
			@Override
			public void run() {
				final War3MapViewer viewer = MeleeUIMenus.this.ui.war3MapViewer;
				final boolean pause = !viewer.isGamePaused(War3MapViewer.PauseSource.PLAYER);
				MeleeUIMenus.this.ui.unitOrderListener.setGamePaused(pause);
				MeleeUIMenus.this.ui.rootFrame.setText(pauseButtonText, MeleeUIMenus.this.ui.rootFrame.getTemplates()
						.getDecoratedString(pause ? "KEY_RESUME_GAME" : "KEY_PAUSE_GAME"));
			}
		});
		GameOptions.get().apply();
		this.ui.war3MapViewer.setInterfaceStateKeeper(this.ui);
		this.ui.war3MapViewer.setLoadGameListener(this::loadSavedGame);
		this.ui.war3MapViewer.setSaveGameListener(this::onGameSaved);
		this.ui.gameMenu = new MeleeGameMenu(this.ui.rootFrame, this.ui.uiViewport, this.ui.smashEscMenu,
				escMenuBackdrop, escMenuInnerMainPanel, this.ui.war3MapViewer.mapMpq, new SavedGameDialog.Host() {
					@Override
					public void focusOn(final FocusableFrame frame) {
						setMenuFocus(frame);
					}

					@Override
					public void saveGame(final String name) {
						saveFromMenu(name);
						returnButton.onClick(Input.Buttons.LEFT);
					}

					@Override
					public void loadGame(final String name) {
						MeleeUIMenus.this.ui.war3MapViewer.requestLoadGame(CSaveStorage.savedGame(name));
					}

					@Override
					public SavedGame.Loadability loadability(final FileHandle file) {
						return SavedGame.loadability(file, MeleeUIMenus.this.ui.war3MapViewer);
					}

					@Override
					public void closed() {
					}
				}, () -> this.ui.musicPlayer.optionsChanged(), this.ui.war3MapViewer.getGameData());
		this.ui.openMenuAction = () -> this.menuButton.onClick(Input.Buttons.LEFT);
		this.closeMenuAction = () -> returnButton.onClick(Input.Buttons.LEFT);
		this.escMenuButtons = new GlueTextButtonFrame[] { pauseButton, saveGameButton, loadGameButton,
				(GlueTextButtonFrame) this.ui.rootFrame.getFrameByName("OptionsButton", 0), helpButton, tipsButton,
				endGameButton, returnButton, endGameRestartButton, endGameQuitButton, endGameExitButton,
				endGamePreviousButton, confirmQuitQuitButton, confirmQuitCancelButton };
		this.escMenuBackAction = () -> {
			if (escMenuInnerConfirmQuitPanel.isVisible()) {
				confirmQuitCancelButton.onClick(Input.Buttons.LEFT);
			}
			else if (escMenuInnerEndGamePanel.isVisible()) {
				endGamePreviousButton.onClick(Input.Buttons.LEFT);
			}
			else {
				returnButton.onClick(Input.Buttons.LEFT);
			}
		};
		this.menuButton.setOnClick(new Runnable() {
			@Override
			public void run() {
				if (MeleeUIMenus.this.ui.smashEscMenu.isVisible()) {
					return;
				}
				if (MeleeUIMenus.this.ui.isSinglePlayerGame()) {
					MeleeUIMenus.this.ui.war3MapViewer.setGamePaused(War3MapViewer.PauseSource.MENU, true);
				}
				// 1.29 single player: no Pause Game button (the menu itself pauses), and the upper bar is greyed out
				// while the menu is up.
				pauseButton.setVisible(!MeleeUIMenus.this.ui.isSinglePlayerGame());
				setUpperButtonsEnabled(false);
				MeleeUIMenus.this.ui.chat.closeEntry();
				// A replay's menu has no Save Game, Load Game or Restart Mission (measured).
				final boolean replay = MeleeUIMenus.this.ui.war3MapViewer.isReplayPlayback();
				MeleeUIMenus.this.ui.gameMenu
						.setSaveAndLoadEnabled(!MeleeUIMenus.this.ui.war3MapViewer.isNetworkGame() && !replay);
				endGameRestartButton.setEnabled(!MeleeUIMenus.this.ui.war3MapViewer.isNetworkGame()
						&& !MeleeUIMenus.this.ui.war3MapViewer.isRestartMissionDisabled() && !replay);
				escMenuBackdrop.setVisible(true);
				escMenuMainPanel.setVisible(true);
				MeleeUIMenus.this.ui.smashEscMenu.setVisible(true);
				escMenuInnerMainPanel.setVisible(true);
				updateEscMenuCurrentPanel(escMenuBackdrop, escMenuMainPanel, escMenuInnerMainPanel);
			}
		});
		returnButton.setOnClick(new Runnable() {
			@Override
			public void run() {
				MeleeUIMenus.this.ui.war3MapViewer.setGamePaused(War3MapViewer.PauseSource.MENU, false);
				setUpperButtonsEnabled(true);
				escMenuBackdrop.setVisible(false);
				escMenuMainPanel.setVisible(false);
				MeleeUIMenus.this.ui.smashEscMenu.setVisible(false);
				escMenuInnerMainPanel.setVisible(false);
			}
		});
		endGameButton.setOnClick(new Runnable() {
			@Override
			public void run() {
				escMenuInnerMainPanel.setVisible(false);
				escMenuInnerEndGamePanel.setVisible(true);
				updateEscMenuCurrentPanel(escMenuBackdrop, escMenuMainPanel, escMenuInnerEndGamePanel);
			}
		});
		endGamePreviousButton.setOnClick(new Runnable() {
			@Override
			public void run() {
				escMenuInnerEndGamePanel.setVisible(false);
				escMenuInnerMainPanel.setVisible(true);
				updateEscMenuCurrentPanel(escMenuBackdrop, escMenuMainPanel, escMenuInnerMainPanel);
			}
		});
		endGameQuitButton.setOnClick(new Runnable() {
			@Override
			public void run() {
				escMenuInnerEndGamePanel.setVisible(false);
				final CPlayer localPlayer = MeleeUIMenus.this.ui.war3MapViewer.simulation
						.getPlayer(MeleeUIMenus.this.ui.war3MapViewer.getLocalPlayerIndex());
				final boolean resultGiven = localPlayer.getRemovedResult() != null;
				if (resultGiven || localPlayer.hasPlayerEvents(JassGameEventsWar3.EVENT_PLAYER_LEAVE)) {
					// 1.29 goes straight to the score screen: a victory already given stays, otherwise leaving
					// is a defeat. The melee leave trigger is not run, as its defeat dialog never shows there.
					MeleeUIMenus.this.ui.war3MapViewer.setGamePaused(War3MapViewer.PauseSource.MENU, false);
					escMenuBackdrop.setVisible(false);
					escMenuMainPanel.setVisible(false);
					MeleeUIMenus.this.ui.smashEscMenu.setVisible(false);
					MeleeUIMenus.this.ui.unitOrderListener.leaveGame();
					MeleeUIMenus.this.ui.endGame(true);
				}
				else {
					MeleeUIMenus.this.ui.quitMission();
				}
			}
		});
		endGameExitButton.setOnClick(new Runnable() {
			@Override
			public void run() {
				escMenuInnerEndGamePanel.setVisible(false);
				escMenuInnerConfirmQuitPanel.setVisible(true);
				updateEscMenuCurrentPanel(escMenuBackdrop, escMenuMainPanel, escMenuInnerConfirmQuitPanel);
			}
		});
		confirmQuitCancelButton.setOnClick(new Runnable() {
			@Override
			public void run() {
				escMenuInnerEndGamePanel.setVisible(true);
				escMenuInnerConfirmQuitPanel.setVisible(false);
				updateEscMenuCurrentPanel(escMenuBackdrop, escMenuMainPanel, escMenuInnerEndGamePanel);
			}
		});
		confirmQuitQuitButton.setOnClick(new Runnable() {
			@Override
			public void run() {
				Gdx.app.exit();
			}
		});
	}

	void createTimeIndicator() {
		// Create the Time Indicator (clock)
		this.ui.timeIndicator = (SpriteFrame) this.ui.rootFrame.createFrame("TimeOfDayIndicator", this.ui.rootFrame, 0,
				0);
		this.ui.timeIndicator.setSequence(0); // play the stand
		this.ui.timeIndicator.setAnimationSpeed(0.0f); // do not advance automatically
	}

	private void updateEscMenuCurrentPanel(final UIFrame escMenuBackdrop, final UIFrame escMenuMainPanel,
			final UIFrame escMenuInnerMainPanel) {
		this.ui.smashEscMenu.setWidth(escMenuInnerMainPanel.getAssignedWidth());
		this.ui.smashEscMenu.setHeight(escMenuInnerMainPanel.getAssignedHeight());
		escMenuBackdrop.setWidth(escMenuInnerMainPanel.getAssignedWidth());
		escMenuBackdrop.setHeight(escMenuInnerMainPanel.getAssignedHeight());
		this.ui.smashEscMenu.positionBounds(this.ui.rootFrame, this.ui.uiViewport);

	}

	void toggleAllianceDialog() {
		if (this.ui.allianceDialog.isVisible()) {
			this.ui.allianceDialog.setVisible(false);
		}
		else if (this.ui.allianceDialog.isAvailable()) {
			this.ui.allianceDialog.setVisible(true);
		}
	}

	void updateAlliesButton() {
		final boolean available = this.ui.allianceDialog.isAvailable() && !this.ui.smashEscMenu.isVisible()
				&& !this.ui.war3MapViewer.isReplayPlayback();
		if (available != this.alliesButtonAvailable) {
			this.alliesButtonAvailable = available;
			this.alliesButton.setEnabled(available);
			if (!available) {
				this.ui.allianceDialog.setVisible(false);
			}
		}
		if (this.ui.war3MapViewer.isObserverView()) {
			this.ui.meleeUIMinimap.setObserverView(true);
		}
	}

	public void goldChanged() {
		this.ui.rootFrame.setText(this.ui.resourceBarGoldText, Integer.toString(resourceBarPlayer().getGold()));
	}

	public void lumberChanged() {
		this.ui.rootFrame.setText(this.ui.resourceBarLumberText, Integer.toString(resourceBarPlayer().getLumber()));
	}

	public void foodChanged() {
		final CPlayer player = resourceBarPlayer();
		final int foodCap = player.getFoodCap();
		if (foodCap == 0) {
			this.ui.rootFrame.setText(this.ui.resourceBarSupplyText, Integer.toString(player.getFoodUsed()));
			this.ui.resourceBarSupplyText.setColor(Color.WHITE);
		}
		else {
			this.ui.rootFrame.setText(this.ui.resourceBarSupplyText, player.getFoodUsed() + "/" + foodCap);
			this.ui.resourceBarSupplyText.setColor(player.getFoodUsed() > foodCap ? Color.RED : Color.WHITE);
		}
		upkeepChanged();
	}

	/** The local player, or in a replay the player Replay Vision shows (measured). */
	private CPlayer resourceBarPlayer() {
		return this.ui.war3MapViewer.simulation.getPlayer(this.ui.war3MapViewer.getViewingPlayerIndex());
	}

	void refreshResourceBar() {
		goldChanged();
		lumberChanged();
		foodChanged();
	}

	public void upkeepChanged() {
		if (this.ui.resourceBarUpkeepText == null) {
			return;
		}
		final int level = this.ui.war3MapViewer.simulation.getGameplayConstants()
				.getUpkeepLevel(resourceBarPlayer().getFoodUsed());
		this.ui.resourceBarUpkeepText.setColor(Color.WHITE);
		this.ui.rootFrame.setDecoratedText(this.ui.resourceBarUpkeepText,
				level == 0 ? "UPKEEP_NONE" : (level == 1 ? "UPKEEP_LOW" : "UPKEEP_HIGH"));
	}

	/** With the menu up the keys are its own: the game behind it takes no orders. */
	boolean menuKeyDown(final int keycode) {
		final FocusableFrame focusBefore = this.ui.menuFocus;
		final boolean handled = menuKeyDownInner(keycode);
		// A key that opened a panel with a text box (S for Save Game) also arrives as a typed letter.
		this.ui.swallowNextTypedChar = (this.ui.menuFocus != null) && (this.ui.menuFocus != focusBefore);
		return handled;
	}

	private boolean menuKeyDownInner(final int keycode) {
		if (this.ui.menuFocus != null) {
			if ((keycode == Input.Keys.ESCAPE) || (keycode == Input.Keys.ENTER)) {
				if (keycode == Input.Keys.ENTER) {
					this.ui.menuFocus.keyDown(keycode);
				}
				else {
					this.ui.gameMenu.keyDown(keycode);
				}
			}
			else {
				this.ui.menuFocus.keyDown(keycode);
			}
			return true;
		}
		if (this.ui.gameMenu.isSubPanelShowing()) {
			return this.ui.gameMenu.keyDown(keycode);
		}
		if (keycode == Input.Keys.F10) {
			this.closeMenuAction.run();
		}
		else if (keycode == Input.Keys.ESCAPE) {
			this.escMenuBackAction.run();
		}
		else {
			MenuHotkey.press(keycode, this.escMenuButtons);
		}
		return true;
	}

	private float escMenuTop() {
		return GameUI.convertY(this.ui.uiViewport, -0.05f);
	}

	void followKeyboard() {
		this.ui.gameMenu.followKeyboard(escMenuTop());
	}

	void setMenuFocus(final FocusableFrame frame) {
		if (frame == this.ui.menuFocus) {
			// A panel opened on a touch screen holds its text box without the keyboard; tapping the box brings it up,
			// also again after Back put the keyboard away.
			if ((frame != null) && this.ui.menuFocusFromTouch) {
				frame.onFocusGained();
			}
			return;
		}
		if (this.ui.menuFocus != null) {
			this.ui.menuFocus.onFocusLost();
		}
		this.ui.menuFocus = frame;
		if ((frame != null) && !(TouchInputRouter.isActive() && !this.ui.menuFocusFromTouch)) {
			frame.onFocusGained();
		}
	}

	void displayLoadDialog() {
		if (this.ui.openMenuAction == null) {
			return;
		}
		this.ui.openMenuAction.run();
		this.ui.gameMenu.showLoadDialog();
	}

	private void saveFromMenu(final String name) {
		this.ui.unitOrderListener.saveGame(name);
		this.savingFromMenu = true;
		this.ui.war3MapViewer.requestSaveGame(CSaveStorage.savedGame(name));
	}

	private void onGameSaved(final FileHandle file, final boolean saved) {
		if (this.savingFromMenu) {
			this.savingFromMenu = false;
			this.ui.messages.displayTimedText(0, 0, 10, saved ? savedMessage() : "Could not save the game.");
		}
	}

	private String savedMessage() {
		final String text = this.ui.rootFrame.getTemplates().getDecoratedString("GAME_SAVED");
		// 1.29 GlobalStrings has no GAME_SAVED; the lookup then returns the key itself.
		return "GAME_SAVED".equals(text) ? "Game saved." : text;
	}

	private void restartMission() {
		this.ui.war3MapViewer.setRestartAsNextLevel();
		this.ui.war3MapViewer.setGamePaused(War3MapViewer.PauseSource.MENU, false);
		this.ui.quitMission();
	}

	private void setUpperButtonLabels(final SimpleButtonFrame button, final String keyLabel, final String plainLabel) {
		for (final UIFrame label : new UIFrame[] { button.getButtonText(), button.getHighlightText() }) {
			if (label instanceof StringFrame) {
				this.ui.rootFrame.setText((StringFrame) label,
						this.ui.rootFrame.getTemplates().getDecoratedString(keyLabel));
			}
		}
		if (button.getDisabledText() instanceof StringFrame) {
			this.ui.rootFrame.setText((StringFrame) button.getDisabledText(),
					this.ui.rootFrame.getTemplates().getDecoratedString(plainLabel));
		}
	}

	private void setUpperButtonsEnabled(final boolean enabled) {
		// A replay has Quests and Allies greyed out (measured).
		this.ui.questsButton.setEnabled(enabled && !this.ui.war3MapViewer.isReplayPlayback());
		this.menuButton.setEnabled(enabled);
		this.chatButton.setEnabled(enabled);
		this.alliesButtonAvailable = enabled && this.ui.allianceDialog.isAvailable()
				&& !this.ui.war3MapViewer.isReplayPlayback();
		this.alliesButton.setEnabled(this.alliesButtonAvailable);
		if (!enabled) {
			this.ui.allianceDialog.setVisible(false);
			this.ui.questDialog.setVisible(false);
			this.ui.messageLog.setVisible(false);
			if (this.ui.chat.isDialogVisible()) {
				this.ui.chatOpenEntryAfterDialog = false;
				this.ui.chat.hideDialog();
			}
		}
	}

	private void toggleMessageLog() {
		this.ui.messageLog.setVisible(!this.ui.messageLog.isVisible());
	}

	void toggleChatOrLog() {
		if (this.ui.isSinglePlayerGame()) {
			toggleMessageLog();
		}
		else {
			this.ui.chatOpenEntryAfterDialog = false;
			toggleChatDialog();
			this.ui.chatOpenEntryAfterDialog = false;
		}
	}

	private void toggleChatEntry() {
		if (this.ui.chat.isEntryOpen()) {
			this.ui.chat.closeEntry();
		}
		else {
			this.ui.chat.openEntry(null);
		}
	}

	private void toggleChatDialog() {
		if (this.ui.chat.isDialogVisible()) {
			this.ui.chatOpenEntryAfterDialog = false;
			this.ui.chat.hideDialog();
		}
		else {
			this.ui.chatOpenEntryAfterDialog = true;
			this.ui.chat.showDialog();
		}
	}

	private float chatFontHeight() {
		final Element fontHeights = this.ui.war3MapViewer.miscData.get("FontHeights");
		final float chat = fontHeights.getFieldFloatValue("WorldFrameChatMessage");
		return chat > 0 ? chat : fontHeights.getFieldFloatValue("WorldFrameMessage");
	}

	private void setChatFocus(final FocusableFrame frame) {
		if (frame == this.chatFocus) {
			return;
		}
		if (this.chatFocus != null) {
			this.chatFocus.onFocusLost();
		}
		this.chatFocus = frame;
		if (frame != null) {
			frame.onFocusGained();
		}
	}

	/**
	 * The map of motherland's campaign mission: human, orc, undead or nightelf for Reign of Chaos, the same with an x
	 * for The Frozen Throne, in whichever layout the game data have.
	 */
	private String campaignMissionFile(final String race, final int mission) {
		final boolean frozenThrone = race.endsWith("x");
		final String raceName = race.substring(0, frozenThrone ? race.length() - 1 : race.length());
		final String name = (raceName.equals("nightelf") ? "NightElf"
				: Character.toUpperCase(raceName.charAt(0)) + raceName.substring(1))
				+ (frozenThrone ? "X" : "") + String.format(Locale.US, "%02d", mission);
		final String[] candidates = frozenThrone
				? new String[] { "Maps\\Campaign\\Classic\\" + name + ".w3x",
						"Maps\\FrozenThrone\\Campaign\\" + name + ".w3x" }
				: new String[] { "Maps\\Campaign\\Classic\\" + name + ".w3m", "Maps\\Campaign\\" + name + ".w3m" };
		for (final String candidate : candidates) {
			if (this.ui.war3MapViewer.dataSource.has(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private void showChatLine(final String line) {
		this.ui.messages.showGameMessage(line, MeleeUI.CHAT_MESSAGE_SECONDS);
	}

	void toggleQuestDialog() {
		this.ui.questButtonFlashTime = -1;
		this.ui.questsButton.setLit(false);
		this.ui.questDialog.setVisible(!this.ui.questDialog.isVisible());
	}

	public void flashQuestDialogButton() {
		if (!this.ui.questDialog.isVisible()) {
			this.ui.questButtonFlashTime = 0;
		}
	}

	public void forceQuestDialogUpdate() {
		this.ui.questDialog.forceUpdate();
	}

	/** A loaded game is the map started again with the saved state put in place of its main function. */
	private void loadSavedGame(final FileHandle file, final boolean byScript) {
		// The script LoadGame comes here without the list, and leaving the game cannot be taken back.
		final SavedGame.Loadability state = SavedGame.loadability(file, this.ui.war3MapViewer);
		if (state != SavedGame.Loadability.LOADABLE) {
			this.ui.messages.displayTimedText(0, 0, 10,
					this.ui.rootFrame.getTemplates().getDecoratedString(SavedGameDialog.reasonKey(state)));
			return;
		}
		try {
			final SavedGame.Header header = SavedGame.readHeader(file);
			this.ui.war3MapViewer.getGameExit().setNextLevel(header.mapFile, null);
			this.ui.war3MapViewer.getGameExit().setSavedGame(file, byScript);
			this.ui.war3MapViewer.setGamePaused(War3MapViewer.PauseSource.MENU, false);
			this.ui.quitMission();
		}
		catch (final IOException exc) {
			Log.error(exc);
			this.ui.messages.displayTimedText(0, 0, 10, exc.getMessage());
		}
	}
}
