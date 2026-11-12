package com.etheller.warsmash.viewer5.handlers.w3x;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SplatModel.SplatMover;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.BuildingShadow;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderAttackInstant;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItem;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderSpellEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.SpellEffectModel;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.BuffUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.EffectAttachmentUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.EffectAttachmentUIMissile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackInstant;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitAttackSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightning;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderController;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.TextTagConfigType;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws and sounds what the simulation does on a map viewer. The simulation reads nothing back from it that
 * changes the game.
 */
public class War3MapRenderController implements SimulationRenderController {
	/** The game draws the text it makes for a unit this far below a script tag put at the same point. */
	private static final float UNIT_TEXT_TAG_DROP = 19f;
	private final War3MapViewer viewer;
	private final Map<String, UnitSound> keyToCombatSound = new HashMap<>();

	public War3MapRenderController(final War3MapViewer viewer) {
		this.viewer = viewer;
	}

	@Override
	public void spawnProjectileArt(final CProjectile projectile, final CUnit source,
			final CUnitAttackSettings settings, final int bounceIndex) {
		final float x = projectile.getX();
		final float y = projectile.getY();
		final float launchZ = settings.getZ() != null ? settings.getZ()
				: source.getFlyHeight() + this.viewer.simulation.getUnitData().getProjectileLaunchZ(source.getTypeId());
		final float height = this.viewer.terrain.getGroundHeight(x, y) + launchZ;
		final MdxModel model = this.viewer.loadModelMdx(settings.getProjectileArt());
		if (model == null) {
			return;
		}
		final MdxComplexInstance modelInstance = (MdxComplexInstance) model.addInstance();
		modelInstance.setTeamColor(this.viewer.getMissileTeamColor(source));
		modelInstance.setScene(this.viewer.worldScene);
		if (bounceIndex == 0) {
			SequenceUtils.randomBirthSequence(modelInstance);
		}
		else {
			SequenceUtils.randomStandSequence(modelInstance);
		}
		modelInstance.setLocation(x, y, height);
		final RenderProjectile renderProjectile = new RenderProjectile(projectile, modelInstance, height,
				settings.getProjectileArc(), this.viewer);
		if (settings.getImpactZ() != null) {
			renderProjectile.setImpactZ(this.viewer, settings.getImpactZ());
		}
		if (settings.getArtDeathTime() != null) {
			renderProjectile.setDeathTime(settings.getArtDeathTime());
		}
		this.viewer.projectiles.add(renderProjectile);
	}

	@Override
	public void spawnProjectileArt(final CProjectile projectile, final CUnit source, final War3ID spellAlias) {
		final AbilityUI spellDataUI = this.viewer.abilityDataUI.getUI(spellAlias);
		final EffectAttachmentUIMissile missileArt = spellDataUI == null ? null : spellDataUI.getMissileArt(0);
		if (missileArt == null) {
			return;
		}
		final MdxModel model = this.viewer.loadModelMdx(missileArt.getModelPath());
		if (model == null) {
			return;
		}
		final float x = projectile.getX();
		final float y = projectile.getY();
		final float height = this.viewer.terrain.getGroundHeight(x, y) + source.getFlyHeight()
				+ this.viewer.simulation.getUnitData().getProjectileLaunchZ(source.getTypeId());
		final MdxComplexInstance modelInstance = (MdxComplexInstance) model.addInstance();
		modelInstance.setTeamColor(this.viewer.getMissileTeamColor(source));
		modelInstance.setScene(this.viewer.worldScene);
		SequenceUtils.randomBirthSequence(modelInstance);
		modelInstance.setLocation(x, y, height);
		this.viewer.projectiles
				.add(new RenderProjectile(projectile, modelInstance, height, missileArt.getArc(), this.viewer));
	}

	@Override
	public void createInstantAttackEffect(final CSimulation cSimulation, final CUnit source,
			final CUnitAttackInstant unitAttack, final CWidget target) {
		final War3ID typeId = source.getTypeId();

		final String missileArt = unitAttack.getProjectileArt();
		final float projectileLaunchX = this.viewer.simulation.getUnitData()
				.getProjectileLaunchX(typeId);
		final float projectileLaunchY = this.viewer.simulation.getUnitData()
				.getProjectileLaunchY(typeId);
		final float facing = (float) Math.toRadians(source.getFacing());
		final float sinFacing = (float) Math.sin(facing);
		final float cosFacing = (float) Math.cos(facing);
		final float x = source.getX() + (projectileLaunchY * cosFacing)
				+ (projectileLaunchX * sinFacing);
		final float y = (source.getY() + (projectileLaunchY * sinFacing))
				- (projectileLaunchX * cosFacing);

		final float targetX = target.getX();
		final float targetY = target.getY();
		final float angleToTarget = (float) Math.atan2(targetY - y, targetX - x);

		final float height = this.viewer.terrain.getGroundHeight(targetX, targetY)
				+ target.getFlyHeight() + target.getImpactZ();

		final MdxModel model = this.viewer.loadModelMdx(missileArt);
		final MdxComplexInstance modelInstance = (MdxComplexInstance) model.addInstance();
		modelInstance.setTeamColor(source.getPlayerIndex());
		SequenceUtils.randomBirthSequence(modelInstance);
		modelInstance.setLocation(targetX, targetY, height);
		modelInstance.setScene(this.viewer.worldScene);
		this.viewer.projectiles
				.add(new RenderAttackInstant(modelInstance, this.viewer, angleToTarget));
	}

