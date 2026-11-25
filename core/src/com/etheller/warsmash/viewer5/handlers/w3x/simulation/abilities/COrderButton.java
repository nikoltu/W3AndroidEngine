package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities;

import com.etheller.interpreter.ast.util.CExtensibleHandleAbstract;
import com.etheller.warsmash.util.War3ID;

public class COrderButton extends CExtensibleHandleAbstract {
	private static final int INFINITE_CHARGES = -1;

	private int orderId;
	private int autoCastOrderId;
	private int autoCastUnOrderId;
	private int containerMenuOrderId;
	private boolean disabled;
	private boolean hidden;
	private int manaCost;
	private int goldCost;
	private int lumberCost;
	private int charges = INFINITE_CHARGES;
	private int foodCost;
	private JassOrderButtonType type;
	private boolean autoCastActive;
	// What a script gave the button to show; the render side has its own copy to draw, this one is what scripts read.
	private int buttonPositionX;
	private int buttonPositionY;
	private String tip;
	private String uberTip;
	private char hotkey;
	private War3ID previewBuildUnitId;
	private float mouseTargetRadius;

	public COrderButton(final int orderId) {
		this.orderId = orderId;
	}

	public int getOrderId() {
		return this.orderId;
	}

	public int getAutoCastOrderId() {
		return this.autoCastOrderId;
	}

	public void setAutoCastOrderId(final int autoCastOrderId) {
		this.autoCastOrderId = autoCastOrderId;
	}

	public int getAutoCastUnOrderId() {
		return this.autoCastUnOrderId;
	}

	public void setAutoCastUnOrderId(final int autoCastUnOrderId) {
		this.autoCastUnOrderId = autoCastUnOrderId;
	}

	public int getContainerMenuOrderId() {
		return this.containerMenuOrderId;
	}

	public void setContainerMenuOrderId(final int containerMenuOrderId) {
		this.containerMenuOrderId = containerMenuOrderId;
	}

	public boolean isDisabled() {
		return this.disabled;
	}

	public void setDisabled(final boolean disabled) {
		this.disabled = disabled;
	}

	/** A hidden button is left off the command card; its order can still be given. */
	public boolean isHidden() {
		return this.hidden;
	}

	public void setHidden(final boolean hidden) {
		this.hidden = hidden;
	}

	public int getManaCost() {
		return this.manaCost;
	}

	public void setManaCost(final int manaCost) {
		this.manaCost = manaCost;
	}

	public int getGoldCost() {
		return this.goldCost;
	}

	public void setGoldCost(final int goldCost) {
		this.goldCost = goldCost;
	}

	public int getLumberCost() {
		return this.lumberCost;
	}

	public void setLumberCost(final int lumberCost) {
		this.lumberCost = lumberCost;
	}

	public int getCharges() {
		return this.charges;
	}

	public void setCharges(final int charges) {
		this.charges = charges;
	}

	public int getFoodCost() {
		return this.foodCost;
	}

	public void setFoodCost(final int foodCost) {
		this.foodCost = foodCost;
	}

	public JassOrderButtonType getType() {
		return this.type;
	}

	public void setType(final JassOrderButtonType type) {
		this.type = type;
	}

	public boolean isAutoCastActive() {
		return this.autoCastActive;
	}

	public void setAutoCastActive(final boolean autoCastActive) {
		this.autoCastActive = autoCastActive;
	}

	public int getButtonPositionX() {
		return this.buttonPositionX;
	}

	public void setButtonPositionX(final int buttonPositionX) {
		this.buttonPositionX = buttonPositionX;
	}

	public int getButtonPositionY() {
		return this.buttonPositionY;
	}

	public void setButtonPositionY(final int buttonPositionY) {
		this.buttonPositionY = buttonPositionY;
	}

	public String getTip() {
		return this.tip;
	}

	public void setTip(final String tip) {
		this.tip = tip;
	}

	public String getUberTip() {
		return this.uberTip;
	}

	public void setUberTip(final String uberTip) {
		this.uberTip = uberTip;
	}

	public char getHotkey() {
		return this.hotkey;
	}

	public void setHotkey(final char hotkey) {
		this.hotkey = hotkey;
	}

	/** The unit type a build button previews; null until a script sets one. */
	public War3ID getPreviewBuildUnitId() {
		return this.previewBuildUnitId;
	}

	public void setPreviewBuildUnitId(final War3ID previewBuildUnitId) {
		this.previewBuildUnitId = previewBuildUnitId;
	}

	public float getMouseTargetRadius() {
		return this.mouseTargetRadius;
	}

	public void setMouseTargetRadius(final float mouseTargetRadius) {
		this.mouseTargetRadius = mouseTargetRadius;
	}

	public static enum JassOrderButtonType {
		INSTANT_NO_TARGET,
		UNIT_TARGET,
		POINT_TARGET,
		UNIT_OR_POINT_TARGET,
		INSTANT_NO_TARGET_NO_INTERRUPT,
		PASSIVE,
		MENU;

		public static final JassOrderButtonType[] VALUES = values();
	}
}
