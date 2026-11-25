package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.item.CEquipmentType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.item.CItemTag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

public class CItem extends CWidget {
	private java.util.Map<War3ID, Integer> carriedCooldownEnds;
	private final static int COLLISION_SIZE = 16;
	private War3ID typeId;
	private CItemType itemType;
	private boolean hidden;
	private boolean invulnerable;
	private int charges;
	private CAbilityInventory containedInventory;
	private CUnit containedUnit;
	private CUnit lastHolder;
	private Rectangle registeredEnumRectangle;
	private boolean dropOnDeath;
	private boolean droppable;
	private boolean pawnable;
	private int userData;
	private War3ID dropId;
	// Set only by SetItemPlayer; -1 is neutral passive, the owner of every map item.
	private int playerIndex = -1;
	// The player color a script gave the model (SetItemColor); -1 leaves the model as it is. Only the picture.
	private int scriptColor = -1;
	// What a script set for the model's color and size (the item fields iclr, iclg, iclb, ical, isca); null reads the
	// item type. Kept here so the script reads them back without asking the render side.
	private int[] tintOverride;
	private Float modelScaleOverride;
	// What a script set on this one item through the object fields; null reads what the item type says.
	private War3ID skinId;
	private String nameOverride;
	private String descriptionOverride;
	private Boolean perishableOverride;
	private Boolean activelyUsedOverride;
	private Boolean useAutomaticallyOverride;
	private Boolean includeAsRandomChoiceOverride;
	// The abilities this one item gives its carrier, after a script added or removed one; null reads what the item
	// type says. groundAbilities are the abilities of an item lying on the ground, made when a script asks for them.
	private List<War3ID> abilityIds;
	private List<CAbility> groundAbilities;

	public CItem(final int handleId, final float x, final float y, final float life, final War3ID typeId,
			final CItemType itemTypeInstance) {
		super(handleId, x, y, life);
		this.typeId = typeId;
		this.itemType = itemTypeInstance;
		this.charges = itemTypeInstance.getNumberOfCharges();
		this.dropOnDeath = itemTypeInstance.isDroppedWhenCarrierDies();
		this.droppable = itemTypeInstance.isCanBeDropped();
		this.pawnable = itemTypeInstance.isPawnable();
	}
	
	public void setTypeId(CSimulation game, War3ID typeId) {
		int slot = 0;
		if (this.containedInventory != null) {
			slot = this.getContainedInventory().getSlot(this);
			this.forceDropIfHeld(game);
		}
		this.typeId = typeId;
		this.itemType = game.getItemData().getItemType(typeId);
		this.charges = this.itemType.getNumberOfCharges();
		this.dropOnDeath = this.itemType.isDroppedWhenCarrierDies();
		this.droppable = this.itemType.isCanBeDropped();
		this.pawnable = this.itemType.isPawnable();
		this.skinId = null;
		this.tintOverride = null;
		this.modelScaleOverride = null;
		this.nameOverride = null;
		this.descriptionOverride = null;
		this.perishableOverride = null;
		this.activelyUsedOverride = null;
		this.useAutomaticallyOverride = null;
		this.includeAsRandomChoiceOverride = null;
		this.abilityIds = null;
		this.groundAbilities = null;

		game.updateItemModel(this);
		// a worn item has no numbered slot to go back to
		if ((this.containedInventory != null) && (slot >= 0)) {
			this.containedInventory.giveItem(game, this.containedUnit, this, slot, false);
		}
		
	}

	@Override
	public float getFlyHeight() {
		return 0;
	}

	@Override
	public float getImpactZ() {
		return 0; // TODO probably from ItemType
	}

	@Override
	public float damage(final CSimulation simulation, final CUnit source, final CDamageFlags flags,
			final CAttackType attackType, final CDamageType damageType, final String weaponSoundType,
			final float damage) {
		if (this.invulnerable && !flags.isIgnoreInvulnerable()) {
			return 0;
		}
		final boolean wasDead = isDead();
		this.life -= damage;
		simulation.itemDamageEvent(this, weaponSoundType, this.itemType.getArmorType());
		if (isDead() && !wasDead) {
			fireDeathEvents(simulation);
			forceDropIfHeld(simulation);
			simulation.getWorldCollision().removeItem(this);
		}
		return damage;
	}

	@Override
	public float damage(final CSimulation simulation, final CUnit source, final CDamageFlags flags,
			final CAttackType attackType, final CDamageType damageType, final String weaponSoundType,
			final float damage, final float bonusDamage) {
		return this.damage(simulation, source, flags, attackType, damageType, weaponSoundType, damage + bonusDamage);
	}

	public void forceDropIfHeld(final CSimulation simulation) {
		if ((this.containedInventory != null) && (this.containedUnit != null)) {
			this.containedInventory.dropItem(simulation, this.containedUnit, this, this.containedUnit.getX(),
					this.containedUnit.getY(), false);
		}
	}

