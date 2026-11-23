package com.etheller.warsmash.viewer5.handlers.mdx;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.IndependentStream;
import com.etheller.warsmash.util.StreamBytes;

import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.Model;
import com.etheller.warsmash.viewer5.ModelInstance;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.Texture;
import com.hiveworkshop.rms.parsers.mdlx.MdlxAttachment;
import com.hiveworkshop.rms.parsers.mdlx.MdlxBone;
import com.hiveworkshop.rms.parsers.mdlx.MdlxCamera;
import com.hiveworkshop.rms.parsers.mdlx.MdlxCollisionShape;
import com.hiveworkshop.rms.parsers.mdlx.MdlxEventObject;
import com.hiveworkshop.rms.parsers.mdlx.MdlxExtent;
import com.hiveworkshop.rms.parsers.mdlx.MdlxGeoset;
import com.hiveworkshop.rms.parsers.mdlx.MdlxGeosetAnimation;
import com.hiveworkshop.rms.parsers.mdlx.MdlxHelper;
import com.hiveworkshop.rms.parsers.mdlx.MdlxLayer;
import com.hiveworkshop.rms.parsers.mdlx.MdlxLight;
import com.hiveworkshop.rms.parsers.mdlx.MdlxMaterial;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.MdlxParticleEmitter;
import com.hiveworkshop.rms.parsers.mdlx.MdlxParticleEmitter2;
import com.hiveworkshop.rms.parsers.mdlx.MdlxRibbonEmitter;
import com.hiveworkshop.rms.parsers.mdlx.MdlxSequence;
import com.hiveworkshop.rms.parsers.mdlx.MdlxTexture;
import com.hiveworkshop.rms.parsers.mdlx.MdlxTexture.WrapMode;
import com.hiveworkshop.rms.parsers.mdlx.MdlxTextureAnimation;

public class MdxModel extends com.etheller.warsmash.viewer5.Model<MdxHandler> {
	public boolean reforged = false;
	public boolean hd = false;
	public SolverParams solverParams = new SolverParams();
	public String name = "";
	public long blendTime;
	public List<Sequence> sequences = new ArrayList<>();
	/** Only while the model loads. */
	ArrayPool arrayPool;
	private byte[] noVariants = new byte[0];

	private boolean[] noFlags = new boolean[0];

	/** The flags of an object that varies in no sequence are only read: they share one array of the model. */
	synchronized boolean[] noFlags(final int length) {
		if (this.noFlags.length != length) {
			this.noFlags = new boolean[length];
		}
		return this.noFlags;
	}

	synchronized byte[] noVariants(final int length) {
		if (this.noVariants.length != length) {
			this.noVariants = new byte[length];
		}
		return this.noVariants;
	}

	private int[][] sequenceIntervals;

	/** The start and the end of every sequence, as two arrays shared by the timelines of this model. */
	synchronized int[][] sequenceIntervals() {
		if (this.sequenceIntervals == null) {
			final int[] starts = new int[this.sequences.size()];
			final int[] ends = new int[starts.length];
			for (int i = 0; i < starts.length; i++) {
				final long[] interval = this.sequences.get(i).getInterval();
				starts[i] = (int) interval[0];
				ends[i] = (int) interval[1];
			}
			this.sequenceIntervals = new int[][] { starts, ends };
		}
		return this.sequenceIntervals;
	}
	public List<Long> globalSequences = new ArrayList<>();
	public List<Material> materials = new ArrayList<>();
	public List<Layer> layers = new ArrayList<>();
	public List<Integer> replaceables = new ArrayList<>();
	public List<Texture> textures = new ArrayList<>();
	public List<TextureAnimation> textureAnimations = new ArrayList<>();
	public List<Geoset> geosets = new ArrayList<>();
	public List<GeosetAnimation> geosetAnimations = new ArrayList<>();
	public List<Bone> bones = new ArrayList<>();
	public List<Light> lights = new ArrayList<>();
	public List<Helper> helpers = new ArrayList<>();
	public List<Attachment> attachments = new ArrayList<>();
	public List<float[]> pivotPoints = new ArrayList<>();
	public List<ParticleEmitterObject> particleEmitters = new ArrayList<>();
	public List<ParticleEmitter2Object> particleEmitters2 = new ArrayList<>();
	public List<RibbonEmitterObject> ribbonEmitters = new ArrayList<>();
	public List<Camera> cameras = new ArrayList<>();
	public List<EventObjectEmitterObject> eventObjects = new ArrayList<>();
	public List<CollisionShape> collisionShapes = new ArrayList<>();
	public List<Batch> batches = new ArrayList<>();
	public List<GenericObject> genericObjects = new ArrayList<>();
	public List<GenericObject> sortedGenericObjects = new ArrayList<>();
	public List<Integer> hierarchy = new ArrayList<>();
	public List<GenericGroup> opaqueGroups = new ArrayList<>();
	public List<GenericGroup> translucentGroups = new ArrayList<>();
	public List<GenericGroup> simpleGroups = new ArrayList<>();
	private int[] nodeIndexByObjectId = new int[0];
	public int arrayBuffer;
	public int elementBuffer;
	private final ShadowMesh[] shadowMeshes = new ShadowMesh[2];
	private final boolean[] shadowMeshBuilt = new boolean[2];