	@Override
	public SimulationRenderComponentLightning createLightning(final CSimulation simulation,
			final War3ID lightningId, final CUnit source, final CUnit target) {
		final RenderUnit renderPeerSource = this.viewer.getRenderPeer(source);
		final RenderWidget renderPeerTarget = this.viewer.getRenderPeer(target);
		return this.viewer.createLightning(lightningId, renderPeerSource,
				renderPeerTarget);
	}

	@Override
	public SimulationRenderComponentLightning createLightning(final CSimulation simulation,
			final War3ID lightningId, final CUnit source, final CUnit target,
			final Float duration) {
		final RenderUnit renderPeerSource = this.viewer.getRenderPeer(source);
		final RenderWidget renderPeerTarget = this.viewer.getRenderPeer(target);
		return this.viewer.createLightning(lightningId, renderPeerSource,
				renderPeerTarget, duration);
	}

	@Override
	public SimulationRenderComponentLightning createAbilityLightning(
			final CSimulation simulation, final War3ID lightningId, final CUnit source,
			final CUnit target, final int index) {
		final RenderUnit renderPeerSource = this.viewer.getRenderPeer(source);
		final RenderWidget renderPeerTarget = this.viewer.getRenderPeer(target);
		final List<War3ID> lightnings = this.viewer.getLightningEffectList(lightningId);
		return this.viewer.createLightning(lightnings.get(index), renderPeerSource,
				renderPeerTarget);
	}

	@Override
	public SimulationRenderComponentLightning createAbilityLightning(
			final CSimulation simulation, final War3ID lightningId, final CUnit source,
			final CUnit target, final int index, final Float duration) {
		final RenderUnit renderPeerSource = this.viewer.getRenderPeer(source);
		final RenderWidget renderPeerTarget = this.viewer.getRenderPeer(target);
		final List<War3ID> lightnings = this.viewer.getLightningEffectList(lightningId);
		if (index >= lightnings.size()) {
			return SimulationRenderComponentLightning.DO_NOTHING;
		}
		return this.viewer.createLightning(lightnings.get(index), renderPeerSource,
				renderPeerTarget, duration);
	}

	@Override
	public SimulationRenderComponent createStaticUberSplat(final float x, final float y,
			final War3ID uberId) {
		if (uberId != null) {
			final Element uberSplatInfo = this.viewer.terrain.uberSplatTable
					.get(uberId.asStringValue());
			if (uberSplatInfo != null) {
				String uberSplatTexturePath = uberSplatInfo.getField("Dir") + "\\"
						+ uberSplatInfo.getField("file") + ".blp";
				float uberSplatScaleValue = uberSplatInfo.getFieldFloatValue("Scale");
				final SplatMover buildingUberSplatDynamicIngame = this.viewer.terrain
						.addUberSplat(uberSplatTexturePath, x, y, 1, uberSplatScaleValue, false,
								false, false, false);
				return new SimulationRenderComponent() {
					@Override
					public void remove() {
						buildingUberSplatDynamicIngame.destroy(Gdx.gl30,
								War3MapRenderController.this.viewer.terrain.centerOffset);
					}
				};
			}
		}
		return new SimulationRenderComponent() {
			@Override
			public void remove() {
			}
		};
	}

	@Override
	public void spawnDamageSound(final CWidget damagedDestructable, final String weaponSound,
			final String armorType) {
		final RenderWidget damagedWidget = this.viewer
				.getRenderPeer(damagedDestructable);
		if ((damagedWidget == null)
				|| !this.viewer.isSoundHeardAt(damagedDestructable.getX(), damagedDestructable.getY())) {
			return;
		}
		final String key = weaponSound + armorType;
		UnitSound combatSound = this.keyToCombatSound.get(key);
		if (combatSound == null) {
			combatSound = UnitSound.create(this.viewer.dataSource,
					this.viewer.unitCombatSoundsTable, weaponSound, armorType);
			this.keyToCombatSound.put(key, combatSound);
		}
		combatSound.play(this.viewer.worldScene.audioContext, damagedDestructable.getX(),
				damagedDestructable.getY(), damagedWidget.getZ());
	}

	@Override
	public void spawnUnitConstructionSound(final CUnit constructingUnit,
			final CUnit constructedStructure) {
		final UnitSound constructingBuilding = this.viewer.uiSounds
				.getSound(this.viewer.gameUI.getSkinField("ConstructingBuilding"));
		if ((constructingBuilding != null)
				&& this.viewer.isSoundHeardAt(constructedStructure.getX(), constructedStructure.getY())) {
			constructingBuilding.playUnitResponse(this.viewer.worldScene.audioContext,
					this.viewer.unitToRenderPeer.get(constructedStructure));
		}
	}

