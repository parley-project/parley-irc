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

import java.util.Objects;

/**
 * Who a message comes from: the part after the leading ':' of a message (RFC 2812 section 2.3.1).
 * <pre>
 * prefix = servername / ( nickname [ [ "!" user ] "@" host ] )
 * </pre>
 * A server name and a nickname can look the same (a server named {@code localhost}): a name with a
 * '.' and no '!' or '@' is taken as a server name, because a nickname can't contain a '.'.
 *
 * @author Tony Bringardner
 */
public final class Prefix {

	private final String name;
	private final String user;
	private final String host;
	private final boolean server;

	private Prefix(String name, String user, String host, boolean server) {
		if( name == null || name.isEmpty() ) {
			throw new IrcFormatException("a prefix needs a name");
		}
		check(name, "name");
		if( user != null ) {
			if( user.isEmpty() ) {
				throw new IrcFormatException("the user part of a prefix is empty");
			}
			check(user, "user");
		}
		if( host != null ) {
			if( host.isEmpty() ) {
				throw new IrcFormatException("the host part of a prefix is empty");
			}
			check(host, "host");
		}
		if( user != null && host == null ) {
			throw new IrcFormatException("a prefix with a user needs a host (nick!user@host)");
		}
		this.name = name;
		this.user = user;
		this.host = host;
		this.server = server;
	}

	private static void check(String part, String what) {
		for (int i = 0; i < part.length(); i++) {
			char c = part.charAt(i);
			if( c == ' ' || c == '\0' || c == '\r' || c == '\n' ) {
				throw new IrcFormatException("the "+what+" of a prefix can't contain a space, NUL, CR or LF");
			}
		}
	}

	/**
	 * @param text the prefix without the leading ':' (e.g. {@code irc.example.net} or {@code tony!tb@host})
	 * @throws IrcFormatException if it's empty or a part is empty
	 */
	public static Prefix parse(String text) {
		if( text == null || text.isEmpty() ) {
			throw new IrcFormatException("empty prefix");
		}
		int at = text.indexOf('@');
		String host = null;
		String rest = text;
		if( at >= 0 ) {
			host = text.substring(at+1);
			rest = text.substring(0, at);
		}
		int bang = rest.indexOf('!');
		String user = null;
		String name = rest;
		if( bang >= 0 ) {
			user = rest.substring(bang+1);
			name = rest.substring(0, bang);
		}
		boolean server = user == null && host == null && name.indexOf('.') >= 0;
		return new Prefix(name, user, host, server);
	}

	/** A server's prefix, e.g. {@code irc.example.net} */
	public static Prefix server(String serverName) {
		return new Prefix(serverName, null, null, true);
	}

	/** A user's prefix, {@code nick!user@host}; user and host may be null (nick, or nick@host) */
	public static Prefix user(String nickname, String user, String host) {
		return new Prefix(nickname, user, host, false);
	}

	/** @return the server name or the nickname */
	public String getName() {
		return name;
	}

	/** @return the nickname, or null when this is a server's prefix */
	public String getNickname() {
		return server ? null : name;
	}

	/** @return the server name, or null when this is a user's prefix */
	public String getServerName() {
		return server ? name : null;
	}

	/** @return the user (after '!'), or null */
	public String getUser() {
		return user;
	}

	/** @return the host (after '@'), or null */
	public String getHost() {
		return host;
	}

	/** @return true for a server's prefix, false for a user's (see the class description) */
	public boolean isServer() {
		return server;
	}

	/** @return the prefix as written in a message, without the leading ':' */
	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder(name);
		if( user != null ) {
			ret.append('!').append(user);
		}
		if( host != null ) {
			ret.append('@').append(host);
		}
		return ret.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if( !(obj instanceof Prefix) ) {
			return false;
		}
		Prefix other = (Prefix) obj;
		return server == other.server && name.equals(other.name) && Objects.equals(user, other.user) && Objects.equals(host, other.host);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, user, host, server);
	}
}
