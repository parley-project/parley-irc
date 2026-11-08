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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import us.bringardner.parley.irc.protocol.IrcMessage;
import us.bringardner.parley.irc.protocol.Masks;

/**
 * A channel of stage 1 (RFC 2811 section 2): '#' (network-wide) or '&amp;' (this server's),
 * with the modes biklmnopstv (RFC 2811 section 4).
 * <p>
 * Every method that reads or changes the channel's state holds the channel's lock, and messages to
 * the members are queued ({@link ClientSession#send(IrcMessage)}) while it is held, so every
 * member sees the channel's changes in the same order. Sending never blocks.
 * <p>
 * A channel is made by the first JOIN and goes when its last member leaves ({@link IrcNode}).
 *
 * @author Tony Bringardner
 */
public class IrcChannel {

	/** The modes that are flags, with no parameter */
	public static final String FLAG_MODES = "imnpst";
	/** The most bans a channel holds */
	public static final int MAX_BANS = 100;

	/** What a member may do on the channel */
	static final class Member {
		boolean op;
		boolean voice;

		/** @return "@", "+" or "" (RFC 2812 section 3.2.5) */
		String prefix() {
			return op ? "@" : voice ? "+" : "";
		}
	}

	private final IrcNode node;
	private final String name;
	private final long created = System.currentTimeMillis();
	private final LinkedHashMap<IrcUser, Member> members = new LinkedHashMap<IrcUser, Member>();
	private final Set<Character> flags = new TreeSet<Character>();
	private final List<String> bans = new ArrayList<String>();
	private final Set<IrcUser> invited = new HashSet<IrcUser>();
	private String key;
	private int limit;
	private String topic;
	private String topicBy;
	private long topicTime;
	// Set once the last member has left; a JOIN racing with that makes a new channel
	private boolean gone;

