package com.etheller.warsmash.parsers.jass;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.interpreter.ast.function.ConvertedConstant;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.RemovableTriggerEvent;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.TextJustify;
import com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame;
import com.etheller.warsmash.parsers.fdf.frames.AbstractUIFrame;
import com.etheller.warsmash.parsers.fdf.frames.BackdropFrame;
import com.etheller.warsmash.parsers.fdf.frames.CheckBoxFrame;
import com.etheller.warsmash.parsers.fdf.frames.EditBoxFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.PopupMenuFrame;
import com.etheller.warsmash.parsers.fdf.frames.ScrollBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleCheckBoxFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleStatusBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.SpriteFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextAreaFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextureFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.FrameEventSink;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashUI;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.ClickableFrame;

/**
 * The Frame API of Reforged (BlzCreateFrame, BlzFrameSetPoint and the rest): a map script makes frames from the
 * templates of the game's FDF files or from a frame type, places them on the screen of this machine and reacts to
 * what the player does to them. The frames are the ones the game's own interface is made of.
 *
 * What a player does to a frame (a click, a value change, a text entry) travels as a player command, as all input
 * does, and the triggers registered for that frame run in the same step for every player and replay, with
 * GetTriggerPlayer the one who did it. A frame is told by its number in the order the script made them known
 * (created or looked up), so scripts that make frames for one player only keep the same numbers on every machine
 * only when they make them in the same order everywhere.
 */
final class FrameNatives implements FrameEventSink {
	private static final int EVENT_CONTROL_CLICK = 1;
	private static final int EVENT_MOUSE_ENTER = 2;
	private static final int EVENT_MOUSE_LEAVE = 3;
	private static final int EVENT_MOUSE_UP = 4;
	private static final int EVENT_MOUSE_DOWN = 5;
	private static final int EVENT_MOUSE_WHEEL = 6;
	private static final int EVENT_CHECKBOX_CHECKED = 7;
	private static final int EVENT_CHECKBOX_UNCHECKED = 8;
	private static final int EVENT_EDITBOX_TEXT_CHANGED = 9;
	private static final int EVENT_POPUPMENU_ITEM_CHANGED = 10;
	private static final int EVENT_MOUSE_DOUBLECLICK = 11;
	private static final int EVENT_SLIDER_VALUE_CHANGED = 13;
	private static final int EVENT_EDITBOX_ENTER = 16;
	private static final int ORIGIN_GAME_UI = 0;
	private static final int ORIGIN_WORLD_FRAME = 17;
	private static final int ORIGIN_SIMPLE_UI_PARENT = 18;
	private static final int ORIGIN_COMMAND_BUTTON = 1;
	private static final int ORIGIN_ITEM_BUTTON = 7;
	private static final int ORIGIN_MINIMAP = 8;
	private static final int ORIGIN_CHAT_MSG = 13;
	private static final int ORIGIN_UNIT_MSG = 14;
	private static final int ORIGIN_PORTRAIT = 16;
	private static final long DOUBLE_CLICK_MILLIS = 300;

	private final JassProgram program;
	private final War3MapViewer viewer;
	private final Viewport uiViewport;
	private final WarsmashUI ui;
	private final CSimulation simulation;

	private final List<UIFrame> frames = new ArrayList<>();
	private final Map<UIFrame, Integer> frameIds = new IdentityHashMap<>();
	private final Set<UIFrame> scriptFrames = new LinkedHashSet<>();
	private final Map<Integer, List<Registration>> registrations = new java.util.HashMap<>();
	private final Map<UIFrame, UIFrame> tooltips = new IdentityHashMap<>();
	private final Map<UIFrame, Integer> textSizeLimits = new IdentityHashMap<>();
	/** Minimum, maximum and step a script gave a slider or a status bar; the frames count in whole steps. */
	private final Map<UIFrame, float[]> ranges = new IdentityHashMap<>();
	private final Set<UIFrame> layoutDirty = new LinkedHashSet<>();
	private UIFrame lastClicked;
	private long lastClickMillis;
	private UIFrame worldFrame;
	private final List<UIFrame> hiddenByScript = new ArrayList<>();

	private final class Registration extends RemovableTriggerEvent {
		final GlobalScope globalScope;
		final Trigger trigger;
		final int frameId;
		final int eventType;

		Registration(final GlobalScope globalScope, final Trigger trigger, final int frameId, final int eventType) {
			super(trigger);
			this.globalScope = globalScope;
			this.trigger = trigger;
			this.frameId = frameId;
			this.eventType = eventType;
		}

		@Override
		public void remove() {
			final List<Registration> list = FrameNatives.this.registrations.get(this.frameId);
			if (list != null) {
				list.remove(this);
			}
		}
	}

	private FrameNatives(final JassProgram program, final War3MapViewer viewer, final Viewport uiViewport,
			final WarsmashUI ui) {
		this.program = program;
		this.viewer = viewer;
		this.uiViewport = uiViewport;
		this.ui = ui;
		this.simulation = viewer.simulation;
	}

