package com.etheller.warsmash.android;

import java.util.Locale;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

/** The app's resources in the game language the configuration chooses, made once for each language. */
final class LanguageResources {
	private Resources resources;
	private String language = "";

	/**
	 * @param plain the resources in the language of the system, which stay when no language is chosen
	 * @param chosen the game language such as esES, or empty
	 */
	Resources get(final Context context, final Resources plain, final String chosen) {
		if (chosen.length() < 2) {
			return plain;
		}
		if ((this.resources == null) || !chosen.equals(this.language)) {
			final Configuration configuration = new Configuration(plain.getConfiguration());
			configuration.setLocale(new Locale(chosen.substring(0, 2), chosen.substring(2)));
			this.resources = context.createConfigurationContext(configuration).getResources();
			this.language = chosen;
		}
		return this.resources;
	}
}
