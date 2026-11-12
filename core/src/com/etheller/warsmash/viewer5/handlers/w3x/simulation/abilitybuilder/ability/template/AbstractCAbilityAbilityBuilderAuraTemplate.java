package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.template;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAreaTargetVisibility;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitEnumFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbilityGenericSingleIconPassiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABLocalStoreKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.types.impl.CAbilityTypeAbilityBuilderLevelData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

/**
 * An aura of an Ability Builder template: the units around its owner that it may target get its effect. Units leave
 * every 3 s and join every 0.4 s; a disabled aura checks like 1.29 (see {@link AuraCheckClock}).
 */
public abstract class AbstractCAbilityAbilityBuilderAuraTemplate extends AbilityGenericSingleIconPassiveAbility {
	private static final int LEAVE_GROUP_TICKS = CTimer.wholeSteps(3);
	private static final int ENTER_GROUP_TICKS = CTimer.wholeSteps(0.4);
	private static final int RESET_GROUP_TICKS = LEAVE_GROUP_TICKS * 2;
	private static final Rectangle recycleRect = new Rectangle();

	protected List<CAbilityTypeAbilityBuilderLevelData> levelData;
	protected Map<String, Object> localStore;

	protected Set<CUnit> auraGroup;
	private int lastSeenLevel = 0;
	private int loopTick = 0;
	private final AuraCheckClock checkClock = new AuraCheckClock();

	protected EnumSet<CTargetType> targetsAllowed = null;
	protected float range = 0;

	public AbstractCAbilityAbilityBuilderAuraTemplate(final int handleId, final War3ID code, final War3ID alias,
			final List<CAbilityTypeAbilityBuilderLevelData> levelData, final Map<String, Object> localStore) {
		super(code, alias, handleId);
		this.levelData = levelData;
		this.localStore = localStore;
	}

	@Override
	public void setLevel(final CSimulation game, final CUnit unit, final int level) {
		super.setLevel(game, unit, level);
		this.localStore.put(ABLocalStoreKeys.CURRENTLEVEL, level);
	}

	@Override
	public void onAddDisabled(final CSimulation game, final CUnit unit) {
		super.onAddDisabled(game, unit);
		this.checkClock.start();
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		if (!this.checkClock.isJustStarted()) {
			this.checkClock.enabled();
		}
		if (this.auraGroup != null) {
			onAuraResumed(game, unit);
			return;
		}
		beforeAuraStarts(game, unit);
		this.auraGroup = new LinkedHashSet<>();
		this.localStore.put(ABLocalStoreKeys.AURAGROUP, this.auraGroup);
		this.lastSeenLevel = getLevel();
		onAuraStarted(game, unit);
	}

	/** Added again to the unit it was on, its units still in the aura. */
	protected void onAuraResumed(final CSimulation game, final CUnit unit) {
	}

	protected void beforeAuraStarts(final CSimulation game, final CUnit unit) {
	}

	protected void onAuraStarted(final CSimulation game, final CUnit unit) {
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		if (isDisabled()) {
			this.checkClock.disabled();
		}
		else {
			emptyAura(game, unit);
		}
	}

	@Override
	public void onRemoveDisabled(final CSimulation game, final CUnit unit) {
		super.onRemoveDisabled(game, unit);
		emptyAura(game, unit);
		// Taken off the unit for good: a later add starts a new aura.
		this.auraGroup = null;
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		if (unit.isIllusion()) {
			return;
		}
		// A disabled aura (player-disabled or unmet requirements) stays in the unit's ability list and keeps
		// ticking; one added while disabled never ran onAdd.
		if (isDisabled() || (this.auraGroup == null)) {
			if (this.checkClock.tickDisabled()) {
				emptyAura(game, unit);
			}
			return;
		}
		final int clockState = this.checkClock.tickEnabled();
		if (clockState == AuraCheckClock.WAIT) {
			return;
		}
		if (clockState == AuraCheckClock.RESUME) {
			this.loopTick = 0;
		}
		if ((this.loopTick % LEAVE_GROUP_TICKS) == 0) {
			if (tracksLevelChanges() && (this.lastSeenLevel != getLevel())) {
				for (final CUnit iter : new ArrayList<>(this.auraGroup)) {
					if (!(iter.canBeAreaTargetedBy(game, unit, CAreaTargetVisibility.ANY, this.targetsAllowed)
							&& unit.canReach(iter, this.range))) {
						updateLevelOfAura(game, iter, this.lastSeenLevel, getLevel());
					}
				}
				this.lastSeenLevel = getLevel();
			}
			for (final CUnit iter : new ArrayList<>(this.auraGroup)) {
				if (!(iter.canBeAreaTargetedBy(game, unit, CAreaTargetVisibility.ANY, this.targetsAllowed)
						&& unit.canReach(iter, this.range))) {
					removeUnitFromAura(game, iter);
				}
			}
		}
		if ((this.loopTick % ENTER_GROUP_TICKS) == 0) {
			recycleRect.set(unit.getX() - this.range, unit.getY() - this.range, this.range * 2, this.range * 2);
			game.getWorldCollision().enumUnitsInRect(recycleRect, new CUnitEnumFunction() {
				@Override
				public boolean call(final CUnit enumUnit) {
					if (unit.canReach(enumUnit, AbstractCAbilityAbilityBuilderAuraTemplate.this.range)
							&& enumUnit.canBeAreaTargetedBy(game, unit, CAreaTargetVisibility.ANY,
									AbstractCAbilityAbilityBuilderAuraTemplate.this.targetsAllowed)
							&& !AbstractCAbilityAbilityBuilderAuraTemplate.this.auraGroup.contains(enumUnit)
							&& canJoin(enumUnit)) {
						addUnitToAura(game, enumUnit);
					}
					return false;
				}
			});
		}
		this.loopTick++;
		this.loopTick = this.loopTick % RESET_GROUP_TICKS;
	}

	/** Whether the units of the aura follow its level when it changes. */
	protected boolean tracksLevelChanges() {
		return false;
	}

	/** A further test, after range and targets allowed, for a unit to join the aura. */
	protected boolean canJoin(final CUnit enumUnit) {
		return true;
	}

	public void emptyAura(final CSimulation game, final CUnit unit) {
		if (this.auraGroup == null) {
			return;
		}
		for (final CUnit iter : new ArrayList<>(this.auraGroup)) {
			removeUnitFromAura(game, iter);
		}
	}

	public abstract void addUnitToAura(CSimulation game, CUnit unit);

	public void updateLevelOfAura(final CSimulation game, final CUnit unit, final int prevLevel, final int curLevel) {
	}

	public abstract void removeUnitFromAura(CSimulation game, CUnit unit);

	@Override
	public void onDeath(final CSimulation game, final CUnit unit) {
		emptyAura(game, unit);
	}

	@Override
	public boolean isUniversal() {
		return true;
	}
}
