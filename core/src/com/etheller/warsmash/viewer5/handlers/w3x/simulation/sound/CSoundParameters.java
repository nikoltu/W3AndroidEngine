package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

import com.etheller.warsmash.viewer5.AudioContext;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;

/**
 * What scripts set on a sound before starting it, and how that becomes volume and position when it starts.
 */
final class CSoundParameters {
	private static final float MAX_VOLUME = 127f;
	int volume = -1;
	float pitch = 1.0f;
	int channel = 0;
	boolean positioned;
	float x;
	float y;
	float z;
	float minDistance = 99999;
	float maxDistance = 99999;
	float distanceCutoff = 99999;
	float coneInside = 0;
	float coneOutside = 0;
	int coneOutsideVolume = 127;
	float coneX;
	float coneY;
	float coneZ;
	RenderUnit attachedUnit;
	String dialogueSpeakerNameKey = "";
	String dialogueTextKey = "";

	void updatePositionFromUnit() {
		if (this.attachedUnit != null) {
			this.positioned = true;
			this.x = this.attachedUnit.location[0];
			this.y = this.attachedUnit.location[1];
			this.z = this.attachedUnit.location[2];
		}
	}

	float scaledVolume(final float fallback, final AudioContext audioContext) {
		float result = this.volume >= 0 ? this.volume / MAX_VOLUME : fallback;
		result *= CSoundMixer.channelScale(this.channel);
		return result * coneScale(audioContext);
	}

	private float coneScale(final AudioContext audioContext) {
		final float length = (float) Math
				.sqrt((this.coneX * this.coneX) + (this.coneY * this.coneY) + (this.coneZ * this.coneZ));
		if (!this.positioned || (length == 0) || (this.coneOutside <= 0) || (audioContext == null)) {
			return 1f;
		}
		final float toListenerX = audioContext.listener.getX() - this.x;
		final float toListenerY = audioContext.listener.getY() - this.y;
		final float toListenerZ = audioContext.listener.getZ() - this.z;
		final float distance = (float) Math.sqrt(
				(toListenerX * toListenerX) + (toListenerY * toListenerY) + (toListenerZ * toListenerZ));
		if (distance == 0) {
			return 1f;
		}
		final float cosine = ((toListenerX * this.coneX) + (toListenerY * this.coneY) + (toListenerZ * this.coneZ))
				/ (distance * length);
		final float angle = (float) Math.toDegrees(Math.acos(Math.max(-1f, Math.min(1f, cosine)))) * 2;
		if (angle <= this.coneInside) {
			return 1f;
		}
		final float outsideScale = this.coneOutsideVolume / MAX_VOLUME;
		if (angle >= this.coneOutside) {
			return outsideScale;
		}
		final float blend = (angle - this.coneInside) / (this.coneOutside - this.coneInside);
		return 1f + ((outsideScale - 1f) * blend);
	}
}
