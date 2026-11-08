/**
 * <PRE>
 *
 * Copyright Tony Bringardner 2026 <A href="http://bringardner.com/tony">Tony Bringardner</A>
 *
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 *
 *
 *	@author Tony Bringardner
 *
 *
 */
package us.bringardner.parley.irc.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.irc.protocol.IrcMessage;

/**
 * Stage 1 step (b): channels and messages.
 */
class ChannelsTest extends ServerTestBase {

	/** JOIN and read up to the end of the NAMES reply; @return what came */
	private static List<IrcMessage> join(TestClient c, String channel) throws IOException {
		c.send("JOIN "+channel);
		return c.readUntil(m -> m.is("366") || m.is("403") || m.is("405") || m.is("471") || m.is("473") || m.is("474") || m.is("475"));
	}

	/** Read up to the answer of a PING, so nothing sent before it is still to come; @return what came */
	private static List<IrcMessage> sync(TestClient c) throws IOException {
		c.send("PING sync");
		List<IrcMessage> got = c.readUntil(m -> m.is("PONG") && "sync".equals(m.getLastParam()));
		got.remove(got.size()-1);
		return got;
	}

	private static boolean has(List<IrcMessage> got, String command) {
		return got.stream().anyMatch(m -> m.is(command));
	}

	private static String names(List<IrcMessage> got) {
		StringBuilder ret = new StringBuilder();
		for (IrcMessage m : got) {
			if( m.is("353") ) {
				ret.append(ret.length() == 0 ? "" : " ").append(m.getLastParam());
			}
		}
		return ret.toString();
	}

	@Test
	void joinMakesTheChannelAndItsOperator() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient alice = register(s, "alice");
		List<IrcMessage> got = join(alice, "#Parley");
		IrcMessage echo = got.get(0);
		assertTrue(echo.is("JOIN"), got.toString());
		assertEquals("alice", echo.getPrefix().getNickname());
		assertEquals("#Parley", echo.getParam(0));
		IrcMessage names = got.stream().filter(m -> m.is("353")).findFirst().get();
		assertEquals("=", names.getParam(1));
		assertEquals("@alice", names.getLastParam());
		assertNotNull(s.getNode().findChannel("#parley"));
		assertEquals(1, s.getNode().getChannelCount());

		TestClient bob = register(s, "bob");
		got = join(bob, "#PARLEY");
		// the channel keeps the name it was made with
		assertEquals("#Parley", got.get(0).getParam(0));
		assertEquals("@alice bob", names(got));
		IrcMessage seen = alice.readCommand("JOIN");
		assertEquals("bob", seen.getPrefix().getNickname());

