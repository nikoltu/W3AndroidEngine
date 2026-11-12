package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;

/**
 * A player's mouse events go through the command log like any other input: the recorded command fires the same
 * trigger responses when a replay applies it, and the event waits for the end of the step with the orders.
 */
class CPlayerMouseEventTest {
	private static final class CapturingEvent extends CPlayerEvent {
		private final List<CommonTriggerExecutionScope> fired;

		CapturingEvent(final JassGameEventsWar3 eventType, final List<CommonTriggerExecutionScope> fired) {
			super(null, null, null, eventType, null);
			this.fired = fired;
		}

		@Override
		public void fire(final CPlayer player, final TriggerExecutionScope scope) {
			this.fired.add((CommonTriggerExecutionScope) scope);
		}
	}

	private static CSimulation gameListeningTo(final JassGameEventsWar3 eventType,
			final List<CommonTriggerExecutionScope> fired) {
		final CSimulation game = TestObjects.allocate(CSimulation.class);
		final List<CPlayer> players = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			final CPlayer player = TestObjects.allocate(CPlayer.class);
			final EnumMap<JassGameEventsWar3, List<CPlayerEvent>> events = new EnumMap<>(JassGameEventsWar3.class);
			TestObjects.set(player, "eventTypeToEvents", events);
			if (i == 1) {
				final List<CPlayerEvent> list = new ArrayList<>();
				list.add(new CapturingEvent(eventType, fired));
				events.put(eventType, list);
			}
			players.add(player);
		}
		TestObjects.set(game, "players", players);
		return game;
	}

	@Test
	void recordedMouseEventFiresTheSameResponsesWhenReplayed() {
		final List<CommonTriggerExecutionScope> fired = new ArrayList<>();
		final CSimulation game = gameListeningTo(JassGameEventsWar3.EVENT_PLAYER_MOUSE_DOWN, fired);
		assertTrue(game.getPlayer(1).hasPlayerEvents(JassGameEventsWar3.EVENT_PLAYER_MOUSE_DOWN));
		assertFalse(game.getPlayer(1).hasPlayerEvents(JassGameEventsWar3.EVENT_PLAYER_MOUSE_MOVE));
		assertFalse(game.getPlayer(0).hasPlayerEvents(JassGameEventsWar3.EVENT_PLAYER_MOUSE_DOWN));

		final CPlayerCommandRecorder recorder = new CPlayerCommandRecorder(game, 1,
				new CPlayerUnitOrderExecutor(game, 1));
		final CPlayerUnitOrderListenerDelaying delaying = new CPlayerUnitOrderListenerDelaying(recorder);
		delaying.mouseEvent(JassGameEventsWar3.EVENT_PLAYER_MOUSE_DOWN.getEventId(), -1250.5f, 384.25f, 3);
		// A move nobody listens to, and an event id that is no mouse event, fire nothing.
		delaying.mouseEvent(JassGameEventsWar3.EVENT_PLAYER_MOUSE_MOVE.getEventId(), 1f, 2f, 0);
		delaying.mouseEvent(JassGameEventsWar3.EVENT_PLAYER_END_CINEMATIC.getEventId(), 1f, 2f, 1);
		assertEquals(0, fired.size());
		delaying.publishDelayedActions();
		assertEquals(1, fired.size());
		assertMouseScope(fired.get(0), JassGameEventsWar3.EVENT_PLAYER_MOUSE_DOWN, -1250.5f, 384.25f, 3);

		final List<CPlayerCommandLog.Entry> entries = game.getPlayerCommandLog().getEntries();
		assertEquals(3, entries.size());
		final List<CommonTriggerExecutionScope> replayedFired = new ArrayList<>();
		final CSimulation replayed = gameListeningTo(JassGameEventsWar3.EVENT_PLAYER_MOUSE_DOWN, replayedFired);
		for (final CPlayerCommandLog.Entry entry : entries) {
			entry.applyTo(new CPlayerUnitOrderExecutor(replayed, entry.getPlayerIndex()));
		}
		assertEquals(1, replayedFired.size());
		assertMouseScope(replayedFired.get(0), JassGameEventsWar3.EVENT_PLAYER_MOUSE_DOWN, -1250.5f, 384.25f, 3);
	}

	@Test
	void moveHasNoButton() {
		final List<CommonTriggerExecutionScope> fired = new ArrayList<>();
		final CSimulation game = gameListeningTo(JassGameEventsWar3.EVENT_PLAYER_MOUSE_MOVE, fired);
		new CPlayerUnitOrderExecutor(game, 1).mouseEvent(JassGameEventsWar3.EVENT_PLAYER_MOUSE_MOVE.getEventId(), 64f,
				-32f, 0);
		assertEquals(1, fired.size());
		assertMouseScope(fired.get(0), JassGameEventsWar3.EVENT_PLAYER_MOUSE_MOVE, 64f, -32f, 0);
		// Triggers run from the event's trigger keep its responses.
		assertMouseScope(new CommonTriggerExecutionScope(fired.get(0)), JassGameEventsWar3.EVENT_PLAYER_MOUSE_MOVE,
				64f, -32f, 0);
	}

	private static void assertMouseScope(final CommonTriggerExecutionScope scope, final JassGameEventsWar3 event,
			final float x, final float y, final int button) {
		assertEquals(event, scope.getTriggerEventId());
		assertEquals(x, scope.getMouseX());
		assertEquals(y, scope.getMouseY());
		assertEquals(button, scope.getMouseButton());
	}
}
