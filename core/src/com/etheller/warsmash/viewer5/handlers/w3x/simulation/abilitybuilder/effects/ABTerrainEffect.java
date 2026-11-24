package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.effects;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CTerrainDeformations;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponent;

/**
 * Handle of a ground deformation an ability made; removing it lets the ground settle back over the stop time.
 */
public class ABTerrainEffect implements SimulationRenderComponent {
	private final CSimulation simulation;
	private final CTerrainDeformations.Deformation deformation;
	private final int stopTimeMs;

	public ABTerrainEffect(final CSimulation simulation, final CTerrainDeformations.Deformation deformation,
			final int stopTimeMs) {
		this.simulation = simulation;
		this.deformation = deformation;
		this.stopTimeMs = stopTimeMs;
	}

	@Override
	public void remove() {
		this.simulation.getTerrainDeformations().stop(this.simulation, this.deformation, this.stopTimeMs);
	}
}
