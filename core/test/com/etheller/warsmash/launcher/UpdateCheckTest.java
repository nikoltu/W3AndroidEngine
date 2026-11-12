package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Version comparison and the release lookup's reading of GitHub's answer. */
class UpdateCheckTest {
	private static final String RELEASE = "{\"tag_name\":\"v0.3.6\",\"html_url\":\"https://github.com/x/y/releases/tag/v0.3.6\","
			+ "\"draft\":false,\"prerelease\":false,\"assets\":[{\"name\":\"notes.txt\",\"size\":5},"
			+ "{\"name\":\"Warsmash-v0.3.6.apk\",\"size\":6697397,"
			+ "\"browser_download_url\":\"https://github.com/x/y/releases/download/v0.3.6/Warsmash-v0.3.6.apk\"}]}";

	@Test
	void parsesVersions() {
		assertArrayEquals(new int[] { 0, 3, 6 }, UpdateCheck.parse("v0.3.6"));
		assertArrayEquals(new int[] { 0, 3, 6 }, UpdateCheck.parse("0.3.6-debug"));
		assertArrayEquals(new int[] { 0, 3, 0 }, UpdateCheck.parse("0.3"));
		assertArrayEquals(new int[] { 0, 10, 2 }, UpdateCheck.parse(" V0.10.2+build "));
		assertNull(UpdateCheck.parse("dev"));
		assertNull(UpdateCheck.parse(null));
	}

	@Test
	void comparesNumerically() {
		assertTrue(UpdateCheck.compare("v0.3.10", "0.3.9") > 0);
		assertTrue(UpdateCheck.compare("v0.3.6", "0.3.8-debug") < 0);
		assertTrue(UpdateCheck.compare("v0.4.0", "0.3.99") > 0);
		assertEquals(0, UpdateCheck.compare("v0.3.6", "0.3.6-release"));
		assertEquals(0, UpdateCheck.compare("v0.3.6", "dev"));
	}

	@Test
	void readsRelease() {
		final UpdateCheck.Release release = UpdateCheck.parseRelease(RELEASE);
		assertNotNull(release);
		assertEquals("v0.3.6", release.tag);
		assertEquals("Warsmash-v0.3.6.apk", release.apkName);
		assertEquals(6697397, release.apkSize);
		assertTrue(release.hasApk());
		assertNull(UpdateCheck.parseRelease(RELEASE.replace("\"draft\":false", "\"draft\":true")));
		assertNull(UpdateCheck.parseRelease("{\"message\":\"rate limit\"}"));
		assertFalse(UpdateCheck.parseRelease(RELEASE.replace(".apk", ".zip")).hasApk());
	}

	@Test
	void offersOnlyNewerUnskipped() {
		final UpdateCheck.Release release = UpdateCheck.parseRelease(RELEASE);
		assertTrue(UpdateCheck.offers("0.3.5", release, null));
		assertTrue(UpdateCheck.offers("0.3.5", release, "v0.3.4"));
		assertFalse(UpdateCheck.offers("0.3.5", release, "v0.3.6"));
		assertFalse(UpdateCheck.offers("0.3.6", release, null));
		assertFalse(UpdateCheck.offers("0.3.8", release, null));
		assertFalse(UpdateCheck.offers("0.3.5", null, null));
	}

	@Test
	void versionCodeIsMinorTimesHundredPlusPatch() {
		assertEquals(311, UpdateCheck.versionCode("v0.3.11"));
		assertEquals(306, UpdateCheck.versionCode("0.3.6-debug"));
		assertEquals(400, UpdateCheck.versionCode("0.4"));
		assertEquals(10000, UpdateCheck.versionCode("1.0.0"));
		assertEquals(0, UpdateCheck.versionCode("dev"));
		assertEquals(0, UpdateCheck.versionCode(null));
		assertTrue(UpdateCheck.versionCode("0.3.10") > UpdateCheck.versionCode("0.3.9"));
		assertTrue(UpdateCheck.versionCode("0.4.0") > UpdateCheck.versionCode("0.3.99"));
	}

	@Test
	void officialBuildIsOfferedOnlyAHigherVersionCode() {
		final UpdateCheck.Release release = UpdateCheck.parseRelease(RELEASE);
		assertTrue(UpdateCheck.offers(305, release, null, true));
		assertFalse(UpdateCheck.offers(306, release, null, true));
		assertFalse(UpdateCheck.offers(311, release, null, true));
		assertFalse(UpdateCheck.offers(0, release, null, true));
		assertFalse(UpdateCheck.offers(305, null, null, true));
		assertFalse(UpdateCheck.offers(305, release, "v0.3.6", true));
		assertTrue(UpdateCheck.offers(305, release, "v0.3.5", true));
		assertTrue(UpdateCheck.offers("0.3.5", release, null, true));
		assertFalse(UpdateCheck.offers("0.3.6", release, null, true));
	}

	@Test
	void unofficialBuildIsOfferedTheReleaseOfItsOwnVersionOrHigher() {
		final UpdateCheck.Release release = UpdateCheck.parseRelease(RELEASE);
		assertTrue(UpdateCheck.offers("0.3.6", release, null, false));
		assertTrue(UpdateCheck.offers(305, release, "", false));
		assertTrue(UpdateCheck.offers(0, release, null, false));
		assertFalse(UpdateCheck.offers("0.9.0", release, null, false));
		assertFalse(UpdateCheck.offers(307, release, null, false));
		assertFalse(UpdateCheck.offers("0.3.6", release, "v0.3.6", false));
		assertFalse(UpdateCheck.offers("0.3.5", null, null, false));
		assertFalse(UpdateCheck.offers("0.3.5", UpdateCheck.parseRelease(RELEASE.replace(".apk", ".zip")), null, false));
	}

	@Test
	void aFakeReleaseComesFromTheSystemProperty() {
		System.setProperty(UpdateCheck.FAKE_PROPERTY, "v9.9.99");
		try {
			final UpdateCheck.Release release = UpdateCheck.fetchLatest(1);
			assertNotNull(release);
			assertEquals("v9.9.99", release.tag);
			assertFalse(release.hasApk());
			assertTrue(UpdateCheck.offers("0.3.11", release, null));
			System.setProperty(UpdateCheck.FAKE_PROPERTY, "not a version");
			assertNull(UpdateCheck.fetchLatest(1));
		}
		finally {
			System.clearProperty(UpdateCheck.FAKE_PROPERTY);
		}
	}
}
