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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.net.nio.LineFrameDecoder;

/** IrcMessage.newDecoder and parse(ByteBuffer): parley-net's LineFrameDecoder set up for IRC */
public class DecoderTest {

	private static List<IrcMessage> decodeAll(byte[] input) throws IOException {
		LineFrameDecoder decoder = IrcMessage.newDecoder();
		List<IrcMessage> ret = new ArrayList<IrcMessage>();
		ByteBuffer in = ByteBuffer.wrap(input);
		ByteBuffer frame;
		while( (frame = decoder.decode(in)) != null ) {
			ret.add(IrcMessage.parse(frame));
		}
		return ret;
	}

	private static byte[] latin1(String s) {
		return s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
	}

	@Test
	public void crLfCrAndLfAllEndAMessageAndEmptyOnesAreSkipped() throws IOException {
		List<IrcMessage> got = decodeAll(latin1("NICK a\r\n\r\nUSER b 0 * :B\rPING x\nQUIT\r\n"));
		assertEquals(4, got.size());
		assertEquals("NICK", got.get(0).getCommand());
		assertEquals("B", got.get(1).getLastParam());
		assertEquals("PING", got.get(2).getCommand());
		assertEquals("QUIT", got.get(3).getCommand());
	}

	@Test
	public void longMessageIsCutTo510Bytes() throws IOException {
		StringBuilder text = new StringBuilder("PRIVMSG #p :");
		while( text.length() < 700 ) {
			text.append('x');
		}
		List<IrcMessage> got = decodeAll(latin1(text+"\r\nQUIT\r\n"));
		assertEquals(2, got.size());
		// 510 bytes in all, "PRIVMSG #p :" included
		assertEquals(IrcMessage.MAX_LINE_BYTES-"PRIVMSG #p :".length(), got.get(0).getLastParam().length());
		assertEquals("QUIT", got.get(1).getCommand());
	}

	@Test
	public void utf8OrLatin1() throws IOException {
		assertEquals("café", decodeAll("PRIVMSG #p :café\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)).get(0).getLastParam());
		assertEquals("café", decodeAll(latin1("PRIVMSG #p :café\n")).get(0).getLastParam());
	}
}
