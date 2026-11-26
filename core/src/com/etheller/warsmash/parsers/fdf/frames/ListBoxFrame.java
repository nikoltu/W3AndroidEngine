package com.etheller.warsmash.parsers.fdf.frames;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.util.SessionResources;
import com.etheller.warsmash.util.AbstractListItemDisplay;
import com.etheller.warsmash.util.AbstractListItemProperty;
import com.etheller.warsmash.util.ListItemEnum;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.input.touch.TouchInputRouter;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.ClickableFrame;

public class ListBoxFrame extends ControlFrame
		implements ScrollBarFrame.ScrollBarChangeListener, ClickableFrame {
	// TODO where are these colors in the UI definition files?
	public static final Color SELECT_COLOR = Color.BLUE;
	public static final Color MOUSE_OVER_HIGHLIGHT_COLOR = new Color(0.3f, 0.3f, 1.0f, 0.25f);

	// private final List<String> listItems = new ArrayList<>();
	private final List<AbstractListItemProperty> listItems = new ArrayList<>();
	private final List<AbstractListItemDisplay> listFrames = new ArrayList<>();
	private BitmapFont frameFont;
	private float listBoxBorder;
	private int selectedIndex = -1;
	private int mouseOverIndex = -1;
	private float builtRowWidth = -1;
	private BitmapFont builtFont;
	private final EnumMap<ListItemEnum, Float> rowHeights = new EnumMap<>(ListItemEnum.class);
	private float pressX;
	private float pressY;
	private int pressScrollValue;
	private boolean touchPress;
	private boolean pressDragged;

	private final TextureFrame selectionFrame;
	private final TextureFrame mouseHighlightFrame;
	private GameUI gameUI;
	private Viewport viewport;
	private ScrollBarFrame scrollBarFrame;
	private ListBoxSelelectionListener selectionListener = ListBoxSelelectionListener.DO_NOTHING;

	private final DataSource dataSource;

	public ListBoxFrame(final String name, final UIFrame parent, final Viewport viewport, DataSource dataSource) {
		super(name, parent);
		this.listBoxBorder = GameUI.convertX(viewport, 0.01f);
		this.selectionFrame = new TextureFrame(null, this, false, null);
		this.mouseHighlightFrame = new TextureFrame(null, this, false, null);
		final Pixmap pixmap = new Pixmap(1, 1, Format.RGBA8888);
		pixmap.setColor(SELECT_COLOR);
		pixmap.fill();
		this.selectionFrame.setTexture(SessionResources.track(new Texture(pixmap)));
		pixmap.dispose();
		final Pixmap mousePixmap = new Pixmap(1, 1, Format.RGBA8888);
		mousePixmap.setColor(MOUSE_OVER_HIGHLIGHT_COLOR);
		mousePixmap.fill();
		this.mouseHighlightFrame.setTexture(SessionResources.track(new Texture(mousePixmap)));
		mousePixmap.dispose();

		this.dataSource = dataSource;
	}

	public void setScrollBarFrame(final ScrollBarFrame scrollBarFrame) {
		this.scrollBarFrame = scrollBarFrame;
		// TODO might be a better place to add these set points, but we definitely need
		// them
		scrollBarFrame.addSetPoint(
				new SetPoint(FramePoint.TOPRIGHT, this, FramePoint.TOPRIGHT, -this.listBoxBorder, -this.listBoxBorder));
		scrollBarFrame.addSetPoint(new SetPoint(FramePoint.BOTTOMRIGHT, this, FramePoint.BOTTOMRIGHT,
				-this.listBoxBorder, this.listBoxBorder));
		// The list keeps its scroll bar in items: the value counts the items hidden below the shown ones, so the
		// highest value is the top of the list, where the original opens it, and an arrow moves one item.
		scrollBarFrame.setRange(1, 1);
		scrollBarFrame.setStepSize(1);
		scrollBarFrame.setChangeListener(this);
	}

	/** Moves the list a number of items; positive shows later items. */
	public void scrollItems(final GameUI gameUI, final Viewport viewport, final int items) {
		if ((this.scrollBarFrame != null) && (items != 0)) {
			this.scrollBarFrame.setValue(gameUI, viewport, this.scrollBarFrame.getValue() - items);
		}
	}

	/** The shown list box under the point, searched from the frontmost frame down, or null. */
	public static ListBoxFrame findUnder(final UIFrame frame, final float screenX, final float screenY) {
		if (!frame.isVisible()) {
			return null;
		}
		if (frame instanceof ListBoxFrame) {
			return ((ListBoxFrame) frame).renderBounds.contains(screenX, screenY) ? (ListBoxFrame) frame : null;
		}
		if (frame instanceof AbstractUIFrame) {
			final List<UIFrame> children = ((AbstractUIFrame) frame).getChildFrames();
			for (int i = children.size() - 1; i >= 0; i--) {
				final ListBoxFrame found = findUnder(children.get(i), screenX, screenY);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	public ScrollBarFrame getScrollBarFrame() {
		return this.scrollBarFrame;
	}

	public void setListBoxBorder(final float listBoxBorder) {
		this.listBoxBorder = listBoxBorder;
	}

	public float getListBoxBorder() {
		return this.listBoxBorder;
	}

	public void setFrameFont(final BitmapFont frameFont) {
		this.frameFont = frameFont;
	}

	public BitmapFont getFrameFont() {
		return this.frameFont;
	}

	@Override
	protected void innerPositionBounds(final GameUI gameUI, final Viewport viewport) {
		this.gameUI = gameUI;
		this.viewport = viewport;
		super.innerPositionBounds(gameUI, viewport);
		updateUI(gameUI, viewport);
	}

	private void positionChildren(final GameUI gameUI, final Viewport viewport) {
		for (final AbstractListItemDisplay frame : this.listFrames) {
			frame.positionBounds(gameUI, viewport);
		}
		this.selectionFrame.positionBounds(gameUI, viewport);
		this.mouseHighlightFrame.positionBounds(gameUI, viewport);
		if (this.scrollBarFrame != null) {
			this.scrollBarFrame.positionBounds(gameUI, viewport);
		}
	}

	@Override
	protected void internalRender(final SpriteBatch batch, final BitmapFont baseFont, final GlyphLayout glyphLayout) {
		super.internalRender(batch, baseFont, glyphLayout);
		this.selectionFrame.render(batch, baseFont, glyphLayout);
		this.mouseHighlightFrame.render(batch, baseFont, glyphLayout);
		for (final AbstractListItemDisplay frame : this.listFrames) {
			frame.internalRender(batch, baseFont, glyphLayout);
		}
		if (this.scrollBarFrame != null) {
			this.scrollBarFrame.render(batch, baseFont, glyphLayout);
		}
	}

	// Default
	public void addItem(final String item, final GameUI gameUI, final Viewport viewport) {
		this.listItems.add(AbstractListItemProperty.createFromType(item, ListItemEnum.ITEM_STRING, gameUI, this.dataSource));
		refreshItems(gameUI, viewport);
	}

	public void addItem(final String item, final ListItemEnum itemType, final GameUI gameUI, final Viewport viewport) {
		this.listItems.add(AbstractListItemProperty.createFromType(item, itemType, gameUI, this.dataSource));
		refreshItems(gameUI, viewport);
	}

	public void setItems(final List<AbstractListItemProperty> items, final GameUI gameUI, final Viewport viewport) {
		this.listItems.clear();
		this.listItems.addAll(items);
		this.selectedIndex = -1;
		this.mouseOverIndex = -1;
		refreshItems(gameUI, viewport);
	}

	public void removeItem(final String item, final GameUI gameUI, final Viewport viewport) {
		for (int i = 0; i < this.listItems.size(); i++) {
			if (this.listItems.get(i).getRawValue().equals(item)) {
				this.listItems.remove(i);
				break;
			}
		}
		refreshItems(gameUI, viewport);
	}

	public void removeItem(final int index, final GameUI gameUI, final Viewport viewport) {
		this.listItems.remove(index);
		refreshItems(gameUI, viewport);
	}

	public void removeAllItems() {
		this.listItems.clear();
		refreshItems(this.gameUI, this.viewport);
	}

	/** Item frames are built when the list is laid out, so a change shows only after laying it out again. */
	private void refreshItems(final GameUI gameUI, final Viewport viewport) {
		if ((gameUI != null) && (viewport != null) && (this.gameUI != null)) {
			updateUI(gameUI, viewport);
		}
	}

	public void setSelectedIndex(final int selectedIndex) {
		this.selectedIndex = selectedIndex;
	}

	/** Selects an item as a click would: scrolls it into view and tells the selection listener. */
	public void selectIndex(final int index) {
		if ((index < 0) || (index >= this.listItems.size()) || !this.listItems.get(index).isSelectable()) {
			return;
		}
		this.selectedIndex = index;
		if ((this.gameUI == null) || (this.viewport == null)) {
			this.selectionListener.onSelectionChanged(this.selectedIndex, getSelectedItem());
			return;
		}
		final int shown = this.listFrames.size();
		if ((this.scrollBarFrame != null) && (shown > 0) && (this.listItems.size() > shown)) {
			final int offset = computeScrollOffset(shown);
			int newOffset = offset;
			if (index < offset) {
				newOffset = index;
			}
			else if (index >= (offset + shown)) {
				newOffset = (index - shown) + 1;
			}
			if (newOffset != offset) {
				this.scrollBarFrame.setValue(this.gameUI, this.viewport,
						this.scrollBarFrame.getMaxValue() - newOffset);
			}
		}
		updateUI(this.gameUI, this.viewport);
		this.selectionListener.onSelectionChanged(this.selectedIndex, getSelectedItem());
	}

	/** The first item a click could choose, or -1. */
	public int firstSelectableIndex() {
		for (int i = 0; i < this.listItems.size(); i++) {
			if (this.listItems.get(i).isSelectable()) {
				return i;
			}
		}
		return -1;
	}

	public int indexOf(final String rawValue) {
		for (int i = 0; i < this.listItems.size(); i++) {
			if (this.listItems.get(i).getRawValue().equals(rawValue)) {
				return i;
			}
		}
		return -1;
	}

	public int getItemCount() {
		return this.listItems.size();
	}

	public int getSelectedIndex() {
		return this.selectedIndex;
	}

	public String getSelectedItem() {
		if ((this.selectedIndex < 0) || (this.selectedIndex >= this.listItems.size())) {
			return null;
		}
		return this.listItems.get(this.selectedIndex).getRawValue();
	}

	private void updateUI(final GameUI gameUI, final Viewport viewport) {
		AbstractRenderableFrame prev = null;
		boolean foundSelected = false;
		boolean foundMouseOver = false;
		
		final float rowWidth = this.renderBounds.width - 2 * this.listBoxBorder;
		if ((rowWidth != this.builtRowWidth) || (this.frameFont != this.builtFont)) {
			this.rowHeights.clear();
		}
		final float itemSize = this.listItems.isEmpty() ? 0 : measureRowHeight(gameUI, viewport);
		final int numStringFrames = (itemSize <= 0) ? 0
				: (int) Math.min(this.listItems.size(),
						Math.floor(((this.renderBounds.height - (this.listBoxBorder * 2)) / itemSize) + 0.001f));

		syncScrollRange(numStringFrames);
		final int scrollOffset = computeScrollOffset(numStringFrames);
		// The rows are rebuilt only when how many fit, or how wide they are, has changed; hovering or selecting only
		// moves the highlights and refills the rows.
		if ((numStringFrames != this.listFrames.size()) || (rowWidth != this.builtRowWidth)
				|| (this.frameFont != this.builtFont)) {
			this.builtRowWidth = rowWidth;
			this.builtFont = this.frameFont;
			for (int i = 0; i < this.listFrames.size(); i++) {
				this.listFrames.get(i).remove(gameUI);
			}
			this.listFrames.clear();

			for (int stringFrameIndex = 0; stringFrameIndex < numStringFrames; stringFrameIndex++) {
				final int index = stringFrameIndex + scrollOffset;
				final boolean selected = (index == this.selectedIndex);
				final boolean mousedOver = (index == this.mouseOverIndex) && (index < this.listItems.size())
						&& this.listItems.get(index).isSelectable();

				final AbstractListItemProperty itemProperty = (index < this.listItems.size()) ? this.listItems.get(index) : null;
				final AbstractListItemDisplay listDisplay = AbstractListItemDisplay.createFromType(
						(itemProperty == null) ? ListItemEnum.ITEM_STRING : itemProperty.getItemType(),
						"LISTY_" + stringFrameIndex, this, gameUI, viewport);
				if (itemProperty != null) {
					listDisplay.setValuesFromProperty(itemProperty);
				}
				if (prev != null) {
					listDisplay.getParentFrame().addSetPoint(new SetPoint(FramePoint.TOPLEFT, prev, FramePoint.BOTTOMLEFT, 0, 0));
				} else {
					listDisplay.getParentFrame().addSetPoint(new SetPoint(FramePoint.TOPLEFT, this, FramePoint.TOPLEFT, this.listBoxBorder, -this.listBoxBorder));
				}
				this.listFrames.add(listDisplay);
				prev = listDisplay.getParentFrame();

				if (selected) {
					this.selectionFrame
							.addSetPoint(new SetPoint(FramePoint.TOPLEFT, listDisplay.getParentFrame(), FramePoint.TOPLEFT, 0, 0));
					this.selectionFrame.addSetPoint(
							new SetPoint(FramePoint.BOTTOMRIGHT, listDisplay.getParentFrame(), FramePoint.BOTTOMRIGHT, 0, 0));
					foundSelected = true;
				}
				else if (mousedOver) {
					this.mouseHighlightFrame
							.addSetPoint(new SetPoint(FramePoint.TOPLEFT, listDisplay.getParentFrame(), FramePoint.TOPLEFT, 0, 0));
					this.mouseHighlightFrame.addSetPoint(
							new SetPoint(FramePoint.BOTTOMRIGHT, listDisplay.getParentFrame(), FramePoint.BOTTOMRIGHT, 0, 0));
					foundMouseOver = true;
				}
			}
		}
		else {
			for (int stringFrameIndex = 0; stringFrameIndex < numStringFrames; stringFrameIndex++) {
				final int index = stringFrameIndex + scrollOffset;
				final boolean selected = (index == this.selectedIndex);
				final boolean mousedOver = (index == this.mouseOverIndex) && (index < this.listItems.size())
						&& this.listItems.get(index).isSelectable();

				AbstractListItemDisplay listDisplay = this.listFrames.get(stringFrameIndex);
				if (index < this.listItems.size()) {
					if (listDisplay.compareType(this.listItems.get(index))) {
						listDisplay.setValuesFromProperty(this.listItems.get(index));
					} else {
						listDisplay.remove(gameUI);
						listDisplay = AbstractListItemDisplay.createFromType(this.listItems.get(index).getItemType(), "LISTY_" + stringFrameIndex, this, gameUI, viewport);
						listDisplay.setValuesFromProperty(this.listItems.get(index));

						if (stringFrameIndex > 0) {
							listDisplay.getParentFrame().addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.listFrames.get(stringFrameIndex - 1).getParentFrame(), FramePoint.BOTTOMLEFT, 0, 0));
						} else {
							listDisplay.getParentFrame().addSetPoint(new SetPoint(FramePoint.TOPLEFT, this, FramePoint.TOPLEFT, this.listBoxBorder, -this.listBoxBorder));
						}
						if (stringFrameIndex < this.listFrames.size()-1) {
							this.listFrames.get(stringFrameIndex+1).getParentFrame().addSetPoint(new SetPoint(FramePoint.TOPLEFT, listDisplay.getParentFrame(), FramePoint.BOTTOMLEFT, 0, 0));
						}

						this.listFrames.set(stringFrameIndex, listDisplay);
					}
				}
				if (selected) {
					this.selectionFrame
							.addSetPoint(new SetPoint(FramePoint.TOPLEFT, listDisplay.getParentFrame(), FramePoint.TOPLEFT, 0, 0));
					this.selectionFrame.addSetPoint(
							new SetPoint(FramePoint.BOTTOMRIGHT, listDisplay.getParentFrame(), FramePoint.BOTTOMRIGHT, 0, 0));
					foundSelected = true;
				}
				else if (mousedOver) {
					this.mouseHighlightFrame
							.addSetPoint(new SetPoint(FramePoint.TOPLEFT, listDisplay.getParentFrame(), FramePoint.TOPLEFT, 0, 0));
					this.mouseHighlightFrame.addSetPoint(
							new SetPoint(FramePoint.BOTTOMRIGHT, listDisplay.getParentFrame(), FramePoint.BOTTOMRIGHT, 0, 0));
					foundMouseOver = true;
				}
			}
		}
		this.selectionFrame.setVisible(foundSelected);
		this.mouseHighlightFrame.setVisible(foundMouseOver);
		positionChildren(gameUI, viewport);
	}

	/**
	 * The height every row is counted with: the tallest of the item types in the list, so how many rows fit does not
	 * change with which items are scrolled into view.
	 */
	private float measureRowHeight(final GameUI gameUI, final Viewport viewport) {
		float tallest = 0;
		for (int i = 0; i < this.listItems.size(); i++) {
			final ListItemEnum type = this.listItems.get(i).getItemType();
			Float height = this.rowHeights.get(type);
			if (height == null) {
				final AbstractListItemDisplay probe = AbstractListItemDisplay.createFromType(type, "LISTY_PROBE", this,
						gameUI, viewport);
				probe.positionBounds(gameUI, viewport);
				height = probe.getParentFrame().getRenderBounds().height;
				probe.remove(gameUI);
				this.rowHeights.put(type, height);
			}
			tallest = Math.max(tallest, height);
		}
		return tallest;
	}

	/** Keeps the scroll bar's range at the hidden item count and the first shown item where it was. */
	private void syncScrollRange(final int numStringFrames) {
		if (this.scrollBarFrame == null) {
			return;
		}
		final int hidden = Math.max(0, this.listItems.size() - numStringFrames);
		final int max = Math.max(1, hidden);
		final int oldMax = this.scrollBarFrame.getMaxValue();
		if (max != oldMax) {
			final int firstShown = Math.min(hidden, Math.max(0, oldMax - this.scrollBarFrame.getValue()));
			this.scrollBarFrame.setRange(max, max - firstShown);
		}
	}

	private int computeScrollOffset(final int numStringFrames) {
		if ((this.scrollBarFrame != null) && (this.listItems.size() > numStringFrames)) {
			return Math.min(this.listItems.size() - numStringFrames,
					Math.max(0, this.scrollBarFrame.getMaxValue() - this.scrollBarFrame.getValue()));
		}
		return 0;
	}

	@Override
	public UIFrame touchDown(final float screenX, final float screenY, final int button) {
		if (isVisible() && this.renderBounds.contains(screenX, screenY)) {
			if (this.scrollBarFrame != null) {
				final UIFrame sliderFrameChildUnderMouse = this.scrollBarFrame.touchDown(screenX, screenY, button);
				if (sliderFrameChildUnderMouse != null) {
					return sliderFrameChildUnderMouse;
				}
			}
			this.pressX = screenX;
			this.pressY = screenY;
			this.pressScrollValue = (this.scrollBarFrame == null) ? 0 : this.scrollBarFrame.getValue();
			this.touchPress = TouchInputRouter.isActive();
			this.pressDragged = false;
			// A finger press may still turn into a drag that scrolls the list, so touch selects on release.
			if (!this.touchPress) {
				selectAt(screenX, screenY);
			}
			return this;
		}
		return super.touchDown(screenX, screenY, button);
	}

	private void selectAt(final float screenX, final float screenY) {
		int index = 0;
		for (final AbstractListItemDisplay listFrame : this.listFrames) {
			if (listFrame.getParentFrame().getRenderBounds().contains(screenX, screenY)) {
				final int clicked = index + computeScrollOffset(this.listFrames.size());
				if ((clicked < this.listItems.size()) && this.listItems.get(clicked).isSelectable()) {
					this.selectedIndex = clicked;
				}
				break;
			}
			index++;
		}
		updateUI(this.gameUI, this.viewport);
		this.selectionListener.onSelectionChanged(this.selectedIndex, getSelectedItem());
	}

	@Override
	public UIFrame touchUp(final float screenX, final float screenY, final int button) {
		if (isVisible() && this.renderBounds.contains(screenX, screenY)) {
			if (this.scrollBarFrame != null) {
				final UIFrame sliderFrameChildUnderMouse = this.scrollBarFrame.touchDown(screenX, screenY, button);
				if (sliderFrameChildUnderMouse != null) {
					return sliderFrameChildUnderMouse;
				}
			}
			return this;
		}
		return super.touchUp(screenX, screenY, button);
	}

	@Override
	public void mouseDown(final GameUI gameUI, final Viewport uiViewport) {
	}

	@Override
	public void mouseUp(final GameUI gameUI, final Viewport uiViewport) {
	}

	@Override
	public void mouseEnter(final GameUI gameUI, final Viewport uiViewport) {
	}

	@Override
	public void mouseExit(final GameUI gameUI, final Viewport uiViewport) {
	}

	@Override
	public void onClick(final int button) {
		if (this.touchPress && !this.pressDragged) {
			selectAt(this.pressX, this.pressY);
		}
	}

	@Override
	public String getSoundKey() {
		return SOUND_KEY_NONE;
	}

	@Override
	public void mouseDragged(final GameUI rootFrame, final Viewport uiViewport, final float x, final float y) {
		if (!this.touchPress || (this.scrollBarFrame == null) || this.listFrames.isEmpty()) {
			return;
		}
		this.pressDragged = true;
		final int hiddenItems = this.listItems.size() - this.listFrames.size();
		if (hiddenItems <= 0) {
			return;
		}
		final float itemHeight = this.listFrames.get(0).getParentFrame().getRenderBounds().height;
		if (itemHeight <= 0) {
			return;
		}
		// Content follows the finger: moving up reveals later items, which is a lower scroll bar value.
		final float draggedItems = (y - this.pressY) / itemHeight;
		final int newValue = Math.round(this.pressScrollValue - draggedItems);
		if (newValue != this.scrollBarFrame.getValue()) {
			this.scrollBarFrame.setValue(rootFrame, uiViewport, newValue);
		}
	}

	@Override
	public UIFrame getFrameChildUnderMouse(final float screenX, final float screenY) {
		if (isVisible() && this.renderBounds.contains(screenX, screenY)) {
			if (this.scrollBarFrame != null) {
				final UIFrame sliderFrameChildUnderMouse = this.scrollBarFrame.getFrameChildUnderMouse(screenX,
						screenY);
				if (sliderFrameChildUnderMouse != null) {
					return sliderFrameChildUnderMouse;
				}
			}
			int index = 0;
			int mouseOverIndex = -1;
			for (final AbstractListItemDisplay listFrame : this.listFrames) {
				if (listFrame.getParentFrame().getRenderBounds().contains(screenX, screenY)) {
					mouseOverIndex = index + computeScrollOffset(this.listFrames.size());
					break;
				}
				index++;
			}
			if (this.mouseOverIndex != mouseOverIndex) {
				this.mouseOverIndex = mouseOverIndex;
				updateUI(this.gameUI, this.viewport);
			}
		}
		return super.getFrameChildUnderMouse(screenX, screenY);
	}

	public void setSelectionListener(final ListBoxSelelectionListener selectionListener) {
		this.selectionListener = selectionListener;
	}

	@Override
	public void onChange(final GameUI gameUI, final Viewport uiViewport, final int newValue) {
		updateUI(gameUI, uiViewport);
	}

	public interface ListBoxSelelectionListener {
		void onSelectionChanged(int newSelectedIndex, String newSelectedItem);

		ListBoxSelelectionListener DO_NOTHING = new ListBoxSelelectionListener() {
			@Override
			public void onSelectionChanged(final int newSelectedIndex, final String newSelectedItem) {
			}
		};
	}
}
