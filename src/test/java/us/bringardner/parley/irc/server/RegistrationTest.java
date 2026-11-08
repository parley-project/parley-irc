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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.parley.irc.protocol.IrcMessage;

/** Registration (RFC 2812 section 3.1) and the welcome replies */
class RegistrationTest extends ServerTestBase {

	@Test
	void registersAndWelcomes() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient c = connect(s);
		c.send("NICK tony");
		c.send("USER tb 8 * :Tony B");
		List<IrcMessage> got = c.readUntil(m -> m.is("MODE"));

		assertEquals("001", got.get(0).getCommand());
		assertEquals("irc.example.net", got.get(0).getPrefix().getServerName());
		assertEquals("tony", got.get(0).getParam(0));
		assertEquals("Welcome to the Internet Relay Network tony!tb@127.0.0.1", got.get(0).getLastParam());
		assertEquals("002", got.get(1).getCommand());
		assertEquals("003", got.get(2).getCommand());
		IrcMessage myInfo = got.get(3);
		assertEquals("004", myInfo.getCommand());
		assertEquals("irc.example.net", myInfo.getParam(1));
		assertEquals(IrcNode.USER_MODES, myInfo.getParam(3));
		IrcMessage isupport = got.get(4);
		assertEquals("005", isupport.getCommand());
		assertTrue(isupport.getParams().contains("CASEMAPPING=rfc1459"), isupport.toLine());
		assertTrue(isupport.getParams().contains("NICKLEN=9"), isupport.toLine());
		assertEquals("are supported by this server", isupport.getLastParam());
		assertTrue(got.stream().anyMatch(m -> m.is("251") && m.getLastParam().startsWith("There are 1 users")));
		assertTrue(got.stream().anyMatch(m -> m.is("255")));
		// no MOTD file
		assertTrue(got.stream().anyMatch(m -> m.is("422")));
		// mode bit 3 (8) is +i
		IrcMessage mode = got.get(got.size()-1);
		assertEquals("tony", mode.getParam(0));
		assertEquals("+i", mode.getParam(1));
	}

	@Test
	void acceptsRfc1459User() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient c = connect(s);
		// USER user host server :real name, the order of NICK and USER doesn't matter
		c.send("USER tb tony-pc irc.example.net :Tony B");
		c.send("NICK tony");
		IrcMessage welcome = c.readCommand("001");
		assertEquals("tony", welcome.getParam(0));
		c.readCommand("422");
		c.send("MODE tony");
		IrcMessage modes = c.readCommand("221");
		assertEquals("+", modes.getParam(1));
	}

	@Test
	void bareLfAndCrEndMessages() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient c = connect(s);
		c.sendRaw("NICK tony\nUSER tb 0 * :Tony B\r");
		assertEquals("tony", c.readCommand("001").getParam(0));
	}

	@Test
	void nickErrors() throws IOException {
		IrcServer s = start(nodeSettings());
		register(s, "tony");
		TestClient c = connect(s);
		c.send("NICK");
		assertEquals("*", c.readCommand("431").getParam(0));
		c.send("NICK 9lives");
		assertEquals("9lives", c.readCommand("432").getParam(1));
		c.send("NICK abcdefghij");
		c.readCommand("432");
		// RFC 1459 case mapping: {} are the lower case of []
		c.send("NICK TONY");
		assertEquals("TONY", c.readCommand("433").getParam(1));
	}

	@Test
	void nickLengthIsASetting() throws IOException {
		Properties p = nodeSettings();
		p.setProperty(IrcNode.PROPERTY_NICK_LENGTH, "16");
		IrcServer s = start(p);
		TestClient c = register(s, "averylongnick16c");
		c.send("LUSERS");
		c.readCommand("255");
	}

	@Test
	void caseMappedNicksCollide() throws IOException {
		IrcServer s = start(nodeSettings());
		register(s, "a[b]");
		TestClient c = connect(s);
		c.send("NICK A{B}");
		c.readCommand("433");
	}

	@Test
	void commandsBeforeRegistration() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient c = connect(s);
		c.send("MOTD");
		assertEquals("*", c.readCommand("451").getParam(0));
		c.send("JOIN #parley");
		assertEquals("JOIN", c.readCommand("421").getParam(1));
		// decision D8
		c.send("CAP LS 302");
		assertEquals("CAP", c.readCommand("421").getParam(1));
		c.send("PING abc");
		IrcMessage pong = c.readCommand("PONG");
		assertEquals("abc", pong.getParam(1));
	}

	@Test
	void unknownCommandAfterRegistration() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient c = register(s, "tony");
		c.send("FROB x");
		IrcMessage m = c.readCommand("421");
		assertEquals("tony", m.getParam(0));
		assertEquals("FROB", m.getParam(1));
		c.send("CAP END");
		c.readCommand("421");
	}

	@Test
	void userTwiceAndPassAfterRegistration() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient c = register(s, "tony");
		c.send("USER again 0 * :x");
		c.readCommand("462");
		c.send("PASS secret");
		c.readCommand("462");
		c.send("USER only");
		c.readCommand("462");
	}

	@Test
	void userNeedsFourParameters() throws IOException {
		IrcServer s = start(nodeSettings());
		TestClient c = connect(s);
		c.send("USER tb 0 *");
		assertEquals("USER", c.readCommand("461").getParam(1));
	}

	@Test
	void passwordRequired() throws IOException {
		Properties listener = new Properties();
		listener.setProperty("parley.irc.password", "s3cret");
		IrcServer s = start(new IrcNode(nodeSettings()), listener);
		assertTrue(s.isPasswordRequired());

		TestClient bad = connect(s);
		bad.send("PASS wrong");
		bad.send("NICK tony");
		bad.send("USER tb 0 * :Tony");
		bad.readCommand("464");
		IrcMessage error = bad.readCommand("ERROR");
		assertTrue(error.getLastParam().contains("Bad password"), error.toLine());
		assertTrue(bad.closedByServer());

		TestClient none = connect(s);
		none.send("NICK tony");
		none.send("USER tb 0 * :Tony");
		none.readCommand("464");

		TestClient good = connect(s);
		good.send("PASS s3cret");
		good.send("NICK tony");
		good.send("USER tb 0 * :Tony");
		assertEquals("tony", good.readCommand("001").getParam(0));
	}

	@Test
	void passWithoutPasswordIsIgnored() throws IOException {
		IrcServer s = start(nodeSettings());
		assertFalse(s.isPasswordRequired());
		TestClient c = connect(s);
		c.send("PASS anything");
		c.send("NICK tony");
		c.send("USER tb 0 * :Tony");
		c.readCommand("001");
	}

	@Test
	void numeric005Bounce() throws IOException {
		Properties listener = new Properties();
		listener.setProperty("parley.irc.numeric005", "bounce");
		IrcServer s = start(new IrcNode(nodeSettings()), listener);
		assertFalse(s.isIsupport());
		TestClient c = connect(s);
		c.send("NICK tony");
		c.send("USER tb 0 * :Tony");
		List<IrcMessage> got = c.readUntil(m -> m.is("422"));
		assertFalse(got.stream().anyMatch(m -> m.is("005")), got.toString());
	}

	@Test
	void badNumeric005() {
		Properties listener = new Properties();
		listener.setProperty("parley.irc.numeric005", "both");
		IOException e = assertThrows(IOException.class, () -> start(new IrcNode(nodeSettings()), listener));
		assertTrue(e.getMessage().contains("numeric005"), e.getMessage());
	}

	@Test
	void nodeIsRequired() {
		assertThrows(IOException.class, () -> start(null, new Properties()));
		assertThrows(IOException.class, () -> new IrcNode(new Properties()));
		Properties p = new Properties();
		p.setProperty(IrcNode.PROPERTY_NAME, "nodots");
		assertThrows(IOException.class, () -> new IrcNode(p));
	}

	@Test
	void nodeFileMotdAndSharing(@TempDir Path dir) throws IOException {
		String longLine = "0123456789".repeat(9);
		Files.write(dir.resolve("motd.txt"), ("Welcome to Parley\n"+longLine+"\n").getBytes(StandardCharsets.UTF_8));
		Files.write(dir.resolve("node.properties"), ("name=irc.example.net\nmotd=motd.txt\nnetwork=ParleyNet\n").getBytes(StandardCharsets.UTF_8));
		String location = dir.resolve("node.properties").toString();
		Properties listener = new Properties();
		listener.setProperty("parley.irc.node", location);
		IrcServer a = start(null, listener);
		IrcServer b = start(null, listener);
		assertNotNull(a.getNode());
		// one node for the listeners that name the same file
		assertTrue(a.getNode() == b.getNode());

		TestClient c = connect(a);
		c.send("NICK tony");
		c.send("USER tb 0 * :Tony");
		List<IrcMessage> got = c.readUntil(m -> m.is("376"));
		assertTrue(got.stream().anyMatch(m -> m.is("005") && m.getParams().contains("NETWORK=ParleyNet")));
		assertTrue(got.stream().anyMatch(m -> m.is("375")));
		List<String> lines = new java.util.ArrayList<String>();
		got.stream().filter(m -> m.is("372")).forEach(m -> lines.add(m.getLastParam()));
		assertEquals(List.of("- Welcome to Parley", "- "+longLine.substring(0, 80), "- "+longLine.substring(80)), lines);

		// a nick taken on one listener is taken on the other
		TestClient other = connect(b);
		other.send("NICK Tony");
		other.readCommand("433");
	}
}
