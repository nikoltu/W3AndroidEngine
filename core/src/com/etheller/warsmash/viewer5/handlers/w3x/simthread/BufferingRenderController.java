package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.badlogic.gdx.graphics.Color;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.TextTag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackInstant;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitAttackSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightning;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderController;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.TextTagConfigType;

/**
 * The simulation's render controller while the simulation runs apart from the render thread: nothing is drawn here,
 * every call becomes a {@link RenderEvent} (in the order it was made) for the {@link RenderEventPlayer} to apply to the
 * real controller. Simulation objects never travel in an event, only handles and copies of the plain values the render
 * side needs. What the simulation needs back at once is made here: the units, items and destructables by the
 * {@link WorldFactory} (the simulation's own objects, without render peers), and stand-ins for components, lightnings
 * and text tags that carry an id the player maps to the real ones.
 * <p>
 * Simulation thread only.
 */
public final class BufferingRenderController implements SimulationRenderController {
	private static final float SCRIPT_TEXT_TAG_HEIGHT = 0.024f;
	private static final float SCRIPT_TEXT_TAG_LIFETIME = 100f;
	private static final float UNIT_TEXT_TAG_LIFETIME = 3f;

	/** Makes the simulation's side of a unit, item or destructable: the object the simulation uses, no render peer. */
	public interface WorldFactory {
		CUnit createUnit(CSimulation simulation, War3ID typeId, int playerIndex, float x, float y, float facing);

		CItem createItem(CSimulation simulation, War3ID typeId, float x, float y);

		CDestructable createDestructable(War3ID typeId, float x, float y, float facing, float scale, int variation);

		CDestructable createDestructableZ(War3ID typeId, float x, float y, float z, float facing, float scale,
				int variation);

		/**
		 * What the render side builds the peer of the object just made from (see {@link WorldPeerData}); asked for
		 * right after the call that made it, and sent with the creation event. Null when the render side needs nothing.
		 */
		default WorldPeerData.Unit unitPeerData(final CUnit unit) {
			return null;
		}

		default WorldPeerData.Item itemPeerData(final CItem item) {
			return null;
		}

		default WorldPeerData.Destructable destructablePeerData(final CDestructable destructable) {
			return null;
		}

		/** Colour of the player, which the render side cannot look up; -1 when the factory does not know. */
		default int playerColor(final int playerIndex) {
			return -1;
		}

		/** Values the art of a launched projectile needs from its source; null for none. */
		default WorldPeerData.ProjectileLaunch projectileLaunch(final CProjectile projectile, final int projectileId,
				final CUnit source, final CUnitAttackSettings settings) {
			return null;
		}

		/** Values the effect of an instant attack needs from its source and target; null for none. */
		default WorldPeerData.InstantAttack instantAttack(final CUnit source, final CUnitAttackInstant attack,
				final CWidget target) {
			return null;
		}

		/** Values the peer of a unit that changes type needs, copied from the simulation; null for none. */
		default WorldPeerData.TypeChange typeChangeData(final CUnit unit) {
			return null;
		}
	}

	/** The values of a unit's attack settings that the missile art reads, copied when the missile is launched. */
	public static final class AttackArt {
		public final String art;
		public final float arc;
		public final boolean homing;
		public final int speed;
		public final Float z;
		public final Float impactZ;
		public final Float deathTime;
		/** Values from the source of the missile; null when the factory gave none. */
		public WorldPeerData.ProjectileLaunch launch;

		AttackArt(final CUnitAttackSettings settings) {
			this.art = settings.getProjectileArt();
			this.arc = settings.getProjectileArc();
			this.homing = settings.isProjectileHomingEnabled();
			this.speed = settings.getProjectileSpeed();
			this.z = settings.getZ();
			this.impactZ = settings.getImpactZ();
			this.deathTime = settings.getArtDeathTime();
		}
	}

	/** Copy of the arguments of the rectangle form of terrain adjustment. */
	public static final class TerrainAdjustment {
		public final int[] rect;
		public final float[] modBuffer;

