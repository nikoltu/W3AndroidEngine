package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.utils.IntMap;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderController;

/**
 * A handle the simulation gives out again (a removed item's or destructable's, the next step) while the render side
 * still plays the events of the object that held it before: the events of the old object reach the old object, the
 * new one is named only from its creation event, and nothing the old one left behind stays.
 */
class RecycledHandleTest {
	private static final int H = 0x100010;
	private static final sun.misc.Unsafe UNSAFE;
	private static int nextSerial = 1;
	static {
		try {
			final Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			UNSAFE = (sun.misc.Unsafe) field.get(null);
		}
		catch (final ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static <T extends CWidget> T widget(final Class<T> type, final int handle) {
		try {
			@SuppressWarnings("unchecked")
			final T widget = (T) UNSAFE.allocateInstance(type);
			final Field handleId = CWidget.class.getDeclaredField("handleId");
			handleId.setAccessible(true);
			handleId.setInt(widget, handle);
			final Field serial = CWidget.class.getDeclaredField("creationSerial");
			serial.setAccessible(true);
			serial.setInt(widget, nextSerial++);
			return widget;
		}
		catch (final ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * The render side as the map viewer's simulation thread object keeps it: handles name objects from their creation
	 * event to their removal, objects built ahead wait per handle, and an event before a creation event names the
	 * earliest of those.
	 */
	private static final class RenderSide
			implements RenderEventPlayer.Resolver, RenderEventPlayer.WorldPeers, PeerQueue.Builder {
		final PeerQueue queue = new PeerQueue(this);
		final IntMap<CWidget> named = new IntMap<>();
		final IntMap<ArrayDeque<Object>> unplayed = new IntMap<>();
		/** The render peers: by object, as the real controller keeps them. */
		final IdentityHashMap<Object, String> peers = new IdentityHashMap<>();
		final List<String> calls = new ArrayList<>();

		@Override
		public void build(final int handle, final Object data) {
			final Object object = data instanceof WorldPeerData.Item ? ((WorldPeerData.Item) data).item
					: ((WorldPeerData.Destructable) data).destructable;
			this.peers.put(object, "peer");
			ArrayDeque<Object> objects = this.unplayed.get(handle);
			if (objects == null) {
				objects = new ArrayDeque<>();
				this.unplayed.put(handle, objects);
			}
			objects.addLast(object);
		}

		Object beforeCreation(final int handle) {
			final ArrayDeque<Object> built = this.unplayed.get(handle);
			if ((built == null) && this.queue.isWaiting(handle)) {
				this.queue.present(handle);
				return this.unplayed.get(handle).peekFirst();
			}
			return built == null ? null : built.peekFirst();
		}

		private void created(final int handle, final Object named) {
			this.queue.present(handle);
			final ArrayDeque<Object> objects = this.unplayed.get(handle);
			final Object object = objects.pollFirst();
			if (objects.isEmpty()) {
				this.unplayed.remove(handle);
			}
			if (object != named) {
				throw new IllegalStateException("creation of " + handle + " out of order");
			}
			this.named.put(handle, (CWidget) object);
		}

		@Override
		public CWidget widget(final int handle) {
			final CWidget widget = this.named.get(handle);
			if (widget != null) {
				return widget;
			}
			final Object made = beforeCreation(handle);
			return made instanceof CWidget ? (CWidget) made : null;
		}

		@Override
		public CUnit unit(final int handle) {
			final CWidget widget = widget(handle);
			return widget instanceof CUnit ? (CUnit) widget : null;
		}

		@Override
		public CItem item(final int handle) {
			final CWidget widget = widget(handle);
			return widget instanceof CItem ? (CItem) widget : null;
		}

		@Override
		public CDestructable destructable(final int handle) {
			final CWidget widget = widget(handle);
			return widget instanceof CDestructable ? (CDestructable) widget : null;
		}

		@Override
		public CProjectile projectile(final int id) {
			return null;
		}

		@Override
		public void itemRemoved(final int handle, final CItem item) {
			if (this.named.get(handle) == item) {
				this.named.remove(handle);
			}
		}

		@Override
		public void destructableRemoved(final int handle, final CDestructable destructable) {
			if (this.named.get(handle) == destructable) {
				this.named.remove(handle);
			}
		}

		@Override
		public void unitCreated(final int handle, final War3ID typeId, final int playerIndex, final float x,
				final float y, final float facing, final WorldPeerData.Unit data) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void itemCreated(final int handle, final War3ID typeId, final float x, final float y,
				final WorldPeerData.Item data) {
			created(handle, data.item);
		}

		@Override
		public void destructableCreated(final int handle, final War3ID typeId, final float x, final float y,
				final float z, final float facing, final float scale, final int variation,
				final WorldPeerData.Destructable data) {
			created(handle, data.destructable);
		}

		/** The real controller: removes the peer of the object it is handed, and notes what it was asked. */
		SimulationRenderController controller() {
			return (SimulationRenderController) Proxy.newProxyInstance(RecycledHandleTest.class.getClassLoader(),
					new Class<?>[] { SimulationRenderController.class }, (proxy, method, args) -> {
						final Object object = args == null ? null : args[args.length > 1 ? 1 : 0];
						switch (method.getName()) {
						case "removeItem":
						case "removeDestructable":
							if (this.peers.remove(args[0]) == null) {
								throw new NullPointerException("no peer for " + args[0]);
							}
							this.calls.add(method.getName() + " " + serial(args[0]));
							return null;
						default:
							this.calls.add(method.getName() + " " + serial(object));
							return null;
						}
					});
		}
	}

	private static String serial(final Object widget) {
		return widget instanceof CWidget ? "#" + ((CWidget) widget).getCreationSerial() : String.valueOf(widget);
	}

	private static RenderEvent event(final int code, final int a, final Object o0) {
		final RenderEvent event = new RenderEvent();
		event.code = code;
		event.a = a;
		event.o0 = o0;
		return event;
	}

	private static RenderEvent itemCreate(final CItem item) {
		return event(RenderCallCodes.ITEM_CREATE, item.getHandleId(),
				new WorldPeerData.Item(item, new War3ID(1), 0, 0, 0));
	}

	private static RenderEvent destructableCreate(final CDestructable destructable) {
		return event(RenderCallCodes.DESTRUCTABLE_CREATE, destructable.getHandleId(),
				new WorldPeerData.Destructable(destructable, new War3ID(1), 0, 0, Float.NaN, 0, 1, 0));
	}

	@Test
	void anItemRemovedBeforeItsHandleIsGivenAgainLeavesNoGhost() {
		final RenderSide side = new RenderSide();
		final RenderEventPlayer player = new RenderEventPlayer(side.controller(), side, null, side);
		final CItem first = widget(CItem.class, H);
		final CItem second = widget(CItem.class, H);
		final CUnit hero = widget(CUnit.class, H + 1);
		side.named.put(hero.getHandleId(), hero);

		// step N: the first item is made; step N+1: a tome is used and removed; step N+2: an item drops with its handle
		side.queue.announce(H, new WorldPeerData.Item(first, new War3ID(1), 0, 0, 0));
		player.play(itemCreate(first));
		side.queue.announce(H, new WorldPeerData.Item(second, new War3ID(1), 0, 0, 0));
		// the render frame builds the dropped item's peer before the events of N+1 are played
		side.queue.beginFrame(Long.MAX_VALUE, 100);
		assertTrue(side.peers.containsKey(second));

		final RenderEvent pickUp = event(RenderCallCodes.UI_GET_ITEM_SOUND, hero.getHandleId(), first);
		pickUp.b = H;
		player.play(pickUp);
		// an event that names the handle only still means the first item until its removal is played
		assertSame(first, side.widget(H));
		player.play(event(RenderCallCodes.ITEM_REMOVE, H, first));
		assertFalse(side.peers.containsKey(first));
		assertTrue(side.peers.containsKey(second));
		assertSame(second, side.widget(H), "between the removal and the creation the handle names the next item");
		player.play(itemCreate(second));
		player.play(event(RenderCallCodes.ITEM_UPDATE_MODEL, H, second));
		player.play(event(RenderCallCodes.ITEM_REMOVE, H, second));

		assertEquals(List.of("spawnUIUnitGetItemSound #" + first.getCreationSerial(),
				"removeItem #" + first.getCreationSerial(), "updateItemModel #" + second.getCreationSerial(),
				"removeItem #" + second.getCreationSerial()), side.calls);
		assertTrue(side.peers.isEmpty());
		assertNull(side.named.get(H), "a removed item's handle names nothing");
		assertEquals(0, side.unplayed.size);
	}

	@Test
	void aDestructableMadeAgainWithTheHandleIsNotTheOneRemoved() {
		final RenderSide side = new RenderSide();
		final RenderEventPlayer player = new RenderEventPlayer(side.controller(), side, null, side);
		final CDestructable removed = widget(CDestructable.class, H);
		final CDestructable made = widget(CDestructable.class, H);
		side.queue.announce(H, new WorldPeerData.Destructable(removed, new War3ID(1), 0, 0, Float.NaN, 0, 1, 0));
		player.play(destructableCreate(removed));

		// RemoveDestructable, then CreateDestructable in the next step: its peer is built before the removal is played
		side.queue.announce(H, new WorldPeerData.Destructable(made, new War3ID(1), 0, 0, Float.NaN, 0, 1, 0));
		final RenderEvent effect = event(RenderCallCodes.EFFECT_OVER_DESTRUCTABLE, 7, null);
		effect.c = H;
		player.play(effect);
		assertFalse(side.peers.containsKey(made), "naming the held handle builds no successor");
		player.play(event(RenderCallCodes.DESTRUCTABLE_REMOVE, H, removed));
		player.play(destructableCreate(made));
		player.play(effect);

		assertEquals(List.of("createSpellEffectOverDestructable #" + removed.getCreationSerial(),
				"removeDestructable #" + removed.getCreationSerial(),
				"createSpellEffectOverDestructable #" + made.getCreationSerial()), side.calls);
		assertSame(made, side.named.get(H));
		assertTrue(side.peers.containsKey(made));
	}

	@Test
	void theEarliestWaitingObjectOfAHandleIsBuiltFirst() {
		final List<Object> built = new ArrayList<>();
		final PeerQueue queue = new PeerQueue((handle, data) -> built.add(data));
		queue.announce(H, "first");
		queue.announce(H + 1, "other");
		queue.announce(H, "second");
		assertFalse(queue.present(H));
		assertEquals(List.of("first"), built);
		assertTrue(queue.isWaiting(H));
		queue.present(H);
		assertEquals(List.of("first", "other", "second"), built);
		assertFalse(queue.isWaiting(H));
	}

	@Test
	void aCreationEventOutOfOrderIsAnError() {
		final RenderSide side = new RenderSide();
		final RenderEventPlayer player = new RenderEventPlayer(side.controller(), side, null, side);
		final CItem first = widget(CItem.class, H);
		final CItem second = widget(CItem.class, H);
		side.queue.announce(H, new WorldPeerData.Item(first, new War3ID(1), 0, 0, 0));
		side.queue.announce(H, new WorldPeerData.Item(second, new War3ID(1), 0, 0, 0));
		assertThrows(IllegalStateException.class, () -> player.play(itemCreate(second)));
	}

	@Test
	void eventsThatFindTheRingFullGoIntoTheNextFrame() {
		final RenderEventPool pool = new RenderEventPool();
		final SimFrameRing ring = new SimFrameRing(3, pool);
		final FrameEventSink sink = new FrameEventSink(ring, pool, () -> 5);
		for (int i = 0; i < 3; i++) {
			sink.emit(sink.begin(RenderCallCodes.UNIT_READY_SOUND));
			ring.publish();
		}
		assertNull(ring.beginWrite());
		// a command's events while the render side holds every frame: kept, not thrown
		final RenderEvent first = sink.begin(RenderCallCodes.UNIT_READY_SOUND);
		first.a = 1;
		sink.emit(first);
		final RenderEvent second = sink.begin(RenderCallCodes.UNIT_READY_SOUND);
		second.a = 2;
		sink.emit(second);
		assertTrue(sink.hasPending());
		assertEquals(2, sink.waitingCount());
		ring.release(1);
		final SimFrame frame = ring.beginWrite();
		assertEquals(2, frame.eventCount());
		assertEquals(1, frame.event(0).a);
		assertEquals(2, frame.event(1).a);
		assertEquals(0, sink.waitingCount());
	}

	@Test
	void aCommandWhoseTargetHandleNamesALaterWidgetIsNotApplied() {
		final OrderMessage message = new OrderMessage();
		message.targetCount = 1;
		message.targetHandle[0] = H;
		message.targetSerial[0] = 5;
		assertTrue(ThreadedOrderListener.targetsStillNamed(message, handle -> 5));
		assertTrue(ThreadedOrderListener.targetsStillNamed(message, handle -> 0), "a target gone is left to the order");
		assertFalse(ThreadedOrderListener.targetsStillNamed(message, handle -> 6));
		message.targetSerial[0] = 0;
		assertTrue(ThreadedOrderListener.targetsStillNamed(message, handle -> 6), "nothing shown to check");
	}
}
