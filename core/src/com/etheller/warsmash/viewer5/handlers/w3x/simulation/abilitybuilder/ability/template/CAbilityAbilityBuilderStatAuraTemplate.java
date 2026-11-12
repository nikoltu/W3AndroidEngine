package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.ABGenericAuraBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser.template.DataFieldLetter;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser.template.MeleeRangeTargetOverride;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser.template.StatBuffFromDataField;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.types.impl.CAbilityTypeAbilityBuilderLevelData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponent;

public class CAbilityAbilityBuilderStatAuraTemplate extends AbstractCAbilityAbilityBuilderAuraTemplate {

	private War3ID buffId;
	private CBuff buff;

	private SimulationRenderComponent fx;

	private boolean targetMelee = false;
	private boolean targetRange = false;
	private MeleeRangeTargetOverride rangeOverride;

	private String auraStackingKey;

	private List<StatBuffFromDataField> statBuffDataFields;

	private boolean leveled;
	private float[] dataBonuses;

	public CAbilityAbilityBuilderStatAuraTemplate(int handleId, War3ID code, War3ID alias,
			List<CAbilityTypeAbilityBuilderLevelData> levelData, Map<String, Object> localStore,
			List<StatBuffFromDataField> statBuffDataFields, MeleeRangeTargetOverride meleeRangeTargetOverride) {
		super(handleId, code, alias, levelData, localStore);
		this.rangeOverride = meleeRangeTargetOverride;
		this.targetsAllowed = levelData.get(getLevel() - 1).getTargetsAllowed();
		this.range = levelData.get(getLevel() - 1).getArea();
		this.auraStackingKey = "";

		this.statBuffDataFields = new ArrayList<>();
		for (StatBuffFromDataField statBuff : statBuffDataFields) {
			this.statBuffDataFields.add(new StatBuffFromDataField(statBuff));
		}

		if (!this.levelData.get(getLevel() - 1).getBuffs().isEmpty()) {
			buffId = this.levelData.get(getLevel() - 1).getBuffs().get(0);
			this.auraStackingKey = buffId.asStringValue();
		}

		for (StatBuffFromDataField statBuff : this.statBuffDataFields) {
			createNewBuffs(statBuff);
		}

		if (this.rangeOverride != null) {
			this.targetMelee = this.rangeOverride.isTargetMelee();
			this.targetRange = this.rangeOverride.isTargetRange();
		}

		this.leveled = levelData.size() > 1;
	}

	private float getDataValue(final StatBuffFromDataField statBuff) {
		final int index = statBuff.getDataField().getIndex();
		float value;
		try {
			value = Float.parseFloat(levelData.get(getLevel() - 1).getData().get(index));
		} catch (NumberFormatException exc) {
			value = 0;
		}
		if (this.dataBonuses != null && index < this.dataBonuses.length) {
			value += this.dataBonuses[index];
		}
		return value;
	}

	/**
	 * Research bonus on top of a data field (raud), kept across level changes.
	 */
	public void addDataBonus(final CSimulation game, final CUnit unit, final int dataIndex, final float bonus) {
		if (this.dataBonuses == null) {
			this.dataBonuses = new float[DataFieldLetter.values().length];
		}
		this.dataBonuses[dataIndex] += bonus;
		if (this.auraGroup != null) {
			setLevel(game, unit, getLevel());
		}
		else {
			for (final StatBuffFromDataField statBuff : this.statBuffDataFields) {
				statBuff.getBuff().setValue(getDataValue(statBuff));
				if (statBuff.getSecondAtkBuff() != null) {
					statBuff.getSecondAtkBuff().setValue(getDataValue(statBuff));
				}
			}
		}
	}

	private void removeExistingBuffs(CSimulation game, StatBuffFromDataField statBuff) {
		for (CUnit unit : auraGroup) {
			unit.removeNonStackingStatBuff(game, statBuff.getBuff());
			if (statBuff.getSecondAtkBuff() != null) {
				unit.removeNonStackingStatBuff(game, statBuff.getSecondAtkBuff());
			}
		}
	}

