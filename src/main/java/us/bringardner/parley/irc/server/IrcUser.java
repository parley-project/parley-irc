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
package us.bringardner.parley.irc.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import us.bringardner.parley.irc.protocol.Prefix;

/**
 * A registered client of this server (RFC 2812 section 1.2.1): its nickname, user name, host,
 * real name and user modes. Owned by its {@link ClientSession}; other sessions may read it.
 *
 * @author Tony Bringardner
 */
public class IrcUser {

	private final ClientSession session;
	private final String user;
	private final String host;
	private final String realName;
	private final long signOn = System.currentTimeMillis();
	private volatile String nickname;
	// Sorted, so "+iw" always reads the same way
	private final Set<Character> modes = Collections.synchronizedSet(new TreeSet<Character>());
	// The channels the user is on, in the order joined
	private final Set<IrcChannel> channels = Collections.synchronizedSet(new LinkedHashSet<IrcChannel>());

	IrcUser(ClientSession session, String nickname, String user, String host, String realName) {
		this.session = session;
		this.nickname = nickname;
		this.user = user;
		this.host = host;
		this.realName = realName;
	}

	public ClientSession getSession() {
		return session;
	}

	public String getNickname() {
		return nickname;
	}

	void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public String getUser() {
		return user;
	}

	public String getHost() {
		return host;
	}

	public String getRealName() {
		return realName;
	}

	/** @return when the user registered (System.currentTimeMillis() time) */
	public long getSignOn() {
		return signOn;
	}

	/** @return {@code nick!user@host} */
	public Prefix getPrefix() {
		return Prefix.user(nickname, user, host);
	}

	public boolean hasMode(char mode) {
		return modes.contains(mode);
	}

	/** @return true if the mode was not set before */
	boolean setMode(char mode) {
		return modes.add(mode);
	}

	/** @return true if the mode was set */
	boolean clearMode(char mode) {
		return modes.remove(mode);
	}

	/** @return the user's modes, e.g. "+iw", or "+" for none */
	public String getModes() {
		StringBuilder ret = new StringBuilder("+");
		synchronized (modes) {
			for (Character c : modes) {
				ret.append(c.charValue());
			}
		}
		return ret.toString();
	}

	/** @return the channels the user is on (a copy, in the order joined) */
	public List<IrcChannel> getChannels() {
		synchronized (channels) {
			return new ArrayList<IrcChannel>(channels);
		}
	}

	public int getChannelCount() {
		return channels.size();
	}

	void joined(IrcChannel channel) {
		channels.add(channel);
	}

	void left(IrcChannel channel) {
		channels.remove(channel);
	}

	/** @return true for an operator (user mode o or O) */
	public boolean isOperator() {
		return hasMode('o') || hasMode('O');
	}

	public boolean isInvisible() {
		return hasMode('i');
	}

	/** @return true for a restricted connection (user mode r) */
	public boolean isRestricted() {
		return hasMode('r');
	}

	@Override
	public String toString() {
		return getPrefix().toString();
	}
}
