package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.TimeUtils;
import com.etheller.warsmash.input.touch.VirtualCursor;
import com.etheller.warsmash.input.touch.VirtualModifiers;
import com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame;
import com.etheller.warsmash.parsers.fdf.frames.AbstractUIFrame;
import com.etheller.warsmash.parsers.fdf.frames.ScrollBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.Terrain;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItem;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.ItemUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitEnumFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitStateListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidgetFilterFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityView;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.AbstractCAbilityBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityWispHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.queue.CAbilityRally;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CGroupMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitRace;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CWidgetAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ExternStringMsgTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.PointAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.ClickableActionFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CTrackable;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.ClickableFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.FocusableFrame;

/**
 * Keyboard, mouse and touch input of the game interface and the selection it changes: clicks,
 * drag selection, right-click orders and the hover state.
 */
final class MeleeUIInput {
	/** The trackable the mouse is on, whose track event already fired. */
	private CTrackable trackedTrackable;
	private final Vector3 mouseEventLocation = new Vector3();
	private final Vector2 mouseEventScreen = new Vector2();
	private int mouseMoveSentTick = -1;
	private boolean mouseMovePending;
	private float mouseMovePendingX;
	private float mouseMovePendingY;
	private static final AbilityPointTarget clickLocationTemp2 = new AbilityPointTarget();
	private final Vector3 dragCameraTargetOrigin = new Vector3();
	private final List<MultiSelectUnitStateListener> multiSelectUnitStateListeners = new ArrayList<>();
	private long lastUnitClickTime = 0;
	private RenderWidget lastClickUnit;
	private final AnyClickableUnitFilter anyClickableUnitFilter = new AnyClickableUnitFilter();
	private final AnyTargetableUnitFilter anyTargetableUnitFilter = new AnyTargetableUnitFilter();
	private final SelectionHarvestsTreeFilter selectionHarvestsTreeFilter = new SelectionHarvestsTreeFilter();

	final MeleeUI ui;

	MeleeUIInput(final MeleeUI ui) {
		this.ui = ui;
	}

	boolean isUnitSelectable(final RenderWidget mouseOverUnit) {
		return mouseOverUnit.isSelectable(this.ui.war3MapViewer.simulation, this.ui.war3MapViewer.getLocalPlayerIndex())
				&& !mouseOverUnit.getSimulationWidget().isDead();
	}

	private UIFrame getHoveredFrame(final AbstractUIFrame startFrame, final float screenX, final float screenY,
			final String[] includeParent, final String[] ignoreFrame) {
		if (startFrame.isVisible()) {
			final ListIterator<UIFrame> curIterator = startFrame.getChildIterator();
			while (curIterator.hasPrevious()) {
				final UIFrame child = curIterator.previous();
				UIFrame hit = null;
				if ((child instanceof AbstractUIFrame) && !checkFrameInArray(child, includeParent)) {
					hit = getHoveredFrame((AbstractUIFrame) child, screenX, screenY, includeParent, ignoreFrame);
				}
				else {
					final AbstractRenderableFrame renderFrame = (AbstractRenderableFrame) child;
					if (renderFrame.getRenderBounds().contains(screenX, screenY) && renderFrame.isVisible()) {
						hit = renderFrame;
					}
				}
				if ((hit != null) && !checkFrameInArray(hit, ignoreFrame)) {
					return hit;
				}
			}
		}
		return null;
	}

	private boolean checkFrameInArray(final UIFrame frame, final String[] targetFrames) {
		if ((targetFrames == null) || (frame == null)) {
			return false;
		}
		else {
			if (frame.getName() == null) {
				return false;
			}

			for (int ind = 0; ind < targetFrames.length; ind++) {
				if (frame.getName().startsWith(targetFrames[ind])) {
					return true;
				}
			}
			return false;
		}
	}

	private final class AnyClickableUnitFilter implements CWidgetFilterFunction {
		@Override
		public boolean call(final CWidget unit) {
			final RenderWidget renderPeer = MeleeUIInput.this.ui.war3MapViewer.getRenderPeer(unit);
			return !unit.isDead() && renderPeer.isSelectable(MeleeUIInput.this.ui.war3MapViewer.simulation,
					MeleeUIInput.this.ui.war3MapViewer.getLocalPlayerIndex());
		}
	}

	private final class AnyTargetableUnitFilter implements CWidgetFilterFunction {
		@Override
		public boolean call(final CWidget unit) {
			return !unit.isDead();
		}
	}

	/**
	 * A tree near a finger order is only taken when a selected worker would harvest it; anyone else keeps the plain
	 * move to the touched point.
	 */
	private final class SelectionHarvestsTreeFilter implements CWidgetFilterFunction {
		@Override
		public boolean call(final CWidget tree) {
			for (final RenderUnit unit : MeleeUIInput.this.ui.selectedUnits) {
				if (unit.getSimulationUnit().getPlayerIndex() != MeleeUIInput.this.ui.war3MapViewer
						.getLocalPlayerIndex()) {
					continue;
				}
				for (final CAbility ability : unit.getSimulationUnit().getAbilities()) {
					if (!(ability instanceof CAbilityHarvest) && !(ability instanceof CAbilityWispHarvest)) {
						continue;
					}
					final CWidgetAbilityTargetCheckReceiver receiver = CWidgetAbilityTargetCheckReceiver.INSTANCE
							.reset();
					ability.checkCanTarget(MeleeUIInput.this.ui.war3MapViewer.simulation, unit.getSimulationUnit(),
							MeleeUIInput.this.ui.localPlayer.getId(), OrderIds.smart, false, tree, receiver);
					if (receiver.getTarget() != null) {
						return true;
					}
				}
			}
			return false;
		}
	}

	private static float fingerTreeTolerancePx() {
		return 24 * Gdx.graphics.getDensity();
	}

	public void selectUnit(RenderUnit unit) {
		this.ui.subMenuOrderIdStack.clear();
		if ((unit != null) && unit.getSimulationUnit().isDead()) {
			unit = null;
		}
		if (this.ui.selectedUnit != null) {
			this.ui.selectedUnit.getSimulationUnit().removeStateListener(this.ui);
		}
		this.ui.portrait.setSelectedUnit(unit);
		this.ui.selectedUnit = unit;
		this.ui.commands.setDraggingItem(null);
		if (unit == null) {
			this.ui.commands.clearCommandCard();
			this.ui.rootFrame.setText(this.ui.simpleNameValue, "");
			this.ui.rootFrame.setText(this.ui.unitLifeText, "");
			this.ui.rootFrame.setText(this.ui.unitManaText, "");
			this.ui.rootFrame.setText(this.ui.simpleClassValue, "");
			this.ui.rootFrame.setText(this.ui.simpleBuildingActionLabel, "");
			this.ui.attack1Icon.setVisible(false);
			this.ui.attack2Icon.setVisible(false);
			this.ui.rootFrame.setText(this.ui.attack1InfoPanelIconLevel, "");
			this.ui.rootFrame.setText(this.ui.attack2InfoPanelIconLevel, "");
			this.ui.rootFrame.setText(this.ui.simpleBuildingBuildingActionLabel, "");
			this.ui.rootFrame.setText(this.ui.simpleBuildingNameValue, "");
			this.ui.armorIcon.setVisible(false);
			this.ui.rootFrame.setText(this.ui.armorInfoPanelIconLevel, "");
			this.ui.simpleBuildTimeIndicator.setVisible(false);
			this.ui.simpleHeroLevelBar.setVisible(false);
			this.ui.simpleBuildingBuildTimeIndicator.setVisible(false);
			this.ui.simpleInfoPanelBuildingDetail.setVisible(false);
			this.ui.simpleInfoPanelItemDetail.setVisible(false);
			this.ui.simpleInfoPanelDestructableDetail.setVisible(false);
			this.ui.simpleInfoPanelUnitDetail.setVisible(false);
			for (final QueueIcon queueIconFrame : this.ui.queueIconFrames) {
				queueIconFrame.setVisible(false);
			}
			this.ui.selectWorkerInsideFrame.setVisible(false);
			this.ui.heroInfoPanel.setVisible(false);
			this.ui.rallyPointInstance.hide();
			this.ui.rallyPointInstance.detach();
			this.ui.inventoryCover.setVisible(true);
			this.ui.inventoryBarFrame.setVisible(false);
			for (final MultiSelectionIcon iconFrame : this.ui.selectedUnitFrames) {
				iconFrame.setVisible(false);
			}
			for (final UIFrame frame : this.ui.selectedUnitHighlightBackdrop) {
				frame.setVisible(false);
			}
			this.ui.commands.repositionWaypointFlags(null);
		}
		else {
			unit.getSimulationUnit().addStateListener(this.ui);
			this.ui.infoPanel.reloadSelectedUnitUI(unit);
		}
	}

