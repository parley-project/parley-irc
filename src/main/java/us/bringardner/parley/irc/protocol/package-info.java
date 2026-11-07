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
/**
 * What the IRC server, its server links and the client share: messages ({@link
 * us.bringardner.parley.irc.protocol.IrcMessage}, {@link us.bringardner.parley.irc.protocol.Prefix}),
 * numeric replies ({@link us.bringardner.parley.irc.protocol.Numeric}), reading a connection's
 * input as messages ({@link us.bringardner.parley.irc.protocol.IrcMessage#newDecoder()}), and the
 * RFCs' rules for names: case ({@link
 * us.bringardner.parley.irc.protocol.CaseMapping}), wildcard masks ({@link
 * us.bringardner.parley.irc.protocol.Masks}) and syntax ({@link
 * us.bringardner.parley.irc.protocol.IrcSyntax}).
 */
package us.bringardner.parley.irc.protocol;
