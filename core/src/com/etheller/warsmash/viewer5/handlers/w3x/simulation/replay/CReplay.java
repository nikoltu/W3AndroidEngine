package com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

/**
 * A recorded game: how it started, every player command with the step it was applied after, the game's checksum at
 * regular steps, what it read from the disk and the local values its scripts read.
 * <p>
 * The file, big-endian as {@link java.io.DataOutputStream} writes it:
 *
 * <pre>
 * int  magic "WSRP", int format version
 * header (uncompressed, so a replay list reads it without the body): see {@link CReplayHeader}
 * body: a deflate stream of records, each a type byte and then
 *   COMMAND  varint step, byte player, varint length, the command's bytes (CPlayerCommandCodec)
 *   CHECKSUM varint step, int checksum of the game after that step
 *   CACHE    varint step, varint length + 1 (0: no file), the bytes the game caches were read from
 *   LOCAL    varint step, float: a value only this player's program has (the camera) as a script read it
 *   END      varint step the game ended at
 * </pre>
 *
 * A step number is the count of steps the game had made. The body is flushed at every checksum, so the replay of a
 * game that was cut off (a crash, a killed process) still plays up to the last checksum it wrote.
 */
public final class CReplay {
	public static final int MAGIC = ('W' << 24) | ('S' << 16) | ('R' << 8) | 'P';
	/** A replay of another format version is refused rather than played wrong. */
	public static final int FORMAT_VERSION = 4;

	static final int END = 0;
	static final int COMMAND = 1;
	static final int CHECKSUM = 2;
	static final int CACHE = 3;
	static final int LOCAL = 4;

	private final CReplayHeader header;
	private final List<Command> commands;
	private final List<Checksum> checksums;
	private final List<CacheRead> cacheReads;
	private final List<LocalValue> localValues;
	private final int endTick;

	CReplay(final CReplayHeader header, final List<Command> commands, final List<Checksum> checksums,
			final List<CacheRead> cacheReads, final List<LocalValue> localValues, final int endTick) {
		this.header = header;
		this.commands = Collections.unmodifiableList(commands);
		this.checksums = Collections.unmodifiableList(checksums);
		this.cacheReads = Collections.unmodifiableList(cacheReads);
		this.localValues = Collections.unmodifiableList(localValues);
		this.endTick = endTick;
	}

	public CReplayHeader getHeader() {
		return this.header;
	}

	public List<Command> getCommands() {
		return this.commands;
	}

	public List<Checksum> getChecksums() {
		return this.checksums;
	}

	public List<CacheRead> getCacheReads() {
		return this.cacheReads;
	}

	public List<LocalValue> getLocalValues() {
		return this.localValues;
	}

	/** Whether the game was recorded to its end, not cut off. */
	public boolean isComplete() {
		return this.endTick >= 0;
	}

	/** The step a playback goes to: the end of the game, or the last one the replay knows of. */
	public int getLastTick() {
		if (this.endTick >= 0) {
			return this.endTick;
		}
		int last = 0;
		if (!this.checksums.isEmpty()) {
			last = this.checksums.get(this.checksums.size() - 1).tick;
		}
		if (!this.commands.isEmpty()) {
			// A command is applied after its step, so the next step is the first to show it.
			last = Math.max(last, this.commands.get(this.commands.size() - 1).tick + 1);
		}
		return last;
	}

	/** Reads only the header, for a list of replays. */
	public static CReplayHeader readHeader(final InputStream input) throws IOException {
		return readHeader(new DataInputStream(input));
	}

	private static CReplayHeader readHeader(final DataInputStream in) throws IOException {
		if (in.readInt() != MAGIC) {
			throw new CReplayException("not a replay file");
		}
		final int formatVersion = in.readInt();
		if (formatVersion != FORMAT_VERSION) {
			throw new CReplayException(
					"replay format " + formatVersion + ", this engine plays format " + FORMAT_VERSION);
		}
		return CReplayHeader.read(in);
	}

