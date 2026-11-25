package com.etheller.warsmash.parsers.jass;

import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderCommands;
import java.util.List;

import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderSpellEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ScriptEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.PendingEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.PendingLightning;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityDataUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.BuffUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.EffectAttachmentUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CSoundType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightning;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightningMovable;

/**
 * Special effect and lightning natives.
 */
final class EffectNatives extends CommonNatives {
	private final RenderCommands commands;

	EffectNatives(final CommonEnvironment environment) {
		super(environment);
		this.commands = this.link.commands();
	}

	/**
	 * The handle of an effect the render side makes. With a simulation thread the handle is made at once and the effect
	 * by the render thread in frame order, so the script never waits for it.
	 */
	private HandleJassValue effectHandle(final HandleJassType effectType,
			final java.util.function.Supplier<RenderSpellEffect> create) {
		if (!this.commands.isQueued()) {
			return newHandle(effectType, create.get());
		}
		final PendingEffect pending = new PendingEffect();
		final HandleJassValue handle = newHandle(effectType, pending);
		this.commands.send(() -> pending.attach(create.get()));
		return handle;
	}

	/**
	 * A lightning that was made while the simulation had its own thread keeps what the script reads here; one a loaded
	 * game brought back lives on the render side and is asked there.
	 */
	private <T> T lightningCall(final SimulationRenderComponentLightning lightning,
			final java.util.function.Supplier<T> work) {
		return (lightning == null) || (lightning instanceof PendingLightning)
				|| (lightning == SimulationRenderComponentLightning.DO_NOTHING)
				|| (lightning == SimulationRenderComponentLightningMovable.DO_NOTHING) ? work.get()
				: this.commands.call("lightning", work);
	}

	/** The same for a lightning; a type the map does not draw has no handle to wait for. */
	private HandleJassValue scriptLightning(final HandleJassType lightningType, final War3ID lightningId,
			final java.util.function.Supplier<SimulationRenderComponentLightningMovable> create) {
		if (!this.commands.isQueued()) {
			return newLightning(lightningType, create.get());
		}
		if (!war3MapViewer.hasLightningType(lightningId)) {
			return newLightning(lightningType, SimulationRenderComponentLightningMovable.DO_NOTHING);
		}
		final PendingLightning pending = new PendingLightning(this.commands);
		final HandleJassValue handle = newLightning(lightningType, pending);
		this.commands.send(() -> pending.attach(create.get()));
		return handle;
	}

