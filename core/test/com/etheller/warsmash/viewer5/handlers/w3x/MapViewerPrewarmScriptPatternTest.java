package com.etheller.warsmash.viewer5.handlers.w3x;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.War3ID;

class MapViewerPrewarmScriptPatternTest {
	private static Set<String> effects(final String script) {
		final Set<String> found = new LinkedHashSet<>();
		MapViewerPrewarm.addMatches(found, MapViewerPrewarm.SCRIPT_EFFECT_MODEL, script);
		return found;
	}

	@Test
	void effectModelsAreFoundOnTheLineOfTheCall() {
		final Set<String> found = effects("call AddSpecialEffect(\"Abilities\\\\Spells\\\\A.mdl\", 0, 0)\n"
				+ "set e = AddSpecialEffectTarget(\"x\", u, \"overhead\")\n"
				+ "set e = AddSpecialEffectTarget(\"Objects\\\\B.MDX\", u, \"origin\")\n"
				+ "call AddSpecialEffect(\n\"Objects\\\\C.mdl\", 0, 0)\n");
		assertEquals(new ArrayList<>(Arrays.asList("Abilities\\Spells\\A.mdl", "Objects\\B.MDX")),
				new ArrayList<>(found));
	}

	@Test
	void aVeryLongStringDoesNotOverflowTheStack() {
		final StringBuilder longText = new StringBuilder(500_000);
		for (int i = 0; i < 500_000; i++) {
			longText.append(i % 7 == 0 ? "\\\\n" : "a");
		}
		final String script = "call AddSpecialEffect(\"" + longText + "\", 0, 0)\n" + "call AddSpecialEffect(\""
				+ longText + ".mdl\", 0, 0)\n";
		assertEquals(1, effects(script).size());
	}

	@Test
	void createdTypesAreCountedAndTheMostOftenNamedComeFirst() {
		final String script = "call CreateUnit(Player(0), 'hfoo', 0, 0, 0)\n"
				+ "set u = CreateNUnitsAtLoc(2, 'hkni', Player(1), loc, 0.0)\n"
				+ "call UnitPoolAddUnitType(pool, 'hkni', 1)\n"
				+ "set u = BlzCreateUnitWithSkin(Player(0), 'hrif', 0, 0, 0, 'hfoo')\n"
				+ "call CreateUnitByName(Player(0), \"hfoo\", 0, 0, 0)\n";
		final List<War3ID> found = MapViewerPrewarm.createdTypes(MapViewerPrewarm.SCRIPT_UNIT_CREATION, script);
		assertEquals(Arrays.asList(War3ID.fromString("hkni"), War3ID.fromString("hfoo"), War3ID.fromString("hrif")),
				found);
	}

	@Test
	void obfuscatedScriptsWriteDecimalRawCodesAndEndLinesWithCarriageReturns() {
		final String script = "call CreateUnit(Player(15),1211117657,0,0,270)\rcall SetUnitX(u,1000000000)\r"
				+ "set t=CreateUnit(Player(0),v,0,0,0)\rset u=CreateUnit(Player(0),1751543663,0,0,0)\r";
		assertEquals(Arrays.asList(new War3ID(1211117657), new War3ID(1751543663)),
				MapViewerPrewarm.createdTypes(MapViewerPrewarm.SCRIPT_UNIT_CREATION, script));
	}

	@Test
	void itemsAndDestructablesAreToldApart() {
		final String script = "call CreateItem('ratf', 0, 0)\ncall CreateDestructable('LTlt', 0, 0, 0, 1, 0)\n"
				+ "call CreateUnit(Player(0), 'hfoo', 0, 0, 0)\n";
		assertEquals(Arrays.asList(War3ID.fromString("ratf")),
				MapViewerPrewarm.createdTypes(MapViewerPrewarm.SCRIPT_ITEM_CREATION, script));
		assertEquals(Arrays.asList(War3ID.fromString("LTlt")),
				MapViewerPrewarm.createdTypes(MapViewerPrewarm.SCRIPT_DESTRUCTABLE_CREATION, script));
	}

