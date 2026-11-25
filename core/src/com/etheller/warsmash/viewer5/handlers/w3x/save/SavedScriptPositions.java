package com.etheller.warsmash.viewer5.handlers.w3x.save;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.etheller.interpreter.ast.execution.JassStackFrame;
import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.execution.instruction.BeginFunctionInstruction;
import com.etheller.interpreter.ast.execution.instruction.JassInstruction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.InstructionCodeJassValue;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassType;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;

/**
 * Script positions in a save by function instead of by place in the compiled program. A thread's next instruction,
 * the return addresses on its stack and code values are stored as they were, with a table of the functions they are
 * in: where each function started, its length and a hash of its compiled code. Loading finds each function again by
 * name and moves the positions there, so a change to other functions, or anything that only shifts the program, keeps
 * the save loadable. A position inside a function needs that function unchanged; a position at a function's start
 * (a code value, a thread not started yet) only needs the function to exist.
 */
final class SavedScriptPositions {
	private SavedScriptPositions() {
	}

	/**
	 * One scope's compiled program cut into functions: each runs from its BeginFunctionInstruction up to the next.
	 * Names repeat for globals blocks and methods, so the key counts them.
	 */
	static final class Program {
		final GlobalScope scope;
		private final List<JassInstruction> instructions;
		private final int[] starts;
		private final String[] keys;
		private final String[] sources;
		private final Map<String, Integer> segmentByKey = new HashMap<>();
		private final Map<Integer, Long> hashes = new HashMap<>();
		// A function's instructions do not change while its program lives, and every save asks again for the
		// hashes of the same functions; keyed by the function's first instruction, with its length.
		private static final Map<JassInstruction, long[]> FUNCTION_HASHES = Collections
				.synchronizedMap(new WeakHashMap<>());
		private String[] globalNames;
		private String[] nativeNames;

		Program(final GlobalScope scope) {
			this.scope = scope;
			this.instructions = scope.getInstructions();
			final List<Integer> starts = new ArrayList<>();
			final List<String> keys = new ArrayList<>();
			final List<String> sources = new ArrayList<>();
			final Map<String, Integer> occurrences = new HashMap<>();
			for (int i = 0; i < this.instructions.size(); i++) {
				final JassInstruction instruction = this.instructions.get(i);
				if (instruction instanceof BeginFunctionInstruction) {
					final BeginFunctionInstruction begin = (BeginFunctionInstruction) instruction;
					final String name = begin.getName();
					final Integer seen = occurrences.get(name);
					final int occurrence = seen == null ? 0 : seen;
					occurrences.put(name, occurrence + 1);
					final String key = occurrence == 0 ? name : name + "#" + occurrence;
					this.segmentByKey.put(key, starts.size());
					starts.add(i);
					keys.add(key);
					sources.add(begin.getSourceFile() == null ? "" : begin.getSourceFile());
				}
			}
			this.starts = new int[starts.size()];
			for (int i = 0; i < this.starts.length; i++) {
				this.starts[i] = starts.get(i);
			}
			this.keys = keys.toArray(new String[0]);
			this.sources = sources.toArray(new String[0]);
		}

		int size() {
			return this.instructions.size();
		}

		int segmentAt(final int pointer) {
			if ((pointer < 0) || (pointer >= this.instructions.size()) || (this.starts.length == 0)
					|| (pointer < this.starts[0])) {
				return -1;
			}
			final int index = Arrays.binarySearch(this.starts, pointer);
			return index >= 0 ? index : -index - 2;
		}

		Integer segmentOf(final String key) {
			return this.segmentByKey.get(key);
		}

		int segmentCount() {
			return this.starts.length;
		}

		int start(final int segment) {
			return this.starts[segment];
		}

		int length(final int segment) {
			final int end = segment + 1 < this.starts.length ? this.starts[segment + 1] : this.instructions.size();
			return end - this.starts[segment];
		}

		String key(final int segment) {
			return this.keys[segment];
		}

		String source(final int segment) {
			return this.sources[segment];
		}

		JassInstruction instructionAt(final int pointer) {
			return this.instructions.get(pointer);
		}

		/** Where the instruction is in this program, or -1. Function starts are looked at first: frames name those. */
		int indexOf(final JassInstruction instruction) {
			if (instruction instanceof BeginFunctionInstruction) {
				for (final int start : this.starts) {
					if (this.instructions.get(start) == instruction) {
						return start;
					}
				}
			}
			for (int i = 0; i < this.instructions.size(); i++) {
				if (this.instructions.get(i) == instruction) {
					return i;
				}
			}
			return -1;
		}

