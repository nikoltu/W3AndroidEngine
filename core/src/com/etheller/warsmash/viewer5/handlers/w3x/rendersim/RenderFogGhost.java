package com.etheller.warsmash.viewer5.handlers.w3x.rendersim;

import com.badlogic.gdx.Gdx;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.w3x.SplatModel.SplatMover;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.BuildingShadow;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;

/**
 * A building as the local player last saw it, left standing in the fog. Measured on 1.29: a building that goes into
 * fog stays drawn there, still and darkened by the fog, with the model and team colour it had when last seen, on the
 * minimap too; what happens to it in the fog (death, a new owner, an upgrade) shows only once the place is seen again.
 */
public final class RenderFogGhost {
	private final CUnit unit;
	private final float x;
	private final float y;
	private final int fakePlayerIndex;
	private final int colorIndex;
	private final boolean minimapIconHidden;
	private final MdxComplexInstance instance;
	private final float[] baseColor = new float[4];
	private RenderUnit renderUnit;
	private BuildingShadow buildingShadow;
	private SplatMover uberSplat;
	private int shownTeamColor = -1;
	private byte fogShade;
	private boolean shaded;
	private boolean masked;
	private final String buildingShadowFile;
	private boolean buildingShadowMasked;

	public RenderFogGhost(final War3MapViewer map, final RenderUnit source) {
		this.unit = source.getSimulationUnit();
		this.renderUnit = source;
		this.x = source.location[0];
		this.y = source.location[1];
		this.fakePlayerIndex = this.unit.getFakePlayerIndex();
		this.colorIndex = source.playerIndex;
		this.minimapIconHidden = this.unit.isHideMinimapIcon();
		this.buildingShadowFile = source.getTypeData().getBuildingShadow();
		final MdxComplexInstance seen = source.instance;
		this.instance = (MdxComplexInstance) ((MdxModel) seen.model).addInstance();
		this.instance.setTransformation(seen.localLocation, seen.localRotation, seen.localScale);
		System.arraycopy(seen.replaceableTextures, 0, this.instance.replaceableTextures, 0,
				seen.replaceableTextures.length);
		System.arraycopy(seen.vertexColor, 0, this.baseColor, 0, this.baseColor.length);
		this.instance.setSequenceLoopMode(seen.sequenceLoopMode);
		this.instance.setSequence(seen.sequence);
		this.instance.setFrame(seen.frame);
		this.instance.setAnimationSpeed(0);
		this.instance.emittersSuppressed = true;
		this.instance.setScene(map.worldScene);
		updateColors(map);
	}

	/**
	 * Keeps the ground texture and shadow of the building, so that its removal does not show either.
	 */
	void takeBuildingDecals(final BuildingShadow buildingShadow, final SplatMover uberSplat) {
		this.buildingShadow = buildingShadow;
		this.uberSplat = uberSplat;
		this.renderUnit = null;
		// Under the mask the building had already taken its shadow away.
		this.buildingShadowMasked = (buildingShadow == null) && this.masked;
	}

	/**
	 * Returns true once the place is seen again and the ghost is gone.
	 */
	public boolean update(final War3MapViewer map) {
		final PathingGrid pathingGrid = map.simulation.getPathingGrid();
		final int indexX = pathingGrid.getFogOfWarIndexX(this.x);
		final int indexY = pathingGrid.getFogOfWarIndexY(this.y);
		final CFogState state = map.getFogOfWar().getFogState(map.simulation, indexX, indexY);
		if (CFogState.VISIBLE.equals(state)) {
			remove(map);
			return true;
		}
		// Under the black mask (a fog modifier or a script can mask an explored place again) nothing is shown, not
		// even what was seen there before, on the minimap neither.
		final boolean nowMasked = CFogState.MASKED.equals(state);
		if (nowMasked != this.masked) {
			this.masked = nowMasked;
			if (nowMasked) {
				this.instance.hide();
				if (this.uberSplat != null) {
					this.uberSplat.hide();
				}
				if (this.buildingShadow != null) {
					this.buildingShadow.remove();
					this.buildingShadow = null;
					this.buildingShadowMasked = true;
				}
			}
			else {
				this.instance.show();
				if (this.uberSplat != null) {
					this.uberSplat.show(map.terrain.centerOffset);
				}
				if (this.buildingShadowMasked) {
					this.buildingShadowMasked = false;
					if (this.buildingShadowFile != null) {
						this.buildingShadow = map.terrain.addShadow(this.buildingShadowFile, this.x, this.y);
					}
				}
			}
			if (this.renderUnit != null) {
				this.renderUnit.fogGhostMaskChanged(map);
			}
		}
		updateColors(map);
		return false;
	}

	/** Whether the ghost is drawn: not while its place is under the black mask. */
	public boolean isShown() {
		return !this.masked;
	}

	public void remove(final War3MapViewer map) {
		map.worldScene.removeInstance(this.instance);
		if (this.buildingShadow != null) {
			this.buildingShadow.remove();
			this.buildingShadow = null;
		}
		if (this.uberSplat != null) {
			this.uberSplat.destroy(Gdx.gl30, map.terrain.centerOffset);
			this.uberSplat = null;
		}
		if (this.renderUnit != null) {
			this.renderUnit.fogGhostRemoved(this);
			this.renderUnit = null;
		}
	}

	private void updateColors(final War3MapViewer map) {
		final int teamColor = map.getAllyColorFilter().worldColor(map.simulation, map.getLocalPlayerIndex(),
				this.fakePlayerIndex, this.colorIndex);
		if (teamColor != this.shownTeamColor) {
			this.shownTeamColor = teamColor;
			this.instance.setTeamColor(teamColor);
		}
		final PathingGrid pathingGrid = map.simulation.getPathingGrid();
		final byte shade = map.getFogShade(map.getFogOfWar()
				.getFogState(map.simulation, pathingGrid.getFogOfWarIndexX(this.x), pathingGrid.getFogOfWarIndexY(this.y))
				.getMask());
		if (!this.shaded || (shade != this.fogShade)) {
			this.shaded = true;
			this.fogShade = shade;
			final float brightness = (255 - (shade & 0xFF)) / 255f;
			this.instance.setVertexColor(this.baseColor[0] * brightness, this.baseColor[1] * brightness,
					this.baseColor[2] * brightness, this.baseColor[3]);
		}
	}

	public CUnit getUnit() {
		return this.unit;
	}

	public float getX() {
		return this.x;
	}

	public float getY() {
		return this.y;
	}

	public int getFakePlayerIndex() {
		return this.fakePlayerIndex;
	}

	public boolean isMinimapIconHidden() {
		return this.minimapIconHidden;
	}
}