	@Override
	public boolean canBeTargetedBy(final CSimulation simulation, final CUnit source,
			final Set<CTargetType> targetsAllowed, final AbilityTargetCheckReceiver<CWidget> receiver) {
		if (targetsAllowed.contains(CTargetType.ITEM)) {
			return true;
		}
		receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_ITEMS);
		return false;
	}

	public void setX(final float x, final CWorldCollision collision) {
		final float oldX = getX();
		super.setX(x);
		collision.translate(this, x - oldX, 0);
	}

	public void setY(final float y, final CWorldCollision collision) {
		final float oldY = getY();
		super.setY(y);
		collision.translate(this, 0, y - oldY);
	}

	@Override
	public <T> T visit(final AbilityTargetVisitor<T> visitor) {
		return visitor.accept(this);
	}

	@Override
	public <T> T visit(final CWidgetVisitor<T> visitor) {
		return visitor.accept(this);
	}

	public War3ID getTypeId() {
		return this.typeId;
	}

	public CItemType getItemType() {
		return this.itemType;
	}

	public void setHidden(final boolean hidden) {
		this.hidden = hidden;
	}

	public boolean isHidden() {
		return this.hidden;
	}

	@Override
	public float getMaxLife() {
		return this.itemType.getMaxLife();
	}

	public void setPointAndCheckUnstuck(final float newX, final float newY, final CSimulation game) {
		final PathingGrid pathingGrid = game.getPathingGrid();
		;
		float outputX = newX, outputY = newY;
		int checkX = 0;
		int checkY = 0;
		tempRect.setSize(16, 16);
		for (int i = 0; i < 300; i++) {
			final float centerX = newX + (checkX * 64);
			final float centerY = newY + (checkY * 64);
			tempRect.setCenter(centerX, centerY);
			// Measured on 1.29: an item is only kept off the cell under its own point.
			if (pathingGrid.isPathable(centerX, centerY, MovementType.FOOT, 0f)) {
				outputX = centerX;
				outputY = centerY;
				break;
			}
			final double angle = ((((int) Math.floor(Math.sqrt((4 * i) + 1))) % 4) * Math.PI) / 2;
			checkX -= (int) StrictMath.cos(angle);
			checkY -= (int) StrictMath.sin(angle);
		}
		final float oldX = getX();
		final float oldY = getY();
		setX(outputX);
		setY(outputY);
		game.getWorldCollision().translate(this, outputX - oldX, outputY - oldY);
	}

	@Override
	public boolean isInvulnerable() {
		return this.invulnerable;
	}

	public void setInvulernable(final boolean invulnerable) {
		this.invulnerable = invulnerable;
	}

	public int getCharges() {
		return this.charges;
	}

	/**
	 * Measured on 1.29: the cooldown of an item without charges stays with the item, so whoever picks it up next
	 * finds it still running; a charged item's cooldown stays with the unit that used it.
	 */
	public void carryCooldown(final War3ID abilityCode, final int endTick) {
		if (this.carriedCooldownEnds == null) {
			this.carriedCooldownEnds = new java.util.HashMap<>();
		}
		this.carriedCooldownEnds.put(abilityCode, endTick);
	}

	public java.util.Map<War3ID, Integer> getCarriedCooldownEnds() {
		return (this.carriedCooldownEnds == null) ? java.util.Collections.<War3ID, Integer>emptyMap()
				: this.carriedCooldownEnds;
	}

	public void clearCarriedCooldowns() {
		this.carriedCooldownEnds = null;
	}

	public void setCharges(final int charges) {
		this.charges = charges;
	}

	public void setContainedInventory(final CAbilityInventory containedInventory, final CUnit containedUnit) {
		this.containedInventory = containedInventory;
		this.containedUnit = containedUnit;
	}

	public CAbilityInventory getContainedInventory() {
		return this.containedInventory;
	}

	/** The unit that held the item when it was last dropped, for the Rune of Rebirth. */
	public CUnit getLastHolder() {
		return this.lastHolder;
	}

	public void setLastHolder(final CUnit lastHolder) {
		this.lastHolder = lastHolder;
	}

	public CUnit getContainedUnit() {
		return this.containedUnit;
	}

	@Override
	public double distance(final float x, final float y) {
		return StrictMath.sqrt(distanceSquaredNoCollision(x, y));
	}

	public Rectangle getOrCreateRegisteredEnumRectangle() {
		if (this.registeredEnumRectangle == null) {
			this.registeredEnumRectangle = new Rectangle(getX() - COLLISION_SIZE, getY() - COLLISION_SIZE,
					COLLISION_SIZE * 2, COLLISION_SIZE * 2);
		}
		return this.registeredEnumRectangle;
	}

	public int getScriptColor() {
		return this.scriptColor;
	}

	public void setScriptColor(final int scriptColor) {
		this.scriptColor = scriptColor;
	}

	/** Red, green, blue (0 to 2) or alpha (3) of the model, 0 to 255. */
	public int getTint(final int channel) {
		if (this.tintOverride != null) {
			return this.tintOverride[channel];
		}
		return channel == 3 ? 255 : this.itemType.getTint(channel);
	}

	public void setTint(final int channel, final int value) {
		if (this.tintOverride == null) {
			this.tintOverride = new int[] { getTint(0), getTint(1), getTint(2), getTint(3) };
		}
		this.tintOverride[channel] = Math.max(0, Math.min(255, value));
	}

	/** A skin brings the color and size of its own item type. */
	public void setSkinLook(final CItemType skinType) {
		this.tintOverride = new int[] { skinType.getTint(0), skinType.getTint(1), skinType.getTint(2), 255 };
		this.modelScaleOverride = skinType.getModelScale();
	}

	public float getModelScale() {
		return this.modelScaleOverride == null ? this.itemType.getModelScale() : this.modelScaleOverride;
	}

	public void setModelScale(final float modelScale) {
		this.modelScaleOverride = modelScale;
	}

	public int getPlayerIndex() {
		return this.playerIndex;
	}

	public void setPlayerIndex(final int playerIndex) {
		this.playerIndex = playerIndex;
	}

	/** The skin the item shows (BlzSetItemSkin); its own type until a script gives it another. */
	public War3ID getSkinId() {
		return this.skinId == null ? this.typeId : this.skinId;
	}

	public void setSkinId(final War3ID skinId) {
		this.skinId = skinId;
	}

	public String getNameOverride() {
		return this.nameOverride;
	}

	public void setNameOverride(final String nameOverride) {
		this.nameOverride = nameOverride;
	}

	public String getDescriptionOverride() {
		return this.descriptionOverride;
	}

	public void setDescriptionOverride(final String descriptionOverride) {
		this.descriptionOverride = descriptionOverride;
	}

	public boolean isPerishable() {
		return (this.perishableOverride != null) ? this.perishableOverride : this.itemType.isPerishable();
	}

	public void setPerishable(final boolean perishable) {
		this.perishableOverride = perishable;
	}

	public boolean isActivelyUsed() {
		return (this.activelyUsedOverride != null) ? this.activelyUsedOverride : this.itemType.isActivelyUsed();
	}

	public void setActivelyUsed(final boolean activelyUsed) {
		this.activelyUsedOverride = activelyUsed;
	}

	public boolean isUseAutomaticallyWhenAcquired() {
		return (this.useAutomaticallyOverride != null) ? this.useAutomaticallyOverride
				: this.itemType.isUseAutomaticallyWhenAcquired();
	}

	public void setUseAutomaticallyWhenAcquired(final boolean useAutomatically) {
		this.useAutomaticallyOverride = useAutomatically;
	}

	public boolean isIncludeAsRandomChoice() {
		return (this.includeAsRandomChoiceOverride != null) ? this.includeAsRandomChoiceOverride
				: this.itemType.isIncludeAsRandomChoice();
	}

	public void setIncludeAsRandomChoice(final boolean includeAsRandomChoice) {
		this.includeAsRandomChoiceOverride = includeAsRandomChoice;
	}

	/** The ids of the abilities this item gives its carrier. */
	public List<War3ID> getAbilityIds() {
		return (this.abilityIds != null) ? this.abilityIds : this.itemType.getAbilityList();
	}

	public boolean addAbilityId(final War3ID abilityId) {
		if (getAbilityIds().contains(abilityId)) {
			return false;
		}
		if (this.abilityIds == null) {
			this.abilityIds = new ArrayList<>(this.itemType.getAbilityList());
		}
		this.abilityIds.add(abilityId);
		return true;
	}

	public boolean removeAbilityId(final War3ID abilityId) {
		if (!getAbilityIds().contains(abilityId)) {
			return false;
		}
		if (this.abilityIds == null) {
			this.abilityIds = new ArrayList<>(this.itemType.getAbilityList());
		}
		this.abilityIds.remove(abilityId);
		return true;
	}

	public List<CAbility> getGroundAbilities() {
		return this.groundAbilities;
	}

	public void setGroundAbilities(final List<CAbility> groundAbilities) {
		this.groundAbilities = groundAbilities;
	}

	public int getStackMax() {
		return this.itemType.getStackMax();
	}

	public CEquipmentType getEquipmentType() {
		return this.itemType.getEquipmentType();
	}

	public CItemTag getItemTag() {
		return this.itemType.getItemTag();
	}

	public boolean isDropOnDeath() {
		return this.dropOnDeath;
	}

	public void setDropOnDeath(final boolean dropOnDeath) {
		this.dropOnDeath = dropOnDeath;
	}

	public boolean isDroppable() {
		return this.droppable;
	}

	public void setDroppable(final boolean droppable) {
		this.droppable = droppable;
	}

	public boolean isPawnable() {
		return this.pawnable;
	}

	public void setPawnable(final boolean pawnable) {
		this.pawnable = pawnable;
	}

	public int getUserData() {
		return this.userData;
	}

	public void setUserData(final int userData) {
		this.userData = userData;
	}

	public War3ID getDropId() {
		return this.dropId;
	}

	public void setDropId(final War3ID unitId) {
		this.dropId = unitId;
	}

}
