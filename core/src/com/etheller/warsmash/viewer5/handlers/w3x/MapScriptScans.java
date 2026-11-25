package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.etheller.warsmash.util.War3ID;

/**
 * What the loading reads out of the map's script with patterns, found on a helper thread: a script of several
 * megabytes took a pattern a quarter of a second each, in a frame of the loading screen that was not drawn meanwhile.
 * A task asks {@link #isDone} for what it needs and waits for its next turn when the helper has not got to it.
 */
final class MapScriptScans {
	final CompletableFuture<List<String>> ambientLabels;
	final CompletableFuture<List<String>> soundFiles;
	final CompletableFuture<List<String>> labelParameters;
	final CompletableFuture<Set<String>> labelCreations;
	final CompletableFuture<List<String>> preloaders;
	final CompletableFuture<Set<String>> preloadedFiles;
	final CompletableFuture<List<String>> skyModels;
	final CompletableFuture<Set<String>> effectModels;
	final CompletableFuture<List<War3ID>> units;
	final CompletableFuture<List<War3ID>> items;
	final CompletableFuture<List<War3ID>> destructables;
	final CompletableFuture<Map<War3ID, Set<Integer>>> variations;
	final CompletableFuture<List<War3ID>> rawCodes;

	/** Starts the scans, in the order the loading needs their results. */
	MapScriptScans(final String script) {
		final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
			final Thread thread = new Thread(runnable, "script-scan");
			thread.setDaemon(true);
			thread.setPriority(Thread.NORM_PRIORITY - 1);
			return thread;
		});
		this.ambientLabels = CompletableFuture.supplyAsync(
				() -> groups(MapViewerPrewarm.SCRIPT_LABEL_SOUND, script, false), executor);
		this.soundFiles = CompletableFuture.supplyAsync(
				() -> groups(MapViewerPrewarm.SCRIPT_FILE_SOUND, script, true), executor);
		this.labelParameters = CompletableFuture.supplyAsync(
				() -> groups(MapViewerPrewarm.SCRIPT_LABEL_PARAMETERS, script, true), executor);
		this.labelCreations = CompletableFuture.supplyAsync(() -> {
			final Set<String> found = new LinkedHashSet<>();
			MapViewerPrewarm.addMatches(found, MapViewerPrewarm.SCRIPT_LABEL_CREATE, script);
			return found;
		}, executor);
		this.preloaders = CompletableFuture.supplyAsync(
				() -> groups(MapViewerPrewarm.SCRIPT_PRELOADER, script, true), executor);
		this.preloadedFiles = CompletableFuture.supplyAsync(() -> {
			final Set<String> found = new LinkedHashSet<>();
			MapViewerPrewarm.addPreloadedFiles(found, script);
			return found;
		}, executor);
		this.skyModels = CompletableFuture.supplyAsync(
				() -> groups(MapViewerPrewarm.SCRIPT_SKY_MODEL, script, true), executor);
		this.effectModels = CompletableFuture.supplyAsync(() -> {
			final Set<String> found = new LinkedHashSet<>();
			MapViewerPrewarm.addMatches(found, MapViewerPrewarm.SCRIPT_EFFECT_MODEL, script);
			return found;
		}, executor);
		this.units = CompletableFuture.supplyAsync(
				() -> MapViewerPrewarm.createdTypes(MapViewerPrewarm.SCRIPT_UNIT_CREATION, script), executor);
		this.items = CompletableFuture.supplyAsync(
				() -> MapViewerPrewarm.createdTypes(MapViewerPrewarm.SCRIPT_ITEM_CREATION, script), executor);
		this.destructables = CompletableFuture.supplyAsync(
				() -> MapViewerPrewarm.createdTypes(MapViewerPrewarm.SCRIPT_DESTRUCTABLE_CREATION, script), executor);
		this.variations = CompletableFuture.supplyAsync(
				() -> MapViewerPrewarm.createdDestructableVariations(script), executor);
		this.rawCodes = CompletableFuture.supplyAsync(() -> MapViewerPrewarm.rawCodes(script), executor);
		executor.shutdown();
	}

	/** The first group of each match, in the script's order. */
	private static List<String> groups(final Pattern pattern, final String script, final boolean unescape) {
		final List<String> found = new ArrayList<>();
		final Matcher matcher = pattern.matcher(script);
		while (matcher.find()) {
			found.add(unescape ? MapViewerPrewarm.unescape(matcher.group(1)) : matcher.group(1));
		}
		return found;
	}

	static boolean isDone(final CompletableFuture<?>... scans) {
		for (final CompletableFuture<?> scan : scans) {
			if (!scan.isDone()) {
				return false;
			}
		}
		return true;
	}
}
