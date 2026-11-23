package com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.RemovableTriggerEvent;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.interpreter.ast.util.AllocatedHandle;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;

public class CScriptDialog extends AllocatedHandle {
	private static final float LOST_TITLE_HEIGHT = 0.0001f;
	private final GlobalScope globalScope;
	private SimpleFrame scriptDialogFrame;
	private StringFrame scriptDialogTextFrame;
	private UIFrame lastAddedComponent;
	private final List<Trigger> eventTriggers = new ArrayList<>();
	private final List<CScriptDialogButton> buttons = new ArrayList<>();
	private String title;
	private boolean visible;
	private int shownOrder;
	private int shownTick;
	/**
	 * A dialog shown in the place of another one, and the one it took the place of, have no title on the screen until
	 * a script sets their message again.
	 */
	private boolean titleLost;
	/** Whether the title has been on the screen since the message was last set. */
	private boolean titleApplied;

	public CScriptDialog(final GlobalScope globalScope, final SimpleFrame scriptDialogFrame,
			final StringFrame scriptDialogTextFrame) {
		this.globalScope = globalScope;
		this.scriptDialogFrame = scriptDialogFrame;
		this.scriptDialogTextFrame = scriptDialogTextFrame;
		this.lastAddedComponent = scriptDialogTextFrame;
	}

	public SimpleFrame getScriptDialogFrame() {
		return this.scriptDialogFrame;
	}

	public StringFrame getScriptDialogTextFrame() {
		return this.scriptDialogTextFrame;
	}

	public UIFrame getLastAddedComponent() {
		return this.lastAddedComponent;
	}

	public void setTitle(final GameUI rootFrame, final String text) {
		this.title = text;
		if (this.scriptDialogTextFrame != null) {
			if (this.titleLost) {
				// Back to the height of its text.
				this.scriptDialogTextFrame.setHeight(0);
			}
			rootFrame.setText(this.scriptDialogTextFrame, text);
		}
		this.titleLost = false;
		this.titleApplied = false;
	}

	public String getTitle() {
		return this.title;
	}

	public List<CScriptDialogButton> getButtons() {
		return this.buttons;
	}

	public boolean isVisible() {
		return this.visible;
	}

	public void reset(final SimpleFrame scriptDialogFrame, final StringFrame scriptDialogTextFrame) {
		this.scriptDialogFrame = scriptDialogFrame;
		this.scriptDialogTextFrame = scriptDialogTextFrame;
		this.lastAddedComponent = scriptDialogTextFrame;
		this.buttons.clear();
		this.title = null;
		this.titleLost = false;
		this.titleApplied = false;
		this.visible = false;
	}

	public int getShownOrder() {
		return this.shownOrder;
	}

	/** Which dialog was shown last: the larger, the later. */
	public void setShownOrder(final int shownOrder) {
		this.shownOrder = shownOrder;
	}

	/** The game step the dialog was displayed in. */
	public int getShownTick() {
		return this.shownTick;
	}

	public void setShownTick(final int shownTick) {
		this.shownTick = shownTick;
	}

	/** The shown dialog among these that was shown last: a save from before dialogs replaced each other can hold several. */
	public static CScriptDialog shownLast(final Iterable<CScriptDialog> dialogs) {
		CScriptDialog last = null;
		for (final CScriptDialog dialog : dialogs) {
			if (dialog.isVisible() && ((last == null) || (dialog.getShownOrder() > last.getShownOrder()))) {
				last = dialog;
			}
		}
		return last;
	}

	public boolean isTitleLost() {
		return this.titleLost;
	}

	public boolean isTitleApplied() {
		return this.titleApplied;
	}

	public void setTitleApplied(final boolean titleApplied) {
		this.titleApplied = titleApplied;
	}

	/** Takes the title off the box, which closes up around the buttons. */
	public void loseTitle(final GameUI rootFrame, final Viewport uiViewport) {
		this.titleLost = true;
		if (this.scriptDialogTextFrame != null) {
			// An empty text still stands one line high.
			this.scriptDialogTextFrame.setHeight(LOST_TITLE_HEIGHT);
			rootFrame.setText(this.scriptDialogTextFrame, "");
			layOut(rootFrame, uiViewport);
		}
	}

	/** Places the buttons again after the title changed, and fits the box around them. */
	public void layOut(final GameUI rootFrame, final Viewport uiViewport) {
		if (this.buttons.isEmpty() || (this.scriptDialogFrame == null)) {
			return;
		}
		for (final CScriptDialogButton button : this.buttons) {
			button.getButtonFrame().positionBounds(rootFrame, uiViewport);
		}
		fitHeight(rootFrame, uiViewport, this.buttons.get(this.buttons.size() - 1).getButtonFrame());
	}

	public void setVisible(final boolean flag) {
		this.visible = flag;
		if (this.scriptDialogFrame != null) {
			this.scriptDialogFrame.setVisible(flag);
		}
	}

	public RemovableTriggerEvent addEvent(final Trigger trigger) {
		this.eventTriggers.add(trigger);
		return new RemovableTriggerEvent(trigger) {
			@Override
			public void remove() {
				CScriptDialog.this.eventTriggers.remove(trigger);
			}
		};
	}

	public void onButtonClick(final CScriptDialogButton cScriptDialogButton) {
		setVisible(false);
		for (final Trigger trigger : this.eventTriggers) {
			final CommonTriggerExecutionScope scope = CommonTriggerExecutionScope
					.triggerDialogScope(JassGameEventsWar3.EVENT_DIALOG_CLICK, trigger, this, cScriptDialogButton);
			this.globalScope.queueTrigger(null, null, trigger, scope, scope);
		}
	}

	public void addButton(final GameUI rootFrame, final Viewport uiViewport,
			final CScriptDialogButton scriptDialogButton, final Runnable onPress) {
		this.buttons.add(scriptDialogButton);
		final GlueTextButtonFrame buttonFrame = scriptDialogButton.getButtonFrame();
		this.scriptDialogFrame.add(buttonFrame);
		this.lastAddedComponent = buttonFrame;
		buttonFrame.positionBounds(rootFrame, uiViewport);
		fitHeight(rootFrame, uiViewport, buttonFrame);
		scriptDialogButton.setupEvents(onPress);
	}

	// The box is as tall as its content: from its top down to the last button, and the gap the title has above it
	// below that.
	private void fitHeight(final GameUI rootFrame, final Viewport uiViewport, final UIFrame lastButtonFrame) {
		final float top = this.scriptDialogFrame.getFramePointY(FramePoint.TOP);
		final float titleInset = top - this.scriptDialogTextFrame.getFramePointY(FramePoint.TOP);
		this.scriptDialogFrame.setHeight((top - lastButtonFrame.getFramePointY(FramePoint.BOTTOM)) + titleInset);
		this.scriptDialogFrame.positionBounds(rootFrame, uiViewport);
	}

	public GlobalScope getGlobalScope() {
		return this.globalScope;
	}
}
