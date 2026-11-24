package com.etheller.warsmash.viewer5;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.etheller.warsmash.viewer5.gl.DataTexture;
import com.etheller.warsmash.common.FetchDataTypeName;
import com.etheller.warsmash.common.LoadGenericCallback;
import com.etheller.warsmash.datasources.AssetPathFallback;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.IndependentStream;
import com.etheller.warsmash.util.DataErrors;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.viewer5.gl.ClientBuffer;
import com.etheller.warsmash.viewer5.gl.WebGL;
import com.etheller.warsmash.viewer5.handlers.ResourceHandler;
import com.etheller.warsmash.viewer5.handlers.ResourceHandlerConstructionParams;
import com.etheller.warsmash.viewer5.handlers.blp.MipLevels;
import com.google.common.io.ByteStreams;

public abstract class ModelViewer {
	public DataSource dataSource;
	public final CanvasProvider canvas;
	public List<Resource> resources;
	public Map<String, Resource> fetchCache;
	private final Map<String, String> foundSources = new HashMap<>();
	private DataSource foundSourcesData;
	private final MissingSources missingSources = new MissingSources();
	public int frameTime;
	public GL20 gl;
	public WebGL webGL;
	public List<Scene> scenes;
	private int visibleCells;
	private int visibleInstances;
	private int updatedParticles;
	public int frame;
	public final int rectBuffer;
	public ClientBuffer buffer;
	public boolean audioEnabled;
	private final Map<Model, List<TextureMapper>> textureMappers;
	private final Set<ResourceHandler> handlers;
	private final List<DataTexture> dataTextures = new ArrayList<>();
	/** The largest width and height of a model texture, 0 for no limit; see {@link #setTextureSizeLimit(int)}. */
	private int textureSizeLimit;
	private int textureSkippedLevels;
	private boolean textureCompression;
	private boolean blockTextures;
	private long textureBytes;
	/** The bytes of model textures above which those not drawn for a while are let go, 0 for no budget. */
	private long textureBudget;
	private int framesToBudgetCheck;
	/** Frame numbers at the budget checks with their times, the oldest first, to tell how long ago a frame was. */
	private final ArrayDeque<long[]> budgetCheckFrames = new ArrayDeque<>();
	private int evictedTextures;
	private int evictedModels;
	private boolean deferTextureLoads;
	private boolean deferModelTextures;
	private boolean deferModelLoads;
	private boolean prepareModelsAhead = true;
	/** The pending model whose build is begun and goes on in the next frames. */
	private Model<?> building;
	/** How many pending models have not had their file opened yet. */
	private int lazyModels;
	private final ArrayDeque<Model<?>> pendingModels = new ArrayDeque<>();
	private final ModelPrepareBudget prepareBudget = new ModelPrepareBudget();
	private final TextureLoadQueue deferredTextureLoads = new TextureLoadQueue(TextureWorkers.EXECUTOR,
			TextureWorkers.THREADS, TextureWorkers.READY_BYTES);

