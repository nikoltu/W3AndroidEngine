package com.etheller.warsmash.datasources;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;

import com.etheller.warsmash.util.FileReadChannel;

import mpq.MPQArchive;
import mpq.MPQException;

public class MpqDataSourceDescriptor implements DataSourceDescriptor {
	/**
	 * Generated serial id
	 */
	private static final long serialVersionUID = 8424254987711783598L;
	private final String mpqFilePath;

	public MpqDataSourceDescriptor(final String mpqFilePath) {
		this.mpqFilePath = mpqFilePath;
	}

	@Override
	public DataSource createDataSource() {
		try {
			SeekableByteChannel sbc;
			sbc = new FileReadChannel(new File(this.mpqFilePath));
			return new MpqDataSource(new MPQArchive(sbc), sbc);
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
		catch (final MPQException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getDisplayName() {
		return "MPQ Archive: " + this.mpqFilePath;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + ((this.mpqFilePath == null) ? 0 : this.mpqFilePath.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final MpqDataSourceDescriptor other = (MpqDataSourceDescriptor) obj;
		if (this.mpqFilePath == null) {
			if (other.mpqFilePath != null) {
				return false;
			}
		}
		else if (!this.mpqFilePath.equals(other.mpqFilePath)) {
			return false;
		}
		return true;
	}

	public String getMpqFilePath() {
		return this.mpqFilePath;
	}

	@Override
	public DataSourceDescriptor duplicate() {
		return new MpqDataSourceDescriptor(this.mpqFilePath);
	}
}
