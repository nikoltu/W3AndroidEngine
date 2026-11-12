package com.etheller.warsmash.viewer5.handlers.w3x;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.etheller.interpreter.ast.util.CHandle;
import com.etheller.warsmash.parsers.fdf.frames.ColorCodedText;

public class TextTag implements CHandle {
	public static final int NO_RECEIVING_PLAYER = -1;

	private final Vector3 position;
	private final Vector2 velocity;
	private String text;
	private final Color color;
	private float lifetimeDuration;
	private float fadeStart;
	private float fontHeight;
	private int handleId;
	private final Vector2 screenCoordTravelOffset;
	private float lifetime = 0;
	/** Age in game time, which the simulation counts so a script tag's id comes back at the same step everywhere. */
	private float gameAge = 0;
	private boolean suspended;
	private boolean visible;
	private boolean permanent;
	private boolean centered;
	private boolean centeredOnPoint;
	private int receivingPlayer = NO_RECEIVING_PLAYER;
	private final ColorCodedText layout = new ColorCodedText();

	public TextTag(final Vector3 position, final Vector2 velocity, final String text, final Color color,
			final float lifetimeDuration, final float fadeStart, final float fontHeight, final int handleId) {
		this.position = position;
		this.velocity = velocity;
		this.text = text;
		this.color = new Color(color);
		this.lifetimeDuration = lifetimeDuration;
		this.fadeStart = fadeStart;
		this.fontHeight = fontHeight;
		this.handleId = handleId;
		this.screenCoordTravelOffset = new Vector2(0, 0);
		this.visible = true;
		this.position.z += 10f;
	}

	public boolean update(final float deltaTime) {
		if (this.suspended) {
			return false;
		}
		this.screenCoordTravelOffset.add(this.velocity.x * deltaTime, this.velocity.y * deltaTime);
		this.lifetime += deltaTime;
		if (this.permanent) {
			return false;
		}
		return this.lifetime > this.lifetimeDuration;
	}

	public Vector3 getPosition() {
		return this.position;
	}

	public float getRemainingLife() {
		return this.lifetimeDuration - this.lifetime;
	}

	public Color getColor() {
		return this.color;
	}

	/**
	 * The alpha the tag is drawn with. A permanent tag is opaque whatever the alpha of its color; one that is fading
	 * takes the share of its fade still to go instead of that alpha.
	 */
	public float getDrawAlpha() {
		if (this.permanent) {
			return 1f;
		}
		final float fadingSeconds = this.lifetimeDuration - this.fadeStart;
		if ((fadingSeconds > 0) && (this.lifetime > this.fadeStart)) {
			return Math.max(0f, Math.min(1f, getRemainingLife() / fadingSeconds));
		}
		return this.color.a;
	}

	public String getText() {
		return this.text;
	}

	public void setText(final String text) {
		this.text = text;
	}

	public Vector2 getScreenCoordTravelOffset() {
		return this.screenCoordTravelOffset;
	}

	public float getFadeStart() {
		return this.fadeStart;
	}

	public void setFadeStart(final float fadeStart) {
		this.fadeStart = fadeStart;
	}

	public float getFontHeight() {
		return this.fontHeight;
	}

	public void setFontHeight(final float fontHeight) {
		this.fontHeight = fontHeight;
	}

	public void setLifetime(final float lifetime) {
		this.lifetime = lifetime;
		this.gameAge = lifetime;
	}

	/** @return true once a tag that is not permanent has outlived its lifespan */
	public boolean advanceGameAge(final float seconds) {
		if (this.permanent || this.suspended) {
			return false;
		}
		this.gameAge += seconds;
		return this.gameAge > this.lifetimeDuration;
	}

	public float getLifetimeDuration() {
		return this.lifetimeDuration;
	}

	public void setLifetimeDuration(final float lifetimeDuration) {
		this.lifetimeDuration = lifetimeDuration;
	}

	public boolean isSuspended() {
		return this.suspended;
	}

	public void setSuspended(final boolean suspended) {
		this.suspended = suspended;
	}

	@Override
	public int getHandleId() {
		return this.handleId;
	}

	public void setHandleId(final int handleId) {
		this.handleId = handleId;
	}

	public void setPosition(final float x, final float y, final float z) {
		this.position.set(x, y, z);
		this.position.z += 10f;
	}

	public void setColor(final int r, final int g, final int b, final float alpha) {
		this.color.r = r / 255.0f;
		this.color.g = g / 255.0f;
		this.color.b = b / 255.0f;
		this.color.a = alpha / 255f;
	}

	public void setVelocity(final float vx, final float vy) {
		this.velocity.set(vx, vy);
	}

	public boolean isVisible() {
		return this.visible;
	}

	public void setVisible(final boolean visible) {
		this.visible = visible;
	}

	public boolean isPermanent() {
		return this.permanent;
	}

	public void setPermanent(final boolean permanent) {
		this.permanent = permanent;
	}

	public void setCentered(final boolean centered) {
		this.centered = centered;
	}

	public boolean isCentered() {
		return this.centered;
	}

	/** The text the game makes for a unit is centered across the point, but its bottom line stays on the point. */
	public void setCenteredOnPoint(final boolean centeredOnPoint) {
		this.centeredOnPoint = centeredOnPoint;
	}

	public boolean isCenteredOnPoint() {
		return this.centeredOnPoint;
	}

	/** The player the text is for; every other player is not shown it. NO_RECEIVING_PLAYER shows it to all. */
	public void setReceivingPlayer(final int receivingPlayer) {
		this.receivingPlayer = receivingPlayer;
	}

	public boolean isShownTo(final int playerIndex) {
		return (this.receivingPlayer == NO_RECEIVING_PLAYER) || (this.receivingPlayer == playerIndex);
	}

	/** The text laid out for drawing, kept by the renderer between frames. */
	public ColorCodedText getLayout() {
		return this.layout;
	}
}
