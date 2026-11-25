package com.etheller.warsmash.parsers.lua;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.etheller.interpreter.ast.util.JassNumbers;

/**
 * Turns the functions and globals of blizzard.j into Lua, because the game data has no Lua version of it. The Lua
 * script of a map calls these functions as it calls natives (PolledWait, CreateNUnitsAtLoc, the bj_ variables), so
 * they have to be Lua: a wait in one of them suspends the Lua function that called it.
 * <p>
 * It reads the JASS text with a small parser of its own: the program needs the types of the expressions (integer
 * division, string concatenation, integers that are used as reals), which the engine's syntax tree does not keep.
 * The natives and constants come from common.j; they are not written out, the engine provides them.
 */
public final class JassToLuaTranspiler {
	/** The Lua helpers every output needs. */
	private static final String PRELUDE = String.join("\n",
			"function __idiv(a, b)",
			"  local q = a // b",
			"  if q < 0 and q * b ~= a then q = q + 1 end",
			"  return q",
			"end",
			"function __i32(x)",
			"  return ((x + 0x80000000) & 0xFFFFFFFF) - 0x80000000",
			"end",
			"");

	private static final Set<String> LUA_WORDS = new HashSet<>(
			Arrays.asList("break", "do", "end", "for", "goto", "in", "nil", "repeat", "until", "while"));

	private enum Kind {
		IDENT, INT, REAL, STRING, RAWCODE, OP, NEWLINE, EOF
	}

	private static final class Token {
		final Kind kind;
		final String text;
		final int line;

		Token(final Kind kind, final String text, final int line) {
			this.kind = kind;
			this.text = text;
			this.line = line;
		}

		@Override
		public String toString() {
			return this.kind + "(" + this.text + ")@" + this.line;
		}
	}

	public static final class TranspileException extends RuntimeException {
		TranspileException(final String message) {
			super(message);
		}
	}

	// --- syntax tree

	private abstract static class Expression {
	}

	private static final class Literal extends Expression {
		final Kind kind;
		final String text;

		Literal(final Kind kind, final String text) {
			this.kind = kind;
			this.text = text;
		}
	}

	private static final class Word extends Expression {
		final String text; // true, false, null

		Word(final String text) {
			this.text = text;
		}
	}

	private static final class Name extends Expression {
		final String id;

		Name(final String id) {
			this.id = id;
		}
	}

	private static final class Index extends Expression {
		final String id;
		final Expression index;

		Index(final String id, final Expression index) {
			this.id = id;
			this.index = index;
		}
	}

	private static final class Call extends Expression {
		final String id;
		final List<Expression> arguments;

		Call(final String id, final List<Expression> arguments) {
			this.id = id;
			this.arguments = arguments;
		}
	}

	private static final class FunctionRef extends Expression {
		final String id;

		FunctionRef(final String id) {
			this.id = id;
		}
	}

	private static final class Binary extends Expression {
		final String op;
		final Expression left;
		final Expression right;

		Binary(final String op, final Expression left, final Expression right) {
			this.op = op;
			this.left = left;
			this.right = right;
		}
	}

	private static final class Unary extends Expression {
		final String op;
		final Expression operand;

		Unary(final String op, final Expression operand) {
			this.op = op;
			this.operand = operand;
		}
	}

	private abstract static class Statement {
	}

	private static final class Local extends Statement {
		final String type;
		final String id;
		final boolean array;
		final Expression init;

		Local(final String type, final String id, final boolean array, final Expression init) {
			this.type = type;
			this.id = id;
			this.array = array;
			this.init = init;
		}
	}

	private static final class Set_ extends Statement {
		final String id;
		final Expression index;
		final Expression value;

		Set_(final String id, final Expression index, final Expression value) {
			this.id = id;
			this.index = index;
			this.value = value;
		}
	}

	private static final class CallStatement extends Statement {
		final Call call;

		CallStatement(final Call call) {
			this.call = call;
		}
	}

	private static final class If extends Statement {
		final List<Expression> conditions = new ArrayList<>();
		final List<List<Statement>> branches = new ArrayList<>();
		List<Statement> otherwise;
	}

	private static final class Loop extends Statement {
		final List<Statement> body;

		Loop(final List<Statement> body) {
			this.body = body;
		}
	}

