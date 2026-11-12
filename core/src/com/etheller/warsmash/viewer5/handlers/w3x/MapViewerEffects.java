package com.etheller.warsmash.viewer5.handlers.w3x;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.handlers.mdx.Attachment;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxHandler;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxNode;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.lightning.LightningEffectModel;
import com.etheller.warsmash.viewer5.handlers.w3x.lightning.LightningEffectNode;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderAttackInstant;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderImage;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderLightningEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderSpellEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWeatherEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.WeatherType;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.BuffUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.EffectAttachmentUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CImage;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightning;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightningMovable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentUbersplat;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentWeather;

/**
 * Spell effects, special effects, lightning, weather and ubersplats of a map viewer.
 */
final class MapViewerEffects {
	private MapViewerEffects() {
	}

	static SimulationRenderComponentWeather createWeatherEffect(final War3MapViewer viewer, final War3ID weatherId, final float minX,
			final float minY, final float maxX, final float maxY) {
		final WeatherType type = viewer.weatherTypes == null ? null : viewer.weatherTypes.get(weatherId);
		if (type == null) {
			return SimulationRenderComponentWeather.DO_NOTHING;
		}
		final MdxModel model = new MdxModel((MdxHandler) viewer.findHandler(".mdx")[0], viewer, ".mdx", viewer.mapPathSolver,
				"<weather:" + type.getKey() + ">");
		model.loadData(new ByteArrayInputStream(type.buildModel().saveMdx().array()), null);
		if (!model.ok) {
			return SimulationRenderComponentWeather.DO_NOTHING;
		}
		final RenderWeatherEffect weather = new RenderWeatherEffect(viewer, weatherId, type, model, minX, minY, maxX,
				maxY);
		viewer.weatherEffects.removeIf(RenderWeatherEffect::isRemoved);
		viewer.weatherEffects.add(weather);
		return weather;
	}

	static com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUbersplat createUbersplat(final War3MapViewer viewer, 
			final SimulationRenderComponentUbersplat.State state) {
		final com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUbersplat splat = new com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUbersplat(
				viewer, state);
		viewer.scriptUbersplats.add(splat);
		return splat;
	}

	/** Draws a script image as it is now, or stops drawing it once destroyed. */
	static void updateImage(final War3MapViewer viewer, final CImage image) {
		RenderImage renderImage = viewer.scriptImages.get(image);
		if (renderImage == null) {
			if (image.isDestroyed() || !image.isValid()) {
				return;
			}
			renderImage = new RenderImage(viewer, image);
			viewer.scriptImages.put(image, renderImage);
		}
		if (!renderImage.update()) {
			viewer.scriptImages.remove(image);
		}
	}

	static SimulationRenderComponentLightning createLightning(final War3MapViewer viewer, final War3ID lightningId,
			final RenderUnit renderPeerSource, final RenderWidget renderPeerTarget) {
		return createLightning(viewer, lightningId, renderPeerSource, renderPeerTarget, null);
	}

	static SimulationRenderComponentLightning createLightning(final War3MapViewer viewer, final War3ID lightningId,
			final RenderUnit renderPeerSource, final RenderWidget renderPeerTarget, final Float duration) {
		final LightningEffectModel lightningEffectModel = viewer.lightningTypeToModel.get(lightningId);
		if ((lightningEffectModel != null) && (renderPeerSource != null) && (renderPeerTarget != null)) {
			final LightningEffectNode source = (LightningEffectNode) lightningEffectModel.addInstance();
			final LightningEffectNode target = (LightningEffectNode) lightningEffectModel.addInstance();
			// ----- NOTE -----
			// Automatic ingame lightnings apply their builtin durations below. For user
			// code, we DONT do the same
			// on the other arbitrary (non-unit-bound) createLightning func.
			// Later on we might want a user API for creating unit-attached lightnings that
			// have a duration specified
			// by user code
			if (duration != null) {
				if (duration >= 0) {
					source.setLifeSpanRemaining(duration);
					target.setLifeSpanRemaining(duration);
				}
			} else {
				source.setLifeSpanRemaining(lightningEffectModel.getDuration());
				target.setLifeSpanRemaining(lightningEffectModel.getDuration());
			}
			source.setFriend(target);
			target.setFriend(source);
			source.setSource(true);
			source.setParent(renderPeerSource.getInstance());
			source.setLocation(0, 0, viewer.simulation.getUnitData()
					.getProjectileLaunchZ(renderPeerSource.getSimulationUnit().getTypeId()));
			target.setParent(renderPeerTarget.getInstance());
			target.setLocation(0, 0, renderPeerTarget.getSimulationWidget().getImpactZ());
			source.setScene(viewer.worldScene);
			target.setScene(viewer.worldScene);
			return viewer.addLightning(new RenderLightningEffect(source, target, viewer, lightningId,
					renderPeerSource.getSimulationUnit(), renderPeerTarget.getSimulationWidget(), 0, 0, 0, 0, 0, 0));
		}
		return SimulationRenderComponentLightning.DO_NOTHING;
	}

