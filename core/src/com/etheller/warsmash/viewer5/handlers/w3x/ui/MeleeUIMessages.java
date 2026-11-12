package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.TimeUtils;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.AnchorDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.TextJustify;
import com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CPlayerStateListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUpgradeType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUpgradeType.UpgradeLevel;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CLeaderboard;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialogButton;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CTimerDialog;

/**
 * What the game and its scripts tell the player: game and error messages, timer dialogs, the
 * leaderboard and script dialogs.
 */
final class MeleeUIMessages {
	private static final long WORLD_FRAME_MESSAGE_FADEOUT_MILLIS = TimeUnit.SECONDS.toMillis(9);
	private static final long WORLD_FRAME_MESSAGE_EXPIRE_MILLIS = TimeUnit.SECONDS.toMillis(10);
	private static final long WORLD_FRAME_MESSAGE_FADE_DURATION = WORLD_FRAME_MESSAGE_EXPIRE_MILLIS
			- WORLD_FRAME_MESSAGE_FADEOUT_MILLIS;
	private StringFrame errorMessageFrame;
	private final ArrayDeque<GameMessage> gameMessages = new ArrayDeque<>();
	// Measured on 1.29: a message is shown fully for its duration and then fades out over 3 s; 0 shows it until it is
	// cleared or scrolled away. New messages come in at the bottom line and push the older ones up; a message that
	// has faded out keeps its lines empty.
	private static final long GAME_MESSAGE_FADE_MILLIS = 3000;
	private static final float GAME_MESSAGE_LEFT = 0.0527f;
	private static final float GAME_MESSAGE_BOTTOM = 0.2405f;
	private static final float GAME_MESSAGE_WIDTH = 0.495f;
	private static final int GAME_MESSAGE_MAX_LINES = 16;
	/**
	 * Game messages keep their font height between lines, but 1.29 draws their letters at four fifths of it, where
	 * other text has letters of its full font height.
	 */
	private static final float GAME_MESSAGE_GLYPH_SCALE = 0.8f;

	// The x and y of the last script message move all of them, by about this much per 1.
	private static final float GAME_MESSAGE_OFFSET_X_SCALE = 0.308f;
	private static final float GAME_MESSAGE_OFFSET_Y_SCALE = 0.223f;
	private float gameMessageOffsetX;
	private float gameMessageOffsetY;
	private float gameMessageLift;
	private long lastErrorMessageExpireTime;
	private long lastErrorMessageFadeTime;
	/** Game time: it stands while the game is paused and runs with the game speed. */
	private double gameClockMillis;
	private CLeaderboard shownLeaderboard;
	private UIFrame leaderboardFrame;
	private StringFrame leaderboardTitle;
	private StringFrame leaderboardLabels;
	private StringFrame leaderboardValues;
	private float timerDialogRight = Float.NaN;
	private float worldFrameUnitMessageFontHeight;
	private static final float TIMER_DIALOG_RIGHT = -0.0055f;
	private static final float TIMER_DIALOG_TOP = -0.0353f;
	private static final float TIMER_DIALOG_STEP = 0.03f;
	private static final float TIMER_DIALOG_GAP = 0.002f;
	private static final float MULTIBOARD_TOP = -0.0236f;
	// Measured on 1.29: shown 1 s after the death, fully for 10 s, then fading out.
	private static final long HERO_DEATH_MESSAGE_DELAY_MILLIS = 1000;
	private static final float HERO_DEATH_MESSAGE_SECONDS = 10;
	private final ArrayDeque<PendingHeroDeath> pendingHeroDeaths = new ArrayDeque<>();
	// Measured on 1.29: shown fully for 10 s, then fading out; gold on the screen, plain in the log.
	private static final float SHARED_CONTROL_MESSAGE_SECONDS = 10;
	private static final Color SHARED_CONTROL_MESSAGE_COLOR = new Color(0xFED312FF);
	private SharedControlWatcher[] sharedControlWatchers;
	// Measured on 1.29: the box takes its width once, when a leaderboard is first shown in the game, from what that
	// board holds then (the widest label and value, with the insets and the space between them, or the title); later
	// boards and later items keep it, and what does not fit is cut.
	private static final float LEADERBOARD_MIN_WIDTH = 0.0367f;
	private static final float LEADERBOARD_TEXT_SPACE = 0.0647f;
	private static final float LEADERBOARD_TITLE_LEFT = 0.02f;
	private static final float LEADERBOARD_TITLE_OVERHANG = 0.003f;
	private static final float LEADERBOARD_VALUE_GAP = 0.004f;
	private static final float LEADERBOARD_UNDER_BOARD_GAP = 0.0053f;
	private float leaderboardWidth = Float.NaN;
	private float leaderboardRight = Float.NaN;
	private float leaderboardTop = Float.NaN;
	private static final float LEADERBOARD_FONT_HEIGHT = 0.011f;
	private static final float LEADERBOARD_INSET_X = 0.016f;
	private static final float LEADERBOARD_TITLE_TOP = 0.013f;
	private static final float LEADERBOARD_ROWS_TOP = 0.028f;
	private static final float LEADERBOARD_ROW_HEIGHT = 0.014f;
	private static final float LEADERBOARD_BOTTOM_INSET = 0.012f;

	final MeleeUI ui;

	MeleeUIMessages(final MeleeUI ui) {
		this.ui = ui;
	}

