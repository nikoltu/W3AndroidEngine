package com.etheller.warsmash.parsers.w3x.objectdata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.GraphicsSet;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.ObjectData;
import com.etheller.warsmash.units.StandardObjectData;
import com.etheller.warsmash.units.StandardObjectData.WarcraftData;
import com.etheller.warsmash.units.collapsed.CollapsedObjectData;
import com.etheller.warsmash.units.custom.WTS;
import com.etheller.warsmash.units.custom.WTSFile;
import com.etheller.warsmash.units.manager.MutableObjectData.WorldEditorDataType;
import com.etheller.warsmash.util.WorldEditStrings;

public final class Warcraft3MapRuntimeObjectData {
	private final ObjectData units;
	private final ObjectData items;
	private final ObjectData destructibles;
	private final ObjectData doodads;
	private final ObjectData abilities;
	private final ObjectData buffs;
	private final ObjectData upgrades;
	private final DataTable standardUpgradeEffectMeta;
	private final List<ObjectData> datas;
	private transient Map<WorldEditorDataType, ObjectData> typeToData = new HashMap<>();
	private final WTS wts;

	public Warcraft3MapRuntimeObjectData(final ObjectData units, final ObjectData items, final ObjectData destructibles,
			final ObjectData doodads, final ObjectData abilities, final ObjectData buffs, final ObjectData upgrades,
			final DataTable standardUpgradeEffectMeta, final WTS wts) {
		this.units = units;
		this.items = items;
		this.destructibles = destructibles;
		this.doodads = doodads;
		this.abilities = abilities;
		this.buffs = buffs;
		this.upgrades = upgrades;
		this.standardUpgradeEffectMeta = standardUpgradeEffectMeta;
		this.datas = new ArrayList<>();
		this.datas.add(units);
		this.typeToData.put(WorldEditorDataType.UNITS, units);
		this.datas.add(items);
		this.typeToData.put(WorldEditorDataType.ITEM, items);
		this.datas.add(destructibles);
		this.typeToData.put(WorldEditorDataType.DESTRUCTIBLES, destructibles);
		this.datas.add(doodads);
		this.typeToData.put(WorldEditorDataType.DOODADS, doodads);
		this.datas.add(abilities);
		this.typeToData.put(WorldEditorDataType.ABILITIES, abilities);
		this.datas.add(buffs);
		this.typeToData.put(WorldEditorDataType.BUFFS_EFFECTS, buffs);
		this.datas.add(upgrades);
		this.typeToData.put(WorldEditorDataType.UPGRADES, upgrades);
		for (final ObjectData data : this.datas) {
		}
		this.wts = wts;
	}

	public ObjectData getDataByType(final WorldEditorDataType type) {
		return this.typeToData.get(type);
	}

	public ObjectData getUnits() {
		return this.units;
	}

	public ObjectData getItems() {
		return this.items;
	}

	public ObjectData getDestructibles() {
		return this.destructibles;
	}

	public ObjectData getDoodads() {
		return this.doodads;
	}

	public ObjectData getAbilities() {
		return this.abilities;
	}

	public ObjectData getBuffs() {
		return this.buffs;
	}

	public ObjectData getUpgrades() {
		return this.upgrades;
	}

	public DataTable getStandardUpgradeEffectMeta() {
		return this.standardUpgradeEffectMeta;
	}

	public List<ObjectData> getDatas() {
		return this.datas;
	}

	public WTS getWts() {
		return this.wts;
	}

	private static WTS loadWTS(final DataSource dataSource, final String name) throws IOException {
		final WTS wts = dataSource.has(name) ? new WTSFile(dataSource.getResourceAsStream(name)) : WTS.DO_NOTHING;
		return wts;
	}

	public static WTS loadWTS(final DataSource dataSource) throws IOException {
		return loadWTS(dataSource, "war3map.wts");
	}

	public static WTS loadCampaignWTS(final DataSource dataSource) throws IOException {
		return loadWTS(dataSource, "war3campaign.wts");
	}

