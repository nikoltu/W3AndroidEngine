package com.etheller.warsmash.viewer5.handlers.w3x.rendersim;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.datasources.AssetPathFallback;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.units.ObjectData;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.MdxAssetLoader;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSoundset;

public class RenderUnitTypeData extends RenderWidgetTypeData<RenderUnitType> {
	private static final String UNIT_FILE = "file"; // replaced from 'umdl'
	private static final String[] CLASSIC_MODEL_QUALITIES = { "sd", "de" };
	private static final String UNIT_PATHING = "pathTex"; // replaced from 'upat'
	private static final String UNIT_SPECIAL = "Specialart"; // replaced from 'uspa'
	private static final String UNIT_SHADOW = "unitShadow"; // replaced from 'ushu'
	private static final String UNIT_SHADOW_X = "shadowX"; // replaced from 'ushx'
	private static final String UNIT_SHADOW_Y = "shadowY"; // replaced from 'ushy'
	private static final String UNIT_SHADOW_W = "shadowW"; // replaced from 'ushw'
	private static final String UNIT_SHADOW_H = "shadowH"; // replaced from 'ushh'
	private static final String BUILDING_SHADOW = "buildingShadow"; // replaced from 'ushb'
	public static final String UNIT_SELECT_SCALE = "scale"; // replaced from 'ussc'
	private static final String UNIT_SOUNDSET = "unitSound"; // replaced from 'usnd'
	private static final String UBER_SPLAT = "uberSplat"; // replaced from 'uubs'
	private static final String ELEVATION_SAMPLE_RADIUS = "elevRad"; // replaced from 'uerd'
	private static final String MAX_PITCH = "maxPitch"; // replaced from 'umxp'
	private static final String ALLOW_CUSTOM_TEAM_COLOR = "customTeamColor"; // replaced from 'utcc'
	private static final String TEAM_COLOR = "teamColor"; // replaced from 'utco'
	private static final String MAX_ROLL = "maxRoll"; // replaced from 'umxr'
	private static final String ANIMATION_RUN_SPEED = "run"; // replaced from 'urun'
	private static final String ANIMATION_WALK_SPEED = "walk"; // replaced from 'uwal'
	private static final String MODEL_SCALE = "modelScale"; // replaced from 'usca'
	private static final String RED = "red"; // replaced from 'uclr'
	private static final String GREEN = "green"; // replaced from 'uclg'
	private static final String BLUE = "blue"; // replaced from 'uclb'
	private static final String ORIENTATION_INTERPOLATION = "orientInterp"; // replaced from 'uori'
	public static final String ANIM_PROPS = "animProps"; // replaced from 'uani'
	public static final String ATTACHMENT_ANIM_PROPS = "Attachmentanimprops"; // replaced from 'uaap'
	private static final String BLEND_TIME = "blend"; // replaced from 'uble'
	private static final String UNIT_SELECT_HEIGHT = "selZ"; // replaced from 'uslz'

	/** Set (-D or an automated run's extra) to keep every model, as before models were let go. */
	public static final String KEEP_MODELS_PROPERTY = "warsmash.keepmodels";
	/** How many types with an idle model keep it, and for how long a model must have been idle to be let go. */
	private static final int KEEP_IDLE_TYPES = 24;
	private static final long MIN_IDLE_NANOS = 45_000_000_000L;
	private static final int TIGHT_KEEP_IDLE_TYPES = 4;
	private static final long TIGHT_MIN_IDLE_NANOS = 10_000_000_000L;
	private static final float TIGHT_HEAP_SHARE = 0.6f;

	private final DataTable unitAckSoundsTable;
	private final DataTable uberSplatTable;
	private final Map<String, UnitSoundset> soundsetNameToSoundset = new HashMap<>();

	public RenderUnitTypeData(ObjectData unitObjectData, DataSource dataSource, MdxAssetLoader mapViewer,
			DataTable unitAckSoundsTable, DataTable uberSplatTable) {
		super(unitObjectData, dataSource, mapViewer);
		this.unitAckSoundsTable = unitAckSoundsTable;
		this.uberSplatTable = uberSplatTable;
	}

