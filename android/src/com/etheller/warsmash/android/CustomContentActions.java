package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.LauncherKeys.KEY_MAP;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;

import com.etheller.warsmash.launcher.CustomContent;
import com.etheller.warsmash.launcher.StorageTree;

/**
 * The player's content folder on Android: it is picked with the system's folder picker, which grants the app a
 * permission that lasts, and the maps picked in the system's file picker are copied into its Maps/Custom folder.
 * The engine reads its files as plain files, so a picked tree is turned back into the path it stands for, as the game
 * data folder is.
 */
final class CustomContentActions {
	static final int REQUEST_FOLDER = 7301;
	static final int REQUEST_MAP = 7302;
	static final int REQUEST_STORAGE = 7303;
	private static final String TAG = LauncherActivity.TAG;
	private static final String KEY_TREE = "customContentTree";

	private final LauncherActivity activity;

	CustomContentActions(final LauncherActivity activity) {
		this.activity = activity;
	}

	/** The folder the player chose, or null. */
	File folder() {
		return new EngineSettings(this.activity).customFolder();
	}

	void chooseFolder() {
		final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				| Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
		// Pickers of Android 8 to 10 keep the internal storage out of the list unless asked.
		intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
		intent.putExtra("android.provider.extra.SHOW_ADVANCED", true);
		this.activity.startActivityForResult(intent, REQUEST_FOLDER);
	}

