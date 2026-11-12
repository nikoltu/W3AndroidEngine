package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedGame;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.GameDataConstants;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextAreaFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.FocusableFrame;

/** The Save Game, Load Game, Options, Help and Tips parts of the game menu; the rest of it is wired up by {@link MeleeUI}. */
final class MeleeGameMenu {
	private static final Pattern HELP_TOKEN = Pattern.compile("%([A-Za-z]+[0-9]?)");

	private final GameUI rootFrame;
	private final Viewport uiViewport;
	private final SimpleFrame holder;
	private final UIFrame backdrop;
	private final UIFrame mainPanel;
	private final SavedGameDialog savedGames;
	private final GameOptionsDialog options;
	private final UIFrame helpPanel;
	private final UIFrame tipsPanel;
	private final TextAreaFrame helpText;
	private final TextAreaFrame tipsText;
	private final GlueTextButtonFrame tipsBack;
	private final GlueTextButtonFrame tipsNext;
	private final List<String> tips;
	private final String help;
	private int tipIndex;
	private final GlueTextButtonFrame saveButton;
	private final GlueTextButtonFrame loadButton;
	private final GlueTextButtonFrame helpOk;
	private final GlueTextButtonFrame tipsOk;

	MeleeGameMenu(final GameUI rootFrame, final Viewport uiViewport, final SimpleFrame holder, final UIFrame backdrop,
			final UIFrame mainPanel, final DataSource dataSource, final SavedGameDialog.Host host,
			final Runnable optionsApplied, final GameDataConstants gameData) {
		this.rootFrame = rootFrame;
		this.uiViewport = uiViewport;
		this.holder = holder;
		this.backdrop = backdrop;
		this.mainPanel = mainPanel;
		this.savedGames = new SavedGameDialog(rootFrame, uiViewport, holder, backdrop, new SavedGameDialog.Host() {
			@Override
			public void focusOn(final FocusableFrame frame) {
				host.focusOn(frame);
			}

			@Override
			public void saveGame(final String name) {
				host.saveGame(name);
			}

			@Override
			public void loadGame(final String name) {
				host.loadGame(name);
			}

			@Override
			public SavedGame.Loadability loadability(final FileHandle file) {
				return host.loadability(file);
			}

			@Override
			public void closed() {
				showMain();
			}
		});
		this.options = new GameOptionsDialog(rootFrame, uiViewport, holder, backdrop, new GameOptionsDialog.Host() {
			@Override
			public void closed() {
				showMain();
			}

			@Override
			public void applied() {
				optionsApplied.run();
			}
		}, gameData.isCatchCursor(), gameData.isEnableMusic());
		this.helpPanel = rootFrame.getFrameByName("HelpPanel", 0);
		this.tipsPanel = rootFrame.getFrameByName("TipsPanel", 0);
		this.helpText = (TextAreaFrame) rootFrame.getFrameByName("HelpTextArea", 0);
		this.tipsText = (TextAreaFrame) rootFrame.getFrameByName("TipsTextArea", 0);
		this.tipsBack = (GlueTextButtonFrame) rootFrame.getFrameByName("TipsBackButton", 0);
		this.tipsNext = (GlueTextButtonFrame) rootFrame.getFrameByName("TipsNextButton", 0);
		this.tips = readTips(dataSource);
		this.help = readHelp(dataSource);

		this.saveButton = button("SaveGameButton", () -> {
			hideMain();
			this.savedGames.showSave();
		});
		this.loadButton = button("LoadGameButton", () -> {
			hideMain();
			this.savedGames.showLoad();
		});
		button("OptionsButton", () -> {
			hideMain();
			this.options.show();
		});
		button("HelpButton", this::showHelp);
		button("TipsButton", this::showTips);
		this.helpOk = button("HelpOKButton", this::showMain);
		this.tipsOk = button("TipsOKButton", this::showMain);
		this.tipsBack.setOnClick(() -> showTip(this.tipIndex - 1));
		this.tipsNext.setOnClick(() -> showTip(this.tipIndex + 1));
	}

	private GlueTextButtonFrame button(final String name, final Runnable action) {
		final GlueTextButtonFrame button = (GlueTextButtonFrame) this.rootFrame.getFrameByName(name, 0);
		button.setEnabled(true);
		button.setOnClick(action);
		return button;
	}

	/** The Load Game button's panel, from the open menu; nothing when this game cannot load. */
	void showLoadDialog() {
		if (this.loadButton.isEnabled()) {
			hideMain();
			this.savedGames.showLoad();
		}
	}

	void setSaveAndLoadEnabled(final boolean enabled) {
		this.saveButton.setEnabled(enabled);
		this.loadButton.setEnabled(enabled);
	}

	private void hideMain() {
		this.mainPanel.setVisible(false);
	}

	private void showMain() {
		this.helpPanel.setVisible(false);
		this.tipsPanel.setVisible(false);
		this.mainPanel.setVisible(true);
		SavedGameDialog.fit(this.holder, this.backdrop, this.mainPanel, this.rootFrame, this.uiViewport);
	}

