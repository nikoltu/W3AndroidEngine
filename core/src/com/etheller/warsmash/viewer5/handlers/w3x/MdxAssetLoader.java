package com.etheller.warsmash.viewer5.handlers.w3x;

import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;

public interface MdxAssetLoader {
	public MdxModel loadModelMdx(final String path);

	public ArgbImage loadPathingTexture(String pathingTexture);
}
