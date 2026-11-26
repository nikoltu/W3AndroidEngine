package com.etheller.warsmash.units;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.StringBundle;

public class DataTable implements ObjectData {
	public static final boolean DEBUG = false;

	private static final Set<String> ACCUMULATED_LIST_FIELDS = new HashSet<>(
			Arrays.asList("researches", "trains", "builds", "sellunits", "sellitems", "makeitems", "upgrade", "requires"));

	Map<StringKey, Element> dataTable = new LinkedHashMap<>();

	private final StringBundle worldEditStrings;

	public DataTable(final StringBundle worldEditStrings) {
		this.worldEditStrings = worldEditStrings;
	}

	@Override
	public String getLocalizedString(final String key) {
		return this.worldEditStrings.getString(key);
	}

	@Override
	public Set<String> keySet() {
		final Set<String> outputKeySet = new HashSet<>();
		final Set<StringKey> internalKeySet = this.dataTable.keySet();
		for (final StringKey key : internalKeySet) {
			outputKeySet.add(key.getString());
		}
		return outputKeySet;
	}

	public void readTXT(final InputStream inputStream) {
		try {
			readTXT(inputStream, false);
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void readTXT(final File f) {
		readTXT(f, false);
	}

	public void readTXT(final File f, final boolean canProduce) {
		try {
			readTXT(new FileInputStream(f), canProduce);
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void readSLK(final File f) {
		try {
			readSLK(new FileInputStream(f));
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void readTXT(final InputStream txt, final boolean canProduce) throws IOException {
		if (txt == null) {
			return;
		}
		final BufferedReader reader = new BufferedReader(new InputStreamReader(txt, "utf-8"));
		// BOM marker will only appear on the very beginning
		reader.mark(4);
		if ('\ufeff' != reader.read()) {
			reader.reset(); // not the BOM marker
		}

		String input = "";
		Element currentUnit = null;
		final boolean first = true;
		final Set<String> listFieldsRead = new HashSet<>();
		while ((input = reader.readLine()) != null) {
			if (DEBUG) {
				Log.info(input);
			}
			if (input.startsWith("//")) {
				continue;
			}
			if (input.startsWith("[") && input.contains("]")) {
				final int start = input.indexOf("[") + 1;
				final int end = input.indexOf("]");
				final String newKey = input.substring(start, end);
				final String newKeyBase = newKey;
				currentUnit = this.dataTable.get(new StringKey(newKey));
				if (currentUnit == null) {
					currentUnit = new Element(newKey, this);
					if (canProduce) {
						currentUnit = new LMUnit(newKey, this);
						this.dataTable.put(new StringKey(newKey), currentUnit);
					}
				}
			}
			else if (input.contains("=")) {
				final int eIndex = input.indexOf("=");
				final String fieldValue = input.substring(eIndex + 1);
				final StringBuilder builder = new StringBuilder();
				boolean withinQuotedString = false;
				final String fieldName = input.substring(0, eIndex);
				boolean wasSlash = false;
				final List<String> values = new ArrayList<>();
				for (int i = 0; i < fieldValue.length(); i++) {
					final char c = fieldValue.charAt(i);
					final boolean isSlash = c == '/';
					if (isSlash && wasSlash && !withinQuotedString) {
						builder.setLength(builder.length() - 1);
						break; // comment starts here
					}
					if (c == '\"') {
						withinQuotedString = !withinQuotedString;
					}
					else if (!withinQuotedString && (c == ',')) {
						values.add(builder.toString().trim());
						builder.setLength(0); // empty buffer
					}
					else {
						builder.append(c);
					}
					wasSlash = isSlash;
				}
				if (builder.length() > 0) {
					if (currentUnit == null) {
						Log.info("null for " + input);
					}
					values.add(builder.toString().trim());
				}
				if (ACCUMULATED_LIST_FIELDS.contains(fieldName.trim().toLowerCase(Locale.ROOT))
						&& !listFieldsRead.add(currentUnit.getId() + "=" + fieldName.trim().toLowerCase(Locale.ROOT))) {
					// A list given on two lines of one section is one list (the Temple of the Damned has its
					// researches on two Researches lines).
					final List<String> merged = new ArrayList<>(currentUnit.getFieldAsList(fieldName.trim()));
					for (final String value : values) {
						if (!merged.contains(value)) {
							merged.add(value);
						}
					}
					values.clear();
					values.addAll(merged);
				}
				currentUnit.setField(fieldName, values);
			}
		}

		reader.close();
	}

	public void readSLK(final InputStream txt) throws IOException {
		if (txt == null) {
			return;
		}
		final StringBuilder content = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(txt, "utf-8"))) {
			final char[] buffer = new char[8192];
			int read;
			while ((read = in.read(buffer)) != -1) {
				content.append(buffer, 0, read);
			}
		}
		final String text = content.toString();
		if (hasRowsStartingAfterTheKey(text)) {
			readSLKCells(text);
			return;
		}
		final BufferedReader reader = new BufferedReader(new StringReader(text));

		String input = "";
		Element currentUnit = null;
		input = reader.readLine();
		if (!input.contains("ID")) {
			Log.warn("Formatting of SLK is unusual.");
		}
		input = reader.readLine();
		while (input.startsWith("P;") || input.startsWith("F;")) {
			input = reader.readLine();
		}
		final int yIndex = input.indexOf("Y") + 1;
		final int xIndex = input.indexOf("X") + 1;
		int colCount = 0;
		int rowCount = 0;
		boolean flipMode = false;
		if (xIndex > yIndex) {
			colCount = Integer.parseInt(input.substring(xIndex, input.lastIndexOf(";")));
			rowCount = Integer.parseInt(input.substring(yIndex, xIndex - 2));
		}
		else {
			rowCount = Integer.parseInt(input.substring(yIndex, input.lastIndexOf(";")));
			colCount = Integer.parseInt(input.substring(xIndex, yIndex - 2));
			flipMode = true;
		}
		int rowStartCount = 0;
		String[] dataNames = new String[colCount];
		int col = 0;
		int lastFieldId = 0;
		int lastColumn = 0;
		while ((input = reader.readLine()) != null) {
			if (DEBUG) {
				Log.info(input);
			}
			if (input.startsWith("E")) {
				break;
			}
			if (input.startsWith("O;")) {
				continue;
			}
			if (input.contains("X1;") || input.endsWith(";X1")) {
				rowStartCount++;
				col = 0;
			}
			else {
				col++;
			}
			String kInput;
			if (input.startsWith("F;")) {
				kInput = reader.readLine();
				if (DEBUG) {
					Log.info(kInput);
				}
			}
			else {
				kInput = input;
			}
			if (rowStartCount <= 1) {
				final int subXIndex = input.indexOf("X");
				final int subYIndex = input.indexOf("Y");
				if ((subYIndex >= 0) && (subYIndex < subXIndex)) {
					final int eIndex = kInput.indexOf("K");
					final int fieldIdEndIndex = kInput != input ? input.length() : eIndex - 1;
					if ((eIndex == -1) || (kInput.charAt(eIndex - 1) != ';')) {
						continue;
					}
					final int fieldId;
					if (subXIndex < 0) {
						if (lastFieldId == 0) {
							rowStartCount++;
						}
						fieldId = lastFieldId + 1;
					}
					else {
						fieldId = Integer.parseInt(input.substring(subXIndex + 1, fieldIdEndIndex));
					}

					final int quotationIndex = kInput.indexOf("\"");
					if ((fieldId - 1) >= dataNames.length) {
						dataNames = Arrays.copyOf(dataNames, fieldId);
					}
					if (quotationIndex == -1) {
						dataNames[fieldId - 1] = kInput.substring(eIndex + 1);
					}
					else {
						dataNames[fieldId - 1] = kInput.substring(quotationIndex + 1, kInput.lastIndexOf("\""));
					}
					lastFieldId = fieldId;
					continue;
				}
				else {
					int eIndex = kInput.indexOf("K");
					if ((eIndex == -1) || (kInput.charAt(eIndex - 1) != ';')) {
						continue;
					}
					final int fieldId;
					if (subXIndex < 0) {
						if (lastFieldId == 0) {
							rowStartCount++;
						}
						fieldId = lastFieldId + 1;
					}
					else {
						if (flipMode && input.contains("Y") && (input == kInput)) {
							eIndex = Math.min(subYIndex, eIndex);
						}
						final String afterX = input.substring(subXIndex + 1);
						int afterXSemicolon = afterX.indexOf(';');
						if (afterXSemicolon == -1) {
							afterXSemicolon = afterX.length();
						}
						fieldId = Integer.parseInt(afterX.substring(0, afterXSemicolon));
//						final int fieldIdEndIndex = kInput != input ? input.length() : eIndex - 1;
					}

					final int quotationIndex = kInput.indexOf("\"");
					if ((fieldId - 1) >= dataNames.length) {
						dataNames = Arrays.copyOf(dataNames, fieldId);
					}
					if (quotationIndex == -1) {
						dataNames[fieldId - 1] = kInput.substring(eIndex + 1, kInput.length());
					}
					else {
						dataNames[fieldId - 1] = kInput.substring(quotationIndex + 1, kInput.lastIndexOf("\""));
					}
					lastFieldId = fieldId;
					continue;
				}
			}
			// A cell without X stays in the previous cell's column (3.0 DialogueHumanBase.slk starts rows so).
			final boolean keyCell = input.contains("X1;") || ((input != kInput) && input.endsWith("X1"))
					|| (input.startsWith("C;Y") && (slkColumn(input) == -1) && (lastColumn == 1));
			if (input.startsWith("C;")) {
				final int column = slkColumn(input);
				if (column != -1) {
					lastColumn = column;
				}
			}
			if (keyCell) {
				final int start = kInput.indexOf("\"") + 1;
				final int end = kInput.lastIndexOf("\"");
				if ((start - 1) != end) {
					final String newKey = kInput.substring(start, end);
					currentUnit = this.dataTable.get(new StringKey(newKey));
					if (currentUnit == null) {
						currentUnit = new Element(newKey, this);
						this.dataTable.put(new StringKey(newKey), currentUnit);
					}
				}
			}
			else if (kInput.contains("K")) {
				final int subXIndex = input.indexOf("X");
				final int eIndex = kInput.indexOf("K");
				int eIndexCutoff = eIndex;
				if (flipMode && kInput.contains("Y")) {
					eIndexCutoff = Math.min(kInput.indexOf("Y"), eIndexCutoff);
				}
				final int nIndex = kInput.indexOf("N");
				eIndexCutoff = ((nIndex != -1) && (nIndex < eIndexCutoff)) ? nIndex : eIndexCutoff;
				int fieldIdEndIndex = kInput != input ? input.length() : eIndexCutoff - 1;
				if (subXIndex != -1) {
					// "C;X4;Y2060;K..." (X before Y) must not feed the Y field into the column number.
					final int fieldIdSemicolon = input.indexOf(';', subXIndex + 1);
					if ((fieldIdSemicolon != -1) && (fieldIdSemicolon < fieldIdEndIndex)) {
						fieldIdEndIndex = fieldIdSemicolon;
					}
				}
				final int fieldId = (subXIndex == -1) || (subXIndex > fieldIdEndIndex) ? 1
						: parseInt(input, subXIndex + 1, fieldIdEndIndex);
				if (dataNames[fieldId - 1] != null) {
					int valueStart = eIndex + 1;
					int valueEnd = kInput.length();
					if (((valueEnd - valueStart) > 1) && (kInput.charAt(valueStart) == '"')
							&& (kInput.charAt(valueEnd - 1) == '"')) {
						valueStart++;
						valueEnd--;
					}
					final String fieldValue = kInput.substring(valueStart, valueEnd);
					final int indexOfComma = fieldValue.indexOf(",");
					if (indexOfComma != -1) {
						final String[] splitLine = fieldValue.split(",");
						for (int splitChunkId = 0; splitChunkId < splitLine.length; splitChunkId++) {
							currentUnit.setField(dataNames[fieldId - 1], splitLine[splitChunkId], splitChunkId);
						}
					}
					else {
						currentUnit.setField(dataNames[fieldId - 1], fieldValue);
					}
				}
			}
		}

		reader.close();
	}

	/**
	 * Map optimizers write SLK rows in either direction, so a row may end with its key cell; every cell then has to be
	 * placed by its own coordinates.
	 */
	static boolean hasRowsStartingAfterTheKey(final String text) {
		final int[] position = { 1, 1 };
		int row = 1;
		boolean rowStartedOffKey = false;
		final int length = text.length();
		int start = 0;
		// the lines as BufferedReader.readLine splits them, without making them
		while (start < length) {
			int end = start;
			char c = 0;
			while ((end < length) && ((c = text.charAt(end)) != '\n') && (c != '\r')) {
				end++;
			}
			if ((end > start) && (text.charAt(start) == 'E')) {
				break;
			}
			if (((end - start) >= 2) && ((text.charAt(start) == 'C') || (text.charAt(start) == 'F'))
					&& (text.charAt(start + 1) == ';') && slkPosition(text, start, end, position)) {
				if (position[1] != row) {
					row = position[1];
					rowStartedOffKey = (row > 1) && (position[0] != 1);
				}
				else if (rowStartedOffKey && (position[0] == 1)) {
					return true;
				}
			}
			start = end + 1;
			if ((c == '\r') && (start < length) && (text.charAt(start) == '\n')) {
				start++;
			}
		}
		return false;
	}

	/** Moves position (x, y) by the record's X and Y fields; returns whether the record holds a value. */
	static boolean slkPosition(final String record, final int[] position) {
		return slkPosition(record, 0, record.length(), position);
	}

	/**
	 * {@link #slkPosition(String, int[])} for the record text.substring(start, end): the fields are read in place, as
	 * every record of a table is looked at for this.
	 */
	private static boolean slkPosition(final String text, final int start, final int end, final int[] position) {
		final int valueIndex = indexOfValue(text, start, end);
		final int fieldsEnd = valueIndex == -1 ? end : valueIndex;
		// the first field, the record type, is not a coordinate
		int fieldStart = indexOf(text, ';', start, fieldsEnd);
		while (fieldStart != -1) {
			fieldStart++;
			int fieldEnd = indexOf(text, ';', fieldStart, fieldsEnd);
			if (fieldEnd == -1) {
				fieldEnd = fieldsEnd;
			}
			if ((fieldEnd - fieldStart) > 1) {
				final char name = text.charAt(fieldStart);
				if ((name == 'X') || (name == 'Y')) {
					try {
						position[name == 'X' ? 0 : 1] = parseInt(text, fieldStart + 1, fieldEnd);
					}
					catch (final NumberFormatException e) {
						// not a coordinate
					}
				}
			}
			fieldStart = fieldEnd == fieldsEnd ? -1 : fieldEnd;
		}
		return (valueIndex != -1) && ((end - start) >= 2) && (text.charAt(start) == 'C')
				&& (text.charAt(start + 1) == ';');
	}

	/** The first c in text from from to end, or -1. */
	private static int indexOf(final String text, final char c, final int from, final int end) {
		for (int i = from; i < end; i++) {
			if (text.charAt(i) == c) {
				return i;
			}
		}
		return -1;
	}

	/** The first ";K" that ends by end, or -1. */
	private static int indexOfValue(final String text, final int from, final int end) {
		for (int i = from; i < (end - 1); i++) {
			if ((text.charAt(i) == ';') && (text.charAt(i + 1) == 'K')) {
				return i;
			}
		}
		return -1;
	}

	/** Integer.parseInt(text.substring(start, end)), without the substring for plain numbers. */
	private static int parseInt(final String text, final int start, final int end) {
		if (((end - start) > 0) && ((end - start) <= 9)) {
			int value = 0;
			int k = start;
			for (; k < end; k++) {
				final char c = text.charAt(k);
				if ((c < '0') || (c > '9')) {
					break;
				}
				value = (value * 10) + (c - '0');
			}
			if (k == end) {
				return value;
			}
		}
		return Integer.parseInt(text.substring(start, end));
	}

	private void readSLKCells(final String text) throws IOException {
		final Map<Integer, String> columnNames = new LinkedHashMap<>();
		final Map<Integer, Map<Integer, String>> rows = new LinkedHashMap<>();
		final int[] position = { 1, 1 };
		final BufferedReader reader = new BufferedReader(new StringReader(text));
		String record;
		while ((record = reader.readLine()) != null) {
			if (record.startsWith("E")) {
				break;
			}
			if (!record.startsWith("C;") && !record.startsWith("F;")) {
				continue;
			}
			if (!slkPosition(record, position)) {
				continue;
			}
			final String value = record.substring(record.indexOf(";K") + 2);
			if (position[1] == 1) {
				columnNames.put(position[0], unquote(value));
			}
			else {
				Map<Integer, String> cells = rows.get(position[1]);
				if (cells == null) {
					cells = new LinkedHashMap<>();
					rows.put(position[1], cells);
				}
				cells.put(position[0], value);
			}
		}
		for (final Map<Integer, String> cells : rows.values()) {
			final String key = cells.containsKey(1) ? unquote(cells.get(1)) : "";
			if (key.isEmpty()) {
				continue;
			}
			Element element = this.dataTable.get(new StringKey(key));
			if (element == null) {
				element = new Element(key, this);
				this.dataTable.put(new StringKey(key), element);
			}
			for (final Map.Entry<Integer, String> cell : cells.entrySet()) {
				final String name = columnNames.get(cell.getKey());
				if ((cell.getKey() == 1) || (name == null)) {
					continue;
				}
				final String fieldValue = unquote(cell.getValue());
				if (fieldValue.indexOf(',') != -1) {
					final String[] chunks = fieldValue.split(",");
					for (int i = 0; i < chunks.length; i++) {
						element.setField(name, chunks[i], i);
					}
				}
				else {
					element.setField(name, fieldValue);
				}
			}
		}
	}

	private static String unquote(final String value) {
		if ((value.length() > 1) && value.startsWith("\"") && value.endsWith("\"")) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}

	static int slkColumn(final String record) {
		final int length = record.length();
		final int valueIndex = indexOfValue(record, 0, length);
		final int fieldsEnd = valueIndex == -1 ? length : valueIndex;
		int fieldStart = 0;
		while (fieldStart < fieldsEnd) {
			int fieldEnd = indexOf(record, ';', fieldStart, fieldsEnd);
			if (fieldEnd == -1) {
				fieldEnd = fieldsEnd;
			}
			if ((fieldEnd > fieldStart) && (record.charAt(fieldStart) == 'X')) {
				try {
					return parseInt(record, fieldStart + 1, fieldEnd);
				}
				catch (final NumberFormatException e) {
					return -1;
				}
			}
			fieldStart = fieldEnd + 1;
		}
		return -1;
	}

	@Override
	public Element get(final String id) {
		return this.dataTable.get(new StringKey(id));
	}

	public void applyQualitySuffixes(final String[] preferred, final String[] fallbacks) {
		for (final Element element : this.dataTable.values()) {
			element.applyQualitySuffixes(preferred, fallbacks);
		}
	}

	public void applyQualityOverrides(final String[] qualities, final Set<String> baseNames) {
		for (final Element element : this.dataTable.values()) {
			element.applyQualityOverrides(qualities, baseNames);
		}
	}

	@Override
	public void cloneUnit(final String parentId, final String cloneId) {
		final Element parentEntry = get(parentId);
		if (parentEntry != null) {
			final LMUnit cloneUnit = new LMUnit(cloneId, this);
			for (final String key : parentEntry.keySet()) {
				final List<String> fieldList = parentEntry.getFieldAsList(key);
				for (int i = 0; i < fieldList.size(); i++) {
					cloneUnit.setField(key, fieldList.get(i), i);
				}
			}
			put(cloneId, cloneUnit);
		}
	}

	@Override
	public void inheritFrom(String childKey, String parentKey) {
		final Element childEntry = get(childKey);
		final Element parentEntry = get(parentKey);
		if (parentEntry != null) {
			if (childEntry != null) {
				for (final String key : parentEntry.keySet()) {
					// The tech requirement belongs to the rawcode itself: Sentinel without research and the Warlock's
					// Unholy Frenzy do not take the research their base ability needs.
					if (!childEntry.hasField(key) && !"Requires".equalsIgnoreCase(key)
							&& !"Requiresamount".equalsIgnoreCase(key)) {
						final List<String> fieldList = parentEntry.getFieldAsList(key);
						for (int i = 0; i < fieldList.size(); i++) {
							childEntry.setField(key, fieldList.get(i), i);
						}
					}
				}
			}
			else {
				cloneUnit(parentKey, childKey);
			}
		}
	}

	@Override
	public void setValue(final String slk, final String id, final String field, final String value) {
		get(id).setField(field, value);
	}

	public void put(final String id, final Element e) {
		this.dataTable.put(new StringKey(id), e);
	}
}