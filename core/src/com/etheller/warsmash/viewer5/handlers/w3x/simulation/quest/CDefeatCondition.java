package com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest;

public class CDefeatCondition {
	private final CQuestLog log;
	private String description = "";

	CDefeatCondition(final CQuestLog log) {
		this.log = log;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(final String description) {
		this.description = description == null ? "" : description;
		this.log.changed();
	}
}
