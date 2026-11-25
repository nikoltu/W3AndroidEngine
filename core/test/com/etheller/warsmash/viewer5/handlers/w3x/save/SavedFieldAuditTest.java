package com.etheller.warsmash.viewer5.handlers.w3x.save;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;

/**
 * Walks the classes a save can reach from the simulation and lists the fields a save stores although they are
 * {@code transient} or look like caches or hold what belongs to rendering. The graph stores a field that is
 * {@code transient} in the Java sense and leaves out only {@link SavedTransient} ones, so each such field needs a
 * verdict here: it is state and stays in the save, or it is worked out again after a load and then carries the
 * annotation. A new field of this kind fails the test until it has been judged.
 */
class SavedFieldAuditTest {
	private static final String ROOT_PACKAGE = "com/etheller/warsmash";
	private static final String NAME_PREFIX = "com.etheller.warsmash.viewer5.handlers.w3x.simulation.";

	// Names and types of what is usually worked out from other state. Render and UI objects are not listed: the save
	// rules drop them (SavedGameRules.isDroppedClass), so they never reach the file.
	private static final Pattern CACHE_NAME = Pattern.compile("(?i)cache|scratch|memo");
	private static final Pattern CACHE_TYPE = Pattern.compile("Cache$|^java[.]nio[.][A-Za-z]+Buffer$");

	private static final String[] SIMULATION_PREFIXES = { "com.etheller.warsmash.viewer5.handlers.w3x.simulation.",
			"com.etheller.interpreter.", "com.etheller.warsmash.parsers.jass.",
			"com.etheller.warsmash.parsers.w3x.objectdata.",
			"com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid", "com.etheller.warsmash.util." };

	/** Fields that are {@code transient} and stored on purpose: they are state, or nothing rebuilds them. */
	private static final Set<String> STORED_TRANSIENT = new TreeSet<>(Arrays.asList(
			// The command log of the game: the replay recorder counts what is in it, and no save is made of a replay.
			"CSimulation#playerCommandLog",
			// Behaviours, orders and what a unit is in or on: state the constructor made, which a load does not run.
			"CUnit#attackBehavior", "CUnit#attackMoveBehavior", "CUnit#autocastAbility",
			"CUnit#boardTransportBehavior", "CUnit#followBehavior", "CUnit#holdPositionBehavior", "CUnit#loadedIn",
			"CUnit#moveBehavior", "CUnit#patrolBehavior", "CUnit#stopBehavior",
			// Which regions a unit is in decides which enter and leave events fire after the load; the pending ones
			// fire on a later tick.
			"CUnit#containingRegions", "CUnit#priorContainingRegions", "CUnit#pendingRegionEvents",
			"CUnit#regionEventsDeferred", "CUnit#deadScriptLife",
			// Listener sets the simulation itself subscribes to (abilities, upgrades); the interface's own
			// listeners are dropped from them and subscribe again after a load, see restoreInterfaceState.
			"CUnit#stateListenersUpdates", "CUnit#stateNotifier", "players.CPlayer#stateNotifier",
			// The very buff the unit holds in its list, taken off again by identity.
			"abilitybuilder.parser.template.StatBuffFromDataField#buff",
			"abilitybuilder.parser.template.StatBuffFromDataField#secondAtkBuff",
			// Made with the trigger and handed to every thread it runs.
			"com.etheller.interpreter.ast.scope.trigger.Trigger#triggerExecutionScope",
			// Nonzero while a trigger of an entered region runs, which a script can save from.
			"region.CRegionManager#enterTriggerDepth", "region.CRegionManager#silentEnterUnit",
			"region.CRegionManager#firingEnterTriggers",
			// Units created in a script tick whose enter events wait for the end of that tick, which a save never falls in.
			"CSimulation#lateCreationRegionEvents", "CUnit#regionEventsLate", "region.CRegionManager#lateUnits"));

	/** Fields of a render, UI or cache type that are stored on purpose. */
	private static final Set<String> STORED_RENDER_TYPED = new TreeSet<>(Arrays.asList(
			// A snapshot a script takes (CachePlayerHeroData) for the score screen, not worked out again.
			"players.CPlayer#cachedHeroLevels : java.util.List", "players.CPlayer#cachedHeroes : java.util.List"));

	@Test
	void everyStoredTransientOrRenderTypedFieldHasAVerdict() throws Exception {
		final Set<String> transientFields = new TreeSet<>();
		final Set<String> renderTyped = new TreeSet<>();
		audit(transientFields, renderTyped);

		final Set<String> unjudgedTransient = new TreeSet<>(transientFields);
		unjudgedTransient.removeAll(STORED_TRANSIENT);
		final Set<String> unjudgedRender = new TreeSet<>(renderTyped);
		unjudgedRender.removeAll(STORED_RENDER_TYPED);
		final Set<String> staleTransient = new TreeSet<>(STORED_TRANSIENT);
		staleTransient.removeAll(transientFields);
		final Set<String> staleRender = new TreeSet<>(STORED_RENDER_TYPED);
		staleRender.removeAll(renderTyped);
		assertTrue(
				unjudgedTransient.isEmpty() && unjudgedRender.isEmpty() && staleTransient.isEmpty()
						&& staleRender.isEmpty(),
				"transient without SavedTransient, not judged: " + unjudgedTransient
						+ "; render or cache typed, not judged: " + unjudgedRender + "; judged but gone: "
						+ staleTransient + " " + staleRender);
	}

	static void audit(final Set<String> transientFields, final Set<String> renderTyped) throws Exception {
		audit(transientFields, renderTyped, new TreeSet<>());
	}

