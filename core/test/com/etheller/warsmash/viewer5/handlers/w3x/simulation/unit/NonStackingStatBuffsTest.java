package com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Taking away a bonus the unit does not hold (an item's bonus a second time) is reported but does not throw, and
 * leaves the bonuses it does hold as they are.
 */
class NonStackingStatBuffsTest {
	private static final String KEY = "";

	@Test
	void removingABuffThatIsNotThereKeepsTheOthers() {
		final NonStackingStatBuffs buffs = new NonStackingStatBuffs();
		final NonStackingStatBuff held = new NonStackingStatBuff(NonStackingStatBuffType.MVSPD, KEY, 60);
		final NonStackingStatBuff other = new NonStackingStatBuff(NonStackingStatBuffType.MVSPD, KEY, 60);
		buffs.add(held);

		assertDoesNotThrow(() -> buffs.remove(other, "unit"));
		assertEquals(60, buffs.sum(NonStackingStatBuffType.MVSPD, 0));

		buffs.remove(held, "unit");
		assertEquals(0, buffs.sum(NonStackingStatBuffType.MVSPD, 0));
		assertDoesNotThrow(() -> buffs.remove(held, "unit"));
	}

	@Test
	void removingABuffOfAStatTheUnitNeverHadDoesNotThrow() {
		final NonStackingStatBuffs buffs = new NonStackingStatBuffs();
		assertDoesNotThrow(() -> buffs.remove(new NonStackingStatBuff(NonStackingStatBuffType.ATKSPD, KEY, 0.15f), "unit"));
		assertEquals(0, buffs.sum(NonStackingStatBuffType.ATKSPD, 0));
	}

	@Test
	void removingAnAttackBuffOfBothKindsTakesOffTheMeleeAndRangedOnes() {
		final NonStackingStatBuffs buffs = new NonStackingStatBuffs();
		final NonStackingStatBuff all = new NonStackingStatBuff(NonStackingStatBuffType.ALLATK, KEY, 5);
		buffs.add(all);
		assertEquals(5, buffs.sum(NonStackingStatBuffType.MELEEATK, 0));
		assertEquals(5, buffs.sum(NonStackingStatBuffType.RNGDATK, 0));

		buffs.remove(all, "unit");
		assertEquals(0, buffs.sum(NonStackingStatBuffType.MELEEATK, 0));
		assertEquals(0, buffs.sum(NonStackingStatBuffType.RNGDATK, 0));
	}
}
