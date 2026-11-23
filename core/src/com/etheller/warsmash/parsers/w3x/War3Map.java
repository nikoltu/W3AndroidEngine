package com.etheller.warsmash.parsers.w3x;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.zip.Checksum;

import com.etheller.warsmash.util.FileReadChannel;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

import com.etheller.warsmash.datasources.CompoundDataSource;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.FolderDataSource;
import com.etheller.warsmash.datasources.GameLanguage;
import com.etheller.warsmash.datasources.GraphicsSet;
import com.etheller.warsmash.datasources.IndependentStream;
import com.etheller.warsmash.datasources.LayeredDataSource;
import com.etheller.warsmash.datasources.MpqDataSource;
import com.etheller.warsmash.datasources.SubdirDataSource;
import com.etheller.warsmash.parsers.w3x.doo.War3MapDoo;
import com.etheller.warsmash.parsers.w3x.objectdata.Warcraft3MapRuntimeObjectData;
import com.etheller.warsmash.parsers.w3x.unitsdoo.War3MapUnitsDoo;
import com.etheller.warsmash.parsers.w3x.w3e.War3MapW3e;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.parsers.w3x.w3r.War3MapW3r;
import com.etheller.warsmash.parsers.w3x.wpm.War3MapWpm;
import com.etheller.warsmash.units.custom.WTS;
import com.google.common.io.LittleEndianDataInputStream;

import mpq.MPQArchive;
import mpq.MPQException;

/**
 * Warcraft 3 map (W3X and W3M).
 */
public class War3Map implements DataSource {

	private CompoundDataSource baseDataSource;
	private DataSource dataSource;
	private DataSource internalMpqContentsDataSource;
	private DataSource mapContentsDataSource;
	private GraphicsSet graphics = GraphicsSet.CLASSIC;