		TerrainAdjustment(final int[] rect, final float[] modBuffer) {
			this.rect = rect.clone();
			this.modBuffer = modBuffer.clone();
		}
	}

	/** Config type and text of a floating text on a unit. */
	public static final class TextTagMessage {
		public final TextTagConfigType type;
		public final String message;

		TextTagMessage(final TextTagConfigType type, final String message) {
			this.type = type;
			this.message = message;
		}
	}

	private final RenderEventSink sink;
	private final WorldFactory factory;
	private final WeakHashMap<CProjectile, Integer> projectileIds = new WeakHashMap<>();
	private final Map<Integer, WeakReference<CProjectile>> projectilesById = new ConcurrentHashMap<>();
	private int nextProxyId = 1;
	private int nextProjectileId = 1;
	private RenderEvent pending;

	public BufferingRenderController(final RenderEventSink sink, final WorldFactory factory) {
		this.sink = sink;
		this.factory = factory;
	}

	/**
	 * The id events use for a projectile (made on first use). Projectiles have no handle of their own; the id is what
	 * {@link RenderEventPlayer.Resolver#projectile} is asked for.
	 */
	public int projectileId(final CProjectile projectile) {
		Integer id = this.projectileIds.get(projectile);
		if (id == null) {
			id = this.nextProjectileId++;
			this.projectileIds.put(projectile, id);
			this.projectilesById.put(id, new WeakReference<>(projectile));
			if ((id & 1023) == 0) {
				for (final Iterator<WeakReference<CProjectile>> it = this.projectilesById.values().iterator(); it
						.hasNext();) {
					if (it.next().get() == null) {
						it.remove();
					}
				}
			}
		}
		return id;
	}

	/** Any thread. The projectile an event names by the id {@link #projectileId} gave it, null once it is gone. */
	public CProjectile projectile(final int id) {
		final WeakReference<CProjectile> reference = this.projectilesById.get(id);
		return reference == null ? null : reference.get();
	}

	RenderEvent begin(final int code) {
		this.pending = this.sink.begin(code);
		return this.pending;
	}

	void emitPending() {
		this.sink.emit(this.pending);
		this.pending = null;
	}

	private void emit() {
		emitPending();
	}

	private static int handle(final CWidget widget) {
		return widget == null ? 0 : widget.getHandleId();
	}

	private static int raw(final War3ID id) {
		return id == null ? RenderEventPlayer.NO_ID : id.getValue();
	}

	@Override
	public void spawnProjectileArt(final CProjectile projectile, final CUnit source,
			final CUnitAttackSettings settings, final int bounceIndex) {
		final RenderEvent event = begin(RenderCallCodes.PROJECTILE_ART_ATTACK);
		final int id = projectileId(projectile);
		event.a = id;
		event.b = handle(source);
		event.c = bounceIndex;
		event.f0 = projectile.getX();
		event.f1 = projectile.getY();
		final AttackArt art = new AttackArt(settings);
		art.launch = this.factory.projectileLaunch(projectile, id, source, settings);
		event.o0 = art;
		emit();
	}

	@Override
	public void spawnProjectileArt(final CProjectile projectile, final CUnit source, final War3ID spellAlias) {
		final RenderEvent event = begin(RenderCallCodes.PROJECTILE_ART_SPELL);
		final int id = projectileId(projectile);
		event.a = id;
		event.b = handle(source);
		event.c = raw(spellAlias);
		event.f0 = projectile.getX();
		event.f1 = projectile.getY();
		event.o0 = this.factory.projectileLaunch(projectile, id, source, null);
		emit();
	}

	private SimulationRenderComponentLightning lightning(final int code, final War3ID lightningId, final CUnit source,
			final CUnit target, final Float duration, final int index) {
		final int id = this.nextProxyId++;
		final RenderEvent event = begin(code);
		event.a = id;
		event.b = raw(lightningId);
		event.c = handle(source);
		event.d = handle(target);
		event.f0 = duration == null ? 0 : duration;
		event.f1 = index;
		event.f2 = duration == null ? 1 : 0;
		emit();
		return new ProxyLightning(this, id);
	}

