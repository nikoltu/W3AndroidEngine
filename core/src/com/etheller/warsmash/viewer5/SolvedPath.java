package com.etheller.warsmash.viewer5;

public class SolvedPath {
	public String finalSrc;
	public String extension;

	public SolvedPath(final String finalSrc, final String extension) {
		this.finalSrc = finalSrc;
		this.extension = extension;
	}

	public String getFinalSrc() {
		return this.finalSrc;
	}

	public String getExtension() {
		return this.extension;
	}

}