	static SimulationRenderComponentLightningMovable createLightning(final War3MapViewer viewer, final War3ID lightningId, final float x1,
			final float y1, final float z1, final float x2, final float y2, final float z2) {
		return createLightning(viewer, lightningId, x1, y1, z1, x2, y2, z2, false);
	}

	/**
	 * With checkVisibility the lightning is not drawn while both its ends are in the black mask; fog hides nothing.
	 */
	static SimulationRenderComponentLightningMovable createLightning(final War3MapViewer viewer, final War3ID lightningId, final float x1,
			final float y1, final float z1, final float x2, final float y2, final float z2,
			final boolean checkVisibility) {
		final LightningEffectModel lightningEffectModel = viewer.lightningTypeToModel.get(lightningId);
		if (lightningEffectModel != null) {
			final LightningEffectNode source = (LightningEffectNode) lightningEffectModel.addInstance();
			final LightningEffectNode target = (LightningEffectNode) lightningEffectModel.addInstance();
			source.setFriend(target);
			target.setFriend(source);
			source.setSource(true);
			source.setLocation(x1, y1, z1);
			target.setLocation(x2, y2, z2);
			source.hiddenInMask = checkVisibility;
			target.hiddenInMask = checkVisibility;
			source.setScene(viewer.worldScene);
			target.setScene(viewer.worldScene);
			final RenderLightningEffect lightning = new RenderLightningEffect(source, target, viewer, lightningId, null,
					null, x1, y1, z1, x2, y2, z2);
			lightning.setCheckVisibility(checkVisibility);
			return viewer.addLightning(lightning);
		}
		return SimulationRenderComponentLightningMovable.DO_NOTHING;
	}

	static void spawnFxOnOrigin(final War3MapViewer viewer, final RenderUnit renderUnit, final String heroLevelUpArt) {
		final MdxModel heroLevelUpModel = loadModelMdx(viewer, heroLevelUpArt);
		if (heroLevelUpModel != null) {
			final MdxComplexInstance modelInstance = (MdxComplexInstance) heroLevelUpModel.addInstance();
			modelInstance.setTeamColor(renderUnit.playerIndex);

			final MdxModel model = (MdxModel) renderUnit.instance.model;
			int index = -1;
			for (int i = 0; i < model.attachments.size(); i++) {
				final Attachment attachment = model.attachments.get(i);
				if (attachment.getName().startsWith("origin ref")) {
					index = i;
					break;
				}
			}
			if ((index != -1) && false) {
				final MdxNode attachment = renderUnit.instance.getAttachment(index);
				modelInstance.setParent(attachment);
			} else {
				modelInstance.setLocation(renderUnit.location);
			}

			modelInstance.setScene(viewer.worldScene);
			SequenceUtils.randomBirthSequence(modelInstance);
			viewer.projectiles.add(new RenderAttackInstant(modelInstance, viewer,
					(float) Math.toRadians(renderUnit.getSimulationUnit().getFacing())));
		}
	}

	static RenderSpellEffect spawnSpellEffectOnUnitEx(final War3MapViewer viewer, final CWidget unit, final War3ID alias,
			final CEffectType effectType, final int index) {
		final EffectAttachmentUI effectAttachmentUI = getEffectAttachmentUI(viewer, alias, effectType, index);
		if (effectAttachmentUI == null) {
			return null;
		}
		final String modelPath = effectAttachmentUI.getModelPath();
		final List<String> attachmentPoint = effectAttachmentUI.getAttachmentPoint();
		final RenderSpellEffect specialEffect = addSpecialEffectTarget(viewer, modelPath, unit, attachmentPoint);
		return specialEffect;
	}

	static RenderSpellEffect spawnSpellEffectOnUnitEx(final War3MapViewer viewer, final CWidget unit, final War3ID alias,
			final CEffectType effectType, final int index, final String attachPointName) {
		final EffectAttachmentUI effectAttachmentUI = getEffectAttachmentUI(viewer, alias, effectType, index);
		if (effectAttachmentUI == null) {
			return null;
		}
		final String modelPath = effectAttachmentUI.getModelPath();
		final RenderSpellEffect specialEffect = addSpecialEffectTarget(viewer, modelPath, unit,
				Arrays.asList(attachPointName.split(",")));
		return specialEffect;
	}