	void createErrorMessageFrame() {
		final Element fontHeights = this.ui.war3MapViewer.miscData.get("FontHeights");
		final float worldFrameMessageFontHeight = fontHeights.getFieldFloatValue("WorldFrameMessage");
		this.errorMessageFrame = this.ui.rootFrame.createStringFrame("SmashErrorMessageFrame", this.ui.rootFrame,
				new Color(0xFFCC00FF), TextJustify.LEFT, TextJustify.MIDDLE, worldFrameMessageFontHeight);
		this.errorMessageFrame.addAnchor(new AnchorDefinition(FramePoint.BOTTOMLEFT,
				GameUI.convertX(this.ui.uiViewport, 0.212f), GameUI.convertY(this.ui.uiViewport, 0.182f)));
		this.errorMessageFrame.setWidth(GameUI.convertX(this.ui.uiViewport, 0.35f));
		this.errorMessageFrame.setHeight(GameUI.convertY(this.ui.uiViewport, worldFrameMessageFontHeight));

		this.errorMessageFrame.setFontShadowColor(new Color(0f, 0f, 0f, 0.9f));
		this.errorMessageFrame.setFontShadowOffsetX(GameUI.convertX(this.ui.uiViewport, 0.001f));
		this.errorMessageFrame.setFontShadowOffsetY(GameUI.convertY(this.ui.uiViewport, -0.001f));
		this.errorMessageFrame.setVisible(false);

		this.worldFrameUnitMessageFontHeight = fontHeights.getFieldFloatValue("WorldFrameUnitMessage");
	}

	public void showInterfaceError(final int playerIndex, final String externStringKey) {
		if (this.ui.war3MapViewer.isViewedPlayer(playerIndex)) {
			if (externStringKey != null) {
				showLocalCommandErrorString(playerIndex, externStringKey);
			}
			getUiSoundForError(externStringKey).play(this.ui.uiScene.audioContext, 0, 0, 0);
		}
	}

	UnitSound getUiSoundForError(final String externStringKey) {
		String sound = "InterfaceError";
		final String soundKey = externStringKey + "Sound";
		if (this.ui.rootFrame.hasSkinField(soundKey)) {
			sound = this.ui.rootFrame.getSkinField(soundKey);
		}
		return this.ui.war3MapViewer.getUiSounds().getSound(sound);
	}

	public void showCommandErrorWithoutSound(final int playerIndex, final String message) {
		if (this.ui.war3MapViewer.isViewedPlayer(playerIndex)) {
			showLocalCommandErrorString(playerIndex, message);
		}
	}

	private void showLocalCommandErrorString(final int playerIndex, final String message) {
		String errorString = this.ui.rootFrame.getErrorString(message);
		if (errorString.isEmpty() && !message.isEmpty()) {
			errorString = message; // this may show some NOTEXTERN engine garbage that we should fix later
		}
		innerShowLocalCommandErrorString(playerIndex, errorString);
	}

	private void innerShowLocalCommandErrorString(final int playerIndex, final String message) {
		this.ui.rootFrame.setText(this.errorMessageFrame, message);
		this.errorMessageFrame.setVisible(true);
		final long millis = TimeUtils.millis();
		this.lastErrorMessageExpireTime = millis + WORLD_FRAME_MESSAGE_EXPIRE_MILLIS;
		this.lastErrorMessageFadeTime = millis + WORLD_FRAME_MESSAGE_FADEOUT_MILLIS;
		this.errorMessageFrame.setAlpha(1.0f);
	}

	private static final class GameMessage {
		private final StringFrame stringFrame;
		private final int lines;
		private final long fadeTime;
		private final long expireTime;
		private boolean expired;

		private GameMessage(final StringFrame stringFrame, final int lines, final long fadeTime,
				final long expireTime) {
			this.stringFrame = stringFrame;
			this.lines = lines;
			this.fadeTime = fadeTime;
			this.expireTime = expireTime;
		}
	}

	/** Messages stack up from their bottom line; a chat line lifted by the on-screen keyboard can reach it. */
	void liftGameMessages() {
		float lift = 0;
		final float chatTop = this.ui.chat.entryTop();
		if (!this.gameMessages.isEmpty() && (chatTop != Float.NEGATIVE_INFINITY)) {
			final float bottom = this.ui.rootFrame.getFramePointY(FramePoint.BOTTOM)
					+ GameUI.convertY(this.ui.uiViewport, GAME_MESSAGE_BOTTOM);
			lift = Math.max(0, (chatTop + GameUI.convertY(this.ui.uiViewport, 0.005f)) - bottom);
		}
		if (lift == this.gameMessageLift) {
			return;
		}
		this.gameMessageLift = lift;
		layoutGameMessages();
	}

	private void layoutGameMessages() {
		final float left = GameUI.convertX(this.ui.uiViewport,
				GAME_MESSAGE_LEFT + (this.gameMessageOffsetX * GAME_MESSAGE_OFFSET_X_SCALE));
		float bottom = GameUI.convertY(this.ui.uiViewport,
				GAME_MESSAGE_BOTTOM + (this.gameMessageOffsetY * GAME_MESSAGE_OFFSET_Y_SCALE)) + this.gameMessageLift;
		final Iterator<GameMessage> descendingIterator = this.gameMessages.descendingIterator();
		while (descendingIterator.hasNext()) {
			final GameMessage gameMessage = descendingIterator.next();
			if (!gameMessage.expired) {
				gameMessage.stringFrame.addAnchor(new AnchorDefinition(FramePoint.BOTTOMLEFT, left, bottom));
				gameMessage.stringFrame.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
			}
			bottom += gameMessage.stringFrame.getAssignedHeight();
		}
	}

