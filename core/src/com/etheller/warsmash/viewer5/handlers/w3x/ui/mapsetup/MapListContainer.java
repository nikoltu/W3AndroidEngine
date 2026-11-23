package com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.frames.ListBoxFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.ListBoxFrame.ListBoxSelelectionListener;
import com.etheller.warsmash.launcher.GameCatalog;
import com.etheller.warsmash.launcher.MapListCache;
import com.etheller.warsmash.launcher.MapSections;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.util.AbstractListItemProperty;
import com.etheller.warsmash.util.ListItemEnum;
import com.etheller.warsmash.util.ListItemMapProperty;
import com.etheller.warsmash.util.ListItemStringProperty;
import com.etheller.warsmash.util.Log;

public class MapListContainer {
	/** The colour of a section's heading. */
	private static final Color HEADING_COLOR = new Color(1f, 0.8f, 0f, 1f);

	private final GameUI rootFrame;
	private final Viewport uiViewport;
	private final DataSource dataSource;
	private final DataTable ini;
	private final SimpleFrame mapListContainer;
	private final ListBoxFrame mapListBox;
	private final Set<String> unreadable = new HashSet<>();

	public MapListContainer(final GameUI rootFrame, final Viewport uiViewport, final String containerKey,
			final DataSource dataSource, final BitmapFont font) {
		this(rootFrame, uiViewport, containerKey, dataSource, font, null);
	}

	/**
	 * @param ini the engine configuration, which lets the list come from the map list the launcher keeps for the same
	 *            game data and map files instead of opening every map again; null opens the maps
	 */
	public MapListContainer(final GameUI rootFrame, final Viewport uiViewport, final String containerKey,
			final DataSource dataSource, final BitmapFont font, final DataTable ini) {
		this.rootFrame = rootFrame;
		this.uiViewport = uiViewport;
		this.dataSource = dataSource;
		this.ini = ini;
		this.mapListContainer = (SimpleFrame) rootFrame.getFrameByName(containerKey, 0);
		this.mapListBox = (ListBoxFrame) rootFrame.createFrameByType("LISTBOX", "MapListBox", this.mapListContainer,
				"WITHCHILDREN", 0);
		this.mapListBox.setSetAllPoints(true);
		this.mapListBox.setFrameFont(font);
		fill();
		this.mapListContainer.add(this.mapListBox);
	}

	/**
	 * Makes the list again from the maps the data has now, after the player's folder was changed or maps were added
	 * to it. The map chosen stays chosen while the list still has it.
	 */
	public void reload() {
		final String chosen = this.mapListBox.getSelectedItem();
		fill();
		if (chosen != null) {
			this.mapListBox.selectIndex(this.mapListBox.indexOf(chosen));
		}
	}

	private void fill() {
		this.unreadable.clear();
		List<AbstractListItemProperty> maps = keptMaps();
		if (maps == null) {
			maps = openedMaps();
		}
		// Within a section the maps are by players and name, those that cannot be read last.
		maps.sort(AbstractListItemProperty::compare);
		final List<AbstractListItemProperty> items = new ArrayList<>();
		for (final MapSections.Section<AbstractListItemProperty> section : MapSections.group(maps,
				AbstractListItemProperty::getRawValue)) {
			items.add(ListItemStringProperty.heading(section.title, HEADING_COLOR));
			items.addAll(section.items);
		}
		this.mapListBox.setItems(items, this.rootFrame, this.uiViewport);
	}

	private List<AbstractListItemProperty> openedMaps() {
		final List<AbstractListItemProperty> items = new ArrayList<>();
		for (final String file : this.dataSource.getListfile(LISTED_MAPS)) {
			try {
				items.add(AbstractListItemProperty.createFromType(file, ListItemEnum.ITEM_MAP, this.rootFrame,
						this.dataSource));
			}
			catch (final RuntimeException e) {
				Log.error("Skipping map with unreadable info: " + file + " (" + e + ")");
			}
		}
		return items;
	}