		/**
		 * The hash covers what the function does, not where things are: jumps and calls by function and offset,
		 * globals and natives by name. Line numbers are left out, so an edit above the function does not count.
		 */
		long hash(final int segment) {
			final Long known = this.hashes.get(segment);
			if (known != null) {
				return known;
			}
			final JassInstruction first = this.instructions.get(this.starts[segment]);
			final long[] remembered = FUNCTION_HASHES.get(first);
			if ((remembered != null) && (remembered[0] == length(segment))) {
				this.hashes.put(segment, remembered[1]);
				return remembered[1];
			}
			final StringBuilder text = new StringBuilder();
			final int end = this.starts[segment] + length(segment);
			for (int i = this.starts[segment]; i < end; i++) {
				describe(text, segment, this.instructions.get(i));
			}
			long hash = 0xcbf29ce484222325L;
			for (int i = 0; i < text.length(); i++) {
				hash ^= text.charAt(i);
				hash *= 0x100000001b3L;
			}
			this.hashes.put(segment, hash);
			FUNCTION_HASHES.put(first, new long[] { length(segment), hash });
			return hash;
		}

		private void describe(final StringBuilder out, final int segment, final JassInstruction instruction) {
			if (instruction == null) {
				out.append("null;");
				return;
			}
			out.append(instruction.getClass().getName());
			for (final Field field : hashedFields(instruction.getClass())) {
				final String name = field.getName();
				out.append('|').append(name).append('=');
				try {
					if (field.getType() == int.class) {
						final int value = field.getInt(instruction);
						if (isPointer(name)) {
							appendPointer(out, segment, value);
						}
						else if ("globalId".equals(name)) {
							out.append(globalName(value));
						}
						else if ("nativeId".equals(name)) {
							out.append(nativeName(value));
						}
						else {
							out.append(value);
						}
					}
					else {
						describeValue(out, segment, field.get(instruction));
					}
				}
				catch (final IllegalAccessException exc) {
					throw new IllegalStateException(exc);
				}
			}
			out.append(';');
		}

		private void describeValue(final StringBuilder out, final int segment, final Object value) {
			if (value == null) {
				out.append("null");
			}
			else if (value instanceof CodeJassValue) {
				final Integer pointer = ((CodeJassValue) value).getUserFunctionInstructionPtr();
				// The class was named CodeJassValue before code of other languages existed; saved names hash this text.
				out.append(value.getClass() == InstructionCodeJassValue.class ? CodeJassValue.class.getName()
						: value.getClass().getName()).append('(');
				appendPointer(out, segment, pointer == null ? -1 : pointer);
				out.append(')');
			}
			else if (value instanceof IntegerJassValue) {
				out.append("i(").append(((IntegerJassValue) value).getValue()).append(')');
			}
			else if (value instanceof RealJassValue) {
				// Bits, not text: the two platforms need not print a double the same way.
				out.append("r(").append(Double.doubleToLongBits(((RealJassValue) value).getValue())).append(')');
			}
			else if (value instanceof StringJassValue) {
				out.append("s(").append(((StringJassValue) value).getValue()).append(')');
			}
			else if (value instanceof BooleanJassValue) {
				out.append("b(").append(((BooleanJassValue) value).getValue()).append(')');
			}
			else if (value instanceof HandleJassValue) {
				out.append("h(").append(((HandleJassValue) value).getType().getName()).append(')');
			}
			else if (value instanceof JassType) {
				out.append("t(").append(((JassType) value).getName()).append(')');
			}
			else if ((value instanceof String) || (value instanceof Integer) || (value instanceof Boolean)
					|| (value instanceof Enum)) {
				out.append(value);
			}
			else {
				// Struct values and converters hold state or nothing to compare; their kind is what the code fixes.
				out.append(value.getClass().getName());
			}
		}

		private void appendPointer(final StringBuilder out, final int segment, final int pointer) {
			final int target = segmentAt(pointer);
			if (target < 0) {
				out.append(pointer < 0 ? "none" : "outside");
			}
			else if (target == segment) {
				out.append('@').append(pointer - this.starts[segment]);
			}
			else {
				out.append("->").append(this.keys[target]).append('@').append(pointer - this.starts[target]);
			}
		}