	/**
	 * The coarse geometry the ground shadows are drawn from, made when first asked for; null for the model's own.
	 *
	 * @param exactCutouts whether the small cut-out geosets keep their alpha test, as the leaves of a tree must, or
	 *                     are drawn solid with the rest
	 */
	ShadowMesh shadowMesh(final boolean exactCutouts) {
		final int slot = exactCutouts ? 1 : 0;
		if (!this.shadowMeshBuilt[slot]) {
			this.shadowMeshBuilt[slot] = true;
			this.shadowMeshes[slot] = ShadowMesh.build(this, exactCutouts);
		}
		return this.shadowMeshes[slot];
	}

	@Override
	public void dispose() {
		for (final ShadowMesh mesh : this.shadowMeshes) {
			if (mesh != null) {
				mesh.dispose(this.viewer.gl);
			}
		}
		this.viewer.gl.glDeleteBuffer(this.arrayBuffer);
		this.viewer.gl.glDeleteBuffer(this.elementBuffer);
		for (final Geoset geoset : this.geosets) {
			geoset.disposePickData();
		}
	}

	public MdxModel(final MdxHandler handler, final ModelViewer viewer, final String extension,
			final PathSolver pathSolver, final String fetchUrl) {
		super(handler, viewer, extension, pathSolver, fetchUrl);
	}

	@Override
	public ModelInstance createInstance(final int type) {
		if (type == 1) {
			return new MdxSimpleInstance(this);
		}
		else {
			return new MdxComplexInstance(this);
		}
	}

	private static boolean reportedStreamConversion = false;

	/** What a pending model's helper made, see {@link ModelViewer#setDeferModelLoads}. */
	private static final class Prepared {
		final MdlxModel parser;
		final SetupGeosets.Prepared geometry;
		final TextureSources textureSources;

		Prepared(final MdlxModel parser, final SetupGeosets.Prepared geometry, final TextureSources textureSources) {
			this.parser = parser;
			this.geometry = geometry;
			this.textureSources = textureSources;
		}
	}

	/**
	 * The paths the textures of a file are loaded by and the files they find, looked up by the helper: each is a lookup
	 * in every data source, and some of them for several names (.dds beside .blp, the graphics packages).
	 */
	private static final class TextureSources {
		final DataSource dataSource;
		final String[] paths;
		/** What {@link ModelViewer#lookupSource} finds for each path, null for none or when not looked up. */
		final String[] found;

		private TextureSources(final DataSource dataSource, final String[] paths, final String[] found) {
			this.dataSource = dataSource;
			this.paths = paths;
			this.found = found;
		}

		static TextureSources lookUp(final MdlxModel parser, final DataSource dataSource, final boolean lookUpFiles) {
			nameCliffTextures(parser);
			final List<MdlxTexture> textures = parser.getTextures();
			final boolean[] shaderTextures = shaderTextures(parser);
			final boolean reforged = parser.getVersion() > 800;
			final String[] paths = new String[textures.size()];
			final String[] found = new String[textures.size()];
			for (int i = 0; i < paths.length; i++) {
				final MdlxTexture texture = textures.get(i);
				paths[i] = texturePath(texture, texture.reforged || shaderTextures[i] || reforged, dataSource);
				if (lookUpFiles) {
					found[i] = ModelViewer.lookupSource(dataSource, paths[i]);
				}
			}
			return new TextureSources(dataSource, paths, found);
		}
	}

	/** Which textures a layer of a material with a shader uses: those are the textures of the HD data. */
	private static boolean[] shaderTextures(final MdlxModel parser) {
		final int count = parser.getTextures().size();
		final boolean[] marked = new boolean[count];
		for (final MdlxMaterial material : parser.getMaterials()) {
			if ((material.shader != null) && (material.shader.length() > 1)) {
				for (final MdlxLayer layer : material.layers) {
					final int textureId = layer.textureId;
					if ((textureId >= 0) && (textureId < count)) {
						marked[textureId] = true;
					}
				}
			}
		}
		return marked;
	}

