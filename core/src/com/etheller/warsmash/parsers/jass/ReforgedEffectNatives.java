package com.etheller.warsmash.parsers.jass;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.etheller.interpreter.ast.function.ConvertedConstant;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.Sequence;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.IndexedSequence;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.LocalUiLink;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderCommands;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderSpellEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ScriptEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

/**
 * The Reforged natives that choose the animation of a special effect (by type, by name, queued, with sub animations
 * and a blend time), scale it on the three axes, and read the length of a unit's animations. Effects are drawn on
 * this machine only, as are these changes.
 */
final class ReforgedEffectNatives {
	private final JassProgram program;
	private final War3MapViewer viewer;
	private final RenderCommands commands;

	private ReforgedEffectNatives(final JassProgram program, final War3MapViewer viewer) {
		this.program = program;
		this.viewer = viewer;
		this.commands = LocalUiLink.of(viewer, null).commands();
	}

	static void register(final JassProgram program, final War3MapViewer viewer) {
		final ReforgedEffectNatives natives = new ReforgedEffectNatives(program, viewer);
		natives.registerAnimations();
		natives.registerMatrix();
		natives.registerAnimationDurations();
	}

	private void add(final String name, final JassFunction function) {
		this.program.getJassNativeManager().createNative(name, function);
	}

	/** A native that changes the drawn effect and returns nothing: it runs on the render side in the frame's order. */
	private void addCommand(final String name, final JassFunction function) {
		add(name, (arguments, globalScope, triggerScope) -> {
			final List<JassValue> copy = new ArrayList<>(arguments);
			this.commands.send(() -> function.call(copy, globalScope, triggerScope));
			return null;
		});
	}

	/** A native that reads the render side: the render thread runs it, after the commands sent before it. */
	private void addCall(final String name, final JassFunction function) {
		add(name, (arguments, globalScope, triggerScope) -> this.commands.call(name,
				() -> function.call(arguments, globalScope, triggerScope)));
	}

	private static <T> T object(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(ObjectJassValueVisitor.<T>getInstance());
	}

	private static String string(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(StringJassValueVisitor.getInstance());
	}

	private static float real(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(RealJassValueVisitor.getInstance()).floatValue();
	}

	private static int constant(final List<JassValue> arguments, final int index) {
		final Object value = ReforgedEffectNatives.<Object>object(arguments, index);
		return value instanceof ConvertedConstant ? ((ConvertedConstant) value).getValue() : -1;
	}

	private static RenderSpellEffect effect(final List<JassValue> arguments) {
		final ScriptEffect handle = object(arguments, 0);
		return handle == null ? null : handle.getLiveEffect();
	}

