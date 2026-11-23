package com.etheller.warsmash.viewer5.handlers.blp;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import com.etheller.warsmash.datasources.IndependentStream;
import com.etheller.warsmash.util.OffHeapBlock;
import com.etheller.warsmash.util.StreamBytes;

import com.etheller.warsmash.util.ImageUtils;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.RawOpenGLTextureResource;
import com.etheller.warsmash.viewer5.handlers.ResourceHandler;

public class BlpTexture extends RawOpenGLTextureResource {

	public BlpTexture(final ModelViewer viewer, final ResourceHandler handler, final String extension,
			final PathSolver pathSolver, final String fetchUrl) {
		super(viewer, extension, pathSolver, fetchUrl, handler);
	}

	@Override
	protected void lateLoad() {

	}

	/** What {@link #prepare} left for {@link #finishLoad}: blocks or decoded levels, or the error that stopped it. */
	private CompressedMips preparedBlocks;
	private MipLevels preparedLevels;
	private Exception prepareError;
	/** The file, kept for the game thread only while the helper thread has not prepared it. */
	private byte[] fileBytes;
	/** The file of a texture that is queued before its helper has read it, outside the Java heap. */
	private OffHeapBlock queuedFile;

	/** The queued file as an array for the helper thread; it stays in {@link #fileBytes} for what the helper cannot do. */
	private byte[] queuedBytes() {
		final OffHeapBlock block = this.queuedFile;
		if (block == null) {
			return this.fileBytes;
		}
		final byte[] bytes = new byte[block.buffer().capacity()];
		block.buffer().duplicate().get(bytes);
		this.queuedFile = null;
		block.free();
		this.fileBytes = bytes;
		return bytes;
	}

