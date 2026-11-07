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

import org.junit.jupiter.api.Test;

public class PrefixTest {

	@Test
	public void serverName() {
		Prefix p = Prefix.parse("irc.example.net");
		assertTrue(p.isServer());
		assertEquals("irc.example.net", p.getServerName());
		assertNull(p.getNickname());
	}

	@Test
	public void fullUser() {
		Prefix p = Prefix.parse("tony!tb@host.example.net");
		assertFalse(p.isServer());
		assertEquals("tony", p.getNickname());
		assertEquals("tb", p.getUser());
		assertEquals("host.example.net", p.getHost());
		assertEquals("tony!tb@host.example.net", p.toString());
	}

	@Test
	public void nickOnlyAndNickAtHost() {
		assertEquals("tony", Prefix.parse("tony").getNickname());
		Prefix p = Prefix.parse("tony@host");
		assertEquals("tony", p.getNickname());
		assertNull(p.getUser());
		assertEquals("host", p.getHost());
	}

	@Test
	public void rejectsEmptyParts() {
		assertThrows(IrcFormatException.class, () -> Prefix.parse(""));
		assertThrows(IrcFormatException.class, () -> Prefix.parse("tony!@host"));
		assertThrows(IrcFormatException.class, () -> Prefix.parse("tony!tb@"));
		assertThrows(IrcFormatException.class, () -> Prefix.parse("!tb@host"));
		assertThrows(IrcFormatException.class, () -> Prefix.parse("tony!tb"));
		assertThrows(IrcFormatException.class, () -> Prefix.user("a b", null, null));
	}

	@Test
	public void equality() {
		assertEquals(Prefix.parse("tony!tb@h"), Prefix.user("tony", "tb", "h"));
		assertEquals(Prefix.parse("irc.example.net"), Prefix.server("irc.example.net"));
	}
}