	private void registerAnimations() {
		add("BlzGetAnimName", (arguments, globalScope, triggerScope) -> {
			final String name = ScriptAnimTypes.animName(constant(arguments, 0));
			return new StringJassValue(name == null ? "" : name);
		});
		addCommand("BlzSetSpecialEffectAnimation", (arguments, globalScope, triggerScope) -> {
			final RenderSpellEffect effect = effect(arguments);
			if (effect != null) {
				effect.playScriptAnimation(string(arguments, 1));
			}
			return null;
		});
		addCommand("BlzQueueSpecialEffectAnimation", (arguments, globalScope, triggerScope) -> {
			final RenderSpellEffect effect = effect(arguments);
			if (effect != null) {
				effect.queueScriptAnimation(string(arguments, 1));
			}
			return null;
		});
		addCommand("BlzPlaySpecialEffect", (arguments, globalScope, triggerScope) -> {
			final RenderSpellEffect effect = effect(arguments);
			final String name = ScriptAnimTypes.animName(constant(arguments, 1));
			if ((effect != null) && (name != null)) {
				effect.playScriptAnimation(name);
			}
			return null;
		});
		addCommand("BlzPlaySpecialEffectWithTimeScale", (arguments, globalScope, triggerScope) -> {
			final RenderSpellEffect effect = effect(arguments);
			final String name = ScriptAnimTypes.animName(constant(arguments, 1));
			if ((effect != null) && (name != null)) {
				effect.playScriptAnimation(name);
				effect.getModelInstance().setAnimationSpeed(real(arguments, 2));
			}
			return null;
		});
		addCommand("BlzSetSpecialEffectAnimationBlendTime", (arguments, globalScope, triggerScope) -> {
			final RenderSpellEffect effect = effect(arguments);
			if (effect != null) {
				effect.getModelInstance().setBlendTime(Math.max(0, real(arguments, 1)) * 1000.0f);
			}
			return null;
		});
		addCommand("BlzSpecialEffectAddSubAnimation", (arguments, globalScope, triggerScope) -> {
			final RenderSpellEffect effect = effect(arguments);
			final SecondaryTag tag = ScriptAnimTypes.subAnim(constant(arguments, 1));
			if ((effect != null) && (tag != null)) {
				effect.addScriptSubAnimation(tag);
			}
			return null;
		});
		addCommand("BlzSpecialEffectRemoveSubAnimation", (arguments, globalScope, triggerScope) -> {
			final RenderSpellEffect effect = effect(arguments);
			final SecondaryTag tag = ScriptAnimTypes.subAnim(constant(arguments, 1));
			if ((effect != null) && (tag != null)) {
				effect.removeScriptSubAnimation(tag);
			}
			return null;
		});
		addCommand("BlzSpecialEffectClearSubAnimations", (arguments, globalScope, triggerScope) -> {
			final RenderSpellEffect effect = effect(arguments);
			if (effect != null) {
				effect.clearScriptSubAnimations();
			}
			return null;
		});
	}

	private void registerMatrix() {
		addCommand("BlzSetSpecialEffectMatrixScale", (arguments, globalScope, triggerScope) -> {
			final RenderSpellEffect effect = effect(arguments);
			if (effect != null) {
				effect.setScriptMatrixScale(real(arguments, 1), real(arguments, 2), real(arguments, 3));
			}
			return null;
		});
		addCommand("BlzResetSpecialEffectMatrix", (arguments, globalScope, triggerScope) -> {
			final RenderSpellEffect effect = effect(arguments);
			if (effect != null) {
				effect.resetScriptMatrix();
			}
			return null;
		});
		addCall("BlzGetSpecialEffectScale", (arguments, globalScope, triggerScope) -> {
			final RenderSpellEffect effect = effect(arguments);
			return RealJassValue.of(effect == null ? 0 : effect.getScriptScale());
		});
	}

	/** The seconds an animation of the unit's model runs, or 0 when it has none by that name or number. */
	private static float duration(final MdxComplexInstance instance, final int index) {
		final List<Sequence> sequences = ((MdxModel) instance.model).getSequences();
		if ((index < 0) || (index >= sequences.size())) {
			return 0;
		}
		final long[] interval = sequences.get(index).getInterval();
		return (interval[1] - interval[0]) / 1000f;
	}

	private void registerAnimationDurations() {
		addCall("BlzGetUnitAnimationDuration", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final RenderUnit peer = unit == null ? null : this.viewer.getRenderPeer(unit);
			final String name = string(arguments, 1);
			if ((peer == null) || (name == null)) {
				return RealJassValue.ZERO;
			}
			final EnumSet<PrimaryTag> primaryTags = EnumSet.noneOf(PrimaryTag.class);
			final EnumSet<SecondaryTag> secondaryTags = EnumSet.noneOf(SecondaryTag.class);
			Sequence.populateTags(primaryTags, secondaryTags, name);
			final IndexedSequence sequence = SequenceUtils.selectSequence(Sequence.any(primaryTags), secondaryTags,
					((MdxModel) peer.instance.model).getSequences(), false);
			return RealJassValue.of(sequence == null ? 0 : duration(peer.instance, sequence.index));
		});
		addCall("BlzGetUnitAnimationDurationByIndex", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final RenderUnit peer = unit == null ? null : this.viewer.getRenderPeer(unit);
			return RealJassValue.of(peer == null ? 0
					: duration(peer.instance, arguments.get(1).visit(IntegerJassValueVisitor.getInstance())));
		});
	}
}