		// joining again does nothing
		bob.send("JOIN #parley");
		assertFalse(has(sync(bob), "JOIN"));
	}

	@Test
	void badChannelNames() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient c = register(s, "tony");
		for (String bad : new String[] {"parley", "+modeless", "!ABCDEsafe", "#bell\u0007"}) {
			c.send("JOIN "+bad);
			IrcMessage m = c.readUntil(x -> x.is("403")).get(0);
			assertTrue(m.is("403"), bad+": "+m);
		}
		assertEquals(0, s.getNode().getChannelCount());
		// '&' channels are this server's
		assertEquals("&local", join(c, "&local").get(0).getParam(0));
	}

	@Test
	void messagesToChannelsAndUsers() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient alice = register(s, "alice");
		TestClient bob = register(s, "bob");
		TestClient carol = register(s, "carol");
		join(alice, "#p");
		join(bob, "#p");
		alice.readCommand("JOIN");

		alice.send("PRIVMSG #p :hello all");
		IrcMessage m = bob.readCommand("PRIVMSG");
		assertEquals("alice", m.getPrefix().getNickname());
		assertEquals("#p", m.getParam(0));
		assertEquals("hello all", m.getParam(1));
		// the sender doesn't get it back
		assertFalse(has(sync(alice), "PRIVMSG"));

		// a user outside the channel may send unless +n
		carol.send("NOTICE #p :from outside");
		assertEquals("from outside", alice.readCommand("NOTICE").getLastParam());

		// to a nickname, any case; several targets
		bob.send("PRIVMSG ALICE,carol :hi");
		assertEquals("alice", alice.readCommand("PRIVMSG").getParam(0));
		assertEquals("carol", carol.readCommand("PRIVMSG").getParam(0));

		bob.send("PRIVMSG nobody :x");
		assertEquals("nobody", bob.readCommand("401").getParam(1));
		bob.send("PRIVMSG #none :x");
		bob.readCommand("401");
		bob.send("PRIVMSG");
		bob.readCommand("411");
		bob.send("PRIVMSG alice");
		bob.readCommand("412");
		// NOTICE never gets an error back
		bob.send("NOTICE nobody :x");
		bob.send("NOTICE #none :x");
		bob.send("NOTICE");
		List<IrcMessage> got = sync(bob);
		assertTrue(got.isEmpty(), got.toString());
	}

	@Test
	void noExternalAndModerated() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient alice = register(s, "alice");
		TestClient bob = register(s, "bob");
		TestClient carol = register(s, "carol");
		join(alice, "#p");
		join(bob, "#p");
		alice.send("MODE #p +nm");
		assertEquals("+nm", alice.readCommand("MODE").getParam(1));
		bob.readCommand("MODE");

		carol.send("PRIVMSG #p :from outside");
		assertEquals("#p", carol.readCommand("404").getParam(1));
		bob.send("PRIVMSG #p :unvoiced");
		bob.readCommand("404");
		// the operator may speak; voice lets bob speak
		alice.send("MODE #p +v bob");
		IrcMessage mode = bob.readCommand("MODE");
		assertEquals("+v", mode.getParam(1));
		assertEquals("bob", mode.getParam(2));
		bob.send("PRIVMSG #p :voiced");
		assertEquals("voiced", alice.readCommand("PRIVMSG").getLastParam());
	}

	@Test
	void partAndTheEmptyChannelGoes() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient alice = register(s, "alice");
		TestClient bob = register(s, "bob");
		join(alice, "#p");
		join(bob, "#p");
		bob.send("PART #p :see you");
		IrcMessage part = alice.readCommand("PART");
		assertEquals("bob", part.getPrefix().getNickname());
		assertEquals("see you", part.getParam(1));
		assertEquals("#p", bob.readCommand("PART").getParam(0));
		bob.send("PART #p");
		assertEquals("#p", bob.readCommand("442").getParam(1));
		bob.send("PART #nowhere");
		bob.readCommand("403");

		alice.send("PART #p");
		alice.readCommand("PART");
		assertNull(s.getNode().findChannel("#p"));
		assertEquals(0, s.getNode().getChannelCount());
		// made again by the next JOIN, with a new operator
		assertEquals("@bob", names(join(bob, "#p")));
	}

	@Test
	void joinZeroLeavesEveryChannel() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient alice = register(s, "alice");
		join(alice, "#a");
		join(alice, "#b");
		alice.send("JOIN 0");
		alice.readCommand("PART");
		alice.readCommand("PART");
		assertEquals(0, s.getNode().getChannelCount());
		assertTrue(s.getNode().findUser("alice").getChannels().isEmpty());
	}

	@Test
	void tooManyChannels() throws IOException {
		Properties p = nodeSettings();
		p.setProperty(IrcNode.PROPERTY_MAX_CHANNELS, "2");
		IrcServer s = start(p);
		TestClient c = connect(s);
		c.send("NICK alice");
		c.send("USER alice 0 * :A");
		List<IrcMessage> welcome = c.readUntil(m -> m.is("422"));
		assertTrue(welcome.stream().anyMatch(m -> m.is("005") && m.getParams().contains("CHANLIMIT=#&:2")), welcome.toString());
		join(c, "#a");
		join(c, "#b");
		assertTrue(has(join(c, "#c"), "405"));
		assertNull(s.getNode().findChannel("#c"));
	}

	@Test
	void topic() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient alice = register(s, "alice");
		TestClient bob = register(s, "bob");
		join(alice, "#p");
		alice.send("TOPIC #p");
		alice.readCommand("331");
		bob.send("TOPIC #p :not a member");
		bob.readCommand("442");

		join(bob, "#p");
		bob.send("TOPIC #p :Parley talk");
		IrcMessage t = alice.readCommand("TOPIC");
		assertEquals("bob", t.getPrefix().getNickname());
		assertEquals("Parley talk", t.getParam(1));
		bob.readCommand("TOPIC");
		alice.send("TOPIC #p");
		assertEquals("Parley talk", alice.readCommand("332").getLastParam());

		// +t: operators only; a later JOIN gets the topic
		alice.send("MODE #p +t");
		bob.readCommand("MODE");
		bob.send("TOPIC #p :mine");
		bob.readCommand("482");
		TestClient carol = register(s, "carol");
		List<IrcMessage> got = join(carol, "#p");
		assertTrue(got.stream().anyMatch(m -> m.is("332") && m.getLastParam().equals("Parley talk")), got.toString());
	}

	@Test
	void channelModes() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient alice = register(s, "alice");
		TestClient bob = register(s, "bob");
		join(alice, "#p");
		join(bob, "#p");

		bob.send("MODE #p +s");
		assertEquals("#p", bob.readCommand("482").getParam(1));
		bob.send("MODE #p");
		IrcMessage is = bob.readCommand("324");
		assertEquals("+", is.getParam(2));

		alice.send("MODE #p +kl-x secret 5");
		assertEquals("x", alice.readCommand("472").getParam(1));
		IrcMessage m = alice.readCommand("MODE");
		assertEquals(List.of("#p", "+kl", "secret", "5"), m.getParams());
		bob.send("MODE #p");
		assertEquals(List.of("bob", "#p", "+kl", "secret", "5"), bob.readCommand("324").getParams());

		sync(bob);
		// a key can't be set twice; -k takes it off
		alice.send("MODE #p +k other");
		alice.readCommand("467");
		alice.send("MODE #p -k secret");
		assertEquals(List.of("#p", "-k", "secret"), alice.readCommand("MODE").getParams());

		// +o and -o
		sync(bob);
		alice.send("MODE #p +o bob");
		assertEquals(List.of("#p", "+o", "bob"), bob.readCommand("MODE").getParams());
		sync(alice);
		bob.send("MODE #p -o alice");
		assertEquals(List.of("#p", "-o", "alice"), alice.readCommand("MODE").getParams());
		// alice is no longer an operator; bob is
		alice.send("MODE #p +o carol");
		alice.readCommand("482");
		bob.send("MODE #p +o carol");
		bob.readCommand("401");

		// at most three changes with a parameter
		TestClient carol = register(s, "carol");
		TestClient dave = register(s, "dave");
		join(carol, "#p");
		join(dave, "#p");
		sync(bob);
		bob.send("MODE #p +vvvv alice carol dave bob");
		assertEquals(List.of("#p", "+vvv", "alice", "carol", "dave"), bob.readCommand("MODE").getParams());
		bob.send("MODE #nowhere +i");
		bob.readCommand("403");
	}

	@Test
	void keyLimitAndInvite() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient alice = register(s, "alice");
		TestClient bob = register(s, "bob");
		TestClient carol = register(s, "carol");
		join(alice, "#p");
		alice.send("MODE #p +k key");
		alice.readCommand("MODE");
		assertTrue(has(join(bob, "#p"), "475"));
		assertFalse(has(join(bob, "#p key"), "475"));
		alice.readCommand("JOIN");

		alice.send("MODE #p -k+l key 2");
		alice.readCommand("MODE");
		assertTrue(has(join(carol, "#p"), "471"));
		alice.send("MODE #p -l+i");
		alice.readCommand("MODE");
		assertTrue(has(join(carol, "#p"), "473"));

		// +i: only an operator invites
		bob.send("INVITE carol #p");
		bob.readCommand("482");
		alice.send("INVITE carol #p");
		assertEquals(List.of("alice", "#p", "carol"), alice.readCommand("341").getParams());
		IrcMessage inv = carol.readCommand("INVITE");
		assertEquals("alice", inv.getPrefix().getNickname());
		assertEquals(List.of("carol", "#p"), inv.getParams());
		assertFalse(has(join(carol, "#p"), "473"));
		alice.send("INVITE carol #p");
		alice.readCommand("443");
		alice.send("INVITE nobody #p");
		alice.readCommand("401");
	}

	@Test
	void bans() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient alice = register(s, "alice");
		TestClient bob = register(s, "bob");
		join(alice, "#p");
		join(bob, "#p");
		alice.send("MODE #p +b bob");
		assertEquals(List.of("#p", "+b", "bob!*@*"), alice.readCommand("MODE").getParams());
		bob.readCommand("MODE");
		// on the channel but banned: can't speak
		bob.send("PRIVMSG #p :hi");
		bob.readCommand("404");
		bob.send("MODE #p b");
		IrcMessage ban = bob.readCommand("367");
		assertEquals("bob!*@*", ban.getParam(2));
		bob.readCommand("368");
		bob.send("PART #p");
		bob.readCommand("PART");
		assertTrue(has(join(bob, "#p"), "474"));
		alice.send("MODE #p -b BOB!*@*");
		assertEquals(List.of("#p", "-b", "bob!*@*"), alice.readCommand("MODE").getParams());
		assertFalse(has(join(bob, "#p"), "474"));
	}

	@Test
	void kick() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient alice = register(s, "alice");
		TestClient bob = register(s, "bob");
		TestClient carol = register(s, "carol");
		join(alice, "#p");
		join(bob, "#p");
		bob.send("KICK #p alice");
		bob.readCommand("482");
		alice.send("KICK #p carol");
		alice.readCommand("441");
		alice.send("KICK #p bob :behave");
		IrcMessage k = bob.readCommand("KICK");
		assertEquals(List.of("#p", "bob", "behave"), k.getParams());
		assertEquals("alice", k.getPrefix().getNickname());
		alice.readCommand("KICK");
		assertFalse(s.getNode().findChannel("#p").isMember(s.getNode().findUser("bob")));
		sync(bob);
		alice.send("PRIVMSG #p :bob is gone");
		assertFalse(has(sync(bob), "PRIVMSG"));
		carol.send("KICK #p alice");
		carol.readCommand("442");
	}

	@Test
	void nickAndQuitReachTheChannels() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient alice = register(s, "alice");
		TestClient bob = register(s, "bob");
		TestClient carol = register(s, "carol");
		join(alice, "#a");
		join(alice, "#b");
		join(bob, "#a");
		join(bob, "#b");
		sync(alice);

		bob.send("NICK robert");
		assertEquals("robert", bob.readCommand("NICK").getParam(0));
		IrcMessage n = alice.readCommand("NICK");
		assertEquals("bob", n.getPrefix().getNickname());
		// once, though they share two channels; carol shares none
		assertFalse(has(sync(alice), "NICK"));
		assertFalse(has(sync(carol), "NICK"));
		assertEquals("@alice robert carol", names(join(carol, "#a")));

		bob.send("QUIT :bye");
		IrcMessage q = alice.readCommand("QUIT");
		assertEquals("robert", q.getPrefix().getNickname());
		assertEquals("Quit: bye", q.getParam(0));
		assertFalse(has(sync(alice), "QUIT"));
		carol.readCommand("QUIT");

		// a connection that just ends
		carol.close();
		assertEquals("Connection closed", alice.readCommand("QUIT").getParam(0));
		alice.send("NAMES #a");
		assertEquals("@alice", names(alice.readUntil(m -> m.is("366"))));
	}

	@Test
	void namesAndListHideSecretAndPrivate() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient alice = register(s, "alice");
		TestClient bob = register(s, "bob");
		TestClient eve = register(s, "eve");
		eve.send("MODE eve +i");
		eve.readCommand("MODE");
		join(alice, "#open");
		alice.send("TOPIC #open :all welcome");
		alice.readCommand("TOPIC");
		join(alice, "#hidden");
		alice.send("MODE #hidden +s");
		alice.readCommand("MODE");
		join(alice, "#quiet");
		alice.send("MODE #quiet +p");
		alice.readCommand("MODE");
		join(eve, "#open");

		bob.send("LIST");
		List<IrcMessage> list = bob.readUntil(m -> m.is("323"));
		assertTrue(has(list, "321"));
		List<String> rows = new ArrayList<String>();
		list.stream().filter(m -> m.is("322")).forEach(m -> rows.add(m.getParam(1)+" "+m.getParam(2)+" "+m.getParam(3)));
		rows.sort(null);
		assertEquals(List.of("#open 2 all welcome", "Prv 1 "), rows);

		// to a non-member a secret channel doesn't exist
		bob.send("TOPIC #hidden");
		bob.readCommand("403");
		bob.send("NAMES #hidden");
		IrcMessage end = bob.readCommand("366");
		assertEquals("#hidden", end.getParam(1));

		// an invisible user shows only to the channel's members
		bob.send("NAMES #open");
		List<IrcMessage> got = bob.readUntil(m -> m.is("366"));
		assertEquals("@alice", names(got));
		alice.send("NAMES #open");
		assertEquals("@alice eve", names(alice.readUntil(m -> m.is("366"))));

		// NAMES with no parameter: visible channels, then the visible users on none of them
		bob.send("NAMES");
		got = bob.readUntil(m -> m.is("366"));
		assertEquals("*", got.get(got.size()-1).getParam(1));
		List<String> channels = new ArrayList<String>();
		got.stream().filter(m -> m.is("353")).forEach(m -> channels.add(m.getParam(2)+" "+m.getLastParam()));
		assertEquals(List.of("#open @alice", "* bob"), channels);
	}

	@Test
	void lusersCountsChannels() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient alice = register(s, "alice");
		join(alice, "#a");
		alice.send("LUSERS");
		IrcMessage m = alice.readCommand("254");
		assertEquals("1", m.getParam(1));
	}
}