	private static final class ExitWhen extends Statement {
		final Expression condition;

		ExitWhen(final Expression condition) {
			this.condition = condition;
		}
	}

	private static final class Return extends Statement {
		final Expression value;

		Return(final Expression value) {
			this.value = value;
		}
	}

	private static final class Function {
		final String name;

		Function(final String name) {
			this.name = name;
		}

		final List<String> parameterTypes = new ArrayList<>();
		final List<String> parameterNames = new ArrayList<>();
		String returnType = "nothing";
		final List<Statement> body = new ArrayList<>();
	}

	private static final class GlobalVariable {
		final String type;
		final String id;
		final boolean array;
		final Expression init;

		GlobalVariable(final String type, final String id, final boolean array, final Expression init) {
			this.type = type;
			this.id = id;
			this.array = array;
			this.init = init;
		}
	}

	/** What a file declares. */
	private static final class Unit {
		final List<Function> functions = new ArrayList<>();
		final List<GlobalVariable> globals = new ArrayList<>();
		/** The natives, with the same shape as functions that have no body. */
		final List<Function> natives = new ArrayList<>();
	}

	// --- lexer

	private static List<Token> lex(final String text) {
		final List<Token> tokens = new ArrayList<>();
		int line = 1;
		final int length = text.length();
		int i = 0;
		while (i < length) {
			final char c = text.charAt(i);
			if (c == '\n') {
				tokens.add(new Token(Kind.NEWLINE, "\n", line));
				line++;
				i++;
			}
			else if ((c == ' ') || (c == '\t') || (c == '\r') || (c == '﻿')) {
				i++;
			}
			else if ((c == '/') && (i + 1 < length) && (text.charAt(i + 1) == '/')) {
				while ((i < length) && (text.charAt(i) != '\n')) {
					i++;
				}
			}
			else if (Character.isLetter(c) || (c == '_')) {
				final int start = i;
				while ((i < length) && (Character.isLetterOrDigit(text.charAt(i)) || (text.charAt(i) == '_'))) {
					i++;
				}
				tokens.add(new Token(Kind.IDENT, text.substring(start, i), line));
			}
			else if (Character.isDigit(c) || ((c == '.') && (i + 1 < length) && Character.isDigit(text.charAt(i + 1)))
					|| ((c == '$') && (i + 1 < length) && (Character.digit(text.charAt(i + 1), 16) >= 0))) {
				final int start = i;
				if (c == '$') {
					i++;
					while ((i < length) && (Character.digit(text.charAt(i), 16) >= 0)) {
						i++;
					}
					tokens.add(new Token(Kind.INT, "0x" + text.substring(start + 1, i), line));
				}
				else if ((c == '0') && (i + 1 < length) && ((text.charAt(i + 1) == 'x') || (text.charAt(i + 1) == 'X'))) {
					i += 2;
					while ((i < length) && (Character.digit(text.charAt(i), 16) >= 0)) {
						i++;
					}
					tokens.add(new Token(Kind.INT, text.substring(start, i), line));
				}
				else {
					boolean real = false;
					while ((i < length) && (Character.isDigit(text.charAt(i)) || (text.charAt(i) == '.'))) {
						real |= text.charAt(i) == '.';
						i++;
					}
					tokens.add(new Token(real ? Kind.REAL : Kind.INT, text.substring(start, i), line));
				}
			}
			else if (c == '"') {
				final StringBuilder value = new StringBuilder();
				i++;
				while ((i < length) && (text.charAt(i) != '"')) {
					char d = text.charAt(i);
					if ((d == '\\') && (i + 1 < length)) {
						i++;
						d = text.charAt(i);
						switch (d) {
						case 'n':
							value.append('\n');
							break;
						case 'r':
							value.append('\r');
							break;
						case 't':
							value.append('\t');
							break;
						default:
							value.append(d);
						}
					}
					else {
						if (d == '\n') {
							line++;
						}
						value.append(d);
					}
					i++;
				}
				i++;
				tokens.add(new Token(Kind.STRING, value.toString(), line));
			}
			else if (c == '\'') {
				final int start = ++i;
				while ((i < length) && (text.charAt(i) != '\'')) {
					i++;
				}
				tokens.add(new Token(Kind.RAWCODE, text.substring(start, i), line));
				i++;
			}
			else {
				final String two = i + 1 < length ? text.substring(i, i + 2) : "";
				if (two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=")) {
					tokens.add(new Token(Kind.OP, two, line));
					i += 2;
				}
				else {
					tokens.add(new Token(Kind.OP, String.valueOf(c), line));
					i++;
				}
			}
		}
		tokens.add(new Token(Kind.NEWLINE, "\n", line));
		tokens.add(new Token(Kind.EOF, "", line));
		return tokens;
	}

