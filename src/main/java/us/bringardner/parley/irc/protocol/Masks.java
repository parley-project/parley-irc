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

import us.bringardner.parley.core.util.Wildcards;

/**
 * IRC's wildcard masks (RFC 2812 section 2.5): '?' matches exactly one character, '*' any number of
 * characters (none included), and '\' before '*', '?' or '\' makes it an ordinary character.
 * Matching ignores case by the given {@link CaseMapping}. It is parley-core's {@link Wildcards}
 * with IRC's escapes and case mapping.
 *
 * @author Tony Bringardner
 */
public final class Masks {

	private Masks() {
	}

	/** @return true if the text matches the mask, comparing by RFC 1459 case mapping */
	public static boolean matches(String mask, String text) {
		return matches(mask, text, CaseMapping.RFC1459);
	}

	/**
	 * @param mask the mask, e.g. {@code *!*@*.example.net}
	 * @param text what to test, e.g. {@code tony!tb@host.example.net}
	 * @param mapping how case is compared
	 * @return true if the whole text matches the mask
	 */
	public static boolean matches(String mask, String text, CaseMapping mapping) {
		return Wildcards.matches(mask, text, true, mapping);
	}

	/** @return true if the mask has an unescaped '*' or '?' */
	public static boolean hasWildcards(String mask) {
		return Wildcards.hasWildcards(mask, true);
	}
}