	public void showGameMessage(final String message, final float duration) {
		showGameMessage(message, Color.WHITE, duration);
	}

	private long gameClock() {
		return (long) this.gameClockMillis;
	}

	/** Called every frame with the game time that passed, 0 while the game is paused. */
	void advanceGameClock(final float gameSeconds) {
		this.gameClockMillis += gameSeconds * 1000.0;
	}

	private void showGameMessage(final String message, final Color color, final float duration) {
		this.ui.messageLog.add(message);
		// The score screen replaces the game screen; messages that come after it are only kept in the log.
		if ((this.ui.scoreScreen != null) && this.ui.scoreScreen.isVisible()) {
			return;
		}
		final StringFrame gameMessagesFrame = this.ui.rootFrame.createStringFrame("SmashGameMessageFrame",
				this.ui.rootFrame, color, TextJustify.LEFT, TextJustify.MIDDLE,
				this.worldFrameUnitMessageFontHeight * GAME_MESSAGE_GLYPH_SCALE);
		gameMessagesFrame.setLineSpacing(GameUI.convertY(this.ui.uiViewport, this.worldFrameUnitMessageFontHeight));
		gameMessagesFrame.setWidth(GameUI.convertX(this.ui.uiViewport, GAME_MESSAGE_WIDTH));

		gameMessagesFrame.setFontShadowColor(new Color(0f, 0f, 0f, 0.9f));
		gameMessagesFrame.setFontShadowOffsetX(GameUI.convertX(this.ui.uiViewport, 0.001f));
		gameMessagesFrame.setFontShadowOffsetY(GameUI.convertY(this.ui.uiViewport, -0.001f));

		this.ui.rootFrame.setText(gameMessagesFrame, message);
		gameMessagesFrame.setHeight(gameMessagesFrame.getPredictedViewportHeight());
		gameMessagesFrame.setVisible(true);
		gameMessagesFrame.setAlpha(1.0f);

		final int lines = Math.max(1,
				Math.round(gameMessagesFrame.getAssignedHeight() / gameMessagesFrame.getLineSpacing()));
		final long fadeTime;
		final long expireTime;
		if (duration == 0) {
			fadeTime = Long.MAX_VALUE;
			expireTime = Long.MAX_VALUE;
		}
		else {
			fadeTime = TimeUtils.millis() + (long) (Math.max(0, duration) * 1000);
			expireTime = fadeTime + GAME_MESSAGE_FADE_MILLIS;
		}
		this.gameMessages.addLast(new GameMessage(gameMessagesFrame, lines, fadeTime, expireTime));
		int shownLines = 0;
		final Iterator<GameMessage> descendingIterator = this.gameMessages.descendingIterator();
		while (descendingIterator.hasNext()) {
			final GameMessage gameMessage = descendingIterator.next();
			shownLines += gameMessage.lines;
			if (shownLines > GAME_MESSAGE_MAX_LINES) {
				removeGameMessage(gameMessage);
				descendingIterator.remove();
			}
		}
		layoutGameMessages();
	}

	private void removeGameMessage(final GameMessage gameMessage) {
		if (!gameMessage.expired) {
			gameMessage.expired = true;
			this.ui.rootFrame.remove(gameMessage.stringFrame);
		}
	}

	/**
	 * A hero of the local player or of an ally died: as on 1.29, a second of game time later "<name> the <class>
	 * (level N) has fallen." in the wording of the local player's race, the name in the owner's color, with the
	 * race's hero death sound, or its ally hero death sound for an ally's hero. A paused game holds the message
	 * back; once shown, it fades in real time like any other message.
	 */
	void heroDied(final CUnit hero) {
		if (hero.getHeroData() == null) {
			return;
		}
		final int localPlayerIndex = this.ui.war3MapViewer.getLocalPlayerIndex();
		final CPlayer owner = this.ui.war3MapViewer.simulation.getPlayer(hero.getPlayerIndex());
		final boolean own = hero.getPlayerIndex() == localPlayerIndex;
		if (!own && ((owner == null) || (localPlayerIndex < 0) || !isHeroDeathAlly(owner, localPlayerIndex))) {
			return;
		}
		final String format = this.ui.rootFrame.getErrorString("Herodeath");
		final String name = "|cff" + MeleeChat.playerTextHex(owner.getColor()) + hero.getHeroData().getProperName()
				+ "|r";
		final String message = (format == null) || format.isEmpty() ? null
				: format.replaceFirst("%s", Matcher.quoteReplacement(name))
						.replaceFirst("%s", Matcher.quoteReplacement(hero.getUnitType().getName()))
						.replaceFirst("%d", Integer.toString(hero.getHeroData().getHeroLevel()));
		this.pendingHeroDeaths.addLast(new PendingHeroDeath(gameClock() + HERO_DEATH_MESSAGE_DELAY_MILLIS, message,
				own ? "HeroDiesSound" : "AllyHeroDiesSound"));
	}

	/** Measured on 1.29: an ally answering calls for help gives the message; passive or shared vision alone not. */
	private boolean isHeroDeathAlly(final CPlayer owner, final int localPlayerIndex) {
		final CPlayer local = this.ui.war3MapViewer.simulation.getPlayer(localPlayerIndex);
		return owner.hasAlliance(localPlayerIndex, CAllianceType.PASSIVE)
				&& local.hasAlliance(owner.getId(), CAllianceType.PASSIVE)
				&& owner.hasAlliance(localPlayerIndex, CAllianceType.HELP_RESPONSE);
	}