	private void showHelp() {
		hideMain();
		this.helpPanel.setVisible(true);
		SavedGameDialog.fit(this.holder, this.backdrop, this.helpPanel, this.rootFrame, this.uiViewport);
		this.helpText.setText(this.help, this.rootFrame, this.uiViewport);
		this.helpText.getScrollBarFrame().setValue(this.rootFrame, this.uiViewport, 100);
	}

	private void showTips() {
		hideMain();
		this.tipsPanel.setVisible(true);
		SavedGameDialog.fit(this.holder, this.backdrop, this.tipsPanel, this.rootFrame, this.uiViewport);
		showTip(this.tipIndex);
	}

	private void showTip(final int index) {
		if (this.tips.isEmpty()) {
			return;
		}
		this.tipIndex = Math.max(0, Math.min(this.tips.size() - 1, index));
		this.tipsText.setText(this.tips.get(this.tipIndex), this.rootFrame, this.uiViewport);
		this.tipsBack.setEnabled(this.tipIndex > 0);
		this.tipsNext.setEnabled(this.tipIndex < (this.tips.size() - 1));
	}

	/** Whether a panel of this class is up, so that the keys belong to it and the back key returns to the menu. */
	boolean keyDown(final int keycode) {
		if (this.savedGames.isShowing()) {
			return this.savedGames.keyDown(keycode);
		}
		if (this.options.isShowing()) {
			return this.options.keyDown(keycode);
		}
		if (this.helpPanel.isVisible() || this.tipsPanel.isVisible()) {
			if ((keycode == Input.Keys.ESCAPE) || (keycode == Input.Keys.ENTER)) {
				showMain();
			}
			else {
				MenuHotkey.press(keycode, this.helpOk, this.tipsOk, this.tipsBack, this.tipsNext);
			}
			return true;
		}
		return false;
	}

	void followKeyboard(final float holderTop) {
		this.savedGames.followKeyboard(holderTop);
	}

	boolean isSubPanelShowing() {
		return this.savedGames.isShowing() || this.options.isShowing() || this.helpPanel.isVisible()
				|| this.tipsPanel.isVisible();
	}

	void pressed(final UIFrame frame) {
		this.options.pressed(frame);
	}

	private static List<String> readTips(final DataSource dataSource) {
		final Map<Integer, String> numbered = new HashMap<>();
		final Pattern line = Pattern.compile("Tip([0-9]+)=\"?(.*?)\"?\\s*");
		for (final String text : readLines(dataSource, "UI\\TipStrings.txt")) {
			final Matcher matcher = line.matcher(text);
			if (matcher.matches()) {
				numbered.put(Integer.valueOf(matcher.group(1)), matcher.group(2));
			}
		}
		final List<String> tips = new ArrayList<>();
		for (int i = 1; numbered.containsKey(i); i++) {
			tips.add(numbered.get(i));
		}
		return tips;
	}

	private static Map<String, String> knownHelpKeys() {
		final Map<String, String> keys = new HashMap<>();
		keys.put("QLog", "F9");
		keys.put("Menu", "F10");
		keys.put("Ally", "F11");
		keys.put("Chat", "F12");
		keys.put("MEnd", "F10, E");
		keys.put("MSav", "F10, S");
		keys.put("MLod", "F10, L");
		keys.put("MHlp", "F10, H");
		keys.put("sbgp", "Tab");
		keys.put("sidw", "F8");
		for (int i = 0; i <= 9; i++) {
			keys.put("Ctr" + i, "Ctrl - " + i + ", " + i);
		}
		return keys;
	}

	/**
	 * The help text names its keys by token and the data does not say what they stand for. An entry is shown only
	 * when this engine has that key; the line after a token line is the entry's description.
	 */
	private static String readHelp(final DataSource dataSource) {
		final Map<String, String> keys = knownHelpKeys();
		final List<String> lines = readLines(dataSource, "UI\\HelpStrings.txt");
		final StringBuilder help = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			final Matcher matcher = HELP_TOKEN.matcher(lines.get(i));
			boolean known = true;
			final StringBuffer replaced = new StringBuffer();
			while (matcher.find()) {
				final String key = keys.get(matcher.group(1));
				known &= key != null;
				matcher.appendReplacement(replaced, key == null ? "" : Matcher.quoteReplacement(key));
			}
			matcher.appendTail(replaced);
			if (known) {
				help.append(replaced).append('\n');
			}
			else if (((i + 1) < lines.size()) && lines.get(i + 1).startsWith("-")) {
				i++;
			}
		}
		return help.toString();
	}

	private static List<String> readLines(final DataSource dataSource, final String path) {
		final List<String> lines = new ArrayList<>();
		try (InputStream stream = dataSource.getResourceAsStream(path)) {
			if (stream != null) {
				final BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
				String line;
				while ((line = reader.readLine()) != null) {
					lines.add(line);
				}
			}
		}
		catch (final IOException exc) {
			Log.error(exc);
		}
		return lines;
	}
}