	@Override
	public void unitUpgradingEvent(final CUnit unit, final War3ID upgradeIdType) {
//						final RenderUnit renderUnit = this.viewer.unitToRenderPeer.get(unit);
		final EnumSet<SecondaryTag> originalRequiredAnimationNames = this.viewer.renderUnitTypeData
				.get(unit.getTypeId()).getRequiredAnimationNames();
		boolean changedAnimationProperties = false;
		for (final SecondaryTag secondaryTag : originalRequiredAnimationNames) {
			if (unit.getUnitAnimationListener().removeSecondaryTag(secondaryTag)) {
				changedAnimationProperties = true;
			}
		}
		final EnumSet<SecondaryTag> requiredAnimationNames = this.viewer.renderUnitTypeData
				.get(upgradeIdType).getRequiredAnimationNames();
		for (final SecondaryTag secondaryTag : requiredAnimationNames) {
			if (unit.getUnitAnimationListener().addSecondaryTag(secondaryTag)) {
				changedAnimationProperties = true;
			}
		}

		if (changedAnimationProperties) {
			unit.getUnitAnimationListener().forceResetCurrentAnimation();
		}
	}

	@Override
	public void unitCancelUpgradingEvent(final CUnit unit, final War3ID upgradeIdType) {
		final EnumSet<SecondaryTag> requiredAnimationNames = this.viewer.renderUnitTypeData
				.get(upgradeIdType).getRequiredAnimationNames();
		boolean changedAnimationProperties = false;
		for (final SecondaryTag secondaryTag : requiredAnimationNames) {
			if (unit.getUnitAnimationListener().removeSecondaryTag(secondaryTag)) {
				changedAnimationProperties = true;
			}
		}
		final EnumSet<SecondaryTag> originalRequiredAnimationNames = this.viewer.renderUnitTypeData
				.get(unit.getTypeId()).getRequiredAnimationNames();
		for (final SecondaryTag secondaryTag : originalRequiredAnimationNames) {
			if (unit.getUnitAnimationListener().addSecondaryTag(secondaryTag)) {
				changedAnimationProperties = true;
			}
		}

		if (changedAnimationProperties) {
			unit.getUnitAnimationListener().forceResetCurrentAnimation();
		}
	}

	@Override
	public void removeUnit(final CUnit unit) {
		final RenderUnit renderUnit = this.viewer.unitToRenderPeer.remove(unit);
		if (renderUnit == null) {
			Log.warn("Attempted to remove a null unit!");
			return;
		}
		this.viewer.widgets.remove(renderUnit);
		this.viewer.units.remove(renderUnit);
		this.viewer.worldScene.removeInstance(renderUnit.instance);
		renderUnit.onRemove(this.viewer);
	}

	@Override
	public void removeDestructable(final CDestructable dest) {
		final RenderDestructable renderPeer = this.viewer.destructableToRenderPeer
				.remove(dest);
		this.viewer.worldScene.removeInstance(renderPeer.instance);
		if (renderPeer.walkableBounds != null) {
			this.viewer.walkableObjectsTree.remove(
					(MdxComplexInstance) renderPeer.instance, renderPeer.walkableBounds);
		}
	}

	@Override
	public void spawnUnitConstructionFinishSound(final CUnit constructedStructure) {
		final UnitSound constructingBuilding = this.viewer.uiSounds
				.getSound(this.viewer.gameUI.getSkinField("JobDoneSound"));
		final RenderUnit renderUnit = this.viewer.unitToRenderPeer
				.get(constructedStructure);
		if ((constructingBuilding != null) && (renderUnit.getSimulationUnit()
				.getPlayerIndex() == this.viewer.localPlayerIndex)) {
			constructingBuilding.play(this.viewer.worldScene.audioContext,
					constructedStructure.getX(), constructedStructure.getY(),
					renderUnit.getZ());
		}
	}

	@Override
	public void spawnUnitUpgradeFinishSound(final CUnit constructedStructure) {
		final UnitSound constructingBuilding = this.viewer.uiSounds
				.getSound(this.viewer.gameUI.getSkinField("UpgradeComplete"));
		final RenderUnit renderUnit = this.viewer.unitToRenderPeer
				.get(constructedStructure);
		if ((constructingBuilding != null) && (renderUnit.getSimulationUnit()
				.getPlayerIndex() == this.viewer.localPlayerIndex)) {
			constructingBuilding.play(this.viewer.worldScene.audioContext,
					constructedStructure.getX(), constructedStructure.getY(),
					renderUnit.getZ());
		}
	}

