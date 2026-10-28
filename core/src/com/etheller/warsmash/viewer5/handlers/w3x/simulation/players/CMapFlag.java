package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

import com.etheller.interpreter.ast.util.CHandle;

public enum CMapFlag implements CHandle {
	// The ids are common.j's; 64 is not used, so they do not follow the declaration order.
	MAP_FOG_HIDE_TERRAIN(1),
	MAP_FOG_MAP_EXPLORED(2),
	MAP_FOG_ALWAYS_VISIBLE(4),

	MAP_USE_HANDICAPS(8),
	MAP_OBSERVERS(16),
	MAP_OBSERVERS_ON_DEATH(32),

	MAP_FIXED_COLORS(128),

	MAP_LOCK_RESOURCE_TRADING(256),
	MAP_RESOURCE_TRADING_ALLIES_ONLY(512),

	MAP_LOCK_ALLIANCE_CHANGES(1024),
	MAP_ALLIANCE_CHANGES_HIDDEN(2048),

	MAP_CHEATS(4096),
	MAP_CHEATS_HIDDEN(8192),

	MAP_LOCK_SPEED(8192 * 2),
	MAP_LOCK_RANDOM_SEED(8192 * 4),
	MAP_SHARED_ADVANCED_CONTROL(8192 * 8),
	MAP_RANDOM_HERO(8192 * 16),
	MAP_RANDOM_RACES(8192 * 32),
	MAP_RELOADED(8192 * 64);

	private final int id;

	CMapFlag(final int id) {
		this.id = id;
	}

	public static CMapFlag[] VALUES = values();

	public static CMapFlag getById(final int id) {
		for (final CMapFlag type : VALUES) {
			if ((type.getId()) == id) {
				return type;
			}
		}
		return null;
	}

	public int getId() {
		return this.id;
	}

	@Override
	public int getHandleId() {
		return getId();
	}
}
