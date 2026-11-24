package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The crash log's file names, how many are kept and the text the player was told it holds. */
class CrashReportTest {
	private static final ZoneId UTC = ZoneId.of("UTC");

	@Test
	void namesSortByTime() {
		final String early = CrashReport.fileName(1762650000000L, UTC);
		final String late = CrashReport.fileName(1762650000000L + 86_400_000L, UTC);
		assertEquals("crash-20251109-010000.txt", early);
		assertTrue(early.compareTo(late) < 0);
		assertTrue(CrashReport.isCrashLog(early));
	}

	@Test
	void onlyCrashLogsAreListedAndNeverPaths() {
		assertFalse(CrashReport.isCrashLog("notes.txt"));
		assertFalse(CrashReport.isCrashLog("crash-.txt"));
		assertFalse(CrashReport.isCrashLog("crash-../x.txt"));
		assertFalse(CrashReport.isCrashLog(null));
	}

	@Test
	void oldestLogsExpireAndOtherFilesStay() {
		final List<String> names = new ArrayList<>();
		for (int day = 11; day <= 17; day++) {
			names.add("crash-202611" + day + "-100000.txt");
		}
		names.add("other.txt");
		Collections.shuffle(names);
		final List<String> expired = CrashReport.expired(names, CrashReport.KEEP);
		assertEquals(Arrays.asList("crash-20261111-100000.txt", "crash-20261112-100000.txt"), expired);
		assertEquals("crash-20261117-100000.txt", CrashReport.newest(names));
		assertTrue(CrashReport.expired(names, 10).isEmpty());
		assertNull(CrashReport.newest(Collections.singletonList("other.txt")));
	}

	@Test
	void rendererIsTheNewestNamedInTheLog() {
		final List<String> log = Arrays.asList("I/x: Renderer: Mali-A", "W/System.err: noise",
				"W/System.err: Renderer: Adreno (TM) 740 ",
				"D OpenGLRenderer: setSurface called with nullptr");
		assertEquals("Adreno (TM) 740", CrashReport.rendererFrom(log));
		assertNull(CrashReport.rendererFrom(Collections.singletonList("nothing")));
		assertNull(CrashReport.rendererFrom(null));
	}

	@Test
	void textHasEverythingTheConsentNamesAndKeepsTheNewestLogLines() {
		final CrashReport.Details details = new CrashReport.Details();
		details.time = 1762650000000L;
		details.thread = "GLThread 12";
		details.error = new OutOfMemoryError("heap");
		details.lastLoggedMessage = "Could not load";
		details.lastLogged = new IllegalStateException("map");
		details.appVersion = "0.3.72";
		details.commit = "abc1234";
		details.device = "Samsung SM-X900";
		details.androidVersion = "14 (API 34)";
		details.renderer = "Adreno";
		details.graphics = "set=HD, textureSize=1024";
		details.map = "Maps\\Test.w3x";
		details.log = new ArrayList<>();
		for (int i = 0; i < CrashReport.LOG_LINES + 5; i++) {
			details.log.add("line " + i);
		}
		final String text = CrashReport.text(details, UTC);
		for (final String part : new String[] { "App version: 0.3.72", "Commit: abc1234", "Device: Samsung SM-X900",
			"Android: 14 (API 34)", "GPU: Adreno", "Graphics: set=HD, textureSize=1024", "Map: Maps\\Test.w3x",
			"java.lang.OutOfMemoryError: heap", "Could not load", "java.lang.IllegalStateException: map",
			"line " + (CrashReport.LOG_LINES + 4) }) {
			assertTrue(text.contains(part), part);
		}
		assertFalse(text.contains("line 4\n"));
		assertTrue(text.contains("line 5\n"));
	}

	@Test
	void unknownPartsAreSaidSo() {
		final String text = CrashReport.text(new CrashReport.Details(), UTC);
		assertTrue(text.contains("GPU: unknown"));
		assertTrue(text.contains("Error\nnone"));
		assertFalse(text.contains("Last error the game logged"));
	}

	@Test
	void snapshotIsShareableButNotACrashLog() {
		final String name = CrashReport.snapshotName(1762650000000L, UTC);
		assertEquals("log-20251109-010000.txt", name);
		assertTrue(CrashReport.isSnapshot(name));
		assertTrue(CrashReport.isShareable(name));
		assertFalse(CrashReport.isCrashLog(name));
		assertFalse(CrashReport.isSnapshot("log-../x.txt"));
		assertFalse(CrashReport.isShareable("other.txt"));
		assertEquals(null, CrashReport.newest(Arrays.asList(name)));
	}

	@Test
	void snapshotCarriesTheRunLogAndTheLatestCrash() {
		final CrashReport.Details details = new CrashReport.Details();
		details.time = 1762650000000L;
		details.appVersion = "1.2.3";
		details.log = Arrays.asList("line one", "line two");
		final String text = CrashReport.snapshotText(details, "Warsmash crash log\nError", UTC);
		assertTrue(text.contains("App version: 1.2.3"));
		assertTrue(text.contains("line two"));
		assertTrue(text.contains("Latest saved crash log\nWarsmash crash log"));
		assertFalse(CrashReport.snapshotText(details, null, UTC).contains("Latest saved crash log"));
	}

	@Test
	void processExitHasItsOwnSections() {
		final CrashReport.Details details = new CrashReport.Details();
		details.time = 1762650000000L;
		details.exit = "Reason: LOW_MEMORY\nStatus or signal: 9";
		details.exitTrace = "backtrace";
		final String text = CrashReport.text(details, UTC);
		assertTrue(text.contains("Reason: LOW_MEMORY"));
		assertTrue(text.contains("backtrace"));
		assertFalse(text.contains("\nError\n"));
	}

	@Test
	void memoryNotesAreInBothKindsOfLog() {
		final CrashReport.Details details = new CrashReport.Details();
		details.time = 1762650000000L;
		details.memory = "10:00:00.000 load start | avail=900MB threshold=200MB low=false";
		details.exit = "Reason: LOW_MEMORY";
		assertTrue(CrashReport.text(details, UTC).contains("Memory (newest last)\n10:00:00.000 load start"));
		details.exit = null;
		details.error = new IllegalStateException("x");
		assertTrue(CrashReport.text(details, UTC).contains("threshold=200MB"));
		details.memory = null;
		assertFalse(CrashReport.text(details, UTC).contains("Memory (newest last)"));
	}

	@Test
	void binaryTraceKeepsReadableRuns() {
		final byte[] bytes = { 0, 1, 'a', 'b', 'c', 'd', 'e', 0, 'x', 'y', 0 };
		assertEquals("abcde\n", CrashReport.traceText(bytes, bytes.length));
		final byte[] plain = "hello".getBytes();
		assertEquals("hello", CrashReport.traceText(plain, plain.length));
	}
}
