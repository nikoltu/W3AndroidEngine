package com.etheller.warsmash.input.touch;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

/** A captured mouse keeps its own pointer inside the screen, and the edge scroll reads it there. */
class CapturedMouseTest {
	private final List<String> calls = new ArrayList<>();
	private Input savedInput;
	private Graphics savedGraphics;

	@BeforeEach
	void setUp() {
		this.savedInput = Gdx.input;
		this.savedGraphics = Gdx.graphics;
		final InputAdapter screen = new InputAdapter() {
			@Override
			public boolean touchDown(final int x, final int y, final int pointer, final int button) {
				calls.add("down" + button + "@" + x + "," + y);
				return false;
			}

			@Override
			public boolean touchUp(final int x, final int y, final int pointer, final int button) {
				calls.add("up" + button);
				return false;
			}

			@Override
			public boolean mouseMoved(final int x, final int y) {
				calls.add("move@" + x + "," + y);
				return false;
			}

			@Override
			public boolean touchDragged(final int x, final int y, final int pointer) {
				calls.add("drag@" + x + "," + y);
				return false;
			}
		};
		Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] { Input.class },
				(proxy, method, args) -> method.getName().equals("getInputProcessor") ? screen : null);
		Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
				new Class<?>[] { Graphics.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "getWidth":
						return 100;
					case "getHeight":
						return 50;
					default:
						return null;
					}
				});
		VirtualCursor.mouseAt(50, 25);
		CapturedMouse.setActive(true);
		CapturedMouse.drain();
	}

	@AfterEach
	void tearDown() {
		CapturedMouse.setActive(false);
		CapturedMouse.drain();
		Gdx.input = this.savedInput;
		Gdx.graphics = this.savedGraphics;
	}

	@Test
	void pointerStopsAtTheScreenEdgesWhereTheEdgeScrollReadsIt() {
		CapturedMouse.post(500, -500, 0, 0, 0);
		CapturedMouse.drain();
		assertEquals(99, VirtualCursor.getMouseX());
		assertEquals(0, VirtualCursor.getMouseY());
		assertTrue(VirtualCursor.isMouseOver());
		assertEquals(List.of("move@99,0"), this.calls);
	}

	@Test
	void buttonsClickAndDragAtThePointer() {
		CapturedMouse.post(10, 0, 1, 0, 0);
		CapturedMouse.post(5, 0, 1, 0, 0);
		CapturedMouse.post(0, 0, 0, 0, 0);
		CapturedMouse.drain();
		assertEquals(List.of("move@60,25", "down" + Input.Buttons.LEFT + "@60,25", "drag@65,25",
				"up" + Input.Buttons.LEFT), this.calls);
	}

	@Test
	void cursorIsDrawnForACapturedMouseOnATouchDevice() {
		VirtualCursor.setTouchDevice(true);
		try {
			assertFalse(VirtualCursor.isCursorHidden());
			CapturedMouse.setActive(false);
			assertTrue(VirtualCursor.isCursorHidden());
		}
		finally {
			VirtualCursor.setTouchDevice(false);
		}
	}
}
