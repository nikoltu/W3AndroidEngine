package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.TimeUtils;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.SpriteFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextureFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.ImageUtils;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.mdx.ReplaceableIds;
import com.etheller.warsmash.viewer5.handlers.tga.TgaFile;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

/**
 * The minimap with its buttons, signals, pings, creep camps and the attack alerts that
 * ping it.
 */
final class MeleeUIMinimapPanel {
	private final List<MinimapPing> minimapPings = new ArrayList<>();
	private final ArrayDeque<SpriteFrame> freeMinimapPings = new ArrayDeque<>();
	private Runnable minimapTerrainRedraw = () -> {
	};
	private Runnable minimapCreepRedraw = () -> {
	};
	private Runnable formationRedraw = () -> {
	};
	private final List<SpriteFrame> creepCampFrames = new ArrayList<>();
	private final List<String> creepCampModels = new ArrayList<>();
	/** The creep camps as the local player last saw them: x, y, level sum. */
	private final List<float[]> knownCreepCamps = new ArrayList<>();
	/** Creeps that existed at the start or were already grouped; only units made later can start a new camp. */
	private final Set<CUnit> accountedCreeps = new HashSet<>();
	private boolean creepCampsStarted;
	private long creepCampsRefreshMillis;

	private final Vector3 minimapCornerTemp = new Vector3();

	private final Map<Integer, float[]> lastAttackAlerts = new HashMap<>();
	private float attackNotifyDelay = -1;
	private float attackNotifyRange;

	// Measured on 1.29: the ring spans about a sixth of the minimap's width.
	private static final float MINIMAP_PING_SCALE = 1f;
	private static final float MINIMAP_CREEP_CAMP_SCALE = 1f;
	// Minimap-Ping sequences: Birth, Stand, Death, then the same three for "AllyPing".
	private static final int MINIMAP_PING_BIRTH = 0;
	private static final int MINIMAP_PING_STAND = 1;
	private static final int MINIMAP_PING_DEATH = 2;
	private static final int MINIMAP_ALLY_PING_BIRTH = 3;
	private static final int MINIMAP_ALLY_PING_STAND = 4;
	private static final int MINIMAP_ALLY_PING_DEATH = 5;
	// Measured on 1.29: the signal is gone about 5 s after the click, its death included.
	private static final float MINIMAP_SIGNAL_SECONDS = 4.6f;
	private static final float ATTACK_PING_SECONDS = 4.6f;
	private static final long CREEP_CAMPS_REFRESH_MILLIS = 1000;

	final MeleeUI ui;

	MeleeUIMinimapPanel(final MeleeUI ui) {
		this.ui = ui;
	}

	MeleeUIMinimap createMinimap(final War3MapViewer war3MapViewer) {
		final Rectangle minimapDisplayArea = new Rectangle(18.75f * this.ui.widthRatioCorrection,
				13.75f * this.ui.heightRatioCorrection, 278.75f * this.ui.widthRatioCorrection,
				276.25f * this.ui.heightRatioCorrection);
		Texture minimapTexture = null;
		if (war3MapViewer.dataSource.has("war3mapMap.tga")) {
			try {
				minimapTexture = ImageUtils.getTextureNoColorCorrection(TgaFile.readTGAPixels("war3mapMap.tga",
						war3MapViewer.dataSource.getResourceAsStream("war3mapMap.tga")));
			}
			catch (final IOException e) {
				Log.error("Could not load minimap TGA file");
				Log.error(e);
			}
		}
		else if (war3MapViewer.dataSource.has("war3mapMap.blp")) {
			minimapTexture = ImageUtils.getAnyExtensionTexture(war3MapViewer.dataSource, "war3mapMap.blp");
		}
		final Texture[] teamColors = new Texture[this.ui.war3MapViewer.getGameData().getMaxPlayers()];
		for (int i = 0; i < teamColors.length; i++) {
			teamColors[i] = ImageUtils.getAnyExtensionTexture(war3MapViewer.dataSource,
					"ReplaceableTextures\\" + ReplaceableIds.getPathString(1) + ReplaceableIds.getIdString(i) + ".blp");
		}
		final Texture[] specialIcons = new Texture[6];
		specialIcons[0] = ImageUtils.getAnyExtensionTexture(war3MapViewer.mapMpq, "UI\\MiniMap\\minimap-gold.blp");
		specialIcons[1] = ImageUtils.getAnyExtensionTexture(war3MapViewer.mapMpq,
				"UI\\MiniMap\\minimap-neutralbuilding.blp");
		specialIcons[2] = ImageUtils.getAnyExtensionTexture(war3MapViewer.mapMpq, "UI\\MiniMap\\minimap-hero.blp");
		specialIcons[3] = ImageUtils.getAnyExtensionTexture(war3MapViewer.mapMpq,
				"UI\\MiniMap\\minimap-gold-entangled.blp");
		specialIcons[4] = ImageUtils.getAnyExtensionTexture(war3MapViewer.mapMpq,
				"UI\\MiniMap\\minimap-gold-haunted.blp");
		specialIcons[5] = ImageUtils.getAnyExtensionTexture(war3MapViewer.mapMpq, "Textures\\white.blp");

		final Rectangle playableMapArea = war3MapViewer.terrain.getPlayableMapArea();
		final MeleeUIMinimap minimap = new MeleeUIMinimap(minimapDisplayArea, playableMapArea, minimapTexture, teamColors,
				specialIcons);
		minimap.setMaskedAreasPartiallyVisible(war3MapViewer.isMaskedAreasPartiallyVisible());
		return minimap;
	}

