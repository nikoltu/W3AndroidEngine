package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.CustomGame.RACES;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_ANIMATED;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_CAMPAIGN;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_EDITION;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_LAUNCHED;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_LAUNCH_RACE;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_MISSION;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_MUSIC;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_RETURN_PAGE;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_SKIN;
import static com.etheller.warsmash.launcher.LauncherKeys.PREFERENCES;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.GlueBackdropRenderer;
import com.etheller.warsmash.datasources.GameLanguage;
import com.etheller.warsmash.launcher.CampaignChoice;
import com.etheller.warsmash.launcher.DataFolderCheck;
import com.etheller.warsmash.launcher.CampaignResult;
import com.etheller.warsmash.launcher.GameResult;
import com.etheller.warsmash.launcher.LaunchGuard;
import com.etheller.warsmash.launcher.MapIssue;
import com.etheller.warsmash.launcher.LaunchRequest;
import com.etheller.warsmash.launcher.lan.LanLauncherModel;
import net.warsmash.lan.maps.LanMapIndex;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.LauncherKeys;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.viewer5.FogSettings;

/**
 * The launcher after the game data is chosen: prepares its art from the game data once (Prep), then shows the main
 * menu (Home), the Campaign, Missions, Custom Game and Load pages that start a game directly in the engine, and its
 * own Options and Help pages and the player profiles. The entries it has no page for yet open the engine's own menu.
 * Behind its pages it plays the original 3D glue scenes with the engine's viewer, or their recordings when the game
 * data has them.
 */
public class LauncherActivity extends AndroidApplication {
	static final String TAG = "Warsmash";
	private static final long PAGE_FADE_MILLIS = 150;

	/** Home rail in its original order: the glue string and the text used when the game data lacks it. */
	static final String[] RAIL = { "KEY_CAMPAIGN", "KEY_LOAD_SAVED_GAME", "KEY_VIEW_REPLAY", "KEY_CUSTOM_GAME",
		"KEY_LOCAL_AREA_NETWORK", "KEY_OPTIONS", "KEY_HELP" };
	static final int RAIL_CAMPAIGN = 0;
	static final int RAIL_LOAD = 1;
	static final int RAIL_REPLAY = 2;
	static final int RAIL_CUSTOM = 3;
	static final int RAIL_LAN = 4;
	static final int RAIL_OPTIONS = 5;
	static final int RAIL_HELP = 6;
	/** No rail entry is pressed (the profile page). */
	static final int RAIL_NONE = RAIL.length;

	static final int PAGE_PREP = -1;
	static final int PAGE_HOME = 0;
	static final int PAGE_OPTIONS = 1;
	static final int PAGE_HELP = 2;
	static final int PAGE_CAMPAIGN = 3;
	static final int PAGE_MISSIONS = 4;
	static final int PAGE_CUSTOM = 5;
	static final int PAGE_LOAD = 6;
	static final int PAGE_PROFILE = 7;
	static final int PAGE_REPLAY = 8;
	static final int PAGE_LAN = 9;

	/** The layers of the live scenes: the page's backdrop and the Continue Campaign banner. */
	static final int LAYER_PAGE = 0;
	static final int LAYER_BANNER = 1;
	/** The recorded scenes are 1920 x 810; their models are framed for that width. */
	private static final float SCENE_ASPECT = 1920f / 810f;

	LauncherBoard board;
	float scale;
	FrameLayout root;
	private FrameLayout page;
	DataTable ini;
	SkinCache cache;
	SkinCache.Art art;
	SharedPreferences preferences;
	final boolean[] cancelled = new boolean[1];
	int shown = PAGE_PREP;
	int afterPrep = PAGE_HOME;
	List<BackdropView> backdrops = new ArrayList<>();
	List<BackdropView> building = new ArrayList<>();
	boolean resumed;
	/** Grows with every page shown; an answer of the catalog for an earlier page is dropped. */
	int generation;
	GameCatalog catalog;
	GameCatalog.Campaigns campaigns;
	private GameResult returned;
	/** The campaign mission's result the Missions page shows instead of the chapters, until Continue or Back. */
	GameResult campaignResult;
	private boolean returnedUnexpectedly;
	Profiles profiles;
	GlueBackdropRenderer renderer;
	private View sceneView;
	private long sceneRequests;
	final GlueBackdropRenderer.Request[] buildingScenes = new GlueBackdropRenderer.Request[GlueBackdropRenderer.LAYERS];
	final BackdropView[] buildingSceneViews = new BackdropView[GlueBackdropRenderer.LAYERS];
	private final long[] shownScenes = new long[GlueBackdropRenderer.LAYERS];
	private final BackdropView[] sceneViews = new BackdropView[GlueBackdropRenderer.LAYERS];
	private MediaPlayer music;
	private SoundPool clicks;
	private int click;
	private int bigClick;
	private File musicTrack;
	/** A game is being started; the launcher takes no more taps. */
	boolean launching;
	private boolean checkingMap;
	TextView pageNote;