	/** The replaceable texture the cliff doodads take from the cliffs they stand in. */
	private static final int CLIFF_REPLACEABLE_ID = 11;
	/** The slots of an HD material that hold the maps of the surface: colour, normal and ORM. */
	private static final int HD_MAP_SLOTS = 3;

	/**
	 * The cliff texture of a material of the HD data, drawn when the instance assigns none, named for the slot of the
	 * material it is bound to (colour, normal or ORM map) and so of the kind of map that suits it.
	 */
	private static String cliffTexturePath(final int number, final int slot) {
		return "ReplaceableTextures\\Cliff\\Cliff" + number + "Legacy_" + (slot == 0 ? "diffuse" : slot == 1 ? "normal" : "orm") + ".dds";
	}

	/** The cliff number a placeholder texture of the HD cliff doodads names, or -1 for any other texture. */
	private static int cliffPlaceholderNumber(final String path) {
		if (path == null) {
			return -1;
		}
		final String name = path.replace('\\', '/').toLowerCase(Locale.ROOT);
		final int slash = name.lastIndexOf('/');
		final String folder = name.substring(0, slash + 1);
		final String file = name.substring(slash + 1);
		if (!(folder.endsWith("doodads/terrain/#ignore_cliffs/") || folder.endsWith("replaceabletextures/cliff/"))
				|| !file.startsWith("cliff")) {
			return -1;
		}
		int end = "cliff".length();
		while ((end < file.length()) && Character.isDigit(file.charAt(end))) {
			end++;
		}
		return end == "cliff".length() ? -1 : Integer.parseInt(file.substring("cliff".length(), end));
	}

	/**
	 * Makes the cliff textures of the HD materials replaceable textures bound by their slot. The HD cliff doodads name
	 * the cliff texture either as a replaceable texture in each of the colour, normal and ORM slots, with the same
	 * default file for all of them, or, in one model, by placeholder files that exist in no data and are named for a
	 * slot other than the one they are in. The slot decides what the texture is, as it does for any other HD texture.
	 */
	private static void nameCliffTextures(final MdlxModel parser) {
		final List<MdlxTexture> textures = parser.getTextures();
		final int[] slots = new int[textures.size()];
		Arrays.fill(slots, -1);
		for (final MdlxMaterial material : parser.getMaterials()) {
			if ((material.shader == null) || (material.shader.length() <= 1)) {
				continue;
			}
			for (int i = 0; i < material.layers.size(); i++) {
				final int textureId = material.layers.get(i).textureId;
				if ((textureId >= 0) && (textureId < slots.length)) {
					// a texture bound to two slots keeps its own file: it cannot be both kinds of map
					slots[textureId] = (slots[textureId] == -1) && (i < HD_MAP_SLOTS) ? i : -2;
				}
			}
		}
		for (int i = 0; i < slots.length; i++) {
			if (slots[i] < 0) {
				continue;
			}
			final MdlxTexture texture = textures.get(i);
			int number = cliffPlaceholderNumber(texture.getPath());
			if ((number == -1) && (texture.getReplaceableId() == CLIFF_REPLACEABLE_ID) && texture.getPath().isEmpty()) {
				number = 0;
			}
			if (number != -1) {
				texture.setReplaceableId(CLIFF_REPLACEABLE_ID);
				texture.setPath(cliffTexturePath(number, slots[i]));
			}
		}
	}

	/** The path a texture of the file is loaded by. */
	private static String texturePath(final MdlxTexture texture, final boolean reforged, final DataSource dataSource) {
		String path = texture.getPath();
		final int replaceableId = texture.getReplaceableId();

		if ((replaceableId == CLIFF_REPLACEABLE_ID) && reforged && !path.isEmpty()) {
			// the cliff texture of an HD material, named by nameCliffTextures
		}
		else if (replaceableId != 0) {
			// TODO This uses dumb, stupid, terrible, no-good hardcoded replaceable IDs
			// instead of the real system, because currently MdxSimpleInstance is not
			// supporting it correctly.
			path = ReplaceableIds.getDefaultTexturePath(replaceableId);
			if (path == null) {
				// Only instances that assign this replaceable at runtime draw it with a real
				// texture.
				path = "Textures\\white.blp";
			}
		}

		if ("".equals(path)) {
			path = "Textures\\white.blp";
		}
		else if (reforged && !path.endsWith(".dds")) {
			final String ddsPath = path.substring(0, path.length() - 4) + ".dds";
			// The .dds is taken only when no later data source has the path as written: an override folder
			// may hold the older format.
			final String found = path.endsWith(".tif") ? ddsPath
					: dataSource.findFirst(ddsPath.replace("\\", "/"), ddsPath.replace("/", "\\"), path);
			if ((found != null) && !found.equals(path)) {
				path = ddsPath;
			}
		}
		return path;
	}

