# parley-irc

Internet Relay Chat for **Parley**, a family of Java libraries for implementing internet protocols:
an IRC server, its links to other servers, and a client, built on `parley-net`'s non-blocking `nio`
package.

It is being built in stages, newest RFCs first:

1. RFC 2812 (client protocol) with the basics of RFC 2811 (channels) and RFC 7194 (TLS on port 6697)
2. RFC 2811 (channel management) in full
3. RFC 2813 (server protocol) and RFC 2810 (architecture)
4. RFC 1459, checked requirement by requirement; where the RFCs differ, the most recent one is followed

**Status:** the shared protocol package (stage 0), the server's registration (stage 1, step a),
and channels and messages (step b). Queries and operator commands, the client and the TLS
listener come next.

| Package | What it holds |
|---|---|
| `us.bringardner.parley.irc.protocol` | Messages and prefixes, numeric replies, the line decoder, case mapping, wildcard masks, name syntax |
| `us.bringardner.parley.irc.server` | The server: the node, its listeners, client sessions, users and channels |

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

## The server

An `IrcNode` is what RFC 2812 calls a server: its name, its users and (soon) its channels. Each
`IrcServer` is one listener on parley-net's `NioServer`, serving a node; a plain and a TLS
listener in one JVM share the node, so a nickname taken on one is taken on the other.

A node lives in a folder of its own, which each listener names. Its settings are in
`node.properties` inside that folder; the other files it keeps (the MOTD now) are reached from
there. Paths are FileSources from the default factory, used as local files would be.

```properties
# node.properties, in the node's folder (names have no prefix here)
name=irc.example.net
description=Parley IRC
network=ParleyNet
# a relative path is in the node's folder; an absolute one is used as it is
motd=motd.txt
nickLength=9
registrationTimeout=60s
pingInterval=2m
pingTimeout=2m
# flood control (RFC 2812 section 5.8): each message moves the client's timer on by
# floodPenalty; while it is more than floodLimit ahead, the server stops reading from it
floodPenalty=2s
floodLimit=10s
maxSendQueue=1048576
# the most channels a user may be on at once (sent as CHANLIMIT)
maxChannels=10
```

A name the node doesn't know (a misspelling such as `nickLenght`) is logged as a warning naming
the file, and ignored; `IrcNode.getUnknownSettings()` lists them.

```properties
# the listener's settings
parley.irc.port=6667
# the node's folder, holding node.properties
parley.irc.node=/etc/parley/irc
# optional: where the password PASS must give is kept (@keychain:, @file:, @env:);
# never the password itself: a plain value stops the listener from starting
parley.irc.password=@keychain:parley/irc
# isupport (default) sends RPL_ISUPPORT; bounce keeps 005 for RFC 2812's RPL_BOUNCE
parley.irc.numeric005=isupport
```

```java
IrcServer server = new IrcServer();      // reads parley.irc.*
server.startAndWait(5000);
```

A client registers with NICK and USER (and PASS first when the listener has a password); USER
takes RFC 2812's form (`USER tb 8 * :Tony B`, where 8 sets +i and 4 sets +w) or RFC 1459's
(`USER tb host server :Tony B`). The server then sends 001-004, 005, the LUSERS replies and the
MOTD (or 422). It answers PING, LUSERS, MOTD and MODE for the user's own modes (`i`, `w`, `s`,
`r` set only, `o`/`O` given up only); anything else gets 421 for now, CAP included. Clients get
no host name lookup: the host is the address they connect from.

### Channels and messages

Channels are `#` (network-wide) and `&` (this server's); `+` and `!` channels come with RFC 2811
in full. The first user to JOIN a channel is its operator, and the channel goes when its last
member leaves. The server handles JOIN (keys, and `JOIN 0` to leave every channel), PART, TOPIC,
NAMES, LIST, INVITE, KICK, PRIVMSG and NOTICE, and MODE for channels:

| Mode | Meaning |
|---|---|
| `o` / `v` | channel operator / voice, given to a member by nickname |
| `b` | ban mask (`nick` means `nick!*@*`); `MODE #chan b` lists them |
| `k` / `l` | the key JOIN must give / the most members |
| `i` | invite only: an operator's INVITE lets a user in |
| `m` | moderated: only operators and voiced members speak |
| `n` | no messages from users not on the channel |
| `t` | only operators change the topic |
| `p` / `s` | private / secret |

A MODE makes at most three changes that take a parameter. To someone not on it, a secret channel
acts as if it doesn't exist; a private one is listed by LIST as `Prv` with no topic and left out
of a NAMES with no parameter. Invisible users (`+i`) show in NAMES only to people on the same
channel. A user's NICK and QUIT go to everyone on their channels, once each; a connection that
ends without QUIT sends QUIT with the reason (a ping timeout, for example).

## Building

`mvn install`. Java 11 or later. Depends on `parley-net`, `parley-iam`, `parley-files`,
`parley-io` and `parley-core`.
