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

/**
 * Checks of parameter syntax from RFC 2812 section 2.3.1 and RFC 2811 section 2.1.
 *
 * @author Tony Bringardner
 */
public final class IrcSyntax {

	/** The longest nickname RFC 2812 allows; a server may allow longer ones */
	public static final int RFC_NICKNAME_LENGTH = 9;
	/** The longest channel name, its prefix character included (RFC 2811 section 2.1) */
	public static final int MAX_CHANNEL_LENGTH = 50;
	/** The longest channel key (RFC 2812 section 2.3.1) */
	public static final int MAX_KEY_LENGTH = 23;
	/** The length of a safe channel's ID, the part after '!' (RFC 2811 section 3.2) */
	public static final int CHANNEL_ID_LENGTH = 5;

	private IrcSyntax() {
	}

	/** {@code special = %x5B-60 / %x7B-7D}: [ ] \ ` _ ^ { | } */
	public static boolean isSpecial(char c) {
		return (c >= 0x5B && c <= 0x60) || (c >= 0x7B && c <= 0x7D);
	}

	/** {@code letter = %x41-5A / %x61-7A} */
	public static boolean isLetter(char c) {
		return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
	}

	/** {@code digit = %x30-39} */
	public static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	/** @return true if it's a nickname RFC 2812 allows (at most 9 characters) */
	public static boolean isNickname(String nick) {
		return isNickname(nick, RFC_NICKNAME_LENGTH);
	}

	/**
	 * {@code nickname = ( letter / special ) *8( letter / digit / special / "-" )}, with the
	 * length limit a server chooses.
	 *
	 * @param maxLength the longest nickname accepted
	 */
	public static boolean isNickname(String nick, int maxLength) {
		if( nick == null || nick.isEmpty() || nick.length() > maxLength ) {
			return false;
		}
		char first = nick.charAt(0);
		if( !isLetter(first) && !isSpecial(first) ) {
			return false;
		}
		for (int i = 1; i < nick.length(); i++) {
			char c = nick.charAt(i);
			if( !isLetter(c) && !isDigit(c) && !isSpecial(c) && c != '-' ) {
				return false;
			}
		}
		return true;
	}

	/** @return true for the characters a channel name starts with: '&amp;', '#', '+', '!' (RFC 2811 section 2.1) */
	public static boolean isChannelPrefix(char c) {
		return c == '&' || c == '#' || c == '+' || c == '!';
	}

	/** @return true if the name starts like a channel name (whether or not it is a valid one) */
	public static boolean looksLikeChannel(String name) {
		return name != null && !name.isEmpty() && isChannelPrefix(name.charAt(0));
	}

	/**
	 * A channel name (RFC 2811 section 2.1, RFC 2812 section 2.3.1): a prefix character, then a
	 * chanstring, optionally ":" and a second chanstring (the channel mask), 50 characters at most.
	 * A chanstring is any characters except NUL, BELL (^G), CR, LF, space, ',' and ':'. A safe
	 * channel's name ('!') starts with its 5-character channel ID (A-Z, 0-9).
	 *
	 * @return true if it's a valid channel name
	 */
	public static boolean isChannelName(String name) {
		if( name == null || name.length() < 2 || name.length() > MAX_CHANNEL_LENGTH ) {
			return false;
		}
		char prefix = name.charAt(0);
		if( !isChannelPrefix(prefix) ) {
			return false;
		}
		int start = 1;
		if( prefix == '!' ) {
			if( name.length() < 1+CHANNEL_ID_LENGTH+1 ) {
				return false;
			}
			for (int i = 1; i <= CHANNEL_ID_LENGTH; i++) {
				char c = name.charAt(i);
				if( !(c >= 'A' && c <= 'Z') && !isDigit(c) ) {
					return false;
				}
			}
			start = 1+CHANNEL_ID_LENGTH;
		}
		boolean colon = false;
		int partLength = 0;
		for (int i = start; i < name.length(); i++) {
			char c = name.charAt(i);
			if( c == ':' ) {
				if( colon || partLength == 0 ) {
					return false;
				}
				colon = true;
				partLength = 0;
				continue;
			}
			if( !isChanChar(c) ) {
				return false;
			}
			partLength++;
		}
		return partLength > 0;
	}

	private static boolean isChanChar(char c) {
		return c != '\0' && c != 7 && c != '\r' && c != '\n' && c != ' ' && c != ',' && c != ':';
	}

	/**
	 * {@code user = 1*( %x01-09 / %x0B-0C / %x0E-1F / %x21-3F / %x41-FF )}: any characters except
	 * NUL, CR, LF, space and '@'.
	 */
	public static boolean isUser(String user) {
		if( user == null || user.isEmpty() ) {
			return false;
		}
		for (int i = 0; i < user.length(); i++) {
			char c = user.charAt(i);
			if( c == '\0' || c == '\r' || c == '\n' || c == ' ' || c == '@' ) {
				return false;
			}
		}
		return true;
	}

	/**
	 * {@code key = 1*23( %x01-05 / %x07-08 / %x0C / %x0E-1F / %x21-7F )}: 1 to 23 US-ASCII
	 * characters except NUL, ACK, tabs, LF, VT, CR and space (as RFC 2812 lists them).
	 */
	public static boolean isKey(String key) {
		if( key == null || key.isEmpty() || key.length() > MAX_KEY_LENGTH ) {
			return false;
		}
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			boolean ok = (c >= 0x01 && c <= 0x05) || (c >= 0x07 && c <= 0x08) || c == 0x0C
					|| (c >= 0x0E && c <= 0x1F) || (c >= 0x21 && c <= 0x7F);
			if( !ok ) {
				return false;
			}
		}
		return true;
	}
}
