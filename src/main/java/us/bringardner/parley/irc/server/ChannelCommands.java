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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import us.bringardner.parley.irc.protocol.IrcMessage;
import us.bringardner.parley.irc.protocol.IrcSyntax;
import us.bringardner.parley.irc.protocol.Numeric;

/**
 * The commands of stage 1 step (b), channels and messages, for one {@link ClientSession}: JOIN,
 * PART, MODE for channels, TOPIC, NAMES, LIST, INVITE, KICK (RFC 2812 section 3.2), PRIVMSG and
 * NOTICE (section 3.3), and what a user's NICK and QUIT tell the people on their channels.
 * <p>
 * Called on the session's own connection thread. A channel's state is read and changed holding
 * its lock (see {@link IrcChannel}).
 * <p>
 * Private and secret channels (RFC 2811 section 4.2.6, decision D44): to someone not on it, a
 * secret channel acts as if it doesn't exist (TOPIC, NAMES, LIST, MODE, PART), and a private one
 * is listed by LIST as "Prv" with no topic and left out of NAMES with no parameter.
 *
 * @author Tony Bringardner
 */
class ChannelCommands {

	/** The most mode changes with a parameter in one MODE (RFC 2812 section 3.2.3) */
	static final int MAX_MODE_PARAMS = 3;
	/** Room left in a 353 line for the names, so it stays under 512 bytes */
	private static final int NAMES_ROOM = 400;

	private final ClientSession session;
	private final IrcNode node;

	ChannelCommands(ClientSession session, IrcNode node) {
		this.session = session;
		this.node = node;
	}

	private IrcUser me() {
		return session.getUser();
	}

	private void numeric(Numeric numeric, String... params) {
		session.numeric(numeric, params);
	}

	private static List<String> split(String list) {
		List<String> ret = new ArrayList<String>();
		for (String s : list.split(",")) {
			if( !s.isEmpty() ) {
				ret.add(s);
			}
		}
		return ret;
	}

	/** @return the channel, unless it doesn't exist or is secret and the user isn't on it */
	private IrcChannel visible(String name) {
		IrcChannel ch = node.findChannel(name);
		if( ch == null || (ch.isSecret() && !ch.isMember(me())) ) {
			return null;
		}
		return ch;
	}

	// Called with the channel's lock held
	private void removeMember(IrcChannel ch, IrcUser u) {
		boolean last = ch.remove(u);
		u.left(ch);
		if( last ) {
			node.channelEmptied(ch);
		}
	}

	// ------------------------------------------------------------------ JOIN

	/** RFC 2812 section 3.2.1 */
	void join(IrcMessage msg) {
		if( !session.needParams(msg, 1) ) {
			return;
		}
		if( msg.getParam(0).equals("0") ) {
			for (IrcChannel ch : me().getChannels()) {
				part(ch, null);
			}
			return;
		}
		List<String> names = split(msg.getParam(0));
		List<String> keys = msg.getParamCount() > 1 ? Arrays.asList(msg.getParam(1).split(",", -1)) : Collections.<String>emptyList();
		for (int i = 0; i < names.size(); i++) {
			join(names.get(i), i < keys.size() ? keys.get(i) : null);
		}
	}

