package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.launcher.DataContentCheck.Problem;

/** The launcher tells data that was found but lacks scripts or maps, such as data prepared with "Melee only". */
class DataContentCheckTest {
	@TempDir
	File temp;

	private static final String LIST = "[Index]\r\nCampaignList=\"Human\",\"Orc\"\r\n"
			+ "[Orc]\r\nHeader=\"Orc\"\r\nName=\"Orc\"\r\nDefaultOpen=1\r\n"
			+ "[Human]\r\nHeader=\"Human\"\r\nName=\"The Scourge of Lordaeron\"\r\nDefaultOpen=1\r\n"
			+ "Mission0=\"Chapter One\",\"Hill\",\"Campaign\\Human01.w3m\"\r\n"
			+ "Mission1=\"Chapter Two\",\"Wood\",\"Campaign\\Human02.w3m\"\r\n";

	/** Game data held in memory, found by lower case path like the real sources. */
	private static final class FakeData implements DataSource {
		final Map<String, byte[]> files = new HashMap<>();

		FakeData with(final String path, final String text) {
			this.files.put(path.toLowerCase(Locale.ROOT), text.getBytes(StandardCharsets.UTF_8));
			return this;
		}

		@Override
		public InputStream getResourceAsStream(final String filepath) {
			final byte[] bytes = this.files.get(filepath.toLowerCase(Locale.ROOT));
			return bytes == null ? null : new ByteArrayInputStream(bytes);
		}

		@Override
		public File getFile(final String filepath) {
			return null;
		}

		@Override
		public File getDirectory(final String filepath) {
			return null;
		}

		@Override
		public ByteBuffer read(final String path) {
			final byte[] bytes = this.files.get(path.toLowerCase(Locale.ROOT));
			return bytes == null ? null : ByteBuffer.wrap(bytes);
		}

		@Override
		public boolean has(final String filepath) {
			return this.files.containsKey(filepath.toLowerCase(Locale.ROOT));
		}

		@Override
		public Collection<String> getListfile() {
			return new ArrayList<>(this.files.keySet());
		}

		@Override
		public void close() {
		}
	}

	private static FakeData complete() {
		return new FakeData().with("Scripts\\common.j", "x").with("Scripts\\Blizzard.j", "x")
				.with("UI\\war3skins.txt", "x").with("UI\\MiscData.txt", "x").with("Units\\CommandFunc.txt", "x")
				.with("Units\\CommandStrings.txt", "x").with("UI\\CampaignInfo.txt", LIST).with("Campaign\\Human01.w3m", "m")
				.with("Maps\\Melee\\(2)Lost Temple.w3m", "m");
	}

	@Test
	void completeDataHasNoProblem() {
		assertEquals(Collections.emptyList(), DataContentCheck.check(complete(), (String) null));
	}

	@Test
	void dataPreparedWithMeleeOnlyHasNoCampaignMaps() {
		final FakeData data = complete();
		data.files.remove("campaign\\human01.w3m");
		assertEquals(Arrays.asList(Problem.NO_CAMPAIGN_MAPS), DataContentCheck.check(data, "UI\\CampaignInfo.txt"));
	}

	@Test
	void anyOneCampaignMapIsEnough() {
		final FakeData data = complete();
		data.files.remove("campaign\\human01.w3m");
		data.with("Campaign\\Human02.w3m", "m");
		assertEquals(Collections.emptyList(), DataContentCheck.check(data, "UI\\CampaignInfo.txt"));
	}

	@Test
	void anEncryptedCampaignMapDoesNotCount() {
		final FakeData data = complete();
		data.files.remove("campaign\\human01.w3m");
		data.with("Campaign\\Human01.w3xd", "m");
		assertEquals(Arrays.asList(Problem.NO_CAMPAIGN_MAPS), DataContentCheck.check(data, "UI\\CampaignInfo.txt"));
	}

	@Test
	void missingScriptsAreToldOnce() {
		final FakeData data = complete();
		data.files.remove("scripts\\common.j");
		data.files.remove("scripts\\blizzard.j");
		assertEquals(Arrays.asList(Problem.SCRIPTS), DataContentCheck.check(data, "UI\\CampaignInfo.txt"));
		data.with("Scripts\\common.j", "x");
		assertEquals(Arrays.asList(Problem.SCRIPTS), DataContentCheck.check(data, "UI\\CampaignInfo.txt"));
	}

	@Test
	void missingUiFilesAreToldOnceAndNamed() {
		final FakeData data = complete();
		data.files.remove("ui\\war3skins.txt");
		data.files.remove("units\\commandfunc.txt");
		assertEquals(Arrays.asList(Problem.REQUIRED_FILES), DataContentCheck.check(data, "UI\\CampaignInfo.txt"));
		assertEquals("UI\\war3skins.txt",DataContentCheck.missingRequiredFile(data));
		assertEquals(null, DataContentCheck.missingRequiredFile(complete()));
	}