	static List<RenderSpellEffect> spawnSpellEffectOnUnitEx(final War3MapViewer viewer, final CUnit unit, final War3ID alias,
			final CEffectType effectType) {
		final List<EffectAttachmentUI> effectAttachmentUI = getEffectAttachmentUIList(viewer, alias, effectType);
		if (effectAttachmentUI == null) {
			return null;
		}
		final List<RenderSpellEffect> renderEffects = new ArrayList<>();
		for (final EffectAttachmentUI effect : effectAttachmentUI) {
			final String modelPath = effect.getModelPath();
			final List<String> attachmentPoint = effect.getAttachmentPoint();
			final RenderSpellEffect specialEffect = addSpecialEffectTarget(viewer, modelPath, unit, attachmentPoint);
			if (specialEffect != null) {
				renderEffects.add(specialEffect);
			}
		}
		return renderEffects;
	}

	static RenderSpellEffect spawnSpellEffectEx(final War3MapViewer viewer, final float x, final float y, final float facing, final War3ID alias,
			final CEffectType effectType, final int index) {
		final EffectAttachmentUI effectAttachmentUI = getEffectAttachmentUI(viewer, alias, effectType, index);
		if (effectAttachmentUI == null) {
			return null;
		}
		final String modelPath = effectAttachmentUI.getModelPath();
		final List<String> attachmentPoint = effectAttachmentUI.getAttachmentPoint();
		final RenderSpellEffect specialEffect = addSpecialEffect(viewer, modelPath, x, y, (float) StrictMath.toRadians(facing));
		return specialEffect;
	}

	static List<EffectAttachmentUI> getEffectAttachmentUIList(final War3MapViewer viewer, final War3ID alias, final CEffectType effectType) {
		final AbilityUI abilityUI = viewer.abilityDataUI.getUI(alias);
		List<EffectAttachmentUI> effectAttachmentUI = null;
		if (abilityUI != null) {
			switch (effectType) {
			case EFFECT:
				effectAttachmentUI = abilityUI.getEffectArt();
				break;
			case TARGET:
				effectAttachmentUI = abilityUI.getTargetArt();
				break;
			case CASTER:
				effectAttachmentUI = abilityUI.getCasterArt();
				break;
			case SPECIAL:
				effectAttachmentUI = abilityUI.getSpecialArt();
				break;
			case AREA_EFFECT:
				effectAttachmentUI = abilityUI.getAreaEffectArt();
				break;
			case MISSILE:
				effectAttachmentUI = new ArrayList<>(abilityUI.getMissileArt());
				break;
			default:
				throw new IllegalArgumentException("Unsupported effect type: " + effectType);
			}
		} else {
			final BuffUI buffUI = viewer.abilityDataUI.getBuffUI(alias);
			if (buffUI != null) {
				switch (effectType) {
				case EFFECT:
					effectAttachmentUI = buffUI.getEffectArt();
					break;
				case TARGET:
					effectAttachmentUI = buffUI.getTargetArt();
					break;
				case SPECIAL:
					effectAttachmentUI = buffUI.getSpecialArt();
					break;
				case MISSILE:
					effectAttachmentUI = new ArrayList<>(buffUI.getMissileArt());
					break;
				default:
					throw new IllegalArgumentException("Unsupported effect type: " + effectType);
				}
			} else {
				return null;
			}
		}
		return effectAttachmentUI;
	}

	static EffectAttachmentUI getEffectAttachmentUI(final War3MapViewer viewer, final War3ID alias, final CEffectType effectType, final int index) {
		final AbilityUI abilityUI = viewer.abilityDataUI.getUI(alias);
		EffectAttachmentUI effectAttachmentUI = null;
		if (abilityUI != null) {
			switch (effectType) {
			case EFFECT:
				effectAttachmentUI = abilityUI.getEffectArt(index);
				break;
			case TARGET:
				effectAttachmentUI = abilityUI.getTargetArt(index);
				break;
			case CASTER:
				effectAttachmentUI = abilityUI.getCasterArt(index);
				break;
			case SPECIAL:
				effectAttachmentUI = abilityUI.getSpecialArt(index);
				break;
			case AREA_EFFECT:
				effectAttachmentUI = abilityUI.getAreaEffectArt(index);
				break;
			case MISSILE:
				effectAttachmentUI = abilityUI.getMissileArt(index);
				break;
			default:
				throw new IllegalArgumentException("Unsupported effect type: " + effectType);
			}
		} else {
			final BuffUI buffUI = viewer.abilityDataUI.getBuffUI(alias);
			if (buffUI != null) {
				switch (effectType) {
				case EFFECT:
					effectAttachmentUI = buffUI.getEffectArt(index);
					break;
				case TARGET:
					effectAttachmentUI = buffUI.getTargetArt(index);
					break;
				case SPECIAL:
					effectAttachmentUI = buffUI.getSpecialArt(index);
					break;
				case MISSILE:
					effectAttachmentUI = buffUI.getMissileArt(index);
					break;
				default:
					break;
				}
			} else {
				return null;
			}
		}
		return effectAttachmentUI;
	}

