package com.etheller.warsmash.parsers.w3x.objectdata;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.units.StandardObjectData.WarcraftData;
import com.etheller.warsmash.units.custom.ObjectDataChangeEntry;
import com.etheller.warsmash.units.custom.ObjectMap;
import com.etheller.warsmash.units.custom.WTS;
import com.etheller.warsmash.units.custom.War3ObjectDataChangeset;
import com.etheller.warsmash.util.War3ID;
import com.google.common.io.LittleEndianDataInputStream;

/**
 * The object editor changes of a map and its campaign, read in the order the game applies them: map, skin, campaign.
 */
final class MapObjectChangesets {
	final War3ObjectDataChangeset units = new War3ObjectDataChangeset('u');
	final War3ObjectDataChangeset items = new War3ObjectDataChangeset('t');
	final War3ObjectDataChangeset doodads = new War3ObjectDataChangeset('d');
	final War3ObjectDataChangeset destructables = new War3ObjectDataChangeset('b');
	final War3ObjectDataChangeset abilities = new War3ObjectDataChangeset('a');
	final War3ObjectDataChangeset buffs = new War3ObjectDataChangeset('h');
	final War3ObjectDataChangeset upgrades = new War3ObjectDataChangeset('q');

	private final DataSource dataSource;
	private final boolean inlineWTS;

	private MapObjectChangesets(final DataSource dataSource, final boolean inlineWTS) {
		this.dataSource = dataSource;
		this.inlineWTS = inlineWTS;
	}

	static MapObjectChangesets read(final DataSource dataSource, final boolean inlineWTS, final WTS wts,
			final WTS campaignWTS, final WarcraftData standardUnits, final WarcraftData standardItems)
			throws IOException {
		final MapObjectChangesets changesets = new MapObjectChangesets(dataSource, inlineWTS);
		if (changesets.load(changesets.units, "war3map.w3u", wts)) {
			// push unit changes to items.... as a Reign of Chaos support...
			changesets.moveItems(changesets.units.getOriginal(), changesets.items.getOriginal(), standardUnits,
					standardItems);
			changesets.moveItems(changesets.units.getCustom(), changesets.items.getCustom(), standardUnits,
					standardItems);
		}
		changesets.load(changesets.units, "war3mapSkin.w3u", wts);
		changesets.load(changesets.units, "war3campaign.w3u", campaignWTS);
		// ================== REMOVE LATER =====================
		changesets.load(changesets.units, "war3mod.w3u", wts);
		// =====================================================
		changesets.load(changesets.items, "war3map.w3t", wts);
		changesets.load(changesets.items, "war3mapSkin.w3t", wts);
		changesets.load(changesets.items, "war3campaign.w3t", campaignWTS);
		changesets.load(changesets.doodads, "war3map.w3d", wts);
		changesets.load(changesets.doodads, "war3campaign.w3d", campaignWTS);
		changesets.load(changesets.destructables, "war3map.w3b", wts);
		changesets.load(changesets.destructables, "war3mapSkin.w3b", wts);
		changesets.load(changesets.destructables, "war3campaign.w3b", campaignWTS);
		changesets.load(changesets.abilities, "war3map.w3a", wts);
		changesets.load(changesets.abilities, "war3mapSkin.w3a", wts);
		changesets.load(changesets.abilities, "war3campaign.w3a", campaignWTS);
		changesets.load(changesets.buffs, "war3map.w3h", wts);
		changesets.load(changesets.buffs, "war3mapSkin.w3h", wts);
		changesets.load(changesets.buffs, "war3campaign.w3h", campaignWTS);
		changesets.load(changesets.upgrades, "war3map.w3q", wts);
		changesets.load(changesets.upgrades, "war3mapSkin.w3q", wts);
		changesets.load(changesets.upgrades, "war3campaign.w3q", campaignWTS);
		return changesets;
	}

	private boolean load(final War3ObjectDataChangeset changeset, final String file, final WTS wts)
			throws IOException {
		if (!this.dataSource.has(file)) {
			return false;
		}
		changeset.load(new LittleEndianDataInputStream(this.dataSource.getResourceAsStream(file)), wts,
				this.inlineWTS);
		return true;
	}

	private void moveItems(final ObjectMap unitEntries, final ObjectMap itemEntries,
			final WarcraftData standardUnits, final WarcraftData standardItems) {
		final Iterator<Entry<War3ID, ObjectDataChangeEntry>> entryIterator = unitEntries.iterator();
		while (entryIterator.hasNext()) {
			final Entry<War3ID, ObjectDataChangeEntry> entry = entryIterator.next();
			final String oldIdString = entry.getValue().getOldId().toString();
			if ((standardUnits.get(oldIdString) == null) && (standardItems.get(oldIdString) != null)) {
				itemEntries.put(entry.getKey(), entry.getValue());
				entryIterator.remove();
			}
		}
	}
}