	static void register(final JassProgram program, final War3MapViewer viewer, final Viewport uiViewport,
			final WarsmashUI ui) {
		final FrameNatives natives = new FrameNatives(program, viewer, uiViewport, ui);
		natives.registerCreation();
		natives.registerLayout();
		natives.registerContent();
		natives.registerState();
		natives.registerEvents();
		natives.registerCoordinates();
		natives.simulation.setFrameEventHandler(natives::onFrameEvent);
		if (ui != null) {
			ui.setFrameEventSink(natives);
		}
		viewer.scriptUiState.setFrameFlush(natives::flushLayout);
	}

	private void add(final String name, final JassFunction function) {
		this.program.getJassNativeManager().createNative(name, function);
	}

	private GameUI gameUI() {
		return this.viewer.getGameUI();
	}

	private static <T> T object(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(ObjectJassValueVisitor.<T>getInstance());
	}

	private static String string(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(StringJassValueVisitor.getInstance());
	}

	private static int integer(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(IntegerJassValueVisitor.getInstance());
	}

	private static float real(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(RealJassValueVisitor.getInstance()).floatValue();
	}

	private static boolean bool(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(BooleanJassValueVisitor.getInstance());
	}

	private static int constant(final List<JassValue> arguments, final int index) {
		final Object value = FrameNatives.<Object>object(arguments, index);
		return value instanceof ConvertedConstant ? ((ConvertedConstant) value).getValue() : -1;
	}

	private static FramePoint framePoint(final List<JassValue> arguments, final int index) {
		final int value = constant(arguments, index);
		return (value >= 0) && (value < FramePoint.values().length) ? FramePoint.values()[value] : null;
	}

	private HandleJassValue handle(final GlobalScope globalScope, final UIFrame frame) {
		if (frame != null) {
			idOf(frame);
		}
		return new HandleJassValue(globalScope.getHandleType("framehandle"), frame);
	}

	private int idOf(final UIFrame frame) {
		Integer id = this.frameIds.get(frame);
		if (id == null) {
			this.frames.add(frame);
			id = this.frames.size();
			this.frameIds.put(frame, id);
		}
		return id;
	}

	private float frameX(final float x) {
		return GameUI.convertX(this.uiViewport, x);
	}

	private float frameY(final float y) {
		return GameUI.convertY(this.uiViewport, y);
	}

	private static void anchorsDecideSize(final UIFrame frame) {
		if (frame instanceof com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame) {
			((com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame) frame).anchorsDecideSize();
		}
	}

	/** A frame the script made or changed is laid out again before the next draw. */
	private void markDirty(final UIFrame frame) {
		if (frame != null) {
			this.layoutDirty.add(frame);
		}
	}

	private void flushLayout() {
		if (this.layoutDirty.isEmpty()) {
			return;
		}
		final List<UIFrame> dirty = new ArrayList<>(this.layoutDirty);
		this.layoutDirty.clear();
		// Twice, as a frame set relative to another one that is laid out after it.
		for (int pass = 0; pass < (dirty.size() > 1 ? 2 : 1); pass++) {
			for (final UIFrame frame : dirty) {
				frame.positionBounds(gameUI(), this.uiViewport);
			}
		}
	}

	/** A frame the script made, or one inside such a frame; the others are the game's own. */
	private boolean isScriptOwned(final UIFrame frame) {
		for (UIFrame owner = frame; owner != null; owner = owner.getParent()) {
			if (this.scriptFrames.contains(owner)) {
				return true;
			}
		}
		return false;
	}

	private void created(final UIFrame frame) {
		if (frame != null) {
			markDirty(frame);
			if (frame instanceof StringFrame) {
				((StringFrame) frame).setWrapToAnchors(true);
			}
			this.scriptFrames.add(frame);
			showChosenItems(frame);
			if (this.viewer.scriptUiState.isOriginFramesHidden()) {
				idOf(frame);
			}
		}
	}

	/** A popup menu of the script's frames shows the item chosen in it, whether or not the script listens for it. */
	private void showChosenItems(final UIFrame frame) {
		if (frame instanceof PopupMenuFrame) {
			final PopupMenuFrame popup = (PopupMenuFrame) frame;
			popup.setMenuClickListener((button, index) -> popup.setSelectedIndex(gameUI(), index));
			popup.setSelectedIndex(gameUI(), 0);
		}
		if (frame instanceof AbstractUIFrame) {
			for (final UIFrame child : ((AbstractUIFrame) frame).getChildFrames()) {
				showChosenItems(child);
			}
		}
	}

	private UIFrame ownerOrRoot(final List<JassValue> arguments, final int index) {
		final UIFrame owner = object(arguments, index);
		return owner == null ? gameUI() : owner;
	}

