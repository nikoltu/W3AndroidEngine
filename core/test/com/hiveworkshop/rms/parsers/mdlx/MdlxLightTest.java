package com.hiveworkshop.rms.parsers.mdlx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class MdlxLightTest {
	private static final int LIGHT_SIZE = 96 + 4 + 8 + 12 + 4 + 12 + 4;

	private static void putTag(final ByteBuffer buffer, final String tag) {
		buffer.put(tag.getBytes(StandardCharsets.US_ASCII));
	}

	/** One light: inclusive size, the 96 byte node, then the light fields; padding follows the fields. */
	private static void putLight(final ByteBuffer buffer, final int type, final int padding, final float intensity) {
		buffer.putInt(LIGHT_SIZE + 4 + padding);
		buffer.putInt(96);
		final byte[] name = new byte[80];
		buffer.put(name);
		buffer.putInt(1); // object id
		buffer.putInt(-1); // parent id
		buffer.putInt(0x200); // flags
		buffer.putInt(type);
		buffer.putFloat(80f).putFloat(200f); // attenuation
		buffer.putFloat(1f).putFloat(1f).putFloat(1f); // color
		buffer.putFloat(intensity);
		buffer.putFloat(1f).putFloat(1f).putFloat(1f); // ambient color
		buffer.putFloat(0f); // ambient intensity
		buffer.put(new byte[padding]);
	}

	private static MdlxModel model(final int firstType, final int firstPadding) {
		final ByteBuffer buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
		putTag(buffer, "MDLX");
		putTag(buffer, "VERS");
		buffer.putInt(4);
		buffer.putInt(800);
		putTag(buffer, "LITE");
		final int lengthAt = buffer.position();
		buffer.putInt(0);
		final int start = buffer.position();
		putLight(buffer, firstType, firstPadding, 18f);
		putLight(buffer, 2, 0, 3f);
		buffer.putInt(lengthAt, buffer.position() - start);
		buffer.flip();
		return new MdlxModel(buffer);
	}

	@Test
	void validLightTypeIsKept() {
		final MdlxModel model = model(1, 0);
		assertEquals(2, model.getLights().size());
		assertEquals(MdlxLight.Type.DIRECTIONAL, model.getLights().get(0).getType());
		assertEquals(MdlxLight.Type.AMBIENT, model.getLights().get(1).getType());
	}

	/** Some editors write 512 into the type; the model still loads and the light is the default omni light. */
	@Test
	void unknownLightTypeIsOmnidirectional() {
		final MdlxModel model = model(512, 0);
		assertEquals(2, model.getLights().size());
		assertEquals(MdlxLight.Type.OMNIDIRECTIONAL, model.getLights().get(0).getType());
		assertEquals(18f, model.getLights().get(0).getIntensity());
		assertEquals(3f, model.getLights().get(1).getIntensity());
	}

	/** An object longer than the fields known to the reader must not shift the object behind it. */
	@Test
	void trailingBytesInsideALightDoNotShiftTheNextLight() {
		final MdlxModel model = model(0, 12);
		assertEquals(2, model.getLights().size());
		assertEquals(3f, model.getLights().get(1).getIntensity());
		assertEquals(MdlxLight.Type.AMBIENT, model.getLights().get(1).getType());
	}
}