	/**
	 * Asks the data for the models of every unit type on a helper thread, as the first unit of a type does when it
	 * comes into play: the data keeps what it was asked, and a first unit of a type in the middle of a game, of which
	 * a script may make a hundred at once, otherwise looked each model up through all of its sources and packages in
	 * the frame it was made in. Only the data source is used there, never the types' own data.
	 */
	public void warmModelLookups() {
		final List<String> basePaths = new ArrayList<>();
		final List<Integer> versionFlags = new ArrayList<>();
		for (final String id : this.objectData.keySet()) {
			final GameObject row = this.objectData.get(id);
			if (row != null) {
				basePaths.add(modelBasePath(row));
				versionFlags.add(row.readSLKTagInt("fileVerFlags"));
			}
		}
		final Thread warmer = new Thread(() -> {
			try {
				for (int i = 0; i < basePaths.size(); i++) {
					final String path = versionedModelPath(basePaths.get(i), versionFlags.get(i));
					if (!hasModel(path)) {
						AssetPathFallback.find(this.dataSource, path);
					}
					this.dataSource.has(path.substring(0, path.length() - 4) + "_portrait.mdx");
				}
			}
			catch (final RuntimeException e) {
				// The first unit of a type asks again and fails there as it would have anyway.
				Log.error(e);
			}
		}, "Model lookups");
		warmer.setDaemon(true);
		warmer.setPriority(Thread.MIN_PRIORITY);
		warmer.start();
	}

	/**
	 * Lets go of the models of unit types that no unit or picture shows any more, the ones unused longest first, once
	 * more of them are kept than {@link #KEEP_IDLE_TYPES} (fewer while the memory is short): a long game of many unit
	 * types otherwise holds the animation data of all of them. A type's model is read again when a unit of it comes.
	 *
	 * @return the number of models let go
	 */
	public int evictIdleModels(final ModelViewer viewer, final long nowNanos) {
		if (Boolean.getBoolean(KEEP_MODELS_PROPERTY)) {
			return 0;
		}
		final boolean tight = isMemoryTight();
		final long minIdle = tight ? TIGHT_MIN_IDLE_NANOS : MIN_IDLE_NANOS;
		final int keep = tight ? TIGHT_KEEP_IDLE_TYPES : KEEP_IDLE_TYPES;
		List<RenderUnitType> idle = null;
		for (final RenderUnitType type : allTypes()) {
			if (type.isModelIdle()) {
				if (type.getModelIdleSinceNanos() == 0) {
					type.setModelIdleSinceNanos(nowNanos);
				}
				else if ((nowNanos - type.getModelIdleSinceNanos()) >= minIdle) {
					if (idle == null) {
						idle = new ArrayList<>();
					}
					idle.add(type);
				}
			}
			else {
				type.setModelIdleSinceNanos(0);
			}
		}
		if ((idle == null) || (idle.size() <= keep)) {
			return 0;
		}
		// the idle types that stay are the ones idle for the shortest time
		idle.sort((a, b) -> Long.compare(a.getModelIdleSinceNanos(), b.getModelIdleSinceNanos()));
		int released = 0;
		for (int i = 0; i < (idle.size() - keep); i++) {
			final MdxModel model = idle.get(i).releaseModel();
			if (model != null) {
				viewer.evictModel(model);
				released++;
			}
		}
		return released;
	}

	private static boolean isMemoryTight() {
		final Runtime runtime = Runtime.getRuntime();
		return (runtime.totalMemory() - runtime.freeMemory()) > (runtime.maxMemory() * TIGHT_HEAP_SHARE);
	}

	private static String modelBasePath(final GameObject row) {
		String path = row.getFieldAsString(UNIT_FILE, 0);
		if (path.toLowerCase(Locale.ROOT).endsWith(".mdl") || path.toLowerCase(Locale.ROOT).endsWith(".mdx")) {
			path = path.substring(0, path.length() - 4);
		}
		return path;
	}

	private String versionedModelPath(String path, final int fileVerFlags) {
		if ((fileVerFlags == 2) && this.dataSource.has(path + "_V1.mdx")) {
			path += "_V1";
		}
		else if ((fileVerFlags == 1) && this.dataSource.has(path + "_V0.mdx")) {
			path += "_V0";
		}
		return path + ".mdx";
	}

	private String getUnitModelPath(final GameObject row) {
		String path = versionedModelPath(modelBasePath(row), row.readSLKTagInt("fileVerFlags"));
		if (!hasModel(path)) {
			final String fallback = AssetPathFallback.find(this.dataSource, path);
			if (fallback != null) {
				return fallback;
			}
			// Reforged maps give some units models in their skin data that Classic data do not have (H08 Emissary:
			// the Chaplain); without the HD packages the unit's own Classic model stands in.
			for (final String quality : CLASSIC_MODEL_QUALITIES) {
				String classicPath = row.getFieldAsString(UNIT_FILE + ":" + quality, 0);
				if ((classicPath == null) || classicPath.isEmpty()) {
					continue;
				}
				if (classicPath.toLowerCase(Locale.ROOT).endsWith(".mdl") || classicPath.toLowerCase(Locale.ROOT).endsWith(".mdx")) {
					classicPath = classicPath.substring(0, classicPath.length() - 4);
				}
				classicPath += ".mdx";
				if (hasModel(classicPath)) {
					return classicPath;
				}
			}
		}
		return path;
	}

