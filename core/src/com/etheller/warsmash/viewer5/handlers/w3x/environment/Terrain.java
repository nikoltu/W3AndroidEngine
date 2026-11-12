package com.etheller.warsmash.viewer5.handlers.w3x.environment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import com.badlogic.gdx.utils.BufferUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.w3x.w3e.Corner;
import com.etheller.warsmash.parsers.w3x.w3e.War3MapW3e;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.parsers.w3x.wpm.War3MapWpm;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.DataErrors;
import com.etheller.warsmash.util.ImageUtils;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.ImageUtils.RgbaImage;
import com.etheller.warsmash.util.RenderMathUtils;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WorldEditStrings;
import com.etheller.warsmash.viewer5.Camera;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.RawOpenGLTextureResource;
import com.etheller.warsmash.viewer5.Texture;
import com.etheller.warsmash.viewer5.gl.DataTexture;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.gl.WebGL;
import com.etheller.warsmash.viewer5.handlers.w3x.DynamicShadowManager;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.GameOptions;
import com.etheller.warsmash.viewer5.handlers.w3x.SplatModel;
import com.etheller.warsmash.viewer5.handlers.w3x.SplatModel.SplatMover;
import com.etheller.warsmash.viewer5.handlers.w3x.Variations;
import com.etheller.warsmash.viewer5.handlers.w3x.W3xSceneLightManager;
import com.etheller.warsmash.viewer5.handlers.w3x.W3xShaders;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CCliffLayout;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CFogMaskSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;

public class Terrain {
	public static final float CELL_SIZE = 128f;
	private static final String[] colorTags = { "R", "G", "B", "A" };
	private static final float[] sizeHeap = new float[2];
	private static final float[] fourComponentHeap = new float[4];
	private static final Matrix4 tempMatrix = new Matrix4();
	public static boolean WIREFRAME_TERRAIN = false;
	public ShaderProgram groundShader;
	public ShaderProgram waterShader;
	public ShaderProgram cliffShader;
	public float waterIndex;
	public float waterIncreasePerFrame;
	public float waterHeightOffset;

	//
	public List<GroundTexture> groundTextures = new ArrayList<>();
	public List<UnloadedTexture> cliffTextures = new ArrayList<>();
	public RenderCorner[][] corners;
	public int columns;
	public int rows;
	public int blightTextureIndex = -1;
	// Blight as the viewer last saw it, per corner: a change shows only once the viewer sees the place (measured on
	// 1.29: blight spread under the black mask or in the fog is not drawn until seen).
	private boolean[] shownBlight;
	private boolean[] blightPending;
	private int[] pendingBlight = new int[64];
	private int pendingBlightCount;
	public float[] maxDeepColor = new float[4];
	public float[] minDeepColor = new float[4];
	public float[] maxShallowColor = new float[4];
	public float[] minShallowColor = new float[4];
	public float[] maxDeepColorApplied = new float[4];
	public float[] minDeepColorApplied = new float[4];
	public float[] maxShallowColorApplied = new float[4];
	public float[] minShallowColorApplied = new float[4];

	private final DataTable terrainTable;
	private final DataTable cliffTable;
	private final DataTable waterTable;
	private final int waterTextureCount;
	private int cliffTexturesSize;
	private final List<CliffMesh> cliffMeshes = new ArrayList<>();
	private final Map<String, Integer> pathToCliff = new HashMap<>();
	private final Map<String, Integer> groundTextureToId = new HashMap<>();
	/** The tile type of each ground texture, in the order the corners index them. */
	private final List<Integer> groundTileIds = new ArrayList<>();
	/** The ground shader samples one texture array per texture unit it has; the blight tile is one of them. */
	private static final int MAX_GROUND_TEXTURES = 16;
	private final List<Integer> cliffToGroundTexture = new ArrayList<>();
	private final List<IVec3> cliffs = new ArrayList<>();
	private final DataSource dataSource;
	private final float[] groundHeights;
	private final float[] groundCornerHeights;
	private final short[] groundTextureList;
	private ByteBuffer groundTextureUploadBuffer;
	private final float[] waterHeights;
	private final byte[] waterExistsData;
	/** Script terrain deformations per corner, in terrain height units (128 world units). */
	private float[] deformation;
	private FloatBuffer heightUploadBuffer;

	private int groundTextureData = -1;
	private final int groundHeight;
	private final int groundCornerHeight;
	private final int groundCornerHeightLinear;
	private final int cliffTextureArray;
	private final int waterHeight;
	private final int waterExists;
	private final int waterTextureArray;
	private final Camera camera;
	private final War3MapViewer viewer;
	public float[] centerOffset;
	private final WebGL webGL;
	/** The GL of the context the terrain was made in, which leaves deletes alone once that context is lost. */
	private final GL30 gl;
	private final ShaderProgram uberSplatShader;
	public final DataTable uberSplatTable;

	private final Map<String, SplatModel> uberSplatModels;
	private final List<SplatModel> uberSplatModelsList;
	private final Set<SplatModel> unitShadowSplatModels = Collections.newSetFromMap(new IdentityHashMap<>());
	private int shadowMap;
	private int fogOfWarMap;
	private boolean fogOfWarMapAllocated;
	public final Map<String, Splat> splats = new HashMap<>();
	public final Map<String, List<float[]>> shadows = new HashMap<>();
	public final Map<String, Texture> shadowTextures = new HashMap<>();
	private final int[] mapBounds;
	private final float[] shaderMapBounds;
	private final int[] mapSize;
	public FloatBuffer renderGroundBuffer;
	public final SoftwareGroundMesh softwareGroundMesh;
	public final SoftwareWaterAndGroundMesh softwareWaterAndGroundMesh;
	private int testArrayBuffer;
	private final int quadVertexBuffer;
	private final int quadIndexBuffer;
	private final int testElementBuffer;
	private boolean initShadowsFinished = false;
	// The shadow map is filled again once a frame at most: the shadows of many buildings can go away together, as
	// when a whole base goes out of sight.
	private boolean shadowsChanged;
	private byte[] staticShadowData;
	private byte[] shadowData;
	private ByteBuffer shadowDataBuffer;
	private final Map<RawOpenGLTextureResource, boolean[]> shadowMasks = new IdentityHashMap<>();
	private CPlayerFogOfWar fogOfWarData;
	private ByteBuffer visualFogData;
	private byte[] visualFogArray;
	private byte[] fogStateArray;
	private final byte[] fogStateMapping = new byte[256];

