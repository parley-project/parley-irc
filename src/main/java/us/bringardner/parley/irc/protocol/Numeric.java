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
package us.bringardner.parley.irc.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The numeric replies named by RFC 2812 section 5 (command responses, error replies and reserved
 * numerics), and RPL_ISUPPORT, the meaning modern clients give 005. Every RFC 1459 reply is among
 * them, some only as reserved.
 * <p>
 * Two replies share 005: RPL_BOUNCE (RFC 2812) and RPL_ISUPPORT (de facto); {@link #of(int)}
 * gives RPL_BOUNCE, the RFC's. Which one a server sends is a setting (design decisions D4, D11).
 *
 * @author Tony Bringardner
 */
public enum Numeric {

	RPL_WELCOME(1, Source.RFC_2812),
	RPL_YOURHOST(2, Source.RFC_2812),
	RPL_CREATED(3, Source.RFC_2812),
	RPL_MYINFO(4, Source.RFC_2812),
	RPL_BOUNCE(5, Source.RFC_2812),
	/** 005 as clients read it today (RPL_ISUPPORT), not in the RFCs; see design decision D4 */
	RPL_ISUPPORT(5, Source.DE_FACTO),
	RPL_TRACELINK(200, Source.RFC_2812),
	RPL_TRACECONNECTING(201, Source.RFC_2812),
	RPL_TRACEHANDSHAKE(202, Source.RFC_2812),
	RPL_TRACEUNKNOWN(203, Source.RFC_2812),
	RPL_TRACEOPERATOR(204, Source.RFC_2812),
	RPL_TRACEUSER(205, Source.RFC_2812),
	RPL_TRACESERVER(206, Source.RFC_2812),
	RPL_TRACESERVICE(207, Source.RFC_2812),
	RPL_TRACENEWTYPE(208, Source.RFC_2812),
	RPL_TRACECLASS(209, Source.RFC_2812),
	RPL_TRACERECONNECT(210, Source.RFC_2812),
	RPL_STATSLINKINFO(211, Source.RFC_2812),
	RPL_STATSCOMMANDS(212, Source.RFC_2812),
	RPL_STATSCLINE(213, Source.RFC_2812_RESERVED),
	RPL_STATSNLINE(214, Source.RFC_2812_RESERVED),
	RPL_STATSILINE(215, Source.RFC_2812_RESERVED),
	RPL_STATSKLINE(216, Source.RFC_2812_RESERVED),
	RPL_STATSQLINE(217, Source.RFC_2812_RESERVED),
	RPL_STATSYLINE(218, Source.RFC_2812_RESERVED),
	RPL_ENDOFSTATS(219, Source.RFC_2812),
	RPL_UMODEIS(221, Source.RFC_2812),
	RPL_SERVICEINFO(231, Source.RFC_2812_RESERVED),
	RPL_ENDOFSERVICES(232, Source.RFC_2812_RESERVED),
	RPL_SERVICE(233, Source.RFC_2812_RESERVED),
	RPL_SERVLIST(234, Source.RFC_2812),
	RPL_SERVLISTEND(235, Source.RFC_2812),
	RPL_STATSVLINE(240, Source.RFC_2812_RESERVED),
	RPL_STATSLLINE(241, Source.RFC_2812_RESERVED),
	RPL_STATSUPTIME(242, Source.RFC_2812),
	RPL_STATSOLINE(243, Source.RFC_2812),
	RPL_STATSHLINE(244, Source.RFC_2812_RESERVED),
	RPL_STATSSLINE(244, Source.RFC_2812_RESERVED),
	RPL_STATSPING(246, Source.RFC_2812_RESERVED),
	RPL_STATSBLINE(247, Source.RFC_2812_RESERVED),
	RPL_STATSDLINE(250, Source.RFC_2812_RESERVED),
	RPL_LUSERCLIENT(251, Source.RFC_2812),
	RPL_LUSEROP(252, Source.RFC_2812),
	RPL_LUSERUNKNOWN(253, Source.RFC_2812),
	RPL_LUSERCHANNELS(254, Source.RFC_2812),
	RPL_LUSERME(255, Source.RFC_2812),
	RPL_ADMINME(256, Source.RFC_2812),
	RPL_ADMINLOC1(257, Source.RFC_2812),
	RPL_ADMINLOC2(258, Source.RFC_2812),
	RPL_ADMINEMAIL(259, Source.RFC_2812),
	RPL_TRACELOG(261, Source.RFC_2812),
	RPL_TRACEEND(262, Source.RFC_2812),
	RPL_TRYAGAIN(263, Source.RFC_2812),
	RPL_NONE(300, Source.RFC_2812_RESERVED),
	RPL_AWAY(301, Source.RFC_2812),
	RPL_USERHOST(302, Source.RFC_2812),
	RPL_ISON(303, Source.RFC_2812),
	RPL_UNAWAY(305, Source.RFC_2812),
	RPL_NOWAWAY(306, Source.RFC_2812),
	RPL_WHOISUSER(311, Source.RFC_2812),
	RPL_WHOISSERVER(312, Source.RFC_2812),
	RPL_WHOISOPERATOR(313, Source.RFC_2812),
	RPL_WHOWASUSER(314, Source.RFC_2812),
	RPL_ENDOFWHO(315, Source.RFC_2812),
	RPL_WHOISCHANOP(316, Source.RFC_2812_RESERVED),
	RPL_WHOISIDLE(317, Source.RFC_2812),
	RPL_ENDOFWHOIS(318, Source.RFC_2812),
	RPL_WHOISCHANNELS(319, Source.RFC_2812),
	RPL_LISTSTART(321, Source.RFC_2812),
	RPL_LIST(322, Source.RFC_2812),
	RPL_LISTEND(323, Source.RFC_2812),
	RPL_CHANNELMODEIS(324, Source.RFC_2812),
	RPL_UNIQOPIS(325, Source.RFC_2812),
	RPL_NOTOPIC(331, Source.RFC_2812),
	RPL_TOPIC(332, Source.RFC_2812),
	RPL_INVITING(341, Source.RFC_2812),
	RPL_SUMMONING(342, Source.RFC_2812),
	RPL_INVITELIST(346, Source.RFC_2812),
	RPL_ENDOFINVITELIST(347, Source.RFC_2812),
	RPL_EXCEPTLIST(348, Source.RFC_2812),
	RPL_ENDOFEXCEPTLIST(349, Source.RFC_2812),
	RPL_VERSION(351, Source.RFC_2812),
	RPL_WHOREPLY(352, Source.RFC_2812),
	RPL_NAMREPLY(353, Source.RFC_2812),
	RPL_KILLDONE(361, Source.RFC_2812_RESERVED),
	RPL_CLOSING(362, Source.RFC_2812_RESERVED),
	RPL_CLOSEEND(363, Source.RFC_2812_RESERVED),
	RPL_LINKS(364, Source.RFC_2812),
	RPL_ENDOFLINKS(365, Source.RFC_2812),
	RPL_ENDOFNAMES(366, Source.RFC_2812),
	RPL_BANLIST(367, Source.RFC_2812),
	RPL_ENDOFBANLIST(368, Source.RFC_2812),
	RPL_ENDOFWHOWAS(369, Source.RFC_2812),
	RPL_INFO(371, Source.RFC_2812),
	RPL_MOTD(372, Source.RFC_2812),
	RPL_INFOSTART(373, Source.RFC_2812_RESERVED),
	RPL_ENDOFINFO(374, Source.RFC_2812),
	RPL_MOTDSTART(375, Source.RFC_2812),
	RPL_ENDOFMOTD(376, Source.RFC_2812),
	RPL_YOUREOPER(381, Source.RFC_2812),
	RPL_REHASHING(382, Source.RFC_2812),
	RPL_YOURESERVICE(383, Source.RFC_2812),
	RPL_MYPORTIS(384, Source.RFC_2812_RESERVED),
	RPL_TIME(391, Source.RFC_2812),
	RPL_USERSSTART(392, Source.RFC_2812),
	RPL_USERS(393, Source.RFC_2812),
	RPL_ENDOFUSERS(394, Source.RFC_2812),
	RPL_NOUSERS(395, Source.RFC_2812),
	ERR_NOSUCHNICK(401, Source.RFC_2812),
	ERR_NOSUCHSERVER(402, Source.RFC_2812),
	ERR_NOSUCHCHANNEL(403, Source.RFC_2812),
	ERR_CANNOTSENDTOCHAN(404, Source.RFC_2812),
	ERR_TOOMANYCHANNELS(405, Source.RFC_2812),
	ERR_WASNOSUCHNICK(406, Source.RFC_2812),
	ERR_TOOMANYTARGETS(407, Source.RFC_2812),
	ERR_NOSUCHSERVICE(408, Source.RFC_2812),
	ERR_NOORIGIN(409, Source.RFC_2812),
	ERR_NORECIPIENT(411, Source.RFC_2812),
	ERR_NOTEXTTOSEND(412, Source.RFC_2812),
	ERR_NOTOPLEVEL(413, Source.RFC_2812),
	ERR_WILDTOPLEVEL(414, Source.RFC_2812),
	ERR_BADMASK(415, Source.RFC_2812),
	ERR_UNKNOWNCOMMAND(421, Source.RFC_2812),
	ERR_NOMOTD(422, Source.RFC_2812),
	ERR_NOADMININFO(423, Source.RFC_2812),
	ERR_FILEERROR(424, Source.RFC_2812),
	ERR_NONICKNAMEGIVEN(431, Source.RFC_2812),
	ERR_ERRONEUSNICKNAME(432, Source.RFC_2812),
	ERR_NICKNAMEINUSE(433, Source.RFC_2812),
	ERR_NICKCOLLISION(436, Source.RFC_2812),
	ERR_UNAVAILRESOURCE(437, Source.RFC_2812),
	ERR_USERNOTINCHANNEL(441, Source.RFC_2812),
	ERR_NOTONCHANNEL(442, Source.RFC_2812),
	ERR_USERONCHANNEL(443, Source.RFC_2812),
	ERR_NOLOGIN(444, Source.RFC_2812),
	ERR_SUMMONDISABLED(445, Source.RFC_2812),
	ERR_USERSDISABLED(446, Source.RFC_2812),
	ERR_NOTREGISTERED(451, Source.RFC_2812),
	ERR_NEEDMOREPARAMS(461, Source.RFC_2812),
	ERR_ALREADYREGISTRED(462, Source.RFC_2812),
	ERR_NOPERMFORHOST(463, Source.RFC_2812),
	ERR_PASSWDMISMATCH(464, Source.RFC_2812),
	ERR_YOUREBANNEDCREEP(465, Source.RFC_2812),
	ERR_YOUWILLBEBANNED(466, Source.RFC_2812),
	ERR_KEYSET(467, Source.RFC_2812),
	ERR_CHANNELISFULL(471, Source.RFC_2812),
	ERR_UNKNOWNMODE(472, Source.RFC_2812),
	ERR_INVITEONLYCHAN(473, Source.RFC_2812),
	ERR_BANNEDFROMCHAN(474, Source.RFC_2812),
	ERR_BADCHANNELKEY(475, Source.RFC_2812),
	ERR_BADCHANMASK(476, Source.RFC_2812),
	ERR_NOCHANMODES(477, Source.RFC_2812),
	ERR_BANLISTFULL(478, Source.RFC_2812),
	ERR_NOPRIVILEGES(481, Source.RFC_2812),
	ERR_CHANOPRIVSNEEDED(482, Source.RFC_2812),
	ERR_CANTKILLSERVER(483, Source.RFC_2812),
	ERR_RESTRICTED(484, Source.RFC_2812),
	ERR_UNIQOPPRIVSNEEDED(485, Source.RFC_2812),
	ERR_NOOPERHOST(491, Source.RFC_2812),
	ERR_NOSERVICEHOST(492, Source.RFC_2812_RESERVED),
	ERR_UMODEUNKNOWNFLAG(501, Source.RFC_2812),
	ERR_USERSDONTMATCH(502, Source.RFC_2812);

	/** Where a reply is defined */
	public enum Source {
		/** RFC 2812 section 5.1 or 5.2 */
		RFC_2812,
		/** RFC 2812 section 5.3: reserved, not in use (most come from RFC 1459) */
		RFC_2812_RESERVED,
		/** In wide use but in no RFC */
		DE_FACTO
	}

	private static final Map<Integer, Numeric> BY_CODE;
	static {
		Map<Integer, Numeric> map = new LinkedHashMap<Integer, Numeric>();
		for (Numeric n : values()) {
			if( !map.containsKey(n.code) ) {
				map.put(n.code, n);
			}
		}
		BY_CODE = Collections.unmodifiableMap(map);
	}

	private final int code;
	private final Source source;

	private Numeric(int code, Source source) {
		this.code = code;
		this.source = source;
	}

	/** @return the reply's number */
	public int getCode() {
		return code;
	}

	/** @return the number as three digits, as written in a message (e.g. "001") */
	public String getText() {
		return String.format("%03d", code);
	}

	/** @return where the reply is defined */
	public Source getSource() {
		return source;
	}

	/** @return true for an error reply (400 to 599, RFC 2812 section 5.2) */
	public boolean isError() {
		return code >= 400 && code < 600;
	}

	/**
	 * @return the reply with this number (for 005, RPL_BOUNCE), or null if no RFC names it
	 */
	public static Numeric of(int code) {
		return BY_CODE.get(code);
	}
}