	static RenderSpellEffect addSpecialEffectTarget(final War3MapViewer viewer, final String modelName, final CWidget targetWidget,
			final String attachPointName) {
		return addSpecialEffectTarget(viewer, modelName, targetWidget, Arrays.asList(attachPointName.split("\\s+")));
	}

	static RenderSpellEffect addSpecialEffectTarget(final War3MapViewer viewer, final String modelName, final CWidget targetWidget,
			List<String> attachPointNames) {
		if (targetWidget instanceof CUnit) {
			if (attachPointNames.isEmpty() || ((attachPointNames.size() == 1) && attachPointNames.get(0).isEmpty())) {
				attachPointNames = War3MapViewer.ORIGIN_STRING_LIST;
			}
			final RenderUnit renderUnit = viewer.unitToRenderPeer.get(targetWidget);
			if (renderUnit == null) {
				final NullPointerException nullPointerException = new NullPointerException(
						"renderUnit is null! targetWidget is \"" + ((CUnit) targetWidget).getUnitType().getName()
								+ "\", attachPointName=\"" + attachPointNames + "\"");
				if (WarsmashConstants.ENABLE_DEBUG) {
					throw nullPointerException;
				} else {
					Log.error(nullPointerException);
				}
			}
			final MdxModel spawnedEffectModel = loadModelMdx(viewer, modelName);
			if (spawnedEffectModel != null) {
				final MdxComplexInstance modelInstance = (MdxComplexInstance) spawnedEffectModel.addInstance();
				float yaw = 0;
				if (renderUnit != null) {
					modelInstance.setTeamColor(renderUnit.playerIndex);
					{
						final MdxModel model = (MdxModel) renderUnit.instance.model;
						int index = -1;
						int bestFitAttachmentNameLength = Integer.MAX_VALUE;
						for (int i = 0; i < model.attachments.size(); i++) {
							final Attachment attachment = model.attachments.get(i);
							boolean match = true;
							for (final String attachmentPointNameToken : attachPointNames) {
								if (!attachment.getName().contains(attachmentPointNameToken)) {
									match = false;
								}
							}
							final int attachmentNameLength = attachment.getName().length();
							if (match && (attachmentNameLength < bestFitAttachmentNameLength)) {
								index = i;
								bestFitAttachmentNameLength = attachmentNameLength;
							}
						}
						if (index != -1) {
							modelInstance.detach();
							final MdxNode attachment = renderUnit.instance.getAttachment(index);
							modelInstance.setParent(attachment);
							modelInstance.setLocation(0, 0, 0);
						} else {
							// TODO This is not consistent with War3, is it? Should look nice though.
							modelInstance.setLocation(renderUnit.location);
							yaw = (float) Math.toRadians(renderUnit.getSimulationUnit().getFacing());
						}
					}
				} else {
					modelInstance.setLocation(0, 0, 0);
				}
				modelInstance.setScene(viewer.worldScene);
				final EnumSet<SecondaryTag> requiredAnimationNamesForAttachments = renderUnit == null
						? SequenceUtils.EMPTY
						: renderUnit.getTypeData().getRequiredAnimationNamesForAttachments();
				final RenderSpellEffect renderAttackInstant = new RenderSpellEffect(modelInstance, viewer,
						yaw, RenderSpellEffect.DEFAULT_ANIMATION_QUEUE, requiredAnimationNamesForAttachments);
				renderAttackInstant.setOrigin(modelName, targetWidget, attachPointNames, 0, 0, 0);
				viewer.projectiles.add(renderAttackInstant);
				return renderAttackInstant;
			}
		} else if (targetWidget instanceof CItem) {
			// TODO this is stupid api, who would do this?
			throw new UnsupportedOperationException("API for addSpecialEffectTarget() on item is NYI");
		} else if (targetWidget instanceof CDestructable) {
			if (attachPointNames.isEmpty() || ((attachPointNames.size() == 1) && attachPointNames.get(0).isEmpty())) {
				attachPointNames = War3MapViewer.ORIGIN_STRING_LIST;
			}
			final RenderDestructable renderDest = viewer.destructableToRenderPeer.get(targetWidget);

			if (renderDest == null) {
				final NullPointerException nullPointerException = new NullPointerException(
						"renderDest is null! targetWidget is \""
								+ ((CDestructable) targetWidget).getDestType().getName() + "\", attachPointName=\""
								+ attachPointNames + "\"");
				if (WarsmashConstants.ENABLE_DEBUG) {
					throw nullPointerException;
				} else {
					Log.error(nullPointerException);
				}
			}
			final MdxModel spawnedEffectModel = loadModelMdx(viewer, modelName);
			if (spawnedEffectModel != null) {
				final MdxComplexInstance modelInstance = (MdxComplexInstance) spawnedEffectModel.addInstance();
				if (renderDest != null) {
					{
						final MdxModel model = (MdxModel) renderDest.instance.model;
						int index = -1;
						int bestFitAttachmentNameLength = Integer.MAX_VALUE;
						for (int i = 0; i < model.attachments.size(); i++) {
							final Attachment attachment = model.attachments.get(i);
							boolean match = true;
							for (final String attachmentPointNameToken : attachPointNames) {
								if (!attachment.getName().contains(attachmentPointNameToken)) {
									match = false;
								}
							}
							final int attachmentNameLength = attachment.getName().length();
							if (match && (attachmentNameLength < bestFitAttachmentNameLength)) {
								index = i;
								bestFitAttachmentNameLength = attachmentNameLength;
							}
						}
						if (index != -1) {
							modelInstance.detach();
							final MdxNode attachment = renderDest.getInstance().getAttachment(index);
							modelInstance.setParent(attachment);
							modelInstance.setLocation(0, 0, 0);
						} else {
							// TODO This is not consistent with War3, is it? Should look nice though.
							modelInstance.setLocation(renderDest.getX(), renderDest.getY(), 0);
						}
					}
				} else {
					modelInstance.setLocation(0, 0, 0);
				}
				modelInstance.setScene(viewer.worldScene);
				final RenderSpellEffect renderAttackInstant = new RenderSpellEffect(modelInstance, viewer,
						0, RenderSpellEffect.DEFAULT_ANIMATION_QUEUE, SequenceUtils.EMPTY);
				renderAttackInstant.setOrigin(modelName, targetWidget, attachPointNames, 0, 0, 0);
				viewer.projectiles.add(renderAttackInstant);
				return renderAttackInstant;
			}
		}
		return null;
	}