	private boolean hasModel(final String mdxPath) {
		return this.dataSource.has(mdxPath)
				|| this.dataSource.has(mdxPath.substring(0, mdxPath.length() - 4) + ".mdl");
	}

	// The simulation asks for every unit type's pathing map as soon as it knows the type (a shop knows every unit it
	// sells), and making the whole render type for that loaded its models: seconds of a map's start on phones.
	public ArgbImage getBuildingPathingPixelMap(final War3ID key) {
		final GameObject row = this.objectData.get(key);
		return row == null ? null : getBuildingPathingPixelMap(row);
	}

	/** Whether a colour chosen for a placed unit of the type is used; read from the data, without making the render type. */
	public boolean isAllowCustomTeamColor(final War3ID key) {
		final GameObject row = this.objectData.get(key);
		return (row != null) && row.getFieldAsBoolean(ALLOW_CUSTOM_TEAM_COLOR, 0);
	}

	private ArgbImage getBuildingPathingPixelMap(final GameObject row) {
		final String pathingTexture = row.getFieldAsString(UNIT_PATHING, 0);
		final ArgbImage buildingPathingPixelMap = this.mapViewer.loadPathingTexture(pathingTexture);
		return buildingPathingPixelMap;
	}

	/**
	 * The unit type's answers, without loading its models as its render type would.
	 *
	 * @return null for a type the data does not have
	 */
	public UnitSoundset getSoundset(final War3ID key) {
		final GameObject row = this.objectData.get(key);
		return row == null ? null : getSoundset(row);
	}

	private UnitSoundset getSoundset(final GameObject row) {
		final String soundName = row.getFieldAsString(UNIT_SOUNDSET, 0);
		UnitSoundset unitSoundset = this.soundsetNameToSoundset.get(soundName);
		if (unitSoundset == null) {
			unitSoundset = new UnitSoundset(this.dataSource, this.unitAckSoundsTable, soundName);
			this.soundsetNameToSoundset.put(soundName, unitSoundset);
		}
		return unitSoundset;
	}

	/**
	 * While the game goes on, the models of a type first needed are read on a helper thread and its units wait for
	 * them (see {@link com.etheller.warsmash.viewer5.ModelViewer#setDeferModelLoads}).
	 */
	@Override
	protected final RenderUnitType createTypeData(final War3ID key, final GameObject row) {
		final boolean deferBefore = this.mapViewer.setDeferModelLoadsInGame(true);
		try {
			return createTypeDataNow(row);
		}
		finally {
			this.mapViewer.setDeferModelLoadsInGame(deferBefore);
		}
	}

	private MdxModel loadPortraitModel(final String path, final Supplier<MdxModel> model) {
		final boolean deferBefore = this.mapViewer.setDeferModelLoadsInGame(true);
		try {
			return getPortraitModel(path, model);
		}
		finally {
			this.mapViewer.setDeferModelLoadsInGame(deferBefore);
		}
	}

	/** The model of a type that was let go, read on a helper thread as the first unit of a type is. */
	private MdxModel loadModelAgain(final String path) {
		final boolean deferBefore = this.mapViewer.setDeferModelLoadsInGame(true);
		try {
			final MdxModel model = this.mapViewer.loadModelMdx(path);
			return model != null ? model : this.mapViewer.emptyModel();
		}
		finally {
			this.mapViewer.setDeferModelLoadsInGame(deferBefore);
		}
	}