	public ModelViewer(final DataSource dataSource, final CanvasProvider canvas) {
		this.dataSource = dataSource;
		this.canvas = canvas;
		this.resources = new ArrayList<>();
		this.fetchCache = new HashMap<>();
		this.handlers = new HashSet<ResourceHandler>();
		this.frameTime = 1000 / 60;
		this.gl = Gdx.gl;
		this.webGL = new WebGL(this.gl);
		this.scenes = new ArrayList<>();
		this.visibleCells = 0;
		this.visibleInstances = 0;
		this.updatedParticles = 0;

		this.frame = 0;

		this.rectBuffer = this.gl.glGenBuffer();
		this.buffer = new ClientBuffer(this.gl);
		this.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, this.rectBuffer);
		final ByteBuffer temp = ByteBuffer.allocateDirect(6).order(ByteOrder.nativeOrder());
		temp.put((byte) 0);
		temp.put((byte) 1);
		temp.put((byte) 2);
		temp.put((byte) 0);
		temp.put((byte) 2);
		temp.put((byte) 3);
		temp.clear();
		this.gl.glBufferData(GL20.GL_ARRAY_BUFFER, temp.capacity(), temp, GL20.GL_STATIC_DRAW);
		this.audioEnabled = false;
		this.textureMappers = new HashMap<Model, List<TextureMapper>>();
	}

	/**
	 * Model and terrain textures larger than this are loaded from their first stored mip level within it (0 for no
	 * limit). Phones decode the block compressed images of the game data into plain RGBA, where one 2048 pixel HD
	 * texture alone takes 16 MB.
	 */
	public void setTextureSizeLimit(final int textureSizeLimit) {
		this.textureSizeLimit = Math.max(0, textureSizeLimit);
	}

	/**
	 * How many of the largest levels a model texture the size limit leaves whole loses (devices with little memory);
	 * terrain textures keep theirs.
	 */
	public void setTextureSkippedLevels(final int skippedLevels) {
		this.textureSkippedLevels = Math.max(0, skippedLevels);
	}

	/**
	 * The bytes the model textures may hold before those not drawn for {@link #UNUSED_TEXTURE_NANOS} are let go, 0 for
	 * no budget. A texture let go is read and decoded again on helper threads when it is drawn next, and until then
	 * it is drawn from a small level it keeps.
	 */
	public void setTextureBudget(final long bytes) {
		this.textureBudget = Math.max(0, bytes);
	}

	/**
	 * Whether the model textures are uploaded as ETC2 blocks, a quarter to a half of the memory of plain RGBA, see
	 * {@link com.etheller.warsmash.viewer5.handlers.blp.TextureTranscoder}. The caller makes sure the GPU reads them.
	 */
	public void setTextureCompression(final boolean textureCompression) {
		this.textureCompression = textureCompression;
	}

	/**
	 * Whether the DDS textures are uploaded in the block format they are stored in (S3TC, and RGTC for the two channel
	 * normal maps), a quarter of the memory of plain RGBA, which is what a GPU that reads those formats allows; the
	 * caller makes sure it does. Textures are then neither decoded nor re-encoded, and ETC2 compression, which does
	 * that, takes precedence.
	 */
	public void setBlockTextures(final boolean blockTextures) {
		this.blockTextures = blockTextures;
	}

	public boolean isBlockTextures() {
		return this.blockTextures && !this.textureCompression;
	}

	/**
	 * While set, a texture file is read when its model asks for it but decoded and compressed later, on helper threads,
	 * and uploaded by {@link #loadNextDeferredTexture()}: decoding and compressing the textures of one model can take
	 * seconds, and a map's loading hands the frame back between them.
	 *
	 * @return the setting before
	 */
	public boolean setDeferTextureLoads(final boolean defer) {
		final boolean before = this.deferTextureLoads;
		this.deferTextureLoads = defer;
		return before;
	}

	/**
	 * While set, the textures of a model loaded from now on are always deferred as by {@link #setDeferTextureLoads},
	 * so that a model first needed while the game goes on does not hold a frame up for decoding them; the caller
	 * uploads them with {@link #finishReadyDeferredTexture()}, and what uses a texture draws nothing until then.
	 */
	public void setDeferModelTextures(final boolean deferModelTextures) {
		this.deferModelTextures = deferModelTextures;
	}

	public boolean isDeferModelTextures() {
		return this.deferModelTextures;
	}

	/**
	 * While set, a model file is read and parsed on a helper thread and the model stays empty, not ok, until
	 * {@link #finishReadyModels(long)} builds it in a later frame: reading and parsing are half of what a model first
	 * needed while the game goes on costs, and building it then takes a frame of its own instead of adding to the
	 * frame of the unit that asks for it. Instances made with {@link Model#addWaitingInstance()} fill in when it is
	 * built; any other use of the model through this viewer builds it at once.
	 *
	 * @return the setting before
	 */
	public boolean setDeferModelLoads(final boolean defer) {
		final boolean before = this.deferModelLoads;
		this.deferModelLoads = defer;
		return before;
	}

	/**
	 * Whether the files of the models loaded while deferring are read and parsed at once (the default). When not,
	 * they are not even opened until a model is needed or the setting is back: a game that is not drawn, or one that
	 * skips ahead, would only pile up what it never shows.
	 */
	public void setPrepareModelsAhead(final boolean ahead) {
		this.prepareModelsAhead = ahead;
	}

	public boolean hasPendingModels() {
		return !this.pendingModels.isEmpty();
	}

	public int pendingModelCount() {
		return this.pendingModels.size();
	}

	/**
	 * Builds the models whose files are read and parsed, the oldest first, a unit of work (a texture, a geoset, a
	 * node) at a time while the time lasts; at least one unit when a model is ready, none waits for its helper.
	 */
	public void finishReadyModels(final long nanos) {
		final long end = System.nanoTime() + nanos;
		startWaitingPreparations(end);
		while (true) {
			Model<?> next = this.building;
			if (next == null) {
				for (final Model<?> model : this.pendingModels) {
					if ((model.lazySource == null) && model.isPrepared()) {
						next = model;
						break;
					}
				}
				if (next == null) {
					return;
				}
			}
			buildPending(next, end);
			if (System.nanoTime() >= end) {
				return;
			}
			startWaitingPreparations(end);
		}
	}

	/**
	 * Opens the files of waiting models, the oldest first, as the budget of {@link ModelPrepareBudget} and the time
	 * allow: those loaded while the game was not drawn, and those past the budget when they were asked for.
	 */
	private void startWaitingPreparations(final long end) {
		if (!this.prepareModelsAhead || (this.lazyModels <= 0)) {
			return;
		}
		final List<Model<?>> loadedNow = new ArrayList<>();
		for (final Model<?> model : this.pendingModels) {
			if (model.lazySource != null) {
				if (!mayStartPreparation()) {
					break;
				}
				if (!startPreparation(model)) {
					loadedNow.add(model);
				}
				if (System.nanoTime() >= end) {
					break;
				}
			}
		}
		for (final Model<?> model : loadedNow) {
			completeModel(model);
		}
	}

	/** Whether the helper may be given another model file now, see {@link ModelPrepareBudget}. */
	private boolean mayStartPreparation() {
		int unread = 0;
		long readBytes = 0;
		for (final Model<?> model : this.pendingModels) {
			if ((model.lazySource == null) && !model.built) {
				final long bytes = model.getPreparedFileBytes();
				if (bytes == 0) {
					unread++;
				}
				else {
					readBytes += bytes;
				}
			}
		}
		return this.prepareBudget.admits(unread, readBytes);
	}

	void finishPendingModel(final Model<?> model) {
		if (model.stepping) {
			// asked for by what its own build makes
			return;
		}
		buildPending(model, Long.MAX_VALUE);
	}

	/**
	 * Starts the helper that reads and parses a model whose file was not opened yet.
	 *
	 * @return false when the model was loaded from its file now instead and is complete
	 */
	private boolean startPreparation(final Model<?> model) {
		final String source = model.lazySource;
		if (source == null) {
			return true;
		}
		model.lazySource = null;
		this.lazyModels--;
		try {
			final InputStream stream = openForHelper(source);
			if ((stream != null) && model.prepareLater(stream, ModelWorkers.EXECUTOR)) {
				return true;
			}
			final boolean deferBefore = this.deferTextureLoads;
			this.deferTextureLoads = deferBefore || this.deferModelTextures;
			try {
				model.loadData(stream, null);
			}
			finally {
				this.deferTextureLoads = deferBefore;
			}
			return false;
		}
		catch (final Exception e) {
			throw new IllegalStateException("Unable to load data: " + source, e);
		}
	}

	/** Builds a pending model until the time is up, and completes it when it is built. */
	private void buildPending(final Model<?> model, final long endNanos) {
		boolean done = true;
		boolean returned = false;
		boolean progressed = false;
		final long callStart = System.nanoTime();
		try {
			if (model.built) {
				// nothing left of the build
			}
			else if (!startPreparation(model)) {
				model.built = true;
			}
			else {
				final long start = System.nanoTime();
				final boolean deferTexturesBefore = this.deferTextureLoads;
				final boolean deferModelsBefore = this.deferModelLoads;
				this.deferTextureLoads = deferTexturesBefore || this.deferModelTextures;
				// what the model loads with it (attachment models) is loaded now, as when its file was read at once
				this.deferModelLoads = false;
				model.stepping = true;
				try {
					model.built = model.buildPrepared(endNanos);
					progressed = true;
				}
				finally {
					model.stepping = false;
					this.deferTextureLoads = deferTexturesBefore;
					this.deferModelLoads = deferModelsBefore;
				}
			}
			done = model.built && loadWaitingInstances(model, endNanos, progressed);
			model.buildNanos += System.nanoTime() - callStart;
			returned = true;
		}
		finally {
			if (!returned || done) {
				completeModel(model);
			}
			else {
				this.building = model;
			}
		}
	}

	/**
	 * Loads the instances made before the model was built, one at a time while the time lasts (as many as are due
	 * when the call has not done any work yet is one).
	 *
	 * @return true when all are loaded
	 */
	private boolean loadWaitingInstances(final Model<?> model, final long endNanos, boolean progressed) {
		if (!model.ok) {
			return true;
		}
		final List<ModelInstance> instances = model.preloadedInstances;
		// what an instance asks of its model as it loads is answered from the built model, not by going on here
		model.stepping = true;
		try {
			// the models its instances attach are built first, as time allows, not on the spot as an instance loads
			Model<?> required;
			while ((required = model.firstPendingRequired()) != null) {
				if (progressed && (System.nanoTime() >= endNanos)) {
					return false;
				}
				buildPending(required, endNanos);
				progressed = true;
				if (required.isPending()) {
					return false;
				}
			}
			while (model.waitingLoaded < instances.size()) {
				if (progressed && (System.nanoTime() >= endNanos)) {
					return false;
				}
				instances.get(model.waitingLoaded++).loadWaiting();
				progressed = true;
			}
		}
		finally {
			model.stepping = false;
		}
		return true;
	}

	private void completeModel(final Model<?> model) {
		this.pendingModels.remove(model);
		final long fileBytes = model.getPreparedFileBytes();
		if (fileBytes > 0) {
			this.prepareBudget.read(fileBytes);
		}
		if (this.building == model) {
			this.building = null;
		}
		model.setPending(false);
		model.preloadedInstances = new ArrayList<>();
		model.waitingLoaded = 0;
		final long nanos = model.buildNanos;
		model.buildNanos = 0;
		if (PerfStats.ENABLED) {
			PerfStats.loaded(model.fetchUrl, nanos);
		}
	}

	/** The thread that reads and parses the model files of {@link #setDeferModelLoads}. */
	private static final class ModelWorkers {
		static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
			final Thread thread = new Thread(runnable, "model-prepare");
			thread.setDaemon(true);
			thread.setPriority(Thread.NORM_PRIORITY - 1);
			return thread;
		});
	}

	/**
	 * Queues the rest of a texture's loading when deferring; false when it is to be done now.
	 *
	 * @param prepare runs on a helper thread and may not touch OpenGL or anything the game thread uses; it returns the
	 *                bytes of the result it holds
	 * @param finish  runs on the game thread after prepare, in a frame of its own
	 */
	public boolean deferTextureLoad(final LongSupplier prepare, final Runnable finish) {
		if (!this.deferTextureLoads) {
			return false;
		}
		this.deferredTextureLoads.add(prepare, finish);
		return true;
	}

	public boolean hasDeferredTextures() {
		return !this.deferredTextureLoads.isEmpty();
	}

	/** How many textures wait to be decoded or uploaded. */
	public int deferredTextureCount() {
		return this.deferredTextureLoads.size();
	}

	/** Finishes the first queued texture whose preparation is done; when none is done it waits a moment for one. */
	public void loadNextDeferredTexture() {
		this.deferredTextureLoads.loadNext();
	}

	/** Finishes the first queued texture whose preparation is done, without waiting; false when none is done yet. */
	public boolean finishReadyDeferredTexture() {
		return this.deferredTextureLoads.finishReady();
	}

	/**
	 * The threads that decode and compress textures while a map loads. Each such texture holds its decoded levels on
	 * the heap, and the encoder spreads one texture over the other cores itself, so only a few are at work at once.
	 */
	private static final class TextureWorkers {
		static final int THREADS = Runtime.getRuntime().maxMemory() < (768L << 20) ? 1 : 2;
		/** The bytes of prepared textures that may wait for their upload before the helpers stop starting new ones. */
		static final long READY_BYTES = Math.max(16L << 20, Math.min(64L << 20, Runtime.getRuntime().maxMemory() / 16));
		static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(THREADS, runnable -> {
			final Thread thread = new Thread(runnable, "texture-prepare");
			thread.setDaemon(true);
			thread.setPriority(Thread.NORM_PRIORITY - 1);
			return thread;
		});
	}

	public boolean isTextureCompression() {
		return this.textureCompression;
	}

	public int getTextureSizeLimit() {
		return this.textureSizeLimit;
	}

	/** The size limit of model textures with the levels they skip, as the decoders take it. */
	public int getTextureSizeRule() {
		return MipLevels.sizeRule(this.textureSizeLimit, this.textureSkippedLevels);
	}

	/** Gives the GPU memory of every texture that can be read again back, and returns the bytes freed. */
	public long evictAllTextures() {
		final long before = this.textureBytes;
		for (final Resource resource : new ArrayList<>(this.resources)) {
			if (resource instanceof RawOpenGLTextureResource) {
				final RawOpenGLTextureResource texture = (RawOpenGLTextureResource) resource;
				if (texture.isEvictable()) {
					texture.evict();
					this.evictedTextures++;
				}
			}
		}
		return before - this.textureBytes;
	}

	/** Reads again, now, every texture that was let go, so that they are back before they are drawn. */
	public void restoreTextures() {
		for (final Resource resource : new ArrayList<>(this.resources)) {
			if ((resource instanceof RawOpenGLTextureResource) && ((RawOpenGLTextureResource) resource).isEvicted()) {
				((RawOpenGLTextureResource) resource).restore();
			}
		}
	}

	public boolean hasTextureBudget() {
		return this.textureBudget > 0;
	}

	public int getEvictedTextures() {
		return this.evictedTextures;
	}

	/** Counts the texture memory this viewer's textures hold, as uploaded (all mip levels). */
	public void addTextureBytes(final long bytes) {
		this.textureBytes += bytes;
	}

	public long getTextureBytes() {
		return this.textureBytes;
	}

	public void setDataSource(final DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public boolean enableAudio() {
		this.audioEnabled = true;
		return this.audioEnabled;
	}

	public boolean addHandler(ResourceHandler handler) {
		if (handler != null) {

			// Allow to pass also the handler's module for convenience.
			if (handler.handler != null) {
				handler = handler.handler;
			}

			if (!this.handlers.contains(handler)) {
				// Check if the handler has a loader, and if so load it.
				if (handler.load && !handler.load(this)) {
					onResourceLoadError();
					return false;
				}

				this.handlers.add(handler);

				return true;
			}
		}
		return false;
	}

	public Scene addSimpleScene() {
		return addSimpleScene(createLightManager(true));
	}

	public Scene addSimpleScene(final SceneLightManager lightManager) {
		final Scene scene = new SimpleScene(this, lightManager);

		this.scenes.add(scene);

		return scene;
	}

	public WorldScene addWorldScene() {
		final WorldScene scene = new WorldScene(this, createLightManager(false));

		this.scenes.add(scene);

		return scene;
	}

	public boolean removeScene(final Scene scene) {
		return this.scenes.remove(scene);
	}

	public void trackDataTexture(final DataTexture texture) {
		this.dataTextures.add(texture);
	}

	public void dispose() {
		this.deferredTextureLoads.close();
		for (final Model<?> model : this.pendingModels) {
			model.discardPrepared();
		}
		this.pendingModels.clear();
		this.building = null;
		this.lazyModels = 0;
		for (final Resource resource : this.resources) {
			resource.dispose();
		}
		this.resources.clear();
		this.fetchCache.clear();
		this.missingSources.clear();
		for (final Scene scene : this.scenes) {
			scene.dispose();
		}
		this.scenes.clear();
		for (final DataTexture texture : this.dataTextures) {
			texture.dispose();
		}
		this.dataTextures.clear();
		this.gl.glDeleteBuffer(this.rectBuffer);
		this.buffer.dispose();
		this.webGL.dispose();
	}

	public void clear() {
		this.scenes.clear();
	}

	public Object[] findHandler(final String ext) {
		for (final ResourceHandler handler : this.handlers) {
			for (final String[] extension : handler.extensions) {
				if (extension[0].equals(ext)) {
					return new Object[] { handler, extension[1] };
				}
			}
		}
		return null;
	}

	/**
	 * The file a solved path loads. Each is a lookup in every data source, and the same paths come again and again (a
	 * unit's team colour textures, the models of units and effects of one type), so what was found is kept for the
	 * data source it was found in. A missing file is looked for again and reported each time, as before.
	 */
	private String findSource(final String src) {
		if (this.foundSourcesData != this.dataSource) {
			this.foundSources.clear();
			this.foundSourcesData = this.dataSource;
		}
		final String known = this.foundSources.get(src);
		if (known != null) {
			return known;
		}
		final String found = lookupSource(this.dataSource, src);
		if (found != null) {
			this.foundSources.put(src, found);
			return found;
		}
		final String missing = this.missingSources.get(this.dataSource, src);
		if (missing != null) {
			return missing;
		}
		DataErrors.report(DataErrors.MISSING_ASSET, src, "Attempting to load non-existant file: " + src);
		final String answer = withDdsBeside(this.dataSource, src);
		this.missingSources.put(this.dataSource, src, answer);
		return answer;
	}

	/**
	 * The file a solved path loads, as {@link #load} finds it; null when the data source has none. A helper thread
	 * may look it up ahead for the game thread, see {@link #rememberSource}.
	 */
	public static String lookupSource(final DataSource dataSource, final String src) {
		if (dataSource.has(src)) {
			return withDdsBeside(dataSource, src);
		}
		return AssetPathFallback.find(dataSource, src);
	}

	private static String withDdsBeside(final DataSource dataSource, final String src) {
		if ((src.length() > 4) && src.regionMatches(true, src.length() - 4, ".blp", 0, 4)) {
			// The latest data source with either name decides, so a .dds in an override folder wins over the
			// game's .blp; within one source the .blp stays first.
			final String ddsPath = src.substring(0, src.length() - 4) + ".dds";
			if (ddsPath.equals(dataSource.findFirst(src, ddsPath))) {
				return ddsPath;
			}
		}
		return src;
	}

	/** What {@link #lookupSource} found for a solved path in this data source, so that loading it does not look again. */
	public void rememberSource(final DataSource dataSource, final String src, final String found) {
		if (this.foundSourcesData != this.dataSource) {
			this.foundSources.clear();
			this.foundSourcesData = this.dataSource;
		}
		if ((dataSource == this.dataSource) && (found != null)) {
			this.foundSources.put(src, found);
		}
	}

	/**
	 * Loads a texture a model instance asks for after the model is built (a team colour, a replaceable one). While the
	 * game goes on, a texture not loaded before is read, decoded and uploaded like the textures of a model loaded then
	 * (see {@link #setDeferModelTextures}) instead of holding up the frame of the instance that asks for it.
	 */
	public Resource loadInstanceTexture(final String src, final PathSolver pathSolver, final Object solverParams) {
		final boolean deferBefore = this.deferTextureLoads;
		this.deferTextureLoads = deferBefore || this.deferModelTextures;
		try {
			return load(src, pathSolver, solverParams);
		}
		finally {
			this.deferTextureLoads = deferBefore;
		}
	}

	public Resource load(final String src, final PathSolver pathSolver, final Object solverParams) {
		String finalSrc = src;
		String extension = "";

		// If a given path solver, resolve.
		if (pathSolver != null) {
			final SolvedPath solved = pathSolver.solve(src, solverParams);

			finalSrc = findSource(solved.getFinalSrc());
			extension = solved.getExtension();
			if (!finalSrc.equals(solved.getFinalSrc())) {
				// Found under another name (a .dds beside it or in a graphics package), so it is read as that file's type.
				extension = finalSrc.substring(finalSrc.lastIndexOf('.'));
			}

			if (!(extension instanceof String)) {
				throw new IllegalStateException("The path solver did not return an extension!");
			}

			if (extension.charAt(0) != '.') {
				extension = '.' + extension;
			}
			// Built-in texture sources
			// ---- TODO not using JS code here

			final Object[] handlerAndDataType = this.findHandler(extension.toLowerCase(Locale.ROOT));

			// Is there a handler for this file type?
			if (handlerAndDataType != null) {
				final Resource cached = this.fetchCache.get(cacheKey(finalSrc));

				if (cached != null) {
					if (cached instanceof Model) {
						((Model<?>) cached).requested();
						if (!this.deferModelLoads) {
							((Model<?>) cached).finishPending();
						}
					}
					return cached;
				}

				final ResourceHandler handler = (ResourceHandler) handlerAndDataType[0];
				final Resource resource = handler.construct(new ResourceHandlerConstructionParams(this, handler,
						extension, pathSolver, finalSrc));

				this.resources.add(resource);

				this.fetchCache.put(cacheKey(finalSrc), resource);
				if (resource instanceof Model) {
					((Model<?>) resource).requested();
				}

				// TODO this is a synchronous hack, skipped some Ghostwolf code
				final long loadStart = PerfStats.ENABLED ? System.nanoTime() : 0;
				final boolean deferBefore = this.deferTextureLoads;
				if (this.deferModelTextures
						&& (extension.equalsIgnoreCase(".mdx") || extension.equalsIgnoreCase(".mdl"))) {
					this.deferTextureLoads = true;
				}
				try {
					if (this.deferModelLoads && (resource instanceof Model)
							&& (!this.prepareModelsAhead || !mayStartPreparation())) {
						final Model<?> model = (Model<?>) resource;
						model.lazySource = finalSrc;
						this.lazyModels++;
						model.setPending(true);
						this.pendingModels.add(model);
					}
					else {
						final InputStream stream = (this.deferTextureLoads && (resource instanceof Texture))
								|| (this.deferModelLoads && (resource instanceof Model)) ? openForHelper(finalSrc)
										: this.dataSource.getResourceAsStream(finalSrc);
						if ((stream != null) && this.deferModelLoads && (resource instanceof Model)
								&& ((Model<?>) resource).prepareLater(stream, ModelWorkers.EXECUTOR)) {
							((Model<?>) resource).setPending(true);
							this.pendingModels.add((Model<?>) resource);
						}
						else {
							resource.loadData(stream, null);
						}
					}
					if (PerfStats.ENABLED) {
						PerfStats.loaded(finalSrc, System.nanoTime() - loadStart);
					}
				}
				catch (final Exception e) {
					throw new IllegalStateException("Unable to load data: " + finalSrc, e);
				}
				finally {
					this.deferTextureLoads = deferBefore;
				}

				return resource;
			}
			else {
				throw new IllegalStateException("Missing handler for: " + finalSrc);
			}
		}
		else {
			throw new IllegalStateException(
					"Could not resolve " + finalSrc + ". Did you forget to pass a path solver?");
		}

	}

	/**
	 * Builds a model from a file whose bytes {@code edit} changes first. The model is kept under no name, so every call
	 * makes a model of its own; it is built at once and its textures come from the same cache as any other model's.
	 */
	public Resource loadEdited(final String src, final PathSolver pathSolver, final Object solverParams,
			final UnaryOperator<byte[]> edit) {
		final SolvedPath solved = pathSolver.solve(src, solverParams);
		final String finalSrc = findSource(solved.getFinalSrc());
		final String extension = finalSrc.substring(finalSrc.lastIndexOf('.'));
		final Object[] handlerAndDataType = findHandler(extension.toLowerCase(Locale.ROOT));
		if (handlerAndDataType == null) {
			throw new IllegalStateException("Missing handler for: " + finalSrc);
		}
		final boolean deferBefore = this.deferTextureLoads;
		try (InputStream stream = this.dataSource.getResourceAsStream(finalSrc)) {
			final byte[] data = edit.apply(ByteStreams.toByteArray(stream));
			final ResourceHandler handler = (ResourceHandler) handlerAndDataType[0];
			final Resource resource = handler.construct(
					new ResourceHandlerConstructionParams(this, handler, extension, pathSolver, ""));
			this.resources.add(resource);
			this.deferTextureLoads = false;
			resource.loadData(new ByteArrayInputStream(data), null);
			return resource;
		}
		catch (final IOException e) {
			throw new IllegalStateException("Unable to load data: " + finalSrc, e);
		}
		finally {
			this.deferTextureLoads = deferBefore;
		}
	}

	/**
	 * The file of a resource whose loading goes on on a helper thread: when the data source allows, the helper opens
	 * it too, which for a file of the game's storage that is not streamed means reading and decoding all of it.
	 */
	private InputStream openForHelper(final String path) throws IOException {
		final IndependentStream.Opener opener = this.dataSource.opener(path);
		return opener != null ? new IndependentStream.Opening(opener) : this.dataSource.getResourceAsStream(path);
	}

	/**
	 * The key a file is kept under: the data sources find a file whatever the case or the slashes of its name, and the
	 * same file is asked for under different spellings (a model's own path and the one a unit's data gives), which
	 * would otherwise be read and built twice.
	 */
	private static String cacheKey(final String path) {
		return path.replace('/', '\\').toLowerCase(Locale.ROOT);
	}

	public boolean has(final String key) {
		return this.fetchCache.containsKey(cacheKey(key));
	}

	public Resource get(final String key) {
		final Resource resource = this.fetchCache.get(cacheKey(key));
		if (resource instanceof Model) {
			((Model<?>) resource).requested();
		}
		return resource;
	}

	/**
	 * Lets go of a model that nothing holds any more (see {@link Model#isReleasable}): its file is read and built again
	 * when it is asked for next.
	 */
	public void evictModel(final Model<?> model) {
		unload(model);
		this.textureMappers.remove(model);
		model.dispose();
		this.evictedModels++;
	}

	public int getEvictedModels() {
		return this.evictedModels;
	}

	public GenericResource loadGeneric(final String path, final FetchDataTypeName dataType,
			final LoadGenericCallback callback) {
		return loadGeneric(path, dataType, callback, this.dataSource);
	}

	/**
	 * Load something generic.
	 *
	 * Unlike load(), this does not use handlers or construct any internal objects.
	 *
	 * `dataType` can be one of: `"image"`, `"string"`, `"arrayBuffer"`, `"blob"`.
	 *
	 * If `callback` isn't given, the resource's `data` is the fetch data, according
	 * to `dataType`.
	 *
	 * If `callback` is given, the resource's `data` is the value returned by it
	 * when called with the fetch data.
	 *
	 * If `callback` returns a promise, the resource's `data` will be whatever the
	 * promise resolved to.
	 */
	public GenericResource loadGeneric(final String path, final FetchDataTypeName dataType,
			final LoadGenericCallback callback, final DataSource dataSource) {
		final Resource cachedResource = this.fetchCache.get(cacheKey(path));

		if (cachedResource != null) {
			// Technically also non-generic resources can be returned here, since the fetch
			// cache is shared.
			// That being said, this should be used for generic resources, and it makes the
			// typing a lot easier.
			return (GenericResource) cachedResource;
		}

		final GenericResource resource = new GenericResource(this, null, null, path, callback);

		this.resources.add(resource);
		this.fetchCache.put(cacheKey(path), resource);

		// TODO this is a synchronous hack, skipped some Ghostwolf code
		try {
			// opened only when the callback reads it: one that has the file already (a sound decoded ahead) does not
			final IndependentStream.Opener opener = dataSource.opener(path);
			resource.loadData(
					opener != null ? new IndependentStream.Opening(opener) : dataSource.getResourceAsStream(path), null);
		}
		catch (final IOException e) {
			throw new IllegalStateException("Unable to load data: " + path);
		}

		return resource;

	}

	public void updateAndRender() {
		this.update();
		this.startFrame();
		this.render();
	}

//	public Resource loadGeneric(String path, String dataType, )

	/** Frames between looks at the texture budget. */
	private static final int BUDGET_CHECK_FRAMES = 60;
	/** A texture not drawn for this long may be let go when the textures are over their budget. */
	private static final long UNUSED_TEXTURE_NANOS = 30_000_000_000L;
	/** Once over the budget, textures are let go until this part of it is left, so that it is not over again at once. */
	private static final double BUDGET_LEFT_AFTER_EVICTING = 0.85;

	private void keepTexturesInBudget(final long now) {
		this.budgetCheckFrames.addLast(new long[] { this.frame, now });
		while (this.budgetCheckFrames.size() > 1) {
			final Iterator<long[]> checks = this.budgetCheckFrames.iterator();
			checks.next();
			if ((now - checks.next()[1]) < UNUSED_TEXTURE_NANOS) {
				break;
			}
			this.budgetCheckFrames.pollFirst();
		}
		final long[] oldest = this.budgetCheckFrames.peekFirst();
		if (((now - oldest[1]) < UNUSED_TEXTURE_NANOS) || (this.textureBytes <= this.textureBudget)) {
			return;
		}
		final long unusedBefore = oldest[0];
		final List<RawOpenGLTextureResource> unused = new ArrayList<>();
		for (final Resource resource : this.resources) {
			if (resource instanceof RawOpenGLTextureResource) {
				final RawOpenGLTextureResource texture = (RawOpenGLTextureResource) resource;
				if (texture.isEvictable() && (texture.getLastUsedFrame() < unusedBefore)) {
					unused.add(texture);
				}
			}
		}
		unused.sort((a, b) -> Integer.compare(a.getLastUsedFrame(), b.getLastUsedFrame()));
		final long target = (long) (this.textureBudget * BUDGET_LEFT_AFTER_EVICTING);
		for (int i = 0; (i < unused.size()) && (this.textureBytes > target); i++) {
			unused.get(i).evict();
			this.evictedTextures++;
		}
	}

	/** Reads and decodes again a texture that was let go, on helper threads where the data allows. */
	void reloadTexture(final RawOpenGLTextureResource texture) {
		reloadTexture(texture, true);
	}

	void reloadTexture(final RawOpenGLTextureResource texture, final boolean defer) {
		final boolean before = this.deferTextureLoads;
		this.deferTextureLoads = defer;
		try {
			final InputStream stream = openForHelper(texture.fetchUrl);
			if (stream == null) {
				texture.reloadFailed();
				return;
			}
			texture.load(stream, null);
		}
		catch (final IOException | RuntimeException e) {
			texture.reloadFailed();
			Log.error(e);
		}
		finally {
			this.deferTextureLoads = before;
		}
	}

	public boolean unload(final Resource resource) {
		// TODO Auto-generated method stub
		final String fetchUrl = resource.fetchUrl;
		if (!"".equals(fetchUrl)) {
			this.fetchCache.remove(cacheKey(fetchUrl));
		}
		return this.resources.remove(resource);
	}

	public void update() {
		update(Gdx.graphics.getRawDeltaTime());
	}

	public void update(final float dt) {
		this.frame += 1;
		if ((this.textureBudget > 0) && (--this.framesToBudgetCheck <= 0)) {
			this.framesToBudgetCheck = BUDGET_CHECK_FRAMES;
			keepTexturesInBudget(System.nanoTime());
		}

		this.visibleCells = 0;
		this.visibleInstances = 0;
		this.updatedParticles = 0;

		for (final Scene scene : this.scenes) {
			scene.update(dt);

			this.visibleCells += scene.visibleCells;
			this.visibleInstances += scene.visibleInstances;
			this.updatedParticles += scene.updatedParticles;
		}
	}

	public void startFrame() {
		Gdx.gl.glScissor(0, 0, (int) this.canvas.getWidth(), (int) this.canvas.getHeight());
		this.gl.glDepthMask(true);
		// The scenes set their own clear colours: the bars beside a letterboxed view must not show the last of them.
		this.gl.glClearColor(0, 0, 0, 1);
		this.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
//		WarsmashGdxGame.bindDefaultVertexArray();
	}

	public void render() {
		for (final Scene scene : this.scenes) {
			scene.startFrame();
			scene.renderOpaque();
			scene.renderTranslucent();
		}
	}

	public TextureMapper baseTextureMapper(final ModelInstance instance) {
		final Model model = instance.model;
		List<TextureMapper> mappers = this.textureMappers.get(model);
		if (mappers == null) {
			mappers = new ArrayList<>();
			this.textureMappers.put(model, mappers);
		}
		if (mappers.isEmpty()) {
			mappers.add(new TextureMapper(model));
		}
		return mappers.get(0);
	}

	public TextureMapper changeTextureMapper(final ModelInstance instance, final Object key, final Texture texture) {
		final Map<Object, Texture> map = new HashMap<>(instance.textureMapper.textures);

		if (texture instanceof Texture) { // not null?
			map.put(key, texture);
		}
		else {
			map.remove(key);
		}

		final Model model = instance.model;
		final List<TextureMapper> mappers = this.textureMappers.get(model);

		for (final TextureMapper mapper : mappers) {
			if (mapper.textures.equals(map)) {
				return mapper;
			}
		}

		final TextureMapper mapper = new TextureMapper(model, map);

		mappers.add(mapper);

		return mapper;
	}

	private void onResourceLoadError() {
		Log.error("error, this, InvalidHandler, FailedToLoad");
	}

	public abstract SceneLightManager createLightManager(boolean simple);

	/** The light of a scene that shows a talking portrait. */
	public SceneLightManager createPortraitLightManager() {
		return createLightManager(true);
	}
}