	/**
	 * Listens to every other player for it giving the local player shared control of its units or taking it away.
	 * Again after a load, since the listeners are not part of a saved game.
	 */
	void watchSharedControl() {
		final List<CPlayer> players = this.ui.war3MapViewer.simulation.getPlayers();
		if ((this.sharedControlWatchers == null) || (this.sharedControlWatchers.length != players.size())) {
			this.sharedControlWatchers = new SharedControlWatcher[players.size()];
		}
		final int localPlayerIndex = this.ui.war3MapViewer.getLocalPlayerIndex();
		for (int i = 0; i < players.size(); i++) {
			if (i != localPlayerIndex) {
				if (this.sharedControlWatchers[i] == null) {
					this.sharedControlWatchers[i] = new SharedControlWatcher(i);
				}
				players.get(i).addStateListener(this.sharedControlWatchers[i]);
			}
		}
	}

	/** As on 1.29, the owner's name in its player color. */
	private void sharedControlChanged(final int ownerIndex, final boolean granted) {
		final String format = this.ui.rootFrame.getErrorString(granted ? "Controlgranted" : "Controlrevoked");
		if ((format == null) || format.isEmpty()) {
			return;
		}
		final CPlayer owner = this.ui.war3MapViewer.simulation.getPlayer(ownerIndex);
		final String name = "|cff" + MeleeChat.playerTextHex(owner.getColor()) + owner.getName() + "|r";
		showGameMessage(format.replaceFirst("%s", Matcher.quoteReplacement(name)), SHARED_CONTROL_MESSAGE_COLOR,
				SHARED_CONTROL_MESSAGE_SECONDS);
	}

	private final class SharedControlWatcher implements CPlayerStateListener {
		private final int playerIndex;

		private SharedControlWatcher(final int playerIndex) {
			this.playerIndex = playerIndex;
		}

		@Override
		public void allianceChanged(final int otherPlayerIndex, final CAllianceType allianceType,
				final boolean value) {
			// Full shared control alone gives no message.
			if ((allianceType == CAllianceType.SHARED_CONTROL)
					&& (otherPlayerIndex == MeleeUIMessages.this.ui.war3MapViewer.getLocalPlayerIndex())) {
				sharedControlChanged(this.playerIndex, value);
			}
		}

		@Override
		public void goldChanged() {
		}

		@Override
		public void lumberChanged() {
		}

		@Override
		public void foodChanged() {
		}

		@Override
		public void upkeepChanged() {
		}

		@Override
		public void heroDeath() {
		}

		@Override
		public void heroTokensChanged() {
		}
	}

	private void showPendingHeroDeaths() {
		final long now = gameClock();
		while (!this.pendingHeroDeaths.isEmpty() && (now >= this.pendingHeroDeaths.getFirst().dueMillis)) {
			final PendingHeroDeath death = this.pendingHeroDeaths.removeFirst();
			if (death.message != null) {
				showGameMessage(death.message, HERO_DEATH_MESSAGE_SECONDS);
			}
			if (this.ui.rootFrame.hasSkinField(death.soundField)) {
				final UnitSound sound = this.ui.war3MapViewer.getUiSounds()
						.getSound(this.ui.rootFrame.getSkinField(death.soundField));
				if (sound != null) {
					sound.play(this.ui.uiScene.audioContext, 0, 0, 0);
				}
			}
		}
	}

	private static final class PendingHeroDeath {
		private final long dueMillis;
		private final String message;
		private final String soundField;

		private PendingHeroDeath(final long dueMillis, final String message, final String soundField) {
			this.dueMillis = dueMillis;
			this.message = message;
			this.soundField = soundField;
		}
	}

	public void showUpgradeCompleteAlert(final int playerIndex, final War3ID queuedRawcode, final int level) {
		if (this.ui.war3MapViewer.isViewedPlayer(playerIndex)) {
			String upgradeName;
			final CUpgradeType upgradeType = this.ui.war3MapViewer.simulation.getUpgradeData().getType(queuedRawcode);
			if (upgradeType != null) {
				final UpgradeLevel upgradeLevel = upgradeType.getLevel(level - 1);
				if (upgradeLevel != null) {
					upgradeName = upgradeLevel.getName();
				}
				else {
					upgradeName = "NOTEXTERN Unknown Level " + level + " for '" + queuedRawcode + "'";
				}
			}
			else {
				upgradeName = "NOTEXTERN Unknown ('" + queuedRawcode + "')";
			}
			innerShowLocalCommandErrorString(playerIndex,
					this.ui.rootFrame.getTemplates().getDecoratedString("COLON_COMPLETED") + upgradeName);
			this.ui.war3MapViewer.getUiSounds().getSound(this.ui.rootFrame.getSkinField("ResearchComplete"))
					.play(this.ui.uiScene.audioContext, 0, 0, 0);
		}
	}