	@Override
	public CUnit createUnit(final CSimulation simulation, final War3ID typeId,
			final int playerIndex, final float x, final float y, final float facing) {
		final CUnit unit = (CUnit) this.viewer.createNewUnit(this.viewer.allObjectData, typeId, x, y,
				playerIndex, playerIndex, (float) Math.toRadians(facing));
		// The minimap's creep camps start from the creeps the map placed in its first tick, also those that the melee
		// setup then removes around the start locations.
		if ((unit != null) && (simulation.getGameTurnTick() <= 1) && simulation.isNeutralAggressive(playerIndex)
				&& !unit.isBuilding()) {
			this.viewer.addInitialCreep(x, y, unit.getUnitType().getLevel());
		}
		return unit;
	}

	@Override
	public CDestructable createDestructable(final War3ID typeId, final float requestedX, final float requestedY,
			final float facing, final float scale, final int variation) {
		final GameObject row = this.viewer.allObjectData.getDestructibles().get(typeId);
		final float x = row == null ? requestedX : this.viewer.snapRunTimeDestructableX(row, requestedX, facing);
		final float y = row == null ? requestedY : this.viewer.snapRunTimeDestructableY(row, requestedY, facing);
		return createDestructableZ(typeId, x, y,
				Math.max(this.viewer.getWalkableRenderHeight(x, y),
						this.viewer.terrain.getGroundHeight(x, y)),
				facing, scale, variation);
	}

	@Override
	public CDestructable createDestructableZ(final War3ID typeId, final float requestedX, final float requestedY,
			final float z, final float facing, final float scale, final int variation) {
		final GameObject row = this.viewer.allObjectData.getDestructibles().get(typeId);
		if (row == null) {
			throw new NullPointerException("createDestructableZ called on: " + typeId + "," + requestedX
					+ "," + requestedY + "," + z + "," + facing + "," + scale + "," + variation);
		}
		final float x = this.viewer.snapRunTimeDestructableX(row, requestedX, facing);
		final float y = this.viewer.snapRunTimeDestructableY(row, requestedY, facing);
		final float[] location3d = { x, y, z };
		final float[] scale3d = { scale, scale, scale };
		final RenderDestructable newDestructable = this.viewer.createNewDestructable(typeId, row, variation,
				location3d, (float) Math.toRadians(facing), (short) 100, scale3d);
		this.viewer.createdDestructables
				.add(new War3MapViewer.CreatedDestructable(newDestructable.getSimulationDestructable().getHandleId(),
						typeId.getValue(), x, y, z, facing, scale, variation));
		return newDestructable.getSimulationDestructable();
	}

	@Override
	public CItem createItem(final CSimulation simulation, final War3ID typeId, final float x,
			final float y) {
		return (CItem) this.viewer.createNewUnit(this.viewer.allObjectData, typeId, x, y, -1, -1,
				(float) Math.toRadians(this.viewer.simulation.getGameplayConstants()
						.getBuildingAngle()));
	}

	@Override
	public void updateItemModel(CItem item) {
		RenderItem ri = this.viewer.getRenderPeer(item);
		RenderItemType rit = this.viewer.renderItemTypeData.get(item.getTypeId());
		if (ri != null && rit != null) {
			ri.updateItemModel(this.viewer, rit);
		}
	}

	@Override
	public void spawnDeathExplodeEffect(final CUnit source,
			final War3ID explodesOnDeathBuffId) {
		final RenderUnit renderUnit = this.viewer.unitToRenderPeer.get(source);
		MdxComplexInstance modelInstance = null;
		if (explodesOnDeathBuffId != null) {
			EffectAttachmentUI effectAttachmentUI = this.viewer.getEffectAttachmentUI(explodesOnDeathBuffId,
					CEffectType.EFFECT, 0);
			String modelPath = effectAttachmentUI.getModelPath();
			if (modelPath == null || modelPath.isEmpty()) {
				effectAttachmentUI = this.viewer.getEffectAttachmentUI(explodesOnDeathBuffId,
						CEffectType.SPECIAL, 0);
				modelPath = effectAttachmentUI.getModelPath();
			}
			final MdxModel spawnedEffectModel = this.viewer.loadModelMdx(modelPath);
			if (spawnedEffectModel != null) {
				modelInstance = (MdxComplexInstance) spawnedEffectModel.addInstance();
			}
		}
		if ((modelInstance == null) && (renderUnit.specialArtModel != null)) {
			modelInstance = (MdxComplexInstance) renderUnit.specialArtModel.addInstance();
		}
		if (modelInstance != null) {
			modelInstance.setTeamColor(source.getPlayerIndex());
			modelInstance.setLocation(renderUnit.location);
			modelInstance.setScene(this.viewer.worldScene);
			SequenceUtils.randomBirthSequence(modelInstance);
			this.viewer.projectiles.add(new RenderAttackInstant(modelInstance,
					this.viewer,
					(float) Math.toRadians(renderUnit.getSimulationUnit().getFacing())));
		}
	}

	@Override
	public void spawnGainLevelEffect(final CUnit source) {
		final AbilityUI heroUI = this.viewer.abilityDataUI.getUI(War3MapViewer.ABILITY_HERO_RAWCODE);
		final RenderUnit renderUnit = this.viewer.unitToRenderPeer.get(source);
		final String heroLevelUpArt = heroUI.getCasterArt(0).getModelPath();
		// TODO use addSpellEffectTarget
		this.viewer.spawnFxOnOrigin(renderUnit, heroLevelUpArt);
	}