	private void join(String name, String key) {
		IrcUser u = me();
		char type = name.charAt(0);
		if( (type != '#' && type != '&') || !IrcSyntax.isChannelName(name) ) {
			numeric(Numeric.ERR_NOSUCHCHANNEL, name, "No such channel");
			return;
		}
		while( true ) {
			IrcChannel ch = node.channelToJoin(name);
			synchronized (ch) {
				if( ch.isGone() ) {
					// its last member left as we found it; make it again
					continue;
				}
				if( ch.isMember(u) ) {
					return;
				}
				if( u.getChannelCount() >= node.getMaxChannels() ) {
					numeric(Numeric.ERR_TOOMANYCHANNELS, name, "You have joined too many channels");
					leaveIfEmpty(ch);
					return;
				}
				if( ch.getMemberCount() > 0 ) {
					boolean invited = ch.isInvited(u);
					if( ch.isBanned(u) ) {
						numeric(Numeric.ERR_BANNEDFROMCHAN, ch.getName(), "Cannot join channel (+b)");
						return;
					}
					if( ch.hasFlag('i') && !invited ) {
						numeric(Numeric.ERR_INVITEONLYCHAN, ch.getName(), "Cannot join channel (+i)");
						return;
					}
					if( ch.getKey() != null && !ch.getKey().equals(key) ) {
						numeric(Numeric.ERR_BADCHANNELKEY, ch.getName(), "Cannot join channel (+k)");
						return;
					}
					if( ch.getLimit() > 0 && ch.getMemberCount() >= ch.getLimit() ) {
						numeric(Numeric.ERR_CHANNELISFULL, ch.getName(), "Cannot join channel (+l)");
						return;
					}
				}
				ch.add(u);
				u.joined(ch);
				ch.send(IrcMessage.of(u.getPrefix(), "JOIN", Collections.singletonList(ch.getName())), null);
				if( ch.getTopic() != null ) {
					numeric(Numeric.RPL_TOPIC, ch.getName(), ch.getTopic());
				}
				sendNames(ch);
				numeric(Numeric.RPL_ENDOFNAMES, ch.getName(), "End of NAMES list");
				return;
			}
		}
	}

	// A channel made for a JOIN that then failed has no members; it mustn't stay
	private void leaveIfEmpty(IrcChannel ch) {
		if( ch.getMemberCount() == 0 ) {
			node.channelEmptied(ch);
		}
	}

	// ------------------------------------------------------------------ PART

	/** RFC 2812 section 3.2.2 */
	void part(IrcMessage msg) {
		if( !session.needParams(msg, 1) ) {
			return;
		}
		String reason = msg.getParamCount() > 1 ? msg.getParam(1) : null;
		for (String name : split(msg.getParam(0))) {
			IrcChannel ch = visible(name);
			if( ch == null ) {
				numeric(Numeric.ERR_NOSUCHCHANNEL, name, "No such channel");
				continue;
			}
			part(ch, reason);
		}
	}

	private void part(IrcChannel ch, String reason) {
		IrcUser u = me();
		synchronized (ch) {
			if( !ch.isMember(u) ) {
				numeric(Numeric.ERR_NOTONCHANNEL, ch.getName(), "You're not on that channel");
				return;
			}
			List<String> params = reason == null ? Collections.singletonList(ch.getName()) : Arrays.asList(ch.getName(), reason);
			ch.send(IrcMessage.of(u.getPrefix(), "PART", params), null);
			removeMember(ch, u);
		}
	}

	// ------------------------------------------------------------------ MODE

	/** RFC 2812 section 3.2.3, RFC 2811 section 4 */
	void mode(IrcMessage msg) {
		String name = msg.getParam(0);
		IrcChannel ch = visible(name);
		if( ch == null ) {
			numeric(Numeric.ERR_NOSUCHCHANNEL, name, "No such channel");
			return;
		}
		IrcUser u = me();
		synchronized (ch) {
			if( msg.getParamCount() == 1 ) {
				List<String> params = new ArrayList<String>();
				params.add(ch.getName());
				params.addAll(ch.modes(ch.isMember(u)));
				numeric(Numeric.RPL_CHANNELMODEIS, params.toArray(new String[0]));
				return;
			}
			changeModes(ch, u, msg);
		}
	}