	void updateMessages(final long currentMillis) {
		showPendingHeroDeaths();
		if (currentMillis > this.lastErrorMessageExpireTime) {
			this.errorMessageFrame.setVisible(false);
		}
		else if (currentMillis > this.lastErrorMessageFadeTime) {
			final float fadeAlpha = (this.lastErrorMessageExpireTime - currentMillis)
					/ (float) WORLD_FRAME_MESSAGE_FADE_DURATION;
			this.errorMessageFrame.setAlpha(fadeAlpha);
		}
		for (final GameMessage gameMessage : this.gameMessages) {
			if (gameMessage.expired) {
				continue;
			}
			if (currentMillis > gameMessage.expireTime) {
				removeGameMessage(gameMessage);
			}
			else if (currentMillis > gameMessage.fadeTime) {
				gameMessage.stringFrame
						.setAlpha((gameMessage.expireTime - currentMillis) / (float) GAME_MESSAGE_FADE_MILLIS);
			}
		}
		// Faded out messages above every shown one hold no place any more.
		while (!this.gameMessages.isEmpty() && this.gameMessages.getFirst().expired) {
			this.gameMessages.removeFirst();
		}
	}

	/**
	 * A script dialog hotkey is a Windows virtual key code ('C' is the C key; 'c' is no key). As in 1.29, only the
	 * dialog shown last takes it, not even an earlier one still shown once that one is hidden, and of its buttons
	 * sharing the key the one added last.
	 */
	boolean pressScriptDialogHotkey(final int keycode) {
		if (keycode == Input.Keys.UNKNOWN) {
			return false;
		}
		CScriptDialog shown = null;
		for (final CScriptDialog dialog : this.ui.scriptDialogs) {
			if ((shown == null) || (dialog.getShownOrder() > shown.getShownOrder())) {
				shown = dialog;
			}
		}
		if ((shown == null) || !shown.isVisible()) {
			return false;
		}
		final List<CScriptDialogButton> buttons = shown.getButtons();
		for (int i = buttons.size() - 1; i >= 0; i--) {
			final CScriptDialogButton button = buttons.get(i);
			if ((MeleeUIInput.virtualKeyToKeycode(button.getHotkey()) == keycode)
					&& (button.getButtonFrame() != null)) {
				button.getButtonFrame().onClick(Input.Buttons.LEFT);
				return true;
			}
		}
		return false;
	}

	public void setLeaderboard(final CLeaderboard leaderboard) {
		if (this.shownLeaderboard != null) {
			this.shownLeaderboard.setChangeListener(null);
		}
		this.shownLeaderboard = leaderboard;
		if (this.leaderboardFrame == null) {
			if (leaderboard == null) {
				return;
			}
			this.leaderboardFrame = this.ui.rootFrame.createFrame("Leaderboard", this.ui.rootFrame, 0, 0);
			this.leaderboardTitle = (StringFrame) this.ui.rootFrame.getFrameByName("LeaderboardTitle", 0);
			final UIFrame backdrop = this.ui.rootFrame.getFrameByName("LeaderboardBackdrop", 0);
			this.leaderboardLabels = this.ui.rootFrame.createStringFrame("SmashLeaderboardLabels", backdrop,
					Color.WHITE, TextJustify.LEFT, TextJustify.TOP, LEADERBOARD_FONT_HEIGHT);
			this.leaderboardLabels.addSetPoint(new SetPoint(FramePoint.TOPLEFT, backdrop, FramePoint.TOPLEFT,
					GameUI.convertX(this.ui.uiViewport, LEADERBOARD_INSET_X),
					GameUI.convertY(this.ui.uiViewport, -LEADERBOARD_ROWS_TOP)));
			this.leaderboardLabels.addSetPoint(new SetPoint(FramePoint.BOTTOMRIGHT, backdrop, FramePoint.BOTTOMRIGHT,
					GameUI.convertX(this.ui.uiViewport, -LEADERBOARD_INSET_X), 0));
			this.leaderboardValues = this.ui.rootFrame.createStringFrame("SmashLeaderboardValues", backdrop,
					Color.WHITE, TextJustify.RIGHT, TextJustify.TOP, LEADERBOARD_FONT_HEIGHT);
			this.leaderboardValues.addSetPoint(new SetPoint(FramePoint.TOPLEFT, backdrop, FramePoint.TOPLEFT,
					GameUI.convertX(this.ui.uiViewport, LEADERBOARD_INSET_X),
					GameUI.convertY(this.ui.uiViewport, -LEADERBOARD_ROWS_TOP)));
			this.leaderboardValues.addSetPoint(new SetPoint(FramePoint.BOTTOMRIGHT, backdrop, FramePoint.BOTTOMRIGHT,
					GameUI.convertX(this.ui.uiViewport, -LEADERBOARD_INSET_X), 0));
			this.leaderboardFrame.setWidth(GameUI.convertX(this.ui.uiViewport, LEADERBOARD_MIN_WIDTH));
			this.ui.leaderboardSlot = -1;
		}
		final boolean shown = leaderboard != null;
		this.leaderboardFrame.setVisible(shown);
		this.leaderboardLabels.setVisible(shown);
		this.leaderboardValues.setVisible(shown);
		if (shown) {
			leaderboard.setChangeListener(this::refreshLeaderboard);
			refreshLeaderboard();
		}
	}