	/** The maps from the shared list, or null when there is none to use. */
	private List<AbstractListItemProperty> keptMaps() {
		final File kept = MapListCache.shared();
		if ((kept == null) || (this.ini == null)) {
			return null;
		}
		try {
			final MapListCache.Listing listing = MapListCache.load(kept, this.ini, this.dataSource);
			final List<AbstractListItemProperty> items = new ArrayList<>();
			for (final GameCatalog.MapEntry entry : listing.maps) {
				items.add(new ListItemMapProperty(entry.path, entry.name, entry.players, entry.melee));
			}
			for (final MapListCache.Skipped left : listing.skipped) {
				final String file = MapSections.fileName(left.path);
				items.add(new ListItemMapProperty(left.path, file + " (" + reasonText(left.problem) + ")", 0, false, true));
				this.unreadable.add(left.path);
			}
			return items;
		}
		catch (final RuntimeException e) {
			Log.error("The kept map list cannot be used: " + e);
			this.unreadable.clear();
			return null;
		}
	}

	private String reasonText(final MapListCache.Problem problem) {
		return problem == MapListCache.Problem.UNREADABLE
				? this.rootFrame.smashString("SMASH_MAP_UNREADABLE", "not a map file or damaged")
				: this.rootFrame.smashString("SMASH_MAP_NO_INFORMATION", "no map information");
	}

	/** Whether the list shows the map only to say it cannot be read, so that it cannot be started. */
	public boolean isUnreadable(final String path) {
		return (path != null) && this.unreadable.contains(path);
	}

	/**
	 * Folders of maps built for automated test runs; those maps keep the names of the maps they were built from and
	 * are not for players.
	 */
	private static final String[] TEST_MAP_FOLDERS = { "wc3probes", "probemaps" };

	/**
	 * The maps the custom game list offers: those under Maps and those at the top of a data source, but no test
	 * maps.
	 */
	public static boolean isListedMap(final String file) {
		if (!hasMapExtension(file)) {
			return false;
		}
		final String lower = file.toLowerCase(Locale.ROOT).replace('/', '\\');
		return (lower.endsWith(".w3x") || lower.endsWith(".w3m"))
				&& ((!lower.contains("\\")) || lower.startsWith("maps\\")) && !isTestMap(lower);
	}

	/** {@link #isListedMap} as a filter of listed paths. */
	public static final DataSource.PathFilter LISTED_MAPS = (path, from) -> ((path.length() - from) >= 4)
			&& hasMapExtension(path) && isListedMap(from == 0 ? path : path.substring(from));

	/** The cheap part of {@link #isListedMap}, which every path of the game data goes through. */
	private static boolean hasMapExtension(final String file) {
		final int length = file.length();
		return (length >= 4) && (file.charAt(length - 4) == '.')
				&& (file.regionMatches(true, length - 3, "w3x", 0, 3) || file.regionMatches(true, length - 3, "w3m", 0, 3));
	}

	/** Whether a file lies in a folder of test maps, at any depth. */
	public static boolean isTestMap(final String file) {
		final String[] parts = file.toLowerCase(Locale.ROOT).replace('/', '\\').split("\\\\");
		for (int i = 0; i < (parts.length - 1); i++) {
			for (final String folder : TEST_MAP_FOLDERS) {
				if (parts[i].equals(folder)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Selects a map as a click on it would.
	 *
	 * @return false when the list does not have it
	 */
	public boolean select(final String path) {
		final int index = this.mapListBox.indexOf(path);
		if (index < 0) {
			return false;
		}
		this.mapListBox.selectIndex(index);
		return true;
	}

	public void addSelectionListener(final ListBoxSelelectionListener listener) {
		this.mapListBox.setSelectionListener(listener);
	}

	public String getSelectedItem() {
		return this.mapListBox.getSelectedItem();
	}

	/**
	 * The original opens the list on the map last played from it (registry Map\skirmish_V0) and otherwise on the first
	 * entry.
	 */
	public void selectInitial(final String lastPlayedMap) {
		if (this.mapListBox.getSelectedItem() != null) {
			return;
		}
		final int lastPlayedIndex = lastPlayedMap == null ? -1 : this.mapListBox.indexOf(lastPlayedMap);
		this.mapListBox.selectIndex(lastPlayedIndex >= 0 ? lastPlayedIndex : this.mapListBox.firstSelectableIndex());
	}
}
