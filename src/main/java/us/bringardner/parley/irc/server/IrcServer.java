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
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

import us.bringardner.parley.files.credentials.CredentialRequest;
import us.bringardner.parley.files.credentials.Credentials;
import us.bringardner.parley.irc.protocol.IrcMessage;
import us.bringardner.parley.net.nio.NioServer;

/**
 * One IRC listener: a port, plain or TLS, serving the clients of an {@link IrcNode} (decisions
 * D6, D17). Built on parley-net's {@link NioServer}: each connection gets a {@link ClientSession}
 * and its input is split into messages by {@link IrcMessage#newDecoder()}.
 * <p>
 * Settings, under the listener's prefix ({@value #PROPERTY_PREFIX} by default):
 * <ul>
 * <li>{@code port}: default {@value #DEFAULT_PORT} (decision D18)</li>
 * <li>{@code node}: the node's settings file, a FileSource path or {@code filesource:} URL;
 *     required unless {@link #setNode(IrcNode)} gives the node (decision D19)</li>
 * <li>{@code password}: the connection password PASS must give; a reference such as
 *     {@code @keychain:...} is allowed. None: PASS is accepted and ignored (decision D21)</li>
 * <li>{@code numeric005}: {@code isupport} (the default) sends RPL_ISUPPORT after registering;
 *     {@code bounce} keeps 005 for RFC 2812's RPL_BOUNCE (decisions D4, D11)</li>
 * </ul>
 * plus the settings of every parley-net server (bindAddress, maxConnections,
 * maxConnectionsPerClient, allowedClients ...).
 * <p>
 * IRC clients don't log in, so the server's loginTimeLimit doesn't apply; the node's
 * registrationTimeout does instead.
 *
 * @author Tony Bringardner
 */
public class IrcServer extends NioServer {

	/** The prefix of a plain listener's settings (parley.irc.port ...) */
	public static final String PROPERTY_PREFIX = "parley.irc";
	public static final String PROPERTY_NODE = "node";
	public static final String PROPERTY_PASSWORD = "password";
	public static final String PROPERTY_NUMERIC_005 = "numeric005";
	public static final String NUMERIC_005_ISUPPORT = "isupport";
	public static final String NUMERIC_005_BOUNCE = "bounce";
	/** RFC 7194 section 1 */
	public static final int DEFAULT_PORT = 6667;
	/** For {@link us.bringardner.parley.files.credentials.CredentialRequest}s: parley.credentials.irc.* */
	public static final String CREDENTIALS_ID = "irc";

	private volatile IrcNode node;
	private volatile byte[] password;
	private volatile boolean isupport = true;

	/** A plain listener with the {@value #PROPERTY_PREFIX} settings */
	public IrcServer() {
		this(PROPERTY_PREFIX, DEFAULT_PORT);
	}

	/**
	 * @param prefix the prefix of this listener's settings, e.g. parley.irc
	 * @param defaultPort the port when {@code <prefix>.port} isn't set
	 */
	public IrcServer(String prefix, int defaultPort) {
		setPropertyPrefix(prefix);
		setName(prefix);
		setPort(getIntProperty(propertyName(PROPERTY_PORT), defaultPort));
		setHandlerFactory(() -> new ClientSession(this));
		setDecoderFactory(IrcMessage::newDecoder);
	}

	/** IRC clients register instead of logging in (decision D22) */
	@Override
	protected boolean isLoginRequired() {
		return false;
	}

	/**
	 * Find the node and read this listener's settings, then start.
	 * @throws IOException if they are wrong, or the server doesn't start
	 */
	@Override
	public void startAndWait(long timeoutMillis) throws IOException {
		prepare();
		super.startAndWait(timeoutMillis);
	}

	/**
	 * Find the node and read this listener's settings, then start; a problem with them is logged
	 * and the server doesn't start.
	 */
	@Override
	public synchronized void start() {
		try {
			prepare();
		} catch (IOException e) {
			logError("Server "+getName()+" not started: "+e.getMessage(), e);
			return;
		}
		super.start();
	}

	private synchronized void prepare() throws IOException {
		IrcNode n = node;
		if( n == null ) {
			String location = getProperty(propertyName(PROPERTY_NODE));
			if( location == null || location.trim().isEmpty() ) {
				throw new IOException(propertyName(PROPERTY_NODE)+" isn't set: it names the IRC node's settings file");
			}
			n = IrcNode.open(location.trim());
			node = n;
		}
		String mode = getProperty(propertyName(PROPERTY_NUMERIC_005), NUMERIC_005_ISUPPORT).trim().toLowerCase(Locale.ROOT);
		if( mode.equals(NUMERIC_005_ISUPPORT) ) {
			isupport = true;
		} else if( mode.equals(NUMERIC_005_BOUNCE) ) {
			isupport = false;
		} else {
			throw new IOException(propertyName(PROPERTY_NUMERIC_005)+" is "+mode+": it must be "+NUMERIC_005_ISUPPORT+" or "+NUMERIC_005_BOUNCE);
		}
		password = readPassword();
		setMaxIdleTime(n.getPingInterval());
	}

	private byte[] readPassword() throws IOException {
		String value = getProperty(propertyName(PROPERTY_PASSWORD));
		if( value == null || value.isEmpty() ) {
			return null;
		}
		char[] secret = Credentials.secret(value, new CredentialRequest(CREDENTIALS_ID, PROPERTY_PASSWORD, null, null));
		try {
			java.nio.ByteBuffer b = StandardCharsets.UTF_8.encode(CharBuffer.wrap(secret));
			byte[] ret = new byte[b.remaining()];
			b.get(ret);
			return ret;
		} finally {
			Arrays.fill(secret, '\0');
		}
	}

	/** @return the node this listener serves, null before it is set or the server starts */
	public IrcNode getNode() {
		return node;
	}

	/** @param node the node to serve, instead of the one {@code <prefix>.node} names */
	public void setNode(IrcNode node) {
		this.node = node;
	}

	/** @return true if 005 is RPL_ISUPPORT, false if it is RPL_BOUNCE */
	public boolean isIsupport() {
		return isupport;
	}

	/** @return true if PASS must give the connection password */
	public boolean isPasswordRequired() {
		return password != null;
	}

	/** @return true if the password PASS gave is this listener's (always true when there is none) */
	boolean checkPassword(String given) {
		byte[] p = password;
		if( p == null ) {
			return true;
		}
		return given != null && MessageDigest.isEqual(p, given.getBytes(StandardCharsets.UTF_8));
	}
}
