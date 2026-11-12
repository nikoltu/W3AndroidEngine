package com.etheller.warsmash.viewer5.handlers.w3x.simulation.util;

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

/**
 * Runs the game without presenting it while {@link #setPresenting(boolean) presenting} is off: no missiles, effects,
 * lightning, sounds or floating text are made. Objects that scripts hold or that the world keeps (units, items,
 * destructables, script text tags, unit looks, blight and terrain shape) still go to the given controller, which owns
 * how they are created, so the world is whole again when presenting resumes, as after a replay skips ahead. A game
 * must keep the same state, step by step, whichever way it runs.
 */
public class NoRenderController implements SimulationRenderController {
	private final SimulationRenderController world;
	private boolean presenting;

	public NoRenderController(final SimulationRenderController world) {
		this.world = world;
	}

	public void setPresenting(final boolean presenting) {
		this.presenting = presenting;
	}

	public boolean isPresenting() {
		return this.presenting;
	}

	@Override
	public void spawnProjectileArt(final CProjectile projectile, final CUnit source,
			final CUnitAttackSettings settings, final int bounceIndex) {
		if (this.presenting) {
			this.world.spawnProjectileArt(projectile, source, settings, bounceIndex);
		}
	}

	@Override
	public void spawnProjectileArt(final CProjectile projectile, final CUnit source, final War3ID spellAlias) {
		if (this.presenting) {
			this.world.spawnProjectileArt(projectile, source, spellAlias);
		}
	}

	@Override
	public SimulationRenderComponentLightning createLightning(final CSimulation simulation, final War3ID lightningId,
			final CUnit source, final CUnit target) {
		if (this.presenting) {
			return this.world.createLightning(simulation, lightningId, source, target);
		}
		return SimulationRenderComponentLightning.DO_NOTHING;
	}

	@Override
	public SimulationRenderComponentLightning createLightning(final CSimulation simulation, final War3ID lightningId,
			final CUnit source, final CUnit target, final Float duration) {
		if (this.presenting) {
			return this.world.createLightning(simulation, lightningId, source, target, duration);
		}
		return SimulationRenderComponentLightning.DO_NOTHING;
	}

	@Override
	public SimulationRenderComponentLightning createAbilityLightning(final CSimulation simulation,
			final War3ID lightningId, final CUnit source, final CUnit target, final int index) {
		if (this.presenting) {
			return this.world.createAbilityLightning(simulation, lightningId, source, target, index);
		}
		return SimulationRenderComponentLightning.DO_NOTHING;
	}

	@Override
	public SimulationRenderComponentLightning createAbilityLightning(final CSimulation simulation,
			final War3ID lightningId, final CUnit source, final CUnit target, final int index, final Float duration) {
		if (this.presenting) {
			return this.world.createAbilityLightning(simulation, lightningId, source, target, index, duration);
		}
		return SimulationRenderComponentLightning.DO_NOTHING;
	}

	@Override
	public SimulationRenderComponent createStaticUberSplat(final float x, final float y, final War3ID id) {
		if (this.presenting) {
			return this.world.createStaticUberSplat(x, y, id);
		}
		return SimulationRenderComponent.DO_NOTHING;
	}

	@Override
	public CUnit createUnit(final CSimulation simulation, final War3ID typeId, final int playerIndex, final float x,
			final float y, final float facing) {
		return this.world.createUnit(simulation, typeId, playerIndex, x, y, facing);
	}

	@Override
	public CItem createItem(final CSimulation simulation, final War3ID typeId, final float x, final float y) {
		return this.world.createItem(simulation, typeId, x, y);
	}

	@Override
	public void updateItemModel(final CItem item) {
		this.world.updateItemModel(item);
	}

	@Override
	public CDestructable createDestructable(final War3ID typeId, final float x, final float y, final float facing,
			final float scale, final int variation) {
		return this.world.createDestructable(typeId, x, y, facing, scale, variation);
	}

	@Override
	public CDestructable createDestructableZ(final War3ID typeId, final float x, final float y, final float z,
			final float facing, final float scale, final int variation) {
		return this.world.createDestructableZ(typeId, x, y, z, facing, scale, variation);
	}

	@Override
	public void createInstantAttackEffect(final CSimulation cSimulation, final CUnit source,
			final CUnitAttackInstant attack, final CWidget target) {
		if (this.presenting) {
			this.world.createInstantAttackEffect(cSimulation, source, attack, target);
		}
	}

	@Override
	public void spawnDamageSound(final CWidget damagedDestructable, final String weaponSound,
			final String armorType) {
		if (this.presenting) {
			this.world.spawnDamageSound(damagedDestructable, weaponSound, armorType);
		}
	}

