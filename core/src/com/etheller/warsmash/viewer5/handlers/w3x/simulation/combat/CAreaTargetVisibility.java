package com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat;

/**
 * Which units an area effect reaches that the source's owner does not see. Measured on 1.29: the kind of effect
 * decides, not the alliance of the target.
 */
public enum CAreaTargetVisibility {
	/**
	 * Fog and invisibility alike: auras, Immolation, Bladestorm, War Stomp, Thunder Clap, Frost Nova, Death and Decay,
	 * Blizzard.
	 */
	ANY,
	/**
	 * Units in the fog, but not invisible ones the owner does not detect: splash and bounce of attacks, Cluster
	 * Rockets, Kaboom.
	 */
	NOT_INVISIBLE,
	/**
	 * Only units the owner sees, as a targeted order: Starfall, Fan of Knives.
	 */
	VISIBLE;
}