	@Override
	public SimulationRenderComponentLightning createLightning(final CSimulation simulation, final War3ID lightningId,
			final CUnit source, final CUnit target) {
		return lightning(RenderCallCodes.LIGHTNING_PLAIN, lightningId, source, target, null, 0);
	}

	@Override
	public SimulationRenderComponentLightning createLightning(final CSimulation simulation, final War3ID lightningId,
			final CUnit source, final CUnit target, final Float duration) {
		return lightning(RenderCallCodes.LIGHTNING_PLAIN_DURATION, lightningId, source, target, duration, 0);
	}

	@Override
	public SimulationRenderComponentLightning createAbilityLightning(final CSimulation simulation,
			final War3ID lightningId, final CUnit source, final CUnit target, final int index) {
		return lightning(RenderCallCodes.LIGHTNING_ABILITY, lightningId, source, target, null, index);
	}

	@Override
	public SimulationRenderComponentLightning createAbilityLightning(final CSimulation simulation,
			final War3ID lightningId, final CUnit source, final CUnit target, final int index, final Float duration) {
		return lightning(RenderCallCodes.LIGHTNING_ABILITY_DURATION, lightningId, source, target, duration, index);
	}

	@Override
	public SimulationRenderComponent createStaticUberSplat(final float x, final float y, final War3ID id) {
		final int proxy = this.nextProxyId++;
		final RenderEvent event = begin(RenderCallCodes.UBERSPLAT_STATIC);
		event.a = proxy;
		event.b = raw(id);
		event.f0 = x;
		event.f1 = y;
		emit();
		return new ProxyRenderComponent(this, proxy);
	}

	@Override
	public CUnit createUnit(final CSimulation simulation, final War3ID typeId, final int playerIndex, final float x,
			final float y, final float facing) {
		final CUnit unit = this.factory.createUnit(simulation, typeId, playerIndex, x, y, facing);
		final RenderEvent event = begin(RenderCallCodes.UNIT_CREATE);
		event.a = handle(unit);
		event.b = raw(typeId);
		event.c = playerIndex;
		event.f0 = x;
		event.f1 = y;
		event.f2 = facing;
		event.o0 = this.factory.unitPeerData(unit);
		emit();
		return unit;
	}

	@Override
	public CItem createItem(final CSimulation simulation, final War3ID typeId, final float x, final float y) {
		final CItem item = this.factory.createItem(simulation, typeId, x, y);
		final RenderEvent event = begin(RenderCallCodes.ITEM_CREATE);
		event.a = handle(item);
		event.b = raw(typeId);
		event.f0 = x;
		event.f1 = y;
		event.o0 = this.factory.itemPeerData(item);
		emit();
		return item;
	}

	@Override
	public void updateItemModel(final CItem item) {
		final RenderEvent event = begin(RenderCallCodes.ITEM_UPDATE_MODEL);
		event.a = handle(item);
		event.o0 = item;
		emit();
	}

	@Override
	public void removeItem(final CItem item) {
		final RenderEvent event = begin(RenderCallCodes.ITEM_REMOVE);
		event.a = handle(item);
		event.o0 = item;
		emit();
	}

	@Override
	public CDestructable createDestructable(final War3ID typeId, final float x, final float y, final float facing,
			final float scale, final int variation) {
		final CDestructable destructable = this.factory.createDestructable(typeId, x, y, facing, scale, variation);
		final RenderEvent event = begin(RenderCallCodes.DESTRUCTABLE_CREATE);
		event.a = handle(destructable);
		event.b = raw(typeId);
		event.c = variation;
		event.f0 = x;
		event.f1 = y;
		event.f2 = facing;
		event.f3 = scale;
		event.o0 = this.factory.destructablePeerData(destructable);
		emit();
		return destructable;
	}