	// --- parser

	private static final class Parser {
		private final List<Token> tokens;
		private int position;

		Parser(final List<Token> tokens) {
			this.tokens = tokens;
		}

		private Token peek() {
			return this.tokens.get(this.position);
		}

		private Token next() {
			return this.tokens.get(this.position++);
		}

		private boolean isWord(final String word) {
			final Token token = peek();
			return (token.kind == Kind.IDENT) && token.text.equals(word);
		}

		private boolean isOp(final String op) {
			final Token token = peek();
			return (token.kind == Kind.OP) && token.text.equals(op);
		}

		private TranspileException error(final String message) {
			return new TranspileException("line " + peek().line + ": " + message + " (at " + peek() + ")");
		}

		private void expectWord(final String word) {
			if (!isWord(word)) {
				throw error("expected " + word);
			}
			next();
		}

		private void expectOp(final String op) {
			if (!isOp(op)) {
				throw error("expected " + op);
			}
			next();
		}

		private String ident() {
			if (peek().kind != Kind.IDENT) {
				throw error("expected a name");
			}
			return next().text;
		}

		private void skipNewlines() {
			while (peek().kind == Kind.NEWLINE) {
				next();
			}
		}

		private void endOfLine() {
			if ((peek().kind != Kind.NEWLINE) && (peek().kind != Kind.EOF)) {
				throw error("expected the end of the line");
			}
			skipNewlines();
		}

		private void skipLine() {
			while ((peek().kind != Kind.NEWLINE) && (peek().kind != Kind.EOF)) {
				next();
			}
			skipNewlines();
		}

		Unit parseFile() {
			final Unit unit = new Unit();
			skipNewlines();
			while (peek().kind != Kind.EOF) {
				if (isWord("type")) {
					skipLine();
				}
				else if (isWord("globals")) {
					skipLine();
					parseGlobals(unit);
				}
				else if (isWord("native") || (isWord("constant") && isWordAt(1, "native"))) {
					if (isWord("constant")) {
						next();
					}
					next();
					final Function function = parseSignature();
					unit.natives.add(function);
					endOfLine();
				}
				else if (isWord("function") || (isWord("constant") && isWordAt(1, "function"))) {
					if (isWord("constant")) {
						next();
					}
					next();
					final Function function = parseSignature();
					endOfLine();
					parseBlock(function.body, "endfunction");
					expectWord("endfunction");
					endOfLine();
					unit.functions.add(function);
				}
				else {
					throw error("unexpected text at the top of the file");
				}
			}
			return unit;
		}

		private boolean isWordAt(final int offset, final String word) {
			final Token token = this.tokens.get(this.position + offset);
			return (token.kind == Kind.IDENT) && token.text.equals(word);
		}

		private Function parseSignature() {
			final Function function = new Function(ident());
			expectWord("takes");
			if (isWord("nothing")) {
				next();
			}
			else {
				while (true) {
					function.parameterTypes.add(ident());
					function.parameterNames.add(ident());
					if (isOp(",")) {
						next();
					}
					else {
						break;
					}
				}
			}
			expectWord("returns");
			function.returnType = ident();
			return function;
		}

		private void parseGlobals(final Unit unit) {
			skipNewlines();
			while (!isWord("endglobals")) {
				if (isWord("constant")) {
					next();
				}
				final String type = ident();
				boolean array = false;
				if (isWord("array")) {
					next();
					array = true;
				}
				final String id = ident();
				Expression init = null;
				if (isOp("=")) {
					next();
					init = parseExpression();
				}
				endOfLine();
				unit.globals.add(new GlobalVariable(type, id, array, init));
			}
			next();
			endOfLine();
		}

