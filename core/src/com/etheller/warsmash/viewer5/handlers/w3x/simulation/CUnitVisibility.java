package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.Set;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAreaTargetVisibility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Who can see and target a unit, and what a unit sees.
 */
final class CUnitVisibility {
	private CUnitVisibility() {
	}

	static boolean canBeTargetedBy(final CUnit self, final CSimulation simulation, final CUnit source,
			final Set<CTargetType> targetsAllowed, final AbilityTargetCheckReceiver<CWidget> receiver) {
		return canBeTargetedBy(self, simulation, source, true, targetsAllowed, receiver);
	}

	static boolean canBeTargetedBy(final CUnit self, final CSimulation simulation, final CUnit source, final boolean targeted,
			final Set<CTargetType> targetsAllowed, final AbilityTargetCheckReceiver<CWidget> receiver) {
		return canBeTargetedBy(self, simulation, source, targeted, targetsAllowed, receiver, simulation.isScriptOrder());
	}

	static boolean canBeTargetedBy(final CUnit self, final CSimulation simulation, final CUnit source, final boolean targeted,
			final Set<CTargetType> targetsAllowed, final AbilityTargetCheckReceiver<CWidget> receiver,
			final boolean throughFog) {
		if ((self == source) && targetsAllowed.contains(CTargetType.NOTSELF)
				&& !targetsAllowed.contains(CTargetType.SELF)) {
			receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_SELF);
			return false;
		}
		// "player" alone means own units only; with "ally", "neutral" or "enemy" (Mass Teleport) it is one of the
		// alternatives that the alliance checks below weigh.
		if (targetsAllowed.contains(CTargetType.PLAYERUNITS) && (source.getPlayerIndex() != self.getPlayerIndex())
				&& !targetsAllowed.contains(CTargetType.ALLIES) && !targetsAllowed.contains(CTargetType.FRIEND)
				&& !targetsAllowed.contains(CTargetType.NEUTRAL) && !targetsAllowed.contains(CTargetType.ENEMIES)) {
			receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_ONE_OF_YOUR_OWN_UNITS);
			return false;
		}
		if (targetsAllowed.contains(CTargetType.NON_MAGIC_IMMUNE) && self.isMagicImmune()) {
			receiver.targetCheckFailed(CommandStringErrorKeys.THAT_UNIT_IS_IMMUNE_TO_MAGIC);
			return false;
		}
		if (self.isLimitedMagicImmune() && targetsAllowed.contains(CTargetType.NON_MAGIC_IMMUNE)
				&& !targetsAllowed.contains(CTargetType.LIMITED_MAGIC_IMMUNE)) {
			receiver.targetCheckFailed(CommandStringErrorKeys.THAT_UNIT_IS_IMMUNE_TO_MAGIC);
			return false;
		}
		// Measured: script attack orders may target a unit hidden only by the fog of war.
		final boolean fogOnly = throughFog && (self.visibility.invisLevels == 0);
		if (targeted && ((!fogOnly && !isVisible(self, simulation, source.getPlayerIndex()))
				|| (self.visibility.untargetablePlayers & (1 << source.getPlayerIndex())) != 0)) {
			receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_UNIT_WITH_THIS_ACTION);
			return false;
		}
		if (self.blocked.repairBlocked && targetsAllowed.contains(CTargetType.REPAIRABLE)) {
			if (self.hasUniqueFlag("b_Frozen")) {
				receiver.targetCheckFailed(CommandStringErrorKeys.TARGET_BUILDING_HAS_BEEN_FROZEN);
			} else if (self.hasUniqueFlag("b_LFire")) {
				receiver.targetCheckFailed(CommandStringErrorKeys.TARGET_BUILDING_HAS_LIQUID_FIRE);
			} else {
				receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_THIS_UNIT);
			}
			return false;
		}
		if (targetsAllowed.containsAll(self.targetedAs) || (!targetsAllowed.contains(CTargetType.GROUND)
				&& !targetsAllowed.contains(CTargetType.STRUCTURE) && !targetsAllowed.contains(CTargetType.AIR))) {
			final int sourcePlayerIndex = source.getPlayerIndex();
			final CPlayer sourcePlayer = simulation.getPlayer(sourcePlayerIndex);
			// "ally" and "player" name friendly targets too; the flags are alternatives, as in "friend".
			final boolean friendAllowed = targetsAllowed.contains(CTargetType.FRIEND)
					|| targetsAllowed.contains(CTargetType.ALLIES) || targetsAllowed.contains(CTargetType.PLAYERUNITS);
			if (!targetsAllowed.contains(CTargetType.ENEMIES)
					|| !sourcePlayer.hasAlliance(self.fakePlayerIndex, CAllianceType.PASSIVE)
					|| (friendAllowed
							&& sourcePlayer.hasAlliance(self.fakePlayerIndex, CAllianceType.SHARED_SPELLS))
					|| (targetsAllowed.contains(CTargetType.NEUTRAL)
							&& !sourcePlayer.hasAlliance(self.fakePlayerIndex, CAllianceType.SHARED_SPELLS))) {
				if (!friendAllowed
						|| sourcePlayer.hasAlliance(self.fakePlayerIndex, CAllianceType.SHARED_SPELLS)
						|| (targetsAllowed.contains(CTargetType.ENEMIES)
								&& !sourcePlayer.hasAlliance(self.fakePlayerIndex, CAllianceType.PASSIVE))
						|| (targetsAllowed.contains(CTargetType.NEUTRAL)
								&& sourcePlayer.hasAlliance(self.fakePlayerIndex, CAllianceType.PASSIVE))) {
					if (!targetsAllowed.contains(CTargetType.NEUTRAL)
							|| (sourcePlayer.hasAlliance(self.fakePlayerIndex, CAllianceType.PASSIVE)
									&& !sourcePlayer.hasAlliance(self.fakePlayerIndex, CAllianceType.SHARED_SPELLS))
							|| (targetsAllowed.contains(CTargetType.ENEMIES)
									&& !sourcePlayer.hasAlliance(self.fakePlayerIndex, CAllianceType.PASSIVE))
							|| (friendAllowed
									&& sourcePlayer.hasAlliance(self.fakePlayerIndex, CAllianceType.SHARED_SPELLS))) {
						if (!targetsAllowed.contains(CTargetType.MECHANICAL)
								|| self.classifications.contains(CUnitClassification.MECHANICAL)) {
							if (!targetsAllowed.contains(CTargetType.ORGANIC)
									|| !self.classifications.contains(CUnitClassification.MECHANICAL)) {
								// "ancient,nonancient" (Finger of Death) allows both, like the alliance flags.
								final boolean anyAncientness = targetsAllowed.contains(CTargetType.ANCIENT)
										&& targetsAllowed.contains(CTargetType.NONANCIENT);
								if (anyAncientness || !targetsAllowed.contains(CTargetType.ANCIENT)
										|| self.classifications.contains(CUnitClassification.ANCIENT)) {
									if (anyAncientness || !targetsAllowed.contains(CTargetType.NONANCIENT)
											|| !self.classifications.contains(CUnitClassification.ANCIENT)) {
										final boolean invulnerable = self.isInvulnerable();
										if ((!invulnerable && (targetsAllowed.contains(CTargetType.VULNERABLE)
												|| !targetsAllowed.contains(CTargetType.INVULNERABLE)))
												|| (invulnerable
														&& targetsAllowed.contains(CTargetType.INVULNERABLE))) {
											if (!targetsAllowed.contains(CTargetType.HERO) || (self.getHeroData() != null)) {
												if (!targetsAllowed.contains(CTargetType.NONHERO)
														|| (self.getHeroData() == null)) {
													if (self.isDead()) {
														if (self.isRaisable() && self.isDecays() && self.isUsableCorpse()) {
															if (targetsAllowed.contains(CTargetType.DEAD)) {
																return true;
															} else {
																receiver.targetCheckFailed(
																		CommandStringErrorKeys.TARGET_MUST_BE_LIVING);
															}
														} else {
															receiver.targetCheckFailed(
																	CommandStringErrorKeys.MUST_TARGET_A_UNIT_WITH_THIS_ACTION);
														}
													} else {
														if (!targetsAllowed.contains(CTargetType.DEAD)
																|| targetsAllowed.contains(CTargetType.ALIVE)) {
															return true;
														} else {
															receiver.targetCheckFailed(
																	CommandStringErrorKeys.MUST_TARGET_A_CORPSE);
														}
													}
												} else {
													receiver.targetCheckFailed(
															CommandStringErrorKeys.UNABLE_TO_TARGET_HEROES);
												}
											} else {
												receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_HERO);
											}
										} else {
											if (invulnerable) {
												receiver.targetCheckFailed(
														CommandStringErrorKeys.THAT_TARGET_IS_INVULNERABLE);
											} else {
												receiver.targetCheckFailed(
														CommandStringErrorKeys.UNABLE_TO_TARGET_THIS_UNIT);
											}
										}
									} else {
										receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_ANCIENTS);
									}
								} else {
									receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_AN_ANCIENT);
								}
							} else {
								receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_ORGANIC_UNITS);
							}
						} else {
							receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_ORGANIC_UNITS);
						}
					} else {
						receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_NEUTRAL_UNIT);
					}
				} else {
					receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_FRIENDLY_UNIT);
				}
			} else {
				receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_AN_ENEMY_UNIT);
			}
		} else {
			if (self.targetedAs.contains(CTargetType.GROUND) && !targetsAllowed.contains(CTargetType.GROUND)) {
				receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_GROUND_UNITS);
			} else if (self.targetedAs.contains(CTargetType.STRUCTURE)
					&& !targetsAllowed.contains(CTargetType.STRUCTURE)) {
				receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_BUILDINGS);
			} else if (self.targetedAs.contains(CTargetType.AIR) && !targetsAllowed.contains(CTargetType.AIR)) {
				receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_AIR_UNITS);
			} else if (self.targetedAs.contains(CTargetType.WARD) && !targetsAllowed.contains(CTargetType.WARD)) {
				receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_WARDS);
			} else if (targetsAllowed.contains(CTargetType.GROUND)) {
				receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_GROUND_UNIT);
			} else if (targetsAllowed.contains(CTargetType.STRUCTURE)) {
				receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_BUILDING);
			} else if (targetsAllowed.contains(CTargetType.AIR)) {
				receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_AN_AIR_UNIT);
			} else if (targetsAllowed.contains(CTargetType.WARD)) {
				receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_WARD);
			} else {
				receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_THIS_UNIT);
			}
		}
		return false;
	}

	/**
	 * Ultravision (the Night Elf ability and research effect) keeps the day sight radius at night.
	 */
	static boolean canBeAreaTargetedBy(final CUnit self, final CSimulation simulation, final CUnit source,
			final CAreaTargetVisibility visibility, final Set<CTargetType> targetsAllowed,
			final AbilityTargetCheckReceiver<CWidget> receiver) {
		switch (visibility) {
		case VISIBLE:
			return canBeTargetedBy(self, simulation, source, true, targetsAllowed, receiver);
		case NOT_INVISIBLE:
			if (isHiddenByInvisibility(self, simulation, source.getPlayerIndex())) {
				receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_UNIT_WITH_THIS_ACTION);
				return false;
			}
			return canBeTargetedBy(self, simulation, source, false, targetsAllowed, receiver);
		default:
			return canBeTargetedBy(self, simulation, source, false, targetsAllowed, receiver);
		}
	}

	/**
	 * True when the unit's invisibility, not the fog, keeps the player from seeing it.
	 */
	static boolean isHiddenByInvisibility(final CUnit self, final CSimulation simulation, final int toPlayerIndex) {
		if ((self.visibility.invisLevels == 0) || self.isDead()
				|| ((self.visibility.detections & (1 << toPlayerIndex)) != 0)) {
			return false;
		}
		final CPlayer toPlayer = simulation.getPlayer(toPlayerIndex);
		final CPlayer owner = simulation.getPlayer(self.playerIndex);
		if ((toPlayerIndex == self.playerIndex) || owner.hasAlliance(toPlayerIndex, CAllianceType.SHARED_VISION)
				|| owner.hasAlliance(toPlayerIndex, CAllianceType.SHARED_VISION_FORCED)
				|| simulation.isUnitVisionShared(self, toPlayer)) {
			return false;
		}
		return !toPlayer.getFogOfWar().isDetecting(simulation, simulation.getPathingGrid(), self.getX(), self.getY(),
				self.visibility.invisLevels);
	}

	static float getCurrentSightRadius(final CUnit self, final CSimulation game) {
		if (game.isDay() || (self.visibility.ultravisionSources > 0)) {
			return self.unitType.getSightRadiusDay();
		}
		return self.unitType.getSightRadiusNight();
	}

	static void updateFogOfWar(final CUnit self, final CSimulation game) {
		updateFogOfWar(self, game, game.getPlayer(self.playerIndex).getFogOfWar());
	}

	static void updateFogOfWar(final CUnit self, final CSimulation game, final CPlayerFogOfWar fogOfWar) {
		if (!self.isDead() && !self.hidden) {
			final float sightRadius = getCurrentSightRadius(self, game);
			if (sightRadius > 0) {
				final int sightCells = CPlayerFogOfWar.getSightCells(sightRadius);
				final boolean flying = self.getMovementType() == MovementType.FLY;
				final float myX = self.getX();
				final float myY = self.getY();
				final int myZ = flying ? Integer.MAX_VALUE : game.getTerrainHeight(myX, myY);
				final PathingGrid pathingGrid = game.getPathingGrid();
				byte detections = 0;
				for (int d = 0, dn = self.visibility.detectorLevels.size(); d < dn; d++) {
					final int detectionLevel = self.visibility.detectorLevels.get(d);
					if ((detectionLevel >> 8) > 0) {
						detections |= (byte) detectionLevel;
					}
				}
				fogOfWar.setDetecting(pathingGrid.getFogOfWarIndexX(myX), pathingGrid.getFogOfWarIndexY(myY),
						CFogState.VISIBLE, detections);

				final int myXi = pathingGrid.getFogOfWarIndexX(myX);
				final int myYi = pathingGrid.getFogOfWarIndexY(myY);
				// Terrain and sight blockers are read at the first pathing cell of each fog cell, not at the unit's offset
				// within its cell: the cell centre hides far more than 1.29 does, and the offset made it depend on the step.
				final float cellX = pathingGrid.getFogOfWarSampleX(myXi);
				final float cellY = pathingGrid.getFogOfWarSampleY(myYi);
				for (int a = 1; a <= sightCells; a++) {
					detections = 0;
					for (int d = 0, dn = self.visibility.detectorLevels.size(); d < dn; d++) {
						final int detectionLevel = self.visibility.detectorLevels.get(d);
						if ((detectionLevel >> 8) > a) {
							detections |= (byte) detectionLevel;
						}
					}

					fogOfWar.checkCardinalVision(game, pathingGrid, detections, flying, myXi, myYi - a, myXi,
							(myYi - a) + 1, cellX, cellY - (a * CPlayerFogOfWar.GRID_STEP), cellX,
							cellY - ((a - 1) * CPlayerFogOfWar.GRID_STEP), myZ);
					fogOfWar.checkCardinalVision(game, pathingGrid, detections, flying, myXi, myYi + a, myXi,
							(myYi + a) - 1, cellX, cellY + (a * CPlayerFogOfWar.GRID_STEP), cellX,
							cellY + ((a - 1) * CPlayerFogOfWar.GRID_STEP), myZ);
					fogOfWar.checkCardinalVision(game, pathingGrid, detections, flying, myXi - a, myYi,
							(myXi - a) + 1, myYi, cellX - (a * CPlayerFogOfWar.GRID_STEP), cellY,
							cellX - ((a - 1) * CPlayerFogOfWar.GRID_STEP), cellY, myZ);
					fogOfWar.checkCardinalVision(game, pathingGrid, detections, flying, myXi + a, myYi,
							(myXi + a) - 1, myYi, cellX + (a * CPlayerFogOfWar.GRID_STEP), cellY,
							cellX + ((a - 1) * CPlayerFogOfWar.GRID_STEP), cellY, myZ);
				}

				for (int y = 1; y <= sightCells; y++) {
					for (int x = 1; x <= sightCells; x++) {
						final float distance = (x * x) + (y * y);
						detections = 0;
						for (int d = 0, dn = self.visibility.detectorLevels.size(); d < dn; d++) {
							final int detectionLevel = self.visibility.detectorLevels.get(d);
							if (((detectionLevel >> 8) * (detectionLevel >> 8)) > distance) {
								detections |= (byte) detectionLevel;
							}
						}
						if (CPlayerFogOfWar.isInSight(x, y, sightCells)) {
							final int xf = x * CPlayerFogOfWar.GRID_STEP;
							final int yf = y * CPlayerFogOfWar.GRID_STEP;

							fogOfWar.checkDiagonalVision(game, pathingGrid, detections, flying, x, y, myXi - x,
									myYi - y, (myXi - x) + 1, (myYi - y) + 1, cellX - xf, cellY - yf,
									(cellX - xf) + CPlayerFogOfWar.GRID_STEP, (cellY - yf) + CPlayerFogOfWar.GRID_STEP,
									myZ);
							fogOfWar.checkDiagonalVision(game, pathingGrid, detections, flying, x, y, myXi - x,
									myYi + y, (myXi - x) + 1, (myYi + y) - 1, cellX - xf, cellY + yf,
									(cellX - xf) + CPlayerFogOfWar.GRID_STEP, (cellY + yf) - CPlayerFogOfWar.GRID_STEP,
									myZ);
							fogOfWar.checkDiagonalVision(game, pathingGrid, detections, flying, x, y, myXi + x,
									myYi - y, (myXi + x) - 1, (myYi - y) + 1, cellX + xf, cellY - yf,
									(cellX + xf) - CPlayerFogOfWar.GRID_STEP, (cellY - yf) + CPlayerFogOfWar.GRID_STEP,
									myZ);
							fogOfWar.checkDiagonalVision(game, pathingGrid, detections, flying, x, y, myXi + x,
									myYi + y, (myXi + x) - 1, (myYi + y) - 1, cellX + xf, cellY + yf,
									(cellX + xf) - CPlayerFogOfWar.GRID_STEP, (cellY + yf) - CPlayerFogOfWar.GRID_STEP,
									myZ);
						}
					}
				}
			}
		}
	}

	static void updateSeenBy(final CUnit self, final CSimulation simulation, final int playerCount) {
		// A dead unit counts as visible everywhere; dying unseen under the black mask does not make it seen (1.29).
		if (self.isDead()) {
			return;
		}
		for (int i = 0; i < playerCount; i++) {
			if (((self.visibility.seenByPlayers & (1L << i)) == 0) && isVisible(self, simulation, i)) {
				self.visibility.seenByPlayers |= 1L << i;
			}
		}
	}

	static boolean isVisible(final CUnit self, final CSimulation simulation, final int toPlayerIndex) {
		final CPlayer toPlayer = simulation.getPlayer(toPlayerIndex);
		final CPlayer owner = simulation.getPlayer(self.playerIndex);
		if (self.isDead() || (((toPlayerIndex == self.playerIndex)
				|| owner.hasAlliance(toPlayerIndex, CAllianceType.SHARED_VISION)
				|| owner.hasAlliance(toPlayerIndex, CAllianceType.SHARED_VISION_FORCED))
				&& (getCurrentSightRadius(self, simulation) > 0))) {
			return true;
		}
		// Takes effect at once, not at the next fog update (measured).
		if (simulation.isUnitVisionShared(self, toPlayer)) {
			return true;
		}
		if ((self.visibility.invisLevels > 0) && ((self.visibility.detections & (1 << toPlayerIndex)) == 0)) {
			return toPlayer.getFogOfWar().isDetecting(simulation, simulation.getPathingGrid(), self.getX(), self.getY(),
					self.visibility.invisLevels);
		}
		// Measured on 1.29: the neutral players see every unit, behind trees or out of any sight range alike, so
		// creeps acquire and attack without line of sight.
		if (simulation.isNeutralPlayer(toPlayerIndex)) {
			return true;
		}
		return toPlayer.getFogOfWar().isVisible(simulation, simulation.getPathingGrid(), self.getX(), self.getY());
	}
}
