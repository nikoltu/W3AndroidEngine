package com.etheller.warsmash.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.etheller.warsmash.datasources.GraphicsSet;

/**
 * The game art a launcher skin draws with: what each piece is called, where the game data keeps it and how it is made
 * from there, and the skin's colours. Android decodes the list once into its cache folder; the desktop reads the same
 * pieces straight from the game data.
 */
public final class SkinArt {
	public static final String CLASSIC_SKIN = "classic";
	public static final String REFORGED_SKIN = "reforged";

	public static final String BACKGROUND = "background.jpg";
	public static final String PANEL_BORDER = "panel-border.9.png";
	public static final String BUTTON_BORDER = "button-border.9.png";
	public static final String BUTTON_BORDER_DOWN = "button-border-down.9.png";
	public static final String BUTTON_BORDER_DISABLED = "button-border-disabled.9.png";
	public static final String BUTTON_BACKGROUND = "button-background.png";
	public static final String BUTTON_BACKGROUND_DOWN = "button-background-down.png";
	public static final String BUTTON_BACKGROUND_DISABLED = "button-background-disabled.png";
	public static final String HOLDER = "holder.png";
	public static final String BAR_BACKGROUND = "bar-background.png";
	public static final String BAR_FILL = "bar-fill.png";
	public static final String BAR_GLASS = "bar-glass.png";
	public static final String LOGO_TFT = "logo-tft.png";
	public static final String LOGO_ROC = "logo-roc.png";
	public static final String PORTRAIT_ACTIVE = "portrait-active.png";
	public static final String QUESTION = "dialog-question.png";
	public static final String MUSIC = "menu-music.mp3";
	public static final String MUSIC_ROC = "menu-music-roc.mp3";
	public static final String CHECKBOX = "checkbox.png";
	public static final String CHECKBOX_CHECK = "checkbox-check.png";
	public static final String DIALOG_BORDER = "dialog-border.9.png";
	public static final String DIALOG_BACKGROUND = "dialog-background.png";
	public static final String CAMPAIGN_POSTER = "campaign-prologue.jpg";
	public static final String FONT = "frizqt.ttf";
	/** Friz Quadrata has no ą č ę ė į ų ū; the game's global font draws them. */
	public static final String FONT_GLOBAL = "blizzardglobal.ttf";
	public static final String CLICK = "click.ogg";
	public static final String CLICK_BIG = "click-big.ogg";
	public static final String ICON_FOLDER = "icon-folder.png";
	public static final String STRINGS = "strings.txt";
	public static final String REFORGED = "reforged";
	/** Marks of the in-game graphics packages the game data has. */
	public static final String GRAPHICS_HD = "graphics-hd";
	public static final String GRAPHICS_DE = "graphics-de";
	public static final String RADIO = "radio.png";
	public static final String RADIO_BUTTON = "radio-button.png";
	public static final String SLIDER_BORDER = "slider-border.9.png";
	public static final String SLIDER_BACKGROUND = "slider-background.png";
	public static final String KNOB = "knob.png";
	public static final String EDIT_BORDER = "edit-border.9.png";
	public static final String EDIT_BACKGROUND = "edit-background.png";
	public static final String RAIL_BACKGROUND = "rail-background.jpg";
	public static final String CAMPAIGN_ARROW = "campaign-arrow.png";
	public static final String PULLDOWN_ARROW = "pulldown-arrow.png";
	public static final String ICON_FILE = "icon-file.png";
	public static final String ICON_MELEE = "icon-file-melee.png";
	public static final String ICON_UMS = "icon-file-ums.png";
	public static final String MINIMAP_UNKNOWN = "minimap-unknown.png";
	public static final String ROW_HIGHLIGHT = "row-highlight.png";
	/** The star of the game's Quests button flash, which the LAN button borrows. */
	public static final String GLOWSTAR = "glowstar.png";
	public static final String BUTTON_RED = "button-red.png";
	public static final String BUTTON_RED_DOWN = "button-red-down.png";
	public static final String BUTTON_GREEN = "button-green.png";
	public static final String BUTTON_GREEN_DOWN = "button-green-down.png";
	/** The Reforged campaign screen's shield of the campaign version its missions are played in. */
	public static final String VERSION_REFORGED = "version-reforged.png";
	public static final String VERSION_CLASSIC = "version-classic.png";
	/** The Reforged chapter icons: a cinematic, a chapter won, a chapter not won yet. */
	public static final String CHAPTER_CINEMATIC = "chapter-cinematic.png";
	public static final String CHAPTER_WON = "chapter-won.png";
	public static final String CHAPTER_OPEN = "chapter-open.png";
	public static final String CHEVRON_RIGHT = "chevron-right.png";
	/** The Reforged drop-down menu's gold arrow, at rest and disabled. */
	public static final String CHEVRON_DOWN = "chevron-down.png";
	public static final String CHEVRON_DOWN_DISABLED = "chevron-down-disabled.png";
	/** The Reforged interface's own fonts: MasterFont (text) and MasterFontBold (headings, names) of its war3skins. */
	public static final String FONT_MASTER = "master.ttf";
	public static final String FONT_BOLD = "master-bold.ttf";
	/** The Reforged ornament beside a gold heading; the one on the right is its mirror. */
	public static final String HEADER_ORNAMENT = "header-ornament.png";
	public static final String ICON_FOLDER_GOLD = "icon-folder-gold.png";
	public static final String ICON_SEARCH = "icon-search.png";
	/** The Reforged campaign result: the campaign's banner, the line under a heading and the icons of the numbers. */
	public static final String RESULT_BANNER_HUMAN = "result-banner-human.png";
	public static final String RESULT_BANNER_HORDE = "result-banner-horde.png";
	public static final String RESULT_BANNER_UNDEAD = "result-banner-undead.png";
	public static final String RESULT_BANNER_NIGHTELF = "result-banner-nightelf.png";
	public static final String RESULT_BANNER_FK = "result-banner-fk.png";
	public static final String RESULT_BANNER_RANDOM = "result-banner-random.png";
	public static final String RESULT_DIVIDER = "result-divider.png";
	public static final String RESULT_HEROES = "result-heroes.png";
	public static final String RESULT_UNITS = "result-units.png";
	public static final String RESULT_STRUCTURES = "result-structures.png";
	public static final String DIFFICULTY_EASY = "difficulty-easy.png";
	public static final String DIFFICULTY_NORMAL = "difficulty-normal.png";
	public static final String DIFFICULTY_HARD = "difficulty-hard.png";
	/** The Reforged mission pictures, made small: an index of "file TAB data path" lines and the files it names. */
	public static final String MISSIONS = "missions/index.txt";
	/** Where the Reforged mission pictures lie in the game data, in lower case. */
	public static final String MISSION_FOLDER = "webui\\loadingscreen\\campaign\\";