	public boolean keyDown(final int keycode) {
		if (WarsmashConstants.ENABLE_DEBUG) {
			if (keycode == Input.Keys.Z) {
				this.ui.war3MapViewer.debugDepth++;
			}
			if (keycode == Input.Keys.X) {
				this.ui.war3MapViewer.debugDepth = 0;
			}
			if (keycode == Input.Keys.W) {
				Terrain.WIREFRAME_TERRAIN = !Terrain.WIREFRAME_TERRAIN;
			}
		}
		if (keycode == Input.Keys.BACK) {
			// Back opens the game menu as F10 does; inside the menu or chat it steps back as Escape does.
			return keyDown((this.ui.smashEscMenu.isVisible() || this.ui.chat.isEntryOpen()) ? Input.Keys.ESCAPE
					: Input.Keys.F10);
		}
		if (this.ui.smashEscMenu.isVisible()) {
			return this.ui.menus.menuKeyDown(keycode);
		}
		if (this.ui.chat.isEntryOpen()) {
			return this.ui.chat.entryKeyDown(keycode);
		}
		if (this.ui.messages.pressScriptDialogHotkey(keycode)) {
			return true;
		}
		if ((this.ui.replayPanel != null) && this.ui.replayPanel.keyDown(keycode)) {
			return true;
		}
		if (keycode == Input.Keys.F10) {
			this.ui.openMenuAction.run();
			return true;
		}
		if (keycode == Input.Keys.F12) {
			this.ui.menus.toggleChatOrLog();
			return true;
		}
		if (this.ui.messageLog.isVisible() && this.ui.messageLog.keyDown(keycode)) {
			return true;
		}
		if ((keycode == Input.Keys.ESCAPE) && this.ui.chat.isDialogVisible()) {
			this.ui.chatOpenEntryAfterDialog = false;
			this.ui.chat.hideDialog();
			return true;
		}
		if ((keycode == Input.Keys.ENTER) || (keycode == Input.Keys.NUMPAD_ENTER)) {
			this.ui.chat.openEntryFromEnterKey(isShiftDown() ? MeleeChat.Recipient.ALL
					: isControlDown() ? MeleeChat.Recipient.ALLIES : null);
			return true;
		}
		if ((keycode == Input.Keys.ESCAPE) && this.ui.allianceDialog.isVisible()) {
			this.ui.allianceDialog.setVisible(false);
			return true;
		}
		if ((keycode == Input.Keys.ESCAPE) && this.ui.minimapSignalTargeting) {
			this.ui.minimapSignalTargeting = false;
			return true;
		}
		if (keycode == Input.Keys.ESCAPE) {
			this.ui.unitOrderListener.issueGuiPlayerEvent(JassGameEventsWar3.EVENT_PLAYER_END_CINEMATIC.getEventId());
			if (this.ui.userControlEnabled && (this.ui.activeCommand != null) && (this.ui.draggingItem == null)) {
				this.ui.commands.openMenu(this.ui.commands.getSubMenuOrderId());
			}
			return true;
		}
		if (!this.ui.userControlEnabled) {
			return false;
		}
		if (keycode == Input.Keys.TAB) {
			if (this.ui.selectedUnits.size() > 1) {
				if (isShiftDown()) {
					advanceSelectedSubGroupReverse();
				}
				else {
					advanceSelectedSubGroup();
				}
				this.ui.war3MapViewer.getUiSounds().getSound("SubGroupSelectionChange")
						.play(this.ui.uiScene.audioContext, 0, 0, 0);
				return true;
			}
		}
		if ((keycode == Input.Keys.SPACE) && this.ui.getCameraManager().jumpToQuickPosition()) {
			return true;
		}
		if ((keycode == Input.Keys.BACKSPACE) && this.ui.groups.jumpToNextTownHall()) {
			return true;
		}
		if (keycode == Input.Keys.F9) {
			this.ui.menus.toggleQuestDialog();
			return true;
		}
		if (keycode == Input.Keys.F11) {
			this.ui.menus.toggleAllianceDialog();
			return true;
		}
		if ((keycode == Input.Keys.A) && isAltDown()) {
			if (this.ui.minimapPanel.allyFilterEnabled) {
				this.ui.minimapPanel.setAllyColorFilterByPlayer(Math.max(0,
						this.ui.war3MapViewer.getAllyColorFilter().getState()) + 1);
			}
			return true;
		}
		if ((keycode == Input.Keys.G) && isAltDown()) {
			this.ui.minimapPanel.beginMinimapSignal();
			return true;
		}
		if ((keycode == Input.Keys.T) && isAltDown()) {
			this.ui.minimapPanel.setMinimapTerrain(!GameOptions.get().isMinimapTerrain());
			return true;
		}
		if ((keycode == Input.Keys.R) && isAltDown()) {
			if (this.ui.minimapPanel.creepFilterEnabled) {
				this.ui.minimapPanel.setMinimapCreeps(!GameOptions.get().isMinimapCreeps());
			}
			return true;
		}
		if ((keycode == Input.Keys.F) && isAltDown()) {
			this.ui.minimapPanel.setFormationMovement(!GameOptions.get().isFormationMovement());
			return true;
		}
		if ((keycode >= Input.Keys.F1) && (keycode < (Input.Keys.F1 + MeleeUIControlGroups.HERO_HOTKEYS))
				&& this.ui.groups.heroHotkeyPressed(keycode - Input.Keys.F1)) {
			return true;
		}
		if ((keycode == Input.Keys.F8) && !this.ui.idleWorkers.isEmpty()) {
			this.ui.groups.idleWorkerClicked(false);
			return true;
		}
		if ((keycode >= Input.Keys.NUM_0) && (keycode <= Input.Keys.NUM_9)) {
			final int group = keycode - Input.Keys.NUM_0;
			if (isControlDown()) {
				this.ui.groups.assignControlGroup(group, false);
			}
			else if (isShiftDown()) {
				this.ui.groups.assignControlGroup(group, true);
			}
			else {
				this.ui.groups.selectControlGroup(group);
			}
			return true;
		}
		final String keyString = Input.Keys.toString(keycode);
		final char c = keyString.length() == 1 ? keyString.charAt(0) : ' ';
		for (int j = 0; j < MeleeUI.COMMAND_CARD_HEIGHT; j++) {
			for (int i = 0; i < MeleeUI.COMMAND_CARD_WIDTH; i++) {
				boolean match = false;
				switch (this.ui.war3MapViewer.getGameData().getInputHotkeyMode()) {
				case 0:
					if (this.ui.commandCard[j][i].checkHotkey(c, keycode)) {
						match = true;
					}
					break;
				case 1:

					if (keycode == this.ui.commandCardGridHotkeys[j][i]) {
						match = true;
					}
					break;
				}
				if (match) {
					this.ui.commandCard[j][i].onClick(Input.Buttons.LEFT);
					this.ui.war3MapViewer.getUiSounds().getSound("InterfaceClick")
							.play(this.ui.uiScene.audioContext, 0, 0, 0);
					return true;
				}
			}
		}
		return this.ui.cameraManager.keyDown(keycode);
	}

	static int virtualKeyToKeycode(final int virtualKey) {
		if ((virtualKey >= 'A') && (virtualKey <= 'Z')) {
			return Input.Keys.A + (virtualKey - 'A');
		}
		if ((virtualKey >= '0') && (virtualKey <= '9')) {
			return Input.Keys.NUM_0 + (virtualKey - '0');
		}
		switch (virtualKey) {
		case 0x08:
			return Input.Keys.BACKSPACE;
		case 0x09:
			return Input.Keys.TAB;
		case 0x0D:
			return Input.Keys.ENTER;
		case 0x1B:
			return Input.Keys.ESCAPE;
		case 0x20:
			return Input.Keys.SPACE;
		default:
			return Input.Keys.UNKNOWN;
		}
	}

	public boolean keyTyped(final char character) {
		if (this.ui.swallowNextTypedChar) {
			this.ui.swallowNextTypedChar = false;
			return true;
		}
		if (this.ui.menuFocus != null) {
			return this.ui.menuFocus.keyTyped(character);
		}
		if (this.ui.chat.isEntryOpen() && !this.ui.smashEscMenu.isVisible()) {
			return this.ui.chat.entryKeyTyped(character);
		}
		return false;
	}

	public boolean keyUp(final int keycode) {
		if (!this.ui.userControlEnabled) {
			return false;
		}
		return this.ui.cameraManager.keyUp(keycode);
	}

	public boolean scrolled(final float amountX, final float amountY) {
		if (!this.ui.userControlEnabled) {
			return false;
		}
		this.ui.cameraManager.scrolled((int) amountY);
		return false;
	}