	void createMinimapButtons() {
		this.ui.minimapButtonBar = this.ui.rootFrame.createSimpleFrame("MinimapButtonBar", this.ui.consoleUI, 0);
		final SimpleButtonFrame signalButton = (SimpleButtonFrame) this.ui.rootFrame
				.getFrameByName("MinimapSignalButton", 0);
		if (signalButton != null) {
			signalButton.setOnClick(this::beginMinimapSignal);
			this.ui.tooltips.setFrameTooltip(signalButton, "MINIMAPSIGNALTOOLTIP");
		}
		final GameOptions options = GameOptions.get();
		setupMinimapToggle("MiniMapTerrainButton", () -> options.isMinimapTerrain() ? 0 : 1,
				(state) -> setMinimapTerrain(state == 0), (listener) -> this.minimapTerrainRedraw = listener,
				"MiniMapTerrainButtonActive", "MiniMapTerrainButtonInactive");
		final AllyColorFilter allyColorFilter = this.ui.war3MapViewer.getAllyColorFilter();
		setupMinimapToggle("MiniMapAllyButton", allyColorFilter::getState,
				this::setAllyColorFilterByPlayer, allyColorFilter::addListener,
				"MiniMapAllyButtonOff", "MiniMapAllyButtonInactive", "MiniMapAllyButtonActive");
		setupMinimapToggle("MiniMapCreepButton", () -> options.isMinimapCreeps() ? 0 : 1,
				(state) -> setMinimapCreeps(state == 0), (listener) -> this.minimapCreepRedraw = listener,
				"MiniMapCreepButtonActive", "MiniMapCreepButtonInactive");
		setupMinimapToggle("FormationButton", () -> options.isFormationMovement() ? 0 : 1,
				(state) -> setFormationMovement(state == 0), (listener) -> this.formationRedraw = listener,
				"MiniMapFormationOnButton", "MiniMapFormationOffButton");
		this.ui.tooltips.setFrameTooltip(this.ui.rootFrame.getFrameByName("MiniMapTerrainButton", 0),
				"MINIMAPTERRAINTOOLTIP");
		this.ui.tooltips.setFrameTooltip(this.ui.rootFrame.getFrameByName("MiniMapAllyButton", 0),
				"MINIMAPALLYCOLORTOOLTIP");
		this.ui.tooltips.setFrameTooltip(this.ui.rootFrame.getFrameByName("MiniMapCreepButton", 0),
				"MINIMAPCREEPCOLORTOOLTIP");
		this.ui.tooltips.setFrameTooltip(this.ui.rootFrame.getFrameByName("FormationButton", 0), "FORMATION_TOOLTIP");
		applyFilterButtonsEnabled();
	}

	void beginMinimapSignal() {
		if (this.ui.activeCommand != null) {
			this.ui.activeCommandUnit = null;
			this.ui.activeCommand = null;
			this.ui.activeCommandOrderId = -1;
			if (this.ui.draggingItem != null) {
				this.ui.commands.setDraggingItem(null);
			}
			this.ui.commands.clearAndRepopulateCommandCard();
		}
		this.ui.minimapSignalTargeting = true;
	}

