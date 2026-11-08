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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.irc.protocol.IrcMessage;

/** The commands of stage 1 step (a) after registration */
class CommandsTest extends ServerTestBase {

	@Test
	void pingPong() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient c = register(s, "tony");
		c.send("PING :hello there");
		IrcMessage pong = c.readCommand("PONG");
		assertEquals("irc.example.net", pong.getPrefix().getServerName());
		assertEquals("irc.example.net", pong.getParam(0));
		assertEquals("hello there", pong.getParam(1));
		c.send("PING");
		c.readCommand("409");
		c.send("PING abc other.example.net");
		assertEquals("other.example.net", c.readCommand("402").getParam(1));
		c.send("PONG");
		c.readCommand("409");
	}

	@Test
	void quitReleasesTheNick() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient c = register(s, "tony");
		c.send("QUIT :see you");
		IrcMessage error = c.readCommand("ERROR");
		assertEquals("Closing Link: 127.0.0.1 (Quit: see you)", error.getLastParam());
		assertTrue(c.closedByServer());

		TestClient again = register(s, "tony");
		again.send("LUSERS");
		IrcMessage client = again.readCommand("251");
		assertTrue(client.getLastParam().startsWith("There are 1 users"), client.toLine());
	}

	@Test
	void closingReleasesTheNick() throws Exception {
		IrcServer s = start(nodeSettings());
		TestClient c = register(s, "tony");
		c.close();
		long end = System.currentTimeMillis()+5000;
		while( s.getNode().findUser("tony") != null && System.currentTimeMillis() < end ) {
			Thread.sleep(20);
		}
		register(s, "tony");
	}

	@Test
	void lusersCounts() throws IOException {
		IrcServer s = start(nodeSettings());
		register(s, "one");
		TestClient two = register(s, "two");
		connect(s).send("NICK pending");
		// let the unregistered connection's NICK arrive
		two.send("PING sync");
		two.readCommand("PONG");
		long end = System.currentTimeMillis()+2000;
		while( s.getNode().getUnregisteredCount() < 1 && System.currentTimeMillis() < end ) {
			Thread.yield();
		}
		two.send("LUSERS");
		List<IrcMessage> got = two.readUntil(m -> m.is("255"));
		assertEquals("There are 2 users and 0 services on 1 servers", got.get(0).getLastParam());
		assertTrue(got.stream().anyMatch(m -> m.is("253") && m.getParam(1).equals("1")), got.toString());
		assertEquals("I have 2 clients and 0 servers", got.get(got.size()-1).getLastParam());
		two.send("LUSERS * nowhere.example.net");
		two.readCommand("402");
	}

	@Test
	void motdCommand() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient c = register(s, "tony");
		c.send("MOTD");
		c.readCommand("422");
		c.send("MOTD irc.example.net");
		c.readCommand("422");
		c.send("MOTD *.example.net");
		c.readCommand("422");
		c.send("MOTD other.example.org");
		c.readCommand("402");
	}

	@Test
	void nickChange() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient c = register(s, "tony");
		c.send("NICK Tony2");
		IrcMessage m = c.readCommand("NICK");
		assertEquals("tony", m.getPrefix().getNickname());
		assertEquals("tb", m.getPrefix().getUser().replace("tony", "tb"));
		assertEquals("Tony2", m.getParam(0));
		assertTrue(s.getNode().findUser("tony2") != null);
		assertTrue(s.getNode().findUser("tony") == null);
		// the old nick is free again
		register(s, "tony");
		// a case change of one's own nick is allowed
		c.send("NICK TONY2");
		assertEquals("TONY2", c.readCommand("NICK").getParam(0));
	}

	@Test
	void userModes() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient c = register(s, "tony");
		c.send("MODE tony +w");
		IrcMessage m = c.readCommand("MODE");
		assertEquals("tony", m.getParam(0));
		assertEquals("+w", m.getParam(1));
		// +o is OPER's; +a is AWAY's; nothing changes, so no MODE
		c.send("MODE tony +oa");
		c.send("MODE tony");
		assertEquals("+w", c.readCommand("221").getParam(1));
		c.send("MODE tony +i-w");
		assertEquals("+i-w", c.readCommand("MODE").getParam(1));
		c.send("MODE tony +x");
		c.readCommand("501");
		c.send("MODE other +i");
		c.readCommand("502");
		c.send("MODE #parley");
		c.readCommand("403");
		c.send("MODE");
		c.readCommand("461");
		// +r can be set, not lifted, and a restricted user can't change nick
		c.send("MODE tony +r");
		assertEquals("+r", c.readCommand("MODE").getParam(1));
		c.send("MODE tony -r");
		c.send("MODE tony");
		assertEquals("+ir", c.readCommand("221").getParam(1));
		c.send("NICK newnick");
		c.readCommand("484");
	}

	@Test
	void malformedMessagesAreIgnored() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient c = register(s, "tony");
		c.send(":onlyprefix");
		c.send("PING after");
		assertEquals("after", c.readCommand("PONG").getParam(1));
	}
}
