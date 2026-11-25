package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import com.etheller.interpreter.ast.util.AllocatedHandle;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderSpellEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ScriptEffect;

/**
 * What a script's effect handle is while the simulation runs on its own thread: the handle exists at once, the effect
 * itself is made by the render thread when the frame that holds the creating command is played, and is attached then.
 * Every use of the effect runs as a command after the one that made it, on the render thread.
 */
public final class PendingEffect extends AllocatedHandle implements ScriptEffect {
	private volatile RenderSpellEffect effect;

	/** Render thread: the effect the creating command made, null when the model could not be shown. */
	public void attach(final RenderSpellEffect made) {
		this.effect = made;
	}

	/** The made effect, or null while it is not made yet or could not be. */
	public RenderSpellEffect made() {
		return this.effect;
	}

	@Override
	public void setKillWhenDone(final boolean killWhenDone) {
		final RenderSpellEffect made = this.effect;
		if (made != null) {
			made.setKillWhenDone(killWhenDone);
		}
	}

	@Override
	public void removeWithoutDeath() {
		final RenderSpellEffect made = this.effect;
		if (made != null) {
			made.removeWithoutDeath();
		}
	}

	@Override
	public RenderSpellEffect getLiveEffect() {
		final RenderSpellEffect made = this.effect;
		return made == null ? null : made.getLiveEffect();
	}
}
