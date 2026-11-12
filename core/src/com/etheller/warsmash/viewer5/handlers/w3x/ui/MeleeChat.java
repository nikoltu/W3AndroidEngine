package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.input.touch.OnscreenKeyboard;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.AnchorDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.MenuItem;
import com.etheller.warsmash.parsers.fdf.datamodel.TextJustify;
import com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame;
import com.etheller.warsmash.parsers.fdf.frames.CheckBoxFrame;
import com.etheller.warsmash.parsers.fdf.frames.EditBoxFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.MenuFrame;
import com.etheller.warsmash.parsers.fdf.frames.PopupMenuFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextAreaFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CReleaseCheats;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

/**
 * In-game chat: the line Enter opens above the console, the Chat dialog (F12) with the default recipient and the
 * history, and the messages themselves, which reach the sending player's chat triggers.
 */
public final class MeleeChat {
	enum Recipient {
		ALL("CHAT_RECIPIENT_ALL", "COLON_MESSAGE_ALL"),
		ALLIES("CHAT_RECIPIENT_ALLIES", "COLON_MESSAGE_ALLIES"),
		OBSERVERS("CHAT_RECIPIENT_OBSERVERS", "COLON_MESSAGE_OBSERVERS"),
		PLAYER("CHAT_RECIPIENT_PRIVATE", "COLON_MESSAGE_PLAYER");

		private final String tagKey;
		private final String promptKey;

		Recipient(final String tagKey, final String promptKey) {
			this.tagKey = tagKey;
			this.promptKey = promptKey;
		}
	}

	/** The usual text colors of the twelve player colors. */
	private static final String[] PLAYER_TEXT_COLORS = { "ff0303", "0042ff", "1ce6b9", "540081", "fffc01", "feba0e",
			"20c000", "e55bb0", "959697", "7ebff1", "106246", "4e2a04" };

	interface Host {
		void showChatMessage(String line);

		void focusOn(EditBoxFrame box);

		void dialogClosed();

		void sendChatMessage(int recipient, int recipientPlayerIndex, String message);

		/** A single player game that is not a replay, where the chat line takes the release cheats. */
		boolean isCheatGame();

		void sendCheat(String cheat);

		/** motherland: leaves for a campaign mission, named by its campaign race and number. */
		void playCampaignMission(String race, int mission);
	}

	private static final float ENTRY_BOTTOM = 0.155f;

	private final GameUI rootFrame;
	private final Viewport uiViewport;
	private final CSimulation simulation;
	private final CPlayer localPlayer;
	private final Host host;

	private final SimpleFrame entryHolder;
	private final StringFrame entryLabel;
	private final EditBoxFrame entryBox;
	private Recipient entryRecipient;
	private boolean openingLineBreak;
	private float entryBottom;

	private final SimpleFrame dialogHolder;
	private final UIFrame dialog;
	private final CheckBoxFrame playerRadio;
	private final CheckBoxFrame alliesRadio;
	private final CheckBoxFrame observersRadio;
	private final CheckBoxFrame everyoneRadio;
	private final PopupMenuFrame playerMenu;
	private final TextAreaFrame historyDisplay;
	private final GlueTextButtonFrame acceptButton;
	private final List<CPlayer> menuPlayers = new ArrayList<>();

	private Recipient preference = Recipient.ALLIES;
	private CPlayer privatePlayer;
	private Recipient shownPreference;
	private CPlayer shownPrivatePlayer;
	private final List<String> history = new ArrayList<>();

