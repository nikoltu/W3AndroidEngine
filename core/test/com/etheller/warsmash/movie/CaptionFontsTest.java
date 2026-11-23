package com.etheller.warsmash.movie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class CaptionFontsTest {
	private static final List<String> JAPANESE = Arrays.asList("\u30a2\u30fc\u30b5\u30fc\u738b\u5b50\u3001\u5f85\u3063\u3066\u3044\u308b");
	private static final List<String> CHINESE = Arrays.asList("\u963f\u59cb\u738b\u5b50\uff0c\u6211\u5728\u7b49\u4f60");
	private static final List<String> KOREAN = Arrays.asList("\uc544\uc11c\ub294 \uae30\ub2e4\ub9b0\ub2e4");

	@Test
	void kanaMakeTheCaptionsJapaneseWhateverTheGameLanguageIs() {
		assertTrue(CaptionFonts.isJapanese(JAPANESE, ""));
		assertTrue(CaptionFonts.isJapanese(JAPANESE, "enUS"));
		assertTrue(CaptionFonts.isJapanese(Arrays.asList("\uff76\uff85"), "zhCN"));
	}

	@Test
	void ideographsAloneAreJapaneseOnlyInAJapaneseGame() {
		final List<String> kanji = Arrays.asList("\u6226\u4e89");
		assertTrue(CaptionFonts.isJapanese(kanji, "jaJP"));
		assertFalse(CaptionFonts.isJapanese(kanji, "zhCN"));
		assertFalse(CaptionFonts.isJapanese(CHINESE, "enUS"));
	}

	@Test
	void koreanLatinAndCyrillicAreNotJapanese() {
		assertFalse(CaptionFonts.isJapanese(KOREAN, "jaJP"));
		assertFalse(CaptionFonts.isJapanese(Arrays.asList("The Lich King waits.", "\u041f\u0440\u0438\u0432\u0435\u0442"), "jaJP"));
		assertFalse(CaptionFonts.isJapanese(Collections.emptyList(), "jaJP"));
	}

	@Test
	void theProlongedSoundMarkAndTheMiddleDotAloneDoNotMakeChineseJapanese() {
		assertFalse(CaptionFonts.isKana('\u30fb'));
		assertFalse(CaptionFonts.isKana('\u30a0'));
		assertTrue(CaptionFonts.isKana('\u30fc'));
		assertTrue(CaptionFonts.isKana('\u3042'));
		assertFalse(CaptionFonts.isKana('\u4e00'));
	}

	@Test
	void japaneseFontsComeFirstTheGameOwnOneBeforeTheComputers() {
		final File latin = new File("Fonts/FRIZQT__.TTF");
		final File chinese = new File("Fonts/ARHEIU20M.TTF");
		final File gameJapanese = new File("Fonts/tt5500m_.ttf");
		final File yuGothic = new File("C:/Windows/Fonts/YuGothM.ttc");
		final File yaHei = new File("C:/Windows/Fonts/msyh.ttc");
		final File mac = new File("/System/Library/Fonts/\u30d2\u30e9\u30ae\u30ce\u89d2\u30b4\u30b7\u30c3\u30af W3.ttc");
		final File hiraginoChinese = new File("/System/Library/Fonts/Hiragino Sans GB.ttc");
		final List<File> fonts = Arrays.asList(latin, chinese, gameJapanese, yuGothic, yaHei, mac, hiraginoChinese);
		assertEquals(Arrays.asList(gameJapanese, yuGothic, mac, latin, chinese, yaHei, hiraginoChinese),
				CaptionFonts.order(fonts, true));
	}

	@Test
	void otherCaptionsKeepTheFontOrderAsItIs() {
		final List<File> fonts = Arrays.asList(new File("Fonts/ARHEIU20M.TTF"), new File("Fonts/tt5500m_.ttf"));
		assertSame(fonts, CaptionFonts.order(fonts, false));
	}

	@Test
	void theLinuxJapaneseFontsAreKnownByName() {
		assertTrue(CaptionFonts.isJapaneseFont(new File("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc")));
		assertTrue(CaptionFonts.isJapaneseFont(new File("/usr/share/fonts/opentype/ipafont-gothic/ipag.ttf")));
		assertFalse(CaptionFonts.isJapaneseFont(new File("/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf")));
		assertFalse(CaptionFonts.isJapaneseFont(new File("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")));
	}
}