	public Terrain(final War3MapW3e w3eFile, final War3MapWpm terrainPathing, final War3MapW3i w3iFile,
			final WebGL webGL, final DataSource dataSource, final WorldEditStrings worldEditStrings,
			final War3MapViewer viewer, final DataTable worldEditData) throws IOException {
		this.webGL = webGL;
		this.gl = Gdx.gl30;
		this.viewer = viewer;
		this.camera = viewer.worldScene.camera;
		this.dataSource = dataSource;
		final String texturesExt = ".blp";
		final Corner[][] corners = w3eFile.getCorners();
		this.corners = new RenderCorner[corners[0].length][corners.length];
		for (int i = 0; i < corners.length; i++) {
			for (int j = 0; j < corners[i].length; j++) {
				this.corners[j][i] = new RenderCorner(corners[i][j]);
			}
		}
		final int width = w3eFile.getMapSize()[0];
		final int height = w3eFile.getMapSize()[1];
		this.columns = width;
		this.rows = height;
		CCliffLayout.markCliffs(this.corners, width, height);

		this.terrainTable = new DataTable(worldEditStrings);
		try (InputStream terrainSlkStream = dataSource.getResourceAsStream("TerrainArt\\Terrain.slk")) {
			this.terrainTable.readSLK(terrainSlkStream);
		}
		this.cliffTable = new DataTable(worldEditStrings);
		try (InputStream cliffSlkStream = dataSource.getResourceAsStream("TerrainArt\\CliffTypes.slk")) {
			this.cliffTable.readSLK(cliffSlkStream);
		}
		this.waterTable = new DataTable(worldEditStrings);
		try (InputStream waterSlkStream = dataSource.getResourceAsStream("TerrainArt\\Water.slk")) {
			this.waterTable.readSLK(waterSlkStream);
		}
		this.uberSplatTable = new DataTable(worldEditStrings);
		try (InputStream uberSlkStream = dataSource.getResourceAsStream("Splats\\UberSplatData.slk")) {
			this.uberSplatTable.readSLK(uberSlkStream);
		}

		final char tileset = w3eFile.getTileset();
		final Element waterInfo = this.waterTable.get(tileset + "Sha");
		if (waterInfo != null) {
			this.waterHeightOffset = waterInfo.getFieldFloatValue("height");
			this.waterTextureCount = waterInfo.getFieldValue("numTex");
			this.waterIncreasePerFrame = waterInfo.getFieldValue("texRate");
		} else {
			this.waterHeightOffset = 0;
			this.waterTextureCount = 0;
			this.waterIncreasePerFrame = 0;
		}

		loadWaterColor(this.minShallowColor, "Smin", waterInfo);
		loadWaterColor(this.maxShallowColor, "Smax", waterInfo);
		loadWaterColor(this.minDeepColor, "Dmin", waterInfo);
		loadWaterColor(this.maxDeepColor, "Dmax", waterInfo);
		for (int i = 0; i < 3; i++) {
			if (this.minDeepColor[i] > this.maxDeepColor[i]) {
				this.maxDeepColor[i] = this.minDeepColor[i];
			}
		}
		setWaterBaseColor(1.0f, 1.0f, 1.0f, 1.0f);

		// Cliff Meshes

		Map<String, Integer> cliffVars = Variations.CLIFF_VARS;
		for (final Map.Entry<String, Integer> cliffVar : cliffVars.entrySet()) {
			final Integer maxVariations = cliffVar.getValue();
			for (int variation = 0; variation <= maxVariations; variation++) {
				final String fileName = "Doodads\\Terrain\\Cliffs\\Cliffs" + cliffVar.getKey() + variation + ".mdx";
				this.cliffMeshes.add(new CliffMesh(fileName, dataSource, Gdx.gl30));
				this.pathToCliff.put("Cliffs" + cliffVar.getKey() + variation, this.cliffMeshes.size() - 1);
			}
		}
		cliffVars = Variations.CITY_CLIFF_VARS;
		for (final Map.Entry<String, Integer> cliffVar : cliffVars.entrySet()) {
			final Integer maxVariations = cliffVar.getValue();
			for (int variation = 0; variation <= maxVariations; variation++) {
				final String fileName = "Doodads\\Terrain\\CityCliffs\\CityCliffs" + cliffVar.getKey() + variation
						+ ".mdx";
				this.cliffMeshes.add(new CliffMesh(fileName, dataSource, Gdx.gl30));
				this.pathToCliff.put("CityCliffs" + cliffVar.getKey() + variation, this.cliffMeshes.size() - 1);
			}
		}

		final int textureSizeLimit = viewer.getTextureSizeLimit();
		// Ground textures
		for (final War3ID groundTile : w3eFile.getGroundTiles()) {
			final Element terrainTileInfo = this.terrainTable.get(groundTile.asStringValue());
			if (terrainTileInfo == null) {
				throw new RuntimeException("No terrain info for: " + groundTile.asStringValue());
			}
			final String dir = terrainTileInfo.getField("dir");
			final String file = terrainTileInfo.getField("file");
			this.groundTextures
					.add(new GroundTexture(dir + "\\" + file + texturesExt, terrainTileInfo, dataSource, Gdx.gl30,
							textureSizeLimit));
			this.groundTextureToId.put(groundTile.asStringValue(), this.groundTextures.size() - 1);
			this.groundTileIds.add(groundTile.getValue());
		}

		final Element tilesets = worldEditData.get("TileSets");

		this.blightTextureIndex = this.groundTextures.size();
		this.groundTextures
				.add(new GroundTexture(tilesets.getField(Character.toString(tileset)).split(",")[1] + texturesExt, null,
						dataSource, Gdx.gl30, textureSizeLimit));

		// Cliff Textures
		Element fallbackCliffInfo = null;
		for (final War3ID cliffTile : w3eFile.getCliffTiles()) {
			if (fallbackCliffInfo == null) {
				fallbackCliffInfo = this.cliffTable.get(cliffTile.asStringValue());
			}
		}
		for (final War3ID cliffTile : w3eFile.getCliffTiles()) {
			Element cliffInfo = this.cliffTable.get(cliffTile.asStringValue());
			if (cliffInfo == null) {
				// Corners index the map's cliff list, so an unknown type (HumanX03Interlude's COdt, absent from
				// 1.29 CliffTypes.slk) must keep its slot or every later type shifts down by one.
				DataErrors.report(DataErrors.UNKNOWN_ID, "cliff type " + cliffTile.asStringValue(),
						"Missing cliff type: " + cliffTile.asStringValue());
				cliffInfo = fallbackCliffInfo;
				if (cliffInfo == null) {
					continue;
				}
			}
			final String texDir = cliffInfo.getField("texDir");
			final String texFile = cliffInfo.getField("texFile");
			final RgbaImage image = ImageUtils.getAnyExtensionRgbaImage(dataSource,
					texDir + "\\" + texFile + texturesExt, "cliff texture", true, textureSizeLimit);
			this.cliffTextures.add(new UnloadedTexture(image.getWidth(), image.getHeight(), image.getPixels(),
					cliffInfo.getField("cliffModelDir"), cliffInfo.getField("rampModelDir")));
			this.cliffTexturesSize = Math.max(this.cliffTexturesSize,
					this.cliffTextures.get(this.cliffTextures.size() - 1).width);
			this.cliffToGroundTexture.add(this.groundTextureToId.get(cliffInfo.getField("groundTile")));
		}

		updateCliffMeshes(new Rectangle(0, 0, width - 1, height - 1));

		// prepare GPU data
		this.groundHeights = new float[width * height];
		this.groundCornerHeights = new float[width * height];
		this.groundTextureList = new short[(width - 1) * (height - 1) * 4];
		this.waterHeights = new float[width * height];
		this.waterExistsData = new byte[width * height];

		this.shownBlight = new boolean[width * height];
		this.blightPending = new boolean[width * height];
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				this.shownBlight[(j * width) + i] = this.corners[i][j].getBlight() != 0;
			}
		}
		updateGroundTextures(new Rectangle(0, 0, width - 1, height - 1));
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				this.groundCornerHeights[(j * width) + i] = this.corners[i][j].computeFinalGroundHeight();
				// The 3.0 terrain format keeps the water flag at bit 8, which a plain byte cast drops.
				this.waterExistsData[(j * width) + i] = (byte) (this.corners[i][j].getWater() != 0 ? 0xFF : 0);
				this.groundHeights[(j * width) + i] = this.corners[i][j].getGroundHeight();
				this.waterHeights[(j * width) + i] = this.corners[i][j].getWaterHeight();
			}
		}

		final GL30 gl = Gdx.gl30;
		// Ground
		this.groundTextureData = gl.glGenTexture();
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.groundTextureData);
		gl.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA16UI, width - 1, height - 1, 0, GL30.GL_RGBA_INTEGER,
				GL30.GL_UNSIGNED_SHORT, RenderMathUtils.wrapShort(this.groundTextureList));
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_NEAREST);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_NEAREST);

		this.groundHeight = gl.glGenTexture();
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.groundHeight);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);

		gl.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_R16F, width, height, 0, GL30.GL_RED, GL30.GL_FLOAT,
				RenderMathUtils.wrap(this.groundHeights));
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);

		this.groundCornerHeight = gl.glGenTexture();
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.groundCornerHeight);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_NEAREST);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_NEAREST);

		gl.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_R16F, width, height, 0, GL30.GL_RED, GL30.GL_FLOAT,
				RenderMathUtils.wrap(this.groundCornerHeights));
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);

		this.groundCornerHeightLinear = gl.glGenTexture();
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.groundCornerHeightLinear);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);

		gl.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_R16F, width, height, 0, GL30.GL_RED, GL30.GL_FLOAT,
				RenderMathUtils.wrap(this.groundCornerHeights));
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);

		// Cliff
		this.cliffTextureArray = gl.glGenTexture();
		gl.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, this.cliffTextureArray);
		gl.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, GL30.GL_RGBA8, this.cliffTexturesSize, this.cliffTexturesSize,
				this.cliffTextures.size(), 0, GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, null);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR_MIPMAP_LINEAR);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_BASE_LEVEL, 0);

		int sub = 0;
		for (final UnloadedTexture i : this.cliffTextures) {
			gl.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, sub, i.width, i.height, 1, GL30.GL_RGBA,
					GL30.GL_UNSIGNED_BYTE, i.data);
			sub += 1;
		}
		gl.glGenerateMipmap(GL30.GL_TEXTURE_2D_ARRAY);

		// Water
		this.waterHeight = gl.glGenTexture();
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.waterHeight);
		gl.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_R16F, width, height, 0, GL30.GL_RED, GL30.GL_FLOAT,
				RenderMathUtils.wrap(this.waterHeights));
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_NEAREST);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_NEAREST);

		this.waterExists = gl.glGenTexture();
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.waterExists);
		gl.glPixelStorei(GL30.GL_UNPACK_ALIGNMENT, 1);
		gl.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_R8, width, height, 0, GL30.GL_RED, GL30.GL_UNSIGNED_BYTE,
				RenderMathUtils.wrap(this.waterExistsData));
		gl.glPixelStorei(GL30.GL_UNPACK_ALIGNMENT, 4);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_NEAREST);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_NEAREST);

		// Water textures
		this.waterTextureArray = gl.glGenTexture();
		gl.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, this.waterTextureArray);

		if (waterInfo != null) {
			final String fileName = waterInfo.getField("texFile");
			final List<RgbaImage> waterTextures = new ArrayList<>();
			boolean anyWaterTextureNeedsSRGB = false;
			int waterImageDimension = 128;
			for (int i = 0; i < this.waterTextureCount; i++) {
				final RgbaImage image = ImageUtils.getAnyExtensionRgbaImage(dataSource,
						fileName + (i < 10 ? "0" : "") + Integer.toString(i) + texturesExt, "water texture", false);
				if ((image.getWidth() != 128) || (image.getHeight() != 128)) {
					Log.warn(
							"Odd water texture size detected of " + image.getWidth() + " x " + image.getHeight());
					waterImageDimension = image.getWidth();
				}
				anyWaterTextureNeedsSRGB |= image.isNeedsSRGBFix();
				waterTextures.add(image);
			}
			gl.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0,
					anyWaterTextureNeedsSRGB ? GL30.GL_SRGB8_ALPHA8 : GL30.GL_RGBA8, waterImageDimension,
					waterImageDimension, this.waterTextureCount, 0, GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, null);
			gl.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
			gl.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
			gl.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_BASE_LEVEL, 0);

			for (int i = 0; i < waterTextures.size(); i++) {
				final RgbaImage image = waterTextures.get(i);
				gl.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, image.getWidth(), image.getHeight(), 1,
						GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, image.getPixels());
			}
		}

		gl.glGenerateMipmap(GL30.GL_TEXTURE_2D_ARRAY);

		updateGroundHeights(new Rectangle(0, 0, width - 1, height - 1));

		this.groundShader = webGL.createShaderProgram(TerrainShaders.Terrain.vert(), TerrainShaders.Terrain.frag);
		this.cliffShader = webGL.createShaderProgram(TerrainShaders.Cliffs.vert(), TerrainShaders.Cliffs.frag);
		this.waterShader = webGL.createShaderProgram(TerrainShaders.Water.vert(), TerrainShaders.Water.frag);

		this.uberSplatShader = webGL.createShaderProgram(W3xShaders.UberSplat.vert(), W3xShaders.UberSplat.frag);

		// TODO collision bodies (?)

		this.centerOffset = w3eFile.getCenterOffset();
		this.uberSplatModels = new LinkedHashMap<>();
		this.uberSplatModelsList = new ArrayList<>();
		this.defaultCameraBounds = w3iFile.getCameraBounds();
		this.mapBounds = w3iFile.getCameraBoundsComplements();
		this.shaderMapBounds = new float[] { (this.mapBounds[0] * 128.0f) + this.centerOffset[0],
				(this.mapBounds[2] * 128.0f) + this.centerOffset[1],
				((this.columns - this.mapBounds[1] - 1) * 128.0f) + this.centerOffset[0],
				((this.rows - this.mapBounds[3] - 1) * 128.0f) + this.centerOffset[1] };
		this.shaderMapBoundsRectangle = new Rectangle(this.shaderMapBounds[0], this.shaderMapBounds[1],
				this.shaderMapBounds[2] - this.shaderMapBounds[0], this.shaderMapBounds[3] - this.shaderMapBounds[1]);
		this.mapSize = w3eFile.getMapSize();
		this.entireMapRectangle = new Rectangle(this.centerOffset[0], this.centerOffset[1],
				(this.mapSize[0] * 128f) - 128, (this.mapSize[1] * 128f) - 128);
		this.softwareGroundMesh = new SoftwareGroundMesh(this.groundHeights, this.groundCornerHeights,
				this.centerOffset, width, height);
		this.softwareWaterAndGroundMesh = new SoftwareWaterAndGroundMesh(this.waterHeightOffset,
				this.groundCornerHeights, this.waterHeights, this.waterExistsData, this.centerOffset, width, height);

		this.testArrayBuffer = gl.glGenBuffer();
		gl.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.testArrayBuffer);
		this.renderGroundBuffer = RenderMathUtils.wrap(this.softwareGroundMesh.vertices);
		gl.glBufferData(GL30.GL_ARRAY_BUFFER, this.softwareGroundMesh.vertices.length * 4, this.renderGroundBuffer,
				GL30.GL_STATIC_DRAW);

		this.testElementBuffer = gl.glGenBuffer();
		this.quadVertexBuffer = Shapes.INSTANCE.createQuadVertexBuffer(gl);
		this.quadIndexBuffer = Shapes.INSTANCE.createQuadIndexBuffer(gl);

		this.waveBuilder = new WaveBuilder(this.mapSize, this.waterTable, viewer, this.corners, this.centerOffset,
				this.waterHeightOffset, w3eFile, w3iFile);
		this.pathingGrid = new PathingGrid(terrainPathing, this.centerOffset);
	}

	public void createWaves() {
		this.waveBuilder.createWaves(this);
	}

	private void updateGroundHeights(final Rectangle area) {
		for (int j = (int) area.y; j < (area.y + area.height); j++) {
			for (int i = (int) area.x; i < (area.x + area.width); i++) {
				final float deformed = this.deformation == null ? 0 : this.deformation[(j * this.columns) + i];
				this.groundHeights[(j * this.columns) + i] = this.corners[i][j].getGroundHeight() + deformed;

				final float rampHeight = CCliffLayout.rampHeight(this.corners, this.columns, this.rows, i, j);

				final RenderCorner corner = this.corners[i][j];
				final float newGroundCornerHeight = corner.computeFinalGroundHeight() + rampHeight + deformed;
				this.groundCornerHeights[(j * this.columns) + i] = newGroundCornerHeight;
				corner.depth = corner.getWater() != 0
						? (this.waterHeightOffset + corner.getWaterHeight()) - newGroundCornerHeight
						: 0;
			}
		}
		final int firstRow = Math.max(0, (int) area.y);
		final int lastRow = Math.min(this.rows, (int) (area.y + area.height));
		uploadHeightRows(this.groundHeight, this.groundHeights, firstRow, lastRow);
		uploadHeightRows(this.groundCornerHeight, this.groundCornerHeights, firstRow, lastRow);
		uploadHeightRows(this.groundCornerHeightLinear, this.groundCornerHeights, firstRow, lastRow);
		refreshSoftwareMeshes((int) area.x - 1, firstRow - 1, (int) (area.x + area.width), lastRow);
	}

	// Deformations change a few rows every game tick; only those rows go to the GPU, through one reused buffer.
	private void uploadHeightRows(final int texture, final float[] heights, final int firstRow, final int lastRow) {
		if (lastRow <= firstRow) {
			return;
		}
		if (this.heightUploadBuffer == null) {
			this.heightUploadBuffer = BufferUtils.newFloatBuffer(this.columns * this.rows);
		}
		final FloatBuffer buffer = this.heightUploadBuffer;
		buffer.clear();
		buffer.put(heights, firstRow * this.columns, (lastRow - firstRow) * this.columns);
		buffer.flip();
		Gdx.gl30.glBindTexture(GL30.GL_TEXTURE_2D, texture);
		Gdx.gl30.glTexSubImage2D(GL30.GL_TEXTURE_2D, 0, 0, firstRow, this.columns, lastRow - firstRow, GL30.GL_RED,
				GL30.GL_FLOAT, buffer);
	}

	/** The meshes clicks are picked on follow the corner heights of the tiles in the given corner range. */
	private void refreshSoftwareMeshes(final int fromColumn, final int fromRow, final int toColumn, final int toRow) {
		if ((this.softwareGroundMesh == null) || (this.softwareWaterAndGroundMesh == null)) {
			return;
		}
		final float[][] quadVertices = Shapes.INSTANCE.quadVertices;
		for (int y = Math.max(0, fromRow); y <= Math.min(this.rows - 2, toRow); y++) {
			for (int x = Math.max(0, fromColumn); x <= Math.min(this.columns - 2, toColumn); x++) {
				final int instanceId = (y * (this.columns - 1)) + x;
				for (int vertexId = 0; vertexId < quadVertices.length; vertexId++) {
					final int corner = (int) (((quadVertices[vertexId][1] + y) * this.columns)
							+ (quadVertices[vertexId][0] + x));
					final int z = (instanceId * 4 * 3) + (vertexId * 3) + 2;
					final float ground = this.groundCornerHeights[corner];
					this.softwareGroundMesh.vertices[z] = ground * 128f;
					this.softwareWaterAndGroundMesh.vertices[z] = (this.waterExistsData[corner] != 0
							? Math.max(ground, this.waterHeights[corner] + this.waterHeightOffset)
							: ground) * 128f;
				}
			}
		}
	}

	public int[] getCornerCount() {
		return new int[] { this.columns, this.rows };
	}

	/**
	 * Sets the script deformation offsets (world units) of the corners in a rectangle; the drawn ground, splats on it
	 * and GetLocationZ follow them.
	 */
	public void setDeformation(final int column, final int row, final int width, final int height,
			final float[] offsets) {
		if (this.deformation == null) {
			this.deformation = new float[this.columns * this.rows];
		}
		for (int j = 0; j < height; j++) {
			for (int i = 0; i < width; i++) {
				this.deformation[((row + j) * this.columns) + column + i] = offsets[(j * width) + i] / 128f;
			}
		}
		updateGroundHeights(tempRect.set(column, row, width, height));
	}

	/// Updates the cliff and ramp meshes for an area
	private void updateCliffMeshes(final Rectangle area) throws IOException {
		// Remove all existing cliff meshes in area
		for (int i = this.cliffs.size(); i-- > 0;) {
			final IVec3 pos = this.cliffs.get(i);
			if (area.contains(pos.x, pos.y)) {
				this.cliffs.remove(i);
			}
		}

		CCliffLayout.update(this.corners, this.columns, this.rows, area, this.cliffTypes, this.cliffMeshPlacer);
	}

	private final CCliffLayout.CliffTypes cliffTypes = new CCliffLayout.CliffTypes() {
		@Override
		public String getRampModelDir(final int cliffType) {
			return Terrain.this.cliffTextures.get(cliffType).rampModelDir;
		}

		@Override
		public boolean has(final String path) {
			return Terrain.this.dataSource.has(path);
		}
	};

	private final CCliffLayout.Listener cliffMeshPlacer = new CCliffLayout.Listener() {
		@Override
		public void ramp(final String path, final int column, final int row) throws IOException {
			if (!Terrain.this.pathToCliff.containsKey(path)) {
				Terrain.this.cliffMeshes.add(new CliffMesh(path, Terrain.this.dataSource, Gdx.gl30));
				Terrain.this.pathToCliff.put(path, Terrain.this.cliffMeshes.size() - 1);
			}

			for (int ji = Terrain.this.cliffs.size(); ji-- > 0;) {
				final IVec3 pos = Terrain.this.cliffs.get(ji);
				if ((pos.x == column) && (pos.y == row)) {
					Terrain.this.cliffs.remove(ji);
					break;
				}
			}

			Terrain.this.cliffs.add(new IVec3(column, row, Terrain.this.pathToCliff.get(path)));
		}

		@Override
		public void cliff(final int i, final int j, final int base, final int cliffType) {
			final RenderCorner bottomLeft = Terrain.this.corners[i][j];
			final RenderCorner bottomRight = Terrain.this.corners[i + 1][j];
			final RenderCorner topLeft = Terrain.this.corners[i][j + 1];
			final RenderCorner topRight = Terrain.this.corners[i + 1][j + 1];

			String fileName = "" + (char) (('A' + topLeft.getLayerHeight()) - base)
					+ (char) (('A' + topRight.getLayerHeight()) - base)
					+ (char) (('A' + bottomRight.getLayerHeight()) - base)
					+ (char) (('A' + bottomLeft.getLayerHeight()) - base);

			if ("AAAA".equals(fileName)) {
				return;
			}

			// Clamp to within max variations

			final String cliffModelDir = Terrain.this.cliffTextures.get(cliffType).cliffModelDir;
			fileName = cliffModelDir + fileName
					+ Variations.getCliffVariation(cliffModelDir, fileName, bottomLeft.getCliffVariation());
			if (!Terrain.this.pathToCliff.containsKey(fileName)) {
				throw new IllegalArgumentException("No such pathToCliff entry: " + fileName);
			}
			Terrain.this.cliffs.add(new IVec3(i, j, Terrain.this.pathToCliff.get(fileName)));
		}
	};

	/**
	 * Sets the ground type and variation of corners; {@code cells} holds a column, row, tile type and variation for
	 * each. A type the map does not use yet is loaded, if there is room for its texture.
	 */
	public void setGroundTypes(final int[] cells) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		boolean textureAdded = false;
		for (int k = 0; (k + 3) < cells.length; k += 4) {
			final int column = cells[k];
			final int row = cells[k + 1];
			if ((column < 0) || (column >= this.columns) || (row < 0) || (row >= this.rows)) {
				continue;
			}
			final int known = this.groundTextures.size();
			final int index = groundTileIndex(cells[k + 2]);
			if (index < 0) {
				continue;
			}
			textureAdded |= this.groundTextures.size() != known;
			this.corners[column][row].setGroundTexture(index);
			this.corners[column][row].setGroundVariation(cells[k + 3]);
			minX = Math.min(minX, column);
			minY = Math.min(minY, row);
			maxX = Math.max(maxX, column);
			maxY = Math.max(maxY, row);
		}
		if (minX > maxX) {
			return;
		}
		if (textureAdded) {
			// The blight tile moved to the next slot, so every cell that shows it changes.
			updateGroundTextures(new Rectangle(0, 0, this.columns - 1, this.rows - 1));
		}
		else {
			updateGroundTextures(new Rectangle(minX, minY, maxX - minX, maxY - minY));
		}
	}

	private int groundTileIndex(final int tileType) {
		final int known = this.groundTileIds.indexOf(tileType);
		if (known >= 0) {
			return known;
		}
		if (this.groundTextures.size() >= MAX_GROUND_TEXTURES) {
			return -1;
		}
		final String id = new War3ID(tileType).asStringValue();
		final Element tileInfo = this.terrainTable.get(id);
		if (tileInfo == null) {
			return -1;
		}
		try {
			final GroundTexture texture = new GroundTexture(tileInfo.getField("dir") + "\\" + tileInfo.getField("file")
					+ ".blp", tileInfo, this.dataSource, Gdx.gl30, this.viewer.getTextureSizeLimit());
			this.groundTextures.add(this.blightTextureIndex, texture);
		}
		catch (final IOException e) {
			return -1;
		}
		final int index = this.blightTextureIndex++;
		this.groundTileIds.add(tileType);
		this.groundTextureToId.put(id, index);
		return index;
	}

	public void updateGroundTextures(final Rectangle area) {
		final Rectangle adjusted = new Rectangle(area.x - 1, area.y - 1, area.width + 2, area.height + 2);
		final Rectangle updateArea = new Rectangle();
		Intersector.intersectRectangles(new Rectangle(0, 0, this.columns - 1, this.rows - 1), adjusted, updateArea);

		for (int j = (int) updateArea.getY(); j <= (int) ((updateArea.getY() + updateArea.getHeight()) - 1); j++) {
			for (int i = (int) updateArea.getX(); i <= (int) ((updateArea.getX() + updateArea.getWidth()) - 1); i++) {
				getTextureVariations(i, j, this.groundTextureList, ((j * (this.columns - 1)) + i) * 4);

				if (this.corners[i][j].cliff || this.corners[i][j].romp) {
					if (isCornerRampEntrance(i, j)) {
						continue;
					}
					this.groundTextureList[(((j * (this.columns - 1)) + i) * 4) + 3] |= 0b1000000000000000;
				}
			}
		}

		uploadGroundTextureRows((int) updateArea.getY(), (int) updateArea.getHeight());
	}

	/**
	 * Uploads only the changed rows from one reused direct buffer: the whole texture with a new direct buffer each
	 * time stalls phones when blight grows.
	 */
	private void uploadGroundTextureRows(final int firstRow, final int rowCount) {
		if ((this.groundTextureData == -1) || (rowCount <= 0)) {
			return;
		}
		final int rowShorts = (this.columns - 1) * 4;
		if (this.groundTextureUploadBuffer == null) {
			this.groundTextureUploadBuffer = ByteBuffer.allocateDirect(this.groundTextureList.length * 2)
					.order(ByteOrder.nativeOrder());
		}
		final ByteBuffer buffer = this.groundTextureUploadBuffer;
		buffer.clear();
		buffer.asShortBuffer().put(this.groundTextureList, firstRow * rowShorts, rowCount * rowShorts);
		buffer.limit(rowCount * rowShorts * 2);
		Gdx.gl30.glBindTexture(GL30.GL_TEXTURE_2D, this.groundTextureData);
		Gdx.gl30.glTexSubImage2D(GL30.GL_TEXTURE_2D, 0, 0, firstRow, this.columns - 1, rowCount,
				GL30.GL_RGBA_INTEGER, GL30.GL_UNSIGNED_SHORT, buffer);
	}

	/** The blight of the corner at the point changed; it is drawn once the viewer sees it. */
	public void blightChanged(final float x, final float y) {
		final int cellX = (int) ((x - this.centerOffset[0]) / 128.0f);
		final int cellY = (int) ((y - this.centerOffset[1]) / 128.0f);
		if ((cellX < 0) || (cellX >= this.columns) || (cellY < 0) || (cellY >= this.rows)) {
			return;
		}
		final int index = (cellY * this.columns) + cellX;
		if (!this.blightPending[index]) {
			this.blightPending[index] = true;
			if (this.pendingBlightCount == this.pendingBlight.length) {
				this.pendingBlight = Arrays.copyOf(this.pendingBlight, this.pendingBlight.length * 2);
			}
			this.pendingBlight[this.pendingBlightCount++] = index;
		}
	}

	/** The blight of every corner (the ground itself, or as the viewer last saw it) as bits in corner order. */
	public byte[] captureBlight(final boolean asSeen) {
		final byte[] bits = new byte[((this.columns * this.rows) + 7) / 8];
		for (int j = 0; j < this.rows; j++) {
			for (int i = 0; i < this.columns; i++) {
				final int index = (j * this.columns) + i;
				if (asSeen ? this.shownBlight[index] : (this.corners[i][j].getBlight() != 0)) {
					bits[index >> 3] |= 1 << (index & 7);
				}
			}
		}
		return bits;
	}

	/**
	 * A loaded game starts from the map's own blight: puts back the ground's blight and what the viewer had seen of
	 * it. The corners that differ are drawn once the viewer sees them again.
	 */
	public void restoreBlight(final byte[] ground, final byte[] seen) {
		final int count = this.columns * this.rows;
		if ((ground == null) || (seen == null) || (ground.length * 8 < count) || (seen.length * 8 < count)) {
			return;
		}
		Arrays.fill(this.blightPending, false);
		this.pendingBlightCount = 0;
		for (int j = 0; j < this.rows; j++) {
			for (int i = 0; i < this.columns; i++) {
				final int index = (j * this.columns) + i;
				final boolean blighted = (ground[index >> 3] & (1 << (index & 7))) != 0;
				this.corners[i][j].setBlight(blighted);
				this.shownBlight[index] = (seen[index >> 3] & (1 << (index & 7))) != 0;
				if (blighted != this.shownBlight[index]) {
					this.blightPending[index] = true;
					if (this.pendingBlightCount == this.pendingBlight.length) {
						this.pendingBlight = Arrays.copyOf(this.pendingBlight, this.pendingBlight.length * 2);
					}
					this.pendingBlight[this.pendingBlightCount++] = index;
				}
			}
		}
		updateGroundTextures(new Rectangle(0, 0, this.columns - 1, this.rows - 1));
	}

	/** Draws the blight changes at the corners the viewer now sees. */
	public void updateShownBlight() {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int kept = 0;
		for (int k = 0; k < this.pendingBlightCount; k++) {
			final int index = this.pendingBlight[k];
			final int x = index % this.columns;
			final int y = index / this.columns;
			final boolean blighted = this.corners[x][y].getBlight() != 0;
			if (blighted != this.shownBlight[index]) {
				if (!this.viewer.isPointShownToViewer(this.centerOffset[0] + (x * 128f),
						this.centerOffset[1] + (y * 128f))) {
					this.pendingBlight[kept++] = index;
					continue;
				}
				this.shownBlight[index] = blighted;
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
			}
			this.blightPending[index] = false;
		}
		this.pendingBlightCount = kept;
		if (minX <= maxX) {
			updateGroundTextures(new Rectangle(minX, minY, maxX - minX, maxY - minY));
		}
	}

	public void removeTerrainCellWithoutFlush(final int i, final int j) {
		this.groundTextureList[(((j * (this.columns - 1)) + i) * 4) + 3] |= 0b1000000000000000;
		this.corners[i][j].hideCliff = true;
	}

	public void flushRemovedTerrainCells() {
		uploadGroundTexture();
		try {
			updateCliffMeshes(new Rectangle(0, 0, this.columns - 1, this.rows - 1));
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void uploadGroundTexture() {
		if (this.groundTextureData != -1) {
			Gdx.gl30.glBindTexture(GL30.GL_TEXTURE_2D, this.groundTextureData);
			Gdx.gl30.glTexSubImage2D(GL30.GL_TEXTURE_2D, 0, 0, 0, this.columns - 1, this.rows - 1, GL30.GL_RGBA_INTEGER,
					GL30.GL_UNSIGNED_SHORT, RenderMathUtils.wrapShort(this.groundTextureList));
		}
	}

	/// The 4 ground textures of the tilepoint. First 5 bits are which texture array
	/// to use and the next 5 bits are which subtexture to use
	private void getTextureVariations(final int x, final int y, final short[] out, final int outStartOffset) {
		final int bottomLeft = realTileTexture(x, y);
		final int bottomRight = realTileTexture(x + 1, y);
		final int topLeft = realTileTexture(x, y + 1);
		final int topRight = realTileTexture(x + 1, y + 1);

		final TreeSet<Integer> set = new TreeSet<>();
		set.add(bottomLeft);
		set.add(bottomRight);
		set.add(topLeft);
		set.add(topRight);
		Arrays.fill(out, outStartOffset, outStartOffset + 4, (short) 17);
		int component = outStartOffset + 1;

		final Iterator<Integer> iterator = set.iterator();
		iterator.hasNext();
		final short firstValue = iterator.next().shortValue();
		out[outStartOffset] = (short) (firstValue
				+ (getVariation(firstValue, this.corners[x][y].getGroundVariation()) << 5));

		int index;
		while (iterator.hasNext()) {
			index = 0;
			final int texture = iterator.next().intValue();
			index |= (bottomRight == texture ? 1 : 0) << 0;
			index |= (bottomLeft == texture ? 1 : 0) << 1;
			index |= (topRight == texture ? 1 : 0) << 2;
			index |= (topLeft == texture ? 1 : 0) << 3;

			out[component++] = (short) (texture + (index << 5));
		}
	}

	private int realTileTexture(final int x, final int y) {
		ILoop: for (int i = -1; i < 1; i++) {
			for (int j = -1; j < 1; j++) {
				if (((x + i) >= 0) && ((x + i) < this.columns) && ((y + j) >= 0) && ((y + j) < this.rows)) {
					if (this.corners[x + i][y + j].cliff) {
						if (((x + i) < (this.columns - 1)) && ((y + j) < (this.rows - 1))) {
							final RenderCorner bottomLeft = this.corners[x + i][y + j];
							final RenderCorner bottomRight = this.corners[x + i + 1][y + j];
							final RenderCorner topLeft = this.corners[x + i][y + j + 1];
							final RenderCorner topRight = this.corners[x + i + 1][y + j + 1];

							if (bottomLeft.isRamp() && topLeft.isRamp() && bottomRight.isRamp() && topRight.isRamp()
									&& !bottomLeft.romp && !bottomRight.romp && !topLeft.romp && !topRight.romp) {
								break ILoop;
							}
						}
					}
					if (this.corners[x + i][y + j].romp || this.corners[x + i][y + j].cliff) {
						int texture = this.corners[x + i][y + j].getCliffTexture();
						// Number 15 seems to be something
						if (texture == 15) {
							texture -= 14;
						}

						return this.cliffToGroundTexture.get(texture);
					}
				}
			}
		}

		if ((this.shownBlight != null) ? this.shownBlight[(y * this.columns) + x] : (this.corners[x][y].getBlight() != 0)) {
			return this.blightTextureIndex;
		}

		return this.corners[x][y].getGroundTexture();
	}

	private boolean isCornerRampEntrance(final int x, final int y) {
		return CCliffLayout.isCornerRampEntrance(this.corners, this.columns, this.rows, x, y);
	}

	private static void loadWaterColor(final float[] out, final String prefix, final Element waterInfo) {
		for (int i = 0; i < colorTags.length; i++) {
			final String colorTag = colorTags[i];
			out[i] = waterInfo == null ? 0.0f : waterInfo.getFieldFloatValue(prefix + "_" + colorTag) / 255f;
		}
	}

	public short getVariation(final int groundTexture, final int variation) {
		final GroundTexture texture = this.groundTextures.get(groundTexture);

		// Extended ?
		if (texture.extended) {
			if (variation <= 15) {
				return (short) (16 + variation);
			} else if (variation == 16) {
				return 15;
			} else {
				return 0;
			}
		} else {
			if (variation == 0) {
				return 0;
			} else {
				return 15;
			}
		}
	}

	public void update(final float deltaTime) {
		if (this.shadowsChanged) {
			this.shadowsChanged = false;
			if (this.initShadowsFinished) {
				reloadShadowDataToGPU();
			}
		}
		this.waterIndex += this.waterIncreasePerFrame * deltaTime;

		if (this.waterIndex >= this.waterTextureCount) {
			this.waterIndex = 0;
		}
	}

	public void renderGround(final DynamicShadowManager dynamicShadowManager) {
		// Render tiles

		this.webGL.useShaderProgram(this.groundShader);

		final GL30 gl = Gdx.gl30;
		gl.glEnable(GL20.GL_CULL_FACE);
		gl.glDisable(GL30.GL_BLEND);
		gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		gl.glEnable(GL20.GL_DEPTH_TEST);
		gl.glDepthMask(true);

		gl.glUniformMatrix4fv(this.groundShader.getUniformLocation("MVP"), 1, false,
				this.camera.viewProjectionMatrix.val, 0);
		gl.glUniform1i(this.groundShader.getUniformLocation("show_pathing_map"), this.viewer.renderPathing);
		gl.glUniform1i(this.groundShader.getUniformLocation("show_lighting"), this.viewer.renderLighting);
		gl.glUniform1i(this.groundShader.getUniformLocation("height_texture"), 0);
		gl.glUniform1i(this.groundShader.getUniformLocation("height_cliff_texture"), 1);
		gl.glUniform1i(this.groundShader.getUniformLocation("terrain_texture_list"), 2);
		gl.glUniform1i(this.groundShader.getUniformLocation("shadowMap"), 20);
		gl.glUniform1f(this.groundShader.getUniformLocation("centerOffsetX"), this.centerOffset[0]);
		gl.glUniform1f(this.groundShader.getUniformLocation("centerOffsetY"), this.centerOffset[1]);

		final W3xSceneLightManager lightManager = (W3xSceneLightManager) this.viewer.worldScene.getLightManager();
		final DataTexture unitLightsTexture = lightManager.getTerrainLightsTexture();

		unitLightsTexture.bind(21);
		gl.glUniform1i(this.groundShader.getUniformLocation("lightTexture"), 21);
		gl.glUniform1f(this.groundShader.getUniformLocation("lightCount"), lightManager.getTerrainLightCount());
		gl.glUniform1f(this.groundShader.getUniformLocation("lightTextureHeight"), unitLightsTexture.getHeight());
		this.groundShader.setUniformf("u_fogColor", this.viewer.worldScene.fogSettings.color);
		this.groundShader.setUniformf("u_fogParams", this.viewer.worldScene.fogSettings.style.ordinal(),
				this.viewer.worldScene.fogSettings.start, this.viewer.worldScene.fogSettings.end,
				this.viewer.worldScene.fogSettings.density);

		gl.glUniformMatrix4fv(this.groundShader.getUniformLocation("DepthBiasMVP"), 1, false,
				dynamicShadowManager.getDepthBiasMVP().val, 0);

		gl.glUniform1i(this.groundShader.getUniformLocation("cliff_textures"), 0);
		gl.glActiveTexture(GL30.GL_TEXTURE0);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.groundHeight);

		gl.glActiveTexture(GL30.GL_TEXTURE1);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.groundCornerHeight);

		gl.glUniform1i(this.groundShader.getUniformLocation("pathing_map_static"), 2);
		gl.glActiveTexture(GL30.GL_TEXTURE2);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.groundTextureData);

		gl.glUniform1i(this.groundShader.getUniformLocation("sample0"), 3);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample1"), 4);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample2"), 5);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample3"), 6);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample4"), 7);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample5"), 8);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample6"), 9);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample7"), 10);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample8"), 11);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample9"), 12);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample10"), 13);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample11"), 14);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample12"), 15);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample13"), 16);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample14"), 17);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample15"), 18);
		gl.glUniform1i(this.groundShader.getUniformLocation("sample16"), 19);
		gl.glUniform1i(this.groundShader.getUniformLocation("shadowMap"), 20);
		gl.glUniform1i(this.groundShader.getUniformLocation("fogOfWarMap"), 22);
		for (int i = 0; i < this.groundTextures.size(); i++) {
			gl.glActiveTexture(GL30.GL_TEXTURE3 + i);
			gl.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, this.groundTextures.get(i).id);
		}