		private void parseBlock(final List<Statement> body, final String... enders) {
			skipNewlines();
			while (true) {
				for (final String ender : enders) {
					if (isWord(ender)) {
						return;
					}
				}
				if (peek().kind == Kind.EOF) {
					throw error("the block does not end");
				}
				final Statement statement = parseStatement();
				if (statement != null) {
					body.add(statement);
				}
			}
		}

		private Statement parseStatement() {
			if (isWord("debug")) {
				skipLine();
				return null;
			}
			if (isWord("local")) {
				next();
				final String type = ident();
				boolean array = false;
				if (isWord("array")) {
					next();
					array = true;
				}
				final String id = ident();
				Expression init = null;
				if (isOp("=")) {
					next();
					init = parseExpression();
				}
				endOfLine();
				return new Local(type, id, array, init);
			}
			if (isWord("set")) {
				next();
				final String id = ident();
				Expression index = null;
				if (isOp("[")) {
					next();
					index = parseExpression();
					expectOp("]");
				}
				expectOp("=");
				final Expression value = parseExpression();
				endOfLine();
				return new Set_(id, index, value);
			}
			if (isWord("call")) {
				next();
				final String id = ident();
				expectOp("(");
				final List<Expression> arguments = parseArguments();
				endOfLine();
				return new CallStatement(new Call(id, arguments));
			}
			if (isWord("if")) {
				next();
				final If statement = new If();
				statement.conditions.add(parseExpression());
				expectWord("then");
				endOfLine();
				List<Statement> branch = new ArrayList<>();
				parseBlock(branch, "elseif", "else", "endif");
				statement.branches.add(branch);
				while (true) {
					if (isWord("elseif")) {
						next();
						statement.conditions.add(parseExpression());
						expectWord("then");
						endOfLine();
						branch = new ArrayList<>();
						parseBlock(branch, "elseif", "else", "endif");
						statement.branches.add(branch);
					}
					else if (isWord("else")) {
						next();
						endOfLine();
						statement.otherwise = new ArrayList<>();
						parseBlock(statement.otherwise, "endif");
					}
					else {
						break;
					}
				}
				expectWord("endif");
				endOfLine();
				return statement;
			}
			if (isWord("loop")) {
				next();
				endOfLine();
				final List<Statement> body = new ArrayList<>();
				parseBlock(body, "endloop");
				expectWord("endloop");
				endOfLine();
				return new Loop(body);
			}
			if (isWord("exitwhen")) {
				next();
				final Expression condition = parseExpression();
				endOfLine();
				return new ExitWhen(condition);
			}
			if (isWord("return")) {
				next();
				Expression value = null;
				if ((peek().kind != Kind.NEWLINE) && (peek().kind != Kind.EOF)) {
					value = parseExpression();
				}
				endOfLine();
				return new Return(value);
			}
			throw error("unknown statement");
		}

		private List<Expression> parseArguments() {
			final List<Expression> arguments = new ArrayList<>();
			if (isOp(")")) {
				next();
				return arguments;
			}
			while (true) {
				arguments.add(parseExpression());
				if (isOp(",")) {
					next();
				}
				else {
					break;
				}
			}
			expectOp(")");
			return arguments;
		}

		Expression parseExpression() {
			return parseOr();
		}

		private Expression parseOr() {
			Expression left = parseAnd();
			while (isWord("or")) {
				next();
				left = new Binary("or", left, parseAnd());
			}
			return left;
		}

		private Expression parseAnd() {
			Expression left = parseNot();
			while (isWord("and")) {
				next();
				left = new Binary("and", left, parseNot());
			}
			return left;
		}

		private Expression parseNot() {
			if (isWord("not")) {
				next();
				return new Unary("not", parseNot());
			}
			return parseComparison();
		}

		private Expression parseComparison() {
			Expression left = parseSum();
			while (isOp("==") || isOp("!=") || isOp("<") || isOp(">") || isOp("<=") || isOp(">=")) {
				final String op = next().text;
				left = new Binary(op, left, parseSum());
			}
			return left;
		}

		private Expression parseSum() {
			Expression left = parseProduct();
			while (isOp("+") || isOp("-")) {
				final String op = next().text;
				left = new Binary(op, left, parseProduct());
			}
			return left;
		}

		private Expression parseProduct() {
			Expression left = parseUnary();
			while (isOp("*") || isOp("/")) {
				final String op = next().text;
				left = new Binary(op, left, parseUnary());
			}
			return left;
		}

