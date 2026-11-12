package com.etheller.warsmash.viewer5;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.ResourceHandler;
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
	private final int magFilter = GL20.GL_LINEAR;
	private final int minFilter = GL20.GL_LINEAR;
	private ByteBuffer data;
	private long uploadedBytes;

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
		this.viewer.gl.glDeleteTexture(this.handle);
		this.viewer.addTextureBytes(-this.uploadedBytes);
		this.uploadedBytes = 0;
	}

	@Override
	protected void error(final Exception e) {
		Log.error(e);
	}

	@Override
	public void bind(final int unit) {
		this.viewer.webGL.bindTexture(this, unit);
	}

	@Override
	public void internalBind() {
		this.viewer.gl.glBindTexture(this.target, this.handle);
		this.viewer.gl.glTexParameteri(this.target, GL20.GL_TEXTURE_WRAP_S, this.wrapS);
		this.viewer.gl.glTexParameteri(this.target, GL20.GL_TEXTURE_WRAP_T, this.wrapT);
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
		if (levels.count() <= 1) {
			update(levels.getWidth(0), levels.getHeight(0), levels.getRgba(0), sRGBFix);
			return;
		}
		final GL20 gl = this.viewer.gl;
		this.data = keepsPixels() ? levels.getRgba(0) : null;
		gl.glBindTexture(GL20.GL_TEXTURE_2D, this.handle);
		for (int level = 0; level < levels.count(); level++) {
			gl.glTexImage2D(GL20.GL_TEXTURE_2D, level, sRGBFix ? GL30.GL_SRGB8_ALPHA8 : GL30.GL_RGBA8,
					levels.getWidth(level), levels.getHeight(level), 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE,
					levels.getRgba(level));
		}
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAX_LEVEL, levels.count() - 1);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR_MIPMAP_LINEAR);
		this.width = levels.getWidth(0);
		this.height = levels.getHeight(0);
		countUpload(levels.byteCount());
	}

	private void countUpload(final long bytes) {
		this.viewer.addTextureBytes(bytes - this.uploadedBytes);
		this.uploadedBytes = bytes;
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
		return (this.fetchUrl != null) && this.fetchUrl.toLowerCase(Locale.ROOT).contains("shadows");
	}

}
