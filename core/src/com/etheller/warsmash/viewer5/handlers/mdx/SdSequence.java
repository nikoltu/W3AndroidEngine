package com.etheller.warsmash.viewer5.handlers.mdx;

import java.util.ArrayList;
import java.util.Arrays;

import com.etheller.warsmash.util.ParseUtils;
import com.hiveworkshop.rms.parsers.mdlx.AnimationMap;
import com.hiveworkshop.rms.parsers.mdlx.timeline.MdlxTimeline;

public final class SdSequence<TYPE> {
	private static final boolean INJECT_FRAMES_GHOSTWOLF_STYLE = false;

	private final Sd<TYPE> sd;
	public final long start; // UInt32
	public final long end; // UInt32
	// The keys of HD models fill most of a phone's heap. Float keys are kept in flat arrays: those of the whole
	// timeline, shared by its sequences, where the sequence's keys are a run of them (first, count); otherwise the
	// sequence's own. A constant sequence keeps only its first key.
	private int[] frames;
	private int first;
	private int count;
	private TYPE[] values;
	private TYPE[] inTans;
	private TYPE[] outTans;
	private int stride;
	private float[] packedValues;
	private float[] packedInTans;
	private float[] packedOutTans;
	public boolean constant;
	// Keys taken out of the flat arrays for the interpolation; like the interpolation's own heaps, only for the
	// rendering thread. One set for each key length.
	private static final float[][][] KEY_HEAP = new float[5][][];
	private static final float[][] VALUES_HEAP = new float[2][];
	private static final float[][] IN_TANS_HEAP = new float[2][];
	private static final float[][] OUT_TANS_HEAP = new float[2][];
	private static final float[][] NO_TANS = new float[0][];

	static {
		for (int i = 1; i < KEY_HEAP.length; i++) {
			KEY_HEAP[i] = new float[][] { new float[i], new float[i], new float[i], new float[i] };
		}
	}