	@Override
	public void heroRevived(final CUnit source) {
		final AbilityUI reviveUI = this.viewer.abilityDataUI
				.getUI(War3MapViewer.ABILITY_REVIVE_RAWCODE);
		final RenderUnit renderUnit = this.viewer.unitToRenderPeer.get(source);
		renderUnit.instance.additiveOverrideMeshMode = false;
		renderUnit.instance.setVertexAlpha(1.0f);
		final CPlayer player = this.viewer.simulation.getPlayer(source.getPlayerIndex());
		final String heroReviveArt = reviveUI.getTargetArt(player.getRace().ordinal())
				.getModelPath();
		// TODO use addSpellEffectTarget, maybe?
		this.viewer.spawnFxOnOrigin(renderUnit, heroReviveArt);

		// Recreate unit shadow.... is needed here
		renderUnit.shadow = this.viewer.createUnitShadow(renderUnit.getTypeData().getRenderShadowType(),
				source.getX(), source.getY());
	}

	@Override
	public void heroDeathEvent(final CUnit source) {
		final RenderUnit renderUnit = this.viewer.unitToRenderPeer.get(source);
		renderUnit.instance.additiveOverrideMeshMode = true;
	}

	@Override
	public void spawnEffectOnUnit(final CUnit unit, final String effectPath) {
		final RenderSpellEffect spellEffect = this.viewer.addSpecialEffectTarget(effectPath, unit, "");
		if (spellEffect != null) {
			spellEffect.setKillWhenDone(true);
		}
	}

	@Override
	public void spawnTemporarySpellEffectOnUnit(final CUnit unit, final War3ID alias,
			final CEffectType effectType) {
		final RenderSpellEffect spellEffect = this.viewer.spawnSpellEffectOnUnitEx(unit, alias, effectType,
				0);
		if (spellEffect != null) {
			spellEffect.setKillWhenDone(true);
		}
	}

	@Override
	public SimulationRenderComponentModel spawnPersistentSpellEffectOnUnit(final CUnit unit,
			final War3ID alias, final CEffectType effectType) {
		final List<RenderSpellEffect> specialEffects = this.viewer.spawnSpellEffectOnUnitEx(unit, alias,
				effectType);
		if ((specialEffects == null) || specialEffects.isEmpty()) {
			return SimulationRenderComponentModel.DO_NOTHING;
		}
		return new SpellEffectModel(specialEffects, 0);
	}

	@Override
	public SimulationRenderComponentModel spawnPersistentSpellEffectOnUnit(final CUnit unit,
			final War3ID alias, final CEffectType effectType, final int index) {
		final RenderSpellEffect specialEffect = this.viewer.spawnSpellEffectOnUnitEx(unit, alias,
				effectType, index);
		if (specialEffect == null) {
			return SimulationRenderComponentModel.DO_NOTHING;
		}
		return new SpellEffectModel(Collections.singletonList(specialEffect), 0);
	}

	@Override
	public SimulationRenderComponentModel createSpellEffectOverDestructable(final CUnit source,
			final CDestructable target, final War3ID alias, final float artAttachmentHeight) {
		final AbilityUI abilityUI = this.viewer.abilityDataUI.getUI(alias);
		final String effectPath = abilityUI.getTargetArt(0).getModelPath();
		// TODO use addSpellEffectTarget maybe?
		final RenderDestructable renderDestructable = this.viewer.destructableToRenderPeer
				.get(target);
		final MdxModel spawnedEffectModel = this.viewer.loadModelMdx(effectPath);
		if (spawnedEffectModel != null) {
			final MdxComplexInstance modelInstance = (MdxComplexInstance) spawnedEffectModel
					.addInstance();
			modelInstance.setTeamColor(this.viewer.simulation
					.getPlayer(source.getPlayerIndex()).getColor());
			final float startingHeight = renderDestructable.getZ() + artAttachmentHeight;
			modelInstance.setLocation(renderDestructable.getX(), renderDestructable.getY(),
					startingHeight);
			modelInstance.setScene(this.viewer.worldScene);
			final RenderSpellEffect renderAttackInstant = new RenderSpellEffect(modelInstance,
					this.viewer, 0, RenderSpellEffect.STAND_ONLY, SequenceUtils.EMPTY);
			renderAttackInstant.setAnimations(RenderSpellEffect.STAND_ONLY, false);
			this.viewer.projectiles.add(renderAttackInstant);
			return new SimulationRenderComponentModel() {
				@Override
				public void remove() {
					renderAttackInstant.setAnimations(RenderSpellEffect.DEATH_ONLY, true);
				}

				@Override
				public void setHeight(final float height) {
					renderAttackInstant.setHeight(startingHeight + height);
				}
			};
		}
		return null;
	}

