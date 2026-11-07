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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.io.LineTooLongException;

public class IrcLineDecoderTest {

	/**
	 * Feed the chunks one after another the way a connection does: unconsumed bytes are kept and
	 * offered again with the next chunk.
	 */
	private static List<String> decodeAll(IrcLineDecoder decoder, byte[]... chunks) throws IOException {
		List<String> ret = new ArrayList<String>();
		ByteBuffer in = ByteBuffer.allocate(8192);
		for (byte[] chunk : chunks) {
			in.put(chunk);
			in.flip();
			ByteBuffer frame;
			while( (frame = decoder.decode(in)) != null ) {
				ret.add(IrcText.decode(frame));
			}
			in.compact();
		}
		return ret;
	}

	private static byte[] b(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	public void crLfCrAndLfAllEndAMessage() throws IOException {
		assertEquals(Arrays.asList("A", "B", "C", "D"), decodeAll(new IrcLineDecoder(), b("A\r\nB\rC\nD\r\n")));
	}

	@Test
	public void emptyMessagesAreSkipped() throws IOException {
		assertEquals(Arrays.asList("A", "B"), decodeAll(new IrcLineDecoder(), b("\r\n\r\nA\n\n\rB\r\n")));
	}

	@Test
	public void crLfSplitAcrossReads() throws IOException {
		assertEquals(Arrays.asList("NICK a", "USER b"), decodeAll(new IrcLineDecoder(), b("NICK a\r"), b("\nUSER b\r\n")));
	}

	@Test
	public void partialMessageWaits() throws IOException {
		IrcLineDecoder d = new IrcLineDecoder();
		ByteBuffer in = ByteBuffer.wrap(b("PRIV"));
		assertNull(d.decode(in));
		assertEquals(Arrays.asList("PRIVMSG a b"), decodeAll(new IrcLineDecoder(), b("PRIV"), b("MSG a"), b(" b\n")));
	}

	@Test
	public void longMessageIsCut() throws IOException {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < 700; i++) {
			s.append('x');
		}
		List<String> got = decodeAll(new IrcLineDecoder(), b(s+"\r\nNEXT\r\n"));
		assertEquals(2, got.size());
		assertEquals(IrcMessage.MAX_LINE_BYTES, got.get(0).length());
		assertEquals("NEXT", got.get(1));
	}

	@Test
	public void longMessageCutAcrossReads() throws IOException {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < 300; i++) {
			s.append('y');
		}
		// 900 bytes in three reads, the line end in the last
		List<String> got = decodeAll(new IrcLineDecoder(), b(s.toString()), b(s.toString()), b(s+"\nNEXT\n"));
		assertEquals(2, got.size());
		assertEquals(IrcMessage.MAX_LINE_BYTES, got.get(0).length());
		assertEquals("NEXT", got.get(1));
	}

	@Test
	public void cutKeepsUtf8Whole() throws IOException {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < 400; i++) {
			s.append('é');
		}
		List<String> got = decodeAll(new IrcLineDecoder(11, true), b("a"+s+"\n"));
		// 1 + 5 two-byte characters = 11 bytes
		assertEquals("aééééé", got.get(0));
	}

	@Test
	public void tooLongIsAnErrorWhenNotTruncating() {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < 600; i++) {
			s.append('z');
		}
		assertThrows(LineTooLongException.class, () -> decodeAll(new IrcLineDecoder(IrcMessage.MAX_LINE_BYTES, false), b(s+"\n")));
		assertThrows(LineTooLongException.class, () -> decodeAll(new IrcLineDecoder(IrcMessage.MAX_LINE_BYTES, false), b(s.toString())));
	}

	@Test
	public void latin1WhenNotUtf8() throws IOException {
		byte[] latin1 = {'c', 'a', 'f', (byte)0xE9, '\n'};
		assertEquals(Arrays.asList("café"), decodeAll(new IrcLineDecoder(), latin1));
		assertEquals(Arrays.asList("café"), decodeAll(new IrcLineDecoder(), b("café\n")));
	}

	@Test
	public void framesAreMessages() throws IOException {
		IrcLineDecoder d = new IrcLineDecoder();
		ByteBuffer frame = d.decode(ByteBuffer.wrap(b(":srv.example.net PING :token\r\n")));
		IrcMessage m = IrcMessage.parse(frame);
		assertTrue(m.is("PING"));
		assertEquals("token", m.getParam(0));
	}
}