	final PrepPage prepPage = new PrepPage(this);
	final HomePage homePage = new HomePage(this);
	final OptionsPage optionsPage = new OptionsPage(this);
	final HelpPage helpPage = new HelpPage(this);
	final CampaignPage campaignPage = new CampaignPage(this);
	final CustomGamePage customPage = new CustomGamePage(this);
	final CustomContentActions customContent = new CustomContentActions(this);
	final LoadPage loadPage = new LoadPage(this);
	final ReplayPage replayPage = new ReplayPage(this);
	final ProfilePage profilePage = new ProfilePage(this);
	final LanPage lanPage = new LanPage(this);
	final LobbyPage lobbyPage = new LobbyPage(this);
	final LauncherDialogs dialogs = new LauncherDialogs(this);

	private static LanLauncherModel lanModel;
	private static AndroidLanEnv lanEnv;
	private static LauncherActivity currentActivity;
	private final Handler lanClock = new Handler(Looper.getMainLooper());
	private long lanTickAt;
	private final Runnable lanTick = new Runnable() {
		@Override
		public void run() {
			final long now = SystemClock.uptimeMillis();
			final float delta = Math.min(0.5f, (now - LauncherActivity.this.lanTickAt) / 1000f);
			LauncherActivity.this.lanTickAt = now;
			final LanLauncherModel model = lan();
			model.update(delta);
			LauncherActivity.this.homePage.setLanAttention(model.attention().isLit(), model.attention().hasChatDot());
			if (LauncherActivity.this.shown == PAGE_LAN) {
				if (LauncherActivity.this.lobbyPage.isShown()) {
					LauncherActivity.this.lobbyPage.onTick();
				}
				else {
					LauncherActivity.this.lanPage.onTick();
				}
			}
			if (LauncherActivity.this.resumed) {
				LauncherActivity.this.lanClock.postDelayed(this, 50);
			}
		}
	};

	/** The LAN pages' model: one for the process, as the LAN session is. */
	LanLauncherModel lan() {
		synchronized (LauncherActivity.class) {
			if (lanModel == null) {
				lanEnv = new AndroidLanEnv(getApplicationContext());
				lanModel = new LanLauncherModel(lanEnv);
			}
			lanModel.attention().setReducedMotion(!ValueAnimator.areAnimatorsEnabled());
			return lanModel;
		}
	}

	/** The launcher's activity on screen, or null. */
	static LauncherActivity current() {
		return currentActivity;
	}

	/** The index of the maps this device has, which the LAN pages share. */
	static LanMapIndex lanIndex(final Context context) {
		synchronized (LauncherActivity.class) {
			if (lanEnv == null) {
				lanEnv = new AndroidLanEnv(context.getApplicationContext());
			}
			return lanEnv.mapIndex();
		}
	}

	boolean lanChecksumAsked;

	/** The game data's checksum the players of a LAN game compare, read once from the catalog. */
	void askLanChecksum() {
		if (!this.lanChecksumAsked && (this.catalog != null)) {
			this.lanChecksumAsked = true;
			lan();
			this.catalog.dataChecksum(sum -> lanEnv.setDataChecksum(sum));
			lanEnv.mapIndex().setVirtualMaps(this.catalog.virtualMaps());
		}
	}

	private final LanguageResources languageResources = new LanguageResources();

	/**
	 * The launcher's own texts follow the game language the configuration chooses, where a translation of them exists
	 * (values-xx), and the language of the system otherwise.
	 */
	@Override
	public Resources getResources() {
		final Resources plain = super.getResources();
		if (getBaseContext() == null) {
			return plain;
		}
		final Resources found = this.languageResources.get(this, plain,
				this.ini == null ? "" : GameLanguage.configured(this.ini));
		// The LAN texts, which any page and the game itself ask for, are told the same language.
		WarsmashApplication.useLanguageResources(found == plain ? null : found);
		return found;
	}

