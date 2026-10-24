package com.etheller.interpreter.ast.function;

import com.etheller.interpreter.ast.util.CHandle;

/**
 * The value of a Convert* native that the engine has no dedicated type for, such as the field types of the
 * object editor API: it keeps the integer so later natives can still tell the constants apart.
 */
public final class ConvertedConstant implements CHandle {
	private final String typeName;
	private final int value;

	public ConvertedConstant(final String typeName, final int value) {
		this.typeName = typeName;
		this.value = value;
	}

	public String getTypeName() {
		return this.typeName;
	}

	public int getValue() {
		return this.value;
	}

	@Override
	public int getHandleId() {
		return this.value;
	}

	@Override
	public String toString() {
		return this.typeName + "(" + this.value + ")";
	}
}