	@Test
	void scriptsAreNamedFirstWhenTheyAreMissingToo() {
		final FakeData data = complete();
		data.files.remove("scripts\\common.j");
		assertEquals("Scripts\\common.j", DataContentCheck.missingRequiredFile(data));
		assertEquals(Arrays.asList(Problem.SCRIPTS), DataContentCheck.check(data, "UI\\CampaignInfo.txt"));
	}

	@Test
	void anExtractedFolderWithoutItsUiAndScriptsFoldersIsNamed() throws IOException {
		for (final String file : Arrays.asList("ui/WorldEditGameStrings.txt", "units/UnitData.slk")) {
			final File target = new File(this.temp, file);
			target.getParentFile().mkdirs();
			Files.write(target.toPath(), new byte[] { 1 });
		}
		assertEquals(DataFolderCheck.Kind.FOLDER, DataFolderCheck.detect(this.temp));
		assertEquals("Scripts\\common.j",
				DataContentCheck.missingRequiredFile(DataFolderCheck.Kind.FOLDER, this.temp));
		assertEquals(null, DataContentCheck.missingRequiredFile(DataFolderCheck.Kind.CASC, this.temp));
	}

	@Test
	void dataWithoutMeleeMapsOrCampaignListIsTold() {
		final FakeData data = new FakeData().with("Scripts\\common.j", "x").with("Scripts\\Blizzard.j", "x")
				.with("Maps\\Custom\\Arena.w3x", "m");
		assertEquals(Arrays.asList(Problem.REQUIRED_FILES, Problem.NO_CAMPAIGN_LIST, Problem.NO_MELEE_MAPS),
				DataContentCheck.check(data, (String) null));
	}

	@Test
	void everyProblemHasItsTextInTheLauncherStrings() throws IOException {
		final LauncherStrings strings;
		try (InputStream in = Files.newInputStream(new File("../android/res/values/strings.xml").toPath())) {
			strings = new LauncherStrings(in);
		}
		for (final Problem problem : Problem.values()) {
			assertEquals(false, strings.get(problem.message).equals(problem.message));
		}
		assertEquals("No campaign maps in the game data \u2013 was it prepared with 'Melee only'?",
				strings.get(Problem.NO_CAMPAIGN_MAPS.message));
	}

	@Test
	void anExtractedFolderIsCheckedByItsFiles() throws IOException {
		final List<String> files = Arrays.asList("ui/WorldEditGameStrings.txt", "units/UnitData.slk",
				"Scripts/common.j", "Scripts/Blizzard.j", "UI/war3skins.txt", "UI/MiscData.txt", "Units/CommandFunc.txt",
				"Units/CommandStrings.txt", "UI/CampaignInfo.txt", "Campaign/Human01.w3m",
				"Maps/Melee/(2)Lost Temple.w3m");
		for (final String file : files) {
			final File target = new File(this.temp, file);
			target.getParentFile().mkdirs();
			Files.write(target.toPath(), file.equals("UI/CampaignInfo.txt") ? LIST.getBytes(StandardCharsets.UTF_8)
					: new byte[] { 1 });
		}
		assertEquals(DataFolderCheck.Kind.FOLDER, DataFolderCheck.detect(this.temp));
		assertEquals(Collections.emptyList(), DataContentCheck.check(DataFolderCheck.Kind.FOLDER, this.temp));
		assertEquals(true, new File(this.temp, "Campaign/Human01.w3m").delete());
		assertEquals(Arrays.asList(Problem.NO_CAMPAIGN_MAPS),
				DataContentCheck.check(DataFolderCheck.Kind.FOLDER, this.temp));
	}

	@Test
	void meleeOnlyDataNeedsNoCampaignMaps() throws IOException {
		final List<String> files = Arrays.asList("ui/WorldEditGameStrings.txt", "units/UnitData.slk",
				"Scripts/common.j", "Scripts/Blizzard.j", "UI/war3skins.txt", "UI/MiscData.txt", "Units/CommandFunc.txt",
				"Units/CommandStrings.txt", "UI/CampaignInfo.txt", "Maps/Melee/(2)Lost Temple.w3m");
		for (final String file : files) {
			final File target = new File(this.temp, file);
			target.getParentFile().mkdirs();
			Files.write(target.toPath(), file.equals("UI/CampaignInfo.txt") ? LIST.getBytes(StandardCharsets.UTF_8)
					: new byte[] { 1 });
		}
		final File marker = new File(this.temp, DataPackage.MARKER);
		Files.write(marker.toPath(), "tool=x\nprofile=full-classic\nentries=7\n".getBytes(StandardCharsets.UTF_8));
		assertEquals(Arrays.asList(Problem.NO_CAMPAIGN_MAPS),
				DataContentCheck.check(DataFolderCheck.Kind.FOLDER, this.temp));
		Files.write(marker.toPath(), "tool=x\nprofile=melee-only\nentries=7\n".getBytes(StandardCharsets.UTF_8));
		assertEquals(Collections.emptyList(), DataContentCheck.check(DataFolderCheck.Kind.FOLDER, this.temp));
	}

	@Test
	void anInstalledGameIsNotOpened() {
		assertEquals(Collections.emptyList(), DataContentCheck.check(DataFolderCheck.Kind.CASC, this.temp));
	}
}
