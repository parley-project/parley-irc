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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import us.bringardner.parley.io.IoUtils;
import us.bringardner.parley.net.nio.LineFrameDecoder;
import us.bringardner.parley.net.nio.LineFrameDecoder.LineEnd;

/**
 * One IRC message: an optional prefix, a command and up to 15 parameters (RFC 2812 section 2.3.1).
 * <pre>
 * message = [ ":" prefix SPACE ] command [ params ] crlf
 * command = 1*letter / 3digit
 * params  = *14( SPACE middle ) [ SPACE ":" trailing ]
 *         =/ 14( SPACE middle ) [ SPACE [ ":" ] trailing ]
 * </pre>
 * Messages are immutable. After parsing, all parameters are equal whether they were written as
 * middle or trailing (RFC 2812 note 1); {@link #toLine()} writes the last one as trailing (with ':')
 * only when it has to: it is empty, contains a space or starts with ':'.
 * <p>
 * Parsing is lenient where RFC 1459 was (one or more spaces between the parts) and strict where
 * both RFCs agree (no NUL, CR or LF, a command of letters or of three digits). Commands are kept
 * in upper case.
 * <p>
 * The RFCs name no character set (RFC 2812 section 2.2: a message is octets), so a message is
 * read as UTF-8, or as ISO-8859-1 when it isn't valid UTF-8, and written as UTF-8 (design
 * decision D7). A connection's input is split into messages by {@link #newDecoder()}.
 *
 * @author Tony Bringardner
 */
public final class IrcMessage {

	/** The most parameters a message has (RFC 2812 section 2.3) */
	public static final int MAX_PARAMS = 15;
	/** The longest message in bytes, counting the CR LF at its end (RFC 2812 section 2.3) */
	public static final int MAX_BYTES = 512;
	/** The longest message in bytes without its CR LF */
	public static final int MAX_LINE_BYTES = MAX_BYTES-2;

	private final Prefix prefix;
	private final String command;
	private final List<String> params;

	private IrcMessage(Prefix prefix, String command, List<String> params) {
		this.prefix = prefix;
		this.command = command;
		this.params = params;
	}

	/**
	 * A message with no prefix (what a client sends).
	 *
	 * @param command a command (letters, any case) or a three-digit numeric
	 * @param params the parameters, at most 15; only the last may be empty, contain a space or start with ':'
	 * @throws IrcFormatException if the command or a parameter can't be written in a message
	 */
	public static IrcMessage of(String command, String... params) {
		return of(null, command, Arrays.asList(params));
	}

	/**
	 * @param prefix who it's from, or null
	 * @param command a command (letters, any case) or a three-digit numeric
	 * @param params the parameters, at most 15; only the last may be empty, contain a space or start with ':'
	 * @throws IrcFormatException if the command or a parameter can't be written in a message
	 */
	public static IrcMessage of(Prefix prefix, String command, List<String> params) {
		String cmd = checkCommand(command);
		List<String> list = new ArrayList<String>(params);
		checkParams(list);
		return new IrcMessage(prefix, cmd, Collections.unmodifiableList(list));
	}

	/**
	 * A numeric reply: {@code :server NNN target params...}
	 *
	 * @param server the server's prefix
	 * @param numeric the reply
	 * @param target who it's for: the client's nickname, or "*" before it has one
	 * @param params the rest of the reply's parameters
	 */
	public static IrcMessage numeric(Prefix server, Numeric numeric, String target, String... params) {
		List<String> list = new ArrayList<String>(params.length+1);
		list.add(target);
		list.addAll(Arrays.asList(params));
		return of(server, numeric.getText(), list);
	}

	/** @return a copy of this message from another prefix (null for none) */
	public IrcMessage withPrefix(Prefix newPrefix) {
		return new IrcMessage(newPrefix, command, params);
	}

	private static String checkCommand(String command) {
		if( command == null || command.isEmpty() ) {
			throw new IrcFormatException("no command");
		}
		boolean digits = Character.isDigit(command.charAt(0));
		if( digits ) {
			if( command.length() != 3 ) {
				throw new IrcFormatException("a numeric command has three digits: "+command);
			}
		}
		for (int i = 0; i < command.length(); i++) {
			char c = command.charAt(i);
			boolean ok = digits ? (c >= '0' && c <= '9') : ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'));
			if( !ok ) {
				throw new IrcFormatException("a command is letters or three digits: "+command);
			}
		}
		return digits ? command : command.toUpperCase(Locale.ROOT);
	}

