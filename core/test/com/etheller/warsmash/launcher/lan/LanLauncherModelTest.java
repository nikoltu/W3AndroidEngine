package com.etheller.warsmash.launcher.lan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.input.touch.ControlScheme;
import com.etheller.warsmash.lan.LanSession;
import com.etheller.warsmash.lan.LanSessions;
import com.etheller.warsmash.launcher.LaunchGuard;
import com.etheller.warsmash.launcher.LaunchRequest;
import com.etheller.warsmash.launcher.MapIssue;

import net.warsmash.lan.LanGameSetup;
import net.warsmash.lan.RejoinKey;
import net.warsmash.lan.RejoinStore;
import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.maps.LanMapIndex;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.protocol.LanTimings;
import net.warsmash.lan.transport.DefaultLanNetwork;
import net.warsmash.lan.transport.LanNetwork;

/** The LAN pages' model hosting a game over loopback, with a plain client as the other player. */
class LanLauncherModelTest {
	@TempDir
	Path tmp;

	private final Queue<Runnable> posted = new ConcurrentLinkedQueue<>();
	private final List<LaunchRequest> launched = new CopyOnWriteArrayList<>();
	private LanSession guest;
	private volatile MapIssue mapIssueAnswer = MapIssue.NONE;
	private volatile boolean askedAboutNoMap;
	private LanLauncherModel model;

	private final LanNetwork network = new DefaultLanNetwork() {
		@Override
		public boolean isNetworkAvailable() {
			return true;
		}
	};

	private final RejoinStore keys = new RejoinStore() {
		private final java.util.Map<Long, RejoinKey> held = new java.util.concurrent.ConcurrentHashMap<>();

		@Override
		public RejoinKey load(final long gameId) {
			return this.held.get(gameId);
		}

		@Override
		public void save(final RejoinKey key) {
			this.held.put(key.gameId, key);
		}

		@Override
		public void clear(final long gameId) {
			this.held.remove(gameId);
		}
	};

	private final LanLauncherModel.Env env = new LanLauncherModel.Env() {
		private LanMapIndex index;

		@Override
		public LanNetwork network() {
			return LanLauncherModelTest.this.network;
		}

		@Override
		public synchronized LanMapIndex mapIndex() {
			if (this.index == null) {
				this.index = new LanMapIndex(null, LanLauncherModelTest.this.tmp.resolve("dl").toFile());
			}
			return this.index;
		}

		@Override
		public String playerName() {
			return "Thrall";
		}

		@Override
		public String portrait() {
			return "5";
		}

		@Override
		public int controlScheme() {
			return LanProtocol.CONTROL_TOUCH;
		}

		@Override
		public String engineVersion() {
			return "test";
		}

		@Override
		public String buildCommit() {
			return "abc";
		}

		@Override
		public boolean official() {
			return false;
		}

		@Override
		public long dataChecksum() {
			return 7;
		}

		@Override
		public Executor ui() {
			return LanLauncherModelTest.this.posted::add;
		}

		@Override
		public void launch(final LaunchRequest request) {
			LanLauncherModelTest.this.launched.add(request);
		}

		@Override
		public void mapIssue(final File map, final java.util.function.Consumer<MapIssue> done) {
			final MapIssue answer = LanLauncherModelTest.this.mapIssueAnswer;
			if (map == null) {
				LanLauncherModelTest.this.askedAboutNoMap = true;
			}
			LanLauncherModelTest.this.posted.add(() -> done.accept(answer));
		}

		@Override
		public boolean hasMapNamed(final String name) {
			return true;
		}

		@Override
		public RejoinStore rejoinStore() {
			return LanLauncherModelTest.this.keys;
		}

		@Override
		public boolean hostMigration() {
			return false;
		}
	};

	@AfterEach
	void tearDown() {
		if (this.guest != null) {
			this.guest.close();
		}
		if (this.model != null) {
			this.model.leave();
			this.model.pageHidden();
		}
	}

	private void until(final String what, final BooleanSupplier condition) throws Exception {
		final long end = System.currentTimeMillis() + 30000;
		while (System.currentTimeMillis() < end) {
			Runnable r;
			while ((r = this.posted.poll()) != null) {
				r.run();
			}
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(10);
		}
		throw new AssertionError("timed out waiting for " + what);
	}

