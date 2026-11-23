package com.etheller.warsmash.lan.ui;

import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.lan.LanPlay;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.AnchorDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.MeleeChat;

import net.warsmash.lan.protocol.LanProtocol;

/**
 * The window of a LAN game that waits: who is waited for and why, for how long, and what the others may do about it
 * ("Continue without them", "Claim victory"), or that the host is gone. It is built from the same frames as a map
 * script's dialog, so it looks and takes clicks like one. The rules of when it shows and what it offers are
 * {@link LanPlay}'s; this class only draws them. Also moves the lines about players who left into the game's
 * message area.
 */
public final class LanOverlay {
	/** The game's interface, as far as the window needs it. */
	public interface Host {
		/** A line for the game's message area. */
		void showLine(String line);

		/** A line a referee or an observer said. */
		void showChat(LanPlay.WatcherChat chat);

		/** The player leaves the game (the window of a lost host offers it). */
		void leave();
	}

	private static final float BUTTON_HEIGHT = 0.03f;
	private static final float BUTTON_GAP = 0.005f;
	private static final float MARGIN = 0.035f;

	private final GameUI rootFrame;
	private final Viewport viewport;
	private final LanPlay play;
	private final Host host;
	private SimpleFrame frame;
	private StringFrame text;
	private GlueTextButtonFrame continueButton;
	private StringFrame continueLabel;
	private GlueTextButtonFrame claimButton;
	private StringFrame claimLabel;
	private String shownLayout = "";
	private String shownText = "";
	private String shownContinue = "";
	private String shownClaim = "";

	public LanOverlay(final GameUI rootFrame, final Viewport viewport, final LanPlay play, final Host host) {
		this.rootFrame = rootFrame;
		this.viewport = viewport;
		this.play = play;
		this.host = host;
	}

	/** Every frame of the game's interface. */
	public void update(final float deltaTime) {
		String line;
		while ((line = this.play.pollMessage()) != null) {
			this.host.showLine(line);
		}
		LanPlay.WatcherChat chat;
		while ((chat = this.play.pollChat()) != null) {
			this.host.showChat(chat);
		}
		final LanPlay.Window window = this.play.window();
		if (window == null) {
			hide();
			return;
		}
		final String layout = layoutOf(window);
		if (!layout.equals(this.shownLayout)) {
			build(window, layout);
		}
		refresh(window);
	}

	public boolean isShown() {
		return this.frame != null;
	}

	/** What decides which frames the window has; the texts change under the same frames. */
	static String layoutOf(final LanPlay.Window window) {
		return (window.cannotSync ? "sync" : window.rejoinPercent >= 0 ? "rejoin"
				: window.migrating ? "migrating" : window.hostLost ? "host" : window.loading ? "loading"
						: window.absentOnly ? "absent" : "wait")
				+ ":" + window.rows.size() + ":"
				+ (window.continueInSeconds >= 0) + ":" + (window.claimInSeconds >= 0);
	}

	static String reasonText(final int reason) {
		switch (reason) {
		case LanProtocol.LAG_BACKGROUND:
			return "app in background";
		case LanProtocol.LAG_RECONNECTING:
			return "reconnecting";
		case LanProtocol.LAG_MIGRATING:
			return "host migration";
		default:
			return "not responding";
		}
	}

	/** The text of the window, with the players' names in their colors. */
	static String textOf(final LanPlay.Window window) {
		final StringBuilder b = new StringBuilder();
		if (window.cannotSync) {
			return com.etheller.warsmash.launcher.lan.LanText.cannotSync();
		}
		if (window.rejoinPercent >= 0) {
			return com.etheller.warsmash.launcher.lan.LanText.rejoining(window.rejoinPercent, window.rejoinTime);
		}
		if (window.migrating) {
			return "Host migration...";
		}
		if (window.hostLost) {
			return "The host has left the game.|nThe game cannot go on.";
		}
		if (window.loading) {
			b.append("Waiting for players to load...");
			for (final LanPlay.Row row : window.rows) {
				b.append("|n|cff").append(MeleeChat.playerTextHex(row.color)).append(row.name).append("|r - loading - ")
						.append(row.loadPercent).append(" %");
			}
			return b.toString();
		}
		if (window.absentOnly) {
			return "Your opponents are absent.";
		}
		b.append("Waiting for players...");
		for (final LanPlay.Row row : window.rows) {
			b.append("|n|cff").append(MeleeChat.playerTextHex(row.color)).append(row.name).append("|r - ")
					.append(reasonText(row.reason)).append(" - ").append(row.waitedMs / 1000).append(" s");
		}
		return b.toString();
	}

