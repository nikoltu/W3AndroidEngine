package com.etheller.warsmash.viewer5.handlers.w3x.save;

import com.etheller.interpreter.ast.util.CAllocatedHandle;
import com.etheller.warsmash.parsers.jass.triggers.BoolExprCondition;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWeatherEffect;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.badlogic.gdx.files.FileHandle;
import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.GlobalScopeAssignable;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.warsmash.parsers.jass.JassAIEnvironment;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderLightningEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderSpellEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUbersplat;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.HandleIdAllocator;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CGameCache;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CImage;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CSaveStorage;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CTrackable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapFlag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CMapDifficulty;

/**
 * A saved game file: a short header the Load dialog can read alone, then the game's object graph. The map's data and
 * script are not in the file: loading starts the map again, leaves out its main function and replaces the state with
 * the saved one.
 */
public final class SavedGame {
	private static final int MAGIC = 0x57534D53;
	/**
	 * Raised only when the file layout itself changes. Classes gaining or losing fields need no new version: the class
	 * table lets older saves load (see SavedObjectGraph). 3 added the table of script functions to the header, 4 the
	 * types of the saved globals, 5 the game's random seed, 6 came with the handle table (the map's destructables,
	 * which are found again by id, are numbered differently), 7 the campaign and difficulty the game is played in; 2 is
	 * still read, its script positions then need the very same scripts.
	 */
	private static final int FORMAT_VERSION = 7;
	/** The first format whose header says the campaign and difficulty of the game. */
	public static final int CAMPAIGN_FORMAT_VERSION = 7;
	/** Every game used this seed before the seed was saved. */
	private static final int SEED_BEFORE_FORMAT_5 = 1337;
	private static final int OLDEST_FORMAT_VERSION = 2;

	private SavedGame() {
	}

	public static final class Header {
		public String mapFile;
		public int localPlayerIndex;
		public float gameTime;
		/** The seed the game started from; its random numbers themselves are in the saved simulation. */
		public int gameSeed = SEED_BEFORE_FORMAT_5;
		/**
		 * The campaign the game is a mission of, by the number scripts know it by; -1 for none, or a save before format
		 * 7, which did not say.
		 */
		public int campaign = -1;
		/** Whether that campaign is played in its Reforged version, its maps and graphics. */
		public boolean reforgedCampaignMaps;
		/** The game's difficulty; null in a save before format 7. */
		public CMapDifficulty difficulty;
		public int formatVersion = FORMAT_VERSION;
		public List<AiEntry> aiScripts = new ArrayList<>();
		/** Script positions are stored by index, so they only mean something on the same scripts and game data. */
		public List<Integer> scopeSizes = new ArrayList<>();
		/** Scripts the game loaded while running, in that order: instructions are found by index, and AI programs start from what the map's program had by then. */
		public List<AiEntry> loadedScripts = new ArrayList<>();
		public List<War3MapViewer.CreatedDestructable> createdDestructables = new ArrayList<>();
		/** The functions saved script positions are in; null in format 2. */
		SavedScriptPositions.Table positions;

		private void write(final DataOutputStream out) throws IOException {
			out.writeInt(MAGIC);
			out.writeInt(FORMAT_VERSION);
			out.writeUTF(this.mapFile);
			out.writeInt(this.localPlayerIndex);
			out.writeFloat(this.gameTime);
			out.writeInt(this.gameSeed);
			out.writeInt(this.campaign);
			out.writeBoolean(this.reforgedCampaignMaps);
			out.writeInt(this.difficulty == null ? -1 : this.difficulty.ordinal());
			out.writeInt(this.scopeSizes.size());
			for (final int size : this.scopeSizes) {
				out.writeInt(size);
			}
			out.writeInt(this.loadedScripts.size());
			for (final AiEntry entry : this.loadedScripts) {
				out.writeInt(entry.playerIndex);
				out.writeUTF(entry.scriptFile);
				out.writeUTF(entry.mainFunction);
			}
			out.writeInt(this.aiScripts.size());
			for (final AiEntry entry : this.aiScripts) {
				out.writeInt(entry.playerIndex);
				out.writeUTF(entry.scriptFile);
				out.writeUTF(entry.mainFunction);
			}
			out.writeInt(this.createdDestructables.size());
			for (final War3MapViewer.CreatedDestructable created : this.createdDestructables) {
				created.write(out);
			}
			this.positions.write(out);
		}