	@Override
	public CDestructable createDestructableZ(final War3ID typeId, final float x, final float y, final float z,
			final float facing, final float scale, final int variation) {
		final CDestructable destructable = this.factory.createDestructableZ(typeId, x, y, z, facing, scale, variation);
		final RenderEvent event = begin(RenderCallCodes.DESTRUCTABLE_CREATE_Z);
		event.a = handle(destructable);
		event.b = raw(typeId);
		event.c = variation;
		event.d = Float.floatToRawIntBits(scale);
		event.f0 = x;
		event.f1 = y;
		event.f2 = z;
		event.f3 = facing;
		event.o0 = this.factory.destructablePeerData(destructable);
		emit();
		return destructable;
	}

	@Override
	public void createInstantAttackEffect(final CSimulation cSimulation, final CUnit source,
			final CUnitAttackInstant attack, final CWidget target) {
		final RenderEvent event = begin(RenderCallCodes.INSTANT_ATTACK_EFFECT);
		event.a = handle(source);
		event.b = handle(target);
		final WorldPeerData.InstantAttack values = this.factory.instantAttack(source, attack, target);
		event.o0 = values != null ? values : attack.getProjectileArt();
		emit();
	}

	@Override
	public void spawnDamageSound(final CWidget damagedDestructable, final String weaponSound, final String armorType) {
		final RenderEvent event = begin(RenderCallCodes.DAMAGE_SOUND);
		event.a = handle(damagedDestructable);
		event.o0 = new String[] { weaponSound, armorType };
		emit();
	}

	@Override
	public void spawnUnitConstructionSound(final CUnit constructingUnit, final CUnit constructedStructure) {
		final RenderEvent event = begin(RenderCallCodes.CONSTRUCTION_SOUND);
		event.a = handle(constructingUnit);
		event.b = handle(constructedStructure);
		emit();
	}

	@Override
	public void removeUnit(final CUnit unit) {
		final RenderEvent event = begin(RenderCallCodes.UNIT_REMOVE);
		event.a = handle(unit);
		event.o0 = unit;
		emit();
	}

	@Override
	public void removeDestructable(final CDestructable dest) {
		final RenderEvent event = begin(RenderCallCodes.DESTRUCTABLE_REMOVE);
		event.a = handle(dest);
		event.o0 = dest;
		emit();
	}

	@Override
	public void spawnUnitConstructionFinishSound(final CUnit constructedStructure) {
		begin(RenderCallCodes.CONSTRUCTION_FINISH_SOUND).a = handle(constructedStructure);
		emit();
	}

	@Override
	public void spawnUnitUpgradeFinishSound(final CUnit constructedStructure) {
		begin(RenderCallCodes.UPGRADE_FINISH_SOUND).a = handle(constructedStructure);
		emit();
	}

	@Override
	public void spawnDeathExplodeEffect(final CUnit cUnit, final War3ID explodesOnDeathBuffId) {
		final RenderEvent event = begin(RenderCallCodes.DEATH_EXPLODE_EFFECT);
		event.a = handle(cUnit);
		event.b = raw(explodesOnDeathBuffId);
		event.c = explodesOnDeathBuffId == null ? 1 : 0;
		emit();
	}

	@Override
	public void spawnGainLevelEffect(final CUnit cUnit) {
		begin(RenderCallCodes.GAIN_LEVEL_EFFECT).a = handle(cUnit);
		emit();
	}

	@Override
	public void spawnUnitReadySound(final CUnit trainedUnit) {
		begin(RenderCallCodes.UNIT_READY_SOUND).a = handle(trainedUnit);
		emit();
	}

	@Override
	public void unitRepositioned(final CUnit cUnit) {
		begin(RenderCallCodes.UNIT_REPOSITIONED).a = handle(cUnit);
		emit();
	}

	private TextTag textTag(final float lifetime, final boolean permanent) {
		return new ProxyTextTag(this, this.nextProxyId++, lifetime, SCRIPT_TEXT_TAG_HEIGHT, permanent);
	}