	/** The launcher's own text for a glue string the game data lacks, in the language of the game. */
	String glueFallback(final String key) {
		final int id = getResources().getIdentifier("glue_" + key.toLowerCase(Locale.ROOT), "string", R.class.getPackage().getName());
		return id != 0 ? getString(id) : key;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (GlesSupport.redirect(this)) {
			return;
		}
		MemoryState.noteAsync(this, "launcher created");
		ExitLogs.recordPreviousExit(this);
		DataFolderActivity.useFolderCache(this);
		CCampaignProgress.setAllMissionsOpen(
				(getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0);
		BackCallbacks.register(this, this::goBack);
		if (AndroidLauncher.showRunningGame(this)) {
			finish();
			return;
		}
		this.preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
		this.profiles = new Profiles(this, this.preferences);
		final File iniFile = SkinCache.iniFile(this);
		try {
			this.ini = iniFile.isFile() ? AndroidLauncher.loadWarsmashIni(iniFile) : null;
		}
		catch (final IOException e) {
			Log.e(TAG, "Unable to read " + iniFile, e);
			this.ini = null;
		}
		this.cache = this.ini == null ? null : SkinCache.forConfiguration(this, this.ini, chosenSkin());
		if ((this.cache == null) || !DataFolderCheck.hasGameData(this.ini.get("DataSources"))) {
			openGameData();
			return;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
		}
		hideSystemBars();
		final DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
		final float screenW = Math.max(metrics.widthPixels, metrics.heightPixels);
		final float screenH = Math.min(metrics.widthPixels, metrics.heightPixels);
		this.board = LauncherBoard.device(screenW, screenH);
		this.scale = this.board.scale;
		final FrameLayout outer = new FrameLayout(this);
		outer.setBackgroundColor(Color.BLACK);
		this.root = new FrameLayout(this);
		outer.addView(this.root, new FrameLayout.LayoutParams(px(this.board.width), px(this.board.height),
				Gravity.CENTER));
		addSceneView();
		setContentView(outer);
		this.dialogs.trackOnscreenKeyboard();
		this.catalog = newCatalog();
		takeReturn();
		if (this.cache.isCurrent()) {
			showAfterPrep();
		}
		else {
			this.prepPage.showPrep();
		}
	}

	/**
	 * The surface the live 3D scenes are drawn on, behind every page; the pages leave it visible where a scene is
	 * shown. The libGDX application of the launcher only draws these scenes; the game itself is its own activity.
	 */
	private void addSceneView() {
		AndroidLauncher.loadExtensions(new File(getCacheDir(), "sounds"));
		this.renderer = new GlueBackdropRenderer(this.ini, new GlueBackdropRenderer.Listener() {
			@Override
			public void shown(final int layer, final long request) {
				runOnUiThread(() -> {
					if ((LauncherActivity.this.shownScenes[layer] == request)
							&& (LauncherActivity.this.sceneViews[layer] != null)) {
						LauncherActivity.this.sceneViews[layer].reveal();
					}
				});
			}

			@Override
			public void failed(final int layer, final long request) {
				Log.w(TAG, "The 3D scene of layer " + layer + " cannot be shown");
			}
		});
		final AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		config.useGL30 = true;
		config.disableAudio = true;
		config.useImmersiveMode = true;
		config.useAccelerometer = false;
		config.useCompass = false;
		config.useGyroscope = false;
		config.useRotationVectorSensor = false;
		config.a = 0;
		config.depth = 16;
		final GlueBackdropRenderer scenes = this.renderer;
		this.sceneView = initializeForView(GlContextWatch.watch(scenes, scenes::contextLost), config);
		GlContextWatch.dropContextOnPauseIfAsked(this, this.sceneView);
		this.root.addView(this.sceneView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
	}

	/** The catalog of the game data, whose campaigns follow the edition the launcher is in. */
	private GameCatalog newCatalog() {
		final GameCatalog opened = new GameCatalog(this, this.ini, GameCatalog.dataKey(this.ini));
		opened.setEdition(this.preferences.getString(KEY_EDITION, LauncherKeys.EDITION_TFT));
		return opened;
	}

	/**
	 * After a game started from here: the page it was started from comes back, and a game that crashed closed
	 * unexpectedly. A game without a result was stopped from outside unless Android says it crashed.
	 */
	private void takeReturn() {
		final long launched = this.preferences.getLong(KEY_LAUNCHED, 0);
		if (launched == 0) {
			LaunchContract.takeResult(this);
			return;
		}
		this.returned = LaunchContract.takeResult(this);
		this.returnedUnexpectedly = this.returned == null ? LaunchContract.crashedSince(this, launched)
				: this.returned.crashed();
		if ((this.returned != null) && this.returned.crashed()) {
			this.returned = null;
		}
		this.afterPrep = this.preferences.getInt(KEY_RETURN_PAGE, PAGE_HOME);
		if (this.afterPrep == PAGE_LAN) {
			// The game of the lobby is over: the session closes and the Join Game page shows.
			lan().gameEnded();
		}
		if ((this.returned != null) && "exit".equals(this.returned.result)) {
			this.afterPrep = PAGE_HOME;
		}
		else if ((this.returned == null) && !this.returnedUnexpectedly) {
			// A game stopped from outside brought no result: this is a fresh start of the launcher, not a return.
			this.afterPrep = PAGE_HOME;
		}
		this.campaignResult = null;
		if (CampaignResult.shows(this.returned)) {
			// As the Reforged campaign screen does, the mission's result comes first, over its campaign; both skins
			// share the flow and differ only in its look.
			this.campaignResult = this.returned;
			this.afterPrep = PAGE_MISSIONS;
		}
		final GameResult result = this.returned;
		final boolean lanSource = (result != null) && LaunchRequest.SOURCE_LAN.equals(result.source);
		if ((result != null) && (LaunchRequest.SOURCE_CUSTOM.equals(result.source) || lanSource) && !result.failed()
				&& !"exit".equals(result.result) && !result.profile.isEmpty()) {
			// A LAN game counts too; a random race is counted as the race it drew.
			final int played = (result.race > 0) && (result.race < RACES.length) ? result.race
					: lanSource ? 0 : this.preferences.getInt(KEY_LAUNCH_RACE, 0);
			this.profiles.record(result.profile, result.result, played);
		}
		final SharedPreferences.Editor editor = this.preferences.edit().remove(KEY_LAUNCHED);
		if (CampaignResult.shows(this.returned) && (this.returned.campaign >= 0)
				&& (this.preferences.getInt(KEY_CAMPAIGN, -1) != this.returned.campaign)) {
			// The result is of the campaign the game played, which a save loaded in the game may have changed.
			editor.putInt(KEY_CAMPAIGN, this.returned.campaign).remove(KEY_MISSION);
		}
		if ((this.returned != null) && "victory".equals(this.returned.result)
				&& LaunchRequest.SOURCE_CAMPAIGN.equals(this.returned.source)) {
			// The mission the victory opened is the one shown chosen.
			editor.remove(KEY_MISSION);
		}
		editor.apply();
	}

	/** The dialog a return brings, once the page it returns to is shown. */
	private void showReturnDialog() {
		final GameResult result = this.returned;
		final boolean unexpectedly = this.returnedUnexpectedly;
		this.returned = null;
		this.returnedUnexpectedly = false;
		if (unexpectedly) {
			this.dialogs.showMessage(getString(R.string.game_closed_title), getString(R.string.game_closed_line));
		}
		else if ((result != null) && (result.loadFailure != null)) {
			this.dialogs.showMessage(getString(R.string.game_cannot_load),
					this.loadPage.loadFailureText(result.loadFailure));
		}
		else if ((result != null) && result.failed()) {
			this.dialogs.showMessage(getString(R.string.game_not_started),
					result.issue != MapIssue.NONE ? this.customPage.issueText(result.issue, false)
							: result.failureText());
		}
	}

	/**
	 * The skin the player chose; Reforged only while the game data has its interface. A classic cache that Android
	 * cleared cannot tell, so the choice is kept and the classic skin used until the preparation finds out again.
	 */
	private String chosenSkin() {
		final String skin = this.preferences.getString(KEY_SKIN, SkinArt.CLASSIC_SKIN);
		return SkinArt.REFORGED_SKIN.equals(skin) && SkinCache.hasReforged(this) ? SkinArt.REFORGED_SKIN
				: SkinArt.CLASSIC_SKIN;
	}

	@Override
	public void onWindowFocusChanged(final boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			hideSystemBars();
		}
	}

	@SuppressWarnings("deprecation")
	private void hideSystemBars() {
		getWindow().getDecorView()
				.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
						| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
						| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
	}

	@Override
	protected void onResume() {
		super.onResume();
		this.resumed = true;
		currentActivity = this;
		ProfileStorageAndroid.mirror(this.profiles);
		this.lanTickAt = SystemClock.uptimeMillis();
		this.lanClock.removeCallbacks(this.lanTick);
		this.lanClock.post(this.lanTick);
		for (final BackdropView backdrop : this.backdrops) {
			backdrop.play();
		}
		updateMusic();
	}

	@Override
	protected void onPause() {
		this.resumed = false;
		this.lanClock.removeCallbacks(this.lanTick);
		if (currentActivity == this) {
			currentActivity = null;
		}
		for (final BackdropView backdrop : this.backdrops) {
			backdrop.stop();
		}
		stopMusic();
		super.onPause();
	}

	/** The original menu music plays while the launcher shows a page and the Menu music box is on (Prep is silent). */
	void updateMusic() {
		final File track = menuMusic();
		final boolean wanted = this.resumed && (this.shown != PAGE_PREP) && (track != null)
				&& this.preferences.getBoolean(KEY_MUSIC, true);
		if (!wanted) {
			stopMusic();
			return;
		}
		if (this.music != null) {
			if (track.equals(this.musicTrack)) {
				return;
			}
			stopMusic();
		}
		final MediaPlayer player = new MediaPlayer();
		this.music = player;
		this.musicTrack = track;
		try {
			player.setDataSource(track.getAbsolutePath());
			player.setLooping(true);
			// The engine's music volume (40 of 100 unless the player changed it).
			final float volume = Math.max(0, Math.min(100, new EngineSettings(this).number("musicVolume", 40))) / 100f;
			player.setVolume(volume, volume);
			player.setOnPreparedListener(prepared -> {
				if (this.music == prepared) {
					prepared.start();
				}
			});
			player.setOnErrorListener((failed, what, extra) -> {
				Log.w(TAG, "The menu music cannot play: " + what + "/" + extra);
				stopMusic();
				return true;
			});
			player.prepareAsync();
		}
		catch (final IOException | RuntimeException e) {
			Log.w(TAG, "The menu music cannot play", e);
			stopMusic();
		}
	}

	/** Reign of Chaos has its own main screen music; without it the expansion's plays. */
	private File menuMusic() {
		if (this.cache == null) {
			return null;
		}
		final File roc = this.cache.file(SkinArt.MUSIC_ROC);
		if ("roc".equals(this.preferences.getString(KEY_EDITION, "tft")) && roc.isFile()) {
			return roc;
		}
		final File tft = this.cache.file(SkinArt.MUSIC);
		return tft.isFile() ? tft : null;
	}

	private void stopMusic() {
		if (this.music != null) {
			try {
				this.music.release();
			}
			catch (final RuntimeException e) {
				Log.w(TAG, "Unable to release the menu music", e);
			}
			this.music = null;
		}
	}

	@Override
	protected void onDestroy() {
		stopMusic();
		if (this.clicks != null) {
			this.clicks.release();
			this.clicks = null;
			LauncherKit.clicks = null;
		}
		this.cancelled[0] = true;
		if (this.catalog != null) {
			this.catalog.close();
		}
		super.onDestroy();
	}

	@Override
	public boolean dispatchKeyEvent(final android.view.KeyEvent event) {
		return this.optionsPage.hotkeys.handleKey(event) || super.dispatchKeyEvent(event);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void onBackPressed() {
		goBack();
	}

	private void goBack() {
		if (this.launching) {
			return;
		}
		if (this.dialogs.dialog != null) {
			if (!this.dialogs.dialogLocked) {
				this.dialogs.closeDialog();
			}
		}
		else if ((this.shown == PAGE_MISSIONS) && (this.campaignResult != null)) {
			this.campaignResult = null;
			this.campaignPage.showMissions();
		}
		else if (this.shown == PAGE_MISSIONS) {
			this.campaignPage.showCampaign();
		}
		else if ((this.shown == PAGE_LAN) && lan().inSession()) {
			// A lobby is left with its own button, which asks a host once more.
			this.lobbyPage.leave();
		}
		else if ((this.shown == PAGE_OPTIONS) || (this.shown == PAGE_HELP) || (this.shown == PAGE_CAMPAIGN)
				|| (this.shown == PAGE_LAN)
				|| (this.shown == PAGE_CUSTOM) || (this.shown == PAGE_LOAD) || (this.shown == PAGE_PROFILE)
				|| (this.shown == PAGE_REPLAY)) {
			this.homePage.showHome();
		}
		else if (this.shown == PAGE_HOME) {
			this.dialogs.showExit();
		}
		else {
			finishAfterTransition();
		}
	}

	@Override
	public boolean dispatchTouchEvent(final MotionEvent event) {
		if (this.launching) {
			return true;
		}
		return super.dispatchTouchEvent(event);
	}

	/** The glue click sounds of the skin's art, loaded once the art is prepared. */
	private void loadClicks() {
		if ((this.clicks != null) || (this.cache == null) || !this.cache.file(SkinArt.CLICK).isFile()) {
			return;
		}
		this.clicks = new SoundPool.Builder().setMaxStreams(2)
				.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
						.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
				.build();
		this.click = this.clicks.load(this.cache.file(SkinArt.CLICK).getAbsolutePath(), 1);
		this.bigClick = this.cache.file(SkinArt.CLICK_BIG).isFile()
				? this.clicks.load(this.cache.file(SkinArt.CLICK_BIG).getAbsolutePath(), 1)
				: this.click;
		LauncherKit.clicks = main -> {
			if (this.clicks != null) {
				this.clicks.play(main ? this.bigClick : this.click, 1, 1, 1, 0, 1);
			}
		};
	}

	int px(final float design) {
		return Math.round(design * this.scale);
	}

	void setPage(final FrameLayout view, final int kind) {
		for (final BackdropView backdrop : this.backdrops) {
			backdrop.stop();
		}
		this.backdrops = this.building;
		this.building = new ArrayList<>();
		final View previous = this.page;
		this.page = view;
		if ((this.shown == PAGE_LAN) && (kind != PAGE_LAN) && (lanModel != null)) {
			lanModel.pageHidden();
		}
		this.shown = kind;
		// Preparing the skin comes before the page a game returns to, and keeps its result for it.
		if ((kind != PAGE_MISSIONS) && (kind != PAGE_PREP)) {
			this.campaignResult = null;
		}
		this.generation++;
		final int at = previous != null ? this.root.indexOfChild(previous) + 1 : this.sceneView != null ? 1 : 0;
		this.root.addView(view, at, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		if (previous != null) {
			if (ValueAnimator.areAnimatorsEnabled()) {
				view.setAlpha(0);
				view.animate().alpha(1).setDuration(PAGE_FADE_MILLIS).withEndAction(() -> this.root.removeView(previous));
			}
			else {
				this.root.removeView(previous);
			}
		}
		loadClicks();
		if (this.resumed) {
			for (final BackdropView backdrop : this.backdrops) {
				backdrop.play();
			}
		}
		for (int i = 0; i < GlueBackdropRenderer.LAYERS; i++) {
			showScene(i, this.buildingScenes[i], this.buildingSceneViews[i]);
			this.buildingScenes[i] = null;
			this.buildingSceneViews[i] = null;
		}
		updateMusic();
	}

	/** Asks the renderer for a layer's scene; the view's still gives way when the scene is drawn. */
	void showScene(final int layer, final GlueBackdropRenderer.Request request, final BackdropView view) {
		this.shownScenes[layer] = request != null ? request.id() : 0;
		this.sceneViews[layer] = view;
		if (this.renderer != null) {
			this.renderer.show(layer, request);
		}
	}

	void showAfterPrep() {
		final int next = this.afterPrep;
		this.afterPrep = PAGE_HOME;
		this.prepPage.prepDone = 0;
		this.prepPage.prepTotal = 0;
		showPage(next);
		if (this.returnedUnexpectedly || (this.returned != null)) {
			showReturnDialog();
		}
	}

	void showPage(final int kind) {
		switch (kind) {
		case PAGE_OPTIONS:
			this.optionsPage.showOptions(this.optionsPage.optionsTab);
			break;
		case PAGE_CAMPAIGN:
			this.campaignPage.showCampaign();
			break;
		case PAGE_MISSIONS:
			this.campaignPage.showMissions();
			break;
		case PAGE_CUSTOM:
			this.customPage.showCustom();
			break;
		case PAGE_LOAD:
			this.loadPage.showLoad();
			break;
		case PAGE_REPLAY:
			this.replayPage.showReplays();
			break;
		case PAGE_PROFILE:
			this.profilePage.showProfile(null);
			break;
		case PAGE_LAN:
			this.lanPage.showLan();
			break;
		default:
			this.homePage.showHome();
			break;
		}
	}

	boolean animated() {
		return this.preferences.getBoolean(KEY_ANIMATED, true) && ValueAnimator.areAnimatorsEnabled();
	}

	/** An original scene behind a page: its recording when the data has one and animations are on, else the still. */
	BackdropView backdrop(final Drawable still, final String scene, final float focus) {
		return backdrop(still, scene, focus, 1, 1);
	}

	/** As above, the scene enlarged by the zoom about its left edge at pivotY of the height. */
	BackdropView backdrop(final Drawable still, final String scene, final float focus, final float zoom,
			final float pivotY) {
		final BackdropView view = new BackdropView(this, still, animated() ? this.art.recording(scene) : null, focus,
				zoom, pivotY);
		this.building.add(view);
		return view;
	}

	/**
	 * A page's original scene: its recording when the game data has one, else the live 3D scene drawn behind the
	 * page once it is loaded, with the still until then. With a model x (a part of the recorded width, else negative)
	 * the scene is cropped around its model the way the recording is.
	 */
	BackdropView sceneBackdrop(final Drawable still, final String recorded, final float focus, final int layer,
			final String skinField, final String fallbackField, final FogSettings fog, final int fogVersion,
			final float x, final float y, final float w, final float h, final float modelX) {
		return sceneBackdrop(still, recorded, focus, layer, skinField, fallbackField, fog, fogVersion, x, y, w, h,
				modelX, 1, 1);
	}

	BackdropView sceneBackdrop(final Drawable still, final String recorded, final float focus, final int layer,
			final String skinField, final String fallbackField, final FogSettings fog, final int fogVersion,
			final float x, final float y, final float w, final float h, final float modelX, final float zoom,
			final float pivotY) {
		final BackdropView view = backdrop(still, recorded, focus, zoom, pivotY);
		if (!view.hasRecording() && animated() && (this.renderer != null)) {
			final Rectangle clip = glRect(x, y, w, h);
			Rectangle viewport = clip;
			final float sceneW = h * SCENE_ASPECT;
			if ((zoom > 1) || (pivotY < 1)) {
				// A framed scene (pivotY under 1) is placed as its recording is: cropped by the focus, then enlarged.
				final float framedW = h * CampaignChoice.RECORDING_ASPECT;
				viewport = glRect(x - (Math.max(0, framedW - w) * focus * zoom), y + (pivotY * h * (1 - zoom)),
						framedW * zoom, h * zoom);
			}
			else if ((modelX >= 0) && (sceneW > w)) {
				final float crop = CampaignChoice.modelFocus(modelX, w / h, SCENE_ASPECT);
				viewport = glRect(x - (crop * (sceneW - w)), y, sceneW, h);
			}
			this.buildingScenes[layer] = new GlueBackdropRenderer.Request(++this.sceneRequests, skinField,
					fallbackField, fog, fogVersion, viewport, viewport == clip ? null : clip);
			this.buildingSceneViews[layer] = view;
		}
		return view;
	}

	/** A campaign's background model skin field in the edition the campaign list is shown for. */
	String backdropField(final String background) {
		return CampaignChoice.backdropField(background,
				this.preferences.getString(KEY_EDITION, LauncherKeys.EDITION_TFT));
	}

	/** A place of the board in the scene surface's pixels, counted from its bottom as GL counts. */
	private Rectangle glRect(final float x, final float y, final float w, final float h) {
		return new Rectangle(px(x), px(this.board.height) - px(y + h), px(w), px(h));
	}

	void openEntry(final int entry) {
		if (entry == RAIL_OPTIONS) {
			this.optionsPage
					.showOptions(this.shown == PAGE_OPTIONS ? this.optionsPage.optionsTab : OptionsPage.TAB_GRAPHICS);
		}
		else if (entry == RAIL_HELP) {
			this.helpPage.showHelp(this.shown == PAGE_HELP ? this.helpPage.helpTab : 0);
		}
		else if (entry == RAIL_CAMPAIGN) {
			this.campaignPage.showCampaign();
		}
		else if (entry == RAIL_LOAD) {
			this.loadPage.showLoad();
		}
		else if (entry == RAIL_REPLAY) {
			this.replayPage.showReplays();
		}
		else if (entry == RAIL_LAN) {
			this.lanPage.showLan();
		}
		else if (entry == RAIL_CUSTOM) {
			// Maps added to the folders since the list was read are found by reading it again.
			this.customPage.forget();
			this.customPage.showCustom();
		}
	}

	/**
	 * Starts a game in the engine; the launcher comes back when it ends, on the page given. A map the engine cannot
	 * play is refused here, before the engine is started.
	 */
	void launchGame(final LaunchRequest request, final int returnPage) {
		// Only one game runs at a time: this is where every page's start action ends up.
		final LaunchGuard.Blocked blocked = LaunchGuard.check(request);
		if (blocked != null) {
			this.lanPage.showBlocked(blocked);
			return;
		}
		if (this.launching || this.checkingMap || isFinishing()) {
			return;
		}
		final GameCatalog asked = this.catalog;
		if ((request.mapFile == null) || request.mapChecked || (asked == null)) {
			beginLaunch(request, returnPage);
			return;
		}
		this.checkingMap = true;
		asked.mapIssue(request.mapFile, issue -> {
			this.checkingMap = false;
			if (isFinishing()) {
				return;
			}
			if (issue == null) {
				// the catalog was closed during the check: the lobby must not wait for a game that never starts
				if (request.lan) {
					lan().launchFailed();
				}
			}
			else if (issue == MapIssue.NONE) {
				beginLaunch(request, returnPage);
			}
			else if (request.lan) {
				lan().launchRefused(issue);
			}
			else {
				this.dialogs.showMessage(getString(R.string.game_not_started), this.customPage.issueText(issue, false));
			}
		});
	}

	private void beginLaunch(final LaunchRequest request, final int returnPage) {
		final LaunchGuard.Blocked blocked = LaunchGuard.check(request);
		if (blocked != null) {
			this.lanPage.showBlocked(blocked);
			return;
		}
		// A second tap while the first one starts the game would start another game in the same process.
		if (this.launching || isFinishing()) {
			return;
		}
		this.launching = true;
		stopMusic();
		request.profile = this.profiles.current();
		request.edition = this.preferences.getString(KEY_EDITION, LauncherKeys.EDITION_TFT);
		request.skin = chosenSkin();
		if (SkinArt.REFORGED_SKIN.equals(request.skin)) {
			request.portrait = this.profiles.portrait(request.profile) + 1;
		}
		final Intent intent = new Intent(this, AndroidLauncher.class);
		LaunchContract.put(intent, request);
		LaunchContract.takeResult(this);
		intent.putExtra(LaunchContract.EXTRA_TOKEN, LaunchContract.newToken(this));
		final SharedPreferences.Editor editor = this.preferences.edit().putInt(KEY_RETURN_PAGE, returnPage)
				.putLong(KEY_LAUNCHED, System.currentTimeMillis());
		if ((request.saveFile != null) && (request.campaign >= 0)) {
			// A campaign mission's save ends on its campaign's result, over that campaign as a mission started from
			// its list does.
			editor.putInt(KEY_CAMPAIGN, request.campaign);
			if (request.mission >= 0) {
				editor.putInt(KEY_MISSION, request.mission);
			}
			else {
				editor.remove(KEY_MISSION);
			}
		}
		editor.commit();
		startActivity(intent);
		finish();
		cutTo();
	}

	/**
	 * The engine's loading screen takes the whole screen at once: the window animation would slide it in over the
	 * launcher's menu, which then shows beside the loading art.
	 */
	private void cutTo() {
		overridePendingTransition(0, 0);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if ((resultCode != RESULT_OK) || (data == null) || (data.getData() == null)) {
			return;
		}
		if (requestCode == CustomContentActions.REQUEST_FOLDER) {
			this.customContent.folderChosen(data);
		}
		else if (requestCode == CustomContentActions.REQUEST_MAP) {
			this.customContent.mapChosen(data.getData());
		}
	}

	@Override
	public void onRequestPermissionsResult(final int requestCode, final String[] permissions,
			final int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == CustomContentActions.REQUEST_STORAGE) {
			this.customContent.storageAnswered();
		}
	}

	/** The engine configuration was changed by the launcher (the content folder): it is read again with the catalog. */
	void reloadContent() {
		if (isDestroyed()) {
			// onDestroy closed the catalog; a new one would keep its thread and the game data open
			return;
		}
		final File iniFile = SkinCache.iniFile(this);
		try {
			this.ini = AndroidLauncher.loadWarsmashIni(iniFile);
		}
		catch (final IOException e) {
			Log.e(TAG, "Unable to read " + iniFile, e);
			return;
		}
		this.lanChecksumAsked = false;
		final GameCatalog old = this.catalog;
		this.catalog = newCatalog();
		if (old != null) {
			old.close();
		}
		this.customPage.forget();
		this.campaigns = null;
	}

	void openGameData() {
		if (isFinishing()) {
			// A second tap before this window is gone would open the data folder window twice.
			return;
		}
		final Intent intent = new Intent(this, DataFolderActivity.class);
		intent.putExtra(DataFolderActivity.EXTRA_OPEN, true);
		startActivity(intent);
		finish();
	}
}
