package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataFolderCheckTest {
	@TempDir
	File folder;

	private void buildInfo(final String tags) throws IOException {
		Files.write(new File(this.folder, ".build.info").toPath(),
				("Branch!STRING:0|Tags!STRING:0|Version!STRING:0\nus|" + tags + "|1.32.10\n")
						.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void anInstallInAnotherLanguageReadsItsOwnLocaleLayer() throws IOException {
		buildInfo("Windows x86_64 ruRU? ruRU speech?:ruRU text?");
		assertEquals(Arrays.asList("ruRU"), DataFolderCheck.cascLocales(this.folder));
		assertEquals("war3.w3mod,war3.w3mod\\_deprecated.w3mod,war3.w3mod\\_locales\\ruru.w3mod",
				DataFolderCheck.cascPrefixes(this.folder));
	}

	@Test
	void anInstallWithSeveralLanguagesListsAllOfThem() throws IOException {
		buildInfo("Windows enUS? deDE? enUS speech?:enUS text?:deDE text?");
		assertEquals(Arrays.asList("enUS", "deDE"), DataFolderCheck.cascLocales(this.folder));
	}

	@Test
	void anInstallThatNamesNoLanguageIsEnglish() throws IOException {
		buildInfo("Windows x86_64");
		assertEquals("war3.w3mod,war3.w3mod\\_deprecated.w3mod,war3.w3mod\\_locales\\enus.w3mod",
				DataFolderCheck.cascPrefixes(this.folder));
	}

	@Test
	void anInstallListsTheLanguagesOfItsTags() throws IOException {
		buildInfo("Windows enUS? deDE? enUS speech?:enUS text?:deDE text?");
		assertEquals(Arrays.asList("enUS", "deDE"), DataFolderCheck.locales(DataFolderCheck.Kind.CASC, this.folder));
	}

	@Test
	void anExtractedFolderListsTheLanguageFoldersThatHoldText() throws IOException {
		for (final String layer : new String[] { "enus", "eses" }) {
			final File ui = new File(this.folder, "_locales/" + layer + ".w3mod/ui");
			ui.mkdirs();
			Files.write(new File(ui, "WorldEditGameStrings.txt").toPath(), new byte[] { 1 });
		}
		new File(this.folder, "_locales/frfr.w3mod").mkdirs();
		assertEquals(Arrays.asList("enUS", "esES"), DataFolderCheck.locales(DataFolderCheck.Kind.FOLDER, this.folder));
	}

	@Test
	void anExtractedFolderKeepsItsOwnLanguageBesideTheAddedPacks() throws IOException {
		Files.write(new File(this.folder, "w3-datapacker.txt").toPath(), "locale=enUS\n".getBytes(StandardCharsets.UTF_8));
		final File ui = new File(this.folder, "_locales/eses.w3mod/ui");
		ui.mkdirs();
		Files.write(new File(ui, "WorldEditGameStrings.txt").toPath(), new byte[] { 1 });
		assertEquals(Arrays.asList("enUS", "esES"), DataFolderCheck.locales(DataFolderCheck.Kind.FOLDER, this.folder));
	}

	private void pack(final String layer) throws IOException {
		final File ui = new File(this.folder, "_locales/" + layer + ".w3mod/ui");
		ui.mkdirs();
		Files.write(new File(ui, "WorldEditGameStrings.txt").toPath(), new byte[] { 1 });
	}

	@Test
	void aFolderWithoutMarkerKeepsEnglishBesideAnAddedPack() throws IOException {
		pack("eses");
		assertEquals("enUS", DataFolderCheck.locale(DataFolderCheck.Kind.FOLDER, this.folder));
		assertEquals(Arrays.asList("enUS", "esES"), DataFolderCheck.locales(DataFolderCheck.Kind.FOLDER, this.folder));
	}

	@Test
	void aFolderWithoutMarkerReadsItsLanguageFromTheConfig() throws IOException {
		Files.write(new File(this.folder, "config.txt").toPath(), "LANGID='deDE'\n".getBytes(StandardCharsets.UTF_8));
		pack("eses");
		assertEquals("deDE", DataFolderCheck.locale(DataFolderCheck.Kind.FOLDER, this.folder));
		assertEquals(Arrays.asList("deDE", "esES"), DataFolderCheck.locales(DataFolderCheck.Kind.FOLDER, this.folder));
	}

	@Test
	void aFolderWithoutLanguagePacksOrMarkerHasNoKnownLanguage() {
		assertEquals("", DataFolderCheck.locale(DataFolderCheck.Kind.FOLDER, this.folder));
	}

	@Test
	void aLocalFilesInstallTakesItsLanguageFromItsLocaleLayer() throws IOException {
		pack("eses");
		assertEquals("esES", DataFolderCheck.locale(DataFolderCheck.Kind.LOCAL_FILES, this.folder));
	}

	private void buildInfoRows(final String... rows) throws IOException {
		Files.write(new File(this.folder, ".build.info").toPath(),
				("Branch!STRING:0|Active!DEC:1|Tags!STRING:0|Version!STRING:0\n" + String.join("\n", rows) + "\n")
						.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void version130And131UseTheOlderLayoutAndNewerOnesDoNot() throws IOException {
		for (final String version : new String[] { "1.30.4.11274", "1.31.1.12164" }) {
			buildInfoRows("eu|1|Windows EU? enUS speech?:Windows EU? enUS text?|" + version);
			assertEquals(true, DataFolderCheck.usesOldCascFormat(this.folder), version);
			assertEquals(true, DataFolderCheck.dataSourcesSection(DataFolderCheck.Kind.CASC, this.folder)
					.contains("OldFormat00=1"), version);
		}
		for (final String version : new String[] { "1.32.10.19151", "1.36.1.20719", "2.0.0.22988", "3.0.1.23456" }) {
			buildInfoRows("us|1|Windows enUS? enUS speech?:enUS text?|" + version);
			assertEquals(false, DataFolderCheck.usesOldCascFormat(this.folder), version);
			assertEquals(false, DataFolderCheck.dataSourcesSection(DataFolderCheck.Kind.CASC, this.folder)
					.contains("OldFormat"), version);
		}
	}

	@Test
	void theActiveRowDecidesTheLayout() throws IOException {
		buildInfoRows("kr|0|Windows KR? zhCN text?|1.31.1.12164", "cn|1|Windows CN? zhCN text?|1.32.10.19151");
		assertEquals(false, DataFolderCheck.usesOldCascFormat(this.folder));
		buildInfoRows("kr|0|Windows KR? zhCN text?|1.32.10.19151", "cn|1|Windows CN? zhCN text?|1.31.1.12164");
		assertEquals(true, DataFolderCheck.usesOldCascFormat(this.folder));
	}

	@Test
	void aBuildInfoWithoutAVersionIsTakenAsNewer() throws IOException {
		assertEquals(false, DataFolderCheck.usesOldCascFormat(this.folder));
		assertEquals(false, DataFolderCheck.isUnsupportedCasc(this.folder));
		buildInfoRows("us|1|Windows enUS?|");
		assertEquals(false, DataFolderCheck.usesOldCascFormat(this.folder));
		buildInfoRows("us|1|Windows enUS?|unknown");
		assertEquals(false, DataFolderCheck.isUnsupportedCasc(this.folder));
	}

	@Test
	void versionsBeforeTheFirstSupportedOneAreUnsupported() {
		assertEquals(true, DataFolderCheck.isUnsupportedCascVersion("1.30.4.11274"));
		assertEquals(true, DataFolderCheck.isUnsupportedCascVersion("version 1.30.4.11274"));
		assertEquals(true, DataFolderCheck.isUnsupportedCascVersion("1.29.2.9231"));
		assertEquals(false, DataFolderCheck.isUnsupportedCascVersion("1.31.1.12164"));
		assertEquals(false, DataFolderCheck.isUnsupportedCascVersion("version 1.32.10.19151"));
		assertEquals(false, DataFolderCheck.isUnsupportedCascVersion("3.0.1.23456"));
		assertEquals(false, DataFolderCheck.isUnsupportedCascVersion(""));
		assertEquals(false, DataFolderCheck.isUnsupportedCascVersion(null));
	}

	@Test
	void version130IsReportedAsUnsupportedByTheContentCheck() throws IOException {
		buildInfoRows("eu|1|Windows EU? enUS speech?:Windows EU? enUS text?|1.30.4.11274");
		assertEquals(true, DataFolderCheck.isUnsupportedCasc(this.folder));
		assertEquals(Arrays.asList(DataContentCheck.Problem.UNSUPPORTED_VERSION),
				DataContentCheck.check(DataFolderCheck.Kind.CASC, this.folder));
		buildInfoRows("cn|1|Windows CN? zhCN text?|1.31.1.12164");
		assertEquals(Arrays.asList(), DataContentCheck.check(DataFolderCheck.Kind.CASC, this.folder));
	}

	private void installLayout(final boolean config) throws IOException {
		final String key = "7c45731c22f6bf4ff30035ab9d905745";
		Files.write(new File(this.folder, ".build.info").toPath(),
				("Branch!STRING:0|Active!DEC:1|Build Key!HEX:16|CDN Path!STRING:0|Tags!STRING:0|Version!STRING:0\n"
						+ "eu|1|" + key + "|tpr/war3|Windows enUS|1.31.1.12164\n").getBytes(StandardCharsets.UTF_8));
		final File data = new File(this.folder, "Data/data");
		data.mkdirs();
		Files.write(new File(data, "0000000001.idx").toPath(), new byte[] { 1 });
		Files.write(new File(data, "data.000").toPath(), new byte[] { 1 });
		if (config) {
			final File file = new File(this.folder, "Data/config/7c/45/" + key);
			file.getParentFile().mkdirs();
			Files.write(file.toPath(), new byte[] { 1 });
		}
	}

	@Test
	void anInstallWithItsBuildConfigurationIsMountable() throws IOException {
		installLayout(true);
		assertEquals(null, DataFolderCheck.missingCascFile(this.folder));
		assertEquals(DataFolderCheck.Kind.CASC, DataFolderCheck.detect(this.folder));
	}

	@Test
	void anInstallWithoutItsBuildConfigurationNamesTheMissingFile() throws IOException {
		installLayout(false);
		assertEquals("Data/config/7c/45/7c45731c22f6bf4ff30035ab9d905745", DataFolderCheck.missingCascFile(this.folder));
		assertEquals(DataFolderCheck.Kind.NONE, DataFolderCheck.detect(this.folder));
	}
}
