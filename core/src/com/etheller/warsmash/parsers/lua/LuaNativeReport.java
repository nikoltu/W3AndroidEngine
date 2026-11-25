package com.etheller.warsmash.parsers.lua;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.etheller.interpreter.ast.function.NativeJassFunction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.warsmash.lua.LuaState;

/**
 * Which of the natives that common.j declares a Lua script can call: every declared native should be a function of the
 * script, and one that is not is either one the engine has no code for or one the engine registered without common.j
 * declaring it.
 */
public final class LuaNativeReport {
	private static final Pattern DECLARATION = Pattern.compile("^\\s*(?:constant\\s+)?native\\s+(\\w+)",
			Pattern.MULTILINE);

	private final List<String> declared;
	private final List<String> callable = new ArrayList<>();
	private final List<String> withoutCode = new ArrayList<>();
	private final List<String> notInProgram = new ArrayList<>();
	private final List<String> hidden = new ArrayList<>();
	private final List<String> undeclared;

	private LuaNativeReport(final List<String> declared, final Collection<String> undeclared) {
		this.declared = declared;
		this.undeclared = new ArrayList<>(undeclared);
	}

	/** The names of the natives a common.j declares, in file order. */
	public static List<String> declaredNatives(final String commonJ) {
		final Set<String> names = new LinkedHashSet<>();
		final Matcher matcher = DECLARATION.matcher(commonJ);
		while (matcher.find()) {
			names.add(matcher.group(1));
		}
		return new ArrayList<>(names);
	}

	/**
	 * @param commonJ the text of the common.j the game was started with
	 * @param undeclaredByEngine the natives the engine made code for that no script declared
	 */
	public static LuaNativeReport compute(final String commonJ, final LuaEnvironment environment,
			final Collection<String> undeclaredByEngine) {
		final LuaNativeReport report = new LuaNativeReport(declaredNatives(commonJ), undeclaredByEngine);
		final GlobalScope scope = environment.getGlobalScope();
		final LuaState lua = environment.lua();
		for (final String name : report.declared) {
			final Integer id = scope.getNativeIds().get(name);
			final NativeJassFunction function = id == null ? null : scope.getNativeById(id);
			final boolean isFunction = isLuaFunction(lua, name);
			if (isFunction) {
				report.callable.add(name);
			}
			else if (function == null) {
				report.notInProgram.add(name);
			}
			else if (function.getImplementation() == null) {
				report.withoutCode.add(name);
			}
			else {
				report.hidden.add(name);
			}
		}
		return report;
	}

	private static boolean isLuaFunction(final LuaState lua, final String name) {
		lua.getGlobal(name);
		try {
			return lua.type(-1) == LuaState.TYPE_FUNCTION;
		}
		finally {
			lua.pop(1);
		}
	}

	public List<String> getDeclared() {
		return Collections.unmodifiableList(this.declared);
	}

	/** Declared natives a script can call. */
	public List<String> getCallable() {
		return Collections.unmodifiableList(this.callable);
	}

	/** Declared natives the program has but the engine gave no code. */
	public List<String> getWithoutCode() {
		return Collections.unmodifiableList(this.withoutCode);
	}

	/** Declared natives the program does not have at all (the script's common.j is not the one the program read). */
	public List<String> getNotInProgram() {
		return Collections.unmodifiableList(this.notInProgram);
	}

	/** Declared natives with code that a script still cannot call: always a binding fault. */
	public List<String> getHidden() {
		return Collections.unmodifiableList(this.hidden);
	}

	/** Natives the engine has code for that no script declared: no script can call them. */
	public List<String> getUndeclared() {
		return Collections.unmodifiableList(this.undeclared);
	}

	public String summary() {
		return "Lua natives: " + this.declared.size() + " declared, " + this.callable.size() + " callable, "
				+ this.withoutCode.size() + " without engine code, " + this.notInProgram.size() + " not in program, "
				+ this.hidden.size() + " hidden, " + this.undeclared.size() + " engine natives undeclared";
	}

	public String detail() {
		final StringBuilder out = new StringBuilder(summary());
		append(out, "without engine code", this.withoutCode);
		append(out, "not in program", this.notInProgram);
		append(out, "hidden", this.hidden);
		append(out, "undeclared", this.undeclared);
		return out.toString();
	}

	private static void append(final StringBuilder out, final String title, final List<String> names) {
		if (!names.isEmpty()) {
			out.append('\n').append(title).append(": ").append(String.join(" ", names));
		}
	}
}