	static String continueLabelOf(final LanPlay.Window window) {
		return window.canContinue() ? "Continue without them"
				: "Continue without them (" + window.continueInSeconds + ")";
	}

	static String claimLabelOf(final LanPlay.Window window) {
		return window.canClaim() ? "Claim victory" : "Claim victory (" + window.claimInSeconds + ")";
	}

	private void hide() {
		if (this.frame != null) {
			Log.warn("LAN: the waiting window is closed");
			this.rootFrame.remove(this.frame);
			this.frame = null;
			this.text = null;
			this.continueButton = null;
			this.claimButton = null;
			this.shownLayout = "";
		}
	}

	private void build(final LanPlay.Window window, final String layout) {
		hide();
		this.shownLayout = layout;
		Log.warn("LAN: the waiting window shows " + layout);
		this.shownText = "";
		this.shownContinue = "";
		this.shownClaim = "";
		// Made as a map script makes a dialog, so that the frames are the ones the game's input already takes clicks on.
		this.frame = (SimpleFrame) this.rootFrame.createFrame("ScriptDialog", this.rootFrame, 0, 0);
		this.frame.addAnchor(new AnchorDefinition(FramePoint.TOP, 0, GameUI.convertY(this.viewport, -0.05f)));
		this.text = (StringFrame) this.rootFrame.getFrameByName("ScriptDialogText", 0);
		this.rootFrame.setText(this.text, textOf(window));
		this.shownText = textOf(window);
		this.frame.positionBounds(this.rootFrame, this.viewport);
		UIFrame last = this.text;
		int buttons = 0;
		if (window.hostLost || window.migrating || window.cannotSync) {
			last = addButton(last, "Leave game", this.host::leave, false);
			buttons++;
		}
		else {
			if (window.continueInSeconds >= 0) {
				last = addButton(last, continueLabelOf(window), this.play::continueWithout, true);
				buttons++;
			}
			if (window.claimInSeconds >= 0) {
				last = addButton(last, claimLabelOf(window), this.play::claimVictory, false);
				buttons++;
			}
		}
		final float textHeight = this.text.getAssignedHeight() > 0 ? this.text.getAssignedHeight()
				: this.text.getPredictedViewportHeight();
		this.frame.setHeight(textHeight
				+ (buttons * GameUI.convertY(this.viewport, BUTTON_HEIGHT + BUTTON_GAP))
				+ GameUI.convertY(this.viewport, MARGIN));
		this.frame.positionBounds(this.rootFrame, this.viewport);
		this.frame.setVisible(true);
	}

	private UIFrame addButton(final UIFrame above, final String label, final Runnable onClick,
			final boolean isContinue) {
		final GlueTextButtonFrame button = (GlueTextButtonFrame) this.rootFrame.createFrame("ScriptDialogButton",
				this.frame, 0, 0);
		button.setHeight(GameUI.convertY(this.viewport, BUTTON_HEIGHT));
		final StringFrame buttonLabel = (StringFrame) this.rootFrame.getFrameByName("ScriptDialogButtonText", 0);
		this.rootFrame.setText(buttonLabel, label);
		button.addSetPoint(new SetPoint(FramePoint.TOP, above, FramePoint.BOTTOM, 0,
				GameUI.convertY(this.viewport, -BUTTON_GAP)));
		this.frame.add(button);
		button.positionBounds(this.rootFrame, this.viewport);
		button.setOnClick(onClick);
		if (isContinue) {
			this.continueButton = button;
			this.continueLabel = buttonLabel;
		}
		else {
			this.claimButton = button;
			this.claimLabel = buttonLabel;
		}
		return button;
	}

	private void refresh(final LanPlay.Window window) {
		final String content = textOf(window);
		if (!content.equals(this.shownText) && this.text != null) {
			this.shownText = content;
			this.rootFrame.setText(this.text, content);
		}
		if (this.continueButton != null && window.continueInSeconds >= 0) {
			final String label = continueLabelOf(window);
			if (!label.equals(this.shownContinue)) {
				this.shownContinue = label;
				this.rootFrame.setText(this.continueLabel, label);
			}
			this.continueButton.setEnabled(window.canContinue());
		}
		if (this.claimButton != null && window.claimInSeconds >= 0) {
			final String label = claimLabelOf(window);
			if (!label.equals(this.shownClaim)) {
				this.shownClaim = label;
				this.rootFrame.setText(this.claimLabel, label);
			}
			this.claimButton.setEnabled(window.canClaim());
		}
	}
}