	private File map() throws Exception {
		final byte[] bytes = new byte[2048];
		bytes[0] = 'H';
		bytes[1] = 'M';
		bytes[2] = '3';
		bytes[3] = 'W';
		bytes[512] = 'M';
		bytes[513] = 'P';
		bytes[514] = 'Q';
		bytes[515] = 0x1A;
		bytes[516] = 32;
		final File file = this.tmp.resolve("maps").resolve("Echo.w3x").toFile();
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), bytes);
		return file;
	}

	private LanLauncherModel.CreateSettings settings() throws Exception {
		final LanLauncherModel.CreateSettings s = new LanLauncherModel.CreateSettings();
		s.mapFile = map();
		s.map = this.env.mapIndex().describe(s.mapFile);
		s.maxSlots = 4;
		s.gameName = "Thrall's Game";
		s.port = 0;
		s.countdownMs = 300;
		return s;
	}

	private final List<LanMessages.LobbyState> guestLobbies = new CopyOnWriteArrayList<>();
	private final List<LanMessages.Chat> guestChats = new CopyOnWriteArrayList<>();
	private volatile LanGameSetup guestStart;
	private volatile LanClient guestClient;

	private void joinGuest(final int port) throws Exception {
		this.guest = new LanSession(new LanTimings());
		this.guest.addListener(new LanClient.Listener() {
			@Override
			public void onLobby(final LanMessages.LobbyState lobby) {
				LanLauncherModelTest.this.guestLobbies.add(lobby);
			}

			@Override
			public void onChat(final LanMessages.Chat chat) {
				LanLauncherModelTest.this.guestChats.add(chat);
			}

			@Override
			public void onStart(final LanGameSetup setup) {
				LanLauncherModelTest.this.guestStart = setup;
			}
		});
		final LanClient.Config c = new LanClient.Config();
		c.engineVersion = "test";
		c.buildCommit = "abc";
		c.dataChecksum = 7;
		c.playerName = "Jaina";
		c.events = Runnable::run;
		c.mapIndex = new LanMapIndex(null, this.tmp.resolve("guest").toFile()).addRoot(this.tmp.resolve("maps").toFile());
		this.guestClient = this.guest.connect(c, new InetSocketAddress("127.0.0.1", port));
	}

	private LanMessages.LobbyState lastGuestLobby() {
		return this.guestLobbies.isEmpty() ? null : this.guestLobbies.get(this.guestLobbies.size() - 1);
	}

	@Test
	void hostsALobbyAndStartsTheGame() throws Exception {
		this.model = new LanLauncherModel(this.env);
		this.model.showCreate();
		assertEquals(LanLauncherModel.Mode.CREATE, this.model.mode());
		assertNull(this.model.create(settings()));
		assertTrue(this.model.isHosting());
		until("the lobby", () -> this.model.mode() == LanLauncherModel.Mode.LOBBY && this.model.lobby() != null);
		assertTrue(this.model.lobby().iAmHost);
		assertEquals("Thrall's Game", this.model.lobby().options.gameName);
		assertEquals(4, this.model.lobby().rows.size());
		until("the host's map", () -> this.model.mapReady());
		assertEquals(0, this.model.lobby().readyHumans);
		assertFalse(this.model.lobby().canStart());
		assertEquals(LanLobbyView.HostAction.READY, this.model.lobby().hostAction());
		this.model.startGame();
		this.model.hostPrimary();
		until("the host's own mark", () -> this.model.lobby().readyHumans == 1);
		assertEquals(LanLauncherModel.Mode.LOBBY, this.model.mode());
		assertTrue(this.model.lobby().canStart());
		assertEquals(LanLobbyView.HostAction.START, this.model.lobby().hostAction());

		// While the lobby stands no other game starts, and the lobby's own game does.
		final LaunchRequest custom = new LaunchRequest();
		custom.customMap = "x";
		final LaunchGuard.Blocked blocked = LaunchGuard.check(custom);
		assertNotNull(blocked);
		assertTrue(blocked.hosting());
		assertSame(this.model.session(), blocked.lanSession);

		joinGuest(this.model.session().host().port());
		until("the guest in the lobby", () -> this.model.lobby().humans == 2);
		assertTrue(this.model.chat().stream().anyMatch(l -> l.system && l.text.equals("Jaina has joined the game.")));
		assertFalse(this.model.lobby().canStart());

		until("the guest's map", () -> this.guestClient != null && this.guestClient.mapState() == LanClient.MAP_READY);
		assertEquals(LanLobbyView.HostAction.NOT_READY, this.model.lobby().hostAction());
		this.guestClient.setReady(true);
		until("everybody ready", () -> this.model.lobby().canStart());
		assertEquals(LanLobbyView.HostAction.START, this.model.lobby().hostAction());
		this.guestClient.setReady(false);
		until("the guest not ready", () -> !this.model.lobby().canStart());
		assertEquals(LanLobbyView.HostAction.NOT_READY, this.model.lobby().hostAction());
		this.model.hostPrimary();
		until("the host not ready", () -> this.model.lobby().readyHumans == 0);
		assertEquals(LanLobbyView.HostAction.READY, this.model.lobby().hostAction());
		this.model.hostPrimary();
		this.guestClient.setReady(true);
		until("everybody ready again", () -> this.model.lobby().canStart());

		this.guestClient.chat(LanProtocol.CHAT_ALL, "glhf");
		until("the chat", () -> this.model.chat().stream().anyMatch(l -> !l.system && l.text.equals("glhf")));
		assertEquals("Jaina", this.model.chat().get(this.model.chat().size() - 1).from);

		this.model.startGame();
		until("the countdown", () -> this.model.mode() == LanLauncherModel.Mode.COUNTDOWN
				|| this.model.mode() == LanLauncherModel.Mode.LOADING);
		until("the loading", () -> this.model.mode() == LanLauncherModel.Mode.LOADING);
		until("the engine call", () -> !this.launched.isEmpty());
		final LaunchRequest request = this.launched.get(0);
		assertEquals(LaunchRequest.SOURCE_LAN, request.source());
		assertTrue(request.mapFile.endsWith("Echo.w3x"));
		assertNull(LaunchGuard.check(request));
		assertNotNull(this.guestStart);
		assertEquals(2, LanLauncherModel.slotsOf(this.model.setup()).split(";").length);
		assertTrue(LanLauncherModel.slotsOf(this.model.setup()).startsWith("0:USER:"));
	}

	@Test
	void theHostSetsAComputerAndChangesTheMapInTheLobby() throws Exception {
		this.model = new LanLauncherModel(this.env);
		final LanLauncherModel.CreateSettings first = settings();
		first.map.title = "Echo Isles";
		assertNull(this.model.create(first));
		until("the lobby", () -> this.model.lobby() != null);
		assertEquals("Echo Isles", this.model.lobby().mapLabel());
		assertTrue(this.model.lobby().title().contains("Echo Isles"));

		this.model.hostSlot(2, LanProtocol.ACTION_COMPUTER, 1);
		until("the computer", () -> this.model.lobby().rows.get(2).kind == LanLobbyView.COMPUTER);
		final LanLobbyView.Row computer = this.model.lobby().rows.get(2);
		assertTrue(computer.editable, "the host edits a computer's row");
		this.model.lobby().choose(this.model, computer, 3, 1, 9, 80);
		until("the setup", () -> this.model.lobby().rows.get(2).race == 3);
		assertEquals(9, this.model.lobby().rows.get(2).color);
		assertEquals(80, this.model.lobby().rows.get(2).handicap);

		final LanLauncherModel.CreateSettings second = settings();
		second.map.name = "Six.w3x";
		second.map.title = "";
		second.maxSlots = 6;
		second.syncWarning = true;
		assertNull(this.model.changeMap(second));
		until("six slots", () -> this.model.lobby().rows.size() == 6);
		assertEquals("Six.w3x", this.model.lobby().mapLabel(), "without its own name the file's shows");
		assertTrue(this.model.lobby().syncWarning);
		assertEquals(LanLobbyView.COMPUTER, this.model.lobby().rows.get(2).kind);

		final LanLauncherModel.CreateSettings none = new LanLauncherModel.CreateSettings();
		assertEquals(LanText.mapChangeFailed(), this.model.changeMap(none));
	}

	@Test
	void aLobbyIsNotMadeOnAMapTheEngineCannotPlay() throws Exception {
		this.model = new LanLauncherModel(this.env);
		final LanLauncherModel.CreateSettings lua = settings();
		lua.issue = MapIssue.LUA;
		assertEquals("This map uses a Lua script, and the Lua library is not available on this system.", this.model.create(lua));
		assertFalse(this.model.inSession());
		assertNull(LanSessions.current());

		assertNull(this.model.create(settings()));
		until("the lobby", () -> this.model.lobby() != null);
		final LanLauncherModel.CreateSettings sealed = settings();
		sealed.issue = MapIssue.PROTECTED;
		assertEquals(LanText.mapIssue(MapIssue.PROTECTED), this.model.changeMap(sealed));
		assertEquals(LanLauncherModel.Mode.LOBBY, this.model.mode());
	}

	@Test
	void aPlayerCannotReadyOnAMapTheEngineCannotPlay() throws Exception {
		this.mapIssueAnswer = MapIssue.LUA;
		this.model = new LanLauncherModel(this.env);
		assertNull(this.model.create(settings()));
		until("the map's issue", () -> this.model.mapIssue() == MapIssue.LUA);
		assertTrue(this.model.mapReady());
		assertFalse(this.model.canReady());
		assertTrue(this.model.chat().stream()
				.anyMatch(l -> l.system && l.text.equals(LanText.mapIssue(MapIssue.LUA))));
		assertEquals(LanText.mapIssue(MapIssue.LUA), this.model.takeMapFailure());
		assertEquals(LanText.mapIssueTitle(), this.model.mapFailureTitle());
		assertNull(this.model.takeMapFailure());
		this.model.setReady(true);
		assertEquals(0, this.model.lobby().readyHumans);
		assertTrue(this.launched.isEmpty());
	}

	@Test
	void aReadyMapWithoutAFileIsNotAskedAbout() throws Exception {
		this.model = new LanLauncherModel(this.env);
		assertNull(this.model.create(settings()));
		until("the lobby", () -> this.model.lobby() != null);
		final java.lang.reflect.Field field = LanLauncherModel.class.getDeclaredField("listener");
		field.setAccessible(true);
		final LanClient.Listener listener = (LanClient.Listener) field.get(this.model);
		listener.onMapState(LanClient.MAP_DOWNLOADING, null, null, 0);
		this.askedAboutNoMap = false;
		listener.onMapState(LanClient.MAP_READY, null, null, 0);
		until("the posted answers", () -> this.posted.isEmpty());
		assertFalse(this.askedAboutNoMap);
		assertEquals(MapIssue.NONE, this.model.mapIssue());
	}

	@Test
	void aMapCheckThatGetsNoAnswerKeepsThePlayerFromReadyingWithAMessage() throws Exception {
		this.mapIssueAnswer = null;
		this.model = new LanLauncherModel(this.env);
		assertNull(this.model.create(settings()));
		final String text = LanText.mapFailed(net.warsmash.lan.client.MapDownload.FAIL_IO);
		until("the refusal", () -> this.model.chat().stream().anyMatch(l -> l.system && l.text.equals(text)));
		assertFalse(this.model.canReady());
		assertEquals(text, this.model.takeMapFailure());
		assertEquals(LanText.mapFailedTitle(), this.model.mapFailureTitle());
	}

	@Test
	void aMapTheLauncherRefusesLeavesTheLobbyWithItsReason() throws Exception {
		this.model = new LanLauncherModel(this.env);
		assertNull(this.model.create(settings()));
		until("the lobby", () -> this.model.lobby() != null);
		this.model.launchRefused(MapIssue.LUA);
		assertFalse(this.model.inSession());
		assertNull(LanSessions.current());
		assertEquals(LanText.mapIssue(MapIssue.LUA), this.model.takeError());
	}

	@Test
	void aMapIsToldFromTheHostByItsContent() throws Exception {
		final File file = map();
		final LanData.MapInfo info = this.env.mapIndex().describe(file);
		assertFalse(info.sha256.length == 0);
		this.env.mapIndex().addRoot(file.getParentFile());
		assertNotNull(this.env.mapIndex().find(info));
		final LanData.MapInfo other = new LanData.MapInfo();
		other.name = info.name;
		other.size = info.size;
		other.sha256 = new byte[32];
		other.crc32 = 5;
		assertNull(this.env.mapIndex().find(other), "a map of the same name and another content is not the map");
	}

	@Test
	void theControlSchemeReachesTheOthersAtOnce() throws Exception {
		this.model = new LanLauncherModel(this.env);
		assertNull(this.model.create(settings()));
		until("the lobby", () -> this.model.lobby() != null);
		joinGuest(this.model.session().host().port());
		until("the guest sees the lobby", () -> lastGuestLobby() != null && lastGuestLobby().slots.get(1).kind
				== LanProtocol.SLOT_HUMAN);
		assertEquals(LanProtocol.CONTROL_TOUCH, lastGuestLobby().slots.get(0).controlScheme);
		LanSessions.onControlSchemeChanged(ControlScheme.MOUSE_KEYBOARD);
		until("the host's mark", () -> lastGuestLobby().slots.get(0).controlScheme == LanProtocol.CONTROL_KBM);
		until("the model's own view", () -> this.model.lobby().rows.get(0).keyboardAndMouse);
		assertTrue(this.model.chat().stream().anyMatch(l -> l.text.equals("Thrall now uses keyboard and mouse.")));
	}

	@Test
	void changesWhileAnotherPageIsShownMakeTheButtonFlash() throws Exception {
		this.model = new LanLauncherModel(this.env);
		assertNull(this.model.create(settings()));
		until("the lobby", () -> this.model.lobby() != null);
		this.model.pageShown();
		this.model.pageHidden();
		assertFalse(this.model.attention().isUnseen());
		joinGuest(this.model.session().host().port());
		until("the guest", () -> this.model.lobby().humans == 2);
		assertTrue(this.model.attention().isUnseen());
		this.model.pageShown();
		assertFalse(this.model.attention().isUnseen());
	}

	@Test
	void leavingEndsTheSessionAndFreesTheGuard() throws Exception {
		this.model = new LanLauncherModel(this.env);
		assertNull(this.model.create(settings()));
		until("the lobby", () -> this.model.lobby() != null);
		assertNotNull(LanSessions.current());
		this.model.leave();
		assertNull(LanSessions.current());
		assertFalse(this.model.inSession());
		assertEquals(LanLauncherModel.Mode.JOIN, this.model.mode());
	}

	@Test
	void leavingDoesNotHangOnTheLateDisconnect() throws Exception {
		this.model = new LanLauncherModel(this.env);
		assertNull(this.model.create(settings()));
		until("the lobby", () -> (this.model.lobby() != null) && (this.model.lobby().mine != null));
		assertEquals(5, this.model.lobby().mine.portrait);
		this.model.leave();
		// the closed session's last word reaches the page's thread after the loop has stopped
		org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
			Runnable r;
			while ((r = this.posted.poll()) != null) {
				r.run();
			}
		});
	}

	@Test
	void addressesAreParsed() {
		assertEquals(new InetSocketAddress("192.168.1.40", 6112), LanLauncherModel.parseAddress("192.168.1.40"));
		assertEquals(new InetSocketAddress("192.168.1.40", 7000), LanLauncherModel.parseAddress(" 192.168.1.40:7000 "));
		assertNull(LanLauncherModel.parseAddress("192.168.1"));
		assertNull(LanLauncherModel.parseAddress("192.168.1.256"));
		assertNull(LanLauncherModel.parseAddress("192.168.1.4:0"));
		assertNull(LanLauncherModel.parseAddress("192.168.1.4:70000"));
		assertNull(LanLauncherModel.parseAddress("host.local"));
		assertNull(LanLauncherModel.parseAddress(null));
	}

	@Test
	void aBadAddressIsSaidInTheDialog() {
		this.model = new LanLauncherModel(this.env);
		this.model.joinByIp("nonsense");
		assertEquals(LanText.badAddress(), this.model.ipError());
		assertFalse(this.model.inSession());
	}

	@Test
	void textsMatchTheMockup() {
		assertEquals("Thrall's Game", LanText.defaultGameName("Thrall"));
		assertEquals(31, LanText.defaultGameName("AVeryLongProfileNameIndeed12345").length());
		assertEquals("Map from host · 63 % · 1.9 / 3.1 MB",
				LanText.mapProgress((long) (1.95 * 1024 * 1024), (int) (3.1 * 1024 * 1024)));
		final LanMessages.Reject version = new LanMessages.Reject(LanProtocol.REJECT_VERSION, "");
		version.hostVersion = "v0.4.3";
		assertEquals("Different version (host v0.4.3)", LanText.reject(version));
		assertEquals("Game is full", LanText.reject(new LanMessages.Reject(LanProtocol.REJECT_FULL, "")));
		assertEquals("Game has started", LanText.reject(new LanMessages.Reject(LanProtocol.REJECT_STARTED, "")));
		assertEquals("Thrall has left. Jaina is now the host.", LanText.newHost("Thrall", "Jaina"));
		assertEquals("You are in a LAN lobby. Leave the lobby first.", LanText.blockText(false));
		assertEquals("You are hosting a LAN game. Leave the lobby first.", LanText.blockText(true));
		final List<String> lines = new ArrayList<>();
		lines.add(LanText.tag(true, false, false, null));
		lines.add(LanText.tag(false, true, false, null));
		lines.add(LanText.tag(false, false, true, "v0.4.1"));
		assertEquals("↻ in progress · Rejoin", lines.get(0));
		assertEquals("⇩ map from host", lines.get(1));
		assertEquals("other version (v0.4.1)", lines.get(2));
		final LanData.Options o = new LanData.Options();
		assertEquals("None", LanText.observers(o.observers));
	}
}