	@Override
	public SimulationRenderComponentModel spawnSpellEffectOnPoint(final float x, final float y,
			final float facing, final War3ID alias, final CEffectType effectType,
			final int index) {
		final RenderSpellEffect specialEffect = this.viewer.spawnSpellEffectEx(x, y, facing, alias,
				effectType, index);
		if (specialEffect == null) {
			return SimulationRenderComponentModel.DO_NOTHING;
		}
		return new SpellEffectModel(Collections.singletonList(specialEffect),
				Math.max(this.viewer.getWalkableRenderHeight(x, y),
						this.viewer.terrain.getGroundHeight(x, y)));
	}

	@Override
	public void spawnTemporarySpellEffectOnPoint(final float x, final float y,
			final float facing, final War3ID alias, final CEffectType effectType,
			final int index) {
		final RenderSpellEffect specialEffect = this.viewer.spawnSpellEffectEx(x, y, facing, alias,
				effectType, index);
		if (specialEffect != null) {
			specialEffect.setKillWhenDone(true);
		}
	}

	@Override
	public void spawnUnitReadySound(final CUnit trainedUnit) {
		if (trainedUnit.getPlayerIndex() == this.viewer.localPlayerIndex) {
			final RenderUnit renderPeer = this.viewer.unitToRenderPeer.get(trainedUnit);
			renderPeer.soundset.ready
					.playUnitResponse(this.viewer.worldScene.audioContext, renderPeer);
		}
	}

	@Override
	public void unitRepositioned(final CUnit cUnit) {
		final RenderUnit renderPeer = this.viewer.unitToRenderPeer.get(cUnit);
		renderPeer.repositioned(this.viewer);
	}

	@Override
	public void unitUpdatedType(final CUnit simulationUnit, final War3ID typeId,
			final boolean updatePortrait) {
		final RenderUnit renderPeer = this.viewer.unitToRenderPeer.get(simulationUnit);

		final RenderUnitType typeData = this.viewer.getUnitTypeData(typeId);
		final int customTeamColor = this.viewer.simulation
				.getPlayer(simulationUnit.getPlayerIndex()).getColor();
		final float unitX = simulationUnit.getX();
		final float unitY = simulationUnit.getY();
		final float unitZ = Math.max(this.viewer.getWalkableRenderHeight(unitX, unitY),
				this.viewer.terrain.getGroundHeight(unitX, unitY))
				+ simulationUnit.getFlyHeight();

		BuildingShadow buildingShadowInstance = null;
		final String buildingShadow = typeData.getBuildingShadow();
		if (buildingShadow != null) {
			buildingShadowInstance = this.viewer.terrain.addShadow(buildingShadow, unitX,
					unitY);
		}

		if (renderPeer.shadow != null) {
			renderPeer.shadow.destroy(Gdx.gl30, this.viewer.terrain.centerOffset);
			renderPeer.shadow = null;
		}
		renderPeer.shadow = this.viewer.createUnitShadow(typeData.getRenderShadowType(), unitX, unitY);

		renderPeer.resetRenderUnit(this.viewer, unitX, unitY, unitZ,
				customTeamColor /* renderPeer.playerIndex */, simulationUnit, typeData,
				updatePortrait, buildingShadowInstance,
				this.viewer.selectionCircleScaleFactor, typeData.getAnimationWalkSpeed(),
				typeData.getAnimationRunSpeed(), typeData.getScalingValue());
	}

	@Override
	public void changeUnitScale(final CUnit unit, final float scale, final boolean multiplier) {
		final RenderUnit renderPeer = this.viewer.unitToRenderPeer.get(unit);
		if (multiplier) {
			renderPeer.selectionScale *= scale;
			Vector3 newScale = new Vector3(renderPeer.instance.localScale);
			newScale.scl(scale);
			renderPeer.instance.setScale(new float[] { newScale.x, newScale.y, newScale.z });
		} else {
			renderPeer.selectionScale = scale;
			renderPeer.instance.setScale(new float[] { scale, scale, scale });
		}

	}

	@Override
	public void changeUnitColor(final CUnit unit, final int playerIndex) {
		final RenderUnit renderPeer = this.viewer.unitToRenderPeer.get(unit);
		if (renderPeer != null) {
			renderPeer.setPlayerColor(
					this.viewer.simulation.getPlayer(playerIndex).getColor());
		}
	}

	@Override
	public void changeUnitVertexColor(final CUnit unit, final Color color) {
		final RenderUnit renderPeer = this.viewer.unitToRenderPeer.get(unit);
		if (renderPeer != null) {
			renderPeer.setVertexColoring(this.viewer, color);
		}
	}

	@Override
	public void changeUnitVertexColor(final CUnit unit, final float r, final float g,
			final float b) {
		final RenderUnit renderPeer = this.viewer.unitToRenderPeer.get(unit);
		if (renderPeer != null) {
			renderPeer.setVertexColoring(this.viewer, r, g, b);
		}
	}

	@Override
	public void changeUnitVertexColor(final CUnit unit, final float r, final float g,
			final float b, final float a) {
		final RenderUnit renderPeer = this.viewer.unitToRenderPeer.get(unit);
		if (renderPeer != null) {
			renderPeer.setVertexColoring(this.viewer, r, g, b, a);
		}
	}

