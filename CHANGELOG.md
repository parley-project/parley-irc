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
