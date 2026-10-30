package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.AnchorDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame;
import com.etheller.warsmash.parsers.fdf.frames.BackdropFrame;
import com.etheller.warsmash.parsers.fdf.frames.CheckBoxFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.mdx.ReplaceableIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerScore;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

/**
 * The Allies dialog (F11) of the original game: one row for each other player in the game, with its Ally, Vision and
 * Units boxes and the gold and lumber to send. Changes take effect only on Accept.
 */
public class MeleeAllianceDialog {
	private static final CAllianceType[] ALLY_TYPES = { CAllianceType.PASSIVE, CAllianceType.HELP_REQUEST,
			CAllianceType.HELP_RESPONSE, CAllianceType.SHARED_XP, CAllianceType.SHARED_SPELLS };
	private static final float FIRST_ROW_LEFT = 0.024f;
	private static final float FIRST_ROW_TOP = -0.0745f;
	private static final float ROW_HEIGHT = 0.024f;
	// miscdata.txt TradingIncSmall and TradingIncLarge.
	private static final int TRADING_INC_SMALL = 100;
	private static final int TRADING_INC_LARGE = 200;

	private final GameUI rootFrame;
	private final Viewport uiViewport;
	private final CSimulation simulation;
	private final CPlayer localPlayer;
	private final SimpleFrame container;
	private final UIFrame dialog;
	private final List<Row> rows = new ArrayList<>();
	private final CheckBoxFrame alliedVictory;

	public MeleeAllianceDialog(final GameUI rootFrame, final Viewport uiViewport, final CSimulation simulation,
			final CPlayer localPlayer) {
		this.rootFrame = rootFrame;
		this.uiViewport = uiViewport;
		this.simulation = simulation;
		this.localPlayer = localPlayer;
		this.container = (SimpleFrame) rootFrame.createSimpleFrame("SmashAllianceDialog", rootFrame, 0);
		this.container.addAnchor(new AnchorDefinition(FramePoint.CENTER, 0, 0));
		this.dialog = rootFrame.createFrame("AllianceDialog", this.container, 0, 0);
		this.dialog.addSetPoint(new SetPoint(FramePoint.CENTER, this.container, FramePoint.CENTER, 0, 0));
		this.container.add(this.dialog);
		this.container.setWidth(this.dialog.getAssignedWidth());
		this.container.setHeight(this.dialog.getAssignedHeight());

		for (int playerIndex = 0; playerIndex < (WarsmashConstants.MAX_PLAYERS - 4); playerIndex++) {
			final CPlayer other = simulation.getPlayer(playerIndex);
			if ((playerIndex == localPlayer.getId()) || (other.getSlotState() != CPlayerSlotState.PLAYING)) {
				continue;
			}
			final Row row = new Row(other, this.rows.size());
			this.rows.add(row);
		}

		final GlueTextButtonFrame acceptButton = (GlueTextButtonFrame) rootFrame
				.getFrameByName("AllianceAcceptButton", 0);
		acceptButton.setOnClick(() -> {
			apply();
			setVisible(false);
		});
		final GlueTextButtonFrame cancelButton = (GlueTextButtonFrame) rootFrame
				.getFrameByName("AllianceCancelButton", 0);
		cancelButton.setOnClick(() -> setVisible(false));
		// Melee victory (Blizzard.j MeleeCheckForVictors) reads this as the player's allied victory state.
		this.alliedVictory = (CheckBoxFrame) rootFrame.getFrameByName("AlliedVictoryCheckBox", 0);
		// Every other player's row fits in the dialog, and the bar has no place set in the FDF.
		rootFrame.getFrameByName("AllianceDialogScrollBar", 0).setVisible(false);
		this.container.setVisible(false);
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
		if (visible) {
			for (final Row row : this.rows) {
				row.refresh();
			}
			this.alliedVictory.setChecked(
					this.localPlayer.getPlayerState(this.simulation, CPlayerState.ALLIED_VICTORY) != 0);
			this.alliedVictory.setEnabled(!this.simulation.isAllianceChangesLocked());
		}
		this.container.setVisible(visible);
		this.container.positionBounds(this.rootFrame, this.uiViewport);
	}

	/**
	 * A click on a row's gold or lumber field: the left button (a tap) adds to the amount to send, the right button (a
	 * long press) takes away, and Shift doubles the step, as in the original game.
	 */
	public boolean tradeClick(final float uiX, final float uiY, final boolean decrease, final boolean largeStep) {
		if (!isVisible()) {
			return false;
		}
		final int step = largeStep ? TRADING_INC_LARGE : TRADING_INC_SMALL;
		for (final Row row : this.rows) {
			if (row.goldField.getRenderBounds().contains(uiX, uiY)) {
				row.gold = changeAmount(row.gold, decrease ? -step : step, this.localPlayer.getGold() - sentGold(row));
				row.updateTradeTexts();
				return true;
			}
			if (row.lumberField.getRenderBounds().contains(uiX, uiY)) {
				row.lumber = changeAmount(row.lumber, decrease ? -step : step,
						this.localPlayer.getLumber() - sentLumber(row));
				row.updateTradeTexts();
				return true;
			}
		}
		return false;
	}

	private static int changeAmount(final int amount, final int change, final int available) {
		return Math.max(0, Math.min(amount + change, Math.max(0, available)));
	}

	private int sentGold(final Row except) {
		int total = 0;
		for (final Row row : this.rows) {
			if (row != except) {
				total += row.gold;
			}
		}
		return total;
	}