	private RenderUnitType createTypeDataNow(final GameObject row) {
		final String path = getUnitModelPath(row);
		final String unitSpecialArtPath = row.getFieldAsString(UNIT_SPECIAL, 0);
		MdxModel specialArtModel;
		if ((unitSpecialArtPath != null) && !unitSpecialArtPath.isEmpty()) {
			try {
				specialArtModel = this.mapViewer.loadModelMdx(unitSpecialArtPath);
			}
			catch (final Exception exc) {
				Log.error(exc);
				specialArtModel = null;
			}
		}
		else {
			specialArtModel = null;
		}
		MdxModel model = this.mapViewer.loadModelMdx(path);
		if (model == null) {
			// A unit type without a model file (map dummies and casters) is a unit all the same, just not seen.
			model = this.mapViewer.emptyModel();
		}

		final ArgbImage buildingPathingPixelMap = getBuildingPathingPixelMap(row);

		final String unitShadow = row.getFieldAsString(UNIT_SHADOW, 0);
		RenderShadowType renderShadowType = null;
		if ((unitShadow != null) && !"_".equals(unitShadow)) {
			final float shadowX = row.getFieldAsFloat(UNIT_SHADOW_X, 0);
			final float shadowY = row.getFieldAsFloat(UNIT_SHADOW_Y, 0);
			final float shadowWidth = row.getFieldAsFloat(UNIT_SHADOW_W, 0);
			final float shadowHeight = row.getFieldAsFloat(UNIT_SHADOW_H, 0);
			final String shadowTexture = findShadowTexture(unitShadow);
			if (shadowTexture != null) {
				renderShadowType = new RenderShadowType(shadowTexture, shadowX, shadowY, shadowWidth, shadowHeight);
			}
		}

		final UnitSoundset soundset = getSoundset(row);
		final String uberSplat = row.getFieldAsString(UBER_SPLAT, 0);
		String uberSplatTexturePath = null;
		float uberSplatScaleValue = 0.0f;
		if (uberSplat != null) {
			final Element uberSplatInfo = this.uberSplatTable.get(uberSplat);
			if (uberSplatInfo != null) {
				uberSplatTexturePath = uberSplatInfo.getField("Dir") + "\\" + uberSplatInfo.getField("file") + ".blp";
				uberSplatScaleValue = uberSplatInfo.getFieldFloatValue("Scale");
			}
		}
		String buildingShadow = row.getFieldAsString(BUILDING_SHADOW, 0);
		if ((buildingShadow == null) || buildingShadow.isEmpty() || "_".equals(buildingShadow)
				|| "none".equalsIgnoreCase(buildingShadow)) {
			buildingShadow = null;
		}
		final String requiredAnimationNamesForAttachmentsString = row.getField(ATTACHMENT_ANIM_PROPS);
		final EnumSet<SecondaryTag> requiredAnimationNamesForAttachments = parseSecondaryTags(
				requiredAnimationNamesForAttachmentsString);
		final String requireAnimationNamesString = row.getField(ANIM_PROPS);
		final EnumSet<SecondaryTag> requiredAnimationNames = parseSecondaryTags(requireAnimationNamesString);

		final float red = row.getFieldAsInteger(RED, 0) / 255f;
		final float green = row.getFieldAsInteger(GREEN, 0) / 255f;
		final float blue = row.getFieldAsInteger(BLUE, 0) / 255f;

		final float selectScale = row.getFieldAsFloat(RenderUnitTypeData.UNIT_SELECT_SCALE, 0);
		final float selectHeight = row.getFieldAsFloat(UNIT_SELECT_HEIGHT, 0);

		final int orientationInterpolation = row.getFieldAsInteger(ORIENTATION_INTERPOLATION, 0);

		final float blendTime = row.getFieldAsFloat(BLEND_TIME, 0);

		final RenderUnitType[] made = new RenderUnitType[1];
		made[0] = new RenderUnitType(model, () -> loadModelAgain(path),
				() -> loadPortraitModel(path, () -> made[0].getModel()), specialArtModel,
				buildingPathingPixelMap, row.getFieldAsFloat(MAX_PITCH, 0), row.getFieldAsFloat(MAX_ROLL, 0),
				row.getFieldAsFloat(ELEVATION_SAMPLE_RADIUS, 0), row.getFieldAsBoolean(ALLOW_CUSTOM_TEAM_COLOR, 0),
				row.getFieldAsInteger(TEAM_COLOR, 0), row.getFieldAsFloat(ANIMATION_RUN_SPEED, 0),
				row.getFieldAsFloat(ANIMATION_WALK_SPEED, 0), row.getFieldAsFloat(MODEL_SCALE, 0), buildingShadow,
				uberSplatTexturePath, uberSplatScaleValue, requiredAnimationNamesForAttachments, requiredAnimationNames,
				renderShadowType, soundset, new Vector3(red, green, blue), selectScale, selectHeight,
				orientationInterpolation, blendTime);
		return made[0];
	}

	private EnumSet<SecondaryTag> parseSecondaryTags(final String requiredAnimationNamesForAttachmentsString) {
		final EnumSet<SecondaryTag> requiredAnimationNamesForAttachments = EnumSet.noneOf(SecondaryTag.class);
		TokenLoop: for (final String animationName : requiredAnimationNamesForAttachmentsString.split(",")) {
			final String upperCaseToken = animationName.toUpperCase(Locale.ROOT);
			for (final SecondaryTag secondaryTag : SecondaryTag.VALUES) {
				if (upperCaseToken.equals(secondaryTag.name())) {
					requiredAnimationNamesForAttachments.add(secondaryTag);
					continue TokenLoop;
				}
			}
		}
		return requiredAnimationNamesForAttachments;
	}
}