	/**
	 * The local player's mouse events for the map's triggers. They go out as commands, like orders, so every machine
	 * and a replay fire them in the same step; only over the game world (not the interface), with the point of the
	 * ground under the pointer, and only while a trigger listens, as the original sends them. A move is sent at most
	 * once per game step, the last position winning. A finger is the left button; a two-finger pan the middle one.
	 */
	void sendMouseEvent(final JassGameEventsWar3 eventType, final int screenX, final int screenY,
			final float worldScreenY, final int button) {
		if (this.ui.scoreScreen.isVisible() || (this.ui.unitOrderListener == null)) {
			return;
		}
		final int localPlayerIndex = this.ui.war3MapViewer.getLocalPlayerIndex();
		if ((localPlayerIndex < 0) || !this.ui.war3MapViewer.simulation.getPlayer(localPlayerIndex)
				.hasPlayerEvents(eventType)) {
			return;
		}
		if ((screenX < 0) || (screenY < 0) || (screenX >= Gdx.graphics.getWidth())
				|| (screenY >= Gdx.graphics.getHeight())) {
			return;
		}
		final Vector2 uiPoint = this.mouseEventScreen.set(screenX, screenY);
		this.ui.uiViewport.unproject(uiPoint);
		if (this.ui.meleeUIMinimap.containsMouse(uiPoint.x, uiPoint.y) || (getHoveredFrame(this.ui.rootFrame,
				uiPoint.x, uiPoint.y, this.ui.includeFrames, this.ui.ignoreFrames) != null)) {
			return;
		}
		this.ui.war3MapViewer.getClickLocation(this.mouseEventLocation, screenX, (int) worldScreenY, false, false);
		final float x = this.mouseEventLocation.x;
		final float y = this.mouseEventLocation.y;
		if (eventType == JassGameEventsWar3.EVENT_PLAYER_MOUSE_MOVE) {
			final int tick = this.ui.war3MapViewer.simulation.getGameTurnTick();
			if (tick == this.mouseMoveSentTick) {
				this.mouseMovePending = true;
				this.mouseMovePendingX = x;
				this.mouseMovePendingY = y;
				return;
			}
			this.mouseMoveSentTick = tick;
			this.mouseMovePending = false;
			this.ui.unitOrderListener.mouseEvent(eventType.getEventId(), x, y, 0);
			return;
		}
		this.ui.unitOrderListener.mouseEvent(eventType.getEventId(), x, y, mouseButtonType(button));
	}

	/** The last move of a step that already sent one goes out in the next step. */
	void flushMouseMove() {
		if (!this.mouseMovePending || (this.ui.unitOrderListener == null)) {
			return;
		}
		final int tick = this.ui.war3MapViewer.simulation.getGameTurnTick();
		if (tick != this.mouseMoveSentTick) {
			this.mouseMovePending = false;
			this.mouseMoveSentTick = tick;
			this.ui.unitOrderListener.mouseEvent(JassGameEventsWar3.EVENT_PLAYER_MOUSE_MOVE.getEventId(),
					this.mouseMovePendingX, this.mouseMovePendingY, 0);
		}
	}

	/** The numbers of MOUSE_BUTTON_TYPE_LEFT, _MIDDLE and _RIGHT in common.j. */
	static int mouseButtonType(final int button) {
		switch (button) {
		case Input.Buttons.LEFT:
			return 1;
		case Input.Buttons.MIDDLE:
			return 2;
		case Input.Buttons.RIGHT:
			return 3;
		default:
			return 0;
		}
	}

