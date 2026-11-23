package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.graphics.Color;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.TextTag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackInstant;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitAttackSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAttackProjectileMissile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightning;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderController;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.TextTagConfigType;

class BufferingRenderControllerTest {
	private static final War3ID TYPE = new War3ID(0x68667464);
	private static final War3ID ALIAS = new War3ID(0x41484864);
	private static final int TICK = 7;

	/** Simulation objects without running their constructors (which need a whole game); only the handle is set. */
	private static final class Objects {
		private static final sun.misc.Unsafe UNSAFE;
		static {
			try {
				final Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
				field.setAccessible(true);
				UNSAFE = (sun.misc.Unsafe) field.get(null);
			}
			catch (final ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}

		final Map<Integer, CWidget> widgets = new HashMap<>();
		final CProjectile projectile = make(CAttackProjectileMissile.class);
		private int nextHandle = 0x100000;

		@SuppressWarnings("unchecked")
		private static <T> T make(final Class<T> type) {
			try {
				return (T) UNSAFE.allocateInstance(type);
			}
			catch (final InstantiationException e) {
				throw new IllegalStateException(e);
			}
		}

		<T extends CWidget> T widget(final Class<T> type) {
			final T widget = make(type);
			try {
				final Field field = CWidget.class.getDeclaredField("handleId");
				field.setAccessible(true);
				field.setInt(widget, this.nextHandle);
			}
			catch (final ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
			this.widgets.put(this.nextHandle++, widget);
			return widget;
		}
	}

	/** Logs every call it gets, naming simulation objects by handle and returned things by creation number. */
	private static final class Recorder implements SimulationRenderController {
		final List<String> log = new ArrayList<>();
		private final Objects objects;
		private int made;

		Recorder(final Objects objects) {
			this.objects = objects;
		}

		private static String h(final CWidget widget) {
			return widget == null ? "null" : "w" + widget.getHandleId();
		}

		private static String f(final float... values) {
			return Arrays.toString(values);
		}

		private static String raw(final War3ID id) {
			return id == null ? "null" : Integer.toHexString(id.getValue());
		}

		private final class Component implements SimulationRenderComponentModel {
			private final int n = Recorder.this.made++;

			@Override
			public void remove() {
				Recorder.this.log.add("component" + this.n + ".remove");
			}

			@Override
			public void setHeight(final float height) {
				Recorder.this.log.add("component" + this.n + ".setHeight " + height);
			}
		}

		private final class Lightning implements SimulationRenderComponentLightning {
			private final int n = Recorder.this.made++;
			private boolean removed;
			private float[] color;

			@Override
			public void remove() {
				this.removed = true;
				Recorder.this.log.add("lightning" + this.n + ".remove");
			}

			@Override
			public void setColor(final float r, final float g, final float b, final float a) {
				this.color = new float[] { r, g, b, a };
				Recorder.this.log.add("lightning" + this.n + ".setColor " + f(r, g, b, a));
			}

			@Override
			public float[] getColor() {
				return this.color;
			}

			@Override
			public boolean isRemoved() {
				return this.removed;
			}
		}

		private final class Tag extends TextTag {
			private final int n = Recorder.this.made++;

			Tag() {
				super(new com.badlogic.gdx.math.Vector3(), new com.badlogic.gdx.math.Vector2(), "", Color.WHITE, 1, 0,
						1, -1);
			}

			private void note(final String what) {
				Recorder.this.log.add("tag" + this.n + "." + what);
			}

			@Override
			public void setText(final String text) {
				super.setText(text);
				note("setText " + text);
			}

			@Override
			public void setPosition(final float x, final float y, final float z) {
				super.setPosition(x, y, z);
				note("setPosition " + f(x, y, z));
			}

			@Override
			public void setColor(final int r, final int g, final int b, final float alpha) {
				super.setColor(r, g, b, alpha);
				note("setColor " + r + "," + g + "," + b + "," + alpha);
			}

			@Override
			public void setVelocity(final float vx, final float vy) {
				super.setVelocity(vx, vy);
				note("setVelocity " + f(vx, vy));
			}

			@Override
			public void setVisible(final boolean visible) {
				super.setVisible(visible);
				note("setVisible " + visible);
			}

			@Override
			public void setPermanent(final boolean permanent) {
				super.setPermanent(permanent);
				note("setPermanent " + permanent);
			}

			@Override
			public void setCentered(final boolean centered) {
				super.setCentered(centered);
				note("setCentered " + centered);
			}

			@Override
			public void setCenteredOnPoint(final boolean centeredOnPoint) {
				super.setCenteredOnPoint(centeredOnPoint);
				note("setCenteredOnPoint " + centeredOnPoint);
			}

			@Override
			public void setReceivingPlayer(final int receivingPlayer) {
				super.setReceivingPlayer(receivingPlayer);
				note("setReceivingPlayer " + receivingPlayer);
			}

			@Override
			public void setFadeStart(final float fadeStart) {
				super.setFadeStart(fadeStart);
				note("setFadeStart " + fadeStart);
			}

			@Override
			public void setFontHeight(final float fontHeight) {
				super.setFontHeight(fontHeight);
				note("setFontHeight " + fontHeight);
			}

			@Override
			public void setLifetime(final float lifetime) {
				super.setLifetime(lifetime);
				note("setLifetime " + lifetime);
			}

			@Override
			public void setLifetimeDuration(final float lifetimeDuration) {
				super.setLifetimeDuration(lifetimeDuration);
				note("setLifetimeDuration " + lifetimeDuration);
			}

			@Override
			public void setSuspended(final boolean suspended) {
				super.setSuspended(suspended);
				note("setSuspended " + suspended);
			}

			@Override
			public void setHandleId(final int handleId) {
				super.setHandleId(handleId);
				note("setHandleId " + handleId);
			}
		}

		@Override
		public void spawnProjectileArt(final CProjectile projectile, final CUnit source,
				final CUnitAttackSettings settings, final int bounceIndex) {
			this.log.add("spawnProjectileArt " + (projectile == this.objects.projectile) + " " + h(source) + " "
					+ settings.getProjectileArt() + " " + settings.getProjectileArc() + " "
					+ settings.isProjectileHomingEnabled() + " " + settings.getProjectileSpeed() + " "
					+ settings.getZ() + " " + settings.getImpactZ() + " " + settings.getArtDeathTime() + " "
					+ bounceIndex);
		}

		@Override
		public void spawnProjectileArt(final CProjectile projectile, final CUnit source, final War3ID spellAlias) {
			this.log.add("spawnProjectileArtSpell " + (projectile == this.objects.projectile) + " " + h(source) + " "
					+ raw(spellAlias));
		}

		@Override
		public SimulationRenderComponentLightning createLightning(final CSimulation simulation,
				final War3ID lightningId, final CUnit source, final CUnit target) {
			this.log.add("createLightning " + raw(lightningId) + " " + h(source) + " " + h(target));
			return new Lightning();
		}

		@Override
		public SimulationRenderComponentLightning createLightning(final CSimulation simulation,
				final War3ID lightningId, final CUnit source, final CUnit target, final Float duration) {
			this.log.add("createLightningDuration " + raw(lightningId) + " " + h(source) + " " + h(target) + " "
					+ duration);
			return new Lightning();
		}

		@Override
		public SimulationRenderComponentLightning createAbilityLightning(final CSimulation simulation,
				final War3ID lightningId, final CUnit source, final CUnit target, final int index) {
			this.log.add("createAbilityLightning " + raw(lightningId) + " " + h(source) + " " + h(target) + " "
					+ index);
			return new Lightning();
		}

		@Override
		public SimulationRenderComponentLightning createAbilityLightning(final CSimulation simulation,
				final War3ID lightningId, final CUnit source, final CUnit target, final int index,
				final Float duration) {
			this.log.add("createAbilityLightningDuration " + raw(lightningId) + " " + h(source) + " " + h(target) + " "
					+ index + " " + duration);
			return new Lightning();
		}

		@Override
		public SimulationRenderComponent createStaticUberSplat(final float x, final float y, final War3ID id) {
			this.log.add("createStaticUberSplat " + f(x, y) + " " + raw(id));
			return new Component();
		}

		@Override
		public CUnit createUnit(final CSimulation simulation, final War3ID typeId, final int playerIndex,
				final float x, final float y, final float facing) {
			this.log.add("createUnit " + raw(typeId) + " " + playerIndex + " " + f(x, y, facing));
			return this.objects.widget(CUnit.class);
		}

		@Override
		public CItem createItem(final CSimulation simulation, final War3ID typeId, final float x, final float y) {
			this.log.add("createItem " + raw(typeId) + " " + f(x, y));
			return this.objects.widget(CItem.class);
		}

		@Override
		public void updateItemModel(final CItem item) {
			this.log.add("updateItemModel " + h(item));
		}

		@Override
		public CDestructable createDestructable(final War3ID typeId, final float x, final float y,
				final float facing, final float scale, final int variation) {
			this.log.add("createDestructable " + raw(typeId) + " " + f(x, y, facing, scale) + " " + variation);
			return this.objects.widget(CDestructable.class);
		}

		@Override
		public CDestructable createDestructableZ(final War3ID typeId, final float x, final float y, final float z,
				final float facing, final float scale, final int variation) {
			this.log.add("createDestructableZ " + raw(typeId) + " " + f(x, y, z, facing, scale) + " " + variation);
			return this.objects.widget(CDestructable.class);
		}

		@Override
		public void createInstantAttackEffect(final CSimulation cSimulation, final CUnit source,
				final CUnitAttackInstant attack, final CWidget target) {
			this.log.add("createInstantAttackEffect " + h(source) + " " + attack.getProjectileArt() + " " + h(target));
		}

		@Override
		public void spawnDamageSound(final CWidget damagedDestructable, final String weaponSound,
				final String armorType) {
			this.log.add("spawnDamageSound " + h(damagedDestructable) + " " + weaponSound + " " + armorType);
		}

		@Override
		public void spawnUnitConstructionSound(final CUnit constructingUnit, final CUnit constructedStructure) {
			this.log.add("spawnUnitConstructionSound " + h(constructingUnit) + " " + h(constructedStructure));
		}

		@Override
		public void removeUnit(final CUnit unit) {
			this.log.add("removeUnit " + h(unit));
		}

		@Override
		public void removeItem(final CItem item) {
			this.log.add("removeItem " + h(item));
		}

		@Override
		public void removeDestructable(final CDestructable dest) {
			this.log.add("removeDestructable " + h(dest));
		}

		@Override
		public void spawnUnitConstructionFinishSound(final CUnit constructedStructure) {
			this.log.add("spawnUnitConstructionFinishSound " + h(constructedStructure));
		}

		@Override
		public void spawnUnitUpgradeFinishSound(final CUnit constructedStructure) {
			this.log.add("spawnUnitUpgradeFinishSound " + h(constructedStructure));
		}

		@Override
		public void spawnDeathExplodeEffect(final CUnit cUnit, final War3ID explodesOnDeathBuffId) {
			this.log.add("spawnDeathExplodeEffect " + h(cUnit) + " " + raw(explodesOnDeathBuffId));
		}

		@Override
		public void spawnGainLevelEffect(final CUnit cUnit) {
			this.log.add("spawnGainLevelEffect " + h(cUnit));
		}

		@Override
		public void spawnUnitReadySound(final CUnit trainedUnit) {
			this.log.add("spawnUnitReadySound " + h(trainedUnit));
		}

		@Override
		public void unitRepositioned(final CUnit cUnit) {
			this.log.add("unitRepositioned " + h(cUnit));
		}

		@Override
		public TextTag spawnTextTag(final CUnit unit, final TextTagConfigType configType, final int displayAmount) {
			this.log.add("spawnTextTagAmount " + h(unit) + " " + configType + " " + displayAmount);
			return new Tag();
		}

		@Override
		public TextTag spawnTextTag(final CUnit unit, final TextTagConfigType configType, final String message) {
			this.log.add("spawnTextTagMessage " + h(unit) + " " + configType + " " + message);
			return new Tag();
		}

		@Override
		public TextTag createTextTag() {
			this.log.add("createTextTag");
			return new Tag();
		}

		@Override
		public void destroyTextTag(final TextTag textTag) {
			this.log.add("destroyTextTag " + ((Tag) textTag).n);
		}

		@Override
		public void spawnEffectOnUnit(final CUnit unit, final String effectPath) {
			this.log.add("spawnEffectOnUnit " + h(unit) + " " + effectPath);
		}

		@Override
		public void spawnTemporarySpellEffectOnUnit(final CUnit unit, final War3ID alias,
				final CEffectType effectType) {
			this.log.add("spawnTemporarySpellEffectOnUnit " + h(unit) + " " + raw(alias) + " " + effectType);
		}

		@Override
		public SimulationRenderComponentModel spawnPersistentSpellEffectOnUnit(final CUnit unit, final War3ID alias,
				final CEffectType effectType) {
			this.log.add("spawnPersistentSpellEffectOnUnit " + h(unit) + " " + raw(alias) + " " + effectType);
			return new Component();
		}

		@Override
		public SimulationRenderComponentModel spawnPersistentSpellEffectOnUnit(final CUnit unit, final War3ID alias,
				final CEffectType effectType, final int index) {
			this.log.add("spawnPersistentSpellEffectOnUnitIndexed " + h(unit) + " " + raw(alias) + " " + effectType
					+ " " + index);
			return new Component();
		}

		@Override
		public SimulationRenderComponentModel spawnSpellEffectOnPoint(final float x, final float y,
				final float facing, final War3ID alias, final CEffectType effectType, final int index) {
			this.log.add("spawnSpellEffectOnPoint " + f(x, y, facing) + " " + raw(alias) + " " + effectType + " "
					+ index);
			return new Component();
		}

		@Override
		public void spawnTemporarySpellEffectOnPoint(final float x, final float y, final float facing,
				final War3ID alias, final CEffectType effectType, final int index) {
			this.log.add("spawnTemporarySpellEffectOnPoint " + f(x, y, facing) + " " + raw(alias) + " " + effectType
					+ " " + index);
		}

		@Override
		public void spawnUIUnitGetItemSound(final CUnit cUnit, final CItem item) {
			this.log.add("spawnUIUnitGetItemSound " + h(cUnit) + " " + h(item));
		}

		@Override
		public void spawnUIUnitDropItemSound(final CUnit cUnit, final CItem item) {
			this.log.add("spawnUIUnitDropItemSound " + h(cUnit) + " " + h(item));
		}

		@Override
		public SimulationRenderComponent spawnAbilitySoundEffect(final CUnit caster, final War3ID alias) {
			this.log.add("spawnAbilitySoundEffect " + h(caster) + " " + raw(alias));
			return new Component();
		}

		@Override
		public SimulationRenderComponent loopAbilitySoundEffect(final CUnit caster, final War3ID alias) {
			this.log.add("loopAbilitySoundEffect " + h(caster) + " " + raw(alias));
			return new Component();
		}

		@Override
		public void stopAbilitySoundEffect(final CUnit caster, final War3ID alias) {
			this.log.add("stopAbilitySoundEffect " + h(caster) + " " + raw(alias));
		}

		@Override
		public void unitPreferredSelectionReplacement(final CUnit unit, final CUnit newUnit) {
			this.log.add("unitPreferredSelectionReplacement " + h(unit) + " " + h(newUnit));
		}

		@Override
		public void heroRevived(final CUnit trainedUnit, final boolean doEyecandy) {
			this.log.add("heroRevived " + h(trainedUnit) + " " + doEyecandy);
		}

		@Override
		public void heroDeathEvent(final CUnit cUnit) {
			this.log.add("heroDeathEvent " + h(cUnit));
		}

		@Override
		public SimulationRenderComponentModel createSpellEffectOverDestructable(final CUnit source,
				final CDestructable target, final War3ID alias, final float artAttachmentHeight) {
			this.log.add("createSpellEffectOverDestructable " + h(source) + " " + h(target) + " " + raw(alias) + " "
					+ artAttachmentHeight);
			return new Component();
		}

		@Override
		public void unitUpgradingEvent(final CUnit unit, final War3ID upgradeIdType) {
			this.log.add("unitUpgradingEvent " + h(unit) + " " + raw(upgradeIdType));
		}

		@Override
		public void unitCancelUpgradingEvent(final CUnit unit, final War3ID upgradeIdType) {
			this.log.add("unitCancelUpgradingEvent " + h(unit) + " " + raw(upgradeIdType));
		}

		@Override
		public void blightChanged(final float[] cornerPositions, final boolean blighted) {
			this.log.add("blightChanged " + java.util.Arrays.toString(cornerPositions) + " " + blighted);
		}

		@Override
		public void unitUpdatedType(final CUnit unit, final War3ID typeId, final boolean updatePortrait) {
			this.log.add("unitUpdatedType " + h(unit) + " " + raw(typeId) + " " + updatePortrait);
		}

		@Override
		public void changeUnitColor(final CUnit unit, final int playerIndex) {
			this.log.add("changeUnitColor " + h(unit) + " " + playerIndex);
		}

		@Override
		public void setUnitTeamColor(final CUnit unit, final int color) {
			this.log.add("setUnitTeamColor " + h(unit) + " " + color);
		}

		@Override
		public void changeUnitPlayerColor(final CUnit unit, final int previousColor, final int newColor) {
			this.log.add("changeUnitPlayerColor " + h(unit) + " " + previousColor + " " + newColor);
		}

		@Override
		public void changeUnitVertexColor(final CUnit unit, final Color color) {
			this.log.add("changeUnitVertexColor " + h(unit) + " " + f(color.r, color.g, color.b, color.a));
		}

		@Override
		public void changeUnitVertexColor(final CUnit unit, final float r, final float g, final float b) {
			this.log.add("changeUnitVertexColorRgb " + h(unit) + " " + f(r, g, b));
		}

		@Override
		public void changeUnitVertexColor(final CUnit unit, final float r, final float g, final float b,
				final float a) {
			this.log.add("changeUnitVertexColorRgba " + h(unit) + " " + f(r, g, b, a));
		}

		@Override
		public void adjustTerrain(final float x, final float y, final float i) {
			this.log.add("adjustTerrain " + f(x, y, i));
		}

		@Override
		public void adjustTerrain(final int[] rect, final float[] modBuffer) {
			this.log.add("adjustTerrainRect " + Arrays.toString(rect) + " " + f(modBuffer));
		}

		@Override
		public void setTerrainDeformation(final int column, final int row, final int width, final int height,
				final float[] offsets) {
			this.log.add("setTerrainDeformation " + column + " " + row + " " + width + " " + height + " "
					+ f(offsets));
		}

		@Override
		public void setTerrainTypes(final int[] cells) {
			this.log.add("setTerrainTypes " + Arrays.toString(cells));
		}

		@Override
		public void changeUnitScale(final CUnit unit, final float scale, final boolean multiplier) {
			this.log.add("changeUnitScale " + h(unit) + " " + scale + " " + multiplier);
		}

		@Override
		public SimulationRenderComponent spawnAbilitySoundEffect(final float x, final float y, final War3ID alias,
				final boolean looping) {
			this.log.add("spawnAbilitySoundEffectAtPoint " + f(x, y) + " " + raw(alias) + " " + looping);
			return new Component();
		}
	}

	/** What the calls drive the simulation to read back from the things they returned. */
	private static void script(final SimulationRenderController c, final Objects objects, final List<String> read) {
		final CUnit unit = c.createUnit(null, TYPE, 3, 100.5f, -20f, 90f);
		final CUnit other = c.createUnit(null, TYPE, 4, 1f, 2f, 3f);
		final CItem item = c.createItem(null, TYPE, 5f, 6f);
		c.updateItemModel(item);
		c.removeItem(item);
		final CDestructable dest = c.createDestructable(TYPE, 7f, 8f, 45f, 1.5f, 2);
		final CDestructable destZ = c.createDestructableZ(TYPE, 9f, 10f, 11f, 12f, 0.75f, 3);

		final CUnitAttackSettings settings = new CUnitAttackSettings(0.25f, "Missile.mdx", true, 900);
		settings.setZ(60f);
		settings.setImpactZ(null);
		settings.setArtDeathTime(1.5f);
		c.spawnProjectileArt(objects.projectile, unit, settings, 2);
		c.spawnProjectileArt(objects.projectile, other, ALIAS);
		c.createInstantAttackEffect(null,
				unit, new CUnitAttackInstant(0, 0, null, 0, 0, 0, 0, 0, 0, 0, false, EnumSet.noneOf(CTargetType.class),
						null, null, null, "Instant.mdx"),
				dest);
		c.spawnDamageSound(dest, "MetalHeavyBash", "Wood");

		final SimulationRenderComponentLightning l1 = c.createLightning(null, ALIAS, unit, other);
		final SimulationRenderComponentLightning l2 = c.createLightning(null, ALIAS, unit, other, 2.5f);
		final SimulationRenderComponentLightning l3 = c.createLightning(null, ALIAS, unit, other, (Float) null);
		final SimulationRenderComponentLightning l4 = c.createAbilityLightning(null, ALIAS, other, unit, 1);
		final SimulationRenderComponentLightning l5 = c.createAbilityLightning(null, ALIAS, other, unit, 0, 4f);
		read.add("color before " + l1.getColor() + " removed " + l1.isRemoved());
		l1.setColor(1f, 0.5f, 0.25f, 0.125f);
		l4.setColor(0f, 0f, 0f, 1f);
		read.add("color after " + Arrays.toString(l1.getColor()) + " removed " + l1.isRemoved());
		l1.remove();
		l2.remove();
		l5.setColor(0.5f, 0.5f, 0.5f, 0.5f);
		read.add("l1 removed " + l1.isRemoved() + " l3 removed " + l3.isRemoved());

		final SimulationRenderComponent splat = c.createStaticUberSplat(30f, 40f, ALIAS);
		splat.remove();

		final SimulationRenderComponentModel persistent = c.spawnPersistentSpellEffectOnUnit(unit, ALIAS,
				CEffectType.TARGET);
		final SimulationRenderComponentModel indexed = c.spawnPersistentSpellEffectOnUnit(unit, ALIAS,
				CEffectType.CASTER, 1);
		final SimulationRenderComponentModel onPoint = c.spawnSpellEffectOnPoint(1f, 2f, 3f, ALIAS,
				CEffectType.AREA_EFFECT, 0);
		final SimulationRenderComponentModel overDest = c.createSpellEffectOverDestructable(unit, destZ, ALIAS, 55f);
		persistent.setHeight(12f);
		indexed.setHeight(-1f);
		onPoint.setHeight(0.5f);
		overDest.setHeight(3f);
		persistent.remove();
		overDest.remove();
		c.spawnTemporarySpellEffectOnUnit(unit, ALIAS, CEffectType.EFFECT);
		c.spawnTemporarySpellEffectOnPoint(4f, 5f, 6f, ALIAS, CEffectType.SPECIAL, 2);
		c.spawnEffectOnUnit(unit, "Effect.mdx");

		final SimulationRenderComponent sound = c.spawnAbilitySoundEffect(unit, ALIAS);
		final SimulationRenderComponent loop = c.loopAbilitySoundEffect(unit, ALIAS);
		final SimulationRenderComponent point = c.spawnAbilitySoundEffect(8f, 9f, ALIAS, true);
		c.stopAbilitySoundEffect(unit, ALIAS);
		sound.remove();
		loop.remove();
		point.remove();
		c.spawnUIUnitGetItemSound(unit, item);
		c.spawnUIUnitDropItemSound(unit, item);
		c.spawnUnitConstructionSound(unit, other);
		c.spawnUnitConstructionFinishSound(other);
		c.spawnUnitUpgradeFinishSound(other);
		c.spawnUnitReadySound(unit);

		final TextTag amount = c.spawnTextTag(unit, TextTagConfigType.GOLD, 25);
		amount.setReceivingPlayer(3);
		amount.setCenteredOnPoint(true);
		final TextTag message = c.spawnTextTag(unit, TextTagConfigType.MISS_TEXT, "miss");
		message.setReceivingPlayer(-1);
		final TextTag script = c.createTextTag();
		script.setHandleId(77);
		script.setText("hello");
		script.setPosition(1f, 2f, 3f);
		script.setColor(255, 128, 0, 200f);
		script.setVelocity(0.5f, 0.25f);
		script.setVisible(false);
		script.setPermanent(false);
		script.setCentered(true);
		script.setCenteredOnPoint(false);
		script.setFadeStart(2f);
		script.setFontHeight(0.03f);
		script.setLifetime(1f);
		script.setLifetimeDuration(9f);
		script.setSuspended(true);
		read.add("tag " + script.getHandleId() + " " + script.getText() + " " + script.isVisible() + " "
				+ script.isShownTo(3) + " " + amount.isShownTo(3) + " " + amount.isShownTo(4) + " "
				+ message.isShownTo(4));
		c.destroyTextTag(script);
		c.destroyTextTag(amount);

		c.spawnDeathExplodeEffect(unit, ALIAS);
		c.spawnDeathExplodeEffect(other, null);
		c.spawnGainLevelEffect(unit);
		c.unitRepositioned(unit);
		c.unitPreferredSelectionReplacement(unit, other);
		c.heroRevived(unit, true);
		c.heroRevived(other, false);
		c.heroDeathEvent(unit);
		c.unitUpgradingEvent(unit, ALIAS);
		c.unitCancelUpgradingEvent(unit, TYPE);
		c.unitUpdatedType(unit, TYPE, true);
		c.changeUnitColor(unit, 6);
		c.changeUnitPlayerColor(unit, 1, 2);
		c.setUnitTeamColor(unit, 5);
		c.changeUnitVertexColor(unit, new Color(0.1f, 0.2f, 0.3f, 0.4f));
		c.changeUnitVertexColor(unit, 0.5f, 0.6f, 0.7f);
		c.changeUnitVertexColor(unit, 0.8f, 0.9f, 1f, 0.5f);
		c.changeUnitScale(unit, 1.25f, true);
		c.changeUnitScale(other, 0.5f, false);

		c.blightChanged(new float[] { 1f, 2f, 3f, 4f }, true);
		c.adjustTerrain(10f, 20f, 30f);
		final int[] rect = { 1, 2, 3, 4 };
		final float[] mods = { 0.5f, 1.5f };
		c.adjustTerrain(rect, mods);
		final float[] offsets = { 1f, 2f, 3f, 4f };
		c.setTerrainDeformation(5, 6, 2, 2, offsets);
		final int[] cells = { 1, 2, 3, 4, 5 };
		c.setTerrainTypes(cells);
		// The recorded copies must not follow later changes to the caller's arrays.
		rect[0] = 99;
		mods[0] = 99f;
		offsets[0] = 99f;
		cells[0] = 99;

		c.removeUnit(other);
		c.removeDestructable(dest);
	}

	private static final class TestSink implements RenderEventSink {
		final RenderEventPool pool = new RenderEventPool();
		final List<RenderEvent> events = new ArrayList<>();
		private int order;

		@Override
		public RenderEvent begin(final int code) {
			final RenderEvent event = this.pool.obtain();
			event.tick = TICK;
			event.order = this.order++;
			event.code = code;
			return event;
		}

		@Override
		public void emit(final RenderEvent event) {
			this.events.add(event);
		}
	}

	private static final class Result {
		final List<String> calls;
		final List<String> read;

		Result(final List<String> calls, final List<String> read) {
			this.calls = calls;
			this.read = read;
		}
	}

	private static Result direct() {
		final Objects objects = new Objects();
		final Recorder recorder = new Recorder(objects);
		final List<String> read = new ArrayList<>();
		script(recorder, objects, read);
		return new Result(recorder.log, read);
	}

	private static final class Buffered {
		final TestSink sink = new TestSink();
		final Result result;
		final Set<String> methodsCalled = new HashSet<>();

		Buffered(final boolean play) {
			final Objects simObjects = new Objects();
			final BufferingRenderController controller = new BufferingRenderController(this.sink,
					new BufferingRenderController.WorldFactory() {
						@Override
						public CUnit createUnit(final CSimulation simulation, final War3ID typeId,
								final int playerIndex, final float x, final float y, final float facing) {
							return simObjects.widget(CUnit.class);
						}

						@Override
						public CItem createItem(final CSimulation simulation, final War3ID typeId, final float x,
								final float y) {
							return simObjects.widget(CItem.class);
						}

						@Override
						public CDestructable createDestructable(final War3ID typeId, final float x, final float y,
								final float facing, final float scale, final int variation) {
							return simObjects.widget(CDestructable.class);
						}

						@Override
						public CDestructable createDestructableZ(final War3ID typeId, final float x, final float y,
								final float z, final float facing, final float scale, final int variation) {
							return simObjects.widget(CDestructable.class);
						}
					});
			final SimulationRenderController counted = (SimulationRenderController) Proxy.newProxyInstance(
					BufferingRenderControllerTest.class.getClassLoader(),
					new Class<?>[] { SimulationRenderController.class }, (proxy, method, args) -> {
						this.methodsCalled.add(signature(method));
						try {
							return method.invoke(controller, args);
						}
						catch (final java.lang.reflect.InvocationTargetException e) {
							throw e.getCause();
						}
					});
			final List<String> read = new ArrayList<>();
			script(counted, simObjects, read);

			final Objects renderObjects = simObjects; // the render side resolves the same handles to the same objects
			final Recorder recorder = new Recorder(renderObjects);
			if (play) {
				final RenderEventPlayer player = new RenderEventPlayer(recorder, new RenderEventPlayer.Resolver() {
					@Override
					public CUnit unit(final int handle) {
						return (CUnit) renderObjects.widgets.get(handle);
					}

					@Override
					public CItem item(final int handle) {
						return (CItem) renderObjects.widgets.get(handle);
					}

					@Override
					public CDestructable destructable(final int handle) {
						return (CDestructable) renderObjects.widgets.get(handle);
					}

					@Override
					public CWidget widget(final int handle) {
						return renderObjects.widgets.get(handle);
					}

					@Override
					public CProjectile projectile(final int id) {
						assertEquals(1, id);
						return renderObjects.projectile;
					}
				}, null, null);
				for (final RenderEvent event : this.sink.events) {
					player.play(event);
				}
			}
			this.result = new Result(recorder.log, read);
		}
	}

	private static String signature(final Method method) {
		return method.getName() + Arrays.toString(method.getParameterTypes());
	}

	@Test
	void recordedThenPlayedCallsMatchTheDirectCalls() {
		final Result direct = direct();
		final Buffered buffered = new Buffered(true);
		assertEquals(direct.calls.size(), buffered.result.calls.size());
		for (int i = 0; i < direct.calls.size(); i++) {
			assertEquals(direct.calls.get(i), buffered.result.calls.get(i), "call " + i);
		}
		assertEquals(direct.read, buffered.result.read);
		assertTrue(direct.calls.size() > 60);
	}

	@Test
	void everyInterfaceMethodWasRecorded() {
		final Buffered buffered = new Buffered(false);
		final Set<String> all = new HashSet<>();
		for (final Method method : SimulationRenderController.class.getMethods()) {
			all.add(signature(method));
		}
		assertTrue(all.size() >= 55, "the interface has " + all.size() + " methods");
		assertEquals(new HashSet<>(), difference(all, buffered.methodsCalled), "methods the script never calls");
	}

	private static Set<String> difference(final Set<String> a, final Set<String> b) {
		final Set<String> result = new HashSet<>(a);
		result.removeAll(b);
		return result;
	}

	@Test
	void eventsKeepOrderAndCarryNoSimulationObjects() {
		final Buffered buffered = new Buffered(false);
		int expectedOrder = 0;
		for (final RenderEvent event : buffered.sink.events) {
			assertEquals(TICK, event.tick);
			assertEquals(expectedOrder++, event.order);
			final Object payload = event.o0;
			assertFalse(payload instanceof CWidget || payload instanceof CProjectile || payload instanceof TextTag
					|| payload instanceof SimulationRenderComponent || payload instanceof CUnitAttackSettings
					|| payload instanceof CUnitAttackInstant, "payload of code " + event.code);
		}
		assertTrue(expectedOrder > 60);
	}

	@Test
	void recordedArraysAreCopies() {
		final Buffered buffered = new Buffered(false);
		for (final RenderEvent event : buffered.sink.events) {
			if (event.code == RenderCallCodes.TERRAIN_TYPES) {
				assertEquals(1, ((int[]) event.o0)[0]);
			}
			if (event.code == RenderCallCodes.TERRAIN_DEFORMATION) {
				assertEquals(1f, ((float[]) event.o0)[0]);
			}
			if (event.code == RenderCallCodes.ADJUST_TERRAIN_RECT) {
				final BufferingRenderController.TerrainAdjustment adjustment = (BufferingRenderController.TerrainAdjustment) event.o0;
				assertEquals(1, adjustment.rect[0]);
				assertEquals(0.5f, adjustment.modBuffer[0]);
			}
		}
	}

	@Test
	void proxiesGetDistinctIdsAndWorldObjectsKeepTheirHandles() {
		final Buffered buffered = new Buffered(false);
		final Set<Integer> proxyIds = new HashSet<>();
		int units = 0;
		for (final RenderEvent event : buffered.sink.events) {
			switch (event.code) {
			case RenderCallCodes.LIGHTNING_PLAIN:
			case RenderCallCodes.LIGHTNING_PLAIN_DURATION:
			case RenderCallCodes.LIGHTNING_ABILITY:
			case RenderCallCodes.LIGHTNING_ABILITY_DURATION:
			case RenderCallCodes.UBERSPLAT_STATIC:
			case RenderCallCodes.EFFECT_ON_POINT:
			case RenderCallCodes.PERSISTENT_EFFECT_ON_UNIT:
			case RenderCallCodes.ABILITY_SOUND:
			case RenderCallCodes.TEXT_TAG_CREATE:
				assertTrue(proxyIds.add(event.a), "proxy id " + event.a + " reused");
				break;
			case RenderCallCodes.UNIT_CREATE:
				assertEquals(0x100000 + units++, event.a);
				break;
			default:
				break;
			}
		}
		assertEquals(2, units);
		assertTrue(proxyIds.size() >= 10);
	}
}
