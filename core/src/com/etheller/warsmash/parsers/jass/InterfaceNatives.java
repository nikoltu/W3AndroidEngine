package com.etheller.warsmash.parsers.jass;

import com.badlogic.gdx.Gdx;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.Bounds;
import com.etheller.warsmash.viewer5.handlers.w3x.TextTag;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerColor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.TextTagConfigType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Text tags, messages, interface and cinematic state, preloading and game end.
 */
final class InterfaceNatives extends CommonNatives {
	private List<String> preloadGenLines;
	/**
	 * Reforged dialogue text carries FaceFX analysis tags (["Happy" type=lt easein=0.25]...[/"Happy"]); the curves
	 * are already baked into the .animset and the original never shows the tags. The data also has unquoted
	 * ([Angry type=tt v1=4.0]), typographic-quoted, unterminated ([/"Happy]) and bare ([PH]) variants. Tags start
	 * with a letter, so color-coded bracketed text ([|cffffcc00...|r]) survives.
	 */
	private static final java.util.regex.Pattern DIALOGUE_ANIMATION_TAG = java.util.regex.Pattern
			.compile("\\[/?[\"\u201C\u201D]?\\p{L}[^\\[\\]]*\\]");

	InterfaceNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		registerTextTags();
		registerPreload();
		registerMessagesAndInterface();
	}

	private void registerTextTags() {
		final HandleJassType texttagType = type("texttag");
		final HandleJassType texttagconfigtypeType = type("texttagconfigtype");

		// text tags
		jassProgramVisitor.getJassNativeManager().createNative("CreateTextTag",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(texttagType, this.simulation.createTextTag());
				});
		jassProgramVisitor.getJassNativeManager().createNative("DestroyTextTag",
				(arguments, globalScope, triggerScope) -> {
					final TextTag textTag = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (textTag != null) {
						this.simulation.destroyTextTag(textTag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetTextTagText",
				(arguments, globalScope, triggerScope) -> {
					final TextTag textTag = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					String textValue = nullable(arguments, 1, StringJassValueVisitor.getInstance());
					textValue = war3MapViewer.getGameUI().getTrigStr(textValue);
					final float height = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (textTag != null) {
						textTag.setText(textValue);
						textTag.setFontHeight(height);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetTextTagPos",
				(arguments, globalScope, triggerScope) -> {
					final TextTag textTag = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float heightOffset = arguments.get(3).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					if (textTag != null) {
						textTag.setPosition(x, y, war3MapViewer.terrain.getGroundHeight(x, y) + heightOffset);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetTextTagPosUnit",
				(arguments, globalScope, triggerScope) -> {
					final TextTag textTag = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CUnit whichUnit = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final float heightOffset = arguments.get(2).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					if ((textTag != null) && (whichUnit != null)) {
						final float x = whichUnit.getX();
						final float y = whichUnit.getY();
						final float flyHeight = whichUnit.getFlyHeight();
						// NOTE: lep jassdoc on github says that the below sum also adds max Z extent of
						// whichUnit model, but according to their notes that value changes from
						// bloodlust but not SetUnitScale, some crazy bugs, we are not reproducing those
						// at the moment
						final RenderUnit renderPeer = war3MapViewer.getRenderPeer(whichUnit);
						final Bounds bounds = renderPeer == null ? null : renderPeer.instance.getBounds();
						float estimatedBoundsMaxZ = 0;
						if (bounds != null) {
							estimatedBoundsMaxZ = bounds.getEstimatedMaxZ();
						}
						textTag.setPosition(x, y, war3MapViewer.terrain.getGroundHeight(x, y) + flyHeight
								+ estimatedBoundsMaxZ + heightOffset);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetTextTagColor",
				(arguments, globalScope, triggerScope) -> {
					final TextTag textTag = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int red = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int green = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					final int blue = arguments.get(3).visit(IntegerJassValueVisitor.getInstance());
					final int alpha = arguments.get(4).visit(IntegerJassValueVisitor.getInstance());
					if (textTag != null) {
						textTag.setColor(red, green, blue, alpha);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetTextTagVelocity",
				(arguments, globalScope, triggerScope) -> {
					final TextTag textTag = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float xVelocity = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float yVelocity = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (textTag != null) {
						textTag.setVelocity(xVelocity, yVelocity);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetTextTagVisibility",
				(arguments, globalScope, triggerScope) -> {
					final TextTag textTag = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean visiblity = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (textTag != null) {
						textTag.setVisible(visiblity);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetTextTagSuspended",
				(arguments, globalScope, triggerScope) -> {
					final TextTag textTag = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (textTag != null) {
						textTag.setSuspended(flag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetTextTagPermanent",
				(arguments, globalScope, triggerScope) -> {
					final TextTag textTag = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (textTag != null) {
						textTag.setPermanent(flag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetTextTagAge",
				(arguments, globalScope, triggerScope) -> {
					final TextTag textTag = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float age = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (textTag != null) {
						textTag.setLifetime(age);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetTextTagLifespan",
				(arguments, globalScope, triggerScope) -> {
					final TextTag textTag = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float lifespan = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (textTag != null) {
						textTag.setLifetimeDuration(lifespan);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetTextTagFadepoint",
				(arguments, globalScope, triggerScope) -> {
					final TextTag textTag = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float fadepoint = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (textTag != null) {
						textTag.setFadeStart(fadepoint);
					}
					return null;
				});
		// AB floating text
		jassProgramVisitor.getJassNativeManager().createNative("SetTextTagCentered",
				(arguments, globalScope, triggerScope) -> {
					final TextTag textTag = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (textTag != null) {
						textTag.setCentered(flag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertTextTagConfigType",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(texttagconfigtypeType, TextTagConfigType.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateTextTagFromConfig",
				(arguments, globalScope, triggerScope) -> {
					final CUnit sourceUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					TextTagConfigType configType = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					String textValue = nullable(arguments, 2, StringJassValueVisitor.getInstance());

					if (sourceUnit != null) {
						if (configType == null) {
							configType = TextTagConfigType.GOLD;
						}
						if (textValue == null) {
							textValue = "(null)";
						}
						return new HandleJassValue(texttagType, this.simulation
								.spawnTextTag(sourceUnit, sourceUnit.getPlayerIndex(), configType, textValue));
					}
					return texttagType.getNullValue();

				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateIntTextTagFromConfig",
				(arguments, globalScope, triggerScope) -> {
					final CUnit sourceUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					TextTagConfigType configType = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final int intValue = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());

					if (sourceUnit != null) {
						if (configType == null) {
							configType = TextTagConfigType.GOLD;
						}
						return new HandleJassValue(texttagType, this.simulation
								.spawnTextTag(sourceUnit, sourceUnit.getPlayerIndex(), configType, intValue));
					}
					return texttagType.getNullValue();

				});
	}

	private void registerPreload() {
		jassProgramVisitor.getJassNativeManager().createNative("Preload", new JassFunction() {
			@Override
			public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
					final TriggerExecutionScope triggerScope) {
				final String filename = nullable(arguments, 0, StringJassValueVisitor.getInstance());
				// Preload lists also name sounds and Reforged-only formats (.tif, .pkfx) that
				// have no viewer handler; sounds are loaded on demand when played.
				if ((filename != null) && (InterfaceNatives.this.preloadGenLines != null)) {
					InterfaceNatives.this.preloadGenLines.add(filename);
				}
				if ((filename != null) && hasViewerHandler(war3MapViewer, filename.trim())
						&& hasOwnFile(war3MapViewer, filename.trim())) {
					try {
						war3MapViewer.load(filename.trim(), war3MapViewer.mapPathSolver,
								war3MapViewer.solverParams);
					}
					catch (final Exception exc) {
						Log.error("Preload(\"" + filename + "\") failed!");
						Log.error(exc);
					}
				}
				return null;
			}
		});
		// Measured on 1.29: PreloadStart and PreloadEnd drop the lines collected for PreloadGenEnd so far;
		// PreloadRefresh and PreloadEndEx keep them.
		jassProgramVisitor.getJassNativeManager().createNative("PreloadStart",
				(arguments, globalScope, triggerScope) -> {
					clearPreloadGenLines();
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PreloadGenStart",
				(arguments, globalScope, triggerScope) -> {
					this.preloadGenLines = new ArrayList<>();
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PreloadGenClear",
				(arguments, globalScope, triggerScope) -> {
					if (this.preloadGenLines != null) {
						this.preloadGenLines.clear();
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PreloadGenEnd",
				(arguments, globalScope, triggerScope) -> {
					final String filename = nullable(arguments, 0, StringJassValueVisitor.getInstance());
					if ((filename != null) && (this.preloadGenLines != null)) {
						writePreloadFile(filename, this.preloadGenLines);
					}
					this.preloadGenLines = null;
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("EndGame",
				(arguments, globalScope, triggerScope) -> {
					meleeUI.endGame(arguments.get(0).visit(BooleanJassValueVisitor.getInstance()));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RestartGame",
				(arguments, globalScope, triggerScope) -> {
					final boolean showScoreScreen = arguments.get(0).visit(BooleanJassValueVisitor.getInstance());
					war3MapViewer.runAfterPendingSave(() -> {
						war3MapViewer.setRestartAsNextLevel();
						meleeUI.endGame(showScoreScreen);
					});
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PreloadEnd", new JassFunction() {
			@Override
			public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
					final TriggerExecutionScope triggerScope) {
				clearPreloadGenLines();
				return null;
			}
		});
		jassProgramVisitor.getJassNativeManager().createNative("Preloader",
				(arguments, globalScope, triggerScope) -> {
					final String filename = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					// Campaign maps name .pld files the game data does not ship (NightElfX05); a preload list
					// only warms the cache.
					if ((filename != null) && dataSource.has(filename)) {
						Jass2.doPreloadScript(dataSource, uiViewport, uiScene, war3MapViewer, filename, meleeUI,
								originalFiles, jassProgramVisitor, "PreloadFiles", true);
					}
					return null;
				});
	}

	private void registerMessagesAndInterface() {
		jassProgramVisitor.getJassNativeManager().createNative("EnableUserUI",
				(arguments, globalScope, triggerScope) -> {
					meleeUI.enableUserUI(arguments.get(0).visit(BooleanJassValueVisitor.getInstance()));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("DisplayTimedTextToPlayer",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer whichPlayer = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float duration = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					final String message = this.gameUI
							.getTrigStr(arguments.get(4).visit(StringJassValueVisitor.getInstance()));
					Log.warn("DisplayTimedTextToPlayer: " + message.replace("\\n", "\n"));
					if ((whichPlayer != null) && war3MapViewer.isViewedPlayer(whichPlayer.getId())) {
						meleeUI.displayTimedText(x, y, duration, message);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("DisplayTimedTextFromPlayer",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass fromPlayer = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float duration = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					final String format = this.gameUI
							.getTrigStr(arguments.get(4).visit(StringJassValueVisitor.getInstance()));
					final String message = format.replace("%s",
							fromPlayer == null ? "" : fromPlayer.getName());
					Log.warn("DisplayTimedTextFromPlayer: " + message.replace("\\n", "\n"));
					meleeUI.displayTimedText(x, y, duration, message);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("DisplayTextToPlayer",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer whichPlayer = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final String message = this.gameUI
							.getTrigStr(arguments.get(3).visit(StringJassValueVisitor.getInstance()));
					if (whichPlayer == this.simulation
							.getPlayer(war3MapViewer.getLocalPlayerIndex())) {
						// Measured on 1.29: longer texts stay longer, up to about 10.7 s.
						meleeUI.displayTimedText(x, y, Math.min(2.8f + (0.1f * message.length()), 10.7f), message);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ClearTextMessages",
				(arguments, globalScope, triggerScope) -> {
					meleeUI.clearTextMessages();
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ShowInterface",
				(arguments, globalScope, triggerScope) -> {
					final boolean show = arguments.get(0).visit(BooleanJassValueVisitor.getInstance());
					final float fadeDuration = arguments.get(1).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					meleeUI.showInterface(show, fadeDuration);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("EnableUserControl",
				(arguments, globalScope, triggerScope) -> {
					final boolean value = arguments.get(0).visit(BooleanJassValueVisitor.getInstance());
					meleeUI.enableUserControl(value);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetCinematicScene",
				(arguments, globalScope, triggerScope) -> {
					final int portraitUnitId = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final Object colorValue = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final CPlayerColor color = colorValue instanceof CPlayerColor ? (CPlayerColor) colorValue : null;
					String speakerTitle = nullable(arguments, 2, StringJassValueVisitor.getInstance());
					String text = nullable(arguments, 3, StringJassValueVisitor.getInstance());
					final float sceneDuration = arguments.get(4).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					final float voiceoverDuration = arguments.get(5).visit(RealJassValueVisitor.getInstance())
							.floatValue();

					speakerTitle = this.gameUI.getTrigStr(speakerTitle);
					text = this.gameUI.getTrigStr(text);
					if (text != null) {
						text = DIALOGUE_ANIMATION_TAG.matcher(text).replaceAll("").trim();
					}

					meleeUI.setCinematicScene(portraitUnitId, color, speakerTitle, text, sceneDuration,
							voiceoverDuration);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("EndCinematicScene",
				(arguments, globalScope, triggerScope) -> {
					meleeUI.endCinematicScene();
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ForceCinematicSubtitles",
				(arguments, globalScope, triggerScope) -> {
					final boolean value = arguments.get(0).visit(BooleanJassValueVisitor.getInstance());
					meleeUI.forceCinematicSubtitles(value);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzHideCinematicPanels",
				(arguments, globalScope, triggerScope) -> {
					meleeUI.hideCinematicPanels(arguments.get(0).visit(BooleanJassValueVisitor.getInstance()));
					return null;
				});
		// Measured: it acts as an Escape press, so it also fires EVENT_PLAYER_END_CINEMATIC (skips a cinematic).
		jassProgramVisitor.getJassNativeManager().createNative("ForceUICancel",
				(arguments, globalScope, triggerScope) -> {
					meleeUI.keyDown(com.badlogic.gdx.Input.Keys.ESCAPE);
					meleeUI.keyUp(com.badlogic.gdx.Input.Keys.ESCAPE);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ShowInterfaceError",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass whichPlayer = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final String text = nullable(arguments, 1, StringJassValueVisitor.getInstance());
					if ((whichPlayer != null) && (text != null)) {
						this.simulation.getCommandErrorListener().showInterfaceError(whichPlayer.getId(), text);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPreferredSelectionReplacement",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnitIsGoingToBeRemoved = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final CUnit whichUnitWeWantToSelect = nullable(arguments, 1,
							ObjectJassValueVisitor.getInstance());
					if (whichUnitIsGoingToBeRemoved != null) {
						this.simulation.unitPreferredSelectionReplacement(whichUnitIsGoingToBeRemoved,
								whichUnitWeWantToSelect);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzGetHUDScale",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(100));
	}

	/**
	 * Same layout as the original game's preload files (CustomMapData), which maps read back as save codes.
	 */
	private void clearPreloadGenLines() {
		if (this.preloadGenLines != null) {
			this.preloadGenLines.clear();
		}
	}

	private static void writePreloadFile(final String filename, final List<String> lines) {
		final StringBuilder text = new StringBuilder();
		text.append("function PreloadFiles takes nothing returns nothing\r\n\r\n\tcall PreloadStart()\r\n");
		for (final String line : lines) {
			text.append("\tcall Preload( \"").append(line).append("\" )\r\n");
		}
		text.append("\tcall PreloadEnd( 0.0 )\r\n\r\nendfunction\r\n\r\n");
		try {
			Gdx.files.local("CustomMapData/" + filename.replace('\\', '/')).writeString(text.toString(), false,
					"UTF-8");
		}
		catch (final Exception exc) {
			Log.error("PreloadGenEnd(\"" + filename + "\") failed!");
			Log.error(exc);
		}
	}

	private static boolean hasViewerHandler(final War3MapViewer war3MapViewer, final String filename) {
		final int dotIndex = filename.lastIndexOf('.');
		return (dotIndex != -1)
				&& (war3MapViewer.findHandler(filename.substring(dotIndex).toLowerCase(Locale.US)) != null);
	}

	/**
	 * The melee and campaign preload lists of 2.0 and later name the Reforged birth, death and portrait models of
	 * every building, which the Classic data does not have. Loading them through the graphics packages only fills
	 * memory with models nothing shows, so a preload takes only files the data has under their own path.
	 */
	private static boolean hasOwnFile(final War3MapViewer war3MapViewer, final String filename) {
		final String path = war3MapViewer.mapPathSolver.solve(filename, war3MapViewer.solverParams).getFinalSrc();
		return war3MapViewer.dataSource.has(path);
	}
}
