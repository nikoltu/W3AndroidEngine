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
}