	/** The race symbols of the profile statistics: human, orc, undead, night elf (random has none). */
	public static final String[] RACE_SYMBOLS = { "race-human.png", "race-orc.png", "race-undead.png",
		"race-nightelf.png" };

	/**
	 * Original menu and campaign scenes, as Blizzard recorded them. The classic skin plays the "_sd" recordings of the
	 * classic 3D scenes, the Reforged skin the ones without; the two sets are never mixed.
	 */
	public static final String[] SCENES = { "mainmenu", "mainmenu_tft", "roc_prologue", "roc_human", "roc_undead",
		"roc_orc", "roc_nightelf", "tft_nightelf", "tft_human", "tft_undead", "tft_orc" };

	/** The string lists the launcher speaks from: the game's own and the engine's (its saved game messages). */
	public static final String[] STRING_FILES = { "UI\\FrameDef\\GlobalStrings.fdf",
		"UI\\FrameDef\\SmashUI\\SmashStrings.fdf" };

	/**
	 * The classic profile portraits, command buttons of the heroes and units by race: the glue name of the race, then the
	 * buttons. The first twelve were the only ones before; profiles keep their
	 * portrait by its place in {@link #CLASSIC_PORTRAITS}, so those come first there.
	 */
	private static final String[][] CLASSIC_PORTRAIT_RACES = {
		{ "HUMAN", "BTNHeroPaladin", "BTNHeroArchMage", "BTNHeroMountainKing", "BTNHeroBloodElfPrince",
			"BTNArthas", "BTNJaina", "BTNGarithos", "BTNFootman", "BTNKnight", "BTNRifleman", "BTNPriest",
			"BTNSorceress", "BTNSpellBreaker", "BTNGryphonRider", "BTNDragonHawk", "BTNMortarTeam", "BTNSteamTank",
			"BTNPeasant" },
		{ "ORC", "BTNHeroBlademaster", "BTNHeroFarSeer", "BTNHeroTaurenChieftain", "BTNShadowHunter",
			"BTNThrall", "BTNRexxar", "BTNChaosGrom", "BTNGrunt", "BTNHeadhunter", "BTNRaider", "BTNShaman",
			"BTNWitchDoctor", "BTNSpiritWalker", "BTNTauren", "BTNWyvernRider", "BTNTrollBatRider", "BTNPeon" },
		{ "UNDEAD", "BTNHeroDeathKnight", "BTNHeroLich", "BTNHeroDreadlord", "BTNHeroCryptLord",
			"BTNKelThuzad", "BTNTichondrius", "BTNBansheeRanger", "BTNGhoul", "BTNCryptFiend", "BTNAbomination",
			"BTNNecromancer", "BTNBanshee", "BTNGargoyle", "BTNFrostWyrm", "BTNDestroyer", "BTNAcolyte" },
		{ "NIGHT_ELF", "BTNHeroDemonHunter", "BTNKeeperOfTheGrove", "BTNHeroMoonPriestess",
			"BTNHeroWarden", "BTNFurion", "BTNPriestessOfTheMoon", "BTNEvilIllidan", "BTNWarden2", "BTNArcher",
			"BTNHuntress", "BTNDryad", "BTNDruidOfTheClaw", "BTNDruidOfTheTalon", "BTNMountainGiant",
			"BTNHippogriffRider", "BTNChimaera", "BTNFaerieDragon", "BTNWisp" },
		{ "KEY_NEUTRAL", "BTNHeroTinker", "BTNHeroAlchemist", "BTNHeroAvatarOfFlame", "BTNPandarenBrewmaster",
			"BTNBeastmaster", "BTNPitLord", "BTNNagaSeaWitch", "BTNArchimonde", "BTNMannoroth", "BTNAkama",
			"BTNMedivh", "BTNNagaMyrmidon", "BTNMurloc", "BTNSatyr", "BTNFurbolg", "BTNOgreMagi", "BTNCentaurKhan",
			"BTNHarpyQueen", "BTNGnollKing", "BTNInfernal", "BTNDoomGuard", "BTNFelguard", "BTNBanditLord",
			"BTNRanger" } };
	private static final String[] CLASSIC_PORTRAITS = classicPortraits();
	/** Reforged keeps its portraits numbered from p001; as many as the data has are offered. */
	private static final int REFORGED_PORTRAITS = 256;