	MeleeChat(final GameUI rootFrame, final Viewport uiViewport, final CSimulation simulation,
			final CPlayer localPlayer, final float messageFontHeight, final Host host) {
		this.rootFrame = rootFrame;
		this.uiViewport = uiViewport;
		this.simulation = simulation;
		this.localPlayer = localPlayer;
		this.host = host;

		this.entryHolder = (SimpleFrame) rootFrame.createSimpleFrame("SmashChatEntry", rootFrame, 0);
		this.entryBottom = GameUI.convertY(uiViewport, ENTRY_BOTTOM);
		this.entryHolder.addAnchor(new AnchorDefinition(FramePoint.BOTTOMLEFT, GameUI.convertX(uiViewport, 0.0f),
				this.entryBottom));
		this.entryHolder.setWidth(GameUI.convertX(uiViewport, 0.6f));
		this.entryHolder.setHeight(GameUI.convertY(uiViewport, messageFontHeight * 1.5f));
		this.entryLabel = rootFrame.createStringFrame("SmashChatEntryLabel", this.entryHolder, Color.WHITE,
				TextJustify.LEFT, TextJustify.MIDDLE, messageFontHeight);
		this.entryLabel.addSetPoint(new SetPoint(FramePoint.LEFT, this.entryHolder, FramePoint.LEFT,
				GameUI.convertX(uiViewport, 0.01f), 0));
		this.entryLabel.setHeight(this.entryHolder.getAssignedHeight());
		this.entryLabel.setFontShadowColor(new Color(0f, 0f, 0f, 0.9f));
		this.entryLabel.setFontShadowOffsetX(GameUI.convertX(uiViewport, 0.001f));
		this.entryLabel.setFontShadowOffsetY(GameUI.convertY(uiViewport, -0.001f));
		adopt(this.entryLabel);
		this.entryHolder.add(this.entryLabel);

		this.entryBox = new EditBoxFrame("SmashChatEntryBox", this.entryHolder, 0, Color.WHITE);
		final SimpleFrame noBackdrop = new SimpleFrame("SmashChatEntryBackdrop", this.entryBox);
		noBackdrop.setSetAllPoints(true);
		this.entryBox.setControlBackdrop(noBackdrop);
		final StringFrame entryText = rootFrame.createStringFrame("SmashChatEntryText", this.entryBox, Color.WHITE,
				TextJustify.LEFT, TextJustify.MIDDLE, messageFontHeight);
		entryText.setSetAllPoints(true);
		entryText.setFontShadowColor(new Color(0f, 0f, 0f, 0.9f));
		entryText.setFontShadowOffsetX(GameUI.convertX(uiViewport, 0.001f));
		entryText.setFontShadowOffsetY(GameUI.convertY(uiViewport, -0.001f));
		adopt(entryText);
		this.entryBox.setEditTextFrame(entryText);
		this.entryBox.addSetPoint(new SetPoint(FramePoint.LEFT, this.entryLabel, FramePoint.RIGHT,
				GameUI.convertX(uiViewport, 0.004f), 0));
		this.entryBox.addSetPoint(new SetPoint(FramePoint.RIGHT, this.entryHolder, FramePoint.RIGHT, 0, 0));
		this.entryBox.setHeight(this.entryHolder.getAssignedHeight());
		this.entryBox.setFilterAllowAny();
		this.entryBox.setOnEnter(this::send);
		this.entryHolder.add(this.entryBox);
		this.entryHolder.setVisible(false);

		this.dialogHolder = (SimpleFrame) rootFrame.createSimpleFrame("SmashChatDialog", rootFrame, 0);
		this.dialogHolder.addAnchor(new AnchorDefinition(FramePoint.CENTER, 0, 0));
		this.dialog = rootFrame.createFrame("ChatDialog", this.dialogHolder, 0, 0);
		this.dialog.addSetPoint(new SetPoint(FramePoint.CENTER, this.dialogHolder, FramePoint.CENTER, 0, 0));
		this.dialogHolder.add(this.dialog);
		this.dialogHolder.setWidth(this.dialog.getAssignedWidth());
		this.dialogHolder.setHeight(this.dialog.getAssignedHeight());
		this.playerRadio = (CheckBoxFrame) rootFrame.getFrameByName("ChatPlayerRadioButton", 0);
		this.alliesRadio = (CheckBoxFrame) rootFrame.getFrameByName("ChatAlliesRadioButton", 0);
		this.observersRadio = (CheckBoxFrame) rootFrame.getFrameByName("ChatObserversRadioButton", 0);
		this.everyoneRadio = (CheckBoxFrame) rootFrame.getFrameByName("ChatEveryoneRadioButton", 0);
		this.playerRadio.setOnClick(() -> choose(Recipient.PLAYER));
		this.alliesRadio.setOnClick(() -> choose(Recipient.ALLIES));
		this.observersRadio.setOnClick(() -> choose(Recipient.OBSERVERS));
		this.everyoneRadio.setOnClick(() -> choose(Recipient.ALL));
		this.playerMenu = (PopupMenuFrame) rootFrame.getFrameByName("ChatPlayerMenu", 0);
		this.playerMenu.setMenuClickListener((button, index) -> {
			if ((index >= 0) && (index < this.menuPlayers.size())) {
				this.shownPrivatePlayer = this.menuPlayers.get(index);
				choose(Recipient.PLAYER);
			}
		});
		// The menu frame is spawned on the root; it belongs on top of the dialog.
		rootFrame.remove(this.playerMenu.getPopupMenuFrame());
		this.dialogHolder.add(this.playerMenu.getPopupMenuFrame());
		this.historyDisplay = (TextAreaFrame) rootFrame.getFrameByName("ChatHistoryDisplay", 0);
		this.acceptButton = (GlueTextButtonFrame) rootFrame.getFrameByName("ChatAcceptButton", 0);
		this.acceptButton.setOnClick(this::accept);
		this.dialogHolder.setVisible(false);
	}

