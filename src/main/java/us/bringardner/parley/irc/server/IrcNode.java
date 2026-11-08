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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import us.bringardner.parley.core.BaseObject;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.io.IoUtils;
import us.bringardner.parley.irc.protocol.CaseMapping;
import us.bringardner.parley.irc.protocol.IrcSyntax;
import us.bringardner.parley.irc.protocol.Prefix;

/**
 * The RFC's "IRC server" (RFC 2812 section 1.1): its name, its users and (later) its channels,
 * shared by every listener ({@link IrcServer}) that names it, plain and TLS alike (decision D6).
 * <p>
 * A node lives in a folder of its own (decision D39), which each listener names with its required
 * {@code <prefix>.node} setting. Its settings are in the fixed file {@value #SETTINGS_FILE} inside
 * that folder (decisions D19, D42), and the other files it keeps (the MOTD now) are reached from
 * it. Every path is a FileSource from the default factory, used as a local file would be
 * (decision D41). Inside the file the names have no prefix:
 * <ul>
 * <li>{@code name}: the server's name, e.g. irc.example.net; required, no default</li>
 * <li>{@code description}: for LINKS and WHOIS (default: the name)</li>
 * <li>{@code network}: the network's name, sent as NETWORK in 005 (default: none)</li>
 * <li>{@code motd}: the message of the day's file; a relative path is in the node's folder, an
 *     absolute one is used as it is. None: 422 ERR_NOMOTD</li>
 * <li>{@code nickLength}: the longest nickname (default 9, RFC 2812)</li>
 * <li>{@code registrationTimeout}: how long a connection has to register (default 60s)</li>
 * <li>{@code pingInterval}: quiet time before the server sends PING (default 2m)</li>
 * <li>{@code pingTimeout}: how long the client has to answer it (default 2m)</li>
 * <li>{@code floodPenalty}, {@code floodLimit}: flood control (RFC 2812 section 5.8, defaults 2s
 *     and 10s)</li>
 * <li>{@code maxSendQueue}: bytes waiting to be sent to a client before it is dropped
 *     (default 1048576)</li>
 * </ul>
 * Durations carry their unit (250ms, 30s, 5m, 1h); a bare number is milliseconds. The settings
 * are read once, when the node is made. A name the node doesn't know is logged as a warning, naming
 * the file, and ignored (decision D37), so a misspelled setting doesn't silently give its default.
 * <p>
 * Use {@link #open(String)} so listeners naming the same folder share one node, or make one with
 * {@link #IrcNode(Properties)} and give it to each listener ({@link IrcServer#setNode(IrcNode)}).
 *
 * @author Tony Bringardner
 */
public class IrcNode extends BaseObject {

	public static final String PROPERTY_NAME = "name";
	public static final String PROPERTY_DESCRIPTION = "description";
	public static final String PROPERTY_NETWORK = "network";
	public static final String PROPERTY_MOTD = "motd";
	public static final String PROPERTY_NICK_LENGTH = "nickLength";
	public static final String PROPERTY_REGISTRATION_TIMEOUT = "registrationTimeout";
	public static final String PROPERTY_PING_INTERVAL = "pingInterval";
	public static final String PROPERTY_PING_TIMEOUT = "pingTimeout";
	public static final String PROPERTY_FLOOD_PENALTY = "floodPenalty";
	public static final String PROPERTY_FLOOD_LIMIT = "floodLimit";
	public static final String PROPERTY_MAX_SEND_QUEUE = "maxSendQueue";

	/** The node's settings file, in its folder (decisions D39, D42) */
	public static final String SETTINGS_FILE = "node.properties";

