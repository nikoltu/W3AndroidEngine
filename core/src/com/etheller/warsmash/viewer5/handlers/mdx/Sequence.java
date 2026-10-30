package com.etheller.warsmash.viewer5.handlers.mdx;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import com.etheller.warsmash.viewer5.Bounds;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.hiveworkshop.rms.parsers.mdlx.MdlxExtent;
import com.hiveworkshop.rms.parsers.mdlx.MdlxSequence;

public class Sequence {
	private static final Map<String, PrimaryTag> PRIMARY_TAGS = new HashMap<>();
	private static final Map<String, SecondaryTag> SECONDARY_TAGS = new HashMap<>();
	static {
		for (final PrimaryTag tag : PrimaryTag.VALUES) {
			PRIMARY_TAGS.put(tag.name(), tag);
		}
		for (final SecondaryTag tag : SecondaryTag.VALUES) {
			SECONDARY_TAGS.put(tag.name(), tag);
		}
	}

	private final MdlxSequence sequence;
	private final Bounds bounds;
	private final EnumSet<AnimationTokens.PrimaryTag> primaryTags = EnumSet.noneOf(AnimationTokens.PrimaryTag.class);
	private final EnumSet<AnimationTokens.SecondaryTag> secondaryTags = EnumSet
			.noneOf(AnimationTokens.SecondaryTag.class);

	public Sequence(final MdlxSequence sequence) {
		this.sequence = sequence;
		this.bounds = new Bounds();
		final MdlxExtent sequenceExtent = sequence.getExtent();
		this.bounds.fromExtents(sequenceExtent.getMin(), sequenceExtent.getMax(), sequenceExtent.getBoundsRadius());
		populateTags();
	}

	private void populateTags() {
		populateTags(this.primaryTags, this.secondaryTags, this.sequence.name);
	}

	public static void populateTags(final EnumSet<AnimationTokens.PrimaryTag> primaryTags,
			final EnumSet<AnimationTokens.SecondaryTag> secondaryTags, final String name) {
		primaryTags.clear();
		secondaryTags.clear();
		for (final String token : name.split("\\s+|,")) {
			final String upperCaseToken = token.toUpperCase();
			final PrimaryTag primaryTag = PRIMARY_TAGS.get(upperCaseToken);
			if (primaryTag != null) {
				primaryTags.add(primaryTag);
				continue;
			}
			final SecondaryTag secondaryTag = SECONDARY_TAGS.get(upperCaseToken);
			if (secondaryTag != null) {
				secondaryTags.add(secondaryTag);
				continue;
			}
			break;
		}
	}

	public static AnimationTokens.PrimaryTag any(final EnumSet<AnimationTokens.PrimaryTag> primaryTags) {
		AnimationTokens.PrimaryTag primaryTag;
		if (primaryTags.isEmpty()) {
			primaryTag = null;
		}
		else {
			primaryTag = primaryTags.iterator().next();
		}
		return primaryTag;
	}

	public static AnimationTokens.SecondaryTag anySecondary(final EnumSet<AnimationTokens.SecondaryTag> primaryTags) {
		AnimationTokens.SecondaryTag primaryTag;
		if (primaryTags.isEmpty()) {
			primaryTag = null;
		}
		else {
			primaryTag = primaryTags.iterator().next();
		}
		return primaryTag;
	}

	public String getName() {
		return this.sequence.getName();
	}

	public long[] getInterval() {
		return this.sequence.getInterval();
	}

	public float getMoveSpeed() {
		return this.sequence.getMoveSpeed();
	}

	public int getFlags() {
		return this.sequence.getFlags();
	}

	public float getRarity() {
		return this.sequence.getRarity();
	}

	public long getSyncPoint() {
		return this.sequence.getSyncPoint();
	}

	public Bounds getBounds() {
		return this.bounds;
	}

	public MdlxExtent getExtent() {
		return this.sequence.getExtent();
	}

	public EnumSet<AnimationTokens.PrimaryTag> getPrimaryTags() {
		return this.primaryTags;
	}

	public EnumSet<AnimationTokens.SecondaryTag> getSecondaryTags() {
		return this.secondaryTags;
	}
}
