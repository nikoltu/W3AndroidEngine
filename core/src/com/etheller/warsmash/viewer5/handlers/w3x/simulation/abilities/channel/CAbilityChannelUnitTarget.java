package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.channel;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;

/** Channel that targets a unit. */
public class CAbilityChannelUnitTarget extends CAbilityTargetSpellBase {
	private final ChannelSpell spell = new ChannelSpell();

	public CAbilityChannelUnitTarget(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return this.spell.getBaseOrderId();
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.spell.populate(worldEditorAbility, level);
		if (!this.spell.isVisible()) {
			setIconShowing(false);
		}
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		return this.spell.begin(simulation, caster, this);
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		return this.spell.tick(simulation, caster, getAlias());
	}

	@Override
	public void doChannelEnd(final CSimulation game, final CUnit unit, final AbilityTarget target,
			final boolean interrupted) {
		this.spell.end(unit, this);
	}

	@Override
	public boolean isUniqueCast() {
		return this.spell.isUniqueCast();
	}

	@Override
	public boolean isPhysical() {
		return this.spell.isPhysical();
	}

	@Override
	public boolean isUniversal() {
		return this.spell.isUniversal();
	}

	@Override
	public boolean isMagic() {
		return !this.spell.isPhysical();
	}
}
