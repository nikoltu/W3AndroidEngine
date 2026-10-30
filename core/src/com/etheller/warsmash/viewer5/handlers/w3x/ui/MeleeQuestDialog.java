package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.AnchorDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame;
import com.etheller.warsmash.parsers.fdf.frames.BackdropFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.ScrollBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextAreaFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest.CDefeatCondition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest.CQuest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest.CQuestItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest.CQuestLog;

/**
 * The Quests dialog (F9) of the original game, built from QuestDialog.fdf: the main and optional quests the map
 * script made, each a button with its icon, and for the chosen quest its requirements, the map's defeat conditions
 * and the quest's description. Placement and order as measured in the original game (1.29).
 */
public class MeleeQuestDialog {
	private static final float DIALOG_TOP = -0.03f;
	private static final int LIST_ROWS = 3;
	private static final float LIST_ITEM_WIDTH = 0.2f;
	private static final float LIST_ITEM_HEIGHT = 0.035f;
	private static final float LIST_ICON_SIZE = 0.035f;
	private static final float LIST_ITEM_FRAME_HEIGHT = 0.033f;
	private static final float BUTTON_INSET = 0.003f;
	// Measured places of the texts; this engine does not apply FDF FontJustificationOffset.
	private static final float LIST_TITLE_LEFT = 0.007f;
	private static final float REQUIREMENT_LEFT = 0.012f;
	private static final int REQUIREMENT_ROWS = 4;
	private static final float REQUIREMENT_LINE_HEIGHT = 0.013f;

	private final GameUI rootFrame;
	private final Viewport uiViewport;
	private final CQuestLog questLog;
	private final SimpleFrame container;
	private final UIFrame dialog;
	private final QuestList mainList;
	private final QuestList optionalList;
	private final StringFrame detailsTitle;
	private final UIFrame requirementsContainer;
	private final ScrollBarFrame requirementsScrollBar;
	private final List<StringFrame> requirementLines = new ArrayList<>();
	private final List<Requirement> requirements = new ArrayList<>();
	private int firstRequirement;
	private final TextAreaFrame display;
	private final Texture undiscoveredIcon;
	private CQuest selected;
	private boolean changed;

	public MeleeQuestDialog(final GameUI rootFrame, final Viewport uiViewport, final CQuestLog questLog) {
		this.rootFrame = rootFrame;
		this.uiViewport = uiViewport;
		this.questLog = questLog;
		this.container = (SimpleFrame) rootFrame.createSimpleFrame("SmashQuestDialog", rootFrame, 0);
		this.container.addAnchor(new AnchorDefinition(FramePoint.TOP, 0, GameUI.convertY(uiViewport, DIALOG_TOP)));
		this.dialog = rootFrame.createFrame("QuestDialog", this.container, 0, 0);
		this.dialog.addSetPoint(new SetPoint(FramePoint.CENTER, this.container, FramePoint.CENTER, 0, 0));
		this.container.add(this.dialog);
		this.container.setWidth(this.dialog.getAssignedWidth());
		this.container.setHeight(this.dialog.getAssignedHeight());

		rootFrame.setText((StringFrame) rootFrame.getFrameByName("QuestTitleValue", 0), "");
		rootFrame.setDecoratedText((StringFrame) rootFrame.getFrameByName("QuestSubtitleValue", 0), "QUESTS");
		this.mainList = new QuestList(rootFrame.getFrameByName("QuestMainContainer", 0), "QuestMainListScrollBar");
		this.optionalList = new QuestList(rootFrame.getFrameByName("QuestOptionalContainer", 0),
				"QuestOptionalListScrollBar");
		this.detailsTitle = (StringFrame) rootFrame.getFrameByName("QuestDetailsTitle", 0);
		this.requirementsContainer = rootFrame.getFrameByName("QuestItemListContainer", 0);
		this.display = (TextAreaFrame) rootFrame.getFrameByName("QuestDisplay", 0);
		// The FDF pins the text area's lower corner to the backdrop it is inside, which is not named yet while its
		// children are made.
		this.display.addSetPoint(new SetPoint(FramePoint.BOTTOMRIGHT,
				rootFrame.getFrameByName("QuestDisplayBackdrop", 0), FramePoint.BOTTOMRIGHT,
				GameUI.convertX(uiViewport, -0.01f), GameUI.convertY(uiViewport, 0.02f)));
		this.requirementsScrollBar = (ScrollBarFrame) rootFrame.createFrame("QuestItemListScrollBar", this.container,
				0, 0);
		this.container.add(this.requirementsScrollBar);
		this.requirementsScrollBar.setChangeListener((gameUI, viewport, newValue) -> {
			final int hidden = Math.max(0, this.requirements.size() - REQUIREMENT_ROWS);
			this.firstRequirement = Math.round(((100 - newValue) / 100f) * hidden);
			showRequirementLines();
		});
		for (int i = 0; i < REQUIREMENT_ROWS; i++) {
			this.requirementLines.add(createRequirementLine(i));
		}
		this.undiscoveredIcon = rootFrame.loadTexture(rootFrame.getSkinField("UndiscoveredQuestIcon"));
		final GlueTextButtonFrame acceptButton = (GlueTextButtonFrame) rootFrame.getFrameByName("QuestAcceptButton",
				0);
		acceptButton.setOnClick(() -> setVisible(false));
		questLog.setChangeListener(() -> this.changed = true);
		this.container.setVisible(false);
	}