	private FutureTask<Prepared> preparation;
	private volatile boolean discarded;
	/** The build of a pending model that is begun and goes on in later frames. */
	private Build build;

	@Override
	protected boolean prepareLater(final InputStream src, final ExecutorService executor) {
		final DataSource dataSource = this.viewer.dataSource;
		// the files a solved path loads are looked up only for the solver that leaves paths as they are
		final boolean lookUpFiles = this.pathSolver == PathSolver.DEFAULT;
		byte[] read = null;
		if (!(src instanceof IndependentStream)) {
			// an archive's streams share its reader with the game thread
			try {
				read = StreamBytes.readAll(src);
			}
			catch (final IOException e) {
				final IOException failure = e;
				this.preparation = new FutureTask<>(() -> {
					throw failure;
				});
				executor.execute(this.preparation);
				return true;
			}
		}
		final byte[] bytes = read;
		this.preparation = new FutureTask<>(() -> {
			final MdlxModel parser;
			if (bytes != null) {
				setPreparedFileBytes(bytes.length);
				parser = new MdlxModel(ByteBuffer.wrap(bytes));
			}
			else {
				try (InputStream stream = src) {
					parser = StreamBytes.parse(stream, buffer -> {
						setPreparedFileBytes(buffer.remaining());
						return new MdlxModel(buffer);
					});
				}
			}
			final SetupGeosets.Prepared geometry = SetupGeosets.prepare(parser.getGeosets(), parser.getMaterials(),
					parser.getBones().size() >= 256);
			if (this.discarded) {
				geometry.dispose();
			}
			return new Prepared(parser, geometry, TextureSources.lookUp(parser, dataSource, lookUpFiles));
		});
		executor.execute(this.preparation);
		return true;
	}

	@Override
	protected boolean isPrepared() {
		return (this.preparation == null) || this.build != null || this.preparation.isDone();
	}

	@Override
	protected void discardPrepared() {
		this.discarded = true;
		final FutureTask<Prepared> preparation = this.preparation;
		this.preparation = null;
		if (preparation != null) {
			preparation.cancel(true);
			if (preparation.isDone() && !preparation.isCancelled()) {
				try {
					preparation.get().geometry.dispose();
				}
				catch (final Exception e) {
					// nothing was made that holds memory
				}
			}
		}
		final Build build = this.build;
		this.build = null;
		if (build != null) {
			build.discard();
		}
	}

	@Override
	protected Model<?> firstPendingRequired() {
		for (final Attachment attachment : this.attachments) {
			final MdxModel internal = attachment.internalModel;
			if ((internal != null) && internal.isPending() && !internal.isStepping()) {
				return internal;
			}
		}
		for (final EventObjectEmitterObject eventObject : this.eventObjects) {
			final MdxModel internal = eventObject.internalModel;
			if ((internal != null) && internal.isPending() && !internal.isStepping()) {
				return internal;
			}
		}
		return null;
	}

	@Override
	protected boolean buildPrepared(final long endNanos) {
		if (this.build == null) {
			final FutureTask<Prepared> preparation = this.preparation;
			this.preparation = null;
			// a model asked for before its helper came to it is prepared here instead of waiting for the ones before
			// it; a helper already at it is waited for
			preparation.run();
			Prepared prepared = null;
			boolean interrupted = false;
			try {
				while (prepared == null) {
					try {
						prepared = preparation.get();
					}
					catch (final InterruptedException e) {
						interrupted = true;
					}
				}
			}
			catch (final ExecutionException e) {
				this.loaded = true;
				error(new IllegalStateException("Unable to load data: " + this.fetchUrl, e.getCause()));
				return true;
			}
			finally {
				if (interrupted) {
					Thread.currentThread().interrupt();
				}
			}
			this.loaded = true;
			this.arrayPool = new ArrayPool();
			this.build = new Build(prepared.parser, prepared.geometry, prepared.textureSources, true);
		}
		final Build build = this.build;
		try {
			if (!build.run(endNanos)) {
				return false;
			}
			this.build = null;
			this.arrayPool = null;
			this.ok = true;
			lateLoad();
		}
		catch (final Exception e) {
			build.discard();
			this.build = null;
			this.arrayPool = null;
			error(e);
		}
		return true;
	}