	/**
	 * The signal shows on the minimaps of the sender and of the sender's allies: the sender's at once, the allies' when
	 * the signal reaches them as the sender's command. Measured on 1.29: the signal is the ping model's "AllyPing"
	 * exclamation mark in the sender's player colour.
	 */
	void sendMinimapSignal(final float x, final float y) {
		this.ui.minimapSignalTargeting = false;
		showMinimapSignal(this.ui.war3MapViewer.getLocalPlayerIndex(), x, y);
		this.ui.unitOrderListener.minimapSignal(x, y);
	}

	void showMinimapSignal(final int playerIndex, final float x, final float y) {
		final int rgba = MeleeChat.playerTextColorRgba(
				this.ui.war3MapViewer.simulation.getPlayer(playerIndex).getColor());
		pingMinimap(x, y, MINIMAP_SIGNAL_SECONDS, (rgba >>> 24) & 0xFF, (rgba >>> 16) & 0xFF, (rgba >>> 8) & 0xFF,
				MINIMAP_ALLY_PING_BIRTH, MINIMAP_ALLY_PING_STAND, MINIMAP_ALLY_PING_DEATH);
		this.ui.war3MapViewer.getUiSounds().getSound("MapPing").play(this.ui.uiScene.audioContext, 0, 0, 0);
	}

	void setMinimapTerrain(final boolean shown) {
		GameOptions.get().setMinimapTerrain(shown);
		GameOptions.get().save();
		this.ui.meleeUIMinimap.setTerrainVisible(shown);
		this.minimapTerrainRedraw.run();
	}

	/** Measured on 1.29: the mode the player picks is kept for the next games (allyFilter). */
	void setAllyColorFilterByPlayer(final int state) {
		final int mode = state % AllyColorFilter.MODE_COUNT;
		GameOptions.get().setAllyFilter(mode);
		GameOptions.get().save();
		this.ui.war3MapViewer.getAllyColorFilter().setState(mode);
	}

	boolean allyFilterEnabled = true;
	boolean creepFilterEnabled = true;

	/** EnableMinimapFilterButtons: the ally and creep buttons grey out and their Alt keys stop working. */
	void enableFilterButtons(final boolean ally, final boolean creep) {
		this.allyFilterEnabled = ally;
		this.creepFilterEnabled = creep;
		applyFilterButtonsEnabled();
	}

	void applyFilterButtonsEnabled() {
		setButtonEnabled("MiniMapAllyButton", this.allyFilterEnabled);
		setButtonEnabled("MiniMapCreepButton", this.creepFilterEnabled);
	}

	private void setButtonEnabled(final String name, final boolean enabled) {
		final UIFrame button = this.ui.rootFrame.getFrameByName(name, 0);
		if (button instanceof SimpleButtonFrame) {
			((SimpleButtonFrame) button).setEnabled(enabled);
		}
	}

	/** SetCreepCampFilterState: the creep button's state, without keeping it as the player's own choice. */
	void setCreepCampFilterState(final boolean shown) {
		GameOptions.get().setMinimapCreeps(shown);
		this.creepCampsRefreshMillis = 0;
		this.minimapCreepRedraw.run();
	}

	void setMinimapCreeps(final boolean shown) {
		GameOptions.get().setMinimapCreeps(shown);
		GameOptions.get().save();
		this.creepCampsRefreshMillis = 0;
		this.minimapCreepRedraw.run();
	}

	void setFormationMovement(final boolean on) {
		GameOptions.get().setFormationMovement(on);
		GameOptions.get().save();
		this.formationRedraw.run();
	}

	/** The formation preference for an order given now: Alt inverts it unless the player turned that off. */
	boolean isFormationOrder() {
		final GameOptions options = GameOptions.get();
		return options.isFormationMovement() != (options.isFormationToggle() && MeleeUIInput.isAltDown());
	}

