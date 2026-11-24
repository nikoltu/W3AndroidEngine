package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.Arrays;

/**
 * State of every unit at the end of one simulation step, as parallel arrays (structure of arrays) so a frame is
 * filled and interpolated without allocating.
 * <p>
 * Writer: the simulation thread, while the owning {@link SimFrame} is being built. Reader: the render thread (world
 * view, bars, minimap, cursor), only after the frame is published. Rows are sorted by ascending {@link #handle}, so two
 * frames are matched by a merge walk. Fields are public for speed; the arrays may be longer than {@link #count}.
 */
public final class UnitFrameState {
	// flags
	public static final int HIDDEN = 1;
	public static final int DEAD = 1 << 1;
	public static final int VISIBLE_TO_LOCAL = 1 << 2;
	public static final int BUILDING = 1 << 3;
	public static final int BUSY = 1 << 4;
	public static final int HERO = 1 << 5;
	public static final int FLYING = 1 << 6;
	public static final int SUMMONED = 1 << 7;
	public static final int CORPSE = 1 << 8;
	public static final int BONE_CORPSE = 1 << 9;
	public static final int TEAM_GLOW = 1 << 10;
	/** An illusion the local player is allied with (drawn with the illusion tint). */
	public static final int ILLUSION_ALLY = 1 << 11;
	/** The unit's fade timer is running. */
	public static final int FADING = 1 << 12;
	/** Under construction or being upgraded. */
	public static final int CONSTRUCTING = 1 << 13;
	public static final int HIDE_MINIMAP_ICON = 1 << 14;
	public static final int ALT_MINIMAP_ICON = 1 << 15;
	/** The unit has a mana pool (a mana bar is shown for it). */
	public static final int HAS_MANA = 1 << 16;
	/** The local player may select the unit (it is not marked unselectable for them). */
	public static final int SELECTABLE = 1 << 17;
	/** The local player shares control of the unit's owner (the owner is not the local player). */
	public static final int CONTROL_SHARED = 1 << 18;
	/** As {@link #CONTROL_SHARED}, also when only the player the unit is drawn as shares control. */
	public static final int TEAM_CONTROL_SHARED = 1 << 19;
	/** The local player is allied (passive) with the player the unit is drawn as. */
	public static final int TEAM_ALLIED = 1 << 20;
	/** The local player is not allied with the player the unit is drawn as (selection circle in the enemy colour). */
	public static final int SELECT_ENEMY = 1 << 21;
	/** An allied unit the local player shares control with, or whose real owner it does (friend colour). */
	public static final int SELECT_FRIEND = 1 << 22;
	/** Its selection circle is drawn above the water: it may move on water, hovers or flies. */
	public static final int ABOVE_WATER = 1 << 23;
	/** Owned by a neutral aggressive player: a creep. */
	public static final int CREEP = 1 << 24;
	/** Has the building alarm ability, so an attack on it raises the under attack alert. */
	public static final int ALARM = 1 << 25;
	/** Being built for the first time (an upgrade is {@link #CONSTRUCTING} but not this). */
	public static final int RISING = 1 << 26;

	// targetClass: how the local player sees the unit (cursor colour, targeting rejection)
	public static final int TARGET_ALLY = 1;
	public static final int TARGET_ENEMY = 1 << 1;
	public static final int TARGET_NEUTRAL = 1 << 2;
	public static final int TARGET_MECHANICAL = 1 << 3;
	public static final int TARGET_INVULNERABLE = 1 << 4;
	public static final int TARGET_OWN = 1 << 5;

	/** Waypoints kept per unit for extrapolation. */
	public static final int PATH_POINTS = 4;

	public int count;
	public int[] handle = new int[0];
	public int[] typeId = new int[0];
	public int[] owner = new int[0];
	public float[] x = new float[0];
	public float[] y = new float[0];
	/** Height above the terrain for flyers, ground height otherwise. */
	public float[] z = new float[0];
	public float[] facing = new float[0];
	public float[] speed = new float[0];
	public float[] scale = new float[0];
	public byte[] movementType = new byte[0];
	public int[] flags = new int[0];
	public byte[] invisLevels = new byte[0];
	public byte[] targetClass = new byte[0];
	public float[] hpFraction = new float[0];
	/** Absolute life points, for what compares losses between units (the replay's auto camera). */
	public float[] life = new float[0];
	public float[] manaFraction = new float[0];
	public int[] playerColor = new int[0];
	public int[] vertexColor = new int[0];
	/** Order type id of the current order, 0 when idle. */
	public int[] orderId = new int[0];
	/** Type id first in the build or train queue, 0 when none (type prewarm). */
	public int[] queueFirstType = new int[0];
	public byte[] pathCount = new byte[0];
	/** {@link #PATH_POINTS} x/y pairs per unit, nearest first. */
	public float[] path = new float[0];
	/** Player the unit is drawn as (the fake player index of a unit that pretends to belong to another player). */
	public int[] teamPlayer = new int[0];
	/** Seconds left on the fade timer and its full length, at the end of the step; 0 without a fade. */
	public float[] fadeRemaining = new float[0];
	public float[] fadeTimeout = new float[0];
	/** Construction or upgrade progress, 0..1. */
	public float[] constructRatio = new float[0];
	/** Seconds a corpse takes to decay (0 for a unit that is not a corpse). */
	public float[] decayTime = new float[0];
	/** Minimap landmark kind of the unit (RenderUnit.LANDMARK_*). */
	public byte[] landmark = new byte[0];
	/** How the local player sees the team player, as {@code AllyColorFilter.relation} says. */
	public byte[] relation = new byte[0];
	/** Unit type level (creep camp strength on the minimap). */
	public int[] level = new int[0];
	/** Collision size, for picking a building that is still rising. */
	public float[] collisionSize = new float[0];

