package com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest;

public class CQuestItem {
	private final CQuest quest;
	private String description = "";
	private boolean completed;

	CQuestItem(final CQuest quest) {
		this.quest = quest;
	}

	public CQuest getQuest() {
		return this.quest;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(final String description) {
		this.description = description == null ? "" : description;
		this.quest.changed();
	}

	public boolean isCompleted() {
		return this.completed;
	}

	public void setCompleted(final boolean completed) {
		this.completed = completed;
		this.quest.changed();
	}
}
