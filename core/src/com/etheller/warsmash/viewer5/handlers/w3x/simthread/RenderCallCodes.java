package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

/**
 * {@link RenderEvent#code}s of the calls {@link BufferingRenderController} records, one per method of the simulation's
 * render controller (plus the calls on the objects it hands back). The comment of a code says which fields it uses;
 * a unit, item or destructable is its handle id (0 for null), a rawcode is its int value (0 for null), an enum is
 * {@link RenderEvent#o0}. {@code proxy} is an id the simulation side gave the object it returned.
 */
public final class RenderCallCodes {
	// Projectile art. a = projectile id, b = source handle, f0/f1 = projectile x/y.
	/** c = bounce index, o0 = {@link BufferingRenderController.AttackArt}. */
	public static final int PROJECTILE_ART_ATTACK = 100;
	/** c = spell alias. */
	public static final int PROJECTILE_ART_SPELL = 101;

	// Lightning. a = proxy, b = lightning id, c = source handle, d = target handle, f0 = duration, f1 = ability index,
	// f2 = 1 when the duration passed was null.
	public static final int LIGHTNING_PLAIN = 102;
	public static final int LIGHTNING_PLAIN_DURATION = 103;
	public static final int LIGHTNING_ABILITY = 104;
	public static final int LIGHTNING_ABILITY_DURATION = 105;
	/** a = proxy, f0..f3 = r, g, b, a. */
	public static final int LIGHTNING_SET_COLOR = 106;
	/** a = proxy. */
	public static final int LIGHTNING_REMOVE = 107;

	/** a = proxy, b = splat id, f0/f1 = x/y. */
	public static final int UBERSPLAT_STATIC = 108;

	// World objects. The simulation side made the object; a = its handle.
	/** b = type, c = player, f0/f1 = x/y, f2 = facing, o0 = {@link WorldPeerData.Unit} or null. */
	public static final int UNIT_CREATE = 109;
	/** b = type, f0/f1 = x/y, o0 = {@link WorldPeerData.Item} or null. */
	public static final int ITEM_CREATE = 110;
	public static final int ITEM_UPDATE_MODEL = 111;
	/** b = type, c = variation, f0/f1 = x/y, f2 = facing, f3 = scale, o0 = {@link WorldPeerData.Destructable} or null. */
	public static final int DESTRUCTABLE_CREATE = 112;
	/** b = type, c = variation, d = scale as float bits, f0..f3 = x, y, z, facing, o0 = {@link WorldPeerData.Destructable} or null. */
	public static final int DESTRUCTABLE_CREATE_Z = 113;
	public static final int UNIT_REMOVE = 114;
	public static final int DESTRUCTABLE_REMOVE = 115;

	/** a = source handle, b = target handle, o0 = projectile art path. */
	public static final int INSTANT_ATTACK_EFFECT = 116;

	// Sounds.
	/** a = widget handle, o0 = String[] { weapon sound, armor type }. */
	public static final int DAMAGE_SOUND = 117;
	/** a = constructing unit, b = structure. */
	public static final int CONSTRUCTION_SOUND = 118;
	/** a = structure. */
	public static final int CONSTRUCTION_FINISH_SOUND = 119;
	/** a = structure. */
	public static final int UPGRADE_FINISH_SOUND = 120;
	/** a = unit. */
	public static final int UNIT_READY_SOUND = 121;
	/** a = unit, b = item. */
	public static final int UI_GET_ITEM_SOUND = 122;
	/** a = unit, b = item. */
	public static final int UI_DROP_ITEM_SOUND = 123;

