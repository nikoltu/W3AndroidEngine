package com.etheller.warsmash.viewer5.handlers.w3x.rendersim;

import com.etheller.warsmash.units.Element;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.MdlxParticleEmitter2;
import com.hiveworkshop.rms.parsers.mdlx.MdlxParticleEmitter2.FilterMode;
import com.hiveworkshop.rms.parsers.mdlx.MdlxParticleEmitter2.HeadOrTail;
import com.hiveworkshop.rms.parsers.mdlx.MdlxSequence;
import com.hiveworkshop.rms.parsers.mdlx.MdlxTexture;

/**
 * One row of TerrainArt\Weather.slk. Its columns are the fields of a model particle emitter, so each weather effect
 * is drawn by a model made in code with one such emitter.
 */
public final class WeatherType {
	private static final float BOUNDS = 100000;
	private static final int UNFOGGED = 0x40000;
	// Calibration: the original draws a streak (rain) wider and longer than the table's size and tail length give;
	// the factors make the streaks match the original's pictures.
	private static final float TAIL_WIDTH_SCALE = 4;
	private static final float TAIL_LENGTH_SCALE = 1.7f;

	private final String key;
	private final String texturePath;
	private final boolean additive;
	private final boolean fogged;
	private final float height;
	private final float tiltX;
	private final float tiltY;
	private final float emissionRate;
	private final float lifeSpan;
	private final int maxParticles;
	private final float speed;
	private final float gravity;
	private final float variation;
	private final int rows;
	private final int columns;
	private final HeadOrTail headOrTail;
	private final float tailLength;
	private final float latitude;
	private final float timeMiddle;
	private final float[][] colors = new float[3][3];
	private final short[] alphas = new short[3];
	private final float[] scaling = new float[3];
	private final long[] headUv = new long[3];
	private final long[] tailUv = new long[3];
	private final String ambientSound;

	public WeatherType(final String key, final Element row) {
		this.key = key;
		this.texturePath = row.getField("texDir") + "\\" + row.getField("texFile") + ".blp";
		this.additive = row.getFieldValue("alphaMode") == 1;
		this.fogged = row.getFieldValue("useFog") == 1;
		this.height = row.getFieldFloatValue("height");
		this.tiltX = row.getFieldFloatValue("angx");
		this.tiltY = row.getFieldFloatValue("angy");
		this.emissionRate = row.getFieldFloatValue("emrate");
		this.lifeSpan = row.getFieldFloatValue("lifespan");
		this.maxParticles = row.getFieldValue("particles");
		this.speed = row.getFieldFloatValue("veloc");
		this.gravity = row.getFieldFloatValue("accel");
		this.variation = row.getFieldFloatValue("var");
		this.rows = Math.max(1, row.getFieldValue("texr"));
		this.columns = Math.max(1, row.getFieldValue("texc"));
		final boolean head = row.getFieldValue("head") == 1;
		final boolean tail = row.getFieldValue("tail") == 1;
		this.headOrTail = head && tail ? HeadOrTail.BOTH : (tail ? HeadOrTail.TAIL : HeadOrTail.HEAD);
		this.tailLength = row.getFieldFloatValue("taillen");
		this.latitude = row.getFieldFloatValue("lati");
		this.timeMiddle = row.getFieldFloatValue("midTime");
		final String[] stages = { "Start", "Mid", "End" };
		for (int i = 0; i < 3; i++) {
			this.colors[i][0] = row.getFieldFloatValue("red" + stages[i]) / 255f;
			this.colors[i][1] = row.getFieldFloatValue("green" + stages[i]) / 255f;
			this.colors[i][2] = row.getFieldFloatValue("blue" + stages[i]) / 255f;
			this.alphas[i] = (short) row.getFieldValue("alpha" + stages[i]);
			this.scaling[i] = row.getFieldFloatValue("scale" + stages[i]);
			this.headUv[i] = row.getFieldValue("hUV" + stages[i]);
			this.tailUv[i] = row.getFieldValue("tUV" + stages[i]);
		}
		final String sound = row.getField("AmbientSound");
		this.ambientSound = (sound == null) || sound.isEmpty() || "-".equals(sound) ? null : sound;
	}

	public MdlxModel buildModel() {
		final MdlxModel model = new MdlxModel();
		model.name = "Weather" + this.key;
		model.extent.min = new float[] { -BOUNDS, -BOUNDS, -BOUNDS };
		model.extent.max = new float[] { BOUNDS, BOUNDS, BOUNDS };
		model.extent.boundsRadius = BOUNDS;
		final MdlxSequence stand = new MdlxSequence();
		stand.name = "Stand";
		stand.interval = new long[] { 0, 10000 };
		stand.extent = model.extent;
		model.sequences.add(stand);
		final MdlxTexture texture = new MdlxTexture();
		texture.path = this.texturePath;
		model.textures.add(texture);

		final MdlxParticleEmitter2 emitter = new MdlxParticleEmitter2();
		emitter.name = "Weather";
		emitter.objectId = 0;
		emitter.setSpeed(this.speed);
		emitter.setVariation(this.variation);
		emitter.setLatitude(this.latitude);
		emitter.setGravity(this.gravity);
		emitter.setLifeSpan(this.lifeSpan);
		emitter.setFilterMode(this.additive ? FilterMode.ADDITIVE : FilterMode.BLEND);
		emitter.setRows(this.rows);
		emitter.setColumns(this.columns);
		emitter.setHeadOrTail(this.headOrTail);
		emitter.setTailLength(this.tailLength * (this.headOrTail == HeadOrTail.TAIL ? TAIL_LENGTH_SCALE : 1));
		emitter.setTimeMiddle(this.timeMiddle);
		for (int i = 0; i < 3; i++) {
			System.arraycopy(this.colors[i], 0, emitter.getSegmentColors()[i], 0, 3);
		}
		emitter.setSegmentAlphas(this.alphas.clone());
		final float[] sizes = this.scaling.clone();
		if (this.headOrTail == HeadOrTail.TAIL) {
			for (int i = 0; i < 3; i++) {
				sizes[i] *= TAIL_WIDTH_SCALE;
			}
		}
		emitter.setSegmentScaling(sizes);
		emitter.setHeadIntervals(new long[][] { { this.headUv[0], this.headUv[1], 1 }, { this.headUv[1], this.headUv[2], 1 } });
		emitter.setTailIntervals(new long[][] { { this.tailUv[0], this.tailUv[1], 1 }, { this.tailUv[1], this.tailUv[2], 1 } });
		emitter.setTextureId(0);
		if (!this.fogged) {
			emitter.setFlags(emitter.getFlags() | UNFOGGED);
		}
		model.particleEmitters2.add(emitter);
		model.pivotPoints.add(new float[] { 0, 0, 0 });
		return model;
	}

	public String getKey() {
		return this.key;
	}

	public float getHeight() {
		return this.height;
	}

	public float getTiltX() {
		return this.tiltX;
	}

	public float getTiltY() {
		return this.tiltY;
	}

	public float getEmissionRate() {
		return this.emissionRate;
	}

	/**
	 * The emission rate at which the table's particle count is alive at once.
	 */
	public float getMaxEmissionRate() {
		return this.lifeSpan > 0 ? this.maxParticles / this.lifeSpan : this.emissionRate;
	}

	public String getAmbientSound() {
		return this.ambientSound;
	}
}
