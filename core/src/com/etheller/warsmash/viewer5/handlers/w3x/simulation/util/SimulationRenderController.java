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
 * What the simulation tells the presentation (models, sounds, text). The game must play the same whatever an
 * implementation does, so nothing returned here may decide a game rule; {@link NoRenderController} runs it with none.
 */
public interface SimulationRenderController {
	/** Missile art for a projectile the simulation launched with a unit's attack settings. */
	void spawnProjectileArt(CProjectile projectile, CUnit source, CUnitAttackSettings settings, int bounceIndex);

	/** Missile art of an ability (its Missileart) for a projectile the simulation launched. */
	void spawnProjectileArt(CProjectile projectile, CUnit source, War3ID spellAlias);

	SimulationRenderComponentLightning createLightning(CSimulation simulation, War3ID lightningId, CUnit source,
			CUnit target);

	SimulationRenderComponentLightning createLightning(CSimulation simulation, War3ID lightningId, CUnit source,
			CUnit target, Float duration);

	SimulationRenderComponentLightning createAbilityLightning(CSimulation simulation, War3ID lightningId, CUnit source,
			CUnit target, int index);

	SimulationRenderComponentLightning createAbilityLightning(CSimulation simulation, War3ID lightningId, CUnit source,
			CUnit target, int index, Float duration);

	SimulationRenderComponent createStaticUberSplat(float x, float y, War3ID id);

	CUnit createUnit(CSimulation simulation, final War3ID typeId, final int playerIndex, final float x, final float y,
			final float facing);

	CItem createItem(CSimulation simulation, final War3ID typeId, final float x, final float y);

	void updateItemModel(CItem item);

	CDestructable createDestructable(War3ID typeId, float x, float y, float facing, float scale, int variation);

	CDestructable createDestructableZ(War3ID typeId, float x, float y, float z, float facing, float scale,
			int variation);

	void createInstantAttackEffect(CSimulation cSimulation, CUnit source, CUnitAttackInstant attack, CWidget target);

	void spawnDamageSound(CWidget damagedDestructable, String weaponSound, String armorType);

	void spawnUnitConstructionSound(CUnit constructingUnit, CUnit constructedStructure);

	void removeUnit(CUnit unit);

	void removeDestructable(CDestructable dest);

	void spawnUnitConstructionFinishSound(CUnit constructedStructure);

	void spawnUnitUpgradeFinishSound(CUnit constructedStructure);

	void spawnDeathExplodeEffect(CUnit cUnit, War3ID explodesOnDeathBuffId);

	void spawnGainLevelEffect(CUnit cUnit);

	void spawnUnitReadySound(CUnit trainedUnit);

	void unitRepositioned(CUnit cUnit);

	TextTag spawnTextTag(CUnit unit, TextTagConfigType configType, int displayAmount);

	TextTag spawnTextTag(CUnit unit, TextTagConfigType configType, String message);

	TextTag createTextTag();

	void destroyTextTag(TextTag textTag);

	void spawnEffectOnUnit(CUnit unit, String effectPath);

	void spawnTemporarySpellEffectOnUnit(CUnit unit, War3ID alias, CEffectType effectType);

	SimulationRenderComponentModel spawnPersistentSpellEffectOnUnit(CUnit unit, War3ID alias, CEffectType effectType);

	SimulationRenderComponentModel spawnPersistentSpellEffectOnUnit(CUnit unit, War3ID alias, CEffectType effectType,
			int index);

	SimulationRenderComponentModel spawnSpellEffectOnPoint(float x, float y, float facing, War3ID alias,
			CEffectType effectType, int index);

	void spawnTemporarySpellEffectOnPoint(float x, float y, float facing, War3ID alias, CEffectType effectType,
			int index);

	void spawnUIUnitGetItemSound(CUnit cUnit, CItem item);

	void spawnUIUnitDropItemSound(CUnit cUnit, CItem item);

	SimulationRenderComponent spawnAbilitySoundEffect(CUnit caster, War3ID alias);

	SimulationRenderComponent loopAbilitySoundEffect(CUnit caster, War3ID alias);

	void stopAbilitySoundEffect(CUnit caster, War3ID alias);

	void unitPreferredSelectionReplacement(CUnit unit, CUnit newUnit);

	void heroRevived(CUnit trainedUnit);

	void heroDeathEvent(CUnit cUnit);

	SimulationRenderComponentModel createSpellEffectOverDestructable(CUnit source, CDestructable target, War3ID alias,
			float artAttachmentHeight);

	void unitUpgradingEvent(CUnit unit, War3ID upgradeIdType);

	void unitCancelUpgradingEvent(CUnit unit, War3ID upgradeIdType);

	void setBlight(float x, float y, float radius, boolean blighted);

	void setBlightSquare(float x, float y, float halfSize, boolean blighted);

	void unitUpdatedType(CUnit unit, War3ID typeId, boolean updatePortrait);

	void changeUnitColor(CUnit unit, int playerIndex);

	void changeUnitPlayerColor(CUnit unit, int previousColor, int newColor);

	void changeUnitVertexColor(CUnit unit, Color color);

	void changeUnitVertexColor(CUnit unit, float r, float g, float b);

	void changeUnitVertexColor(CUnit unit, float r, float g, float b, float a);

	void adjustTerrain(float x, float y, float i);

	void adjustTerrain(int[] rect, float[] modBuffer);

	/** Height offsets (world units) of the terrain corners in a rectangle, replacing the ones set before. */
	default void setTerrainDeformation(final int column, final int row, final int width, final int height,
			final float[] offsets) {
	}

	/** Ground type of terrain corners: a column, row, tile type and variation for each. */
	default void setTerrainTypes(final int[] cells) {
	}

	void changeUnitScale(CUnit unit, float scale, boolean multiplier);

	SimulationRenderComponent spawnAbilitySoundEffect(float x, float y, War3ID alias, boolean looping);

}
