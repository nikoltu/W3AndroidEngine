package com.etheller.warsmash.parsers.jass;

import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassType;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.JassRandom;
import com.etheller.warsmash.util.RawcodeUtils;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.ItemUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructableType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.COrderButton.JassOrderButtonType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.COrderButton;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CVersion;

/**
 * Natives for object ids and names, handle ids, bit operations, versions, rawcodes and random numbers.
 */
final class ScriptNatives extends CommonNatives {
	ScriptNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		final HandleJassType versionType = type("version");
		final HandleJassType orderbuttontypeType = type("orderbuttontype");

		jassProgramVisitor.getJassNativeManager().createNative("ConvertOrderButtonType",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(orderbuttontypeType, COrderButton.JassOrderButtonType.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitId", (arguments, globalScope, triggerScope) -> {
			final String idString = arguments.get(0).visit(StringJassValueVisitor.getInstance());
			final CUnitType unitTypeTmp = this.simulation.getUnitData()
					.getUnitTypeByJassLegacyName(idString);
			return IntegerJassValue.of((unitTypeTmp == null) ? 0 : unitTypeTmp.getTypeId().getValue());
		});
		jassProgramVisitor.getJassNativeManager().createNative("UnitId2String",
				(arguments, globalScope, triggerScope) -> {
					final Integer id = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final War3ID war3id = new War3ID(id);
					final CUnitType unitTypeTmp = this.simulation.getUnitData()
							.getUnitType(war3id);
					return unitTypeTmp == null ? JassType.STRING.getNullValue()
							: new StringJassValue(unitTypeTmp.getLegacyName());
				});
		jassProgramVisitor.getJassNativeManager().createNative("AbilityId",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue.of(0);
				});
		jassProgramVisitor.getJassNativeManager().createNative("AbilityId2String",
				(arguments, globalScope, triggerScope) -> {
					return new StringJassValue("");
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetObjectName",
				(arguments, globalScope, triggerScope) -> {
					final Integer id = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final War3ID war3id = new War3ID(id);
					final CUnitType unitTypeTmp = this.simulation.getUnitData()
							.getUnitType(war3id);
					if (unitTypeTmp != null) {
						return new StringJassValue(unitTypeTmp.getName());
					}
					// TODO for now this looks in the ability editor data, not the fast symbol table
					// layer on top, because the layer on top forgot to have a name value...
					final GameObject abilityEditorData = war3MapViewer.getAllObjectData().getAbilities()
							.get(war3id);
					if (abilityEditorData != null) {
						return new StringJassValue(abilityEditorData.getName());
					}
					final ItemUI itemUI = war3MapViewer.getAbilityDataUI().getItemUI(war3id);
					if (itemUI != null) {
						return new StringJassValue(itemUI.getName());
					}
					final CDestructableType destructableTypeTmp = this.simulation
							.getDestructableData().getUnitType(war3id);
					if (destructableTypeTmp != null) {
						return new StringJassValue(destructableTypeTmp.getName());
					}
					return new StringJassValue("");
				});
		// Bit Operations
		jassProgramVisitor.getJassNativeManager().createNative("BlzBitOr",
				(arguments, globalScope, triggerScope) -> {
					final Integer x = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final Integer y = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());

					return IntegerJassValue.of(x | y);
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzBitAnd",
				(arguments, globalScope, triggerScope) -> {
					final Integer x = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final Integer y = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());

					return IntegerJassValue.of(x & y);
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzBitXor",
				(arguments, globalScope, triggerScope) -> {
					final Integer x = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final Integer y = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());

					return IntegerJassValue.of(x ^ y);
				});
		jassProgramVisitor.getJassNativeManager().createNative("VersionGet",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(versionType,
							CVersion.VALUES[this.simulation.getMapGameVersion()]);
				});
		jassProgramVisitor.getJassNativeManager().createNative("VersionCompatible",
				(arguments, globalScope, triggerScope) -> {
					final CVersion version = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((version != null)
							&& (version.ordinal() <= this.simulation.getMapGameVersion()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("VersionSupported",
				(arguments, globalScope, triggerScope) -> {
					final CVersion version = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of(version != null);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetHandleId",
				(arguments, globalScope, triggerScope) -> {
					final Object whichHandle = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichHandle == null) {
						return IntegerJassValue.ZERO;
					}
					return IntegerJassValue.of(this.simulation.getHandleIdAllocator().handleIdOf(whichHandle));
				});
		jassProgramVisitor.getJassNativeManager().createNative("WarsmashGetRawcode2String",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return new StringJassValue(new War3ID(rawcode).toString());
				});
		jassProgramVisitor.getJassNativeManager().createNative("Rawcode2String",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return StringJassValue.of(RawcodeUtils.toString(rawcode));
				});
		final JassFunction string2Rawcode = (arguments, globalScope, triggerScope) -> {
			final String rawcodeStr = arguments.get(0).visit(StringJassValueVisitor.getInstance());
			return IntegerJassValue.of(RawcodeUtils.toInt(rawcodeStr));
		};
		jassProgramVisitor.getJassNativeManager().createNative("String2Rawcode", string2Rawcode);
		jassProgramVisitor.getJassNativeManager().createNative("FourCC", string2Rawcode);
		registerRandomNatives(jassProgramVisitor, this.simulation);
	}

	private static void registerRandomNatives(final JassProgram jassProgramVisitor, final CSimulation simulation) {
		jassProgramVisitor.getJassNativeManager().createNative("GetRandomReal",
				(arguments, globalScope, triggerScope) -> {
					final float lowBound = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float highBound = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					return RealJassValue.of(JassRandom.nextReal(simulation.getSeededRandom(), lowBound, highBound));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetRandomInt",
				(arguments, globalScope, triggerScope) -> {
					final int lowBound = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final int highBound = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					return IntegerJassValue.of(JassRandom.nextInt(simulation.getSeededRandom(), lowBound, highBound));
				});
	}
}
