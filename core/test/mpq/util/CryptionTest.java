package mpq.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** Map files with letters outside ASCII in a model path are looked up by their bytes, not by a negative table index. */
class CryptionTest {
	@Test
	void hashOfANameWithLettersOutsideAsciiDoesNotThrow() {
		final String model = "нет.mdl";
		for (final int type : new int[] { Cryption.MPQ_HASH_TABLE_OFFSET, Cryption.MPQ_HASH_NAME_A,
				Cryption.MPQ_HASH_NAME_B }) {
			assertDoesNotThrow(() -> Cryption.HashString(model, type));
		}
		assertEquals(Cryption.HashString(model, Cryption.MPQ_HASH_NAME_A),
				Cryption.HashString(model, Cryption.MPQ_HASH_NAME_A));
		assertNotEquals(Cryption.HashString(model, Cryption.MPQ_HASH_NAME_A),
				Cryption.HashString("нет.mdx", Cryption.MPQ_HASH_NAME_A));
	}

	@Test
	void hashOfAnAsciiNameIsUnchanged() {
		// The value an independent implementation of the hash gives for this name
		assertEquals(0x2D2F0A94, Cryption.HashString("(listfile)", Cryption.MPQ_HASH_FILE_KEY));
	}
}
