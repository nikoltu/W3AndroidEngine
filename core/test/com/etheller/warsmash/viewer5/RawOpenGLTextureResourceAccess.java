package com.etheller.warsmash.viewer5;

/** The package private reporting of a texture that could not be decoded, for tests in other packages. */
public final class RawOpenGLTextureResourceAccess {
	private RawOpenGLTextureResourceAccess() {
	}

	public static void recordDecodeFailure(final String fetchUrl, final Exception e) {
		RawOpenGLTextureResource.recordDecodeFailure(fetchUrl, e);
	}
}
