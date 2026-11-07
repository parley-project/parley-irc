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

import us.bringardner.parley.io.WireFormatException;

/**
 * A line that isn't a valid IRC message, or a message that can't be written as one (RFC 2812
 * section 2.3.1).
 *
 * @author Tony Bringardner
 */
public class IrcFormatException extends WireFormatException {

	private static final long serialVersionUID = 1L;

	public IrcFormatException(String message) {
		super(message);
	}
}
