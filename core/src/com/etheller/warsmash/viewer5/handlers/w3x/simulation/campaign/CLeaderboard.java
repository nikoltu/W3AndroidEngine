package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Leaderboard contents. LeaderboardGetPlayerIndex and the sort natives are measured against 1.29.
 */
public final class CLeaderboard {
	private final List<Item> items = new ArrayList<>();
	private String label = "";
	private boolean displayed;
	private boolean showLabel = true;
	private boolean showNames = true;
	private boolean showValues = true;
	private boolean showIcons = true;
	private int labelColor = -1;
	private int valueColor = -1;
	private int sizeByItemCount;
	private Runnable changeListener;

	public void setChangeListener(final Runnable changeListener) {
		this.changeListener = changeListener;
	}

	private void changed() {
		if (this.changeListener != null) {
			this.changeListener.run();
		}
	}

	public List<Item> getItems() {
		return this.items;
	}

	public int getItemCount() {
		return this.items.size();
	}

	public void addItem(final String label, final int value, final int playerIndex) {
		final int existing = getPlayerIndex(playerIndex);
		if (existing != -1) {
			final Item item = this.items.get(existing);
			item.label = label;
			item.value = value;
		}
		else {
			final Item item = new Item();
			item.label = label;
			item.value = value;
			item.playerIndex = playerIndex;
			this.items.add(item);
		}
		changed();
	}

	public void removeItem(final int index) {
		if ((index >= 0) && (index < this.items.size())) {
			this.items.remove(index);
			changed();
		}
	}

	public void removePlayerItem(final int playerIndex) {
		removeItem(getPlayerIndex(playerIndex));
	}

	public void clear() {
		this.items.clear();
		changed();
	}

	public boolean hasPlayerItem(final int playerIndex) {
		return getPlayerIndex(playerIndex) != -1;
	}

	public int getPlayerIndex(final int playerIndex) {
		for (int i = 0; i < this.items.size(); i++) {
			if (this.items.get(i).playerIndex == playerIndex) {
				return i;
			}
		}
		return -1;
	}

	public void sortByValue(final boolean ascending) {
		sort(Comparator.comparingInt(item -> item.value), ascending);
	}

	public void sortByPlayer(final boolean ascending) {
		sort(Comparator.comparingInt(item -> item.playerIndex), ascending);
	}

	public void sortByLabel(final boolean ascending) {
		sort(Comparator.comparing(item -> item.label), ascending);
	}

	private void sort(final Comparator<Item> comparator, final boolean ascending) {
		this.items.sort(ascending ? comparator : comparator.reversed());
		changed();
	}

	public void setItemValue(final int index, final int value) {
		if ((index >= 0) && (index < this.items.size())) {
			this.items.get(index).value = value;
			changed();
		}
	}

	public void setItemLabel(final int index, final String label) {
		if ((index >= 0) && (index < this.items.size())) {
			this.items.get(index).label = label;
			changed();
		}
	}

	public void setItemStyle(final int index, final boolean showLabel, final boolean showValue,
			final boolean showIcon) {
		if ((index >= 0) && (index < this.items.size())) {
			final Item item = this.items.get(index);
			item.showLabel = showLabel;
			item.showValue = showValue;
			item.showIcon = showIcon;
			changed();
		}
	}

	public void setItemLabelColor(final int index, final int color) {
		if ((index >= 0) && (index < this.items.size())) {
			this.items.get(index).labelColor = color;
			changed();
		}
	}

	public void setItemValueColor(final int index, final int color) {
		if ((index >= 0) && (index < this.items.size())) {
			this.items.get(index).valueColor = color;
			changed();
		}
	}

	public String getLabel() {
		return this.label;
	}

	public void setLabel(final String label) {
		this.label = label == null ? "" : label;
		changed();
	}

	public boolean isDisplayed() {
		return this.displayed;
	}

	public void setDisplayed(final boolean displayed) {
		this.displayed = displayed;
		changed();
	}

	public void setStyle(final boolean showLabel, final boolean showNames, final boolean showValues,
			final boolean showIcons) {
		this.showLabel = showLabel;
		this.showNames = showNames;
		this.showValues = showValues;
		this.showIcons = showIcons;
		changed();
	}

	public boolean isShowLabel() {
		return this.showLabel;
	}

	public boolean isShowNames() {
		return this.showNames;
	}

	public boolean isShowValues() {
		return this.showValues;
	}

	public boolean isShowIcons() {
		return this.showIcons;
	}

	public void setLabelColor(final int color) {
		this.labelColor = color;
		changed();
	}

	public int getLabelColor() {
		return this.labelColor;
	}

	public void setValueColor(final int color) {
		this.valueColor = color;
		changed();
	}

	public int getValueColor() {
		return this.valueColor;
	}

	public void setSizeByItemCount(final int count) {
		this.sizeByItemCount = count;
		changed();
	}

	public int getSizeByItemCount() {
		return this.sizeByItemCount;
	}

	public static final class Item {
		public String label;
		public int value;
		public int playerIndex;
		public boolean showLabel = true;
		public boolean showValue = true;
		public boolean showIcon = true;
		public int labelColor = -1;
		public int valueColor = -1;
	}
}