	private SkinArt() {
	}

	private static String[] classicPortraits() {
		final List<String> names = new ArrayList<>(Arrays.asList("BTNHeroPaladin", "BTNArthas", "BTNThrall",
				"BTNJaina", "BTNFurion", "BTNHeroArchMage", "BTNHeroBlademaster", "BTNHeroDeathKnight",
				"BTNHeroMountainKing", "BTNKeeperOfTheGrove", "BTNKelThuzad", "BTNRexxar"));
		for (final String[] race : CLASSIC_PORTRAIT_RACES) {
			for (int i = 1; i < race.length; i++) {
				if (!names.contains(race[i])) {
					names.add(race[i]);
				}
			}
		}
		return names.toArray(new String[0]);
	}

	/** A group of portraits shown together: the race's glue name and the portraits' numbers. */
	public static final class PortraitGroup {
		public final String key;
		public final int[] portraits;

		PortraitGroup(final String key, final int[] portraits) {
			this.key = key;
			this.portraits = portraits;
		}
	}

	/** The portraits as the picker shows them: by race for the classic skin, in their order for Reforged. */
	public static List<PortraitGroup> portraitGroups(final boolean reforged) {
		final List<PortraitGroup> groups = new ArrayList<>();
		if (reforged) {
			final int[] all = new int[REFORGED_PORTRAITS];
			for (int i = 0; i < all.length; i++) {
				all[i] = i;
			}
			groups.add(new PortraitGroup(null, all));
			return groups;
		}
		final List<String> names = Arrays.asList(CLASSIC_PORTRAITS);
		for (final String[] race : CLASSIC_PORTRAIT_RACES) {
			final int[] portraits = new int[race.length - 1];
			for (int i = 1; i < race.length; i++) {
				portraits[i - 1] = names.indexOf(race[i]);
			}
			groups.add(new PortraitGroup(race[0], portraits));
		}
		return groups;
	}

