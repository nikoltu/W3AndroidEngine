package com.etheller.warsmash.viewer5.handlers.w3x;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.common.FetchDataTypeName;
import com.etheller.warsmash.parsers.w3x.War3Map;
import com.etheller.warsmash.parsers.w3x.objectdata.Warcraft3MapObjectData;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.units.ObjectData;
import com.etheller.warsmash.units.StandardObjectData;
import com.etheller.warsmash.units.custom.WTS;
import com.etheller.warsmash.units.manager.MutableObjectData.WorldEditorDataType;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.MappedData;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WorldEditStrings;
import com.etheller.warsmash.viewer5.FogSettings;
import com.etheller.warsmash.viewer5.GenericResource;
import com.etheller.warsmash.viewer5.Texture;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer.SelectionCircleSize;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.Terrain;
import com.etheller.warsmash.viewer5.handlers.w3x.lightning.LightningEffectModel;
import com.etheller.warsmash.viewer5.handlers.w3x.lightning.LightningEffectModelHandler;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.WeatherType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.sound.KeyedSounds;

/**
 * Loads the data tables, sounds and lists a map viewer needs before and while a map loads.
 */
final class MapViewerLoading {
	private MapViewerLoading() {
	}

	static void loadSLKs(final War3MapViewer viewer, final WorldEditStrings worldEditStrings) throws IOException {
		final GenericResource terrain = viewer.loadMapGeneric("TerrainArt\\Terrain.slk", FetchDataTypeName.SLK,
				War3MapViewer.stringDataCallback);
		final GenericResource cliffTypes = viewer.loadMapGeneric("TerrainArt\\CliffTypes.slk", FetchDataTypeName.SLK,
				War3MapViewer.stringDataCallback);
		final GenericResource water = viewer.loadMapGeneric("TerrainArt\\Water.slk", FetchDataTypeName.SLK,
				War3MapViewer.stringDataCallback);

		// == when loaded, which is always in our system ==
		viewer.terrainData.load(terrain.data.toString());
		viewer.cliffTypesData.load(cliffTypes.data.toString());
		viewer.waterData.load(water.data.toString());
		// emit terrain loaded??

		final GenericResource doodads = viewer.loadMapGeneric("Doodads\\Doodads.slk", FetchDataTypeName.SLK,
				War3MapViewer.stringDataCallback);
		final GenericResource doodadMetaData = viewer.loadMapGeneric("Doodads\\DoodadMetaData.slk", FetchDataTypeName.SLK,
				War3MapViewer.stringDataCallback);
		final GenericResource destructableData = viewer.loadMapGeneric("Units\\DestructableData.slk", FetchDataTypeName.SLK,
				War3MapViewer.stringDataCallback);
		final GenericResource destructableMetaData = viewer.loadMapGeneric("Units\\DestructableMetaData.slk",
				FetchDataTypeName.SLK, War3MapViewer.stringDataCallback);

		// == when loaded, which is always in our system ==
		viewer.doodadsAndDestructiblesLoaded = true;
		viewer.doodadsData.load(doodads.data.toString());
		viewer.doodadMetaData.load(doodadMetaData.data.toString());
		viewer.doodadsData.load(destructableData.data.toString());
		viewer.destructableMetaData.load(destructableData.data.toString());
		// emit doodads loaded

		final GenericResource unitData = viewer.loadMapGeneric("Units\\UnitData.slk", FetchDataTypeName.SLK,
				War3MapViewer.stringDataCallback);
		final GenericResource unitUi = viewer.loadMapGeneric("Units\\unitUI.slk", FetchDataTypeName.SLK, War3MapViewer.stringDataCallback);
		final GenericResource itemData = viewer.loadMapGeneric("Units\\ItemData.slk", FetchDataTypeName.SLK,
				War3MapViewer.stringDataCallback);
		final GenericResource unitMetaData = viewer.loadMapGeneric("Units\\UnitMetaData.slk", FetchDataTypeName.SLK,
				War3MapViewer.stringDataCallback);

		loadLightningData(viewer, worldEditStrings);
		loadWeatherData(viewer, worldEditStrings);

		// == when loaded, which is always in our system ==
		viewer.unitsAndItemsLoaded = true;
		viewer.unitsData.load(unitData.data.toString());
		viewer.unitsData.load(unitUi.data.toString());
		viewer.unitsData.load(itemData.data.toString());
		viewer.unitMetaData.load(unitMetaData.data.toString());
		// emit loaded

		viewer.unitAckSoundsTable = new DataTable(worldEditStrings);
		try (InputStream terrainSlkStream = viewer.dataSource.getResourceAsStream("UI\\SoundInfo\\UnitAckSounds.slk")) {
			viewer.unitAckSoundsTable.readSLK(terrainSlkStream);
		}
		DialogueLabels.resolve(viewer.dataSource, viewer.unitAckSoundsTable, worldEditStrings);
		viewer.unitCombatSoundsTable = new DataTable(worldEditStrings);
		try (InputStream terrainSlkStream = viewer.dataSource
				.getResourceAsStream("UI\\SoundInfo\\UnitCombatSounds.slk")) {
			viewer.unitCombatSoundsTable.readSLK(terrainSlkStream);
		}
		viewer.animSoundsTable = new DataTable(worldEditStrings);
		try (InputStream stream = viewer.dataSource.getResourceAsStream("UI\\SoundInfo\\AnimSounds.slk")) {
			if (stream != null) {
				viewer.animSoundsTable.readSLK(stream);
			}
		}
		viewer.miscData = new DataTable(worldEditStrings);
		try (InputStream miscDataTxtStream = viewer.dataSource.getResourceAsStream("UI\\MiscData.txt")) {
			viewer.miscData.readTXT(miscDataTxtStream, true);
		}
		try (InputStream miscDataTxtStream = viewer.dataSource.getResourceAsStream("Units\\MiscData.txt")) {
			viewer.miscData.readTXT(miscDataTxtStream, true);
		}
		try (InputStream miscDataTxtStream = viewer.dataSource.getResourceAsStream("Units\\MiscGame.txt")) {
			viewer.miscData.readTXT(miscDataTxtStream, true);
		}
		try (InputStream miscDataTxtStream = viewer.dataSource.getResourceAsStream("UI\\MiscUI.txt")) {
			viewer.miscData.readTXT(miscDataTxtStream, true);
		}
		try (InputStream miscDataTxtStream = viewer.dataSource.getResourceAsStream("UI\\SoundInfo\\MiscData.txt")) {
			viewer.miscData.readTXT(miscDataTxtStream, true);
		}
		if (viewer.dataSource.has("war3mapMisc.txt")) {
			try (InputStream miscDataTxtStream = viewer.dataSource.getResourceAsStream("war3mapMisc.txt")) {
				viewer.miscData.readTXT(miscDataTxtStream, true);
			}
		}
		viewer.misc = viewer.miscData.get("Misc");
		// TODO Find the upkeep constants inside the assets files ?????
		if (!viewer.misc.hasField("UpkeepUsage")) {
			viewer.misc.setField("UpkeepUsage", "50,80,10000,10000,10000,10000,10000,10000,10000,10000");
		}
		if (!viewer.misc.hasField("UpkeepGoldTax")) {
			viewer.misc.setField("UpkeepGoldTax", "0.00,0.30,0.60,0.60,0.60,0.60,0.60,0.60,0.60,0.60");
		}
		if (!viewer.misc.hasField("UpkeepLumberTax")) {
			viewer.misc.setField("UpkeepLumberTax", "0.00,0.00,0.00,0.00,0.00,0.00,0.00,0.00,0.00,0.00");
		}
		final Element zFogElement = viewer.miscData.get("DefaultZFog");
		if (zFogElement != null) {
			viewer.defaultFogSettings = FogSettings.parse(zFogElement, viewer.getGameData().getGameVersion());
		}
		final Element light = viewer.miscData.get("Light");
		final float lightX = light.getFieldFloatValue("Direction", 0);
		final float lightY = light.getFieldFloatValue("Direction", 1);
		final float lightZ = light.getFieldFloatValue("Direction", 2);
		viewer.lightDirection = new Vector3(lightX, lightY, lightZ).nor();
		viewer.unitGlobalStrings = new DataTable(worldEditStrings);
		try (InputStream miscDataTxtStream = viewer.dataSource.getResourceAsStream("Units\\UnitGlobalStrings.txt")) {
			viewer.unitGlobalStrings.readTXT(miscDataTxtStream, true);
		}
		final Element categories = viewer.unitGlobalStrings.get("Categories");
		for (final CUnitClassification unitClassification : CUnitClassification.values()) {
			if (unitClassification.getLocaleKey() != null) {
				final String displayName = categories.getField(unitClassification.getLocaleKey());
				unitClassification.setDisplayName(displayName);
			}
		}
		viewer.selectionCircleSizes.clear();
		final Element selectionCircleData = viewer.miscData.get("SelectionCircle");
		final int selectionCircleNumSizes = selectionCircleData.getFieldValue("NumSizes");
		for (int i = 0; i < selectionCircleNumSizes; i++) {
			final String indexString = i < 10 ? "0" + i : Integer.toString(i);
			final float size = selectionCircleData.getFieldFloatValue("Size" + indexString);
			final String texture = selectionCircleData.getField("Texture" + indexString);
			final String textureDotted = selectionCircleData.getField("TextureDotted" + indexString);
			viewer.selectionCircleSizes.add(new SelectionCircleSize(size, texture, textureDotted));
		}
		viewer.selectionCircleScaleFactor = selectionCircleData.getFieldFloatValue("ScaleFactor");
		viewer.imageWalkableZOffset = selectionCircleData.getFieldValue("ImageWalkableZOffset");
		viewer.selectionCircleColorFriend = War3MapViewer.parseColor(selectionCircleData, "ColorFriend");
		viewer.selectionCircleColorNeutral = War3MapViewer.parseColor(selectionCircleData, "ColorNeutral");
		viewer.selectionCircleColorEnemy = War3MapViewer.parseColor(selectionCircleData, "ColorEnemy");

		viewer.uiSoundsTable = new DataTable(worldEditStrings);
		try (InputStream miscDataTxtStream = viewer.dataSource.getResourceAsStream("UI\\SoundInfo\\UISounds.slk")) {
			viewer.uiSoundsTable.readSLK(miscDataTxtStream);
		}
		try (InputStream miscDataTxtStream = viewer.dataSource.getResourceAsStream("UI\\SoundInfo\\AmbienceSounds.slk")) {
			viewer.uiSoundsTable.readSLK(miscDataTxtStream);
		}
		try (InputStream miscDataTxtStream = viewer.dataSource.getResourceAsStream("UI\\SoundInfo\\AbilitySounds.slk")) {
			viewer.uiSoundsTable.readSLK(miscDataTxtStream);
		}
		// CreateMIDISound labels (day and night ambience) live here; 3.0 ships them as audio files.
		try (InputStream miscDataTxtStream = viewer.dataSource.getResourceAsStream("UI\\SoundInfo\\AmbientMusic.slk")) {
			if (miscDataTxtStream != null) {
				viewer.uiSoundsTable.readSLK(miscDataTxtStream);
			}
		}
	}

