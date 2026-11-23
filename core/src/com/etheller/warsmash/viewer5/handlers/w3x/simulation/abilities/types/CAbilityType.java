package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.AbstractCAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.ScriptFieldOverlay;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.impl.CAbilityTypeItemStatBonusLevelData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;

public abstract class CAbilityType<TYPE_LEVEL_DATA extends CAbilityTypeLevelData> {
	/* alias: defines which ability editor ability to use */
	private final War3ID alias;
	/* code: defines which CAbility class to use */
	private final War3ID code;

	private final List<TYPE_LEVEL_DATA> levelData;
	private GameObject levelDataSource;
	private LevelDataFactory<TYPE_LEVEL_DATA> levelDataFactory;

	/** Builds the level data of one level (counted from 1) from ability data, the type's own or an overlay of it. */
	public interface LevelDataFactory<TYPE_LEVEL_DATA> {
		TYPE_LEVEL_DATA create(GameObject abilityData, int level);
	}

	public CAbilityType(final War3ID alias, final War3ID code, final List<TYPE_LEVEL_DATA> levelData) {
		this.alias = alias;
		this.code = code;
		this.levelData = levelData;
	}

	public War3ID getAlias() {
		return this.alias;
	}

	public War3ID getCode() {
		return this.code;
	}

	public EnumSet<CTargetType> getTargetsAllowed(final int level) {
		return getLevelData(level).getTargetsAllowed();
	}

	public int getLevelCount() {
		return levelData.size();
	}

	protected final List<TYPE_LEVEL_DATA> getLevelData() {
		return this.levelData;
	}

	protected final TYPE_LEVEL_DATA getLevelData(final int level) {
		return this.levelData.get(level);
	}

	/**
	 * Lets abilities of this type with values a script set on them take their level data from the data with those
	 * values in front, instead of the level data every ability of the type shares.
	 */
	public final void setLevelDataSource(final GameObject source, final LevelDataFactory<TYPE_LEVEL_DATA> factory) {
		this.levelDataSource = source;
		this.levelDataFactory = factory;
	}

	/** Whether {@link #setLevel} reads an ability's own script values, so it applies them when called again. */
	public final boolean appliesScriptFields() {
		return this.levelDataFactory != null;
	}

	/** The shared level data, or for an ability a script set values on, its own copy built from them. */
	protected final TYPE_LEVEL_DATA getLevelData(final CAbility ability, final int level) {
		if ((this.levelDataFactory != null) && (ability instanceof AbstractCAbility)) {
			final GameObject overlay = ScriptFieldOverlay.of((AbstractCAbility) ability, this.levelDataSource);
			if (overlay != this.levelDataSource) {
				return this.levelDataFactory.create(overlay, level + 1);
			}
		}
		return getLevelData(level);
	}

	/**
	 * The level data of every level for an ability a script set values on, built from them, or null when the
	 * ability has none and so reads the shared level data.
	 */
	protected final List<TYPE_LEVEL_DATA> getScriptLevelData(final CAbility ability) {
		if ((this.levelDataFactory == null) || !(ability instanceof AbstractCAbility)) {
			return null;
		}
		final GameObject overlay = ScriptFieldOverlay.of((AbstractCAbility) ability, this.levelDataSource);
		if (overlay == this.levelDataSource) {
			return null;
		}
		final List<TYPE_LEVEL_DATA> result = new ArrayList<>(this.levelData.size());
		for (int level = 1; level <= this.levelData.size(); level++) {
			result.add(this.levelDataFactory.create(overlay, level));
		}
		return result;
	}

	/**
	 * The attribute bonus an item ability of this type gives (strength, agility, intelligence), or null when it gives none.
	 */
	public CAbilityTypeItemStatBonusLevelData getItemStatBonus() {
		return null;
	}

	public abstract CAbility createAbility(int handleId);

	public abstract void setLevel(CSimulation game, CUnit unit, CLevelingAbility existingAbility, int level);

}
