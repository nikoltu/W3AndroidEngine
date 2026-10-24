package com.etheller.warsmash.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Lets the player choose the folder with the game data before the engine starts, and writes the
 * engine configuration for it.
 */
public class DataFolderActivity extends Activity {
	private static final String TAG = "Warsmash";
	private static final String PREFERENCES = "launcher";
	private static final String KEY_DATA_FOLDER = "dataFolder";
	private static final String INI_TEMPLATE = "warsmash-template.ini";

	private EditText folderField;
	private TextView status;
	private Button startButton;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final File appFolder = getExternalFilesDir(null);
		final String defaultFolder = new File(appFolder, "data").getAbsolutePath();
		final String savedFolder = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getString(KEY_DATA_FOLDER,
				defaultFolder);

		final int padding = (int) (24 * getResources().getDisplayMetrics().density);
		final LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(padding, padding, padding, padding);
		layout.setGravity(Gravity.CENTER_HORIZONTAL);

		final TextView title = new TextView(this);
		title.setText(R.string.data_folder_title);
		title.setTextSize(22);
		layout.addView(title);

		final TextView hint = new TextView(this);
		hint.setText(getString(R.string.data_folder_hint, appFolder.getAbsolutePath()));
		layout.addView(hint);

		this.folderField = new EditText(this);
		this.folderField.setSingleLine(true);
		this.folderField.setText(savedFolder);
		layout.addView(this.folderField);

		final LinearLayout buttons = new LinearLayout(this);
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		final Button checkButton = new Button(this);
		checkButton.setText(R.string.data_folder_check);
		checkButton.setOnClickListener(view -> check());
		buttons.addView(checkButton);
		this.startButton = new Button(this);
		this.startButton.setText(R.string.data_folder_start);
		this.startButton.setOnClickListener(view -> start());
		buttons.addView(this.startButton);
		layout.addView(buttons);

		this.status = new TextView(this);
		layout.addView(this.status);

		setContentView(layout);
		check();
	}

	private File selectedFolder() {
		return new File(this.folderField.getText().toString().trim());
	}

	private DataFolderCheck.Kind check() {
		final DataFolderCheck.Kind kind = DataFolderCheck.detect(selectedFolder());
		switch (kind) {
		case CASC:
			this.status.setText(R.string.data_folder_casc);
			break;
		case FOLDER:
			this.status.setText(R.string.data_folder_extracted);
			break;
		default:
			this.status.setText(R.string.data_folder_missing);
			break;
		}
		this.startButton.setEnabled(kind != DataFolderCheck.Kind.NONE);
		return kind;
	}

	private void start() {
		final DataFolderCheck.Kind kind = check();
		if (kind == DataFolderCheck.Kind.NONE) {
			return;
		}
		final File dataFolder = selectedFolder();
		final File appFolder = getExternalFilesDir(null);
		try {
			writeIni(new File(appFolder, AndroidLauncher.INI_FILE_NAME), DataFolderCheck.dataSourcesSection(kind,
					dataFolder, new File(appFolder, "resources"), new File(appFolder, "engine")));
		}
		catch (final IOException e) {
			Log.e(TAG, "Unable to write the engine configuration", e);
			this.status.setText(R.string.data_folder_write_failed);
			return;
		}
		final SharedPreferences.Editor editor = getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit();
		editor.putString(KEY_DATA_FOLDER, dataFolder.getAbsolutePath());
		editor.apply();

		final Intent intent = new Intent(this, AndroidLauncher.class);
		final Bundle extras = getIntent().getExtras();
		if (extras != null) {
			intent.putExtras(extras);
		}
		startActivity(intent);
		finish();
	}

	private void writeIni(final File iniFile, final String dataSources) throws IOException {
		try (InputStream template = getAssets().open(INI_TEMPLATE);
				OutputStream out = new FileOutputStream(iniFile)) {
			out.write(dataSources.getBytes(StandardCharsets.UTF_8));
			out.write('\n');
			final byte[] buffer = new byte[4096];
			int read;
			while ((read = template.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
		}
	}
}
