package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Where the profiles are kept, how they are copied there, and how a lost profile list comes back. */
class ProfileStorageTest {
	@TempDir
	File temp;

	@AfterEach
	void reset() {
		StorageRoot.set(null);
	}

	private static final class MemoryStore implements LauncherStore {
		final Map<String, Object> values = new HashMap<>();

		@Override
		public String getString(final String key, final String fallback) {
			final Object value = this.values.get(key);
			return value instanceof String ? (String) value : fallback;
		}

		@Override
		public int getInt(final String key, final int fallback) {
			final Object value = this.values.get(key);
			return value instanceof Integer ? (Integer) value : fallback;
		}

		@Override
		public long getLong(final String key, final long fallback) {
			final Object value = this.values.get(key);
			return value instanceof Long ? (Long) value : fallback;
		}

		@Override
		public boolean getBoolean(final String key, final boolean fallback) {
			final Object value = this.values.get(key);
			return value instanceof Boolean ? (Boolean) value : fallback;
		}

		@Override
		public boolean contains(final String key) {
			return this.values.containsKey(key);
		}

		@Override
		public Object get(final String key) {
			return this.values.get(key);
		}

		@Override
		public Edit edit() {
			return new Edit() {
				@Override
				public Edit putString(final String key, final String value) {
					MemoryStore.this.values.put(key, value);
					return this;
				}

				@Override
				public Edit putInt(final String key, final int value) {
					MemoryStore.this.values.put(key, value);
					return this;
				}

				@Override
				public Edit putLong(final String key, final long value) {
					MemoryStore.this.values.put(key, value);
					return this;
				}

				@Override
				public Edit putBoolean(final String key, final boolean value) {
					MemoryStore.this.values.put(key, value);
					return this;
				}

				@Override
				public Edit remove(final String key) {
					MemoryStore.this.values.remove(key);
					return this;
				}

				@Override
				public void commit() {
				}

				@Override
				public void apply() {
				}
			};
		}
	}

