package com.etheller.warsmash.parsers.lua;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.lua.LuaState;
import com.etheller.warsmash.util.Log;

/**
 * The Lua script of a map (war3map.lua) with blizzard.j as Lua before it. Loading compiles both, so a script that does
 * not compile is known before the game starts; starting runs them and then the map's main or config function, on one
 * thread of the game.
 */
public final class LuaMapScript {
	private static final String[] MAP_SCRIPTS = { "war3map.lua", "Scripts\\war3map.lua" };
	private static final String[] JASS_MAP_SCRIPTS = { "war3map.j", "Scripts\\war3map.j" };
	private static final String[] COMMON = { "Scripts\\common.j", "common.j" };
	private static final String[] BLIZZARD = { "Scripts\\Blizzard.j", "Scripts\\blizzard.j", "Blizzard.j" };

	private static final String DRIVER = String.join("\n",
			"local function run(name, chunk)",
			"  local ok, err = xpcall(chunk, __wc_traceback)",
			"  if not ok then __report(name .. ': ' .. tostring(err)) end",
			"  return ok",
			"end",
			"function __start()",
			"  __early(true)",
			"  run('blizzard.j', __blizzard)",
			"  local loaded = run('war3map.lua', __map)",
			"  __early(false)",
			"  if loaded then",
			"    if main then run('main', main) else __report('war3map.lua has no main function') end",
			"  end",
			"end",
			"function __config()",
			"  run('blizzard.j', __blizzard)",
			"  if run('war3map.lua', __map) then",
			"    if config then run('config', config) else __report('war3map.lua has no config function') end",
			"  end",
			"end");

	private final LuaEnvironment environment;

	private LuaMapScript(final LuaEnvironment environment) {
		this.environment = environment;
	}

	/** Developer switch that makes maps with Lua scripts unplayable: -Dwarsmash.lua.disabled=true. */
	private static final String DISABLE_PROPERTY = "warsmash.lua.disabled";

	/** Lua scripts are run unless a developer turned them off or this system cannot load the Lua library. */
	public static boolean isEnabled() {
		return !Boolean.getBoolean(DISABLE_PROPERTY) && LuaState.isSupported();
	}

	/** True if the map has a Lua script and no JASS script. */
	public static boolean usesLua(final DataSource map) {
		return has(map, MAP_SCRIPTS) && !has(map, JASS_MAP_SCRIPTS);
	}

	private static boolean has(final DataSource source, final String[] paths) {
		for (final String path : paths) {
			if (source.has(path)) {
				return true;
			}
		}
		return false;
	}

	private static String read(final DataSource source, final String[] paths) throws IOException {
		for (final String path : paths) {
			if (source.has(path)) {
				try (InputStream stream = source.getResourceAsStream(path)) {
					return new String(IOUtils.toByteArray(stream), StandardCharsets.UTF_8);
				}
			}
		}
		throw new IOException("The game data has none of " + String.join(", ", paths));
	}

	/**
	 * @param map the map's data, which also finds the game's scripts
	 * @param globalScope the program the engine's natives and constants are in
	 * @throws IOException if a script file is missing
	 * @throws com.etheller.warsmash.lua.LuaSyntaxException if a script does not compile
	 */
	public static LuaMapScript load(final DataSource map, final GlobalScope globalScope,
			final LuaEnvironment.Host host) throws IOException {
		final String blizzardLua = JassToLuaTranspiler.transpile(read(map, COMMON), read(map, BLIZZARD));
		final byte[] mapScript;
		try (InputStream stream = map.getResourceAsStream(has(map, new String[] { MAP_SCRIPTS[0] }) ? MAP_SCRIPTS[0]
				: MAP_SCRIPTS[1])) {
			mapScript = IOUtils.toByteArray(stream);
		}
		final LuaEnvironment environment = new LuaEnvironment(globalScope, host);
		try {
			environment.defineChunk("__blizzard", blizzardLua.getBytes(StandardCharsets.UTF_8), "blizzard.lua");
			environment.defineChunk("__map", mapScript, "war3map.lua");
			environment.getLuaState().run(DRIVER, "=wc");
		}
		catch (final RuntimeException e) {
			environment.close();
			throw e;
		}
		return new LuaMapScript(environment);
	}

	/** Which natives of the game's common.j the script can call; logged by the loader of a map. */
	public LuaNativeReport nativeReport(final DataSource map, final Collection<String> undeclaredByEngine)
			throws IOException {
		return LuaNativeReport.compute(read(map, COMMON), this.environment, undeclaredByEngine);
	}

	public LuaEnvironment getEnvironment() {
		return this.environment;
	}

	/** Starts blizzard.j, the script and its main function on a thread of the game; a wait there ends the thread. */
	public void start() {
		this.environment.queue(this.environment.getGlobalFunction("__start"), true);
	}

	/** Runs blizzard.j, the script and its config function now. */
	public void runConfig() {
		Log.info("Lua script: config");
		this.environment.runNow(this.environment.getGlobalFunction("__config"));
	}

	public void close() {
		this.environment.close();
	}
}