	private StringFrame createRequirementLine(final int row) {
		final UIFrame line = this.rootFrame.createFrame("QuestItemListItem", this.container, 0, 0);
		this.container.add(line);
		line.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.requirementsContainer, FramePoint.TOPLEFT, 0,
				GameUI.convertY(this.uiViewport, -row * REQUIREMENT_LINE_HEIGHT)));
		line.setWidth(this.requirementsContainer.getAssignedWidth());
		final StringFrame text = (StringFrame) this.rootFrame.getFrameByName("QuestItemListItemTitle", 0);
		text.addSetPoint(new SetPoint(FramePoint.LEFT, line, FramePoint.LEFT,
				GameUI.convertX(this.uiViewport, REQUIREMENT_LEFT), 0));
		text.addSetPoint(new SetPoint(FramePoint.RIGHT, line, FramePoint.RIGHT,
				GameUI.convertX(this.uiViewport, -0.002f), 0));
		return text;
	}

	public UIFrame getFrame() {
		return this.container;
	}

	public boolean isVisible() {
		return this.container.isVisible();
	}

	public boolean contains(final float uiX, final float uiY) {
		return isVisible() && ((AbstractRenderableFrame) this.dialog).getRenderBounds().contains(uiX, uiY);
	}

	public void setVisible(final boolean visible) {
		this.container.setVisible(visible);
		if (visible) {
			refresh();
		}
	}

	/**
	 * Shows what the script changed while the dialog is open.
	 */
	public void update() {
		if (this.changed && isVisible()) {
			refresh();
		}
	}

	public void forceUpdate() {
		this.changed = true;
		update();
	}

	private void refresh() {
		this.changed = false;
		final List<CQuest> main = new ArrayList<>();
		final List<CQuest> optional = new ArrayList<>();
		// Measured: open quests first, then failed, then completed, each in the order the script made them.
		for (int pass = 0; pass < 3; pass++) {
			for (final CQuest quest : this.questLog.getQuests()) {
				if (quest.isEnabled() && (listPass(quest) == pass)) {
					(quest.isRequired() ? main : optional).add(quest);
				}
			}
		}
		if ((this.selected == null) || !(main.contains(this.selected) || optional.contains(this.selected))) {
			this.selected = !main.isEmpty() ? main.get(0) : (!optional.isEmpty() ? optional.get(0) : null);
		}
		this.mainList.show(main);
		this.optionalList.show(optional);
		showDetails();
		this.container.positionBounds(this.rootFrame, this.uiViewport);
		this.mainList.positionItems();
		this.optionalList.positionItems();
		showRequirementLines();
		showDescription();
	}

	private static int listPass(final CQuest quest) {
		if (quest.isCompleted()) {
			return 2;
		}
		return quest.isFailed() ? 1 : 0;
	}

	private void showDetails() {
		final CQuest quest = this.selected;
		this.requirements.clear();
		if (quest == null) {
			this.rootFrame.setText(this.detailsTitle, "");
		}
		else if (quest.isDiscovered()) {
			this.rootFrame.setText(this.detailsTitle, quest.getTitle());
			final String completed = this.rootFrame.getTemplates().getDecoratedString("QUESTCOMPLETED");
			for (final CQuestItem item : quest.getItems()) {
				if (item.isCompleted()) {
					this.requirements
							.add(new Requirement("- " + item.getDescription() + " ( " + completed + " )", true));
				}
				else {
					this.requirements.add(new Requirement("- " + item.getDescription(), false));
				}
			}
			for (final CDefeatCondition condition : this.questLog.getDefeatConditions()) {
				this.requirements.add(new Requirement("- " + condition.getDescription(), false));
			}
		}
		else {
			this.rootFrame.setDecoratedText(this.detailsTitle, "UNDISCOVERED_QUEST");
		}
		this.firstRequirement = 0;
		final boolean scrolls = this.requirements.size() > REQUIREMENT_ROWS;
		this.requirementsScrollBar.setVisible(scrolls);
		if (scrolls) {
			this.requirementsScrollBar.setValue(this.rootFrame, this.uiViewport, 100);
		}
	}

	private void showRequirementLines() {
		for (int row = 0; row < REQUIREMENT_ROWS; row++) {
			final StringFrame line = this.requirementLines.get(row);
			final int index = this.firstRequirement + row;
			if (index < this.requirements.size()) {
				final Requirement requirement = this.requirements.get(index);
				this.rootFrame.setText(line, requirement.text);
				line.setColor(requirement.completed ? line.getFontDisabledColor() : line.getFontOriginalColor());
			}
			else {
				this.rootFrame.setText(line, "");
			}
			line.positionBounds(this.rootFrame, this.uiViewport);
		}
	}

	private void showDescription() {
		final CQuest quest = this.selected;
		String text = "";
		if (quest != null) {
			text = quest.isDiscovered() ? quest.getDescription()
					: this.rootFrame.getTemplates().getDecoratedString("QUESTNOTDISCOVERED");
		}
		final ScrollBarFrame scrollBar = this.display.getScrollBarFrame();
		if (scrollBar != null) {
			scrollBar.setValue(this.rootFrame, this.uiViewport, 100);
		}
		this.display.setText(text, this.rootFrame, this.uiViewport);
	}

	private void select(final CQuest quest) {
		this.selected = quest;
		refresh();
	}

	private static final class Requirement {
		private final String text;
		private final boolean completed;

		private Requirement(final String text, final boolean completed) {
			this.text = text;
			this.completed = completed;
		}
	}

	private final class QuestList {
		private final UIFrame listContainer;
		private final ScrollBarFrame scrollBar;
		private final List<ListItem> items = new ArrayList<>();
		private List<CQuest> quests = new ArrayList<>();
		private int firstRow;

		private QuestList(final UIFrame listContainer, final String scrollBarName) {
			this.listContainer = listContainer;
			this.scrollBar = (ScrollBarFrame) MeleeQuestDialog.this.rootFrame.createFrame(scrollBarName,
					MeleeQuestDialog.this.container, 0, 0);
			MeleeQuestDialog.this.container.add(this.scrollBar);
			this.scrollBar.setChangeListener((gameUI, uiViewport, newValue) -> {
				final int hiddenRows = Math.max(0, this.quests.size() - LIST_ROWS);
				final int firstRow = Math.round(((100 - newValue) / 100f) * hiddenRows);
				if (firstRow != this.firstRow) {
					this.firstRow = firstRow;
					positionItems();
				}
			});
		}

		private void show(final List<CQuest> quests) {
			this.quests = quests;
			while (this.items.size() < quests.size()) {
				this.items.add(new ListItem());
			}
			for (int i = 0; i < this.items.size(); i++) {
				this.items.get(i).show(i < quests.size() ? quests.get(i) : null);
			}
			this.firstRow = Math.min(this.firstRow, Math.max(0, quests.size() - LIST_ROWS));
			this.scrollBar.setVisible(quests.size() > LIST_ROWS);
		}

		private void positionItems() {
			final GameUI rootFrame = MeleeQuestDialog.this.rootFrame;
			final Viewport viewport = MeleeQuestDialog.this.uiViewport;
			this.scrollBar.positionBounds(rootFrame, viewport);
			for (int i = 0; i < this.quests.size(); i++) {
				final ListItem item = this.items.get(i);
				final int row = i - this.firstRow;
				final boolean onList = (row >= 0) && (row < LIST_ROWS);
				item.frame.setVisible(onList);
				item.selectedHighlight.setVisible(onList && (item.quest == MeleeQuestDialog.this.selected));
				if (onList) {
					item.frame.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.listContainer, FramePoint.TOPLEFT, 0,
							GameUI.convertY(viewport, -row * LIST_ITEM_HEIGHT)));
					item.frame.positionBounds(rootFrame, viewport);
					item.selectedHighlight.positionBounds(rootFrame, viewport);
				}
			}
		}
	}

	private final class ListItem {
		private final UIFrame frame;
		private final BackdropFrame icon;
		private final GlueButtonFrame button;
		private final UIFrame normalBackdrop;
		private final UIFrame completedBackdrop;
		private final UIFrame failedBackdrop;
		private final UIFrame selectedHighlight;
		private final StringFrame status;
		private final StringFrame title;
		private CQuest quest;

		private ListItem() {
			final GameUI rootFrame = MeleeQuestDialog.this.rootFrame;
			final Viewport viewport = MeleeQuestDialog.this.uiViewport;
			this.frame = rootFrame.createFrame("QuestListItem", MeleeQuestDialog.this.container, 0, 0);
			this.frame.setWidth(GameUI.convertX(viewport, LIST_ITEM_WIDTH));
			MeleeQuestDialog.this.container.add(this.frame);
			this.icon = (BackdropFrame) rootFrame.getFrameByName("QuestListItemIconContainer", 0);
			this.button = (GlueButtonFrame) rootFrame.getFrameByName("QuestListItemButton", 0);
			this.status = (StringFrame) rootFrame.getFrameByName("QuestListItemComplete", 0);
			this.title = (StringFrame) rootFrame.getFrameByName("QuestListItemTitle", 0);
			// The FDF pins these to the list item itself, which is not named yet while its children are made.
			this.icon.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.frame, FramePoint.TOPLEFT, 0, 0));
			this.button.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.icon, FramePoint.TOPRIGHT,
					GameUI.convertX(viewport, -BUTTON_INSET), 0));
			// Sized here too: an explicit size from the template wins over the second anchor in this engine.
			this.button.setWidth(GameUI.convertX(viewport, LIST_ITEM_WIDTH - LIST_ICON_SIZE));
			this.button.setHeight(GameUI.convertY(viewport, LIST_ITEM_FRAME_HEIGHT));
			this.title.addSetPoint(new SetPoint(FramePoint.LEFT, this.button, FramePoint.LEFT,
					GameUI.convertX(viewport, LIST_TITLE_LEFT), 0));
			this.normalBackdrop = backdrop("QuestButtonBackdropTemplate");
			this.completedBackdrop = backdrop("QuestButtonPushedBackdropTemplate");
			this.failedBackdrop = backdrop("QuestButtonDisabledPushedBackdropTemplate");
			this.selectedHighlight = rootFrame.createFrame("QuestButtonMouseOverHighlightTemplate", this.button, 0,
					0);
			this.selectedHighlight.setSetAllPoints(true);
			MeleeQuestDialog.this.container.add(this.selectedHighlight);
			this.button.setOnClick(() -> {
				if (this.quest != null) {
					select(this.quest);
				}
			});
		}

		private UIFrame backdrop(final String template) {
			final UIFrame backdrop = MeleeQuestDialog.this.rootFrame.createFrame(template, this.button, 0, 0);
			backdrop.setSetAllPoints(true);
			return backdrop;
		}

		private void show(final CQuest quest) {
			this.quest = quest;
			if (quest == null) {
				this.frame.setVisible(false);
				this.selectedHighlight.setVisible(false);
				return;
			}
			final GameUI rootFrame = MeleeQuestDialog.this.rootFrame;
			if (quest.isDiscovered()) {
				rootFrame.setText(this.title, quest.getTitle());
				this.icon.setBackground(
						quest.getIconPath().isEmpty() ? null : rootFrame.loadTexture(quest.getIconPath()));
			}
			else {
				rootFrame.setDecoratedText(this.title, "UNDISCOVERED_QUEST");
				this.icon.setBackground(MeleeQuestDialog.this.undiscoveredIcon);
			}
			UIFrame backdrop = this.normalBackdrop;
			String status = "";
			if (quest.isCompleted()) {
				backdrop = this.completedBackdrop;
				status = "QUESTCOMPLETED";
			}
			else if (quest.isFailed()) {
				backdrop = this.failedBackdrop;
				status = "QUESTFAILED";
			}
			this.button.setControlBackdrop(backdrop);
			this.button.setControlPushedBackdrop(backdrop);
			rootFrame.setDecoratedText(this.status, status);
		}
	}
}
