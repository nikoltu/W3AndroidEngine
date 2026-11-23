package com.etheller.warsmash.viewer5;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.DataErrors;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.UploadStaging;
import com.etheller.warsmash.viewer5.handlers.ResourceHandler;
import com.etheller.warsmash.viewer5.handlers.blp.CompressedMips;
import com.etheller.warsmash.viewer5.handlers.blp.MipLevels;

/**
 * Similar to GdxTextureResource, but now I'm probably replacing use of that one
 * with this one. I'm trying to fight the system here and avoid porting
 * Ghostwolf's BLP parser to java, and just use the Java BLP parser that I
 * already had, but the libraries are not playing nicely with each other, so
 * this class is written to be a lower level solution (OpenGL calls instead of
 * LibGDX api) that will work.
 *
 * My theory is that because doing it THIS way works on Retera Model Studio,
 * therefore it should work here as well.
 */
public abstract class RawOpenGLTextureResource extends Texture {
	private static final int BYTES_PER_PIXEL = 4;
	private final int target;
	protected int handle;
	private int width;
	private int height;
	private int wrapS = GL20.GL_CLAMP_TO_EDGE;
	private int wrapT = GL20.GL_CLAMP_TO_EDGE;
	private int appliedWrapS = -1;
	private int appliedWrapT = -1;
	private final int magFilter = GL20.GL_LINEAR;
	private final int minFilter = GL20.GL_LINEAR;
	private ByteBuffer data;
	private long uploadedBytes;
	private boolean disposed;
	/** The viewer's frame this texture was last drawn in, or uploaded in. */
	private int lastUsedFrame;
	/** Let go to keep the viewer's textures in their budget; read again when it is drawn next. */
	private boolean evicted;
	/** Its GL texture is drawn by something that holds the handle itself, so it is never let go. */
	private boolean handleShared;
	/** Read again after being let go and not uploaded yet. */
	private boolean reloading;
	/** A small level kept while the texture is let go, so that it is drawn blurred instead of not at all. */
	private byte[] stub;
	private int stubFormat;
	private int stubWidth;
	private int stubHeight;
	private boolean stubSrgb;
	/** Textures this small give nothing back when let go. */
	private static final int STUB_SIZE = 32;