	static TextTagConfig parseTextTagConfig(final Element misc, final String name) {
		final Color color = War3MapViewer.parseColor(misc, name + "TextColor");
		final String velocityKey = name + "TextVelocity";
		final float[] velocity = { misc.getFieldFloatValue(velocityKey, 0), misc.getFieldFloatValue(velocityKey, 1),
				misc.getFieldFloatValue(velocityKey, 2) };
		final float lifetime = misc.getFieldFloatValue(name + "TextLifetime");
		final float fadeStart = misc.getFieldFloatValue(name + "TextFadeStart");
		final float height = misc.getFieldFloatValue(name + "TextHeight");
		return new TextTagConfig(color, velocity, lifetime, fadeStart, height);
	}

	static void loadLightningData(final War3MapViewer viewer, final WorldEditStrings worldEditStrings) throws IOException {
		viewer.lightningDataTable = new DataTable(worldEditStrings);
		try (InputStream slkStream = viewer.dataSource.getResourceAsStream("Splats\\LightningData.slk")) {
			viewer.lightningDataTable.readSLK(slkStream);
		}
		viewer.lightningTypeToModel = new HashMap<>();
		final LightningEffectModelHandler lightningEffectModelHandler = new LightningEffectModelHandler();
		lightningEffectModelHandler.load(viewer);
		for (final String key : viewer.lightningDataTable.keySet()) {
			final War3ID typeId = War3ID.fromString(key);
			final Element element = viewer.lightningDataTable.get(key);
			final String textureFilePath = element.getField("Dir") + "\\" + element.getField("file");
			final float avgSegLen = element.getFieldFloatValue("AvgSegLen");
			final float width = element.getFieldFloatValue("Width");
			final float r = element.getFieldFloatValue("R");
			final float g = element.getFieldFloatValue("G");
			final float b = element.getFieldFloatValue("B");
			final float a = element.getFieldFloatValue("A");
			final float noiseScale = element.getFieldFloatValue("NoiseScale");
			final float texCoordScale = element.getFieldFloatValue("TexCoordScale");
			final float duration = element.getFieldFloatValue("Duration");
			final int version = element.getFieldValue("version");
			final LightningEffectModel lightningEffectModel = new LightningEffectModel(lightningEffectModelHandler,
					viewer, ".lightning", viewer.mapPathSolver, "<lightning:" + key + ">", typeId, textureFilePath,
					avgSegLen, width, new float[] { r / 255f, g / 255f, b / 255f, a / 255f }, noiseScale, texCoordScale,
					duration, version);
			lightningEffectModel.loadData(null, null);
			viewer.lightningTypeToModel.put(typeId, lightningEffectModel);
		}
	}

