package com.etheller.warsmash.movie;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * A movie found in the game data: the picture, and the sound and captions kept beside it when there are any, with the
 * fonts a player that draws the captions itself can try, the game's own first.
 */
public final class Movie {
	private final String name;
	private final File video;
	private final File audio;
	private final File captions;
	private final Supplier<List<File>> captionFonts;

	public Movie(final String name, final File video, final File audio, final File captions) {
		this(name, video, audio, captions, Collections::emptyList);
	}

	public Movie(final String name, final File video, final File audio, final File captions,
			final Supplier<List<File>> captionFonts) {
		this.name = name;
		this.video = video;
		this.audio = audio;
		this.captions = captions;
		this.captionFonts = captionFonts;
	}

	public String getName() {
		return this.name;
	}

	public File getVideo() {
		return this.video;
	}

	/** @return the sound track, or null for a silent movie */
	public File getAudio() {
		return this.audio;
	}

	/** @return the captions, or null when the movie has none in this language */
	public File getCaptions() {
		return this.captions;
	}

	/**
	 * @return the fonts the captions may be drawn in, the best looking first; the player takes the first one that has
	 *         every letter of the captions
	 */
	public List<File> getCaptionFonts() {
		return this.captionFonts.get();
	}

	@Override
	public String toString() {
		return this.name + " (" + this.video + ")";
	}
}