	public RawOpenGLTextureResource(final ModelViewer viewer, final String extension, final PathSolver pathSolver,
			final String fetchUrl, final ResourceHandler handler) {
		super(viewer, extension, pathSolver, fetchUrl, handler);
		final GL20 gl = this.viewer.gl;
		this.handle = gl.glGenTexture();
		this.target = GL20.GL_TEXTURE_2D;
		gl.glBindTexture(this.target, this.handle);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, this.minFilter);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, this.magFilter);
	}

	@Override
	public void dispose() {
		this.disposed = true;
		this.viewer.gl.glDeleteTexture(this.handle);
		this.viewer.addTextureBytes(-this.uploadedBytes);
		this.uploadedBytes = 0;
	}

	@Override
	protected void error(final Exception e) {
		recordDecodeFailure(this.fetchUrl, e);
		Log.error("Texture could not be decoded: " + this.fetchUrl, e);
		showFallback();
	}

	/** The other format of the same texture, else the neutral value of its kind, in place of the empty texture a failed decode leaves. */
	private void showFallback() {
		if (this.disposed || isShadowTexture()) {
			return;
		}
		try {
			final MipLevels other = TextureFallback.alternate(this.viewer.dataSource, this.fetchUrl,
					this.viewer.getTextureSizeRule());
			if (other != null) {
				update(other, false);
			}
			else {
				update(1, 1, TextureFallback.neutral(TextureFallback.kindOf(this.fetchUrl)), false);
				setFallback(true);
			}
			this.ok = true;
		}
		catch (final RuntimeException e) {
			Log.error("Texture fallback failed: " + this.fetchUrl, e);
		}
	}

	/** The file is there but could not be read as an image, which the missing file report does not cover. */
	static void recordDecodeFailure(final String fetchUrl, final Exception e) {
		DataErrors.record(DataErrors.BAD_TEXTURE, fetchUrl + " (" + e + ")");
	}

	/** Records that a texture was left empty without an exception to show for it. */
	protected final void reportUnreadable(final String reason) {
		DataErrors.report(DataErrors.BAD_TEXTURE, this.fetchUrl + " (" + reason + ")",
				"Texture left empty: " + this.fetchUrl + " (" + reason + ")");
		showFallback();
	}

	protected final boolean isDisposed() {
		return this.disposed;
	}

	@Override
	public void bind(final int unit) {
		this.viewer.webGL.bindTexture(this, unit);
	}

	@Override
	public void markDrawn() {
		this.lastUsedFrame = this.viewer.frame;
		if (this.evicted) {
			this.evicted = false;
			this.reloading = true;
			this.viewer.reloadTexture(this);
			if (this.stub != null) {
				// until the levels arrive the stub is drawn, not nothing
				setPending(false);
			}
		}
	}

	final int getLastUsedFrame() {
		return this.lastUsedFrame;
	}

	/** Whether the texture can be let go now: loaded, with nothing else holding its pixels or its GL handle. */
	final boolean isEvictable() {
		return !this.disposed && !this.evicted && !this.reloading && !this.handleShared && this.ok && !isPending()
				&& (this.width > STUB_SIZE || this.height > STUB_SIZE) && (this.uploadedBytes > 0) && (this.data == null) && (this.fetchUrl != null)
				&& !this.fetchUrl.isEmpty() && !isShadowTexture();
	}

	/** Gives the texture's GPU memory back; it is read again when it is drawn next. */
	final void evict() {
		final GL20 gl = this.viewer.gl;
		gl.glDeleteTexture(this.handle);
		this.handle = gl.glGenTexture();
		gl.glBindTexture(this.target, this.handle);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, this.minFilter);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, this.magFilter);
		this.appliedWrapS = -1;
		this.appliedWrapT = -1;
		if (this.stub != null) {
			final ByteBuffer blocks = ByteBuffer.allocateDirect(this.stub.length).order(ByteOrder.nativeOrder());
			blocks.put(this.stub).flip();
			if (this.stubFormat != 0) {
				gl.glCompressedTexImage2D(GL20.GL_TEXTURE_2D, 0, this.stubFormat, this.stubWidth, this.stubHeight, 0,
						blocks.remaining(), blocks);
			}
			else {
				gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, this.stubSrgb ? GL30.GL_SRGB8_ALPHA8 : GL30.GL_RGBA8,
						this.stubWidth, this.stubHeight, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, blocks);
			}
			gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAX_LEVEL, 0);
			countUpload(this.stub.length);
		}
		else {
			countUpload(0);
			setPending(true);
		}
		this.evicted = true;
	}

	final boolean isEvicted() {
		return this.evicted;
	}

	/** Reads a texture that was let go again now, in place of when it is drawn next. */
	final void restore() {
		this.evicted = false;
		this.reloading = true;
		this.lastUsedFrame = this.viewer.frame;
		this.viewer.reloadTexture(this, false);
		// Letting go without a kept stub marked the texture pending, which only a queued load clears; this one was read
		// at once, so it is ready now and is not left drawn as nothing.
		if (!this.reloading) {
			setPending(false);
		}
	}

	/** Keeps the largest level of at most {@link #STUB_SIZE} pixels to draw while the texture is let go. */
	private void keepStub(final int format, final int levelWidth, final int levelHeight,
			final ByteBuffer pixels, final boolean sRGBFix) {
		final ByteBuffer copy = pixels.duplicate();
		if (format == 0) {
			copy.limit(Math.min(copy.limit(), copy.position() + (levelWidth * levelHeight * BYTES_PER_PIXEL)));
		}
		final byte[] bytes = new byte[copy.remaining()];
		copy.get(bytes);
		this.stub = bytes;
		this.stubFormat = format;
		this.stubWidth = levelWidth;
		this.stubHeight = levelHeight;
		this.stubSrgb = sRGBFix;
	}

	/** The file of a texture that was let go could not be read again: it stays empty, as a missing file does. */
	final void reloadFailed() {
		DataErrors.record(DataErrors.BAD_TEXTURE, this.fetchUrl + " (could not be read again)");
		this.reloading = false;
		setPending(false);
	}

	@Override
	public void internalBind() {
		this.viewer.gl.glBindTexture(this.target, this.handle);
		// A texture keeps its wrap modes, and setting them again makes some drivers rebuild the texture's state.
		if (this.appliedWrapS != this.wrapS) {
			this.viewer.gl.glTexParameteri(this.target, GL20.GL_TEXTURE_WRAP_S, this.wrapS);
			this.appliedWrapS = this.wrapS;
		}
		if (this.appliedWrapT != this.wrapT) {
			this.viewer.gl.glTexParameteri(this.target, GL20.GL_TEXTURE_WRAP_T, this.wrapT);
			this.appliedWrapT = this.wrapT;
		}
	}

	@Override
	public int getWidth() {
		return this.width;
	}

	@Override
	public int getHeight() {
		return this.height;
	}

	@Override
	public int getGlTarget() {
		return this.target;
	}

	@Override
	public int getGlHandle() {
		this.handleShared = true;
		return this.handle;
	}

	@Override
	public void setWrapS(final boolean wrapS) {
		this.wrapS = wrapS ? GL20.GL_REPEAT : GL20.GL_CLAMP_TO_EDGE;
		final GL20 gl = this.viewer.gl;
	}

	@Override
	public void setWrapT(final boolean wrapT) {
		this.wrapT = wrapT ? GL20.GL_REPEAT : GL20.GL_CLAMP_TO_EDGE;
		final GL20 gl = this.viewer.gl;
	}

	public void update(final BufferedImage image, final boolean sRGBFix) {
		final int imageWidth = image.getWidth();
		final int imageHeight = image.getHeight();
		final int[] pixels = new int[imageWidth * imageHeight];
		image.getRGB(0, 0, imageWidth, imageHeight, pixels, 0, imageWidth);

		final ByteBuffer buffer = ByteBuffer.allocateDirect(imageWidth * imageHeight * BYTES_PER_PIXEL)
				.order(ByteOrder.nativeOrder());
		// 4
		// for
		// RGBA,
		// 3
		// for
		// RGB

		for (int y = 0; y < imageHeight; y++) {
			for (int x = 0; x < imageWidth; x++) {
				final int pixel = pixels[(y * imageWidth) + x];
				buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red component
				buffer.put((byte) ((pixel >> 8) & 0xFF)); // Green component
				buffer.put((byte) (pixel & 0xFF)); // Blue component
				buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha component.
				// Only for RGBA
			}
		}

		buffer.flip();
		update(imageWidth, imageHeight, buffer, sRGBFix);
	}

	public void update(final ArgbImage image, final boolean sRGBFix) {
		final int imageWidth = image.getWidth();
		final int imageHeight = image.getHeight();
		final ByteBuffer buffer = ByteBuffer.allocateDirect(imageWidth * imageHeight * BYTES_PER_PIXEL)
				.order(ByteOrder.nativeOrder());
		for (int y = 0; y < imageHeight; y++) {
			for (int x = 0; x < imageWidth; x++) {
				final int pixel = image.getRGB(x, y);
				buffer.put((byte) ((pixel >> 16) & 0xFF));
				buffer.put((byte) ((pixel >> 8) & 0xFF));
				buffer.put((byte) (pixel & 0xFF));
				buffer.put((byte) ((pixel >> 24) & 0xFF));
			}
		}
		buffer.flip();
		update(imageWidth, imageHeight, buffer, sRGBFix);
	}

	/**
	 * @param rgba direct buffer of imageWidth * imageHeight RGBA pixels
	 */
	public void update(final int imageWidth, final int imageHeight, final ByteBuffer rgba, final boolean sRGBFix) {
		final GL20 gl = this.viewer.gl;
		final ByteBuffer buffer = rgba;
		// Only the terrain's building shadows read pixels back. Every other texture kept its pixels on the Java heap,
		// where Android counts direct buffers too, and a game with the menu behind it ran out of heap on phones.
		this.data = keepsPixels() ? buffer : null;

		gl.glBindTexture(GL20.GL_TEXTURE_2D, this.handle);

		gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, sRGBFix ? GL30.GL_SRGB8_ALPHA8 : GL30.GL_RGBA8, imageWidth, imageHeight,
				0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buffer);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAX_LEVEL, 0);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, this.minFilter);

		this.width = imageWidth;
		this.height = imageHeight;
		countUpload((long) imageWidth * imageHeight * BYTES_PER_PIXEL);
	}

	/**
	 * Uploads decoded mip levels, the largest first. The game's textures carry their own smaller levels, which are used
	 * as they are; a chain that stops before one pixel ends the texture's levels there.
	 */
	public void update(final MipLevels levels, final boolean sRGBFix) {
		final boolean keepsPixels = keepsPixels();
		if (levels.count() <= 1) {
			try {
				update(levels.getWidth(0), levels.getHeight(0),
						keepsPixels ? levels.getRgba(0) : levels.getRgbaForUpload(0), sRGBFix);
			}
			finally {
				finishUpload(levels, keepsPixels);
			}
			return;
		}
		final GL20 gl = this.viewer.gl;
		this.data = keepsPixels ? levels.getRgba(0) : null;
		gl.glBindTexture(GL20.GL_TEXTURE_2D, this.handle);
		try {
			for (int level = 0; level < levels.count(); level++) {
				gl.glTexImage2D(GL20.GL_TEXTURE_2D, level, sRGBFix ? GL30.GL_SRGB8_ALPHA8 : GL30.GL_RGBA8,
						levels.getWidth(level), levels.getHeight(level), 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE,
						keepsPixels ? levels.getRgba(level) : levels.getRgbaForUpload(level));
				UploadStaging.done();
			}
			if (this.viewer.hasTextureBudget()) {
				for (int level = 0; level < levels.count(); level++) {
					if (Math.max(levels.getWidth(level), levels.getHeight(level)) <= STUB_SIZE) {
						keepStub(0, levels.getWidth(level), levels.getHeight(level),
								keepsPixels ? levels.getRgba(level) : levels.getRgbaForUpload(level), sRGBFix);
						break;
					}
				}
			}
		}
		finally {
			finishUpload(levels, keepsPixels);
		}
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAX_LEVEL, levels.count() - 1);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR_MIPMAP_LINEAR);
		this.width = levels.getWidth(0);
		this.height = levels.getHeight(0);
		countUpload(levels.byteCount());
	}

	/**
	 * Uploads levels that are already in a block format the GPU reads, the largest first. The colors are the
	 * non sRGB ones, as the plain RGBA levels of the game's textures are.
	 */
	public void update(final CompressedMips levels) {
		final GL20 gl = this.viewer.gl;
		this.data = null;
		gl.glBindTexture(GL20.GL_TEXTURE_2D, this.handle);
		try {
			boolean stubKept = !this.viewer.hasTextureBudget();
			for (int level = 0; level < levels.count(); level++) {
				final ByteBuffer view = levels.getView(level);
				final ByteBuffer blocks = view != null ? view : UploadStaging.of(levels.getBlocks(level));
				gl.glCompressedTexImage2D(GL20.GL_TEXTURE_2D, level, levels.getFormat(), levels.getWidth(level),
						levels.getHeight(level), 0, blocks.remaining(), blocks);
				if (!stubKept && (Math.max(levels.getWidth(level), levels.getHeight(level)) <= STUB_SIZE)) {
					keepStub(levels.getFormat(), levels.getWidth(level), levels.getHeight(level), blocks, false);
					stubKept = true;
				}
				UploadStaging.done();
			}
		}
		finally {
			levels.free();
		}
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAX_LEVEL, levels.count() - 1);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER,
				levels.count() > 1 ? GL20.GL_LINEAR_MIPMAP_LINEAR : this.minFilter);
		this.width = levels.getWidth(0);
		this.height = levels.getHeight(0);
		countUpload(levels.byteCount());
	}

	/** The levels are on the GPU now: what was decoded outside the Java heap for them is given back. */
	private static void finishUpload(final MipLevels levels, final boolean keepsPixels) {
		UploadStaging.done();
		if (!keepsPixels) {
			levels.free();
		}
	}

	private void countUpload(final long bytes) {
		this.viewer.addTextureBytes(bytes - this.uploadedBytes);
		this.uploadedBytes = bytes;
		this.reloading = false;
		this.lastUsedFrame = this.viewer.frame;
	}

	/**
	 * I really don't like holding the reference to the original buffer like this.
	 * Seems wasteful. It's already on the GPU. However, while porting some code for
	 * shadow maps I hit a point where I really finally felt obligated to add this
	 * (there is some code in the Terrain stuff that should've had this, but
	 * doesn't, and does its own texture management as a result).
	 *
	 * So, as a note to future authors, please reinvent the system such that this
	 * cached buffer data is only stored for shadow maps and terrain textures or
	 * whatever. Right now, this holds a reference to these guys on every texture,
	 * on every unit, on every doodad, etc. Java will not be able to garbage collect
	 * them because we hold on to the buffer in "update()".
	 */
	public ByteBuffer getData() {
		return this.data;
	}

	private boolean keepsPixels() {
		return isShadowTexture();
	}

	/**
	 * The terrain's shadow textures are read pixel by pixel into the shadow map, one pixel for one map cell, so they
	 * are neither scaled down to the texture size limit nor stored as blocks.
	 */
	protected final boolean isShadowTexture() {
		return (this.fetchUrl != null) && this.fetchUrl.toLowerCase(Locale.ROOT).contains("shadows");
	}

}