	public static String portraitFile(final int index) {
		return String.format(Locale.ROOT, "portrait-%02d.png", index);
	}

	/** How many portraits a skin offers; the files the game data lacks are left out where they are listed. */
	public static int portraitCount(final boolean reforged) {
		return reforged ? REFORGED_PORTRAITS : CLASSIC_PORTRAITS.length;
	}

	/** The recording a skin plays for a scene: the "_sd" one for the classic skin. */
	public static String sceneName(final String scene, final boolean reforged) {
		return reforged ? scene : scene + "_sd";
	}

	/** How a piece of art is made from the game data. */
	public enum Kind {
		/** An image as it is. */
		PLAIN,
		/** An edge file of eight squares, laid out as a 3 x 3 frame. */
		EDGE,
		/** The menu button holder without the spike on its left. */
		HOLDER,
		/** A loading screen of four tiles, joined. */
		TILES,
		/** A PNG or JPEG of the Reforged interface, with a slice a whole frame cut into 3 x 3. */
		WEB,
		/** A file copied as it is (fonts, sounds, music). */
		COPY,
		/** The glue strings of {@link SkinArt#STRING_FILES}. */
		STRINGS,
		/** Only a mark: the game data has the Reforged interface at the path. */
		MARK,
		/** A recording of a menu or campaign scene, by its name. */
		SCENE,
		/** The Reforged mission pictures below {@link SkinArt#MISSION_FOLDER}. */
		MISSIONS
	}

	/** A piece of art: its name in the skin, the name shown while it is prepared, and where the data keeps it. */
	public static final class Item {
		public final String name;
		public final String shown;
		public final Kind kind;
		/** The data path; without extension for the classic images and tiles, which come as DDS or BLP. */
		public final String path;
		public final int slice;
		public final boolean centre;

		Item(final String name, final String shown, final Kind kind, final String path, final int slice,
				final boolean centre) {
			this.name = name;
			this.shown = shown;
			this.kind = kind;
			this.path = path;
			this.slice = slice;
			this.centre = centre;
		}
	}

	private static Item image(final String name, final String path, final Kind kind) {
		return new Item(name, path.substring(path.lastIndexOf('\\') + 1), kind, path, 0, false);
	}

	private static Item web(final String name, final String path, final int slice, final boolean centre) {
		return new Item(name, path.substring(path.lastIndexOf('\\') + 1), Kind.WEB, path, slice, centre);
	}

	private static Item item(final String name, final String shown, final Kind kind, final String path) {
		return new Item(name, shown, kind, path, 0, false);
	}

	/**
	 * A number that changes whenever a piece of art is added, removed or taken from another path, so a cache of
	 * prepared art made for an older set is made again (the prepared files are named by the items).
	 */
	public static int revision(final boolean reforged) {
		int hash = 17;
		for (final Item item : items(reforged)) {
			hash = (31 * hash) + item.name.hashCode();
			hash = (31 * hash) + item.kind.ordinal();
			hash = (31 * hash) + (item.path == null ? 0 : item.path.hashCode());
			hash = (31 * hash) + item.slice;
			hash = (31 * hash) + (item.centre ? 1 : 0);
		}
		return hash;
	}