	public War3Map(final DataSource dataSource, final String mapFileName) {
		try {
			// Slightly complex. Here's the theory:
			// 1.) Copy map into RAM
			// 2.) Setup a Data Source that will read assets
			// from either the map or the game, giving the map priority.

			SeekableByteChannel sbc;
			sbc = new SeekableInMemoryByteChannel(dataSource.read(mapFileName).array());
			this.internalMpqContentsDataSource = new MpqDataSource(new MPQArchive(sbc), sbc);
			this.mapContentsDataSource = localized(this.internalMpqContentsDataSource, GameLanguage.inGame());
			this.baseDataSource = new CompoundDataSource(Arrays.asList(dataSource, this.mapContentsDataSource));
			this.dataSource = this.baseDataSource;
			this.graphics = LayeredDataSource.graphicsOf(dataSource);
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
		catch (final MPQException e) {
			throw new RuntimeException(e);
		}
	}

	public War3Map(final DataSource dataSource, final File mapFileName) {
		try {
			if (mapFileName.isDirectory()) {
				this.internalMpqContentsDataSource = new FolderDataSource(mapFileName.toPath());
			}
			else {
				// Read the archive straight from the file: a big map held in the heap is too much on Android.
				final FileReadChannel sbc = new FileReadChannel(mapFileName);
				try {
					this.internalMpqContentsDataSource = new MpqDataSource(new MPQArchive(sbc), sbc);
				}
				catch (final IOException | MPQException | RuntimeException e) {
					sbc.close();
					throw e;
				}
			}
			this.mapContentsDataSource = localized(this.internalMpqContentsDataSource, GameLanguage.inGame());
			this.baseDataSource = new CompoundDataSource(Arrays.asList(dataSource, this.mapContentsDataSource));
			this.dataSource = this.baseDataSource;
			this.graphics = LayeredDataSource.graphicsOf(dataSource);
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
		catch (final MPQException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * The files of a map archive with the ones of its language folder over them: a map of the Reforged campaigns keeps
	 * its text in each language of the game in a "_Locales/xxXX.w3mod" folder. The files themselves when the language
	 * is English or the map has no folder of it.
	 */
	public static DataSource localized(final DataSource contents, final String locale) {
		final String folder = GameLanguage.mapFolder(locale);
		if ((folder == null) || !hasFolder(contents, folder)) {
			return contents;
		}
		return new CompoundDataSource(Arrays.asList(contents, new SubdirDataSource(contents, folder)));
	}

	private static boolean hasFolder(final DataSource contents, final String folder) {
		if (contents.has(folder + "war3map.wts")) {
			return true;
		}
		final String prefix = folder.replace('/', '\\').toLowerCase(Locale.ROOT);
		for (final String path : contents.getListfile()) {
			if (path.replace('/', '\\').toLowerCase(Locale.ROOT).startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	public MpqDataSource getInternalMpqContentsDataSource() {
		// TODO remove cast
		return (MpqDataSource) this.internalMpqContentsDataSource;
	}

	public War3MapW3i readMapInformation() throws IOException {
		War3MapW3i mapInfo;
		try (LittleEndianDataInputStream stream = new LittleEndianDataInputStream(
				this.dataSource.getResourceAsStream("war3map.w3i"))) {
			mapInfo = new War3MapW3i(stream);
		}
		return mapInfo;
	}

	/**
	 * Whether the map has the JASS script the engine runs, where the script file list looks for it. Maps saved with a
	 * Lua script (World Editor 1.31 and later) carry war3map.lua instead.
	 */
	public boolean hasJassScript() {
		return has("Scripts\\war3map.j") || has("war3map.j");
	}

	public boolean hasLuaScript() {
		return has("Scripts\\war3map.lua") || has("war3map.lua");
	}

	public War3MapW3e readEnvironment() throws IOException {
		War3MapW3e environment;
		try (LittleEndianDataInputStream stream = new LittleEndianDataInputStream(
				this.dataSource.getResourceAsStream("war3map.w3e"))) {
			environment = new War3MapW3e(stream);
		}
		return environment;
	}

	public War3MapWpm readPathing() throws IOException {
		War3MapWpm pathingMap;
		try (LittleEndianDataInputStream stream = new LittleEndianDataInputStream(
				this.dataSource.getResourceAsStream("war3map.wpm"))) {
			pathingMap = new War3MapWpm(stream);
		}
		return pathingMap;
	}

	public War3MapDoo readDoodads(final War3MapW3i war3MapW3i) throws IOException {
		War3MapDoo doodadsFile;
		try (LittleEndianDataInputStream stream = new LittleEndianDataInputStream(
				this.dataSource.getResourceAsStream("war3map.doo"))) {
			doodadsFile = new War3MapDoo(stream, war3MapW3i);
		}
		return doodadsFile;
	}

	public War3MapUnitsDoo readUnits(final War3MapW3i war3MapW3i) throws IOException {
		War3MapUnitsDoo unitsFile;
		try (LittleEndianDataInputStream stream = new LittleEndianDataInputStream(
				this.dataSource.getResourceAsStream("war3mapUnits.doo"))) {
			unitsFile = new War3MapUnitsDoo(stream, war3MapW3i);
		}
		return unitsFile;
	}

	public War3MapW3r readRegions() throws IOException {
		War3MapW3r unitsFile;
		try (LittleEndianDataInputStream stream = new LittleEndianDataInputStream(
				this.dataSource.getResourceAsStream("war3map.w3r"))) {
			unitsFile = new War3MapW3r(stream);
		}
		return unitsFile;
	}

	public Warcraft3MapRuntimeObjectData readModifications() throws IOException {
		return readModifications(Warcraft3MapRuntimeObjectData.loadWTS(this.dataSource));
	}

	/** The map's object data over the standard data of the graphics the game data shows. */
	public Warcraft3MapRuntimeObjectData readModifications(final WTS preloadedWTS) throws IOException {
		final Warcraft3MapRuntimeObjectData changes = Warcraft3MapRuntimeObjectData.load(this.dataSource, true,
				preloadedWTS, Warcraft3MapRuntimeObjectData.loadCampaignWTS(this.dataSource), this.graphics);
		return changes;
	}

	/** The steps of {@link #readModifications(WTS)}, which the caller runs one after the other. */
	public Warcraft3MapRuntimeObjectData.Loading readModificationsInSteps(final WTS preloadedWTS) throws IOException {
		return Warcraft3MapRuntimeObjectData.loadInSteps(this.dataSource, true,
				preloadedWTS != null ? preloadedWTS : Warcraft3MapRuntimeObjectData.loadWTS(this.dataSource),
				Warcraft3MapRuntimeObjectData.loadCampaignWTS(this.dataSource), this.graphics);
	}

	@Override
	public InputStream getResourceAsStream(final String filepath) throws IOException {
		return this.dataSource.getResourceAsStream(filepath);
	}

	@Override
	public File getFile(final String filepath) throws IOException {
		return this.dataSource.getFile(filepath);
	}

	@Override
	public File getDirectory(final String filepath) throws IOException {
		return this.dataSource.getDirectory(filepath);
	}

	@Override
	public IndependentStream.Opener opener(final String filepath) {
		return this.dataSource.opener(filepath);
	}

	@Override
	public boolean has(final String filepath) {
		return this.dataSource.has(filepath);
	}

	@Override
	public String findFirst(final String... filepaths) {
		return this.dataSource.findFirst(filepaths);
	}

	@Override
	public ByteBuffer read(final String path) throws IOException {
		return this.dataSource.read(path);
	}

	@Override
	public Collection<String> getListfile() {
		return this.dataSource.getListfile();
	}

	/**
	 * Closes only the map's own archive: the game data under it belongs to whoever passed it in and stays open for
	 * the menu and the next maps (a closed CASC storage cannot be read again).
	 */
	@Override
	public void close() throws IOException {
		this.internalMpqContentsDataSource.close();
	}

	@Override
	public DataSource graphicsIndependent() {
		return this.dataSource.graphicsIndependent();
	}

	/** Only the files of the map itself, which hold more than the game's of the same name. */
	public DataSource getMapContentsDataSource() {
		return this.mapContentsDataSource;
	}

	public DataSource getCompoundDataSource() {
		return this.baseDataSource;
	}

	public void setDataSource(DataSource tilesetSource) {
		this.dataSource = tilesetSource;
	}

	public long computeChecksum(final Checksum checksum) {
		final SeekableByteChannel inputChannel = getInternalMpqContentsDataSource().getInputChannel();
		try {
			final ByteBuffer byteBuffer = ByteBuffer.allocate(8 * 1024);
			inputChannel.position(0);
			int result;
			byteBuffer.clear();
			checksum.reset();
			while ((result = inputChannel.read(byteBuffer)) != -1) {
				checksum.update(byteBuffer.array(), 0, byteBuffer.position());
				byteBuffer.clear();
			}
			return checksum.getValue();
		}
		catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
