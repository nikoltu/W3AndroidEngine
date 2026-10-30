package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A game cache handle. Measured on 1.29: every InitGameCache call makes a new handle, handles of one file name share
 * the data, keys ignore case and each value type has its own keys; FlushGameCache empties the file and leaves the
 * handle dead (stores through it are lost).
 *
 * The data lives as long as the process, which is what carries it from one mission to the next. It is not written to
 * disk: that belongs with saved games (D-100).
 */
public final class CGameCache {
	private static final Map<String, Data> FILES = new HashMap<>();

	private final String fileName;
	private Data data;

	public CGameCache(final String fileName) {
		this.fileName = fileName.toLowerCase(Locale.ROOT);
		this.data = FILES.computeIfAbsent(this.fileName, key -> new Data());
	}

	public void flush() {
		if (this.data != null) {
			this.data.dead = true;
			FILES.remove(this.fileName);
		}
		this.data = null;
	}

	public void flushMission(final String mission) {
		if (live()) {
			final String key = key(mission);
			this.data.integers.remove(key);
			this.data.reals.remove(key);
			this.data.booleans.remove(key);
			this.data.strings.remove(key);
			this.data.units.remove(key);
		}
	}

	public void storeInteger(final String mission, final String key, final int value) {
		if (live()) {
			put(this.data.integers, mission, key, value);
		}
	}

	public void storeReal(final String mission, final String key, final float value) {
		if (live()) {
			put(this.data.reals, mission, key, value);
		}
	}

	public void storeBoolean(final String mission, final String key, final boolean value) {
		if (live()) {
			put(this.data.booleans, mission, key, value);
		}
	}

	public boolean storeString(final String mission, final String key, final String value) {
		if (live()) {
			put(this.data.strings, mission, key, value);
			return true;
		}
		return false;
	}

	public boolean storeUnit(final String mission, final String key, final CStoredUnit value) {
		if (live()) {
			put(this.data.units, mission, key, value);
			return true;
		}
		return false;
	}

	public Integer getInteger(final String mission, final String key) {
		return live() ? get(this.data.integers, mission, key) : null;
	}

	public Float getReal(final String mission, final String key) {
		return live() ? get(this.data.reals, mission, key) : null;
	}

	public Boolean getBoolean(final String mission, final String key) {
		return live() ? get(this.data.booleans, mission, key) : null;
	}

	public String getString(final String mission, final String key) {
		return live() ? get(this.data.strings, mission, key) : null;
	}

	public CStoredUnit getUnit(final String mission, final String key) {
		return live() ? get(this.data.units, mission, key) : null;
	}

	public void flushInteger(final String mission, final String key) {
		if (live()) {
			remove(this.data.integers, mission, key);
		}
	}

	public void flushReal(final String mission, final String key) {
		if (live()) {
			remove(this.data.reals, mission, key);
		}
	}

	public void flushBoolean(final String mission, final String key) {
		if (live()) {
			remove(this.data.booleans, mission, key);
		}
	}

	public void flushString(final String mission, final String key) {
		if (live()) {
			remove(this.data.strings, mission, key);
		}
	}

	public void flushUnit(final String mission, final String key) {
		if (live()) {
			remove(this.data.units, mission, key);
		}
	}

	private boolean live() {
		return (this.data != null) && !this.data.dead;
	}

	private static String key(final String text) {
		return text == null ? "" : text.toLowerCase(Locale.ROOT);
	}

	private static <T> void put(final Map<String, Map<String, T>> values, final String mission, final String key,
			final T value) {
		values.computeIfAbsent(key(mission), ignored -> new HashMap<>()).put(key(key), value);
	}

	private static <T> T get(final Map<String, Map<String, T>> values, final String mission, final String key) {
		final Map<String, T> missionValues = values.get(key(mission));
		return missionValues == null ? null : missionValues.get(key(key));
	}

	private static <T> void remove(final Map<String, Map<String, T>> values, final String mission, final String key) {
		final Map<String, T> missionValues = values.get(key(mission));
		if (missionValues != null) {
			missionValues.remove(key(key));
		}
	}

	private static final class Data {
		private final Map<String, Map<String, Integer>> integers = new HashMap<>();
		private final Map<String, Map<String, Float>> reals = new HashMap<>();
		private final Map<String, Map<String, Boolean>> booleans = new HashMap<>();
		private final Map<String, Map<String, String>> strings = new HashMap<>();
		private final Map<String, Map<String, CStoredUnit>> units = new HashMap<>();
		private boolean dead;
	}
}