	/** createStringFrame puts the frame on the root, named by its own name; here a parent draws it. */
	private void adopt(final StringFrame frame) {
		this.rootFrame.remove(frame);
		this.rootFrame.setText(frame, "");
	}

	boolean isEntryOpen() {
		return this.entryHolder.isVisible();
	}

	UIFrame getDialogFrame() {
		return this.dialogHolder;
	}

	boolean isDialogVisible() {
		return this.dialogHolder.isVisible();
	}

	boolean dialogContains(final float uiX, final float uiY) {
		return isDialogVisible() && ((AbstractRenderableFrame) this.dialog).getRenderBounds().contains(uiX, uiY);
	}

	/**
	 * Opens the line; a recipient of null takes the player's default from the Chat dialog. Measured on 1.29: with no
	 * other human in the game the line is just "Message:" and everything is sent to all, allies or not.
	 */
	void openEntry(final Recipient override) {
		final boolean alone = !hasOtherHumans();
		this.entryRecipient = alone ? Recipient.ALL : override != null ? override : this.preference;
		if ((this.entryRecipient == Recipient.PLAYER) && (this.privatePlayer == null)) {
			this.entryRecipient = Recipient.ALLIES;
		}
		this.rootFrame.setText(this.entryLabel, alone
				? this.rootFrame.getTemplates().getDecoratedString("COLON_MESSAGE_SINGLEPLAYER")
				: prompt(this.entryRecipient, this.privatePlayer));
		this.entryBox.setText("", this.rootFrame, this.uiViewport);
		this.entryHolder.setVisible(true);
		this.entryHolder.positionBounds(this.rootFrame, this.uiViewport);
		this.host.focusOn(this.entryBox);
	}

	/** Keeps the open line above the on-screen keyboard, which covers the game surface instead of shrinking it. */
	void followKeyboard() {
		if (!isEntryOpen()) {
			return;
		}
		float bottom = GameUI.convertY(this.uiViewport, ENTRY_BOTTOM);
		final int covered = OnscreenKeyboard.getCoveredHeightPixels();
		final int screenHeight = Gdx.graphics.getHeight();
		if ((covered > 0) && (screenHeight > 0)) {
			bottom = Math.max(bottom, ((this.uiViewport.getWorldHeight() * covered) / screenHeight)
					+ GameUI.convertY(this.uiViewport, 0.01f));
		}
		if (bottom != this.entryBottom) {
			this.entryBottom = bottom;
			this.entryHolder.addAnchor(
					new AnchorDefinition(FramePoint.BOTTOMLEFT, GameUI.convertX(this.uiViewport, 0.0f), bottom));
			this.entryHolder.positionBounds(this.rootFrame, this.uiViewport);
		}
	}

	/** The top of the open line in UI units, or negative infinity when it is closed. */
	float entryTop() {
		if (!isEntryOpen()) {
			return Float.NEGATIVE_INFINITY;
		}
		final Rectangle bounds = this.entryHolder.getRenderBounds();
		return bounds.y + bounds.height;
	}

	void closeEntry() {
		this.entryHolder.setVisible(false);
		this.openingLineBreak = false;
		this.host.focusOn(null);
	}