	/**
	 * Creep camps on the minimap: neutral hostile units within CreepCampPathingCellDistance pathing cells of each
	 * other form one camp, drawn at its centre and sized and colored by the camp's total level.
	 */
	void updateCreepCamps(final long currentMillis) {
		// Measured on 1.29: only maps whose masked areas are partially visible (melee maps) show creep camps; on
		// campaign maps the minimap has none, even for creeps in sight.
		final boolean shown = GameOptions.get().isMinimapCreeps() && this.ui.meleeUIMinimap.isVisible()
				&& !this.ui.scoreScreen.isVisible() && this.ui.war3MapViewer.isMaskedAreasPartiallyVisible();
		if (!shown) {
			for (final SpriteFrame frame : this.creepCampFrames) {
				frame.setVisible(false);
			}
			return;
		}
		if (currentMillis < this.creepCampsRefreshMillis) {
			for (final SpriteFrame frame : this.creepCampFrames) {
				if (frame.isVisible() && frame.isSequenceEnded()) {
					frame.setSequence(0);
				}
			}
			return;
		}
		final CSimulation simulation = this.ui.war3MapViewer.simulation;
		if (!this.creepCampsStarted && (simulation.getGameTurnTick() == 0)) {
			// The map's script has not placed its creeps yet.
			return;
		}
		this.creepCampsRefreshMillis = currentMillis + CREEP_CAMPS_REFRESH_MILLIS;
		final Element misc = this.ui.war3MapViewer.miscData.get("Misc");
		final Element minimap = this.ui.war3MapViewer.miscData.get("Minimap");
		final float linkDistance = (misc != null) && misc.hasField("CreepCampPathingCellDistance")
				? misc.getFieldFloatValue("CreepCampPathingCellDistance") * 32
				: 26 * 32;
		final float linkDistanceSq = linkDistance * linkDistance;
		final int localPlayer = this.ui.war3MapViewer.getLocalPlayerIndex();
		final boolean observer = this.ui.war3MapViewer.isObserverView();
		final List<CUnit> creeps = new ArrayList<>();
		final List<float[]> creepPoints = new ArrayList<>();
		for (final CUnit unit : simulation.getUnits()) {
			if (!unit.isDead() && !unit.isHidden() && !unit.isBuilding()
					&& simulation.isNeutralAggressive(unit.getPlayerIndex())) {
				creeps.add(unit);
				creepPoints.add(new float[] { unit.getX(), unit.getY(), unit.getUnitType().getLevel(),
						(observer || unit.isVisible(simulation, localPlayer)) ? 1 : 0 });
			}
		}
		final float middleThreshold = minimapField(minimap, "MinimapMiddleCampThreshold", 10);
		final float toughThreshold = minimapField(minimap, "MinimapToughCampThreshold", 20);
		final float middleScale = minimapField(minimap, "MinimapMiddleCampScale", 1.3f);
		final Color weakColor = minimapColor(minimap, "MinimapWeakCampColor", new Color(0, 200 / 255f, 0, 1));
		final Color middleColor = minimapColor(minimap, "MinimapMiddleCampColor",
				new Color(1, 128 / 255f, 0, 1));
		final Color toughColor = minimapColor(minimap, "MinimapToughCampColor", new Color(220 / 255f, 0, 0, 1));
		// Measured on 1.29: the camps on the minimap at the start are those the map placed, also the ones the melee
		// setup removes at the start locations, so that the minimap does not tell which start locations are taken.
		// After that a known camp keeps its place and level whatever happens to its creeps (creeps killed in the fog
		// or the mask, seen killed, or made into it) and is forgotten only when its place is visible and empty;
		// creeps made where no camp is known show as a new camp at once, always as a weak one whatever their levels.
		if (!this.creepCampsStarted) {
			this.creepCampsStarted = true;
			final List<float[]> initialCreeps = this.ui.war3MapViewer.takeInitialCreeps();
			// A loaded game has no record of the placed creeps: its camps start from the creeps there are.
			final List<float[]> startPoints = new ArrayList<>();
			for (final float[] creep : initialCreeps.isEmpty() ? creepPoints : initialCreeps) {
				startPoints.add(new float[] { creep[0], creep[1], creep[2], 1 });
			}
			this.knownCreepCamps.addAll(groupCreepCamps(startPoints, linkDistanceSq));
		}
		// Creeps that chase or wander away from their camp must not show as a new camp: the player would see
		// movement in the fog.
		final List<float[]> newCreepPoints = new ArrayList<>();
		for (int i = 0; i < creeps.size(); i++) {
			if (this.accountedCreeps.add(creeps.get(i))) {
				newCreepPoints.add(creepPoints.get(i));
			}
		}
		this.accountedCreeps.removeIf(CUnit::isDead);
		for (final float[] camp : groupCreepCamps(newCreepPoints, linkDistanceSq)) {
			if (!isKnownCreepCamp(camp, linkDistanceSq)) {
				camp[2] = 0;
				this.knownCreepCamps.add(camp);
			}
		}
		final CPlayerFogOfWar fogOfWar = this.ui.war3MapViewer.getFogOfWar();
		final PathingGrid pathingGrid = simulation.getPathingGrid();
		final java.util.Iterator<float[]> knownIterator = this.knownCreepCamps.iterator();
		while (knownIterator.hasNext()) {
			final float[] known = knownIterator.next();
			if ((fogOfWar != null) && CFogState.VISIBLE.equals(fogOfWar.getFogState(simulation,
					pathingGrid.getFogOfWarIndexX(known[0]), pathingGrid.getFogOfWarIndexY(known[1])))) {
				boolean present = false;
				for (final CUnit creep : creeps) {
					if (distanceSquared(known, creep.getX(), creep.getY()) <= linkDistanceSq) {
						present = true;
						break;
					}
				}
				if (!present) {
					knownIterator.remove();
				}
			}
		}
		final int campCount = this.knownCreepCamps.size();
		for (int c = 0; c < campCount; c++) {
			final float[] known = this.knownCreepCamps.get(c);
			final float sumX = known[0];
			final float sumY = known[1];
			final int members = 1;
			final int levels = (int) known[2];
			while (this.creepCampFrames.size() <= c) {
				final SpriteFrame frame = (SpriteFrame) this.ui.rootFrame.createFrameByType("SPRITE",
						"SmashMinimapCreepCamp", this.ui.rootFrame, "", 0);
				frame.setZDepth(0.5f);
				this.creepCampFrames.add(frame);
				this.creepCampModels.add(null);
			}
			final SpriteFrame frame = this.creepCampFrames.get(c);
			final boolean tough = levels >= toughThreshold;
			final String model = this.ui.rootFrame.getSkinField(tough ? "MinimapCreepCampTough" : "MinimapCreepCamp");
			if (!model.equals(this.creepCampModels.get(c))) {
				this.ui.rootFrame.setSpriteFrameModel(frame, model);
				this.creepCampModels.set(c, model);
			}
			final boolean middle = !tough && (levels >= middleThreshold);
			frame.setModelScale(MINIMAP_CREEP_CAMP_SCALE * (middle ? middleScale : 1));
			frame.setVertexColor(tough ? toughColor : (middle ? middleColor : weakColor));
			frame.setFramePointX(FramePoint.LEFT, this.ui.meleeUIMinimap.toMinimapX(sumX / members));
			frame.setFramePointY(FramePoint.BOTTOM, this.ui.meleeUIMinimap.toMinimapY(sumY / members));
			if (!frame.isVisible()) {
				frame.setSequence(0);
				frame.setVisible(true);
			}
		}
		for (int c = campCount; c < this.creepCampFrames.size(); c++) {
			this.creepCampFrames.get(c).setVisible(false);
		}
	}