	IrcChannel(IrcNode node, String name) {
		this.node = node;
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public IrcNode getNode() {
		return node;
	}

	/** @return when the channel was made (System.currentTimeMillis() time) */
	public long getCreated() {
		return created;
	}

	synchronized boolean isGone() {
		return gone;
	}

	void markGone() {
		gone = true;
	}

	// ------------------------------------------------------------------ members

	public synchronized boolean isMember(IrcUser u) {
		return members.containsKey(u);
	}

	public synchronized boolean isOperator(IrcUser u) {
		Member m = members.get(u);
		return m != null && m.op;
	}

	public synchronized boolean isVoiced(IrcUser u) {
		Member m = members.get(u);
		return m != null && m.voice;
	}

	public synchronized int getMemberCount() {
		return members.size();
	}

	/** @return the members (a copy) */
	public synchronized List<IrcUser> getMembers() {
		return new ArrayList<IrcUser>(members.keySet());
	}

	synchronized Member member(IrcUser u) {
		return members.get(u);
	}

	/** @return the members as NAMES shows them ("@nick", "+nick", "nick"); invisible users only if asked for */
	synchronized List<String> names(boolean includeInvisible) {
		List<String> ret = new ArrayList<String>();
		for (Map.Entry<IrcUser, Member> e : members.entrySet()) {
			if( includeInvisible || !e.getKey().isInvisible() ) {
				ret.add(e.getValue().prefix()+e.getKey().getNickname());
			}
		}
		return ret;
	}

	/** Add a member (the first one becomes the channel's operator, RFC 2811 section 3.1) */
	synchronized void add(IrcUser u) {
		Member m = new Member();
		m.op = members.isEmpty();
		members.put(u, m);
		invited.remove(u);
	}

	/** @return true if this was the last member */
	synchronized boolean remove(IrcUser u) {
		members.remove(u);
		invited.remove(u);
		return members.isEmpty();
	}

	/** Queue a message for every member but one (null: all) */
	synchronized void send(IrcMessage msg, IrcUser except) {
		for (IrcUser u : members.keySet()) {
			if( u != except ) {
				u.getSession().send(msg);
			}
		}
	}

	// ------------------------------------------------------------------ modes

	public synchronized boolean hasFlag(char flag) {
		return flags.contains(flag);
	}

	/** @return true if the flag changed */
	synchronized boolean setFlag(char flag, boolean on) {
		return on ? flags.add(flag) : flags.remove(flag);
	}

	public synchronized boolean isSecret() {
		return flags.contains('s');
	}

	public synchronized boolean isPrivate() {
		return flags.contains('p');
	}

	public synchronized String getKey() {
		return key;
	}

	synchronized void setKey(String key) {
		this.key = key;
	}

	/** @return the most members, 0 for no limit */
	public synchronized int getLimit() {
		return limit;
	}

	synchronized void setLimit(int limit) {
		this.limit = limit;
	}

	/**
	 * @param showKey whether to show the key's value (to a member) or not
	 * @return the mode string and its parameters, e.g. ["+klnt", "secret", "10"]
	 */
	synchronized List<String> modes(boolean showKey) {
		StringBuilder m = new StringBuilder("+");
		for (char c : flags) {
			m.append(c);
		}
		List<String> params = new ArrayList<String>();
		if( key != null ) {
			m.append('k');
			params.add(showKey ? key : "*");
		}
		if( limit > 0 ) {
			m.append('l');
			params.add(Integer.toString(limit));
		}
		List<String> ret = new ArrayList<String>();
		ret.add(m.toString());
		ret.addAll(params);
		return ret;
	}

	// ------------------------------------------------------------------ bans and invitations

	/** @return the ban masks (a copy) */
	public synchronized List<String> getBans() {
		return new ArrayList<String>(bans);
	}

	/** @return true if the mask was added; false if it was there or the list is full */
	synchronized boolean addBan(String mask) {
		for (String b : bans) {
			if( node.getCaseMapping().equals(b, mask) ) {
				return false;
			}
		}
		if( bans.size() >= MAX_BANS ) {
			return false;
		}
		bans.add(mask);
		return true;
	}

	/** @return the mask removed (as it was set), or null */
	synchronized String removeBan(String mask) {
		for (int i = 0; i < bans.size(); i++) {
			if( node.getCaseMapping().equals(bans.get(i), mask) ) {
				return bans.remove(i);
			}
		}
		return null;
	}

	public synchronized boolean isBanned(IrcUser u) {
		String who = u.getPrefix().toString();
		for (String b : bans) {
			if( Masks.matches(b, who, node.getCaseMapping()) ) {
				return true;
			}
		}
		return false;
	}

	synchronized void invite(IrcUser u) {
		invited.add(u);
	}

	public synchronized boolean isInvited(IrcUser u) {
		return invited.contains(u);
	}

	// ------------------------------------------------------------------ topic

	/** @return the topic, or null */
	public synchronized String getTopic() {
		return topic;
	}

	/** @return who set the topic (a nickname), or null */
	public synchronized String getTopicBy() {
		return topicBy;
	}

	/** @return when the topic was set (System.currentTimeMillis() time), 0 if never */
	public synchronized long getTopicTime() {
		return topicTime;
	}

	/** @param topic the new topic; empty clears it */
	synchronized void setTopic(String topic, String by) {
		this.topic = topic == null || topic.isEmpty() ? null : topic;
		this.topicBy = by;
		this.topicTime = System.currentTimeMillis();
	}

	/**
	 * A ban mask in full form: {@code nick} becomes {@code nick!*@*}, {@code user@host}
	 * {@code *!user@host}, {@code nick!user} {@code nick!user@*}.
	 */
	static String normalizeMask(String mask) {
		int bang = mask.indexOf('!');
		int at = mask.indexOf('@');
		if( bang < 0 && at < 0 ) {
			return mask+"!*@*";
		}
		if( bang < 0 ) {
			return "*!"+mask;
		}
		if( at < 0 ) {
			return mask+"@*";
		}
		return mask;
	}

	@Override
	public String toString() {
		return name;
	}
}
