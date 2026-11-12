package com.etheller.warsmash.parsers.jass;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.interpreter.ast.debug.JassException;
import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.util.JassLog;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.fdf.GameSkin;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.AnchorDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.parsers.jass.triggers.UnitGroup;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.GameDataConstants;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedGame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.HandleIdAllocator;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.CBasePlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfig;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CGameType;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashUI;
import net.warsmash.parsers.jass.SmashJassParser;

import static com.etheller.warsmash.parsers.jass.CommonNatives.enumValue;
import static com.etheller.warsmash.parsers.jass.CommonNatives.nullable;

public class Jass2 {
	public static CommonEnvironment loadCommon(final DataSource dataSource, final Viewport uiViewport,
			final Scene uiScene, final War3MapViewer war3MapViewer, final WarsmashUI meleeUI, final String... files) {

		final JassProgram jassProgramVisitor = new JassProgram();
		final CommonEnvironment environment = new CommonEnvironment(jassProgramVisitor, dataSource, uiViewport, uiScene,
				war3MapViewer, meleeUI, files);
		for (final String file : files) {
			String jassFilePath = file;
			if (!dataSource.has(jassFilePath)) {
				jassFilePath = jassFilePath
						.substring(Math.max(jassFilePath.lastIndexOf('/'), jassFilePath.lastIndexOf('\\')) + 1);
			}
			if (!dataSource.has(jassFilePath)) {
				final String lowerCaseDirectoryPath = file.toLowerCase(Locale.US);
				final String lowerCaseDirectoryPathLinux = file.toLowerCase(Locale.US).replace("\\", "/");

				final Collection<String> listfile = dataSource.getListfile();
				final Map<String, String> fixedListfile = new HashMap<>();
				for (final String path : listfile) {
					fixedListfile.put(path.toLowerCase(Locale.US), path);
				}
				for (final Map.Entry<String, String> pathAndPath : fixedListfile.entrySet()) {
					final String lowerCasePath = pathAndPath.getKey();
					final String realPath = pathAndPath.getValue();
					if (lowerCasePath.startsWith(lowerCaseDirectoryPath)
							|| lowerCasePath.startsWith(lowerCaseDirectoryPathLinux)) {
						environment.scriptFailed(readJassFile(dataSource, jassProgramVisitor, realPath));
					}
				}
			}
			else {
				environment.scriptFailed(readJassFile(dataSource, jassProgramVisitor, jassFilePath));
			}
		}
		// The handles the global initializers make come before the players in 1.29.
		war3MapViewer.simulation.getHandleIdAllocator().setEarlyIds(true);
		try {
			jassProgramVisitor.initialize();
		}
		catch (final Exception e) {
			Log.error(e);
			JassLog.report(e);
		}
		finally {
			war3MapViewer.simulation.getHandleIdAllocator().setEarlyIds(false);
		}
		jassProgramVisitor.getJassNativeManager().checkUnregisteredNatives();
		return environment;
	}

	/** @return the reason the file does not compile, or null */
	private static Exception readJassFile(final DataSource dataSource, final JassProgram jassProgramVisitor,
			final String jassFilePath) {
		final String jassFile = jassFilePath;
		try {
			try (InputStreamReader reader = new InputStreamReader(dataSource.getResourceAsStream(jassFile),
					StandardCharsets.UTF_8)) {
				final SmashJassParser smashJassParser = new SmashJassParser(reader);
				smashJassParser.scanAndParse(jassFile, jassProgramVisitor);
			}
			return null;
		}
		catch (final Exception e) {
			Log.error(e);
			JassLog.report(e);
			return e;
		}
	}

	public static ConfigEnvironment loadConfig(final DataSource dataSource, final Viewport uiViewport,
			final Scene uiScene, final GameUI gameUI, final War3MapConfig mapConfig, final String... files) {
		mapConfig.selectGameType(CGameType.MELEE);
		final JassProgram jassProgramVisitor = new JassProgram();
		final ConfigEnvironment environment = new ConfigEnvironment(jassProgramVisitor, dataSource, uiViewport, uiScene,
				gameUI, mapConfig);
		for (final String file : files) {
			String jassFilePath = file;
			if (!dataSource.has(jassFilePath)) {
				jassFilePath = jassFilePath
						.substring(Math.max(jassFilePath.lastIndexOf('/'), jassFilePath.lastIndexOf('\\')) + 1);
			}
			if (!dataSource.has(jassFilePath)) {
				final String lowerCaseDirectoryPath = file.toLowerCase(Locale.US);
				final String lowerCaseDirectoryPathLinux = file.toLowerCase(Locale.US).replace("\\", "/");

				final Collection<String> listfile = dataSource.getListfile();
				final Map<String, String> fixedListfile = new HashMap<>();
				for (final String path : listfile) {
					fixedListfile.put(path.toLowerCase(Locale.US), path);
				}
				for (final Map.Entry<String, String> pathAndPath : fixedListfile.entrySet()) {
					final String lowerCasePath = pathAndPath.getKey();
					final String realPath = pathAndPath.getValue();
					if (lowerCasePath.startsWith(lowerCaseDirectoryPath)
							|| lowerCasePath.startsWith(lowerCaseDirectoryPathLinux)) {
						try {
							readJassFile(dataSource, jassProgramVisitor, realPath);
						}
						catch (final Exception e) {
							Log.error(e);
							JassLog.report(e);
						}
					}
				}
			}
			else {
				readJassFile(dataSource, jassProgramVisitor, jassFilePath);
			}
		}
		try {
			jassProgramVisitor.initialize();
		}
		catch (final Exception e) {
			Log.error(e);
			JassLog.report(e);
		}
		jassProgramVisitor.getJassNativeManager().checkUnregisteredNatives();
		return environment;
	}

