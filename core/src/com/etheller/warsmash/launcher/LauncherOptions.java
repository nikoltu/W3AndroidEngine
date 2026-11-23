package com.etheller.warsmash.launcher;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;

import com.etheller.warsmash.datasources.GraphicsSet;
import com.etheller.warsmash.datasources.GraphicsSettings;

/**
 * The engine settings the Options page offers, tab by tab, with the engine's defaults. Labels are the names of the
 * launcher's string resources.
 */
public final class LauncherOptions {
	public enum Kind {
		CHECK,
		SLIDER,
		RADIO
	}

	public static final class Row {
		public final String label;
		public final Kind kind;
		public final String key;
		public final int fallback;
		/** Slider ends or radio choices, as string resource names. */
		public final List<String> choices;
		/** An older on/off setting of the same choice, off meaning the first choice while the row was never set. */
		private final String legacyFlag;
		/** The default the game applies while the row was never set, when it depends on other settings. */
		private ToIntFunction<EngineSettings> fallbackOf;

		Row(final String label, final Kind kind, final String key, final int fallback, final String... choices) {
			this(label, kind, key, null, fallback, choices);
		}

		Row(final String label, final Kind kind, final String key, final String legacyFlag, final int fallback,
				final String... choices) {
			this.label = label;
			this.kind = kind;
			this.key = key;
			this.legacyFlag = legacyFlag;
			this.fallback = fallback;
			this.choices = Collections.unmodifiableList(Arrays.asList(choices));
		}

		Row defaultedBy(final ToIntFunction<EngineSettings> defaultOf) {
			this.fallbackOf = defaultOf;
			return this;
		}

		/** The row's value in the engine's settings; a check is 1 or 0. */
		public int value(final EngineSettings engine) {
			if (this.kind == Kind.CHECK) {
				return engine.flag(this.key, this.fallback != 0) ? 1 : 0;
			}
			if ((this.legacyFlag != null) && (engine.number(this.key, -1) < 0) && !engine.flag(this.legacyFlag, true)) {
				return 0;
			}
			final int value = engine.number(this.key,
					this.fallbackOf != null ? this.fallbackOf.applyAsInt(engine) : this.fallback);
			return this.kind == Kind.RADIO ? Math.max(0, Math.min(this.choices.size() - 1, value)) : value;
		}

		public void set(final EngineSettings engine, final int value) {
			if (this.kind == Kind.CHECK) {
				engine.setFlag(this.key, value != 0);
			}
			else {
				engine.setNumber(this.key, value);
			}
			if (this.legacyFlag != null) {
				engine.setFlag(this.legacyFlag, value != 0);
			}
		}
	}

	private static Row check(final String label, final String key, final boolean fallback) {
		return new Row(label, Kind.CHECK, key, fallback ? 1 : 0);
	}

	private static Row slider(final String label, final String key, final int fallback, final String low,
			final String high) {
		return new Row(label, Kind.SLIDER, key, fallback, low, high);
	}

	/** Graphics after the graphics set and the animated background, which are the launcher's own. */
	public static final List<Row> GRAPHICS = Collections.unmodifiableList(Arrays.asList(
			new Row("options_particles", Kind.RADIO, "particles", 2, "options_low", "options_medium", "options_high"),
			new Row("options_unit_shadows", Kind.RADIO, "unitShadowMode", "unitShadows", 2, "options_shadows_off",
					"options_shadows_blob", "options_shadows_full").defaultedBy(LauncherOptions::unitShadowsDefault),
			slider("options_gamma", "gamma", 50, "options_low", "options_high")));

	/**
	 * The unit shadows a game casts while the player never chose a mode: what the configuration names, else a round
	 * blob under Classic graphics and the models' own shadows under HD and DE. The row shows the same.
	 */
	static int unitShadowsDefault(final EngineSettings engine) {
		final String named = engine.graphics(GraphicsSettings.KEY_UNIT_SHADOWS, "").trim();
		if (named.equalsIgnoreCase("Full")) {
			return 2;
		}
		if (named.equalsIgnoreCase("Blob")) {
			return 1;
		}
		return GraphicsSet.fromIniName(engine.graphics(GraphicsSettings.KEY_SET, "")) == GraphicsSet.CLASSIC ? 1 : 2;
	}

	/**
	 * The in-game graphics choices, in the order of {@link GraphicsSet}: the label, and the label when the game data
	 * lacks the set's package.
	 */
	public static final String[][] GRAPHICS_SETS = { { "options_graphics_sd", "options_graphics_sd" },
		{ "options_graphics_hd", "options_graphics_hd_locked" }, { "options_graphics_de", "options_graphics_de_locked" } };
	/** The texture size choices, in the order of {@link GraphicsSettings#TEXTURE_SIZES}. */
	public static final String[] TEXTURE_SIZES = { "options_texture_512", "options_texture_1024",
		"options_texture_2048", "options_texture_full" };

	/** The render scale choices, in the order of {@link GraphicsSettings#RENDER_SCALES}. */
	public static final String[] RENDER_SCALES = { "options_scale_100", "options_scale_85", "options_scale_70",
		"options_scale_50" };
	/** The frame rate caps, where vertical sync cannot be turned off; 0 is none. Stored in Options.dat as frameCap. */
	public static final int[] FRAME_CAPS = { 0, 30, 60 };
	public static final String[] FRAME_CAP_NAMES = { "options_cap_none", "options_cap_30", "options_cap_60" };
	public static final String KEY_FRAME_CAP = "frameCap";
	public static final String KEY_VSYNC = "vsync";

	/** The choice of a frame rate cap, the nearest lower one for a cap the launcher does not offer. */
	public static int frameCapChoice(final int cap) {
		int best = 0;
		for (int i = 1; i < FRAME_CAPS.length; i++) {
			if (FRAME_CAPS[i] <= cap) {
				best = i;
			}
		}
		return best;
	}

	/** The choice of a texture size limit, the nearest larger one for a limit the launcher does not offer. */
	public static int textureSizeChoice(final int textureSize) {
		final int[] sizes = GraphicsSettings.TEXTURE_SIZES;
		for (int i = 0; i < sizes.length; i++) {
			if ((sizes[i] == 0) || ((textureSize > 0) && (textureSize <= sizes[i]))) {
				return i;
			}
		}
		return sizes.length - 1;
	}

	/** Sound before the menu music, which is the launcher's own; music at 40, as Warcraft III has it. */
	public static final List<Row> SOUND = Collections.unmodifiableList(Arrays.asList(
			check("options_sound_effects", "sound", true),
			slider("options_sound_volume", "soundVolume", 100, "options_low", "options_high"),
			check("launcher_music", "music", true),
			slider("options_music_volume", "musicVolume", 40, "options_low", "options_high"),
			check("options_ambient", "ambientSounds", true), check("options_movement", "movementSounds", true),
			check("options_unit_sounds", "unitSounds", true), check("options_subtitles", "subtitles", true)));

	/** Gameplay before the profile line; with touch the mouse scroll speed is the speed of a camera drag. */
	public static final List<Row> GAMEPLAY = Collections.unmodifiableList(Arrays.asList(
			slider("options_scroll", "mouseScroll", 50, "options_slow", "options_fast"),
			check("options_tooltips", "enhancedTooltips", true), check("options_health_bars", "statusBars", false),
			check("options_formation", "formationMovement", true),
			check("options_cine_portraits", "cinePortraits", false)));

	private LauncherOptions() {
	}
}