	@Override
	protected void load(final InputStream src, final Object options) {
		try {
			final boolean shadow = isShadowTexture();
			if (!shadow && (src instanceof IndependentStream)) {
				// the helper reads the file too, which is most of what a texture costs on the game thread
				final int limit = this.viewer.getTextureSizeRule();
				final boolean compress = this.viewer.isTextureCompression();
				final boolean blocks = this.viewer.isBlockTextures();
				if (this.viewer.deferTextureLoad(() -> {
					try (InputStream stream = src) {
						if (blocks) {
							// A .dds that a .blp name resolved to is uploaded as it is stored, from a block of its own.
							final OffHeapBlock file = OffHeapBlock.readAll(stream);
							this.preparedBlocks = DdsDecoder.isDds(file.buffer()) ? DdsDecoder.blocks(file, limit) : null;
							if (this.preparedBlocks == null) {
								this.fileBytes = new byte[file.buffer().capacity()];
								file.buffer().duplicate().get(this.fileBytes);
								file.free();
							}
						}
						else {
							this.fileBytes = StreamBytes.readAll(stream);
						}
						if (this.preparedBlocks == null) {
							prepare(this.fileBytes, limit, compress, false);
						}
					}
					catch (final IOException | RuntimeException e) {
						this.prepareError = e;
					}
					catch (final OutOfMemoryError e) {
						this.fileBytes = null;
						this.prepareError = new RuntimeException("Out of memory reading " + this.fetchUrl, e);
					}
					return heldBytes();
				}, () -> finishLoad(null))) {
					setPending(true);
					return;
				}
			}
			final byte[] bytes = StreamBytes.readAll(src);
			if (!shadow) {
				final int limit = this.viewer.getTextureSizeRule();
				final boolean compress = this.viewer.isTextureCompression();
				final boolean blocks = this.viewer.isBlockTextures();
				// Queued files wait for a helper until it starts them: on Android that is kept outside the Java heap.
				if (OffHeapBlock.directBuffersInHeap()) {
					this.queuedFile = OffHeapBlock.copyOf(bytes);
				}
				else {
					this.fileBytes = bytes;
				}
				if (this.viewer.deferTextureLoad(() -> {
					prepare(queuedBytes(), limit, compress, blocks);
					return heldBytes();
				}, () -> finishLoad(null))) {
					setPending(true);
					return;
				}
				if (this.queuedFile != null) {
					this.queuedFile.free();
					this.queuedFile = null;
				}
				this.fileBytes = null;
			}
			decodeAndUpload(bytes);
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/** Decodes and compresses the file on a helper thread; what is not a BLP or DDS is left to the game thread. */
	private void prepare(final byte[] bytes, final int limit, final boolean compress, final boolean blockFormats) {
		try {
			final ByteBuffer data = ByteBuffer.wrap(bytes);
			final boolean dds = DdsDecoder.isDds(data);
			if (!dds && !BlpDecoder.isBlp(data)) {
				return;
			}
			if (blockFormats && dds) {
				final OffHeapBlock block = OffHeapBlock.copyOf(bytes);
				this.preparedBlocks = DdsDecoder.blocks(block, limit);
				if (this.preparedBlocks != null) {
					return;
				}
				block.free();
			}
			final MipLevels[] decoded = new MipLevels[1];
			final TextureTranscoder.Decoder decoder = () -> {
				decoded[0] = dds ? DdsDecoder.decode(data, limit, true).getLevels()
						: BlpDecoder.decode(data, limit, true).getLevels();
				return decoded[0];
			};
			if (compress) {
				this.preparedBlocks = TextureTranscoder.transcode(this.fetchUrl, bytes, limit, decoder,
						dds && DdsDecoder.isNormalMap(data));
				if (this.preparedBlocks != null) {
					return;
				}
			}
			this.preparedLevels = decoded[0] != null ? decoded[0] : decoder.decode();
		}
		catch (final IOException | RuntimeException e) {
			this.prepareError = e;
		}
		catch (final OutOfMemoryError e) {
			// the helper thread dies otherwise, and the texture would stay unfinished
			this.preparedBlocks = null;
			this.preparedLevels = null;
			this.prepareError = new RuntimeException("Out of memory preparing " + this.fetchUrl, e);
		}
	}

	/**
	 * The bytes the helper thread's result holds until the game thread uploads it. Once prepared (or failed) the file
	 * itself is let go: dozens of textures wait for their upload when units of new types appear, and their files
	 * kept beside the results filled a phone's heap.
	 */
	private long heldBytes() {
		if ((this.preparedBlocks != null) || (this.preparedLevels != null) || (this.prepareError != null)) {
			this.fileBytes = null;
		}
		return preparedBytes() + (this.fileBytes != null ? this.fileBytes.length : 0);
	}

	private long preparedBytes() {
		return this.preparedBlocks != null ? this.preparedBlocks.byteCount()
				: this.preparedLevels != null ? this.preparedLevels.byteCount() : 0;
	}

	private void finishLoad(final byte[] readBytes) {
		final byte[] bytes = readBytes != null ? readBytes : this.fileBytes;
		this.fileBytes = null;
		final CompressedMips blocks = this.preparedBlocks;
		final MipLevels levels = this.preparedLevels;
		final Exception failure = this.prepareError;
		this.preparedBlocks = null;
		this.preparedLevels = null;
		this.prepareError = null;
		setPending(false);
		if (isDisposed()) {
			if (levels != null) {
				levels.free();
			}
			if (blocks != null) {
				blocks.free();
			}
			return;
		}
		try {
			if (failure != null) {
				error(failure);
			}
			else if (blocks != null) {
				update(blocks);
			}
			else if (levels != null) {
				// A missing .blp can resolve to its .dds. Classic data stores the same colors in
				// both, so the .dds takes the plain RGBA format of the .blp.
				update(levels, false);
			}
			else {
				decodeAndUpload(bytes);
			}
		}
		catch (final IOException | RuntimeException e) {
			error(e);
		}
	}

	private void decodeAndUpload(final byte[] bytes) throws IOException {
		BufferedImage img;
		final ByteBuffer data = ByteBuffer.wrap(bytes);
		final boolean shadow = isShadowTexture();
		final int limit = shadow ? 0 : this.viewer.getTextureSizeRule();
		final boolean dds = DdsDecoder.isDds(data);
		if (dds && !shadow && this.viewer.isBlockTextures()) {
			final OffHeapBlock block = OffHeapBlock.copyOf(bytes);
			final CompressedMips blocks = DdsDecoder.blocks(block, limit);
			if (blocks != null) {
				update(blocks);
				return;
			}
			block.free();
		}
		if (this.viewer.isTextureCompression() && !shadow && (dds || BlpDecoder.isBlp(data))) {
			final CompressedMips blocks = TextureTranscoder.transcode(this.fetchUrl, bytes, limit,
					() -> dds ? DdsDecoder.decode(data, limit, true).getLevels()
							: BlpDecoder.decode(data, limit, true).getLevels(),
					dds && DdsDecoder.isNormalMap(data));
			if (blocks != null) {
				update(blocks);
				return;
			}
		}
		if (dds) {
			// A missing .blp can resolve to its .dds. Classic data stores the same colors in
			// both, so the .dds takes the plain RGBA format of the .blp below.
			update(DdsDecoder.decode(data, limit, true).getLevels(), false);
			return;
		}
		if (BlpDecoder.isBlp(data)) {
			// The ImageIO plugin reads these colors as linear and the sRGB texture format
			// undoes that, so the raw colors in a plain RGBA texture match it.
			update(BlpDecoder.decode(data, limit, true).getLevels(), false);
			return;
		}
		if (!ImageUtils.IMAGE_IO_AVAILABLE) {
			reportUnreadable("no decoder for this BLP on this platform");
			return;
		}
		img = ImageIO.read(new ByteArrayInputStream(data.array()));
		if (img == null) {
			throw new IOException("Not a readable image: " + this.fetchUrl);
		}
		update(img, true);
	}

}
