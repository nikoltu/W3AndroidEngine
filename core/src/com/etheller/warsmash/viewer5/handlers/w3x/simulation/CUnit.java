package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntIntMap;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.RemovableTriggerEvent;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.interpreter.ast.scope.variableevent.CLimitOp;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.blademaster.CBuffWhirlWindCaster;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.PathingFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.RemovablePathingMapInstance;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedComponent;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedDefault;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitStateListener.CUnitStateNotifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityDisableType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.GetAbilityByRawcodeVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.autocast.CAutocastAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold.CAbilityCargoHold;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilityNeutralBuilding;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.listeners.CUnitAbilityEffectReactionListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityGoldMinable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityOverlayedMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.queue.CAbilityReviveHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.ABGenericTimedBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.BehaviorAbilityVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttackMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorBoardTransport;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorFollow;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorHoldPosition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorPatrol;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorStop;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.harvest.CBehaviorReturnResources;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAreaTargetVisibility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDefenseType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackDamageTakenListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackDamageTakenModificationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackEvasionListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackFinalDamageTakenModificationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPostDamageListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPreDamageListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPreDamageListenerPriority;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDeathReplacementEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDeathReplacementEffectPriority;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDefaultAccuracyCheckListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDefaultLifestealListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDefaultThornsListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitAttackModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitAttackSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAttackProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.listeners.CUnitAbilityProjReactionListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.listeners.CUnitAttackProjReactionListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderNoTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetPoint;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CUnitAttackVisionFogModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.region.CRegion;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.region.CRegionManager;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.state.CUnitState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitAppliedUpgrade;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitBehaviorChangeListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingFx;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffs;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ResourceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitAcquisition.AutocastTargetFinderEnum;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitAcquisition.AutoAttackTargetFinderEnum;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitOrders.UseAbilityOnTargetByIdVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitPlacement.RegionCheckerImpl;

public class CUnit extends CWidget {
	static final int CHEESY_UNIQUE_NUMBER = 1337;
	// The original game's free cell search for a unit covers 63 x 63 cells, 31 rings round the point.
	static final int UNIT_PLACE_SEARCH_CELLS = 63 * 63;

	private static final StateModBuff pauseBuff = new StateModBuff(StateModBuffType.PAUSED, 1);

	War3ID typeId;
	float facing; // degrees
	float handicap = 1f;
	/** Vertex color {r, g, b, a} the game gave the unit (abilities, scripts), or null for its type's tint. */
	private float[] vertexColor;
	private int finalSpeed;


	CUnitDefaultThornsListener flatThornsListener = null;
	CUnitDefaultThornsListener percentThornsListener = null;
	CUnitDefaultLifestealListener lifestealListener = null;

	final List<StateModBuff> stateModBuffs = new ArrayList<>();
	@SavedComponent
	final NonStackingStatBuffs nonStackingStatBuffs = new NonStackingStatBuffs();
	@SavedComponent
	final CUnitDeathState death = new CUnitDeathState();
	@SavedComponent
	final CUnitAggroState aggro = new CUnitAggroState();
	@SavedComponent
	final CUnitProductionState production = new CUnitProductionState();
	@SavedComponent
	final CUnitVisibilityState visibility = new CUnitVisibilityState();
	@SavedComponent
	final CUnitAcquisitionState acquisition = new CUnitAcquisitionState();
	@SavedComponent
	final CUnitOrderState orders = new CUnitOrderState();
	@SavedComponent
	final CUnitRescueState rescue = new CUnitRescueState();
	@SavedComponent
	final CUnitStatsState stats = new CUnitStatsState();
	@SavedComponent
	final CUnitDefenseState defenseState = new CUnitDefenseState();
	@SavedComponent
	final CUnitIllusionState illusionState = new CUnitIllusionState();
	@SavedComponent
	final CUnitMovementState movement = new CUnitMovementState();
	@SavedComponent
	final CUnitInvulnerabilityState invulnerability = new CUnitInvulnerabilityState();
	@SavedComponent
	final CUnitPlacementState placement = new CUnitPlacementState();
	@SavedComponent
	final CUnitBlockedState blocked = new CUnitBlockedState();
	@SavedComponent
	final CUnitFoodState food = new CUnitFoodState();
	@SavedComponent
	final CUnitAutoState auto = new CUnitAutoState();
	final Map<String, List<NonStackingFx>> nonStackingFx = new HashMap<>();
	final Map<String, List<CBuff>> nonStackingDisplayBuffs = new HashMap<>();


	private int cooldownEndTime = 0;
	private int lastWalkTick = Integer.MIN_VALUE / 2;
	int playerIndex;
	int fakePlayerIndex;

	final List<CAbility> abilities = new ArrayList<>();
	private final List<CAbility> disabledAbilities = new ArrayList<>();
	final Set<CUnitAppliedUpgrade> appliedUpgrades = new LinkedHashSet<>();
	final List<Trigger> onTickTriggers = new ArrayList<>();

	CUnitType unitType;
	/** Which unit made before the game started this is, counting from 0. */
	private int startCreationIndex;
	/** The index of the last unit of this unit's type made before the game started; null for a unit made later. */
	private int[] startTypeLastIndex;


	final EnumSet<CUnitClassification> classifications = EnumSet.noneOf(CUnitClassification.class);
	final EnumSet<CTargetType> targetedAs = EnumSet.noneOf(CTargetType.class);

	transient CUnitAnimationListener unitAnimationListener;

	// if you use triggers for this then the transient tag here becomes really
	// questionable -- it already was -- but I meant for those to inform us
	// which fields shouldn't be persisted if we do game state save later
	transient CUnitStateNotifier stateNotifier = new CUnitStateNotifier();
	transient List<StateListenerUpdate> stateListenersUpdates = new ArrayList<>();
	float acquisitionRange;
	transient CAutocastAbility autocastAbility = null;
	EnumSet<CTargetType> priorityAttackTargets = null;

	transient CBehaviorMove moveBehavior;
	transient CBehaviorAttack attackBehavior;
	private transient CBehaviorAttackMove attackMoveBehavior;
	private transient CBehaviorFollow followBehavior;
	transient CBehaviorPatrol patrolBehavior;
	transient CBehaviorStop stopBehavior;
	transient CBehaviorHoldPosition holdPositionBehavior;
	private transient CBehaviorBoardTransport boardTransportBehavior;
	boolean structure;
	boolean uprooted;
	boolean hidden = false;
	boolean removedFromGame;
	boolean paused = false;
	private static final War3ID INVULNERABLE_ABILITY_ID = War3ID.fromString("Avul");
	/** A worker inside a gold mine or the Orc building it builds. */
	public static final StateModBuff INSIDE_INVULNERABLE = new StateModBuff(StateModBuffType.INVULNERABLE, 1);
	/** A unit raised by Animate Dead, for its whole life. */
	public static final StateModBuff ANIMATED_INVULNERABLE = new StateModBuff(StateModBuffType.INVULNERABLE, 1);
	private boolean magicImmune = false;
	private boolean limitedMagicImmune = false;
	private final boolean selectable = false;
	static final Rectangle callForHelpRect = new Rectangle();
	static final float CREEP_ALERT_RADIUS = 800f;
	static final float GUARD_LOST_STOP = 48f;
	static final float GUARD_GIVE_UP_EXTRA_SECONDS = 1.5f;
	static final int GUARD_RETURN_NONE = 0;
	static final int GUARD_RETURN_LOST = 1;
	static final int GUARD_RETURN_GIVE_UP = 2;
	static final War3ID CREEP_SLEEP = War3ID.fromString("Asla");
	CAbility finishedSpell;
	static final float SLEEP_AFTER_WAKE_SECONDS = 2.0f;
	private Boolean canSleepOverride;
	private String nameOverride;
	static final float DEFAULT_RESCUE_RANGE = 414.5f;
	static final int RESCUE_REPEAT_TICKS = CTimer.wholeSteps(1);
	private COrder scriptAnimationOrder = null;
	private int powerWorkers;


	private int triggerEditorCustomValue;

	List<CUnitAttack> unitSpecificAttacks;
	private List<CUnitAttack> unitSpecificCurrentAttacks;
	private boolean disableAttacks;
	final CUnitAttackVisionFogModifier attackFogMod;

	private final Map<CUnitAttackPreDamageListenerPriority, List<CUnitAttackPreDamageListener>> preDamageListeners = new HashMap<>();
	private final List<CUnitAttackPostDamageListener> postDamageListeners = new ArrayList<>();
	final List<CUnitAttackDamageTakenModificationListener> damageTakenModificationListeners = new ArrayList<>();
	final List<CUnitAttackFinalDamageTakenModificationListener> finalDamageTakenModificationListeners = new ArrayList<>();
	final List<CUnitAttackDamageTakenListener> damageTakenListeners = new ArrayList<>();
	private float healingReceivedFactor = 1f;
	final Map<CUnitDeathReplacementEffectPriority, List<CUnitDeathReplacementEffect>> deathReplacementEffects = new HashMap<>();
	final List<CUnitAttackEvasionListener> evasionListeners = new ArrayList<>();
	final List<CUnitAttackProjReactionListener> attackProjReactionListeners = new ArrayList<>();
	final List<CUnitAbilityProjReactionListener> abilityProjReactionListeners = new ArrayList<>();
	final List<CUnitAbilityEffectReactionListener> abilityEffectReactionListeners = new ArrayList<>();
	final List<CUnitAttackModifier> attackModifiers = new ArrayList<>();

	private final List<CUnitBehaviorChangeListener> behaviorChangeListeners = new ArrayList<>();

	transient Set<CRegion> containingRegions = new LinkedHashSet<>();
	transient Set<CRegion> priorContainingRegions = new LinkedHashSet<>();
	transient boolean regionEventsDeferred;
	transient float deadScriptLife = Float.NaN;
	transient List<PendingRegionEvent> pendingRegionEvents;

	private final IntIntMap rawcodeToCooldownExpireTime = new IntIntMap();
	private final IntIntMap rawcodeToCooldownStartTime = new IntIntMap();

	private CTimer fadeTimer;
	int fadeHoldGeneration = -1;

	private boolean usesAltMinimapIcon;

	boolean unenumerable = false;



	private List<String> uniqueFlags = null;

	public CUnit(final int handleId, final int playerIndex, final float x, final float y, final float life,
			final War3ID typeId, final float facing, final float mana, final int maximumLife, final float lifeRegen,
			final int maximumMana, final int speed, final int maxSpeed, final int minSpeed, final CUnitType unitType) {
		super(handleId, x, y, life);
		this.playerIndex = playerIndex;
		this.fakePlayerIndex = playerIndex;
		this.typeId = typeId;
		this.facing = facing;
		this.stats.drawnFacing = this.facing;
		this.stats.drawnFacingTick = Integer.MIN_VALUE;
		this.stats.mana = mana;
		this.stats.baseMaximumLife = maximumLife;
		this.stats.maximumLife = maximumLife;
		this.stats.lifeRegen = lifeRegen;
		this.stats.manaRegen = unitType.getManaRegen();
		this.stats.baseMaximumMana = maximumMana;
		this.stats.maximumMana = maximumMana;
		this.stats.maxSpeed = maxSpeed;
		this.stats.minSpeed = minSpeed;
		this.stats.baseSpeed = speed;
		this.placement.flyHeight = unitType.getDefaultFlyingHeight();
		this.placement.turnRate = unitType.getTurnRate();
		this.placement.propWindow = unitType.getPropWindow();
		this.unitType = unitType;
		this.defenseState.defenseType = unitType.getDefenseType();
		this.classifications.addAll(unitType.getClassifications());
		this.targetedAs.addAll(unitType.getTargetedAs());
		this.acquisitionRange = unitType.getDefaultAcquisitionRange();
		this.structure = unitType.isBuilding();
		this.stopBehavior = new CBehaviorStop(this);
		this.orders.defaultBehavior = this.stopBehavior;
		this.death.raisable = unitType.isRaise();
		this.death.decays = unitType.isDecay();
		initializeListenerLists();
		addPreDamageListener(CUnitAttackPreDamageListenerPriority.ACCURACY, new CUnitDefaultAccuracyCheckListener());
		this.attackFogMod = new CUnitAttackVisionFogModifier(this, playerIndex);
		CUnitStats.computeAllDerivedFields(this);
	}

	public void beginBehavior(final CSimulation game, final CBehavior behavior) {
		CUnitOrders.beginBehavior(this, game, behavior);
	}

	public void beginBehavior(final CSimulation game, final CBehavior behavior, boolean interrupted) {
		CUnitOrders.beginBehavior(this, game, behavior, interrupted);
	}

