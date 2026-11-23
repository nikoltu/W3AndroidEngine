package com.etheller.warsmash.movie;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.etheller.warsmash.datasources.GameLanguage;

/**
 * Which font a caption is drawn in. The Chinese, Japanese and Korean fonts share the ideograph code points but draw
 * them in their own shapes, so Japanese captions are put in a Japanese font before any font that merely has the
 * letters.
 */
public final class CaptionFonts {
	private CaptionFonts() {
	}

	/** @return whether the character is a kana (hiragana, katakana, their extensions and the half-width katakana) */
	public static boolean isKana(final char c) {
		return ((c >= 0x3041) && (c <= 0x30FF) && (c != 0x30A0) && (c != 0x30FB)) || ((c >= 0x31F0) && (c <= 0x31FF))
				|| ((c >= 0xFF66) && (c <= 0xFF9F));
	}

	/** @return whether the character is an ideograph the Chinese, Japanese and Korean languages share */
	public static boolean isIdeograph(final char c) {
		return ((c >= 0x4E00) && (c <= 0x9FFF)) || ((c >= 0x3400) && (c <= 0x4DBF)) || ((c >= 0xF900) && (c <= 0xFAFF));
	}

	/**
	 * @param language the game's language code ("jaJP"), empty for none
	 * @return whether the texts are Japanese: they have kana, or ideographs and the game is in Japanese
	 */
	public static boolean isJapanese(final Collection<String> texts, final String language) {
		final boolean gameInJapanese = (language != null) && language.toLowerCase(Locale.ROOT).startsWith("ja");
		boolean ideographs = false;
		for (final String text : texts) {
			for (int i = 0; i < text.length(); i++) {
				final char c = text.charAt(i);
				if (isKana(c)) {
					return true;
				}
				ideographs |= isIdeograph(c);
			}
		}
		return ideographs && gameInJapanese;
	}

	/** The language the game is in now, as the skin file and the data configuration name it. */
	public static String gameLanguage() {
		final String skin = GameLanguage.skinLanguage();
		return skin.isEmpty() ? GameLanguage.inGame() : skin;
	}

	/** @return whether a font file is one made for Japanese, by the name its makers give it */
	public static boolean isJapaneseFont(final File font) {
		final String name = font.getName().toLowerCase(Locale.ROOT);
		// By the names their makers give them: the game's own Japanese font, then the ones of the computers.
		final String[] japaneseFonts = { "tt5500m_", "yugoth", "msgothic", "meiryo", "biz-ud", "uddigi",
				"yumin", "msmincho", "sourcehansansjp", "notosansjp", "notosanscjkjp", "notosanscjk-regular",
				"notosanscjk-bold", "hiragino sans w", "\u30d2\u30e9\u30ae\u30ce", "ipag", "ipaex", "ipamp", "takao",
				"vl-gothic", "vlgothic", "migu", "kozgo", "sazanami", "mplus" };
		for (final String japanese : japaneseFonts) {
			if (name.contains(japanese)) {
				return true;
			}
		}
		return false;
	}

	/** @return the fonts with the Japanese ones in front, each group in its own order, the list unchanged otherwise */
	public static List<File> order(final List<File> fonts, final boolean japanese) {
		if (!japanese) {
			return fonts;
		}
		final List<File> ordered = new ArrayList<>(fonts.size());
		for (final File font : fonts) {
			if (isJapaneseFont(font)) {
				ordered.add(font);
			}
		}
		for (final File font : fonts) {
			if (!isJapaneseFont(font)) {
				ordered.add(font);
			}
		}
		return ordered;
	}
}