	public static CReplay read(final InputStream input) throws IOException {
		final DataInputStream headerIn = new DataInputStream(new BufferedInputStream(input));
		final CReplayHeader header = readHeader(headerIn);
		final List<Command> commands = new ArrayList<>();
		final List<Checksum> checksums = new ArrayList<>();
		final List<CacheRead> cacheReads = new ArrayList<>();
		final List<LocalValue> localValues = new ArrayList<>();
		int endTick = -1;
		final Inflater inflater = new Inflater();
		try (DataInputStream in = new DataInputStream(new InflaterInputStream(headerIn, inflater))) {
			boolean reading = true;
			while (reading) {
				final int type;
				try {
					type = in.read();
				}
				catch (final EOFException | ZipException e) {
					break;
				}
				if (type < 0) {
					break;
				}
				try {
					switch (type) {
					case COMMAND: {
						final int tick = readVarInt(in);
						final int player = in.readUnsignedByte();
						final byte[] bytes = new byte[readVarInt(in)];
						in.readFully(bytes);
						commands.add(new Command(tick, player, bytes));
						break;
					}
					case CHECKSUM:
						checksums.add(new Checksum(readVarInt(in), in.readInt()));
						break;
					case CACHE: {
						final int tick = readVarInt(in);
						final int length = readVarInt(in) - 1;
						byte[] bytes = null;
						if (length >= 0) {
							bytes = new byte[length];
							in.readFully(bytes);
						}
						cacheReads.add(new CacheRead(tick, bytes));
						break;
					}
					case LOCAL:
						localValues.add(new LocalValue(readVarInt(in), in.readFloat()));
						break;
					case END:
						endTick = readVarInt(in);
						reading = false;
						break;
					default:
						throw new CReplayException("unknown record " + type);
					}
				}
				catch (final EOFException | ZipException e) {
					// Cut off in the middle of a record: what came before it still plays.
					break;
				}
			}
		}
		finally {
			inflater.end();
		}
		return new CReplay(header, commands, checksums, cacheReads, localValues, endTick);
	}

	static int readVarInt(final DataInputStream in) throws IOException {
		int value = 0;
		for (int shift = 0; shift < 35; shift += 7) {
			final int b = in.readUnsignedByte();
			value |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				return value;
			}
		}
		throw new CReplayException("broken number");
	}

	static <T> T enumValue(final T[] values, final int ordinal) throws CReplayException {
		if ((ordinal < 0) || (ordinal >= values.length)) {
			throw new CReplayException("unknown value " + ordinal);
		}
		return values[ordinal];
	}

	public static final class Command {
		private final int tick;
		private final int playerIndex;
		private final byte[] bytes;

		public Command(final int tick, final int playerIndex, final byte[] bytes) {
			this.tick = tick;
			this.playerIndex = playerIndex;
			this.bytes = bytes;
		}

		public int getTick() {
			return this.tick;
		}

		public int getPlayerIndex() {
			return this.playerIndex;
		}

		public byte[] getBytes() {
			return this.bytes.clone();
		}

		byte[] bytes() {
			return this.bytes;
		}
	}

	public static final class Checksum {
		private final int tick;
		private final int value;

		public Checksum(final int tick, final int value) {
			this.tick = tick;
			this.value = value;
		}

		public int getTick() {
			return this.tick;
		}

		public int getValue() {
			return this.value;
		}
	}

	public static final class CacheRead {
		private final int tick;
		private final byte[] bytes;

		public CacheRead(final int tick, final byte[] bytes) {
			this.tick = tick;
			this.bytes = bytes;
		}

		public int getTick() {
			return this.tick;
		}

		/** The file's bytes, or null when there was no file. */
		public byte[] getBytes() {
			return this.bytes == null ? null : this.bytes.clone();
		}
	}

	public static final class LocalValue {
		private final int tick;
		private final float value;

		public LocalValue(final int tick, final float value) {
			this.tick = tick;
			this.value = value;
		}

		public int getTick() {
			return this.tick;
		}

		public float getValue() {
			return this.value;
		}
	}
}
