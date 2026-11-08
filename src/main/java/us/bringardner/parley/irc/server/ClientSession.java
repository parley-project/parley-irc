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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import us.bringardner.parley.irc.protocol.IrcFormatException;
import us.bringardner.parley.irc.protocol.IrcMessage;
import us.bringardner.parley.irc.protocol.IrcSyntax;
import us.bringardner.parley.irc.protocol.Masks;
import us.bringardner.parley.irc.protocol.Numeric;
import us.bringardner.parley.irc.protocol.Prefix;
import us.bringardner.parley.net.nio.INioConnection;
import us.bringardner.parley.net.nio.INioHandler;

/**
 * One client connection (decision D17): registration (PASS, NICK, USER, RFC 2812 section 3.1),
 * the welcome replies, and the commands of stage 1 step (a): QUIT, PING, PONG, MOTD, LUSERS and
 * MODE for user modes. Step (b)'s channels and messages are {@link ChannelCommands}. Any other
 * command gets 421 ERR_UNKNOWNCOMMAND (CAP too, decision D8).
 * <p>
 * A user's NICK, and their QUIT (or any other end of the connection), go to everyone on their
 * channels.
 * <p>
 * Timers use the connection's deadline: the registration timeout, the answer to the server's
 * PING, and flood control (RFC 2812 section 5.8: each message moves the client's timer
 * floodPenalty on; while it is more than floodLimit ahead of the clock, the session stops
 * reading and holds the messages it already has).
 * <p>
 * The handler calls of one connection never overlap (parley-net); other sessions only call
 * {@link #send(IrcMessage)}, which may be called from any thread.
 *
 * @author Tony Bringardner
 */
public class ClientSession implements INioHandler {