	/**
	 * Groups creeps (x, y, level, seen) within the link distance of each other into camps: centre x, y, level sum and
	 * whether any of them is seen.
	 */
	private static List<float[]> groupCreepCamps(final List<float[]> creeps, final float linkDistanceSq) {
		final List<float[]> camps = new ArrayList<>();
		final int[] camp = new int[creeps.size()];
		Arrays.fill(camp, -1);
		final ArrayDeque<Integer> open = new ArrayDeque<>();
		for (int i = 0; i < creeps.size(); i++) {
			if (camp[i] != -1) {
				continue;
			}
			camp[i] = camps.size();
			open.add(i);
			float sumX = 0;
			float sumY = 0;
			int members = 0;
			float levels = 0;
			float seen = 0;
			while (!open.isEmpty()) {
				final float[] member = creeps.get(open.poll());
				sumX += member[0];
				sumY += member[1];
				members++;
				levels += member[2];
				seen = Math.max(seen, member[3]);
				for (int j = 0; j < creeps.size(); j++) {
					if ((camp[j] == -1) && (distanceSquared(member, creeps.get(j)[0], creeps.get(j)[1]) <= linkDistanceSq)) {
						camp[j] = camps.size();
						open.add(j);
					}
				}
			}
			camps.add(new float[] { sumX / members, sumY / members, levels, seen });
		}
		return camps;
	}

