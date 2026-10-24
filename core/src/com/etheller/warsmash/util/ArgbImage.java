package com.etheller.warsmash.util;

/**
 * A plain ARGB pixel grid for data images such as pathing maps, readable the same
 * way as BufferedImage.getRGB without depending on java.awt.
 */
public final class ArgbImage {
	private final int width;
	private final int height;
	private final int[] pixels;

	/**
	 * @param pixels width * height ARGB values, rows from the top
	 */
	public ArgbImage(final int width, final int height, final int[] pixels) {
		this.width = width;
		this.height = height;
		this.pixels = pixels;
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	public int getRGB(final int x, final int y) {
		return this.pixels[(y * this.width) + x];
	}
}
