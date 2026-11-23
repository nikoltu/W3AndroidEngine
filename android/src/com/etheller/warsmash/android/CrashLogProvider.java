package com.etheller.warsmash.android;

import java.io.File;
import java.io.FileNotFoundException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import com.etheller.warsmash.launcher.CrashReport;
import com.etheller.warsmash.launcher.ProblemReport;

/**
 * Gives the share sheet read access to the crash logs, log snapshots and problem reports of the app's private files, and
 * to nothing else.
 */
public final class CrashLogProvider extends ContentProvider {
	static Uri uri(final Context context, final String name) {
		return new Uri.Builder().scheme("content").authority(context.getPackageName() + ".crashlogs").appendPath(name)
				.build();
	}

	static String type(final String name) {
		return ProblemReport.isReport(name) ? "application/zip" : "text/plain";
	}

	private File fileOf(final Uri uri) throws FileNotFoundException {
		final String name = uri.getLastPathSegment();
		if (!CrashReport.isShareable(name) && !ProblemReport.isReport(name)) {
			throw new FileNotFoundException(String.valueOf(uri));
		}
		final File file = new File(CrashLogs.privateFolder(getContext()), name);
		if (!file.isFile()) {
			throw new FileNotFoundException(String.valueOf(uri));
		}
		return file;
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public ParcelFileDescriptor openFile(final Uri uri, final String mode) throws FileNotFoundException {
		if (!"r".equals(mode)) {
			throw new FileNotFoundException("read only");
		}
		return ParcelFileDescriptor.open(fileOf(uri), ParcelFileDescriptor.MODE_READ_ONLY);
	}

	@Override
	public Cursor query(final Uri uri, final String[] projection, final String selection,
			final String[] selectionArgs, final String sortOrder) {
		final File file;
		try {
			file = fileOf(uri);
		}
		catch (final FileNotFoundException e) {
			return null;
		}
		final String[] columns = projection != null ? projection
				: new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
		final Object[] row = new Object[columns.length];
		for (int i = 0; i < columns.length; i++) {
			row[i] = OpenableColumns.SIZE.equals(columns[i]) ? (Object) file.length() : file.getName();
		}
		final MatrixCursor cursor = new MatrixCursor(columns, 1);
		cursor.addRow(row);
		return cursor;
	}

	@Override
	public String getType(final Uri uri) {
		return type(uri.getLastPathSegment());
	}

	@Override
	public Uri insert(final Uri uri, final ContentValues values) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int update(final Uri uri, final ContentValues values, final String selection,
			final String[] selectionArgs) {
		throw new UnsupportedOperationException();
	}
}
