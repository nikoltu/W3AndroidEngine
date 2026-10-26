package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPassiveSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimedLife;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponent;

/**
 * Disease Cloud: enemy living land units near the carrier catch Disease, which hurts them every second for the
 * aura duration but never kills; while they stay close it starts over. A carrier with a plague ward type leaves
 * a cloud behind for a while when it dies.
 */
public class CAbilityDiseaseCloud extends CAbilityPassiveSpellBase {
	private static final War3ID TIMED_LIFE_BUFF_ID = War3ID.fromString("BTLF");
	private static final int AREA_CHECK_TICKS = (int) (1.0f / WarsmashConstants.SIMULATION_STEP_TIME);

	private float diseaseDuration;
	private float damagePerSecond;
	private float plagueWardDuration;
	private War3ID plagueWardUnitType;
	private War3ID diseaseBuffId;
	private SimulationRenderComponent fx;
	private int nextAreaCheckTick;

	public CAbilityDiseaseCloud(final int handleId, final War3ID alias) {
		super(handleId, alias, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.diseaseDuration = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.damagePerSecond = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.plagueWardDuration = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		final String wardType = worldEditorAbility.getFieldAsString(AbilityFields.UNIT_ID + level, 0);
		this.plagueWardUnitType = (wardType == null) || (wardType.length() != 4) ? null
				: War3ID.fromString(wardType);
		this.diseaseBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		this.fx = game.createPersistentSpellEffectOnUnit(unit, getAlias(), CEffectType.TARGET, 0);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		if (this.fx != null) {
			this.fx.remove();
			this.fx = null;
		}
	}

	@Override
	public void onTick(final CSimulation game, final CUnit source) {
		if (isDisabled() || source.isDead() || source.isHidden()) {
			return;
		}
		final int gameTurnTick = game.getGameTurnTick();
		if (gameTurnTick < this.nextAreaCheckTick) {
			return;
		}
		this.nextAreaCheckTick = gameTurnTick + AREA_CHECK_TICKS;
		game.getWorldCollision().enumUnitsInRange(source.getX(), source.getY(), getAreaOfEffect(), (enumUnit) -> {
			if ((enumUnit != source) && enumUnit.canBeTargetedBy(game, source, getTargetsAllowed())) {
				final CBuffDisease existing = CUndeadUnitAbilities.findBuff(enumUnit, CBuffDisease.class,
						this.diseaseBuffId);
				if (existing != null) {
					existing.refresh(game, source, this.damagePerSecond);
				}
				else {
					enumUnit.add(game, new CBuffDisease(game.getHandleIdAllocator().createId(), this.diseaseBuffId,
							this.diseaseDuration, source, this.damagePerSecond));
				}
			}
			return false;
		});
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit unit) {
		if (isDisabled() || (this.plagueWardDuration <= 0) || (this.plagueWardUnitType == null)) {
			return;
		}
		spawnPlagueWard(game, unit, this.plagueWardUnitType, unit.getX(), unit.getY(), this.plagueWardDuration);
	}

	/**
	 * A plague ward is an untouchable cloud: it cannot be selected, targeted or hurt, and fades after its time.
	 */
	public static CUnit spawnPlagueWard(final CSimulation game, final CUnit owner, final War3ID wardType,
			final float x, final float y, final float duration) {
		final CUnit ward = game.createUnit(wardType, owner.getPlayerIndex(), x, y, 225);
		if (ward == null) {
			return null;
		}
		ward.add(game, new CBuffTimedLife(game.getHandleIdAllocator().createId(), TIMED_LIFE_BUFF_ID, duration,
				false));
		ward.addStateModBuff(new StateModBuff(StateModBuffType.INVULNERABLE, 1));
		ward.computeUnitState(game, StateModBuffType.INVULNERABLE);
		ward.addStateModBuff(new StateModBuff(StateModBuffType.MAGIC_IMMUNE, 1));
		ward.computeUnitState(game, StateModBuffType.MAGIC_IMMUNE);
		ward.addStateModBuff(new StateModBuff(StateModBuffType.UNENUMERABLE, 1));
		ward.computeUnitState(game, StateModBuffType.UNENUMERABLE);
		return ward;
	}
}
