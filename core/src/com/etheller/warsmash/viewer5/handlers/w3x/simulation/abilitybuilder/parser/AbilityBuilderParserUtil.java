package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.AbilityBuilderGsonBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

public class AbilityBuilderParserUtil {
	public static void loadAbilityBuilderFiles(final AbilityBuilderFileListener listener) {
		loadAbilityBuilderFiles(listener, "abilityBehaviors");
	}
	public static void loadAbilityBuilderFiles(final AbilityBuilderFileListener listener, String folder) {
		final Gson gson = AbilityBuilderGsonBuilder.create();
		try {
			// Internal files resolve to the APK assets on Android and to the working directory on desktop.
			final FileHandle abilityBehaviorsDir = Gdx.files != null ? Gdx.files.internal(folder)
					: new FileHandle(new File(folder));
			loadAbilityBuilderFiles(gson, abilityBehaviorsDir, listener);
		}
		catch (final Exception e) {
			e.printStackTrace();
		}
	}

	private static void loadAbilityBuilderFiles(final Gson gson, final FileHandle directory,
			final AbilityBuilderFileListener listener) {
		final FileHandle[] children = directory.list();
		Arrays.sort(children, (a, b) -> a.name().compareTo(b.name()));
		for (final FileHandle child : children) {
			if (child.extension().equalsIgnoreCase("json")) {
				loadAbilityBuilderFile(gson, child.reader("UTF-8"), child.name(), listener);
			}
			else {
				loadAbilityBuilderFiles(gson, child, listener);
			}
		}
	}

	public static void loadAbilityBuilderFile(final Gson gson, final File abilityBehaviorFile,
			final AbilityBuilderFileListener listener) throws FileNotFoundException {
		if (abilityBehaviorFile.isDirectory()) {
			final File[] abilityBehaviorFiles = abilityBehaviorFile.listFiles();
			if (abilityBehaviorFiles != null) {
				for (final File subAbilityBehaviorFile : abilityBehaviorFiles) {
					loadAbilityBuilderFile(gson, subAbilityBehaviorFile, listener);
				}
			}
		}
		loadAbilityBuilderFile(gson, new FileReader(abilityBehaviorFile), abilityBehaviorFile.getName(), listener);
	}

	private static void loadAbilityBuilderFile(final Gson gson, final Reader reader, final String name,
			final AbilityBuilderFileListener listener) {
		try (reader) {
			final AbilityBuilderFile behaviors = gson.fromJson(reader, AbilityBuilderFile.class);
			for (final AbilityBuilderParser behavior : behaviors.getAbilityList()) {
				listener.callback(behavior);
			}
		}
		catch (final JsonParseException e) {
			System.err.println("Failed to load Ability Builder config file: " + name);
			e.printStackTrace();
		}
		catch (final IllegalArgumentException e) {
			System.err.println("Failed to load Ability Builder config file: " + name);
			e.printStackTrace();
		}
		catch (final IOException e) {
			e.printStackTrace();
		}
	}

	public static interface AbilityBuilderFileListener {
		void callback(AbilityBuilderParser behavior);
	}
}