	@Override
	public void spawnUnitConstructionSound(final CUnit constructingUnit, final CUnit constructedStructure) {
		if (this.presenting) {
			this.world.spawnUnitConstructionSound(constructingUnit, constructedStructure);
		}
	}

	@Override
	public void removeUnit(final CUnit unit) {
		this.world.removeUnit(unit);
	}

	@Override
	public void removeDestructable(final CDestructable dest) {
		this.world.removeDestructable(dest);
	}

	@Override
	public void spawnUnitConstructionFinishSound(final CUnit constructedStructure) {
		if (this.presenting) {
			this.world.spawnUnitConstructionFinishSound(constructedStructure);
		}
	}

	@Override
	public void spawnUnitUpgradeFinishSound(final CUnit constructedStructure) {
		if (this.presenting) {
			this.world.spawnUnitUpgradeFinishSound(constructedStructure);
		}
	}

	@Override
	public void spawnDeathExplodeEffect(final CUnit cUnit, final War3ID explodesOnDeathBuffId) {
		if (this.presenting) {
			this.world.spawnDeathExplodeEffect(cUnit, explodesOnDeathBuffId);
		}
	}

	@Override
	public void spawnGainLevelEffect(final CUnit cUnit) {
		if (this.presenting) {
			this.world.spawnGainLevelEffect(cUnit);
		}
	}

	@Override
	public void spawnUnitReadySound(final CUnit trainedUnit) {
		if (this.presenting) {
			this.world.spawnUnitReadySound(trainedUnit);
		}
	}

	@Override
	public void unitRepositioned(final CUnit cUnit) {
		this.world.unitRepositioned(cUnit);
	}

	@Override
	public TextTag spawnTextTag(final CUnit unit, final TextTagConfigType configType, final int displayAmount) {
		if (this.presenting) {
			return this.world.spawnTextTag(unit, configType, displayAmount);
		}
		return null;
	}

	@Override
	public TextTag spawnTextTag(final CUnit unit, final TextTagConfigType configType, final String message) {
		if (this.presenting) {
			return this.world.spawnTextTag(unit, configType, message);
		}
		return null;
	}

	@Override
	public TextTag createTextTag() {
		return this.world.createTextTag();
	}

	@Override
	public void destroyTextTag(final TextTag textTag) {
		this.world.destroyTextTag(textTag);
	}

	@Override
	public void spawnEffectOnUnit(final CUnit unit, final String effectPath) {
		if (this.presenting) {
			this.world.spawnEffectOnUnit(unit, effectPath);
		}
	}

	@Override
	public void spawnTemporarySpellEffectOnUnit(final CUnit unit, final War3ID alias, final CEffectType effectType) {
		if (this.presenting) {
			this.world.spawnTemporarySpellEffectOnUnit(unit, alias, effectType);
		}
	}

	@Override
	public SimulationRenderComponentModel spawnPersistentSpellEffectOnUnit(final CUnit unit, final War3ID alias,
			final CEffectType effectType) {
		if (this.presenting) {
			return this.world.spawnPersistentSpellEffectOnUnit(unit, alias, effectType);
		}
		return SimulationRenderComponentModel.DO_NOTHING;
	}

	@Override
	public SimulationRenderComponentModel spawnPersistentSpellEffectOnUnit(final CUnit unit, final War3ID alias,
			final CEffectType effectType, final int index) {
		if (this.presenting) {
			return this.world.spawnPersistentSpellEffectOnUnit(unit, alias, effectType, index);
		}
		return SimulationRenderComponentModel.DO_NOTHING;
	}

	@Override
	public SimulationRenderComponentModel spawnSpellEffectOnPoint(final float x, final float y, final float facing,
			final War3ID alias, final CEffectType effectType, final int index) {
		if (this.presenting) {
			return this.world.spawnSpellEffectOnPoint(x, y, facing, alias, effectType, index);
		}
		return SimulationRenderComponentModel.DO_NOTHING;
	}

	@Override
	public void spawnTemporarySpellEffectOnPoint(final float x, final float y, final float facing,
			final War3ID alias, final CEffectType effectType, final int index) {
		if (this.presenting) {
			this.world.spawnTemporarySpellEffectOnPoint(x, y, facing, alias, effectType, index);
		}
	}

	@Override
	public void spawnUIUnitGetItemSound(final CUnit cUnit, final CItem item) {
		if (this.presenting) {
			this.world.spawnUIUnitGetItemSound(cUnit, item);
		}
	}

