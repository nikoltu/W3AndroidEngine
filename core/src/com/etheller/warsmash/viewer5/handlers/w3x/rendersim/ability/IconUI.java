package com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability;

import com.badlogic.gdx.graphics.Texture;

public class IconUI {
	private IconTexture icon;
	private IconTexture iconDisabled;
	private int buttonPositionX;
	private int buttonPositionY;
	private String toolTip;
	private String uberTip;
	private final char hotkey;

	public IconUI(final IconTexture icon, final IconTexture iconDisabled, final int buttonPositionX, final int buttonPositionY,
			final String toolTip, final String uberTip, final char hotkey) {
		this.icon = icon;
		this.iconDisabled = iconDisabled;
		this.buttonPositionX = buttonPositionX;
		this.buttonPositionY = buttonPositionY;
		this.toolTip = toolTip;
		this.uberTip = uberTip;
		this.hotkey = hotkey;
	}

	public Texture getIcon() {
		return this.icon.get();
	}

	public Texture getIconDisabled() {
		return this.iconDisabled.get();
	}

	public int getButtonPositionX() {
		return this.buttonPositionX;
	}

	public int getButtonPositionY() {
		return this.buttonPositionY;
	}

	public String getToolTip() {
		return this.toolTip;
	}

	public String getUberTip() {
		return this.uberTip;
	}

	public char getHotkey() {
		return this.hotkey;
	}

	public String getIconPath() {
		return this.icon.getPath();
	}

	public void setIcon(final IconTexture icon, final IconTexture iconDisabled) {
		this.icon = icon;
		this.iconDisabled = iconDisabled;
	}

	public void setButtonPositionX(final int buttonPositionX) {
		this.buttonPositionX = buttonPositionX;
	}

	public void setButtonPositionY(final int buttonPositionY) {
		this.buttonPositionY = buttonPositionY;
	}

	public void setToolTip(final String toolTip) {
		this.toolTip = toolTip;
	}

	public void setUberTip(final String uberTip) {
		this.uberTip = uberTip;
	}
}
