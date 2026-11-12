package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import com.etheller.warsmash.util.War3ID;

final class CUnitDeathState {
	int deathTurnTick;
	boolean raisable;
	boolean decays;
	boolean corpse;
	boolean boneCorpse;
	boolean freshCorpse;
	boolean falseDeath;
	boolean animatedCorpse = false;
	boolean explodesOnDeath;
	War3ID explodesOnDeathBuffId;
}