	/** Keys while the line is open: everything goes to it, Escape drops it. */
	boolean entryKeyDown(final int keycode) {
		if (keycode == Input.Keys.ESCAPE) {
			closeEntry();
			return true;
		}
		this.entryBox.keyDown(keycode);
		return true;
	}

	/** The Enter that opened the line also comes as a typed line break, which must not send it at once. */
	void openEntryFromEnterKey(final Recipient override) {
		openEntry(override);
		this.openingLineBreak = true;
	}

	boolean entryKeyTyped(final char character) {
		final boolean opening = this.openingLineBreak;
		this.openingLineBreak = false;
		if ((character == '\n') || (character == '\r')) {
			if (opening) {
				return true;
			}
			// The on-screen keyboard sends its Enter as a typed line break.
			send();
		}
		else if (character >= ' ') {
			this.entryBox.keyTyped(character);
		}
		return true;
	}

	/** A line another player sent, as it reaches this one: in the history and on the screen. */
	void showReceived(final CPlayer sender, final Recipient recipient, final String message) {
		final String line = this.rootFrame.getTemplates().getDecoratedString(recipient.tagKey) + " "
				+ coloredName(sender) + ": " + message;
		this.history.add(line);
		this.host.showChatMessage(line);
	}

	private void send() {
		final String message = this.entryBox.getText() == null ? "" : this.entryBox.getText();
		final Recipient recipient = this.entryRecipient;
		closeEntry();
		if (message.trim().isEmpty() || sendCheat(message)) {
			return;
		}
		final String line = this.rootFrame.getTemplates().getDecoratedString(recipient.tagKey) + " "
				+ coloredName(this.localPlayer) + ": " + message;
		this.history.add(line);
		this.host.showChatMessage(line);
		this.host.sendChatMessage(recipient.ordinal(),
				(recipient == Recipient.PLAYER) && (this.privatePlayer != null) ? this.privatePlayer.getId() : -1,
				message);
	}

	/**
	 * A cheat goes to the game instead of the chat and its triggers; measured on 1.29, its confirmation stands in for
	 * the chat line, without a recipient tag.
	 */
	private boolean sendCheat(final String message) {
		if (!this.host.isCheatGame() || hasOtherHumans()) {
			return false;
		}
		final CReleaseCheats.Parsed cheat = CReleaseCheats.parse(message);
		if (cheat == null) {
			return false;
		}
		final CReleaseCheats.Result result = CReleaseCheats.preview(this.simulation, this.localPlayer.getId(),
				message);
		if (result.getMessageKey() != null) {
			final String line = coloredName(this.localPlayer) + ": "
					+ this.rootFrame.getTemplates().getDecoratedString(result.getMessageKey());
			this.history.add(line);
			this.host.showChatMessage(line);
		}
		if (result != CReleaseCheats.Result.IGNORED) {
			if (cheat.getCode() == CReleaseCheats.Code.MOTHERLAND) {
				final String[] mission = cheat.motherland();
				this.host.playCampaignMission(mission[0], Integer.parseInt(mission[1]));
			}
			else {
				this.host.sendCheat(message);
			}
		}
		return true;
	}

	private String prompt(final Recipient recipient, final CPlayer player) {
		final String text = this.rootFrame.getTemplates().getDecoratedString(recipient.promptKey);
		return recipient == Recipient.PLAYER ? text.replace("%s", player.getName()) : text;
	}

	/** The text color of a player color as RGBA. */
	static int playerTextColorRgba(final int color) {
		final String hex = (color >= 0) && (color < PLAYER_TEXT_COLORS.length) ? PLAYER_TEXT_COLORS[color] : "959697";
		return (Integer.parseInt(hex, 16) << 8) | 0xFF;
	}

	/** The hex RRGGBB a player's name is written in, for |c color codes. */
	public static String playerTextHex(final int color) {
		return (color >= 0) && (color < PLAYER_TEXT_COLORS.length) ? PLAYER_TEXT_COLORS[color] : "959697";
	}

	private static String coloredName(final CPlayer player) {
		final int color = player.getColor();
		final String hex = (color >= 0) && (color < PLAYER_TEXT_COLORS.length) ? PLAYER_TEXT_COLORS[color] : "959697";
		return "|cff" + hex + player.getName() + "|r";
	}