	public CBehavior finishMoveBehavior(final CSimulation game, final CBehavior behavior) {
		fireBehaviorChangeEvent(game, behavior, false);

		return behavior.update(game);
	}

	public void fireBehaviorChangeEvent(final CSimulation game, final CBehavior behavior, final boolean ongoing) {
		for (final CUnitBehaviorChangeListener listener : new ArrayList<>(this.behaviorChangeListeners)) {
			listener.onChange(game, this, this.orders.currentBehavior, behavior, ongoing);
		}
	}

	public void performDefaultBehavior(final CSimulation game) {
		beginBehavior(game, this.orders.defaultBehavior, true);
	}

	public void regeneratePathingInstance(final CSimulation game, final ArgbImage buildingPathingPixelMap) {
		CUnitPlacement.regeneratePathingInstance(this, game, buildingPathingPixelMap);
	}

	public float getLifeRegenBonus() {
		return this.stats.lifeRegenBonus;
	}

	public void setLifeRegenStrengthBonus(final float lifeRegenStrengthBonus) {
		this.stats.lifeRegenStrengthBonus = lifeRegenStrengthBonus;
		computeDerivedFields(NonStackingStatBuffType.HPGEN);
	}

	public void setFadeTimer(final CTimer whichTimer) {
		this.fadeTimer = whichTimer;
		this.fadeHoldGeneration = -1;
	}

	public CTimer getFadeTimer() {
		return this.fadeTimer;
	}

	public byte getInvisLevels() {
		return this.visibility.invisLevels;
	}

	public void addNonStackingStatBuff(final CSimulation game, final NonStackingStatBuff buff) {
		this.nonStackingStatBuffs.add(buff);
		if (buff.getBuffType().isHeroStat()) {
			computeDerivedHeroFields(game, buff.getBuffType());
		} else {
			computeDerivedFields(buff.getBuffType());
		}
	}

	public void removeNonStackingStatBuff(final CSimulation game, final NonStackingStatBuff buff) {
		this.nonStackingStatBuffs.remove(buff, getTypeId().asStringValue());
		if (buff.getBuffType().isHeroStat()) {
			computeDerivedHeroFields(game, buff.getBuffType());
		} else {
			computeDerivedFields(buff.getBuffType());
		}
	}

	public void addStateModBuff(final StateModBuff listener) {
		if (!this.stateModBuffs.contains(listener)) {
			this.stateModBuffs.add(0, listener);
		}
	}

	public void removeStateModBuff(final StateModBuff listener) {
		this.stateModBuffs.remove(listener);
	}

	public void removeAllStateModBuffs(final StateModBuffType type) {
		for (int i = this.stateModBuffs.size() - 1; i >= 0; i--) {
			if (this.stateModBuffs.get(i).getBuffType() == type) {
				this.stateModBuffs.remove(i);
			}
		}
	}

	public void computeUnitState(final CSimulation game, final StateModBuffType type) {
		CUnitStats.computeUnitState(this, game, type);
	}

	public void computeDerivedFields(final NonStackingStatBuffType type) {
		CUnitStats.computeDerivedFields(this, type);
	}

	public void computeDerivedHeroFields(final CSimulation game, final NonStackingStatBuffType type) {
		CUnitStats.computeDerivedHeroFields(this, game, type);
	}

	public NonStackingFx addNonStackingFx(final CSimulation game, final String stackingKey, final War3ID id,
			final CEffectType target) {
		return CUnitStats.addNonStackingFx(this, game, stackingKey, id, target);
	}

	public void removeNonStackingFx(final CSimulation game, final NonStackingFx fx) {
		CUnitStats.removeNonStackingFx(this, game, fx);
	}

	public void addNonStackingDisplayBuff(final CSimulation game, final String stackingKey, final CBuff buff) {
		CUnitStats.addNonStackingDisplayBuff(this, game, stackingKey, buff);
	}

	public void removeNonStackingDisplayBuff(final CSimulation game, final String stackingKey, final CBuff buff) {
		CUnitStats.removeNonStackingDisplayBuff(this, game, stackingKey, buff);
	}

	private void initializeListenerLists() {
		for (final CUnitAttackPreDamageListenerPriority priority : CUnitAttackPreDamageListenerPriority.values()) {
			this.preDamageListeners.put(priority, new ArrayList<>());
		}
		for (final CUnitDeathReplacementEffectPriority priority : CUnitDeathReplacementEffectPriority.values()) {
			this.deathReplacementEffects.put(priority, new ArrayList<>());
		}
	}

	public void setManaRegenIntelligenceBonus(final float manaRegenIntelligenceBonus) {
		this.stats.manaRegenIntelligenceBonus = manaRegenIntelligenceBonus;
		computeDerivedFields(NonStackingStatBuffType.MPGEN);
	}

	public void setLifeRegenBonus(final float lifeRegenBonus) {
		this.stats.lifeRegenBonus = lifeRegenBonus;
		computeDerivedFields(NonStackingStatBuffType.HPGEN);
	}

	public void setManaRegenBonus(final float manaRegenBonus) {
		this.stats.manaRegenBonus = manaRegenBonus;
		computeDerivedFields(NonStackingStatBuffType.MPGEN);
	}

	public void setManaRegen(final float manaRegen) {
		this.stats.manaRegen = manaRegen;
		computeDerivedFields(NonStackingStatBuffType.MPGEN);
	}

	public float getManaRegenBonus() {
		return this.stats.manaRegenBonus;
	}

	public float getManaRegen() {
		return this.stats.manaRegen;
	}

	public void setAgilityDefensePermanentBonus(final float agilityDefensePermanentBonus) {
		this.defenseState.agilityDefensePermanentBonus = agilityDefensePermanentBonus;
		computeDerivedFields(NonStackingStatBuffType.DEF);
	}

	public void setAgilityDefenseTemporaryBonus(final float agilityDefenseTemporaryBonus) {
		this.defenseState.agilityDefenseTemporaryBonus = agilityDefenseTemporaryBonus;
		computeDerivedFields(NonStackingStatBuffType.DEF);
	}

	public void setAgilityMoveBonus(final float agilityMoveBonus) {
		this.defenseState.agilityMoveBonus = agilityMoveBonus;
		computeDerivedFields(NonStackingStatBuffType.MVSPD);
	}

	public void setPermanentDefenseBonus(final int permanentDefenseBonus) {
		this.defenseState.permanentDefenseBonus = permanentDefenseBonus;
		computeDerivedFields(NonStackingStatBuffType.DEF);
	}

	public int getPermanentDefenseBonus() {
		return this.defenseState.permanentDefenseBonus;
	}

	/** BlzSetUnitArmor: the base armor changes so the total is the given value; bonuses keep adding on top. */
	public void setTotalDefense(final float totalDefense) {
		this.defenseState.scriptDefenseBonus += totalDefense - this.defenseState.currentDefense;
		computeDerivedFields(NonStackingStatBuffType.DEF);
	}

	public void setTemporaryDefenseBonus(final float temporaryDefenseBonus) {
		this.defenseState.temporaryDefenseBonus = temporaryDefenseBonus;
		computeDerivedFields(NonStackingStatBuffType.DEF);
	}

	public float getTemporaryDefenseBonus() {
		return this.defenseState.temporaryDefenseBonus;
	}

	public float getTotalTemporaryDefenseBonus() {
		return this.defenseState.totalTemporaryDefenseBonus;
	}

	public int getCurrentDefenseDisplay() {
		return this.defenseState.currentDefenseDisplay;
	}

	public void setUnitAnimationListener(final CUnitAnimationListener unitAnimationListener) {
		this.unitAnimationListener = unitAnimationListener;
		this.unitAnimationListener.playAnimation(true, PrimaryTag.STAND, SequenceUtils.EMPTY, 1.0f, true);
	}

	public CUnitAnimationListener getUnitAnimationListener() {
		return this.unitAnimationListener;
	}

	public void registerOnTickEvent(final Trigger trigger) {
		this.onTickTriggers.add(trigger);
		this.stateNotifier.abilitiesChanged();
	}

	public void unregisterOnTickEvent(final Trigger trigger) {
		this.onTickTriggers.remove(trigger);
		this.stateNotifier.abilitiesChanged();
	}

	public void add(final CSimulation simulation, final CAbility ability) {
		this.abilities.add(ability);
		ability.onAddDisabled(simulation, this);
		simulation.onAbilityAddedToUnit(this, ability);
		if (!ability.isRequirementsMet(simulation, this)) {
			ability.setDisabled(true, CAbilityDisableType.REQUIREMENTS);
		}
		if (simulation.getPlayer(this.playerIndex).isAbilityDisabled(ability.getAlias())) {
			ability.setDisabled(true, CAbilityDisableType.PLAYER);
		}
		if (ability.isDisabled()) {
			this.disabledAbilities.add(ability);
		} else {
			ability.onAdd(simulation, this);
		}
		this.stateNotifier.abilitiesChanged();
	}

	public void add(final CSimulation simulation, final CBuff ability) {
		int index = this.abilities.indexOf(ability);
		if (index >= 0 && (ability instanceof ABGenericTimedBuff)) {
			((ABGenericTimedBuff) this.abilities.get(index)).updateExpiration(simulation, this);
		} else {
			this.abilities.add(ability);
			simulation.onAbilityAddedToUnit(this, ability);
			ability.onAdd(simulation, this);
			this.stateNotifier.abilitiesChanged();
		}
	}

	public void remove(final CSimulation simulation, final CAbility ability) {
		if (this.disabledAbilities.contains(ability)) {
			this.abilities.remove(ability);
			this.disabledAbilities.remove(ability);
			simulation.onAbilityRemovedFromUnit(this, ability);
			ability.onRemoveDisabled(simulation, this);
			this.stateNotifier.abilitiesChanged();
		} else {
			this.abilities.remove(ability);
			simulation.onAbilityRemovedFromUnit(this, ability);
			ability.onRemove(simulation, this);
			ability.onRemoveDisabled(simulation, this);
			this.stateNotifier.abilitiesChanged();
		}
	}

	public void remove(final CSimulation simulation, final CBuff ability) {
		this.abilities.remove(ability);
		simulation.onAbilityRemovedFromUnit(this, ability);
		ability.onRemove(simulation, this);
		this.stateNotifier.abilitiesChanged();
	}

	public void checkDisabledAbilities(final CSimulation simulation, final boolean disable) {
		if (disable) {
			for (final CAbility ability : new ArrayList<>(this.abilities)) {
				if (!ability.isRequirementsMet(simulation, this)) {
					ability.setDisabled(true, CAbilityDisableType.REQUIREMENTS);
				}
				if (simulation.getPlayer(this.playerIndex).isAbilityDisabled(ability.getAlias())) {
					ability.setDisabled(true, CAbilityDisableType.PLAYER);
				}
				if (ability.isDisabled() && !this.disabledAbilities.contains(ability)) {
//					System.err.println("Disabling ability: " + ability.getAlias().asStringValue());
					this.disabledAbilities.add(ability);
					ability.onRemove(simulation, this);
					if ((this.orders.currentBehavior != null)
							&& ability.equals(this.orders.currentBehavior.visit(BehaviorAbilityVisitor.INSTANCE))) {
						performDefaultBehavior(simulation);
					}
				}
			}
		} else {
			for (final CAbility ability : new ArrayList<>(this.disabledAbilities)) {
				if (ability.isRequirementsMet(simulation, this)) {
					ability.setDisabled(false, CAbilityDisableType.REQUIREMENTS);
				}
				if (!simulation.getPlayer(this.playerIndex).isAbilityDisabled(ability.getAlias())) {
					ability.setDisabled(false, CAbilityDisableType.PLAYER);
				}
				if (!ability.isDisabled()) {
					this.disabledAbilities.remove(ability);
					ability.onAdd(simulation, this);
				}
			}
		}
		fireCooldownsChangedEvent();
	}

	/** The vertex color the game gave the unit, or null while it has its type's tint. */
	public float[] getVertexColor() {
		return this.vertexColor;
	}

	public void setVertexColor(final float[] vertexColor) {
		this.vertexColor = vertexColor;
	}

	public War3ID getTypeId() {
		return this.typeId;
	}

	/**
	 * @return facing in DEGREES
	 */
	public float getFacing() {
		return this.facing;
	}

	/** The facing GetUnitFacing reads, at the game's time now; see {@link #drawnFacing}. */
	public float getDrawnFacing(final CSimulation game) {
		CUnitUpdate.updateDrawnFacing(this, CTimer.currentTime(game));
		return this.stats.drawnFacing;
	}

	/** Turns the drawn facing at once too, as BlzSetUnitFacingEx does. */
	public void setFacingAndDrawnFacing(final float facing) {
		setFacing(facing);
		this.stats.drawnFacing = this.facing;
		this.stats.drawnTurnVelocity = 0;
	}

