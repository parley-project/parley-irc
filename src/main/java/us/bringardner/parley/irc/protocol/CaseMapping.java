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
 * How nicknames and channel names are compared without regard to case.
 * <p>
 * IRC's Scandinavian origin makes {}|^ the lower case of []\~ (RFC 2812 section 2.2, RFC 1459
 * section 2.2), which {@link #RFC1459} follows (design decision D7). {@link #ASCII} folds only
 * A-Z. Other characters are compared as they are. A mapping is also a {@link Wildcards.CaseFolding},
 * so masks compare the same way ({@link Masks}).
 *
 * @author Tony Bringardner
 */
public enum CaseMapping implements Wildcards.CaseFolding {

	/** A-Z and []\~ fold to a-z and {}|^ (the RFCs' rule, and the default) */
	RFC1459("rfc1459"),
	/** Only A-Z fold to a-z */
	ASCII("ascii");

	private final String name;

	private CaseMapping(String name) {
		this.name = name;
	}

	/** @return the name clients know it by (RPL_ISUPPORT's CASEMAPPING), e.g. "rfc1459" */
	public String getName() {
		return name;
	}

	/** @return the lower case of one character */
	public char toLowerCase(char c) {
		if( c >= 'A' && c <= 'Z' ) {
			return (char) (c+('a'-'A'));
		}
		if( this == RFC1459 ) {
			switch (c) {
			case '[': return '{';
			case ']': return '}';
			case '\\': return '|';
			case '~': return '^';
			default: break;
			}
		}
		return c;
	}

	/** @return the lower case of c (Wildcards.CaseFolding) */
	@Override
	public char fold(char c) {
		return toLowerCase(c);
	}

	/** @return the lower case of the text; use it as the key when names are kept in a map */
	public String toLowerCase(String text) {
		StringBuilder ret = null;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			char lower = toLowerCase(c);
			if( lower != c && ret == null ) {
				ret = new StringBuilder(text.length());
				ret.append(text, 0, i);
			}
			if( ret != null ) {
				ret.append(lower);
			}
		}
		return ret == null ? text : ret.toString();
	}

	/** @return true if the two are the same name under this mapping */
	public boolean equals(String a, String b) {
		if( a.length() != b.length() ) {
			return false;
		}
		for (int i = 0; i < a.length(); i++) {
			if( toLowerCase(a.charAt(i)) != toLowerCase(b.charAt(i)) ) {
				return false;
			}
		}
		return true;
	}
}