		private Expression parseUnary() {
			if (isOp("-")) {
				next();
				return new Unary("-", parseUnary());
			}
			if (isOp("+")) {
				next();
				return parseUnary();
			}
			return parsePrimary();
		}

		private Expression parsePrimary() {
			final Token token = peek();
			switch (token.kind) {
			case INT:
			case REAL:
			case STRING:
			case RAWCODE:
				next();
				return new Literal(token.kind, token.text);
			case OP:
				if (token.text.equals("(")) {
					next();
					final Expression inner = parseExpression();
					expectOp(")");
					return inner;
				}
				throw error("unexpected operator");
			case IDENT:
				next();
				if (token.text.equals("true") || token.text.equals("false") || token.text.equals("null")) {
					return new Word(token.text);
				}
				if (token.text.equals("function")) {
					return new FunctionRef(ident());
				}
				if (isOp("(")) {
					next();
					return new Call(token.text, parseArguments());
				}
				if (isOp("[")) {
					next();
					final Expression index = parseExpression();
					expectOp("]");
					return new Index(token.text, index);
				}
				return new Name(token.text);
			default:
				throw error("expected an expression");
			}
		}
	}

	// --- code generation

	private static final class Typed {
		final String code;
		final String type;

		Typed(final String code, final String type) {
			this.code = code;
			this.type = type;
		}
	}

	private final Map<String, Function> signatures = new HashMap<>();
	private final Map<String, String> globalTypes = new HashMap<>();
	private final Map<String, Boolean> globalArrays = new HashMap<>();
	private Map<String, String> localTypes = new HashMap<>();
	private Map<String, Boolean> localArrays = new HashMap<>();
	private Function current;
	private final StringBuilder out = new StringBuilder();

	private JassToLuaTranspiler() {
	}

	/**
	 * @param commonJ the declarations of the natives and constants, which are not written out
	 * @param blizzardJ the functions and variables to write out as Lua
	 * @return the Lua text
	 * @throws TranspileException if a file has a syntax this does not know
	 */
	public static String transpile(final String commonJ, final String blizzardJ) {
		return transpile(commonJ, blizzardJ, null);
	}

	/**
	 * @param commonJ the declarations of the natives and constants, which are not written out
	 * @param blizzardJ the declarations of blizzard.j; written out as Lua only when there is no map script
	 * @param mapJ the map's own JASS script to write out as Lua, or null
	 * @return the Lua text
	 * @throws TranspileException if a file has a syntax this does not know
	 */
	public static String transpile(final String commonJ, final String blizzardJ, final String mapJ) {
		final Unit common = new Parser(lex(commonJ)).parseFile();
		final Unit blizzard = new Parser(lex(blizzardJ)).parseFile();
		final Unit map = mapJ == null ? null : new Parser(lex(mapJ)).parseFile();
		final JassToLuaTranspiler transpiler = new JassToLuaTranspiler();
		for (final Function function : common.natives) {
			transpiler.signatures.put(function.name, function);
		}
		for (final GlobalVariable global : common.globals) {
			transpiler.globalTypes.put(global.id, global.type);
			transpiler.globalArrays.put(global.id, global.array);
		}
		for (final Unit unit : map == null ? new Unit[] { blizzard } : new Unit[] { blizzard, map }) {
			for (final Function function : unit.natives) {
				transpiler.signatures.put(function.name, function);
			}
			for (final Function function : unit.functions) {
				transpiler.signatures.put(function.name, function);
			}
			for (final GlobalVariable global : unit.globals) {
				transpiler.globalTypes.put(global.id, global.type);
				transpiler.globalArrays.put(global.id, global.array);
			}
		}
		final Unit written = map == null ? blizzard : map;
		transpiler.out.append(PRELUDE);
		// The variables start with calls of functions that are written further down: the functions come first.
		for (final Function function : written.functions) {
			transpiler.writeFunction(function);
		}
		transpiler.writesBlizzard = map == null;
		for (final GlobalVariable global : written.globals) {
			transpiler.writeGlobal(global);
		}
		return transpiler.out.toString();
	}

