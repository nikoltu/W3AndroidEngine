package com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.frames.ListBoxFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.ListBoxFrame.ListBoxSelelectionListener;
import com.etheller.warsmash.util.ListItemEnum;

public class MapListContainer {
	private final SimpleFrame mapListContainer;
	private final ListBoxFrame mapListBox;

	public MapListContainer(final GameUI rootFrame, final Viewport uiViewport, final String containerKey,
			final DataSource dataSource, final BitmapFont font) {
		this.mapListContainer = (SimpleFrame) rootFrame.getFrameByName(containerKey, 0);
		this.mapListBox = (ListBoxFrame) rootFrame.createFrameByType("LISTBOX", "MapListBox", this.mapListContainer,
				"WITHCHILDREN", 0);
		this.mapListBox.setSetAllPoints(true);
		this.mapListBox.setFrameFont(font);
		final Collection<String> listfile = dataSource.getListfile();
		final List<String> displayItemPaths = new ArrayList<>();
		for (final String file : listfile) {
			final String lower = file.toLowerCase().replace('/', '\\');
			if ((lower.endsWith(".w3x") || lower.endsWith(".w3m"))
					&& ((!lower.contains("\\")) || lower.startsWith("maps\\"))) {
				displayItemPaths.add(file);
			}
		}
		for (final String displayItemPath : displayItemPaths) {
			try {
				this.mapListBox.addItem(displayItemPath, ListItemEnum.ITEM_MAP, rootFrame, uiViewport);
			}
			catch (final RuntimeException e) {
				System.err.println("Skipping map with unreadable info: " + displayItemPath + " (" + e + ")");
			}
		}
		this.mapListBox.sortItems();
		this.mapListContainer.add(this.mapListBox);
	}

	public void addSelectionListener(final ListBoxSelelectionListener listener) {
		this.mapListBox.setSelectionListener(listener);
	}

	public String getSelectedItem() {
		return this.mapListBox.getSelectedItem();
	}
}