		private String globalName(final int id) {
			if (this.globalNames == null) {
				this.globalNames = namesOf(this.scope.getGlobalIds());
			}
			return (id >= 0) && (id < this.globalNames.length) ? this.globalNames[id] : "?" + id;
		}

		private String nativeName(final int id) {
			if (this.nativeNames == null) {
				this.nativeNames = namesOf(this.scope.getNativeIds());
			}
			return (id >= 0) && (id < this.nativeNames.length) ? this.nativeNames[id] : "?" + id;
		}

		private static String[] namesOf(final Map<String, Integer> ids) {
			int size = 0;
			for (final Integer id : ids.values()) {
				size = Math.max(size, id + 1);
			}
			final String[] names = new String[size];
			for (final Map.Entry<String, Integer> entry : ids.entrySet()) {
				names[entry.getValue()] = entry.getKey();
			}
			return names;
		}
	}

	private static final Map<Class<?>, List<Field>> HASHED_FIELDS = new HashMap<>();

	private static synchronized List<Field> hashedFields(final Class<?> type) {
		List<Field> fields = HASHED_FIELDS.get(type);
		if (fields == null) {
			fields = new ArrayList<>();
			for (Class<?> current = type; (current != null) && (current != Object.class); current = current
					.getSuperclass()) {
				for (final Field field : current.getDeclaredFields()) {
					if (!Modifier.isStatic(field.getModifiers()) && !"lineNo".equals(field.getName())
							&& !"sourceFile".equals(field.getName())) {
						field.setAccessible(true);
						fields.add(field);
					}
				}
			}
			// Reflection gives fields in no fixed order, and the hash must not depend on the platform.
			Collections.sort(fields, new Comparator<Field>() {
				@Override
				public int compare(final Field a, final Field b) {
					final int byName = a.getName().compareTo(b.getName());
					return byName != 0 ? byName
							: a.getDeclaringClass().getName().compareTo(b.getDeclaringClass().getName());
				}
			});
			HASHED_FIELDS.put(type, fields);
		}
		return fields;
	}

	private static boolean isPointer(final String fieldName) {
		return fieldName.toLowerCase(Locale.ROOT).contains("instructionp");
	}

	/** A function a saved position or reference is in, as it was when saving. */
	static final class Entry {
		final String key;
		final String source;
		final int savedStart;
		final int length;
		final long hash;
		/** Whether a position inside the function (not at its start) was saved, so its code must be the same. */
		boolean exact;

		Entry(final String key, final String source, final int savedStart, final int length, final long hash,
				final boolean exact) {
			this.key = key;
			this.source = source;
			this.savedStart = savedStart;
			this.length = length;
			this.hash = hash;
			this.exact = exact;
		}
	}

	/** The functions saved positions are in, per scope in the save's order (map first, then AI). */
	static final class Table {
		final List<Map<String, Entry>> scopes = new ArrayList<>();
		/**
		 * Per scope, the type of each global the save holds a value for, as the type's name; empty in saves before
		 * format 4. Globals come back by name, so a global whose type changed would get a value of the old type.
		 */
		final List<Map<String, String>> globalTypes = new ArrayList<>();

		void recordGlobals(final List<GlobalScope> globalScopes) {
			this.globalTypes.clear();
			for (final GlobalScope scope : globalScopes) {
				final Map<String, String> types = new LinkedHashMap<>();
				for (final Map.Entry<String, Integer> global : scope.getGlobalIds().entrySet()) {
					types.put(global.getKey(), scope.getAssignableGlobalById(global.getValue()).getType().getName());
				}
				this.globalTypes.add(types);
			}
		}

		void write(final DataOutputStream out) throws IOException {
			out.writeInt(this.scopes.size());
			for (final Map<String, Entry> entries : this.scopes) {
				out.writeInt(entries.size());
				for (final Entry entry : entries.values()) {
					out.writeUTF(entry.key);
					out.writeUTF(entry.source);
					out.writeInt(entry.savedStart);
					out.writeInt(entry.length);
					out.writeLong(entry.hash);
					out.writeBoolean(entry.exact);
				}
			}
			out.writeInt(this.globalTypes.size());
			for (final Map<String, String> types : this.globalTypes) {
				out.writeInt(types.size());
				for (final Map.Entry<String, String> type : types.entrySet()) {
					out.writeUTF(type.getKey());
					out.writeUTF(type.getValue());
				}
			}
		}

