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
import java.util.Properties;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.irc.protocol.IrcMessage;

/** Registration and ping timeouts, and flood control (RFC 2812 section 5.8) */
class TimersTest extends ServerTestBase {

	@Test
	void registrationTimeout() throws IOException {
		Properties p = nodeSettings();
		p.setProperty(IrcNode.PROPERTY_REGISTRATION_TIMEOUT, "1s");
		IrcServer s = start(p);
		TestClient c = connect(s);
		c.send("NICK tony");
		IrcMessage error = c.readCommand("ERROR");
		assertTrue(error.getLastParam().contains("Registration timed out"), error.toLine());
		assertTrue(c.closedByServer());
		long end = System.currentTimeMillis()+2000;
		while( s.getNode().getUnregisteredCount() != 0 && System.currentTimeMillis() < end ) {
			sleep(10);
		}
		assertEquals(0, s.getNode().getUnregisteredCount());
	}

	@Test
	void pingAndPingTimeout() throws IOException {
		Properties p = nodeSettings();
		p.setProperty(IrcNode.PROPERTY_PING_INTERVAL, "1s");
		p.setProperty(IrcNode.PROPERTY_PING_TIMEOUT, "1500ms");
		IrcServer s = start(p);
		TestClient c = register(s, "tony");
		IrcMessage ping = c.readCommand("PING");
		assertEquals("irc.example.net", ping.getParam(0));
		c.send("PONG irc.example.net");
		// answered: asked again after the next quiet second, then dropped when it isn't answered
		c.readCommand("PING");
		IrcMessage error = c.readCommand("ERROR");
		assertTrue(error.getLastParam().contains("Ping timeout"), error.toLine());
		assertTrue(c.closedByServer());
	}

	@Test
	void floodControlDelaysButKeepsMessages() throws IOException {
		Properties p = nodeSettings();
		p.setProperty(IrcNode.PROPERTY_FLOOD_PENALTY, "500ms");
		p.setProperty(IrcNode.PROPERTY_FLOOD_LIMIT, "1s");
		IrcServer s = start(p);
		TestClient c = register(s, "tony");
		// the welcome's own messages have moved the timer; wait for it to catch up
		sleep(1500);
		long start = System.currentTimeMillis();
		StringBuilder burst = new StringBuilder();
		for (int i = 0; i < 8; i++) {
			burst.append("PING ").append(i).append("\r\n");
		}
		c.sendRaw(burst.toString());
		for (int i = 0; i < 8; i++) {
			assertEquals(Integer.toString(i), c.readCommand("PONG").getParam(1));
		}
		long took = System.currentTimeMillis()-start;
		// 3 at once (the timer may run 1s ahead), the other 5 one per 500ms: at least 2s
		assertTrue(took >= 2000, "took "+took+" ms");
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