	public static Warcraft3MapRuntimeObjectData load(final DataSource dataSource, final boolean inlineWTS)
			throws IOException {
		final WTS wts = loadWTS(dataSource);
		return load(dataSource, inlineWTS, wts);
	}

	public static Warcraft3MapRuntimeObjectData load(final DataSource dataSource, final boolean inlineWTS,
			final WTS wts) throws IOException {
		final WTS campaignWTS = loadCampaignWTS(dataSource);
		return load(dataSource, inlineWTS, wts, campaignWTS);
	}

	public static Warcraft3MapRuntimeObjectData load(final DataSource dataSource, final boolean inlineWTS,
			final WTS wts, final WTS campaignWTS) throws IOException {
		return load(dataSource, inlineWTS, wts, campaignWTS, GraphicsSet.CLASSIC);
	}

	public static Warcraft3MapRuntimeObjectData load(final DataSource dataSource, final boolean inlineWTS,
			final WTS wts, final WTS campaignWTS, final GraphicsSet graphics) throws IOException {

		final StandardObjectData standardObjectData = new StandardObjectData(dataSource, graphics);
		final WarcraftData standardUnits = standardObjectData.getStandardUnits();
		final WarcraftData standardItems = standardObjectData.getStandardItems();
		final WarcraftData standardDoodads = standardObjectData.getStandardDoodads();
		final WarcraftData standardDestructables = standardObjectData.getStandardDestructables();
		final WarcraftData abilities = standardObjectData.getStandardAbilities();
		final WarcraftData standardAbilityBuffs = standardObjectData.getStandardAbilityBuffs();
		final WarcraftData standardUpgrades = standardObjectData.getStandardUpgrades();

		final DataTable standardUnitMeta = standardObjectData.getStandardUnitMeta();
		final DataTable standardDoodadMeta = standardObjectData.getStandardDoodadMeta();
		final DataTable standardDestructableMeta = standardObjectData.getStandardDestructableMeta();
		final DataTable abilityMeta = standardObjectData.getStandardAbilityMeta();
		final DataTable standardAbilityBuffMeta = standardObjectData.getStandardAbilityBuffMeta();
		final DataTable standardUpgradeMeta = standardObjectData.getStandardUpgradeMeta();
		final DataTable standardUpgradeEffectMeta = standardObjectData.getStandardUpgradeEffectMeta();

		final MapObjectChangesets changesets = MapObjectChangesets.read(dataSource, inlineWTS, wts, campaignWTS,
				standardUnits, standardItems);

		final WorldEditStrings worldEditStrings = standardObjectData.getWorldEditStrings();
		CollapsedObjectData.apply(worldEditStrings, WorldEditorDataType.UNITS, standardUnits, standardUnitMeta,
				changesets.units);
		CollapsedObjectData.apply(worldEditStrings, WorldEditorDataType.ITEM, standardItems, standardUnitMeta,
				changesets.items);
		CollapsedObjectData.apply(worldEditStrings, WorldEditorDataType.DOODADS, standardDoodads, standardDoodadMeta,
				changesets.doodads);
		CollapsedObjectData.apply(worldEditStrings, WorldEditorDataType.DESTRUCTIBLES, standardDestructables,
				standardDestructableMeta, changesets.destructables);
		CollapsedObjectData.apply(worldEditStrings, WorldEditorDataType.ABILITIES, abilities, abilityMeta,
				changesets.abilities);
		CollapsedObjectData.apply(worldEditStrings, WorldEditorDataType.BUFFS_EFFECTS, standardAbilityBuffs,
				standardAbilityBuffMeta, changesets.buffs);
		CollapsedObjectData.apply(worldEditStrings, WorldEditorDataType.UPGRADES, standardUpgrades, standardUpgradeMeta,
				changesets.upgrades);

		return new Warcraft3MapRuntimeObjectData(standardUnits, standardItems, standardDestructables, standardDoodads,
				abilities, standardAbilityBuffs, standardUpgrades, standardUpgradeEffectMeta, wts);
	}
}