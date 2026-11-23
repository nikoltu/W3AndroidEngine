package com.etheller.warsmash.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CSaveStorage;

/**
 * The single player profiles. Their names and the current one stay where the engine keeps them (its libGDX
 * preferences, read by PlayerProfileManager), so the engine's own menu sees the same list; what only the launcher
 * shows (portrait, creation time, a fixed id) is kept beside them in the launcher's preferences. A profile's saves,
 * campaign progress and statistics are in Save/&lt;profile&gt;, as the engine has them.
 */
public class Profiles {
	/** The libGDX preferences the engine keeps its profiles in. */
	public static final String ENGINE_PREFERENCES = "WarsmashWC3Engine";
	private static final String CURRENT_PROFILE = "CurrentProfile";
	private static final String PROFILE_COUNT = "ProfileCount";
	/** The profile the engine makes for itself when it finds none. */
	public static final String ENGINE_DEFAULT = "WorldEdit";
	public static final int MAX_NAME = 15;
	private static final String STATS_FILE = "stats";
	/** What the launcher keeps of a profile in its own preferences, by field. */
	static final String[] FIELDS = { "id", "portrait", "created", "used", "campaignmaps" };

	/** Why a typed name cannot be used. */
	public enum NameProblem {
		NONE, EMPTY, TAKEN, CHARACTERS
	}

	private final LauncherStore engine;
	private final LauncherStore launcher;
	private final File saves;

	/**
	 * @param engine   the engine's preferences, with its profile list
	 * @param launcher the launcher's preferences
	 * @param saves    the engine's Save folder, which has a folder per profile
	 */
	public Profiles(final LauncherStore engine, final LauncherStore launcher, final File saves) {
		this.engine = engine;
		this.launcher = launcher;
		this.saves = saves;
	}

	/** The folder of a profile's saves and progress below the engine's Save folder. */
	public static File folder(final File saves, final String name) {
		final String safe = CSaveStorage.safeName(name);
		return new File(saves, safe.isEmpty() ? "Player" : safe);
	}