//		gl.glActiveTexture(GL30.GL_TEXTURE20, /*pathingMap.getTextureStatic()*/);
//		gl.glActiveTexture(GL30.GL_TEXTURE21, /*pathingMap.getTextureDynamic()*/);

		gl.glActiveTexture(GL30.GL_TEXTURE20);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.shadowMap);

		gl.glActiveTexture(GL30.GL_TEXTURE22);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.fogOfWarMap);

//		gl.glEnableVertexAttribArray(0);
		gl.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.quadVertexBuffer);
		gl.glVertexAttribPointer(this.groundShader.getAttributeLocation("vPosition"), 2, GL30.GL_FLOAT, false, 0, 0);

		gl.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, this.quadIndexBuffer);
		if (WIREFRAME_TERRAIN) {
			Extensions.wireframeExtension.glPolygonMode(GL20.GL_FRONT_AND_BACK, Extensions.GL_LINE);
		}
		gl.glDrawElementsInstanced(GL30.GL_TRIANGLES, Shapes.INSTANCE.quadIndices.length * 3, GL30.GL_UNSIGNED_INT, 0,
				(this.columns - 1) * (this.rows - 1));
		if (WIREFRAME_TERRAIN) {
			Extensions.wireframeExtension.glPolygonMode(GL20.GL_FRONT_AND_BACK, Extensions.GL_FILL);
		}

