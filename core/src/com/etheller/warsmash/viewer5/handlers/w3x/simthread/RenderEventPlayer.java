package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.EnumSet;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.IntMap;
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
 * Render thread: applies the events {@link BufferingRenderController} recorded, in order, to the real controller. The
 * real controller still takes the simulation's objects as keys for its peers, so the {@link Resolver} hands it the
 * object of a handle (used as an identity only; the player reads no field of it). The components, lightnings and text
 * tags the real controller returns are kept under the ids the simulation side gave their stand-ins, and later events
 * for those ids reach them.
 */
public final class RenderEventPlayer {
	/**
	 * The simulation objects of the handles events carry; a handle of 0 or an unknown one gives null. A handle is
	 * given out again once its object is gone, so it names the object whose creation event was played last for it
	 * (or, before that event, the earliest object made with it whose creation event is still to come).
	 */
	public interface Resolver {
		CUnit unit(int handle);

		CItem item(int handle);

		CDestructable destructable(int handle);

		/** Unit, item or destructable. */
		CWidget widget(int handle);

		/** By the id {@link BufferingRenderController#projectileId} gave. */
		CProjectile projectile(int id);

		/** The unit's remove event was played: its handle names the next unit made with it, not this one. */
		default void unitRemoved(final int handle, final CUnit unit) {
		}

		default void itemRemoved(final int handle, final CItem item) {
		}

		default void destructableRemoved(final int handle, final CDestructable destructable) {
		}
	}

	/**
	 * Where created world objects go when the simulation side already made them: the render side makes only the peer.
	 * Without one, the player asks the real controller to create the object, which is right while that controller also
	 * makes the simulation's object (the unthreaded way).
	 */
	public interface WorldPeers {
		/** @param data what the simulation side copied for the peer (null when it had nothing) */
		void unitCreated(int handle, War3ID typeId, int playerIndex, float x, float y, float facing,
				WorldPeerData.Unit data);

		void itemCreated(int handle, War3ID typeId, float x, float y, WorldPeerData.Item data);

		/** z is NaN when the controller is to find the height itself. */
		void destructableCreated(int handle, War3ID typeId, float x, float y, float z, float facing, float scale,
				int variation, WorldPeerData.Destructable data);
	}

	/**
	 * Calls of a controller that reads no simulation state: the values come with the event, copied by the simulation
	 * side. A target that has them gets these calls where the event carries the values.
	 */
	public interface ValueTarget {
		void changeUnitColorTo(CUnit unit, int color);

		void unitUpdatedType(CUnit unit, War3ID typeId, boolean updatePortrait, WorldPeerData.TypeChange change);

		void spawnProjectileArt(CUnit source, CUnitAttackSettings settings, int bounceIndex,
				WorldPeerData.ProjectileLaunch launch);

		void spawnProjectileArt(CUnit source, War3ID spellAlias, WorldPeerData.ProjectileLaunch launch);

		void createInstantAttackEffect(CUnit source, WorldPeerData.InstantAttack attack, CWidget target);
	}

	private final SimulationRenderController target;
	private final Resolver resolver;
	private final CSimulation simulation;
	private final WorldPeers peers;
	private final IntMap<Object> proxied = new IntMap<>();
	private BufferedAnimationListener.Player animations;

	/**
	 * @param simulation passed on to the controller calls that take it
	 * @param peers      null to have the controller create world objects itself
	 */
	public RenderEventPlayer(final SimulationRenderController target, final Resolver resolver,
			final CSimulation simulation, final WorldPeers peers) {
		this.target = target;
		this.resolver = resolver;
		this.simulation = simulation;
		this.peers = peers;
	}

	/** Where the animation listener events go; without one they are not accepted. */
	public void setAnimationPlayer(final BufferedAnimationListener.Player animations) {
		this.animations = animations;
	}