	void showDialog() {
		this.shownPreference = this.preference;
		this.shownPrivatePlayer = this.privatePlayer;
		this.menuPlayers.clear();
		final MenuFrame menu = (MenuFrame) this.playerMenu.getPopupMenuFrame();
		final List<MenuItem> items = new ArrayList<>();
		for (int i = 0; i < this.simulation.getGameData().getMaxPlayers(); i++) {
			final CPlayer other = this.simulation.getPlayer(i);
			if ((other != this.localPlayer) && (other.getSlotState() == CPlayerSlotState.PLAYING)
					&& (other.getController() == CMapControl.USER)) {
				this.menuPlayers.add(other);
				items.add(new MenuItem(other.getName(), i));
			}
		}
		menu.setItems(this.uiViewport, items);
		if ((this.shownPrivatePlayer != null) && !this.menuPlayers.contains(this.shownPrivatePlayer)) {
			this.shownPrivatePlayer = null;
		}
		if ((this.shownPreference == Recipient.PLAYER) && (this.shownPrivatePlayer == null)) {
			this.shownPreference = Recipient.ALLIES;
		}
		final boolean otherHumans = !this.menuPlayers.isEmpty();
		this.playerRadio.setEnabled(otherHumans);
		this.playerMenu.setEnabled(otherHumans);
		((GlueTextButtonFrame) this.playerMenu.getPopupTitleFrame()).setEnabled(otherHumans);
		this.observersRadio.setEnabled(hasObservers());
		this.rootFrame.setText((StringFrame) ((GlueTextButtonFrame) this.playerMenu.getPopupTitleFrame()).getButtonText(),
				this.shownPrivatePlayer == null ? (otherHumans ? this.menuPlayers.get(0).getName() : "")
						: this.shownPrivatePlayer.getName());
		updateRadios();
		final StringBuilder text = new StringBuilder();
		for (final String line : this.history) {
			text.append(line).append('\n');
		}
		this.historyDisplay.setText(text.toString(), this.rootFrame, this.uiViewport);
		if (this.historyDisplay.getScrollBarFrame() != null) {
			this.historyDisplay.getScrollBarFrame().setValue(this.rootFrame, this.uiViewport, 0);
		}
		this.dialogHolder.setVisible(true);
		this.dialogHolder.positionBounds(this.rootFrame, this.uiViewport);
	}

	void hideDialog() {
		this.playerMenu.getPopupMenuFrame().setVisible(false);
		this.dialogHolder.setVisible(false);
		this.host.dialogClosed();
	}

	private boolean hasObservers() {
		for (int i = 0; i < this.simulation.getGameData().getMaxPlayers(); i++) {
			final CPlayer other = this.simulation.getPlayer(i);
			if ((other.getSlotState() == CPlayerSlotState.PLAYING) && other.isObserver()) {
				return true;
			}
		}
		return false;
	}

	private void choose(final Recipient recipient) {
		this.shownPreference = recipient;
		if ((recipient == Recipient.PLAYER) && (this.shownPrivatePlayer == null) && !this.menuPlayers.isEmpty()) {
			this.shownPrivatePlayer = this.menuPlayers.get(0);
		}
		updateRadios();
	}

	private void updateRadios() {
		this.playerRadio.setChecked(this.shownPreference == Recipient.PLAYER);
		this.alliesRadio.setChecked(this.shownPreference == Recipient.ALLIES);
		this.observersRadio.setChecked(this.shownPreference == Recipient.OBSERVERS);
		this.everyoneRadio.setChecked(this.shownPreference == Recipient.ALL);
	}

	private void accept() {
		this.preference = this.shownPreference;
		this.privatePlayer = this.shownPrivatePlayer;
		hideDialog();
	}

	private boolean hasOtherHumans() {
		for (int i = 0; i < this.simulation.getGameData().getMaxPlayers(); i++) {
			final CPlayer other = this.simulation.getPlayer(i);
			if ((other != this.localPlayer) && (other.getSlotState() == CPlayerSlotState.PLAYING)
					&& (other.getController() == CMapControl.USER)) {
				return true;
			}
		}
		return false;
	}
}