	public List<String> names() {
		final int count = this.engine.getInt(PROFILE_COUNT, 0);
		final List<String> names = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			final String name = this.engine.getString("Profile" + i + "_Name", null);
			if ((name != null) && !name.isEmpty()) {
				names.add(name);
			}
		}
		return names;
	}

	/**
	 * Whether the player has a profile of their own. The one the engine makes for itself does not count while it
	 * holds nothing and the launcher never showed it.
	 */
	public boolean any() {
		final List<String> names = names();
		if (names.isEmpty()) {
			return false;
		}
		return (names.size() > 1) || !isPlaceholder(names.get(0));
	}

	private boolean isPlaceholder(final String name) {
		return ENGINE_DEFAULT.equals(name) && !this.launcher.contains(key(name, "id"))
				&& !folder(name).exists();
	}

	/** The current profile, as the engine starts with it; the first of the list when that one is gone. */
	public String current() {
		final List<String> names = names();
		final String current = this.engine.getString(CURRENT_PROFILE, ENGINE_DEFAULT);
		if (names.contains(current) || names.isEmpty()) {
			return current;
		}
		return names.get(0);
	}

	public void select(final String name) {
		this.engine.edit().putString(CURRENT_PROFILE, name).commit();
		this.launcher.edit().putLong(key(name, "used"), System.currentTimeMillis()).apply();
		changed();
	}

	public NameProblem check(final String typed, final String renaming) {
		final String name = typed.trim();
		if (name.isEmpty()) {
			return NameProblem.EMPTY;
		}
		if ((name.length() > MAX_NAME) || !CSaveStorage.safeName(name).equals(name)) {
			return NameProblem.CHARACTERS;
		}
		for (final String other : names()) {
			if (!other.equals(renaming) && CSaveStorage.safeName(other).equalsIgnoreCase(name)) {
				return NameProblem.TAKEN;
			}
		}
		return NameProblem.NONE;
	}

	/** A new profile; the engine's untouched placeholder gives way to the first one, which becomes current. */
	public void create(final String typed, final int portrait, final boolean current) {
		final String name = typed.trim();
		final List<String> names = names();
		boolean select = current;
		if ((names.size() == 1) && isPlaceholder(names.get(0))) {
			names.clear();
			select = true;
		}
		names.add(name);
		writeNames(names);
		final byte[] id = new byte[8];
		new SecureRandom().nextBytes(id);
		final StringBuilder hex = new StringBuilder();
		for (final byte b : id) {
			hex.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
		}
		final long now = System.currentTimeMillis();
		this.launcher.edit().putString(key(name, "id"), hex.toString()).putInt(key(name, "portrait"), portrait)
				.putLong(key(name, "created"), now).apply();
		if (select) {
			select(name);
		}
		changed();
	}

	/** Renames the profile and its folder; the folder keeps its saves and progress under the new name. */
	public boolean rename(final String old, final String typed) {
		final String name = typed.trim();
		final File from = folder(old);
		final File to = folder(name);
		if (from.exists() && !from.renameTo(to)) {
			LauncherLog.w("Unable to rename " + from);
			return false;
		}
		final List<String> names = names();
		names.set(names.indexOf(old), name);
		writeNames(names);
		final LauncherStore.Edit editor = this.launcher.edit();
		for (final String field : FIELDS) {
			final String oldKey = key(old, field);
			if (!this.launcher.contains(oldKey)) {
				continue;
			}
			final Object value = this.launcher.get(oldKey);
			if (value instanceof String) {
				editor.putString(key(name, field), (String) value);
			}
			else if (value instanceof Integer) {
				editor.putInt(key(name, field), (Integer) value);
			}
			else if (value instanceof Long) {
				editor.putLong(key(name, field), (Long) value);
			}
			editor.remove(oldKey);
		}
		editor.apply();
		if (old.equals(this.engine.getString(CURRENT_PROFILE, null))) {
			this.engine.edit().putString(CURRENT_PROFILE, name).commit();
		}
		changed();
		return true;
	}

	/** Deletes the profile with its saves, campaign progress and statistics; the last one cannot go. */
	public void delete(final String name) {
		final List<String> names = names();
		if ((names.size() <= 1) || !names.remove(name)) {
			return;
		}
		writeNames(names);
		deleteTree(folder(name));
		final LauncherStore.Edit editor = this.launcher.edit();
		for (final String field : FIELDS) {
			editor.remove(key(name, field));
		}
		editor.apply();
		if (name.equals(this.engine.getString(CURRENT_PROFILE, null))) {
			select(names.get(0));
		}
		changed();
	}

	public int portrait(final String name) {
		return this.launcher.getInt(key(name, "portrait"), 0);
	}

	public void setPortrait(final String name, final int portrait) {
		this.launcher.edit().putInt(key(name, "portrait"), portrait).apply();
		changed();
	}

	/** The version of the campaign maps the profile plays, as {@link LaunchContract#EXTRA_CAMPAIGN_MAPS} names it. */
	public String campaignMaps(final String name) {
		return this.launcher.getString(campaignMapsKey(name), DEFAULT_CAMPAIGN_MAPS);
	}

	/** What a profile plays the campaigns in until it chooses. */
	public static final String DEFAULT_CAMPAIGN_MAPS = LaunchRequest.CAMPAIGN_MAPS_REFORGED;

	/** The launcher preferences' key of a profile's campaign map version, which the engine's own menu reads too. */
	public static String campaignMapsKey(final String name) {
		return key(name, "campaignmaps");
	}

	public void setCampaignMaps(final String name, final String maps) {
		this.launcher.edit().putString(key(name, "campaignmaps"), maps).apply();
		changed();
	}

	/** When the profile was made; 0 for a profile the engine's menu made. */
	public long created(final String name) {
		return this.launcher.getLong(key(name, "created"), 0);
	}

	public File folder(final String name) {
		return folder(this.saves, name);
	}

	public int savedGames(final String name) {
		final File[] files = folder(name).listFiles();
		int count = 0;
		if (files != null) {
			for (final File file : files) {
				if (file.getName().toLowerCase(Locale.ROOT).endsWith(CSaveStorage.SAVE_EXTENSION)) {
					count++;
				}
			}
		}
		return count;
	}

	/** A finished game of the profile for its statistics: the race is the lobby's race index (0 random). */
	public void record(final String name, final String result, final int race) {
		final File file = new File(folder(name), STATS_FILE);
		final String line = System.currentTimeMillis() + "\t" + result + "\t" + race + "\n";
		try {
			file.getParentFile().mkdirs();
			Files.write(file.toPath(), line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
		}
		catch (final IOException e) {
			LauncherLog.w("Unable to record the game in the statistics", e);
		}
	}

	/** Games and wins per race index (0 random to 4 night elf) of the custom and LAN games played. */
	public int[][] statistics(final String name, final int races) {
		final int[][] counts = new int[races][2];
		final File file = new File(folder(name), STATS_FILE);
		if (!file.isFile()) {
			return counts;
		}
		try {
			for (final String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
				final String[] parts = line.split("\t");
				if (parts.length < 3) {
					continue;
				}
				final int race = Integer.parseInt(parts[2]);
				if ((race < 0) || (race >= races)) {
					continue;
				}
				counts[race][0]++;
				if ("victory".equals(parts[1])) {
					counts[race][1]++;
				}
			}
		}
		catch (final IOException | RuntimeException e) {
			LauncherLog.w("Unable to read the statistics", e);
		}
		return counts;
	}

	/** After every change to the list or to what the launcher keeps of a profile; the platform may copy it elsewhere. */
	protected void changed() {
	}

	LauncherStore engineStore() {
		return this.engine;
	}

	LauncherStore launcherStore() {
		return this.launcher;
	}

	void writeNames(final List<String> names) {
		final int old = this.engine.getInt(PROFILE_COUNT, 0);
		final LauncherStore.Edit editor = this.engine.edit();
		for (int i = 0; i < Math.max(old, names.size()); i++) {
			if (i < names.size()) {
				editor.putString("Profile" + i + "_Name", names.get(i));
			}
			else {
				editor.remove("Profile" + i + "_Name");
			}
		}
		editor.putInt(PROFILE_COUNT, names.size());
		editor.commit();
	}

	static String key(final String name, final String field) {
		return "profile." + field + "." + name;
	}

	private static void deleteTree(final File file) {
		final File[] children = file.listFiles();
		if (children != null) {
			for (final File child : children) {
				deleteTree(child);
			}
		}
		if (file.exists() && !file.delete()) {
			LauncherLog.w("Unable to delete " + file);
		}
	}
}