	/** Applies the events of the frame it has not applied yet. */
	public void playFrame(final SimFrame frame) {
		final int count = frame.eventCount();
		for (int i = frame.eventsApplied; i < count; i++) {
			play(frame.event(i));
			frame.eventsApplied = i + 1;
		}
	}

	/** Stands for a null id in an event; a War3ID of value 0 is a real id (a buff or ability with a blank code). */
	public static final int NO_ID = Integer.MIN_VALUE;

	private static War3ID id(final int raw) {
		return raw == NO_ID ? null : new War3ID(raw);
	}

	private CUnit unit(final int handle) {
		return handle == 0 ? null : this.resolver.unit(handle);
	}

	private CWidget widget(final int handle) {
		return handle == 0 ? null : this.resolver.widget(handle);
	}

	private CDestructable destructable(final int handle) {
		return handle == 0 ? null : this.resolver.destructable(handle);
	}

	/** Applies one event. Calls with codes this player does not know throw, so a missing case shows at once. */
	public void play(final RenderEvent e) {
		if ((this.animations != null) && BufferedAnimationListener.isAnimationEvent(e.code)) {
			this.animations.apply(e);
			return;
		}
		switch (e.code) {
		case RenderCallCodes.PROJECTILE_ART_ATTACK: {
			final BufferingRenderController.AttackArt art = (BufferingRenderController.AttackArt) e.o0;
			// The simulation can be steps ahead and the missile over (and gone) before its art plays: what the art
			// needs of the missile came with the event.
			final boolean valued = (art.launch != null) && (this.target instanceof ValueTarget);
			final CProjectile projectile = valued ? null : this.resolver.projectile(e.a);
			if (!valued && (projectile == null)) {
				break;
			}
			final CUnitAttackSettings settings = new CUnitAttackSettings(art.arc, art.art, art.homing, art.speed);
			settings.setZ(art.z);
			settings.setImpactZ(art.impactZ);
			settings.setArtDeathTime(art.deathTime);
			if (valued) {
				((ValueTarget) this.target).spawnProjectileArt(unit(e.b), settings, e.c, art.launch);
			}
			else {
				this.target.spawnProjectileArt(projectile, unit(e.b), settings, e.c);
			}
			break;
		}
		case RenderCallCodes.PROJECTILE_ART_SPELL: {
			final boolean valued = (e.o0 != null) && (this.target instanceof ValueTarget);
			if (valued) {
				((ValueTarget) this.target).spawnProjectileArt(unit(e.b), id(e.c),
						(WorldPeerData.ProjectileLaunch) e.o0);
				break;
			}
			final CProjectile projectile = this.resolver.projectile(e.a);
			if (projectile == null) {
				break;
			}
			this.target.spawnProjectileArt(projectile, unit(e.b), id(e.c));
			break;
		}
		case RenderCallCodes.LIGHTNING_PLAIN:
			this.proxied.put(e.a, this.target.createLightning(this.simulation, id(e.b), unit(e.c), unit(e.d)));
			break;
		case RenderCallCodes.LIGHTNING_PLAIN_DURATION:
			this.proxied.put(e.a, this.target.createLightning(this.simulation, id(e.b), unit(e.c), unit(e.d),
					duration(e)));
			break;
		case RenderCallCodes.LIGHTNING_ABILITY:
			this.proxied.put(e.a, this.target.createAbilityLightning(this.simulation, id(e.b), unit(e.c), unit(e.d),
					(int) e.f1));
			break;
		case RenderCallCodes.LIGHTNING_ABILITY_DURATION:
			this.proxied.put(e.a, this.target.createAbilityLightning(this.simulation, id(e.b), unit(e.c), unit(e.d),
					(int) e.f1, duration(e)));
			break;
		case RenderCallCodes.LIGHTNING_SET_COLOR: {
			final SimulationRenderComponentLightning lightning = (SimulationRenderComponentLightning) this.proxied
					.get(e.a);
			if (lightning != null) {
				lightning.setColor(e.f0, e.f1, e.f2, e.f3);
			}
			break;
		}
		case RenderCallCodes.LIGHTNING_REMOVE:
		case RenderCallCodes.COMPONENT_REMOVE: {
			final SimulationRenderComponent component = (SimulationRenderComponent) this.proxied.get(e.a);
			if (component != null) {
				component.remove();
			}
			break;
		}
		case RenderCallCodes.UBERSPLAT_STATIC:
			this.proxied.put(e.a, this.target.createStaticUberSplat(e.f0, e.f1, id(e.b)));
			break;
		case RenderCallCodes.UNIT_CREATE:
			if (this.peers == null) {
				this.target.createUnit(this.simulation, id(e.b), e.c, e.f0, e.f1, e.f2);
			}
			else {
				this.peers.unitCreated(e.a, id(e.b), e.c, e.f0, e.f1, e.f2, (WorldPeerData.Unit) e.o0);
			}
			break;
		case RenderCallCodes.ITEM_CREATE:
			if (this.peers == null) {
				this.target.createItem(this.simulation, id(e.b), e.f0, e.f1);
			}
			else {
				this.peers.itemCreated(e.a, id(e.b), e.f0, e.f1, (WorldPeerData.Item) e.o0);
			}
			break;
		case RenderCallCodes.ITEM_REMOVE: {
			// the event names its object: the handle may name a later item by now
			final CItem item = (CItem) e.o0;
			this.target.removeItem(item);
			this.resolver.itemRemoved(e.a, item);
			break;
		}
		case RenderCallCodes.ITEM_UPDATE_MODEL:
			this.target.updateItemModel((CItem) e.o0);
			break;
		case RenderCallCodes.DESTRUCTABLE_CREATE:
			if (this.peers == null) {
				this.target.createDestructable(id(e.b), e.f0, e.f1, e.f2, e.f3, e.c);
			}
			else {
				this.peers.destructableCreated(e.a, id(e.b), e.f0, e.f1, Float.NaN, e.f2, e.f3, e.c,
						(WorldPeerData.Destructable) e.o0);
			}
			break;
		case RenderCallCodes.DESTRUCTABLE_CREATE_Z:
			if (this.peers == null) {
				this.target.createDestructableZ(id(e.b), e.f0, e.f1, e.f2, e.f3, Float.intBitsToFloat(e.d), e.c);
			}
			else {
				this.peers.destructableCreated(e.a, id(e.b), e.f0, e.f1, e.f2, e.f3, Float.intBitsToFloat(e.d), e.c,
						(WorldPeerData.Destructable) e.o0);
			}
			break;
		case RenderCallCodes.UNIT_REMOVE: {
			final CUnit unit = (CUnit) e.o0;
			this.target.removeUnit(unit);
			this.resolver.unitRemoved(e.a, unit);
			break;
		}
		case RenderCallCodes.DESTRUCTABLE_REMOVE: {
			final CDestructable destructable = (CDestructable) e.o0;
			this.target.removeDestructable(destructable);
			this.resolver.destructableRemoved(e.a, destructable);
			break;
		}
		case RenderCallCodes.INSTANT_ATTACK_EFFECT:
			if ((e.o0 instanceof WorldPeerData.InstantAttack) && (this.target instanceof ValueTarget)) {
				((ValueTarget) this.target).createInstantAttackEffect(unit(e.a), (WorldPeerData.InstantAttack) e.o0,
						widget(e.b));
				break;
			}
			this.target.createInstantAttackEffect(this.simulation, unit(e.a), new CUnitAttackInstant(0, 0, null, 0, 0,
					0, 0, 0, 0, 0, false, EnumSet.noneOf(
							com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType.class),
					null, null, null, e.o0 instanceof WorldPeerData.InstantAttack
							? ((WorldPeerData.InstantAttack) e.o0).missileArt : (String) e.o0),
					widget(e.b));
			break;
		case RenderCallCodes.DAMAGE_SOUND: {
			final String[] sounds = (String[]) e.o0;
			this.target.spawnDamageSound(widget(e.a), sounds[0], sounds[1]);
			break;
		}
		case RenderCallCodes.CONSTRUCTION_SOUND:
			this.target.spawnUnitConstructionSound(unit(e.a), unit(e.b));
			break;
		case RenderCallCodes.CONSTRUCTION_FINISH_SOUND:
			this.target.spawnUnitConstructionFinishSound(unit(e.a));
			break;
		case RenderCallCodes.UPGRADE_FINISH_SOUND:
			this.target.spawnUnitUpgradeFinishSound(unit(e.a));
			break;
		case RenderCallCodes.UNIT_READY_SOUND:
			this.target.spawnUnitReadySound(unit(e.a));
			break;
		case RenderCallCodes.UI_GET_ITEM_SOUND:
			this.target.spawnUIUnitGetItemSound(unit(e.a), (CItem) e.o0);
			break;
		case RenderCallCodes.UI_DROP_ITEM_SOUND:
			this.target.spawnUIUnitDropItemSound(unit(e.a), (CItem) e.o0);
			break;
		case RenderCallCodes.DEATH_EXPLODE_EFFECT:
			this.target.spawnDeathExplodeEffect(unit(e.a), e.c == 1 ? null : new War3ID(e.b));
			break;
		case RenderCallCodes.GAIN_LEVEL_EFFECT:
			this.target.spawnGainLevelEffect(unit(e.a));
			break;
		case RenderCallCodes.UNIT_REPOSITIONED:
			this.target.unitRepositioned(unit(e.a));
			break;
		case RenderCallCodes.EFFECT_ON_UNIT:
			this.target.spawnEffectOnUnit(unit(e.a), (String) e.o0);
			break;
		case RenderCallCodes.TEMPORARY_EFFECT_ON_UNIT:
			this.target.spawnTemporarySpellEffectOnUnit(unit(e.a), id(e.b), (CEffectType) e.o0);
			break;
		case RenderCallCodes.PERSISTENT_EFFECT_ON_UNIT:
			this.proxied.put(e.a, this.target.spawnPersistentSpellEffectOnUnit(unit(e.b), id(e.c),
					(CEffectType) e.o0));
			break;
		case RenderCallCodes.PERSISTENT_EFFECT_ON_UNIT_INDEXED:
			this.proxied.put(e.a, this.target.spawnPersistentSpellEffectOnUnit(unit(e.b), id(e.c),
					(CEffectType) e.o0, e.d));
			break;
		case RenderCallCodes.EFFECT_ON_POINT:
			this.proxied.put(e.a, this.target.spawnSpellEffectOnPoint(e.f0, e.f1, e.f2, id(e.c), (CEffectType) e.o0,
					e.d));
			break;
		case RenderCallCodes.TEMPORARY_EFFECT_ON_POINT:
			this.target.spawnTemporarySpellEffectOnPoint(e.f0, e.f1, e.f2, id(e.c), (CEffectType) e.o0, e.d);
			break;
		case RenderCallCodes.EFFECT_OVER_DESTRUCTABLE:
			this.proxied.put(e.a, this.target.createSpellEffectOverDestructable(unit(e.b), destructable(e.c),
					id(e.d), e.f0));
			break;
		case RenderCallCodes.ABILITY_SOUND:
			this.proxied.put(e.a, this.target.spawnAbilitySoundEffect(unit(e.b), id(e.c)));
			break;
		case RenderCallCodes.ABILITY_SOUND_LOOP:
			this.proxied.put(e.a, this.target.loopAbilitySoundEffect(unit(e.b), id(e.c)));
			break;
		case RenderCallCodes.ABILITY_SOUND_STOP:
			this.target.stopAbilitySoundEffect(unit(e.b), id(e.c));
			break;
		case RenderCallCodes.ABILITY_SOUND_AT_POINT:
			this.proxied.put(e.a, this.target.spawnAbilitySoundEffect(e.f0, e.f1, id(e.c), e.d == 1));
			break;
		case RenderCallCodes.SELECTION_REPLACEMENT:
			this.target.unitPreferredSelectionReplacement(unit(e.a), unit(e.b));
			break;
		case RenderCallCodes.HERO_REVIVED:
			this.target.heroRevived(unit(e.a), e.c == 1);
			break;
		case RenderCallCodes.HERO_DEATH:
			this.target.heroDeathEvent(unit(e.a));
			break;
		case RenderCallCodes.UPGRADING:
			this.target.unitUpgradingEvent(unit(e.a), id(e.b));
			break;
		case RenderCallCodes.UPGRADE_CANCELLED:
			this.target.unitCancelUpgradingEvent(unit(e.a), id(e.b));
			break;
		case RenderCallCodes.UNIT_UPDATED_TYPE:
			if ((e.o0 != null) && (this.target instanceof ValueTarget)) {
				((ValueTarget) this.target).unitUpdatedType(unit(e.a), id(e.b), e.c == 1,
						(WorldPeerData.TypeChange) e.o0);
			}
			else {
				this.target.unitUpdatedType(unit(e.a), id(e.b), e.c == 1);
			}
			break;
		case RenderCallCodes.UNIT_COLOR:
			if ((e.c >= 0) && (this.target instanceof ValueTarget)) {
				((ValueTarget) this.target).changeUnitColorTo(unit(e.a), e.c);
			}
			else {
				this.target.changeUnitColor(unit(e.a), e.b);
			}
			break;
		case RenderCallCodes.UNIT_PLAYER_COLOR:
			this.target.changeUnitPlayerColor(unit(e.a), e.b, e.c);
			break;
		case RenderCallCodes.UNIT_TEAM_COLOR:
			this.target.setUnitTeamColor(unit(e.a), e.b);
			break;
		case RenderCallCodes.UNIT_VERTEX_COLOR:
			this.target.changeUnitVertexColor(unit(e.a), new Color(e.f0, e.f1, e.f2, e.f3));
			break;
		case RenderCallCodes.UNIT_VERTEX_COLOR_RGB:
			this.target.changeUnitVertexColor(unit(e.a), e.f0, e.f1, e.f2);
			break;
		case RenderCallCodes.UNIT_VERTEX_COLOR_RGBA:
			this.target.changeUnitVertexColor(unit(e.a), e.f0, e.f1, e.f2, e.f3);
			break;
		case RenderCallCodes.UNIT_SCALE:
			this.target.changeUnitScale(unit(e.a), e.f0, e.c == 1);
			break;
		case RenderCallCodes.ADJUST_TERRAIN:
			this.target.adjustTerrain(e.f0, e.f1, e.f2);
			break;
		case RenderCallCodes.ADJUST_TERRAIN_RECT: {
			final BufferingRenderController.TerrainAdjustment adjustment = (BufferingRenderController.TerrainAdjustment) e.o0;
			this.target.adjustTerrain(adjustment.rect, adjustment.modBuffer);
			break;
		}
		case RenderCallCodes.BLIGHT:
			this.target.blightChanged((float[]) e.o0, e.c == 1);
			break;
		case RenderCallCodes.TERRAIN_DEFORMATION:
			this.target.setTerrainDeformation(e.a, e.b, e.c, e.d, (float[]) e.o0);
			break;
		case RenderCallCodes.TERRAIN_TYPES:
			this.target.setTerrainTypes((int[]) e.o0);
			break;
		case RenderCallCodes.TEXT_TAG_SPAWN_AMOUNT:
			this.proxied.put(e.a, this.target.spawnTextTag(unit(e.b), (TextTagConfigType) e.o0, e.d));
			break;
		case RenderCallCodes.TEXT_TAG_SPAWN_MESSAGE: {
			final BufferingRenderController.TextTagMessage message = (BufferingRenderController.TextTagMessage) e.o0;
			this.proxied.put(e.a, this.target.spawnTextTag(unit(e.b), message.type, message.message));
			break;
		}
		case RenderCallCodes.TEXT_TAG_CREATE:
			this.proxied.put(e.a, this.target.createTextTag());
			break;
		case RenderCallCodes.TEXT_TAG_DESTROY: {
			final TextTag tag = (TextTag) this.proxied.remove(e.a);
			if (tag != null) {
				this.target.destroyTextTag(tag);
			}
			break;
		}
		case RenderCallCodes.TEXT_TAG_SET:
			setTextTag((TextTag) this.proxied.get(e.a), e);
			break;
		case RenderCallCodes.COMPONENT_SET_HEIGHT: {
			final Object component = this.proxied.get(e.a);
			if (component instanceof SimulationRenderComponentModel) {
				((SimulationRenderComponentModel) component).setHeight(e.f0);
			}
			break;
		}
		case RenderEvent.DISPLAY_TEXT:
		case RenderEvent.DIALOG:
		case RenderEvent.CAMERA_COMMAND:
		case RenderEvent.FRAME_COMMAND:
		case RenderEvent.MULTIBOARD_COMMAND:
		case RenderEvent.SCRIPT_RENDER_COMMAND:
			// a script's command for the local interface: the link made it to run here
			((Runnable) e.o0).run();
			break;
		default:
			throw new IllegalArgumentException("Unknown render event code " + e.code);
		}
	}

