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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;

import us.bringardner.parley.irc.protocol.IrcMessage;

/**
 * Starts IRC listeners on free ports and talks to them as a plain socket client would.
 */
abstract class ServerTestBase {

	protected final List<IrcServer> servers = new ArrayList<IrcServer>();
	protected final List<TestClient> clients = new ArrayList<TestClient>();

	protected static Properties nodeSettings() {
		Properties p = new Properties();
		p.setProperty(IrcNode.PROPERTY_NAME, "irc.example.net");
		// No flood control unless a test sets it: these tests send bursts of commands
		p.setProperty(IrcNode.PROPERTY_FLOOD_PENALTY, "0");
		return p;
	}

	protected IrcServer start(Properties node) throws IOException {
		return start(new IrcNode(node), new Properties());
	}

	protected IrcServer start(IrcNode node, Properties listener) throws IOException {
		IrcServer s;
		try {
			s = us.bringardner.parley.core.BaseObject.makeWith(listener, () -> new IrcServer(IrcServer.PROPERTY_PREFIX, 0));
		} catch (IOException | RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e);
		}
		s.setConfiguration(listener);
		if( node != null ) {
			s.setNode(node);
		}
		servers.add(s);
		s.startAndWait(5000);
		return s;
	}

	protected TestClient connect(IrcServer s) throws IOException {
		TestClient c = new TestClient(s.getLocalPort());
		clients.add(c);
		return c;
	}

	/** Connect, register as nick and read the welcome up to the end of the MOTD */
	protected TestClient register(IrcServer s, String nick) throws IOException {
		TestClient c = connect(s);
		c.send("NICK "+nick);
		c.send("USER "+nick+" 0 * :Test "+nick);
		c.readUntil(m -> m.is("376") || m.is("422"));
		return c;
	}

	@AfterEach
	void stopAll() {
		for (TestClient c : clients) {
			c.close();
		}
		for (IrcServer s : servers) {
			s.stop();
		}
	}

	static class TestClient {
		final Socket socket;
		final BufferedReader in;
		final OutputStream out;

		TestClient(int port) throws IOException {
			socket = new Socket("127.0.0.1", port);
			socket.setSoTimeout(5000);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			out = socket.getOutputStream();
		}

		void send(String line) throws IOException {
			sendRaw(line+"\r\n");
		}

		void sendRaw(String text) throws IOException {
			out.write(text.getBytes(StandardCharsets.UTF_8));
			out.flush();
		}

		/** @return the next message, or null at the end of the stream */
		IrcMessage read() throws IOException {
			String line = in.readLine();
			return line == null ? null : IrcMessage.parse(line);
		}

		/** Read messages until one matches; @return all of them, the match last */
		List<IrcMessage> readUntil(Predicate<IrcMessage> match) throws IOException {
			List<IrcMessage> ret = new ArrayList<IrcMessage>();
			while( true ) {
				IrcMessage m = read();
				if( m == null ) {
					throw new IOException("Closed before the expected message; read "+ret);
				}
				ret.add(m);
				if( match.test(m) ) {
					return ret;
				}
			}
		}

		IrcMessage readCommand(String command) throws IOException {
			List<IrcMessage> got = readUntil(m -> m.is(command));
			return got.get(got.size()-1);
		}

		/** @return true if the server closes the connection within the socket timeout */
		boolean closedByServer() throws IOException {
			try {
				while( in.readLine() != null ) {
					// skip
				}
				return true;
			} catch (SocketTimeoutException e) {
				return false;
			} catch (IOException e) {
				// reset
				return true;
			}
		}

		void close() {
			try {
				socket.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}
}
