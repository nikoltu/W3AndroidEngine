package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

/**
 * One call the simulation makes towards the render side (the controller and animation listener calls, and the script's
 * interface commands), recorded in the order it happened. The simulation thread fills it, the render thread applies it
 * once the presentation time reaches {@link #tick}. Instances belong to a {@link RenderEventPool}. The fixed fields are
 * shared by all codes (a code's comment says which it uses) so no event allocates.
 */
public final class RenderEvent {
	// Unit and widget lifecycle (a = handle, b = type id).
	public static final int UNIT_CREATED = 1;
	public static final int UNIT_REMOVED = 2;
	public static final int UNIT_TYPE_CHANGED = 3;
	public static final int ITEM_CREATED = 4;
	public static final int ITEM_REMOVED = 5;
	public static final int DESTRUCTABLE_CREATED = 6;
	public static final int DESTRUCTABLE_REMOVED = 7;
	// Animation listener (a = handle, b = animation or tag id, c = flags, f0 = speed).
	public static final int ANIMATION_PLAY = 20;
	public static final int ANIMATION_QUEUE = 21;
	public static final int SECONDARY_TAG_ADDED = 22;
	public static final int SECONDARY_TAG_REMOVED = 23;
	// Effects, projectiles, lightning, sound (a = handle or 0, b = type id, f0..f2 = position).
	public static final int EFFECT_ON_UNIT = 40;
	public static final int EFFECT_ON_POINT = 41;
	public static final int PROJECTILE_CREATED = 42;
	public static final int PROJECTILE_REMOVED = 43;
	public static final int LIGHTNING_CREATED = 44;
	public static final int LIGHTNING_REMOVED = 45;
	public static final int SOUND_PLAYED = 46;
	public static final int TEXT_TAG = 47;
	// Script interface commands for the local player (o0 = text or other immutable payload).
	public static final int DISPLAY_TEXT = 60;
	public static final int DIALOG = 61;
	public static final int CAMERA_COMMAND = 62;
	public static final int FRAME_COMMAND = 63;
	public static final int MULTIBOARD_COMMAND = 64;
	/** A native's change of what is drawn (a peer, the world's look), posted by the script natives' render side. */
	public static final int SCRIPT_RENDER_COMMAND = 65;

	/** Simulation tick the event belongs to; applied when the presentation time reaches it. */
	public int tick;
	/** Position among the tick's events, so events of one tick keep the order they happened in. */
	public int order;
	public int code;
	public int a;
	public int b;
	public int c;
	public int d;
	public float f0;
	public float f1;
	public float f2;
	public float f3;
	/**
	 * Immutable or otherwise thread-safe payload only (a string, a model path). A simulation object only as an
	 * identity the render side reads nothing of: the object a removal or an item's event is about, since its handle may
	 * already name a later object by the time the event is played.
	 */
	public Object o0;

	void clear() {
		tick = 0;
		order = 0;
		code = 0;
		a = 0;
		b = 0;
		c = 0;
		d = 0;
		f0 = 0;
		f1 = 0;
		f2 = 0;
		f3 = 0;
		o0 = null;
	}
}