	static void audit(final Set<String> transientFields, final Set<String> renderTyped, final Set<String> droppedTyped)
			throws Exception {
		final List<Class<?>> scanned = scanClasses();
		final Set<Class<?>> reachable = new HashSet<>();
		final Deque<Class<?>> queue = new ArrayDeque<>();
		queue.add(CSimulation.class);
		reachable.add(CSimulation.class);
		while (!queue.isEmpty()) {
			final Class<?> type = queue.poll();
			final List<Class<?>> next = new ArrayList<>();
			for (final Field field : savedFields(type)) {
				final Set<Class<?>> types = new HashSet<>();
				collect(field.getGenericType(), types, new HashSet<>());
				for (final Class<?> fieldType : types) {
					next.add(fieldType);
					if (SavedGameRules.isDroppedClass(fieldType) || SavedGameRules.isReplaceable(fieldType)
							|| SavedGameRules.isNameable(fieldType)) {
						continue;
					}
					for (final Class<?> candidate : scanned) {
						if ((candidate != fieldType) && walkedByTheGraph(candidate)
								&& fieldType.isAssignableFrom(candidate)) {
							next.add(candidate);
						}
					}
				}
				if (SavedGameRules.isDroppedClass(field.getType())) {
					droppedTyped.add(name(field) + " : " + field.getType().getName());
				}
				if (Modifier.isTransient(field.getModifiers())) {
					transientFields.add(name(field));
				}
				if (!SavedGameRules.isDroppedClass(field.getType()) && (CACHE_TYPE.matcher(field.getType().getName()).find()
						|| CACHE_NAME.matcher(field.getName()).find())) {
					renderTyped.add(name(field) + " : " + field.getType().getName());
				}
			}
			for (final Class<?> candidate : next) {
				if (walkedByTheGraph(candidate) && reachable.add(candidate)) {
					queue.add(candidate);
				}
			}
		}
	}

	/** Dropped, replaced and named objects are not written field by field. */
	private static boolean walkedByTheGraph(final Class<?> type) {
		return inSimulation(type) && !SavedGameRules.isDroppedClass(type) && !SavedGameRules.isReplaceable(type)
				&& !SavedGameRules.isNameable(type);
	}

	/** Interface and abstract types name many implementations; only the simulation's own are state. */
	private static boolean inSimulation(final Class<?> type) {
		final String name = type.getName();
		for (final String prefix : SIMULATION_PREFIXES) {
			if (name.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private static String name(final Field field) {
		final String owner = field.getDeclaringClass().getName();
		return (owner.startsWith(NAME_PREFIX) ? owner.substring(NAME_PREFIX.length()) : owner) + "#"
				+ field.getName();
	}

	private static List<Field> savedFields(final Class<?> type) {
		if (type.isPrimitive() || type.isArray() || type.getName().startsWith("java.")) {
			return Collections.emptyList();
		}
		try {
			return SavedObjectGraph.fieldsOf(type);
		}
		catch (final LinkageError | RuntimeException e) {
			return Collections.emptyList();
		}
	}

	/** The engine's own types a field mentions, as its class, its type arguments, its array components. */
	private static void collect(final Type type, final Set<Class<?>> out, final Set<Type> seen) {
		if (!seen.add(type)) {
			return;
		}
		if (type instanceof Class) {
			Class<?> current = (Class<?>) type;
			while (current.isArray()) {
				current = current.getComponentType();
			}
			if (!current.isPrimitive() && current.getName().startsWith("com.etheller.")) {
				out.add(current);
			}
		}
		else if (type instanceof ParameterizedType) {
			final ParameterizedType parameterized = (ParameterizedType) type;
			collect(parameterized.getRawType(), out, seen);
			for (final Type argument : parameterized.getActualTypeArguments()) {
				collect(argument, out, seen);
			}
		}
		else if (type instanceof GenericArrayType) {
			collect(((GenericArrayType) type).getGenericComponentType(), out, seen);
		}
		else if (type instanceof WildcardType) {
			for (final Type bound : ((WildcardType) type).getUpperBounds()) {
				collect(bound, out, seen);
			}
		}
		else if (type instanceof TypeVariable) {
			for (final Type bound : ((TypeVariable<?>) type).getBounds()) {
				collect(bound, out, seen);
			}
		}
	}

	/** Every class of the engine in the class path directory (or jar) the simulation was loaded from. */
	private static List<Class<?>> scanClasses() throws IOException, URISyntaxException {
		final File location = new File(CSimulation.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		final List<String> names = new ArrayList<>();
		if (location.isDirectory()) {
			final Path root = location.toPath();
			try (Stream<Path> files = Files.walk(root.resolve(ROOT_PACKAGE))) {
				files.filter(path -> path.toString().endsWith(".class"))
						.forEach(path -> names.add(root.relativize(path).toString().replace(File.separatorChar, '.')
								.replaceAll("\\.class$", "")));
			}
		}
		else {
			try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(location)) {
				zip.stream().map(entry -> entry.getName())
						.filter(entry -> entry.startsWith(ROOT_PACKAGE) && entry.endsWith(".class"))
						.forEach(entry -> names.add(entry.replace('/', '.').replaceAll("\\.class$", "")));
			}
		}
		final List<Class<?>> classes = new ArrayList<>();
		for (final String className : names) {
			try {
				classes.add(Class.forName(className, false, CSimulation.class.getClassLoader()));
			}
			catch (final LinkageError | ClassNotFoundException e) {
				// needs a platform library the test class path lacks; a save could not reach it either
			}
		}
		return classes;
	}
}
