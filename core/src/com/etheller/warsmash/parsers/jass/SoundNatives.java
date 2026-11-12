package com.etheller.warsmash.parsers.jass;

import com.badlogic.gdx.audio.Sound;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CMIDISound;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSound;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundFilename;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundFromLabel;

/**
 * Sound and music natives.
 */
final class SoundNatives extends CommonNatives {
	SoundNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		final HandleJassType soundType = type("sound");

		// The original picks an EAX reverb preset; measured on 1.29, scripts read nothing back from it. The audio
		// back ends here (OpenAL through libGDX, Android) have no reverb to set.
		jassProgramVisitor.getJassNativeManager().createNative("NewSoundEnvironment",
				(arguments, globalScope, triggerScope) -> null);
		jassProgramVisitor.getJassNativeManager().createNative("CreateMIDISound",
				(arguments, globalScope, triggerScope) -> {
					final String soundLabel = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final int fadeInRate = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int fadeOutRate = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					final UnitSound sound = war3MapViewer.getUiSounds().getSound(soundLabel);
					if ((sound == null) || (sound.getSoundCount() == 0)) {
						return newHandle(soundType, new CMIDISound(soundLabel, fadeInRate, fadeOutRate));
					}
					return newHandle(soundType, new CSoundFromLabel(sound,
							meleeUI.getUiScene().audioContext, true, false, false, fadeInRate, fadeOutRate));
				});
		jassProgramVisitor.getJassNativeManager().createNative("StartSound",
				(arguments, globalScope, triggerScope) -> {
					final CSound soundHandle = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (soundHandle != null) {
						soundHandle.start(this.simulation.getGameTurnTick());
					}
					return null;
				});
		// Fading out is not supported yet; the sound stops right away.
		jassProgramVisitor.getJassNativeManager().createNative("StopSound",
				(arguments, globalScope, triggerScope) -> {
					final CSound soundHandle = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (soundHandle != null) {
						soundHandle.stop();
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetMapMusic",
				(arguments, globalScope, triggerScope) -> {
					final String musicName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final boolean random = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final int index = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());

					final String musicField = this.gameUI.trySkinField(musicName);
					meleeUI.setMapMusic(musicField, random, index);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PlayMusic",
				(arguments, globalScope, triggerScope) -> {
					final String musicName = arguments.get(0).visit(StringJassValueVisitor.getInstance());

					final String musicField = this.gameUI.trySkinField(musicName);
					meleeUI.playMusic(musicField, true, 0);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PlayMusicEx",
				(arguments, globalScope, triggerScope) -> {
					final String musicName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final int frommsecs = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int fadeInMSecs = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());

					final String musicField = this.gameUI.trySkinField(musicName);
					meleeUI.playMusicEx(musicField, true, 0, frommsecs, fadeInMSecs);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("StopMusic",
				(arguments, globalScope, triggerScope) -> {
					final boolean fadeOut = arguments.get(0).visit(BooleanJassValueVisitor.getInstance());
					meleeUI.stopMusic(fadeOut);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ResumeMusic",
				(arguments, globalScope, triggerScope) -> {
					meleeUI.resumeMusic();
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PlayThematicMusic",
				(arguments, globalScope, triggerScope) -> {
					final String musicName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final String musicField = this.gameUI.trySkinField(musicName);
					meleeUI.playMusic(musicField, true, 0);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PlayThematicMusicEx",
				(arguments, globalScope, triggerScope) -> {
					final String musicName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final String musicField = this.gameUI.trySkinField(musicName);
					final int frommsecs = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					meleeUI.playMusicEx(musicField, true, 0, frommsecs, -1);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("EndThematicMusic",
				(arguments, globalScope, triggerScope) -> {
					meleeUI.stopMusic(false);
					meleeUI.playMapMusic();
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetMusicVolume",
				(arguments, globalScope, triggerScope) -> {
					final int volume = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					meleeUI.setMusicVolume(volume);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetMusicPlayPosition",
				(arguments, globalScope, triggerScope) -> {
					final int millisecs = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					meleeUI.setMusicPlayPosition(millisecs);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetThematicMusicPlayPosition",
				(arguments, globalScope, triggerScope) -> {
					final int millisecs = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					meleeUI.setMusicPlayPosition(millisecs);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSoundDuration",
				(arguments, globalScope, triggerScope) -> {
					final CSound sound = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (sound == null) {
						return IntegerJassValue.ZERO;
					}
					return IntegerJassValue.of(Math.round(sound.getDuration() * 1000));
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateSoundFromLabel",
				(arguments, globalScope, triggerScope) -> {
					final String soundLabel = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final boolean looping = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final boolean is3D = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final boolean stopWhenOutOfRange = arguments.get(3)
							.visit(BooleanJassValueVisitor.getInstance());
					final int fadeInRate = arguments.get(4).visit(IntegerJassValueVisitor.getInstance());
					final int fadeOutRate = arguments.get(5).visit(IntegerJassValueVisitor.getInstance());
					final UnitSound sound = war3MapViewer.getUiSounds().getSound(soundLabel);
					return newHandle(soundType,
							new CSoundFromLabel(sound,
									is3D ? war3MapViewer.worldScene.audioContext
											: meleeUI.getUiScene().audioContext,
									looping, is3D, stopWhenOutOfRange, fadeInRate, fadeOutRate));
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateSound",
				(arguments, globalScope, triggerScope) -> {
					final String fileName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final boolean looping = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final boolean is3D = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final boolean stopWhenOutOfRange = arguments.get(3)
							.visit(BooleanJassValueVisitor.getInstance());
					final int fadeInRate = arguments.get(4).visit(IntegerJassValueVisitor.getInstance());
					final int fadeOutRate = arguments.get(5).visit(IntegerJassValueVisitor.getInstance());
					final String eaxSetting = arguments.get(6).visit(StringJassValueVisitor.getInstance());
					final Sound newSound = UnitSound.createSound(war3MapViewer.mapMpq, fileName);
					war3MapViewer.rememberFileSound(newSound, fileName);
					final float fileDuration = UnitSound.fileDuration(war3MapViewer.mapMpq, fileName);
					return newHandle(soundType,
							new CSoundFilename(newSound, fileDuration,
									is3D ? war3MapViewer.worldScene.audioContext
											: meleeUI.getUiScene().audioContext,
									looping, stopWhenOutOfRange, fadeInRate, fadeOutRate, eaxSetting));
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateSoundFilenameWithLabel",
				(arguments, globalScope, triggerScope) -> {
					final String fileName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final boolean looping = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final boolean is3D = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final boolean stopWhenOutOfRange = arguments.get(3)
							.visit(BooleanJassValueVisitor.getInstance());
					final int fadeInRate = arguments.get(4).visit(IntegerJassValueVisitor.getInstance());
					final int fadeOutRate = arguments.get(5).visit(IntegerJassValueVisitor.getInstance());
					final String label = arguments.get(6).visit(StringJassValueVisitor.getInstance());
					final Sound newSound = UnitSound.createSound(war3MapViewer.mapMpq, fileName);
					war3MapViewer.rememberFileSound(newSound, fileName);
					final float fileDuration = UnitSound.fileDuration(war3MapViewer.mapMpq, fileName);
					final CSoundFilename soundHandle = new CSoundFilename(newSound, fileDuration,
							is3D ? war3MapViewer.worldScene.audioContext : meleeUI.getUiScene().audioContext, looping,
							stopWhenOutOfRange, fadeInRate, fadeOutRate, "");
					CampaignNatives.applyLabelParameters(soundHandle, war3MapViewer.getSoundLabel(label));
					return newHandle(soundType, soundHandle);
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetSoundDuration",
				(arguments, globalScope, triggerScope) -> {
					final CSound sound = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int duration = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (sound != null) {
						sound.setDuration(duration / 1000f);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitLoopSpellSoundEffect",
				(arguments, globalScope, triggerScope) -> {
					final CUnit onUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int abilityAliasRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					this.simulation.unitLoopSoundEffectEvent(onUnit, new War3ID(abilityAliasRawcode));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitSpellSoundEffect",
				(arguments, globalScope, triggerScope) -> {
					final CUnit onUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int abilityAliasRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					this.simulation.unitSoundEffectEvent(onUnit, new War3ID(abilityAliasRawcode));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitStopSpellSoundEffect",
				(arguments, globalScope, triggerScope) -> {
					final CUnit onUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int abilityAliasRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					this.simulation.unitStopSoundEffectEvent(onUnit, new War3ID(abilityAliasRawcode));
					return null;
				});
	}
}