	public float getMana() {
		return this.stats.mana;
	}

	public int getMaximumMana() {
		return this.stats.maximumMana;
	}

	public int getMaximumLife() {
		return this.stats.maximumLife;
	}

	public void setTypeId(final CSimulation game, final War3ID typeId, final boolean keepRatios) {
		CUnitTypes.setTypeId(this, game, typeId, keepRatios);
	}

	public void setTypeId(final CSimulation game, final War3ID typeId, final boolean keepRatios,
			final boolean updateArt) {
		CUnitTypes.setTypeId(this, game, typeId, keepRatios, updateArt);
	}

	public void setFacing(final float facing) {
		// java modulo output can be negative, but not if we
		// force positive and modulo again
		this.facing = ((facing % 360) + 360) % 360;
	}

	public void setMana(final float mana) {
		this.stats.mana = mana;
		this.stateNotifier.manaChanged();
		CUnitStats.checkStateEvents(this, false);
	}

	public void setMaximumLife(final int maximumLife) {
		this.stats.baseMaximumLife = maximumLife;
		computeDerivedFields(NonStackingStatBuffType.MAXHPPCT);
		computeDerivedFields(NonStackingStatBuffType.MAXHPGENPCT);
	}

	public void addMaxLifeRelative(final CSimulation game, final int hitPointBonus) {
		final int oldMaximumLife = getMaximumLife();
		final float oldLife = getLife();
		setMaximumLife(this.stats.baseMaximumLife + hitPointBonus);
		setLife(game, (oldLife * getMaximumLife()) / oldMaximumLife);
	}

	/**
	 * The owner's handicap scales maximum life at once, as in the original game; life keeps its share of the maximum.
	 */
	public void setHandicap(final float handicap) {
		final float lifeShare = this.stats.maximumLife > 0 ? this.life / this.stats.maximumLife : 1;
		this.handicap = handicap;
		computeDerivedFields(NonStackingStatBuffType.MAXHP);
		this.life = lifeShare * this.stats.maximumLife;
		this.stateNotifier.lifeChanged();
	}

	public void setMaximumMana(final int maximumMana) {
		this.stats.baseMaximumMana = maximumMana;
		computeDerivedFields(NonStackingStatBuffType.MAXMPPCT);
		computeDerivedFields(NonStackingStatBuffType.MAXMPGENPCT);
	}

	public void setSpeed(final int speed) {
		this.stats.baseSpeed = speed;
		computeDerivedFields(NonStackingStatBuffType.MVSPD);
	}

	void computeFinalSpeed() {
		this.finalSpeed = Math.max(Math.min(this.stats.baseSpeed + this.stats.speedBonus, this.stats.maxSpeed), this.stats.minSpeed);
	}

	public int getSpeed() {
		return this.movement.moveDisabled ? 0 : this.finalSpeed;
	}

	/** What buffs and items add to the move speed, or take from it. */
	public int getSpeedBonus() {
		return this.stats.speedBonus;
	}

	public boolean update(final CSimulation game) {
		return CUnitUpdate.update(this, game);
	}

	public boolean autoAcquireTargets(final CSimulation game, final boolean disableMove) {
		return CUnitAcquisition.autoAcquireTargets(this, game, disableMove);
	}

	public boolean autoAcquireIdleTargets(final CSimulation game, final boolean disableMove) {
		return CUnitAcquisition.autoAcquireIdleTargets(this, game, disableMove);
	}

	void onAttackOrdered(final CSimulation game, final CUnit attacker) {
		CUnitAcquisition.onAttackOrdered(this, game, attacker);
	}

	void tryCreepCast(final CSimulation game) {
		if (!game.isNeutralAggressive(this.playerIndex)) {
			game.getHarmfulAreas().stepOut(game, this);
		}
		CUnitAcquisition.tryCreepCast(this, game);
	}

	/** Measured: a unit under Bladestorm still chases its target but never swings; only the whirl hurts. */
	public boolean isWhirlingAttackSuppressed() {
		return getFirstAbilityOfType(CBuffWhirlWindCaster.class) != null;
	}

	/** A computer hero that fought through Bladestorm casts his fight spell the moment it ends. */
	public void markBladestormEnded() {
		this.acquisition.creepCastAfterWhirl = true;
	}

	/** Whether the spell with this order was already cast by the creep rules and is cast only once. */
	public boolean hasCastOnce(final int orderId) {
		return (this.acquisition.creepCastOnce != null) && this.acquisition.creepCastOnce.contains(orderId);
	}

	/** The unit behind the hardest hit this one took in the last given number of ticks. */
	public CUnit getHeaviestHitBy(final int tick, final int withinTicks) {
		return ((tick - this.aggro.heaviestHitTick) <= withinTicks) ? this.aggro.heaviestHitBy : null;
	}

	public void markCastOnce(final int orderId) {
		if (this.acquisition.creepCastOnce == null) {
			this.acquisition.creepCastOnce = new LinkedHashSet<>();
		}
		this.acquisition.creepCastOnce.add(orderId);
	}

	public boolean autoAcquireAutocastTargets(final CSimulation game, final boolean disableMove) {
		return CUnitAcquisition.autoAcquireAutocastTargets(this, game, disableMove);
	}

	public boolean autoAcquireAttackTargets(final CSimulation game, final boolean disableMove) {
		return CUnitAcquisition.autoAcquireAttackTargets(this, game, disableMove);
	}

	public boolean acquirePriorityAttackTarget(final CSimulation game, final boolean disableMove) {
		return CUnitAcquisition.acquirePriorityAttackTarget(this, game, disableMove);
	}

	/**
	 * Kinds of enemies (air, ground, structure) this unit acquires first when it looks for something to attack;
	 * null for no preference (Prioritize).
	 */
	public void setPriorityAttackTargets(final EnumSet<CTargetType> priorityAttackTargets) {
		this.priorityAttackTargets = priorityAttackTargets;
	}

	public EnumSet<CTargetType> getPriorityAttackTargets() {
		return this.priorityAttackTargets;
	}

	public static boolean isPriorityAttackTarget(final CUnit unit, final EnumSet<CTargetType> priorityTargets) {
		return CUnitAcquisition.isPriorityAttackTarget(unit, priorityTargets);
	}

	public float getEndingDecayTime(final CSimulation game) {
		if (isBuilding()) {
			return game.getGameplayConstants().getStructureDecayTime();
		}
		if (this.unitType.isHero()) {
			return game.getGameplayConstants().getDissipateTime();
		}
		return game.getGameplayConstants().getBoneDecayTime();
	}

	public void order(final CSimulation game, final COrder order, final boolean queue) {
		CUnitOrders.order(this, game, order, queue);
	}

	public void order(final CSimulation game, final COrder order, final boolean queue, final boolean autoOrder) {
		CUnitOrders.order(this, game, order, queue, autoOrder);
	}

	/**
	 * BlzUnitInterruptAttack, measured: an attack in progress stops and the order is gone, as after a stop; the unit
	 * then picks a target again by itself and the attack cooldown keeps running.
	 */
	public void interruptAttack(final CSimulation game) {
		if (isDead() || (this.orders.currentBehavior == null)
				|| (this.orders.currentBehavior.getBehaviorCategory() != CBehaviorCategory.ATTACK)) {
			return;
		}
		order(game, new COrderNoTarget(this.playerIndex, 0, OrderIds.stop, false), false);
		this.orders.lastStartedOrder = null;
	}

	public boolean order(final CSimulation simulation, final int orderId, final AbilityTarget target) {
		return CUnitOrders.order(this, simulation, orderId, target);
	}

	public boolean order(final CSimulation simulation, final int orderId, final AbilityTarget target,
			final boolean autoOrder) {
		return CUnitOrders.order(this, simulation, orderId, target, autoOrder);
	}

	public CBehavior getCurrentBehavior() {
		return this.orders.currentBehavior;
	}

	public List<CAbility> getAbilities() {
		return this.abilities;
	}

	public <T> T getAbility(final CAbilityVisitor<T> visitor) {
		for (int i = 0, n = this.abilities.size(); i < n; i++) {
			final CAbility ability = this.abilities.get(i);
			final T visited = ability.visit(visitor);
			if (visited != null) {
				return visited;
			}
		}
		return null;
	}

	public <T extends CAbility> T getFirstAbilityOfType(final Class<T> cAbilityClass) {
		for (int i = 0, n = this.abilities.size(); i < n; i++) {
			final CAbility ability = this.abilities.get(i);
			if (cAbilityClass.isAssignableFrom(ability.getClass())) {
				return (T) ability;
			}
		}
		return null;
	}

	public void setCooldownEndTime(final int cooldownEndTime) {
		this.cooldownEndTime = cooldownEndTime;
	}

	public int getCooldownEndTime() {
		return this.cooldownEndTime;
	}

	@Override
	public float getFlyHeight() {
		return this.placement.flyHeight;
	}

	public void setFlyHeight(final float flyHeight) {
		this.placement.flyHeight = flyHeight;
	}

	public float getTurnRate() {
		return this.placement.turnRate;
	}

	public void setTurnRate(final float turnRate) {
		this.placement.turnRate = turnRate;
	}

	public float getPropWindow() {
		return this.placement.propWindow;
	}

	public void setPropWindow(final float propWindow) {
		this.placement.propWindow = propWindow;
	}

	public void markWalked(final int gameTurnTick) {
		this.lastWalkTick = gameTurnTick;
	}

	/**
	 * Whether the unit stepped forward along a move in the last ticks. Pathing goes through walking units (they will be
	 * gone by the time it gets there); a unit that waited a few ticks counts as standing.
	 */
	public boolean isWalking(final int gameTurnTick) {
		return (gameTurnTick - this.lastWalkTick) <= 2;
	}

	public int getPlayerIndex() {
		return this.playerIndex;
	}

	public void setPlayerIndex(final CSimulation simulation, final int playerIndex, final boolean changeColor) {
		this.playerIndex = playerIndex;
		if (changeColor) {
			simulation.changeUnitColor(this, playerIndex);
		}
		this.computeUnitState(simulation, StateModBuffType.FALSE_OWNERSHIP);
	}

	public void changeOwner(final CSimulation simulation, final int newPlayerIndex, final boolean changeColor) {
		CUnitOwnership.changeOwner(this, simulation, newPlayerIndex, changeColor);
	}

	public void setRescuable(final int byPlayerIndex, final boolean rescuable) {
		final int bit = 1 << byPlayerIndex;
		if (rescuable) {
			this.rescue.rescuableByPlayers |= bit;
			this.rescue.unrescuableByPlayers &= ~bit;
		}
		else {
			this.rescue.unrescuableByPlayers |= bit;
			this.rescue.rescuableByPlayers &= ~bit;
		}
	}

	public void setRescueRange(final float rescueRange) {
		this.rescue.rescueRange = rescueRange;
	}

	public boolean isRescuableBy(final CSimulation simulation, final int byPlayerIndex) {
		return CUnitOwnership.isRescuableBy(this, simulation, byPlayerIndex);
	}

	void checkRescue(final CSimulation simulation) {
		CUnitOwnership.checkRescue(this, simulation);
	}

	public int getFakePlayerIndex() {
		return this.fakePlayerIndex;
	}

	public void setStartCreation(final int index, final int[] typeLastIndex) {
		this.startCreationIndex = index;
		this.startTypeLastIndex = typeLastIndex;
	}

	public boolean isMadeBeforeStart() {
		return this.startTypeLastIndex != null;
	}

	public int getStartCreationIndex() {
		return this.startCreationIndex;
	}

	public int getStartTypeLastIndex() {
		return this.startTypeLastIndex[0];
	}

	public CUnitType getUnitType() {
		return this.unitType;
	}

	public void setCollisionRectangle(final Rectangle collisionRectangle) {
		this.placement.collisionRectangle = collisionRectangle;
	}

	public Rectangle getCollisionRectangle() {
		return this.placement.collisionRectangle;
	}

	public void setX(final float newX, final CWorldCollision collision, final CRegionManager regionManager) {
		final float prevX = getX();
		setX(newX);
		collision.translate(this, newX - prevX, 0);
		CUnitPlacement.checkRegionEvents(this, regionManager);
	}

	public void setY(final float newY, final CWorldCollision collision, final CRegionManager regionManager) {
		final float prevY = getY();
		setY(newY);
		collision.translate(this, 0, newY - prevY);
		CUnitPlacement.checkRegionEvents(this, regionManager);
	}

	public void setPointAndCheckUnstuck(final float newX, final float newY, final CSimulation game) {
		CUnitPlacement.setPointAndCheckUnstuck(this, newX, newY, game);
	}

