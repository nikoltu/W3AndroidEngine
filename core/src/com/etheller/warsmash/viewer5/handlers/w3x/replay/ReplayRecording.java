package com.etheller.warsmash.viewer5.handlers.w3x.replay;

import java.io.BufferedOutputStream;
import java.io.IOException;

import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.ChecksumSteps;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CGameCache;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplayHeader;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplayRecorder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplayWriter;

/**
 * A game being recorded: written as it goes, moved to its place when the game ends, unless the map's script asked
 * for no replay.
 */
public final class ReplayRecording implements ReplaySession {
	private final CReplayRecorder recorder;
	private final FileHandle written;
	private final FileHandle target;
	private boolean closed;
	private boolean notSaved;

	ReplayRecording(final CSimulation simulation, final CReplayHeader header, final FileHandle written,
			final FileHandle target, final ChecksumSteps checksum) throws IOException {
		this.written = written;
		this.target = target;
		written.parent().mkdirs();
		// Marks left by a game that was cut off belong to that game, not to this one.
		GameMarks.of(written).delete();
		final CReplayWriter writer = new CReplayWriter(new BufferedOutputStream(written.write(false)), header);
		this.recorder = new CReplayRecorder(simulation, writer, checksum);
		CGameCache.setDisk(this.recorder.recordingDisk(CGameCache.profileDisk()));
	}

	@Override
	public float localValue(final float value) {
		return this.recorder.localValue(value);
	}

	/**
	 * DoNotSaveReplay: the game leaves no replay and the score screen offers none. A file a test asked to record
	 * into is still written.
	 */
	public void doNotSave() {
		this.notSaved = true;
	}

	/** Where Capture a Bug keeps the moments of this game: beside the replay as it is written. */
	public FileHandle marksFile() {
		return GameMarks.of(this.written);
	}

	public boolean isSaved() {
		return !this.notSaved || this.written.equals(this.target);
	}

	/** After each step, before the commands that wait for the step's end are applied. */
	public synchronized void afterStep() {
		this.recorder.afterStep();
	}

	@Override
	public synchronized void gameEnded() {
		this.recorder.gameEnded();
	}

	@Override
	public synchronized void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;
		CGameCache.setDisk(null);
		this.recorder.finish();
		final boolean complete = this.recorder.isEnded();
		if (this.written.equals(this.target)) {
			return;
		}
		final FileHandle marks = GameMarks.of(this.written);
		if (!isSaved()) {
			this.written.delete();
			marks.delete();
		}
		else if (complete) {
			this.written.moveTo(this.target);
			final FileHandle targetMarks = GameMarks.of(this.target);
			if (marks.exists()) {
				marks.moveTo(targetMarks);
			}
			else {
				targetMarks.delete();
			}
		}
	}

	/**
	 * Save Replay on the score screen: the game is over, its replay ends here and a copy is kept under the given name.
	 *
	 * @return the saved file, or null when the replay could not be written
	 */
	public FileHandle saveAs(final String name) {
		close();
		if (!isSaved() || !this.target.exists()) {
			return null;
		}
		final FileHandle copy = GameReplays.folder().child(name + GameReplays.EXTENSION);
		this.target.copyTo(copy);
		final FileHandle marks = GameMarks.of(this.target);
		if (marks.exists()) {
			marks.copyTo(GameMarks.of(copy));
		}
		return copy;
	}
}
