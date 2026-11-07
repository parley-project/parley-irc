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
package us.bringardner.parley.irc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

public class IrcMessageTest {

	@Test
	public void parsesCommandOnly() {
		IrcMessage m = IrcMessage.parse("QUIT");
		assertNull(m.getPrefix());
		assertEquals("QUIT", m.getCommand());
		assertEquals(0, m.getParamCount());
	}

	@Test
	public void parsesPrefixMiddleAndTrailing() {
		IrcMessage m = IrcMessage.parse(":tony!tb@host.example.net PRIVMSG #parley :hello there :)");
		assertEquals("tony", m.getPrefix().getNickname());
		assertEquals("tb", m.getPrefix().getUser());
		assertEquals("host.example.net", m.getPrefix().getHost());
		assertEquals("PRIVMSG", m.getCommand());
		assertEquals(Arrays.asList("#parley", "hello there :)"), m.getParams());
		assertEquals("hello there :)", m.getLastParam());
	}

	@Test
	public void commandIsUpperCased() {
		assertEquals("NICK", IrcMessage.parse("nick tony").getCommand());
		assertTrue(IrcMessage.parse("nick tony").is("Nick"));
	}

	@Test
	public void numericReply() {
		IrcMessage m = IrcMessage.parse(":irc.example.net 001 tony :Welcome to the Internet Relay Network tony!tb@host");
		assertTrue(m.isNumeric());
		assertEquals(1, m.getNumericCode());
		assertEquals(Numeric.RPL_WELCOME, m.getNumeric());
		assertTrue(m.getPrefix().isServer());
	}

	@Test
	public void middleMayContainColons() {
		IrcMessage m = IrcMessage.parse("MODE #a:b +k a:b");
		assertEquals(Arrays.asList("#a:b", "+k", "a:b"), m.getParams());
	}

	@Test
	public void emptyTrailing() {
		IrcMessage m = IrcMessage.parse("TOPIC #parley :");
		assertEquals(Arrays.asList("#parley", ""), m.getParams());
		assertEquals("TOPIC #parley :", m.toLine());
	}

	@Test
	public void extraSpacesAreLenient() {
		// RFC 1459: <SPACE> is one or more spaces
		IrcMessage m = IrcMessage.parse("  :srv.example.net   PING   a   b  ");
		assertEquals("PING", m.getCommand());
		assertEquals(Arrays.asList("a", "b"), m.getParams());
	}

	@Test
	public void fifteenthParameterIsTheRestOfTheLine() {
		StringBuilder line = new StringBuilder("CMD");
		for (int i = 1; i <= 14; i++) {
			line.append(' ').append("p").append(i);
		}
		line.append(" last with spaces");
		IrcMessage m = IrcMessage.parse(line.toString());
		assertEquals(15, m.getParamCount());
		assertEquals("last with spaces", m.getLastParam());
		// and written back with ':'
		assertEquals(m, IrcMessage.parse(m.toLine()));
		assertTrue(m.toLine().endsWith(" :last with spaces"));
	}

	@Test
	public void rejectsBadLines() {
		assertThrows(IrcFormatException.class, () -> IrcMessage.parse(""));
		assertThrows(IrcFormatException.class, () -> IrcMessage.parse("   "));
		assertThrows(IrcFormatException.class, () -> IrcMessage.parse(":prefix.only"));
		assertThrows(IrcFormatException.class, () -> IrcMessage.parse(": NICK a"));
		assertThrows(IrcFormatException.class, () -> IrcMessage.parse("12 a"));
		assertThrows(IrcFormatException.class, () -> IrcMessage.parse("1234 a"));
		assertThrows(IrcFormatException.class, () -> IrcMessage.parse("NI-CK a"));
		assertThrows(IrcFormatException.class, () -> IrcMessage.parse("NICK a\0b"));
		assertThrows(IrcFormatException.class, () -> IrcMessage.parse("NICK a\rb"));
	}

	@Test
	public void writesTrailingOnlyWhenNeeded() {
		assertEquals("NICK tony", IrcMessage.of("nick", "tony").toLine());
		assertEquals("PRIVMSG #p :hi there", IrcMessage.of("PRIVMSG", "#p", "hi there").toLine());
		assertEquals("PRIVMSG #p ::-)", IrcMessage.of("PRIVMSG", "#p", ":-)").toLine());
		assertEquals(":irc.example.net PONG irc.example.net",
				IrcMessage.of(Prefix.server("irc.example.net"), "PONG", Collections.singletonList("irc.example.net")).toLine());
	}

	@Test
	public void rejectsParametersThatCantBeWritten() {
		assertThrows(IrcFormatException.class, () -> IrcMessage.of("PRIVMSG", "a b", "text"));
		assertThrows(IrcFormatException.class, () -> IrcMessage.of("PRIVMSG", "", "text"));
		assertThrows(IrcFormatException.class, () -> IrcMessage.of("PRIVMSG", ":a", "text"));
		assertThrows(IrcFormatException.class, () -> IrcMessage.of("PRIVMSG", "#a", "line\nbreak"));
		List<String> sixteen = new ArrayList<String>();
		for (int i = 0; i < 16; i++) {
			sixteen.add("p");
		}
		assertThrows(IrcFormatException.class, () -> IrcMessage.of(null, "CMD", sixteen));
	}

	@Test
	public void numericFactory() {
		IrcMessage m = IrcMessage.numeric(Prefix.server("irc.example.net"), Numeric.ERR_NOSUCHNICK, "tony", "bob", "No such nick/channel");
		assertEquals(":irc.example.net 401 tony bob :No such nick/channel", m.toLine());
	}

	@Test
	public void roundTrips() {
		String[] lines = {
				":irc.example.net 353 tony = #parley :@tony +bob carol",
				"JOIN #a,#b key1,key2",
				":tony!tb@h MODE #parley +o bob",
				"USER tb 0 * :Tony Bringardner",
		};
		for (String line : lines) {
			assertEquals(line, IrcMessage.parse(line).toLine());
		}
	}

	@Test
	public void toBytesEndsInCrLf() {
		ByteBuffer b = IrcMessage.of("PING", "x").toBytes();
		assertEquals("PING x\r\n", StandardCharsets.UTF_8.decode(b).toString());
	}

	@Test
	public void toBytesCutsTheLastParameter() {
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < 600; i++) {
			text.append('é'); // two bytes in UTF-8
		}
		ByteBuffer b = IrcMessage.of("PRIVMSG", "#p", text.toString()).toBytes();
		assertTrue(b.remaining() <= IrcMessage.MAX_BYTES);
		byte[] bytes = new byte[b.remaining()];
		b.get(bytes);
		String line = new String(bytes, 0, bytes.length-2, StandardCharsets.UTF_8);
		IrcMessage back = IrcMessage.parse(line);
		assertTrue(back.getLastParam().startsWith("éé"));
		assertFalse(line.contains("�"), "no character was split");
	}

	@Test
	public void toBytesFailsWhenOnlyTheHeadIsTooLong() {
		StringBuilder target = new StringBuilder();
		for (int i = 0; i < 600; i++) {
			target.append('a');
		}
		assertThrows(IrcFormatException.class, () -> IrcMessage.of("PRIVMSG", target.toString(), "x").toBytes());
	}
}
