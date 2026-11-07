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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** CaseMapping, Masks and IrcSyntax */
public class NamesTest {

	@Test
	public void rfc1459CaseMapping() {
		CaseMapping m = CaseMapping.RFC1459;
		assertEquals("tony{}|^", m.toLowerCase("TONY[]\\~"));
		assertTrue(m.equals("Tony[a]", "tony{A}"));
		assertFalse(m.equals("tony", "tonyx"));
		String same = "already-lower";
		assertTrue(same == m.toLowerCase(same), "no copy when nothing changes");
	}

	@Test
	public void asciiCaseMapping() {
		CaseMapping m = CaseMapping.ASCII;
		assertEquals("tony[]", m.toLowerCase("TONY[]"));
		assertFalse(m.equals("a[", "a{"));
	}

	@Test
	public void masks() {
		assertTrue(Masks.matches("a?c", "abc"));
		assertFalse(Masks.matches("a?c", "ac"));
		assertTrue(Masks.matches("a*c", "ac"));
		assertTrue(Masks.matches("a*c", "abbbc"));
		assertFalse(Masks.matches("a*c", "abcd"));
		assertTrue(Masks.matches("*", ""));
		assertTrue(Masks.matches("*!*@*.example.net", "Tony!tb@host.EXAMPLE.net"));
		assertFalse(Masks.matches("*!*@*.example.net", "tony!tb@example.net"));
		assertTrue(Masks.matches("*a*b*", "xxaxxbxx"));
		assertTrue(Masks.matches("tony[*", "TONY{x"), "case mapping applies");
	}

	@Test
	public void escapedWildcards() {
		assertTrue(Masks.matches("a\\*c", "a*c"));
		assertFalse(Masks.matches("a\\*c", "abc"));
		assertTrue(Masks.matches("a\\?", "a?"));
		assertFalse(Masks.matches("a\\?", "ab"));
		assertTrue(Masks.matches("a\\\\b", "a\\b"));
		assertTrue(Masks.matches("a\\b", "a\\b"), "a backslash before an ordinary character is itself");
		assertFalse(Masks.hasWildcards("a\\*b"));
		assertTrue(Masks.hasWildcards("a\\*b*"));
	}

	@Test
	public void nicknames() {
		assertTrue(IrcSyntax.isNickname("tony"));
		assertTrue(IrcSyntax.isNickname("[tb]-2"));
		assertTrue(IrcSyntax.isNickname("abcdefghi"));
		assertFalse(IrcSyntax.isNickname("abcdefghij"), "RFC 2812 allows 9");
		assertTrue(IrcSyntax.isNickname("abcdefghij", 30));
		assertFalse(IrcSyntax.isNickname("2tony"));
		assertFalse(IrcSyntax.isNickname("-tony"));
		assertFalse(IrcSyntax.isNickname("to ny"));
		assertFalse(IrcSyntax.isNickname("to.ny"));
		assertFalse(IrcSyntax.isNickname(""));
	}

	@Test
	public void channelNames() {
		assertTrue(IrcSyntax.isChannelName("#parley"));
		assertTrue(IrcSyntax.isChannelName("&local"));
		assertTrue(IrcSyntax.isChannelName("+modeless"));
		assertTrue(IrcSyntax.isChannelName("!ABC12safe"));
		assertTrue(IrcSyntax.isChannelName("#chan:*.example.net"));
		assertFalse(IrcSyntax.isChannelName("!abc12safe"), "a channel ID is upper case");
		assertFalse(IrcSyntax.isChannelName("!ABC12"), "a safe channel needs a name after its ID");
		assertFalse(IrcSyntax.isChannelName("#"));
		assertFalse(IrcSyntax.isChannelName("parley"));
		assertFalse(IrcSyntax.isChannelName("#a,b"));
		assertFalse(IrcSyntax.isChannelName("#a b"));
		assertFalse(IrcSyntax.isChannelName("#a\u0007"));
		assertFalse(IrcSyntax.isChannelName("#a:b:c"));
		StringBuilder fifty = new StringBuilder("#");
		while( fifty.length() < 50 ) {
			fifty.append('x');
		}
		assertTrue(IrcSyntax.isChannelName(fifty.toString()));
		assertFalse(IrcSyntax.isChannelName(fifty+"x"));
	}

	@Test
	public void usersAndKeys() {
		assertTrue(IrcSyntax.isUser("~tb"));
		assertFalse(IrcSyntax.isUser("t@b"));
		assertFalse(IrcSyntax.isUser(""));
		assertTrue(IrcSyntax.isKey("secret"));
		assertFalse(IrcSyntax.isKey("has space"));
		assertFalse(IrcSyntax.isKey("123456789012345678901234"), "at most 23");
		assertFalse(IrcSyntax.isKey("café"), "US-ASCII only");
	}
}
