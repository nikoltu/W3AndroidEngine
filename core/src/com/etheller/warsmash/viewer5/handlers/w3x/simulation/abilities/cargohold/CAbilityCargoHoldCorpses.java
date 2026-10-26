package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold;

import java.util.EnumSet;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;

/**
 * Meat Wagon cargo hold: carries corpses that do not decay while inside; they come out as fresh corpses beside
 * the wagon when dropped or when the wagon dies.
 */
public class CAbilityCargoHoldCorpses extends CAbilityCargoHold {
	private static final float DROP_SPREAD = 40f;

	public CAbilityCargoHoldCorpses(final int handleId, final War3ID code, final War3ID alias,
			final int cargoCapacity, final float duration, final float castRange,
			final EnumSet<CTargetType> targetsAllowed) {
		super(handleId, code, alias, cargoCapacity, duration, castRange, targetsAllowed);
	}

	public boolean isFull() {
		return getCargoCount() >= getCargoCapacity();
	}

	public void addCorpse(final CSimulation game, final CUnit holder, final CUnit corpse) {
		corpse.holdCorpse(game);
		addUnit(holder, corpse);
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		for (int i = 0; i < getCargoCount(); i++) {
			getUnit(i).resetCorpseDecay(game);
		}
	}

	@Override
	public void dropUnitByIndex(final CSimulation game, final CUnit caster, final int targetIndex) {
		game.unitSoundEffectEvent(caster, getAlias());
		releaseCorpse(game, caster, removeUnitAtIndex(caster, targetIndex), 0, 1);
	}

	@Override
	public void unloadAllInstant(final CSimulation game, final CUnit caster) {
		if (!isEmpty()) {
			game.unitSoundEffectEvent(caster, getAlias());
			final int cargoCount = getCargoCount();
			for (int i = 0; i < cargoCount; i++) {
				releaseCorpse(game, caster, removeUnitAtIndex(caster, cargoCount - i - 1), i, cargoCount);
			}
		}
	}

	private static void releaseCorpse(final CSimulation game, final CUnit caster, final CUnit corpse,
			final int index, final int count) {
		float x = caster.getX();
		float y = caster.getY();
		if (count > 1) {
			final double angle = ((Math.PI * 2) * index) / count;
			x += (float) (Math.cos(angle) * DROP_SPREAD);
			y += (float) (Math.sin(angle) * DROP_SPREAD);
		}
		corpse.releaseCorpse(game, x, y);
	}
}
