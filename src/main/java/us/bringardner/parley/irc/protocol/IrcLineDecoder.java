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

import java.io.IOException;
import java.nio.ByteBuffer;

import us.bringardner.parley.io.LineTooLongException;
import us.bringardner.parley.net.nio.IFrameDecoder;

/**
 * Splits a connection's input into IRC messages, for parley-net's NIO connections.
 * <p>
 * CR and LF are both message separators and empty messages are skipped (RFC 2812 section 2.3.1),
 * so a message may end in CR LF, CR or LF. A frame is the message's bytes without the separator.
 * <p>
 * A message may be at most 512 bytes with its CR LF (RFC 2812 section 2.3). A longer one is cut
 * to the limit (at a UTF-8 character boundary) and the rest up to its end is dropped, as IRC
 * servers commonly do; with {@code truncate} false it is an error instead, which closes the
 * connection.
 *
 * @author Tony Bringardner
 */
public class IrcLineDecoder implements IFrameDecoder {

	private final int maxLength;
	private final boolean truncate;
	// The message ended with a CR at the end of the input: a LF that comes next belongs to it
	private boolean skipLf;
	// How far past the position the last call looked, so a long message isn't scanned again
	private int scanned;
	// The start of a message that was too long, kept while the rest of it is dropped
	private ByteBuffer cut;

	/** Messages of at most 510 bytes (512 with CR LF), longer ones cut */
	public IrcLineDecoder() {
		this(IrcMessage.MAX_LINE_BYTES, true);
	}

	/**
	 * @param maxLength the longest message in bytes, without its line end
	 * @param truncate true to cut a longer message, false to fail with a {@link LineTooLongException}
	 */
	public IrcLineDecoder(int maxLength, boolean truncate) {
		if( maxLength <= 0 ) {
			throw new IllegalArgumentException("maxLength must be > 0");
		}
		this.maxLength = maxLength;
		this.truncate = truncate;
	}

	public int getMaxLength() {
		return maxLength;
	}

	public boolean isTruncate() {
		return truncate;
	}

	@Override
	public ByteBuffer decode(ByteBuffer in) throws IOException {
		while( in.hasRemaining() ) {
			if( skipLf ) {
				skipLf = false;
				if( in.get(in.position()) == '\n' ) {
					in.position(in.position()+1);
					continue;
				}
			}
			int start = in.position();
			int limit = in.limit();
			int end = -1;
			for (int i = start+scanned; i < limit; i++) {
				byte b = in.get(i);
				if( b == '\r' || b == '\n' ) {
					end = i;
					break;
				}
			}
			if( end < 0 ) {
				scanned = limit-start;
				if( cut != null ) {
					// Still dropping the rest of a message that was too long
					in.position(limit);
					scanned = 0;
				} else if( scanned > maxLength ) {
					if( !truncate ) {
						throw new LineTooLongException(maxLength);
					}
					cut = cutCopy(in, start);
					in.position(limit);
					scanned = 0;
				}
				return null;
			}
			scanned = 0;
			int len = end-start;
			ByteBuffer frame;
			if( cut != null ) {
				frame = cut;
				cut = null;
				in.position(end);
			} else if( len > maxLength ) {
				if( !truncate ) {
					throw new LineTooLongException(maxLength);
				}
				frame = cutCopy(in, start);
				in.position(end);
			} else {
				frame = len == 0 ? null : IFrameDecoder.copy(in, len);
			}
			// The separator: CR LF, CR or LF
			byte sep = in.get(in.position());
			in.position(in.position()+1);
			if( sep == '\r' ) {
				if( in.hasRemaining() ) {
					if( in.get(in.position()) == '\n' ) {
						in.position(in.position()+1);
					}
				} else {
					skipLf = true;
				}
			}
			if( frame != null ) {
				return frame;
			}
			// An empty message: skip it
		}
		return null;
	}

	// A copy of the first maxLength bytes at start (fewer, to keep a UTF-8 character whole)
	private ByteBuffer cutCopy(ByteBuffer in, int start) {
		byte[] bytes = new byte[Math.min(maxLength+1, in.limit()-start)];
		ByteBuffer src = in.duplicate();
		src.position(start);
		src.get(bytes);
		int len = IrcText.utf8Boundary(bytes, maxLength);
		ByteBuffer ret = ByteBuffer.allocate(len);
		ret.put(bytes, 0, len);
		ret.flip();
		return ret;
	}
}
