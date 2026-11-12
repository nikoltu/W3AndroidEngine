package com.etheller.warsmash.viewer5.handlers;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.StringBundle;
import com.etheller.warsmash.viewer5.CanvasProvider;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxHandler.ShaderEnvironmentType;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer.SolverParams;

public abstract class AbstractMdxModelViewer extends ModelViewer {
	public PathSolver wc3PathSolver = PathSolver.DEFAULT;
	public PathSolver mapPathSolver = PathSolver.DEFAULT;
	public SolverParams solverParams = new SolverParams();
	// The menu draws its models with other shaders and sorting than the game; each viewer keeps its own.
	public ShaderEnvironmentType shaderEnvironmentType = ShaderEnvironmentType.GAME;

	public AbstractMdxModelViewer(final DataSource dataSource, final CanvasProvider canvas) {
		super(dataSource, canvas);
	}

	public abstract StringBundle getWorldEditStrings();

}