	public void load(final Object bufferOrParser) throws IOException {
		this.arrayPool = new ArrayPool();
		Build build = null;
		boolean built = false;
		try {
			final MdlxModel parser;
			if (bufferOrParser instanceof MdlxModel) {
				parser = (MdlxModel) bufferOrParser;
			}
			else {
				if (!reportedStreamConversion) {
					reportedStreamConversion = true;
					Log.warn("Wasting memory with conversion from InputStream to buffer in MdxModel (reported once)");
				}
				parser = StreamBytes.parse((InputStream) bufferOrParser, MdlxModel::new);
			}
			build = new Build(parser, null, null, false);
			build.run(Long.MAX_VALUE);
			built = true;
		}
		finally {
			this.arrayPool = null;
			if (!built && (build != null)) {
				build.discard();
			}
		}
	}

	/**
	 * Builds the model from its parsed file in units of work (a texture, a geoset, a node), so that a model that is
	 * not needed at once can be built a few units a frame. Each unit does what the loading of the whole model did at
	 * that place before, in the same order.
	 */
	private final class Build {
		private static final int RESOLVE = 0;
		private static final int SOUNDS = 1;
		private static final int MATERIALS = 2;
		private static final int TEXTURES = 3;
		private static final int GEOSET_ANIMATIONS = 4;
		private static final int GEOSETS = 5;
		private static final int OBJECTS = 6;
		private static final int FINISH = 7;

		private MdlxModel parser;
		private SetupGeosets.Prepared geometry;
		private TextureSources textureSources;
		/** Whether the build goes on over frames: the sounds of its event objects are then decoded ahead. */
		private final boolean stepped;
		private Set<String> soundsStarted;
		private final MdlxModel original;
		private int phase = RESOLVE;
		private int index;
		private long end;
		private boolean progressed;
		private boolean reforged;
		private PathSolver pathSolver;
		private SolverParams solverParams;
		private int layerId;
		private boolean sequencesMade;
		private SetupGeosets.Upload upload;
		private List<Runnable> objectUnits;
		private int finishStep;

		Build(final MdlxModel parser, final SetupGeosets.Prepared geometry, final TextureSources textureSources,
				final boolean stepped) {
			nameCliffTextures(parser);
			this.parser = parser;
			this.original = parser;
			this.geometry = geometry;
			this.textureSources = textureSources;
			this.stepped = stepped;
		}

		/** Whether the time is used up: the unit that is about to start waits for the next call (one always runs). */
		private boolean outOfTime() {
			return this.progressed && (System.nanoTime() >= this.end);
		}

		/** @return true when the model is built, false when the next call goes on */
		boolean run(final long endNanos) throws IOException {
			this.end = endNanos;
			this.progressed = false;
			while (true) {
				switch (this.phase) {
				case RESOLVE:
					resolve();
					this.phase = this.stepped ? SOUNDS : MATERIALS;
					this.index = 0;
					break;
				case SOUNDS:
					if (!sounds()) {
						return false;
					}
					this.phase = MATERIALS;
					this.index = 0;
					break;
				case MATERIALS:
					if (!materials()) {
						return false;
					}
					this.phase = TEXTURES;
					this.index = 0;
					break;
				case TEXTURES:
					if (!textures()) {
						return false;
					}
					this.phase = GEOSET_ANIMATIONS;
					this.index = 0;
					break;
				case GEOSET_ANIMATIONS:
					if (!geosetAnimations()) {
						return false;
					}
					this.phase = GEOSETS;
					break;
				case GEOSETS:
					if (!geosets()) {
						return false;
					}
					this.phase = OBJECTS;
					this.index = 0;
					break;
				case OBJECTS:
					if (!objects()) {
						return false;
					}
					this.phase = FINISH;
					break;
				default:
					return finish();
				}
			}
		}

		/** Frees what was made ahead and is not taken over by the model. */
		void discard() {
			if (this.geometry != null) {
				this.geometry.dispose();
				this.geometry = null;
			}
			if (this.upload != null) {
				this.upload.discard();
				this.upload = null;
			}
		}

		private void resolve() {
			final MdxModel model = MdxModel.this;
			this.parser = ClassicEffects.resolve(model.viewer, model.fetchUrl, this.parser);
			if (this.parser != this.original) {
				// a Classic model took the place of one with no geometry: what was made for the first is not used
				discard();
				this.textureSources = null;
			}
			else if ((this.textureSources != null)
					&& (this.textureSources.paths.length != this.parser.getTextures().size())) {
				// a fallback emitter was added in place, with a texture of its own
				this.textureSources = null;
			}
			this.pathSolver = model.pathSolver;
			this.solverParams = model.solverParams;
			this.progressed = true;
		}