	@Override
	public TextTag spawnTextTag(final CUnit unit, final TextTagConfigType configType, final int displayAmount) {
		final ProxyTextTag tag = (ProxyTextTag) textTag(UNIT_TEXT_TAG_LIFETIME, false);
		final RenderEvent event = begin(RenderCallCodes.TEXT_TAG_SPAWN_AMOUNT);
		event.a = tag.getProxyId();
		event.b = handle(unit);
		event.d = displayAmount;
		event.o0 = configType;
		emit();
		return tag;
	}

	@Override
	public TextTag spawnTextTag(final CUnit unit, final TextTagConfigType configType, final String message) {
		final ProxyTextTag tag = (ProxyTextTag) textTag(UNIT_TEXT_TAG_LIFETIME, false);
		final RenderEvent event = begin(RenderCallCodes.TEXT_TAG_SPAWN_MESSAGE);
		event.a = tag.getProxyId();
		event.b = handle(unit);
		event.o0 = new TextTagMessage(configType, message);
		emit();
		return tag;
	}

	@Override
	public TextTag createTextTag() {
		final ProxyTextTag tag = (ProxyTextTag) textTag(SCRIPT_TEXT_TAG_LIFETIME, true);
		begin(RenderCallCodes.TEXT_TAG_CREATE).a = tag.getProxyId();
		emit();
		return tag;
	}

	@Override
	public void destroyTextTag(final TextTag textTag) {
		begin(RenderCallCodes.TEXT_TAG_DESTROY).a = ((ProxyTextTag) textTag).getProxyId();
		emit();
	}

	@Override
	public void spawnEffectOnUnit(final CUnit unit, final String effectPath) {
		final RenderEvent event = begin(RenderCallCodes.EFFECT_ON_UNIT);
		event.a = handle(unit);
		event.o0 = effectPath;
		emit();
	}

	@Override
	public void spawnTemporarySpellEffectOnUnit(final CUnit unit, final War3ID alias, final CEffectType effectType) {
		final RenderEvent event = begin(RenderCallCodes.TEMPORARY_EFFECT_ON_UNIT);
		event.a = handle(unit);
		event.b = raw(alias);
		event.o0 = effectType;
		emit();
	}

	@Override
	public SimulationRenderComponentModel spawnPersistentSpellEffectOnUnit(final CUnit unit, final War3ID alias,
			final CEffectType effectType) {
		final int proxy = this.nextProxyId++;
		final RenderEvent event = begin(RenderCallCodes.PERSISTENT_EFFECT_ON_UNIT);
		event.a = proxy;
		event.b = handle(unit);
		event.c = raw(alias);
		event.o0 = effectType;
		emit();
		return new ProxyRenderComponent(this, proxy);
	}

	@Override
	public SimulationRenderComponentModel spawnPersistentSpellEffectOnUnit(final CUnit unit, final War3ID alias,
			final CEffectType effectType, final int index) {
		final int proxy = this.nextProxyId++;
		final RenderEvent event = begin(RenderCallCodes.PERSISTENT_EFFECT_ON_UNIT_INDEXED);
		event.a = proxy;
		event.b = handle(unit);
		event.c = raw(alias);
		event.d = index;
		event.o0 = effectType;
		emit();
		return new ProxyRenderComponent(this, proxy);
	}

	@Override
	public SimulationRenderComponentModel spawnSpellEffectOnPoint(final float x, final float y, final float facing,
			final War3ID alias, final CEffectType effectType, final int index) {
		final int proxy = this.nextProxyId++;
		final RenderEvent event = begin(RenderCallCodes.EFFECT_ON_POINT);
		event.a = proxy;
		event.c = raw(alias);
		event.d = index;
		event.f0 = x;
		event.f1 = y;
		event.f2 = facing;
		event.o0 = effectType;
		emit();
		return new ProxyRenderComponent(this, proxy);
	}

	@Override
	public void spawnTemporarySpellEffectOnPoint(final float x, final float y, final float facing, final War3ID alias,
			final CEffectType effectType, final int index) {
		final RenderEvent event = begin(RenderCallCodes.TEMPORARY_EFFECT_ON_POINT);
		event.c = raw(alias);
		event.d = index;
		event.f0 = x;
		event.f1 = y;
		event.f2 = facing;
		event.o0 = effectType;
		emit();
	}

