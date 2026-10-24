package com.etheller.interpreter.ast.function;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.etheller.interpreter.ast.definition.JassParameterDefinition;
import com.etheller.interpreter.ast.scope.Scope;
import com.etheller.interpreter.ast.type.JassTypeToken;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.JassType;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;

public class JassNativeManager {
	private final Map<String, JassFunction> nameToNativeCode;
	private final Set<String> registeredNativeNames = new HashSet<>();
	private final Map<String, HandleJassValue> convertedConstants = new HashMap<>();

	public JassNativeManager() {
		this.nameToNativeCode = new HashMap<>();
	}

	public void createNative(final String name, final JassFunction nativeCode) {
		this.nameToNativeCode.put(name, nativeCode);
	}

	public void registerNativeCode(final int lineNo, final String sourceFile, final String name,
			final List<JassParameterDefinition> parameterDefinitions, final JassTypeToken returnTypeToken,
			final Scope globals) {
		if (this.registeredNativeNames.contains(name)) {
			System.err.println("Native already registered: " + name);
			return;
//			throw new RuntimeException("Native already registered: " + name);
		}
		JassFunction nativeCode = this.nameToNativeCode.remove(name);
		final List<JassParameter> parameters = JassParameterDefinition.resolve(parameterDefinitions, globals);
		final JassType returnType = returnTypeToken.resolve(globals);
		if ((nativeCode == null) && isConstantConverter(name, parameters, returnType)) {
			nativeCode = createConstantConverter((HandleJassType) returnType);
		}
		globals.defineFunction(lineNo, sourceFile, name,
				new NativeJassFunction(parameters, returnType, name, nativeCode));
		this.registeredNativeNames.add(name);
	}

	/**
	 * Convert* natives only map an integer to a typed constant. Those the engine does not implement still
	 * return a distinct constant per value, so scripts that store them in globals keep working.
	 */
	private static boolean isConstantConverter(final String name, final List<JassParameter> parameters,
			final JassType returnType) {
		return name.startsWith("Convert") && (parameters.size() == 1)
				&& (parameters.get(0).getType() == JassType.INTEGER) && (returnType instanceof HandleJassType);
	}

	private JassFunction createConstantConverter(final HandleJassType returnType) {
		return (arguments, globalScope, triggerScope) -> {
			final int value = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
			return this.convertedConstants.computeIfAbsent(returnType.getName() + ":" + value,
					key -> new HandleJassValue(returnType, new ConvertedConstant(returnType.getName(), value)));
		};
	}

	public void checkUnregisteredNatives() {
		// TODO maybe do this later
	}
}