//		gl.glDisableVertexAttribArray(0);

		gl.glEnable(GL30.GL_BLEND);

	}

	public void renderUberSplats(final boolean onTopLayer) {
		final GL30 gl = Gdx.gl30;
		final WebGL webGL = this.webGL;
		final ShaderProgram shader = this.uberSplatShader;

		gl.glDepthMask(false);
		gl.glEnable(GL30.GL_BLEND);
		gl.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);
		gl.glBlendEquation(GL30.GL_FUNC_ADD);

		webGL.useShaderProgram(this.uberSplatShader);

		shader.setUniformMatrix("u_mvp", this.camera.viewProjectionMatrix);
		shader.setUniformi("u_heightMap", 0);
		sizeHeap[0] = this.columns - 1;
		sizeHeap[1] = this.rows - 1;
		shader.setUniform2fv("u_size", sizeHeap, 0, 2);
		sizeHeap[0] = 1 / (float) this.columns;
		sizeHeap[1] = 1 / (float) this.rows;
		shader.setUniform2fv("u_pixel", sizeHeap, 0, 2);
		shader.setUniform2fv("u_centerOffset", this.centerOffset, 0, 2);
		shader.setUniformi("u_texture", 1);
		shader.setUniformi("u_shadowMap", 2);
		shader.setUniformi("u_waterHeightsMap", 3);
		shader.setUniformi("u_fogOfWarMap", 4);
		shader.setUniformf("u_waterHeightOffset", this.waterHeightOffset);

		gl.glActiveTexture(GL30.GL_TEXTURE0);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.groundCornerHeightLinear);

		gl.glActiveTexture(GL30.GL_TEXTURE2);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.shadowMap);

		gl.glActiveTexture(GL30.GL_TEXTURE3);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.waterHeight);

		gl.glActiveTexture(GL30.GL_TEXTURE4);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.fogOfWarMap);

		final W3xSceneLightManager lightManager = (W3xSceneLightManager) this.viewer.worldScene.getLightManager();
		final DataTexture terrainLightsTexture = lightManager.getTerrainLightsTexture();

		terrainLightsTexture.bind(21);
		gl.glUniform1i(shader.getUniformLocation("u_lightTexture"), 21);
		gl.glUniform1f(shader.getUniformLocation("u_lightCount"), lightManager.getTerrainLightCount());
		gl.glUniform1f(shader.getUniformLocation("u_lightTextureHeight"), terrainLightsTexture.getHeight());
		shader.setUniformf("u_fogColor", this.viewer.worldScene.fogSettings.color);
		shader.setUniformf("u_fogParams", this.viewer.worldScene.fogSettings.style.ordinal(),
				this.viewer.worldScene.fogSettings.start, this.viewer.worldScene.fogSettings.end,
				this.viewer.worldScene.fogSettings.density);

		// Render the cliffs
		for (final SplatModel splat : this.uberSplatModelsList) {
			if ((splat.isHighPriority() == onTopLayer)
					&& (GameOptions.get().isUnitShadows() || !this.unitShadowSplatModels.contains(splat))) {
				splat.render(gl, shader);
			}
		}
	}

	public void renderWater() {
		// Render water
		this.webGL.useShaderProgram(this.waterShader);

		final GL30 gl = Gdx.gl30;
		gl.glDepthMask(false);
		gl.glEnable(GL30.GL_CULL_FACE);
		gl.glEnable(GL30.GL_BLEND);
		gl.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);

		this.waterShader.setUniformMatrix4fv("MVP", this.camera.viewProjectionMatrix.val, 0, 16);
		this.waterShader.setUniform4fv("shallow_color_min", this.minShallowColorApplied, 0, 4);
		this.waterShader.setUniform4fv("shallow_color_max", this.maxShallowColorApplied, 0, 4);
		this.waterShader.setUniform4fv("deep_color_min", this.minDeepColorApplied, 0, 4);
		this.waterShader.setUniform4fv("deep_color_max", this.maxDeepColorApplied, 0, 4);
		this.waterShader.setUniformf("water_offset", this.waterHeightOffset);
		this.waterShader.setUniformi("current_texture", (int) this.waterIndex);
		this.waterShader.setUniformf("centerOffsetX", this.centerOffset[0]);
		this.waterShader.setUniformf("centerOffsetY", this.centerOffset[1]);
		this.waterShader.setUniform4fv("mapBounds", this.shaderMapBounds, 0, 4);

		final W3xSceneLightManager lightManager = (W3xSceneLightManager) this.viewer.worldScene.getLightManager();
		final DataTexture terrainLightsTexture = lightManager.getTerrainLightsTexture();

		terrainLightsTexture.bind(3);
		this.waterShader.setUniformi("lightTexture", 3);
		this.waterShader.setUniformf("lightCount", lightManager.getTerrainLightCount());
		this.waterShader.setUniformf("lightTextureHeight", terrainLightsTexture.getHeight());
		this.waterShader.setUniformf("u_fogColor", this.viewer.worldScene.fogSettings.color);
		this.waterShader.setUniformf("u_fogParams", this.viewer.worldScene.fogSettings.style.ordinal(),
				this.viewer.worldScene.fogSettings.start, this.viewer.worldScene.fogSettings.end,
				this.viewer.worldScene.fogSettings.density);

		this.waterShader.setUniformi("water_height_texture", 0);
		this.waterShader.setUniformi("ground_height_texture", 1);
		this.waterShader.setUniformi("water_exists_texture", 2);
		this.waterShader.setUniformi("water_textures", 4);
		this.waterShader.setUniformi("fogOfWarMap", 5);
		gl.glActiveTexture(GL30.GL_TEXTURE0);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.waterHeight);
		gl.glActiveTexture(GL30.GL_TEXTURE1);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.groundCornerHeight);
		gl.glActiveTexture(GL30.GL_TEXTURE2);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.waterExists);
		gl.glActiveTexture(GL30.GL_TEXTURE4);
		gl.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, this.waterTextureArray);
		gl.glActiveTexture(GL30.GL_TEXTURE5);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.fogOfWarMap);

		gl.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.quadVertexBuffer);
		gl.glVertexAttribPointer(this.waterShader.getAttributeLocation("vPosition"), 2, GL30.GL_FLOAT, false, 0, 0);

		gl.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, this.quadIndexBuffer);
		gl.glDrawElementsInstanced(GL30.GL_TRIANGLES, Shapes.INSTANCE.quadIndices.length * 3, GL30.GL_UNSIGNED_INT, 0,
				(this.columns - 1) * (this.rows - 1));

		gl.glEnable(GL30.GL_BLEND);
	}

	public void renderCliffs() {

		// Render cliffs
		for (final IVec3 i : this.cliffs) {
			final RenderCorner bottomLeft = this.corners[i.x][i.y];
			final RenderCorner bottomRight = this.corners[i.x + 1][i.y];
			final RenderCorner topLeft = this.corners[i.x][i.y + 1];
			final RenderCorner topRight = this.corners[i.x + 1][i.y + 1];

			final float min = Math.min(Math.min(bottomLeft.getLayerHeight() - 2, bottomRight.getLayerHeight() - 2),
					Math.min(topLeft.getLayerHeight() - 2, topRight.getLayerHeight() - 2));

			fourComponentHeap[0] = i.x;
			fourComponentHeap[1] = i.y;
			fourComponentHeap[2] = min;
			fourComponentHeap[3] = bottomLeft.getCliffTexture();
			this.cliffMeshes.get(i.z).renderQueue(fourComponentHeap);
		}

		this.webGL.useShaderProgram(this.cliffShader);

		final GL30 gl = Gdx.gl30;
		gl.glDisable(GL30.GL_BLEND);

		// WC3 models are 128x too large
		tempMatrix.set(this.camera.viewProjectionMatrix);
		gl.glUniformMatrix4fv(this.cliffShader.getUniformLocation("MVP"), 1, false, tempMatrix.val, 0);
		gl.glUniform1i(this.cliffShader.getUniformLocation("show_lighting"), this.viewer.renderLighting);

		final W3xSceneLightManager lightManager = (W3xSceneLightManager) this.viewer.worldScene.getLightManager();
		final DataTexture unitLightsTexture = lightManager.getTerrainLightsTexture();

		unitLightsTexture.bind(21);
		gl.glUniform1i(this.cliffShader.getUniformLocation("lightTexture"), 21);
		gl.glUniform1f(this.cliffShader.getUniformLocation("lightCount"), lightManager.getTerrainLightCount());
		gl.glUniform1f(this.cliffShader.getUniformLocation("lightTextureHeight"), unitLightsTexture.getHeight());
		this.cliffShader.setUniformf("u_fogColor", this.viewer.worldScene.fogSettings.color);
		this.cliffShader.setUniformf("u_fogParams", this.viewer.worldScene.fogSettings.style.ordinal(),
				this.viewer.worldScene.fogSettings.start, this.viewer.worldScene.fogSettings.end,
				this.viewer.worldScene.fogSettings.density);

		this.cliffShader.setUniformi("shadowMap", 2);
		gl.glActiveTexture(GL30.GL_TEXTURE2);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.shadowMap);

		this.cliffShader.setUniformi("fogOfWarMap", 3);
		gl.glActiveTexture(GL30.GL_TEXTURE3);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.fogOfWarMap);

		this.cliffShader.setUniformi("cliff_textures", 0);
		gl.glActiveTexture(GL30.GL_TEXTURE0);
		gl.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, this.cliffTextureArray);
		this.cliffShader.setUniformi("height_texture", 1);
		gl.glActiveTexture(GL30.GL_TEXTURE1);
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.groundHeight);
//		gl.glActiveTexture(GL30.GL_TEXTURE2);
		for (final CliffMesh i : this.cliffMeshes) {
			gl.glUniform1f(this.cliffShader.getUniformLocation("centerOffsetX"), this.centerOffset[0]);
			gl.glUniform1f(this.cliffShader.getUniformLocation("centerOffsetY"), this.centerOffset[1]);
			i.render(this.cliffShader);
		}
	}

	public BuildingShadow addShadow(final String file, final float shadowX, final float shadowY) {
		if (!this.shadows.containsKey(file)) {
			// Some 2.0 and later tables give the shadow with its extension (ShadowCrates.blp).
			final String lowerFile = file.toLowerCase(Locale.US);
			final boolean hasExtension = lowerFile.endsWith(".blp") || lowerFile.endsWith(".dds")
					|| lowerFile.endsWith(".tga");
			final String path = "ReplaceableTextures\\Shadows\\" + file + (hasExtension ? "" : ".blp");
			this.shadows.put(file, new ArrayList<>());
			this.shadowTextures.put(file, (Texture) this.viewer.load(path, PathSolver.DEFAULT, null));
		}
		final List<float[]> shadowList = this.shadows.get(file);
		final float[] shadowPositionArray = new float[] { shadowX, shadowY };
		shadowList.add(shadowPositionArray);
		if (this.initShadowsFinished) {
			final Texture texture = this.shadowTextures.get(file);

			final int columns = (this.columns - 1) * 4;
			final int rows = (this.rows - 1) * 4;
			blitShadowData(columns, rows, shadowX, shadowY, texture);
			uploadShadowData(columns, rows);
		}
		return new BuildingShadow() {
			@Override
			public void remove() {
				shadowList.remove(shadowPositionArray);
				Terrain.this.shadowsChanged = true;
			}

			@Override
			public void move(final float x, final float y) {
				shadowPositionArray[0] = x;
				shadowPositionArray[1] = y;
				Terrain.this.shadowsChanged = true;
			}
		};
	}

	public void blitShadowData(final int columns, final int rows, final float shadowX, final float shadowY,
			final Texture texture) {
		final int width = texture.getWidth();
		final int height = texture.getHeight();
		final int ox = (int) Math.round(width * 0.3);
		final int oy = (int) Math.round(height * 0.7);
		blitShadowDataLocation(columns, rows, (RawOpenGLTextureResource) texture, width, height, ox, oy,
				this.centerOffset, shadowX, shadowY, this.shadowData);
	}

	public void initShadows() throws IOException {
		final GL30 gl = Gdx.gl30;
		final float[] centerOffset = this.centerOffset;
		final int columns = (this.columns - 1) * 4;
		final int rows = (this.rows - 1) * 4;

		final int shadowSize = columns * rows;
		this.staticShadowData = new byte[columns * rows];
		this.shadowData = new byte[columns * rows];
		if (this.viewer.mapMpq.has("war3map.shd")) {
			final byte[] buffer;

			try (final InputStream shadowSource = this.viewer.mapMpq.getResourceAsStream("war3map.shd")) {
				buffer = IOUtils.toByteArray(shadowSource);
			}

			for (int i = 0; i < shadowSize; i++) {
				this.staticShadowData[i] = (byte) ((buffer[i] & 0xFF) / 2f);
			}
		}

		final byte outsideArea = (byte) 204;
		final int x0 = this.mapBounds[0] * 4, x1 = (this.mapSize[0] - this.mapBounds[1] - 1) * 4,
				y0 = this.mapBounds[2] * 4, y1 = (this.mapSize[1] - this.mapBounds[3] - 1) * 4;
		for (int y = y0; y < y1; ++y) {
			for (int x = x0; x < x1; ++x) {
				final RenderCorner c = this.corners[x >> 2][y >> 2];
				if (c.getBoundary() != 0) {
					this.staticShadowData[(y * columns) + x] = outsideArea;
				}
			}
		}
		for (int y = 0; y < rows; ++y) {
			for (int x = 0; x < x0; ++x) {
				this.staticShadowData[(y * columns) + x] = outsideArea;
			}
			for (int x = x1; x < columns; ++x) {
				this.staticShadowData[(y * columns) + x] = outsideArea;
			}
		}
		for (int x = x0; x < x1; ++x) {
			for (int y = 0; y < y0; ++y) {
				this.staticShadowData[(y * columns) + x] = outsideArea;
			}
			for (int y = y1; y < rows; ++y) {
				this.staticShadowData[(y * columns) + x] = outsideArea;
			}
		}
		reloadShadowData(centerOffset, columns, rows);

		this.shadowMap = gl.glGenTexture();
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.shadowMap);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
		this.shadowDataBuffer = ByteBuffer.allocateDirect(this.shadowData.length).order(ByteOrder.nativeOrder());
		this.shadowDataBuffer.put(this.shadowData);
		this.shadowDataBuffer.clear();
		gl.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_R8, columns, rows, 0, GL30.GL_RED, GL30.GL_UNSIGNED_BYTE,
				this.shadowDataBuffer);
		this.initShadowsFinished = true;

		this.fogOfWarMap = gl.glGenTexture();
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.fogOfWarMap);
	}

	private void reloadShadowData(final float[] centerOffset, final int columns, final int rows) {
		System.arraycopy(this.staticShadowData, 0, this.shadowData, 0, this.staticShadowData.length);
		for (final Map.Entry<String, Texture> fileAndTexture : this.shadowTextures.entrySet()) {
			final String file = fileAndTexture.getKey();
			final Texture texture = fileAndTexture.getValue();

			final int width = texture.getWidth();
			final int height = texture.getHeight();
			final int ox = (int) Math.round(width * 0.3);
			final int oy = (int) Math.round(height * 0.7);
			for (final float[] location : this.shadows.get(file)) {
				blitShadowDataLocation(columns, rows, (RawOpenGLTextureResource) texture, width, height, ox, oy,
						centerOffset, location[0], location[1], this.shadowData);
			}
		}
	}

	public void blitShadowDataLocation(final int columns, final int rows, final RawOpenGLTextureResource texture,
			final int width, final int height, final int x01, final int y01, final float[] centerOffset, final float v,
			final float v2, final byte[] shadowData) {
		final int x0 = (int) Math.floor((v - centerOffset[0]) / 32.0) - x01;
		final int y0 = (int) Math.floor((v2 - centerOffset[1]) / 32.0) + y01;
		final boolean[] mask = getShadowMask(texture, width, height);
		for (int y = 0; y < height; ++y) {
			if (((y0 - y) < 0) || ((y0 - y) >= rows)) {
				continue;
			}
			for (int x = 0; x < width; ++x) {
				if (((x0 + x) < 0) || ((x0 + x) >= columns)) {
					continue;
				}
				if (mask[(y * width) + x]) {
					shadowData[((y0 - y) * columns) + x0 + x] = (byte) 128;
				}
			}
		}
	}

	// Reading the texture's direct buffer pixel by pixel was slow on phones, and every shadow is drawn again when one
	// of them goes away.
	private boolean[] getShadowMask(final RawOpenGLTextureResource texture, final int width, final int height) {
		boolean[] mask = this.shadowMasks.get(texture);
		if (mask == null) {
			mask = new boolean[width * height];
			final ByteBuffer data = texture.getData();
			for (int i = 0; i < mask.length; i++) {
				mask[i] = data.get((i * 4) + 3) != 0;
			}
			this.shadowMasks.put(texture, mask);
		}
		return mask;
	}

	private final WaveBuilder waveBuilder;
	public PathingGrid pathingGrid;
	private final Rectangle shaderMapBoundsRectangle;
	private final Rectangle entireMapRectangle;
	private final float[] defaultCameraBounds;

	private static final class UnloadedTexture {
		private final int width;
		private final int height;
		private final Buffer data;
		private final String cliffModelDir;
		private final String rampModelDir;

		public UnloadedTexture(final int width, final int height, final Buffer data, final String cliffModelDir,
				final String rampModelDir) {
			this.width = width;
			this.height = height;
			this.data = data;
			this.cliffModelDir = cliffModelDir;
			this.rampModelDir = rampModelDir;
		}

	}

	public float getGroundHeight(final float x, final float y) {
		final float userCellSpaceX = (x - this.centerOffset[0]) / 128.0f;
		final float userCellSpaceY = (y - this.centerOffset[1]) / 128.0f;
		final int cellX = (int) userCellSpaceX;
		final int cellY = (int) userCellSpaceY;

		if ((cellX >= 0) && (cellX < (this.mapSize[0] - 1)) && (cellY >= 0) && (cellY < (this.mapSize[1] - 1))) {
			final float bottomLeft = this.groundCornerHeights[(cellY * this.columns) + cellX];
			final float bottomRight = this.groundCornerHeights[(cellY * this.columns) + cellX + 1];
			final float topLeft = this.groundCornerHeights[((cellY + 1) * this.columns) + cellX];
			final float topRight = this.groundCornerHeights[((cellY + 1) * this.columns) + cellX + 1];
			final float sqX = userCellSpaceX - cellX;
			final float sqY = userCellSpaceY - cellY;
			float height;

			if ((sqX + sqY) < 1) {
				height = bottomLeft + ((bottomRight - bottomLeft) * sqX) + ((topLeft - bottomLeft) * sqY);
			} else {
				height = topRight + ((bottomRight - topRight) * (1 - sqY)) + ((topLeft - topRight) * (1 - sqX));
			}

			return height * 128.0f;
		}

		return 0;
	}

	/**
	 * The height scripts read: the original game interpolates a tile bilinearly from its four corners (the rendered
	 * mesh splits it into two triangles), and over water it reads the water surface.
	 */
	public float getLocationZ(final float x, final float y) {
		final float userCellSpaceX = (x - this.centerOffset[0]) / 128.0f;
		final float userCellSpaceY = (y - this.centerOffset[1]) / 128.0f;
		final int cellX = (int) userCellSpaceX;
		final int cellY = (int) userCellSpaceY;
		if ((cellX < 0) || (cellX >= (this.mapSize[0] - 1)) || (cellY < 0) || (cellY >= (this.mapSize[1] - 1))) {
			return 0;
		}
		final float bottomLeft = this.groundCornerHeights[(cellY * this.columns) + cellX];
		final float bottomRight = this.groundCornerHeights[(cellY * this.columns) + cellX + 1];
		final float topLeft = this.groundCornerHeights[((cellY + 1) * this.columns) + cellX];
		final float topRight = this.groundCornerHeights[((cellY + 1) * this.columns) + cellX + 1];
		final float sqX = userCellSpaceX - cellX;
		final float sqY = userCellSpaceY - cellY;
		final float bottom = bottomLeft + ((bottomRight - bottomLeft) * sqX);
		final float top = topLeft + ((topRight - topLeft) * sqX);
		final float ground = (bottom + ((top - bottom) * sqY)) * 128.0f;
		final RenderCorner corner = getCorner(x, y);
		if ((corner != null) && (corner.getWater() != 0)) {
			return Math.max(ground, getWaterHeight(x, y));
		}
		return ground;
	}

	public float getTerrainSpaceX(float x) {
		return (x - this.centerOffset[0]) / 128.0f;
	}

	public int get128CellX(final float x) {
		final float userCellSpaceX = (x - this.centerOffset[0]) / 128.0f;
		final int cellX = (int) userCellSpaceX;
		return cellX;
	}

	public float get128WorldCoordinateFromCellX(final int cellX) {
		return (cellX * 128.0f) + this.centerOffset[0];
	}

	public float getTerrainSpaceY(float y) {
		return (y - this.centerOffset[1]) / 128.0f;
	}

	public int get128CellY(final float y) {
		final float userCellSpaceY = (y - this.centerOffset[1]) / 128.0f;
		final int cellY = (int) userCellSpaceY;
		return cellY;
	}

	public float get128WorldCoordinateFromCellY(final int cellY) {
		return (cellY * 128.0f) + this.centerOffset[1];
	}

	public RenderCorner getCorner(final float x, final float y) {
		final float userCellSpaceX = (x - this.centerOffset[0]) / 128.0f;
		final float userCellSpaceY = (y - this.centerOffset[1]) / 128.0f;
		final int cellX = (int) userCellSpaceX;
		final int cellY = (int) userCellSpaceY;

		if ((cellX >= 0) && (cellX < (this.mapSize[0] - 1)) && (cellY >= 0) && (cellY < (this.mapSize[1] - 1))) {
			return this.corners[cellX][cellY];
		}

		return null;
	}

	public float getWaterHeight(final float x, final float y) {
		final float userCellSpaceX = (x - this.centerOffset[0]) / 128.0f;
		final float userCellSpaceY = (y - this.centerOffset[1]) / 128.0f;
		final int cellX = (int) userCellSpaceX;
		final int cellY = (int) userCellSpaceY;

		if ((cellX >= 0) && (cellX < (this.mapSize[0] - 1)) && (cellY >= 0) && (cellY < (this.mapSize[1] - 1))) {
			final float bottomLeft = this.waterHeights[(cellY * this.columns) + cellX];
			final float bottomRight = this.waterHeights[(cellY * this.columns) + cellX + 1];
			final float topLeft = this.waterHeights[((cellY + 1) * this.columns) + cellX];
			final float topRight = this.waterHeights[((cellY + 1) * this.columns) + cellX + 1];
			final float sqX = userCellSpaceX - cellX;
			final float sqY = userCellSpaceY - cellY;
			float height;

			if ((sqX + sqY) < 1) {
				height = bottomLeft + ((bottomRight - bottomLeft) * sqX) + ((topLeft - bottomLeft) * sqY);
			} else {
				height = topRight + ((bottomRight - topRight) * (1 - sqY)) + ((topLeft - topRight) * (1 - sqX));
			}

			return (height + this.waterHeightOffset) * 128.0f;
		}

		return this.waterHeightOffset * 128.0f;
	}

	public static final class Splat {
		public List<float[]> locations = new ArrayList<>();
		public List<Consumer<SplatMover>> unitMapping = new ArrayList<>();
		public float opacity = 1;
		public boolean aboveWater = false;
	}

	public void loadSplats() throws IOException {
		for (final Map.Entry<String, Splat> entry : this.splats.entrySet()) {
			final String path = entry.getKey();
			final Splat splat = entry.getValue();

			final SplatModel splatModel = new SplatModel(Gdx.gl30,
					(Texture) this.viewer.load(path, PathSolver.DEFAULT, null), splat.locations, this.centerOffset,
					splat.unitMapping.isEmpty() ? null : splat.unitMapping, false, false, false, false);
			splatModel.color[3] = splat.opacity;
			addSplatBatchModel(path, splatModel);
		}
	}

	public void removeSplatBatchModel(final String path) {
		this.uberSplatModelsList.remove(this.uberSplatModels.remove(path));
	}

	public void addSplatBatchModel(final String path, final SplatModel model) {
		this.uberSplatModels.put(path, model);
		this.uberSplatModelsList.add(model);
		Collections.sort(this.uberSplatModelsList);
	}

	public SplatModel getSplatModel(final String pathKey) {
		return this.uberSplatModels.get(pathKey);
	}

	public SplatMover addUberSplat(final String path, final float x, final float y, final float z, final float scale,
			final boolean unshaded, final boolean noDepthTest, final boolean highPriority, final boolean aboveWater) {
		SplatModel splatModel = this.uberSplatModels.get(path);
		if (splatModel == null) {
			splatModel = new SplatModel(Gdx.gl30, (Texture) this.viewer.load(path, PathSolver.DEFAULT, null),
					new ArrayList<>(), this.centerOffset, new ArrayList<>(), unshaded, noDepthTest, highPriority,
					aboveWater);
			addSplatBatchModel(path, splatModel);
		}
		return splatModel.add(x - scale, y - scale, x + scale, y + scale, z, this.centerOffset);
	}

	/** A splat with a batch of its own, so that its colour can change alone (script ubersplats). */
	public SplatModel addSingleUberSplat(final String key, final String texturePath, final float x, final float y,
			final float scale) {
		final SplatModel splatModel = new SplatModel(Gdx.gl30,
				(Texture) this.viewer.load(texturePath, PathSolver.DEFAULT, null), new ArrayList<>(),
				this.centerOffset, null, false, false, false, false);
		splatModel.add(x - scale, y - scale, x + scale, y + scale, 1, this.centerOffset);
		addSplatBatchModel(key, splatModel);
		return splatModel;
	}

	/**
	 * A script image: a texture over a rectangle of the ground, in a batch of its own (see {@link #getSplatModel}).
	 * Batches are drawn from the lowest zDepthUpward up, so it sets where the image is drawn among the others.
	 *
	 * @return what moves it to a constant height
	 */
	public SplatMover addImageSplat(final String key, final String texturePath, final float x, final float y,
			final float x2, final float y2, final boolean aboveWater, final float zDepthUpward) {
		final SplatModel splatModel = new SplatModel(Gdx.gl30,
				(Texture) this.viewer.load(texturePath, PathSolver.DEFAULT, null), new ArrayList<>(),
				this.centerOffset, new ArrayList<>(), false, false, false, aboveWater);
		final SplatMover mover = splatModel.add(x, y, x2, y2, zDepthUpward, this.centerOffset);
		addSplatBatchModel(key, splatModel);
		return mover;
	}

	public void removeSingleUberSplat(final String key, final SplatModel model) {
		removeSplatBatchModel(key);
		model.dispose(this.gl);
	}

	public SplatMover addUnitShadowSplat(final String texture, final float x, final float y, final float x2,
			final float y2, final float zDepthUpward, final float opacity, final boolean aboveWater) {
		SplatModel splatModel = this.uberSplatModels.get(texture);
		if (splatModel == null) {
			splatModel = new SplatModel(Gdx.gl30, (Texture) this.viewer.load(texture, PathSolver.DEFAULT, null),
					new ArrayList<>(), this.centerOffset, new ArrayList<>(), false, false, false, aboveWater);
			splatModel.color[3] = opacity;
			addSplatBatchModel(texture, splatModel);
			this.unitShadowSplatModels.add(splatModel);
		}
		return splatModel.add(x, y, x2, y2, zDepthUpward, this.centerOffset);
	}

	public static final class SoftwareGroundMesh {
		public final float[] vertices;
		public final int[] indices;

		private SoftwareGroundMesh(final float[] groundHeights, final float[] groundCornerHeights,
				final float[] centerOffset, final int columns, final int rows) {
			this.vertices = new float[(columns - 1) * (rows - 1) * Shapes.INSTANCE.quadVertices.length * 3];
			this.indices = new int[(columns - 1) * (rows - 1) * Shapes.INSTANCE.quadIndices.length * 3];
			for (int y = 0; y < (rows - 1); y++) {
				for (int x = 0; x < (columns - 1); x++) {
					final int instanceId = (y * (columns - 1)) + x;
					for (int vertexId = 0; vertexId < Shapes.INSTANCE.quadVertices.length; vertexId++) {
						final float vPositionX = Shapes.INSTANCE.quadVertices[vertexId][0];
						final float vPositionY = Shapes.INSTANCE.quadVertices[vertexId][1];
						final int groundCornerHeightIndex = (int) (((vPositionY + y) * columns) + (vPositionX + x));
						final float height = groundCornerHeights[groundCornerHeightIndex];
						this.vertices[(instanceId * 4 * 3) + (vertexId * 3)] = ((vPositionX + x) * 128f)
								+ centerOffset[0];
						this.vertices[(instanceId * 4 * 3) + (vertexId * 3) + 1] = ((vPositionY + y) * 128f)
								+ centerOffset[1];
						this.vertices[(instanceId * 4 * 3) + (vertexId * 3) + 2] = height * 128f;
					}
					for (int triangle = 0; triangle < Shapes.INSTANCE.quadIndices.length; triangle++) {
						for (int vertexId = 0; vertexId < Shapes.INSTANCE.quadIndices[triangle].length; vertexId++) {
							final int vertexIndex = Shapes.INSTANCE.quadIndices[triangle][vertexId];
							final int indexValue = vertexIndex + (instanceId * 4);
							if ((indexValue * 3) >= this.vertices.length) {
								throw new IllegalStateException();
							}
							this.indices[(instanceId * 2 * 3) + (triangle * 3) + vertexId] = indexValue;
						}
					}
				}
			}
		}
	}

	public static final class SoftwareWaterAndGroundMesh {
		public final float[] vertices;
		public final int[] indices;

		private SoftwareWaterAndGroundMesh(final float waterHeightOffset, final float[] groundCornerHeights,
				final float[] waterHeights, final byte[] waterExistsData, final float[] centerOffset, final int columns,
				final int rows) {
			this.vertices = new float[(columns - 1) * (rows - 1) * Shapes.INSTANCE.quadVertices.length * 3];
			this.indices = new int[(columns - 1) * (rows - 1) * Shapes.INSTANCE.quadIndices.length * 3];
			for (int y = 0; y < (rows - 1); y++) {
				for (int x = 0; x < (columns - 1); x++) {
					final int instanceId = (y * (columns - 1)) + x;
					for (int vertexId = 0; vertexId < Shapes.INSTANCE.quadVertices.length; vertexId++) {
						final float vPositionX = Shapes.INSTANCE.quadVertices[vertexId][0];
						final float vPositionY = Shapes.INSTANCE.quadVertices[vertexId][1];
						final int groundCornerHeightIndex = (int) (((vPositionY + y) * columns) + (vPositionX + x));
						final float height;
						if (waterExistsData[groundCornerHeightIndex] != 0) {
							height = Math.max(groundCornerHeights[groundCornerHeightIndex],
									waterHeights[groundCornerHeightIndex] + waterHeightOffset);
						} else {
							height = groundCornerHeights[groundCornerHeightIndex];
						}
						this.vertices[(instanceId * 4 * 3) + (vertexId * 3)] = ((vPositionX + x) * 128f)
								+ centerOffset[0];
						this.vertices[(instanceId * 4 * 3) + (vertexId * 3) + 1] = ((vPositionY + y) * 128f)
								+ centerOffset[1];
						this.vertices[(instanceId * 4 * 3) + (vertexId * 3) + 2] = height * 128f;
					}
					for (int triangle = 0; triangle < Shapes.INSTANCE.quadIndices.length; triangle++) {
						for (int vertexId = 0; vertexId < Shapes.INSTANCE.quadIndices[triangle].length; vertexId++) {
							final int vertexIndex = Shapes.INSTANCE.quadIndices[triangle][vertexId];
							final int indexValue = vertexIndex + (instanceId * 4);
							if ((indexValue * 3) >= this.vertices.length) {
								throw new IllegalStateException();
							}
							this.indices[(instanceId * 2 * 3) + (triangle * 3) + vertexId] = indexValue;
						}
					}
				}
			}
		}
	}

	public boolean inPlayableArea(float x, float y) {
		x = (x - this.centerOffset[0]) / 128.0f;
		y = (y - this.centerOffset[1]) / 128.0f;
		if (x < this.mapBounds[0]) {
			return false;
		}
		if (x >= (this.mapSize[0] - this.mapBounds[1] - 1)) {
			return false;
		}
		if (y < this.mapBounds[2]) {
			return false;
		}
		if (y >= (this.mapSize[1] - this.mapBounds[3] - 1)) {
			return false;
		} // TODO why do we use floor if we can use int cast?
		return this.corners[(int) Math.floor(x)][(int) Math.floor(y)].getBoundary() == 0;
	}

	public Rectangle getPlayableMapArea() {
		return this.shaderMapBoundsRectangle;
	}

	public Rectangle getEntireMap() {
		return this.entireMapRectangle;
	}

	private void reloadShadowDataToGPU() {
		final int columns = (Terrain.this.columns - 1) * 4;
		final int rows = (Terrain.this.rows - 1) * 4;
		reloadShadowData(Terrain.this.centerOffset, columns, rows);
		uploadShadowData(columns, rows);
	}

	// Building shadows change whenever a building appears, moves or goes away; the map stays allocated and is
	// refilled through one buffer instead of being created again from a new buffer each time.
	private void uploadShadowData(final int columns, final int rows) {
		this.shadowDataBuffer.clear();
		this.shadowDataBuffer.put(this.shadowData);
		this.shadowDataBuffer.clear();
		final GL30 gl = Gdx.gl30;
		gl.glBindTexture(GL30.GL_TEXTURE_2D, this.shadowMap);
		gl.glTexSubImage2D(GL30.GL_TEXTURE_2D, 0, 0, 0, columns, rows, GL30.GL_RED, GL30.GL_UNSIGNED_BYTE,
				this.shadowDataBuffer);
	}

	public float[] getDefaultCameraBounds() {
		return this.defaultCameraBounds;
	}

	public void setFogOfWarData(CFogMaskSettings fogMaskSettings, final CPlayerFogOfWar fogOfWarData) {
		this.fogOfWarData = fogOfWarData;
		this.visualFogData = ByteBuffer.allocateDirect(fogOfWarData.getFogOfWarBuffer().capacity());
		this.visualFogArray = new byte[this.visualFogData.capacity()];
		this.fogStateArray = new byte[this.visualFogData.capacity()];
		this.fogOfWarMapAllocated = false;
		reloadFogOfWarDataToGPU(fogMaskSettings);
	}

	/**
	 * Moves the drawn fog one fade step toward the fog of war and sends it to the GPU.
	 *
	 * @return false when the drawn fog already matched, so nothing was sent
	 */
	public boolean reloadFogOfWarDataToGPU(CFogMaskSettings fogMaskSettings) {
		final GL30 gl = Gdx.gl30;
		final ByteBuffer fogOfWarBuffer = this.fogOfWarData.getFogOfWarBuffer();
		boolean changed = !this.fogOfWarMapAllocated;
		final byte[] mapping = this.fogStateMapping;
		for (int i = 0; i < 256; i++) {
			mapping[i] = this.viewer.getFogShade(fogMaskSettings.getFogStateFromSettings((byte) i));
		}
		final byte[] visual = this.visualFogArray;
		final byte[] states = this.fogStateArray;
		final ByteBuffer source = fogOfWarBuffer.duplicate();
		source.clear();
		source.get(states, 0, Math.min(states.length, source.remaining()));
		for (int i = 0; i < visual.length; i++) {
			final byte previous = visual[i];
			final byte faded = War3MapViewer.fadeLineOfSightColor(previous, mapping[states[i] & 0xFF]);
			if (faded != previous) {
				visual[i] = faded;
				changed = true;
			}
		}
		// The fog fades for a moment after each fog of war update and then stays; sending the whole texture every
		// tick anyway cost phones several milliseconds a frame.
		if (!changed) {
			return false;
		}
		this.visualFogData.clear();
		this.visualFogData.put(visual);
		this.visualFogData.clear();
		gl.glBindTexture(GL30.GL_TEXTURE_2D, Terrain.this.fogOfWarMap);
		if (!this.fogOfWarMapAllocated) {
			gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);
			gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
			gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
			gl.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
			gl.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_R8, this.fogOfWarData.getWidth(),
					this.fogOfWarData.getHeight(), 0, GL30.GL_RED, GL30.GL_UNSIGNED_BYTE, this.visualFogData);
			this.fogOfWarMapAllocated = true;
		}
		else {
			gl.glTexSubImage2D(GL30.GL_TEXTURE_2D, 0, 0, 0, this.fogOfWarData.getWidth(), this.fogOfWarData.getHeight(),
					GL30.GL_RED, GL30.GL_UNSIGNED_BYTE, this.visualFogData);
		}
		return true;
	}

	public int getFogOfWarMap() {
		return this.fogOfWarMap;
	}

	public void setWaterBaseColor(float red, float green, float blue, float alpha) {
		final float[] rgba = { red, green, blue, alpha };
		for (int i = 0; i < 4; i++) {
			this.maxDeepColorApplied[i] = this.maxDeepColor[i] * rgba[i];
			this.minDeepColorApplied[i] = this.minDeepColor[i] * rgba[i];
			this.maxShallowColorApplied[i] = this.maxShallowColor[i] * rgba[i];
			this.minShallowColorApplied[i] = this.minShallowColor[i] * rgba[i];
		}
	}

	private static final Rectangle tempRect = new Rectangle();

	public void updateGroundBuffer(float x, float y, float i) {
		int nodeX = get128CellX(x);
		int nodeY = get128CellY(y);
		final int instanceId = (nodeY * (columns - 1)) + nodeX;
		final int vertexZ = (instanceId * 4 * 3) + (0 * 3) + 2;
		softwareGroundMesh.vertices[vertexZ] = softwareGroundMesh.vertices[vertexZ] + i;
		this.corners[nodeX][nodeY].setGroundHeight(this.corners[nodeX][nodeY].getGroundHeight() + i / 512);
		this.updateGroundHeights(tempRect.set(nodeX, nodeY, 1, 1));
	}

	public int[] getTerrainModBufferSize(float x, float y, float width, float height) {
		return new int[] { Math.min(this.rows - 1, Math.max(0, get128CellX(x))),
				Math.min(this.columns - 1, Math.max(0, get128CellY(y))),
				Math.min(this.rows - 1, Math.max(0, get128CellX(x + width)+1)),
				Math.min(this.columns - 1, Math.max(0, get128CellY(y + height)+1)) };
	}

	public int[] getTerrainModBufferSize(float centerX, float centerY, float radius) {
		return getTerrainModBufferSize(centerX - radius, centerY - radius, radius * 2, radius * 2);
	}

	public void dispose() {
		final GL30 gl = this.gl;
		for (final GroundTexture texture : this.groundTextures) {
			texture.dispose(gl);
		}
		for (final CliffMesh mesh : this.cliffMeshes) {
			mesh.dispose();
		}
		for (final SplatModel model : this.uberSplatModelsList) {
			model.dispose(gl);
		}
		for (final SplatModel model : this.unitShadowSplatModels) {
			model.dispose(gl);
		}
		gl.glDeleteTexture(this.groundTextureData);
		gl.glDeleteTexture(this.groundHeight);
		gl.glDeleteTexture(this.groundCornerHeight);
		gl.glDeleteTexture(this.groundCornerHeightLinear);
		gl.glDeleteTexture(this.cliffTextureArray);
		gl.glDeleteTexture(this.waterHeight);
		gl.glDeleteTexture(this.waterExists);
		gl.glDeleteTexture(this.waterTextureArray);
		gl.glDeleteTexture(this.shadowMap);
		gl.glDeleteTexture(this.fogOfWarMap);
		gl.glDeleteBuffer(this.testArrayBuffer);
		gl.glDeleteBuffer(this.testElementBuffer);
		gl.glDeleteBuffer(this.quadVertexBuffer);
		gl.glDeleteBuffer(this.quadIndexBuffer);
	}

	public void updateGroundBuffer(int[] rect, float[] modBuffer) {
		updateGroundBuffer(rect[0], rect[1], rect[2]-rect[0] + 1, rect[3]-rect[1] + 1, modBuffer);
	}

	public void updateGroundBuffer(int x, int y, int width, int height, float[] modBuffer) {
		for (int j = (int) y; j < (y + height); j++) {
			for (int i = (int) x; i < (x + width); i++) {
				final int vertexZ = (((j * (columns - 1)) + i) * 4 * 3) + 2;
				softwareGroundMesh.vertices[vertexZ] = softwareGroundMesh.vertices[vertexZ]
						+ modBuffer[(i - x) + (j - y) * width];
				this.corners[i][j].setGroundHeight(
						this.corners[i][j].getGroundHeight() + modBuffer[(i - x) + (j - y) * width] / 512);
			}
		}
		this.updateGroundHeights(tempRect.set(x, y, width, height));

	}
}