	static void loadWeatherData(final War3MapViewer viewer, final WorldEditStrings worldEditStrings) throws IOException {
		final DataTable weatherTable = new DataTable(worldEditStrings);
		try (InputStream slkStream = viewer.dataSource.getResourceAsStream("TerrainArt\\Weather.slk")) {
			if (slkStream == null) {
				viewer.weatherTypes = new HashMap<>();
				return;
			}
			weatherTable.readSLK(slkStream);
		}
		viewer.weatherTypes = new HashMap<>();
		for (final String key : weatherTable.keySet()) {
			viewer.weatherTypes.put(War3ID.fromString(key), new WeatherType(key, weatherTable.get(key)));
		}
	}

	static DataTable loadWorldEditData(final War3MapViewer viewer, final War3Map map) {
		final StandardObjectData standardObjectData = new StandardObjectData(map);
		viewer.worldEditData = standardObjectData.getWorldEditData();
		return viewer.worldEditData;
	}

	static WTS preloadWTS(final War3MapViewer viewer, final War3Map map) {
		try {
			viewer.preloadedWTS = Warcraft3MapObjectData.loadWTS(map);
			return viewer.preloadedWTS;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	static void loadSounds(final War3MapViewer viewer) {
		viewer.uiSounds = new KeyedSounds(viewer.uiSoundsTable, viewer.mapMpq);
	}

	/**
	 * Decodes the sounds the first minutes of a game play as soon as the map's script has made its starting units in
	 * the first game step, before the player can click anything: the answers to selecting and
	 * ordering the local player's units, the selection and ready answers of every unit type they can build, train or
	 * upgrade into, the selection answer of the other players' units, and these interface sounds. Android plays a
	 * sound only once it is decoded, so the first play of a sound decoded on demand came late or not at all (a unit's
	 * first answer). The rest of a unit type's answers are decoded when first played: all of them for a whole tech
	 * tree took over three times the memory. Only what is heard changes; scripts wait by the length of a file, which comes
	 * from its header and never from this.
	 */
	static void preloadSoundsAtStart(final War3MapViewer viewer, final Collection<String> interfaceSoundKeys) {
		viewer.soundsToPreload = interfaceSoundKeys;
	}

	static void preloadSounds(final War3MapViewer viewer, final Collection<String> interfaceSoundKeys) {
		final long started = System.currentTimeMillis();
		final Set<War3ID> own = new LinkedHashSet<>();
		final Set<War3ID> others = new LinkedHashSet<>();
		for (final CUnit unit : viewer.simulation.getUnits()) {
			(unit.getPlayerIndex() == viewer.localPlayerIndex ? own : others).add(unit.getTypeId());
		}
		final Set<War3ID> made = new LinkedHashSet<>(own);
		addMadeUnitTypes(viewer, made);
		made.removeAll(own);
		others.removeAll(own);
		for (final War3ID typeId : own) {
			final UnitSoundset soundset = viewer.renderUnitTypeData.getSoundset(typeId);
			if (soundset != null) {
				soundset.preload();
			}
		}
		for (final War3ID typeId : made) {
			final UnitSoundset soundset = viewer.renderUnitTypeData.getSoundset(typeId);
			if (soundset != null) {
				soundset.ready.preload();
				soundset.what.preload();
			}
		}
		for (final War3ID typeId : others) {
			final UnitSoundset soundset = viewer.renderUnitTypeData.getSoundset(typeId);
			if (soundset != null) {
				soundset.what.preload();
			}
		}
		for (final String key : interfaceSoundKeys) {
			viewer.uiSounds.getSound(key).preload();
		}
		Extensions.audio.finishLoading(War3MapViewer.SOUND_PRELOAD_WAIT_MILLIS);
		Log.info("Sounds preloaded for " + own.size() + " unit types of the player, " + made.size()
				+ " they can make and " + others.size() + " others in " + (System.currentTimeMillis() - started)
				+ " ms");
	}

	static void addMadeUnitTypes(final War3MapViewer viewer, final Set<War3ID> typeIds) {
		final ArrayDeque<War3ID> open = new ArrayDeque<>(typeIds);
		while (!open.isEmpty()) {
			final CUnitType unitType = viewer.simulation.getUnitData().getUnitType(open.poll());
			if (unitType == null) {
				continue;
			}
			for (final List<War3ID> made : Arrays.asList(unitType.getStructuresBuilt(), unitType.getUnitsTrained(),
					unitType.getUpgradesTo())) {
				for (final War3ID typeId : made) {
					if (typeIds.add(typeId)) {
						open.add(typeId);
					}
				}
			}
		}
	}

	static void applyModificationFile(final War3MapViewer viewer, final MappedData doodadsData2, final MappedData doodadMetaData2,
			final ObjectData destructibles, final WorldEditorDataType dataType) {
		// TODO condense ported MappedData from Ghostwolf and MutableObjectData from
		// Retera

	}
}