	/** Every piece of a skin's art, the ones the preparation screen draws with first. */
	public static List<Item> items(final boolean reforged) {
		final List<Item> items = new ArrayList<>();
		if (reforged) {
			items.add(web(BACKGROUND, "webui\\common\\homescreen.jpg", 0, false));
			items.add(web(PANEL_BORDER, "webui\\baseframes\\war3_metal_frame_4k.png", 90, false));
		}
		else {
			items.add(item(BACKGROUND, "generic-loading", Kind.TILES,
					"UI\\Glues\\Loading\\Load-Generic\\Generic-Loading"));
			items.add(image(PANEL_BORDER, "UI\\Widgets\\EscMenu\\Human\\human-options-menu-border", Kind.EDGE));
		}
		items.add(image(BAR_BACKGROUND, "UI\\Glues\\Loading\\LoadBar\\Loading-BarBackground", Kind.PLAIN));
		items.add(image(BAR_FILL, "UI\\Glues\\Loading\\LoadBar\\Loading-BarFill", Kind.PLAIN));
		items.add(image(BAR_GLASS, "UI\\Glues\\Loading\\LoadBar\\Loading-BarGlass", Kind.PLAIN));
		items.add(item(FONT, "frizqt__.ttf", Kind.COPY, "Fonts\\FRIZQT__.TTF"));
		items.add(item(FONT_GLOBAL, "blizzardglobal-v5_4.ttf", Kind.COPY, "Fonts\\blizzardglobal-v5_4.ttf"));
		items.add(item(CLICK, "MouseClick1", Kind.COPY, "Sound\\Interface\\MouseClick1.ogg"));
		items.add(item(CLICK_BIG, "BigButtonClick", Kind.COPY, "Sound\\Interface\\BigButtonClick.ogg"));
		if (reforged) {
			reforgedItems(items);
		}
		else {
			classicItems(items);
		}
		items.add(item(CAMPAIGN_POSTER, "tutorial loading screen", Kind.TILES,
				"UI\\Glues\\Loading\\Backgrounds\\Campaigns\\Tutorial"));
		items.add(item(STRINGS, "GlobalStrings.fdf", Kind.STRINGS, null));
		final String glues = "UI\\Widgets\\Glues\\";
		items.add(image(CAMPAIGN_ARROW, glues + "GlueScreen-CampaignButton-Arrow", Kind.PLAIN));
		items.add(image(PULLDOWN_ARROW, glues + "GlueScreen-Pulldown-Arrow", Kind.PLAIN));
		items.add(image(ICON_FILE, glues + "Icon-File", Kind.PLAIN));
		items.add(image(ICON_MELEE, glues + "Icon-File-Melee", Kind.PLAIN));
		items.add(image(ICON_UMS, glues + "Icon-File-UMS", Kind.PLAIN));
		items.add(image(ICON_FOLDER, glues + "Icon-Folder", Kind.PLAIN));
		items.add(image(MINIMAP_UNKNOWN, glues + "Minimap-Unknown", Kind.PLAIN));
		final String[] races = { "Human", "Orc", "Undead", "NightElf" };
		for (int i = 0; i < races.length; i++) {
			items.add(image(RACE_SYMBOLS[i], "UI\\Glues\\Loading\\Backgrounds\\Campaigns\\" + races[i] + "Symbol",
					Kind.PLAIN));
		}
		items.add(item(MUSIC, "war3XMainScreen.mp3", Kind.COPY, "Sound\\Music\\mp3Music\\war3XMainScreen.mp3"));
		items.add(item(MUSIC_ROC, "MainScreen.mp3", Kind.COPY, "Sound\\Music\\mp3Music\\MainScreen.mp3"));
		items.add(item(REFORGED, "Reforged UI", Kind.MARK, "webui\\common\\homescreen.jpg"));
		items.add(item(GRAPHICS_HD, "Reforged HD", Kind.MARK, GraphicsSet.REFORGED.getMarkPath()));
		items.add(item(GRAPHICS_DE, "Reforged DE", Kind.MARK, GraphicsSet.DE.getMarkPath()));
		for (final String base : SCENES) {
			final String scene = sceneName(base, reforged);
			items.add(item("webms/" + scene + ".webm", scene + ".webm", Kind.SCENE, scene));
		}
		return items;
	}

