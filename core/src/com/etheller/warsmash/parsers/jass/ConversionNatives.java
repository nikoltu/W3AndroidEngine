package com.etheller.warsmash.parsers.jass;

import com.etheller.interpreter.ast.scope.variableevent.CLimitOp;
import com.etheller.interpreter.ast.util.CHandle;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRaceManager;
import com.etheller.interpreter.ast.util.JassNumbers;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.util.JassStringHash;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.UnknownEnumHandle;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.units.custom.WTS;
import com.etheller.warsmash.util.DataErrors;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.ai.AIDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.item.CItemTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIdUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapFlag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapPlacement;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerColor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerGameResult;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerScore;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CStartLocPrio;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.state.CGameState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.state.CUnitState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CAttackTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CBlendMode;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CCameraField;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CGameSpeed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CGameType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CMapDensity;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CMapDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPathingTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CRarityControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CSoundType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CSoundVolumeGroup;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CTexMapFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CVersion;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.ToIntFunction;

import static com.etheller.warsmash.parsers.jass.CommonNatives.enumValue;
import static com.etheller.warsmash.parsers.jass.CommonNatives.nullable;

/**
 * Convert* natives of the typed constants and the string and number conversion natives, shared by the configuration and the game environments.
 */
final class ConversionNatives {
	private ConversionNatives() {
	}

	private static int unitEventId(final boolean mouseEventsBeforeUnitEvents, final int scriptEventId) {
		if (mouseEventsBeforeUnitEvents && (scriptEventId >= 272) && (scriptEventId <= 304)) {
			return scriptEventId - 3;
		}
		return scriptEventId;
	}