	public void placeOnGround(final CSimulation game, final float fromX, final float fromY) {
		CUnitPlacement.placeOnGround(this, game, fromX, fromY);
	}

	public float[] findFreePoint(final float x, final float y, final CSimulation game) {
		return CUnitPlacement.findFreePoint(this, x, y, game);
	}

	public void setPoint(final float newX, final float newY, final CWorldCollision collision,
			final CRegionManager regionManager) {
		CUnitPlacement.setPoint(this, newX, newY, collision, regionManager);
	}

	/**
	 * Measured on 1.29: the enter and leave events of a unit placed by a script (SetUnitX, SetUnitY, SetUnitPosition)
	 * fire on a later game tick, each of them, so leaving and re-entering a region in one tick fires both.
	 */
	public void setRegionEventsDeferred(final boolean deferred) {
		this.regionEventsDeferred = deferred;
	}

	void dispatchRegionEvent(final CRegionManager regionManager, final CRegion region, final boolean enter) {
		CUnitPlacement.dispatchRegionEvent(this, regionManager, region, enter);
	}

	static final class PendingRegionEvent {
		final CRegion region;
		final boolean enter;

		PendingRegionEvent(final CRegion region, final boolean enter) {
			this.region = region;
			this.enter = enter;
		}
	}

	public EnumSet<CUnitClassification> getClassifications() {
		return this.classifications;
	}

	public boolean addClassification(final CUnitClassification unitClassification) {
		return this.classifications.add(unitClassification);
	}

	public boolean removeClassification(final CUnitClassification unitClassification) {
		return this.classifications.remove(unitClassification);
	}

	public float getDefense() {
		return this.defenseState.currentDefense;
	}

	@Override
	public float getImpactZ() {
		return this.unitType.getImpactZ();
	}

	public double angleTo(final AbilityTarget target) {
		final double dx = target.getX() - getX();
		final double dy = target.getY() - getY();
		return StrictMath.atan2(dy, dx);
	}

	public double distance(final AbilityTarget target) {
		double dx = StrictMath.abs(target.getX() - getX());
		double dy = StrictMath.abs(target.getY() - getY());
		final float thisCollisionSize = this.unitType.getCollisionSize();
		float targetCollisionSize;
		if (target instanceof CUnit) {
			final CUnitType targetUnitType = ((CUnit) target).getUnitType();
			targetCollisionSize = targetUnitType.getCollisionSize();
		} else {
			targetCollisionSize = 0; // TODO destructable collision size here
		}
		if (dx < 0) {
			dx = 0;
		}
		if (dy < 0) {
			dy = 0;
		}

		double groundDistance = StrictMath.sqrt((dx * dx) + (dy * dy)) - thisCollisionSize - targetCollisionSize;
		if (groundDistance < 0) {
			groundDistance = 0;
		}
		return groundDistance;
	}

	@Override
	public double distance(final float x, final float y) {
		return distance((double) x, (double) y);
	}

	public double distance(final double x, final double y) {
		double dx = Math.abs(x - getX());
		double dy = Math.abs(y - getY());
		final float thisCollisionSize = this.unitType.getCollisionSize();
		if (dx < 0) {
			dx = 0;
		}
		if (dy < 0) {
			dy = 0;
		}

		double groundDistance = StrictMath.sqrt((dx * dx) + (dy * dy)) - thisCollisionSize;
		if (groundDistance < 0) {
			groundDistance = 0;
		}
		return groundDistance;
	}

	public boolean checkForMiss(final CSimulation simulation, final CUnit source, final boolean isAttack,
			final boolean isRanged, final CAttackType attackType, final CDamageType damageType, final float damage,
			final float bonusDamage) {
		return CUnitDamage.checkForMiss(this, simulation, source, isAttack, isRanged, attackType, damageType, damage, bonusDamage);
	}

	public boolean checkForAttackProjReaction(final CSimulation simulation, final CUnit source,
			final CAttackProjectile projectile) {
		return CUnitDamage.checkForAttackProjReaction(this, simulation, source, projectile);
	}

	public boolean checkForAbilityProjReaction(final CSimulation simulation, final CUnit source,
			final CProjectile projectile) {
		return CUnitDamage.checkForAbilityProjReaction(this, simulation, source, projectile);
	}

	public boolean checkForAbilityEffectReaction(final CSimulation simulation, final CUnit source,
			final CAbility ability) {
		return CUnitDamage.checkForAbilityEffectReaction(this, simulation, source, ability);
	}

	public CUnitAttackSettings checkForAttackPreLaunchModification(final CSimulation simulation,
			final AbilityTarget target, final CUnitAttack attack, final CUnitAttackSettings settings) {
		return CUnitDamage.checkForAttackPreLaunchModification(this, simulation, target, attack, settings);
	}

	public CUnitAttackSettings checkForAttackModification(final CSimulation simulation, final AbilityTarget target,
			final CUnitAttack attack, final CUnitAttackSettings settings) {
		return CUnitDamage.checkForAttackModification(this, simulation, target, attack, settings);
	}

	public boolean isImmuneToDamage(final CSimulation simulation, final CDamageFlags flags,
			final CAttackType attackType, final CDamageType damageType) {
		return CUnitDamage.isImmuneToDamage(this, simulation, flags, attackType, damageType);
	}

	public void fireAcquiredTargetEvents(final CSimulation simulation, final CUnit target) {
		CUnitEvents.fireAcquiredTargetEvents(this, simulation, target);
	}

	public void fireAttackedEvents(final CSimulation simulation, final CUnit attacker) {
		CUnitEvents.fireAttackedEvents(this, simulation, attacker);
	}

	@Override
	public float damage(final CSimulation simulation, final CUnit source, final CDamageFlags flags,
			final CAttackType attackType, final CDamageType damageType, final String weaponSoundType,
			final float damage) {
		return CUnitDamage.damage(this, simulation, source, flags, attackType, damageType, weaponSoundType, damage);
	}

	@Override
	public float damage(final CSimulation simulation, final CUnit source, final CDamageFlags flags,
			final CAttackType attackType, final CDamageType damageType, final String weaponSoundType,
			final float damage, final float bonusDamage) {
		return CUnitDamage.damage(this, simulation, source, flags, attackType, damageType, weaponSoundType, damage, bonusDamage);
	}

	public void updateNightSleep(final CSimulation game) {
		CUnitUpdate.updateNightSleep(this, game);
	}

	public boolean isNaturallyAsleep() {
		return this.acquisition.naturalSleep != null;
	}

	/**
	 * Stunned or put to sleep by a spell. 1.29: such a unit's current
	 * order reads stunned, a creep in its night sleep reads 0.
	 */
	public boolean isStunnedBySpell() {
		for (final StateModBuff buff : this.stateModBuffs) {
			if ((buff != this.acquisition.naturalSleep) && (buff.getValue() != 0)
					&& ((buff.getBuffType() == StateModBuffType.STUN)
							|| (buff.getBuffType() == StateModBuffType.SLEEPING))) {
				return true;
			}
		}
		return false;
	}

	/** Whether the unit may fall asleep at night: its type's setting until a trigger sets it (UnitAddSleep). */
	public boolean canSleep() {
		return this.canSleepOverride == null ? this.unitType.isCanSleep() : this.canSleepOverride;
	}

	public void setCanSleep(final boolean canSleep) {
		this.canSleepOverride = canSleep;
	}

	public void wakeFromNaturalSleep(final CSimulation game) {
		CUnitUpdate.wakeFromNaturalSleep(this, game);
	}

	/** Called for help or UnitWakeUp: wakes the creep and keeps it awake for a while even if it already was. */
	public void wakeUp(final CSimulation game) {
		this.acquisition.lastWakeTick = game.getGameTurnTick();
		wakeFromNaturalSleep(game);
	}

	public void onCreatedNightSleep(final CSimulation game) {
		CUnitUpdate.onCreatedNightSleep(this, game);
	}

	/** A guarding unit (a creep at its camp) goes back to its spot like the original game's creeps do. */
	public void setGuardPosition(final float x, final float y) {
		this.aggro.guarding = true;
		this.aggro.guardX = x;
		this.aggro.guardY = y;
	}

	public void removeGuardPosition() {
		this.aggro.guarding = false;
	}

	public boolean isGuarding() {
		return this.aggro.guarding;
	}

	static int secondsToTicks(final float seconds) {
		return CTimer.wholeSteps(seconds);
	}

	boolean isIdleForAttack() {
		return CUnitAggro.isIdleForAttack(this);
	}

	void queueAttackBack(final CSimulation simulation, final CUnit source, final boolean wasHit,
			final boolean help) {
		CUnitAggro.queueAttackBack(this, simulation, source, wasHit, help);
	}

	boolean isFleeing() {
		return CUnitAggro.isFleeing(this);
	}

	boolean isCritter() {
		return CUnitAggro.isCritter(this);
	}

	/**
	 * Measured on 1.29: a unit stays a wanderer once it was given Wander, also after the ability is removed.
	 */
	public void markWanderer() {
		this.aggro.wanderer = true;
	}

	public boolean isWanderer() {
		return this.aggro.wanderer;
	}

	public void onTargetedBySpell(final CSimulation simulation, final CUnit caster) {
		CUnitAggro.onTargetedBySpell(this, simulation, caster);
	}

	public void onSpellMissileLaunched(final CUnit caster) {
		CUnitAggro.onSpellMissileLaunched(this, caster);
	}

	void onSpellEffect(final CSimulation simulation, final CUnit caster) {
		CUnitAggro.onSpellEffect(this, simulation, caster);
	}

	void alertCreeps(final CSimulation simulation, final CUnit target) {
		CUnitAggro.alertCreeps(this, simulation, target);
	}

	boolean answerAlert(final CSimulation simulation, final CUnit target) {
		return CUnitAggro.answerAlert(this, simulation, target);
	}

	public void joinCreepCamp(final CSimulation simulation) {
		CUnitAggro.joinCreepCamp(this, simulation);
	}

	static final class CreepCamp {
		final List<CUnit> members = new ArrayList<>();

		CUnit founder() {
			while (!this.members.isEmpty()) {
				final CUnit first = this.members.get(0);
				if (!first.isDead() && !first.isRemovedFromGame() && (first.aggro.creepCamp == this)) {
					return first;
				}
				this.members.remove(0);
			}
			return null;
		}
	}

	List<CUnit> helpersInRange(final CSimulation simulation, final float radius) {
		return CUnitAggro.helpersInRange(this, simulation, radius);
	}

	public void killPathingInstance() {
		if (this.placement.pathingInstance != null) {
			this.placement.pathingInstance.remove();
			this.placement.pathingInstance = null;
		}
	}

	public void kill(final CSimulation simulation) {
		CUnitDeath.kill(this, simulation);
	}

	public boolean canReach(final AbilityTarget target, final float range) {
		return CUnitPlacement.canReach(this, target, range);
	}

	public boolean canReach(final float x, final float y, final float range) {
		return CUnitPlacement.canReach(this, x, y, range);
	}

	public boolean isBesideBuilding(final CUnit building) {
		return CUnitPlacement.isBesideBuilding(this, building);
	}

	public boolean canReachToPathing(final float range, final float rotationForPathing,
			final ArgbImage buildingPathingPixelMap, final float targetX, final float targetY) {
		return CUnitPlacement.canReachToPathing(this, range, rotationForPathing, buildingPathingPixelMap, targetX, targetY);
	}

	public void addStateListener(final CUnitStateListener listener) {
		this.stateListenersUpdates.add(new StateListenerUpdate(listener, StateListenerUpdateType.ADD));
	}

	public void removeStateListener(final CUnitStateListener listener) {
		this.stateListenersUpdates.add(new StateListenerUpdate(listener, StateListenerUpdateType.REMOVE));
	}

	public boolean isCorpse() {
		return this.death.corpse;
	}

	public boolean isIllusion() {
		return this.illusionState.illusion;
	}

	public float getIllusionDamageDealtFactor() {
		return this.illusionState.illusionDamageDealtFactor;
	}

	/**
	 * The level scripts read for an ability the illusion lost, 0 if it never had it. Measured on 1.29: such abilities
	 * do nothing and refuse their orders but still read as present.
	 */
	public int getIllusionAbilityLevel(final int rawcode) {
		final Integer level = this.illusionState.illusionAbilityLevels.get(rawcode);
		return level == null ? 0 : level;
	}

	public void makeIllusion(final CSimulation simulation, final float damageDealtFactor,
			final float damageTakenFactor, final War3ID buffId) {
		CUnitTypes.makeIllusion(this, simulation, damageDealtFactor, damageTakenFactor, buffId);
	}

	public boolean isBoneCorpse() {
		return this.death.boneCorpse;
	}

