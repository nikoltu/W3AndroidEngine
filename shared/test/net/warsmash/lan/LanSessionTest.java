package net.warsmash.lan;

import static net.warsmash.lan.LanTestSupport.assertConsistent;
import static net.warsmash.lan.LanTestSupport.command;
import static net.warsmash.lan.LanTestSupport.waitFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.warsmash.lan.LanTestSupport.Node;
import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.host.LanHost;
import net.warsmash.lan.protocol.ByteWriter;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanProtocol;

class LanSessionTest {
	private final LanTestSupport env = new LanTestSupport();

	@AfterEach
	void tearDown() throws Exception {
		this.env.close();
		waitFor("network threads to end", 3000, () -> LanTestSupport.liveLanThreads().isEmpty());
	}

	/** Sends commands from a background thread at a ragged pace, like a player clicking. */
	static Thread sender(final Node node, final int index, final int count, final long seed) {
		final Thread t = new Thread(() -> {
			final Random r = new Random(seed);
			for (int i = 1; i <= count; i++) {
				node.client.sendCommand(command(index, i));
				if (r.nextInt(4) == 0) {
					try {
						Thread.sleep(r.nextInt(3));
					}
					catch (final InterruptedException e) {
						return;
					}
				}
			}
		}, "test-sender-" + index);
		t.start();
		return t;
	}

	/**
	 * Reads until the server closes the stream; a close with unread data may end it with a reset instead. The host
	 * keeps sending keepalives meanwhile, so anything but the end is skipped. Returns -1 at the end.
	 */
	static int readToEnd(final Socket s) throws IOException {
		try {
			int b;
			while ((b = s.getInputStream().read()) != -1) {
				// keepalive bytes
			}
			return b;
		}
		catch (final java.net.SocketException e) {
			return -1;
		}
	}

	static void settle(final int commands, final Node... nodes) {
		waitFor("all commands in turns", 20000, () -> {
			for (final Node n : nodes) {
				if (n.client.pendingCommands() != 0) {
					return false;
				}
			}
			return true;
		});
		final int target = nodes[0].client.lastTurn() + 30;
		for (final Node n : nodes) {
			waitFor("turns", 10000, () -> n.client.lastTurn() >= target);
		}
	}

	@Test
	void lobbyStartAndTurnsWithCommands() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("Thrall");
		final Node b = this.env.joinAndWait("Jaina");
		final Node c = this.env.joinAndWait("Arthas");
		assertEquals(3, this.env.host.participantCount());
		assertEquals(1, a.rec.welcome.ownerId);
		assertEquals(LanProtocol.SLOT_HUMAN, c.rec.welcome.lobby.slots.get(2).kind);

		b.client.slotRequest(3, 2, 1, 7, 60);
		waitFor("slot change", () -> {
			final LanData.SlotInfo s = a.rec.lobby == null ? null : a.rec.lobby.slots.get(3);
			return s != null && s.kind == LanProtocol.SLOT_HUMAN && s.color == 7 && s.handicap == 60 && s.race == 2;
		});
		assertEquals(LanProtocol.SLOT_OPEN, a.rec.lobby.slots.get(1).kind);

		a.client.chat(LanProtocol.CHAT_ALL, "hello");
		waitFor("chat", () -> !c.rec.chats.isEmpty());
		assertEquals("hello", c.rec.chats.get(0).text);
		assertEquals(1, c.rec.chats.get(0).from);

		this.env.startGame(a, b, c);
		assertEquals(LanProtocol.STATE_IN_GAME, this.env.host.state());
		assertNotNull(a.rec.setup);
		assertEquals(a.rec.setup.gameId, c.rec.setup.gameId);
		assertEquals(a.rec.setup.seed, c.rec.setup.seed);
		assertEquals(3, a.rec.setup.hostOrder.length);