	private void addNewBuffs(CSimulation game, StatBuffFromDataField statBuff) {
		for (CUnit unit : auraGroup) {
			unit.addNonStackingStatBuff(game, statBuff.getBuff());
			if (statBuff.getSecondAtkBuff() != null) {
				unit.addNonStackingStatBuff(game, statBuff.getSecondAtkBuff());
			}
		}
	}

	private void createNewBuffs(StatBuffFromDataField parsedBuff) {
		NonStackingStatBuffType type = parsedBuff.convertToNonStackingType(levelData.get(getLevel() - 1));
		NonStackingStatBuff newBuff = null;
		switch (type) {
		case RNGDATK:
		case RNGDATKPCT:
			newBuff = new NonStackingStatBuff(type, this.auraStackingKey, getDataValue(parsedBuff));
			parsedBuff.setBuff(newBuff);
			targetRange = true;
			break;
		case MELEEATK:
		case MELEEATKPCT:
			newBuff = new NonStackingStatBuff(type, this.auraStackingKey, getDataValue(parsedBuff));
			parsedBuff.setBuff(newBuff);
			targetMelee = true;
			break;
		case ALLATK:
			newBuff = new NonStackingStatBuff(NonStackingStatBuffType.MELEEATK, this.auraStackingKey, getDataValue(parsedBuff));
			parsedBuff.setBuff(newBuff);
			newBuff = new NonStackingStatBuff(NonStackingStatBuffType.RNGDATK, this.auraStackingKey, getDataValue(parsedBuff));
			parsedBuff.setSecondAtkBuff(newBuff);
			targetMelee = true;
			targetRange = true;
			break;
		case ALLATKPCT:
			newBuff = new NonStackingStatBuff(NonStackingStatBuffType.MELEEATKPCT, this.auraStackingKey, getDataValue(parsedBuff));
			parsedBuff.setBuff(newBuff);
			newBuff = new NonStackingStatBuff(NonStackingStatBuffType.RNGDATKPCT, this.auraStackingKey, getDataValue(parsedBuff));
			parsedBuff.setSecondAtkBuff(newBuff);
			targetMelee = true;
			targetRange = true;
			break;
		default:
			newBuff = new NonStackingStatBuff(type, this.auraStackingKey, getDataValue(parsedBuff));
			parsedBuff.setBuff(newBuff);
			targetMelee = true;
			targetRange = true;
		}
	}

	@Override
	public void setLevel(CSimulation game, CUnit unit, int level) {
		super.setLevel(game, unit, level);
		if (this.buff != null) {
			this.buff.setLevel(game, unit, level);
		}
		targetsAllowed = levelData.get(getLevel() - 1).getTargetsAllowed();
		range = levelData.get(getLevel() - 1).getArea();
		this.targetMelee = false;
		this.targetRange = false;
		for (StatBuffFromDataField statBuff : this.statBuffDataFields) {
			NonStackingStatBuffType type = statBuff.convertToNonStackingType(levelData.get(getLevel() - 1));
			if (type == null) {
				continue;
			}
			switch (type) {
			case ALLATK:
				if (statBuff.getSecondAtkBuff() == null) {
					removeExistingBuffs(game, statBuff);
					createNewBuffs(statBuff);
					addNewBuffs(game, statBuff);
				} else if (statBuff.getBuff().getBuffType() != NonStackingStatBuffType.MELEEATK) {
					removeExistingBuffs(game, statBuff);
					createNewBuffs(statBuff);
					addNewBuffs(game, statBuff);
				} else {
					statBuff.getBuff().setValue(getDataValue(statBuff));
					statBuff.getSecondAtkBuff().setValue(getDataValue(statBuff));
					targetMelee = true;
					targetRange = true;
				}
				break;
			case ALLATKPCT:
				if (statBuff.getSecondAtkBuff() == null) {
					removeExistingBuffs(game, statBuff);
					createNewBuffs(statBuff);
					addNewBuffs(game, statBuff);
				} else if (statBuff.getBuff().getBuffType() != NonStackingStatBuffType.MELEEATKPCT) {
					removeExistingBuffs(game, statBuff);
					createNewBuffs(statBuff);
					addNewBuffs(game, statBuff);
				} else {
					statBuff.getBuff().setValue(getDataValue(statBuff));
					statBuff.getSecondAtkBuff().setValue(getDataValue(statBuff));
					targetMelee = true;
					targetRange = true;
				}
				break;
			default:
				if (statBuff.getSecondAtkBuff() != null) {
					removeExistingBuffs(game, statBuff);
					createNewBuffs(statBuff);
					addNewBuffs(game, statBuff);
				} else if (type != statBuff.getBuff().getBuffType()) {
					removeExistingBuffs(game, statBuff);
					createNewBuffs(statBuff);
					addNewBuffs(game, statBuff);
				} else {
					statBuff.getBuff().setValue(getDataValue(statBuff));
					if (type == NonStackingStatBuffType.MELEEATK || type == NonStackingStatBuffType.MELEEATKPCT) {
						targetMelee = true;
					} else if (type == NonStackingStatBuffType.RNGDATK || type == NonStackingStatBuffType.RNGDATKPCT) {
						targetRange = true;
					} else {
						targetMelee = true;
						targetRange = true;
					}
				}
			}
			for (CUnit unitA : auraGroup) {
				if (statBuff.getBuff().getBuffType().isHeroStat()) {
					unitA.computeDerivedHeroFields(game, statBuff.getBuff().getBuffType());
				} else {
					unitA.computeDerivedFields(statBuff.getBuff().getBuffType());
				}
				if (statBuff.getSecondAtkBuff() != null) {
					unitA.computeDerivedFields(statBuff.getSecondAtkBuff().getBuffType());
				}
			}
		}
		if (this.rangeOverride != null) {
			this.targetMelee = this.rangeOverride.isTargetMelee();
			this.targetRange = this.rangeOverride.isTargetRange();
		}
	}