	public boolean isUsableCorpse() {
		return this.death.boneCorpse || this.death.freshCorpse;
	}

	@Override
	public boolean canBeTargetedBy(final CSimulation simulation, final CUnit source,
			final Set<CTargetType> targetsAllowed, final AbilityTargetCheckReceiver<CWidget> receiver) {
		return CUnitVisibility.canBeTargetedBy(this, simulation, source, targetsAllowed, receiver);
	}

	public boolean canBeTargetedBy(final CSimulation simulation, final CUnit source, final boolean targeted,
			final Set<CTargetType> targetsAllowed, final AbilityTargetCheckReceiver<CWidget> receiver) {
		return CUnitVisibility.canBeTargetedBy(this, simulation, source, targeted, targetsAllowed, receiver);
	}

	public boolean canBeTargetedBy(final CSimulation simulation, final CUnit source, final boolean targeted,
			final Set<CTargetType> targetsAllowed, final AbilityTargetCheckReceiver<CWidget> receiver,
			final boolean throughFog) {
		return CUnitVisibility.canBeTargetedBy(this, simulation, source, targeted, targetsAllowed, receiver, throughFog);
	}

	public boolean canBeAreaTargetedBy(final CSimulation simulation, final CUnit source,
			final CAreaTargetVisibility visibility, final Set<CTargetType> targetsAllowed,
			final AbilityTargetCheckReceiver<CWidget> receiver) {
		return CUnitVisibility.canBeAreaTargetedBy(this, simulation, source, visibility, targetsAllowed, receiver);
	}

	public boolean canBeAreaTargetedBy(final CSimulation simulation, final CUnit source,
			final CAreaTargetVisibility visibility, final Set<CTargetType> targetsAllowed) {
		return canBeAreaTargetedBy(simulation, source, visibility, targetsAllowed,
				BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset());
	}

	public boolean isMovementDisabled() {
		return (this.moveBehavior == null) || this.movement.moveDisabled;
		// TODO this used to directly return the state of whether our unit was a
		// building. Will it be a problem that I changed it?
		// I was trying to fix attack move on stationary units which was crashing
	}

	public boolean isMovementOnWaterAllowed() {
		return !isMovementDisabled() && getMovementType().isPathable((short) ~PathingFlags.UNSWIMABLE);
	}

	public MovementType getMovementType() {
		if (isMovementDisabled()) {
			return MovementType.DISABLED;
		}
		return this.movement.movementOverride != null ? this.movement.movementOverride : getUnitType().getMovementType();
		// later maybe it has unit instance override for windwalk, so this wrapper
		// exists to later mod
	}

	public void setNoCollisionMovementType(final boolean active) {
		if (active) {
			this.movement.nextMovementOverride = MovementType.FOOT_NO_COLLISION;
		}
		else {
			this.movement.nextMovementOverride = null;
		}
	}

	public float getAcquisitionRange() {
		return this.acquisitionRange;
	}

	public void setAcquisitionRange(final float acquisitionRange) {
		this.acquisitionRange = acquisitionRange;
	}

	public void setCurrentHp(final CSimulation game, final float hpValue) {
		setLife(game, Math.min(hpValue, getMaximumLife()));
	}

	public void heal(final CSimulation game, final float lifeToRegain) {
		setLife(game, Math.min(getLife() + (lifeToRegain * this.healingReceivedFactor), getMaximumLife()));
	}

	public void multiplyHealingReceived(final float factor) {
		this.healingReceivedFactor *= factor;
	}

	public void restoreMana(final CSimulation game, final float manaToRegain) {
		setMana(Math.min(getMana() + manaToRegain, getMaximumMana()));
	}

	public void resurrect(final CSimulation simulation) {
		CUnitDeath.resurrect(this, simulation);
	}

	/**
	 * Raises a corpse as Animate Dead does. Measured on 1.29: the raised unit uses no food and is left out of
	 * GetPlayerTechCount, GetPlayerUnitCount and GetPlayerTypedUnitCount.
	 */
	public void resurrectAsAnimatedCorpse(final CSimulation simulation) {
		CUnitDeath.resurrect(this, simulation, true);
	}

	public void becomeCorpse(final CSimulation game) {
		CUnitDeath.becomeCorpse(this, game);
	}

	public void holdCorpse(final CSimulation game) {
		CUnitDeath.holdCorpse(this, game);
	}

	public void resetCorpseDecay(final CSimulation game) {
		CUnitDeath.resetCorpseDecay(this, game);
	}

	public void releaseCorpse(final CSimulation game, final float x, final float y) {
		CUnitDeath.releaseCorpse(this, game, x, y);
	}

	public static void releaseStaticReferences() {
		CUnitAcquisition.autoAttackTargetFinderEnum = new AutoAttackTargetFinderEnum();
		CUnitAcquisition.autocastTargetFinderEnum = new AutocastTargetFinderEnum();
		CUnitPlacement.regionCheckerImpl = new RegionCheckerImpl();
		UseAbilityOnTargetByIdVisitor.INSTANCE.reset(null, null, 0, 0);
	}

	boolean canAttackFromHere(final CSimulation game, final CUnit target) {
		return CUnitAcquisition.canAttackFromHere(this, game, target);
	}

	public CBehaviorMove getMoveBehavior() {
		return this.moveBehavior;
	}

	public void setMoveBehavior(final CBehaviorMove moveBehavior) {
		this.moveBehavior = moveBehavior;
	}

	public CBehaviorAttack getAttackBehavior() {
		return this.attackBehavior;
	}

	public void setAttackBehavior(final CBehaviorAttack attackBehavior) {
		this.attackBehavior = attackBehavior;
	}

	public void setAttackMoveBehavior(final CBehaviorAttackMove attackMoveBehavior) {
		this.attackMoveBehavior = attackMoveBehavior;
	}

	public CBehaviorAttackMove getAttackMoveBehavior() {
		return this.attackMoveBehavior;
	}

	public CBehaviorStop getStopBehavior() {
		return this.stopBehavior;
	}

	public void setFollowBehavior(final CBehaviorFollow followBehavior) {
		this.followBehavior = followBehavior;
	}

	public void setPatrolBehavior(final CBehaviorPatrol patrolBehavior) {
		this.patrolBehavior = patrolBehavior;
	}

	public void setHoldPositionBehavior(final CBehaviorHoldPosition holdPositionBehavior) {
		this.holdPositionBehavior = holdPositionBehavior;
	}

	public void setBoardTransportBehavior(final CBehaviorBoardTransport boardTransportBehavior) {
		this.boardTransportBehavior = boardTransportBehavior;
	}

	public CBehaviorFollow getFollowBehavior() {
		return this.followBehavior;
	}

	public CBehaviorPatrol getPatrolBehavior() {
		return this.patrolBehavior;
	}

	public CBehaviorHoldPosition getHoldPositionBehavior() {
		return this.holdPositionBehavior;
	}

	public CBehaviorBoardTransport getBoardTransportBehavior() {
		return this.boardTransportBehavior;
	}

	public CBehavior pollNextOrderBehavior(final CSimulation game) {
		return CUnitOrders.pollNextOrderBehavior(this, game);
	}

	public boolean isMoving() {
		return getCurrentBehavior() instanceof CBehaviorMove;
	}

	public void setConstructing(final boolean constructing) {
		this.production.constructing = constructing;
		if (constructing) {
			this.unitAnimationListener.playAnimation(true, PrimaryTag.BIRTH, SequenceUtils.EMPTY, 0.0f, true);
		}
	}

	public void setConstructingPaused(final boolean constructingPaused) {
		this.production.constructingPaused = constructingPaused;
	}

	public void setConstructionProgress(final float constructionProgress) {
		this.production.constructionProgress = constructionProgress;
	}

	public boolean isConstructing() {
		return this.production.constructing && (this.production.upgradeIdType == null);
	}

	public boolean isConstructingPaused() {
		return this.production.constructingPaused;
	}

	public boolean isUpgrading() {
		return this.production.constructing && (this.production.upgradeIdType != null);
	}

	public War3ID getUpgradeIdType() {
		return this.production.upgradeIdType;
	}

	public boolean isConstructingOrUpgrading() {
		return this.production.constructing;
	}

	public float getConstructionProgress() {
		return this.production.constructionProgress;
	}

	public void setHidden(final boolean hidden) {
		this.hidden = hidden;
		this.stateNotifier.hideStateChanged();
	}

	public void setPaused(final boolean paused) {
		if (paused) {
			this.addStateModBuff(pauseBuff);
		} else {
			this.removeStateModBuff(pauseBuff);
		}
		this.computeUnitState(null, StateModBuffType.PAUSED);
	}

	public boolean isPaused() {
		return this.paused;
	}

	public void setAcceptingOrders(final boolean acceptingOrders) {
		this.orders.acceptingOrders = acceptingOrders;
	}

	public boolean isHidden() {
		return this.hidden;
	}

	/**
	 * SetUnitInvulnerable. Measured on 1.29: it adds or removes the Invulnerable ability ('Avul'), so it is one flag
	 * however often it is set, UnitRemoveAbility 'Avul' clears it, and it neither ends nor is ended by spell sources
	 * (Divine Shield, Big Bad Voodoo, a gold mine visit), which are INVULNERABLE state mod buffs of their own.
	 */
	public void setInvulnerable(final CSimulation simulation, final boolean invulnerable) {
		// Saves from before 'Avul' kept the flag in scriptInvulnerable.
		this.invulnerability.scriptInvulnerable = false;
		final CLevelingAbility existing = getAbility(
				GetAbilityByRawcodeVisitor.getInstance().reset(INVULNERABLE_ABILITY_ID));
		if (invulnerable && (existing == null)) {
			final CAbilityType<?> type = simulation.getAbilityData().getAbilityType(INVULNERABLE_ABILITY_ID);
			if (type != null) {
				add(simulation, type.createAbility(simulation.getHandleIdAllocator().createId()));
			}
			else {
				this.invulnerability.scriptInvulnerable = true;
			}
		}
		else if (!invulnerable && (existing != null)) {
			remove(simulation, existing);
		}
		this.invulnerability.invulnerable = this.invulnerability.scriptInvulnerable || this.invulnerability.buffInvulnerable;
	}

	public void setInvulnerableSource(final CSimulation simulation, final StateModBuff source, final boolean active) {
		if (active) {
			addStateModBuff(source);
		}
		else {
			removeStateModBuff(source);
		}
		computeUnitState(simulation, StateModBuffType.INVULNERABLE);
	}

	/**
	 * A worker leaving a gold mine or an Orc building it was inside. Saves from before the per-source state kept that
	 * in the script flag, which leaving cleared.
	 */
	public void endInsideInvulnerable(final CSimulation simulation) {
		this.invulnerability.scriptInvulnerable = false;
		setInvulnerableSource(simulation, INSIDE_INVULNERABLE, false);
	}

	@Override
	public boolean isInvulnerable() {
		return this.invulnerability.invulnerable;
	}

	public boolean isAutoTargetable() {
		return this.auto.autoTargetable;
	}

	public void setWorker(final CUnit unit, final boolean inside) {
		this.production.worker = unit;
		this.production.workerInside = inside;
	}

	public CUnit getWorker() {
		return this.production.worker;
	}

	public void addPowerWorker() {
		this.powerWorkers++;
	}

	public void removePowerWorker() {
		this.powerWorkers--;
	}

	public int getPowerWorkers() {
		return this.powerWorkers;
	}

	public void nudgeAround(final CSimulation simulation, final CUnit structure) {
		CUnitPlacement.nudgeAround(this, simulation, structure);
	}

	public void placeTrainedUnit(final CSimulation simulation, final CUnit trainer, final AbilityTarget rallyPoint) {
		CUnitProduction.placeTrainedUnit(this, simulation, trainer, rallyPoint);
	}

	@Override
	public void setLife(final CSimulation simulation, final float life) {
		applyLife(simulation, life);
		CUnitStats.checkStateEvents(this, false);
	}

	void applyLife(final CSimulation simulation, final float life) {
		final boolean wasDead = isDead();
		super.setLife(simulation, life);
		if (isDead() && !wasDead) {
			CUnitDeath.kill(this, simulation, null);
		}
		this.stateNotifier.lifeChanged();
	}

	public War3ID[] getBuildQueue() {
		return this.production.buildQueue;
	}

	public QueueItemType[] getBuildQueueTypes() {
		return this.production.buildQueueTypes;
	}

	public boolean isBuildQueueActive() {
		return this.production.buildQueueTypes[0] != null;
	}

