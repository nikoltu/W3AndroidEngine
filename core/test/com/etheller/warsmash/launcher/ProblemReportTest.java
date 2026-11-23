package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.ai.AIDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.CBasePlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfig;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplay;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplayHeader;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplayWriter;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

class ProblemReportTest {
	private static final ZoneId UTC = ZoneId.of("UTC");

	@TempDir
	Path folder;

	/** A replay of two people and a computer, of a map kept outside the game data, with a few commands. */
	private File replay(final String mapPath) throws IOException {
		final War3MapConfig config = new War3MapConfig(WarsmashConstants.MAX_PLAYERS);
		final CBasePlayer host = config.getPlayer(0);
		host.setName("Alice Example");
		host.setController(CMapControl.USER);
		host.setSlotState(CPlayerSlotState.PLAYING);
		final CBasePlayer guest = config.getPlayer(3);
		guest.setName("Bob Example");
		guest.setController(CMapControl.USER);
		guest.setSlotState(CPlayerSlotState.PLAYING);
		final CBasePlayer computer = config.getPlayer(1);
		computer.setName("Computer (Insane)");
		computer.setController(CMapControl.COMPUTER);
		computer.setSlotState(CPlayerSlotState.PLAYING);
		computer.setAIDifficulty(AIDifficulty.INSANE);
		final CReplayHeader header = CReplayHeader.of("0.4.1", "abc1234", false, mapPath, 0x1234abcd, 4096,
				0x5eed, 99L, 1337, 0, 1.0f, config, 20, null);
		final File file = this.folder.resolve("LastReplay.wsr").toFile();
		try (OutputStream out = new FileOutputStream(file)) {
			final CReplayWriter writer = new CReplayWriter(out, header);
			writer.command(5, 0, new byte[] { 1, 2, 3 });
			writer.checksum(20, 77);
			writer.command(31, 3, new byte[] { 9, 8 });
			writer.end(40);
			writer.close();
		}
		return file;
	}

	private File file(final String name, final byte[] bytes) throws IOException {
		final File file = this.folder.resolve(name).toFile();
		Files.write(file.toPath(), bytes);
		return file;
	}

	private File sized(final String name, final long length) throws IOException {
		final File file = this.folder.resolve(name).toFile();
		try (RandomAccessFile out = new RandomAccessFile(file, "rw")) {
			out.setLength(length);
		}
		return file;
	}

