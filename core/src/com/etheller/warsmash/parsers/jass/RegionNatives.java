package com.etheller.warsmash.parsers.jass;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.parsers.jass.triggers.CRect;
import com.etheller.warsmash.parsers.jass.triggers.LocationJass;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityBlightedGoldMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.region.CRegion;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPathingTypeJass;
import java.util.List;

/**
 * Rect, region and location natives, blight and terrain queries.
 */
final class RegionNatives extends CommonNatives {
	RegionNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		registerRects();
		registerRegions();
		registerLocations();
		registerTerrain();
	}

	private void registerRects() {
		final HandleJassType rectType = type("rect");

		// ============================================================================
		// Region and Location API
		//
		jassProgramVisitor.getJassNativeManager().createNative("Rect", (arguments, globalScope, triggerScope) -> {
			final float minx = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
			final float miny = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
			final float maxx = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
			final float maxy = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
			return newHandle(rectType, new CRect(minx, miny, maxx - minx, maxy - miny));
		});
		jassProgramVisitor.getJassNativeManager().createNative("RectFromLoc",
				(arguments, globalScope, triggerScope) -> {
					final AbilityPointTarget min = arguments.get(0)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					final AbilityPointTarget max = arguments.get(1)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					final float minx = min.x;
					final float miny = min.y;
					final float maxx = max.x;
					final float maxy = max.y;
					return newHandle(rectType, new CRect(minx, miny, maxx - minx, maxy - miny));
				});
		jassProgramVisitor.getJassNativeManager().createNative("RemoveRect",
				(arguments, globalScope, triggerScope) -> {
					final Rectangle rect = arguments.get(0).visit(ObjectJassValueVisitor.<Rectangle>getInstance());
					release(rect);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetRect",
				(arguments, globalScope, triggerScope) -> {
					final Rectangle rect = arguments.get(0).visit(ObjectJassValueVisitor.<Rectangle>getInstance());
					final float minx = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float miny = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float maxx = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float maxy = arguments.get(4).visit(RealJassValueVisitor.getInstance()).floatValue();
					rect.set(minx, miny, maxx - minx, maxy - miny);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetRectFromLoc",
				(arguments, globalScope, triggerScope) -> {
					final Rectangle rect = arguments.get(0).visit(ObjectJassValueVisitor.<Rectangle>getInstance());
					final AbilityPointTarget min = arguments.get(1)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					final AbilityPointTarget max = arguments.get(2)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					final float minx = min.x;
					final float miny = min.y;
					final float maxx = max.x;
					final float maxy = max.y;
					rect.set(minx, miny, maxx - minx, maxy - miny);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("MoveRectTo",
				(arguments, globalScope, triggerScope) -> {
					final Rectangle rect = arguments.get(0).visit(ObjectJassValueVisitor.<Rectangle>getInstance());
					final float newCenterX = arguments.get(1).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					final float newCenterY = arguments.get(2).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					rect.setCenter(newCenterX, newCenterY);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("MoveRectToLoc",
				(arguments, globalScope, triggerScope) -> {
					final Rectangle rect = arguments.get(0).visit(ObjectJassValueVisitor.<Rectangle>getInstance());
					final AbilityPointTarget newCenterLoc = arguments.get(1)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					rect.setCenter(newCenterLoc.x, newCenterLoc.y);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetRectCenterX", new JassFunction() {
			Vector2 centerHeap = new Vector2();

			@Override
			public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
					final TriggerExecutionScope triggerScope) {
				final Rectangle rect = nullable(arguments, 0, ObjectJassValueVisitor.<Rectangle>getInstance());
				if (rect == null) {
					return RealJassValue.ZERO;
				}
				return RealJassValue.of(rect.getCenter(this.centerHeap).x);
			}
		});
		jassProgramVisitor.getJassNativeManager().createNative("GetRectCenterY", new JassFunction() {
			Vector2 centerHeap = new Vector2();

			@Override
			public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
					final TriggerExecutionScope triggerScope) {
				final Rectangle rect = nullable(arguments, 0, ObjectJassValueVisitor.<Rectangle>getInstance());
				if (rect == null) {
					return RealJassValue.ZERO;
				}
				return RealJassValue.of(rect.getCenter(this.centerHeap).y);
			}
		});
		jassProgramVisitor.getJassNativeManager().createNative("GetRectMinX",
				(arguments, globalScope, triggerScope) -> {
					final Rectangle rect = arguments.get(0).visit(ObjectJassValueVisitor.<Rectangle>getInstance());
					return RealJassValue.of(rect.getX());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetRectMinY",
				(arguments, globalScope, triggerScope) -> {
					final Rectangle rect = arguments.get(0).visit(ObjectJassValueVisitor.<Rectangle>getInstance());
					return RealJassValue.of(rect.getY());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetRectMaxX",
				(arguments, globalScope, triggerScope) -> {
					final Rectangle rect = arguments.get(0).visit(ObjectJassValueVisitor.<Rectangle>getInstance());
					return RealJassValue.of(rect.getX() + rect.getWidth());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetRectMaxY",
				(arguments, globalScope, triggerScope) -> {
					final Rectangle rect = arguments.get(0).visit(ObjectJassValueVisitor.<Rectangle>getInstance());
					return RealJassValue.of(rect.getY() + rect.getHeight());
				});
	}

	private void registerRegions() {
		final HandleJassType regionType = type("region");

		jassProgramVisitor.getJassNativeManager().createNative("CreateRegion",
				(arguments, globalScope, triggerScope) -> {
					return newHandle(regionType, new CRegion());
				});
		jassProgramVisitor.getJassNativeManager().createNative("RemoveRegion",
				(arguments, globalScope, triggerScope) -> {
					final CRegion region = arguments.get(0).visit(ObjectJassValueVisitor.<CRegion>getInstance());
					region.remove(this.simulation.getRegionManager());
					release(region);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RegionAddRect",
				(arguments, globalScope, triggerScope) -> {
					final CRegion region = arguments.get(0).visit(ObjectJassValueVisitor.<CRegion>getInstance());
					final Rectangle rect = arguments.get(1).visit(ObjectJassValueVisitor.<Rectangle>getInstance());
					region.addRect(rect, this.simulation.getRegionManager());
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RegionClearRect",
				(arguments, globalScope, triggerScope) -> {
					final CRegion region = arguments.get(0).visit(ObjectJassValueVisitor.<CRegion>getInstance());
					final Rectangle rect = arguments.get(1).visit(ObjectJassValueVisitor.<Rectangle>getInstance());
					region.clearRect(rect, this.simulation.getRegionManager());
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RegionAddCell",
				(arguments, globalScope, triggerScope) -> {
					final CRegion region = arguments.get(0).visit(ObjectJassValueVisitor.<CRegion>getInstance());
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					region.addCell(x, y, this.simulation.getRegionManager());
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RegionAddCellAtLoc",
				(arguments, globalScope, triggerScope) -> {
					final CRegion region = arguments.get(0).visit(ObjectJassValueVisitor.<CRegion>getInstance());
					final AbilityPointTarget whichLocation = arguments.get(1)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					region.addCell(whichLocation.x, whichLocation.y,
							this.simulation.getRegionManager());
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RegionClearCell",
				(arguments, globalScope, triggerScope) -> {
					final CRegion region = arguments.get(0).visit(ObjectJassValueVisitor.<CRegion>getInstance());
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					region.clearCell(x, y, this.simulation.getRegionManager());
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RegionClearCellAtLoc",
				(arguments, globalScope, triggerScope) -> {
					final CRegion region = arguments.get(0).visit(ObjectJassValueVisitor.<CRegion>getInstance());
					final AbilityPointTarget whichLocation = arguments.get(1)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					region.clearCell(whichLocation.x, whichLocation.y,
							this.simulation.getRegionManager());
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitInRegion",
				(arguments, globalScope, triggerScope) -> {
					final CRegion whichRegion = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CRegion>getInstance());
					final CUnit whichUnit = arguments.get(1).visit(ObjectJassValueVisitor.<CUnit>getInstance());
					return BooleanJassValue.of(whichUnit.isInRegion(whichRegion));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsPointInRegion",
				(arguments, globalScope, triggerScope) -> {
					final CRegion whichRegion = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CRegion>getInstance());
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					return BooleanJassValue
							.of(whichRegion.contains(x, y, this.simulation.getRegionManager()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsLocationInRegion",
				(arguments, globalScope, triggerScope) -> {
					final CRegion whichRegion = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CRegion>getInstance());
					final AbilityPointTarget whichLocation = arguments.get(1)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					return BooleanJassValue.of(whichRegion.contains(whichLocation.x, whichLocation.y,
							this.simulation.getRegionManager()));
				});
	}

	private void registerLocations() {
		final HandleJassType locationType = type("location");

		jassProgramVisitor.getJassNativeManager().createNative("Location",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					return new HandleJassValue(locationType,
							new LocationJass(x, y, this.simulation.getHandleIdAllocator().createHandleId()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("RemoveLocation",
				(arguments, globalScope, triggerScope) -> {
					final AbilityPointTarget whichLocation = arguments.get(0)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					release(whichLocation);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("MoveLocation",
				(arguments, globalScope, triggerScope) -> {
					final AbilityPointTarget whichLocation = arguments.get(0)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					whichLocation.x = x;
					whichLocation.y = y;
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetLocationX",
				(arguments, globalScope, triggerScope) -> {
					final AbilityPointTarget whichLocation = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					if (whichLocation == null) {
						return RealJassValue.ZERO;
					}
					return RealJassValue.of(whichLocation.x);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetLocationY",
				(arguments, globalScope, triggerScope) -> {
					final AbilityPointTarget whichLocation = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					if (whichLocation == null) {
						return RealJassValue.ZERO;
					}
					return RealJassValue.of(whichLocation.y);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetLocationZ",
				(arguments, globalScope, triggerScope) -> {
					final AbilityPointTarget whichLocation = arguments.get(0)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					war3MapViewer.simulation.getTerrainDeformations().sync(war3MapViewer.simulation);
					return RealJassValue
							.of(war3MapViewer.simulation.getTerrainData().getLocationZ(whichLocation.x,
									whichLocation.y));
				});
	}

	private void registerTerrain() {
		final HandleJassType unitType = type("unit");
		final HandleJassType rectType = type("rect");

		jassProgramVisitor.getJassNativeManager().createNative("GetWorldBounds",
				(arguments, globalScope, triggerScope) -> {
					final float worldMinX = this.simulation.getPathingGrid().getWorldX(0) - 16f;
					final float worldMinY = this.simulation.getPathingGrid().getWorldY(0) - 16f;
					final float worldMaxX = this.simulation.getPathingGrid()
							.getWorldX(this.simulation.getPathingGrid().getWidth() - 1) + 16f;
					final float worldMaxY = this.simulation.getPathingGrid()
							.getWorldY(this.simulation.getPathingGrid().getHeight() - 1) + 16f;
					return newHandle(rectType,
							new CRect(worldMinX, worldMinY, worldMaxX - worldMinX, worldMaxY - worldMinY));
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateBlightedGoldmine",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final double x = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					final double y = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final double facing = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					final War3ID blightedMineRawcode = War3ID.fromString("ugol");
					final War3ID goldMineRawcode = War3ID.fromString("ngol");
					final CUnit blightedMine = this.simulation.createUnitSimple(
							blightedMineRawcode, player.getId(), (float) x, (float) y, (float) facing);
					// Not createUnitSimple: the blighted mine already stands here, so the hidden one would be moved
					// off it and its pathing would block building around the Necropolis.
					final CUnit goldMine = this.simulation.createUnit(goldMineRawcode,
							this.simulation.getGameData().getMaxPlayers() - 1, blightedMine.getX(), blightedMine.getY(),
							(float) facing);
					goldMine.setHidden(true);
					for (final CAbility ability : blightedMine.getAbilities()) {
						if (ability instanceof CAbilityBlightedGoldMine) {
							((CAbilityBlightedGoldMine) ability).setParentMine(goldMine,
									goldMine.getGoldMineData());
						}
					}
					return new HandleJassValue(unitType, blightedMine);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTerrainCliffLevel",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					return IntegerJassValue.of(this.simulation.getTerrainData().getLayerHeight(x, y));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTerrainType",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					return IntegerJassValue.of(this.simulation.getTerrainData().getTerrainType(x, y));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTerrainVariance",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					return IntegerJassValue.of(this.simulation.getTerrainData().getTerrainVariation(x, y));
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetTerrainType",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final int terrainType = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					final int variation = arguments.get(3).visit(IntegerJassValueVisitor.getInstance());
					final int area = arguments.get(4).visit(IntegerJassValueVisitor.getInstance());
					final int shape = arguments.get(5).visit(IntegerJassValueVisitor.getInstance());
					this.simulation.getTerrainTypeChanges().set(this.simulation, x, y, terrainType, variation, area,
							shape);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetBlightLoc",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer whichPlayer = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final AbilityPointTarget whichLocation = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					final float radius = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final boolean addBlight = arguments.get(3).visit(BooleanJassValueVisitor.getInstance());
					if ((whichPlayer == null) || (whichLocation == null)) {
						return null;
					}
					final float whichLocationX = whichLocation.x;
					final float whichLocationY = whichLocation.y;
					war3MapViewer.setBlight(whichLocationX, whichLocationY, radius, addBlight);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetBlight",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float radius = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					final boolean addBlight = arguments.get(4).visit(BooleanJassValueVisitor.getInstance());
					war3MapViewer.setBlight(x, y, radius, addBlight);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetBlightPoint",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final boolean addBlight = arguments.get(3).visit(BooleanJassValueVisitor.getInstance());
					war3MapViewer.setBlight(x, y, 1, addBlight);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetBlightRect",
				(arguments, globalScope, triggerScope) -> {
					final Rectangle rect = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final boolean addBlight = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					if (rect != null) {
						for (float x = rect.x; x <= (rect.x + rect.width); x += 128f) {
							for (float y = rect.y; y <= (rect.y + rect.height); y += 128f) {
								war3MapViewer.setBlight(x, y, 1, addBlight);
							}
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsPointBlighted",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final PathingGrid pathingGrid = this.simulation.getPathingGrid();
					return BooleanJassValue.of(pathingGrid.contains(x, y) && PathingGrid.PathingFlags
							.isPathingFlag(pathingGrid.getPathing(x, y), PathingGrid.PathingFlags.BLIGHTED));
				});
		// Measured: a cell of the map's own terrain, 32 wide, takes the change; every type has a flag of its own, so
		// changing walkability leaves peon harvest and amphibious pathing as they were, and ANY (and no type at
		// all) changes all of them. Blocking a type reads back as unpathable, which is the flag set.
		jassProgramVisitor.getJassNativeManager().createNative("SetTerrainPathable",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final CPathingTypeJass pathingType = nullable(arguments, 2,
							ObjectJassValueVisitor.<CPathingTypeJass>getInstance());
					final boolean pathable = arguments.get(3).visit(BooleanJassValueVisitor.getInstance());
					final PathingGrid pathingGrid = this.simulation.getPathingGrid();
					final CPathingTypeJass type = pathingType == null ? CPathingTypeJass.ANY : pathingType;
					final boolean blocked = !pathable;
					for (final CPathingTypeJass each : CPathingTypeJass.VALUES) {
						if ((type != CPathingTypeJass.ANY) && (type != each)) {
							continue;
						}
						switch (each) {
						case WALKABILITY:
							pathingGrid.setTerrainFlag(x, y, PathingGrid.PathingFlags.UNWALKABLE, blocked);
							break;
						case FLYABILITY:
							pathingGrid.setTerrainFlag(x, y, PathingGrid.PathingFlags.UNFLYABLE, blocked);
							break;
						case BUILDABILITY:
							pathingGrid.setTerrainFlag(x, y, PathingGrid.PathingFlags.UNBUILDABLE, blocked);
							break;
						case PEONHARVESTPATHING:
							pathingGrid.setScriptFlag(x, y, PathingGrid.SCRIPT_PEON_BLOCKED,
									PathingGrid.SCRIPT_PEON_OPEN, blocked);
							break;
						case BLIGHTPATHING:
							pathingGrid.setTerrainFlag(x, y, PathingGrid.PathingFlags.BLIGHTED, blocked);
							break;
						case FLOATABILITY:
							pathingGrid.setTerrainFlag(x, y, PathingGrid.PathingFlags.UNSWIMABLE, blocked);
							break;
						case AMPHIBIOUSPATHING:
							pathingGrid.setScriptFlag(x, y, PathingGrid.SCRIPT_AMPHIBIOUS_BLOCKED,
									PathingGrid.SCRIPT_AMPHIBIOUS_OPEN, blocked);
							break;
						default:
							break;
						}
					}
					return null;
				});
		// Only the map's own terrain pathing counts (not units, buildings or trees); outside the playable area
		// every type except floatability is blocked, and ANY is always blocked.
		jassProgramVisitor.getJassNativeManager().createNative("IsTerrainPathable",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final CPathingTypeJass pathingType = nullable(arguments, 2,
							ObjectJassValueVisitor.<CPathingTypeJass>getInstance());
					final Rectangle entireMap = war3MapViewer.terrain.getEntireMap();
					if ((pathingType == null) || (pathingType == CPathingTypeJass.ANY) || (x < entireMap.x)
							|| (y < entireMap.y) || (x >= (entireMap.x + entireMap.width))
							|| (y >= (entireMap.y + entireMap.height))) {
						return BooleanJassValue.TRUE;
					}
					if (!war3MapViewer.terrain.getPlayableMapArea().contains(x, y)) {
						return BooleanJassValue.of(pathingType != CPathingTypeJass.FLOATABILITY);
					}
					final short pathing = this.simulation.getPathingGrid().getTerrainPathing(x,
							y);
					final short scriptFlags = this.simulation.getPathingGrid().getScriptFlags(x, y);
					switch (pathingType) {
					case WALKABILITY:
						return BooleanJassValue.of(PathingGrid.PathingFlags.isPathingFlag(pathing,
								PathingGrid.PathingFlags.UNWALKABLE));
					case PEONHARVESTPATHING:
						return BooleanJassValue.of(scriptOverride(scriptFlags, PathingGrid.SCRIPT_PEON_BLOCKED,
								PathingGrid.SCRIPT_PEON_OPEN, PathingGrid.PathingFlags.isPathingFlag(pathing,
										PathingGrid.PathingFlags.UNWALKABLE)));
					case FLYABILITY:
						return BooleanJassValue.of(PathingGrid.PathingFlags.isPathingFlag(pathing,
								PathingGrid.PathingFlags.UNFLYABLE));
					case BUILDABILITY:
						return BooleanJassValue.of(PathingGrid.PathingFlags.isPathingFlag(pathing,
								PathingGrid.PathingFlags.UNBUILDABLE));
					case BLIGHTPATHING:
						return BooleanJassValue.of(PathingGrid.PathingFlags.isPathingFlag(pathing,
								PathingGrid.PathingFlags.BLIGHTED));
					case FLOATABILITY:
						return BooleanJassValue.of(PathingGrid.PathingFlags.isPathingFlag(pathing,
								PathingGrid.PathingFlags.UNSWIMABLE));
					case AMPHIBIOUSPATHING:
						return BooleanJassValue.of(scriptOverride(scriptFlags, PathingGrid.SCRIPT_AMPHIBIOUS_BLOCKED,
								PathingGrid.SCRIPT_AMPHIBIOUS_OPEN,
								PathingGrid.PathingFlags.isPathingFlag(pathing, PathingGrid.PathingFlags.UNWALKABLE)
										&& PathingGrid.PathingFlags.isPathingFlag(pathing,
												PathingGrid.PathingFlags.UNSWIMABLE)));
					default:
						return BooleanJassValue.TRUE;
					}
				});
	}

	private static boolean scriptOverride(final short flags, final short blocked, final short open,
			final boolean fallback) {
		if ((flags & blocked) != 0) {
			return true;
		}
		return ((flags & open) == 0) && fallback;
	}
}
