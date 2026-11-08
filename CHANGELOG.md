# Changelog

## parley-irc 2.0.0 (unreleased)

### Added

- **The protocol package** (`us.bringardner.parley.irc.protocol`), shared by the server, its
  server links and the client: `IrcMessage` and `Prefix` (RFC 2812 section 2.3.1), `Numeric`
  (every RFC 2812 reply, which includes RFC 1459's, and `RPL_ISUPPORT`), `IrcMessage.newDecoder()`
  (parley-net's `LineFrameDecoder`: CR LF, CR or LF; 512-byte limit), messages read as UTF-8 or
  ISO-8859-1 (parley-io's `IoUtils.decodeUtf8OrLatin1`), `CaseMapping`, `Masks` (RFC 2812
  section 2.5, on parley-core's `Wildcards`) and `IrcSyntax` (nicknames, channel names, users,
  keys).
- **The server, stage 1 step (a)** (`us.bringardner.parley.irc.server`): `IrcNode` (the RFC's
  server: name, users; a folder of its own holding `node.properties`, shared by the listeners
  that name it; names it doesn't know are logged as warnings and listed by
  `getUnknownSettings()`), `IrcServer`
  (a listener on parley-net's `NioServer`, default port 6667), `ClientSession` and `IrcUser`.
  Registration with PASS (optional listener password, given only as a reference: `@keychain:`,
  `@file:` or `@env:`; a plain value stops the listener from starting), NICK and
  USER (RFC 2812's mode bitmask and RFC 1459's form), QUIT, PING and PONG; the welcome 001-004,
  005 as `RPL_ISUPPORT` or kept for `RPL_BOUNCE` (`numeric005`), LUSERS and MOTD; user modes
  (`aiwroOs`); 421 for unknown commands and CAP; flood control (RFC 2812 section 5.8);
  registration and ping timeouts; a client with too much unsent output is dropped.
