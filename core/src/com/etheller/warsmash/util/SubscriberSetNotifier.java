package com.etheller.warsmash.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public abstract class SubscriberSetNotifier<LISTENER_TYPE> {
	protected final Set<LISTENER_TYPE> set; // bad for iteration but there
											// should never be a dude subscribed
											// 2x

	public SubscriberSetNotifier() {
		// Most notifiers have nobody subscribed but are told of every change; iterating an empty set then hands
		// out the shared empty iterator instead of a new one.
		this.set = new HashSet<LISTENER_TYPE>() {
			@Override
			public Iterator<LISTENER_TYPE> iterator() {
				return isEmpty() ? Collections.<LISTENER_TYPE>emptyIterator() : super.iterator();
			}
		};
	}

	public final void subscribe(final LISTENER_TYPE listener) {
		this.set.add(listener);
	}

	public final void unsubscribe(final LISTENER_TYPE listener) {
		this.set.remove(listener);
	}

}
