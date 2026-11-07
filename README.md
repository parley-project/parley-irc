# parley-irc

Internet Relay Chat for **Parley**, a family of Java libraries for implementing internet protocols:
an IRC server, its links to other servers, and a client, built on `parley-net`'s non-blocking `nio`
package.

It is being built in stages, newest RFCs first:

1. RFC 2812 (client protocol) with the basics of RFC 2811 (channels) and RFC 7194 (TLS on port 6697)
2. RFC 2811 (channel management) in full
3. RFC 2813 (server protocol) and RFC 2810 (architecture)
4. RFC 1459, checked requirement by requirement; where the RFCs differ, the most recent one is followed

**Status:** the shared protocol package (stage 0). The server and client come next.

| Package | What it holds |
|---|---|
| `us.bringardner.parley.irc.protocol` | Messages and prefixes, numeric replies, the line decoder, case mapping, wildcard masks, name syntax |

## The protocol package

- `IrcMessage` parses and writes messages (RFC 2812 section 2.3.1): an optional prefix, a command
  and up to 15 parameters. `toBytes()` gives at most 512 bytes with the CR LF, cutting the last
  parameter (never inside a character) when a message is too long.
- `IrcMessage.newDecoder()` splits a connection's input into messages: parley-net's
  `LineFrameDecoder` set up for IRC, so CR LF, CR and LF all end a message, empty messages are
  skipped, and a message over 512 bytes is cut.
- The RFCs name no character set; messages are read as UTF-8, or as ISO-8859-1 when they aren't
  valid UTF-8 (parley-io's `IoUtils.decodeUtf8OrLatin1`), and written as UTF-8.
- `Numeric` names every reply in RFC 2812 (RFC 1459's are all among them), plus `RPL_ISUPPORT`,
  the meaning modern clients give 005 (RFC 2812 calls it `RPL_BOUNCE`).
- `CaseMapping` compares names the RFCs' way, where `{}|^` are the lower case of `[]\~`;
  `Masks` matches wildcard masks (`*`, `?`, `\` to escape) with parley-core's `Wildcards`;
  `IrcSyntax` checks nicknames,
  channel names, user names and channel keys.

```java
IrcMessage m = IrcMessage.parse(":tony!tb@host.example.net PRIVMSG #parley :hello there");
m.getPrefix().getNickname();   // "tony"
m.getCommand();                // "PRIVMSG"
m.getParam(1);                 // "hello there"

IrcMessage reply = IrcMessage.numeric(Prefix.server("irc.example.net"),
        Numeric.ERR_NOSUCHNICK, "tony", "bob", "No such nick/channel");
reply.toLine();                // ":irc.example.net 401 tony bob :No such nick/channel"
```

## Building

`mvn install`. Java 11 or later. Depends on `parley-net`, `parley-iam`, `parley-io` and `parley-core`.