	public SdSequence(final Sd<TYPE> sd, final long start, final long end, final MdlxTimeline<TYPE> timeline,
			final boolean isGlobalSequence, final SdArrayDescriptor<TYPE> arrayDescriptor, final SharedKeys shared) {
		this.sd = sd;
		this.start = start;
		this.end = end;
		final ArrayList<Long> framesBuilder = new ArrayList<>();
		final ArrayList<TYPE> valuesBuilder = new ArrayList<>();
		final ArrayList<TYPE> inTansBuilder = new ArrayList<>();
		final ArrayList<TYPE> outTansBuilder = new ArrayList<>();
		this.constant = false;

		final int interpolationType = sd.interpolationType;
		final long[] frames = timeline.getFrames();
		final TYPE[] values = getValues(timeline);
		final TYPE[] inTans = getInTans(timeline);
		final TYPE[] outTans = getOutTans(timeline);
		final TYPE defval = sd.defval;

		// When using a global sequence, where the first key is outside of the
		// sequence's length, it becomes its constant value.
		// When having one key in the sequence's range, and one key outside of
		// it, results seem to be non-deterministic.
		// Sometimes the second key is used too, sometimes not.
		// It also differs depending where the model is viewed - the WE
		// previewer, the WE itself, or the game.
		// All three show different results, none of them make sense.
		// Therefore, only handle the case where the first key is outside.
		// This fixes problems spread over many models, e.g. HeroMountainKing
		// (compare in WE and in Magos).
		final boolean outsideKeyAdded = isGlobalSequence && (frames.length > 0) && (frames[0] > end);
		int firstInRange = -1;
		int lastInRange = -1;
		if (outsideKeyAdded) {
			if (start == end) {
				framesBuilder.add(start);
			}
			else {
				framesBuilder.add(frames[0]);
			}
			valuesBuilder.add(values[0]);
		}

		// Go over the keyframes, and add all of the ones that are in this
		// sequence (start <= frame <= end).
		for (int i = 0, l = frames.length; i < l; i++) {
			final long frame = frames[i];

			if ((frame >= start) && (frame <= end)) {
				if (firstInRange < 0) {
					firstInRange = i;
				}
				lastInRange = i;
				framesBuilder.add(frame);
				valuesBuilder.add(values[i]);

				if (interpolationType > 1) {
					inTansBuilder.add(inTans[i]);
					outTansBuilder.add(outTans[i]);
				}
			}
		}

		final int keyframeCount = framesBuilder.size();

		if (keyframeCount == 0) {
			// if there are no keys, use the default value directly.
			this.constant = true;
			framesBuilder.add(start);
			valuesBuilder.add(defval);
		}
		else if (keyframeCount == 1) {
			// If there's only one key, use it directly
			this.constant = true;
		}
		else {
			final TYPE firstValue = valuesBuilder.get(0);

			// If all of the values in this sequence are the same, might as well
			// make it constant.
			boolean allFramesMatch = true;
			for (final TYPE value : valuesBuilder) {
				if (!equals(firstValue, value)) {
					allFramesMatch = false;
				}
			}
			this.constant = allFramesMatch;

			if (!this.constant && INJECT_FRAMES_GHOSTWOLF_STYLE) {
				// If there is no opening keyframe for this sequence, inject one
				// with the default value.
				final boolean hasStart = framesBuilder.get(0) == start;
				if (!hasStart) {
					framesBuilder.add(start);
					valuesBuilder.add(defval);

					if (interpolationType > 1) {
						inTansBuilder.add(defval);
						outTansBuilder.add(defval);
					}
				}

				// If there is no closing keyframe for this sequence, inject one
				// with the default value.
				if (framesBuilder.get(framesBuilder.size() - 1) != end) {
					framesBuilder.add(end);
					final int sourceIndex = hasStart ? 0 : (valuesBuilder.size() - 1);
					valuesBuilder.add(valuesBuilder.get(sourceIndex));

					if (interpolationType > 1) {
						inTansBuilder.add(inTansBuilder.get(sourceIndex));
						outTansBuilder.add(outTansBuilder.get(sourceIndex));
					}
				}
			}
		}
		final int keys = this.constant ? 1 : framesBuilder.size();
		final int tans = this.constant ? 0 : inTansBuilder.size();
		this.count = keys;
		if ((shared != null) && !outsideKeyAdded && (firstInRange >= 0)
				&& ((lastInRange - firstInRange + 1) == framesBuilder.size())
				&& ((tans == 0) || ((tans == keys) && (shared.inTans != null)))) {
			this.frames = shared.frames;
			this.first = firstInRange;
			this.stride = shared.stride;
			this.packedValues = shared.values;
			this.packedInTans = tans == 0 ? null : shared.inTans;
			this.packedOutTans = tans == 0 ? null : shared.outTans;
			return;
		}
		this.frames = new int[keys];
		for (int i = 0; i < keys; i++) {
			this.frames[i] = framesBuilder.get(i).intValue();
		}
		final int stride = packedStride(valuesBuilder, keys, inTansBuilder, outTansBuilder, tans);
		this.stride = stride;
		if (stride > 0) {
			this.packedValues = pack(valuesBuilder, keys, stride);
			this.packedInTans = tans == 0 ? null : pack(inTansBuilder, tans, stride);
			this.packedOutTans = tans == 0 ? null : pack(outTansBuilder, tans, stride);
		}
		else {
			this.values = valuesBuilder.subList(0, keys).toArray(arrayDescriptor.create(keys));
			this.inTans = inTansBuilder.subList(0, tans).toArray(arrayDescriptor.create(tans));
			this.outTans = outTansBuilder.subList(0, tans).toArray(arrayDescriptor.create(tans));
		}
	}