	/** Every name the node's settings may hold (decision D37) */
	public static final Set<String> KNOWN_SETTINGS = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
			PROPERTY_NAME, PROPERTY_DESCRIPTION, PROPERTY_NETWORK, PROPERTY_MOTD, PROPERTY_NICK_LENGTH,
			PROPERTY_REGISTRATION_TIMEOUT, PROPERTY_PING_INTERVAL, PROPERTY_PING_TIMEOUT,
			PROPERTY_FLOOD_PENALTY, PROPERTY_FLOOD_LIMIT, PROPERTY_MAX_SEND_QUEUE)));

	public static final long DEFAULT_REGISTRATION_TIMEOUT = 60_000;
	public static final long DEFAULT_PING_INTERVAL = 120_000;
	public static final long DEFAULT_PING_TIMEOUT = 120_000;
	/** RFC 2812 section 5.8: each message moves the client's timer 2 seconds on */
	public static final long DEFAULT_FLOOD_PENALTY = 2_000;
	/** RFC 2812 section 5.8: a client's timer may run up to 10 seconds ahead */
	public static final long DEFAULT_FLOOD_LIMIT = 10_000;
	public static final int DEFAULT_MAX_SEND_QUEUE = 1024*1024;
	/** The user modes this server knows (RFC 2812 section 3.1.5) */
	public static final String USER_MODES = "aiwroOs";
	/** The channel modes of stage 1 (RFC 2811 section 4) */
	public static final String CHANNEL_MODES = "biklmnopstv";

	/** The longest MOTD line sent (RFC 2812 section 3.4.1) */
	private static final int MOTD_LINE_LENGTH = 80;

	private static final ConcurrentHashMap<String, IrcNode> OPEN = new ConcurrentHashMap<String, IrcNode>();

	private final FileSource folder;
	private final String location;
	private final List<String> unknownSettings;
	private final String name;
	private final Prefix prefix;
	private final long created = System.currentTimeMillis();
	private final CaseMapping caseMapping = CaseMapping.RFC1459;
	private volatile List<String> motd;
	private final int nickLength;
	private final long registrationTimeout;
	private final long pingInterval;
	private final long pingTimeout;
	private final long floodPenalty;
	private final long floodLimit;
	private final int maxSendQueue;

	// Nicknames in use, registered or not, by their case-folded form
	private final ConcurrentHashMap<String, ClientSession> nicks = new ConcurrentHashMap<String, ClientSession>();
	private final ConcurrentHashMap<String, IrcUser> users = new ConcurrentHashMap<String, IrcUser>();
	private final AtomicInteger unregistered = new AtomicInteger();

	/**
	 * A node from its settings (see the class comment).
	 *
	 * @param settings the node's settings
	 * @throws IOException if the name is missing or not a valid server name, or the MOTD can't be read
	 */
	public IrcNode(Properties settings) throws IOException {
		this(settings, null);
	}

	/**
	 * A node from its settings, with its folder for the files it keeps: a relative motd path is
	 * taken from it.
	 *
	 * @param settings the node's settings
	 * @param folder the node's folder, or null (a relative path is then the factory's)
	 * @throws IOException if the name is missing or not a valid server name, or the MOTD can't be read
	 */
	public IrcNode(Properties settings, FileSource folder) throws IOException {
		if( settings == null ) {
			settings = new Properties();
		}
		setConfiguration(settings);
		setSupportPrefixProperty(false);
		this.folder = folder;
		this.location = folder == null ? null : folder.getChild(SETTINGS_FILE).getAbsolutePath();
		unknownSettings = findUnknown(settings);
		String n = getProperty(PROPERTY_NAME);
		if( n == null || n.trim().isEmpty() ) {
			throw new IOException("The IRC node"+where()+" has no "+PROPERTY_NAME);
		}
		n = n.trim();
		if( !isServerName(n) ) {
			throw new IOException("The IRC node's "+PROPERTY_NAME+" ("+n+")"+where()+" isn't a valid server name");
		}
		name = n;
		prefix = Prefix.server(name);
		nickLength = Math.max(1, getIntProperty(PROPERTY_NICK_LENGTH, IrcSyntax.RFC_NICKNAME_LENGTH));
		registrationTimeout = getDuration(PROPERTY_REGISTRATION_TIMEOUT, DEFAULT_REGISTRATION_TIMEOUT);
		pingInterval = getDuration(PROPERTY_PING_INTERVAL, DEFAULT_PING_INTERVAL);
		pingTimeout = getDuration(PROPERTY_PING_TIMEOUT, DEFAULT_PING_TIMEOUT);
		floodPenalty = Math.max(0, getDuration(PROPERTY_FLOOD_PENALTY, DEFAULT_FLOOD_PENALTY));
		floodLimit = Math.max(0, getDuration(PROPERTY_FLOOD_LIMIT, DEFAULT_FLOOD_LIMIT));
		maxSendQueue = getIntProperty(PROPERTY_MAX_SEND_QUEUE, DEFAULT_MAX_SEND_QUEUE);
		motd = readMotd();
	}

	/**
	 * The node in a folder: the same node for every caller that names the same folder, so the
	 * listeners of one JVM share it (decision D24).
	 *
	 * @param location the node's folder, a path on the default FileSource factory; its settings
	 *        are in {@value #SETTINGS_FILE} inside it
	 * @throws IOException if the settings file can't be read or its settings are wrong
	 */
	public static IrcNode open(String location) throws IOException {
		if( location == null || location.trim().isEmpty() ) {
			throw new IOException("No IRC node folder");
		}
		FileSource dir = FileSourceFactory.getDefaultFactory().createFileSource(location.trim());
		String key = dir.getAbsolutePath();
		IrcNode ret = OPEN.get(key);
		if( ret == null ) {
			synchronized (OPEN) {
				ret = OPEN.get(key);
				if( ret == null ) {
					ret = new IrcNode(load(dir), dir);
					OPEN.put(key, ret);
				}
			}
		}
		return ret;
	}

	private static Properties load(FileSource dir) throws IOException {
		if( !dir.isDirectory() ) {
			throw new IOException("The IRC node's folder "+dir.getAbsolutePath()+" doesn't exist or isn't a folder");
		}
		FileSource file = dir.getChild(SETTINGS_FILE);
		if( !file.exists() ) {
			throw new IOException("The IRC node's settings file "+file.getAbsolutePath()+" doesn't exist");
		}
		Properties ret = new Properties();
		try (InputStream in = file.getInputStream()) {
			ret.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		return ret;
	}

	/**
	 * A file the node keeps: an absolute path is used as it is; a relative one is in the node's
	 * folder (or the factory's when the node has none).
	 */
	private FileSource nodeFile(String path) throws IOException {
		if( folder == null || isAbsolute(path) ) {
			return FileSourceFactory.getDefaultFactory().createFileSource(path);
		}
		return folder.getChild(path);
	}

	// A FileSource is always absolute once made, so a path is judged by how it is written
	private static boolean isAbsolute(String path) {
		return path.startsWith("/") || path.startsWith("\\") || (path.length() > 1 && path.charAt(1) == ':');
	}

	private List<String> findUnknown(Properties settings) {
		List<String> ret = new ArrayList<String>();
		for (String n : new TreeSet<String>(settings.stringPropertyNames())) {
			if( !KNOWN_SETTINGS.contains(n) ) {
				ret.add(n);
				logWarn("The IRC node's settings"+(location == null ? "" : " file "+location)
						+" has an unknown setting, "+n+"; it is ignored");
			}
		}
		return Collections.unmodifiableList(ret);
	}

	private String where() {
		return location == null ? "" : " ("+location+")";
	}

	/**
	 * {@code servername = hostname}: letters, digits, '-' and '.', with at least one '.', at most
	 * 63 characters (RFC 2812 sections 1.1 and 2.3.1).
	 */
	static boolean isServerName(String n) {
		if( n.length() > 63 || n.indexOf('.') < 0 || n.startsWith(".") || n.endsWith(".") ) {
			return false;
		}
		for (int i = 0; i < n.length(); i++) {
			char c = n.charAt(i);
			if( !IrcSyntax.isLetter(c) && !IrcSyntax.isDigit(c) && c != '-' && c != '.' ) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ settings

	/** @return the node's settings file, or null when the node has no folder */
	public String getLocation() {
		return location;
	}

	/** @return the node's folder, or null when it was made from Properties alone */
	public FileSource getFolder() {
		return folder;
	}

	/** @return the names in the node's settings that it doesn't know, sorted (decision D37) */
	public List<String> getUnknownSettings() {
		return unknownSettings;
	}

	public String getName() {
		return name;
	}

	/** @return the prefix of the messages this server sends ({@code :name}) */
	public Prefix getPrefix() {
		return prefix;
	}

	public String getDescription() {
		return getProperty(PROPERTY_DESCRIPTION, name);
	}

	/** @return the network's name, or null */
	public String getNetwork() {
		String n = getProperty(PROPERTY_NETWORK);
		return n == null || n.trim().isEmpty() ? null : n.trim();
	}

	/** @return this server's version, e.g. parley-irc-2.0.0 */
	public String getVersion() {
		String v = IrcNode.class.getPackage().getImplementationVersion();
		return "parley-irc-"+(v == null ? "dev" : v);
	}

	/** @return when the node was made (System.currentTimeMillis() time) */
	public long getCreated() {
		return created;
	}

	public CaseMapping getCaseMapping() {
		return caseMapping;
	}

	public int getNickLength() {
		return nickLength;
	}

	public long getRegistrationTimeout() {
		return registrationTimeout;
	}

	public long getPingInterval() {
		return pingInterval;
	}

	public long getPingTimeout() {
		return pingTimeout;
	}

	public long getFloodPenalty() {
		return floodPenalty;
	}

	public long getFloodLimit() {
		return floodLimit;
	}

	public int getMaxSendQueue() {
		return maxSendQueue;
	}

	// ------------------------------------------------------------------ MOTD

	/** @return the message of the day's lines (at most 80 characters each), or null if there is none */
	public List<String> getMotd() {
		return motd;
	}

	/**
	 * Read the message of the day again.
	 * @throws IOException if the file is named but can't be read
	 */
	public void reloadMotd() throws IOException {
		motd = readMotd();
	}

	private List<String> readMotd() throws IOException {
		String path = getProperty(PROPERTY_MOTD);
		if( path == null || path.trim().isEmpty() ) {
			return null;
		}
		FileSource file = nodeFile(path.trim());
		if( !file.exists() ) {
			logWarn("The MOTD file "+file.getAbsolutePath()+" doesn't exist; 422 ERR_NOMOTD is sent instead");
			return null;
		}
		byte[] data;
		try (InputStream in = file.getInputStream()) {
			data = in.readAllBytes();
		}
		List<String> ret = new ArrayList<String>();
		try (BufferedReader r = new BufferedReader(new StringReader(IoUtils.decodeUtf8OrLatin1(data)))) {
			String line;
			while( (line = r.readLine()) != null ) {
				while( line.length() > MOTD_LINE_LENGTH ) {
					ret.add(line.substring(0, MOTD_LINE_LENGTH));
					line = line.substring(MOTD_LINE_LENGTH);
				}
				ret.add(line);
			}
		}
		return Collections.unmodifiableList(ret);
	}

	// ------------------------------------------------------------------ nicknames and users

	/**
	 * Take a nickname for a session, giving up the one it had.
	 *
	 * @return true if the nickname was free (or already the session's)
	 */
	boolean claimNick(ClientSession session, String oldNick, String newNick) {
		String key = caseMapping.toLowerCase(newNick);
		ClientSession owner = nicks.putIfAbsent(key, session);
		if( owner != null && owner != session ) {
			return false;
		}
		if( oldNick != null ) {
			String oldKey = caseMapping.toLowerCase(oldNick);
			if( !oldKey.equals(key) ) {
				nicks.remove(oldKey, session);
				IrcUser u = users.remove(oldKey);
				if( u != null ) {
					users.put(key, u);
				}
			}
		}
		return true;
	}

	void releaseNick(ClientSession session, String nick) {
		if( nick != null ) {
			String key = caseMapping.toLowerCase(nick);
			nicks.remove(key, session);
			IrcUser u = users.get(key);
			if( u != null && u.getSession() == session ) {
				users.remove(key, u);
			}
		}
	}

	void addUser(IrcUser user) {
		users.put(caseMapping.toLowerCase(user.getNickname()), user);
	}

	/** @return the user with this nickname (any case), or null */
	public IrcUser findUser(String nick) {
		return nick == null ? null : users.get(caseMapping.toLowerCase(nick));
	}

	/** @return the registered users (a read only view) */
	public Collection<IrcUser> getUsers() {
		return Collections.unmodifiableCollection(users.values());
	}

	public int getUserCount() {
		return users.size();
	}

	public int getOperatorCount() {
		int ret = 0;
		for (IrcUser u : users.values()) {
			if( u.isOperator() ) {
				ret++;
			}
		}
		return ret;
	}

	/** @return connections that haven't registered yet */
	public int getUnregisteredCount() {
		return unregistered.get();
	}

	void connectionOpened() {
		unregistered.incrementAndGet();
	}

	void connectionRegisteredOrClosed() {
		unregistered.decrementAndGet();
	}

	/** @return the channels (none until step b) */
	public int getChannelCount() {
		return 0;
	}

	@Override
	public String toString() {
		return "IrcNode "+name+where();
	}
}