	private static void classicItems(final List<Item> items) {
		final String glues = "UI\\Widgets\\Glues\\";
		items.add(image(BUTTON_BORDER, glues + "GlueScreen-Button1-BackdropBorder", Kind.EDGE));
		items.add(image(BUTTON_BORDER_DOWN, glues + "GlueScreen-Button1-BackdropBorder-Down", Kind.EDGE));
		items.add(image(BUTTON_BORDER_DISABLED, glues + "GlueScreen-Button1-BackdropBorder-Disabled", Kind.EDGE));
		items.add(image(BUTTON_BACKGROUND, glues + "GlueScreen-Button1-BackdropBackground", Kind.PLAIN));
		items.add(image(BUTTON_BACKGROUND_DOWN, glues + "GlueScreen-Button1-BackdropBackground-Down", Kind.PLAIN));
		items.add(image(BUTTON_BACKGROUND_DISABLED, glues + "GlueScreen-Button1-BackdropBackground-Disabled",
				Kind.PLAIN));
		items.add(image(HOLDER, glues + "GlueScreen-Button1-Border", Kind.HOLDER));
		items.add(image(LOGO_TFT, "UI\\Glues\\MainMenu\\WarCraftIIILogo_exp\\war3xlogo", Kind.PLAIN));
		items.add(image(LOGO_ROC, "UI\\Glues\\SinglePlayer\\CampaignWarcraftIIILogo\\WarcraftIII-Logo-Alpha",
				Kind.PLAIN));
		for (int i = 0; i < CLASSIC_PORTRAITS.length; i++) {
			items.add(image(portraitFile(i), "ReplaceableTextures\\CommandButtons\\" + CLASSIC_PORTRAITS[i],
					Kind.PLAIN));
		}
		items.add(image(PORTRAIT_ACTIVE, "UI\\Widgets\\Console\\Human\\CommandButton\\human-activebutton",
				Kind.PLAIN));
		items.add(image(QUESTION, glues + "dialogbox-question", Kind.PLAIN));
		items.add(image(CHECKBOX, glues + "GlueScreen-Checkbox-Background", Kind.PLAIN));
		items.add(image(CHECKBOX_CHECK, glues + "GlueScreen-Checkbox-Check", Kind.PLAIN));
		items.add(image(DIALOG_BORDER, "UI\\Widgets\\BattleNet\\bnet-dialoguebox-border", Kind.EDGE));
		items.add(image(DIALOG_BACKGROUND, "UI\\Widgets\\BattleNet\\bnet-dialoguebox-background", Kind.PLAIN));
		items.add(image(RADIO, glues + "GlueScreen-RadioButton-Background", Kind.PLAIN));
		items.add(image(RADIO_BUTTON, glues + "GlueScreen-RadioButton-Button", Kind.PLAIN));
		items.add(image(SLIDER_BORDER, glues + "GlueScreen-Slider-BackdropBorder", Kind.EDGE));
		items.add(image(SLIDER_BACKGROUND, glues + "GlueScreen-Slider-BackdropBackground", Kind.PLAIN));
		items.add(image(KNOB, glues + "SinglePlayerSkirmish-ScrollBarKnob", Kind.PLAIN));
		items.add(image(EDIT_BORDER, "UI\\Widgets\\EscMenu\\Human\\editbox-border", Kind.EDGE));
		items.add(image(EDIT_BACKGROUND, "UI\\Widgets\\EscMenu\\Human\\editbox-background", Kind.PLAIN));
		items.add(image(ROW_HIGHLIGHT, "UI\\Widgets\\BattleNet\\bnet-button01-highlight-mouse", Kind.PLAIN));
		items.add(image(GLOWSTAR, "UI\\Feedback\\QuestButton\\glowstar", Kind.PLAIN));
	}

