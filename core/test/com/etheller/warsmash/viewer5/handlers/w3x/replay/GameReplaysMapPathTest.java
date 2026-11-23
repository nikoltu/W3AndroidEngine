package com.etheller.warsmash.viewer5.handlers.w3x.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.DataSource;

class GameReplaysMapPathTest {
	private static final DataSource EMPTY = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
			new Class<?>[] { DataSource.class }, (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);

	@Test
	void checksumOfAFileUriMatchesThePlainPath(@TempDir final Path dir) throws IOException {
		final byte[] bytes = { 1, 2, 3, 4, 5 };
		final File map = Files.write(dir.resolve("m.w3x"), bytes).toFile();
		final CRC32 crc = new CRC32();
		crc.update(bytes);
		final long[] plain = GameReplays.mapChecksum(EMPTY, map.getAbsolutePath());
		final long[] uri = GameReplays.mapChecksum(EMPTY, "file://" + map.toURI().getPath());
		assertEquals(crc.getValue(), plain[0]);
		assertEquals(5, plain[1]);
		assertEquals(plain[0], uri[0]);
		assertEquals(plain[1], uri[1]);
	}

	@Test
	void missingMapIsAnError(@TempDir final Path dir) {
		assertThrows(IOException.class, () -> GameReplays.mapChecksum(EMPTY, dir.resolve("none.w3x").toString()));
	}
}