	/**
	 * The unit type queued for training at the given queue slot, else null. Measured on 1.29: every unit in a
	 * training queue, not only the one in progress, counts as an incomplete unit of its player.
	 */
	public War3ID getQueuedTrainingUnitTypeId(final int index) {
		if ((this.production.buildQueueTypes[index] == QueueItemType.UNIT) && !isDead()) {
			return this.production.buildQueue[index];
		}
		return null;
	}

	public float getBuildQueueTimeRemaining(final CSimulation simulation) {
		return CUnitProduction.getBuildQueueTimeRemaining(this, simulation);
	}

	public void cancelBuildQueueItem(final CSimulation game, final int cancelIndex) {
		CUnitProduction.cancelBuildQueueItem(this, game, cancelIndex);
	}

	public void cancelLastBuildQueueItem(final CSimulation game) {
		CUnitProduction.cancelLastBuildQueueItem(this, game);
	}

	public void clearBuildQueueOnDeath(final CSimulation game) {
		CUnitProduction.clearBuildQueueOnDeath(this, game);
	}

	public void setBuildQueueItem(final CSimulation game, final int index, final War3ID rawcode,
			final QueueItemType queueItemType) {
		CUnitProduction.setBuildQueueItem(this, game, index, rawcode, queueItemType);
	}

	public void queueTrainingUnit(final CSimulation game, final War3ID rawcode) {
		CUnitProduction.queueTrainingUnit(this, game, rawcode);
	}

	public void queueSacrificingUnit(final CSimulation game, final War3ID rawcode, final CUnit sacrifice) {
		CUnitProduction.queueSacrificingUnit(this, game, rawcode, sacrifice);
	}

	public void queueRevivingHero(final CSimulation game, final CUnit hero) {
		CUnitProduction.queueRevivingHero(this, game, hero);
	}

	public void reviveHeroAt(final CSimulation game, final CUnit reviver, final float lifeFactor,
			final float manaFactor, final float manaStart) {
		CUnitProduction.reviveHeroAt(this, game, reviver, lifeFactor, manaFactor, manaStart);
	}

	public void reviveHeroAtPoint(final CSimulation game, final float x, final float y, final boolean doEyecandy) {
		CUnitProduction.reviveHeroAtPoint(this, game, x, y, doEyecandy);
	}

	public boolean isPathingDisabled() {
		return this.movement.pathingDisabled;
	}

	public void setPathingDisabled(final boolean pathingDisabled) {
		this.movement.pathingDisabled = pathingDisabled;
		setNoCollisionMovementType(pathingDisabled);
	}

	public void resetCooldowns() {
		this.rawcodeToCooldownExpireTime.clear();
		this.rawcodeToCooldownStartTime.clear();
		this.cooldownEndTime = 0;
		fireCooldownsChangedEvent();
	}

	public void queueResearch(final CSimulation game, final War3ID rawcode) {
		CUnitProduction.queueResearch(this, game, rawcode);
	}

	public static enum QueueItemType {
		UNIT,
		RESEARCH,
		HERO_REVIVE,
		SACRIFICE;
	}

	public void setRallyPoint(final AbilityTarget target) {
		this.production.rallyPoint = target;
		this.stateNotifier.rallyPointChanged();
	}

	public void internalPublishHeroStatsChanged() {
		this.stateNotifier.heroStatsChanged();
	}

	public AbilityTarget getRallyPoint() {
		return this.production.rallyPoint;
	}

	@Override
	public <T> T visit(final AbilityTargetVisitor<T> visitor) {
		return visitor.accept(this);
	}

	@Override
	public <T> T visit(final CWidgetVisitor<T> visitor) {
		return visitor.accept(this);
	}

	public int getFoodMade() {
		return this.food.foodMade;
	}

	public int getFoodUsed() {
		return this.food.foodUsed;
	}

	public int setFoodMade(final int foodMade) {
		final int delta = foodMade - this.food.foodMade;
		this.food.foodMade = foodMade;
		return delta;
	}

	public int setFoodUsed(final int foodUsed) {
		final int delta = foodUsed - this.food.foodUsed;
		this.food.foodUsed = foodUsed;
		return delta;
	}

	public void setDefaultBehavior(final CBehavior defaultBehavior) {
		this.orders.defaultBehavior = defaultBehavior;
	}

	public CAbilityGoldMinable getGoldMineData() {
		for (int i = 0, n = this.abilities.size(); i < n; i++) {
			final CAbility ability = this.abilities.get(i);
			if (ability instanceof CAbilityGoldMinable) {
				return (CAbilityGoldMinable) ability;
			}
		}
		return null;
	}

	public CAbilityOverlayedMine getOverlayedGoldMineData() {
		for (int i = 0, n = this.abilities.size(); i < n; i++) {
			final CAbility ability = this.abilities.get(i);
			if (ability instanceof CAbilityOverlayedMine) {
				return (CAbilityOverlayedMine) ability;
			}
		}
		return null;
	}

	public int getGold() {
		for (int i = 0, n = this.abilities.size(); i < n; i++) {
			final CAbility ability = this.abilities.get(i);
			if (ability instanceof CAbilityGoldMinable) {
				return ((CAbilityGoldMinable) ability).getGold();
			}
			if (ability instanceof CAbilityOverlayedMine) {
				return ((CAbilityOverlayedMine) ability).getGold();
			}
		}
		return 0;
	}

	public void setGold(final int goldAmount) {
		for (final CAbility ability : this.abilities) {
			if (ability instanceof CAbilityGoldMinable) {
				((CAbilityGoldMinable) ability).setGold(goldAmount);
			}
			if (ability instanceof CAbilityOverlayedMine) {
				((CAbilityOverlayedMine) ability).setGold(goldAmount);
			}
		}
	}

	public Queue<COrder> getOrderQueue() {
		return this.orders.orderQueue;
	}

	/** 1.29: a unit that gave up a chase reads move on its way back; one that lost its target reads 0. */
	public boolean isWalkingToPost() {
		return (this.aggro.guardReturn == GUARD_RETURN_GIVE_UP) && (this.orders.currentBehavior == this.moveBehavior)
				&& (this.moveBehavior != null) && (this.moveBehavior.getRangedBehavior() == null);
	}

	public COrder getCurrentOrder() {
		return this.orders.lastStartedOrder;
	}

	/**
	 * Measured on 1.29: an animation a script sets on a unit walking under an order is not
	 * replaced by the walk; it repeats while the unit keeps walking, until the unit gets another order or the order
	 * ends. An idle unit plays it once.
	 */
	public void holdScriptAnimation() {
		this.scriptAnimationOrder = this.orders.lastStartedOrder;
	}

	public void releaseScriptAnimation() {
		this.scriptAnimationOrder = null;
	}

	public boolean isScriptAnimationHeld() {
		if ((this.scriptAnimationOrder != null) && (this.scriptAnimationOrder != this.orders.lastStartedOrder)) {
			this.scriptAnimationOrder = null;
		}
		return this.scriptAnimationOrder != null;
	}

	public CAbilityHero getHeroData() {
		for (int i = 0, n = this.abilities.size(); i < n; i++) {
			final CAbility ability = this.abilities.get(i);
			if (ability instanceof CAbilityHero) {
				return (CAbilityHero) ability;
			}
		}
		return null;
	}

	public CAbilityInventory getInventoryData() {
		for (int i = 0, n = this.abilities.size(); i < n; i++) {
			final CAbility ability = this.abilities.get(i);
			if (ability instanceof CAbilityInventory) {
				return (CAbilityInventory) ability;
			}
		}
		return null;
	}

	public CAbilityNeutralBuilding getNeutralBuildingData() {
		for (int i = 0, n = this.abilities.size(); i < n; i++) {
			final CAbility ability = this.abilities.get(i);
			if (ability instanceof CAbilityNeutralBuilding) {
				return (CAbilityNeutralBuilding) ability;
			}
		}
		return null;
	}

	public CAbilityCargoHold getCargoData() {
		for (int i = 0, n = this.abilities.size(); i < n; i++) {
			final CAbility ability = this.abilities.get(i);
			if (ability instanceof CAbilityCargoHold) {
				return (CAbilityCargoHold) ability;
			}
		}
		return null;
	}

	public void setUnitSpecificAttacks(final List<CUnitAttack> unitSpecificAttacks) {
		this.unitSpecificAttacks = unitSpecificAttacks;
	}

	public void setUnitSpecificCurrentAttacks(final List<CUnitAttack> unitSpecificCurrentAttacks) {
		this.unitSpecificCurrentAttacks = unitSpecificCurrentAttacks;
		computeDerivedFields(NonStackingStatBuffType.ATKSPD);
	}

	public List<CUnitAttack> getUnitSpecificAttacks() {
		return this.unitSpecificAttacks;
	}

	public List<CUnitAttack> getCurrentAttacks() {
		if (this.disableAttacks) {
			return Collections.emptyList();
		}
		if (this.unitSpecificCurrentAttacks != null) {
			return this.unitSpecificCurrentAttacks;
		}
		return Collections.emptyList();
	}

	public void setDisableAttacks(final boolean disableAttacks) {
		this.disableAttacks = disableAttacks;
		this.stateNotifier.attacksChanged();
	}

	public boolean isDisableAttacks() {
		return this.disableAttacks;
	}

	public void onPickUpItem(final CSimulation game, final CItem item, final boolean playUserUISounds) {
		this.stateNotifier.inventoryChanged();
		if (playUserUISounds) {
			game.unitPickUpItemEvent(this, item);
		}
		firePickUpItemEvents(game, item);
	}

	public void onDropItem(final CSimulation game, final CItem droppedItem, final boolean playUserUISounds) {
		this.stateNotifier.inventoryChanged();
		if (playUserUISounds) {
			game.unitDropItemEvent(this, droppedItem);
		}
	}

	public boolean isInRegion(final CRegion region) {
		return CUnitPlacement.isInRegion(this, region);
	}

	@Override
	public float getMaxLife() {
		return this.stats.maximumLife;
	}

	public boolean isBuilding() {
		return this.structure;
	}

	public void setStructure(final boolean flag) {
		this.structure = flag;
	}

	/**
	 * Measured on 1.29: an uprooted ancient is still a structure for IsUnitType and is counted by
	 * GetPlayerStructureCount with incomplete structures, though it walks and paths as a unit.
	 */
	public boolean isUprooted() {
		return this.uprooted;
	}

	public void setUprooted(final boolean uprooted) {
		this.uprooted = uprooted;
	}

	public void unlockUpgradedFromTypes(final CSimulation simulation, final CPlayer player) {
		CUnitProduction.unlockUpgradedFromTypes(this, simulation, player);
	}

	public void onRemove(final CSimulation simulation) {
		final CPlayer player = simulation.getPlayer(this.playerIndex);
		if (WarsmashConstants.FIRE_DEATH_EVENTS_ON_REMOVEUNIT) {
			// Firing userspace triggers here causes items to appear around the player bases
			// in melee games.
			// (See "Remove creeps and critters from used start locations" implementation)
			setLife(simulation, 0);
		} else {
			if (!isDead()) {
				if (this.production.constructing) {
					player.removeTechtreeInProgress(simulation, this.unitType.getTypeId());
				} else {
					player.removeTechtreeUnlocked(simulation, this.unitType.getTypeId());
					for (final War3ID upgradedFromTypeId : this.production.upgradedFromTypeIds) {
						player.removeTechtreeUnlocked(simulation, upgradedFromTypeId);
					}
				}
			} // else techtree was removed upon death
			setHidden(true);
			// setting hidden to let things that refer to this before it gets garbage
			// collected see it as basically worthless
		}
		killPathingInstance();
		simulation.getWorldCollision().removeUnit(this);
		this.removedFromGame = true;
	}

	/**
	 * After the step that removed it, a unit's handle no longer refers to anything: as measured in the original game,
	 * scripts then read type 0, position 0, no owner and not hidden.
	 */
	public boolean isRemovedFromGame() {
		return this.removedFromGame;
	}

	static enum StateListenerUpdateType {
		ADD,
		REMOVE;
	}

	static final class StateListenerUpdate {
		final CUnitStateListener listener;
		private final StateListenerUpdateType updateType;

		public StateListenerUpdate(final CUnitStateListener listener, final StateListenerUpdateType updateType) {
			this.listener = listener;
			this.updateType = updateType;
		}

		public CUnitStateListener getListener() {
			return this.listener;
		}

		public StateListenerUpdateType getUpdateType() {
			return this.updateType;
		}
	}

	public void cancelConstruction(final CSimulation game) {
		CUnitProduction.cancelConstruction(this, game);
	}

	public void cancelUpgrade(final CSimulation game) {
		CUnitProduction.cancelUpgrade(this, game);
	}

	public void beginUpgrade(final CSimulation game, final War3ID rawcode) {
		CUnitProduction.beginUpgrade(this, game, rawcode);
	}