	private boolean isKnownCreepCamp(final float[] camp, final float linkDistanceSq) {
		for (final float[] known : this.knownCreepCamps) {
			if (distanceSquared(known, camp[0], camp[1]) <= linkDistanceSq) {
				return true;
			}
		}
		return false;
	}

	private static float distanceSquared(final float[] camp, final float x, final float y) {
		final float dx = camp[0] - x;
		final float dy = camp[1] - y;
		return (dx * dx) + (dy * dy);
	}

	private static float minimapField(final Element minimap, final String field, final float fallback) {
		return (minimap != null) && minimap.hasField(field) ? minimap.getFieldFloatValue(field) : fallback;
	}

	private static Color minimapColor(final Element minimap, final String field, final Color fallback) {
		if ((minimap == null) || !minimap.hasField(field)) {
			return fallback;
		}
		final String[] argb = minimap.getField(field).split(",");
		if (argb.length != 4) {
			return fallback;
		}
		return new Color(Integer.parseInt(argb[1].trim()) / 255f, Integer.parseInt(argb[2].trim()) / 255f,
				Integer.parseInt(argb[3].trim()) / 255f, Integer.parseInt(argb[0].trim()) / 255f);
	}

	/** A minimap bar button whose state lives elsewhere: a click advances it, a change from elsewhere redraws it. */
	private void setupMinimapToggle(final String buttonName, final java.util.function.IntSupplier getState,
			final java.util.function.IntConsumer setState,
			final java.util.function.Consumer<Runnable> onStateChanged, final String... statePrefixes) {
		final SimpleButtonFrame button = (SimpleButtonFrame) this.ui.rootFrame.getFrameByName(buttonName, 0);
		if (button == null) {
			return;
		}
		final Runnable applyState = () -> {
			final int current = getState.getAsInt();
			final String prefix = statePrefixes[Math.max(0, Math.min(current, statePrefixes.length - 1))];
			final boolean formation = prefix.startsWith("MiniMapFormation");
			button.setControlBackdrop(createMinimapButtonTexture(button, prefix + "Enabled"));
			button.setControlPushedBackdrop(
					createMinimapButtonTexture(button, prefix + (formation ? "EnabledPushed" : "Pushed")));
			button.setControlDisabledBackdrop(createMinimapButtonTexture(button, prefix + "Disabled"));
		};
		final Runnable redraw = () -> {
			applyState.run();
			button.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
		};
		applyState.run();
		onStateChanged.accept(redraw);
		button.setOnClick(() -> {
			setState.accept((Math.max(0, getState.getAsInt()) + 1) % statePrefixes.length);
			redraw.run();
		});
	}

	private UIFrame createMinimapButtonTexture(final SimpleButtonFrame button, final String skinKey) {
		// Not createTextureFrame: it also adds the frame to the root, which draws it whether or not the button is shown.
		final TextureFrame texture = new TextureFrame(button.getName() + skinKey, button, false,
				TextureFrame.DEFAULT_TEX_COORDS);
		texture.setTexture(this.ui.rootFrame.getSkinField(skinKey), this.ui.rootFrame);
		texture.setSetAllPoints(true);
		return texture;
	}

	/** The minimap outlines the ground the camera sees, as in the original game: the screen corners cast onto the
	 * ground at the camera target's height. */
	void updateMinimapCameraFootprint() {
		final float[] footprint = this.ui.meleeUIMinimap.getCameraFootprint();
		final int width = Gdx.graphics.getWidth();
		final int height = Gdx.graphics.getHeight();
		final int[] cornersX = { 0, width, width, 0 };
		final int[] cornersY = { 0, 0, height, height };
		for (int i = 0; i < 4; i++) {
			this.ui.war3MapViewer.getClickLocationOnZPlane(this.minimapCornerTemp, cornersX[i], cornersY[i],
					this.ui.cameraManager.target.z);
			footprint[i * 2] = this.minimapCornerTemp.x;
			footprint[(i * 2) + 1] = this.minimapCornerTemp.y;
		}
	}

	/** Space goes to where the last alert of the local player happened (unit ready, building or research done). */
	public void noteAlertPosition(final int playerIndex, final float x, final float y) {
		if (playerIndex == this.ui.war3MapViewer.getLocalPlayerIndex()) {
			this.ui.getCameraManager().setQuickPosition(x, y);
		}
	}

