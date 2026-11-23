package com.etheller.warsmash.viewer5;

import com.etheller.warsmash.viewer5.handlers.ResourceHandler;

public abstract class Texture extends HandlerResource<ResourceHandler> implements ViewerTextureRenderable {

	public Texture(final ModelViewer viewer, final String extension, final PathSolver pathSolver, final String fetchUrl,
			final ResourceHandler handler) {
		super(viewer, extension, pathSolver, fetchUrl, handler);
	}

	/** Whether the texture is queued for a later decode and upload, so what it holds is not yet what it will show. */
	private boolean pending;

	public final boolean isPending() {
		return this.pending;
	}

	protected final void setPending(final boolean pending) {
		this.pending = pending;
	}

	private boolean fallback;

	/** Whether the texture shows the neutral stand-in for a file that could not be read. */
	public final boolean isFallback() {
		return this.fallback;
	}

	protected final void setFallback(final boolean fallback) {
		this.fallback = fallback;
	}

	public abstract void bind(final int unit);

	/** Called each time the texture is bound for drawing, before its pending state is looked at. */
	public void markDrawn() {
	}

	public abstract void internalBind();

	public abstract int getWidth();

	public abstract int getHeight();

	@Override
	public abstract int getGlTarget();

	@Override
	public abstract int getGlHandle();

	public abstract void setWrapS(final boolean wrapS);

	public abstract void setWrapT(final boolean wrapT);

}