		final int perClient = 150;
		final Thread[] senders = { sender(a, 0, perClient, 1), sender(b, 1, perClient, 2),
				sender(c, 2, perClient, 3) };
		for (final Thread t : senders) {
			t.join();
		}
		settle(perClient, a, b, c);
		assertConsistent(perClient, a, b, c);
		assertTrue(a.rec.turns.size() > 20);
	}

	@Test
	void everyoneLeavingEndsTheGameAndClosesEverything() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		this.env.startGame(a, b);
		waitFor("some turns", () -> a.client.lastTurn() > 5);
		a.client.leave(LanProtocol.LEAVE_QUIT);
		b.client.leave(LanProtocol.LEAVE_QUIT);
		waitFor("game end", () -> this.env.host.state() == LanProtocol.STATE_ENDED);
		waitFor("end notice", () -> a.rec.disconnects.size() > 0 && b.rec.disconnects.size() > 0);
		int left = 0;
		for (final LanData.TurnData t : this.env.host.archive().range(0, Integer.MAX_VALUE)) {
			for (final LanData.PresenceEntry p : t.presence) {
				if (p.state == LanProtocol.PRESENCE_LEFT) {
					left++;
				}
			}
		}
		assertEquals(2, left);
	}

	@Test
	void handshakeRejectsWhatDoesNotMatch() throws Exception {
		final LanHost.Config cfg = LanTestSupport.hostConfig();
		cfg.maxSlots = 1;
		this.env.startHost(cfg);

		final LanClient.Config wrongVersion = LanTestSupport.clientConfig("V");
		wrongVersion.engineVersion = "other";
		final Node v = this.env.join(wrongVersion);
		waitFor("version reject", () -> !v.rec.rejects.isEmpty());
		assertEquals(LanProtocol.REJECT_VERSION, v.rec.rejects.get(0).reason);
		assertEquals(LanTestSupport.VERSION, v.rec.rejects.get(0).hostVersion);

		final LanClient.Config wrongData = LanTestSupport.clientConfig("D");
		wrongData.dataChecksum = 99;
		final Node d = this.env.join(wrongData);
		waitFor("data reject", () -> !d.rec.rejects.isEmpty());
		assertEquals(LanProtocol.REJECT_DATA, d.rec.rejects.get(0).reason);

		final LanClient.Config blank = LanTestSupport.clientConfig("  ");
		final Node bl = this.env.join(blank);
		waitFor("name reject", () -> !bl.rec.rejects.isEmpty());
		assertEquals(LanProtocol.REJECT_BAD_NAME, bl.rec.rejects.get(0).reason);

		this.env.joinAndWait("First");
		final Node full = this.env.join("Second");
		waitFor("full reject", () -> !full.rec.rejects.isEmpty());
		assertEquals(LanProtocol.REJECT_FULL, full.rec.rejects.get(0).reason);

		assertEquals(1, this.env.host.participantCount());
	}

	@Test
	void observersAndRefereesFollowTheOption() throws Exception {
		final LanHost.Config cfg = LanTestSupport.hostConfig();
		cfg.options.observers = LanProtocol.OBSERVERS_OBSERVERS;
		this.env.startHost(cfg);
		final Node p = this.env.joinAndWait("Player");
		final LanClient.Config ref = LanTestSupport.clientConfig("Ref");
		ref.wantedRole = LanProtocol.ROLE_REFEREE;
		final Node r = this.env.join(ref);
		waitFor("referee reject", () -> !r.rec.rejects.isEmpty());
		final LanClient.Config obs = LanTestSupport.clientConfig("Obs");
		obs.wantedRole = LanProtocol.ROLE_OBSERVER;
		final Node o = this.env.join(obs);
		waitFor("observer welcome", () -> o.rec.welcome != null);
		waitFor("observer listed", () -> p.rec.lobby.observers.size() == 1);
		assertEquals("Obs", p.rec.lobby.observers.get(0).name);
		this.env.startGame(p);
	}

	@Test
	void garbageAndSilenceNeverHurtTheHost() throws Exception {
		this.env.startHost();
		// silent connection: hello timeout
		final Socket silent = new Socket("127.0.0.1", this.env.port);
		silent.setSoTimeout((int) LanTestSupport.scaled(5000));
		assertEquals(-1, readToEnd(silent));
		silent.close();
		// oversize frame
		final Socket big = new Socket("127.0.0.1", this.env.port);
		big.setSoTimeout((int) LanTestSupport.scaled(5000));
		final DataOutputStream out = new DataOutputStream(big.getOutputStream());
		out.writeByte(LanProtocol.HELLO);
		out.writeInt(10_000_000);
		out.flush();
		assertEquals(-1, readToEnd(big));
		big.close();
		// random bytes
		final Socket noise = new Socket("127.0.0.1", this.env.port);
		noise.setSoTimeout((int) LanTestSupport.scaled(5000));
		final byte[] junk = new byte[500];
		new Random(5).nextBytes(junk);
		noise.getOutputStream().write(junk);
		noise.getOutputStream().flush();
		assertEquals(-1, readToEnd(noise));
		noise.close();
		// HELLO of a future protocol version gets an answer instead of silence
		final Socket future = new Socket("127.0.0.1", this.env.port);
		future.setSoTimeout((int) LanTestSupport.scaled(5000));
		final ByteWriter w = new ByteWriter(100);
		w.u8(LanProtocol.HELLO).i32(6).i32(LanProtocol.MAGIC).u16(77);
		future.getOutputStream().write(w.toByteArray());
		future.getOutputStream().flush();
		assertEquals(LanProtocol.REJECT, future.getInputStream().read());
		future.close();
		// the host still works
		final Node ok = this.env.joinAndWait("Fine");
		assertNotNull(ok.rec.welcome);
	}

	@Test
	void startNeedsEveryoneReadyAndOnlyTheOwnerStarts() throws Exception {
		final LanHost.Config cfg = LanTestSupport.hostConfig();
		cfg.countdownMs = 300;
		this.env.startHost(cfg);
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		a.client.setReady(true);
		a.client.requestStart();
		b.client.setReady(true);
		waitFor("both ready", () -> {
			int ready = 0;
			for (final LanData.SlotInfo s : a.rec.lobby.slots) {
				if (s.ready) {
					ready++;
				}
			}
			return ready == 2;
		});
		assertEquals(LanProtocol.STATE_LOBBY, this.env.host.state());
		b.client.requestStart(); // not the owner
		Thread.sleep(50);
		assertEquals(LanProtocol.STATE_LOBBY, this.env.host.state());
		a.client.requestStart();
		waitFor("countdown", () -> !b.rec.countdowns.isEmpty());
		a.client.cancelCountdown();
		Thread.sleep(500);
		assertEquals(LanProtocol.STATE_LOBBY, this.env.host.state());
		a.client.requestStart();
		waitFor("go", () -> a.rec.go && b.rec.go);
	}

	@Test
	void changingTheOptionsClearsReadyAndLateLobbyMessagesAreHarmless() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		a.client.setReady(true);
		waitFor("ready", () -> a.rec.lobby.slots.get(0).ready);
		final LanData.Options o = a.rec.lobby.options.copy();
		o.gameName = "Renamed";
		a.client.hostOptions(o);
		waitFor("options", () -> a.rec.lobby.options.gameName.equals("Renamed") && !a.rec.lobby.slots.get(0).ready);
		this.env.startGame(a);
		a.client.setReady(true);
		a.client.slotRequest(0, 1, 1, 1, 100);
		Thread.sleep(100);
		assertTrue(a.client.isConnected());
	}

	@Test
	void kickedPlayerCannotComeBack() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		a.client.hostSlot(1, LanProtocol.ACTION_KICK, 0);
		waitFor("kick", () -> !b.rec.kicks.isEmpty());
		waitFor("lobby shrinks", () -> a.rec.lobby.slots.get(1).kind == LanProtocol.SLOT_OPEN);
		final Node again = this.env.join("B2");
		waitFor("reject", () -> !again.rec.rejects.isEmpty());
		assertEquals(LanProtocol.REJECT_KICKED, again.rec.rejects.get(0).reason);
	}

	@Test
	void computersAndClosedSlotsAreOwnerOnly() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		b.client.hostSlot(2, LanProtocol.ACTION_COMPUTER, 2); // not the owner: ignored
		a.client.hostSlot(3, LanProtocol.ACTION_COMPUTER, 2);
		a.client.hostSlot(2, LanProtocol.ACTION_CLOSE, 0);
		waitFor("slot kinds", () -> a.rec.lobby.slots.get(3).kind == LanProtocol.SLOT_COMPUTER
				&& a.rec.lobby.slots.get(2).kind == LanProtocol.SLOT_CLOSED);
		assertEquals(2, a.rec.lobby.slots.get(3).computerLevel);
	}

	@Test
	void theOwnerSetsAComputersRaceTeamAndColor() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		a.client.hostSlot(3, LanProtocol.ACTION_COMPUTER, 1);
		waitFor("computer", () -> a.rec.lobby.slots.get(3).kind == LanProtocol.SLOT_COMPUTER);
		b.client.hostSetup(3, 2, 0, 6, 100); // not the owner: ignored
		a.client.hostSetup(3, 3, 0, 6, 70);
		waitFor("setup", () -> a.rec.lobby.slots.get(3).race == 3);
		final LanData.SlotInfo s = a.rec.lobby.slots.get(3);
		assertEquals(0, s.team);
		assertEquals(6, s.color);
		assertEquals(70, s.handicap);
		a.client.hostSetup(1, 2, 0, 6, 100); // a player's slot is its own to set
		a.client.hostSetup(3, 1, 2, 1, 100); // color 1 is taken by the player in slot 1: the color stays
		waitFor("second setup", () -> a.rec.lobby.slots.get(3).race == 1);
		assertEquals(6, a.rec.lobby.slots.get(3).color);
		assertEquals(1, b.rec.lobby.slots.get(1).color);
	}

	@Test
	void twoPlayersAskingForOneColorGetOneEach() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		a.client.slotRequest(0, 0, 0, 9, 100);
		b.client.slotRequest(1, 0, 1, 9, 100);
		waitFor("one got it", () -> (a.rec.lobby.slots.get(0).color == 9) != (a.rec.lobby.slots.get(1).color == 9));
		a.client.hostSlot(3, LanProtocol.ACTION_COMPUTER, 1);
		a.client.hostSetup(3, 0, 3, 9, 100);
		waitFor("computer", () -> a.rec.lobby.slots.get(3).kind == LanProtocol.SLOT_COMPUTER);
		for (int i = 0; i < 4; i++) {
			for (int j = i + 1; j < 4; j++) {
				final LanData.SlotInfo x = a.rec.lobby.slots.get(i);
				final LanData.SlotInfo y = a.rec.lobby.slots.get(j);
				if (x.kind != LanProtocol.SLOT_OPEN && y.kind != LanProtocol.SLOT_OPEN) {
					assertNotEquals(x.color, y.color);
				}
			}
		}
	}

	@Test
	void theOwnerChangesTheMapAfterTheLobbyIsMade() throws Exception {
		final LanHost host = this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		a.client.hostSlot(3, LanProtocol.ACTION_COMPUTER, 0);
		a.client.setReady(true);
		waitFor("computer", () -> a.rec.lobby.slots.get(3).kind == LanProtocol.SLOT_COMPUTER);
		final LanData.MapInfo bigger = new LanData.MapInfo();
		bigger.name = "Maps\\Six.w3x";
		bigger.title = "Six Players";
		bigger.size = 2000;
		assertTrue(host.changeMap(bigger, null, 6, true));
		waitFor("six slots", () -> b.rec.lobby.slots.size() == 6 && "Six Players".equals(b.rec.lobby.map.title));
		assertTrue(b.rec.lobby.syncWarning);
		assertEquals(LanProtocol.SLOT_COMPUTER, b.rec.lobby.slots.get(3).kind);
		assertFalse(a.rec.lobby.slots.get(0).ready, "a new map needs everyone ready again");

		b.client.slotRequest(5, 0, 5, 5, 100);
		waitFor("b in slot 5", () -> b.rec.lobby.slots.get(5).kind == LanProtocol.SLOT_HUMAN);
		final Node c = this.env.joinAndWait("C");
		waitFor("c in slot 1", () -> a.rec.lobby.slots.get(1).kind == LanProtocol.SLOT_HUMAN);
		final LanData.MapInfo two = new LanData.MapInfo();
		two.name = "Maps\\Two.w3x";
		two.size = 1000;
		assertFalse(host.changeMap(two, null, 2, false), "three players do not fit in two slots");
		assertEquals(6, b.rec.lobby.slots.size());
		a.client.hostSlot(1, LanProtocol.ACTION_KICK, 0);
		waitFor("c gone", () -> a.rec.lobby.slots.get(1).kind == LanProtocol.SLOT_OPEN);
		assertTrue(host.changeMap(two, null, 2, false));
		waitFor("two slots", () -> b.rec.lobby.slots.size() == 2);
		assertEquals(LanProtocol.SLOT_HUMAN, b.rec.lobby.slots.get(1).kind, "the player moved to the free slot");
		assertEquals(b.client.participantId(), b.rec.lobby.slots.get(1).participantId);
	}

	@Test
	void invalidCommandsFromTheSameClientEndTheConnection() throws Exception {
		final LanHost.Config cfg = LanTestSupport.hostConfig();
		cfg.commandValidator = (slot, bytes) -> bytes.length > 0 && bytes[0] != 99;
		this.env.startHost(cfg);
		final Node a = this.env.joinAndWait("A");
		this.env.startGame(a);
		for (int i = 0; i < 3; i++) {
			a.client.sendCommand(new byte[] { 99 });
		}
		waitFor("connection closed", () -> !a.rec.disconnects.isEmpty());
	}

	@Test
	void turnArchiveOnDiskMatchesWhatClientsSaw() throws Exception {
		final LanHost.Config cfg = LanTestSupport.hostConfig();
		final java.io.File f = java.io.File.createTempFile("lan-archive", ".bin");
		f.deleteOnExit();
		cfg.archiveFile = f;
		this.env.startHost(cfg);
		final Node a = this.env.joinAndWait("A");
		this.env.startGame(a);
		final Thread t = sender(a, 0, 40, 9);
		t.join();
		settle(40, a);
		this.env.host.close();
		waitFor("host closed", () -> !a.rec.disconnects.isEmpty());
		final int seen = a.rec.turns.size();
		final TurnArchive reloaded = TurnArchive.recover(f);
		try {
			assertTrue(reloaded.size() >= seen);
			for (int i = 0; i < seen; i++) {
				assertTrue(java.util.Arrays.equals(reloaded.get(i).toBytes(), a.rec.turns.get(i).toBytes()));
			}
		}
		finally {
			reloaded.close();
			f.delete();
		}
	}
}