	/**
	 * The leaderboard is an edit box styled panel under the shown timer dialogs: the label centered on top, then one
	 * row per item with its label on the left and its value on the right, both in the item's player color unless the
	 * map sets other colors (1.29).
	 */
	private void refreshLeaderboard() {
		final CLeaderboard board = this.shownLeaderboard;
		if ((board == null) || (this.leaderboardFrame == null)) {
			return;
		}
		// Measured on 1.29: an empty title takes no line, the rows start where it would be (the Human08 board).
		final boolean titled = board.isShowLabel() && (board.getLabel() != null) && !board.getLabel().isEmpty();
		final float unit = GameUI.convertX(this.ui.uiViewport, 1f);
		float labelsWidth = 0;
		float valuesWidth = 0;
		for (final CLeaderboard.Item item : board.getItems()) {
			if (board.isShowNames() && item.showLabel) {
				labelsWidth = Math.max(labelsWidth, leaderboardTextWidth(this.leaderboardLabels, item.label) / unit);
			}
			if (board.isShowValues() && item.showValue) {
				valuesWidth = Math.max(valuesWidth,
						leaderboardTextWidth(this.leaderboardValues, Integer.toString(item.value)) / unit);
			}
		}
		if (Float.isNaN(this.leaderboardWidth)) {
			float width = board.getItemCount() > 0 ? labelsWidth + valuesWidth + LEADERBOARD_TEXT_SPACE : 0;
			if (titled) {
				width = Math.max(width, (2 * LEADERBOARD_TITLE_LEFT)
						+ (leaderboardTextWidth(this.leaderboardTitle, board.getLabel()) / unit));
			}
			this.leaderboardWidth = Math.max(LEADERBOARD_MIN_WIDTH, width);
			this.leaderboardFrame.setWidth(GameUI.convertX(this.ui.uiViewport, this.leaderboardWidth));
		}
		final String title = titled ? fitLeaderboardText(this.leaderboardTitle, board.getLabel(),
				(this.leaderboardWidth - LEADERBOARD_TITLE_LEFT) + LEADERBOARD_TITLE_OVERHANG) : "";
		this.leaderboardTitle.setVisible(!title.isEmpty());
		this.ui.rootFrame.setText(this.leaderboardTitle, title);
		final float labelSpace = this.leaderboardWidth - (2 * LEADERBOARD_INSET_X) - valuesWidth
				- LEADERBOARD_VALUE_GAP;
		final StringBuilder labels = new StringBuilder();
		final StringBuilder values = new StringBuilder();
		for (final CLeaderboard.Item item : board.getItems()) {
			if (labels.length() > 0) {
				labels.append('\n');
				values.append('\n');
			}
			final String label = fitLeaderboardText(this.leaderboardLabels, item.label, labelSpace);
			// A row with nothing in it still takes its line.
			if (board.isShowNames() && item.showLabel && !label.isEmpty()) {
				appendLeaderboardText(labels, label,
						leaderboardColor(item.labelColorSet, item.labelColor, board.isLabelColorSet(),
								board.getLabelColor(), item.playerIndex));
			}
			else {
				labels.append(' ');
			}
			if (board.isShowValues() && item.showValue) {
				appendLeaderboardText(values, Integer.toString(item.value),
						leaderboardColor(item.valueColorSet, item.valueColor, board.isValueColorSet(),
								board.getValueColor(), item.playerIndex));
			}
			else {
				values.append(' ');
			}
		}
		final int rows = Math.max(board.getItemCount(), board.getSizeByItemCount());
		final float rowsTop = titled ? LEADERBOARD_ROWS_TOP : LEADERBOARD_TITLE_TOP;
		this.leaderboardLabels.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.leaderboardLabels.getParent(),
				FramePoint.TOPLEFT, GameUI.convertX(this.ui.uiViewport, LEADERBOARD_INSET_X),
				GameUI.convertY(this.ui.uiViewport, -rowsTop)));
		this.leaderboardValues.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.leaderboardValues.getParent(),
				FramePoint.TOPLEFT, GameUI.convertX(this.ui.uiViewport, LEADERBOARD_INSET_X),
				GameUI.convertY(this.ui.uiViewport, -rowsTop)));
		this.leaderboardFrame.setHeight(GameUI.convertY(this.ui.uiViewport,
				rowsTop + (rows * LEADERBOARD_ROW_HEIGHT) + LEADERBOARD_BOTTOM_INSET));
		this.leaderboardFrame.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
		this.leaderboardLabels.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
		this.leaderboardValues.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
		this.ui.rootFrame.setText(this.leaderboardLabels, labels.toString());
		this.ui.rootFrame.setText(this.leaderboardValues, values.toString());
	}

	private float leaderboardTextWidth(final StringFrame frame, final String text) {
		final GlyphLayout layout = this.ui.rootFrame.getGlyphLayout();
		layout.setText(frame.getFrameFont(), text.replaceAll("\\|[cC][0-9a-fA-F]{8}|\\|[rR]", ""));
		return layout.width;
	}

	/** One line cut to the width: at a word when one fits, else at a letter; nothing when no letter fits. */
	private String fitLeaderboardText(final StringFrame frame, final String text, final float width) {
		final float unit = GameUI.convertX(this.ui.uiViewport, 1f);
		if ((leaderboardTextWidth(frame, text) / unit) <= width) {
			return text;
		}
		int fit = 0;
		for (int end = 1; end <= text.length(); end++) {
			if ((leaderboardTextWidth(frame, text.substring(0, end)) / unit) > width) {
				break;
			}
			fit = end;
		}
		if ((fit < text.length()) && (text.charAt(fit) != ' ')) {
			final int space = text.lastIndexOf(' ', fit);
			if (space > 0) {
				return text.substring(0, space);
			}
		}
		return text.substring(0, fit).trim();
	}

	private static void appendLeaderboardText(final StringBuilder text, final String value, final int rgba) {
		text.append("|c").append(String.format("%02x%06x", rgba & 0xFF, (rgba >>> 8) & 0xFFFFFF)).append(value)
				.append("|r");
	}

	private int leaderboardColor(final boolean itemColorSet, final int itemColor, final boolean boardColorSet,
			final int boardColor, final int playerIndex) {
		if (itemColorSet) {
			return itemColor;
		}
		if (boardColorSet) {
			return boardColor;
		}
		if (playerIndex >= 0) {
			final CPlayer player = this.ui.war3MapViewer.simulation.getPlayer(playerIndex);
			if (player != null) {
				return MeleeChat.playerTextColorRgba(player.getColor());
			}
		}
		return 0xFFFFFFFF;
	}

	public void removeTimerDialog(final CTimerDialog dialog) {
		this.ui.timerDialogs.remove(dialog);
		this.ui.timerDialogSlots.remove(dialog.getFrame());
	}

	/**
	 * Shown timer dialogs form a row under the upper bar from the top right corner of the screen (not of the 4:3
	 * area) to the left, the oldest on the right (1.29).
	 */
	void layoutTimerDialogs() {
		float screenRight = GameUI.convertX(this.ui.uiViewport, TIMER_DIALOG_RIGHT)
				- Math.min(0, (this.ui.uiViewport.getMinWorldWidth() - this.ui.uiViewport.getWorldWidth()) / 2);
		// Without timer dialogs a multiboard hangs from the top right corner of the 4:3 area instead.
		float boardRight = 0;
		// The touch control group buttons take the right screen edge, so the timers and the leaderboard go beside
		// them.
		final ControlGroupBar groupBar = this.ui.groups.getControlGroupBar();
		if (groupBar != null) {
			final float groupBarLeft = groupBar.columnLeft() - this.ui.rootFrame.getFramePointX(FramePoint.RIGHT);
			screenRight = Math.min(screenRight, groupBarLeft);
			boardRight = Math.min(boardRight, groupBarLeft);
		}
		if (screenRight != this.timerDialogRight) {
			this.timerDialogRight = screenRight;
			this.ui.timerDialogSlots.clear();
			this.ui.leaderboardSlot = -1;
		}
		final float top = GameUI.convertY(this.ui.uiViewport, TIMER_DIALOG_TOP);
		float right = screenRight;
		int slot = 0;
		for (final CTimerDialog dialog : this.ui.timerDialogs) {
			final UIFrame frame = dialog.getFrame();
			if (!dialog.isVisible()) {
				continue;
			}
			final Integer placed = this.ui.timerDialogSlots.get(frame);
			if ((placed == null) || (placed != slot)) {
				this.ui.timerDialogSlots.put(frame, slot);
				frame.addAnchor(new AnchorDefinition(FramePoint.TOPRIGHT, right, top));
				frame.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
			}
			right -= ((AbstractRenderableFrame) frame).getAssignedWidth()
					+ GameUI.convertX(this.ui.uiViewport, TIMER_DIALOG_GAP);
			slot++;
		}
		// The leaderboard takes the place under the row of shown timer dialogs, right aligned with it. A shown
		// script multiboard takes that place instead: the game shows one board at a time. The Team Resources board
		// is not one of them; the leaderboard hangs under it, right aligned with it (1.29).
		final int leaderboardRow = Math.min(slot, 1);
		if (slot == 0) {
			this.ui.multiboard.anchor(boardRight, GameUI.convertY(this.ui.uiViewport, MULTIBOARD_TOP));
		}
		else {
			this.ui.multiboard.anchor(screenRight,
					GameUI.convertY(this.ui.uiViewport, TIMER_DIALOG_TOP - (leaderboardRow * TIMER_DIALOG_STEP)));
		}
		final boolean underTeamResources = this.ui.multiboard.isShowing()
				&& this.ui.teamResources.isBoard(this.ui.multiboard.getShownBoard());
		if (this.leaderboardFrame != null) {
			final boolean leaderboardShown = (this.shownLeaderboard != null)
					&& (!this.ui.multiboard.isShowing() || underTeamResources);
			if (this.leaderboardFrame.isVisible() != leaderboardShown) {
				this.leaderboardFrame.setVisible(leaderboardShown);
				this.leaderboardLabels.setVisible(leaderboardShown);
				this.leaderboardValues.setVisible(leaderboardShown);
			}
		}
		if (this.leaderboardFrame == null) {
			return;
		}
		final float leaderboardRight;
		final float leaderboardTop;
		if (underTeamResources) {
			leaderboardRight = this.ui.multiboard.getAnchorRight();
			leaderboardTop = (this.ui.multiboard.getBottom() - this.ui.rootFrame.getFramePointY(FramePoint.TOP))
					- GameUI.convertY(this.ui.uiViewport, LEADERBOARD_UNDER_BOARD_GAP);
		}
		else {
			leaderboardRight = screenRight;
			leaderboardTop = GameUI.convertY(this.ui.uiViewport,
					TIMER_DIALOG_TOP - (leaderboardRow * TIMER_DIALOG_STEP));
		}
		if ((this.ui.leaderboardSlot != leaderboardRow) || (leaderboardRight != this.leaderboardRight)
				|| (leaderboardTop != this.leaderboardTop)) {
			this.ui.leaderboardSlot = leaderboardRow;
			this.leaderboardRight = leaderboardRight;
			this.leaderboardTop = leaderboardTop;
			this.leaderboardFrame
					.addAnchor(new AnchorDefinition(FramePoint.TOPRIGHT, leaderboardRight, leaderboardTop));
			this.leaderboardFrame.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
			// The row texts belong to the root frame, so moving the leaderboard does not move them.
			this.leaderboardLabels.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
			this.leaderboardValues.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
		}
	}

	public CTimerDialog createTimerDialog(final CTimer timer) {
		final UIFrame timerDialog = this.ui.rootFrame.createFrame("TimerDialog", this.ui.rootFrame, 0, 0);
		final StringFrame valueFrame = (StringFrame) this.ui.rootFrame.getFrameByName("TimerDialogValue", 0);
		final StringFrame titleFrame = (StringFrame) this.ui.rootFrame.getFrameByName("TimerDialogTitle", 0);
		final CTimerDialog dialog = new CTimerDialog(this.ui.rootFrame, timer, timerDialog, valueFrame, titleFrame);
		// Like in the game, a new timer dialog stays hidden until TimerDialogDisplay.
		dialog.setVisible(false);
		this.ui.timerDialogs.add(dialog);
		return dialog;
	}

	public void displayTimedText(final float x, final float y, final float duration, final String message) {
		this.gameMessageOffsetX = x;
		this.gameMessageOffsetY = y;
		showGameMessage(message, duration);
	}

	public void clearTextMessages() {
		for (final GameMessage gameMessage : this.gameMessages) {
			removeGameMessage(gameMessage);
		}
		this.gameMessages.clear();
	}

	public CScriptDialog createScriptDialog(final GlobalScope globalScope) {
		final SimpleFrame scriptDialog = (SimpleFrame) this.ui.rootFrame.createFrame("ScriptDialog", this.ui.rootFrame,
				0, 0);
		scriptDialog.addAnchor(new AnchorDefinition(FramePoint.TOP, 0, GameUI.convertY(this.ui.uiViewport, -0.05f)));
		scriptDialog.setVisible(false);
		final StringFrame scriptDialogTextFrame = (StringFrame) this.ui.rootFrame.getFrameByName("ScriptDialogText", 0);
		scriptDialog.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
		final CScriptDialog dialog = new CScriptDialog(globalScope, scriptDialog, scriptDialogTextFrame);
		this.ui.scriptDialogs.add(dialog);
		return dialog;
	}

	public CScriptDialogButton createScriptDialogButton(final CScriptDialog scriptDialog, final String text,
			final char hotkey) {
		if (scriptDialog.getScriptDialogFrame() == null) {
			// A dialog destroyed before a save has no frames in the loaded game.
			clearDialog(scriptDialog);
		}
		final CScriptDialogButton newButton = new CScriptDialogButton(null, null, text, hotkey);
		attachScriptDialogButton(scriptDialog, newButton);
		return newButton;
	}

	void attachScriptDialogButton(final CScriptDialog scriptDialog, final CScriptDialogButton button) {
		final GlueTextButtonFrame scriptDialogButton = (GlueTextButtonFrame) this.ui.rootFrame
				.createFrame("ScriptDialogButton", scriptDialog.getScriptDialogFrame(), 0, 0);
		scriptDialogButton.setHeight(GameUI.convertY(this.ui.uiViewport, 0.03f));
		final StringFrame scriptDialogTextFrame = (StringFrame) this.ui.rootFrame
				.getFrameByName("ScriptDialogButtonText", 0);
		this.ui.rootFrame.setText(scriptDialogTextFrame, button.getText());
		scriptDialogButton.addSetPoint(new SetPoint(FramePoint.TOP, scriptDialog.getLastAddedComponent(),
				FramePoint.BOTTOM, 0, GameUI.convertY(this.ui.uiViewport, -0.005f)));
		button.attachFrames(scriptDialogButton, scriptDialogTextFrame);
		scriptDialog.addButton(this.ui.rootFrame, this.ui.uiViewport, button,
				() -> pressScriptDialogButton(scriptDialog, button));
	}

	private void pressScriptDialogButton(final CScriptDialog dialog, final CScriptDialogButton button) {
		if (this.ui.scriptDialogs.contains(dialog) && dialog.getButtons().contains(button)) {
			this.ui.unitOrderListener.clickDialogButton(dialog.getHandleId(), button.getHandleId());
		}
	}

	public void destroyDialog(final CScriptDialog dialog) {
		this.ui.scriptDialogs.remove(dialog);
		if (dialog.getScriptDialogFrame() != null) {
			this.ui.rootFrame.remove(dialog.getScriptDialogFrame());
		}
	}

	public void displayScriptDialog(final CScriptDialog dialog, final boolean flag) {
		if (flag && !dialog.isVisible()) {
			dialog.setShownOrder(++this.ui.lastScriptDialogShown);
		}
		dialog.setVisible(flag);
	}

	public void clearDialog(final CScriptDialog dialog) {
		destroyDialog(dialog);
		this.ui.scriptDialogs.add(dialog);
		final SimpleFrame scriptDialog = (SimpleFrame) this.ui.rootFrame.createFrame("ScriptDialog", this.ui.rootFrame,
				0, 0);
		scriptDialog.addAnchor(new AnchorDefinition(FramePoint.TOP, 0, GameUI.convertY(this.ui.uiViewport, -0.05f)));
		scriptDialog.setVisible(false);
		final StringFrame scriptDialogTextFrame = (StringFrame) this.ui.rootFrame.getFrameByName("ScriptDialogText", 0);
		scriptDialog.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
		dialog.reset(scriptDialog, scriptDialogTextFrame);
	}
}