	@Override
	public void spawnUIUnitGetItemSound(final CUnit cUnit, final CItem item) {
		final RenderEvent event = begin(RenderCallCodes.UI_GET_ITEM_SOUND);
		event.a = handle(cUnit);
		event.b = handle(item);
		event.o0 = item;
		emit();
	}

	@Override
	public void spawnUIUnitDropItemSound(final CUnit cUnit, final CItem item) {
		final RenderEvent event = begin(RenderCallCodes.UI_DROP_ITEM_SOUND);
		event.a = handle(cUnit);
		event.b = handle(item);
		event.o0 = item;
		emit();
	}

	private SimulationRenderComponent abilitySound(final int code, final CUnit caster, final War3ID alias) {
		final int proxy = this.nextProxyId++;
		final RenderEvent event = begin(code);
		event.a = proxy;
		event.b = handle(caster);
		event.c = raw(alias);
		emit();
		return new ProxyRenderComponent(this, proxy);
	}

	@Override
	public SimulationRenderComponent spawnAbilitySoundEffect(final CUnit caster, final War3ID alias) {
		return abilitySound(RenderCallCodes.ABILITY_SOUND, caster, alias);
	}

	@Override
	public SimulationRenderComponent loopAbilitySoundEffect(final CUnit caster, final War3ID alias) {
		return abilitySound(RenderCallCodes.ABILITY_SOUND_LOOP, caster, alias);
	}

	@Override
	public void stopAbilitySoundEffect(final CUnit caster, final War3ID alias) {
		final RenderEvent event = begin(RenderCallCodes.ABILITY_SOUND_STOP);
		event.b = handle(caster);
		event.c = raw(alias);
		emit();
	}

	@Override
	public SimulationRenderComponent spawnAbilitySoundEffect(final float x, final float y, final War3ID alias,
			final boolean looping) {
		final int proxy = this.nextProxyId++;
		final RenderEvent event = begin(RenderCallCodes.ABILITY_SOUND_AT_POINT);
		event.a = proxy;
		event.c = raw(alias);
		event.d = looping ? 1 : 0;
		event.f0 = x;
		event.f1 = y;
		emit();
		return new ProxyRenderComponent(this, proxy);
	}

	@Override
	public void unitPreferredSelectionReplacement(final CUnit unit, final CUnit newUnit) {
		final RenderEvent event = begin(RenderCallCodes.SELECTION_REPLACEMENT);
		event.a = handle(unit);
		event.b = handle(newUnit);
		emit();
	}

	@Override
	public void heroRevived(final CUnit trainedUnit, final boolean doEyecandy) {
		final RenderEvent event = begin(RenderCallCodes.HERO_REVIVED);
		event.a = handle(trainedUnit);
		event.c = doEyecandy ? 1 : 0;
		emit();
	}

	@Override
	public void heroDeathEvent(final CUnit cUnit) {
		begin(RenderCallCodes.HERO_DEATH).a = handle(cUnit);
		emit();
	}

	@Override
	public SimulationRenderComponentModel createSpellEffectOverDestructable(final CUnit source,
			final CDestructable target, final War3ID alias, final float artAttachmentHeight) {
		final int proxy = this.nextProxyId++;
		final RenderEvent event = begin(RenderCallCodes.EFFECT_OVER_DESTRUCTABLE);
		event.a = proxy;
		event.b = handle(source);
		event.c = handle(target);
		event.d = raw(alias);
		event.f0 = artAttachmentHeight;
		emit();
		return new ProxyRenderComponent(this, proxy);
	}

	@Override
	public void unitUpgradingEvent(final CUnit unit, final War3ID upgradeIdType) {
		final RenderEvent event = begin(RenderCallCodes.UPGRADING);
		event.a = handle(unit);
		event.b = raw(upgradeIdType);
		emit();
	}

	@Override
	public void unitCancelUpgradingEvent(final CUnit unit, final War3ID upgradeIdType) {
		final RenderEvent event = begin(RenderCallCodes.UPGRADE_CANCELLED);
		event.a = handle(unit);
		event.b = raw(upgradeIdType);
		emit();
	}