	private static void write(final File file, final String text) throws IOException {
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private static String read(final File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	@Test
	void theSharedFolderIsChosenOnlyWhenWantedAndUsable() {
		final File shared = ProfileStorage.sharedFolder(new File(this.temp, "sdcard"));
		assertEquals(new File(this.temp, "sdcard/WC3-Android/Profiles"), shared);
		assertEquals(shared, ProfileStorage.choose(true, shared, true));
		assertNull(ProfileStorage.choose(false, shared, true));
		assertNull(ProfileStorage.choose(true, shared, false));
		assertNull(ProfileStorage.choose(true, null, true));
	}

	@Test
	void writableMakesTheFolderAndLeavesNoProbe() {
		final File folder = new File(this.temp, "a/b");
		assertTrue(ProfileStorage.writable(folder));
		assertTrue(folder.isDirectory());
		assertEquals(0, folder.list().length);
		assertFalse(ProfileStorage.writable(null));
	}

	@Test
	void theStorageRootDefaultsToTheEnginesOwnAndFollowsTheFolderGiven() {
		assertNull(StorageRoot.root());
		StorageRoot.set(this.temp);
		assertEquals(this.temp, StorageRoot.root());
	}

	@Test
	void copyingMovesNewFilesAndKeepsTheSource() throws IOException {
		final File from = new File(this.temp, "private/Save");
		final File to = new File(this.temp, "shared/Save");
		write(new File(from, "Ann/progress.dat"), "one");
		write(new File(from, "Ann/slot.w3z"), "two");
		final ProfileStorage.Result result = ProfileStorage.copyTree(from, to, new File(this.temp, "c"));
		assertTrue(result.ok());
		assertEquals(2, result.copied);
		assertEquals("one", read(new File(to, "Ann/progress.dat")));
		assertEquals("two", read(new File(to, "Ann/slot.w3z")));
		assertTrue(new File(from, "Ann/progress.dat").isFile());
		assertFalse(new File(to, "Ann/progress.dat.part").exists());
	}

	@Test
	void copyingTwiceChangesNothing() throws IOException {
		final File from = new File(this.temp, "private/Save");
		final File to = new File(this.temp, "shared/Save");
		write(new File(from, "Ann/a"), "x");
		ProfileStorage.copyTree(from, to, new File(this.temp, "c"));
		final ProfileStorage.Result again = ProfileStorage.copyTree(from, to, new File(this.temp, "c"));
		assertTrue(again.ok());
		assertEquals(0, again.copied);
		assertEquals(1, again.skipped);
		assertEquals(0, again.conflicts);
	}

	@Test
	void anInterruptedCopyIsFinishedAndAHalfFileIsNotTaken() throws IOException {
		final File from = new File(this.temp, "private/Save");
		final File to = new File(this.temp, "shared/Save");
		write(new File(from, "Ann/a"), "whole");
		write(new File(to, "Ann/a.part"), "wh");
		final ProfileStorage.Result result = ProfileStorage.copyTree(from, to, new File(this.temp, "c"));
		assertTrue(result.ok());
		assertEquals("whole", read(new File(to, "Ann/a")));
		assertFalse(new File(to, "Ann/a.part").exists());
	}

	@Test
	void aDifferentFileAtTheTargetIsNeverOverwrittenButKeptApart() throws IOException {
		final File from = new File(this.temp, "private/Save");
		final File to = new File(this.temp, "shared/Save");
		final File conflicts = new File(this.temp, "shared/Conflicts/1/Save");
		write(new File(from, "Ann/a"), "device");
		write(new File(to, "Ann/a"), "shared");
		final ProfileStorage.Result result = ProfileStorage.copyTree(from, to, conflicts);
		assertTrue(result.ok());
		assertEquals(1, result.conflicts);
		assertEquals("shared", read(new File(to, "Ann/a")));
		assertEquals("device", read(new File(conflicts, "Ann/a")));
		final ProfileStorage.Result again = ProfileStorage.copyTree(from, to, conflicts);
		assertTrue(again.ok());
		assertEquals(1, again.conflicts);
	}

	@Test
	void aSourceThatIsMissingCopiesNothing() {
		final ProfileStorage.Result result = ProfileStorage.copyTree(new File(this.temp, "none"),
				new File(this.temp, "to"), new File(this.temp, "c"));
		assertTrue(result.ok());
		assertEquals(0, result.copied);
	}

	@Test
	void everyChangeReachesTheCopyAtOnce() {
		final File shared = new File(this.temp, "shared");
		final Profiles profiles = new Profiles(new MemoryStore(), new MemoryStore(), new File(shared, "Save")) {
			@Override
			protected void changed() {
				ProfileMirror.write(this, shared);
			}
		};
		profiles.create("Arthas", 2, true);
		assertEquals(Arrays.asList("Arthas"), ProfileMirror.read(shared).names);
		profiles.create("Jaina", 1, false);
		assertTrue(profiles.rename("Jaina", "Kael"));
		assertEquals(Arrays.asList("Arthas", "Kael"), ProfileMirror.read(shared).names);
		profiles.delete("Kael");
		assertEquals(Arrays.asList("Arthas"), ProfileMirror.read(shared).names);
		final Profiles again = profiles(new MemoryStore(), new MemoryStore(), new File(shared, "Save"));
		assertEquals(1, ProfileMirror.restore(again, shared));
		assertEquals("Arthas", again.current());
	}

	private static Profiles profiles(final MemoryStore engine, final MemoryStore launcher, final File saves) {
		return new Profiles(engine, launcher, saves);
	}

	@Test
	void theProfileListIsWrittenAndComesBackAfterTheStoresAreLost() throws IOException {
		final File shared = new File(this.temp, "shared");
		final MemoryStore engine = new MemoryStore();
		final MemoryStore launcher = new MemoryStore();
		final Profiles profiles = profiles(engine, launcher, new File(shared, "Save"));
		profiles.create("Arthas", 3, true);
		profiles.create("Jaina", 5, false);
		profiles.setCampaignMaps("Jaina", LaunchRequest.CAMPAIGN_MAPS_CLASSIC);
		write(new File(shared, "Save/Arthas/campaign.dat"), "medals");
		assertTrue(ProfileMirror.write(profiles, shared));
		assertTrue(new File(shared, ProfileMirror.FILE).isFile());

		// An uninstall: new, empty stores; the shared folder stays.
		final MemoryStore engine2 = new MemoryStore();
		final MemoryStore launcher2 = new MemoryStore();
		final Profiles again = profiles(engine2, launcher2, new File(shared, "Save"));
		assertFalse(again.any());
		assertEquals(2, ProfileMirror.restore(again, shared));
		assertEquals(Arrays.asList("Arthas", "Jaina"), again.names());
		assertEquals("Arthas", again.current());
		assertEquals(3, again.portrait("Arthas"));
		assertEquals(5, again.portrait("Jaina"));
		assertEquals(LaunchRequest.CAMPAIGN_MAPS_CLASSIC, again.campaignMaps("Jaina"));
		assertEquals(profiles.created("Arthas"), again.created("Arthas"));
		assertTrue(again.folder("Arthas").isDirectory());
	}

	@Test
	void theCurrentProfileComesBackToo() {
		final File shared = new File(this.temp, "shared");
		final Profiles profiles = profiles(new MemoryStore(), new MemoryStore(), new File(shared, "Save"));
		profiles.create("Arthas", 0, true);
		profiles.create("Jaina", 1, true);
		ProfileMirror.write(profiles, shared);
		final Profiles again = profiles(new MemoryStore(), new MemoryStore(), new File(shared, "Save"));
		ProfileMirror.restore(again, shared);
		assertEquals("Jaina", again.current());
	}

	@Test
	void aFreshStartDoesNotReplaceTheCopy() {
		final File shared = new File(this.temp, "shared");
		final Profiles profiles = profiles(new MemoryStore(), new MemoryStore(), new File(shared, "Save"));
		profiles.create("Arthas", 0, true);
		assertTrue(ProfileMirror.write(profiles, shared));
		final MemoryStore engine = new MemoryStore();
		engine.edit().putInt("ProfileCount", 1).putString("Profile0_Name", Profiles.ENGINE_DEFAULT);
		final Profiles fresh = profiles(engine, new MemoryStore(), new File(shared, "Save"));
		assertFalse(fresh.any());
		assertFalse(ProfileMirror.write(fresh, shared));
		assertEquals(1, ProfileMirror.read(shared).names.size());
	}

	@Test
	void theEnginesPlaceholderGivesWayToTheRestoredProfiles() {
		final File shared = new File(this.temp, "shared");
		final Profiles profiles = profiles(new MemoryStore(), new MemoryStore(), new File(shared, "Save"));
		profiles.create("Arthas", 0, true);
		ProfileMirror.write(profiles, shared);
		final MemoryStore engine = new MemoryStore();
		engine.edit().putInt("ProfileCount", 1).putString("Profile0_Name", Profiles.ENGINE_DEFAULT);
		final Profiles fresh = profiles(engine, new MemoryStore(), new File(shared, "Save"));
		assertEquals(1, ProfileMirror.restore(fresh, shared));
		assertEquals(Arrays.asList("Arthas"), fresh.names());
	}

	@Test
	void profilesOfThisDeviceAreKeptAndOnlyMissingOnesAreAdded() {
		final File shared = new File(this.temp, "shared");
		final Profiles old = profiles(new MemoryStore(), new MemoryStore(), new File(shared, "Save"));
		old.create("Arthas", 2, true);
		old.create("Jaina", 4, false);
		ProfileMirror.write(old, shared);
		final Profiles device = profiles(new MemoryStore(), new MemoryStore(), new File(shared, "Save"));
		device.create("jaina", 7, true);
		assertEquals(1, ProfileMirror.restore(device, shared));
		final List<String> names = device.names();
		assertEquals(Arrays.asList("jaina", "Arthas"), names);
		assertEquals("jaina", device.current());
		assertEquals(7, device.portrait("jaina"));
		assertEquals(2, device.portrait("Arthas"));
		assertEquals(0, ProfileMirror.restore(device, shared));
	}

	@Test
	void withoutTheCopyTheSaveFoldersNameTheProfiles() throws IOException {
		final File shared = new File(this.temp, "shared");
		write(new File(shared, "Save/Thrall/progress"), "x");
		write(new File(shared, "Save/Anduin/progress"), "y");
		new File(shared, "Save/Empty").mkdirs();
		final Profiles fresh = profiles(new MemoryStore(), new MemoryStore(), new File(shared, "Save"));
		assertEquals(2, ProfileMirror.restore(fresh, shared));
		assertEquals(Arrays.asList("Anduin", "Thrall"), fresh.names());
		assertEquals("Anduin", fresh.current());
	}

	@Test
	void aBrokenCopyFallsBackToTheFolders() throws IOException {
		final File shared = new File(this.temp, "shared");
		write(new File(shared, ProfileMirror.FILE), "not a list");
		write(new File(shared, "Save/Thrall/progress"), "x");
		final Profiles fresh = profiles(new MemoryStore(), new MemoryStore(), new File(shared, "Save"));
		assertEquals(1, ProfileMirror.restore(fresh, shared));
		assertEquals(Arrays.asList("Thrall"), fresh.names());
	}

	@Test
	void nothingToRestoreChangesNothing() {
		final Profiles fresh = profiles(new MemoryStore(), new MemoryStore(), new File(this.temp, "Save"));
		assertEquals(0, ProfileMirror.restore(fresh, new File(this.temp, "nowhere")));
		assertTrue(fresh.names().isEmpty());
	}

	@Test
	void namesWithOddCharactersSurviveTheCopy() {
		final File shared = new File(this.temp, "shared");
		final Profiles profiles = profiles(new MemoryStore(), new MemoryStore(), new File(shared, "Save"));
		profiles.create("Žaidėjas Ą", 1, true);
		ProfileMirror.write(profiles, shared);
		final Profiles again = profiles(new MemoryStore(), new MemoryStore(), new File(shared, "Save"));
		ProfileMirror.restore(again, shared);
		assertEquals(Arrays.asList("Žaidėjas Ą"), again.names());
	}

	@Test
	void everyChangeOfTheListIsReportedToThePlatform() {
		final int[] changes = { 0 };
		final Profiles profiles = new Profiles(new MemoryStore(), new MemoryStore(), new File(this.temp, "Save")) {
			@Override
			protected void changed() {
				changes[0]++;
			}
		};
		profiles.create("Arthas", 0, true);
		final int afterCreate = changes[0];
		assertTrue(afterCreate > 0);
		profiles.create("Jaina", 0, false);
		profiles.setPortrait("Jaina", 2);
		profiles.rename("Jaina", "Sylvanas");
		profiles.delete("Sylvanas");
		assertTrue(changes[0] >= afterCreate + 4);
	}

	@Test
	void hasFilesSeesOnlyRealFiles() throws IOException {
		assertFalse(ProfileStorage.hasFiles(this.temp));
		Files.createDirectories(new File(this.temp, "Save/Player/Sub").toPath());
		Files.createDirectories(new File(this.temp, "Replay").toPath());
		assertFalse(ProfileStorage.hasFiles(this.temp));
		Files.write(new File(this.temp, "Other.txt").toPath(), new byte[] { 1 });
		assertFalse(ProfileStorage.hasFiles(this.temp));
		Files.write(new File(this.temp, "Save/Player/Sub/a.w3z").toPath(), new byte[] { 1 });
		assertTrue(ProfileStorage.hasFiles(this.temp));
	}
}