	static RenderSpellEffect addSpecialEffect(final War3MapViewer viewer, final String modelName, final float x, final float y, final float yaw) {
		final MdxModel spawnedEffectModel = loadModelMdx(viewer, modelName);
		if (spawnedEffectModel != null) {
			final MdxComplexInstance modelInstance = (MdxComplexInstance) spawnedEffectModel.addInstance();
			{
				modelInstance.setLocation(x, y,
						Math.max(viewer.getWalkableRenderHeight(x, y), viewer.terrain.getGroundHeight(x, y)));
			}
			modelInstance.setScene(viewer.worldScene);
			final RenderSpellEffect renderAttackInstant = new RenderSpellEffect(modelInstance, viewer, yaw,
					RenderSpellEffect.DEFAULT_ANIMATION_QUEUE, SequenceUtils.EMPTY);
			renderAttackInstant.setOrigin(modelName, null, null, x, y, yaw);
			viewer.projectiles.add(renderAttackInstant);
			return renderAttackInstant;
		}
		return null;
	}

	static MdxModel loadModelMdx(final War3MapViewer viewer, final String path) {
		return loadModelMdx(viewer.dataSource, viewer, path, viewer.mapPathSolver, viewer.solverParams);
	}

	static MdxModel loadModelMdx(final DataSource dataSource, final ModelViewer modelViewer, final String path,
			final PathSolver pathSolver, final Object solverParams) {
		// "_" is the data's "no model" entry (e.g. empty art fields in the 1.29 SLKs).
		if ("".equals(path) || "_".equals(path) || "_.mdl".equalsIgnoreCase(path)
				|| "_.mdx".equalsIgnoreCase(path)) {
			return null;
		}
		final String mdxPath = War3MapViewer.mdx(path);
		if (dataSource.has(mdxPath)) {
			return (MdxModel) modelViewer.load(mdxPath, pathSolver, solverParams);
		} else {
			final String mdlPath = War3MapViewer.mdl(mdxPath);
			if (dataSource.has(mdlPath)) {
				return (MdxModel) modelViewer.load(mdlPath, pathSolver, solverParams);
			}
		}
		return (MdxModel) modelViewer.load(mdxPath, pathSolver, solverParams);
	}
}
