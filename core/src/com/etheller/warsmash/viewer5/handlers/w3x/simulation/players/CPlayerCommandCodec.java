package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * One player command as bytes, the form a network game sends and a replay keeps: the encoder records a call on the
 * listener, {@link #decode} makes the same call again.
 */
public final class CPlayerCommandCodec {
	public static final byte TARGET_ORDER = 1;
	public static final byte POINT_ORDER = 2;
	public static final byte GROUP_POINT_ORDER = 3;
	public static final byte DROP_ITEM_AT_POINT_ORDER = 4;
	public static final byte DROP_ITEM_AT_TARGET_ORDER = 5;
	public static final byte IMMEDIATE_ORDER = 6;
	public static final byte CANCEL_TRAINING = 7;
	public static final byte GUI_PLAYER_EVENT = 8;
	public static final byte SELECT_UNITS = 9;
	public static final byte CHAT_MESSAGE = 10;
	public static final byte CLICK_DIALOG_BUTTON = 11;
	public static final byte SET_ALLIANCE = 12;
	public static final byte TRANSFER_RESOURCES = 13;
	public static final byte GAME_EVENT = 14;
	public static final byte SAVE_GAME = 15;
	public static final byte LEAVE_GAME = 16;
	public static final byte CHEAT = 17;
	public static final byte SET_GAME_PAUSED = 18;
	public static final byte MINIMAP_SIGNAL = 19;
	public static final byte TRACKABLE_EVENT = 20;
	public static final byte MOUSE_EVENT = 21;

	/** A network packet holds a command, so the text of one stays well under its size. */
	public static final int MAX_STRING_BYTES = 512;

	private CPlayerCommandCodec() {
	}

	public static final class Encoder implements CPlayerUnitOrderListener {
		private ByteBuffer buffer = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN);

		/** The bytes of the commands encoded since the last call. */
		public byte[] take() {
			final byte[] bytes = Arrays.copyOf(this.buffer.array(), this.buffer.position());
			this.buffer.clear();
			return bytes;
		}

		private ByteBuffer room(final int bytes) {
			if (this.buffer.remaining() < bytes) {
				final ByteBuffer bigger = ByteBuffer
						.allocate(Math.max(this.buffer.capacity() * 2, this.buffer.position() + bytes))
						.order(ByteOrder.BIG_ENDIAN);
				this.buffer.flip();
				bigger.put(this.buffer);
				this.buffer = bigger;
			}
			return this.buffer;
		}

		private void type(final byte type) {
			room(1).put(type);
		}

		private void putInt(final int value) {
			room(4).putInt(value);
		}

		private void putFloat(final float value) {
			room(4).putFloat(value);
		}

		private void putBoolean(final boolean value) {
			room(1).put(value ? (byte) 1 : (byte) 0);
		}

		private void putInts(final int[] values) {
			putShortCount(values.length);
			room(values.length * 4);
			for (final int value : values) {
				this.buffer.putInt(value);
			}
		}

		private void putFloats(final float[] values) {
			putShortCount(values.length);
			room(values.length * 4);
			for (final float value : values) {
				this.buffer.putFloat(value);
			}
		}

		private void putShortCount(final int count) {
			if ((count < 0) || (count > 0xFFFF)) {
				throw new IllegalArgumentException("Too many values for a player command: " + count);
			}
			room(2).putShort((short) count);
		}

		private void putString(final String value) {
			byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
			if (bytes.length > MAX_STRING_BYTES) {
				bytes = Arrays.copyOf(bytes, MAX_STRING_BYTES);
			}
			putShortCount(bytes.length);
			room(bytes.length).put(bytes);
		}

		@Override
		public void issueTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
				final int targetHandleId, final boolean queue) {
			type(TARGET_ORDER);
			putInt(unitHandleId);
			putInt(abilityHandleId);
			putInt(orderId);
			putInt(targetHandleId);
			putBoolean(queue);
		}

		@Override
		public void issuePointOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
				final float x, final float y, final boolean queue) {
			type(POINT_ORDER);
			putInt(unitHandleId);
			putInt(abilityHandleId);
			putInt(orderId);
			putFloat(x);
			putFloat(y);
			putBoolean(queue);
		}

		@Override
		public void issueGroupPointOrder(final int[] unitHandleIds, final int[] abilityHandleIds, final int orderId,
				final float[] xs, final float[] ys, final boolean queue) {
			type(GROUP_POINT_ORDER);
			putInts(unitHandleIds);
			putInts(abilityHandleIds);
			putInt(orderId);
			putFloats(xs);
			putFloats(ys);
			putBoolean(queue);
		}

		@Override
		public void issueDropItemAtPointOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
				final int targetHandleId, final float x, final float y, final boolean queue) {
			type(DROP_ITEM_AT_POINT_ORDER);
			putInt(unitHandleId);
			putInt(abilityHandleId);
			putInt(orderId);
			putInt(targetHandleId);
			putFloat(x);
			putFloat(y);
			putBoolean(queue);
		}

		@Override
		public void issueDropItemAtTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
				final int targetItemHandleId, final int targetHeroHandleId, final boolean queue) {
			type(DROP_ITEM_AT_TARGET_ORDER);
			putInt(unitHandleId);
			putInt(abilityHandleId);
			putInt(orderId);
			putInt(targetItemHandleId);
			putInt(targetHeroHandleId);
			putBoolean(queue);
		}

		@Override
		public void issueImmediateOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
				final boolean queue) {
			type(IMMEDIATE_ORDER);
			putInt(unitHandleId);
			putInt(abilityHandleId);
			putInt(orderId);
			putBoolean(queue);
		}

		@Override
		public void unitCancelTrainingItem(final int unitHandleId, final int cancelIndex) {
			type(CANCEL_TRAINING);
			putInt(unitHandleId);
			putInt(cancelIndex);
		}

		@Override
		public void issueGuiPlayerEvent(final int eventId) {
			type(GUI_PLAYER_EVENT);
			putInt(eventId);
		}

		@Override
		public void selectUnits(final int[] deselectedUnitHandleIds, final int[] selectedUnitHandleIds) {
			type(SELECT_UNITS);
			putInts(deselectedUnitHandleIds);
			putInts(selectedUnitHandleIds);
		}

		@Override
		public void chatMessage(final int recipient, final int recipientPlayerIndex, final String message) {
			type(CHAT_MESSAGE);
			putInt(recipient);
			putInt(recipientPlayerIndex);
			putString(message);
		}

		@Override
		public void clickDialogButton(final int dialogHandleId, final int buttonHandleId) {
			type(CLICK_DIALOG_BUTTON);
			putInt(dialogHandleId);
			putInt(buttonHandleId);
		}

		@Override
		public void setAlliance(final int otherPlayerIndex, final int allianceType, final boolean value) {
			type(SET_ALLIANCE);
			putInt(otherPlayerIndex);
			putInt(allianceType);
			putBoolean(value);
		}

		@Override
		public void transferResources(final int otherPlayerIndex, final int gold, final int lumber) {
			type(TRANSFER_RESOURCES);
			putInt(otherPlayerIndex);
			putInt(gold);
			putInt(lumber);
		}

		@Override
		public void fireGameEvent(final int eventId) {
			type(GAME_EVENT);
			putInt(eventId);
		}

		@Override
		public void saveGame(final String saveName) {
			type(SAVE_GAME);
			putString(saveName);
		}

		@Override
		public void leaveGame() {
			type(LEAVE_GAME);
		}

		@Override
		public void cheat(final String cheat) {
			type(CHEAT);
			putString(cheat);
		}

		@Override
		public void setGamePaused(final boolean paused) {
			type(SET_GAME_PAUSED);
			putBoolean(paused);
		}

		@Override
		public void minimapSignal(final float x, final float y) {
			type(MINIMAP_SIGNAL);
			putFloat(x);
			putFloat(y);
		}

		@Override
		public void trackableEvent(final int trackableHandleId, final boolean hit) {
			type(TRACKABLE_EVENT);
			putInt(trackableHandleId);
			putBoolean(hit);
		}

		@Override
		public void mouseEvent(final int eventId, final float x, final float y, final int button) {
			type(MOUSE_EVENT);
			putInt(eventId);
			putFloat(x);
			putFloat(y);
			putInt(button);
		}
	}

	/**
	 * Makes the calls these bytes hold on the listener, in order.
	 *
	 * @throws IllegalArgumentException when the bytes are not whole commands; nothing after the bad one is applied
	 */
	public static void decode(final byte[] bytes, final CPlayerUnitOrderListener listener) {
		final ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
		try {
			while (buffer.hasRemaining()) {
				decodeOne(buffer, listener);
			}
		}
		catch (final BufferUnderflowException exc) {
			throw new IllegalArgumentException("Truncated player command", exc);
		}
	}

	private static void decodeOne(final ByteBuffer buffer, final CPlayerUnitOrderListener listener) {
		final byte type = buffer.get();
		switch (type) {
		case TARGET_ORDER: {
			final int unit = buffer.getInt();
			final int ability = buffer.getInt();
			final int order = buffer.getInt();
			final int target = buffer.getInt();
			listener.issueTargetOrder(unit, ability, order, target, getBoolean(buffer));
			break;
		}
		case POINT_ORDER: {
			final int unit = buffer.getInt();
			final int ability = buffer.getInt();
			final int order = buffer.getInt();
			final float x = buffer.getFloat();
			final float y = buffer.getFloat();
			listener.issuePointOrder(unit, ability, order, x, y, getBoolean(buffer));
			break;
		}
		case GROUP_POINT_ORDER: {
			final int[] units = getInts(buffer);
			final int[] abilities = getInts(buffer);
			final int order = buffer.getInt();
			final float[] xs = getFloats(buffer);
			final float[] ys = getFloats(buffer);
			final boolean queue = getBoolean(buffer);
			if ((abilities.length != units.length) || (xs.length != units.length) || (ys.length != units.length)) {
				throw new IllegalArgumentException("Group order with uneven lists");
			}
			listener.issueGroupPointOrder(units, abilities, order, xs, ys, queue);
			break;
		}
		case DROP_ITEM_AT_POINT_ORDER: {
			final int unit = buffer.getInt();
			final int ability = buffer.getInt();
			final int order = buffer.getInt();
			final int target = buffer.getInt();
			final float x = buffer.getFloat();
			final float y = buffer.getFloat();
			listener.issueDropItemAtPointOrder(unit, ability, order, target, x, y, getBoolean(buffer));
			break;
		}
		case DROP_ITEM_AT_TARGET_ORDER: {
			final int unit = buffer.getInt();
			final int ability = buffer.getInt();
			final int order = buffer.getInt();
			final int item = buffer.getInt();
			final int hero = buffer.getInt();
			listener.issueDropItemAtTargetOrder(unit, ability, order, item, hero, getBoolean(buffer));
			break;
		}
		case IMMEDIATE_ORDER: {
			final int unit = buffer.getInt();
			final int ability = buffer.getInt();
			final int order = buffer.getInt();
			listener.issueImmediateOrder(unit, ability, order, getBoolean(buffer));
			break;
		}
		case CANCEL_TRAINING: {
			final int unit = buffer.getInt();
			listener.unitCancelTrainingItem(unit, buffer.getInt());
			break;
		}
		case GUI_PLAYER_EVENT:
			listener.issueGuiPlayerEvent(buffer.getInt());
			break;
		case SELECT_UNITS: {
			final int[] deselected = getInts(buffer);
			listener.selectUnits(deselected, getInts(buffer));
			break;
		}
		case CHAT_MESSAGE: {
			final int recipient = buffer.getInt();
			final int recipientPlayer = buffer.getInt();
			listener.chatMessage(recipient, recipientPlayer, getString(buffer));
			break;
		}
		case CLICK_DIALOG_BUTTON: {
			final int dialog = buffer.getInt();
			listener.clickDialogButton(dialog, buffer.getInt());
			break;
		}
		case SET_ALLIANCE: {
			final int other = buffer.getInt();
			final int allianceType = buffer.getInt();
			listener.setAlliance(other, allianceType, getBoolean(buffer));
			break;
		}
		case TRANSFER_RESOURCES: {
			final int other = buffer.getInt();
			final int gold = buffer.getInt();
			listener.transferResources(other, gold, buffer.getInt());
			break;
		}
		case GAME_EVENT:
			listener.fireGameEvent(buffer.getInt());
			break;
		case SAVE_GAME:
			listener.saveGame(getString(buffer));
			break;
		case LEAVE_GAME:
			listener.leaveGame();
			break;
		case CHEAT:
			listener.cheat(getString(buffer));
			break;
		case SET_GAME_PAUSED:
			listener.setGamePaused(getBoolean(buffer));
			break;
		case MINIMAP_SIGNAL: {
			final float x = buffer.getFloat();
			listener.minimapSignal(x, buffer.getFloat());
			break;
		}
		case TRACKABLE_EVENT: {
			final int trackable = buffer.getInt();
			listener.trackableEvent(trackable, getBoolean(buffer));
			break;
		}
		case MOUSE_EVENT: {
			final int eventId = buffer.getInt();
			final float x = buffer.getFloat();
			final float y = buffer.getFloat();
			listener.mouseEvent(eventId, x, y, buffer.getInt());
			break;
		}
		default:
			throw new IllegalArgumentException("Unknown player command " + type);
		}
	}

	private static boolean getBoolean(final ByteBuffer buffer) {
		return buffer.get() != 0;
	}

	private static int[] getInts(final ByteBuffer buffer) {
		final int[] values = new int[buffer.getShort() & 0xFFFF];
		for (int i = 0; i < values.length; i++) {
			values[i] = buffer.getInt();
		}
		return values;
	}

	private static float[] getFloats(final ByteBuffer buffer) {
		final float[] values = new float[buffer.getShort() & 0xFFFF];
		for (int i = 0; i < values.length; i++) {
			values[i] = buffer.getFloat();
		}
		return values;
	}

	private static String getString(final ByteBuffer buffer) {
		final int length = buffer.getShort() & 0xFFFF;
		if (length > MAX_STRING_BYTES) {
			throw new IllegalArgumentException("Player command text too long: " + length);
		}
		final byte[] bytes = new byte[length];
		buffer.get(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}
}
