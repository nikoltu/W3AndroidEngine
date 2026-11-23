package com.etheller.warsmash.viewer5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.viewer5.handlers.blp.MipLevels;

/** A texture that cannot be decoded takes the other format of the same file, else grey. */
class TextureFallbackTest {
	private static DataSource files(final Map<String, byte[]> files) {
		return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] { DataSource.class },
				(proxy, method, args) -> {
					switch (method.getName()) {
					case "has":
						return files.containsKey(args[0]);
					case "getResourceAsStream":
						return files.containsKey(args[0]) ? new ByteArrayInputStream(files.get(args[0])) : null;
					default:
						throw new UnsupportedOperationException(method.getName());
					}
				});
	}

	/** A 4x4 DXT1 file: one block. */
	private static byte[] dds() {
		final ByteBuffer file = ByteBuffer.allocate(128 + 8).order(ByteOrder.LITTLE_ENDIAN);
		file.putInt(0, 0x20534444);
		file.putInt(8, 0x20000);
		file.putInt(12, 4);
		file.putInt(16, 4);
		file.putInt(28, 1);
		file.putInt(80, 0x4);
		file.putInt(84, 'D' | ('X' << 8) | ('T' << 16) | ('1' << 24));
		return file.array();
	}

	@Test
	void otherFormatSwapsDdsAndBlp() {
		assertEquals("textures/a.blp", TextureFallback.otherFormat("textures/a.dds"));
		assertEquals("Textures/A.DDS", TextureFallback.otherFormat("Textures/A.BLP"));
		assertNull(TextureFallback.otherFormat("textures/a.tga"));
		assertNull(TextureFallback.otherFormat(null));
	}

	@Test
	void otherFormatThatDecodesIsFound() {
		final MipLevels levels = TextureFallback.alternate(files(Map.of("ui/a.dds", dds())), "ui/a.blp", 0);
		assertNotNull(levels);
		assertEquals(4, levels.getWidth(0));
		levels.free();
	}

	@Test
	void missingOrUnreadableOtherFormatGivesNothing() {
		assertNull(TextureFallback.alternate(files(Map.of()), "ui/a.blp", 0));
		assertNull(TextureFallback.alternate(files(Map.of("ui/a.dds", new byte[20])), "ui/a.blp", 0));
		assertNull(TextureFallback.alternate(files(Map.of("ui/a.png", dds())), "ui/a.tga", 0));
	}

	@Test
	void kindIsTakenFromTheName() {
		assertEquals(TextureFallback.Kind.NORMAL, TextureFallback.kindOf("Doodads/Terrain/X_Normal.dds"));
		assertEquals(TextureFallback.Kind.ORM, TextureFallback.kindOf("TerrainArt\\Dungeon\\Cave_Cliff1_ORM.dds"));
		assertEquals(TextureFallback.Kind.DIFFUSE, TextureFallback.kindOf("Textures/CaveCliffDoodad_Diffuse.dds"));
		assertEquals(TextureFallback.Kind.DIFFUSE, TextureFallback.kindOf("Textures/Black32.blp"));
		assertEquals(TextureFallback.Kind.DIFFUSE, TextureFallback.kindOf(null));
	}

	@Test
	void neutralNormalIsFlatAndNeutralOrmHasNoTeamColour() {
		final ByteBuffer normal = TextureFallback.neutral(TextureFallback.Kind.NORMAL);
		assertEquals((byte) 0x80, normal.get(0));
		assertEquals((byte) 0x80, normal.get(1));
		assertEquals((byte) 0xFF, normal.get(2));
		final ByteBuffer orm = TextureFallback.neutral(TextureFallback.Kind.ORM);
		assertEquals((byte) 0xFF, orm.get(0));
		assertEquals((byte) 0x00, orm.get(2));
		assertEquals((byte) 0x00, orm.get(3));
	}

	@Test
	void greyIsOpaque808080() {
		final ByteBuffer pixel = TextureFallback.grey();
		assertEquals(4, pixel.remaining());
		for (int i = 0; i < 3; i++) {
			assertEquals((byte) 0x80, pixel.get(i));
		}
		assertEquals((byte) 0xFF, pixel.get(3));
	}
}