	/**
	 * The Reforged interface keeps its art as PNG and JPEG under webui. Frames are whole images, cut into nine parts at
	 * the slice the game's own style sheets use.
	 */
	private static void reforgedItems(final List<Item> items) {
		items.add(item(FONT_MASTER, "blq55web.ttf", Kind.COPY, "Fonts\\BLQ55Web.ttf"));
		items.add(item(FONT_BOLD, "blq85web.ttf", Kind.COPY, "Fonts\\BLQ85Web.ttf"));
		final String buttons = "webui\\widgets\\buttons\\";
		items.add(web(BUTTON_BORDER, buttons + "wc3_btn_blue_normal_4k.png", 30, true));
		items.add(web(BUTTON_BORDER_DOWN, buttons + "wc3_btn_blue_down_4k.png", 30, true));
		items.add(web(BUTTON_BORDER_DISABLED, buttons + "wc3_btn_disabled_normal_4k.png", 30, true));
		items.add(web(LOGO_TFT, "webui\\common\\wc3tft-logo.png", 0, false));
		items.add(web(LOGO_ROC, "webui\\common\\wc3roc-logo.png", 0, false));
		items.add(web(HEADER_ORNAMENT, "webui\\common\\header-decoration-gold.png", 0, false));
		for (int i = 0; i < REFORGED_PORTRAITS; i++) {
			items.add(web(portraitFile(i), String.format(Locale.ROOT, "webui\\portraits\\p%03d.png", i + 1), 0,
					false));
		}
		items.add(web(PORTRAIT_ACTIVE, "webui\\widgets\\playerwidget\\portrait_frame_hover_4k.png", 0, false));
		items.add(web(QUESTION, "webui\\modal\\errormodal\\error_icon.png", 0, false));
		items.add(web(CHECKBOX, buttons + "checkbox-unchecked.png", 0, false));
		items.add(web(CHECKBOX_CHECK, buttons + "checkbox-checked.png", 0, false));
		items.add(web(DIALOG_BORDER, "webui\\baseframes\\war3_metal_frame_4k.png", 90, false));
		items.add(web(DIALOG_BACKGROUND, "webui\\baseframes\\war3_stone_bg_landscape_4k.png", 0, false));
		items.add(web(RAIL_BACKGROUND, "webui\\baseframes\\war3_stone_bg_landscape_4k.png", 0, false));
		items.add(web(KNOB, "webui\\widgets\\slider\\war3_slider_normal_4k.png", 0, false));
		items.add(web(EDIT_BORDER, "webui\\widgets\\textfields\\war3_text_normal_4k.png", 14, true));
		items.add(web(ROW_HIGHLIGHT, "webui\\social\\clan\\clan_settings_members_playerrow_bg.png", 0, false));
		items.add(web(BUTTON_RED, buttons + "wc3_btn_red_normal_4k.png", 30, true));
		items.add(web(BUTTON_RED_DOWN, buttons + "wc3_btn_red_down_4k.png", 30, true));
		items.add(web(BUTTON_GREEN, buttons + "wc3_btn_green_normal_4k.png", 30, true));
		items.add(web(BUTTON_GREEN_DOWN, buttons + "wc3_btn_green_down_4k.png", 30, true));
		final String campaign = "webui\\campaign\\";
		items.add(web(VERSION_REFORGED, campaign + "reforged-banner.png", 0, false));
		items.add(web(VERSION_CLASSIC, campaign + "classic-banner.png", 0, false));
		items.add(web(CHAPTER_CINEMATIC, campaign + "non-playable_icon.png", 0, false));
		items.add(web(CHAPTER_WON, campaign + "wc3_victoryscreen_difficulty_normal.png", 0, false));
		items.add(web(CHAPTER_OPEN, campaign + "wc3_victoryscreen_difficulty_unplayed.png", 0, false));
		final String icons = "webui\\icons\\";
		items.add(web(CHEVRON_RIGHT, icons + "chevron-right-icon-resting.png", 0, false));
		items.add(web(CHEVRON_DOWN, icons + "chevron-down-icon-resting.png", 0, false));
		items.add(web(CHEVRON_DOWN_DISABLED, icons + "chevron-down-icon-disabled.png", 0, false));
		items.add(web(ICON_FOLDER_GOLD, icons + "folder-icon-resting.png", 0, false));
		items.add(web(ICON_SEARCH, icons + "search-icon-resting.png", 0, false));
		for (final String banner : new String[] { "human", "horde", "undead", "nightelf", "fk", "random" }) {
			items.add(web("result-banner-" + banner + ".png", campaign + "wc3_victoryscreen_banner_" + banner + ".png", 0,
					false));
		}
		items.add(web(RESULT_DIVIDER, campaign + "wc3_victoryscreen_divider.png", 0, false));
		items.add(web(RESULT_HEROES, campaign + "wc3_victoryscreen_staticon_heroes.png", 0, false));
		items.add(web(RESULT_UNITS, campaign + "wc3_victoryscreen_staticon_units.png", 0, false));
		items.add(web(RESULT_STRUCTURES, campaign + "wc3_victoryscreen_staticon_structures.png", 0, false));
		items.add(web(DIFFICULTY_EASY, campaign + "wc3_victoryscreen_difficulty_easy.png", 0, false));
		items.add(web(DIFFICULTY_NORMAL, campaign + "wc3_victoryscreen_difficulty_normal.png", 0, false));
		items.add(web(DIFFICULTY_HARD, campaign + "wc3_victoryscreen_difficulty_hard.png", 0, false));
		items.add(item(MISSIONS, "mission pictures", Kind.MISSIONS, MISSION_FOLDER));
	}