		/** Starts decoding the sounds of the event objects, which are made some frames later. */
		private boolean sounds() {
			final List<MdlxEventObject> eventObjects = this.parser.getEventObjects();
			for (; this.index < eventObjects.size(); this.index++) {
				if (outOfTime()) {
					return false;
				}
				if (this.soundsStarted == null) {
					this.soundsStarted = new HashSet<>();
				}
				EventObjectEmitterObject.prefetchSounds(MdxModel.this, eventObjects.get(this.index), this.soundsStarted);
				this.progressed = true;
			}
			this.soundsStarted = null;
			return true;
		}

		private boolean materials() {
			final MdxModel model = MdxModel.this;
			final MdlxModel parser = this.parser;
			if (!this.sequencesMade) {
				this.sequencesMade = true;
				this.reforged = parser.getVersion() > 800;

				model.reforged = this.reforged;
				model.name = parser.getName();
				model.blendTime = parser.getBlendTime();

				// Initialize the bounds.
				final MdlxExtent extent = parser.getExtent();
				final float[] min = extent.getMin();
				final float[] max = extent.getMax();
				for (int i = 0; i < 3; i++) {
					if (min[i] > max[i]) {
						min[i] = max[i] = 0;
					}
				}
				model.bounds.fromExtents(min, max, extent.getBoundsRadius());

				// Sequences
				for (final MdlxSequence sequence : parser.getSequences()) {
					model.sequences.add(new Sequence(sequence));
				}

				// Global sequences
				model.globalSequences.addAll(parser.getGlobalSequences());

				// Texture animations
				for (final MdlxTextureAnimation textureAnimation : parser.getTextureAnimations()) {
					model.textureAnimations.add(new TextureAnimation(model, textureAnimation));
				}
				this.progressed = true;
			}

			// Materials
			final List<MdlxMaterial> materials = parser.getMaterials();
			for (; this.index < materials.size(); this.index++) {
				if (outOfTime()) {
					return false;
				}
				final MdlxMaterial material = materials.get(this.index);
				final List<Layer> layers = new ArrayList<>();

				for (final MdlxLayer layer : material.getLayers()) {
					final Layer vLayer = new Layer(model, layer, this.layerId++, material.getPriorityPlane());
					// The original draws team colour at full strength day and night, whatever the layer's flags.
					if ("".equals(material.shader) && (layer.getTextureId() >= 0)
							&& (layer.getTextureId() < parser.getTextures().size())
							&& (parser.getTextures().get(layer.getTextureId()).getReplaceableId() == 1)) {
						vLayer.unshaded = 1;
					}

					layers.add(vLayer);

					model.layers.add(vLayer);
				}

				model.materials.add(new Material(model, material.shader, layers));

				if (!"".equals(material.shader)) {
					model.hd = true;
				}
				this.progressed = true;
			}

			if (this.reforged) {
				this.solverParams.reforged = true;
			}

			if (model.hd) {
				this.solverParams.hd = true;
			}

			for (final MdlxMaterial material : materials) {
				if ((material.shader != null) && (material.shader.length() > 1)) {
					for (final MdlxLayer layer : material.layers) {
						final int textureId = layer.textureId;
						if ((textureId >= 0) && (textureId < parser.getTextures().size())) {
							parser.getTextures().get(textureId).reforged = true;
						}
					}
				}
			}
			return true;
		}

		private boolean textures() {
			final MdxModel model = MdxModel.this;
			final MdlxModel parser = this.parser;
			final ModelViewer viewer = model.viewer;
			final List<MdlxTexture> textures = parser.getTextures();
			for (; this.index < textures.size(); this.index++) {
				if (outOfTime()) {
					return false;
				}
				final MdlxTexture texture = textures.get(this.index);
				final int replaceableId = texture.getReplaceableId();
				final WrapMode wrapMode = texture.getWrapMode();
				final TextureSources sources = this.textureSources;
				final String path;
				if (sources != null) {
					path = sources.paths[this.index];
					viewer.rememberSource(sources.dataSource, path, sources.found[this.index]);
				}
				else {
					path = texturePath(texture, texture.reforged || this.reforged, viewer.dataSource);
				}

				final Texture viewerTexture = (Texture) viewer.load(path, this.pathSolver, this.solverParams);

				// When the texture will load, it will apply its wrap modes.
				if (wrapMode.isWrapWidth()) {
					viewerTexture.setWrapS(true);
				}

				if (wrapMode.isWrapHeight()) {
					viewerTexture.setWrapT(true);
				}

				model.replaceables.add(replaceableId);
				model.textures.add(viewerTexture);
				this.progressed = true;
			}
			return true;
		}

