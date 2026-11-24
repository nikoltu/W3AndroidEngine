package com.etheller.warsmash.parsers.fdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.fdfparser.FDFLexer;
import com.etheller.warsmash.fdfparser.FDFParser;
import com.etheller.warsmash.fdfparser.FDFParserBuilder;
import com.etheller.warsmash.util.Log;

public class DataSourceFDFParserBuilder implements FDFParserBuilder {
	/**
	 * Parse trees by path. The menu and the game read the same interface files, and the visitors only read a tree.
	 * A tree stands for the bytes it was parsed from, so a file another source replaced is parsed again.
	 */
	private static final Map<String, SoftReference<ParsedFile>> PARSED = new ConcurrentHashMap<>();

	/** Lets go of the parse trees, which are parsed again from the files when an interface asks for them. */
	public static void releaseParsed() {
		PARSED.clear();
	}

	private final DataSource dataSource;

	private static final class ParsedFile {
		private final byte[] content;
		private final FDFParser.ProgramContext program;

		private ParsedFile(final byte[] content, final FDFParser.ProgramContext program) {
			this.content = content;
			this.program = program;
		}
	}

	public DataSourceFDFParserBuilder(final DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public FDFParser.ProgramContext buildProgram(final String path) {
		final byte[] content;
		try (InputStream stream = this.dataSource.getResourceAsStream(path)) {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			final byte[] chunk = new byte[8192];
			int read;
			while ((read = stream.read(chunk)) != -1) {
				bytes.write(chunk, 0, read);
			}
			content = bytes.toByteArray();
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
		final SoftReference<ParsedFile> reference = PARSED.get(path);
		final ParsedFile known = reference == null ? null : reference.get();
		if ((known != null) && Arrays.equals(known.content, content)) {
			return known.program;
		}
		final FDFParser.ProgramContext program = build(path, new ByteArrayInputStream(content)).program();
		PARSED.put(path, new SoftReference<>(new ParsedFile(content, program)));
		return program;
	}

	@Override
	public FDFParser build(final String path) {
		try {
			return build(path, this.dataSource.getResourceAsStream(path));
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	private FDFParser build(final String path, final InputStream input) {
		FDFLexer lexer;
		try {
			lexer = new FDFLexer(CharStreams.fromStream(input));
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
		final FDFParser fdfParser = new FDFParser(new CommonTokenStream(lexer));
		final BaseErrorListener errorListener = new BaseErrorListener() {
			@Override
			public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
					final int charPositionInLine, final String msg, final RecognitionException e) {
				String sourceName = path;
				if (!sourceName.isEmpty()) {
					sourceName = String.format(Locale.ROOT, "%s:%d:%d: ", sourceName, line, charPositionInLine);
				}

				Log.warn(sourceName + "line " + line + ":" + charPositionInLine + " " + msg);
			}
		};
		fdfParser.addErrorListener(errorListener);
		return fdfParser;
	}
}