	@Test
	void destructableVariationsAreReadFromTheArgumentsOfEachForm() {
		final String script = "call CreateDestructable('LTlt', 0, 0, 0, 1, 3)\n"
				+ "set d = BlzCreateDestructableWithSkinPitchRoll('LTcr', -2752.0, 320.0, 31.0, 0.0, 0.0, 1.2, 1, 'LTcr')\n"
				+ "set d = CreateDestructableZ('DTg5', GetX(u), Cos(1.0), 5.0, 270.0, 0.9, 2)\n"
				+ "set d = CreateDestructable('LTlt', 0, 0, 0, 1, GetRandomInt(0, 4))\n"
				+ "set d = CreateDestructable('LTlt', 0, 0, 0, 1, 7)\n";
		final java.util.Map<War3ID, Set<Integer>> found = MapViewerPrewarm.createdDestructableVariations(script);
		assertEquals(new LinkedHashSet<>(Arrays.asList(3, 7)), found.get(War3ID.fromString("LTlt")));
		assertEquals(new LinkedHashSet<>(Arrays.asList(1)), found.get(War3ID.fromString("LTcr")));
		assertEquals(new LinkedHashSet<>(Arrays.asList(2)), found.get(War3ID.fromString("DTg5")));
	}

	@Test
	void theBudgetGrowsWithTheMemoryOfTheDevice() {
		final long mb = 1L << 20;
		assertEquals(16 * mb, MapViewerPrewarm.scriptCreationBudget(3 * 1024, 512 * mb));
		assertEquals(32 * mb, MapViewerPrewarm.scriptCreationBudget(5 * 1024, 2048 * mb));
		assertEquals(64 * mb, MapViewerPrewarm.scriptCreationBudget(6 * 1024, 1024 * mb));
		assertEquals(64 * mb, MapViewerPrewarm.scriptCreationBudget(7 * 1024, 512 * mb));
		assertEquals(128 * mb, MapViewerPrewarm.scriptCreationBudget(8 * 1024, 1024 * mb));
		assertEquals(128 * mb, MapViewerPrewarm.scriptCreationBudget(12 * 1024, 2048 * mb));
		assertEquals((384 * mb) / 4, MapViewerPrewarm.scriptCreationBudget(12 * 1024, 384 * mb));
		assertEquals((48 * mb) / 4, MapViewerPrewarm.scriptCreationBudget(3 * 1024, 48 * mb));
	}

	@Test
	void typesEarlierGamesMadeMayTakeMoreThanTheBudgetUpToHalfTheHeap() {
		final long mb = 1L << 20;
		assertEquals(384 * mb, MapViewerPrewarm.learnedBudget(64 * mb, 2048 * mb));
		assertEquals(256 * mb, MapViewerPrewarm.learnedBudget(64 * mb, 512 * mb));
		assertEquals(96 * mb, MapViewerPrewarm.learnedBudget(64 * mb, 192 * mb));
	}

	@Test
	void aDesktopTakesTheSmallerOfAGigabyteAndHalfItsHeap() {
		final long mb = 1L << 20;
		assertEquals(1024 * mb, MapViewerPrewarm.scriptCreationBudget(0, 4096 * mb));
		assertEquals(256 * mb, MapViewerPrewarm.scriptCreationBudget(0, 512 * mb));
	}

	@Test
	void everyRawCodeOfAScriptIsFound() {
		final List<War3ID> found = MapViewerPrewarm
				.namedTypes("set udg_a[1] = 'ncen'\nset udg_a[2] = 'nowl'\nif GetUnitTypeId(u) == 'ncen' then\n");
		assertEquals(Arrays.asList(War3ID.fromString("ncen"), War3ID.fromString("nowl")), found);
	}

	@Test
	void rawCodesAreFoundQuotedAndAsIntegers() {
		// 1211117642 is H00J; 1000000000 and a real number are not codes
		final String script = "set a = 'ncen'\n" + "set b = 1211117642\n" + "set c = 1000000000\n"
				+ "set d = 1211117642.5\n" + "set e = 1211117642\n";
		assertEquals(Arrays.asList(War3ID.fromString("H00J"), War3ID.fromString("ncen")), MapViewerPrewarm.rawCodes(script));
	}
}
