package com.etheller.warsmash.viewer5.handlers.blp;

import java.io.IOException;

/**
 * A baseline JPEG decoder for the JPEG content of BLP1 files. BLP stores its
 * color channels (BGRA) directly as JPEG components, so no color transform is
 * applied: the result holds the decoded components in file order.
 */
public final class JpegDecoder {
	private static final int[] ZIGZAG = { 0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33,
			40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28, 35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44,
			51, 58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63 };
	private static final float[][] IDCT_COSINES = new float[8][8];

	static {
		for (int x = 0; x < 8; x++) {
			for (int u = 0; u < 8; u++) {
				final double scale = u == 0 ? Math.sqrt(0.5) : 1.0;
				IDCT_COSINES[x][u] = (float) (scale * Math.cos(((2 * x) + 1) * u * Math.PI / 16) / 2);
			}
		}
	}

	private final int width;
	private final int height;
	private final int componentCount;
	private final byte[] components;

	private JpegDecoder(final int width, final int height, final int componentCount, final byte[] components) {
		this.width = width;
		this.height = height;
		this.componentCount = componentCount;
		this.components = components;
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	public int getComponentCount() {
		return this.componentCount;
	}

	/**
	 * @return width * height pixels of componentCount bytes each, rows from the top
	 */
	public byte[] getComponents() {
		return this.components;
	}

	private static final class Component {
		int id;
		int horizontalSampling;
		int verticalSampling;
		int quantizationTable;
		int dcTable;
		int acTable;
		int dcPredictor;
		int blocksPerLine;
		int blocksPerColumn;
		byte[] samples;
	}

	private static final class HuffmanTable {
		final int[] maxCode = new int[18];
		final int[] valueOffset = new int[18];
		final byte[] values = new byte[256];

		HuffmanTable(final byte[] data, final int countsOffset, final int valuesOffset) {
			int code = 0;
			int index = 0;
			for (int length = 1; length <= 16; length++) {
				final int count = data[countsOffset + length - 1] & 0xFF;
				this.valueOffset[length] = index - code;
				code += count;
				this.maxCode[length] = count == 0 ? -1 : code - 1;
				index += count;
				code <<= 1;
			}
			this.maxCode[17] = Integer.MAX_VALUE;
			System.arraycopy(data, valuesOffset, this.values, 0, index);
		}
	}

	private static final class BitReader {
		private final byte[] data;
		private int position;
		private int bitBuffer;
		private int bitCount;

		BitReader(final byte[] data, final int position) {
			this.data = data;
			this.position = position;
		}

		int readBit() throws IOException {
			if (this.bitCount == 0) {
				if (this.position >= this.data.length) {
					throw new IOException("Unexpected end of JPEG data");
				}
				int value = this.data[this.position++] & 0xFF;
				if (value == 0xFF) {
					final int next = this.position < this.data.length ? this.data[this.position] & 0xFF : 0;
					if (next == 0) {
						this.position++;
					}
					else {
						// A marker inside entropy coded data; the decoder pads with ones.
						value = 0xFF;
						this.position--;
					}
				}
				this.bitBuffer = value;
				this.bitCount = 8;
			}
			this.bitCount--;
			return (this.bitBuffer >> this.bitCount) & 1;
		}

		int receive(final int length) throws IOException {
			int value = 0;
			for (int i = 0; i < length; i++) {
				value = (value << 1) | readBit();
			}
			return value;
		}

		int decode(final HuffmanTable table) throws IOException {
			int code = 0;
			for (int length = 1; length <= 16; length++) {
				code = (code << 1) | readBit();
				if (code <= table.maxCode[length]) {
					return table.values[code + table.valueOffset[length]] & 0xFF;
				}
			}
			throw new IOException("Invalid Huffman code");
		}

		void resetForRestart() {
			this.bitCount = 0;
			while ((this.position + 1) < this.data.length) {
				if (((this.data[this.position] & 0xFF) == 0xFF) && ((this.data[this.position + 1] & 0xFF) >= 0xD0)
						&& ((this.data[this.position + 1] & 0xFF) <= 0xD7)) {
					this.position += 2;
					return;
				}
				this.position++;
			}
		}
	}

	public static JpegDecoder decode(final byte[] data) throws IOException {
		final int[][] quantizationTables = new int[4][];
		final HuffmanTable[] dcTables = new HuffmanTable[4];
		final HuffmanTable[] acTables = new HuffmanTable[4];
		Component[] components = null;
		int width = 0;
		int height = 0;
		int restartInterval = 0;
		int position = 2;
		if (((data[0] & 0xFF) != 0xFF) || ((data[1] & 0xFF) != 0xD8)) {
			throw new IOException("Not a JPEG stream");
		}
		while (position < data.length) {
			if ((data[position] & 0xFF) != 0xFF) {
				position++;
				continue;
			}
			final int marker = data[position + 1] & 0xFF;
			position += 2;
			if ((marker == 0xD8) || ((marker >= 0xD0) && (marker <= 0xD7)) || (marker == 0xFF)) {
				continue;
			}
			if (marker == 0xD9) {
				break;
			}
			final int length = ((data[position] & 0xFF) << 8) | (data[position + 1] & 0xFF);
			final int segmentEnd = position + length;
			int offset = position + 2;
			switch (marker) {
			case 0xDB:
				while (offset < segmentEnd) {
					final int precisionAndId = data[offset++] & 0xFF;
					final int[] table = new int[64];
					for (int i = 0; i < 64; i++) {
						if ((precisionAndId >> 4) == 0) {
							table[ZIGZAG[i]] = data[offset++] & 0xFF;
						}
						else {
							table[ZIGZAG[i]] = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
							offset += 2;
						}
					}
					quantizationTables[precisionAndId & 0xF] = table;
				}
				break;
			case 0xC0:
			case 0xC1: {
				height = ((data[offset + 1] & 0xFF) << 8) | (data[offset + 2] & 0xFF);
				width = ((data[offset + 3] & 0xFF) << 8) | (data[offset + 4] & 0xFF);
				final int componentCount = data[offset + 5] & 0xFF;
				components = new Component[componentCount];
				for (int i = 0; i < componentCount; i++) {
					final Component component = new Component();
					final int componentOffset = offset + 6 + (i * 3);
					component.id = data[componentOffset] & 0xFF;
					component.horizontalSampling = (data[componentOffset + 1] & 0xFF) >> 4;
					component.verticalSampling = data[componentOffset + 1] & 0xF;
					component.quantizationTable = data[componentOffset + 2] & 0xFF;
					components[i] = component;
				}
				break;
			}
			case 0xC2:
			case 0xC3:
				throw new IOException("Only baseline JPEG is supported");
			case 0xC4:
				while (offset < segmentEnd) {
					final int classAndId = data[offset] & 0xFF;
					int valueCount = 0;
					for (int i = 0; i < 16; i++) {
						valueCount += data[offset + 1 + i] & 0xFF;
					}
					final HuffmanTable table = new HuffmanTable(data, offset + 1, offset + 17);
					if ((classAndId >> 4) == 0) {
						dcTables[classAndId & 0xF] = table;
					}
					else {
						acTables[classAndId & 0xF] = table;
					}
					offset += 17 + valueCount;
				}
				break;
			case 0xDD:
				restartInterval = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
				break;
			case 0xDA: {
				if (components == null) {
					throw new IOException("Scan before frame header");
				}
				final int scanComponentCount = data[offset] & 0xFF;
				final Component[] scanComponents = new Component[scanComponentCount];
				for (int i = 0; i < scanComponentCount; i++) {
					final int id = data[offset + 1 + (i * 2)] & 0xFF;
					final int tables = data[offset + 2 + (i * 2)] & 0xFF;
					for (final Component component : components) {
						if (component.id == id) {
							component.dcTable = tables >> 4;
							component.acTable = tables & 0xF;
							scanComponents[i] = component;
						}
					}
				}
				final int entropyStart = segmentEnd;
				position = decodeScan(data, entropyStart, components, scanComponents, width, height, restartInterval,
						quantizationTables, dcTables, acTables);
				continue;
			}
			default:
				break;
			}
			position = segmentEnd;
		}
		if (components == null) {
			throw new IOException("Missing JPEG frame header");
		}
		return new JpegDecoder(width, height, components.length, interleave(components, width, height));
	}

	private static int decodeScan(final byte[] data, final int start, final Component[] frameComponents,
			final Component[] scanComponents, final int width, final int height, final int restartInterval,
			final int[][] quantizationTables, final HuffmanTable[] dcTables, final HuffmanTable[] acTables)
			throws IOException {
		int maxHorizontal = 1;
		int maxVertical = 1;
		for (final Component component : frameComponents) {
			maxHorizontal = Math.max(maxHorizontal, component.horizontalSampling);
			maxVertical = Math.max(maxVertical, component.verticalSampling);
		}
		final int mcusPerLine = (width + (8 * maxHorizontal) - 1) / (8 * maxHorizontal);
		final int mcusPerColumn = (height + (8 * maxVertical) - 1) / (8 * maxVertical);
		for (final Component component : frameComponents) {
			component.blocksPerLine = mcusPerLine * component.horizontalSampling;
			component.blocksPerColumn = mcusPerColumn * component.verticalSampling;
			if (component.samples == null) {
				component.samples = new byte[component.blocksPerLine * component.blocksPerColumn * 64];
			}
		}

		final BitReader reader = new BitReader(data, start);
		final int[] coefficients = new int[64];
		final float[] block = new float[64];
		final boolean singleComponent = scanComponents.length == 1;
		final int totalUnits = singleComponent
				? ((scanComponents[0].blocksPerLine * scanComponents[0].blocksPerColumn))
				: (mcusPerLine * mcusPerColumn);
		for (int unit = 0; unit < totalUnits; unit++) {
			if ((restartInterval > 0) && (unit > 0) && ((unit % restartInterval) == 0)) {
				reader.resetForRestart();
				for (final Component component : scanComponents) {
					component.dcPredictor = 0;
				}
			}
			if (singleComponent) {
				final Component component = scanComponents[0];
				final int blockRow = unit / component.blocksPerLine;
				final int blockColumn = unit % component.blocksPerLine;
				decodeBlock(reader, component, quantizationTables, dcTables, acTables, coefficients, block,
						blockRow, blockColumn);
			}
			else {
				final int mcuRow = unit / mcusPerLine;
				final int mcuColumn = unit % mcusPerLine;
				for (final Component component : scanComponents) {
					for (int v = 0; v < component.verticalSampling; v++) {
						for (int h = 0; h < component.horizontalSampling; h++) {
							decodeBlock(reader, component, quantizationTables, dcTables, acTables, coefficients,
									block, (mcuRow * component.verticalSampling) + v,
									(mcuColumn * component.horizontalSampling) + h);
						}
					}
				}
			}
		}
		// Continue after the entropy coded data at the next marker that is not a
		// restart marker or a stuffed byte.
		int position = reader.position;
		while ((position + 1) < data.length) {
			if ((data[position] & 0xFF) == 0xFF) {
				final int next = data[position + 1] & 0xFF;
				if ((next != 0) && ((next < 0xD0) || (next > 0xD7))) {
					return position;
				}
			}
			position++;
		}
		return data.length;
	}

	private static void decodeBlock(final BitReader reader, final Component component,
			final int[][] quantizationTables, final HuffmanTable[] dcTables, final HuffmanTable[] acTables,
			final int[] coefficients, final float[] block, final int blockRow, final int blockColumn)
			throws IOException {
		java.util.Arrays.fill(coefficients, 0);
		final int dcLength = reader.decode(dcTables[component.dcTable]);
		component.dcPredictor += dcLength == 0 ? 0 : extend(reader.receive(dcLength), dcLength);
		coefficients[0] = component.dcPredictor;
		int k = 1;
		while (k < 64) {
			final int symbol = reader.decode(acTables[component.acTable]);
			final int run = symbol >> 4;
			final int size = symbol & 0xF;
			if (size == 0) {
				if (run < 15) {
					break;
				}
				k += 16;
				continue;
			}
			k += run;
			if (k > 63) {
				break;
			}
			coefficients[ZIGZAG[k]] = extend(reader.receive(size), size);
			k++;
		}
		if ((blockRow >= component.blocksPerColumn) || (blockColumn >= component.blocksPerLine)) {
			return;
		}
		final int[] quantization = quantizationTables[component.quantizationTable];
		final float[] rows = new float[64];
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				float sum = 0;
				for (int u = 0; u < 8; u++) {
					sum += IDCT_COSINES[x][u] * coefficients[(y * 8) + u] * quantization[(y * 8) + u];
				}
				rows[(y * 8) + x] = sum;
			}
		}
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				float sum = 0;
				for (int v = 0; v < 8; v++) {
					sum += IDCT_COSINES[y][v] * rows[(v * 8) + x];
				}
				block[(y * 8) + x] = sum;
			}
		}
		final int samplesPerLine = component.blocksPerLine * 8;
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				final int value = Math.round(block[(y * 8) + x] + 128);
				component.samples[(((blockRow * 8) + y) * samplesPerLine) + (blockColumn * 8) + x] = (byte) Math
						.max(0, Math.min(255, value));
			}
		}
	}

	private static int extend(final int value, final int length) {
		return value < (1 << (length - 1)) ? value - (1 << length) + 1 : value;
	}

	private static byte[] interleave(final Component[] components, final int width, final int height) {
		int maxHorizontal = 1;
		int maxVertical = 1;
		for (final Component component : components) {
			maxHorizontal = Math.max(maxHorizontal, component.horizontalSampling);
			maxVertical = Math.max(maxVertical, component.verticalSampling);
		}
		final byte[] out = new byte[width * height * components.length];
		for (int c = 0; c < components.length; c++) {
			final Component component = components[c];
			final int samplesPerLine = component.blocksPerLine * 8;
			for (int y = 0; y < height; y++) {
				final int sourceY = (y * component.verticalSampling) / maxVertical;
				for (int x = 0; x < width; x++) {
					final int sourceX = (x * component.horizontalSampling) / maxHorizontal;
					out[(((y * width) + x) * components.length) + c] = component.samples[(sourceY * samplesPerLine)
							+ sourceX];
				}
			}
		}
		return out;
	}
}