		static Table read(final DataInputStream in, final boolean withGlobalTypes) throws IOException {
			final Table table = new Table();
			final int scopeCount = in.readInt();
			for (int i = 0; i < scopeCount; i++) {
				final Map<String, Entry> entries = new LinkedHashMap<>();
				final int count = in.readInt();
				for (int j = 0; j < count; j++) {
					final Entry entry = new Entry(in.readUTF(), in.readUTF(), in.readInt(), in.readInt(), in.readLong(),
							in.readBoolean());
					entries.put(entry.key, entry);
				}
				table.scopes.add(entries);
			}
			if (withGlobalTypes) {
				final int typeScopes = in.readInt();
				for (int i = 0; i < typeScopes; i++) {
					final Map<String, String> types = new LinkedHashMap<>();
					final int count = in.readInt();
					for (int j = 0; j < count; j++) {
						types.put(in.readUTF(), in.readUTF());
					}
					table.globalTypes.add(types);
				}
			}
			return table;
		}
	}

	/** Collects, while the graph is written, every function a stored position or reference is in. */
	static final class Recorder {
		private final List<Program> programs;
		private final Table table = new Table();
		private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());

		Recorder(final List<Program> programs) {
			this.programs = programs;
			for (int i = 0; i < programs.size(); i++) {
				this.table.scopes.add(new LinkedHashMap<String, Entry>());
			}
		}

		Table table() {
			return this.table;
		}

		void thread(final JassThread thread) {
			if (!this.seen.add(thread)) {
				return;
			}
			final int scope = scopeIndex(this.programs, thread.globalScope);
			if (scope < 0) {
				return;
			}
			position(scope, thread.instructionPtr);
			for (JassStackFrame frame = thread.stackFrame; frame != null; frame = frame.stackBase) {
				// A frame returning to -1 ends the thread; the frames under it are never returned to.
				if (frame.returnAddressInstructionPtr < 0) {
					break;
				}
				position(scope, frame.returnAddressInstructionPtr);
			}
		}

		/** A code value that is not a script literal (casts, methods); it knows no scope, so it goes by the sizes. */
		void inlineCode(final CodeJassValue code) {
			if (!this.seen.add(code)) {
				return;
			}
			final Integer pointer = code.getUserFunctionInstructionPtr();
			if ((pointer != null) && (pointer >= 0)) {
				final List<Integer> sizes = new ArrayList<>();
				for (final Program program : this.programs) {
					sizes.add(program.size());
				}
				final int scope = inlineCodeScope(sizes, pointer);
				if (scope >= 0) {
					position(scope, pointer);
				}
			}
		}

		void function(final int scope, final int segment, final int offset) {
			add(scope, segment, offset != 0);
		}

		private void position(final int scope, final int pointer) {
			if (pointer < 0) {
				return;
			}
			final Program program = this.programs.get(scope);
			final int segment = program.segmentAt(pointer);
			if (segment < 0) {
				throw new IllegalStateException("Script position " + pointer + " is in no function");
			}
			add(scope, segment, pointer != program.start(segment));
		}