		private static Header read(final DataInputStream in) throws IOException {
			if (in.readInt() != MAGIC) {
				throw new IOException("Not a saved game of this engine");
			}
			final int version = in.readInt();
			if ((version < OLDEST_FORMAT_VERSION) || (version > FORMAT_VERSION)) {
				throw new IOException("Saved game format " + version + ", this engine reads " + FORMAT_VERSION);
			}
			final Header header = new Header();
			header.formatVersion = version;
			header.mapFile = in.readUTF();
			header.localPlayerIndex = in.readInt();
			header.gameTime = in.readFloat();
			if (version >= 5) {
				header.gameSeed = in.readInt();
			}
			if (version >= CAMPAIGN_FORMAT_VERSION) {
				header.campaign = in.readInt();
				header.reforgedCampaignMaps = in.readBoolean();
				final int difficulty = in.readInt();
				header.difficulty = (difficulty >= 0) && (difficulty < CMapDifficulty.VALUES.length)
						? CMapDifficulty.VALUES[difficulty]
						: null;
			}
			final int scopeCount = in.readInt();
			for (int i = 0; i < scopeCount; i++) {
				header.scopeSizes.add(in.readInt());
			}
			final int scriptCount = in.readInt();
			for (int i = 0; i < scriptCount; i++) {
				final int playerIndex = in.readInt();
				final String scriptFile = in.readUTF();
				header.loadedScripts.add(new AiEntry(playerIndex, scriptFile, in.readUTF()));
			}
			final int aiCount = in.readInt();
			for (int i = 0; i < aiCount; i++) {
				final int playerIndex = in.readInt();
				final String scriptFile = in.readUTF();
				header.aiScripts.add(new AiEntry(playerIndex, scriptFile, in.readUTF()));
			}
			final int createdCount = in.readInt();
			for (int i = 0; i < createdCount; i++) {
				header.createdDestructables.add(War3MapViewer.CreatedDestructable.read(in));
			}
			if (version >= 3) {
				header.positions = SavedScriptPositions.Table.read(in, version >= 4);
			}
			return header;
		}
	}

	public static final class AiEntry {
		public final int playerIndex;
		public final String scriptFile;
		public final String mainFunction;

		public AiEntry(final int playerIndex, final String scriptFile, final String mainFunction) {
			this.playerIndex = playerIndex;
			this.scriptFile = scriptFile;
			this.mainFunction = mainFunction;
		}
	}

	/** What the graph starts from. */
	private static final class Root {
		private CSimulation simulation;
		private List<ScopeThreads> scopes;
		private List<JassAIEnvironment> aiEnvironments;
		private byte[] gameCaches;
		private SavedInterfaceState interfaceState;
		private boolean scriptPaused;
		private boolean playerPaused;
		private List<SavedEffect> effects;
		private List<SavedLightning> lightnings;
		private List<SavedWeather> weathers;
		private List<SavedUbersplat> ubersplats;
	}

	private static final class ScopeThreads {
		private List<GlobalScopeAssignable> globals;
		private List<JassThread> threads;
		private List<JassThread> newThreads;
		private ArrayDeque<?> triggerQueue;
		private ArrayDeque<?> runningTriggerQueue;
	}

