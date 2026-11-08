/**
 * The IRC server (stage 1, RFC 2812): an {@link us.bringardner.parley.irc.server.IrcNode} is the
 * RFC's "server" (its name, users and channels), shared by the
 * {@link us.bringardner.parley.irc.server.IrcServer} listeners that serve it, plain and TLS; each
 * client connection is a {@link us.bringardner.parley.irc.server.ClientSession}, and once it has
 * registered, an {@link us.bringardner.parley.irc.server.IrcUser}.
 */
package us.bringardner.parley.irc.server;
