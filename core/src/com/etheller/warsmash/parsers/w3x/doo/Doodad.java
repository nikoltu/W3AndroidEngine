package com.etheller.warsmash.parsers.w3x.doo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.util.ParseUtils;
import com.etheller.warsmash.util.War3ID;
import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;

public class Doodad {
	private War3ID id;
	private War3ID skinId;
	private int variation;
	private final float[] location = new float[3];
	private float angle;
	private final float[] scale = { 1f, 1f, 1f };
	private short flags; // short to store unsigned byte, java problem
	private short life; // short to store unsigned byte, java problem
	private long itemTable = -1; // long to store unsigned int32, java problem
	private final List<RandomItemSet> itemSets = new ArrayList<>();
	private int editorId;
	private final short[] u1 = new short[8]; // short to store unsigned byte, java problem
	// Version 12 (Reforged saves of the classic campaign maps) adds fields of unknown meaning, kept as they are.
	private int v12BeforeFlags = -1;
	private int v13AfterItemSets = -1;
	private final int[] v12AfterEditorId = new int[2];
	private final List<short[]> v12Entries = new ArrayList<>();

	public void load(final LittleEndianDataInputStream stream, final int version, final War3MapW3i mapInformation)
			throws IOException {
		this.id = ParseUtils.readWar3ID(stream);
		this.variation = stream.readInt();
		ParseUtils.readFloatArray(stream, this.location);
		this.angle = stream.readFloat();
		ParseUtils.readFloatArray(stream, this.scale);

		if (((mapInformation.getGameVersionMajor() * 100) + mapInformation.getGameVersionMinor()) >= 132) {
			this.skinId = ParseUtils.readWar3ID(stream);
		}
		else {
			this.skinId = this.id;
		}
		if (version >= 12) {
			this.v12BeforeFlags = stream.readInt();
		}

		this.flags = ParseUtils.readUInt8(stream);
		this.life = ParseUtils.readUInt8(stream);

		if (version > 7) {
			this.itemTable = ParseUtils.readUInt32(stream);

			for (long i = 0, l = ParseUtils.readUInt32(stream); i < l; i++) {
				final RandomItemSet itemSet = new RandomItemSet();

				itemSet.load(stream);

				this.itemSets.add(itemSet);
			}
		}

		if (version >= 13) {
			this.v13AfterItemSets = stream.readInt();
		}
		this.editorId = stream.readInt();
		if (version >= 12) {
			this.v12AfterEditorId[0] = stream.readInt();
			this.v12AfterEditorId[1] = stream.readInt();
			for (int i = 0, l = stream.readInt(); i < l; i++) {
				final short[] entry = new short[36];
				ParseUtils.readUInt8Array(stream, entry);
				this.v12Entries.add(entry);
			}
		}
	}

	public void save(final LittleEndianDataOutputStream stream, final int version, final War3MapW3i mapInformation)
			throws IOException {
		ParseUtils.writeWar3ID(stream, this.id);
		;
		stream.writeInt(this.variation);
		ParseUtils.writeFloatArray(stream, this.location);
		stream.writeFloat(this.angle);
		ParseUtils.writeFloatArray(stream, this.scale);
		if (((mapInformation.getGameVersionMajor() * 100) + mapInformation.getGameVersionMinor()) >= 132) {
			War3ID skinToWrite = this.skinId;
			if (skinToWrite == null) {
				skinToWrite = this.id;
			}
			ParseUtils.writeWar3ID(stream, skinToWrite);
		}
		if (version >= 12) {
			stream.writeInt(this.v12BeforeFlags);
		}
		ParseUtils.writeUInt8(stream, this.flags);
		ParseUtils.writeUInt8(stream, this.life);

		if (version > 7) {
			ParseUtils.writeUInt32(stream, this.itemTable);
			ParseUtils.writeUInt32(stream, this.itemSets.size());

			for (final RandomItemSet itemSet : this.itemSets) {
				itemSet.save(stream);
			}
		}

		if (version >= 13) {
			stream.writeInt(this.v13AfterItemSets);
		}
		stream.writeInt(this.editorId);
		if (version >= 12) {
			stream.writeInt(this.v12AfterEditorId[0]);
			stream.writeInt(this.v12AfterEditorId[1]);
			stream.writeInt(this.v12Entries.size());
			for (final short[] entry : this.v12Entries) {
				ParseUtils.writeUInt8Array(stream, entry);
			}
		}
	}

	public int getByteLength(final int version) {
		int size = 42;
		if (version >= 12) {
			size += 16 + (36 * this.v12Entries.size()) + ((version >= 13) ? 4 : 0);
		}

		if (version > 7) {
			size += 8;

			for (final RandomItemSet itemSet : this.itemSets) {
				size += itemSet.getByteLength();
			}
		}

		return size;
	}

	public War3ID getId() {
		return this.id;
	}

	public void setId(final War3ID id) {
		this.id = id;
	}

	public int getVariation() {
		return this.variation;
	}

	public void setVariation(final int variation) {
		this.variation = variation;
	}

	public float getAngle() {
		return this.angle;
	}

	public void setAngle(final float angle) {
		this.angle = angle;
	}

	public short getFlags() {
		return this.flags;
	}

	public void setFlags(final short flags) {
		this.flags = flags;
	}

	public short getLife() {
		return this.life;
	}

	public void setLife(final short life) {
		this.life = life;
	}

	public long getItemTable() {
		return this.itemTable;
	}

	public void setItemTable(final long itemTable) {
		this.itemTable = itemTable;
	}

	public int getEditorId() {
		return this.editorId;
	}

	public void setEditorId(final int editorId) {
		this.editorId = editorId;
	}

	public float[] getLocation() {
		return this.location;
	}

	public float[] getScale() {
		return this.scale;
	}

	public List<RandomItemSet> getItemSets() {
		return this.itemSets;
	}

	public short[] getU1() {
		return this.u1;
	}

}