	/** The folder picker answered. */
	void folderChosen(final Intent data) {
		final Uri tree = data.getData();
		try {
			this.activity.getContentResolver().takePersistableUriPermission(tree,
					data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
			this.activity.preferences.edit().putString(KEY_TREE, tree.toString()).apply();
		}
		catch (final SecurityException | UnsupportedOperationException e) {
			// Some pickers offer no lasting permission; the folder is read as a path, which does not need it.
			Log.w(TAG, "No lasting permission for " + tree, e);
		}
		final StorageTree.Result result = DataFolderActivity.resolveTree(tree);
		if (!result.ok()) {
			Log.w(TAG, "Not a folder of the device storage: " + tree + " " + result.problem);
			this.activity.dialogs.showMessage(getString(R.string.options_custom_choose),
					DataFolderActivity.treeProblemText(this.activity, result.problem));
			return;
		}
		use(result.folder);
	}

	private void use(final File folder) {
		if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.R) && lacksStoragePermission() && !isInAppFolder(folder)) {
			// The folder is made once the permission is given.
			new EngineSettings(this.activity).setCustomFolder(folder);
			this.activity.requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE,
				Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_STORAGE);
			return;
		}
		if (needsAllFilesAccess() && !isInAppFolder(folder)) {
			// The folder is made once the access is given, when the engine configuration is read the next time.
			new EngineSettings(this.activity).setCustomFolder(folder);
			this.activity.dialogs.showMessage(getString(R.string.options_custom_choose),
					getString(R.string.custom_folder_access));
			final Intent settings = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
					Uri.parse("package:" + this.activity.getPackageName()));
			this.activity.startActivity(settings);
			return;
		}
		if ((!folder.isDirectory() && !folder.mkdirs()) || !CustomContent.ensureStructure(folder)) {
			this.activity.dialogs.showMessage(getString(R.string.options_custom_choose),
					getString(R.string.custom_folder_unwritable));
			return;
		}
		new EngineSettings(this.activity).setCustomFolder(folder);
		this.activity.reloadContent();
		this.activity.optionsPage.showOptions(OptionsPage.TAB_GAME_DATA);
	}

	/** The player answered the storage permission asked for a content folder (Android 6 to 10). */
	void storageAnswered() {
		final File folder = folder();
		if (folder == null) {
			return;
		}
		if (lacksStoragePermission()) {
			this.activity.dialogs.showMessage(getString(R.string.options_custom_choose),
					getString(R.string.custom_folder_storage_denied));
			return;
		}
		use(folder);
	}

	private boolean lacksStoragePermission() {
		return this.activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
	}

	/** Goes back to using only the game data. The folder and what is in it stay as they are. */
	void clear() {
		new EngineSettings(this.activity).setCustomFolder(null);
		this.activity.preferences.edit().remove(KEY_TREE).apply();
		this.activity.reloadContent();
		this.activity.optionsPage.showOptions(OptionsPage.TAB_GAME_DATA);
	}

	/** How many maps the player has imported into the content folder. */
	int importedCount() {
		return CustomContent.importedMaps(folder()).size();
	}

	/** The result line with the count in bold red, the only part of it that tells anything new. */
	private CharSequence deletedLine(final int deleted) {
		final String text = getString(R.string.game_delete_imported_done, deleted);
		final SpannableStringBuilder line = new SpannableStringBuilder(text);
		final String count = Integer.toString(deleted);
		final int at = text.indexOf(count);
		if (at >= 0) {
			line.setSpan(new ForegroundColorSpan(Color.RED), at, at + count.length(),
					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			line.setSpan(new StyleSpan(Typeface.BOLD), at, at + count.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		return line;
	}

	/** Asks, then deletes every imported map; game data, built-in maps and the folder's other files stay. */
	void askDeleteImported() {
		final int count = importedCount();
		if (count == 0) {
			this.activity.dialogs.showMessage(getString(R.string.game_delete_imported_none), null);
			return;
		}
		// Destructive: No comes first and is the plain way out.
		this.activity.dialogs.showQuestion(getString(R.string.game_delete_imported_question, count), null,
				new CharSequence[] { this.activity.customPage.glueLabel("NO", getString(R.string.game_no)),
					this.activity.customPage.glueLabel("YES", getString(R.string.game_yes)) },
				new Runnable[] { null, () -> {
					final int deleted = CustomContent.deleteImportedMaps(folder());
					final String chosen = this.activity.preferences.getString(KEY_MAP, null);
					if ((chosen != null) && chosen.startsWith("Maps\\Custom\\")) {
						this.activity.preferences.edit().remove(KEY_MAP).apply();
					}
					this.activity.reloadContent();
					this.activity.dialogs.showMessage(deletedLine(deleted), null);
				} });
	}

	void chooseMap() {
		final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		// Maps have no media type of their own; file managers call them anything.
		intent.setType("*/*");
		this.activity.startActivityForResult(intent, REQUEST_MAP);
	}

	/** The file picker answered: the map is copied away from the main thread and the page shows the result. */
	void mapChosen(final Uri uri) {
		final String name = displayName(uri);
		final File chosen = folder();
		final File root = chosen != null ? chosen : new File(EngineResources.appFolder(this.activity), "custom");
		if (chosen == null) {
			root.mkdirs();
		}
		new Thread(() -> {
			String message;
			boolean added = false;
			try (InputStream in = this.activity.getContentResolver().openInputStream(uri)) {
				if (in == null) {
					throw new IOException("No data for " + uri);
				}
				final File map = CustomContent.importMap(root, name, in);
				message = getString(R.string.game_import_done, map.getName());
				added = true;
			}
			catch (final CustomContent.ImportException e) {
				message = getString(importMessage(e.problem), name);
				Log.w(TAG, "The map was not imported: " + name, e);
			}
			catch (final IOException | RuntimeException e) {
				message = getString(R.string.game_import_failed, name);
				Log.w(TAG, "The map could not be copied: " + name, e);
			}
			final boolean done = added;
			final String shown = message;
			this.activity.runOnUiThread(() -> {
				// A window closed during the copy keeps the map and its folder, but shows nothing more.
				final boolean gone = this.activity.isDestroyed();
				if (done) {
					if (chosen == null) {
						new EngineSettings(this.activity).setCustomFolder(root);
						if (!gone) {
							this.activity.reloadContent();
						}
					}
					// The list opens with the new map chosen.
					final String file = CustomContent.mapFileName(name);
					this.activity.preferences.edit().putString(KEY_MAP, "Maps\\Custom\\" + file).apply();
					if (!gone) {
						this.activity.customPage.forget();
						this.activity.customPage.showCustom();
					}
				}
				if (!gone) {
					this.activity.dialogs.showMessage(getString(R.string.game_import_title), shown);
				}
			});
		}, "MapImport").start();
	}

	private static int importMessage(final CustomContent.Problem problem) {
		switch (problem) {
		case NOT_A_MAP_NAME:
			return R.string.game_import_not_map;
		case UNREADABLE:
			return R.string.game_import_unreadable;
		case NO_INFORMATION:
			return R.string.game_import_no_information;
		case NO_FOLDER:
			return R.string.game_import_no_folder;
		default:
			return R.string.game_import_failed;
		}
	}

	private String displayName(final Uri uri) {
		try (Cursor cursor = this.activity.getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME },
				null, null, null)) {
			if ((cursor != null) && cursor.moveToFirst() && !cursor.isNull(0)) {
				return cursor.getString(0);
			}
		}
		catch (final RuntimeException e) {
			Log.w(TAG, "No name for " + uri, e);
		}
		return uri.getLastPathSegment();
	}

	private String getString(final int id, final Object... arguments) {
		return this.activity.getString(id, arguments);
	}

	private static boolean needsAllFilesAccess() {
		return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) && !Environment.isExternalStorageManager();
	}

	/** The app's own folder is readable and writable without "All files access". */
	private boolean isInAppFolder(final File folder) {
		final File app = EngineResources.appFolder(this.activity);
		final String path = folder.getAbsolutePath();
		return (app != null) && (path.equals(app.getAbsolutePath()) || path.startsWith(app.getAbsolutePath() + File.separator));
	}
}
