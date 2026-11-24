package com.etheller.warsmash.viewer5.handlers.w3x.ui;


import com.badlogic.gdx.audio.Music;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.warsmash.input.touch.PointKind;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.handlers.w3x.camera.GameCameraManager;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CLeaderboard;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerColor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.CommandErrorListener;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialogButton;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CTimerDialog;

public interface WarsmashUI extends CommandErrorListener, WarsmashBaseUI {

	/** The listener this machine's input goes through, or null when the game has none (a test). */
	default CPlayerUnitOrderListener getUnitOrderListener() {
		return null;
	}

	/** The unit under the pointer now, or null. */
	default CUnit getMouseFocusUnit() {
		return null;
	}

	/**
	 * A frame the game's own interface is made of, for BlzGetOriginFrame: the ORIGIN_FRAME_* type of Reforged and the
	 * index among those of its kind. Null when this interface has no such frame.
	 */
	default UIFrame getOriginFrame(final int originType, final int index) {
		return null;
	}

	/** Where the interface tells the script what the player does to its frames; null for none. */
	default void setFrameEventSink(final FrameEventSink sink) {
	}

	/** BlzFrameSetFocus: a text box takes the typed keys, or gives them back to the game. */
	default void setScriptFocus(final UIFrame frame, final boolean focus) {
	}

	CScriptDialog createScriptDialog(GlobalScope globalScope);

	void clearDialog(CScriptDialog dialog);

	void destroyDialog(CScriptDialog dialog);

	/** DialogDisplay: shows or hides the dialog; one shown now is the dialog shown last. */
	void displayScriptDialog(CScriptDialog dialog, boolean flag);

	/** DialogSetMessage. */
	void setScriptDialogTitle(CScriptDialog dialog, String text);

	CScriptDialogButton createScriptDialogButton(CScriptDialog dialog, String buttonText, char hotkeyInt);

	void quitMission();

	void endGame(boolean showScoreScreen);

	GameCameraManager getCameraManager();

	Music playMusic(String musicField, boolean random, int index);

	Music setMapMusic(String musicField, boolean random, int index);

	void playMapMusic();

	Music playMusicEx(String musicField, boolean random, int index, int fromMSecs, int fadeInMSecs);

	void stopMusic(boolean fadeOut);

	void resumeMusic();

	void setMusicVolume(int volume);

	void setMusicPlayPosition(int millisecs);

	Scene getUiScene();

	CTimerDialog createTimerDialog(CTimer timer);

	void scriptSelectUnit(CUnit unit, boolean select);

	/** Runs what the simulation asked of the interface while it ran its step on its own thread. */
	default void runSimulationCalls() {
	}

	/** Whether calls the simulation thread made wait for {@link #runSimulationCalls}; any thread. */
	default boolean hasSimulationCalls() {
		return false;
	}

	void scriptClearSelection();

	boolean isOverClickableUi(int screenX, int screenY);

	boolean isOverSlider(int screenX, int screenY);

	boolean isOverInventorySlot(int screenX, int screenY);

	boolean isOverAutocastButton(int screenX, int screenY);

	boolean hasActiveCommand();

	boolean isPlacingBuilding();

	void screenToWorld(int screenX, int screenY, float[] out);

	void worldToScreen(float[] world, float[] out);

	void cancelPointerDrag();

	void endTouchHover();

	boolean skipCinematic();

	/** What lies under the screen point, for orders given by double tap. */
	PointKind pointKindAt(int screenX, int screenY);

	boolean hasOrderableSelection();

	int touchOverlaySlotAt(int screenX, int screenY);

	void selectControlGroupSlot(int slot);

	void assignControlGroupSlot(int slot, boolean add);

	void removedUnit(CUnit whichUnit);

	void removedItem(CItem whichItem);

	void displayTimedText(float x, float y, float duration, String message);

	void clearTextMessages();

	void showInterface(boolean show, float fadeDuration);

	void setCinematicScene(int portraitUnitId, CPlayerColor color, String speakerTitle, String text,
			float sceneDuration, float voiceoverDuration);

	/** A script started the sound file of a dialogue line, which the portrait of a scene may lip sync to. */
	default void dialogueSoundStarted(final String soundPath) {
	}

	void enableUserControl(boolean value);

	void enableUserUI(boolean enable);

	CineFilter getCineFilter();

	void endCinematicScene();

	void forceCinematicSubtitles(boolean value);

	void hideCinematicPanels(boolean hide);

	void flashQuestDialogButton();

	void forceQuestDialogUpdate();

	void pingMinimap(float x, float y, float duration);

	void pingMinimap(float x, float y, float duration, int red, int green, int blue, boolean extraEffects);

	void setLeaderboard(CLeaderboard leaderboard);

	void removeTimerDialog(CTimerDialog dialog);

}