	/** The float keys of one timeline in flat arrays, or null when they are not float keys of one length. */
	static SharedKeys sharedKeys(final MdlxTimeline<?> timeline) {
		final long[] frames = timeline.getFrames();
		final Object[] values = fixTimelineArray(timeline, timeline.getValues());
		final Object[] inTans = fixTimelineArray(timeline, timeline.getInTans());
		final Object[] outTans = fixTimelineArray(timeline, timeline.getOutTans());
		final int keys = frames.length;
		if ((keys == 0) || (values == null) || (values.length != keys)) {
			return null;
		}
		final boolean hasTans = (inTans != null) && (outTans != null) && (inTans.length == keys)
				&& (outTans.length == keys);
		final ArrayList<Object> valueList = new ArrayList<>(Arrays.asList(values));
		final ArrayList<Object> inList = hasTans ? new ArrayList<>(Arrays.asList(inTans)) : new ArrayList<>();
		final ArrayList<Object> outList = hasTans ? new ArrayList<>(Arrays.asList(outTans)) : new ArrayList<>();
		final int stride = packedStride(valueList, keys, inList, outList, hasTans ? keys : 0);
		if (stride == 0) {
			return null;
		}
		final int[] intFrames = new int[keys];
		for (int i = 0; i < keys; i++) {
			intFrames[i] = (int) frames[i];
		}
		return new SharedKeys(intFrames, pack(valueList, keys, stride), hasTans ? pack(inList, keys, stride) : null,
				hasTans ? pack(outList, keys, stride) : null, stride);
	}

	static final class SharedKeys {
		private final int[] frames;
		private final float[] values;
		private final float[] inTans;
		private final float[] outTans;
		private final int stride;

		private SharedKeys(final int[] frames, final float[] values, final float[] inTans, final float[] outTans,
				final int stride) {
			this.frames = frames;
			this.values = values;
			this.inTans = inTans;
			this.outTans = outTans;
			this.stride = stride;
		}
	}

	/** The length of every key when all are float arrays of one length the flat form handles, else 0. */
	private static int packedStride(final ArrayList<?> values, final int keys, final ArrayList<?> inTans,
			final ArrayList<?> outTans, final int tans) {
		if (!(values.get(0) instanceof float[]) || ((tans != 0) && (tans != keys))) {
			return 0;
		}
		final int stride = ((float[]) values.get(0)).length;
		if ((stride < 1) || (stride >= KEY_HEAP.length)) {
			return 0;
		}
		for (int i = 0; i < keys; i++) {
			if (((float[]) values.get(i)).length != stride) {
				return 0;
			}
		}
		for (int i = 0; i < tans; i++) {
			if ((((float[]) inTans.get(i)).length != stride) || (((float[]) outTans.get(i)).length != stride)) {
				return 0;
			}
		}
		return stride;
	}

	private static float[] pack(final ArrayList<?> keys, final int count, final int stride) {
		final float[] packed = new float[count * stride];
		for (int i = 0; i < count; i++) {
			System.arraycopy(keys.get(i), 0, packed, i * stride, stride);
		}
		return packed;
	}

	private static float[] unpack(final float[] packed, final int index, final float[] key) {
		System.arraycopy(packed, index * key.length, key, 0, key.length);
		return key;
	}

	private TYPE[] getValues(final MdlxTimeline<TYPE> timeline) {
		final TYPE[] values = timeline.getValues();
		return fixTimelineArray(timeline, values);
	}

	private TYPE[] getOutTans(final MdlxTimeline<TYPE> timeline) {
		final TYPE[] outTans = timeline.getOutTans();
		return fixTimelineArray(timeline, outTans);
	}

	private TYPE[] getInTans(final MdlxTimeline<TYPE> timeline) {
		final TYPE[] inTans = timeline.getInTans();
		return fixTimelineArray(timeline, inTans);
	}

