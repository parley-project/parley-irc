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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * The character set of IRC text. The RFCs name none (RFC 2812 section 2.2: a message is octets),
 * so parley-irc reads a message as UTF-8, or as ISO-8859-1 (Latin-1) when it isn't valid UTF-8,
 * and writes UTF-8 (design decision D7). Latin-1 maps every octet to a character, so nothing a
 * peer sends is lost.
 *
 * @author Tony Bringardner
 */
public final class IrcText {

	private IrcText() {
	}

	/**
	 * @param frame a message's bytes (position to limit), not changed
	 * @return the text: UTF-8 when the bytes are valid UTF-8, otherwise ISO-8859-1
	 */
	public static String decode(ByteBuffer frame) {
		CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		try {
			CharBuffer chars = utf8.decode(frame.duplicate());
			return chars.toString();
		} catch (CharacterCodingException e) {
			return StandardCharsets.ISO_8859_1.decode(frame.duplicate()).toString();
		}
	}

	/** @see #decode(ByteBuffer) */
	public static String decode(byte[] bytes) {
		return decode(ByteBuffer.wrap(bytes));
	}

	/** @return the text as UTF-8 */
	public static byte[] encode(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Where to cut UTF-8 bytes so no character is split.
	 *
	 * @param utf8 the bytes
	 * @param max the most bytes wanted
	 * @return a length of at most max that doesn't end inside a UTF-8 character (max, or less);
	 * bytes that aren't UTF-8 are cut at max
	 */
	public static int utf8Boundary(byte[] utf8, int max) {
		return utf8Boundary(utf8, 0, utf8.length, max);
	}

	/**
	 * Where to cut a run of UTF-8 bytes so no character is split.
	 *
	 * @param bytes the buffer
	 * @param offset where the run starts
	 * @param length the run's length
	 * @param max the most bytes wanted
	 * @return a length of at most max (and at most length)
	 */
	public static int utf8Boundary(byte[] bytes, int offset, int length, int max) {
		if( length <= max ) {
			return length;
		}
		if( max <= 0 ) {
			return 0;
		}
		// Back up over continuation bytes (10xxxxxx) to the start of the character at max
		int cut = max;
		int back = 0;
		while( cut > 0 && back < 3 && (bytes[offset+cut] & 0xC0) == 0x80 ) {
			cut--;
			back++;
		}
		if( (bytes[offset+cut] & 0xC0) == 0x80 ) {
			// Not UTF-8 (too many continuation bytes)
			return max;
		}
		return cut;
	}
}