	// Called with the channel's lock held
	private void changeModes(IrcChannel ch, IrcUser u, IrcMessage msg) {
		boolean op = ch.isOperator(u);
		boolean refused = false;
		boolean listedBans = false;
		int withParams = 0;
		StringBuilder changes = new StringBuilder();
		List<String> changeParams = new ArrayList<String>();
		char written = 0;
		int next = 1;
		while( next < msg.getParamCount() ) {
			String modes = msg.getParam(next++);
			char sign = '+';
			for (char c : modes.toCharArray()) {
				if( c == '+' || c == '-' ) {
					sign = c;
					continue;
				}
				boolean adding = sign == '+';
				String arg = null;
				boolean takesArg = c == 'o' || c == 'v' || c == 'b' || c == 'k' || (c == 'l' && adding);
				if( takesArg && next < msg.getParamCount() ) {
					arg = msg.getParam(next++);
				}
				if( c == 'b' && arg == null ) {
					if( !listedBans ) {
						listedBans = true;
						for (String mask : ch.getBans()) {
							numeric(Numeric.RPL_BANLIST, ch.getName(), mask);
						}
						numeric(Numeric.RPL_ENDOFBANLIST, ch.getName(), "End of channel ban list");
					}
					continue;
				}
				if( "biklmnopstv".indexOf(c) < 0 ) {
					numeric(Numeric.ERR_UNKNOWNMODE, String.valueOf(c), "is unknown mode char to me for "+ch.getName());
					continue;
				}
				if( !op ) {
					refused = true;
					continue;
				}
				if( takesArg ) {
					if( arg == null && c != 'k' ) {
						// +o, +v, +l without their parameter: nothing to do
						continue;
					}
					if( withParams >= MAX_MODE_PARAMS ) {
						continue;
					}
					withParams++;
				}
				String shown = applyMode(ch, c, adding, arg);
				if( shown == null ) {
					continue;
				}
				if( written != sign ) {
					changes.append(sign);
					written = sign;
				}
				changes.append(c);
				if( !shown.isEmpty() ) {
					changeParams.add(shown);
				}
			}
		}
		if( refused ) {
			numeric(Numeric.ERR_CHANOPRIVSNEEDED, ch.getName(), "You're not channel operator");
		}
		if( changes.length() > 0 ) {
			List<String> params = new ArrayList<String>();
			params.add(ch.getName());
			params.add(changes.toString());
			params.addAll(changeParams);
			ch.send(IrcMessage.of(u.getPrefix(), "MODE", params), null);
		}
	}

	/**
	 * Apply one mode change, the channel's lock held.
	 * @return null if nothing changed, else the parameter to show with it ("" for none)
	 */
	private String applyMode(IrcChannel ch, char c, boolean adding, String arg) {
		switch (c) {
			case 'o':
			case 'v': {
				IrcUser target = node.findUser(arg);
				if( target == null ) {
					numeric(Numeric.ERR_NOSUCHNICK, arg, "No such nick/channel");
					return null;
				}
				IrcChannel.Member m = ch.member(target);
				if( m == null ) {
					numeric(Numeric.ERR_USERNOTINCHANNEL, target.getNickname(), ch.getName(), "They aren't on that channel");
					return null;
				}
				boolean was = c == 'o' ? m.op : m.voice;
				if( was == adding ) {
					return null;
				}
				if( c == 'o' ) {
					m.op = adding;
				} else {
					m.voice = adding;
				}
				return target.getNickname();
			}
			case 'b': {
				String mask = IrcChannel.normalizeMask(arg);
				if( adding ) {
					return ch.addBan(mask) ? mask : null;
				}
				return ch.removeBan(mask);
			}
			case 'k':
				if( adding ) {
					if( arg == null || !IrcSyntax.isKey(arg) ) {
						return null;
					}
					if( ch.getKey() != null ) {
						numeric(Numeric.ERR_KEYSET, ch.getName(), "Channel key already set");
						return null;
					}
					ch.setKey(arg);
					return arg;
				} else {
					String old = ch.getKey();
					if( old == null ) {
						return null;
					}
					ch.setKey(null);
					return old;
				}
			case 'l':
				if( adding ) {
					int limit;
					try {
						limit = Integer.parseInt(arg);
					} catch (NumberFormatException e) {
						return null;
					}
					if( limit <= 0 || limit == ch.getLimit() ) {
						return null;
					}
					ch.setLimit(limit);
					return Integer.toString(limit);
				}
				if( ch.getLimit() == 0 ) {
					return null;
				}
				ch.setLimit(0);
				return "";
			default:
				return ch.setFlag(c, adding) ? "" : null;
		}
	}

	// ------------------------------------------------------------------ TOPIC

