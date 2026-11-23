package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import com.etheller.warsmash.util.Log;

/**
 * Campaigns a player has opened by hand, for a profile whose campaign progress got stuck. They are kept in a file of
 * their own beside the progress ({@link CCampaignProgress}), by the campaign's name in the campaign list, and the
 * campaign screens show them as played through; the progress, the saved games and the records are never changed, so
 * taking a campaign out of the set shows exactly what the profile had reached.
 */
public final class CampaignUnlock {
	public static final String FILE = "CampaignUnlocks.w3v";
	private static final int VERSION = 1;

	private CampaignUnlock() {
	}

	/** The campaigns of a profile folder that are opened by hand; empty when there are none or the file is unreadable. */
	public static Set<String> read(final File folder) {
		final File file = new File(folder, FILE);
		final Set<String> sections = new TreeSet<>();
		if (!file.isFile()) {
			return sections;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
			if (in.readInt() != VERSION) {
				return new TreeSet<>();
			}
			final int count = in.readInt();
			for (int i = 0; i < count; i++) {
				sections.add(in.readUTF());
			}
		}
		catch (final IOException e) {
			Log.error(e);
			return new TreeSet<>();
		}
		return sections;
	}

	/** Keeps the set in the profile folder; an empty set removes the file, as a profile that never used it has. */
	public static boolean write(final File folder, final Set<String> sections) {
		final File file = new File(folder, FILE);
		if (sections.isEmpty()) {
			return !file.exists() || file.delete();
		}
		final File temporary = new File(folder, FILE + ".tmp");
		try {
			folder.mkdirs();
			try (FileOutputStream stream = new FileOutputStream(temporary)) {
				final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(stream));
				out.writeInt(VERSION);
				out.writeInt(sections.size());
				for (final String section : new TreeSet<>(sections)) {
					out.writeUTF(section);
				}
				out.flush();
				stream.getFD().sync();
			}
			Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			return true;
		}
		catch (final IOException e) {
			Log.error(e);
			temporary.delete();
			return false;
		}
	}

	/** The campaigns the playing profile has opened by hand. */
	public static Set<String> current() {
		try {
			return read(CSaveStorage.profileFile("").file());
		}
		catch (final RuntimeException e) {
			return Collections.emptySet();
		}
	}
}
