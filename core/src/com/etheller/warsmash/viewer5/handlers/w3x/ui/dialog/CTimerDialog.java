package com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog;

import com.badlogic.gdx.graphics.Color;
import com.etheller.interpreter.ast.util.AllocatedHandle;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

public class CTimerDialog extends AllocatedHandle {
	private GameUI rootFrame;
	private final CTimer timer;
	private UIFrame timerDialogFrame;
	private StringFrame valueFrame;
	private StringFrame titleFrame;
	private String title;
	private boolean visible;
	private Integer titleColor;
	private Integer timeColor;
	/** Set by TimerDialogSetRealTimeRemaining: a dialog without a timer counts this down itself. */
	private boolean realTime;
	private float realTimeBase;
	private double realTimeStart;
	private float speed = 1f;

	public CTimerDialog(final GameUI rootFrame, final CTimer timer, final UIFrame timerDialogFrame,
			final StringFrame valueFrame, final StringFrame titleFrame) {
		this.rootFrame = rootFrame;
		this.timer = timer;
		this.timerDialogFrame = timerDialogFrame;
		this.valueFrame = valueFrame;
		this.titleFrame = titleFrame;
	}

	/** A saved game keeps what the dialog shows; the loaded game gives it new frames to show it in. */
	public void attachFrames(final GameUI rootFrame, final UIFrame timerDialogFrame, final StringFrame valueFrame,
			final StringFrame titleFrame) {
		this.rootFrame = rootFrame;
		this.timerDialogFrame = timerDialogFrame;
		this.valueFrame = valueFrame;
		this.titleFrame = titleFrame;
		if (this.title != null) {
			setTitle(this.title);
		}
		if (this.titleColor != null) {
			setTitleColor(this.titleColor);
		}
		if (this.timeColor != null) {
			setTimeColor(this.timeColor);
		}
		setVisible(this.visible);
	}

	public UIFrame getFrame() {
		return this.timerDialogFrame;
	}

	public CTimer getTimer() {
		return this.timer;
	}

	public void setTitleColor(final int rgba) {
		this.titleColor = rgba;
		if (this.titleFrame != null) {
			this.titleFrame.setColor(new Color(rgba));
		}
	}

	public void setTimeColor(final int rgba) {
		this.timeColor = rgba;
		if (this.valueFrame != null) {
			this.valueFrame.setColor(new Color(rgba));
		}
	}

	public void setTitle(final String title) {
		this.title = title;
		this.rootFrame.setText(this.titleFrame, title);
	}

	public void setValue(final GameUI rootFrame, final String value) {
		rootFrame.setText(this.valueFrame, value);
	}

	public void setVisible(final boolean visible) {
		this.visible = visible;
		this.timerDialogFrame.setVisible(visible);
	}

	public boolean isVisible() {
		return this.timerDialogFrame.isVisible();
	}

	/** Sets the time the dialog shows and counts down from now, at the speed set for it. */
	public void setRealTimeRemaining(final CSimulation simulation, final float seconds) {
		this.realTime = true;
		this.realTimeBase = seconds;
		this.realTimeStart = CTimer.currentTime(simulation);
	}

	/** Scales how fast the shown time runs down; the time shown right now is kept. */
	public void setSpeed(final CSimulation simulation, final float speed) {
		if (this.realTime) {
			this.realTimeBase = getRealTimeRemaining(simulation);
			this.realTimeStart = CTimer.currentTime(simulation);
		}
		this.speed = speed;
	}

	private float getRealTimeRemaining(final CSimulation simulation) {
		final double elapsed = CTimer.currentTime(simulation) - this.realTimeStart;
		return (float) Math.max(0, this.realTimeBase - (elapsed * this.speed));
	}

	public void update(final GameUI rootFrame, final CSimulation simulation) {
		if (this.timerDialogFrame.isVisible()) {
			final float remaining;
			if (this.timer != null) {
				remaining = this.timer.getRemaining(simulation);
			}
			else if (this.realTime) {
				remaining = getRealTimeRemaining(simulation);
			}
			else {
				return;
			}
			final int secondsRemaining = (int) remaining;
			final int minutes = secondsRemaining / 60;
			final int seconds = secondsRemaining % 60;

			rootFrame.setText(this.valueFrame, minutes + ":" + (seconds < 10 ? "0" : "") + seconds);
		}
	}

}