	private static Map<String, byte[]> unzip(final byte[] zip) throws IOException {
		final Map<String, byte[]> entries = new LinkedHashMap<>();
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				final byte[] buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) != -1) {
					bytes.write(buffer, 0, read);
				}
				entries.put(entry.getName(), bytes.toByteArray());
			}
		}
		return entries;
	}

	private static Map<String, byte[]> written(final ProblemReport.Request request) throws IOException {
		final ByteArrayOutputStream zip = new ByteArrayOutputStream();
		ProblemReport.write(request, zip, UTC, new java.util.ArrayList<>(), new java.util.ArrayList<>());
		return unzip(zip.toByteArray());
	}

	private static String text(final byte[] bytes) {
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private ProblemReport.Request request() {
		final ProblemReport.Request request = new ProblemReport.Request();
		request.time = 1_700_000_000_000L;
		request.facts.put("App version", "0.4.300");
		request.facts.put("Commit", "abc1234");
		request.facts.put("Device", "Samsung SM-X906B");
		request.facts.put("GPU", "Adreno (TM) 650");
		request.appLog = Arrays.asList("line one", "line two");
		return request;
	}

	@Test
	void theReportHoldsTheReplayTheMarksTheLogsAndTheFacts() throws IOException {
		final ProblemReport.Request request = request();
		request.replay = replay("Maps/FrozenThrone/(2)EchoIsles.w3x");
		request.marks = file("LastReplay.marks", "map=x\nmark 1 tick=31 time=1.55 camera=0.0,0.0\n"
				.getBytes(StandardCharsets.UTF_8));
		request.crashLog = file("crash-20261121-120000.txt", "Warsmash crash log\n".getBytes(StandardCharsets.UTF_8));
		final Map<String, byte[]> zip = written(request);

		assertArrayEquals(Files.readAllBytes(request.replay.toPath()), zip.get(ProblemReport.REPLAY));
		assertEquals("map=x\nmark 1 tick=31 time=1.55 camera=0.0,0.0\n", text(zip.get(ProblemReport.MARKS)));
		assertEquals("line one\nline two\n", text(zip.get(ProblemReport.APP_LOG)));
		assertEquals("Warsmash crash log\n", text(zip.get(ProblemReport.CRASH_LOG)));
		final String info = text(zip.get(ProblemReport.INFO));
		assertTrue(info.startsWith("Warsmash bug report\nTime: 2023-11-14 22:13:20 +0000\n"), info);
		assertTrue(info.contains("App version: 0.4.300\nCommit: abc1234\nDevice: Samsung SM-X906B\n"), info);
		assertTrue(info.contains("- Replay LastReplay.wsr\n- Marked moments\n- App log\n- Latest crash log\n"), info);
		assertFalse(info.contains("Left out"), info);
		assertEquals(Arrays.asList(ProblemReport.REPLAY, ProblemReport.MARKS, ProblemReport.APP_LOG,
				ProblemReport.CRASH_LOG, ProblemReport.INFO), Arrays.asList(zip.keySet().toArray()));
	}

	@Test
	void theMapAndTheSaveAlwaysGoInWhenSmallEnough() throws IOException {
		final ProblemReport.Request request = request();
		request.map = file("My Map.w3x", new byte[] { 'H', 'M', '3', 'W' });
		request.save = file("Chapter 3.w3z", new byte[] { 7, 7, 7 });
		Map<String, byte[]> zip = written(request);
		assertArrayEquals(new byte[] { 'H', 'M', '3', 'W' }, zip.get("map/My Map.w3x"));
		assertArrayEquals(new byte[] { 7, 7, 7 }, zip.get("save/Chapter 3.w3z"));

		request.map = sized("Big Map.w3x", ProblemReport.MAP_LIMIT + 1);
		request.save = null;
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final List<String> leftOut = new java.util.ArrayList<>();
		ProblemReport.write(request, bytes, UTC, new java.util.ArrayList<>(), leftOut);
		zip = unzip(bytes.toByteArray());
		assertNull(zip.get("map/Big Map.w3x"));
		assertEquals(Arrays.asList("Replay: no replay was found (campaign missions and loaded saves are not recorded)",
				"Map: Big Map.w3x is larger than 64 MB (67108865 bytes, SHA-1 e86b0f6894957e6491651921c60fe783069885c2)"),
				leftOut);
	}

	@Test
	void aLongLogKeepsItsEndFromALineStart() {
		final byte[] log = "first line\nsecond line\nthird\n".getBytes(StandardCharsets.UTF_8);
		assertEquals("third\n", text(ProblemReport.tail(log, 8)));
		assertEquals("second line\nthird\n", text(ProblemReport.tail(log, 18)));
		assertArrayEquals(log, ProblemReport.tail(log, log.length));
	}

	@Test
	void reportsAreNamedByTimeAndOnlyTheNewestStay() throws IOException {
		final File reports = this.folder.resolve("Reports").toFile();
		final ProblemReport.Request request = request();
		for (int i = 0; i < (ProblemReport.KEEP + 2); i++) {
			request.time = 1_700_000_000_000L + (i * 1000L);
			final ProblemReport.Result result = ProblemReport.write(request, reports, UTC);
			assertTrue(result.file.isFile());
			assertTrue(ProblemReport.isReport(result.file.getName()), result.file.getName());
		}
		final String[] left = reports.list();
		Arrays.sort(left);
		assertEquals(Arrays.asList("bug-report-20231114-221322.zip", "bug-report-20231114-221323.zip",
				"bug-report-20231114-221324.zip"), Arrays.asList(left));
		assertFalse(ProblemReport.isReport("../bug-report-1.zip"));
		assertFalse(ProblemReport.isReport("crash-20231114-221324.txt"));
		try (InputStream in = Files.newInputStream(new File(reports, left[2]).toPath())) {
			assertNotNull(unzip(in.readAllBytes()).get(ProblemReport.INFO));
		}
	}
}