	// Effects on units. a = unit.
	/** b = buff id, c = 1 when the buff id was null. */
	public static final int DEATH_EXPLODE_EFFECT = 124;
	public static final int GAIN_LEVEL_EFFECT = 125;
	public static final int UNIT_REPOSITIONED = 126;
	/** o0 = effect path. */
	public static final int EFFECT_ON_UNIT = 127;
	/** b = alias, o0 = effect type. */
	public static final int TEMPORARY_EFFECT_ON_UNIT = 128;
	/** a = proxy, b = unit, c = alias, o0 = effect type. */
	public static final int PERSISTENT_EFFECT_ON_UNIT = 129;
	/** a = proxy, b = unit, c = alias, d = index, o0 = effect type. */
	public static final int PERSISTENT_EFFECT_ON_UNIT_INDEXED = 130;
	/** a = proxy, c = alias, d = index, f0..f2 = x, y, facing, o0 = effect type. */
	public static final int EFFECT_ON_POINT = 131;
	/** a = 0, c = alias, d = index, f0..f2 = x, y, facing, o0 = effect type. */
	public static final int TEMPORARY_EFFECT_ON_POINT = 132;
	/** a = proxy, b = source unit, c = destructable, d = alias, f0 = attachment height, o0 = effect type. */
	public static final int EFFECT_OVER_DESTRUCTABLE = 133;

	// Ability sounds.
	/** a = proxy, b = caster, c = alias. */
	public static final int ABILITY_SOUND = 134;
	/** a = proxy, b = caster, c = alias. */
	public static final int ABILITY_SOUND_LOOP = 135;
	/** b = caster, c = alias. */
	public static final int ABILITY_SOUND_STOP = 136;
	/** a = proxy, c = alias, d = 1 when looping, f0/f1 = x/y. */
	public static final int ABILITY_SOUND_AT_POINT = 137;

	// Unit state. a = unit.
	/** b = new unit. */
	public static final int SELECTION_REPLACEMENT = 138;
	/** c = 1 when eye candy. */
	public static final int HERO_REVIVED = 139;
	public static final int HERO_DEATH = 140;
	/** b = upgrade id. */
	public static final int UPGRADING = 141;
	/** b = upgrade id. */
	public static final int UPGRADE_CANCELLED = 142;
	/** b = type, c = 1 when the portrait is updated. */
	public static final int UNIT_UPDATED_TYPE = 143;
	/** b = player index. */
	public static final int UNIT_COLOR = 144;
	/** b = previous, c = new color. */
	public static final int UNIT_PLAYER_COLOR = 145;
	/** f0..f3 = r, g, b, a (the colour object itself is not kept). */
	public static final int UNIT_VERTEX_COLOR = 146;
	/** f0..f2 = r, g, b. */
	public static final int UNIT_VERTEX_COLOR_RGB = 147;
	/** f0..f3 = r, g, b, a. */
	public static final int UNIT_VERTEX_COLOR_RGBA = 148;
	/** f0 = scale, c = 1 when it multiplies. */
	public static final int UNIT_SCALE = 149;

	// Terrain.
	/** f0..f2 = x, y, amount. */
	public static final int ADJUST_TERRAIN = 150;
	/** o0 = {@link BufferingRenderController.TerrainAdjustment}. */
	public static final int ADJUST_TERRAIN_RECT = 151;
	/** o0 = float[] x, y pairs of the corners that changed (the event owns it), c = 1 when blighted. */
	public static final int BLIGHT = 152;
	/** a..d = column, row, width, height, o0 = float[] offsets (copy). */
	public static final int TERRAIN_DEFORMATION = 154;
	/** o0 = int[] cells (copy). */
	public static final int TERRAIN_TYPES = 155;

	// Text tags. a = proxy.
	/** b = unit, d = amount, o0 = config type. */
	public static final int TEXT_TAG_SPAWN_AMOUNT = 156;
	/** b = unit, o0 = {@link BufferingRenderController.TextTagMessage}. */
	public static final int TEXT_TAG_SPAWN_MESSAGE = 157;
	public static final int TEXT_TAG_CREATE = 158;
	public static final int TEXT_TAG_DESTROY = 159;
	/** b = field ({@link ProxyTextTag}.FIELD_*), c = int value, f0..f3 = float values, o0 = text. */
	public static final int TEXT_TAG_SET = 160;

	// Components. a = proxy.
	public static final int COMPONENT_REMOVE = 161;
	/** f0 = height. */
	public static final int COMPONENT_SET_HEIGHT = 162;

	/** a = unit, b = team color. */
	public static final int UNIT_TEAM_COLOR = 163;

	/** a = item. */
	public static final int ITEM_REMOVE = 164;

	private RenderCallCodes() {
	}
}
