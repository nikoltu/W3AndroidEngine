package com.etheller.warsmash.viewer5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.datasources.CompoundDataSource;

class MissingSourcesTest {
	private static final String PATH = "Abilities/Spells/SpikedShellTargetChestLeft.mdx";

	@Test
	void aMissingPathIsAnsweredAgain() {
		final CompoundDataSource data = new CompoundDataSource(new ArrayList<>());
		final MissingSources missing = new MissingSources();
		assertNull(missing.get(data, PATH));
		missing.put(data, PATH, PATH);
		assertEquals(PATH, missing.get(data, PATH));
		assertNull(missing.get(data, "other.mdx"));
		assertEquals(1, missing.size());
	}

	@Test
	void anotherDataSourceForgetsIt() {
		final CompoundDataSource data = new CompoundDataSource(new ArrayList<>());
		final MissingSources missing = new MissingSources();
		missing.put(data, PATH, PATH);
		assertNull(missing.get(new CompoundDataSource(new ArrayList<>()), PATH));
		assertEquals(0, missing.size());
	}

	@Test
	void changedSourcesForgetIt() {
		final CompoundDataSource data = new CompoundDataSource(new ArrayList<>());
		final MissingSources missing = new MissingSources();
		missing.put(data, PATH, PATH);
		data.sourcesChanged();
		assertNull(missing.get(data, PATH));
	}

	@Test
	void changedNestedSourcesForgetIt() {
		final CompoundDataSource inner = new CompoundDataSource(new ArrayList<>());
		final ArrayList<com.etheller.warsmash.datasources.DataSource> list = new ArrayList<>();
		list.add(inner);
		final CompoundDataSource outer = new CompoundDataSource(list);
		final MissingSources missing = new MissingSources();
		missing.put(outer, PATH, PATH);
		inner.sourcesChanged();
		assertNull(missing.get(outer, PATH));
	}

	@Test
	void clearForgets() {
		final CompoundDataSource data = new CompoundDataSource(new ArrayList<>());
		final MissingSources missing = new MissingSources();
		missing.put(data, PATH, PATH);
		missing.clear();
		assertNull(missing.get(data, PATH));
	}
}