	private static void checkParams(List<String> params) {
		if( params.size() > MAX_PARAMS ) {
			throw new IrcFormatException("more than "+MAX_PARAMS+" parameters");
		}
		for (int i = 0; i < params.size(); i++) {
			String p = params.get(i);
			if( p == null ) {
				throw new IrcFormatException("parameter "+(i+1)+" is null");
			}
			for (int j = 0; j < p.length(); j++) {
				char c = p.charAt(j);
				if( c == '\0' || c == '\r' || c == '\n' ) {
					throw new IrcFormatException("parameter "+(i+1)+" contains NUL, CR or LF");
				}
			}
			if( i < params.size()-1 && needsTrailing(p) ) {
				throw new IrcFormatException("only the last parameter may be empty, contain a space or start with ':' (parameter "+(i+1)+")");
			}
		}
	}

	private static boolean needsTrailing(String param) {
		return param.isEmpty() || param.charAt(0) == ':' || param.indexOf(' ') >= 0;
	}

	/**
	 * Parse a line (without its CR LF).
	 *
	 * @param line the message as received
	 * @return the message
	 * @throws IrcFormatException if it isn't a valid message (empty, no command, NUL, CR or LF...)
	 */
	public static IrcMessage parse(String line) {
		if( line == null ) {
			throw new IrcFormatException("no message");
		}
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if( c == '\0' || c == '\r' || c == '\n' ) {
				throw new IrcFormatException("a message can't contain NUL, CR or LF");
			}
		}
		int pos = skipSpaces(line, 0);
		if( pos >= line.length() ) {
			throw new IrcFormatException("empty message");
		}
		Prefix prefix = null;
		if( line.charAt(pos) == ':' ) {
			int end = line.indexOf(' ', pos);
			if( end < 0 ) {
				throw new IrcFormatException("a prefix with no command");
			}
			prefix = Prefix.parse(line.substring(pos+1, end));
			pos = skipSpaces(line, end);
		}
		int end = line.indexOf(' ', pos);
		if( end < 0 ) {
			end = line.length();
		}
		String command = checkCommand(line.substring(pos, end));
		pos = end;
		List<String> params = new ArrayList<String>();
		while( pos < line.length() ) {
			pos = skipSpaces(line, pos);
			if( pos >= line.length() ) {
				break;
			}
			if( line.charAt(pos) == ':' ) {
				params.add(line.substring(pos+1));
				break;
			}
			if( params.size() == MAX_PARAMS-1 ) {
				// The 15th parameter is the rest of the line, ':' or not (RFC 2812)
				params.add(line.substring(pos));
				break;
			}
			end = line.indexOf(' ', pos);
			if( end < 0 ) {
				end = line.length();
			}
			params.add(line.substring(pos, end));
			pos = end;
		}
		return new IrcMessage(prefix, command, Collections.unmodifiableList(params));
	}

	/**
	 * Parse a frame from the connection: UTF-8, or ISO-8859-1 when it isn't valid UTF-8.
	 */
	public static IrcMessage parse(ByteBuffer frame) {
		return parse(IoUtils.decodeUtf8OrLatin1(frame));
	}

	/**
	 * How a connection's input is split into messages (RFC 2812 sections 2.3 and 2.3.1): CR and LF
	 * both end a message, so it may end in CR LF, CR or LF; empty messages are skipped; a message
	 * longer than 512 bytes with its CR LF is cut to that (never inside a UTF-8 character) and the
	 * rest of it dropped, as IRC servers commonly do.
	 *
	 * @return a new decoder, one per connection
	 */
	public static LineFrameDecoder newDecoder() {
		return new LineFrameDecoder(MAX_LINE_BYTES, LineEnd.CR_OR_LF, true, true);
	}

	private static int skipSpaces(String line, int pos) {
		while( pos < line.length() && line.charAt(pos) == ' ' ) {
			pos++;
		}
		return pos;
	}

	/** @return who it's from, or null */
	public Prefix getPrefix() {
		return prefix;
	}

	/** @return the command in upper case, or the three digits of a numeric reply */
	public String getCommand() {
		return command;
	}

	/** @return true if the command is a three-digit numeric reply */
	public boolean isNumeric() {
		return Character.isDigit(command.charAt(0));
	}

	/** @return the numeric reply's code, or -1 if the command isn't numeric */
	public int getNumericCode() {
		return isNumeric() ? Integer.parseInt(command) : -1;
	}

	/** @return the reply, or null if it isn't numeric or isn't one the RFCs name */
	public Numeric getNumeric() {
		return isNumeric() ? Numeric.of(getNumericCode()) : null;
	}

	/** @return true if the command is this one (case doesn't matter) */
	public boolean is(String commandName) {
		return command.equalsIgnoreCase(commandName);
	}

	/** @return the parameters (unmodifiable) */
	public List<String> getParams() {
		return params;
	}

	/** @return how many parameters there are */
	public int getParamCount() {
		return params.size();
	}

	/** @return the parameter at index (0 is the first), or null if there are fewer */
	public String getParam(int index) {
		return index >= 0 && index < params.size() ? params.get(index) : null;
	}

	/** @return the last parameter (the trailing one, when there is one), or null if there are none */
	public String getLastParam() {
		return params.isEmpty() ? null : params.get(params.size()-1);
	}

	/**
	 * @return the message as a line, without CR LF (the last parameter written as trailing when it
	 * has to be). It may be longer than {@link #MAX_LINE_BYTES}; {@link #toBytes()} limits it.
	 */
	public String toLine() {
		StringBuilder ret = new StringBuilder();
		if( prefix != null ) {
			ret.append(':').append(prefix).append(' ');
		}
		ret.append(command);
		for (int i = 0; i < params.size(); i++) {
			String p = params.get(i);
			ret.append(' ');
			if( i == params.size()-1 && (needsTrailing(p) || i == MAX_PARAMS-1) ) {
				ret.append(':');
			}
			ret.append(p);
		}
		return ret.toString();
	}

	/**
	 * @return the message as UTF-8 bytes ending in CR LF, at most {@link #MAX_BYTES}. A message that
	 * is too long has its last parameter cut (at a character boundary) when that is enough.
	 * @throws IrcFormatException if it's too long even with the last parameter empty
	 */
	public ByteBuffer toBytes() {
		byte[] line = toLine().getBytes(StandardCharsets.UTF_8);
		if( line.length > MAX_LINE_BYTES ) {
			line = shorten();
		}
		ByteBuffer ret = ByteBuffer.allocate(line.length+2);
		ret.put(line).put((byte)'\r').put((byte)'\n');
		ret.flip();
		return ret;
	}

	// Cut the last parameter so the line fits
	private byte[] shorten() {
		if( params.isEmpty() ) {
			throw new IrcFormatException("the message is longer than "+MAX_BYTES+" bytes");
		}
		List<String> head = new ArrayList<String>(params.subList(0, params.size()-1));
		head.add("");
		byte[] without = new IrcMessage(prefix, command, head).toLine().getBytes(StandardCharsets.UTF_8);
		// The emptied last parameter is written as " :" and so is already counted
		int room = MAX_LINE_BYTES-without.length;
		if( room < 0 ) {
			throw new IrcFormatException("the message is longer than "+MAX_BYTES+" bytes even without its last parameter");
		}
		byte[] last = getLastParam().getBytes(StandardCharsets.UTF_8);
		int cut = utf8Boundary(last, room);
		byte[] ret = new byte[without.length+cut];
		System.arraycopy(without, 0, ret, 0, without.length);
		System.arraycopy(last, 0, ret, without.length, cut);
		return ret;
	}

	// A length of at most max that doesn't end inside a UTF-8 character
	private static int utf8Boundary(byte[] utf8, int max) {
		if( utf8.length <= max ) {
			return utf8.length;
		}
		int cut = max;
		// Back up over continuation bytes (10xxxxxx) to the start of the character at max
		while( cut > 0 && max-cut < 3 && (utf8[cut] & 0xC0) == 0x80 ) {
			cut--;
		}
		return cut;
	}

	@Override
	public String toString() {
		return toLine();
	}

	@Override
	public boolean equals(Object obj) {
		if( !(obj instanceof IrcMessage) ) {
			return false;
		}
		IrcMessage other = (IrcMessage) obj;
		return command.equals(other.command) && params.equals(other.params) && Objects.equals(prefix, other.prefix);
	}

	@Override
	public int hashCode() {
		return Objects.hash(prefix, command, params);
	}
}