	/** Commands a client may send before it registers (RFC 2812 section 3.1) */
	private static final List<String> BEFORE_REGISTRATION = java.util.Arrays.asList("PASS", "NICK", "USER", "QUIT", "PING", "PONG");
	/** The commands of later steps: before registering they get 451, after it 421 until they are built */
	private static final List<String> KNOWN = java.util.Arrays.asList("MOTD", "LUSERS", "MODE",
			"JOIN", "PART", "TOPIC", "NAMES", "LIST", "INVITE", "KICK", "PRIVMSG", "NOTICE");
	private static final DateTimeFormatter CREATED = DateTimeFormatter.ofPattern("EEE MMM d yyyy 'at' HH:mm:ss 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);

	private final IrcServer server;
	private volatile IrcNode node;
	private volatile INioConnection connection;
	private volatile String host = "unknown";
	private volatile String nick;
	private volatile IrcUser user;
	private volatile boolean closing;
	private volatile String quitReason;
	private ChannelCommands channels;

	// Registration
	private String password;
	private String userName;
	private String realName;
	private int userModeBits;
	private boolean countedUnregistered;

	// Timers (System.currentTimeMillis() times, 0 = not running)
	private long registrationDue;
	private long pingDue;
	private long floodDue;

	// Flood control (RFC 2812 section 5.8)
	private long floodTimer;
	private final ArrayDeque<IrcMessage> held = new ArrayDeque<IrcMessage>();

	public ClientSession(IrcServer server) {
		this.server = server;
	}

	// ------------------------------------------------------------------ connection events

	@Override
	public void onConnect(INioConnection connection) throws Exception {
		this.connection = connection;
		this.node = server.getNode();
		this.channels = new ChannelCommands(this, node);
		this.host = hostOf(connection.getRemoteAddress());
		node.connectionOpened();
		countedUnregistered = true;
		registrationDue = now()+node.getRegistrationTimeout();
		updateDeadline();
	}

	/**
	 * The client's address as its host (no DNS lookup): an IPv6 address starting with ':' gets a
	 * '0' in front so it can be a message parameter.
	 */
	static String hostOf(SocketAddress address) {
		if( !(address instanceof InetSocketAddress) || ((InetSocketAddress)address).getAddress() == null ) {
			return "unknown";
		}
		String ret = ((InetSocketAddress)address).getAddress().getHostAddress();
		int scope = ret.indexOf('%');
		if( scope > 0 ) {
			ret = ret.substring(0, scope);
		}
		return ret.startsWith(":") ? "0"+ret : ret;
	}

	@Override
	public void onMessage(INioConnection connection, ByteBuffer frame) throws Exception {
		if( closing ) {
			return;
		}
		IrcMessage msg;
		try {
			msg = IrcMessage.parse(frame);
		} catch (IrcFormatException e) {
			// RFC 2812 section 3: a message that can't be parsed is ignored
			server.logDebug(() -> "Ignoring a bad message from "+host+": "+e.getMessage());
			return;
		}
		if( pingDue != 0 ) {
			// Anything from the client shows it is still there
			pingDue = 0;
			updateDeadline();
		}
		long now = now();
		if( floodTimer < now ) {
			floodTimer = now;
		}
		if( !held.isEmpty() || floodTimer-now > node.getFloodLimit() ) {
			held.add(msg);
			if( !connection.isReadingPaused() ) {
				connection.pauseReading();
			}
			floodDue = floodTimer-node.getFloodLimit();
			updateDeadline();
			return;
		}
		floodTimer += node.getFloodPenalty();
		handle(msg);
	}

	/** Quiet for the node's pingInterval: ask a registered client whether it is still there */
	@Override
	public void onIdle(INioConnection connection) throws Exception {
		if( user != null && pingDue == 0 && !closing ) {
			send(IrcMessage.of(node.getPrefix(), "PING", java.util.Collections.singletonList(node.getName())));
			pingDue = now()+node.getPingTimeout();
			updateDeadline();
		}
	}

	@Override
	public void onDeadline(INioConnection connection) throws Exception {
		if( closing ) {
			return;
		}
		long now = now();
		if( floodDue != 0 && now >= floodDue ) {
			floodDue = 0;
			releaseHeld();
			if( closing ) {
				return;
			}
		}
		if( registrationDue != 0 && now >= registrationDue ) {
			registrationDue = 0;
			closeLink("Registration timed out");
			return;
		}
		if( pingDue != 0 && now >= pingDue ) {
			pingDue = 0;
			closeLink("Ping timeout: "+(node.getPingTimeout()/1000)+" seconds");
			return;
		}
		updateDeadline();
	}

	// Handle the held messages the flood timer allows now; read again once none are left
	private void releaseHeld() throws IOException {
		while( !held.isEmpty() && !closing ) {
			long now = now();
			if( floodTimer < now ) {
				floodTimer = now;
			}
			if( floodTimer-now > node.getFloodLimit() ) {
				floodDue = floodTimer-node.getFloodLimit();
				return;
			}
			floodTimer += node.getFloodPenalty();
			handle(held.poll());
		}
		if( held.isEmpty() && connection.isReadingPaused() ) {
			connection.resumeReading();
		}
	}

	@Override
	public void onError(INioConnection connection, Throwable error) {
		server.logDebug(() -> "Error on "+connection+": "+error);
	}

	@Override
	public void onClose(INioConnection connection) {
		IrcNode n = node;
		if( n == null ) {
			return;
		}
		if( countedUnregistered ) {
			countedUnregistered = false;
			n.connectionRegisteredOrClosed();
		}
		IrcUser u = user;
		if( u != null ) {
			// QUIT went out when the link was closed; this covers a connection that just ended
			ChannelCommands.quit(u, quitReason == null ? "Connection closed" : quitReason);
		}
		n.releaseNick(this, nick);
	}

	// Set the connection's deadline to the first timer due
	private void updateDeadline() {
		long first = 0;
		for (long due : new long[] {registrationDue, pingDue, floodDue}) {
			if( due != 0 && (first == 0 || due < first) ) {
				first = due;
			}
		}
		if( first == 0 ) {
			connection.clearDeadline();
		} else {
			connection.setDeadline(Math.max(1, first-now()));
		}
	}

	private static long now() {
		return System.currentTimeMillis();
	}

	// ------------------------------------------------------------------ commands

	private void handle(IrcMessage msg) throws IOException {
		if( msg.isNumeric() ) {
			// Clients don't send numerics; ignored
			return;
		}
		String cmd = msg.getCommand().toUpperCase(Locale.ROOT);
		if( user == null && !BEFORE_REGISTRATION.contains(cmd) ) {
			if( KNOWN.contains(cmd) ) {
				numeric(Numeric.ERR_NOTREGISTERED, "You have not registered");
			} else {
				unknown(cmd);
			}
			return;
		}
		switch (cmd) {
			case "PASS":   pass(msg); break;
			case "NICK":   nick(msg); break;
			case "USER":   user(msg); break;
			case "QUIT":   quit(msg); break;
			case "PING":   ping(msg); break;
			case "PONG":   pong(msg); break;
			case "MOTD":   motd(msg); break;
			case "LUSERS": lusers(msg); break;
			case "MODE":   mode(msg); break;
			case "JOIN":   channels.join(msg); break;
			case "PART":   channels.part(msg); break;
			case "TOPIC":  channels.topic(msg); break;
			case "NAMES":  channels.names(msg); break;
			case "LIST":   channels.list(msg); break;
			case "INVITE": channels.invite(msg); break;
			case "KICK":   channels.kick(msg); break;
			case "PRIVMSG": channels.message(msg, false); break;
			case "NOTICE": channels.message(msg, true); break;
			default:       unknown(cmd); break;
		}
	}

	private void unknown(String cmd) {
		numeric(Numeric.ERR_UNKNOWNCOMMAND, cmd, "Unknown command");
	}

	boolean needParams(IrcMessage msg, int count) {
		if( msg.getParamCount() < count ) {
			numeric(Numeric.ERR_NEEDMOREPARAMS, msg.getCommand().toUpperCase(Locale.ROOT), "Not enough parameters");
			return false;
		}
		return true;
	}

	/** RFC 2812 section 3.1.1 */
	private void pass(IrcMessage msg) {
		if( user != null ) {
			numeric(Numeric.ERR_ALREADYREGISTRED, "Unauthorized command (already registered)");
			return;
		}
		if( needParams(msg, 1) ) {
			password = msg.getParam(0);
		}
	}

	/** RFC 2812 section 3.1.2 */
	private void nick(IrcMessage msg) throws IOException {
		if( msg.getParamCount() < 1 || msg.getParam(0).isEmpty() ) {
			numeric(Numeric.ERR_NONICKNAMEGIVEN, "No nickname given");
			return;
		}
		String n = msg.getParam(0);
		if( !IrcSyntax.isNickname(n, node.getNickLength()) ) {
			numeric(Numeric.ERR_ERRONEUSNICKNAME, n, "Erroneous nickname");
			return;
		}
		IrcUser u = user;
		if( u != null && u.isRestricted() ) {
			numeric(Numeric.ERR_RESTRICTED, "Your connection is restricted!");
			return;
		}
		String old = nick;
		if( n.equals(old) ) {
			return;
		}
		if( !node.claimNick(this, old, n) ) {
			numeric(Numeric.ERR_NICKNAMEINUSE, n, "Nickname is already in use");
			return;
		}
		nick = n;
		if( u != null ) {
			Prefix from = u.getPrefix();
			u.setNickname(n);
			ChannelCommands.nickChanged(u, IrcMessage.of(from, "NICK", java.util.Collections.singletonList(n)));
		} else {
			tryRegister();
		}
	}

	/**
	 * RFC 2812 section 3.1.3: {@code USER user mode unused :realname}. In RFC 1459's form the
	 * second parameter is a host name; one that isn't a number is read as mode 0.
	 */
	private void user(IrcMessage msg) throws IOException {
		if( user != null || userName != null ) {
			numeric(Numeric.ERR_ALREADYREGISTRED, "Unauthorized command (already registered)");
			return;
		}
		if( !needParams(msg, 4) ) {
			return;
		}
		String u = msg.getParam(0);
		if( !IrcSyntax.isUser(u) ) {
			numeric(Numeric.ERR_NEEDMOREPARAMS, "USER", "Not enough parameters");
			return;
		}
		try {
			userModeBits = Integer.parseInt(msg.getParam(1));
		} catch (NumberFormatException e) {
			userModeBits = 0;
		}
		userName = u;
		realName = msg.getParam(3);
		tryRegister();
	}

	// Register once both NICK and USER have come
	private void tryRegister() throws IOException {
		if( user != null || nick == null || userName == null ) {
			return;
		}
		if( !server.checkPassword(password) ) {
			numeric(Numeric.ERR_PASSWDMISMATCH, "Password incorrect");
			closeLink("Bad password");
			return;
		}
		IrcUser u = new IrcUser(this, nick, userName, host, realName);
		// RFC 2812 section 3.1.3: bit 2 sets 'w', bit 3 sets 'i'
		if( (userModeBits & 4) != 0 ) {
			u.setMode('w');
		}
		if( (userModeBits & 8) != 0 ) {
			u.setMode('i');
		}
		registrationDue = 0;
		updateDeadline();
		if( countedUnregistered ) {
			countedUnregistered = false;
			node.connectionRegisteredOrClosed();
		}
		user = u;
		node.addUser(u);
		welcome(u);
	}

	/** 001-004, 005 (unless it is RPL_BOUNCE), LUSERS and the MOTD (RFC 2812 section 5.1) */
	private void welcome(IrcUser u) {
		String name = node.getName();
		String version = node.getVersion();
		numeric(Numeric.RPL_WELCOME, "Welcome to the Internet Relay Network "+u.getPrefix());
		numeric(Numeric.RPL_YOURHOST, "Your host is "+name+", running version "+version);
		numeric(Numeric.RPL_CREATED, "This server was created "+CREATED.format(Instant.ofEpochMilli(node.getCreated())));
		numeric(Numeric.RPL_MYINFO, name, version, IrcNode.USER_MODES, IrcNode.CHANNEL_MODES);
		if( server.isIsupport() ) {
			for (List<String> tokens : isupportLines()) {
				tokens.add("are supported by this server");
				numeric(Numeric.RPL_ISUPPORT, tokens.toArray(new String[0]));
			}
		}
		sendLusers();
		sendMotd();
		if( !u.getModes().equals("+") ) {
			send(IrcMessage.of(u.getPrefix(), "MODE", java.util.Arrays.asList(u.getNickname(), u.getModes())));
		}
	}

	/** The RPL_ISUPPORT tokens, at most 13 to a line */
	private List<List<String>> isupportLines() {
		List<String> all = new ArrayList<String>();
		all.add("CASEMAPPING="+node.getCaseMapping().getName());
		all.add("CHANTYPES=#&");
		all.add("CHANLIMIT=#&:"+node.getMaxChannels());
		all.add("PREFIX=(ov)@+");
		all.add("CHANMODES=b,k,l,imnpst");
		all.add("CHANNELLEN="+IrcSyntax.MAX_CHANNEL_LENGTH);
		all.add("MODES="+ChannelCommands.MAX_MODE_PARAMS);
		all.add("NICKLEN="+node.getNickLength());
		String network = node.getNetwork();
		if( network != null && network.indexOf(' ') < 0 ) {
			all.add("NETWORK="+network);
		}
		List<List<String>> ret = new ArrayList<List<String>>();
		for (int i = 0; i < all.size(); i += 13) {
			ret.add(new ArrayList<String>(all.subList(i, Math.min(all.size(), i+13))));
		}
		return ret;
	}

	/** RFC 2812 section 3.1.7 */
	private void quit(IrcMessage msg) {
		String reason = msg.getParamCount() > 0 && !msg.getParam(0).isEmpty() ? "Quit: "+msg.getParam(0) : "Client Quit";
		closeLink(reason);
	}

	/** RFC 2812 section 3.7.2 */
	private void ping(IrcMessage msg) {
		if( msg.getParamCount() < 1 || msg.getParam(0).isEmpty() ) {
			numeric(Numeric.ERR_NOORIGIN, "No origin specified");
			return;
		}
		if( msg.getParamCount() > 1 && !isThisServer(msg.getParam(1)) ) {
			numeric(Numeric.ERR_NOSUCHSERVER, msg.getParam(1), "No such server");
			return;
		}
		send(IrcMessage.of(node.getPrefix(), "PONG", java.util.Arrays.asList(node.getName(), msg.getParam(0))));
	}

	/** RFC 2812 section 3.7.3; that the client sent anything has already been noted */
	private void pong(IrcMessage msg) {
		if( msg.getParamCount() < 1 || msg.getParam(0).isEmpty() ) {
			numeric(Numeric.ERR_NOORIGIN, "No origin specified");
		}
	}

	/** RFC 2812 section 3.4.1 */
	private void motd(IrcMessage msg) {
		if( msg.getParamCount() > 0 && !isThisServer(msg.getParam(0)) ) {
			numeric(Numeric.ERR_NOSUCHSERVER, msg.getParam(0), "No such server");
			return;
		}
		sendMotd();
	}

	private void sendMotd() {
		List<String> lines = node.getMotd();
		if( lines == null ) {
			numeric(Numeric.ERR_NOMOTD, "MOTD File is missing");
			return;
		}
		numeric(Numeric.RPL_MOTDSTART, "- "+node.getName()+" Message of the day - ");
		for (String line : lines) {
			numeric(Numeric.RPL_MOTD, "- "+line);
		}
		numeric(Numeric.RPL_ENDOFMOTD, "End of MOTD command");
	}

	/** RFC 2812 section 3.4.2; with no other servers the mask changes nothing */
	private void lusers(IrcMessage msg) {
		if( msg.getParamCount() > 1 && !isThisServer(msg.getParam(1)) ) {
			numeric(Numeric.ERR_NOSUCHSERVER, msg.getParam(1), "No such server");
			return;
		}
		sendLusers();
	}

	/** RPL_LUSERCLIENT and RPL_LUSERME always; the others only when their count isn't 0 */
	private void sendLusers() {
		numeric(Numeric.RPL_LUSERCLIENT, "There are "+node.getUserCount()+" users and 0 services on 1 servers");
		int ops = node.getOperatorCount();
		if( ops > 0 ) {
			numeric(Numeric.RPL_LUSEROP, Integer.toString(ops), "operator(s) online");
		}
		int unknown = node.getUnregisteredCount();
		if( unknown > 0 ) {
			numeric(Numeric.RPL_LUSERUNKNOWN, Integer.toString(unknown), "unknown connection(s)");
		}
		int channels = node.getChannelCount();
		if( channels > 0 ) {
			numeric(Numeric.RPL_LUSERCHANNELS, Integer.toString(channels), "channels formed");
		}
		numeric(Numeric.RPL_LUSERME, "I have "+node.getUserCount()+" clients and 0 servers");
	}

	/** RFC 2812 section 3.1.5 (user modes); a channel's are {@link ChannelCommands#mode(IrcMessage)} */
	private void mode(IrcMessage msg) {
		if( !needParams(msg, 1) ) {
			return;
		}
		String target = msg.getParam(0);
		if( IrcSyntax.looksLikeChannel(target) ) {
			channels.mode(msg);
			return;
		}
		IrcUser u = user;
		if( !node.getCaseMapping().equals(target, u.getNickname()) ) {
			numeric(Numeric.ERR_USERSDONTMATCH, "Cant change mode for other users");
			return;
		}
		if( msg.getParamCount() == 1 ) {
			numeric(Numeric.RPL_UMODEIS, u.getModes());
			return;
		}
		StringBuilder changes = new StringBuilder();
		char sign = '+';
		char written = 0;
		boolean unknownFlag = false;
		for (int i = 1; i < msg.getParamCount(); i++) {
			for (char c : msg.getParam(i).toCharArray()) {
				if( c == '+' || c == '-' ) {
					sign = c;
					continue;
				}
				if( IrcNode.USER_MODES.indexOf(c) < 0 ) {
					unknownFlag = true;
					continue;
				}
				boolean changed = false;
				switch (c) {
					case 'a':
						// Set and cleared only by AWAY
						break;
					case 'o': case 'O':
						// Only OPER sets these; a user may give them up
						changed = sign == '-' && u.clearMode(c);
						break;
					case 'r':
						// A restricted user can't lift it
						changed = sign == '+' && u.setMode(c);
						break;
					default:
						changed = sign == '+' ? u.setMode(c) : u.clearMode(c);
						break;
				}
				if( changed ) {
					if( written != sign ) {
						changes.append(sign);
						written = sign;
					}
					changes.append(c);
				}
			}
		}
		if( unknownFlag ) {
			numeric(Numeric.ERR_UMODEUNKNOWNFLAG, "Unknown MODE flag");
		}
		if( changes.length() > 0 ) {
			send(IrcMessage.of(u.getPrefix(), "MODE", java.util.Arrays.asList(u.getNickname(), changes.toString())));
		}
	}

	// A server parameter (MOTD, LUSERS, PING): this server's name, or a mask matching it
	boolean isThisServer(String name) {
		return node.getCaseMapping().equals(name, node.getName()) || Masks.matches(name, node.getName(), node.getCaseMapping());
	}

	// ------------------------------------------------------------------ sending

	/** Send a numeric reply to this client: {@code :server NNN nick params...} */
	void numeric(Numeric numeric, String... params) {
		String n = nick;
		send(IrcMessage.numeric(node.getPrefix(), numeric, n == null ? "*" : n, params));
	}

	/**
	 * Queue a message for this client; one that has more than the node's maxSendQueue waiting is
	 * dropped. May be called from any thread.
	 */
	public void send(IrcMessage msg) {
		INioConnection c = connection;
		if( c == null || closing ) {
			return;
		}
		if( c.getPendingWriteBytes() > node.getMaxSendQueue() ) {
			server.logInfo("Max SendQ exceeded, closing "+c);
			quitReason = "Max SendQ exceeded";
			closing = true;
			c.close();
			return;
		}
		try {
			c.write(msg.toBytes());
		} catch (IOException e) {
			// closed or closing
		}
	}

	/** Send ERROR and close once it has been sent (RFC 2812 sections 3.1.7, 3.7.4) */
	private void closeLink(String reason) {
		if( closing ) {
			return;
		}
		send(IrcMessage.of(null, "ERROR", java.util.Collections.singletonList("Closing Link: "+host+" ("+reason+")")));
		closing = true;
		quitReason = reason;
		held.clear();
		IrcUser u = user;
		if( u != null ) {
			ChannelCommands.quit(u, reason);
		}
		// The nickname is free at once, not when the socket has finished closing
		node.releaseNick(this, nick);
		connection.closeAfterFlush();
	}

	// ------------------------------------------------------------------ for other sessions

	/** @return the nickname, or null before NICK */
	public String getNickname() {
		return nick;
	}

	/** @return the registered user, or null before registration */
	public IrcUser getUser() {
		return user;
	}

	public String getHost() {
		return host;
	}

	public INioConnection getConnection() {
		return connection;
	}
}