	@Override
	public void blightChanged(final float[] cornerPositions, final boolean blighted) {
		final RenderEvent event = begin(RenderCallCodes.BLIGHT);
		event.o0 = cornerPositions;
		event.c = blighted ? 1 : 0;
		emit();
	}

	@Override
	public void unitUpdatedType(final CUnit unit, final War3ID typeId, final boolean updatePortrait) {
		final RenderEvent event = begin(RenderCallCodes.UNIT_UPDATED_TYPE);
		event.a = handle(unit);
		event.b = raw(typeId);
		event.c = updatePortrait ? 1 : 0;
		event.o0 = this.factory.typeChangeData(unit);
		emit();
	}

	@Override
	public void changeUnitColor(final CUnit unit, final int playerIndex) {
		final RenderEvent event = begin(RenderCallCodes.UNIT_COLOR);
		event.a = handle(unit);
		event.b = playerIndex;
		event.c = this.factory.playerColor(playerIndex);
		emit();
	}

	@Override
	public void setUnitTeamColor(final CUnit unit, final int color) {
		final RenderEvent event = begin(RenderCallCodes.UNIT_TEAM_COLOR);
		event.a = handle(unit);
		event.b = color;
		emit();
	}

	@Override
	public void changeUnitPlayerColor(final CUnit unit, final int previousColor, final int newColor) {
		final RenderEvent event = begin(RenderCallCodes.UNIT_PLAYER_COLOR);
		event.a = handle(unit);
		event.b = previousColor;
		event.c = newColor;
		emit();
	}

	@Override
	public void changeUnitVertexColor(final CUnit unit, final Color color) {
		final RenderEvent event = begin(RenderCallCodes.UNIT_VERTEX_COLOR);
		event.a = handle(unit);
		event.f0 = color.r;
		event.f1 = color.g;
		event.f2 = color.b;
		event.f3 = color.a;
		emit();
	}

	@Override
	public void changeUnitVertexColor(final CUnit unit, final float r, final float g, final float b) {
		final RenderEvent event = begin(RenderCallCodes.UNIT_VERTEX_COLOR_RGB);
		event.a = handle(unit);
		event.f0 = r;
		event.f1 = g;
		event.f2 = b;
		emit();
	}

	@Override
	public void changeUnitVertexColor(final CUnit unit, final float r, final float g, final float b, final float a) {
		final RenderEvent event = begin(RenderCallCodes.UNIT_VERTEX_COLOR_RGBA);
		event.a = handle(unit);
		event.f0 = r;
		event.f1 = g;
		event.f2 = b;
		event.f3 = a;
		emit();
	}

	@Override
	public void adjustTerrain(final float x, final float y, final float i) {
		final RenderEvent event = begin(RenderCallCodes.ADJUST_TERRAIN);
		event.f0 = x;
		event.f1 = y;
		event.f2 = i;
		emit();
	}

	@Override
	public void adjustTerrain(final int[] rect, final float[] modBuffer) {
		begin(RenderCallCodes.ADJUST_TERRAIN_RECT).o0 = new TerrainAdjustment(rect, modBuffer);
		emit();
	}

	@Override
	public void setTerrainDeformation(final int column, final int row, final int width, final int height,
			final float[] offsets) {
		final RenderEvent event = begin(RenderCallCodes.TERRAIN_DEFORMATION);
		event.a = column;
		event.b = row;
		event.c = width;
		event.d = height;
		event.o0 = offsets.clone();
		emit();
	}

	@Override
	public void setTerrainTypes(final int[] cells) {
		begin(RenderCallCodes.TERRAIN_TYPES).o0 = cells.clone();
		emit();
	}

	@Override
	public void changeUnitScale(final CUnit unit, final float scale, final boolean multiplier) {
		final RenderEvent event = begin(RenderCallCodes.UNIT_SCALE);
		event.a = handle(unit);
		event.f0 = scale;
		event.c = multiplier ? 1 : 0;
		emit();
	}
}
