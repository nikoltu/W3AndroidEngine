package com.etheller.warsmash.parsers.jass;

import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassType;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.JassTypeGettingValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CHashtable;
import java.util.List;

/**
 * Hashtable natives.
 */
final class HashtableNatives extends CommonNatives {
	HashtableNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		final HandleJassType agentType = type("agent");
		final HandleJassType eventType = type("event");
		final HandleJassType playerType = type("player");
		final HandleJassType widgetType = type("widget");
		final HandleJassType unitType = type("unit");
		final HandleJassType destructableType = type("destructable");
		final HandleJassType itemType = type("item");
		final HandleJassType abilityType = type("ability");
		final HandleJassType forceType = type("force");
		final HandleJassType groupType = type("group");
		final HandleJassType triggerType = type("trigger");
		final HandleJassType triggerconditionType = type("triggercondition");
		final HandleJassType triggeractionType = type("triggeraction");
		final HandleJassType timerType = type("timer");
		final HandleJassType locationType = type("location");
		final HandleJassType regionType = type("region");
		final HandleJassType rectType = type("rect");
		final HandleJassType boolexprType = type("boolexpr");
		final HandleJassType soundType = type("sound");
		final HandleJassType unitpoolType = type("unitpool");
		final HandleJassType itempoolType = type("itempool");
		final HandleJassType effectType = type("effect");
		final HandleJassType fogstateType = type("fogstate");
		final HandleJassType fogmodifierType = type("fogmodifier");
		final HandleJassType dialogType = type("dialog");
		final HandleJassType buttonType = type("button");
		final HandleJassType questType = type("quest");
		final HandleJassType questitemType = type("questitem");
		final HandleJassType defeatconditionType = type("defeatcondition");
		final HandleJassType timerdialogType = type("timerdialog");
		final HandleJassType leaderboardType = type("leaderboard");
		final HandleJassType multiboardType = type("multiboard");
		final HandleJassType multiboarditemType = type("multiboarditem");
		final HandleJassType trackableType = type("trackable");
		final HandleJassType texttagType = type("texttag");
		final HandleJassType lightningType = type("lightning");
		final HandleJassType imageType = type("image");
		final HandleJassType ubersplatType = type("ubersplat");
		final HandleJassType hashtableType = type("hashtable");