	@Override
	public TextTag spawnTextTag(final CUnit unit, final TextTagConfigType configType,
			final int displayAmount) {
		String text;
		switch (configType) {
		case GOLD:
		case GOLD_BOUNTY:
		case LUMBER:
		case LUMBER_BOUNTY:
		case XP: {
			text = "+" + displayAmount;
			break;
		}
		case MISS_TEXT:
			text = "miss!";
			break;
		case MANA_BURN:
			text = "-" + displayAmount;
			break;
		default:
		case CRITICAL_STRIKE:
		case SHADOW_STRIKE:
		case BASH: {
			text = displayAmount + "!";
			break;
		}
		}
		return spawnTextTag(unit, configType, text);
	}

	@Override
	public TextTag spawnTextTag(final CUnit unit, final TextTagConfigType configType,
			final String message) {
		final RenderUnit renderPeer = this.viewer.unitToRenderPeer.get(unit);
		final TextTagConfig textTagConfig = this.viewer.getTextTagConfig(configType.getKey());
		final Vector3 unitPosition;
		if (renderPeer != null) {
			unitPosition = new Vector3(renderPeer.location);
		} else if (unit != null) {
			// try to fudge it and spawn in place of stale unit
			unitPosition = new Vector3(unit.getX(), unit.getY(),
					this.viewer.terrain.getGroundHeight(unit.getX(), unit.getY()));
		} else {
			unitPosition = new Vector3(0, 0, 0);
		}
		unitPosition.z -= UNIT_TEXT_TAG_DROP;
		final TextTag textTag = new TextTag(unitPosition,
				new Vector2(textTagConfig.getVelocity()[0], textTagConfig.getVelocity()[1]),
				message, textTagConfig.getColor(), textTagConfig.getLifetime(),
				textTagConfig.getFadeStart(), textTagConfig.getHeight(),
				War3MapViewer.BUILTIN_TEXT_TAG_REPORTED_HANDLE_ID);
		textTag.setCenteredOnPoint(true);
		this.viewer.textTags.add(textTag);
		return textTag;
	}

	@Override
	public TextTag createTextTag() {
		// The game gives script text tags their handle id from its own pool.
		final TextTag genericTextTag = new TextTag(new Vector3(Vector3.Zero), new Vector2(0, 0),
				"", Color.WHITE, 100, 0, War3MapViewer.DEFAULT_TEXT_TAG_HEIGHT,
				War3MapViewer.BUILTIN_TEXT_TAG_REPORTED_HANDLE_ID);
		genericTextTag.setPermanent(true);
		this.viewer.textTags.add(genericTextTag);
		return genericTextTag;
	}

	@Override
	public void destroyTextTag(final TextTag textTag) {
		this.viewer.textTags.remove(textTag);
	}

	@Override
	public void spawnUIUnitGetItemSound(final CUnit cUnit, final CItem item) {
		final RenderUnit renderPeer = this.viewer.unitToRenderPeer.get(cUnit);
		if (this.viewer.localPlayerIndex == renderPeer.getSimulationUnit().getPlayerIndex()) {
			this.viewer.uiSounds.getSound("ItemGet").play(
					this.viewer.worldScene.audioContext, renderPeer.getX(),
					renderPeer.getY(), renderPeer.getZ());
		}
	}

	@Override
	public void spawnUIUnitDropItemSound(final CUnit cUnit, final CItem item) {
		final RenderUnit renderPeer = this.viewer.unitToRenderPeer.get(cUnit);
		if (this.viewer.localPlayerIndex == renderPeer.getSimulationUnit().getPlayerIndex()) {
			this.viewer.uiSounds.getSound("ItemDrop").play(
					this.viewer.worldScene.audioContext, renderPeer.getX(),
					renderPeer.getY(), renderPeer.getZ());
		}
	}

	@Override
	public SimulationRenderComponent spawnAbilitySoundEffect(final CUnit caster,
			final War3ID alias) {
		final RenderUnit renderPeer = this.viewer.unitToRenderPeer.get(caster);
		final AbilityUI abilityUi = this.viewer.abilityDataUI.getUI(alias);
		if ((renderPeer == null) || (abilityUi == null)
				|| (abilityUi.getEffectSound() == null) || (!isLoopingSound(abilityUi.getEffectSound())
						&& !this.viewer.isSoundHeardAt(caster.getX(), caster.getY()))) {
			return SimulationRenderComponent.DO_NOTHING;
		}
		final long soundId = this.viewer.uiSounds.getSound(abilityUi.getEffectSound())
				.playFollowing(this.viewer.worldScene.audioContext, renderPeer.getX(), renderPeer.getY(),
						renderPeer.getZ(), null, renderPeer.location);
		if (soundId == -1) {
			return SimulationRenderComponent.DO_NOTHING;
		}
		return new SimulationRenderComponent() {
			@Override
			public void remove() {
				War3MapRenderController.this.viewer.uiSounds.getSound(abilityUi.getEffectSoundLooped())
						.stop(soundId);
				;
			}
		};
	}

