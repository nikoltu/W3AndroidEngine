package com.etheller.warsmash.viewer5.handlers.w3x.rendersim;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.units.ObjectData;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.w3x.MdxAssetLoader;

public abstract class RenderWidgetTypeData<T> {
	protected final ObjectData objectData;
	protected final DataSource dataSource;
	protected final MdxAssetLoader mapViewer;
	private final Map<War3ID, T> unitIdToTypeData = new HashMap<>();
	private final Map<String, String> shadowTextures = new HashMap<>();

	public RenderWidgetTypeData(ObjectData objectData, DataSource dataSource, MdxAssetLoader mapViewer) {
		this.objectData = objectData;
		this.dataSource = dataSource;
		this.mapViewer = mapViewer;
	}

	/** The types made so far. */
	protected final Iterable<T> allTypes() {
		return this.unitIdToTypeData.values();
	}

	/** The type if it was made already, else null; nothing is loaded. */
	protected final T peek(final War3ID key) {
		return this.unitIdToTypeData.get(key);
	}

	public final T get(War3ID key) {
		T unitTypeData = this.unitIdToTypeData.get(key);
		if (unitTypeData != null) {
			return unitTypeData;
		}
		final GameObject row = this.objectData.get(key);
		if (row != null) {
			unitTypeData = createTypeData(key, row);
			this.unitIdToTypeData.put(key, unitTypeData);
		}
		return unitTypeData;
	}

	/**
	 * The shadow texture of this name that the data has, or null for a name it has none of. Types share a few shadow
	 * names, so each name is looked up in the data once.
	 */
	protected final String findShadowTexture(final String unitShadow) {
		if (this.shadowTextures.containsKey(unitShadow)) {
			return this.shadowTextures.get(unitShadow);
		}
		String shadowTexture = "ReplaceableTextures\\Shadows\\" + unitShadow + ".blp";
		if (!this.dataSource.has(shadowTexture)) {
			shadowTexture = "ReplaceableTextures\\Shadows\\" + unitShadow + ".dds"; // fallback
			if (!this.dataSource.has(shadowTexture)) {
				shadowTexture = null;
			}
		}
		this.shadowTextures.put(unitShadow, shadowTexture);
		return shadowTexture;
	}

	protected final MdxModel getPortraitModel(final String path, final MdxModel model) {
		return getPortraitModel(path, () -> model);
	}

	/** As above, for a type whose own model is only made when it is needed, if it is. */
	protected final MdxModel getPortraitModel(final String path, final Supplier<MdxModel> model) {
		if (path.length() < 4) {
			// An item or unit type with no model file at all.
			return model.get();
		}
		MdxModel portraitModel;
		final String portraitPath = path.substring(0, path.length() - 4) + "_portrait.mdx";
		if (this.dataSource.has(portraitPath)) {
			portraitModel = this.mapViewer.loadModelMdx(portraitPath);
		}
		else {
			portraitModel = model.get();
		}
		return portraitModel;
	}

	protected abstract T createTypeData(War3ID key, GameObject row);
}