		private boolean geosetAnimations() {
			final MdxModel model = MdxModel.this;
			final List<MdlxGeosetAnimation> animations = this.parser.getGeosetAnimations();
			for (; this.index < animations.size(); this.index++) {
				if (outOfTime()) {
					return false;
				}
				model.geosetAnimations.add(new GeosetAnimation(model, animations.get(this.index)));
				this.progressed = true;
			}
			return true;
		}

		private boolean geosets() {
			final MdxModel model = MdxModel.this;
			if (this.upload == null) {
				if (outOfTime()) {
					return false;
				}
				final List<MdlxGeoset> geosets = this.parser.getGeosets();
				if (this.geometry == null) {
					this.geometry = SetupGeosets.prepare(geosets, this.parser.getMaterials(),
							this.parser.getBones().size() >= 256);
				}
				this.upload = new SetupGeosets.Upload(model, geosets, this.geometry);
				this.geometry = null;
				this.progressed = true;
			}
			while (!this.upload.isDone()) {
				if (outOfTime()) {
					return false;
				}
				this.upload.uploadNext();
				this.progressed = true;
			}
			return true;
		}

		private boolean objects() {
			final MdxModel model = MdxModel.this;
			final MdlxModel parser = this.parser;
			if (this.objectUnits == null) {
				this.objectUnits = new ArrayList<>();
				final List<Runnable> units = this.objectUnits;
				model.pivotPoints = parser.getPivotPoints();

				// Tracks the IDs of all generic objects
				int objectId = 0;

				// Bones
				for (final MdlxBone bone : parser.getBones()) {
					final int id = objectId++;
					units.add(() -> model.bones.add(new Bone(model, bone, id)));
				}

				// Lights
				for (final MdlxLight light : parser.getLights()) {
					final int id = objectId++;
					units.add(() -> model.lights.add(new Light(model, light, id)));
				}

				// Helpers
				for (final MdlxHelper helper : parser.getHelpers()) {
					final int id = objectId++;
					units.add(() -> model.helpers.add(new Helper(model, helper, id)));
				}

				// Attachments
				for (final MdlxAttachment attachment : parser.getAttachments()) {
					final int id = objectId++;
					units.add(() -> model.attachments.add(new Attachment(model, attachment, id)));
				}

				// Particle Emitters
				for (final MdlxParticleEmitter particleEmitter : parser.getParticleEmitters()) {
					final int id = objectId++;
					units.add(() -> model.particleEmitters.add(new ParticleEmitterObject(model, particleEmitter, id)));
				}

				// Particle Emitters 2
				for (final MdlxParticleEmitter2 particleEmitter2 : parser.getParticleEmitters2()) {
					final int id = objectId++;
					units.add(() -> model.particleEmitters2.add(new ParticleEmitter2Object(model, particleEmitter2, id)));
				}

				// Ribbon emitters
				for (final MdlxRibbonEmitter ribbonEmitter : parser.getRibbonEmitters()) {
					final int id = objectId++;
					units.add(() -> model.ribbonEmitters.add(new RibbonEmitterObject(model, ribbonEmitter, id)));
				}

				// Camera
				for (final MdlxCamera camera : parser.getCameras()) {
					units.add(() -> model.cameras.add(new Camera(model, camera)));
				}

				// Event objects
				for (final MdlxEventObject eventObject : parser.getEventObjects()) {
					final int id = objectId++;
					units.add(() -> model.eventObjects.add(new EventObjectEmitterObject(model, eventObject, id)));
				}

				// Collision shapes
				for (final MdlxCollisionShape collisionShape : parser.getCollisionShapes()) {
					final int id = objectId++;
					units.add(() -> model.collisionShapes.add(new CollisionShape(model, collisionShape, id)));
				}
			}
			final List<Runnable> units = this.objectUnits;
			for (; this.index < units.size(); this.index++) {
				if (outOfTime()) {
					return false;
				}
				units.get(this.index).run();
				this.progressed = true;
			}
			this.objectUnits = null;
			return true;
		}