	/** The holder's frame part of the 512 x 128 image, scaled to the image's width: left, top, width, height, end. */
	public static int[] holderCut(final int imageWidth) {
		final float unit = imageWidth / 512f;
		return new int[] { Math.round(114 * unit), Math.round(14 * unit), Math.round(396 * unit),
			Math.round(101 * unit), Math.round(40 * unit) };
	}

	/** Where each of the eight squares of an edge file goes in the 3 x 3 frame: left, right, top, bottom, corners. */
	public static final int[][] EDGE_PLACES = { { 0, 1 }, { 2, 1 }, { 1, 0 }, { 1, 2 }, { 0, 0 }, { 2, 0 }, { 0, 2 },
		{ 2, 2 } };

	/** The colours of a skin's text and lines, as 0xAARRGGBB. */
	public static final class Palette {
		public static final int CAPTION = 0xFF8C8576;
		public static final int DISABLED = 0xFF808080;
		public static final int OK = 0xFF6FCF6F;
		public static final int OFF = 0xFF55524A;
		public static final int ERROR = 0xFFE0564E;
		public static final int REFORGED_BUTTON_TEXT = 0xFFF6E7B8;
		public static final int DANGER_TEXT = 0xFFFF8A7A;

		public final int gold;
		public final int text;
		public final int text2;
		/** Row lines and the plain frames drawn when an edge file is missing. */
		public final int line;
		/** The text of a button at rest: gold on the classic button, light parchment on the Reforged one. */
		public final int buttonText;

		private Palette(final boolean reforged) {
			this.gold = reforged ? 0xFFE6B94A : 0xFFFCD312;
			this.text = reforged ? 0xFFF2EBDD : 0xFFFFFFFF;
			this.text2 = reforged ? 0xFFA79F90 : 0xFFB9AE95;
			this.line = reforged ? 0xFF4A4640 : 0xFF73590A;
			this.buttonText = reforged ? REFORGED_BUTTON_TEXT : this.gold;
		}

		private static final Palette CLASSIC = new Palette(false);
		private static final Palette REFORGED_PALETTE = new Palette(true);

		public static Palette of(final boolean reforged) {
			return reforged ? REFORGED_PALETTE : CLASSIC;
		}
	}
}