	public static Header readHeader(final FileHandle file) throws IOException {
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(file.read()))) {
			return Header.read(in);
		}
	}

	public enum Loadability {
		LOADABLE,
		/** Not a save of this engine, or cut short. */
		DAMAGED,
		/** A save this engine cannot read: another file format, a class it no longer has, or a field of another type. */
		OTHER_VERSION,
		/** Saved inside a script function that is no longer there or no longer the same. */
		OTHER_SCRIPTS
	}

	/** A load turned down before anything of the running game was changed. */
	public static final class LoadRefused extends IOException {
		private static final long serialVersionUID = 1L;
		public final Loadability reason;

		LoadRefused(final Loadability reason, final String message) {
			super(message);
			this.reason = reason;
		}
	}

	private static final Map<String, Loadability> FAILED_LOADS = new HashMap<>();
	private static Loadability unreportedFailure;

	/**
	 * A save another map's scripts turned down can only be known as such once loading started; the list then shows
	 * it as it is, and the menu tells why.
	 */
	public static void loadFailed(final FileHandle file, final IOException failure) {
		final Loadability reason = failure instanceof LoadRefused ? ((LoadRefused) failure).reason
				: Loadability.DAMAGED;
		FAILED_LOADS.put(failureKey(file), reason);
		unreportedFailure = reason;
	}

	public static Loadability consumeLoadFailure() {
		final Loadability reason = unreportedFailure;
		unreportedFailure = null;
		return reason;
	}

	private static String failureKey(final FileHandle file) {
		return file.path() + "|" + file.lastModified();
	}

	/** Reads only the header and class table, so a list can ask without reading the whole save. */
	public static boolean isLoadable(final FileHandle file) {
		return loadability(file) == Loadability.LOADABLE;
	}

	public static Loadability loadability(final FileHandle file) {
		return loadability(file, null);
	}

	/**
	 * With a running game, a save of its map is also held against its scripts, as loading would. A save of another
	 * map can only be checked that far once loading started.
	 */
	public static Loadability loadability(final FileHandle file, final War3MapViewer running) {
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(file.read()))) {
			if (in.readInt() != MAGIC) {
				return Loadability.DAMAGED;
			}
			final int version = in.readInt();
			if ((version < OLDEST_FORMAT_VERSION) || (version > FORMAT_VERSION)) {
				return Loadability.OTHER_VERSION;
			}
		}
		catch (final IOException | RuntimeException e) {
			return Loadability.DAMAGED;
		}
		final Header header;
		final byte[] table;
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(file.read()))) {
			header = Header.read(in);
			final byte[] tableBytes = new byte[in.readInt()];
			in.readFully(tableBytes);
			table = readFully(gunzip(tableBytes));
		}
		catch (final IOException | RuntimeException e) {
			return Loadability.DAMAGED;
		}
		try {
			SavedObjectGraph.checkClassesMatch(table, header.formatVersion);
		}
		catch (final IOException | RuntimeException e) {
			return Loadability.OTHER_VERSION;
		}
		final Loadability failed = FAILED_LOADS.get(failureKey(file));
		if (failed != null) {
			return failed;
		}
		if ((running != null) && (running.simulation != null) && sameMap(header.mapFile, running.getMapFileName())) {
			try {
				final String mismatch = scriptsMismatch(header, running);
				if (mismatch != null) {
					Log.error("Saved game " + file.name() + " cannot be loaded: " + mismatch);
					return Loadability.OTHER_SCRIPTS;
				}
			}
			catch (final RuntimeException e) {
				Log.error(e);
				return Loadability.DAMAGED;
			}
		}
		return Loadability.LOADABLE;
	}

	private static boolean sameMap(final String saved, final String running) {
		return (saved != null) && (running != null)
				&& saved.replace('/', '\\').equalsIgnoreCase(running.replace('/', '\\'));
	}

	private static boolean sameScripts(final List<AiEntry> a, final List<AiEntry> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if ((a.get(i).playerIndex != b.get(i).playerIndex) || !a.get(i).scriptFile.equals(b.get(i).scriptFile)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The running game's programs stand in for the ones loading would build; what they cannot tell (scripts loaded
	 * during the game, AI the running game does not have) is passed over.
	 */
	private static String scriptsMismatch(final Header header, final War3MapViewer running) {
		final CSimulation simulation = running.simulation;
		final GlobalScope mapScope = simulation.getGlobalScope();
		final boolean sameLoadedScripts = sameScripts(header.loadedScripts, running.getLoadedScripts());
		if (header.positions == null) {
			if (sameLoadedScripts && !header.scopeSizes.isEmpty()
					&& (header.scopeSizes.get(0) != mapScope.getInstructions().size())) {
				return "Saved with other scripts or game data (" + header.scopeSizes.get(0) + " instructions, now "
						+ mapScope.getInstructions().size() + ")";
			}
			return null;
		}
		final List<SavedScriptPositions.Program> programs = new ArrayList<>();
		programs.add(new SavedScriptPositions.Program(mapScope));
		for (final AiEntry entry : header.aiScripts) {
			programs.add(runningAiProgram(simulation, entry));
		}
		return SavedScriptPositions.mismatch(header.positions, programs, !sameLoadedScripts);
	}

	private static SavedScriptPositions.Program runningAiProgram(final CSimulation simulation, final AiEntry entry) {
		JassAIEnvironment match = null;
		for (int i = 0; i < simulation.getGameData().getMaxPlayers(); i++) {
			final JassAIEnvironment ai = simulation.getPlayer(i).getAiScript();
			if ((ai != null) && entry.scriptFile.equals(ai.getScriptFile())) {
				if ((match == null) || (i == entry.playerIndex)) {
					match = ai;
				}
			}
		}
		return match == null ? null : new SavedScriptPositions.Program(match.getGlobalScope());
	}

	private static List<GlobalScope> scopesOf(final CSimulation simulation, final List<JassAIEnvironment> aiEnvironments) {
		final List<GlobalScope> scopes = new ArrayList<>();
		scopes.add(simulation.getGlobalScope());
		for (final JassAIEnvironment environment : aiEnvironments) {
			scopes.add(environment.getGlobalScope());
		}
		return scopes;
	}

	public static boolean save(final War3MapViewer viewer, final FileHandle file) {
		final CSimulation simulation = viewer.simulation;
		final List<JassAIEnvironment> aiEnvironments = new ArrayList<>();
		final Header header = new Header();
		header.mapFile = viewer.getMapFileName();
		header.localPlayerIndex = viewer.getLocalPlayerIndex();
		header.gameTime = simulation.getGameTurnTick() * WarsmashConstants.SIMULATION_STEP_TIME;
		header.gameSeed = viewer.getGameSeed();
		header.campaign = viewer.getCampaign();
		header.reforgedCampaignMaps = viewer.isReforgedCampaignMaps();
		header.difficulty = viewer.getMapConfig() == null ? null : viewer.getMapConfig().getGameDifficulty();
		for (int i = 0; i < simulation.getGameData().getMaxPlayers(); i++) {
			final CPlayer player = simulation.getPlayer(i);
			final JassAIEnvironment aiScript = player.getAiScript();
			if (aiScript != null) {
				aiEnvironments.add(aiScript);
				header.aiScripts.add(new AiEntry(i, aiScript.getScriptFile(), aiScript.getMainFunction()));
			}
		}
		header.loadedScripts.addAll(viewer.getLoadedScripts());
		header.createdDestructables.addAll(viewer.getCreatedDestructables());
		final List<GlobalScope> scopes = scopesOf(simulation, aiEnvironments);
		for (final GlobalScope scope : scopes) {
			header.scopeSizes.add(scope.getInstructions().size());
		}
		final SavedGameRules rules = new SavedGameRules(viewer, scopes, aiEnvironments);
		for (final CDestructable destructable : simulation.getDestructables()) {
			rules.nameDestructable(destructable, destructable.getHandleId());
		}
		simulation.dropStalePathfindingNodes();
		try {
			final Root root = new Root();
			root.simulation = simulation;
			root.aiEnvironments = aiEnvironments;
			root.scopes = new ArrayList<>();
			for (final GlobalScope scope : scopes) {
				final ScopeThreads state = new ScopeThreads();
				state.globals = scope.getIndexedGlobals();
				state.threads = scope.getThreads();
				state.newThreads = scope.getNewThreads();
				state.triggerQueue = scope.getTriggerQueue();
				state.runningTriggerQueue = scope.getRunningTriggerQueue();
				root.scopes.add(state);
			}
			root.gameCaches = bytesOf(CGameCache::writeAll);
			root.interfaceState = viewer.captureInterfaceState();
			root.scriptPaused = viewer.isGamePaused(War3MapViewer.PauseSource.SCRIPT);
			root.playerPaused = viewer.isGamePaused(War3MapViewer.PauseSource.PLAYER);
			root.effects = new ArrayList<>();
			for (final RenderEffect effect : viewer.projectiles) {
				if ((effect instanceof RenderSpellEffect) && ((RenderSpellEffect) effect).isLasting()) {
					root.effects.add(SavedEffect.of((RenderSpellEffect) effect));
				}
			}
			root.lightnings = new ArrayList<>();
			for (final RenderLightningEffect lightning : viewer.getLightnings()) {
				if (!lightning.isRemoved()) {
					root.lightnings.add(SavedLightning.of(lightning));
				}
			}
			root.weathers = new ArrayList<>();
			for (final RenderWeatherEffect weather : viewer.getWeatherEffects()) {
				if (!weather.isRemoved()) {
					root.weathers.add(SavedWeather.of(weather));
				}
			}
			root.ubersplats = new ArrayList<>();
			for (final RenderUbersplat splat : viewer.getScriptUbersplats()) {
				if (!splat.isGone()) {
					root.ubersplats.add(SavedUbersplat.of(splat));
				}
			}

			// Only taking the state apart has to happen between two steps; the rest is left to the save writer.
			final ByteArrayOutputStream bodyBytes = new SavedObjectGraph.Bytes(1 << 22);
			final SavedObjectGraph.Writer writer;
			try (DataOutputStream bodies = new DataOutputStream(bodyBytes)) {
				writer = new SavedObjectGraph.Writer(rules, bodies);
				writer.writeRoot(root);
			}
			header.positions = rules.getPositions();
			header.positions.recordGlobals(scopes);
			final byte[] table = writer.finishTable();
			final ByteArrayOutputStream headerBytes = new ByteArrayOutputStream(1 << 16);
			try (DataOutputStream out = new DataOutputStream(headerBytes)) {
				header.write(out);
			}
			CSaveStorage.writeInBackground(file, out -> {
				final ByteArrayOutputStream tableBytes = gzip(table.length, gzipped -> gzipped.write(table));
				final ByteArrayOutputStream compressedBodies = gzip(bodyBytes.size(), bodyBytes::writeTo);
				headerBytes.writeTo(out);
				out.writeInt(tableBytes.size());
				tableBytes.writeTo(out);
				out.writeInt(compressedBodies.size());
				compressedBodies.writeTo(out);
			});
			final StringBuilder report = new StringBuilder("Saved game ").append(file.name()).append(": ")
					.append(writer.objectCount()).append(" objects, ")
					.append(bodyBytes.size() + table.length).append(" bytes before compression, ")
					.append(root.effects.size()).append(" effects, ").append(root.lightnings.size()).append(" lightnings");
			for (final Map.Entry<String, Integer> dropped : rules.getDroppedReport().entrySet()) {
				report.append("\n  left out ").append(dropped.getKey()).append(" x").append(dropped.getValue());
			}
			Log.warn(report);
			return true;
		}
		catch (final Exception exc) {
			Log.error(exc);
			return false;
		}
	}

	private interface Content {
		void writeTo(OutputStream out) throws IOException;
	}

	private static ByteArrayOutputStream gzip(final int length, final Content content) throws IOException {
		final ByteArrayOutputStream compressed = new ByteArrayOutputStream(Math.max(1 << 12, length / 4));
		try (GZIPOutputStream out = new GZIPOutputStream(compressed, 1 << 16)) {
			content.writeTo(out);
		}
		return compressed;
	}

	private static byte[] readFully(final DataInputStream compressed) throws IOException {
		try (DataInputStream in = compressed) {
			final ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
			final byte[] buffer = new byte[1 << 16];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			return out.toByteArray();
		}
	}

	/**
	 * Replaces the state of a map just started, whose main function has not run, with the saved one.
	 *
	 * @param aiStarter sets up the AI script of a player, without running it
	 */
	public static void load(final War3MapViewer viewer, final FileHandle file, final AiScriptStarter aiStarter)
			throws IOException {
		final CSimulation simulation = viewer.simulation;
		final Header header;
		final byte[] tableBytes;
		final byte[] bodyBytes;
		final byte[] table;
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(file.read(), 1 << 16))) {
			header = Header.read(in);
			tableBytes = new byte[in.readInt()];
			in.readFully(tableBytes);
			bodyBytes = new byte[in.readInt()];
			in.readFully(bodyBytes);
			checkIntact(tableBytes);
			checkIntact(bodyBytes);
			table = readFully(gunzip(tableBytes));
			// A save from another engine build must be refused here, while the map can still start normally.
			try {
				SavedObjectGraph.checkClassesMatch(table, header.formatVersion);
			}
			catch (final IOException e) {
				throw new LoadRefused(Loadability.OTHER_VERSION, e.getMessage());
			}
		}
		catch (final RuntimeException e) {
			throw new IOException("Damaged save", e);
		}
		for (final AiEntry entry : header.loadedScripts) {
			aiStarter.start(entry);
		}
		final List<JassAIEnvironment> aiEnvironments = new ArrayList<>();
		for (final AiEntry entry : header.aiScripts) {
			aiEnvironments.add(simulation.getPlayer(entry.playerIndex).getAiScript());
		}
		final Map<Integer, CDestructable> placedDestructables = new HashMap<>();
		int legacyHandleId = HandleIdAllocator.LEGACY_FIRST_ID;
		for (final CDestructable destructable : simulation.getDestructables()) {
			// Before format 6 one counter numbered everything, and the map's destructables came first.
			placedDestructables.put(header.formatVersion < 6 ? legacyHandleId++ : destructable.getHandleId(),
					destructable);
		}
		for (final War3MapViewer.CreatedDestructable created : header.createdDestructables) {
			placedDestructables.put(created.savedHandleId, viewer.createDestructableForSavedGame(created));
		}
		final List<GlobalScope> loadedScopes = scopesOf(simulation, aiEnvironments);
		final List<SavedScriptPositions.Program> programs = new ArrayList<>();
		for (final GlobalScope scope : loadedScopes) {
			programs.add(new SavedScriptPositions.Program(scope));
		}
		final SavedScriptPositions.Mapping mapping;
		if (header.positions != null) {
			final String mismatch = SavedScriptPositions.mismatch(header.positions, programs, false);
			if (mismatch != null) {
				throw new LoadRefused(Loadability.OTHER_SCRIPTS, mismatch);
			}
			mapping = new SavedScriptPositions.Mapping(header.positions, programs);
		}
		else {
			mapping = null;
			for (int i = 0; i < loadedScopes.size(); i++) {
				if ((i >= header.scopeSizes.size())
						|| (loadedScopes.get(i).getInstructions().size() != header.scopeSizes.get(i))) {
					throw new LoadRefused(Loadability.OTHER_SCRIPTS, "Saved with other scripts or game data");
				}
			}
		}
		// From here on the map's own state is gone, so everything that can refuse the save comes before this.
		viewer.clearRenderPeersForSavedGame();
		viewer.setGameSeedForSavedGame(header.gameSeed);
		final SavedGameRules rules = new SavedGameRules(viewer, loadedScopes, aiEnvironments);
		for (final Map.Entry<Integer, CDestructable> destructable : placedDestructables.entrySet()) {
			rules.nameDestructable(destructable.getValue(), destructable.getKey());
		}
		final Root root;
		try (DataInputStream bodies = gunzip(bodyBytes)) {
			final SavedObjectGraph.Reader reader = new SavedObjectGraph.Reader(rules, table, bodies,
					header.formatVersion);
			root = (Root) reader.readRoot();
			restoreHandleIds(reader, simulation, header.formatVersion);
			if (mapping != null) {
				mapping.apply(reader.objectsOf(JassThread.class), reader.objectsOf(CodeJassValue.class), programs,
						header.scopeSizes);
			}
		}
		catch (final IOException e) {
			// The simulation is read in place, so past this point starting the map normally is no longer possible.
			throw new IllegalStateException("Could not read the saved game " + file.name(), e);
		}
		final List<GlobalScope> scopes = scopesOf(simulation, aiEnvironments);
		for (int i = 0; i < scopes.size(); i++) {
			scopes.get(i).restoreThreads(root.scopes.get(i).threads, root.scopes.get(i).newThreads,
					root.scopes.get(i).triggerQueue, root.scopes.get(i).runningTriggerQueue);
		}
		for (int i = 0; i < aiEnvironments.size(); i++) {
			simulation.getPlayer(header.aiScripts.get(i).playerIndex).setAiScript(aiEnvironments.get(i));
		}
		try (DataInputStream caches = new DataInputStream(new ByteArrayInputStream(root.gameCaches))) {
			CGameCache.restoreAll(caches);
		}
		viewer.finishLoadingSavedGame(placedDestructables.values());
		for (final SavedEffect effect : root.effects) {
			effect.show(viewer);
		}
		for (final SavedLightning lightning : root.lightnings) {
			lightning.show(viewer);
		}
		for (final SavedWeather weather : root.weathers) {
			weather.show(viewer);
		}
		for (final SavedUbersplat splat : root.ubersplats) {
			splat.show(viewer);
		}
		for (final CImage image : simulation.getScriptImages()) {
			viewer.updateImage(image);
		}
		for (final CTrackable trackable : simulation.getTrackables()) {
			viewer.showTrackable(trackable);
		}
		simulation.getTerrainDeformations().restore(simulation);
		simulation.getTerrainTypeChanges().restore(simulation);
		viewer.setGamePaused(War3MapViewer.PauseSource.SCRIPT, root.scriptPaused);
		viewer.setGamePaused(War3MapViewer.PauseSource.PLAYER, root.playerPaused);
		viewer.restoreInterfaceState(root.interfaceState);
		simulation.setSaveBasicFilename(file.nameWithoutExtension());
		// Zone maps restore their saves from the player's own save folder only on a player's load; a load by the
		// map's LoadGame (zone travel) is told apart by this flag.
		viewer.getMapConfig().setMapFlag(CMapFlag.MAP_RELOADED, viewer.isSavedGameLoadedByScript());
		simulation.fireGameEvent(JassGameEventsWar3.EVENT_GAME_LOADED);
	}

	/**
	 * Handles from a save made before they had ids get them now, in the order the save holds them; Condition and
	 * Filter find their saved handles again. In a newer save an id of 0 is a handle the script never saw, which gets
	 * its id only when asked, as it would have in the saved game.
	 */
	private static void restoreHandleIds(final SavedObjectGraph.Reader reader, final CSimulation simulation,
			final int formatVersion) {
		final HandleIdAllocator handleIds = simulation.getHandleIdAllocator();
		for (final CAllocatedHandle handle : reader.objectsOf(CAllocatedHandle.class)) {
			if ((formatVersion < 6) && (handle.getHandleId() == 0)) {
				handle.setHandleId(handle instanceof SavedLightning ? handleIds.createLightningId()
						: handleIds.createHandleId());
			}
		}
		for (final BoolExprCondition condition : reader.objectsOf(BoolExprCondition.class)) {
			final GlobalScope scope = condition.getScope();
			final CodeJassValue function = condition.getFunction();
			if ((scope != null) && (function != null)
					&& (scope.getFunctionBoolExpr(function.getUserFunctionInstructionPtr()) == null)) {
				scope.putFunctionBoolExpr(function.getUserFunctionInstructionPtr(), condition);
			}
		}
	}

	/** Loads a script again without running it: for a player's AI, or for the map's own program at index -1. */
	public interface AiScriptStarter {
		void start(AiEntry entry);
	}

	private static void checkIntact(final byte[] compressed) throws IOException {
		try (DataInputStream in = gunzip(compressed)) {
			final byte[] buffer = new byte[1 << 16];
			while (in.read(buffer) != -1) {
				// Reading to the end makes the gzip stream compare its checksum.
			}
		}
	}

	private static byte[] bytesOf(final CSaveStorage.Writer writer) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			writer.write(out);
		}
		return bytes.toByteArray();
	}

	static DataInputStream gunzip(final byte[] compressed) throws IOException {
		return new DataInputStream(
				new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(compressed), 1 << 16), 1 << 16));
	}
}