		private boolean finish() {
			final MdxModel model = MdxModel.this;
			// one array for all generic objects, the groups, and the order of the nodes: a unit each
			while (true) {
				if (outOfTime()) {
					return false;
				}
				this.progressed = true;
				switch (this.finishStep++) {
				case 0:
					// One array for all generic objects.
					model.genericObjects.addAll(model.bones);
					model.genericObjects.addAll(model.lights);
					model.genericObjects.addAll(model.helpers);
					model.genericObjects.addAll(model.attachments);
					model.genericObjects.addAll(model.particleEmitters);
					model.genericObjects.addAll(model.particleEmitters2);
					model.genericObjects.addAll(model.ribbonEmitters);
					model.genericObjects.addAll(model.eventObjects);
					model.genericObjects.addAll(model.collisionShapes);

					model.nodeIndexByObjectId = new int[Math.max(model.genericObjects.size(),
							maxObjectId(model.genericObjects) + 1)];
					Arrays.fill(model.nodeIndexByObjectId, -1);
					for (int i = 0, l = model.genericObjects.size(); i < l; i++) {
						model.nodeIndexByObjectId[model.genericObjects.get(i).objectId] = i;
					}
					break;
				case 1:
					// Render groups.
					SetupGroups.setupGroups(model);
					break;
				case 2:
					// SimpleInstance render group.
					SetupSimpleGroups.setupSimpleGroups(model);
					break;
				default:
					// Creates the sorted indices array of the generic objects
					try {
						model.setupHierarchy(-1);
						// A node whose parent is not a node here (a popcorn emitter) is a root.
						for (int i = 0, l = model.genericObjects.size(); (i < l) && (model.hierarchy.size() < l); i++) {
							if (!model.hierarchy.contains(i)
									&& (model.nodeIndexOf(model.genericObjects.get(i).parentId) == -1)) {
								model.hierarchy.add(i);
								model.setupHierarchy(model.genericObjects.get(i).objectId);
							}
						}
					}
					catch (final StackOverflowError e) {
						Log.info("bah");
					}

					// Keep a sorted array.
					for (int i = 0, l = model.genericObjects.size(); i < l; i++) {
						model.sortedGenericObjects.add(model.genericObjects.get(model.hierarchy.get(i)));
					}
					return true;
				}
			}
		}
	}

	private static int maxObjectId(final List<GenericObject> objects) {
		int max = -1;
		for (final GenericObject object : objects) {
			max = Math.max(max, object.objectId);
		}
		return max;
	}

	/**
	 * The place of the node in the instance's node list for the id the file gives it. The file counts the popcorn
	 * emitters, which are not nodes here, so the ids run ahead of the places after them.
	 */
	public int nodeIndexOf(final int objectId) {
		return ((objectId >= 0) && (objectId < this.nodeIndexByObjectId.length)) ? this.nodeIndexByObjectId[objectId]
				: -1;
	}

	private void setupHierarchy(final int parent) {
		for (int i = 0, l = this.genericObjects.size(); i < l; i++) {
			final GenericObject object = this.genericObjects.get(i);

			if (object.parentId == parent) {
				this.hierarchy.add(i);

				setupHierarchy(object.objectId);
			}
		}
	}

	@Override
	protected void lateLoad() {
	}

	@Override
	protected void load(final InputStream src, final Object options) {
		try {
			this.load(src);
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void error(final Exception e) {
		Log.error(e);
	}

	// TODO typing
	public List<Long> getGlobalSequences() {
		return this.globalSequences;
	}

	public List<Sequence> getSequences() {
		return this.sequences;
	}

	public List<float[]> getPivotPoints() {
		return this.pivotPoints;
	}

	public List<GeosetAnimation> getGeosetAnimations() {
		return this.geosetAnimations;
	}

	public List<Texture> getTextures() {
		return this.textures;
	}

	public List<Material> getMaterials() {
		return this.materials;
	}

	public List<TextureAnimation> getTextureAnimations() {
		return this.textureAnimations;
	}

	/** The geoset at this place in the model file, or null for one of the levels of detail left out. */
	public Geoset getGeosetOfFile(final int fileIndex) {
		for (final Geoset geoset : this.geosets) {
			if (geoset.fileIndex == fileIndex) {
				return geoset;
			}
		}
		return null;
	}

	public List<Geoset> getGeosets() {
		return this.geosets;
	}

	private static final class SolverParams {
		public boolean reforged;
		public boolean hd;

	}

	public List<Camera> getCameras() {
		return this.cameras;
	}

	public List<EventObjectEmitterObject> getEventObjects() {
		return this.eventObjects;
	}

	public List<Bone> getBones() {
		return this.bones;
	}

}
