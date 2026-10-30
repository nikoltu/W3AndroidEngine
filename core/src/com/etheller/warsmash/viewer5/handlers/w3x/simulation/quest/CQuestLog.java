package com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The quests and defeat conditions a map script made, in the order it made them, which is the order the Quests dialog
 * lists them in.
 */
public class CQuestLog {
	private final List<CQuest> quests = new ArrayList<>();
	private final List<CDefeatCondition> defeatConditions = new ArrayList<>();
	private Runnable changeListener = () -> {
	};

	public void setChangeListener(final Runnable changeListener) {
		this.changeListener = changeListener;
	}

	public CQuest createQuest() {
		final CQuest quest = new CQuest(this);
		this.quests.add(quest);
		changed();
		return quest;
	}

	public void destroyQuest(final CQuest quest) {
		if (this.quests.remove(quest)) {
			quest.destroy();
			changed();
		}
	}

	public CDefeatCondition createDefeatCondition() {
		final CDefeatCondition condition = new CDefeatCondition(this);
		this.defeatConditions.add(condition);
		changed();
		return condition;
	}

	public void destroyDefeatCondition(final CDefeatCondition condition) {
		if (this.defeatConditions.remove(condition)) {
			changed();
		}
	}

	public List<CQuest> getQuests() {
		return Collections.unmodifiableList(this.quests);
	}

	public List<CDefeatCondition> getDefeatConditions() {
		return Collections.unmodifiableList(this.defeatConditions);
	}

	void changed() {
		this.changeListener.run();
	}
}