		// Patch 1.23+ crap
		// jassProgramVisitor.getJassNativeManager().createNative("InitHashtable", new
		// JassFunction() {
		// @Override
		// public JassValue call(final List<JassValue> arguments, final GlobalScope
		// globalScope,
		// final TriggerExecutionScope triggerScope) {
		// return new HandleJassValue(hashtableType, new CHashtable());
		// }
		// });
		jassProgramVisitor.getJassNativeManager().createNative("InitHashtable",
				(arguments, globalScope, triggerScope) -> {
					return newHandle(hashtableType, new CHashtable());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SaveInteger", new SaveHashtableValueFunc(CHashtable.INTEGER));
		jassProgramVisitor.getJassNativeManager().createNative("SaveReal", new SaveHashtableValueFunc(CHashtable.REAL));
		jassProgramVisitor.getJassNativeManager().createNative("SaveBoolean", new SaveHashtableValueFunc(CHashtable.BOOLEAN));
		jassProgramVisitor.getJassNativeManager().createNative("SaveStr", new SaveHashtableValueFunc(CHashtable.STRING));
		jassProgramVisitor.getJassNativeManager().createNative("SavePlayerHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveWidgetHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveDestructableHandle",
				new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveItemHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveFrameHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveUnitHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveAbilityHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveTimerHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveTriggerHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveTriggerConditionHandle",
				new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveTriggerActionHandle",
				new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveTriggerEventHandle",
				new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveForceHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveGroupHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveLocationHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveRectHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveBooleanExprHandle",
				new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveSoundHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveEffectHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveUnitPoolHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveItemPoolHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveQuestHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveQuestItemHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveDefeatConditionHandle",
				new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveTimerDialogHandle",
				new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveLeaderboardHandle",
				new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveMultiboardHandle",
				new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveMultiboardItemHandle",
				new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveTrackableHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveDialogHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveButtonHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveTextTagHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveLightningHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveImageHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveUbersplatHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveRegionHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveFogStateHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveFogModifierHandle",
				new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveAgentHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("SaveHashtableHandle", new SaveHashtableValueFunc());
		jassProgramVisitor.getJassNativeManager().createNative("LoadInteger",
				new LoadHashtableValueFunc(IntegerJassValue.ZERO));
		jassProgramVisitor.getJassNativeManager().createNative("LoadReal",
				new LoadHashtableValueFunc(RealJassValue.ZERO));
		jassProgramVisitor.getJassNativeManager().createNative("LoadBoolean",
				new LoadHashtableValueFunc(BooleanJassValue.FALSE));
		jassProgramVisitor.getJassNativeManager().createNative("LoadStr",
				new LoadHashtableValueFunc(StringJassValue.EMPTY_STRING));
		jassProgramVisitor.getJassNativeManager().createNative("LoadPlayerHandle",
				new LoadHashtableValueFunc(playerType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadWidgetHandle",
				new LoadHashtableValueFunc(widgetType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadDestructableHandle",
				new LoadHashtableValueFunc(destructableType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadItemHandle",
				new LoadHashtableValueFunc(itemType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadFrameHandle",
				new LoadHashtableValueFunc(type("framehandle").getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadUnitHandle",
				new LoadHashtableValueFunc(unitType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadAbilityHandle",
				new LoadHashtableValueFunc(abilityType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadTimerHandle",
				new LoadHashtableValueFunc(timerType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadTriggerHandle",
				new LoadHashtableValueFunc(triggerType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadTriggerConditionHandle",
				new LoadHashtableValueFunc(triggerconditionType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadTriggerActionHandle",
				new LoadHashtableValueFunc(triggeractionType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadTriggerEventHandle",
				new LoadHashtableValueFunc(eventType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadForceHandle",
				new LoadHashtableValueFunc(forceType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadGroupHandle",
				new LoadHashtableValueFunc(groupType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadLocationHandle",
				new LoadHashtableValueFunc(locationType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadRectHandle",
				new LoadHashtableValueFunc(rectType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadBooleanExprHandle",
				new LoadHashtableValueFunc(boolexprType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadSoundHandle",
				new LoadHashtableValueFunc(soundType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadEffectHandle",
				new LoadHashtableValueFunc(effectType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadUnitPoolHandle",
				new LoadHashtableValueFunc(unitpoolType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadItemPoolHandle",
				new LoadHashtableValueFunc(itempoolType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadQuestHandle",
				new LoadHashtableValueFunc(questType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadQuestItemHandle",
				new LoadHashtableValueFunc(questitemType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadDefeatConditionHandle",
				new LoadHashtableValueFunc(defeatconditionType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadTimerDialogHandle",
				new LoadHashtableValueFunc(timerdialogType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadLeaderboardHandle",
				new LoadHashtableValueFunc(leaderboardType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadMultiboardHandle",
				new LoadHashtableValueFunc(multiboardType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadMultiboardItemHandle",
				new LoadHashtableValueFunc(multiboarditemType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadTrackableHandle",
				new LoadHashtableValueFunc(trackableType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadDialogHandle",
				new LoadHashtableValueFunc(dialogType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadButtonHandle",
				new LoadHashtableValueFunc(buttonType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadTextTagHandle",
				new LoadHashtableValueFunc(texttagType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadLightningHandle",
				new LoadHashtableValueFunc(lightningType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadImageHandle",
				new LoadHashtableValueFunc(imageType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadUbersplatHandle",
				new LoadHashtableValueFunc(ubersplatType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadRegionHandle",
				new LoadHashtableValueFunc(regionType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadFogStateHandle",
				new LoadHashtableValueFunc(fogstateType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadFogModifierHandle",
				new LoadHashtableValueFunc(fogmodifierType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadHashtableHandle",
				new LoadHashtableValueFunc(hashtableType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("LoadAgentHandle",
				new LoadHashtableValueFunc(agentType.getNullValue()));
		jassProgramVisitor.getJassNativeManager().createNative("HaveSavedInteger",
				new HaveSavedHashtableValueFunc(JassType.INTEGER));
		jassProgramVisitor.getJassNativeManager().createNative("HaveSavedReal",
				new HaveSavedHashtableValueFunc(JassType.REAL));
		jassProgramVisitor.getJassNativeManager().createNative("HaveSavedBoolean",
				new HaveSavedHashtableValueFunc(JassType.BOOLEAN));
		jassProgramVisitor.getJassNativeManager().createNative("HaveSavedString",
				new HaveSavedHashtableValueFunc(JassType.STRING));
		jassProgramVisitor.getJassNativeManager().createNative("HaveSavedHandle",
				new HaveSavedHashtableValueFunc(agentType));
		jassProgramVisitor.getJassNativeManager().createNative("RemoveSavedInteger",
				new RemoveSavedHashtableValueFunc(CHashtable.INTEGER));
		jassProgramVisitor.getJassNativeManager().createNative("RemoveSavedReal",
				new RemoveSavedHashtableValueFunc(CHashtable.REAL));
		jassProgramVisitor.getJassNativeManager().createNative("RemoveSavedBoolean",
				new RemoveSavedHashtableValueFunc(CHashtable.BOOLEAN));
		jassProgramVisitor.getJassNativeManager().createNative("RemoveSavedString",
				new RemoveSavedHashtableValueFunc(CHashtable.STRING));
		jassProgramVisitor.getJassNativeManager().createNative("RemoveSavedHandle",
				new RemoveSavedHashtableValueFunc(CHashtable.HANDLE));
		jassProgramVisitor.getJassNativeManager().createNative("FlushChildHashtable",
				(arguments, globalScope, triggerScope) -> {
					final CHashtable table = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (table != null) {
						table.flushChild(intArgument(arguments, 1));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("FlushParentHashtable",
				(arguments, globalScope, triggerScope) -> {
					final CHashtable table = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (table != null) {
						table.flushAll();
					}
					return null;
				});
	}

	private static int intArgument(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value instanceof IntegerJassValue ? ((IntegerJassValue) value).getValue()
				: value.visit(IntegerJassValueVisitor.getInstance());
	}

	private static boolean isNullValue(final JassValue value) {
		return (value == null) || ((value instanceof HandleJassValue) && (((HandleJassValue) value).getJavaValue() == null));
	}

	private static int hashtableSlot(final JassType type) {
		if (type == JassType.INTEGER) {
			return CHashtable.INTEGER;
		}
		if (type == JassType.REAL) {
			return CHashtable.REAL;
		}
		if (type == JassType.BOOLEAN) {
			return CHashtable.BOOLEAN;
		}
		if (type == JassType.STRING) {
			return CHashtable.STRING;
		}
		return CHashtable.HANDLE;
	}

	private static final class SaveHashtableValueFunc implements JassFunction {
		private final int slot;

		public SaveHashtableValueFunc() {
			this(CHashtable.HANDLE);
		}

		public SaveHashtableValueFunc(final int slot) {
			this.slot = slot;
		}

		@Override
		public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
				final TriggerExecutionScope triggerScope) {
			final CHashtable table = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
			final int parentKey = intArgument(arguments, 1);
			final int childKey = intArgument(arguments, 2);
			if (table == null) {
				return BooleanJassValue.FALSE;
			}
			final JassValue value = arguments.get(3);
			// Measured on 1.29: a null handle is not saved (false, an older value stays); a null string is saved.
			if ((this.slot == CHashtable.HANDLE) && isNullValue(value)) {
				return BooleanJassValue.FALSE;
			}
			table.save(this.slot, parentKey, childKey,
					value == null ? StringJassValue.of(null) : value);
			return BooleanJassValue.TRUE;
		}
	}

	private static final class LoadHashtableValueFunc implements JassFunction {
		private final JassValue nullValue;
		private final JassType type;
		private final int slot;

		public LoadHashtableValueFunc(final JassValue nullValue) {
			this.nullValue = nullValue;
			this.type = nullValue.visit(JassTypeGettingValueVisitor.getInstance());
			this.slot = hashtableSlot(this.type);
		}

		@Override
		public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
				final TriggerExecutionScope triggerScope) {
			final CHashtable table = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
			final int parentKey = intArgument(arguments, 1);
			final int childKey = intArgument(arguments, 2);
			if (table == null) {
				return this.nullValue;
			}
			final JassValue loadedJassValue = table.load(this.slot, parentKey, childKey);
			if (loadedJassValue == null) {
				return this.nullValue;
			}
			if ((this.slot == CHashtable.HANDLE) && !this.type
					.isAssignableFrom(loadedJassValue.visit(JassTypeGettingValueVisitor.getInstance()))) {
				return this.nullValue;
			}
			return loadedJassValue;
		}
	}

	private static final class HaveSavedHashtableValueFunc implements JassFunction {
		private final int slot;

		public HaveSavedHashtableValueFunc(final JassType jassType) {
			this.slot = hashtableSlot(jassType);
		}

		@Override
		public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
				final TriggerExecutionScope triggerScope) {
			final CHashtable table = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
			if (table == null) {
				return BooleanJassValue.FALSE;
			}
			final int parentKey = intArgument(arguments, 1);
			final int childKey = intArgument(arguments, 2);
			return BooleanJassValue.of(table.has(this.slot, parentKey, childKey));
		}
	}

	private static final class RemoveSavedHashtableValueFunc implements JassFunction {
		private final int slot;

		public RemoveSavedHashtableValueFunc(final int slot) {
			this.slot = slot;
		}

		@Override
		public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
				final TriggerExecutionScope triggerScope) {
			final CHashtable table = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
			if (table != null) {
				table.remove(this.slot, intArgument(arguments, 1), intArgument(arguments, 2));
			}
			return null;
		}
	}
}