	public void setUnitState(final CSimulation game, final CUnitState whichUnitState, final float value) {
		CUnitStats.setUnitState(this, game, whichUnitState, value);
	}

	public RemovableTriggerEvent addStateEvent(final GlobalScope globalScope, final Trigger whichTrigger,
			final CUnitState state, final CLimitOp limitOp, final double limitValue) {
		return CUnitStats.addStateEvent(this, globalScope, whichTrigger, state, limitOp, limitValue);
	}

	public float getUnitState(final CSimulation game, final CUnitState whichUnitState) {
		return CUnitStats.getUnitState(this, game, whichUnitState);
	}

	/** A unit state as a script reads it, at the exact time of the timer now firing. */
	/**
	 * Measured on 1.29: a script can write the life of a dead unit and read the value back, but the unit stays dead
	 * and the value takes no part in the game.
	 */
	public void setDeadScriptLife(final float value) {
		this.deadScriptLife = Math.min(value, getMaximumLife());
	}

	public float getScriptLife() {
		return (isDead() && !Float.isNaN(this.deadScriptLife)) ? this.deadScriptLife : getLife();
	}

	public float getScriptUnitState(final CSimulation game, final CUnitState whichUnitState) {
		return CUnitStats.getScriptUnitState(this, game, whichUnitState);
	}

	public boolean isUnitType(final CUnitTypeJass whichUnitType) {
		return CUnitTypes.isUnitType(this, whichUnitType);
	}

	public void addCheesyStateModBuff(final CSimulation game, final StateModBuffType type) {
		CUnitTypes.addCheesyStateModBuff(this, game, type);
	}

	public boolean removeCheesyStateModBuff(final CSimulation game, final StateModBuffType type) {
		return CUnitTypes.removeCheesyStateModBuff(this, game, type);
	}

	public boolean addUnitType(final CSimulation game, final CUnitTypeJass whichUnitType) {
		return CUnitTypes.addUnitType(this, game, whichUnitType);
	}

	public boolean removeUnitType(final CSimulation game, final CUnitTypeJass whichUnitType) {
		return CUnitTypes.removeUnitType(this, game, whichUnitType);
	}

	public Set<CUnitAppliedUpgrade> getAppliedUpgrades() {
		return this.appliedUpgrades;
	}

	public int getLevelOfAppliedUpgrade(War3ID id) {
		CUnitAppliedUpgrade upgrade = CUnitTypes.getAppliedUpgrade(this, id);
		if (upgrade != null) {
			return upgrade.getLevel();
		}
		return 0;
	}

	public void applyUpgrade(CSimulation simulation, CUpgradeType upgradeType, int level) {
		CUnitTypes.applyUpgrade(this, simulation, upgradeType, level);
	}

	public void unapplyUpgrade(CSimulation simulation, CUpgradeType upgradeType) {
		CUnitTypes.unapplyUpgrade(this, simulation, upgradeType);
	}

	public void unapplyUpgrade(CSimulation simulation, CUpgradeType upgradeType, CUnitAppliedUpgrade upgrade) {
		CUnitTypes.unapplyUpgrade(this, simulation, upgradeType, upgrade);
	}

	public int getTriggerEditorCustomValue() {
		return this.triggerEditorCustomValue;
	}

	public void setTriggerEditorCustomValue(final int triggerEditorCustomValue) {
		this.triggerEditorCustomValue = triggerEditorCustomValue;
	}

	/** The unit's name: its type's, unless a trigger renamed this unit (BlzSetUnitName). */
	public String getName() {
		return this.nameOverride == null ? this.unitType.getName() : this.nameOverride;
	}

	public void setName(final String name) {
		this.nameOverride = name;
	}

	public static String maybeMeaningfulName(final CUnit unit) {
		if (unit == null) {
			return "null";
		}
		return unit.getUnitType().getName();
	}

	public void fireCooldownsChangedEvent() {
		this.stateNotifier.ordersChanged();
	}

	/**
	 * The ability a script names by rawcode. Besides the leveling abilities this finds the engine's own ones
	 * (attack, move, build, train queue, upgrade, hero, shops), which the original also lets scripts read, remove and
	 * make permanent.
	 */
	public CAbility getAbilityByRawcode(final War3ID rawcode) {
		final CLevelingAbility leveling = getAbility(GetAbilityByRawcodeVisitor.getInstance().reset(rawcode));
		if (leveling != null) {
			return leveling;
		}
		for (int i = 0, n = this.abilities.size(); i < n; i++) {
			final CAbility ability = this.abilities.get(i);
			// Measured: the original has no 'Arev' on an altar (reviving is part of its train queue, 'Aque').
			if (!(ability instanceof CLevelingAbility) && !(ability instanceof CAbilityReviveHero)
					&& rawcode.equals(ability.getAlias())) {
				return ability;
			}
		}
		return null;
	}

	public void releaseBehaviors(final CSimulation game, final CBehavior... released) {
		CUnitOrders.releaseBehaviors(this, game, released);
	}

	public int getAbilityLevel(final War3ID abilityId) {
		final CLevelingAbility ability = getAbility(GetAbilityByRawcodeVisitor.getInstance().reset(abilityId));
		if (ability == null) {
			return 0;
		} else {
			return ability.getLevel();
		}
	}

	public boolean chargeMana(final int manaCost) {
		if (this.stats.mana >= manaCost) {
			setMana(this.stats.mana - manaCost);
			return true;
		}
		return false;
	}

	public void fireSpellEvents(final CSimulation game, final JassGameEventsWar3 eventId, final CAbility ability,
			AbilityTarget target) {
		CUnitEvents.fireSpellEvents(this, game, eventId, ability, target);
	}

	public void fireSpellEventsNoTarget(final CSimulation game, final JassGameEventsWar3 eventId,
			final CAbility ability) {
		CUnitEvents.fireSpellEventsNoTarget(this, game, eventId, ability);
	}

	public void fireSpellEventsPointTarget(final CSimulation game, final JassGameEventsWar3 eventId,
			final CAbility ability, final AbilityPointTarget target) {
		CUnitEvents.fireSpellEventsPointTarget(this, game, eventId, ability, target);
	}

	public void fireSpellEventsUnitTarget(final CSimulation game, final JassGameEventsWar3 eventId,
			final CAbility ability, final CUnit target) {
		CUnitEvents.fireSpellEventsUnitTarget(this, game, eventId, ability, target);
	}

	public void fireSpellEventsItemTarget(final CSimulation game, final JassGameEventsWar3 eventId,
			final CAbility ability, final CItem target) {
		CUnitEvents.fireSpellEventsItemTarget(this, game, eventId, ability, target);
	}

	public void fireSpellEventsDestructableTarget(final CSimulation game, final JassGameEventsWar3 eventId,
			final CAbility ability, final CDestructable target) {
		CUnitEvents.fireSpellEventsDestructableTarget(this, game, eventId, ability, target);
	}

	public void fireSelectionEvents(final CSimulation game, final CPlayer selector, final boolean selected) {
		CUnitEvents.fireSelectionEvents(this, game, selector, selected);
	}

	public void firePickUpItemEvents(final CSimulation game, final CItem item) {
		CUnitEvents.firePickUpItemEvents(this, game, item);
	}

	public void fireUseItemEvents(final CSimulation game, final CItem item) {
		CUnitEvents.fireUseItemEvents(this, game, item);
	}

	public void fireDropItemEvents(final CSimulation game, final CItem item) {
		CUnitEvents.fireDropItemEvents(this, game, item);
	}

	void fireItemEvents(final CSimulation game, final CItem item, final JassGameEventsWar3 unitEvent,
			final JassGameEventsWar3 playerEvent) {
		CUnitEvents.fireItemEvents(this, game, item, unitEvent, playerEvent);
	}

	public void fireOrderEvents(final CSimulation game, final COrderNoTarget order) {
		CUnitEvents.fireOrderEvents(this, game, order);
	}

	public void fireOrderEvents(final CSimulation game, final COrderTargetPoint order) {
		CUnitEvents.fireOrderEvents(this, game, order);
	}

	public void fireOrderEvents(final CSimulation game, final COrderTargetWidget order) {
		CUnitEvents.fireOrderEvents(this, game, order);
	}

	public void fireConstructFinishEvents(final CSimulation game) {
		CUnitEvents.fireConstructFinishEvents(this, game);
	}

	public void fireSummonEvents(final CSimulation game) {
		CUnitEvents.fireSummonEvents(this, game);
	}

	public void fireHeroLevelEvents(final CSimulation game) {
		CUnitEvents.fireHeroLevelEvents(this, game);
	}

	public void fireHeroSkillEvents(final CSimulation game, final War3ID skill, final int skillLevel) {
		CUnitEvents.fireHeroSkillEvents(this, game, skill, skillLevel);
	}

	public void fireLoadedEvents(final CSimulation game, final CUnit transport) {
		CUnitEvents.fireLoadedEvents(this, game, transport);
	}

	public void fireTrainFinishEvents(final CSimulation game, final CUnit trainedUnit) {
		CUnitEvents.fireTrainFinishEvents(this, game, trainedUnit);
	}

	public void fireResearchFinishEvents(final CSimulation game, final War3ID researched) {
		CUnitEvents.fireResearchFinishEvents(this, game, researched);
	}

	public boolean isHero() {
		CAbility hdata = getHeroData();
		return hdata != null && !hdata.isDisabled(); // in future maybe do this with better performance
	}

	public boolean isUnitAlly(final CPlayer whichPlayer) {
		return whichPlayer.hasAlliance(getPlayerIndex(), CAllianceType.PASSIVE);
	}

	public ResourceType backToWork(final CSimulation game, final ResourceType defaultResourceType) {
		// if possible, check if this is a worker and send it to work
		for (final CAbility ability : this.abilities) {
			if (ability instanceof CAbilityHarvest) {
				final CAbilityHarvest abilityHarvest = (CAbilityHarvest) ability;
				final int carriedResourceAmount = abilityHarvest.getCarriedResourceAmount();
				final ResourceType carriedResourceType = abilityHarvest.getCarriedResourceType();
				if (carriedResourceAmount != 0) {
					switch (carriedResourceType) {
					case GOLD:
						if (carriedResourceAmount >= abilityHarvest.getGoldCapacity()) {
							abilityHarvest.getBehaviorReturnResources().reset(game);
							this.order(game, OrderIds.returnresources,
									abilityHarvest.getBehaviorReturnResources().findNearestDropoffPoint(game));
						} else {
							this.order(game, OrderIds.harvest, CBehaviorReturnResources.findNearestMine(this, game));
						}
						return ResourceType.GOLD;
					case LUMBER:
						if (carriedResourceAmount >= abilityHarvest.getLumberCapacity()) {
							abilityHarvest.getBehaviorReturnResources().reset(game);
							this.order(game, OrderIds.returnresources,
									abilityHarvest.getBehaviorReturnResources().findNearestDropoffPoint(game));
						} else {
							this.order(game, OrderIds.harvest, CBehaviorReturnResources.findNearestTree(this,
									abilityHarvest, game, abilityHarvest.getLastHarvestTarget()));
						}
						return ResourceType.LUMBER;
					default:
						throw new IllegalStateException(
								"Worker was carrying a resource of unsupported type: " + carriedResourceType);
					}
				} else if (carriedResourceType != null) {
					if (carriedResourceType == ResourceType.GOLD) {
						this.order(game, OrderIds.harvest, CBehaviorReturnResources.findNearestMine(this, game));
						return ResourceType.GOLD;
					} else if (carriedResourceType == ResourceType.LUMBER) {
						this.order(game, OrderIds.harvest, CBehaviorReturnResources.findNearestTree(this,
								abilityHarvest, game, abilityHarvest.getLastHarvestTarget()));
						return ResourceType.LUMBER;
					}
				} else if (defaultResourceType != null) {
					if (((defaultResourceType == ResourceType.GOLD) || (abilityHarvest.getLumberCapacity() == 0))
							&& (abilityHarvest.getGoldCapacity() > 0)) {
						this.order(game, OrderIds.harvest, CBehaviorReturnResources.findNearestMine(this, game));
						return ResourceType.GOLD;
					} else if (abilityHarvest.getLumberCapacity() > 0) {
						this.order(game, OrderIds.harvest, CBehaviorReturnResources.findNearestTree(this,
								abilityHarvest, game, abilityHarvest.getLastHarvestTarget()));
						return ResourceType.LUMBER;
					}
				}
			}
		}

		return null;
	}

	public void notifyAttacksChanged() {
		this.stateNotifier.attacksChanged();
	}

	public void notifyOrdersChanged() {
		this.stateNotifier.ordersChanged();
	}

