package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.handler;

import java.util.Locale;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitRace;

/**
 * The types random choices pick from, built from one game's data and tileset on first use. Each game has its own
 * ({@link CSimulation#getRandomTypes()}).
 */
public class RandomTypeHandler {
	private List<CUnitType> landCritters = null;
	private List<CUnitType> airCritters = null;
	private List<CUnitType> seaCritters = null;

	private List<CItemType> randomItems = null;

	private void loadCritterLists(CSimulation game) {
		landCritters = new ArrayList<>();
		airCritters = new ArrayList<>();
		seaCritters = new ArrayList<>();
		Collection<CUnitType> types = game.getUnitData().getAllUnitTypesLightweight();
		String tileset = ("" + game.getTileset()).toUpperCase(Locale.ROOT);

		for (CUnitType type : types) {
			if (type.getRace() == CUnitRace.CRITTERS
					&& ("*".equals(type.getTilesets()) || type.getTilesets().indexOf(tileset) >= 0)
					&& !type.isSpecial()) {
				switch (type.getMovementType()) {
				case AMPHIBIOUS:
					seaCritters.add(type);
					break;
				case FLY:
					airCritters.add(type);
					break;
				case FOOT:
				case FOOT_NO_COLLISION:
				case HORSE:
				case HOVER:
					landCritters.add(type);
					break;
				case FLOAT:
				case DISABLED:
				default:
					break;

				}
			}
		}
	}

	private void loadItemLists(CSimulation game) {
		randomItems = new ArrayList<>();
		Collection<CItemType> types = game.getItemData().getAllItemTypes();

		for (CItemType type : types) {
			if (type.isIncludeAsRandomChoice()) {
				randomItems.add(type);
			}
		}
	}

	public CUnitType getRandomMechanicalCritterType(CSimulation game) {
		if (landCritters == null) {
			loadCritterLists(game);
		}
		if (landCritters.size() > 0 || seaCritters.size() > 0) {
			int i = game.getSeededRandom().nextInt(landCritters.size() + seaCritters.size());
			if (i >= landCritters.size()) {
				return seaCritters.get(i - landCritters.size());
			} else {
				return landCritters.get(i);
			}
		} else if (airCritters.size() > 0) {
			int i = game.getSeededRandom().nextInt(airCritters.size());
			return airCritters.get(i);
		}
		return null;
	}

	public CItemType getRandomItemType(CSimulation game) {
		if (randomItems == null) {
			loadItemLists(game);
		}
		int i = game.getSeededRandom().nextInt(randomItems.size());
		return randomItems.get(i);
	}

}