	@Override
	protected void onAuraResumed(CSimulation game, CUnit unit) {
		this.fx = game.createPersistentSpellEffectOnUnit(unit, this.getAlias(), CEffectType.TARGET);
	}

	@Override
	protected void beforeAuraStarts(CSimulation game, CUnit unit) {
		if (this.buffId != null) {
			this.buff = new ABGenericAuraBuff(game.getHandleIdAllocator().createId(), this.buffId,
					localStore, this, unit,
					this.leveled, true);
		}
		game.getAbilityData().createAbility(getAlias(), game.getHandleIdAllocator().createId());
	}

	@Override
	protected void onAuraStarted(CSimulation game, CUnit unit) {
		this.fx = game.createPersistentSpellEffectOnUnit(unit, this.getAlias(), CEffectType.TARGET);
	}

	@Override
	public void onRemove(CSimulation game, CUnit unit) {
		super.onRemove(game, unit);
		this.fx.remove();
	}

	@Override
	protected boolean canJoin(final CUnit enumUnit) {
		// Measured: an aura for melee and ranged alike also reaches units without an attack (Shade, Devotion Aura).
		if (targetMelee && targetRange) {
			return true;
		}
		return (targetMelee && enumUnit.isUnitType(CUnitTypeJass.MELEE_ATTACKER))
				|| (targetRange && enumUnit.isUnitType(CUnitTypeJass.RANGED_ATTACKER));
	}

	@Override
	public void addUnitToAura(CSimulation game, CUnit unit) {
		for (StatBuffFromDataField statBuff : this.statBuffDataFields) {
			unit.addNonStackingStatBuff(game, statBuff.getBuff());
			if (statBuff.getSecondAtkBuff() != null) {
				unit.addNonStackingStatBuff(game, statBuff.getSecondAtkBuff());
			}
		}
		if (buff != null) {
			unit.addNonStackingDisplayBuff(game, auraStackingKey, buff);
		}
		auraGroup.add(unit);
	}

	@Override
	public void removeUnitFromAura(CSimulation game, CUnit unit) {
		if (buff != null) {
			unit.removeNonStackingDisplayBuff(game, auraStackingKey, buff);
		}
		for (StatBuffFromDataField statBuff : this.statBuffDataFields) {
			unit.removeNonStackingStatBuff(game, statBuff.getBuff());
			if (statBuff.getSecondAtkBuff() != null) {
				unit.removeNonStackingStatBuff(game, statBuff.getSecondAtkBuff());
			}
		}
		auraGroup.remove(unit);
	}
}
