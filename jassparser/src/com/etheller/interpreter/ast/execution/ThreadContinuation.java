package com.etheller.interpreter.ast.execution;

/**
 * What a thread runs when it has no JASS instructions: code of another script language that the thread resumes each
 * time it is awake. A sleep of the thread (TriggerSleepAction) suspends the continuation.
 */
public interface ThreadContinuation {
	/**
	 * Runs until the code finishes or the thread sleeps.
	 *
	 * @return true if the code is not finished (it waits for the thread to wake)
	 */
	boolean resume(JassThread thread);
}