	public void notifyAbilitiesChanged() {
		this.stateNotifier.abilitiesChanged();
	}

	public void notifyUpgradesChanged() {
		this.stateNotifier.upgradesChanged();
	}

	public void setConstructionConsumesWorker(final boolean constructionConsumesWorker) {
		this.production.constructionConsumesWorker = constructionConsumesWorker;
	}

	public boolean isConstructionConsumesWorker() {
		return this.production.constructionConsumesWorker;
	}

	public CDefenseType getDefenseType() {
		return this.defenseState.defenseType;
	}

	public void setDefenseType(final CDefenseType defenseType) {
		this.defenseState.defenseType = defenseType;
	}

	public float getCurrentSightRadius(final CSimulation game) {
		return CUnitVisibility.getCurrentSightRadius(this, game);
	}

	public void addUltravision() {
		this.visibility.ultravisionSources++;
	}

	public void removeUltravision() {
		if (this.visibility.ultravisionSources > 0) {
			this.visibility.ultravisionSources--;
		}
	}

	public boolean isUltravision() {
		return this.visibility.ultravisionSources > 0;
	}

	public EnumSet<CTargetType> getTargetedAs() {
		return this.targetedAs;
	}

	public void updateFogOfWar(final CSimulation game) {
		CUnitVisibility.updateFogOfWar(this, game);
	}

	public void updateFogOfWar(final CSimulation game, final CPlayerFogOfWar fogOfWar) {
		CUnitVisibility.updateFogOfWar(this, game, fogOfWar);
	}

	public void setExplodesOnDeath(final boolean explodesOnDeath) {
		this.death.explodesOnDeath = explodesOnDeath;
	}

	public void setExplodesOnDeathBuffId(final War3ID explodesOnDeathBuffId) {
		this.death.explodesOnDeathBuffId = explodesOnDeathBuffId;
	}

	public boolean isExplodesOnDeath() {
		return this.death.explodesOnDeath;
	}

	public List<CUnitAttackPreDamageListener> getPreDamageListenersForPriority(
			final CUnitAttackPreDamageListenerPriority priority) {
		return this.preDamageListeners.get(priority);
	}

	public void addPreDamageListener(final CUnitAttackPreDamageListenerPriority priority,
			final CUnitAttackPreDamageListener listener) {
		List<CUnitAttackPreDamageListener> list = this.preDamageListeners.get(priority);
		if (list == null) {
			list = new ArrayList<>();
		}
		list.add(0, listener);
	}

	public void removePreDamageListener(final CUnitAttackPreDamageListenerPriority priority,
			final CUnitAttackPreDamageListener listener) {
		final List<CUnitAttackPreDamageListener> list = this.preDamageListeners.get(priority);
		if (list != null) {
			list.remove(listener);
		}
	}

	public List<CUnitBehaviorChangeListener> getBehaviorChangeListeners() {
		return this.behaviorChangeListeners;
	}

	public void addBehaviorChangeListener(final CUnitBehaviorChangeListener listener) {
		this.behaviorChangeListeners.add(0, listener);
	}

	public void removeBehaviorChangeListener(final CUnitBehaviorChangeListener listener) {
		this.behaviorChangeListeners.remove(listener);
	}

	public void addDamageTakenModificationListener(final CUnitAttackDamageTakenModificationListener listener) {
		this.damageTakenModificationListeners.add(0, listener);
	}

	public void removeDamageTakenModificationListener(final CUnitAttackDamageTakenModificationListener listener) {
		this.damageTakenModificationListeners.remove(listener);
	}

	public void addFinalDamageTakenModificationListener(
			final CUnitAttackFinalDamageTakenModificationListener listener) {
		this.finalDamageTakenModificationListeners.add(0, listener);
	}

	public void removeFinalDamageTakenModificationListener(
			final CUnitAttackFinalDamageTakenModificationListener listener) {
		this.finalDamageTakenModificationListeners.remove(listener);
	}

	public void addDamageTakenListener(final CUnitAttackDamageTakenListener listener) {
		this.damageTakenListeners.add(0, listener);
	}

	public void removeDamageTakenListener(final CUnitAttackDamageTakenListener listener) {
		this.damageTakenListeners.remove(listener);
	}

	public void addDeathReplacementEffect(final CUnitDeathReplacementEffectPriority priority,
			final CUnitDeathReplacementEffect listener) {
		List<CUnitDeathReplacementEffect> list = this.deathReplacementEffects.get(priority);
		if (list == null) {
			list = new ArrayList<>();
		}
		list.add(0, listener);
	}

	public void removeDeathReplacementEffect(final CUnitDeathReplacementEffectPriority priority,
			final CUnitDeathReplacementEffect listener) {
		final List<CUnitDeathReplacementEffect> list = this.deathReplacementEffects.get(priority);
		if (list != null) {
			list.remove(listener);
		}
	}

	public void addEvasionListener(final CUnitAttackEvasionListener listener) {
		this.evasionListeners.add(0, listener);
	}

	public void removeEvasionListener(final CUnitAttackEvasionListener listener) {
		this.evasionListeners.remove(listener);
	}

	public void addAttackProjReactionListener(final CUnitAttackProjReactionListener listener) {
		this.attackProjReactionListeners.add(0, listener);
	}

	public void removeAttackProjReactionListener(final CUnitAttackProjReactionListener listener) {
		this.attackProjReactionListeners.remove(listener);
	}

	public void addAbilityProjReactionListener(final CUnitAbilityProjReactionListener listener) {
		this.abilityProjReactionListeners.add(0, listener);
	}

	public void removeAbilityProjReactionListener(final CUnitAbilityProjReactionListener listener) {
		this.abilityProjReactionListeners.remove(listener);
	}

	public void addAbilityEffectReactionListener(final CUnitAbilityEffectReactionListener listener) {
		this.abilityEffectReactionListeners.add(0, listener);
	}

	public void removeAbilityEffectReactionListener(final CUnitAbilityEffectReactionListener listener) {
		this.abilityEffectReactionListeners.remove(listener);
	}

	public List<CUnitAttackPostDamageListener> getPostDamageListeners() {
		return this.postDamageListeners;
	}

	public void addPostDamageListener(final CUnitAttackPostDamageListener listener) {
		this.postDamageListeners.add(0, listener);
	}

	public void removePostDamageListener(final CUnitAttackPostDamageListener listener) {
		this.postDamageListeners.remove(listener);
	}

	public void addAttackModifier(final CUnitAttackModifier modifier) {
		this.attackModifiers.add(0, modifier);
	}

	public void removeAttackModifier(final CUnitAttackModifier modifier) {
		this.attackModifiers.remove(modifier);
	}

	public void beginCooldown(final CSimulation game, final War3ID abilityId, final float cooldownDuration) {
		final int gameTurnTick = game.getGameTurnTick();
		final boolean noCooldowns = game.getPlayer(this.playerIndex).hasCheat(CReleaseCheats.NO_COOLDOWNS);
		this.rawcodeToCooldownExpireTime.put(abilityId.getValue(),
				gameTurnTick + (noCooldowns ? 0 : CTimer.ceilSteps(cooldownDuration)));
		this.rawcodeToCooldownStartTime.put(abilityId.getValue(), gameTurnTick);
		fireCooldownsChangedEvent();
	}

	public int getCooldownRemainingTicks(final CSimulation game, final War3ID abilityId) {
		final int expireTime = this.rawcodeToCooldownExpireTime.get(abilityId.getValue(), -1);
		final int gameTurnTick = game.getGameTurnTick();
		if ((expireTime == -1) || (expireTime <= gameTurnTick)) {
			return 0;
		}
		return expireTime - gameTurnTick;
	}

	public int getCooldownLengthDisplayTicks(final CSimulation game, final War3ID abilityId) {
		final int startTime = this.rawcodeToCooldownStartTime.get(abilityId.getValue(), -1);
		final int expireTime = this.rawcodeToCooldownExpireTime.get(abilityId.getValue(), -1);
		if ((startTime == -1) || (expireTime == -1)) {
			return 0;
		}
		return expireTime - startTime;
	}

	public boolean isRaisable() {
		return this.death.raisable;
	}

	public boolean isDecays() {
		return this.death.decays;
	}

	public boolean isNoUnitCollision() {
		return this.movement.noUnitCollision;
	}

	public boolean isNoBuildingCollision() {
		return this.movement.noBuildingCollision;
	}

	/** Whether the unit is a corpse raised by Animate Dead, which counts for neither food nor its player's units. */
	public boolean isAnimatedCorpse() {
		return this.death.animatedCorpse;
	}

	public void setRaisable(final boolean raisable) {
		this.death.raisable = raisable;
	}

	public void setDecays(final boolean decays) {
		this.death.decays = decays;
	}

	public void setMagicImmune(final boolean magicImmune) {
		this.magicImmune = magicImmune;
	}

	public boolean isMagicImmune() {
		return this.magicImmune;
	}

	public void setLimitedMagicImmune(final boolean limitedMagicImmune) {
		this.limitedMagicImmune = limitedMagicImmune;
	}

	public boolean isLimitedMagicImmune() {
		return this.limitedMagicImmune;
	}

	public boolean isMorphImmune() {
		return this.invulnerability.morphImmune;
	}

	public boolean isFalseDeath() {
		return this.death.falseDeath;
	}

	public void setFalseDeath(final boolean falseDeath) {
		this.death.falseDeath = falseDeath;
	}

	public void setAutocastAbility(final CSimulation simulation, final CAutocastAbility autocastAbility) {
		if (this.autocastAbility != null && !this.autocastAbility.equals(autocastAbility)) {
			this.autocastAbility.setAutoCastOn(simulation, this, false, false);
		}
		this.autocastAbility = autocastAbility;
	}

	public void updateSeenBy(final CSimulation simulation, final int playerCount) {
		CUnitVisibility.updateSeenBy(this, simulation, playerCount);
	}

	public boolean isSeenBy(final int playerIndex) {
		return (this.visibility.seenByPlayers & (1L << playerIndex)) != 0;
	}

	public boolean isVisible(final CSimulation simulation, final int toPlayerIndex) {
		return CUnitVisibility.isVisible(this, simulation, toPlayerIndex);
	}

	/**
	 * True while a revealing effect (Dust of Appearance) detects this invisible unit for the player. A paused unit
	 * that gave up its Ghost invisibility is still the invisible unit the effect was cast on.
	 */
	public boolean isRevealedTo(final int toPlayerIndex) {
		if ((this.visibility.detections & (1 << toPlayerIndex)) == 0) {
			return false;
		}
		if (this.visibility.invisLevels != 0) {
			return true;
		}
		if (!this.paused) {
			return false;
		}
		for (int i = 0, n = this.stateModBuffs.size(); i < n; i++) {
			final StateModBuff buff = this.stateModBuffs.get(i);
			if ((buff.getBuffType() == StateModBuffType.INVISIBLE) && (buff.getValue() != 0)
					&& ((buff.getValue() & StateModBuff.INVISIBLE_UNLESS_PAUSED) != 0)) {
				return true;
			}
		}
		return false;
	}

	public boolean hasUniqueFlag(String flag) {
		if (this.uniqueFlags != null) {
			return this.uniqueFlags.contains(flag);
		}
		return false;
	}

	public void addUniqueFlag(String flag) {
		if (this.uniqueFlags == null) {
			this.uniqueFlags = new ArrayList<>();
		}
		this.uniqueFlags.add(flag);
	}

	public void removeUniqueFlag(String flag) {
		if (this.uniqueFlags != null) {
			this.uniqueFlags.remove(flag);
		}
	}

	public boolean isSelectable(final CSimulation simulation, final int toPlayerIndex) {
		return ((this.visibility.unselectablePlayers & (1 << toPlayerIndex)) == 0);
	}

	public boolean isUnenumerable() {
		return this.unenumerable;
	}

	public boolean isHideMinimapIcon() {
		return this.visibility.hideMinimapIcon;
	}

	/** UnitSetUsesAltIcon: the minimap shows the map's alternate icon (SetAltMinimapIcon) for this unit. */
	public boolean isUsesAltMinimapIcon() {
		return this.usesAltMinimapIcon;
	}

	public void setUsesAltMinimapIcon(final boolean usesAltMinimapIcon) {
		this.usesAltMinimapIcon = usesAltMinimapIcon;
	}

	final class CTimerUnitFade extends CTimer {
		private final StateModBuff stateModBuff;

		public CTimerUnitFade(final StateModBuff stateModBuff) {
			this.stateModBuff = stateModBuff;
		}

		@Override
		public void onFire(final CSimulation simulation) {
			addStateModBuff(this.stateModBuff);
			computeUnitState(simulation, StateModBuffType.INVISIBLE);
		}

	}
}