	/**
	 * The "under attack" alert, as on 1.29: when an enemy hurts a unit the local player controls (its own, or one of a
	 * player sharing control with it) or a unit of an allied human player out of view, a sound by the local race (town
	 * or unit), a red minimap ping and Space jumping there; for the human ally also a message naming it. Allied
	 * computer and campaign players without shared control raise no alert at all. Not again for the same player within
	 * AttackNotifyDelay seconds and AttackNotifyRange of its last alert, and then Space keeps the last alert's place too.
	 */
	public void unitAttacked(final CUnit unit, final CUnit attacker) {
		final CSimulation simulation = this.ui.war3MapViewer.simulation;
		final int localPlayerIndex = this.ui.war3MapViewer.getLocalPlayerIndex();
		final int owner = unit.getPlayerIndex();
		final CPlayer ownerPlayer = simulation.getPlayer(owner);
		if ((attacker.getPlayerIndex() == owner)
				|| ownerPlayer.hasAlliance(attacker.getPlayerIndex(), CAllianceType.PASSIVE)) {
			return;
		}
		final boolean own = (owner == localPlayerIndex)
				|| ownerPlayer.hasAlliance(localPlayerIndex, CAllianceType.SHARED_CONTROL);
		if (!own && !((ownerPlayer.getController() == CMapControl.USER)
				&& (ownerPlayer.getSlotState() == CPlayerSlotState.PLAYING)
				&& ownerPlayer.hasAlliance(localPlayerIndex, CAllianceType.PASSIVE)
				&& simulation.getPlayer(localPlayerIndex).hasAlliance(owner, CAllianceType.PASSIVE))) {
			return;
		}
		if (unit.isBuilding() && (unit.getAbilityByRawcode(CUnitData.ALARM_ABILITY_ID) == null)) {
			return;
		}
		if (isOnScreen(unit.getX(), unit.getY())) {
			return;
		}
		if (this.attackNotifyDelay < 0) {
			readAttackNotifySettings();
		}
		final float now = simulation.getGameTurnTick() * WarsmashConstants.SIMULATION_STEP_TIME;
		final float[] last = this.lastAttackAlerts.get(owner);
		if (last != null) {
			final float dx = unit.getX() - last[1];
			final float dy = unit.getY() - last[2];
			if (((now - last[0]) < this.attackNotifyDelay)
					&& (((dx * dx) + (dy * dy)) <= (this.attackNotifyRange * this.attackNotifyRange))) {
				return;
			}
		}
		this.lastAttackAlerts.put(owner, new float[] { now, unit.getX(), unit.getY() });
		final boolean town = unit.isBuilding();
		final String soundKey;
		if (own) {
			soundKey = town ? "TownAttackSound" : "UnderAttackSound";
		}
		else {
			soundKey = town ? "AllyTownUnderAttackSound" : "AllyUnderAttackSound";
			final String format = this.ui.rootFrame.getErrorString(town ? "Allytownattack" : "Allyunderattack");
			if ((format != null) && !format.isEmpty()) {
				this.ui.messages.showGameMessage(format.replace("%s", ownerPlayer.getName()),
						MeleeUI.CHAT_MESSAGE_SECONDS);
			}
		}
		if (this.ui.rootFrame.hasSkinField(soundKey)) {
			final UnitSound sound = this.ui.war3MapViewer.getUiSounds()
					.getSound(this.ui.rootFrame.getSkinField(soundKey));
			if (sound != null) {
				sound.play(this.ui.uiScene.audioContext, 0, 0, 0);
			}
		}
		final int[] color = attackPingColor();
		pingMinimap(unit.getX(), unit.getY(), ATTACK_PING_SECONDS, color[0], color[1], color[2], MINIMAP_PING_BIRTH,
				MINIMAP_PING_STAND, MINIMAP_PING_DEATH);
		this.ui.getCameraManager().setQuickPosition(unit.getX(), unit.getY());
	}

	private boolean isOnScreen(final float x, final float y) {
		MeleeUI.clickLocationTemp.set(x, y, this.ui.war3MapViewer.terrain.getGroundHeight(x, y));
		this.ui.war3MapViewer.worldScene.camera.worldToScreen(MeleeUI.screenCoordsVector, MeleeUI.clickLocationTemp);
		return (MeleeUI.screenCoordsVector.x >= 0) && (MeleeUI.screenCoordsVector.x <= Gdx.graphics.getWidth())
				&& (MeleeUI.screenCoordsVector.y >= 0) && (MeleeUI.screenCoordsVector.y <= Gdx.graphics.getHeight());
	}

