package com.etheller.warsmash.parsers.jass;

import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest.CDefeatCondition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest.CQuest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest.CQuestItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialogButton;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CTimerDialog;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Quests, defeat conditions, dialogs, leaderboards and timer dialogs.
 */
final class QuestNatives extends CommonNatives {
	QuestNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		registerQuests();
		registerDialogs();
		registerTimerDialogsAndLeaderboards();
	}

	private void registerQuests() {
		final HandleJassType questType = type("quest");
		final HandleJassType questitemType = type("questitem");
		final HandleJassType defeatconditionType = type("defeatcondition");

		jassProgramVisitor.getJassNativeManager().createNative("CreateQuest",
				(arguments, globalScope, triggerScope) -> {
					return newHandle(questType,
							this.simulation.getQuestLog().createQuest());
				});
		jassProgramVisitor.getJassNativeManager().createNative("DestroyQuest",
				(arguments, globalScope, triggerScope) -> {
					final CQuest quest = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (quest != null) {
						this.simulation.getQuestLog().destroyQuest(quest);
						release(quest);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("QuestSetTitle",
				(arguments, globalScope, triggerScope) -> {
					final CQuest quest = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (quest != null) {
						quest.setTitle(this.gameUI
								.getTrigStr(nullable(arguments, 1, StringJassValueVisitor.getInstance())));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("QuestSetDescription",
				(arguments, globalScope, triggerScope) -> {
					final CQuest quest = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (quest != null) {
						quest.setDescription(this.gameUI
								.getTrigStr(nullable(arguments, 1, StringJassValueVisitor.getInstance())));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("QuestSetIconPath",
				(arguments, globalScope, triggerScope) -> {
					final CQuest quest = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (quest != null) {
						quest.setIconPath(nullable(arguments, 1, StringJassValueVisitor.getInstance()));
					}
					return null;
				});
		registerQuestFlagNatives(jassProgramVisitor, "Required", CQuest::setRequired, CQuest::isRequired);
		registerQuestFlagNatives(jassProgramVisitor, "Completed", CQuest::setCompleted, CQuest::isCompleted);
		registerQuestFlagNatives(jassProgramVisitor, "Discovered", CQuest::setDiscovered,
				CQuest::isDiscovered);
		registerQuestFlagNatives(jassProgramVisitor, "Failed", CQuest::setFailed, CQuest::isFailed);
		registerQuestFlagNatives(jassProgramVisitor, "Enabled", CQuest::setEnabled, CQuest::isEnabled);
		jassProgramVisitor.getJassNativeManager().createNative("QuestCreateItem",
				(arguments, globalScope, triggerScope) -> {
					final CQuest quest = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if ((quest == null) || quest.isDestroyed()) {
						return questitemType.getNullValue();
					}
					return newHandle(questitemType, quest.createItem());
				});
		jassProgramVisitor.getJassNativeManager().createNative("QuestItemSetDescription",
				(arguments, globalScope, triggerScope) -> {
					final CQuestItem item = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (item != null) {
						item.setDescription(this.gameUI
								.getTrigStr(nullable(arguments, 1, StringJassValueVisitor.getInstance())));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("QuestItemSetCompleted",
				(arguments, globalScope, triggerScope) -> {
					final CQuestItem item = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (item != null) {
						item.setCompleted(arguments.get(1).visit(BooleanJassValueVisitor.getInstance()));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsQuestItemCompleted",
				(arguments, globalScope, triggerScope) -> {
					final CQuestItem item = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((item != null) && item.isCompleted());
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateDefeatCondition",
				(arguments, globalScope, triggerScope) -> {
					return newHandle(defeatconditionType,
							this.simulation.getQuestLog().createDefeatCondition());
				});
		jassProgramVisitor.getJassNativeManager().createNative("DestroyDefeatCondition",
				(arguments, globalScope, triggerScope) -> {
					final CDefeatCondition condition = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					if (condition != null) {
						this.simulation.getQuestLog().destroyDefeatCondition(condition);
						release(condition);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("DefeatConditionSetDescription",
				(arguments, globalScope, triggerScope) -> {
					final CDefeatCondition condition = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					if (condition != null) {
						condition.setDescription(this.gameUI
								.getTrigStr(nullable(arguments, 1, StringJassValueVisitor.getInstance())));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("FlashQuestDialogButton",
				(arguments, globalScope, triggerScope) -> {
					meleeUI.flashQuestDialogButton();
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ForceQuestDialogUpdate",
				(arguments, globalScope, triggerScope) -> {
					meleeUI.forceQuestDialogUpdate();
					return null;
				});
	}

	private void registerDialogs() {
		final HandleJassType dialogType = type("dialog");
		final HandleJassType buttonType = type("button");

		jassProgramVisitor.getJassNativeManager().createNative("DialogCreate",
				(arguments, globalScope, triggerScope) -> {
					return newHandle(dialogType, meleeUI.createScriptDialog(globalScope));
				});
		jassProgramVisitor.getJassNativeManager().createNative("DialogDestroy",
				(arguments, globalScope, triggerScope) -> {
					final CScriptDialog dialog = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					meleeUI.destroyDialog(dialog);
					release(dialog);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("DialogClear",
				(arguments, globalScope, triggerScope) -> {
					final CScriptDialog dialog = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					meleeUI.clearDialog(dialog);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("DialogSetMessage",
				(arguments, globalScope, triggerScope) -> {
					final CScriptDialog dialog = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final String messageText = this.gameUI
							.getTrigStr(arguments.get(1).visit(StringJassValueVisitor.getInstance()));
					dialog.setTitle(this.gameUI, messageText);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("DialogAddButton",
				(arguments, globalScope, triggerScope) -> {
					final CScriptDialog dialog = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final String buttonText = this.gameUI
							.getTrigStr(arguments.get(1).visit(StringJassValueVisitor.getInstance()));
					final int hotkeyInt = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					return newHandle(buttonType,
							meleeUI.createScriptDialogButton(dialog, buttonText, (char) hotkeyInt));
				});
		jassProgramVisitor.getJassNativeManager().createNative("DialogAddQuitButton",
				(arguments, globalScope, triggerScope) -> {
					final CScriptDialog dialog = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final boolean doScoreScreen = arguments.get(1)
							.visit(BooleanJassValueVisitor.getInstance());
					final String buttonText = this.gameUI
							.getTrigStr(arguments.get(2).visit(StringJassValueVisitor.getInstance()));
					final int hotkeyInt = arguments.get(3).visit(IntegerJassValueVisitor.getInstance());
					final CScriptDialogButton button = meleeUI.createScriptDialogButton(dialog, buttonText,
							(char) hotkeyInt);
					// The melee victory and defeat dialogs quit to the score screen.
					button.setQuitAction(() -> meleeUI.endGame(doScoreScreen));
					return newHandle(buttonType, button);
				});
		jassProgramVisitor.getJassNativeManager().createNative("DialogDisplay",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CScriptDialog whichDialog = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					if ((player != null) && (whichDialog != null)
							&& (player.getId() == war3MapViewer.getLocalPlayerIndex())) {
						meleeUI.displayScriptDialog(whichDialog, flag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetClickedButton",
				(arguments, globalScope, triggerScope) -> {
					return newHandle(buttonType,
							((CommonTriggerExecutionScope) triggerScope).getClickedButton());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetClickedDialog",
				(arguments, globalScope, triggerScope) -> {
					return newHandle(dialogType,
							((CommonTriggerExecutionScope) triggerScope).getClickedDialog());
				});
	}

	private void registerTimerDialogsAndLeaderboards() {
		final HandleJassType timerdialogType = type("timerdialog");

		jassProgramVisitor.getJassNativeManager().createNative("CreateTimerDialog",
				(arguments, globalScope, triggerScope) -> {
					final CTimer timer = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return newHandle(timerdialogType, meleeUI.createTimerDialog(timer));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TimerDialogSetTitle",
				(arguments, globalScope, triggerScope) -> {
					final CTimerDialog dialog = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (dialog != null) {
						dialog.setTitle(this.gameUI
								.getTrigStr(nullable(arguments, 1, StringJassValueVisitor.getInstance())));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("TimerDialogDisplay",
				(arguments, globalScope, triggerScope) -> {
					final CTimerDialog dialog = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (dialog != null) {
						dialog.setVisible(arguments.get(1).visit(BooleanJassValueVisitor.getInstance()));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("TimerDialogSetRealTimeRemaining",
				(arguments, globalScope, triggerScope) -> {
					final CTimerDialog dialog = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (dialog != null) {
						dialog.setRealTimeRemaining(this.simulation,
								arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue());
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("TimerDialogSetSpeed",
				(arguments, globalScope, triggerScope) -> {
					final CTimerDialog dialog = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (dialog != null) {
						dialog.setSpeed(this.simulation,
								arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue());
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsTimerDialogDisplayed",
				(arguments, globalScope, triggerScope) -> {
					final CTimerDialog dialog = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((dialog != null) && dialog.isVisible());
				});
	}

	private static void registerQuestFlagNatives(final JassProgram jassProgramVisitor, final String flag,
			final BiConsumer<CQuest, Boolean> setter, final Predicate<CQuest> getter) {
		jassProgramVisitor.getJassNativeManager().createNative("QuestSet" + flag,
				(arguments, globalScope, triggerScope) -> {
					final CQuest quest = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (quest != null) {
						setter.accept(quest, arguments.get(1).visit(BooleanJassValueVisitor.getInstance()));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsQuest" + flag,
				(arguments, globalScope, triggerScope) -> {
					final CQuest quest = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((quest != null) && getter.test(quest));
				});
	}
}