	/** RFC 2812 section 3.2.4 */
	void topic(IrcMessage msg) {
		if( !session.needParams(msg, 1) ) {
			return;
		}
		String name = msg.getParam(0);
		IrcChannel ch = visible(name);
		if( ch == null ) {
			numeric(Numeric.ERR_NOSUCHCHANNEL, name, "No such channel");
			return;
		}
		IrcUser u = me();
		synchronized (ch) {
			if( msg.getParamCount() == 1 ) {
				if( ch.getTopic() == null ) {
					numeric(Numeric.RPL_NOTOPIC, ch.getName(), "No topic is set");
				} else {
					numeric(Numeric.RPL_TOPIC, ch.getName(), ch.getTopic());
				}
				return;
			}
			if( !ch.isMember(u) ) {
				numeric(Numeric.ERR_NOTONCHANNEL, ch.getName(), "You're not on that channel");
				return;
			}
			if( ch.hasFlag('t') && !ch.isOperator(u) ) {
				numeric(Numeric.ERR_CHANOPRIVSNEEDED, ch.getName(), "You're not channel operator");
				return;
			}
			String topic = msg.getParam(1);
			ch.setTopic(topic, u.getNickname());
			ch.send(IrcMessage.of(u.getPrefix(), "TOPIC", Arrays.asList(ch.getName(), topic)), null);
		}
	}

	// ------------------------------------------------------------------ NAMES

	/** RFC 2812 section 3.2.5 */
	void names(IrcMessage msg) {
		if( msg.getParamCount() > 1 && !session.isThisServer(msg.getParam(1)) ) {
			numeric(Numeric.ERR_NOSUCHSERVER, msg.getParam(1), "No such server");
			return;
		}
		IrcUser u = me();
		if( msg.getParamCount() == 0 || msg.getParam(0).isEmpty() ) {
			Set<IrcUser> shown = new LinkedHashSet<IrcUser>();
			for (IrcChannel ch : node.getChannels()) {
				synchronized (ch) {
					boolean member = ch.isMember(u);
					if( ch.isGone() || ((ch.isSecret() || ch.isPrivate()) && !member) ) {
						continue;
					}
					sendNames(ch);
					for (IrcUser m : ch.getMembers()) {
						if( member || !m.isInvisible() ) {
							shown.add(m);
						}
					}
				}
			}
			List<String> rest = new ArrayList<String>();
			for (IrcUser other : node.getUsers()) {
				if( !shown.contains(other) && (other == u || !other.isInvisible()) ) {
					rest.add(other.getNickname());
				}
			}
			sendNameLines("*", "*", rest);
			numeric(Numeric.RPL_ENDOFNAMES, "*", "End of NAMES list");
			return;
		}
		for (String name : split(msg.getParam(0))) {
			IrcChannel ch = visible(name);
			if( ch != null ) {
				synchronized (ch) {
					sendNames(ch);
				}
			}
			numeric(Numeric.RPL_ENDOFNAMES, ch == null ? name : ch.getName(), "End of NAMES list");
		}
	}

	// 353 lines for a channel, the channel's lock held; RFC 2812: "@" secret, "*" private, "=" public
	private void sendNames(IrcChannel ch) {
		String type = ch.isSecret() ? "@" : ch.isPrivate() ? "*" : "=";
		sendNameLines(type, ch.getName(), ch.names(ch.isMember(me())));
	}

	private void sendNameLines(String type, String channel, List<String> names) {
		StringBuilder line = new StringBuilder();
		for (String n : names) {
			if( line.length() > 0 && line.length()+1+n.getBytes(StandardCharsets.UTF_8).length > NAMES_ROOM ) {
				numeric(Numeric.RPL_NAMREPLY, type, channel, line.toString());
				line.setLength(0);
			}
			if( line.length() > 0 ) {
				line.append(' ');
			}
			line.append(n);
		}
		if( line.length() > 0 ) {
			numeric(Numeric.RPL_NAMREPLY, type, channel, line.toString());
		}
	}

	// ------------------------------------------------------------------ LIST