	public boolean touchDown(final int screenX, final int screenY, final float worldScreenY, final int button) {
		if (this.ui.scoreScreen.isVisible()) {
			// The score screen ends the game, also a game ended in a cinematic that took the player's control away;
			// nothing under it takes clicks.
			MeleeUI.screenCoordsVector.set(screenX, screenY);
			this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector);
			final UIFrame clickedUIFrame = this.ui.rootFrame.touchDown(MeleeUI.screenCoordsVector.x,
					MeleeUI.screenCoordsVector.y, button);
			if (clickedUIFrame instanceof ClickableFrame) {
				this.ui.mouseDownUIFrame = (ClickableFrame) clickedUIFrame;
				this.ui.mouseDownUIFrame.mouseDown(this.ui.rootFrame, this.ui.uiViewport);
			}
			return true;
		}
		if (!this.ui.userControlEnabled) {
			return false;
		}
		this.ui.allowDrag = false;
		if (button == Input.Buttons.FORWARD) {
			if (this.ui.selectedUnits.size() > 1) {
				advanceSelectedSubGroup();
				this.ui.war3MapViewer.getUiSounds().getSound("SubGroupSelectionChange")
						.play(this.ui.uiScene.audioContext, 0, 0, 0);
			}
			return false;
		}
		else if (button == Input.Buttons.BACK) {
			if (this.ui.selectedUnits.size() > 1) {
				advanceSelectedSubGroupReverse();
				this.ui.war3MapViewer.getUiSounds().getSound("SubGroupSelectionChange")
						.play(this.ui.uiScene.audioContext, 0, 0, 0);
			}
			return false;
		}
		MeleeUI.screenCoordsVector.set(screenX, screenY);
		this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector);
		if (this.ui.allianceDialog.tradeClick(MeleeUI.screenCoordsVector.x, MeleeUI.screenCoordsVector.y,
				button == Input.Buttons.RIGHT, isShiftDown())) {
			return true;
		}
		if (this.ui.meleeUIMinimap.containsMouse(MeleeUI.screenCoordsVector.x, MeleeUI.screenCoordsVector.y)) {
			final Vector2 worldPoint = this.ui.meleeUIMinimap.getWorldPointFromScreen(MeleeUI.screenCoordsVector.x,
					MeleeUI.screenCoordsVector.y);
			if ((button == Input.Buttons.LEFT)
					&& (this.ui.minimapSignalTargeting || ((this.ui.activeCommand == null) && isAltDown()))) {
				this.ui.minimapPanel.sendMinimapSignal(worldPoint.x, worldPoint.y);
				return true;
			}
			if ((button == Input.Buttons.RIGHT) && this.ui.minimapSignalTargeting) {
				this.ui.minimapSignalTargeting = false;
				return true;
			}
			if ((button == Input.Buttons.RIGHT) && !this.ui.selectedUnits.isEmpty()) {
				// As in the original game, a right click on the minimap orders the selection there.
				rightClickMoveTo(worldPoint.x, worldPoint.y);
				return true;
			}
			this.ui.cameraManager.jumpTo(worldPoint.x, worldPoint.y);
			return true;
		}
		final UIFrame clickedUIFrame = this.ui.rootFrame.touchDown(MeleeUI.screenCoordsVector.x,
				MeleeUI.screenCoordsVector.y, button);
		if (this.ui.smashEscMenu.isVisible()) {
			this.ui.gameMenu.pressed(clickedUIFrame);
			if (clickedUIFrame == null) {
				return true;
			}
		}
		if ((clickedUIFrame == null) && this.ui.questDialog.contains(MeleeUI.screenCoordsVector.x,
				MeleeUI.screenCoordsVector.y)) {
			return true;
		}
		if ((clickedUIFrame == null) && (this.ui.chat.dialogContains(MeleeUI.screenCoordsVector.x,
				MeleeUI.screenCoordsVector.y) || this.ui.messageLog.contains(MeleeUI.screenCoordsVector.x,
				MeleeUI.screenCoordsVector.y))) {
			return true;
		}
		if ((clickedUIFrame == null) && this.ui.allianceDialog.contains(MeleeUI.screenCoordsVector.x,
				MeleeUI.screenCoordsVector.y)) {
			// The window's background takes the click instead of the map under it.
			return true;
		}
		if ((clickedUIFrame != null) && this.ui.minimapSignalTargeting) {
			this.ui.minimapSignalTargeting = false;
		}
		if ((clickedUIFrame == null) && (button == Input.Buttons.LEFT)
				&& (this.ui.minimapSignalTargeting || ((this.ui.activeCommand == null) && isAltDown()))) {
			this.ui.war3MapViewer.getClickLocation(MeleeUI.clickLocationTemp, screenX, (int) worldScreenY, true, true);
			this.ui.minimapPanel.sendMinimapSignal(MeleeUI.clickLocationTemp.x, MeleeUI.clickLocationTemp.y);
			return true;
		}
		if ((clickedUIFrame == null) && (button == Input.Buttons.RIGHT) && this.ui.minimapSignalTargeting) {
			this.ui.minimapSignalTargeting = false;
			return true;
		}
		if (clickedUIFrame == null) {
			// try to interact with world
			if ((this.ui.activeCommand != null) && (button != Input.Buttons.MIDDLE)) {
				if (button == Input.Buttons.RIGHT) {
					this.ui.activeCommandUnit = null;
					this.ui.activeCommand = null;
					this.ui.activeCommandOrderId = -1;
					if (this.ui.draggingItem != null) {
						this.ui.commands.setDraggingItem(null);
					}
					this.ui.commands.clearAndRepopulateCommandCard();
				}
				else if (button == Input.Buttons.LEFT) {
					final boolean shiftDown = isShiftDown();
					this.ui.commands.activeCommandUnitTargetFilter.reset();
					RenderWidget rayPickUnit = this.ui.war3MapViewer.rayPickUnit(screenX, worldScreenY,
							this.ui.commands.activeCommandUnitTargetFilter);
					if ((rayPickUnit == null) && VirtualCursor.isFingerInput()
							&& ((this.ui.activeCommand instanceof CAbilityHarvest)
									|| (this.ui.activeCommand instanceof CAbilityWispHarvest))) {
						rayPickUnit = this.ui.war3MapViewer.pickNearestTree(screenX, worldScreenY,
								fingerTreeTolerancePx(), this.ui.commands.activeCommandUnitTargetFilter);
						if (rayPickUnit != null) {
							this.ui.commands.activeCommandUnitTargetFilter.reset();
						}
					}
					if (rayPickUnit != null) {
						this.ui.commands.useActiveCommandOnUnit(shiftDown, rayPickUnit);
					}
					else {
						this.ui.war3MapViewer.getClickLocation(MeleeUI.clickLocationTemp, screenX, (int) worldScreenY,
								this.ui.activeCommandUnit.getSimulationUnit().isMovementOnWaterAllowed(), true);
						clickLocationTemp2.set(MeleeUI.clickLocationTemp.x, MeleeUI.clickLocationTemp.y);

						if (this.ui.draggingItem != null) {
							this.ui.war3MapViewer.showConfirmation(MeleeUI.clickLocationTemp, 0, 1, 0);

							this.ui.unitOrderListener.issueDropItemAtPointOrder(
									this.ui.activeCommandUnit.getSimulationUnit().getHandleId(),
									this.ui.activeCommand.getHandleId(), this.ui.activeCommandOrderId,
									this.ui.draggingItem.getHandleId(), clickLocationTemp2.x, clickLocationTemp2.y,
									shiftDown);
							if (this.ui.getSelectedUnit().soundset.yes.playUnitResponse(
									this.ui.war3MapViewer.worldScene.audioContext, this.ui.getSelectedUnit())) {
								this.ui.infoPanel.portraitTalk(this.ui.getSelectedUnit().soundset.yes);
							}
							this.ui.activeCommandUnit = null;
							this.ui.activeCommand = null;
							this.ui.activeCommandOrderId = -1;
							this.ui.commands.setDraggingItem(null);
							this.ui.commands.clearAndRepopulateCommandCard();
						}
						else {
							final ExternStringMsgTargetCheckReceiver<AbilityPointTarget> pointTargetReceiver = ExternStringMsgTargetCheckReceiver
									.getInstance();
							pointTargetReceiver.reset();
							this.ui.activeCommand.checkCanTarget(this.ui.war3MapViewer.simulation,
									this.ui.activeCommandUnit.getSimulationUnit(), this.ui.localPlayer.getId(),
									this.ui.activeCommandOrderId, false, clickLocationTemp2, pointTargetReceiver);
							final Vector2 target = pointTargetReceiver.getTarget();
							if (target != null) {
								if ((this.ui.activeCommand instanceof CAbilityAttack)
										&& (this.ui.activeCommandOrderId == OrderIds.attack)) {
									this.ui.war3MapViewer.showConfirmation(MeleeUI.clickLocationTemp, 1, 0, 0);
								}
								else {
									this.ui.war3MapViewer.showConfirmation(MeleeUI.clickLocationTemp, 0, 1, 0);
								}
								final boolean formation = (this.ui.selectedUnits.size() > 1)
										&& CGroupMove.usesFormation(this.ui.activeCommandOrderId)
										&& this.ui.minimapPanel.isFormationOrder();
								final List<CUnit> walkers = new ArrayList<>();
								final List<CAbilityView> walkerAbilities = new ArrayList<>();
								if (formation
										&& CGroupMove.canTakeSlot(this.ui.activeCommandUnit.getSimulationUnit())) {
									walkers.add(this.ui.activeCommandUnit.getSimulationUnit());
									walkerAbilities.add(this.ui.activeCommand);
								}
								else {
									this.ui.unitOrderListener.issuePointOrder(
											this.ui.activeCommandUnit.getSimulationUnit().getHandleId(),
											this.ui.activeCommand.getHandleId(), this.ui.activeCommandOrderId,
											clickLocationTemp2.x, clickLocationTemp2.y, shiftDown);
								}
								if (this.ui.selectedUnits.size() > 1) {
									for (final RenderUnit otherSelectedUnit : this.ui.selectedUnits) {
										if (otherSelectedUnit != this.ui.activeCommandUnit) {
											CAbility abilityToUse = null;
											AbilityPointTarget targetToUse = null;
											for (final CAbility ability : otherSelectedUnit.getSimulationUnit()
													.getAbilities()) {
												final PointAbilityTargetCheckReceiver receiver = PointAbilityTargetCheckReceiver.INSTANCE
														.reset();
												ability.checkCanTarget(this.ui.war3MapViewer.simulation,
														otherSelectedUnit.getSimulationUnit(),
														this.ui.localPlayer.getId(), this.ui.activeCommandOrderId,
														false, clickLocationTemp2, receiver);
												if (receiver.getTarget() != null) {
													abilityToUse = ability;
													targetToUse = receiver.getTarget();
												}
											}
											if ((abilityToUse != null) && formation
													&& CGroupMove.canTakeSlot(otherSelectedUnit.getSimulationUnit())) {
												walkers.add(otherSelectedUnit.getSimulationUnit());
												walkerAbilities.add(abilityToUse);
											}
											else if (abilityToUse != null) {
												this.ui.unitOrderListener.issuePointOrder(
														otherSelectedUnit.getSimulationUnit().getHandleId(),
														abilityToUse.getHandleId(), this.ui.activeCommandOrderId,
														targetToUse.getX(), targetToUse.getY(), shiftDown);
											}
										}
									}
								}
								issueGroupPointOrder(walkers, walkerAbilities, this.ui.activeCommandOrderId,
										clickLocationTemp2.x, clickLocationTemp2.y, shiftDown);
								if (this.ui.getSelectedUnit().soundset.yes.playUnitResponse(
										this.ui.war3MapViewer.worldScene.audioContext, this.ui.getSelectedUnit())) {
									this.ui.infoPanel.portraitTalk(this.ui.getSelectedUnit().soundset.yes);
								}
								this.ui.selectedSoundCount = 0;
								if (this.ui.activeCommand instanceof AbstractCAbilityBuild) {
									this.ui.war3MapViewer.getUiSounds().getSound("PlaceBuildingDefault")
											.play(this.ui.uiScene.audioContext, 0, 0, 0);
								}
								else if (this.ui.activeCommand instanceof CAbilityRally) {
									this.ui.war3MapViewer.getUiSounds().getSound("RallyPointPlace")
											.play(this.ui.uiScene.audioContext, 0, 0, 0);
								}
								if (!shiftDown) {
									this.ui.subMenuOrderIdStack.clear();
									this.ui.activeCommandUnit = null;
									this.ui.activeCommand = null;
									this.ui.activeCommandOrderId = -1;
									this.ui.commands.clearAndRepopulateCommandCard();
								}

							}
							else {
								if ((this.ui.commands.activeCommandUnitTargetFilter.lastFailureMessage != null)
										&& !this.ui.commands.activeCommandUnitTargetFilter.lastFailureMessage
												.isEmpty()) {
									this.ui.messages.showInterfaceError(
											this.ui.activeCommandUnit.getSimulationUnit().getPlayerIndex(),
											this.ui.commands.activeCommandUnitTargetFilter.lastFailureMessage);
								}
								else {
									final String externStringKey = pointTargetReceiver.getExternStringKey();
									if ((externStringKey != null) && !externStringKey.isEmpty()) {
										this.ui.messages.showInterfaceError(
												this.ui.activeCommandUnit.getSimulationUnit().getPlayerIndex(),
												externStringKey);
									}
									else {
										this.ui.messages.showInterfaceError(
												this.ui.activeCommandUnit.getSimulationUnit().getPlayerIndex(),
												CommandStringErrorKeys.MUST_TARGET_A_UNIT_WITH_THIS_ACTION);
									}
								}
							}
						}

					}
				}
			}
			else {
				if ((button == Input.Buttons.LEFT) && hitTrackable(screenX, worldScreenY)) {
					return true;
				}
				if (button == Input.Buttons.RIGHT) {
					if ((this.ui.getSelectedUnit() != null) && ((this.ui.getSelectedUnit().getSimulationUnit()
							.getPlayerIndex() == this.ui.war3MapViewer.getLocalPlayerIndex())
							|| this.ui.war3MapViewer.simulation.getPlayer(this.ui.war3MapViewer.getLocalPlayerIndex())
									.hasAlliance(this.ui.getSelectedUnit().getSimulationUnit().getPlayerIndex(),
											CAllianceType.SHARED_CONTROL))) {
						RenderWidget rayPickUnit = this.ui.war3MapViewer.rayPickUnit(screenX, worldScreenY,
								this.anyClickableUnitFilter);
						if (rayPickUnit == null) {
							rayPickUnit = this.ui.war3MapViewer.rayPickUnit(screenX, worldScreenY,
									this.anyTargetableUnitFilter);
						}
						if ((rayPickUnit == null) && VirtualCursor.isFingerInput()) {
							rayPickUnit = this.ui.war3MapViewer.pickNearestTree(screenX, worldScreenY,
									fingerTreeTolerancePx(), this.selectionHarvestsTreeFilter);
						}
						if (rayPickUnit != null) {
							boolean ordered = false;
							boolean rallied = false;
							boolean attacked = false;
							for (final RenderUnit unit : this.ui.selectedUnits) {
								CAbility abilityToUse = null;
								CWidget targetToUse = null;
								for (final CAbility ability : unit.getSimulationUnit().getAbilities()) {
									ability.checkCanTarget(this.ui.war3MapViewer.simulation, unit.getSimulationUnit(),
											this.ui.localPlayer.getId(), OrderIds.smart, false,
											rayPickUnit.getSimulationWidget(),
											CWidgetAbilityTargetCheckReceiver.INSTANCE);
									final CWidget targetWidget = CWidgetAbilityTargetCheckReceiver.INSTANCE.getTarget();
									if (targetWidget != null) {
										abilityToUse = ability;
										targetToUse = targetWidget;
									}
								}
								if (abilityToUse != null) {
									this.ui.unitOrderListener.issueTargetOrder(unit.getSimulationUnit().getHandleId(),
											abilityToUse.getHandleId(), OrderIds.smart, targetToUse.getHandleId(),
											isShiftDown());
									rallied |= abilityToUse instanceof CAbilityRally;
									attacked |= abilityToUse instanceof CAbilityAttack;
									ordered = true;
								}
							}
							if (ordered) {
								final UnitSound yesSound = attacked ? this.ui.getSelectedUnit().soundset.yesAttack
										: this.ui.getSelectedUnit().soundset.yes;
								if (yesSound.playUnitResponse(this.ui.war3MapViewer.worldScene.audioContext,
										this.ui.getSelectedUnit())) {
									this.ui.infoPanel.portraitTalk(yesSound);
								}
								if (rallied) {
									this.ui.war3MapViewer.getUiSounds().getSound("RallyPointPlace")
											.play(this.ui.uiScene.audioContext, 0, 0, 0);
								}
								this.ui.selectedSoundCount = 0;
							}
							else {
								rightClickMove(screenX, worldScreenY);
							}
						}
						else {
							rightClickMove(screenX, worldScreenY);
						}
					}
				}
				else {
					if (getHoveredFrame(this.ui.rootFrame, MeleeUI.screenCoordsVector.x, MeleeUI.screenCoordsVector.y,
							this.ui.includeFrames, this.ui.ignoreFrames) != null) {
						return false;
					}
 this.ui.war3MapViewer.getClickLocation(this.ui.lastMouseClickLocation, screenX, (int) worldScreenY, true, true);
					this.ui.lastMouseDragStart.set(this.ui.lastMouseClickLocation);
					this.ui.lastMouseDragCameraTargetStart.set(this.ui.cameraManager.target);
					this.dragCameraTargetOrigin.set(this.ui.cameraManager.target);
					this.ui.allowDrag = true;
					this.ui.draggingMouseButton = button;
				}
			}
		}
		else {
			if (this.ui.smashEscMenu.isVisible()) {
				this.ui.menuFocusFromTouch = true;
				this.ui.menus.setMenuFocus(clickedUIFrame instanceof FocusableFrame ? (FocusableFrame) clickedUIFrame
						: null);
				this.ui.menuFocusFromTouch = false;
			}
			if (clickedUIFrame instanceof ClickableFrame) {
				this.ui.mouseDownUIFrame = (ClickableFrame) clickedUIFrame;
				this.ui.mouseDownUIFrame.mouseDown(this.ui.rootFrame, this.ui.uiViewport);
			}
		}
		return false;
	}

	private void advanceSelectedSubGroup() {
		boolean foundSubSelection = false;
		for (final RenderUnit unit : this.ui.selectedUnits) {
			if (foundSubSelection) {
				if (!unit.groupsWith(this.ui.selectedUnit)) {
					selectUnit(unit);
					return;
				}
			}
			else if (unit == this.ui.selectedUnit) {
				foundSubSelection = true;
			}
		}
		if (!this.ui.selectedUnits.isEmpty()) {
			selectUnit(this.ui.selectedUnits.get(0));
		}
	}

	private void advanceSelectedSubGroupReverse() {
		boolean foundSubSelection = false;
		for (int i = this.ui.selectedUnits.size() - 1; i >= 0; i--) {
			final RenderUnit unit = this.ui.selectedUnits.get(i);
			if (foundSubSelection) {
				if (!unit.groupsWith(this.ui.selectedUnit)) {
					selectUnit(unit);
					return;
				}
			}
			else if (unit == this.ui.selectedUnit) {
				foundSubSelection = true;
			}
		}
		if (!this.ui.selectedUnits.isEmpty()) {
			selectUnit(this.ui.selectedUnits.get(this.ui.selectedUnits.size() - 1));
		}
	}

	private void issueGroupPointOrder(final List<CUnit> units, final List<CAbilityView> abilities, final int orderId,
			final float x, final float y, final boolean queue) {
		if (units.isEmpty()) {
			return;
		}
		final float[] slots = CGroupMove.slots(this.ui.war3MapViewer.simulation, units, x, y);
		final int[] unitHandleIds = new int[units.size()];
		final int[] abilityHandleIds = new int[units.size()];
		final float[] xs = new float[units.size()];
		final float[] ys = new float[units.size()];
		for (int i = 0; i < units.size(); i++) {
			unitHandleIds[i] = units.get(i).getHandleId();
			abilityHandleIds[i] = abilities.get(i).getHandleId();
			xs[i] = slots[i * 2];
			ys[i] = slots[(i * 2) + 1];
		}
		this.ui.unitOrderListener.issueGroupPointOrder(unitHandleIds, abilityHandleIds, orderId, xs, ys, queue);
	}

	private void rightClickMove(final int screenX, final float worldScreenY) {
		this.ui.war3MapViewer.getClickLocation(MeleeUI.clickLocationTemp, screenX, (int) worldScreenY,
				(this.ui.getSelectedUnit() != null) && this.ui.getSelectedUnit().getSimulationUnit()
				.isMovementOnWaterAllowed(), true);
		this.ui.war3MapViewer.showConfirmation(MeleeUI.clickLocationTemp, 0, 1, 0);
		rightClickMoveTo(MeleeUI.clickLocationTemp.x, MeleeUI.clickLocationTemp.y);
	}

	private void rightClickMoveTo(final float worldX, final float worldY) {
		clickLocationTemp2.set(worldX, worldY);

		boolean ordered = false;
		boolean rallied = false;
		final List<CUnit> walkers = new ArrayList<>();
		final List<CAbilityView> walkerAbilities = new ArrayList<>();
		for (final RenderUnit unit : this.ui.selectedUnits) {
			if ((unit.getSimulationUnit().getPlayerIndex() == this.ui.war3MapViewer.getLocalPlayerIndex())
					|| this.ui.war3MapViewer.simulation.getPlayer(this.ui.war3MapViewer.getLocalPlayerIndex())
							.hasAlliance(unit.getSimulationUnit().getPlayerIndex(), CAllianceType.SHARED_CONTROL)) {
				for (final CAbility ability : unit.getSimulationUnit().getAbilities()) {
					ability.checkCanUse(this.ui.war3MapViewer.simulation, unit.getSimulationUnit(),
							this.ui.localPlayer.getId(), OrderIds.smart, false,
							BooleanAbilityActivationReceiver.INSTANCE.reset());
					if (BooleanAbilityActivationReceiver.INSTANCE.isOk()) {
						ability.checkCanTarget(this.ui.war3MapViewer.simulation, unit.getSimulationUnit(),
								this.ui.localPlayer.getId(), OrderIds.smart, false, clickLocationTemp2,
								PointAbilityTargetCheckReceiver.INSTANCE);
						final Vector2 target = PointAbilityTargetCheckReceiver.INSTANCE.getTarget();
						if (target != null) {
							if (!(ability instanceof CAbilityRally) && CGroupMove.canTakeSlot(unit.getSimulationUnit())
									&& this.ui.minimapPanel.isFormationOrder()) {
								walkers.add(unit.getSimulationUnit());
								walkerAbilities.add(ability);
							}
							else {
								this.ui.unitOrderListener.issuePointOrder(unit.getSimulationUnit().getHandleId(),
										ability.getHandleId(), OrderIds.smart, target.x, target.y, isShiftDown());
							}
							rallied |= ability instanceof CAbilityRally;
							ordered = true;
							break;
						}
					}
				}
			}

		}
		issueGroupPointOrder(walkers, walkerAbilities, OrderIds.smart, clickLocationTemp2.x, clickLocationTemp2.y,
				isShiftDown());

		if (ordered) {
			if (this.ui.getSelectedUnit().soundset.yes.playUnitResponse(this.ui.war3MapViewer.worldScene.audioContext,
					this.ui.getSelectedUnit())) {
				this.ui.infoPanel.portraitTalk(this.ui.getSelectedUnit().soundset.yes);
			}
			if (rallied) {
				this.ui.war3MapViewer.getUiSounds().getSound("RallyPointPlace")
						.play(this.ui.uiScene.audioContext, 0, 0, 0);
			}
			this.ui.selectedSoundCount = 0;
		}
	}

	void selectWidgets(final List<RenderWidget> selectedUnits) {
		if ((selectedUnits.size() == 1) && (selectedUnits.get(0) instanceof RenderItem)) {
			final RenderItem selectedItem = (RenderItem) selectedUnits.get(0);
			selectItem(selectedItem);
			return;
		}
		if ((selectedUnits.size() == 1) && (selectedUnits.get(0) instanceof RenderDestructable)) {
			final RenderDestructable selectedItem = (RenderDestructable) selectedUnits.get(0);
			selectDestructable(selectedItem);
			return;
		}
		final List<RenderUnit> units = new ArrayList<>();
		Collections.sort(selectedUnits, new Comparator<RenderWidget>() {
			@Override
			public int compare(final RenderWidget widget1, final RenderWidget widget2) {
				final CUnitType unitType1 = ((RenderUnit) widget2).getSimulationUnit().getUnitType();
				final CUnitType unitType2 = ((RenderUnit) widget1).getSimulationUnit().getUnitType();
				final int prioSort = unitType1.getPriority() - unitType2.getPriority();
				if (prioSort == 0) {
					final int levelSort = unitType1.getLevel() - unitType2.getLevel();
					if (levelSort == 0) {
						return unitType1.getTypeId().getValue() - unitType2.getTypeId().getValue();
					}
					else {
						return levelSort;
					}
				}
				else {
					return prioSort;
				}
			}
		});
		for (final RenderWidget widget : selectedUnits) {
			if (widget instanceof RenderUnit) {
				units.add((RenderUnit) widget);
			}
		}
		selectUnits(units);
	}

	private void selectItem(final RenderItem selectedItem) {
		selectUnit(null);
		this.ui.portrait.setSelectedItem(selectedItem);
		this.ui.simpleInfoPanelItemDetail.setVisible(true);
		final War3ID typeId = selectedItem.getSimulationItem().getTypeId();
		final ItemUI itemUI = this.ui.war3MapViewer.getAbilityDataUI().getItemUI(typeId);
		this.ui.rootFrame.setText(this.ui.simpleItemNameValue, itemUI.getName());
		this.ui.rootFrame.setText(this.ui.simpleItemDescriptionValue, itemUI.getDescription());
	}

	private void selectDestructable(final RenderDestructable selectedItem) {
		selectUnit(null);
		this.ui.portrait.setSelectedDestructable(selectedItem);
		this.ui.simpleInfoPanelDestructableDetail.setVisible(true);
		final String name = selectedItem.getSimulationDestructable().getDestType().getName();
		this.ui.rootFrame.setText(this.ui.simpleDestructableNameValue, name);
	}

	// Measured on 1.29: SelectUnit and ClearSelection from a script fire the selection events as clicks do, a step
	// later. What the game itself takes out of the selection (death, hiding) it drops on its own, without events.
	private void fireSelectionEvents(final List<RenderUnit> before, final List<RenderUnit> after) {
		if (this.ui.isSimulationDriven()) {
			return;
		}
		final List<CUnit> deselected = new ArrayList<>();
		for (final RenderUnit unit : before) {
			if (!after.contains(unit)) {
				deselected.add(unit.getSimulationUnit());
			}
		}
		final List<CUnit> selected = new ArrayList<>();
		for (final RenderUnit unit : after) {
			if (!before.contains(unit)) {
				selected.add(unit.getSimulationUnit());
			}
		}
		if (deselected.isEmpty() && selected.isEmpty()) {
			return;
		}
		this.ui.unitOrderListener.selectUnits(simulationHandleIdsOf(deselected), simulationHandleIdsOf(selected));
	}

	private static int[] simulationHandleIdsOf(final List<CUnit> units) {
		final int[] handleIds = new int[units.size()];
		for (int i = 0; i < handleIds.length; i++) {
			handleIds[i] = units.get(i).getHandleId();
		}
		return handleIds;
	}

	void selectUnits(List<RenderUnit> selectedUnits) {
		if (selectedUnits.size() > WarsmashConstants.MAX_SELECTION_SIZE) {
			selectedUnits = new ArrayList<>(selectedUnits.subList(0, WarsmashConstants.MAX_SELECTION_SIZE));
		}
		final List<RenderUnit> prevSelectedUnits = this.ui.selectedUnits;
		this.ui.selectedUnits = selectedUnits;
		fireSelectionEvents(prevSelectedUnits, selectedUnits);
		if (!selectedUnits.isEmpty()) {
			final RenderUnit unit = selectedUnits.get(0);
			boolean selectionChanged = (unit != this.ui.selectedUnit)
					|| (prevSelectedUnits.size() != selectedUnits.size());
			for (int i = 0; (i < prevSelectedUnits.size()) && (i < selectedUnits.size()); i++) {
				if (prevSelectedUnits.get(i) != selectedUnits.get(i)) {
					selectionChanged = true;
				}
			}
			boolean playedNewSound = false;
			if (selectionChanged) {
				this.ui.selectedSoundCount = 0;
			}
			UnitSound USAudio = null;
			if ((unit.getSimulationUnit().getPlayerIndex() == this.ui.war3MapViewer.getLocalPlayerIndex())
					|| (((unit.getSimulationUnit().getUnitType().getRace() == CUnitRace.CRITTERS)
							|| (unit.getSimulationUnit().getUnitType().getRace() == CUnitRace.OTHER))
							&& (unit.getSimulationUnit().getPlayerIndex() == (this.ui.war3MapViewer.getGameData().getMaxPlayers() - 1)))) {
				if (unit.soundset != null) {
					UnitSound ackSoundToPlay = unit.soundset.what;
					int soundIndex;
					final int pissedSoundCount = unit.soundset.pissed.getSoundCount();
					if (unit.getSimulationUnit().isConstructing()) {
						ackSoundToPlay = this.ui.war3MapViewer.getUiSounds()
								.getSound(this.ui.rootFrame.getSkinField("ConstructingBuilding"));
						soundIndex = (int) (Math.random() * ackSoundToPlay.getSoundCount());
					}
					else {
						if ((this.ui.selectedSoundCount >= 3) && (pissedSoundCount > 0)) {
							soundIndex = this.ui.selectedSoundCount - 3;
							ackSoundToPlay = unit.soundset.pissed;
						}
						else {
							soundIndex = (int) (Math.random() * ackSoundToPlay.getSoundCount());
						}
					}
					if ((ackSoundToPlay != null) && ackSoundToPlay
							.playUnitResponse(this.ui.war3MapViewer.worldScene.audioContext, unit, soundIndex)) {
						this.ui.selectedSoundCount++;
						if ((this.ui.selectedSoundCount - 3) >= pissedSoundCount) {
							this.ui.selectedSoundCount = 0;
						}
						USAudio = ackSoundToPlay;
						playedNewSound = true;
					}
				}
			}
			else {
				this.ui.war3MapViewer.getUiSounds().getSound("InterfaceClick")
						.play(this.ui.uiScene.audioContext, 0, 0, 0);
			}
			if (selectionChanged) {
				for (final MultiSelectUnitStateListener listener : this.multiSelectUnitStateListeners) {
					listener.dispose();
				}
				this.multiSelectUnitStateListeners.clear();
				selectUnit(unit);
				if (selectedUnits.size() > 1) {
					int index = 0;
					for (final RenderUnit renderUnit : selectedUnits) {
						if (index >= this.ui.selectedUnitFrames.length) {
							break;
						}
						final MultiSelectUnitStateListener multiSelectUnitStateListener = new MultiSelectUnitStateListener(
								renderUnit, index++);
						renderUnit.getSimulationUnit().addStateListener(multiSelectUnitStateListener);
						this.multiSelectUnitStateListeners.add(multiSelectUnitStateListener);
					}
				}
			}
			if (playedNewSound) {
				this.ui.infoPanel.portraitTalk(USAudio);
			}
		}
		else {
			selectUnit(null);
		}
	}

	public boolean touchUp(final int screenX, final int screenY, final float worldScreenY, final int button) {
		if (button == Input.Buttons.FORWARD) {
			return false;
		}
		else if (button == Input.Buttons.BACK) {
			return false;
		}
		if (this.ui.scoreScreen.isVisible() && (this.ui.mouseDownUIFrame != null)) {
			MeleeUI.screenCoordsVector.set(screenX, screenY);
			this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector);
			final UIFrame clickedUIFrame = this.ui.rootFrame.touchUp(MeleeUI.screenCoordsVector.x,
					MeleeUI.screenCoordsVector.y, button);
			final ClickableFrame pressed = this.ui.mouseDownUIFrame;
			this.ui.mouseDownUIFrame = null;
			pressed.mouseUp(this.ui.rootFrame, this.ui.uiViewport);
			if (clickedUIFrame == pressed) {
				final String soundKey = pressed.getSoundKey();
				if (soundKey != null) {
					this.ui.war3MapViewer.getUiSounds().getSound(soundKey).play(this.ui.uiScene.audioContext, 0, 0, 0);
				}
				pressed.onClick(button);
			}
			return true;
		}
		if (!this.ui.userControlEnabled) {
			return false;
		}
		MeleeUI.screenCoordsVector.set(screenX, screenY);
		this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector);
		final UIFrame clickedUIFrame = this.ui.rootFrame.touchUp(MeleeUI.screenCoordsVector.x,
				MeleeUI.screenCoordsVector.y, button);
		if (this.ui.mouseDownUIFrame != null) {
			if (clickedUIFrame == this.ui.mouseDownUIFrame) {
				this.ui.mouseDownUIFrame.onClick(button);
				final String soundKey = this.ui.mouseDownUIFrame.getSoundKey();
				if (soundKey != null) {
					this.ui.war3MapViewer.getUiSounds().getSound(soundKey).play(this.ui.uiScene.audioContext, 0, 0, 0);
				}
			}
			this.ui.mouseDownUIFrame.mouseUp(this.ui.rootFrame, this.ui.uiViewport);
		}
		else {
			if (this.ui.draggingMouseButton == Input.Buttons.LEFT) {
				if (!this.ui.dragSelectPreviewUnits.isEmpty()) {
					if (this.ui.allowDrag && this.ui.isSelectionOn(MeleeUI.SELECT_OFF)) {
						final List<RenderWidget> selectedWidgets = new ArrayList<>();
						boolean foundGoal = false;
						for (final RenderUnit unit : this.ui.dragSelectPreviewUnits) {
							if ((unit.getSimulationUnit().getPlayerIndex() == this.ui.war3MapViewer
									.getLocalPlayerIndex()) && !unit.getSimulationUnit().isBuilding()) {
								foundGoal = true;
								selectedWidgets.add(unit);
							}
						}
						if (!foundGoal) {
							selectedWidgets.addAll(this.ui.dragSelectPreviewUnits);
						}
						final boolean shiftDown = isShiftDown();
						if (shiftDown) {
							for (final RenderUnit unit : this.ui.selectedUnits) {
								if (!selectedWidgets.contains(unit)) {
									selectedWidgets.add(unit);
								}
							}
						}

						this.ui.war3MapViewer.clearUnitMouseOverHighlight();

						this.ui.war3MapViewer.doSelectUnit(selectedWidgets);
						selectWidgets(selectedWidgets);
					}
					this.ui.dragSelectPreviewUnits.clear();
				}
				else {
					if (this.ui.allowDrag && this.ui.isSelectionOn(MeleeUI.SELECT_OFF)) {
						if (button == Input.Buttons.LEFT) {
							updateMouseOverUnit(screenX, worldScreenY);
							if ((this.ui.mouseOverUnit != null) && isUnitSelectable(this.ui.mouseOverUnit)) {
								final long currentMillis = TimeUtils.millis();
								final List<RenderWidget> unitList = new ArrayList<>();
								final boolean shiftDown = isShiftDown();
								final boolean controlDown = isControlDown()
										|| (((currentMillis - this.lastUnitClickTime) < 500)
												&& (this.ui.mouseOverUnit == this.lastClickUnit));
								if (shiftDown) {
									unitList.addAll(this.ui.selectedUnits);
								}
								if ((this.ui.mouseOverUnit instanceof RenderUnit) && controlDown) {
									processSelectNearbyUnits(unitList, shiftDown, (RenderUnit) this.ui.mouseOverUnit);
								}
								else {
									processClickSelect(unitList, shiftDown, this.ui.mouseOverUnit);
								}
								this.ui.war3MapViewer.doSelectUnit(unitList);
								selectWidgets(unitList);
								this.lastUnitClickTime = currentMillis;
								this.lastClickUnit = this.ui.mouseOverUnit;
							}
						}
					}
				}
			}
		}
		this.ui.mouseDownUIFrame = null;
		this.ui.draggingMouseButton = -1;
		this.ui.allowDrag = false;
		return false;
	}

	private void processSelectNearbyUnits(final List<RenderWidget> unitList, final boolean shiftDown,
			final RenderUnit mouseOverUnit) {
		this.ui.war3MapViewer.simulation.getWorldCollision().enumUnitsInRect(
				new Rectangle(this.ui.mouseOverUnit.getX() - 1024, this.ui.mouseOverUnit.getY() - 1024, 2048, 2048),
				new CUnitEnumFunction() {
					@Override
					public boolean call(final CUnit unit) {
						if (unit.getUnitType() == mouseOverUnit.getSimulationUnit().getUnitType()) {
							final RenderUnit renderPeer = MeleeUIInput.this.ui.war3MapViewer.getRenderPeer(unit);
							processClickSelect(unitList, shiftDown, renderPeer);
						}
						return false;
					}
				});
	}

	private void processClickSelect(final List<RenderWidget> unitList, final boolean shiftDown,
			final RenderWidget mouseOverUnit) {
		if (shiftDown) {
			if (this.ui.selectedUnits.contains(mouseOverUnit)) {
				unitList.remove(mouseOverUnit);
			}
			else {
				unitList.add(mouseOverUnit);
			}
		}
		else {
			unitList.add(mouseOverUnit);
		}
	}

	static boolean isShiftDown() {
		return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
				|| VirtualModifiers.isShiftDown();
	}

	static boolean isAltDown() {
		return Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
	}

	public boolean isOverSlider(final int screenX, final int screenY) {
		MeleeUI.screenCoordsVector.set(screenX, screenY);
		this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector);
		return ScrollBarFrame.isSliderPart(this.ui.rootFrame.getFrameChildUnderMouse(MeleeUI.screenCoordsVector.x,
				MeleeUI.screenCoordsVector.y));
	}

	static boolean isControlDown() {
		return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
	}

	public boolean touchDragged(final int screenX, final int screenY, final float worldScreenY, final int pointer) {
		if (!this.ui.userControlEnabled) {
			return false;
		}
		MeleeUI.screenCoordsVector.set(screenX, screenY);
		this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector);

		if (this.ui.meleeUIMinimap.containsMouse(MeleeUI.screenCoordsVector.x, MeleeUI.screenCoordsVector.y)) {
			final Vector2 worldPoint = this.ui.meleeUIMinimap.getWorldPointFromScreen(MeleeUI.screenCoordsVector.x,
					MeleeUI.screenCoordsVector.y);
			this.ui.cameraManager.jumpTo(worldPoint.x, worldPoint.y);
		}
		else {
			if (this.ui.allowDrag) {
				if (null != this.ui.mouseOverUnit) {
					this.ui.war3MapViewer.clearUnitMouseOverHighlight();
					this.ui.dragSelectPreviewUnits.clear();
					this.ui.mouseOverUnit = null;
				}

				this.ui.war3MapViewer.getClickLocationOnZPlane(MeleeUI.clickLocationTemp, screenX, (int) worldScreenY,
						this.ui.lastMouseDragStart.z);
				if (this.ui.draggingMouseButton == Input.Buttons.MIDDLE) {
					this.ui.cameraManager.stopPan();
					// The picked point moves with the camera, so the finger's own travel is what is left after taking
					// that out; scaling the picked point itself would only change how fast the grab settles.
					final float factor = GameOptions.get().getMouseScrollFactor();
					final Vector3 origin = this.dragCameraTargetOrigin;
					final Vector3 cameraNow = this.ui.lastMouseDragCameraTargetStart;
					this.ui.cameraManager.target.set(
							origin.x - (factor * ((MeleeUI.clickLocationTemp.x - this.ui.lastMouseDragStart.x)
									- (cameraNow.x - origin.x))),
							origin.y - (factor * ((MeleeUI.clickLocationTemp.y - this.ui.lastMouseDragStart.y)
									- (cameraNow.y - origin.y))),
							cameraNow.z);
				}
				else if (this.ui.draggingMouseButton == Input.Buttons.LEFT) {
					// update mouseover
				}
				this.ui.lastMouseClickLocation.set(MeleeUI.clickLocationTemp);
			}
			else {
				if (this.ui.mouseDownUIFrame != null) {
					this.ui.mouseDownUIFrame.mouseDragged(this.ui.rootFrame, this.ui.uiViewport,
							MeleeUI.screenCoordsVector.x, MeleeUI.screenCoordsVector.y);
				}
			}
		}
		return false;
	}

	public boolean isOverClickableUi(final int screenX, final int screenY) {
		MeleeUI.screenCoordsVector.set(screenX, screenY);
		this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector);
		// The panel parts with a tool tip count too: a long press on them shows the tip, it is no right click.
		return (this.ui.rootFrame.getFrameChildUnderMouse(MeleeUI.screenCoordsVector.x,
				MeleeUI.screenCoordsVector.y) instanceof ClickableFrame)
				|| (this.ui.tooltips.panelTipAt(MeleeUI.screenCoordsVector.x, MeleeUI.screenCoordsVector.y) != -1);
	}

	public void screenToWorld(final int screenX, final int screenY, final float[] out) {
		this.ui.war3MapViewer.getClickLocation(MeleeUI.clickLocationTemp, screenX, Gdx.graphics.getHeight() - screenY,
				false, false);
		out[0] = MeleeUI.clickLocationTemp.x;
		out[1] = MeleeUI.clickLocationTemp.y;
		out[2] = MeleeUI.clickLocationTemp.z;
	}

	public void worldToScreen(final float[] world, final float[] out) {
		MeleeUI.clickLocationTemp.set(world[0], world[1], world[2]);
		this.ui.war3MapViewer.worldScene.camera.worldToScreen(MeleeUI.screenCoordsVector, MeleeUI.clickLocationTemp);
		out[0] = MeleeUI.screenCoordsVector.x;
		out[1] = MeleeUI.screenCoordsVector.y;
	}

	public void cancelPointerDrag() {
		this.ui.draggingMouseButton = -1;
		this.ui.allowDrag = false;
	}

	public void endTouchHover() {
		if (this.ui.mouseOverUIFrame != null) {
			this.ui.mouseOverUIFrame.mouseExit(this.ui.rootFrame, this.ui.uiViewport);
			this.ui.mouseOverUIFrame = null;
		}
		this.ui.tooltipFrame.setVisible(false);
		this.ui.hoveredPanelTip = -1;
		this.ui.panelTipX = Float.NaN;
	}

	/** Shows the tool tip of the panel part under the point; a refresh reloads it for the changed values. */
	void updatePanelTip(final float x, final float y, final boolean refresh) {
		final int previous = this.ui.hoveredPanelTip;
		final int tip = this.ui.tooltips.panelTipAt(x, y);
		this.ui.hoveredPanelTip = tip;
		if (tip != -1) {
			if (((tip != previous) || refresh) && !this.ui.tooltips.loadPanelTip(tip)) {
				this.ui.tooltipFrame.setVisible(false);
			}
		}
		else if (previous != -1) {
			this.ui.tooltipFrame.setVisible(false);
		}
	}

	public boolean mouseMoved(final int screenX, final int screenY, final float worldScreenY) {
		MeleeUI.screenCoordsVector.set(screenX, screenY);
		this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector);
		final UIFrame mousedUIFrame = this.ui.rootFrame.getFrameChildUnderMouse(MeleeUI.screenCoordsVector.x,
				MeleeUI.screenCoordsVector.y);
		this.ui.tooltips.setPointer(MeleeUI.screenCoordsVector.x);
		if (mousedUIFrame != this.ui.mouseOverUIFrame) {
			if (this.ui.mouseOverUIFrame != null) {
				this.ui.mouseOverUIFrame.mouseExit(this.ui.rootFrame, this.ui.uiViewport);
			}
			if (mousedUIFrame instanceof ClickableFrame) {
				this.ui.mouseOverUIFrame = (ClickableFrame) mousedUIFrame;
				if (this.ui.mouseOverUIFrame != null) {
					this.ui.mouseOverUIFrame.mouseEnter(this.ui.rootFrame, this.ui.uiViewport);
				}
				if (mousedUIFrame instanceof ClickableActionFrame) {
					this.ui.tooltips.loadTooltip((ClickableActionFrame) mousedUIFrame);
				}
				else {
					this.ui.tooltips.loadFrameTooltip(mousedUIFrame);
				}
			}
			else {
				this.ui.mouseOverUIFrame = null;
				this.ui.tooltipFrame.setVisible(false);
			}
			this.ui.hoveredPanelTip = -1;
		}
		if (this.ui.mouseOverUIFrame == null) {
			this.ui.panelTipX = MeleeUI.screenCoordsVector.x;
			this.ui.panelTipY = MeleeUI.screenCoordsVector.y;
			updatePanelTip(this.ui.panelTipX, this.ui.panelTipY, false);
		}
		else {
			this.ui.panelTipX = Float.NaN;
		}
		final UIFrame hover = getHoveredFrame(this.ui.rootFrame, MeleeUI.screenCoordsVector.x,
				MeleeUI.screenCoordsVector.y, this.ui.includeFrames, this.ui.ignoreFrames);
		if (hover == null) {
			updateMouseOverUnit(screenX, worldScreenY);
			updateTrackedTrackable(screenX, worldScreenY);
		}
		else {
			this.ui.war3MapViewer.clearUnitMouseOverHighlight();
			this.ui.mouseOverUnit = null;
			this.trackedTrackable = null;
		}
		return false;
	}

	/** The local player's mouse came onto a trackable: its track event, once until the mouse leaves it. */
	private void updateTrackedTrackable(final int screenX, final float worldScreenY) {
		final CTrackable trackable = this.ui.userControlEnabled
				? this.ui.war3MapViewer.rayPickTrackable(screenX, worldScreenY)
				: null;
		if (trackable != this.trackedTrackable) {
			this.trackedTrackable = trackable;
			if (trackable != null) {
				this.ui.unitOrderListener.trackableEvent(trackable.getHandleId(), false);
			}
		}
	}

	/**
	 * A left click on a trackable fires its hit event and takes the click. A touch has no pointer resting on it
	 * before, so the tap tracks it first.
	 */
	private boolean hitTrackable(final int screenX, final float worldScreenY) {
		final CTrackable trackable = this.ui.war3MapViewer.rayPickTrackable(screenX, worldScreenY);
		if (trackable == null) {
			return false;
		}
		if (trackable != this.trackedTrackable) {
			this.trackedTrackable = trackable;
			this.ui.unitOrderListener.trackableEvent(trackable.getHandleId(), false);
		}
		this.ui.unitOrderListener.trackableEvent(trackable.getHandleId(), true);
		return true;
	}

	private void updateMouseOverUnit(final int screenX, final float worldScreenY) {
		final RenderWidget newMouseOverUnit;
		if (this.ui.userControlEnabled) {
			newMouseOverUnit = this.ui.war3MapViewer.rayPickUnit(screenX, worldScreenY, this.anyClickableUnitFilter);
		}
		else {
			newMouseOverUnit = null;
		}
		if (newMouseOverUnit != this.ui.mouseOverUnit) {
			this.ui.war3MapViewer.clearUnitMouseOverHighlight();
			this.ui.dragSelectPreviewUnits.clear();
			if ((newMouseOverUnit != null) && this.ui.isSelectionOn(MeleeUI.PRE_SELECT_UI_OFF)) {
				this.ui.war3MapViewer.showUnitMouseOverHighlight(newMouseOverUnit);
			}
			this.ui.mouseOverUnit = newMouseOverUnit;
		}
	}

	private final class MultiSelectUnitStateListener implements CUnitStateListener {
		private final RenderUnit sourceUnit;
		private int index;
		private boolean disposed = false;

		public MultiSelectUnitStateListener(final RenderUnit sourceUnit, final int index) {
			this.sourceUnit = sourceUnit;
			this.index = index;
		}

		public void dispose() {
			this.sourceUnit.getSimulationUnit().removeStateListener(this);
			this.disposed = true;
		}

		@Override
		public void lifeChanged() {
			if (this.disposed) {
				return;
			}
			if (this.sourceUnit.getSimulationUnit().isDead()) {
				removeSourceUnitFromSelection();
			}
			else {
				MeleeUIInput.this.ui.selectedUnitFrames[this.index]
						.setLifeRatioRemaining(this.sourceUnit.getSimulationUnit().getLife()
								/ this.sourceUnit.getSimulationUnit().getMaximumLife());
			}
		}

		@Override
		public void hideStateChanged() {
			if (this.disposed) {
				return;
			}
			if (this.sourceUnit.getSimulationUnit().isHidden()
					|| !this.sourceUnit.getSimulationUnit().isVisible(MeleeUIInput.this.ui.war3MapViewer.simulation,
							MeleeUIInput.this.ui.war3MapViewer.getLocalPlayerIndex())) {
				removeSourceUnitFromSelection();
			}
		}

		private void removeSourceUnitFromSelection() {
			MeleeUIInput.this.ui.selectedUnits.remove(this.sourceUnit);
			MeleeUIInput.this.ui.war3MapViewer.doUnselectUnit(this.sourceUnit);
			MeleeUIInput.this.multiSelectUnitStateListeners.remove(this.index);
			for (int i = this.index; i < MeleeUIInput.this.multiSelectUnitStateListeners.size(); i++) {
				MeleeUIInput.this.multiSelectUnitStateListeners.get(i).index--;
			}
			dispose();
			MeleeUIInput.this.ui.infoPanel.reloadSelectedUnitUI(MeleeUIInput.this.ui.selectedUnit);
		}

		@Override
		public void manaChanged() {
			if (this.disposed) {
				return;
			}
			MeleeUIInput.this.ui.selectedUnitFrames[this.index]
					.setManaRatioRemaining(this.sourceUnit.getSimulationUnit().getMana()
							/ this.sourceUnit.getSimulationUnit().getMaximumMana());
		}

		@Override
		public void ordersChanged() {
			if (this.disposed) {
				return;
			}

		}

		@Override
		public void queueChanged() {
			if (this.disposed) {
				return;
			}

		}

		@Override
		public void rallyPointChanged() {
			if (this.disposed) {
				return;
			}

		}

		@Override
		public void waypointsChanged() {
			if (this.disposed) {
				return;
			}

		}

		@Override
		public void heroStatsChanged() {
			if (this.disposed) {
				return;
			}

		}

		@Override
		public void inventoryChanged() {
			if (this.disposed) {
				return;
			}

		}

		@Override
		public void attacksChanged() {
			if (this.disposed) {
				return;
			}
		}

		@Override
		public void abilitiesChanged() {
			if (this.disposed) {
				return;
			}
		}

		@Override
		public void upgradesChanged() {
			if (this.disposed) {
				return;
			}
		}

	}

	public void scriptSelectUnit(final CUnit unit, final boolean select) {
		selectUnitFromScript(unit, select);
	}

	private void selectUnitFromScript(final CUnit unit, final boolean select) {
		final RenderUnit renderUnit = this.ui.war3MapViewer.getRenderPeer(unit);
		if (renderUnit == null) {
			return;
		}
		final List<RenderWidget> widgets = new ArrayList<>(this.ui.selectedUnits);
		if (select) {
			if (!widgets.contains(renderUnit)) {
				widgets.add(renderUnit);
			}
		}
		else {
			widgets.remove(renderUnit);
		}
		selectWidgets(widgets);
		this.ui.war3MapViewer.doSelectUnit(widgets);
	}

	public void scriptClearSelection() {
		final List<RenderWidget> none = new ArrayList<>();
		selectWidgets(none);
		this.ui.war3MapViewer.doSelectUnit(none);
	}
}