		private void add(final int scope, final int segment, final boolean exact) {
			final Program program = this.programs.get(scope);
			final Map<String, Entry> entries = this.table.scopes.get(scope);
			final Entry entry = entries.get(program.key(segment));
			if (entry == null) {
				entries.put(program.key(segment), new Entry(program.key(segment), program.source(segment),
						program.start(segment), program.length(segment), program.hash(segment), exact));
			}
			else if (exact) {
				entry.exact = true;
			}
		}
	}

	static int scopeIndex(final List<Program> programs, final GlobalScope scope) {
		for (int i = 0; i < programs.size(); i++) {
			if (programs.get(i).scope == scope) {
				return i;
			}
		}
		return -1;
	}

	static int inlineCodeScope(final List<Integer> sizes, final int pointer) {
		for (int i = 0; i < sizes.size(); i++) {
			if (pointer < sizes.get(i)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Why the saved positions cannot go into these programs, or null. A null program, or {@code skipMissing}, is a
	 * program that may not have every function the loading game would have (a running game asked ahead): functions
	 * it does not have are passed over, and a missing program is checked against the map's.
	 */
	static String mismatch(final Table table, final List<Program> programs, final boolean skipMissing) {
		if (table.scopes.size() != programs.size()) {
			return "Saved with " + table.scopes.size() + " script programs, the game has " + programs.size();
		}
		for (int i = 0; i < table.scopes.size(); i++) {
			final Program program = programs.get(i) != null ? programs.get(i) : programs.get(0);
			final boolean lenient = skipMissing || (programs.get(i) == null);
			for (final Entry entry : table.scopes.get(i).values()) {
				final Integer segment = program.segmentOf(entry.key);
				if (segment == null) {
					if (!lenient) {
						return "The scripts no longer have the function " + entry.key + " (" + entry.source + ")";
					}
				}
				else if (entry.exact && (program.hash(segment) != entry.hash)) {
					return "The function " + entry.key + " (" + entry.source + ") changed since the game was saved";
				}
			}
			if (i < table.globalTypes.size()) {
				final Map<String, Integer> ids = program.scope.getGlobalIds();
				for (final Map.Entry<String, String> saved : table.globalTypes.get(i).entrySet()) {
					// A global that is gone keeps being passed over, as before: its value is read and dropped.
					final Integer id = ids.get(saved.getKey());
					if (id == null) {
						continue;
					}
					final String now = program.scope.getAssignableGlobalById(id).getType().getName();
					if (!now.equals(saved.getValue())) {
						return "The global " + saved.getKey() + " is now " + now + ", it was " + saved.getValue()
								+ " when the game was saved";
					}
				}
			}
		}
		return null;
	}

	/** Where each saved function is in the programs now; made after {@link #mismatch} found nothing. */
	static final class Mapping {
		/** Per scope, sorted by saved start: saved start, length, start now. */
		private final List<int[][]> ranges = new ArrayList<>();

		Mapping(final Table table, final List<Program> programs) {
			for (int i = 0; i < table.scopes.size(); i++) {
				final List<int[]> scopeRanges = new ArrayList<>();
				for (final Entry entry : table.scopes.get(i).values()) {
					scopeRanges.add(new int[] { entry.savedStart, entry.length,
							programs.get(i).start(programs.get(i).segmentOf(entry.key)) });
				}
				Collections.sort(scopeRanges, new Comparator<int[]>() {
					@Override
					public int compare(final int[] a, final int[] b) {
						return Integer.compare(a[0], b[0]);
					}
				});
				this.ranges.add(scopeRanges.toArray(new int[0][]));
			}
		}

		int translate(final int scope, final int pointer) {
			if (pointer < 0) {
				return pointer;
			}
			final int[][] scopeRanges = this.ranges.get(scope);
			int low = 0;
			int high = scopeRanges.length - 1;
			while (low <= high) {
				final int middle = (low + high) >>> 1;
				final int[] range = scopeRanges[middle];
				if (pointer < range[0]) {
					high = middle - 1;
				}
				else if (pointer >= (range[0] + range[1])) {
					low = middle + 1;
				}
				else {
					return range[2] + (pointer - range[0]);
				}
			}
			throw new IllegalStateException("Script position " + pointer + " of program " + scope
					+ " is in no saved function");
		}

		/**
		 * Moves what the graph read: thread positions and return addresses, and code values that are not script
		 * literals (literals come back by name).
		 */
		void apply(final List<JassThread> threads, final List<CodeJassValue> codes, final List<Program> programs,
				final List<Integer> savedSizes) {
			final Set<Object> moved = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
			for (final JassThread thread : threads) {
				final int scope = scopeIndex(programs, thread.globalScope);
				if ((scope < 0) || !moved.add(thread)) {
					continue;
				}
				thread.instructionPtr = translate(scope, thread.instructionPtr);
				for (JassStackFrame frame = thread.stackFrame; frame != null; frame = frame.stackBase) {
					if (frame.returnAddressInstructionPtr < 0) {
						break;
					}
					if (moved.add(frame)) {
						frame.returnAddressInstructionPtr = translate(scope, frame.returnAddressInstructionPtr);
					}
				}
			}
			for (final CodeJassValue code : codes) {
				final Integer pointer = code.getUserFunctionInstructionPtr();
				if ((pointer == null) || (pointer < 0) || !moved.add(code)) {
					continue;
				}
				final int scope = inlineCodeScope(savedSizes, pointer);
				if (scope >= 0) {
					setCodePointer(code, translate(scope, pointer));
				}
			}
		}
	}

	private static void setCodePointer(final CodeJassValue code, final int pointer) {
		try {
			final Field field = InstructionCodeJassValue.class.getDeclaredField("userFunctionInstructionPtr");
			field.setAccessible(true);
			field.set(code, pointer);
		}
		catch (final ReflectiveOperationException exc) {
			throw new IllegalStateException(exc);
		}
	}
}