	/** RFC 2812 section 3.2.6 */
	void list(IrcMessage msg) {
		if( msg.getParamCount() > 1 && !session.isThisServer(msg.getParam(1)) ) {
			numeric(Numeric.ERR_NOSUCHSERVER, msg.getParam(1), "No such server");
			return;
		}
		List<IrcChannel> channels;
		if( msg.getParamCount() == 0 || msg.getParam(0).isEmpty() ) {
			channels = node.getChannels();
		} else {
			channels = new ArrayList<IrcChannel>();
			for (String name : split(msg.getParam(0))) {
				IrcChannel ch = node.findChannel(name);
				if( ch != null ) {
					channels.add(ch);
				}
			}
		}
		IrcUser u = me();
		numeric(Numeric.RPL_LISTSTART, "Channel", "Users  Name");
		for (IrcChannel ch : channels) {
			synchronized (ch) {
				boolean member = ch.isMember(u);
				if( ch.isGone() || (ch.isSecret() && !member) ) {
					continue;
				}
				String count = Integer.toString(ch.getMemberCount());
				if( ch.isPrivate() && !member ) {
					numeric(Numeric.RPL_LIST, "Prv", count, "");
				} else {
					numeric(Numeric.RPL_LIST, ch.getName(), count, ch.getTopic() == null ? "" : ch.getTopic());
				}
			}
		}
		numeric(Numeric.RPL_LISTEND, "End of LIST");
	}

	// ------------------------------------------------------------------ INVITE

	/** RFC 2812 section 3.2.7 */
	void invite(IrcMessage msg) {
		if( !session.needParams(msg, 2) ) {
			return;
		}
		String nick = msg.getParam(0);
		String name = msg.getParam(1);
		IrcUser target = node.findUser(nick);
		if( target == null ) {
			numeric(Numeric.ERR_NOSUCHNICK, nick, "No such nick/channel");
			return;
		}
		IrcUser u = me();
		IrcChannel ch = node.findChannel(name);
		if( ch != null ) {
			synchronized (ch) {
				if( !ch.isMember(u) ) {
					numeric(Numeric.ERR_NOTONCHANNEL, ch.getName(), "You're not on that channel");
					return;
				}
				if( ch.isMember(target) ) {
					numeric(Numeric.ERR_USERONCHANNEL, target.getNickname(), ch.getName(), "is already on channel");
					return;
				}
				if( ch.hasFlag('i') && !ch.isOperator(u) ) {
					numeric(Numeric.ERR_CHANOPRIVSNEEDED, ch.getName(), "You're not channel operator");
					return;
				}
				ch.invite(target);
				name = ch.getName();
			}
		}
		// RFC 2812 gives RPL_INVITING as "<channel> <nick>"
		numeric(Numeric.RPL_INVITING, name, target.getNickname());
		target.getSession().send(IrcMessage.of(u.getPrefix(), "INVITE", Arrays.asList(target.getNickname(), name)));
	}

	// ------------------------------------------------------------------ KICK

	/** RFC 2812 section 3.2.8: one channel and many users, or as many channels as users */
	void kick(IrcMessage msg) {
		if( !session.needParams(msg, 2) ) {
			return;
		}
		List<String> names = split(msg.getParam(0));
		List<String> nicks = split(msg.getParam(1));
		if( names.isEmpty() || nicks.isEmpty() || (names.size() != 1 && names.size() != nicks.size()) ) {
			numeric(Numeric.ERR_NEEDMOREPARAMS, "KICK", "Not enough parameters");
			return;
		}
		IrcUser u = me();
		String comment = msg.getParamCount() > 2 ? msg.getParam(2) : u.getNickname();
		for (int i = 0; i < nicks.size(); i++) {
			String name = names.get(names.size() == 1 ? 0 : i);
			String nick = nicks.get(i);
			IrcChannel ch = visible(name);
			if( ch == null ) {
				numeric(Numeric.ERR_NOSUCHCHANNEL, name, "No such channel");
				continue;
			}
			synchronized (ch) {
				if( !ch.isMember(u) ) {
					numeric(Numeric.ERR_NOTONCHANNEL, ch.getName(), "You're not on that channel");
					continue;
				}
				if( !ch.isOperator(u) ) {
					numeric(Numeric.ERR_CHANOPRIVSNEEDED, ch.getName(), "You're not channel operator");
					continue;
				}
				IrcUser target = node.findUser(nick);
				if( target == null || !ch.isMember(target) ) {
					numeric(Numeric.ERR_USERNOTINCHANNEL, nick, ch.getName(), "They aren't on that channel");
					continue;
				}
				ch.send(IrcMessage.of(u.getPrefix(), "KICK", Arrays.asList(ch.getName(), target.getNickname(), comment)), null);
				removeMember(ch, target);
			}
		}
	}