	public static JUIEnvironment loadJUI(final DataSource dataSource, final Viewport uiViewport, final Scene uiScene,
			final War3MapViewer war3MapViewer, final RootFrameListener rootFrameListener, final String... files) {

		final JassProgram jassProgramVisitor = new JassProgram();
		final JUIEnvironment environment = new JUIEnvironment(jassProgramVisitor, dataSource, uiViewport, uiScene,
				war3MapViewer, rootFrameListener);
		for (final String jassFile : files) {
			try {
				try (InputStreamReader reader = new InputStreamReader(dataSource.getResourceAsStream(jassFile),
						StandardCharsets.UTF_8)) {
					final SmashJassParser smashJassParser = new SmashJassParser(reader);
					smashJassParser.scanAndParse(jassFile, jassProgramVisitor);
				}
			}
			catch (final Exception e) {
				Log.error(e);
			}
		}
		try {
			jassProgramVisitor.initialize();
		}
		catch (final Exception e) {
			Log.error(e);
			JassLog.report(e);
		}
		jassProgramVisitor.getJassNativeManager().checkUnregisteredNatives();
		return environment;
	}

	public static interface RootFrameListener {
		void onCreate(GameUI rootFrame);
	}

	private static final class JUIEnvironment {
		private GameUI gameUI;
		private Element skin;