	private boolean isLoopingSound(final String soundName) {
		final UnitSound sound = this.viewer.uiSounds.getSound(soundName);
		return (sound != null) && sound.isLooping();
	}

	@Override
	public SimulationRenderComponent loopAbilitySoundEffect(final CUnit caster,
			final War3ID alias) {
		final RenderUnit renderPeer = this.viewer.unitToRenderPeer.get(caster);
		final AbilityUI abilityUi = this.viewer.abilityDataUI.getUI(alias);
		final String loopedSound;
		if (abilityUi != null) {
			loopedSound = abilityUi.getEffectSoundLooped();
		}
		else {
			// Buffs such as Cyclone and Mana Flare carry their own looped sound.
			final BuffUI buffUi = this.viewer.abilityDataUI.getBuffUI(alias);
			loopedSound = buffUi == null ? null : buffUi.getEffectSoundLooped();
		}
		if ((renderPeer == null) || (loopedSound == null) || loopedSound.isEmpty()) {
			return SimulationRenderComponent.DO_NOTHING;
		}
		// The loop follows the unit and the player's sight, so it starts whether or not the unit is seen now.
		final UnitSound loopSound = this.viewer.uiSounds.getSound(loopedSound);
		final long soundId = loopSound == null ? -1
				: loopSound.playFollowing(this.viewer.worldScene.audioContext, renderPeer.getX(), renderPeer.getY(),
						renderPeer.getZ(), true, renderPeer.location);
		if (soundId == -1) {
			return SimulationRenderComponent.DO_NOTHING;
		}
		return new SimulationRenderComponent() {
			@Override
			public void remove() {
				War3MapRenderController.this.viewer.uiSounds.getSound(loopedSound).stop(soundId);
			}
		};
	}

	@Override
	public void stopAbilitySoundEffect(final CUnit caster, final War3ID alias) {
		final AbilityUI abilityUi = this.viewer.abilityDataUI.getUI(alias);
		if (abilityUi.getEffectSoundLooped() != null) {
			// TODO below this probably stops all instances of the sound, which is silly
			// and busted. Would be better to keep a notion of sound instance
			this.viewer.uiSounds.getSound(abilityUi.getEffectSoundLooped()).stop();
		}
	}

	@Override
	public SimulationRenderComponent spawnAbilitySoundEffect(final float x, float y,
			final War3ID alias, final boolean looping) {
		final AbilityUI abilityUi = this.viewer.abilityDataUI.getUI(alias);
		final String soundName = looping ? abilityUi.getEffectSoundLooped()
				: abilityUi.getEffectSound();
		if ((abilityUi == null) || (soundName == null)
				|| (!isLoopingSound(soundName) && !this.viewer.isSoundHeardAt(x, y))) {
			return SimulationRenderComponent.DO_NOTHING;
		}
		final long soundId = this.viewer.uiSounds.getSound(soundName).play(
				this.viewer.worldScene.audioContext, x, y,
				this.viewer.terrain.getGroundHeight(x, y));
		if (soundId == -1) {
			return SimulationRenderComponent.DO_NOTHING;
		}
		return new SimulationRenderComponent() {
			@Override
			public void remove() {
				War3MapRenderController.this.viewer.uiSounds.getSound(soundName).stop(soundId);
				;
			}
		};
	}

	@Override
	public void unitPreferredSelectionReplacement(final CUnit oldUnit, final CUnit newUnit) {
		final RenderUnit oldRenderPeer = this.viewer.unitToRenderPeer.get(oldUnit);
		final RenderUnit newRenderPeer = this.viewer.unitToRenderPeer.get(newUnit);
		oldRenderPeer.setPreferredSelectionReplacement(newRenderPeer);

	}

	@Override
	public void setBlight(final float x, final float y, final float radius,
			final boolean blighted) {
		this.viewer.setBlight(x, y, radius, blighted);
	}

	@Override
	public void setBlightSquare(final float x, final float y, final float halfSize,
			final boolean blighted) {
		this.viewer.setBlight(x, y, halfSize, true, blighted);
	}

	@Override
	public void adjustTerrain(int[] rect, float[] modBuffer) {
		this.viewer.terrain.updateGroundBuffer(rect, modBuffer);
	}

	@Override
	public void adjustTerrain(float x, float y, float i) {
		this.viewer.terrain.updateGroundBuffer(x, y, i);
	}

	@Override
	public void setTerrainDeformation(final int column, final int row, final int width,
			final int height, final float[] offsets) {
		this.viewer.terrain.setDeformation(column, row, width, height, offsets);
	}

	@Override
	public void setTerrainTypes(final int[] cells) {
		this.viewer.terrain.setGroundTypes(cells);
	}

	@Override
	public void changeUnitPlayerColor(final CUnit unit, final int previousColor,
			final int newColor) {
		final RenderUnit renderUnit = this.viewer.getRenderPeer(unit);
		if (renderUnit.playerIndex == previousColor) {
			renderUnit.setPlayerColor(newColor);
		}

	}
}