	private int sentLumber(final Row except) {
		int total = 0;
		for (final Row row : this.rows) {
			if (row != except) {
				total += row.lumber;
			}
		}
		return total;
	}

	private void apply() {
		for (final Row row : this.rows) {
			row.apply();
		}
		this.localPlayer.setPlayerState(this.simulation, CPlayerState.ALLIED_VICTORY,
				this.alliedVictory.isChecked() ? 1 : 0);
	}

	private final class Row {
		private final CPlayer other;
		private final CheckBoxFrame ally;
		private final CheckBoxFrame vision;
		private final CheckBoxFrame units;
		private final AbstractRenderableFrame goldField;
		private final AbstractRenderableFrame lumberField;
		private final StringFrame goldText;
		private final StringFrame lumberText;
		private int gold;
		private int lumber;

		private Row(final CPlayer other, final int rowIndex) {
			this.other = other;
			final int context = other.getId();
			final UIFrame slot = MeleeAllianceDialog.this.rootFrame.createFrame("AllianceSlot",
					MeleeAllianceDialog.this.container, 0, context);
			slot.addSetPoint(new SetPoint(FramePoint.TOPLEFT, MeleeAllianceDialog.this.dialog, FramePoint.TOPLEFT,
					GameUI.convertX(MeleeAllianceDialog.this.uiViewport, FIRST_ROW_LEFT),
					GameUI.convertY(MeleeAllianceDialog.this.uiViewport, FIRST_ROW_TOP - (rowIndex * ROW_HEIGHT))));
			MeleeAllianceDialog.this.container.add(slot);
			final GameUI rootFrame = MeleeAllianceDialog.this.rootFrame;
			((BackdropFrame) rootFrame.getFrameByName("ColorBackdrop", context))
					.setBackground(rootFrame.loadTexture("ReplaceableTextures\\" + ReplaceableIds.getPathString(1)
							+ ReplaceableIds.getIdString(other.getColor()) + ".blp"));
			rootFrame.setText((StringFrame) rootFrame.getFrameByName("PlayerNameLabel", context), other.getName());
			this.ally = (CheckBoxFrame) rootFrame.getFrameByName("AllyCheckBox", context);
			this.vision = (CheckBoxFrame) rootFrame.getFrameByName("VisionCheckBox", context);
			this.units = (CheckBoxFrame) rootFrame.getFrameByName("UnitsCheckBox", context);
			this.ally.setOnClick(this::updateEnabled);
			this.goldField = (AbstractRenderableFrame) rootFrame.getFrameByName("GoldBackdrop", context);
			this.lumberField = (AbstractRenderableFrame) rootFrame.getFrameByName("LumberBackdrop", context);
			this.goldText = (StringFrame) rootFrame.getFrameByName("GoldText", context);
			this.lumberText = (StringFrame) rootFrame.getFrameByName("LumberText", context);
			updateTradeTexts();
		}

		private void updateTradeTexts() {
			MeleeAllianceDialog.this.rootFrame.setText(this.goldText, Integer.toString(this.gold));
			MeleeAllianceDialog.this.rootFrame.setText(this.lumberText, Integer.toString(this.lumber));
		}

		private void refresh() {
			this.gold = 0;
			this.lumber = 0;
			updateTradeTexts();
			final CPlayer local = MeleeAllianceDialog.this.localPlayer;
			final int otherIndex = this.other.getId();
			this.ally.setChecked(local.hasAlliance(otherIndex, CAllianceType.PASSIVE));
			this.vision.setChecked(local.hasAlliance(otherIndex, CAllianceType.SHARED_VISION));
			this.units.setChecked(local.hasAlliance(otherIndex, CAllianceType.SHARED_CONTROL));
			this.ally.setEnabled(!MeleeAllianceDialog.this.simulation.isAllianceChangesLocked());
			updateEnabled();
		}

		// Vision and units can only be shared with an ally, as in the original game.
		private void updateEnabled() {
			this.vision.setEnabled(this.ally.isChecked());
			this.units.setEnabled(this.ally.isChecked());
		}

		private void apply() {
			final CPlayer local = MeleeAllianceDialog.this.localPlayer;
			final int otherIndex = this.other.getId();
			final boolean allied = this.ally.isChecked();
			for (final CAllianceType type : ALLY_TYPES) {
				local.setAlliance(otherIndex, type, allied);
			}
			local.setAlliance(otherIndex, CAllianceType.SHARED_VISION, allied && this.vision.isChecked());
			final boolean shareUnits = allied && this.units.isChecked();
			local.setAlliance(otherIndex, CAllianceType.SHARED_CONTROL, shareUnits);
			if (MeleeAllianceDialog.this.simulation.isSharedAdvancedControl()) {
				local.setAlliance(otherIndex, CAllianceType.SHARED_ADVANCED_CONTROL, shareUnits);
			}
			final int goldToSend = Math.min(this.gold, local.getGold());
			final int lumberToSend = Math.min(this.lumber, local.getLumber());
			local.setGold(local.getGold() - goldToSend);
			this.other.setGold(this.other.getGold() + goldToSend);
			local.setLumber(local.getLumber() - lumberToSend);
			this.other.setLumber(this.other.getLumber() + lumberToSend);
			local.addScore(CPlayerScore.GOLD_GIVEN, goldToSend);
			local.addScore(CPlayerScore.LUMBER_GIVEN, lumberToSend);
			this.other.addScore(CPlayerScore.GOLD_RECEIVED, goldToSend);
			this.other.addScore(CPlayerScore.LUMBER_RECEIVED, lumberToSend);
		}
	}
}
