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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class NumericTest {

	@Test
	public void lookUpByCode() {
		assertEquals(Numeric.RPL_WELCOME, Numeric.of(1));
		assertEquals(Numeric.ERR_NICKNAMEINUSE, Numeric.of(433));
		assertEquals(Numeric.ERR_USERSDONTMATCH, Numeric.of(502));
		assertNull(Numeric.of(999));
	}

	@Test
	public void bothMeaningsOf005() {
		assertEquals(Numeric.RPL_BOUNCE, Numeric.of(5));
		assertEquals(5, Numeric.RPL_ISUPPORT.getCode());
		assertEquals(Numeric.Source.DE_FACTO, Numeric.RPL_ISUPPORT.getSource());
	}

	@Test
	public void textIsThreeDigits() {
		assertEquals("001", Numeric.RPL_WELCOME.getText());
		assertEquals("353", Numeric.RPL_NAMREPLY.getText());
	}

	@Test
	public void errors() {
		assertTrue(Numeric.ERR_NOSUCHNICK.isError());
		assertFalse(Numeric.RPL_TOPIC.isError());
	}

	@Test
	public void rfc1459OnlyRepliesAreReservedIn2812() {
		assertEquals(Numeric.Source.RFC_2812_RESERVED, Numeric.RPL_WHOISCHANOP.getSource());
		assertEquals(Numeric.Source.RFC_2812_RESERVED, Numeric.RPL_INFOSTART.getSource());
		assertEquals(Numeric.Source.RFC_2812, Numeric.RPL_ADMINLOC2.getSource());
	}

	@Test
	public void codesAreInRange() {
		for (Numeric n : Numeric.values()) {
			assertTrue(n.getCode() >= 1 && n.getCode() <= 999, n.name());
			assertTrue(n.name().startsWith("RPL_") || n.name().startsWith("ERR_"), n.name());
			assertEquals(n.isError(), n.name().startsWith("ERR_"), n.name());
		}
	}
}
