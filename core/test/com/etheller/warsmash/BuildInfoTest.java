package com.etheller.warsmash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.Test;

class BuildInfoTest {
	private static Properties properties(final String... pairs) {
		final Properties properties = new Properties();
		for (int i = 0; i < pairs.length; i += 2) {
			properties.setProperty(pairs[i], pairs[i + 1]);
		}
		return properties;
	}

	@Test
	void parsesTheBuildsNumbers() {
		final BuildInfo info = BuildInfo.parse(properties("versionName", "0.3.12", "versionCode", "312", "commit",
				"ABC1234", "dirty", "false", "official", "true"));
		assertEquals("0.3.12", info.getVersionName());
		assertEquals(312, info.getVersionCode());
		assertEquals("abc1234", info.getCommit());
		assertFalse(info.isDirty());
		assertTrue(info.isOfficial());
		assertEquals("v0.3.12 (abc1234)", info.displayVersion());
	}

	@Test
	void aBuildWithoutGitOrResourceIsUnofficial() {
		final BuildInfo info = BuildInfo.parse(properties("versionName", "0.3.12", "official", "true"));
		assertEquals(BuildInfo.UNKNOWN_COMMIT, info.getCommit());
		assertFalse(info.isOfficial());
		assertEquals("v0.3.12", info.displayVersion());

		final BuildInfo empty = BuildInfo.parse(new Properties());
		assertEquals("dev", empty.getVersionName());
		assertEquals(0, empty.getVersionCode());
		assertFalse(empty.isOfficial());
	}

	@Test
	void garbageInTheResourceIsNotTrusted() {
		final BuildInfo info = BuildInfo.parse(
				properties("versionCode", "many", "commit", "not a hash", "official", "yes"));
		assertEquals(0, info.getVersionCode());
		assertEquals(BuildInfo.UNKNOWN_COMMIT, info.getCommit());
		assertFalse(info.isOfficial());
	}

	@Test
	void aDirtyTreeIsNeverOfficialAndShowsIt() {
		final BuildInfo info = BuildInfo.parse(
				properties("versionName", "0.3.12", "commit", "abc1234", "dirty", "true", "official", "true"));
		assertTrue(info.isDirty());
		assertFalse(info.isOfficial());
		assertEquals("v0.3.12 (abc1234+)", info.displayVersion());
	}

	@Test
	void fingerprintsCompareWithoutSeparatorsOrCase() {
		assertEquals("abcd01", BuildInfo.normalizeFingerprint("AB:CD:01"));
		assertEquals("", BuildInfo.normalizeFingerprint(null));
	}

	@Test
	void compatibilityIdNamesVersionCommitAndSimulationData() {
		final BuildInfo info = BuildInfo.parse(properties("versionName", "0.3.12", "commit", "abc1234"));
		assertEquals("0.3.12-abc1234-s" + BuildInfo.SIMULATION_DATA_VERSION, info.getNetworkCompatibilityId());
		assertTrue(info.hasNetworkIdentity());
		assertTrue(info.isNetworkCompatible(info.getNetworkCompatibilityId()));
		assertFalse(info.isNetworkCompatible("0.3.12-abc1235-s" + BuildInfo.SIMULATION_DATA_VERSION));
		assertFalse(info.isNetworkCompatible("0.3.13-abc1234-s" + BuildInfo.SIMULATION_DATA_VERSION));
		assertFalse(info.isNetworkCompatible("0.3.12-abc1234-s" + (BuildInfo.SIMULATION_DATA_VERSION + 1)));
		assertFalse(info.isNetworkCompatible(null));
	}

	@Test
	void buildsNothingCanVouchForMatchNobody() {
		final BuildInfo dirty = BuildInfo.parse(
				properties("versionName", "0.3.12", "commit", "abc1234", "dirty", "true"));
		assertFalse(dirty.hasNetworkIdentity());
		assertFalse(dirty.isNetworkCompatible(dirty.getNetworkCompatibilityId()));
		final BuildInfo unknown = BuildInfo.parse(properties("versionName", "0.3.12"));
		assertFalse(unknown.isNetworkCompatible(unknown.getNetworkCompatibilityId()));
	}

	@Test
	void officialAndUnofficialBuildsOfOneCommitShareTheId() {
		final BuildInfo official = BuildInfo.parse(
				properties("versionName", "0.3.12", "commit", "abc1234", "official", "true"));
		final BuildInfo unofficial = BuildInfo.parse(properties("versionName", "0.3.12", "commit", "abc1234"));
		assertTrue(official.isOfficial());
		assertFalse(unofficial.isOfficial());
		assertEquals(official.getNetworkCompatibilityId(), unofficial.getNetworkCompatibilityId());
	}
}