	private void readAttackNotifySettings() {
		final Element misc = this.ui.war3MapViewer.miscData.get("Misc");
		this.attackNotifyDelay = 30;
		this.attackNotifyRange = 1250;
		if (misc != null) {
			if (misc.hasField("AttackNotifyDelay")) {
				this.attackNotifyDelay = misc.getFieldFloatValue("AttackNotifyDelay");
			}
			if (misc.hasField("AttackNotifyRange")) {
				this.attackNotifyRange = misc.getFieldFloatValue("AttackNotifyRange");
			}
		}
	}

	private int[] pingColor() {
		return pingColor("PingColor", 0, 255, 0);
	}

	private int[] attackPingColor() {
		return pingColor("PingAttackColor", 255, 0, 0);
	}

	private int[] pingColor(final String field, final int red, final int green, final int blue) {
		final Element pingColor = this.ui.war3MapViewer.miscData.get("PingColor");
		final int[] color = { red, green, blue };
		if ((pingColor != null) && pingColor.hasField(field)) {
			final String[] argb = pingColor.getField(field).split(",");
			if (argb.length == 4) {
				for (int i = 0; i < 3; i++) {
					color[i] = Integer.parseInt(argb[i + 1].trim());
				}
			}
		}
		return color;
	}

	/**
	 * Measured on 1.29: PingMinimap shows the model's stand, the ring with its arrows drawn in as notches, in the ping
	 * colour; PingMinimapEx keeps the arrows orbiting wide around the ring, as its birth does.
	 */
	public void pingMinimap(final float x, final float y, final float duration) {
		final int[] color = pingColor();
		pingMinimap(x, y, duration, color[0], color[1], color[2], MINIMAP_PING_STAND, MINIMAP_PING_STAND,
				MINIMAP_PING_DEATH);
	}

	public void pingMinimap(final float x, final float y, final float duration, final int red, final int green,
			final int blue, final boolean extraEffects) {
		pingMinimap(x, y, duration, red, green, blue, MINIMAP_PING_BIRTH, MINIMAP_PING_BIRTH, MINIMAP_PING_DEATH);
	}

	private void pingMinimap(final float x, final float y, final float duration, final int red, final int green,
			final int blue, final int firstSequence, final int sequence, final int deathSequence) {
		SpriteFrame frame = this.freeMinimapPings.pollFirst();
		if (frame == null) {
			frame = (SpriteFrame) this.ui.rootFrame.createFrameByType("SPRITE", "SmashMinimapPing", this.ui.rootFrame,
					"", 0);
			this.ui.rootFrame.setSpriteFrameModel(frame, this.ui.rootFrame.getSkinField("MinimapIndicator"));
			frame.setZDepth(0.5f);
			frame.setModelScale(MINIMAP_PING_SCALE);
		}
		frame.setVertexColor(new Color(red / 255f, green / 255f, blue / 255f, 1f));
		frame.setFramePointX(FramePoint.LEFT, this.ui.meleeUIMinimap.toMinimapX(x));
		frame.setFramePointY(FramePoint.BOTTOM, this.ui.meleeUIMinimap.toMinimapY(y));
		frame.setSequence(firstSequence);
		frame.setVisible(true);
		final MinimapPing ping = new MinimapPing();
		ping.frame = frame;
		ping.sequence = sequence;
		ping.deathSequence = deathSequence;
		ping.expireMillis = TimeUtils.millis() + (long) (duration * 1000);
		this.minimapPings.add(ping);
	}

	void updateMinimapPings(final long currentMillis) {
		for (final Iterator<MinimapPing> iterator = this.minimapPings.iterator(); iterator.hasNext();) {
			final MinimapPing ping = iterator.next();
			if (ping.dying) {
				if (ping.frame.isSequenceEnded()) {
					ping.frame.setVisible(false);
					this.freeMinimapPings.add(ping.frame);
					iterator.remove();
				}
			}
			else if (currentMillis >= ping.expireMillis) {
				ping.dying = true;
				ping.frame.setSequence(ping.deathSequence);
			}
			else if (ping.frame.isSequenceEnded()) {
				ping.frame.setSequence(ping.sequence);
			}
		}
	}

	private static final class MinimapPing {
		private SpriteFrame frame;
		private long expireMillis;
		private int sequence;
		private int deathSequence;
		private boolean dying;
	}
}
