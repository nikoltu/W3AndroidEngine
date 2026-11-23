package com.etheller.warsmash.viewer5.handlers.w3x.simulation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

/**
 * Without presentation nothing is shown or heard, while the objects the world keeps still go to the controller that
 * makes them.
 */
class NoRenderControllerTest {
	private final List<String> calls = new ArrayList<>();

	private SimulationRenderController recordingWorld() {
		return (SimulationRenderController) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { SimulationRenderController.class }, (proxy, method, args) -> {
					this.calls.add(method.getName());
					return null;
				});
	}

	@Test
	void presentsNothing() {
		final NoRenderController controller = new NoRenderController(recordingWorld());
		final War3ID alias = War3ID.fromString("AHtb");
		controller.spawnProjectileArt(null, null, alias);
		controller.spawnProjectileArt(null, null, null, 0);
		controller.createInstantAttackEffect(null, null, null, null);
		controller.spawnTemporarySpellEffectOnPoint(0, 0, 0, alias, CEffectType.EFFECT, 0);
		controller.spawnDamageSound(null, "MetalHeavyBash", "Flesh");
		assertNull(controller.spawnTextTag(null, TextTagConfigType.GOLD, 5));
		assertSame(SimulationRenderComponentModel.DO_NOTHING,
				controller.spawnPersistentSpellEffectOnUnit(null, alias, CEffectType.TARGET));
		assertSame(SimulationRenderComponentLightning.DO_NOTHING, controller.createLightning(null, alias, null, null));
		assertSame(SimulationRenderComponent.DO_NOTHING, controller.loopAbilitySoundEffect(null, alias));
		assertEquals(List.of(), this.calls);
	}

	@Test
	void worldObjectsStillMade() {
		final NoRenderController controller = new NoRenderController(recordingWorld());
		controller.createUnit(null, War3ID.fromString("hfoo"), 0, 0, 0, 0);
		controller.createDestructableZ(War3ID.fromString("LTlt"), 0, 0, 0, 0, 1, 0);
		controller.createTextTag();
		controller.removeUnit(null);
		controller.removeItem(null);
		controller.setTerrainDeformation(0, 0, 1, 1, new float[1]);
		assertEquals(List.of("createUnit", "createDestructableZ", "createTextTag", "removeUnit", "removeItem",
				"setTerrainDeformation"),
				this.calls);
	}

	@Test
	void presentsEverythingWhenPresenting() {
		final NoRenderController controller = new NoRenderController(recordingWorld());
		final War3ID alias = War3ID.fromString("AHtb");
		controller.setPresenting(true);
		controller.spawnProjectileArt(null, null, alias);
		controller.spawnDamageSound(null, "MetalHeavyBash", "Flesh");
		controller.createLightning(null, alias, null, null);
		controller.setPresenting(false);
		controller.spawnGainLevelEffect(null);
		assertEquals(List.of("spawnProjectileArt", "spawnDamageSound", "createLightning"), this.calls);
	}
}