	@SuppressWarnings("unchecked")
	private static <T> T[] fixTimelineArray(final MdlxTimeline<?> timeline, final T[] values) {
		if (values == null) {
			return null;
		}
		if (timeline.getName().equals(AnimationMap.KLAC.getWar3id())
				|| timeline.getName().equals(AnimationMap.KLBC.getWar3id())) {
			final float[][] flippedColorData = new float[values.length][3];
			for (int i = 0; i < values.length; i++) {
				flippedColorData[i] = ParseUtils.newFlippedRGB((float[]) values[i]);
			}
			return (T[]) flippedColorData;
		}
		return values;
	}

	@SuppressWarnings("unchecked")
	public int getValue(final TYPE out, final long frame) {
		final int length = this.count;
		final int[] frames = this.frames;
		final int first = this.first;

		if (this.constant || (frame < this.start)) {
			this.sd.copy(out, this.packedValues != null
					? (TYPE) unpack(this.packedValues, first, KEY_HEAP[this.stride][0])
					: this.values[0]);

			return -1;
		}
		else {
			int startFrameIndex = -1;
			int endFrameIndex = -1;
			final int lengthLessOne = length - 1;
			if ((frame < frames[first]) || (frame >= frames[first + lengthLessOne])) {
				startFrameIndex = lengthLessOne;
				endFrameIndex = 0;
			}
			else {
				for (int i = 1; i < length; i++) {
					if (frames[first + i] > frame) {
						startFrameIndex = i - 1;
						endFrameIndex = i;
						break;
					}
				}
			}
			long startFrame = frames[first + startFrameIndex];
			final long endFrame = frames[first + endFrameIndex];
			long timeBetweenFrames = endFrame - startFrame;
			if (timeBetweenFrames < 0) {
				timeBetweenFrames += (this.end - this.start);
				if (frame < startFrame) {
					startFrame = endFrame;
				}
			}
			final float t = ((timeBetweenFrames) == 0 ? 0 : ((frame - startFrame) / (float) (timeBetweenFrames)));
			if (this.packedValues != null) {
				interpolatePacked(out, first + startFrameIndex, first + endFrameIndex, t);
			}
			else {
				this.sd.interpolate(out, this.values, this.inTans, this.outTans, startFrameIndex, endFrameIndex, t);
			}
			return startFrameIndex;
		}
	}

	/** The keys are put at 0 and 1 of arrays shaped as the unpacked ones, so the interpolation is the same. */
	@SuppressWarnings("unchecked")
	private void interpolatePacked(final TYPE out, final int startIndex, final int endIndex, final float t) {
		final float[][] keys = KEY_HEAP[this.stride];
		VALUES_HEAP[0] = unpack(this.packedValues, startIndex, keys[0]);
		VALUES_HEAP[1] = unpack(this.packedValues, endIndex, keys[1]);
		float[][] inTans = NO_TANS;
		float[][] outTans = NO_TANS;
		if (this.packedInTans != null) {
			OUT_TANS_HEAP[0] = unpack(this.packedOutTans, startIndex, keys[2]);
			IN_TANS_HEAP[1] = unpack(this.packedInTans, endIndex, keys[3]);
			inTans = IN_TANS_HEAP;
			outTans = OUT_TANS_HEAP;
		}
		this.sd.interpolate(out, (TYPE[]) VALUES_HEAP, (TYPE[]) inTans, (TYPE[]) outTans, 0, 1, t);
	}

	protected final boolean equals(final TYPE a, final TYPE b) {
		if ((a instanceof Float) && (b instanceof Float)) {
			return a.equals(b);
		}
		else if ((a instanceof Long) && (b instanceof Long)) {
			return a.equals(b);
		}
		else if ((a instanceof float[]) && (b instanceof float[])) {
			return Arrays.equals(((float[]) a), (float[]) b);
		}
		else if ((a instanceof long[]) && (b instanceof long[])) {
			return Arrays.equals(((long[]) a), (long[]) b);
		}
		return false;
	}
}
