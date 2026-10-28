package com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup;

import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.frames.CheckBoxFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.PopupMenuFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfig;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapFlag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapPlacement;

/**
 * The game setup's Advanced Options: the choices become the map flags and placement of the game, as in the original
 * game's lobby.
 */
public class AdvancedOptionsPane {
	private static final String[] OBSERVER_ITEMS = { "FULL_OBSERVERS", "OBSERVERS_ON_DEFEAT", "REFEREES",
			"NO_OBSERVERS" };
	private static final String[] VISIBILITY_ITEMS = { "DEFAULT", "HIDE_TERRAIN", "MAP_EXPLORED", "ALWAYS_VISIBLE" };
	private static final int NO_OBSERVERS = 3;
	private static final int OBSERVERS_ON_DEFEAT = 1;
	private static final int FULL_OBSERVERS = 0;
	private static final int REFEREES = 2;
	private static final int HIDE_TERRAIN = 1;
	private static final int MAP_EXPLORED = 2;
	private static final int ALWAYS_VISIBLE = 3;

	private final UIFrame paneFrame;
	private final CheckBoxFrame lockTeams;
	private final CheckBoxFrame teamsTogether;
	private final CheckBoxFrame sharedControl;
	private final CheckBoxFrame randomRaces;
	private final CheckBoxFrame randomHero;
	private int observers = NO_OBSERVERS;
	private int visibility = 0;

	public AdvancedOptionsPane(final GameUI rootFrame, final SimpleFrame container) {
		this.paneFrame = rootFrame.createFrame("AdvancedOptionsPane", container, 0, 0);
		this.paneFrame.setSetAllPoints(true);
		container.add(this.paneFrame);
		this.lockTeams = (CheckBoxFrame) rootFrame.getFrameByName("LockTeamsCheckBox", 0);
		this.teamsTogether = (CheckBoxFrame) rootFrame.getFrameByName("TeamsTogetherCheckBox", 0);
		this.sharedControl = (CheckBoxFrame) rootFrame.getFrameByName("AdvSharedControlCheckBox", 0);
		this.randomRaces = (CheckBoxFrame) rootFrame.getFrameByName("RandomRacesCheckBox", 0);
		this.randomHero = (CheckBoxFrame) rootFrame.getFrameByName("RandomHeroCheckBox", 0);
		this.lockTeams.setChecked(true);
		this.teamsTogether.setChecked(true);
		setUpMenu(rootFrame, "ObserversMenu", OBSERVER_ITEMS, this.observers, (index) -> this.observers = index);
		setUpMenu(rootFrame, "MapVisibilityMenu", VISIBILITY_ITEMS, this.visibility,
				(index) -> this.visibility = index);
	}

	private interface IndexListener {
		void selected(int index);
	}

	private static void setUpMenu(final GameUI rootFrame, final String name, final String[] items, final int selected,
			final IndexListener listener) {
		final PopupMenuFrame menu = (PopupMenuFrame) rootFrame.getFrameByName(name, 0);
		final StringFrame title = (StringFrame) ((GlueTextButtonFrame) menu.getPopupTitleFrame()).getButtonText();
		rootFrame.setText(title, rootFrame.getTemplates().getDecoratedString(items[selected]));
		menu.setMenuClickListener((button, menuItemIndex) -> {
			if ((menuItemIndex >= 0) && (menuItemIndex < items.length)) {
				rootFrame.setText(title, rootFrame.getTemplates().getDecoratedString(items[menuItemIndex]));
				listener.selected(menuItemIndex);
			}
		});
	}

	public void applyTo(final War3MapConfig config) {
		config.setMapFlag(CMapFlag.MAP_LOCK_ALLIANCE_CHANGES, this.lockTeams.isChecked());
		config.setMapFlag(CMapFlag.MAP_SHARED_ADVANCED_CONTROL, this.sharedControl.isChecked());
		config.setMapFlag(CMapFlag.MAP_RANDOM_RACES, this.randomRaces.isChecked());
		config.setMapFlag(CMapFlag.MAP_RANDOM_HERO, this.randomHero.isChecked());
		config.setMapFlag(CMapFlag.MAP_OBSERVERS,
				(this.observers == FULL_OBSERVERS) || (this.observers == REFEREES));
		config.setMapFlag(CMapFlag.MAP_OBSERVERS_ON_DEATH, this.observers == OBSERVERS_ON_DEFEAT);
		config.setMapFlag(CMapFlag.MAP_FOG_HIDE_TERRAIN, this.visibility == HIDE_TERRAIN);
		config.setMapFlag(CMapFlag.MAP_FOG_MAP_EXPLORED, this.visibility == MAP_EXPLORED);
		config.setMapFlag(CMapFlag.MAP_FOG_ALWAYS_VISIBLE, this.visibility == ALWAYS_VISIBLE);
		config.setPlacement(this.teamsTogether.isChecked() ? CMapPlacement.TEAMS_TOGETHER : CMapPlacement.RANDOM);
	}
}