	private void registerCreation() {
		add("BlzLoadTOCFile", (arguments, globalScope, triggerScope) -> {
			try {
				gameUI().loadTOCFile(string(arguments, 0));
				return BooleanJassValue.TRUE;
			}
			catch (final IOException | RuntimeException exc) {
				return BooleanJassValue.FALSE;
			}
		});
		add("BlzCreateFrame", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = gameUI().createFrame(string(arguments, 0), ownerOrRoot(arguments, 1),
					integer(arguments, 2), integer(arguments, 3));
			created(frame);
			return handle(globalScope, frame);
		});
		add("BlzCreateSimpleFrame", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = gameUI().createSimpleFrame(string(arguments, 0), ownerOrRoot(arguments, 1),
					integer(arguments, 2));
			created(frame);
			return handle(globalScope, frame);
		});
		add("BlzCreateFrameByType", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = gameUI().createFrameByType(string(arguments, 0), string(arguments, 1),
					ownerOrRoot(arguments, 2), string(arguments, 3), integer(arguments, 4));
			created(frame);
			return handle(globalScope, frame);
		});
		add("BlzGetFrameByName", (arguments, globalScope, triggerScope) -> {
			final String name = string(arguments, 0);
			UIFrame frame = gameUI().getFrameByNameInContext(name, integer(arguments, 1));
			// The game's own buttons carry the data names CommandButton_<n> and InventoryButton_<n>.
			if ((frame == null) && (this.ui != null) && (name != null)) {
				final boolean command = name.startsWith("CommandButton_");
				if (command || name.startsWith("InventoryButton_")) {
					try {
						frame = this.ui.getOriginFrame(command ? ORIGIN_COMMAND_BUTTON : ORIGIN_ITEM_BUTTON,
								Integer.parseInt(name.substring(name.indexOf('_') + 1)));
					}
					catch (final NumberFormatException exc) {
						frame = null;
					}
				}
			}
			return handle(globalScope, frame);
		});
		add("BlzFrameGetName", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			return new StringJassValue(frame == null || frame.getName() == null ? "" : frame.getName());
		});
		add("BlzDestroyFrame", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			if ((frame != null) && (frame != gameUI())) {
				frame.setVisible(false);
				gameUI().releaseSprites(frame);
				final UIFrame parent = frame.getParent();
				if (parent instanceof AbstractUIFrame) {
					((AbstractUIFrame) parent).remove(frame);
				}
				else {
					gameUI().remove(frame);
				}
				this.scriptFrames.remove(frame);
				final Integer id = this.frameIds.get(frame);
				if (id != null) {
					this.registrations.remove(id);
				}
			}
			return null;
		});
		add("BlzGetOriginFrame", (arguments, globalScope, triggerScope) -> {
			final int type = constant(arguments, 0);
			if ((type == ORIGIN_GAME_UI) || (type == ORIGIN_SIMPLE_UI_PARENT)) {
				return handle(globalScope, gameUI());
			}
			if (type == ORIGIN_WORLD_FRAME) {
				if (this.worldFrame == null) {
					this.worldFrame = gameUI().createSimpleFrame("WorldFrame", gameUI(), 0);
					this.worldFrame.setSetAllPoints(true);
					this.worldFrame.setVisible(true);
					this.viewer.scriptUiState.setWorldFrame(this.worldFrame);
				}
				return handle(globalScope, this.worldFrame);
			}
			return handle(globalScope, this.ui == null ? null : this.ui.getOriginFrame(type, integer(arguments, 1)));
		});
		add("BlzEnableUIAutoPosition", (arguments, globalScope, triggerScope) -> {
			this.viewer.scriptUiState.setUiAutoPosition(bool(arguments, 0));
			gameUI().setAutoPosition(bool(arguments, 0));
			return null;
		});
		// The interface of the game goes out of sight while the script's own frames stay; false brings it back.
		add("BlzHideOriginFrames", (arguments, globalScope, triggerScope) -> {
			final boolean hide = bool(arguments, 0);
			this.viewer.scriptUiState.setOriginFramesHidden(hide);
			if (hide) {
				// The minimap, the portrait and the message lines hide through place holder frames.
				if (this.ui != null) {
					for (final int type : new int[] { ORIGIN_MINIMAP, ORIGIN_CHAT_MSG, ORIGIN_UNIT_MSG,
							ORIGIN_PORTRAIT }) {
						this.ui.getOriginFrame(type, 0);
					}
				}
				for (final UIFrame child : new ArrayList<>(gameUI().getChildFrames())) {
					if (!this.scriptFrames.contains(child) && child.isVisible() && (child != this.worldFrame)) {
						this.hiddenByScript.add(child);
						child.setVisible(false);
					}
				}
			}
			else {
				for (final UIFrame child : this.hiddenByScript) {
					child.setVisible(true);
				}
				this.hiddenByScript.clear();
			}
			return null;
		});
	}

	private void registerLayout() {
		add("BlzFrameSetPoint", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final FramePoint point = framePoint(arguments, 1);
			final UIFrame relative = ownerOrRoot(arguments, 2);
			final FramePoint relativePoint = framePoint(arguments, 3);
			if ((frame != null) && (point != null) && (relativePoint != null)) {
				frame.addSetPoint(new SetPoint(point, relative, relativePoint, frameX(real(arguments, 4)),
						frameY(real(arguments, 5))));
					anchorsDecideSize(frame);
				markDirty(frame);
			}
			return null;
		});
		add("BlzFrameSetAbsPoint", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final FramePoint point = framePoint(arguments, 1);
			if ((frame != null) && (point != null)) {
				// Kept as a point of the screen's corner, so that laying the frame out again keeps it there.
				frame.addSetPoint(new SetPoint(point, gameUI(), FramePoint.BOTTOMLEFT,
						frameX(real(arguments, 2)) - gameUI().getFramePointX(FramePoint.LEFT),
						frameY(real(arguments, 3)) - gameUI().getFramePointY(FramePoint.BOTTOM)));
					anchorsDecideSize(frame);
				markDirty(frame);
			}
			return null;
		});
		add("BlzFrameClearAllPoints", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			if (frame instanceof com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame) {
				((com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame) frame)
						.clearFramePointAssignments();
				markDirty(frame);
			}
			return null;
		});
		add("BlzFrameSetAllPoints", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final UIFrame relative = ownerOrRoot(arguments, 1);
			// A missing frame to follow leaves the frame where it is instead of stretching it over the screen.
			if ((frame != null) && (object(arguments, 1) != null)) {
				frame.addSetPoint(new SetPoint(FramePoint.TOPLEFT, relative, FramePoint.TOPLEFT, 0, 0));
				frame.addSetPoint(new SetPoint(FramePoint.BOTTOMRIGHT, relative, FramePoint.BOTTOMRIGHT, 0, 0));
				// Opposite corners decide the size; a size set before them no longer applies.
				frame.setWidth(0);
				frame.setHeight(0);
				markDirty(frame);
			}
			return null;
		});
		add("BlzFrameSetSize", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			if (frame != null) {
				frame.setWidth(frameX(real(arguments, 1)));
				frame.setHeight(frameY(real(arguments, 2)));
				markDirty(frame);
			}
			return null;
		});
		add("BlzFrameGetWidth", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			if (frame == null) {
				return RealJassValue.ZERO;
			}
			final float width = frame.getFramePointX(FramePoint.RIGHT) - frame.getFramePointX(FramePoint.LEFT);
			return RealJassValue.of(GameUI.unconvertX(this.uiViewport, width != 0 ? width : frame.getAssignedWidth()));
		});
		add("BlzFrameGetHeight", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			if (frame == null) {
				return RealJassValue.ZERO;
			}
			final float height = frame.getFramePointY(FramePoint.TOP) - frame.getFramePointY(FramePoint.BOTTOM);
			return RealJassValue.of(
					GameUI.unconvertY(this.uiViewport, height != 0 ? height : frame.getAssignedHeight()));
		});
		add("BlzFrameSetParent", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final UIFrame parent = ownerOrRoot(arguments, 1);
			if ((frame != null) && (frame != parent)) {
				final UIFrame old = frame.getParent();
				if (old instanceof AbstractUIFrame) {
					((AbstractUIFrame) old).remove(frame);
				}
				else if (old == null) {
					gameUI().remove(frame);
				}
				frame.setParent(parent);
				if (parent instanceof AbstractUIFrame) {
					((AbstractUIFrame) parent).add(frame);
				}
				markDirty(frame);
			}
			return null;
		});
		add("BlzFrameGetParent", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			return handle(globalScope, frame == null ? null : frame.getParent());
		});
		add("BlzFrameGetChildrenCount", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			return IntegerJassValue
					.of(frame instanceof AbstractUIFrame ? ((AbstractUIFrame) frame).getChildFrames().size() : 0);
		});
		add("BlzFrameGetChild", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final int index = integer(arguments, 1);
			if (frame instanceof AbstractUIFrame) {
				final List<UIFrame> children = ((AbstractUIFrame) frame).getChildFrames();
				if ((index >= 0) && (index < children.size())) {
					return handle(globalScope, children.get(index));
				}
			}
			return handle(globalScope, null);
		});
		// Where a frame sits among the frames of its parent: later in the list is drawn above.
		add("BlzFrameSetLevel", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final int level = integer(arguments, 1);
			if ((frame != null) && (frame.getParent() instanceof AbstractUIFrame)) {
				final AbstractUIFrame parent = (AbstractUIFrame) frame.getParent();
				final List<UIFrame> children = parent.getChildFrames();
				if (children.remove(frame)) {
					children.add(Math.max(0, Math.min(children.size(), level)), frame);
				}
			}
			return null;
		});
	}

	/** The text part of a frame: the frame itself, or its text child when it is a button, a box or a list. */
	private StringFrame textFrame(final UIFrame frame) {
		if (frame instanceof StringFrame) {
			return (StringFrame) frame;
		}
		// A button keeps its label apart from its other children, and the other text children (the highlight or
		// disabled labels) are not the one the script means.
		final UIFrame label = frame instanceof PopupMenuFrame ? popupTitleText((PopupMenuFrame) frame)
				: frame instanceof GlueTextButtonFrame ? ((GlueTextButtonFrame) frame).getButtonText()
				: frame instanceof SimpleButtonFrame ? ((SimpleButtonFrame) frame).getButtonText() : null;
		if (label instanceof StringFrame) {
			return (StringFrame) label;
		}
		if (frame instanceof AbstractUIFrame) {
			for (final UIFrame child : ((AbstractUIFrame) frame).getChildFrames()) {
				if (child instanceof StringFrame) {
					return (StringFrame) child;
				}
			}
		}
		return null;
	}

	private static UIFrame popupTitleText(final PopupMenuFrame popup) {
		final UIFrame title = popup.getPopupTitleFrame();
		return title instanceof GlueTextButtonFrame ? ((GlueTextButtonFrame) title).getButtonText() : title;
	}

	private static Color argb(final int color) {
		final int a = (color >> 24) & 0xFF;
		final int r = (color >> 16) & 0xFF;
		final int g = (color >> 8) & 0xFF;
		final int b = color & 0xFF;
		return new Color(r / 255f, g / 255f, b / 255f, a / 255f);
	}

	private TextureFrame textureOf(final UIFrame frame) {
		if (frame instanceof TextureFrame) {
			return (TextureFrame) frame;
		}
		if (frame instanceof AbstractUIFrame) {
			for (final UIFrame child : ((AbstractUIFrame) frame).getChildFrames()) {
				if (child instanceof TextureFrame) {
					return (TextureFrame) child;
				}
			}
		}
		return null;
	}

	private void registerContent() {
		add("BlzFrameSetText", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final String text = gameUI().getTrigStr(string(arguments, 1));
			if (frame instanceof EditBoxFrame) {
				((EditBoxFrame) frame).setText(text, gameUI(), this.uiViewport);
			}
			else if (frame instanceof TextAreaFrame) {
				((TextAreaFrame) frame).setText(text, gameUI(), this.uiViewport);
			}
			else {
				final StringFrame target = textFrame(frame);
				if (target != null) {
					gameUI().setText(target, text);
				}
			}
			return null;
		});
		add("BlzFrameGetText", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final String text;
			if (frame instanceof EditBoxFrame) {
				text = ((EditBoxFrame) frame).getText();
			}
			else {
				final StringFrame target = textFrame(frame);
				text = target == null ? "" : target.getText();
			}
			return new StringJassValue(text == null ? "" : text);
		});
		add("BlzFrameAddText", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			if (frame instanceof TextAreaFrame) {
				((TextAreaFrame) frame).addItem(gameUI().getTrigStr(string(arguments, 1)), gameUI(), this.uiViewport);
			}
			return null;
		});
		add("BlzFrameSetTextSizeLimit", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			if (frame != null) {
				this.textSizeLimits.put(frame, Math.max(0, integer(arguments, 1)));
			}
			return null;
		});
		add("BlzFrameGetTextSizeLimit", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final Integer limit = frame == null ? null : this.textSizeLimits.get(frame);
			return IntegerJassValue.of(limit == null ? 0 : limit);
		});
		add("BlzFrameSetTextColor", (arguments, globalScope, triggerScope) -> {
			final StringFrame target = textFrame(object(arguments, 0));
			if (target != null) {
				target.setColor(argb(integer(arguments, 1)));
			}
			return null;
		});
		add("BlzFrameSetVertexColor", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final Color color = argb(integer(arguments, 1));
			if (frame instanceof SpriteFrame) {
				((SpriteFrame) frame).setVertexColor(color);
				return null;
			}
			final TextureFrame texture = textureOf(frame);
			if (texture != null) {
				texture.setColor(color.r, color.g, color.b, color.a);
			}
			return null;
		});
		add("BlzFrameSetTextAlignment", (arguments, globalScope, triggerScope) -> {
			final StringFrame target = textFrame(object(arguments, 0));
			final int vertical = constant(arguments, 1);
			final int horizontal = constant(arguments, 2);
			if (target != null) {
				if ((vertical >= 0) && (vertical <= 2)) {
					target.setJustifyV(TextJustify.values()[vertical]);
				}
				if ((horizontal >= 3) && (horizontal <= 5)) {
					target.setJustifyH(TextJustify.values()[horizontal]);
				}
			}
			return null;
		});
		add("BlzFrameSetTexture", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final TextureFrame texture = textureOf(frame);
			if (texture != null) {
				texture.setTexture(string(arguments, 1), gameUI());
			}
			else if (frame instanceof BackdropFrame) {
				((BackdropFrame) frame).setBackground(gameUI().loadTexture(string(arguments, 1)));
			}
			return null;
		});
		add("BlzFrameSetModel", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			if (frame instanceof SpriteFrame) {
				gameUI().setSpriteFrameModel((SpriteFrame) frame, string(arguments, 1));
			}
			return null;
		});
		add("BlzFrameSetSpriteAnimate", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			if (frame instanceof SpriteFrame) {
				((SpriteFrame) frame).setSequence(Math.max(0, integer(arguments, 1)));
			}
			return null;
		});
	}

	private void registerState() {
		add("BlzFrameSetVisible", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			if (frame != null) {
				// The game keeps showing and hiding its own frames; the script's hiding is a layer over that.
				if ((frame instanceof AbstractRenderableFrame) && !isScriptOwned(frame)) {
					((AbstractRenderableFrame) frame).setScriptHidden(!bool(arguments, 1));
				}
				else {
					frame.setVisible(bool(arguments, 1));
				}
			}
			return null;
		});
		add("BlzFrameIsVisible", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			return BooleanJassValue.of((frame != null) && frame.isVisible());
		});
		add("BlzFrameSetEnable", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final boolean enabled = bool(arguments, 1);
			if (frame instanceof GlueButtonFrame) {
				((GlueButtonFrame) frame).setEnabled(enabled);
			}
			else if (frame instanceof SimpleButtonFrame) {
				((SimpleButtonFrame) frame).setEnabled(enabled);
			}
			else if (frame instanceof SimpleCheckBoxFrame) {
				((SimpleCheckBoxFrame) frame).setEnabled(enabled);
			}
			return null;
		});
		add("BlzFrameGetEnable", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			if (frame instanceof GlueButtonFrame) {
				return BooleanJassValue.of(((GlueButtonFrame) frame).isEnabled());
			}
			return BooleanJassValue.of(frame != null);
		});
		add("BlzFrameSetAlpha", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			if (frame instanceof com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame) {
				((com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame) frame)
						.setRenderAlpha(Math.max(0, Math.min(255, integer(arguments, 1))) / 255f);
			}
			return null;
		});
		add("BlzFrameGetAlpha", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			return IntegerJassValue.of(frame instanceof com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame
					? Math.round(((com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame) frame)
							.getRenderAlpha() * 255f)
					: 255);
		});
		add("BlzFrameSetValue", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final float value = real(arguments, 1);
			final float[] range = this.ranges.get(frame);
			if (frame instanceof SimpleStatusBarFrame) {
				final float ratio = range == null ? value : (value - range[0]) / (range[1] - range[0]);
				((SimpleStatusBarFrame) frame).setValue(Math.max(0, Math.min(1, ratio)));
			}
			else if (frame instanceof ScrollBarFrame) {
				((ScrollBarFrame) frame).setValue(gameUI(), this.uiViewport,
						range == null ? Math.round(value) : toStep(range, value));
			}
			return null;
		});
		add("BlzFrameGetValue", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			return RealJassValue.of(valueOf(frame));
		});
		add("BlzFrameClick", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			if (frame instanceof ClickableFrame) {
				((ClickableFrame) frame).onClick(1);
				// The game's own frames tell the script through the interface's input, which a call does not pass.
				if (!isScriptOwned(frame)) {
					click(frame);
				}
			}
			return null;
		});
		add("BlzFrameSetTooltip", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final UIFrame tooltip = object(arguments, 1);
			if (frame != null) {
				this.tooltips.put(frame, tooltip);
				// Shown while the pointer is over the frame, not before.
				if (tooltip != null) {
					tooltip.setVisible(false);
				}
			}
			return null;
		});
	}

	private int stepsOf(final float[] range) {
		return range[2] > 0 ? Math.max(1, Math.round((range[1] - range[0]) / range[2])) : 1000;
	}

	private int toStep(final float[] range, final float value) {
		return Math.round(((value - range[0]) / (range[1] - range[0])) * stepsOf(range));
	}

	private float fromStep(final float[] range, final int step) {
		return range[0] + ((step / (float) stepsOf(range)) * (range[1] - range[0]));
	}

	/** What BlzFrameGetValue tells: the slider's value or the status bar's fill in the script's units. */
	private float valueOf(final UIFrame frame) {
		final float[] range = this.ranges.get(frame);
		if (frame instanceof PopupMenuFrame) {
			return ((PopupMenuFrame) frame).getSelectedIndex();
		}
		if (frame instanceof ScrollBarFrame) {
			final int step = ((ScrollBarFrame) frame).getValue();
			return range == null ? step : fromStep(range, step);
		}
		if (frame instanceof SimpleStatusBarFrame) {
			final float ratio = ((SimpleStatusBarFrame) frame).getValue();
			return range == null ? ratio : range[0] + (ratio * (range[1] - range[0]));
		}
		return 0;
	}

	/** A slider counts whole steps inside its range; the range or the step changed keeps its value in place. */
	private void rangeChanged(final UIFrame frame, final float[] range) {
		if (frame instanceof ScrollBarFrame) {
			final ScrollBarFrame slider = (ScrollBarFrame) frame;
			final float value = valueOf(frame);
			this.ranges.put(frame, range);
			final float kept = Math.max(range[0], Math.min(range[1], value));
			slider.setMinValue(0);
			slider.setMaxValue(stepsOf(range));
			slider.setStepSize(1);
			slider.setRange(stepsOf(range), toStep(range, kept));
			markDirty(frame);
		}
		else {
			this.ranges.put(frame, range);
		}
	}

	private void registerEvents() {
		add("BlzTriggerRegisterFrameEvent", (arguments, globalScope, triggerScope) -> {
			final Trigger trigger = object(arguments, 0);
			final UIFrame frame = object(arguments, 1);
			final int eventType = constant(arguments, 2);
			final HandleJassType eventHandleType = globalScope.getHandleType("event");
			if ((trigger == null) || (frame == null) || (eventType < 0)) {
				return new HandleJassValue(eventHandleType, null);
			}
			final int frameId = idOf(frame);
			final Registration registration = new Registration(globalScope, trigger, frameId, eventType);
			this.registrations.computeIfAbsent(frameId, key -> new ArrayList<>()).add(registration);
			hook(frame, frameId);
			return new HandleJassValue(eventHandleType, registration);
		});
		add("BlzGetTriggerFrame", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope scope = triggerScope instanceof CommonTriggerExecutionScope
					? (CommonTriggerExecutionScope) triggerScope
					: null;
			return handle(globalScope, scope == null ? null : (UIFrame) scope.getFrame());
		});
		add("BlzGetTriggerFrameEvent", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope scope = triggerScope instanceof CommonTriggerExecutionScope
					? (CommonTriggerExecutionScope) triggerScope
					: null;
			final HandleJassType type = globalScope.getHandleType("frameeventtype");
			return scope == null || scope.getFrame() == null ? type.getNullValue()
					: this.program.getJassNativeManager().convertedConstant(type, scope.getFrameEventType());
		});
		add("BlzGetTriggerFrameValue", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope scope = triggerScope instanceof CommonTriggerExecutionScope
					? (CommonTriggerExecutionScope) triggerScope
					: null;
			return RealJassValue.of(scope == null ? 0 : scope.getFrameValue());
		});
		add("BlzGetTriggerFrameText", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope scope = triggerScope instanceof CommonTriggerExecutionScope
					? (CommonTriggerExecutionScope) triggerScope
					: null;
			return new StringJassValue(scope == null || scope.getFrameText() == null ? "" : scope.getFrameText());
		});
		// Frame handles in a hashtable, as the other handles.
		// The frame keeps what the script gave it, so it reads back.
		// The frame and what it holds are drawn larger or smaller about the frame's center, text and textures
		// alike, and the pointer finds them where they are drawn; models of the interface scene keep their size.
		add("BlzFrameSetScale", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final float scale = real(arguments, 1);
			if ((frame instanceof AbstractRenderableFrame) && (scale > 0)) {
				((AbstractRenderableFrame) frame).setRenderScale(scale);
			}
			return null;
		});
		// The font file of the data at the height of the script (in the 0.6 high screen), for the frame's text.
		add("BlzFrameSetFont", (arguments, globalScope, triggerScope) -> {
			final StringFrame target = textFrame(object(arguments, 0));
			if ((target != null) && (real(arguments, 2) > 0)) {
				target.setFrameFont(gameUI().generateFont(string(arguments, 1), real(arguments, 2)));
				gameUI().setText(target, target.getText());
			}
			return null;
		});
		add("BlzFrameSetMinMaxValue", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final float min = real(arguments, 1);
			final float max = real(arguments, 2);
			if (((frame instanceof ScrollBarFrame) || (frame instanceof SimpleStatusBarFrame)) && (max > min)) {
				final float[] old = this.ranges.get(frame);
				rangeChanged(frame, new float[] { min, max, old == null ? 0 : old[2] });
			}
			return null;
		});
		add("BlzFrameSetStepSize", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			final float step = real(arguments, 1);
			if ((frame instanceof ScrollBarFrame) || (frame instanceof SimpleStatusBarFrame)) {
				final float[] old = this.ranges.get(frame);
				rangeChanged(frame, new float[] { old == null ? 0 : old[0], old == null ? 100 : old[1],
						Math.max(0, step) });
			}
			return null;
		});
		add("BlzFrameSetFocus", (arguments, globalScope, triggerScope) -> {
			final UIFrame frame = object(arguments, 0);
			if ((frame != null) && (this.ui != null)) {
				this.ui.setScriptFocus(frame, bool(arguments, 1));
			}
			return null;
		});
		// No mouse capture in the game's frames: a frame takes input where it is clicked, nothing else holds it.
		for (final String name : new String[] { "BlzFrameCageMouse", "BlzTextAreaFrameSetAutoScroll" }) {
			add(name, (arguments, globalScope, triggerScope) -> null);
		}
	}

	/** What this machine's player does to the frame becomes the command every machine turns into the event. */
	private void send(final int frameId, final int eventType, final float value, final String text) {
		final CPlayerUnitOrderListener listener = this.ui == null ? null : this.ui.getUnitOrderListener();
		if (listener != null) {
			listener.frameEvent(frameId, eventType, value, text);
		}
	}

	private void hook(final UIFrame frame, final int frameId) {
		// The game's own frames keep their click handling; what is done to them comes by the interface's input.
		if (!isScriptOwned(frame)) {
			return;
		}
		if (frame instanceof CheckBoxFrame) {
			final CheckBoxFrame box = (CheckBoxFrame) frame;
			box.setOnClick(() -> {
				send(frameId, EVENT_CONTROL_CLICK, 0, "");
				send(frameId, box.isChecked() ? EVENT_CHECKBOX_CHECKED : EVENT_CHECKBOX_UNCHECKED, 0, "");
			});
		}
		else if (frame instanceof PopupMenuFrame) {
			// The click that opens the list stays the popup's own; choosing an item changes the title and the value.
			final PopupMenuFrame popup = (PopupMenuFrame) frame;
			popup.setMenuClickListener((button, index) -> {
				popup.setSelectedIndex(gameUI(), index);
				send(frameId, EVENT_POPUPMENU_ITEM_CHANGED, index, "");
			});
		}
		else if (frame instanceof GlueButtonFrame) {
			final GlueButtonFrame button = (GlueButtonFrame) frame;
			button.setOnClick(() -> send(frameId, EVENT_CONTROL_CLICK, 0, ""));
			button.setButtonListener(new GlueButtonFrame.ButtonListener() {
				@Override
				public void mouseDown(final GameUI gameUI, final Viewport viewport) {
					send(frameId, EVENT_MOUSE_DOWN, 0, "");
				}

				@Override
				public void mouseUp(final GameUI gameUI, final Viewport viewport) {
					send(frameId, EVENT_MOUSE_UP, 0, "");
				}

				@Override
				public void mouseDragged(final GameUI rootFrame, final Viewport viewport, final float x,
						final float y) {
				}
			});
		}
		else if (frame instanceof SimpleCheckBoxFrame) {
			final SimpleCheckBoxFrame box = (SimpleCheckBoxFrame) frame;
			box.setOnClick(() -> {
				send(frameId, EVENT_CONTROL_CLICK, 0, "");
				send(frameId, box.isChecked() ? EVENT_CHECKBOX_CHECKED : EVENT_CHECKBOX_UNCHECKED, 0, "");
			});
		}
		else if (frame instanceof SimpleButtonFrame) {
			((SimpleButtonFrame) frame).setOnClick(() -> send(frameId, EVENT_CONTROL_CLICK, 0, ""));
		}
		else if (frame instanceof EditBoxFrame) {
			final EditBoxFrame box = (EditBoxFrame) frame;
			box.setFilterAllowAny();
			box.setOnChange(() -> send(frameId, EVENT_EDITBOX_TEXT_CHANGED, 0, box.getText()));
			box.setOnEnter(() -> send(frameId, EVENT_EDITBOX_ENTER, 0, box.getText()));
		}
		else if (frame instanceof ScrollBarFrame) {
			((ScrollBarFrame) frame).setChangeListener((gameUI, viewport, newValue) -> {
				final float[] range = this.ranges.get(frame);
				send(frameId, EVENT_SLIDER_VALUE_CHANGED, range == null ? newValue : fromStep(range, newValue), "");
			});
		}
	}

	/** The frame's events go out only when a trigger listens for the frame, as the registration made it known. */
	private void deliver(final UIFrame frame, final int eventType, final float value) {
		final Integer frameId = this.frameIds.get(frame);
		if ((frameId != null) && this.registrations.containsKey(frameId)) {
			send(frameId, eventType, value, "");
		}
	}

	@Override
	public void mouseEnter(final UIFrame frame) {
		final UIFrame tooltip = this.tooltips.get(frame);
		if (tooltip != null) {
			tooltip.setVisible(true);
		}
		deliver(frame, EVENT_MOUSE_ENTER, 0);
	}

	@Override
	public void mouseLeave(final UIFrame frame) {
		final UIFrame tooltip = this.tooltips.get(frame);
		if (tooltip != null) {
			tooltip.setVisible(false);
		}
		deliver(frame, EVENT_MOUSE_LEAVE, 0);
	}

	// The buttons the script made hook their own press and click; only the game's frames come here for these.
	@Override
	public void mouseDown(final UIFrame frame) {
		if (!isScriptOwned(frame)) {
			deliver(frame, EVENT_MOUSE_DOWN, 0);
		}
	}

	@Override
	public void mouseUp(final UIFrame frame) {
		if (!isScriptOwned(frame)) {
			deliver(frame, EVENT_MOUSE_UP, 0);
		}
	}

	@Override
	public void click(final UIFrame frame) {
		final long now = TimeUtils.millis();
		final boolean doubled = (frame == this.lastClicked) && ((now - this.lastClickMillis) <= DOUBLE_CLICK_MILLIS);
		this.lastClicked = doubled ? null : frame;
		this.lastClickMillis = now;
		if (!isScriptOwned(frame)) {
			deliver(frame, EVENT_CONTROL_CLICK, 0);
		}
		if (doubled) {
			deliver(frame, EVENT_MOUSE_DOUBLECLICK, 0);
		}
	}

	@Override
	public void mouseWheel(final UIFrame frame, final float amount) {
		deliver(frame, EVENT_MOUSE_WHEEL, amount);
	}

	/** The command of a frame event came: the triggers registered for that frame and event run. */
	private void onFrameEvent(final CPlayer player, final int frameId, final int eventType, final float value,
			final String text) {
		final List<Registration> list = this.registrations.get(frameId);
		if ((list == null) || (frameId < 1) || (frameId > this.frames.size())) {
			return;
		}
		final UIFrame frame = this.frames.get(frameId - 1);
		for (final Registration registration : new ArrayList<>(list)) {
			if (registration.eventType == eventType) {
				final CommonTriggerExecutionScope scope = CommonTriggerExecutionScope.frameScope(registration.trigger,
						player, frame, eventType, value, text);
				registration.globalScope.queueTrigger(null, null, registration.trigger, scope, scope);
			}
		}
	}

	private void registerCoordinates() {
		// A pixel of this client's window to the 0.8 by 0.6 space of the frames, and back; the pixel row 0 is the top.
		add("BlzPixelToFrameX", (arguments, globalScope, triggerScope) -> RealJassValue
				.of((integer(arguments, 0) / (float) Math.max(1, Gdx.graphics.getWidth())) * 0.8f));
		add("BlzPixelToFrameY", (arguments, globalScope, triggerScope) -> RealJassValue
				.of((1f - (integer(arguments, 0) / (float) Math.max(1, Gdx.graphics.getHeight()))) * 0.6f));
		add("BlzFrameToPixelX", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(Math.round((real(arguments, 0) / 0.8f) * Gdx.graphics.getWidth())));
		add("BlzFrameToPixelY", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(Math.round((1f - (real(arguments, 0) / 0.6f)) * Gdx.graphics.getHeight())));
	}
}
