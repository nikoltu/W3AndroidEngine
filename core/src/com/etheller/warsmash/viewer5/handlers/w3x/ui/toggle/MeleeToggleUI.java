package com.etheller.warsmash.viewer5.handlers.w3x.ui.toggle;

import com.etheller.warsmash.input.touch.PointKind;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.CineFilter;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.FrameEventSink;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.handlers.w3x.camera.GameCameraManager;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerColor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CLeaderboard;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.MeleeUI;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashBaseUI;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashToggleableUI;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashUI;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialogButton;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CTimerDialog;

public class MeleeToggleUI implements WarsmashUI {
	private final MeleeUI meleeUI;
	private WarsmashToggleableUI currentUI;
	private final List<WarsmashToggleableUI> allUIs;
	private int lastWidth;
	private int lastHeight;

	public MeleeToggleUI(final MeleeUI meleeUI, final List<WarsmashToggleableUI> allUIs) {
		this.meleeUI = meleeUI;
		this.allUIs = allUIs;
		this.currentUI = allUIs.get(0);
	}

	public MeleeUI getMeleeUI() {
		return this.meleeUI;
	}

	public void setCurrentUI(final int index) {
		if (this.currentUI != null) {
			this.currentUI.onHide();
		}
		this.currentUI = this.allUIs.get(index);
		this.currentUI.resize(this.lastWidth, this.lastHeight);
		this.currentUI.onShow();
	}

	@Override
	public void showInterfaceError(final int playerIndex, final String message) {
		this.meleeUI.showInterfaceError(playerIndex, message);
	}

	@Override
	public void showCommandErrorWithoutSound(final int playerIndex, final String message) {
		this.meleeUI.showCommandErrorWithoutSound(playerIndex, message);
	}

	@Override
	public void showUpgradeCompleteAlert(final int playerIndex, final War3ID queuedRawcode, final int level) {
		this.meleeUI.showUpgradeCompleteAlert(playerIndex, queuedRawcode, level);
	}

	@Override
	public void noteAlertPosition(final int playerIndex, final float x, final float y) {
		this.meleeUI.noteAlertPosition(playerIndex, x, y);
	}

	@Override
	public void unitAttacked(final CUnit unit, final CUnit attacker) {
		this.meleeUI.unitAttacked(unit, attacker);
	}

	@Override
	public void heroDied(final CUnit hero) {
		this.meleeUI.heroDied(hero);
	}

	@Override
	public CScriptDialog createScriptDialog(final GlobalScope globalScope) {
		return this.meleeUI.createScriptDialog(globalScope);
	}

	@Override
	public void clearDialog(final CScriptDialog dialog) {
		this.meleeUI.clearDialog(dialog);
	}

	@Override
	public void destroyDialog(final CScriptDialog dialog) {
		this.meleeUI.destroyDialog(dialog);
	}

	@Override
	public void displayScriptDialog(final CScriptDialog dialog, final boolean flag) {
		this.meleeUI.displayScriptDialog(dialog, flag);
	}

	@Override
	public void setScriptDialogTitle(final CScriptDialog dialog, final String text) {
		this.meleeUI.setScriptDialogTitle(dialog, text);
	}

	@Override
	public CScriptDialogButton createScriptDialogButton(final CScriptDialog dialog, final String buttonText,
			final char hotkeyInt) {
		return this.meleeUI.createScriptDialogButton(dialog, buttonText, hotkeyInt);
	}

	@Override
	public void quitMission() {
		this.meleeUI.quitMission();
	}

	@Override
	public void endGame(final boolean showScoreScreen) {
		this.meleeUI.endGame(showScoreScreen);
	}

	@Override
	public GameCameraManager getCameraManager() {
		return this.meleeUI.getCameraManager();
	}

	@Override
	public void main() {
		for (final WarsmashBaseUI ui : this.allUIs) {
			ui.main();
		}
	}

	@Override
	public void update(final float deltaTime) {
		for (final WarsmashBaseUI ui : this.allUIs) {
			ui.update(deltaTime);
		}
	}

	@Override
	public void render(final SpriteBatch batch, final GlyphLayout glyphLayout) {
		this.currentUI.render(batch, glyphLayout);
	}

	@Override
	public void renderModal(final SpriteBatch batch, final GlyphLayout glyphLayout) {
		this.currentUI.renderModal(batch, glyphLayout);
	}

	@Override
	public void dispose() {
		for (final WarsmashBaseUI ui : this.allUIs) {
			ui.dispose();
		}
	}