	// ------------------------------------------------------------------ PRIVMSG and NOTICE

	/**
	 * RFC 2812 sections 3.3.1 and 3.3.2: to channels and nicknames, comma separated. NOTICE gets
	 * no error replies. Server and host masks ($ and # targets for operators) are step (c).
	 */
	void message(IrcMessage msg, boolean notice) {
		String cmd = notice ? "NOTICE" : "PRIVMSG";
		if( msg.getParamCount() < 1 || msg.getParam(0).isEmpty() ) {
			if( !notice ) {
				numeric(Numeric.ERR_NORECIPIENT, "No recipient given ("+cmd+")");
			}
			return;
		}
		if( msg.getParamCount() < 2 || msg.getParam(1).isEmpty() ) {
			if( !notice ) {
				numeric(Numeric.ERR_NOTEXTTOSEND, "No text to send");
			}
			return;
		}
		IrcUser u = me();
		String text = msg.getParam(1);
		for (String target : new LinkedHashSet<String>(split(msg.getParam(0)))) {
			if( IrcSyntax.looksLikeChannel(target) ) {
				IrcChannel ch = node.findChannel(target);
				if( ch == null ) {
					if( !notice ) {
						numeric(Numeric.ERR_NOSUCHNICK, target, "No such nick/channel");
					}
					continue;
				}
				synchronized (ch) {
					IrcChannel.Member m = ch.member(u);
					boolean speaks = m != null && (m.op || m.voice);
					boolean allowed = !(ch.hasFlag('n') && m == null)
							&& !(ch.hasFlag('m') && !speaks)
							&& !(!speaks && ch.isBanned(u));
					if( !allowed ) {
						if( !notice ) {
							numeric(Numeric.ERR_CANNOTSENDTOCHAN, ch.getName(), "Cannot send to channel");
						}
						continue;
					}
					ch.send(IrcMessage.of(u.getPrefix(), cmd, Arrays.asList(ch.getName(), text)), u);
				}
			} else {
				IrcUser to = node.findUser(target);
				if( to == null ) {
					if( !notice ) {
						numeric(Numeric.ERR_NOSUCHNICK, target, "No such nick/channel");
					}
					continue;
				}
				to.getSession().send(IrcMessage.of(u.getPrefix(), cmd, Arrays.asList(to.getNickname(), text)));
			}
		}
	}

	// ------------------------------------------------------------------ NICK and QUIT

	/** @return everyone on a channel with the user, once each, not the user */
	private static Set<IrcUser> neighbours(IrcUser u) {
		Set<IrcUser> ret = new LinkedHashSet<IrcUser>();
		for (IrcChannel ch : u.getChannels()) {
			ret.addAll(ch.getMembers());
		}
		ret.remove(u);
		return ret;
	}

	/** Tell everyone on the user's channels (and the user) of a new nickname */
	static void nickChanged(IrcUser u, IrcMessage nickMessage) {
		u.getSession().send(nickMessage);
		for (IrcUser other : neighbours(u)) {
			other.getSession().send(nickMessage);
		}
	}

	/** Take the user off every channel, telling the others on them once each with QUIT */
	static void quit(IrcUser u, String reason) {
		Set<IrcUser> others = new LinkedHashSet<IrcUser>();
		for (IrcChannel ch : u.getChannels()) {
			synchronized (ch) {
				if( !ch.isMember(u) ) {
					continue;
				}
				for (IrcUser m : ch.getMembers()) {
					if( m != u ) {
						others.add(m);
					}
				}
				boolean last = ch.remove(u);
				u.left(ch);
				if( last ) {
					ch.getNode().channelEmptied(ch);
				}
			}
		}
		IrcMessage q = IrcMessage.of(u.getPrefix(), "QUIT", Collections.singletonList(reason));
		for (IrcUser other : others) {
			other.getSession().send(q);
		}
	}
}