	/** Writes a JASS script as Lua: common.j, blizzard.j, the script and the output file. */
	public static void main(final String[] args) throws java.io.IOException {
		if (args.length != 4) {
			System.err.println("usage: JassToLuaTranspiler <common.j> <blizzard.j> <script.j> <out.lua>");
			System.exit(2);
		}
		final java.nio.file.Path[] in = new java.nio.file.Path[3];
		final String[] text = new String[3];
		for (int i = 0; i < 3; i++) {
			in[i] = java.nio.file.Paths.get(args[i]);
			text[i] = new String(java.nio.file.Files.readAllBytes(in[i]), java.nio.charset.StandardCharsets.UTF_8);
		}
		java.nio.file.Files.write(java.nio.file.Paths.get(args[3]),
				transpile(text[0], text[1], text[2]).getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private static String lua(final String id) {
		return LUA_WORDS.contains(id) ? id + "_" : id;
	}

	private static String defaultValue(final String type, final boolean array) {
		if (array) {
			final String element = defaultValue(type, false);
			return "nil".equals(element) ? "{}" : "__jarray(" + element + ")";
		}
		switch (type) {
		case "integer":
			return "0";
		case "real":
			return "0.0";
		case "boolean":
			return "false";
		default:
			return "nil";
		}
	}

	private boolean writesBlizzard;

	private void writeGlobal(final GlobalVariable global) {
		final String value;
		if ((global.init == null) || global.array) {
			value = defaultValue(global.type, global.array);
		}
		else {
			this.current = null;
			this.localTypes = new HashMap<>();
			this.localArrays = new HashMap<>();
			value = coerce(expression(global.init), global.type);
		}
		final String name = lua(global.id);
		if (this.writesBlizzard && !global.array && !"nil".equals(value) && isHandleType(global.type)) {
			// The engine has run blizzard.j for JASS already and gave these to the script; making them again would
			// take handle ids the original game never takes.
			this.out.append("if ").append(name).append(" == nil then ").append(name).append(" = ").append(value)
					.append(" end\n");
			return;
		}
		this.out.append(name).append(" = ").append(value).append('\n');
	}

	private static boolean isHandleType(final String type) {
		switch (type) {
		case "integer":
		case "real":
		case "boolean":
		case "string":
		case "code":
			return false;
		default:
			return true;
		}
	}

	private void writeFunction(final Function function) {
		this.current = function;
		this.localTypes = new HashMap<>();
		this.localArrays = new HashMap<>();
		this.out.append("function ").append(lua(function.name)).append('(');
		for (int i = 0; i < function.parameterNames.size(); i++) {
			if (i > 0) {
				this.out.append(", ");
			}
			final String name = function.parameterNames.get(i);
			this.out.append(lua(name));
			this.localTypes.put(name, function.parameterTypes.get(i));
			this.localArrays.put(name, false);
		}
		this.out.append(")\n");
		writeBlock(function.body, 1);
		this.out.append("end\n");
	}

	private void indent(final int level) {
		for (int i = 0; i < level; i++) {
			this.out.append("  ");
		}
	}

	private void writeBlock(final List<Statement> block, final int level) {
		for (final Statement statement : block) {
			writeStatement(statement, level);
		}
	}

	private void writeStatement(final Statement statement, final int level) {
		indent(level);
		if (statement instanceof Local) {
			final Local local = (Local) statement;
			this.localTypes.put(local.id, local.type);
			this.localArrays.put(local.id, local.array);
			final String value = (local.init == null) || local.array ? defaultValue(local.type, local.array)
					: coerce(expression(local.init), local.type);
			this.out.append("local ").append(lua(local.id)).append(" = ").append(value).append('\n');
		}
		else if (statement instanceof Set_) {
			final Set_ set = (Set_) statement;
			final String type = typeOfVariable(set.id);
			this.out.append(lua(set.id));
			if (set.index != null) {
				this.out.append('[').append(expression(set.index).code).append(']');
			}
			this.out.append(" = ").append(coerce(expression(set.value), type)).append('\n');
		}
		else if (statement instanceof CallStatement) {
			this.out.append(call(((CallStatement) statement).call).code).append('\n');
		}
		else if (statement instanceof If) {
			final If conditional = (If) statement;
			for (int i = 0; i < conditional.conditions.size(); i++) {
				if (i > 0) {
					indent(level);
				}
				this.out.append(i == 0 ? "if " : "elseif ").append(expression(conditional.conditions.get(i)).code)
						.append(" then\n");
				writeBlock(conditional.branches.get(i), level + 1);
			}
			if (conditional.otherwise != null) {
				indent(level);
				this.out.append("else\n");
				writeBlock(conditional.otherwise, level + 1);
			}
			indent(level);
			this.out.append("end\n");
		}
		else if (statement instanceof Loop) {
			this.out.append("while true do\n");
			writeBlock(((Loop) statement).body, level + 1);
			indent(level);
			this.out.append("end\n");
		}
		else if (statement instanceof ExitWhen) {
			this.out.append("if ").append(expression(((ExitWhen) statement).condition).code).append(" then break end\n");
		}
		else if (statement instanceof Return) {
			final Return result = (Return) statement;
			if (result.value == null) {
				this.out.append("do return end\n");
			}
			else {
				this.out.append("do return ").append(coerce(expression(result.value), this.current.returnType))
						.append(" end\n");
			}
		}
	}

	private String typeOfVariable(final String id) {
		final String local = this.localTypes.get(id);
		if (local != null) {
			return local;
		}
		final String global = this.globalTypes.get(id);
		if (global == null) {
			throw new TranspileException("the variable " + id + " is not declared");
		}
		return global;
	}

	/** An integer where a real is expected becomes a real. */
	private static String coerce(final Typed value, final String target) {
		if ("real".equals(target) && "integer".equals(value.type)) {
			return asReal(value);
		}
		return value.code;
	}

	/** An integer as the real the original game makes of it (cut, not rounded), written out for literals. */
	private static String asReal(final Typed value) {
		if (value.code.matches("-?[0-9]+")) {
			return realLiteral(JassNumbers.fromInt((int) Long.parseLong(value.code)));
		}
		return "__i2r(" + value.code + ")";
	}

	private static String realLiteral(final double value) {
		if ((value == Math.rint(value)) && (Math.abs(value) < 1e15)) {
			return (value == 0 ? "0" : Long.toString((long) value)) + ".0";
		}
		return Double.toHexString(value);
	}

	private Typed call(final Call call) {
		final Function signature = this.signatures.get(call.id);
		if (signature == null) {
			throw new TranspileException("the function " + call.id + " is not declared");
		}
		final StringBuilder code = new StringBuilder(lua(call.id)).append('(');
		for (int i = 0; i < call.arguments.size(); i++) {
			if (i > 0) {
				code.append(", ");
			}
			final String parameterType = i < signature.parameterTypes.size() ? signature.parameterTypes.get(i) : "";
			code.append(coerce(expression(call.arguments.get(i)), parameterType));
		}
		code.append(')');
		return new Typed(code.toString(), signature.returnType);
	}

	private static boolean isNumber(final String type) {
		return "integer".equals(type) || "real".equals(type);
	}

	private Typed expression(final Expression expression) {
		if (expression instanceof Literal) {
			final Literal literal = (Literal) expression;
			switch (literal.kind) {
			case INT:
				return new Typed(integerLiteral(literal.text), "integer");
			case REAL: {
				String text = literal.text;
				if (text.startsWith(".")) {
					text = "0" + text;
				}
				if (text.endsWith(".")) {
					text = text + "0";
				}
				final String parsed = realLiteral(JassNumbers.parseLiteral(text));
				return new Typed(parsed.startsWith("-") ? "(" + parsed + ")" : parsed, "real");
			}
			case STRING:
				return new Typed(quote(literal.text), "string");
			default:
				return new Typed(Long.toString(rawcode(literal.text)), "integer");
			}
		}
		if (expression instanceof Word) {
			final String word = ((Word) expression).text;
			return new Typed("null".equals(word) ? "nil" : word, "null".equals(word) ? "null" : "boolean");
		}
		if (expression instanceof Name) {
			final String id = ((Name) expression).id;
			return new Typed(lua(id), typeOfVariable(id));
		}
		if (expression instanceof Index) {
			final Index index = (Index) expression;
			return new Typed(lua(index.id) + "[" + expression(index.index).code + "]", typeOfVariable(index.id));
		}
		if (expression instanceof Call) {
			return call((Call) expression);
		}
		if (expression instanceof FunctionRef) {
			return new Typed(lua(((FunctionRef) expression).id), "code");
		}
		if (expression instanceof Unary) {
			final Unary unary = (Unary) expression;
			final Typed operand = expression(unary.operand);
			if (unary.op.equals("not")) {
				return new Typed("(not " + operand.code + ")", "boolean");
			}
			return new Typed("(-" + operand.code + ")", operand.type);
		}
		final Binary binary = (Binary) expression;
		final Typed left = expression(binary.left);
		final Typed right = expression(binary.right);
		switch (binary.op) {
		case "and":
		case "or":
			return new Typed("(" + left.code + " " + binary.op + " " + right.code + ")", "boolean");
		case "==":
			// Reals are equal within a small tolerance; they are not unequal by it (the original game's rule).
			if (isNumber(left.type) && isNumber(right.type) && ("real".equals(left.type) || "real".equals(right.type))) {
				return new Typed("__req(" + coerce(left, "real") + ", " + coerce(right, "real") + ")", "boolean");
			}
			return new Typed("(" + left.code + " == " + right.code + ")", "boolean");
		case "!=":
			return new Typed("(" + left.code + " ~= " + right.code + ")", "boolean");
		case "<":
		case ">":
		case "<=":
		case ">=":
			return new Typed("(" + left.code + " " + binary.op + " " + right.code + ")", "boolean");
		case "+":
			if ("string".equals(left.type) || "string".equals(right.type)) {
				return new Typed("(" + nonNull(left) + " .. " + nonNull(right) + ")", "string");
			}
			return arithmetic("__ra", "+", left, right);
		case "-":
			return arithmetic("__rs", "-", left, right);
		case "*":
			return arithmetic("__rm", "*", left, right);
		case "/":
			if ("integer".equals(left.type) && "integer".equals(right.type)) {
				return new Typed("__idiv(" + left.code + ", " + right.code + ")", "integer");
			}
			return new Typed("__rd(" + coerce(left, "real") + ", " + coerce(right, "real") + ")", "real");
		default:
			throw new TranspileException("unknown operator " + binary.op);
		}
	}

	/** Integers wrap at 32 bits; reals are the original game's software floats, which Lua's own numbers are not. */
	private static Typed arithmetic(final String realFunction, final String operator, final Typed left,
			final Typed right) {
		if ("integer".equals(left.type) && "integer".equals(right.type)) {
			return new Typed("__i32(" + left.code + " " + operator + " " + right.code + ")", "integer");
		}
		if (isNumber(left.type) && isNumber(right.type)) {
			return new Typed(realFunction + "(" + coerce(left, "real") + ", " + coerce(right, "real") + ")", "real");
		}
		return new Typed("(" + left.code + " " + operator + " " + right.code + ")", left.type);
	}

	/** A null string joins as an empty one. */
	private static String nonNull(final Typed value) {
		return value.code.startsWith("\"") ? value.code : "(" + value.code + " or \"\")";
	}

	private static String resultType(final Typed left, final Typed right) {
		if (isNumber(left.type) && isNumber(right.type)) {
			return "integer".equals(left.type) && "integer".equals(right.type) ? "integer" : "real";
		}
		return left.type;
	}

	private static String integerLiteral(final String text) {
		if (text.startsWith("0x") || text.startsWith("0X")) {
			// JASS integers have 32 bits: 0xFFFFFFFF is -1.
			return Long.toString((int) Long.parseLong(text.substring(2), 16));
		}
		if ((text.length() > 1) && text.startsWith("0")) {
			return Long.toString((int) Long.parseLong(text, 8));
		}
		return text;
	}

	private static long rawcode(final String text) {
		if (text.length() == 1) {
			return text.charAt(0);
		}
		long value = 0;
		for (int i = 0; i < text.length(); i++) {
			value = (value << 8) | (text.charAt(i) & 0xFF);
		}
		return (int) value;
	}

	private static String quote(final String text) {
		final StringBuilder quoted = new StringBuilder("\"");
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			switch (c) {
			case '"':
				quoted.append("\\\"");
				break;
			case '\\':
				quoted.append("\\\\");
				break;
			case '\n':
				quoted.append("\\n");
				break;
			case '\r':
				quoted.append("\\r");
				break;
			case '\t':
				quoted.append("\\t");
				break;
			default:
				quoted.append(c);
			}
		}
		return quoted.append('"').toString();
	}
}