	private static Float duration(final RenderEvent e) {
		return e.f2 == 1 ? null : Float.valueOf(e.f0);
	}

	private static void setTextTag(final TextTag tag, final RenderEvent e) {
		if (tag == null) {
			return;
		}
		switch (e.b) {
		case ProxyTextTag.FIELD_TEXT:
			tag.setText((String) e.o0);
			break;
		case ProxyTextTag.FIELD_POSITION:
			tag.setPosition(e.f0, e.f1, e.f2);
			break;
		case ProxyTextTag.FIELD_COLOR:
			tag.setColor((int) e.f0, (int) e.f1, (int) e.f2, e.f3);
			break;
		case ProxyTextTag.FIELD_VELOCITY:
			tag.setVelocity(e.f0, e.f1);
			break;
		case ProxyTextTag.FIELD_VISIBLE:
			tag.setVisible(e.c == 1);
			break;
		case ProxyTextTag.FIELD_PERMANENT:
			tag.setPermanent(e.c == 1);
			break;
		case ProxyTextTag.FIELD_CENTERED:
			tag.setCentered(e.c == 1);
			break;
		case ProxyTextTag.FIELD_CENTERED_ON_POINT:
			tag.setCenteredOnPoint(e.c == 1);
			break;
		case ProxyTextTag.FIELD_RECEIVING_PLAYER:
			tag.setReceivingPlayer(e.c);
			break;
		case ProxyTextTag.FIELD_FADE_START:
			tag.setFadeStart(e.f0);
			break;
		case ProxyTextTag.FIELD_FONT_HEIGHT:
			tag.setFontHeight(e.f0);
			break;
		case ProxyTextTag.FIELD_LIFETIME:
			tag.setLifetime(e.f0);
			break;
		case ProxyTextTag.FIELD_LIFETIME_DURATION:
			tag.setLifetimeDuration(e.f0);
			break;
		case ProxyTextTag.FIELD_SUSPENDED:
			tag.setSuspended(e.c == 1);
			break;
		case ProxyTextTag.FIELD_HANDLE_ID:
			tag.setHandleId(e.c);
			break;
		default:
			throw new IllegalArgumentException("Unknown text tag field " + e.b);
		}
	}
}