		public JUIEnvironment(final JassProgram jassProgramVisitor, final DataSource dataSource,
				final Viewport uiViewport, final Scene uiScene, final War3MapViewer war3MapViewer,
				final RootFrameListener rootFrameListener) {
			final GlobalScope globals = jassProgramVisitor.getGlobalScope();
			final HandleJassType frameHandleType = globals.registerHandleType("framehandle");
			final HandleJassType framePointType = globals.registerHandleType("framepointtype");
			final HandleJassType triggerType = globals.registerHandleType("trigger");
			final HandleJassType triggerActionType = globals.registerHandleType("triggeraction");
			final HandleJassType triggerConditionType = globals.registerHandleType("triggercondition");
			final HandleJassType boolExprType = globals.registerHandleType("boolexpr");
			final HandleJassType conditionFuncType = globals.registerHandleType("conditionfunc");
			final HandleJassType filterType = globals.registerHandleType("filterfunc");
			final HandleJassType eventidType = globals.registerHandleType("eventid");
			jassProgramVisitor.getJassNativeManager().createNative("LogError",
					(arguments, globalScope, triggerScope) -> {
						final String stringValue = arguments.get(0).visit(StringJassValueVisitor.getInstance());
						Log.warn(stringValue);
						return null;
					});
			jassProgramVisitor.getJassNativeManager().createNative("ConvertFramePointType",
					(arguments, globalScope, triggerScope) -> {
						final int value = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
						return enumValue(framePointType, FramePoint.values(), value);
					});
			jassProgramVisitor.getJassNativeManager().createNative("CreateRootFrame",
					(arguments, globalScope, triggerScope) -> {
						final String skinArg = arguments.get(0).visit(StringJassValueVisitor.getInstance());
						final GameSkin skin = GameUI.loadSkin(dataSource, skinArg);
						final GameUI gameUI = new GameUI(dataSource, skin, uiViewport, uiScene, war3MapViewer, 0,
								war3MapViewer.getAllObjectData().getWts(), war3MapViewer.getGameData().getGameVersion());
						JUIEnvironment.this.gameUI = gameUI;
						JUIEnvironment.this.skin = skin.getSkin();
						rootFrameListener.onCreate(gameUI);
						return new HandleJassValue(frameHandleType, gameUI);
					});
			jassProgramVisitor.getJassNativeManager().createNative("LoadTOCFile",
					(arguments, globalScope, triggerScope) -> {
						final String tocFileName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
						try {
							JUIEnvironment.this.gameUI.loadTOCFile(tocFileName);
						}
						catch (final IOException e) {
							throw new RuntimeException(e);
						}
						return BooleanJassValue.TRUE;
					});
			jassProgramVisitor.getJassNativeManager().createNative("CreateSimpleFrame",
					(arguments, globalScope, triggerScope) -> {
						final String templateName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
						final UIFrame ownerFrame = arguments.get(1)
								.visit(ObjectJassValueVisitor.<UIFrame>getInstance());
						final int createContext = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
						final UIFrame simpleFrame = JUIEnvironment.this.gameUI.createSimpleFrame(templateName,
								ownerFrame, createContext);
						return new HandleJassValue(frameHandleType, simpleFrame);
					});
			jassProgramVisitor.getJassNativeManager().createNative("CreateFrame",
					(arguments, globalScope, triggerScope) -> {
						final String templateName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
						final UIFrame ownerFrame = arguments.get(1)
								.visit(ObjectJassValueVisitor.<UIFrame>getInstance());
						final int priority = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
						final int createContext = arguments.get(3).visit(IntegerJassValueVisitor.getInstance());
						final UIFrame simpleFrame = JUIEnvironment.this.gameUI.createFrame(templateName, ownerFrame,
								priority, createContext);
						return new HandleJassValue(frameHandleType, simpleFrame);
					});
			jassProgramVisitor.getJassNativeManager().createNative("GetFrameByName",
					(arguments, globalScope, triggerScope) -> {
						final String templateName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
						final int createContext = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
						final UIFrame simpleFrame = JUIEnvironment.this.gameUI.getFrameByName(templateName,
								createContext);
						return new HandleJassValue(frameHandleType, simpleFrame);
					});
			jassProgramVisitor.getJassNativeManager().createNative("FrameSetAnchor",
					(arguments, globalScope, triggerScope) -> {
						final UIFrame frame = arguments.get(0).visit(ObjectJassValueVisitor.<UIFrame>getInstance());
						final FramePoint framePoint = arguments.get(1)
								.visit(ObjectJassValueVisitor.<FramePoint>getInstance());
						final double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
						final double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());

						frame.addAnchor(new AnchorDefinition(framePoint, GameUI.convertX(uiViewport, (float) x),
								GameUI.convertY(uiViewport, (float) y)));
						return null;
					});
			jassProgramVisitor.getJassNativeManager().createNative("FrameSetAbsPoint",
					(arguments, globalScope, triggerScope) -> {
						final UIFrame frame = arguments.get(0).visit(ObjectJassValueVisitor.<UIFrame>getInstance());
						final FramePoint framePoint = arguments.get(1)
								.visit(ObjectJassValueVisitor.<FramePoint>getInstance());
						final double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
						final double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());
						frame.setFramePointX(framePoint, GameUI.convertX(uiViewport, (float) x));
						frame.setFramePointY(framePoint, GameUI.convertY(uiViewport, (float) y));
						return null;
					});
			jassProgramVisitor.getJassNativeManager().createNative("FrameSetPoint",
					(arguments, globalScope, triggerScope) -> {
						final UIFrame frame = arguments.get(0).visit(ObjectJassValueVisitor.<UIFrame>getInstance());
						final FramePoint framePoint = arguments.get(1)
								.visit(ObjectJassValueVisitor.<FramePoint>getInstance());
						final UIFrame otherFrame = arguments.get(2)
								.visit(ObjectJassValueVisitor.<UIFrame>getInstance());
						final FramePoint otherPoint = arguments.get(3)
								.visit(ObjectJassValueVisitor.<FramePoint>getInstance());
						final double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
						final double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());

						frame.addSetPoint(new SetPoint(framePoint, otherFrame, otherPoint,
								GameUI.convertX(uiViewport, (float) x), GameUI.convertY(uiViewport, (float) y)));
						return null;
					});
			jassProgramVisitor.getJassNativeManager().createNative("FrameSetText",
					(arguments, globalScope, triggerScope) -> {
						final StringFrame frame = arguments.get(0)
								.visit(ObjectJassValueVisitor.<StringFrame>getInstance());
						final String text = arguments.get(1).visit(StringJassValueVisitor.getInstance());

						JUIEnvironment.this.gameUI.setText(frame, text);
						return null;
					});
			jassProgramVisitor.getJassNativeManager().createNative("FrameSetTextColor",
					(arguments, globalScope, triggerScope) -> {
						final StringFrame frame = arguments.get(0)
								.visit(ObjectJassValueVisitor.<StringFrame>getInstance());
						final int colorInt = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
						frame.setColor(new Color(colorInt));
						return null;
					});
			jassProgramVisitor.getJassNativeManager().createNative("ConvertColor",
					(arguments, globalScope, triggerScope) -> {
						final int a = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
						final int r = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
						final int g = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
						final int b = arguments.get(3).visit(IntegerJassValueVisitor.getInstance());
						return IntegerJassValue.of(a | (b << 8) | (g << 16) | (r << 24));
					});
			jassProgramVisitor.getJassNativeManager().createNative("FramePositionBounds",
					(arguments, globalScope, triggerScope) -> {
						final UIFrame frame = arguments.get(0).visit(ObjectJassValueVisitor.<UIFrame>getInstance());
						frame.positionBounds(JUIEnvironment.this.gameUI, uiViewport);
						return null;
					});
			jassProgramVisitor.getJassNativeManager().createNative("SkinGetField",
					(arguments, globalScope, triggerScope) -> {
						final String fieldName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
						return new StringJassValue(JUIEnvironment.this.skin.getField(fieldName));
					});
			TriggerNatives.setupTriggerAPI(jassProgramVisitor, triggerType, triggerActionType, triggerConditionType, boolExprType,
					conditionFuncType, filterType, eventidType, new HandleIdAllocator());
		}
	}

	public static final class CommonEnvironment {

		final GameUI gameUI;
		private Element skin;
		final JassProgram jassProgramVisitor;
		final CSimulation simulation;
		final DataSource dataSource;
		final Viewport uiViewport;
		final Scene uiScene;
		final War3MapViewer war3MapViewer;
		final WarsmashUI meleeUI;
		final String[] originalFiles;
		final Rectangle tempRect = new Rectangle();
		private final SavedGame.AiScriptStarter aiScriptStarter;
		private Exception scriptError;

		private CommonEnvironment(final JassProgram jassProgramVisitor, final DataSource dataSource,
				final Viewport uiViewport, final Scene uiScene, final War3MapViewer war3MapViewer,
				final WarsmashUI meleeUI, final String[] originalFiles) {
			this.jassProgramVisitor = jassProgramVisitor;
			this.gameUI = war3MapViewer.getGameUI();
			this.simulation = war3MapViewer.simulation;
			this.dataSource = dataSource;
			this.uiViewport = uiViewport;
			this.uiScene = uiScene;
			this.war3MapViewer = war3MapViewer;
			this.meleeUI = meleeUI;
			this.originalFiles = originalFiles;
			war3MapViewer.setScriptEnvironment(this, uiScene.audioContext);
			this.aiScriptStarter = entry -> {
				if (entry.playerIndex < 0) {
					doPreloadScript(dataSource, uiViewport, uiScene, war3MapViewer, entry.scriptFile, meleeUI,
							originalFiles, jassProgramVisitor, entry.mainFunction, false);
				}
				else {
					doAIScript(dataSource, uiViewport, uiScene, war3MapViewer,
							this.simulation.getPlayer(entry.playerIndex), entry.scriptFile, meleeUI, originalFiles,
							jassProgramVisitor, this.simulation, entry.mainFunction, false);
				}
			};
			final GlobalScope globals = jassProgramVisitor.getGlobalScope();
			globals.registerHandleType("handle");
			globals.registerHandleType("agent");
			globals.registerHandleType("event");
			final HandleJassType playerType = globals.registerHandleType("player");
			globals.registerHandleType("widget");
			globals.registerHandleType("unit");
			globals.registerHandleType("destructable");
			globals.registerHandleType("item");
			globals.registerHandleType("ability");
			globals.registerHandleType("buff");
			globals.registerHandleType("force");
			globals.registerHandleType("group");
			globals.registerHandleType("trigger");
			globals.registerHandleType("triggercondition");
			globals.registerHandleType("triggeraction");
			globals.registerHandleType("timer");
			final HandleJassType locationType = globals.registerHandleType("location");
			globals.registerHandleType("region");
			globals.registerHandleType("rect");
			globals.registerHandleType("boolexpr");
			globals.registerHandleType("sound");
			globals.registerHandleType("conditionfunc");
			globals.registerHandleType("filterfunc");
			globals.registerHandleType("unitpool");
			globals.registerHandleType("itempool");
			final HandleJassType raceType = globals.registerHandleType("race");
			final HandleJassType alliancetypeType = globals.registerHandleType("alliancetype");
			final HandleJassType racepreferenceType = globals.registerHandleType("racepreference");
			globals.registerHandleType("gamestate");
			final HandleJassType igamestateType = globals.registerHandleType("igamestate");
			final HandleJassType fgamestateType = globals.registerHandleType("fgamestate");
			final HandleJassType playerstateType = globals.registerHandleType("playerstate");
			final HandleJassType playerscoreType = globals.registerHandleType("playerscore");
			final HandleJassType playergameresultType = globals.registerHandleType("playergameresult");
			final HandleJassType unitstateType = globals.registerHandleType("unitstate");
			final HandleJassType aidifficultyType = globals.registerHandleType("aidifficulty");
			globals.registerHandleType("eventid");
			final HandleJassType gameeventType = globals.registerHandleType("gameevent");
			final HandleJassType playereventType = globals.registerHandleType("playerevent");
			final HandleJassType playeruniteventType = globals.registerHandleType("playerunitevent");
			final HandleJassType uniteventType = globals.registerHandleType("unitevent");
			final HandleJassType limitopType = globals.registerHandleType("limitop");
			final HandleJassType widgeteventType = globals.registerHandleType("widgetevent");
			final HandleJassType dialogeventType = globals.registerHandleType("dialogevent");
			final HandleJassType unittypeType = globals.registerHandleType("unittype");
			final HandleJassType gamespeedType = globals.registerHandleType("gamespeed");
			final HandleJassType gamedifficultyType = globals.registerHandleType("gamedifficulty");
			final HandleJassType gametypeType = globals.registerHandleType("gametype");
			final HandleJassType mapflagType = globals.registerHandleType("mapflag");
			final HandleJassType mapvisibilityType = globals.registerHandleType("mapvisibility");
			final HandleJassType mapsettingType = globals.registerHandleType("mapsetting");
			final HandleJassType mapdensityType = globals.registerHandleType("mapdensity");
			final HandleJassType mapcontrolType = globals.registerHandleType("mapcontrol");
			final HandleJassType playerslotstateType = globals.registerHandleType("playerslotstate");
			final HandleJassType volumegroupType = globals.registerHandleType("volumegroup");
			final HandleJassType camerafieldType = globals.registerHandleType("camerafield");
			globals.registerHandleType("camerasetup");
			final HandleJassType playercolorType = globals.registerHandleType("playercolor");
			final HandleJassType placementType = globals.registerHandleType("placement");
			final HandleJassType startlocprioType = globals.registerHandleType("startlocprio");
			final HandleJassType raritycontrolType = globals.registerHandleType("raritycontrol");
			final HandleJassType blendmodeType = globals.registerHandleType("blendmode");
			final HandleJassType texmapflagsType = globals.registerHandleType("texmapflags");
			globals.registerHandleType("effect");
			final HandleJassType effecttypeType = globals.registerHandleType("effecttype");
			globals.registerHandleType("weathereffect");
			globals.registerHandleType("terraindeformation");
			final HandleJassType fogstateType = globals.registerHandleType("fogstate");
			globals.registerHandleType("fogmodifier");
			globals.registerHandleType("dialog");
			globals.registerHandleType("button");
			globals.registerHandleType("quest");
			globals.registerHandleType("questitem");
			globals.registerHandleType("defeatcondition");
			globals.registerHandleType("timerdialog");
			globals.registerHandleType("leaderboard");
			globals.registerHandleType("multiboard");
			globals.registerHandleType("multiboarditem");
			globals.registerHandleType("trackable");
			globals.registerHandleType("gamecache");
			final HandleJassType versionType = globals.registerHandleType("version");
			final HandleJassType itemtypeType = globals.registerHandleType("itemtype");
			globals.registerHandleType("texttag");
			final HandleJassType attacktypeType = globals.registerHandleType("attacktype");
			final HandleJassType damagetypeType = globals.registerHandleType("damagetype");
			final HandleJassType weapontypeType = globals.registerHandleType("weapontype");
			final HandleJassType soundtypeType = globals.registerHandleType("soundtype");
			globals.registerHandleType("lightning");
			final HandleJassType pathingtypeType = globals.registerHandleType("pathingtype");
			globals.registerHandleType("image");
			globals.registerHandleType("ubersplat");
			globals.registerHandleType("hashtable");
			globals.registerHandleType("framehandle");
			globals.registerHandleType("minimapicon");

			// Warsmash Ability API
			globals.registerHandleType("abilitytype");
			globals.registerHandleType("orderbutton");
			globals.registerHandleType("orderbuttontype");
			globals.registerHandleType("behavior");
			globals.registerHandleType("behaviorexpr");
			globals.registerHandleType("iconui");

			// Warsmash Ability API 2 "Ability Builder ported to Jass"
			globals.registerHandleType("abilitytypeleveldata");
			globals.registerHandleType("targettype");
			globals.registerHandleType("targettypes");
			globals.registerHandleType("texttagconfigtype");
			globals.registerHandleType("activeability");
			globals.registerHandleType("localstore");
			globals.registerHandleType("destructablebuff");
			globals.registerHandleType("abtimeofdayevent");
			globals.registerHandleType("worldeditordatatype");
			globals.registerHandleType("gameobject");
			globals.registerHandleType("projectile");
			globals.registerHandleType("abilityprojectile");
			globals.registerHandleType("nonstackingstatbonus");
			globals.registerHandleType("nonstackingstatbonustype");
			globals.registerHandleType("statemod");
			globals.registerHandleType("statemodtype");
			globals.registerHandleType("datafieldletter");
			globals.registerHandleType("autocasttype");
			globals.registerHandleType("abconftype");
			globals.registerHandleType("abilitybuilderconfiguration");
			globals.registerHandleType("abtimer");
			globals.registerHandleType("abilitydisabletype");
			globals.registerHandleType("resourcetype");
			globals.registerHandleType("intexpr");

			// Warsmash Ability API 3
			globals.registerHandleType("handlelist");
			globals.registerHandleType("behaviorcategory");
			globals.registerHandleType("abilitycategory");
			globals.registerHandleType("stringlist");
			globals.registerHandleType("abilitytarget");
			globals.registerHandleType("abilitytargetvisitor");

			globals.registerHandleType("primarytag");
			globals.registerHandleType("primarytags");
			globals.registerHandleType("secondarytag");
			globals.registerHandleType("secondarytags");

			globals.registerHandleType("rangedbehavior");
			globals.registerHandleType("abstractrangedbehavior");

			ConversionNatives.registerTypingNatives(jassProgramVisitor, raceType, alliancetypeType, racepreferenceType, igamestateType,
					fgamestateType, playerstateType, playerscoreType, playergameresultType, unitstateType,
					aidifficultyType, gameeventType, playereventType, playeruniteventType, uniteventType, limitopType,
					widgeteventType, dialogeventType, unittypeType, gamespeedType, gamedifficultyType, gametypeType,
					mapflagType, mapvisibilityType, mapsettingType, mapdensityType, mapcontrolType, playerslotstateType,
					volumegroupType, camerafieldType, playercolorType, placementType, startlocprioType,
					raritycontrolType, blendmodeType, texmapflagsType, effecttypeType, fogstateType, versionType,
					itemtypeType, attacktypeType, damagetypeType, weapontypeType, soundtypeType, pathingtypeType,
					war3MapViewer.getGameData().getRaceManager());
			ConversionNatives.registerConversionAndStringNatives(jassProgramVisitor, war3MapViewer.getGameUI());
			final War3MapConfig mapConfig = war3MapViewer.getMapConfig();
			ConfigNatives.register(jassProgramVisitor, mapConfig, startlocprioType, gametypeType, placementType,
					gamespeedType, gamedifficultyType, mapdensityType, locationType, playerType, playercolorType,
					mapcontrolType, playerslotstateType, this.simulation, this.simulation.getHandleIdAllocator(),
					war3MapViewer.getGameUI());
			new ScriptNatives(this).register();
			new TimerNatives(this).register();
			new GroupNatives(this).register();
			new OrderNatives(this).register();
			new PlayerNatives(this).register();
			new RegionNatives(this).register();
			new TriggerNatives(this).register();
			new QuestNatives(this).register();
			new InterfaceNatives(this).register();
			new DestructableNatives(this).register();
			new ItemNatives(this).register();
			new WorldNatives(this).register();
			new CameraNatives(this).register();
			new SoundNatives(this).register();
			new EffectNatives(this).register();
			new HashtableNatives(this).register();
			new AINatives(this).register();
			new UnitNatives(this).register();
			new HeroNatives(this).register();
			new AbilityNatives(this).register();
			new AbilityBehaviorNatives(this).register();
			new AbilityDataNatives(this).register();
			new ProjectileBuffNatives(this).register();
			CampaignNatives.register(jassProgramVisitor, war3MapViewer, meleeUI);
			BlzNatives.register(jassProgramVisitor, war3MapViewer);
			CustomMapNatives.register(jassProgramVisitor, war3MapViewer);
			ScriptObjectNatives.register(jassProgramVisitor, war3MapViewer);
		}

		public void loadSavedGame(final War3MapViewer viewer, final FileHandle file) throws IOException {
			this.simulation.setGlobalScope(this.jassProgramVisitor.getGlobals());
			SavedGame.load(viewer, file, this.aiScriptStarter);
		}

		private void scriptFailed(final Exception error) {
			if ((error != null) && (this.scriptError == null)) {
				this.scriptError = error;
			}
		}

		/**
		 * Why the scripts do not compile, or null. The original game does not start such a map: it shows an error
		 * instead of playing the map without its script.
		 */
		public Exception getScriptError() {
			return this.scriptError;
		}

		public void main() {
			this.simulation.setGlobalScope(this.jassProgramVisitor.getGlobals());
			// The JASS ability layer is optional: abilities_main exists only when JassFileList names
			// Scripts\abilitiesDefaults.j.
			if (this.jassProgramVisitor.getGlobals().getUserFunctionInstructionPtr("abilities_main") != null) {
				try {
					final JassThread abilitiesThread = this.jassProgramVisitor.getGlobals()
							.createThread("abilities_main", Collections.emptyList(), TriggerExecutionScope.EMPTY);
					this.jassProgramVisitor.getGlobals().queueThread(abilitiesThread);
				}
				catch (final Exception exc) {
					Log.error(new JassException(this.jassProgramVisitor.getGlobals(),
							"Exception on Line " + this.jassProgramVisitor.getGlobals().getLineNumber(), exc));
				}
			}
			try {
				final JassThread mainThread = this.jassProgramVisitor.getGlobals().createThread("main",
						Collections.emptyList(), TriggerExecutionScope.EMPTY);
				mainThread.setSleepEndsThread(true);
				this.jassProgramVisitor.getGlobals().queueThread(mainThread);
			}
			catch (final Exception exc) {
				throw new JassException(this.jassProgramVisitor.getGlobals(),
						"Exception on Line " + this.jassProgramVisitor.getGlobals().getLineNumber(), exc);
			}
			// Automated test scripts added through JassFileList define test_main; it runs after the map's main.
			if (this.jassProgramVisitor.getGlobals().getUserFunctionInstructionPtr("test_main") != null) {
				final JassThread testThread = this.jassProgramVisitor.getGlobals().createThread("test_main",
						Collections.emptyList(), TriggerExecutionScope.EMPTY);
				this.jassProgramVisitor.getGlobals().queueThread(testThread);
			}
		}

	}

	public static final class ConfigEnvironment {

		private final GameUI gameUI;
		private Element skin;
		private final JassProgram jassProgramVisitor;

		private ConfigEnvironment(final JassProgram jassProgramVisitor, final DataSource dataSource,
				final Viewport uiViewport, final Scene uiScene, final GameUI gameUI, final War3MapConfig mapConfig) {
			this.jassProgramVisitor = jassProgramVisitor;
			this.gameUI = gameUI;
			final Rectangle tempRect = new Rectangle();
			final GlobalScope globals = jassProgramVisitor.getGlobalScope();
			final HandleJassType agentType = globals.registerHandleType("agent");
			final HandleJassType eventType = globals.registerHandleType("event");
			final HandleJassType playerType = globals.registerHandleType("player");
			final HandleJassType widgetType = globals.registerHandleType("widget");
			final HandleJassType unitType = globals.registerHandleType("unit");
			final HandleJassType destructableType = globals.registerHandleType("destructable");
			final HandleJassType itemType = globals.registerHandleType("item");
			final HandleJassType abilityType = globals.registerHandleType("ability");
			final HandleJassType buffType = globals.registerHandleType("buff");
			final HandleJassType forceType = globals.registerHandleType("force");
			final HandleJassType groupType = globals.registerHandleType("group");
			final HandleJassType triggerType = globals.registerHandleType("trigger");
			final HandleJassType triggerconditionType = globals.registerHandleType("triggercondition");
			final HandleJassType triggeractionType = globals.registerHandleType("triggeraction");
			final HandleJassType timerType = globals.registerHandleType("timer");
			final HandleJassType locationType = globals.registerHandleType("location");
			final HandleJassType regionType = globals.registerHandleType("region");
			final HandleJassType rectType = globals.registerHandleType("rect");
			final HandleJassType boolexprType = globals.registerHandleType("boolexpr");
			final HandleJassType soundType = globals.registerHandleType("sound");
			final HandleJassType conditionfuncType = globals.registerHandleType("conditionfunc");
			final HandleJassType filterfuncType = globals.registerHandleType("filterfunc");
			final HandleJassType unitpoolType = globals.registerHandleType("unitpool");
			final HandleJassType itempoolType = globals.registerHandleType("itempool");
			final HandleJassType raceType = globals.registerHandleType("race");
			final HandleJassType alliancetypeType = globals.registerHandleType("alliancetype");
			final HandleJassType racepreferenceType = globals.registerHandleType("racepreference");
			final HandleJassType gamestateType = globals.registerHandleType("gamestate");
			final HandleJassType igamestateType = globals.registerHandleType("igamestate");
			final HandleJassType fgamestateType = globals.registerHandleType("fgamestate");
			final HandleJassType playerstateType = globals.registerHandleType("playerstate");
			final HandleJassType playerscoreType = globals.registerHandleType("playerscore");
			final HandleJassType playergameresultType = globals.registerHandleType("playergameresult");
			final HandleJassType unitstateType = globals.registerHandleType("unitstate");
			final HandleJassType aidifficultyType = globals.registerHandleType("aidifficulty");
			final HandleJassType eventidType = globals.registerHandleType("eventid");
			final HandleJassType gameeventType = globals.registerHandleType("gameevent");
			final HandleJassType playereventType = globals.registerHandleType("playerevent");
			final HandleJassType playeruniteventType = globals.registerHandleType("playerunitevent");
			final HandleJassType uniteventType = globals.registerHandleType("unitevent");
			final HandleJassType limitopType = globals.registerHandleType("limitop");
			final HandleJassType widgeteventType = globals.registerHandleType("widgetevent");
			final HandleJassType dialogeventType = globals.registerHandleType("dialogevent");
			final HandleJassType unittypeType = globals.registerHandleType("unittype");
			final HandleJassType gamespeedType = globals.registerHandleType("gamespeed");
			final HandleJassType gamedifficultyType = globals.registerHandleType("gamedifficulty");
			final HandleJassType gametypeType = globals.registerHandleType("gametype");
			final HandleJassType mapflagType = globals.registerHandleType("mapflag");
			final HandleJassType mapvisibilityType = globals.registerHandleType("mapvisibility");
			final HandleJassType mapsettingType = globals.registerHandleType("mapsetting");
			final HandleJassType mapdensityType = globals.registerHandleType("mapdensity");
			final HandleJassType mapcontrolType = globals.registerHandleType("mapcontrol");
			final HandleJassType playerslotstateType = globals.registerHandleType("playerslotstate");
			final HandleJassType volumegroupType = globals.registerHandleType("volumegroup");
			final HandleJassType camerafieldType = globals.registerHandleType("camerafield");
			final HandleJassType camerasetupType = globals.registerHandleType("camerasetup");
			final HandleJassType playercolorType = globals.registerHandleType("playercolor");
			final HandleJassType placementType = globals.registerHandleType("placement");
			final HandleJassType startlocprioType = globals.registerHandleType("startlocprio");
			final HandleJassType raritycontrolType = globals.registerHandleType("raritycontrol");
			final HandleJassType blendmodeType = globals.registerHandleType("blendmode");
			final HandleJassType texmapflagsType = globals.registerHandleType("texmapflags");
			final HandleJassType effectType = globals.registerHandleType("effect");
			final HandleJassType effecttypeType = globals.registerHandleType("effecttype");
			final HandleJassType weathereffectType = globals.registerHandleType("weathereffect");
			final HandleJassType terraindeformationType = globals.registerHandleType("terraindeformation");
			final HandleJassType fogstateType = globals.registerHandleType("fogstate");
			final HandleJassType fogmodifierType = globals.registerHandleType("fogmodifier");
			final HandleJassType dialogType = globals.registerHandleType("dialog");
			final HandleJassType buttonType = globals.registerHandleType("button");
			final HandleJassType questType = globals.registerHandleType("quest");
			final HandleJassType questitemType = globals.registerHandleType("questitem");
			final HandleJassType defeatconditionType = globals.registerHandleType("defeatcondition");
			final HandleJassType timerdialogType = globals.registerHandleType("timerdialog");
			final HandleJassType leaderboardType = globals.registerHandleType("leaderboard");
			final HandleJassType multiboardType = globals.registerHandleType("multiboard");
			final HandleJassType multiboarditemType = globals.registerHandleType("multiboarditem");
			final HandleJassType trackableType = globals.registerHandleType("trackable");
			final HandleJassType gamecacheType = globals.registerHandleType("gamecache");
			final HandleJassType versionType = globals.registerHandleType("version");
			final HandleJassType itemtypeType = globals.registerHandleType("itemtype");
			final HandleJassType texttagType = globals.registerHandleType("texttag");
			final HandleJassType attacktypeType = globals.registerHandleType("attacktype");
			final HandleJassType damagetypeType = globals.registerHandleType("damagetype");
			final HandleJassType weapontypeType = globals.registerHandleType("weapontype");
			final HandleJassType soundtypeType = globals.registerHandleType("soundtype");
			final HandleJassType lightningType = globals.registerHandleType("lightning");
			final HandleJassType pathingtypeType = globals.registerHandleType("pathingtype");
			final HandleJassType imageType = globals.registerHandleType("image");
			final HandleJassType ubersplatType = globals.registerHandleType("ubersplat");
			final HandleJassType hashtableType = globals.registerHandleType("hashtable");
			final HandleJassType frameHandleType = globals.registerHandleType("framehandle");
			final HandleJassType minimapiconType = globals.registerHandleType("minimapicon");

			// Warsmash Ability API
			final HandleJassType abilitytypeType = globals.registerHandleType("abilitytype");
			final HandleJassType orderbuttonType = globals.registerHandleType("orderbutton");
			final HandleJassType orderbuttontypeType = globals.registerHandleType("orderbuttontype");
			final HandleJassType abilitybehaviorType = globals.registerHandleType("behavior");
			final HandleJassType behaviorexprType = globals.registerHandleType("behaviorexpr");
			final HandleJassType iconuiType = globals.registerHandleType("iconui");

			// Warsmash Ability API 2 "Ability Builder ported to Jass"
			final HandleJassType abilitytypeleveldataType = globals.registerHandleType("abilitytypeleveldata");
			final HandleJassType targettypeType = globals.registerHandleType("targettype");
			final HandleJassType targettypesType = globals.registerHandleType("targettypes");
			final HandleJassType texttagconfigtypeType = globals.registerHandleType("texttagconfigtype");
			final HandleJassType activeabilityType = globals.registerHandleType("activeability");
			final HandleJassType localstoreType = globals.registerHandleType("localstore");
			final HandleJassType destructablebuffType = globals.registerHandleType("destructablebuff");
			final HandleJassType abtimeofdayeventType = globals.registerHandleType("abtimeofdayevent");
			final HandleJassType worldeditordatatypeType = globals.registerHandleType("worldeditordatatype");
			final HandleJassType gameobjectType = globals.registerHandleType("gameobject");
			final HandleJassType projectileType = globals.registerHandleType("projectile");
			final HandleJassType nonstackingstatbuffType = globals.registerHandleType("nonstackingstatbuff");
			final HandleJassType nonstackingstatbufftypeType = globals.registerHandleType("nonstackingstatbufftype");
			final HandleJassType datafieldletterType = globals.registerHandleType("datafieldletter");
			final HandleJassType autocasttypeType = globals.registerHandleType("autocasttype");
			final HandleJassType abconftypeType = globals.registerHandleType("abconftype");
			final HandleJassType abilitybuilderconfigurationType = globals
					.registerHandleType("abilitybuilderconfiguration");
			final HandleJassType abtimerType = globals.registerHandleType("abtimer");
			final HandleJassType abilitydisabletypeType = globals.registerHandleType("abilitydisabletype");
			final HandleJassType resourcetypeType = globals.registerHandleType("resourcetype");
			final HandleJassType intexprType = globals.registerHandleType("intexpr");

			// Warsmash Ability API 3
			final HandleJassType handlelistType = globals.registerHandleType("handlelist");
			final HandleJassType behaviorcategoryType = globals.registerHandleType("behaviorcategory");
			final HandleJassType abilitycategoryType = globals.registerHandleType("abilitycategory");
			final HandleJassType stringlistType = globals.registerHandleType("stringlist");
			final HandleJassType abilitytargetType = globals.registerHandleType("abilitytarget");
			final HandleJassType abilitytargetvisitorType = globals.registerHandleType("abilitytargetvisitor");

			final HandleJassType primarytagType = globals.registerHandleType("primarytag");
			final HandleJassType primarytagsType = globals.registerHandleType("primarytags");
			final HandleJassType secondarytagType = globals.registerHandleType("secondarytag");
			final HandleJassType secondarytagsType = globals.registerHandleType("secondarytags");

			final HandleJassType rangedbehaviorType = globals.registerHandleType("rangedbehavior");
			final HandleJassType abstractrangedbehaviorType = globals.registerHandleType("abstractrangedbehavior");

			ConversionNatives.registerTypingNatives(jassProgramVisitor, raceType, alliancetypeType, racepreferenceType, igamestateType,
					fgamestateType, playerstateType, playerscoreType, playergameresultType, unitstateType,
					aidifficultyType, gameeventType, playereventType, playeruniteventType, uniteventType, limitopType,
					widgeteventType, dialogeventType, unittypeType, gamespeedType, gamedifficultyType, gametypeType,
					mapflagType, mapvisibilityType, mapsettingType, mapdensityType, mapcontrolType, playerslotstateType,
					volumegroupType, camerafieldType, playercolorType, placementType, startlocprioType,
					raritycontrolType, blendmodeType, texmapflagsType, effecttypeType, fogstateType, versionType,
					itemtypeType, attacktypeType, damagetypeType, weapontypeType, soundtypeType, pathingtypeType,
					GameDataConstants.current().getRaceManager());
			ConversionNatives.registerConversionAndStringNatives(jassProgramVisitor, gameUI);
			final HandleIdAllocator configHandleIds = new HandleIdAllocator();
			ConfigNatives.register(jassProgramVisitor, mapConfig, startlocprioType, gametypeType, placementType,
					gamespeedType, gamedifficultyType, mapdensityType, locationType, playerType, playercolorType,
					mapcontrolType, playerslotstateType, mapConfig, configHandleIds, gameUI);
			// Blizzard.j global initializers create timers and groups while the config phase loads it.
			jassProgramVisitor.getJassNativeManager().createNative("CreateTimer",
					(arguments, globalScope, triggerScope) -> new HandleJassValue(timerType,
							new CTimerJass(configHandleIds.createHandleId())));
			jassProgramVisitor.getJassNativeManager().createNative("CreateGroup",
					(arguments, globalScope, triggerScope) -> new HandleJassValue(groupType,
							new UnitGroup(configHandleIds.createHandleId())));
			jassProgramVisitor.getJassNativeManager().createNative("SetPlayerState",
					(arguments, globalScope, triggerScope) -> {
						final CBasePlayer player = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
						final CPlayerState whichPlayerState = nullable(arguments, 1,
								ObjectJassValueVisitor.getInstance());
						if ((player != null) && (whichPlayerState != null)) {
							player.setConfigPlayerState(whichPlayerState,
									arguments.get(2).visit(IntegerJassValueVisitor.getInstance()));
						}
						return null;
					});

		}

		public void config() {
			try {
				final JassThread configThread = this.jassProgramVisitor.getGlobals().createThread("config",
						Collections.emptyList(), TriggerExecutionScope.EMPTY);
				this.jassProgramVisitor.getGlobals().runThreadUntilCompletion(configThread);
			}
			catch (final Exception exc) {
				throw new JassException(this.jassProgramVisitor.getGlobals(),
						"Exception on Line " + this.jassProgramVisitor.getGlobals().getLineNumber(), exc);
			}
		}

	}

	static void doPreloadScript(final DataSource dataSource, final Viewport uiViewport, final Scene uiScene,
			final War3MapViewer war3MapViewer, final String filename, final WarsmashUI meleeUI,
			final String[] originalFiles, final JassProgram jassProgramVisitor, final String mainFunction, final boolean runMain) {
		final Integer prevPtr = jassProgramVisitor.getGlobals().getUserFunctionInstructionPtr(mainFunction);
		try {
			try (InputStreamReader reader = new InputStreamReader(dataSource.getResourceAsStream(filename),
					StandardCharsets.UTF_8)) {
				final SmashJassParser smashJassParser = new SmashJassParser(reader);
				smashJassParser.scanAndParse(filename, jassProgramVisitor);
			}
			jassProgramVisitor.initialize();
		}
		catch (final Exception e) {
			Log.error(e);
			JassLog.report(e);
		}
		final Integer loadedPtr = jassProgramVisitor.getGlobals().getUserFunctionInstructionPtr(mainFunction);
		war3MapViewer.addLoadedScript(new SavedGame.AiEntry(-1, filename, mainFunction));
		if (runMain && (prevPtr != loadedPtr)) {
			// else we didn't actually load a new fxn
			try {
				final JassThread preloadThread = jassProgramVisitor.getGlobals().createThread(mainFunction,
						Collections.emptyList(), TriggerExecutionScope.EMPTY);
				jassProgramVisitor.getGlobals().queueThread(preloadThread);
			}
			catch (final Exception e) {
				throw new JassException(jassProgramVisitor.getGlobals(), "Unable to run: " + filename, e);
			}
		}
	}

	static void doAIScript(final DataSource dataSource, final Viewport uiViewport, final Scene uiScene,
			final War3MapViewer war3MapViewer, final CPlayer player, final String filename, final WarsmashUI meleeUI,
			final String[] originalFiles, final JassProgram jassProgramVisitor, final CSimulation game,
			final String mainFunction, final boolean runMain) {
		final JassProgram jassProgram = new JassProgram();
		jassProgram.inheritFrom(jassProgramVisitor);
		final JassAIEnvironment jassAIEnvironment = new JassAIEnvironment(jassProgram, dataSource, uiViewport, uiScene,
				game, player.getId(), meleeUI);
		// common.ai declares these again, so the AI program needs its own implementations.
		AINatives.registerSharedCommonAIFunctions(jassProgram, war3MapViewer, meleeUI, game);
		final Integer prevPtr = jassProgram.getGlobals().getUserFunctionInstructionPtr(mainFunction);
		// Blizzard.j names the melee scripts without their folder; every AI script runs on top of common.ai.
		final String scriptPath = dataSource.has(filename) ? filename : "Scripts\\" + filename;
		if (!dataSource.has(scriptPath)) {
			// Measured: the original ignores an AI script that is not there.
			return;
		}
		try {
			for (final String aiFile : new String[] { "Scripts\\common.ai", scriptPath }) {
				try (InputStreamReader reader = new InputStreamReader(dataSource.getResourceAsStream(aiFile),
						StandardCharsets.UTF_8)) {
					final SmashJassParser smashJassParser = new SmashJassParser(reader);
					smashJassParser.scanAndParse(aiFile, jassProgram);
				}
			}
			jassProgram.initialize();
		}
		catch (final Exception e) {
			// The original runs nothing of an AI script that does not compile, as with the map's own script.
			Log.error(e);
			JassLog.report(e);
			return;
		}
		final Integer loadedPtr = jassProgram.getGlobals().getUserFunctionInstructionPtr(mainFunction);
		if (runMain && (prevPtr != loadedPtr)) {
			// else we didn't actually load a new fxn
			try {
				final JassThread preloadThread = jassProgram.getGlobals().createThread(mainFunction,
						Collections.emptyList(), TriggerExecutionScope.EMPTY);
				jassProgram.getGlobals().queueThread(preloadThread);
			}
			catch (final Exception e) {
				throw new JassException(jassProgram.getGlobals(), "Unable to run: " + filename, e);
			}
		}
		jassAIEnvironment.setScript(filename, mainFunction);
		war3MapViewer.addLoadedScript(new SavedGame.AiEntry(player.getId(), filename, mainFunction));
		player.setAiScript(jassAIEnvironment);
	}

}