	/** Empties the state, keeping the arrays. */
	public void clear() {
		count = 0;
	}

	/** Makes room for one more row and returns its index; the caller sets every column of it. */
	public int addRow() {
		if (count == handle.length) {
			grow(Math.max(64, count * 2));
		}
		return count++;
	}

	public void copyFrom(final UnitFrameState other) {
		count = 0;
		if (other.count > handle.length) {
			grow(other.count);
		}
		count = other.count;
		final int n = count;
		System.arraycopy(other.handle, 0, handle, 0, n);
		System.arraycopy(other.typeId, 0, typeId, 0, n);
		System.arraycopy(other.owner, 0, owner, 0, n);
		System.arraycopy(other.x, 0, x, 0, n);
		System.arraycopy(other.y, 0, y, 0, n);
		System.arraycopy(other.z, 0, z, 0, n);
		System.arraycopy(other.facing, 0, facing, 0, n);
		System.arraycopy(other.speed, 0, speed, 0, n);
		System.arraycopy(other.scale, 0, scale, 0, n);
		System.arraycopy(other.movementType, 0, movementType, 0, n);
		System.arraycopy(other.flags, 0, flags, 0, n);
		System.arraycopy(other.invisLevels, 0, invisLevels, 0, n);
		System.arraycopy(other.targetClass, 0, targetClass, 0, n);
		System.arraycopy(other.hpFraction, 0, hpFraction, 0, n);
		System.arraycopy(other.life, 0, life, 0, n);
		System.arraycopy(other.manaFraction, 0, manaFraction, 0, n);
		System.arraycopy(other.playerColor, 0, playerColor, 0, n);
		System.arraycopy(other.vertexColor, 0, vertexColor, 0, n);
		System.arraycopy(other.orderId, 0, orderId, 0, n);
		System.arraycopy(other.queueFirstType, 0, queueFirstType, 0, n);
		System.arraycopy(other.pathCount, 0, pathCount, 0, n);
		System.arraycopy(other.path, 0, path, 0, n * PATH_POINTS * 2);
		System.arraycopy(other.teamPlayer, 0, teamPlayer, 0, n);
		System.arraycopy(other.fadeRemaining, 0, fadeRemaining, 0, n);
		System.arraycopy(other.fadeTimeout, 0, fadeTimeout, 0, n);
		System.arraycopy(other.constructRatio, 0, constructRatio, 0, n);
		System.arraycopy(other.decayTime, 0, decayTime, 0, n);
		System.arraycopy(other.landmark, 0, landmark, 0, n);
		System.arraycopy(other.relation, 0, relation, 0, n);
		System.arraycopy(other.level, 0, level, 0, n);
		System.arraycopy(other.collisionSize, 0, collisionSize, 0, n);
	}

	/** Row of the handle, or -1; binary search because rows are sorted by handle. */
	public int indexOf(final int unitHandle) {
		final int index = Arrays.binarySearch(handle, 0, count, unitHandle);
		return index >= 0 ? index : -1;
	}

	public boolean has(final int row, final int flag) {
		return (flags[row] & flag) != 0;
	}

	private void grow(final int capacity) {
		handle = Arrays.copyOf(handle, capacity);
		typeId = Arrays.copyOf(typeId, capacity);
		owner = Arrays.copyOf(owner, capacity);
		x = Arrays.copyOf(x, capacity);
		y = Arrays.copyOf(y, capacity);
		z = Arrays.copyOf(z, capacity);
		facing = Arrays.copyOf(facing, capacity);
		speed = Arrays.copyOf(speed, capacity);
		scale = Arrays.copyOf(scale, capacity);
		movementType = Arrays.copyOf(movementType, capacity);
		flags = Arrays.copyOf(flags, capacity);
		invisLevels = Arrays.copyOf(invisLevels, capacity);
		targetClass = Arrays.copyOf(targetClass, capacity);
		hpFraction = Arrays.copyOf(hpFraction, capacity);
		life = Arrays.copyOf(life, capacity);
		manaFraction = Arrays.copyOf(manaFraction, capacity);
		playerColor = Arrays.copyOf(playerColor, capacity);
		vertexColor = Arrays.copyOf(vertexColor, capacity);
		orderId = Arrays.copyOf(orderId, capacity);
		queueFirstType = Arrays.copyOf(queueFirstType, capacity);
		pathCount = Arrays.copyOf(pathCount, capacity);
		path = Arrays.copyOf(path, capacity * PATH_POINTS * 2);
		teamPlayer = Arrays.copyOf(teamPlayer, capacity);
		fadeRemaining = Arrays.copyOf(fadeRemaining, capacity);
		fadeTimeout = Arrays.copyOf(fadeTimeout, capacity);
		constructRatio = Arrays.copyOf(constructRatio, capacity);
		decayTime = Arrays.copyOf(decayTime, capacity);
		landmark = Arrays.copyOf(landmark, capacity);
		relation = Arrays.copyOf(relation, capacity);
		level = Arrays.copyOf(level, capacity);
		collisionSize = Arrays.copyOf(collisionSize, capacity);
	}
}