	@Override
	public void spawnUIUnitDropItemSound(final CUnit cUnit, final CItem item) {
		if (this.presenting) {
			this.world.spawnUIUnitDropItemSound(cUnit, item);
		}
	}

	@Override
	public SimulationRenderComponent spawnAbilitySoundEffect(final CUnit caster, final War3ID alias) {
		if (this.presenting) {
			return this.world.spawnAbilitySoundEffect(caster, alias);
		}
		return SimulationRenderComponent.DO_NOTHING;
	}

	@Override
	public SimulationRenderComponent loopAbilitySoundEffect(final CUnit caster, final War3ID alias) {
		if (this.presenting) {
			return this.world.loopAbilitySoundEffect(caster, alias);
		}
		return SimulationRenderComponent.DO_NOTHING;
	}

	@Override
	public void stopAbilitySoundEffect(final CUnit caster, final War3ID alias) {
		if (this.presenting) {
			this.world.stopAbilitySoundEffect(caster, alias);
		}
	}

	@Override
	public SimulationRenderComponent spawnAbilitySoundEffect(final float x, final float y, final War3ID alias,
			final boolean looping) {
		if (this.presenting) {
			return this.world.spawnAbilitySoundEffect(x, y, alias, looping);
		}
		return SimulationRenderComponent.DO_NOTHING;
	}

	@Override
	public void unitPreferredSelectionReplacement(final CUnit unit, final CUnit newUnit) {
		this.world.unitPreferredSelectionReplacement(unit, newUnit);
	}

	@Override
	public void heroRevived(final CUnit trainedUnit) {
		this.world.heroRevived(trainedUnit);
	}

	@Override
	public void heroDeathEvent(final CUnit cUnit) {
		this.world.heroDeathEvent(cUnit);
	}

	@Override
	public SimulationRenderComponentModel createSpellEffectOverDestructable(final CUnit source,
			final CDestructable target, final War3ID alias, final float artAttachmentHeight) {
		if (this.presenting) {
			return this.world.createSpellEffectOverDestructable(source, target, alias, artAttachmentHeight);
		}
		return SimulationRenderComponentModel.DO_NOTHING;
	}

	@Override
	public void unitUpgradingEvent(final CUnit unit, final War3ID upgradeIdType) {
		this.world.unitUpgradingEvent(unit, upgradeIdType);
	}

	@Override
	public void unitCancelUpgradingEvent(final CUnit unit, final War3ID upgradeIdType) {
		this.world.unitCancelUpgradingEvent(unit, upgradeIdType);
	}

	@Override
	public void setBlight(final float x, final float y, final float radius, final boolean blighted) {
		this.world.setBlight(x, y, radius, blighted);
	}

	@Override
	public void setBlightSquare(final float x, final float y, final float halfSize, final boolean blighted) {
		this.world.setBlightSquare(x, y, halfSize, blighted);
	}

	@Override
	public void unitUpdatedType(final CUnit unit, final War3ID typeId, final boolean updatePortrait) {
		this.world.unitUpdatedType(unit, typeId, updatePortrait);
	}

	@Override
	public void changeUnitColor(final CUnit unit, final int playerIndex) {
		this.world.changeUnitColor(unit, playerIndex);
	}

	@Override
	public void changeUnitPlayerColor(final CUnit unit, final int previousColor, final int newColor) {
		this.world.changeUnitPlayerColor(unit, previousColor, newColor);
	}

	@Override
	public void changeUnitVertexColor(final CUnit unit, final Color color) {
		this.world.changeUnitVertexColor(unit, color);
	}

	@Override
	public void changeUnitVertexColor(final CUnit unit, final float r, final float g, final float b) {
		this.world.changeUnitVertexColor(unit, r, g, b);
	}

	@Override
	public void changeUnitVertexColor(final CUnit unit, final float r, final float g, final float b, final float a) {
		this.world.changeUnitVertexColor(unit, r, g, b, a);
	}

	@Override
	public void adjustTerrain(final float x, final float y, final float i) {
		this.world.adjustTerrain(x, y, i);
	}

	@Override
	public void adjustTerrain(final int[] rect, final float[] modBuffer) {
		this.world.adjustTerrain(rect, modBuffer);
	}

	@Override
	public void setTerrainTypes(final int[] cells) {
		this.world.setTerrainTypes(cells);
	}

	@Override
	public void setTerrainDeformation(final int column, final int row, final int width, final int height,
			final float[] offsets) {
		this.world.setTerrainDeformation(column, row, width, height, offsets);
	}

	@Override
	public void changeUnitScale(final CUnit unit, final float scale, final boolean multiplier) {
		this.world.changeUnitScale(unit, scale, multiplier);
	}
}
