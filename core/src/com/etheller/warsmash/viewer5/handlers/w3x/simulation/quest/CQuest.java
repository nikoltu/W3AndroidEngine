package com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CQuest {
	private final CQuestLog log;
	private final List<CQuestItem> items = new ArrayList<>();
	private String title = "";
	private String description = "";
	private String iconPath = "";
	private boolean required = true;
	private boolean completed;
	private boolean failed;
	private boolean discovered = true;
	private boolean enabled = true;
	private boolean destroyed;

	CQuest(final CQuestLog log) {
		this.log = log;
	}

	public CQuestItem createItem() {
		final CQuestItem item = new CQuestItem(this);
		this.items.add(item);
		changed();
		return item;
	}

	public List<CQuestItem> getItems() {
		return Collections.unmodifiableList(this.items);
	}

	public String getTitle() {
		return this.title;
	}

	public void setTitle(final String title) {
		this.title = title == null ? "" : title;
		changed();
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(final String description) {
		this.description = description == null ? "" : description;
		changed();
	}

	public String getIconPath() {
		return this.iconPath;
	}

	public void setIconPath(final String iconPath) {
		this.iconPath = iconPath == null ? "" : iconPath;
		changed();
	}

	public boolean isRequired() {
		return this.required;
	}

	public void setRequired(final boolean required) {
		this.required = required;
		changed();
	}

	public boolean isCompleted() {
		return this.completed;
	}

	public void setCompleted(final boolean completed) {
		this.completed = completed;
		changed();
	}

	public boolean isFailed() {
		return this.failed;
	}

	public void setFailed(final boolean failed) {
		this.failed = failed;
		changed();
	}

	public boolean isDiscovered() {
		return this.discovered;
	}

	public void setDiscovered(final boolean discovered) {
		this.discovered = discovered;
		changed();
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(final boolean enabled) {
		this.enabled = enabled;
		changed();
	}

	public boolean isDestroyed() {
		return this.destroyed;
	}

	void destroy() {
		this.destroyed = true;
	}

	void changed() {
		if (!this.destroyed) {
			this.log.changed();
		}
	}
}