	@Override
	void register() {
		final HandleJassType effectType = type("effect");
		final HandleJassType lightningType = type("lightning");

		jassProgramVisitor.getJassNativeManager().createNative("AddSpecialEffectTarget",
				(arguments, globalScope, triggerScope) -> {
					final String modelName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final CWidget targetWidget = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final String attachPointName = arguments.get(2).visit(StringJassValueVisitor.getInstance());
					return effectHandle(effectType, () -> war3MapViewer.addSpecialEffectTarget(modelName, targetWidget, attachPointName));
				});
		jassProgramVisitor.getJassNativeManager().createNative("AddSpecialEffectLoc",
				(arguments, globalScope, triggerScope) -> {
					final String modelName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final AbilityPointTarget positionLoc = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					return effectHandle(effectType, () -> war3MapViewer.addSpecialEffect(modelName, positionLoc.x, positionLoc.y, 0 /* facing */));
				});
		jassProgramVisitor.getJassNativeManager().createNative("AddSpecialEffect",
				(arguments, globalScope, triggerScope) -> {
					final String modelName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					return effectHandle(effectType, () -> war3MapViewer.addSpecialEffect(modelName, x, y, 0 /* facing */));
				});
		jassProgramVisitor.getJassNativeManager().createNative("AddSpellEffectById",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final CEffectType whichEffectType = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					final double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					return effectHandle(effectType, () -> war3MapViewer
							.spawnSpellEffectEx((float) x, (float) y, 0 /* facing */, new War3ID(rawcode), whichEffectType, 0));
				});
		jassProgramVisitor.getJassNativeManager().createNative("AddSpellEffectTargetById",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final CEffectType whichEffectType = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					final CWidget target = arguments.get(2).visit(ObjectJassValueVisitor.getInstance());
					final String attachmentPoint = nullable(arguments, 3, StringJassValueVisitor.getInstance());
					// Measured: an empty attachment point makes no effect.
					if ((attachmentPoint != null) && attachmentPoint.isEmpty()) {
						return newHandle(effectType, null);
					}
					if (attachmentPoint != null) {
						return effectHandle(effectType, () -> war3MapViewer
								.spawnSpellEffectOnUnitEx(target, new War3ID(rawcode), whichEffectType, 0, attachmentPoint));
					}
					else {
						return effectHandle(effectType, () -> war3MapViewer
								.spawnSpellEffectOnUnitEx(target, new War3ID(rawcode), whichEffectType, 0));
					}
				});
		jassProgramVisitor.getJassNativeManager().createNative("AddSpellEffectByIdLoc",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final CEffectType whichEffectType = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					final AbilityPointTarget loc = arguments.get(2).visit(ObjectJassValueVisitor.getInstance());
					final float x = loc.x;
					final float y = loc.y;
					return effectHandle(effectType, () -> war3MapViewer
							.spawnSpellEffectEx(x, y, 0 /* facing */, new War3ID(rawcode), whichEffectType, 0));
				});
		// Measured: the versions that take the ability as a string make no effect and read nothing, even for abilities
		// that exist; only the rawcode versions work.
		for (final String legacy : new String[] { "AddSpellEffect", "AddSpellEffectLoc", "AddSpellEffectTarget" }) {
			jassProgramVisitor.getJassNativeManager().createNative(legacy,
					(arguments, globalScope, triggerScope) -> newHandle(effectType, null));
		}
		jassProgramVisitor.getJassNativeManager().createNative("AddLightning",
				(arguments, globalScope, triggerScope) -> {
					final String rawcode = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final boolean checkVisibility = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					final double x2 = arguments.get(4).visit(RealJassValueVisitor.getInstance());
					final double y2 = arguments.get(5).visit(RealJassValueVisitor.getInstance());
					return scriptLightning(lightningType, War3ID.fromString(rawcode),
							() -> war3MapViewer.createLightning(War3ID.fromString(rawcode), (float) x, (float) y,
									war3MapViewer.terrain.getGroundHeight((float) x, (float) y), (float) x2, (float) y2,
									war3MapViewer.terrain.getGroundHeight((float) x2, (float) y2), checkVisibility));
				});
		jassProgramVisitor.getJassNativeManager().createNative("AddLightningEx",
				(arguments, globalScope, triggerScope) -> {
					final String rawcode = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final boolean checkVisibility = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					final double z = arguments.get(4).visit(RealJassValueVisitor.getInstance());
					final double x2 = arguments.get(5).visit(RealJassValueVisitor.getInstance());
					final double y2 = arguments.get(6).visit(RealJassValueVisitor.getInstance());
					final double z2 = arguments.get(7).visit(RealJassValueVisitor.getInstance());
					return scriptLightning(lightningType, War3ID.fromString(rawcode),
							() -> war3MapViewer.createLightning(War3ID.fromString(rawcode), (float) x, (float) y,
									(float) z, (float) x2, (float) y2, (float) z2, checkVisibility));
				});
		jassProgramVisitor.getJassNativeManager().createNative("MoveLightning",
				(arguments, globalScope, triggerScope) -> {
					final SimulationRenderComponentLightningMovable lightning = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					final double x2 = arguments.get(4).visit(RealJassValueVisitor.getInstance());
					final double y2 = arguments.get(5).visit(RealJassValueVisitor.getInstance());
					return lightningCall(lightning, () -> {
						if ((lightning != null) && !lightning.isRemoved()) {
							lightning.move((float) x, (float) y, (float) x2, (float) y2);
							return BooleanJassValue.TRUE;
						}
						return BooleanJassValue.FALSE;
					});
				});
		jassProgramVisitor.getJassNativeManager().createNative("MoveLightningEx",
				(arguments, globalScope, triggerScope) -> {
					final SimulationRenderComponentLightningMovable lightning = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					final double z = arguments.get(4).visit(RealJassValueVisitor.getInstance());
					final double x2 = arguments.get(5).visit(RealJassValueVisitor.getInstance());
					final double y2 = arguments.get(6).visit(RealJassValueVisitor.getInstance());
					final double z2 = arguments.get(7).visit(RealJassValueVisitor.getInstance());
					return lightningCall(lightning, () -> {
						if ((lightning != null) && !lightning.isRemoved()) {
							lightning.move((float) x, (float) y, (float) z, (float) x2, (float) y2, (float) z2);
							return BooleanJassValue.TRUE;
						}
						return BooleanJassValue.FALSE;
					});
				});
		jassProgramVisitor.getJassNativeManager().createNative("DestroyLightning",
				(arguments, globalScope, triggerScope) -> {
					final SimulationRenderComponentLightning lightning = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					return lightningCall(lightning, () -> {
						if ((lightning != null) && !lightning.isRemoved()) {
							lightning.remove();
							return BooleanJassValue.TRUE;
						}
						return BooleanJassValue.FALSE;
					});
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetLightningColor",
				(arguments, globalScope, triggerScope) -> {
					final SimulationRenderComponentLightningMovable lightning = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final double r = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					final double g = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final double b = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					final double a = arguments.get(4).visit(RealJassValueVisitor.getInstance());
					return lightningCall(lightning, () -> {
						if ((lightning != null) && !lightning.isRemoved()) {
							lightning.setColor((float) r, (float) g, (float) b, (float) a);
							return BooleanJassValue.TRUE;
						}
						return BooleanJassValue.FALSE;
					});
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetAbilityEffectById",
				(arguments, globalScope, triggerScope) -> {
					final int abilityRawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final CEffectType artType = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final int index = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					return StringJassValue.of(abilityEffect(new War3ID(abilityRawcode), artType, index));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetAbilityEffect",
				(arguments, globalScope, triggerScope) -> StringJassValue.of(""));
		jassProgramVisitor.getJassNativeManager().createNative("GetAbilitySound",
				(arguments, globalScope, triggerScope) -> StringJassValue.of(""));
		jassProgramVisitor.getJassNativeManager().createNative("GetAbilitySoundById",
				(arguments, globalScope, triggerScope) -> {
					final int abilityRawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final CSoundType soundType = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					return StringJassValue.of(abilitySound(new War3ID(abilityRawcode), soundType));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetLightningColorR",
				(arguments, globalScope, triggerScope) -> lightningColor(arguments, 0));
		jassProgramVisitor.getJassNativeManager().createNative("GetLightningColorG",
				(arguments, globalScope, triggerScope) -> lightningColor(arguments, 1));
		jassProgramVisitor.getJassNativeManager().createNative("GetLightningColorB",
				(arguments, globalScope, triggerScope) -> lightningColor(arguments, 2));
		jassProgramVisitor.getJassNativeManager().createNative("GetLightningColorA",
				(arguments, globalScope, triggerScope) -> lightningColor(arguments, 3));
		jassProgramVisitor.getJassNativeManager().createNative("DestroyEffect",
				(arguments, globalScope, triggerScope) -> {
					final ScriptEffect fx = nullable(arguments, 0, ObjectJassValueVisitor.<ScriptEffect>getInstance());
					if (fx != null) {
						commands.send(() -> fx.setKillWhenDone(true));
						release(fx);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzRemoveEffect",
				(arguments, globalScope, triggerScope) -> {
					final ScriptEffect fx = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (fx != null) {
						commands.send(fx::removeWithoutDeath);
					}
					return null;
				});
	}

	private String abilityEffect(final War3ID alias, final CEffectType artType, final int index) {
		if (artType == null) {
			return "";
		}
		if (artType == CEffectType.LIGHTNING) {
			final List<War3ID> lightnings = war3MapViewer.getLightningEffectList(alias);
			return ((index >= 0) && (index < lightnings.size())) ? lightnings.get(index).toString() : "";
		}
		// Measured: an index below the list reads like the first entry, one past it the last.
		final EffectAttachmentUI attachment = war3MapViewer.getEffectAttachmentUI(alias, artType,
				Math.max(0, index));
		return attachment == null ? "" : attachment.getModelPath();
	}

	private String abilitySound(final War3ID alias, final CSoundType soundType) {
		final boolean looped = soundType == CSoundType.EFFECT_LOOPED;
		final AbilityDataUI abilityData = war3MapViewer.getAbilityDataUI();
		final AbilityUI abilityUI = abilityData.getUI(alias);
		if (abilityUI != null) {
			return looped ? abilityUI.getEffectSoundLooped() : abilityUI.getEffectSound();
		}
		final BuffUI buffUI = abilityData.getBuffUI(alias);
		if (buffUI != null) {
			return looped ? buffUI.getEffectSoundLooped() : buffUI.getEffectSound();
		}
		return "";
	}

	/**
	 * Measured: a lightning keeps its colour as a byte per channel (the product with 255 cut to a whole number and
	 * wrapped, so 2.0 reads back as 254 / 255), also after it was destroyed; one never coloured is white, null is 0.
	 */
	private RealJassValue lightningColor(final List<JassValue> arguments, final int channel) {
		final SimulationRenderComponentLightning lightning = nullable(arguments, 0,
				ObjectJassValueVisitor.getInstance());
		if (lightning == null) {
			return RealJassValue.of(0f);
		}
		final float[] color = lightningCall(lightning, lightning::getColor);
		return RealJassValue.of(color == null ? 1f : ((int) (color[channel] * 255f) & 0xFF) / 255f);
	}
}
