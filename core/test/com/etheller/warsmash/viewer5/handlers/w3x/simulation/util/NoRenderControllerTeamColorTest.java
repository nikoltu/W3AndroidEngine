package com.etheller.warsmash.viewer5.handlers.w3x.simulation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class NoRenderControllerTeamColorTest {
	@Test
	void scriptTeamColorReachesTheWorldWhileNotPresenting() {
		final List<Object> calls = new ArrayList<>();
		final SimulationRenderController world = (SimulationRenderController) Proxy.newProxyInstance(
				SimulationRenderController.class.getClassLoader(), new Class<?>[] { SimulationRenderController.class },
				(proxy, method, args) -> {
					calls.add(method.getName() + ":" + args[1]);
					return null;
				});
		final NoRenderController controller = new NoRenderController(world);
		controller.setPresenting(false);
		controller.setUnitTeamColor(null, 7);
		assertEquals(List.of("setUnitTeamColor:7"), calls);
	}
}
