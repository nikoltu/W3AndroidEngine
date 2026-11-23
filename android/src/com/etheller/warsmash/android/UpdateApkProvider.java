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

/** Gives the system installer read access to the downloaded update APK in the app's cache, and to nothing else. */
public final class UpdateApkProvider extends ContentProvider {
	static final String TYPE = "application/vnd.android.package-archive";

	static Uri uri(final Context context, final String name) {
		return new Uri.Builder().scheme("content").authority(context.getPackageName() + ".updateapk").appendPath(name)
				.build();
	}

	private File fileOf(final Uri uri) throws FileNotFoundException {
		final String name = uri.getLastPathSegment();
		if ((name == null) || !name.endsWith(".apk") || (name.indexOf('/') >= 0)) {
			throw new FileNotFoundException(String.valueOf(uri));
		}
		final File file = new File(new File(getContext().getCacheDir(), "update"), name);
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
		return TYPE;
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