	static void registerTypingNatives(final JassProgram jassProgramVisitor, final HandleJassType raceType,
			final HandleJassType alliancetypeType, final HandleJassType racepreferenceType,
			final HandleJassType igamestateType, final HandleJassType fgamestateType,
			final HandleJassType playerstateType, final HandleJassType playerscoreType,
			final HandleJassType playergameresultType, final HandleJassType unitstateType,
			final HandleJassType aidifficultyType, final HandleJassType gameeventType,
			final HandleJassType playereventType, final HandleJassType playeruniteventType,
			final HandleJassType uniteventType, final HandleJassType limitopType, final HandleJassType widgeteventType,
			final HandleJassType dialogeventType, final HandleJassType unittypeType, final HandleJassType gamespeedType,
			final HandleJassType gamedifficultyType, final HandleJassType gametypeType,
			final HandleJassType mapflagType, final HandleJassType mapvisibilityType,
			final HandleJassType mapsettingType, final HandleJassType mapdensityType,
			final HandleJassType mapcontrolType, final HandleJassType playerslotstateType,
			final HandleJassType volumegroupType, final HandleJassType camerafieldType,
			final HandleJassType playercolorType, final HandleJassType placementType,
			final HandleJassType startlocprioType, final HandleJassType raritycontrolType,
			final HandleJassType blendmodeType, final HandleJassType texmapflagsType,
			final HandleJassType effecttypeType, final HandleJassType fogstateType, final HandleJassType versionType,
			final HandleJassType itemtypeType, final HandleJassType attacktypeType, final HandleJassType damagetypeType,
			final HandleJassType weapontypeType, final HandleJassType soundtypeType,
			final HandleJassType pathingtypeType, final CRaceManager raceManager) {
		final ToIntFunction<Object> enumHandleValue = (value) -> (value instanceof CHandle)
				? ((CHandle) value).getHandleId()
				: -1;
		for (final HandleJassType enumType : new HandleJassType[] { raceType, alliancetypeType, racepreferenceType,
				igamestateType, fgamestateType, playerstateType, playerscoreType, playergameresultType, unitstateType,
				aidifficultyType, gameeventType, playereventType, playeruniteventType, uniteventType, limitopType,
				widgeteventType, dialogeventType, unittypeType, gamespeedType, gamedifficultyType, gametypeType,
				mapflagType, mapvisibilityType, mapsettingType, mapdensityType, mapcontrolType, playerslotstateType,
				volumegroupType, camerafieldType, playercolorType, placementType, startlocprioType,
				raritycontrolType, blendmodeType, texmapflagsType, effecttypeType, fogstateType, versionType,
				itemtypeType, weapontypeType, soundtypeType, pathingtypeType, damagetypeType }) {
			enumType.setEnumValue(enumHandleValue);
		}
		attacktypeType.setEnumValue((value) -> (value instanceof UnknownEnumHandle)
				? ((UnknownEnumHandle) value).getHandleId()
				: Arrays.asList(CAttackTypeJass.VALUES).indexOf(value));
		jassProgramVisitor.getJassNativeManager().createNative("ConvertRace",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(raceType, raceManager.getRace(i), i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertAllianceType",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(alliancetypeType, CAllianceType.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertRacePref",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(racepreferenceType, raceManager.getRacePreference(i), i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertIGameState",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(igamestateType, CGameState.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertFGameState",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(fgamestateType, CGameState.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertPlayerState",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(playerstateType, CPlayerState.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertPlayerScore",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(playerscoreType, CPlayerScore.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertPlayerGameResult",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(playergameresultType, CPlayerGameResult.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertUnitState",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(unitstateType, CUnitState.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertAIDifficulty",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(aidifficultyType, AIDifficulty.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertGameEvent",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(gameeventType, JassGameEventsWar3.VALUES, i);
				});
		// The 1.29 and 1.30 common.j number the mouse events 269-271, so the Frozen Throne unit events after them sit
		// three higher than in 1.31+ (where the mouse events are 305-307). common.j declares the mouse events before
		// those unit events, which tells the two numberings apart.
		final boolean[] mouseEventsBeforeUnitEvents = { false };
		jassProgramVisitor.getJassNativeManager().createNative("ConvertPlayerEvent",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					if ((i >= 269) && (i <= 271)) {
						mouseEventsBeforeUnitEvents[0] = true;
						return new HandleJassValue(playereventType,
								JassGameEventsWar3.VALUES[JassGameEventsWar3.EVENT_PLAYER_MOUSE_DOWN.getEventId() + (i - 269)]);
					}
					return enumValue(playereventType, JassGameEventsWar3.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertPlayerUnitEvent",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(playeruniteventType, JassGameEventsWar3.VALUES,
							unitEventId(mouseEventsBeforeUnitEvents[0], i));
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertWidgetEvent",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(widgeteventType, JassGameEventsWar3.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertDialogEvent",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(dialogeventType, JassGameEventsWar3.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertUnitEvent",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(uniteventType, JassGameEventsWar3.VALUES,
							unitEventId(mouseEventsBeforeUnitEvents[0], i));
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertLimitOp",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(limitopType, CLimitOp.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertUnitType",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(unittypeType, CUnitTypeJass.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertGameSpeed",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(gamespeedType, CGameSpeed.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertPlacement",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(placementType, CMapPlacement.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertStartLocPrio",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(startlocprioType, CStartLocPrio.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertGameDifficulty",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(gamedifficultyType, CMapDifficulty.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertGameType",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(gametypeType, CGameType.getById(i), i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertMapFlag",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(mapflagType, CMapFlag.getById(i), i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertMapVisibility",
				(arguments, globalScope, triggerScope) -> {
					return mapvisibilityType
							.getUnknownEnumValue(arguments.get(0).visit(IntegerJassValueVisitor.getInstance()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertMapSetting",
				(arguments, globalScope, triggerScope) -> {
					return mapsettingType.getUnknownEnumValue(arguments.get(0).visit(IntegerJassValueVisitor.getInstance()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertMapDensity",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(mapdensityType, CMapDensity.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertMapControl",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(mapcontrolType, CMapControl.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertPlayerColor",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(playercolorType, CPlayerColor.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertPlayerSlotState",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(playerslotstateType, CPlayerSlotState.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertVolumeGroup",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(volumegroupType, CSoundVolumeGroup.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertCameraField",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(camerafieldType, CCameraField.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertBlendMode",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(blendmodeType, CBlendMode.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertRarityControl",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(raritycontrolType, CRarityControl.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertTexMapFlags",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(texmapflagsType, CTexMapFlags.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertFogState",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(fogstateType, CFogState.getById(i), i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertEffectType",
				(arguments, globalScope, triggerType) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(effecttypeType, CEffectType.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertVersion",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(versionType, CVersion.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertItemType",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(itemtypeType, CItemTypeJass.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertAttackType",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(attacktypeType, CAttackTypeJass.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertDamageType",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(damagetypeType, CDamageType.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertWeaponType",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(weapontypeType, CWeaponSoundTypeJass.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertSoundType",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(soundtypeType, CSoundType.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertPathingType",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(pathingtypeType, CPathingTypeJass.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("OrderId", (arguments, globalScope, triggerScope) -> {
			final String idString = arguments.get(0).visit(StringJassValueVisitor.getInstance());
			final int orderId = OrderIdUtils.getOrderId(idString);
			return IntegerJassValue.of(orderId);
		});
		jassProgramVisitor.getJassNativeManager().createNative("OrderId2String",
				(arguments, globalScope, triggerScope) -> {
					final Integer id = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return new StringJassValue(OrderIdUtils.getStringFromOrderId(id));
				});
	}

	static void registerConversionAndStringNatives(final JassProgram jassProgramVisitor, final GameUI gameUI) {
		jassProgramVisitor.getJassNativeManager().createNative("Deg2Rad", (arguments, globalScope, triggerScope) -> {
			final Double value = arguments.get(0).visit(RealJassValueVisitor.getInstance());
			return RealJassValue.of(StrictMath.toRadians(value));
		});
		jassProgramVisitor.getJassNativeManager().createNative("Rad2Deg", (arguments, globalScope, triggerScope) -> {
			final Double value = arguments.get(0).visit(RealJassValueVisitor.getInstance());
			return RealJassValue.of(StrictMath.toDegrees(value));
		});
		jassProgramVisitor.getJassNativeManager().createNative("Sin", (arguments, globalScope, triggerScope) -> {
			final Double value = arguments.get(0).visit(RealJassValueVisitor.getInstance());
			return RealJassValue.of(StrictMath.sin(value));
		});
		jassProgramVisitor.getJassNativeManager().createNative("Cos", (arguments, globalScope, triggerScope) -> {
			final Double value = arguments.get(0).visit(RealJassValueVisitor.getInstance());
			return RealJassValue.of(StrictMath.cos(value));
		});
		jassProgramVisitor.getJassNativeManager().createNative("Tan", (arguments, globalScope, triggerScope) -> {
			final Double value = arguments.get(0).visit(RealJassValueVisitor.getInstance());
			return RealJassValue.of(StrictMath.tan(value));
		});
		jassProgramVisitor.getJassNativeManager().createNative("Asin", (arguments, globalScope, triggerScope) -> {
			final Double value = arguments.get(0).visit(RealJassValueVisitor.getInstance());
			final double result = StrictMath.asin(value);
			if (Double.isNaN(result)) {
				return RealJassValue.of(0);
			}
			return RealJassValue.of(result);
		});
		jassProgramVisitor.getJassNativeManager().createNative("Acos", (arguments, globalScope, triggerScope) -> {
			final Double value = arguments.get(0).visit(RealJassValueVisitor.getInstance());
			final double result = StrictMath.acos(value);
			if (Double.isNaN(result)) {
				return RealJassValue.of(0);
			}
			return RealJassValue.of(result);
		});
		jassProgramVisitor.getJassNativeManager().createNative("Atan", (arguments, globalScope, triggerScope) -> {
			final Double value = arguments.get(0).visit(RealJassValueVisitor.getInstance());
			final double result = StrictMath.atan(value);
			if (Double.isNaN(result)) {
				return RealJassValue.of(0);
			}
			return RealJassValue.of(result);
		});
		jassProgramVisitor.getJassNativeManager().createNative("Atan2", (arguments, globalScope, triggerScope) -> {
			final Double y = arguments.get(0).visit(RealJassValueVisitor.getInstance());
			final Double x = arguments.get(1).visit(RealJassValueVisitor.getInstance());
			final double result = StrictMath.atan2(y, x);
			if (Double.isNaN(result)) {
				return RealJassValue.of(0);
			}
			return RealJassValue.of(result);
		});
		jassProgramVisitor.getJassNativeManager().createNative("SquareRoot", (arguments, globalScope, triggerScope) -> {
			final Double value = arguments.get(0).visit(RealJassValueVisitor.getInstance());
			final double result = StrictMath.sqrt(value);
			return RealJassValue.of(result);
		});
		jassProgramVisitor.getJassNativeManager().createNative("Pow", (arguments, globalScope, triggerScope) -> {
			final Double y = arguments.get(0).visit(RealJassValueVisitor.getInstance());
			final Double x = arguments.get(1).visit(RealJassValueVisitor.getInstance());
			double result = StrictMath.pow(y, x);
			if (Double.isNaN(result)) {
				// Where the power of a negative base is not real, the original game uses the positive base: Pow(-2, 0.5) is 1.414.
				result = StrictMath.pow(Math.abs(y), x);
			}
			return RealJassValue.of(result);
		});
		jassProgramVisitor.getJassNativeManager().createNative("I2R", (arguments, globalScope, triggerScope) -> {
			final Integer i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
			return RealJassValue.of(JassNumbers.fromInt(i));
		});
		jassProgramVisitor.getJassNativeManager().createNative("R2I", (arguments, globalScope, triggerScope) -> {
			final Double r = arguments.get(0).visit(RealJassValueVisitor.getInstance());
			return IntegerJassValue.of(r.intValue());
		});
		jassProgramVisitor.getJassNativeManager().createNative("I2S", (arguments, globalScope, triggerScope) -> {
			final Integer i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
			return new StringJassValue(i.toString());
		});
		jassProgramVisitor.getJassNativeManager().createNative("R2S", (arguments, globalScope, triggerScope) -> {
			final Double r = arguments.get(0).visit(RealJassValueVisitor.getInstance());
			return new StringJassValue(JassNumbers.format(r, 0, 3));
		});
		jassProgramVisitor.getJassNativeManager().createNative("R2SW", (arguments, globalScope, triggerScope) -> {
			final Double r = arguments.get(0).visit(RealJassValueVisitor.getInstance());
			final int width = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
			final int precision = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
			return new StringJassValue(JassNumbers.format(r, width, precision));
		});
		jassProgramVisitor.getJassNativeManager().createNative("S2I", (arguments, globalScope, triggerScope) -> {
			final String s = arguments.get(0).visit(StringJassValueVisitor.getInstance());
			return IntegerJassValue.of(JassNumbers.stringToInt(s));
		});
		jassProgramVisitor.getJassNativeManager().createNative("S2R", (arguments, globalScope, triggerScope) -> {
			final String s = arguments.get(0).visit(StringJassValueVisitor.getInstance());
			return RealJassValue.of(JassNumbers.stringToReal(s));
		});
		jassProgramVisitor.getJassNativeManager().createNative("SubString", (arguments, globalScope, triggerScope) -> {
			final String s = arguments.get(0).visit(StringJassValueVisitor.getInstance());
			int start = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
			int end = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
			if (start > s.length()) {
				start = s.length();
			}
			if (start < 0) {
				start = 0;
			}
			if (end > s.length()) {
				end = s.length();
			}
			if (end < start) {
				end = start;
			}
			return new StringJassValue(s.substring(start, end));
		});
		jassProgramVisitor.getJassNativeManager().createNative("StringLength",
				(arguments, globalScope, triggerScope) -> {
					final String s = gameUI.getTrigStr(nullable(arguments, 0, StringJassValueVisitor.getInstance()));
					return IntegerJassValue.of(s == null ? 0 : s.length());
				});
		jassProgramVisitor.getJassNativeManager().createNative("StringCase", (arguments, globalScope, triggerScope) -> {
			final String s = gameUI.getTrigStr(arguments.get(0).visit(StringJassValueVisitor.getInstance()));
			final boolean upper = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
			return new StringJassValue(upper ? s.toUpperCase(Locale.US) : s.toLowerCase(Locale.US));
		});
		jassProgramVisitor.getJassNativeManager().createNative("StringHash", (arguments, globalScope, triggerScope) -> {
			final String s = arguments.get(0).visit(StringJassValueVisitor.getInstance());
			return IntegerJassValue.of(JassStringHash.hash(s));
		});
		jassProgramVisitor.getJassNativeManager().createNative("GetLocalizedString",
				(arguments, globalScope, triggerScope) -> {
					final String key = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					if (key == null) {
						return new StringJassValue("");
					}
					// 1.32+ dialogue keys (SetDialogueSpeakerNameKey "TRIGSTR_405") are map string keys too.
					if (key.startsWith(WTS.KEY_PREFIX)) {
						return new StringJassValue(gameUI.getTrigStr(key));
					}
					final String decoratedString = gameUI.getTemplates().getDecoratedString(key);
					if (key.equals(decoratedString) && key.matches("[A-Z0-9_]+")) {
						DataErrors.report(DataErrors.MISSING_STRING, key, "GetLocalizedString: NOT FOUND: " + key);
					}
					return new StringJassValue(decoratedString);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetLocalizedHotkey",
				(arguments, globalScope, triggerScope) -> {
					final String key = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					return IntegerJassValue.of(key == null ? 0 : gameUI.getLocalizedHotkey(key));
				});
	}
}