	@Override
	public void resize(final int width, final int height) {
		this.lastWidth = width;
		this.lastHeight = height;
		this.currentUI.resize(width, height);
	}

	@Override
	public boolean keyDown(final int keycode) {
		if ((keycode == Input.Keys.Z)
				&& (Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT))) {
			setCurrentUI((this.allUIs.indexOf(this.currentUI) + 1) % this.allUIs.size());
			return true;
		}
		return this.currentUI.keyDown(keycode);
	}

	@Override
	public boolean keyUp(final int keycode) {
		return this.currentUI.keyUp(keycode);
	}

	@Override
	public boolean touchDown(final int screenX, final int screenY, final float worldScreenY, final int button) {
		return this.currentUI.touchDown(screenX, screenY, worldScreenY, button);
	}

	@Override
	public boolean touchUp(final int screenX, final int screenY, final float worldScreenY, final int button) {
		return this.currentUI.touchUp(screenX, screenY, worldScreenY, button);
	}

	@Override
	public boolean touchDragged(final int screenX, final int screenY, final float worldScreenY, final int pointer) {
		return this.currentUI.touchDragged(screenX, screenY, worldScreenY, pointer);
	}

	@Override
	public boolean mouseMoved(final int screenX, final int screenY, final float worldScreenY) {
		return this.currentUI.mouseMoved(screenX, screenY, worldScreenY);
	}

	@Override
	public boolean scrolled(final float amountX, final float amountY) {
		return this.currentUI.scrolled(amountX, amountY);
	}

	@Override
	public void gameClosed() {
		for (final WarsmashBaseUI ui : this.allUIs) {
			ui.gameClosed();
		}
	}

	@Override
	public Music playMusic(final String musicField, final boolean random, final int index) {
		return this.meleeUI.playMusic(musicField, random, index);
	}

	@Override
	public void pingMinimap(final float x, final float y, final float duration) {
		this.meleeUI.pingMinimap(x, y, duration);
	}

	@Override
	public void pingMinimap(final float x, final float y, final float duration, final int red, final int green,
			final int blue, final boolean extraEffects) {
		this.meleeUI.pingMinimap(x, y, duration, red, green, blue, extraEffects);
	}

	@Override
	public void setLeaderboard(final CLeaderboard leaderboard) {
		this.meleeUI.setLeaderboard(leaderboard);
	}

	@Override
	public void removeTimerDialog(final CTimerDialog dialog) {
		this.meleeUI.removeTimerDialog(dialog);
	}

	@Override
	public Music setMapMusic(String musicField, boolean random, int index) {
		return this.meleeUI.setMapMusic(musicField, random, index);
	}

	@Override
	public void playMapMusic() {
		this.meleeUI.playMapMusic();
	}

	@Override
	public Music playMusicEx(String musicField, boolean random, int index, int fromMSecs, int fadeInMSecs) {
		return this.meleeUI.playMusicEx(musicField, random, index, fromMSecs, fadeInMSecs);
	}

	@Override
	public void stopMusic(boolean fadeOut) {
		this.meleeUI.stopMusic(fadeOut);
	}

	@Override
	public void resumeMusic() {
		this.meleeUI.resumeMusic();
	}

	@Override
	public void setMusicVolume(int volume) {
		this.meleeUI.setMusicVolume(volume);
	}

	@Override
	public void setMusicPlayPosition(int millisecs) {
		this.meleeUI.setMusicPlayPosition(millisecs);
	}

	@Override
	public Scene getUiScene() {
		return this.meleeUI.getUiScene();
	}

	@Override
	public CTimerDialog createTimerDialog(final CTimer timer) {
		return this.meleeUI.createTimerDialog(timer);
	}

	@Override
	public void runSimulationCalls() {
		this.meleeUI.runSimulationCalls();
	}

	@Override
	public boolean hasSimulationCalls() {
		return this.meleeUI.hasSimulationCalls();
	}

	@Override
	public void scriptSelectUnit(final CUnit unit, final boolean select) {
		this.meleeUI.scriptSelectUnit(unit, select);
	}

	@Override
	public void scriptClearSelection() {
		this.meleeUI.scriptClearSelection();
	}

	@Override
	public boolean isOverSlider(final int screenX, final int screenY) {
		return this.meleeUI.isOverSlider(screenX, screenY);
	}

	@Override
	public boolean isOverClickableUi(final int screenX, final int screenY) {
		return this.meleeUI.isOverClickableUi(screenX, screenY);
	}

	@Override
	public boolean isOverInventorySlot(final int screenX, final int screenY) {
		return this.meleeUI.isOverInventorySlot(screenX, screenY);
	}

	@Override
	public boolean isOverAutocastButton(final int screenX, final int screenY) {
		return this.meleeUI.isOverAutocastButton(screenX, screenY);
	}

	@Override
	public boolean hasActiveCommand() {
		return this.meleeUI.hasActiveCommand();
	}

	@Override
	public void screenToWorld(final int screenX, final int screenY, final float[] out) {
		this.meleeUI.screenToWorld(screenX, screenY, out);
	}

	@Override
	public void worldToScreen(final float[] world, final float[] out) {
		this.meleeUI.worldToScreen(world, out);
	}

	@Override
	public boolean isPlacingBuilding() {
		return this.meleeUI.isPlacingBuilding();
	}

	@Override
	public void cancelPointerDrag() {
		this.meleeUI.cancelPointerDrag();
	}

	@Override
	public CPlayerUnitOrderListener getUnitOrderListener() {
		return this.meleeUI.getUnitOrderListener();
	}

	@Override
	public UIFrame getOriginFrame(final int originType, final int index) {
		return this.meleeUI.getOriginFrame(originType, index);
	}

	@Override
	public void setFrameEventSink(final FrameEventSink sink) {
		this.meleeUI.setFrameEventSink(sink);
	}

	@Override
	public void setScriptFocus(final UIFrame frame, final boolean focus) {
		this.meleeUI.setScriptFocus(frame, focus);
	}

	@Override
	public void endTouchHover() {
		this.meleeUI.endTouchHover();
	}

	@Override
	public int touchOverlaySlotAt(final int screenX, final int screenY) {
		return this.meleeUI.touchOverlaySlotAt(screenX, screenY);
	}

	@Override
	public PointKind pointKindAt(final int screenX, final int screenY) {
		return this.meleeUI.pointKindAt(screenX, screenY);
	}

	@Override
	public boolean hasOrderableSelection() {
		return this.meleeUI.hasOrderableSelection();
	}

	@Override
	public void selectControlGroupSlot(final int slot) {
		this.meleeUI.selectControlGroupSlot(slot);
	}

	@Override
	public void assignControlGroupSlot(final int slot, final boolean add) {
		this.meleeUI.assignControlGroupSlot(slot, add);
	}

	@Override
	public void removedUnit(final CUnit whichUnit) {
		this.meleeUI.removedUnit(whichUnit);
	}

	@Override
	public void removedItem(final CItem whichItem) {
		this.meleeUI.removedItem(whichItem);
	}

	@Override
	public void displayTimedText(final float x, final float y, final float duration, final String message) {
		this.meleeUI.displayTimedText(x, y, duration, message);
	}

	@Override
	public void clearTextMessages() {
		this.meleeUI.clearTextMessages();
	}

	@Override
	public void showInterface(boolean show, float fadeDuration) {
		this.meleeUI.showInterface(show, fadeDuration);
	}

	@Override
	public void setCinematicScene(int portraitUnitId, CPlayerColor color, String speakerTitle, String text,
			float sceneDuration, float voiceoverDuration) {
		this.meleeUI.setCinematicScene(portraitUnitId, color, speakerTitle, text, sceneDuration, voiceoverDuration);
	}

	@Override
	public void dialogueSoundStarted(final String soundPath) {
		this.meleeUI.dialogueSoundStarted(soundPath);
	}

	@Override
	public void enableUserControl(boolean value) {
		this.meleeUI.enableUserControl(value);
	}

	@Override
	public boolean skipCinematic() {
		return this.meleeUI.skipCinematic();
	}

	@Override
	public void enableUserUI(final boolean enable) {
		this.meleeUI.enableUserUI(enable);
	}

	@Override
	public CineFilter getCineFilter() {
		return this.meleeUI.getCineFilter();
	}

	@Override
	public void endCinematicScene() {
		this.meleeUI.endCinematicScene();
	}

	@Override
	public void forceCinematicSubtitles(boolean value) {
		this.meleeUI.forceCinematicSubtitles(value);
	}

	@Override
	public void hideCinematicPanels(final boolean hide) {
		this.meleeUI.hideCinematicPanels(hide);
	}

	@Override
	public void flashQuestDialogButton() {
		this.meleeUI.flashQuestDialogButton();
	}

	@Override
	public void forceQuestDialogUpdate() {
		this.meleeUI.forceQuestDialogUpdate();
	}
}
