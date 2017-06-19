/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.undertow.util;

import io.undertow.UndertowMessages;
import io.undertow.server.handlers.Cookie;

import java.util.Date;

/**
 * Class that contains static constants and utility methods for legacy Set-Cookie format.
 * Porting from JBossWeb and Tomcat code.
 */
public final class LegacyCookieSupport {

    // --------------------------------------------------------------- Constants

    /**
     * If set to true, we parse cookies strictly according to the servlet,
     * cookie and HTTP specs by default.
     */
    private static final boolean STRICT_SERVLET_COMPLIANCE;

    /**
     * If true, separators that are not explicitly dis-allowed by the v0 cookie
     * spec but are disallowed by the HTTP spec will be allowed in v0 cookie
     * names and values. These characters are: \"()/:<=>?@[\\]{} Note that the
     * inclusion of / depend on the value of {@link #FWD_SLASH_IS_SEPARATOR}.
     */
    private static final boolean ALLOW_HTTP_SEPARATORS_IN_V0;

    /**
     * If set to false, we don't use the IE6/7 Max-Age/Expires work around.
     * Default is usually true. If STRICT_SERVLET_COMPLIANCE==true then default
     * is false. Explicitly setting always takes priority.
     */
    private static final boolean ALWAYS_ADD_EXPIRES;

    /**
     * If set to true, the <code>/</code> character will be treated as a
     * separator. Default is false.
     */
    private static final boolean FWD_SLASH_IS_SEPARATOR;

    /**
     * The list of separators that apply to version 0 cookies. To quote the
     * spec, these are comma, semi-colon and white-space. The HTTP spec
     * definition of linear white space is [CRLF] 1*( SP | HT )
     */
    private static final char[] V0_SEPARATORS = {',', ';', ' ', '\t'};
    private static final boolean[] V0_SEPARATOR_FLAGS = new boolean[128];

    /**
     * The list of separators that apply to version 1 cookies. This may or may
     * not include '/' depending on the setting of
     * {@link #FWD_SLASH_IS_SEPARATOR}.
     */
    private static final char[] HTTP_SEPARATORS;
    private static final boolean[] HTTP_SEPARATOR_FLAGS = new boolean[128];

    private static final String ancientDate;

    static {

        STRICT_SERVLET_COMPLIANCE = Boolean.valueOf(System.getProperty(
                "io.undertow.legacy.cookie.STRICT_SERVLET_COMPLIANCE",
                "true"));

        ALLOW_HTTP_SEPARATORS_IN_V0 = Boolean.valueOf(System.getProperty(
                "io.undertow.legacy.cookie.ALLOW_HTTP_SEPARATORS_IN_V0",
                "false"));

        FWD_SLASH_IS_SEPARATOR = Boolean.valueOf(System.getProperty(
                "io.undertow.legacy.cookie.FWD_SLASH_IS_SEPARATOR",
                "false"));

        String alwaysAddExpires = System.getProperty(
                "io.undertow.legacy.cookie.ALWAYS_ADD_EXPIRES");
        if (alwaysAddExpires == null) {
            ALWAYS_ADD_EXPIRES = !STRICT_SERVLET_COMPLIANCE;
        } else {
            ALWAYS_ADD_EXPIRES = Boolean.valueOf(alwaysAddExpires);
        }
        /*
        Excluding the '/' char by default violates the RFC, but
        it looks like a lot of people put '/'
        in unquoted values: '/': ; //47
        '\t':9 ' ':32 '\"':34 '(':40 ')':41 ',':44 ':':58 ';':59 '<':60
        '=':61 '>':62 '?':63 '@':64 '[':91 '\\':92 ']':93 '{':123 '}':125
        */
        if (FWD_SLASH_IS_SEPARATOR) {
            HTTP_SEPARATORS = new char[]{'\t', ' ', '\"', '(', ')', ',', '/',
                    ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '{', '}'};
        } else {
            HTTP_SEPARATORS = new char[]{'\t', ' ', '\"', '(', ')', ',',
                    ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '{', '}'};
        }
        for (int i = 0; i < 128; i++) {
            V0_SEPARATOR_FLAGS[i] = false;
            HTTP_SEPARATOR_FLAGS[i] = false;
        }
        for (char V0_SEPARATOR : V0_SEPARATORS) {
            V0_SEPARATOR_FLAGS[V0_SEPARATOR] = true;
        }
        for (char HTTP_SEPARATOR : HTTP_SEPARATORS) {
            HTTP_SEPARATOR_FLAGS[HTTP_SEPARATOR] = true;
        }

        ancientDate = DateUtils.toOldCookieDateString(new Date(10000));
    }

    // ----------------------------------------------------------------- Methods

    /**
     * Returns true if the byte is a separator as defined by V0 of the cookie
     * spec.
     */
    private static boolean isV0Separator(final char c) {
        if (c < 0x20 || c >= 0x7f) {
            if (c != 0x09) {
                throw UndertowMessages.MESSAGES.invalidControlCharacter(Integer.toString(c));
            }
        }

        return V0_SEPARATOR_FLAGS[c];
    }

    private static boolean isV0Token(String value) {
        if( value==null) return false;

        int i = 0;
        int len = value.length();

        if (alreadyQuoted(value)) {
            i++;
            len--;
        }

        for (; i < len; i++) {
            char c = value.charAt(i);

            if (isV0Separator(c))
                return true;
        }
        return false;
    }

    /**
     * Returns true if the byte is a separator as defined by V1 of the cookie
     * spec, RFC2109.
     * @throws IllegalArgumentException if a control character was supplied as
     *         input
     */
    private static boolean isHttpSeparator(final char c) {
        if (c < 0x20 || c >= 0x7f) {
            if (c != 0x09) {
                throw UndertowMessages.MESSAGES.invalidControlCharacter(Integer.toString(c));
            }
        }

        return HTTP_SEPARATOR_FLAGS[c];
    }

    private static boolean isHttpToken(String value) {
        if( value==null) return false;

        int i = 0;
        int len = value.length();

        if (alreadyQuoted(value)) {
            i++;
            len--;
        }

        for (; i < len; i++) {
            char c = value.charAt(i);

            if (isHttpSeparator(c))
                return true;
        }
        return false;
    }

    private static boolean alreadyQuoted(String value) {
        if (value==null || value.length() < 2) return false;
        return (value.charAt(0)=='\"' && value.charAt(value.length()-1)=='\"');
    }

    public static String generateLegacyCookieString(final Cookie cookie) {

        StringBuilder buf = new StringBuilder();

        // Servlet implementation checks name
        buf.append(cookie.getName());
        buf.append("=");
        // Servlet implementation does not check anything else

        // Start by adjusting the cookie version with the cookie attibutes
        int newVersion = adjustedCookieVersion(cookie);

        String value = cookie.getValue();
        String path = cookie.getPath();
        String domain = cookie.getDomain();
        String comment = cookie.getComment();

        // Now build the cookie header
        // Value
        maybeQuote(buf, value);

        // Add version 1 specific information
        if (newVersion == 1) {
            // Version=1 ... required
            buf.append ("; Version=1");

            // Comment=comment
            if (comment != null) {
                buf.append ("; Comment=");
                maybeQuote(buf, comment);
            }
        }

        // Add domain information, if present
        if (domain != null) {
            buf.append("; Domain=");
            maybeQuote(buf, domain);
        }

        // Max-Age=secs ... or use old "Expires" format
        if (cookie.getMaxAge() != null && cookie.getMaxAge() >= 0) {
            if (newVersion > 0) {
                buf.append ("; Max-Age=");
                buf.append (cookie.getMaxAge());
            }
            // IE6, IE7 and possibly other browsers don't understand Max-Age.
            // They do understand Expires, even with V1 cookies!
            if (newVersion == 0 || ALWAYS_ADD_EXPIRES) {
                // Wdy, DD-Mon-YY HH:MM:SS GMT ( Expires Netscape format )
                buf.append ("; Expires=");
                // To expire immediately we need to set the time in past
                if (cookie.getMaxAge() == 0) {
                    buf.append(ancientDate);
                } else {
                    buf.append(DateUtils.toOldCookieDateString(new Date(System.currentTimeMillis() + cookie.getMaxAge() * 1000L)));
                }
            }
        }

        // Path=path
        if (path != null) {
            buf.append ("; Path=");
            maybeQuote(buf, path);
        }

        // Secure
        if (cookie.isSecure()) {
          buf.append ("; Secure");
        }

        // HttpOnly
        if (cookie.isHttpOnly()) {
            buf.append("; HttpOnly");
        }

        return buf.toString();
    }

    /**
     * Quotes values if required.
     * @param buf
     * @param value
     */
    public static void maybeQuote(StringBuilder buf, String value) {
        if (value==null || value.length()==0) {
            buf.append("\"\"");
        } else if (alreadyQuoted(value)) {
            buf.append('"');
            buf.append(escapeDoubleQuotes(value,1,value.length()-1));
            buf.append('"');
        } else if (isHttpToken(value) && !ALLOW_HTTP_SEPARATORS_IN_V0 ||
                isV0Token(value) && ALLOW_HTTP_SEPARATORS_IN_V0) {
            buf.append('"');
            buf.append(escapeDoubleQuotes(value,0,value.length()));
            buf.append('"');
        } else {
            buf.append(value);
        }
    }

    /**
     * Escapes any double quotes in the given string.
     *
     * @param s the input string
     * @param beginIndex start index inclusive
     * @param endIndex exclusive
     * @return The (possibly) escaped string
     */
    private static String escapeDoubleQuotes(String s, int beginIndex, int endIndex) {

        if (s == null || s.length() == 0 || s.indexOf('"') == -1) {
            return s;
        }

        StringBuilder b = new StringBuilder();
        for (int i = beginIndex; i < endIndex; i++) {
            char c = s.charAt(i);
            if (c == '\\' ) {
                b.append(c);
                //ignore the character after an escape, just append it
                if (++i>=endIndex) throw UndertowMessages.MESSAGES.invalidEscapeCharacter();
                b.append(s.charAt(i));
            } else if (c == '"')
                b.append('\\').append('"');
            else
                b.append(c);
        }

        return b.toString();
    }

    public static int adjustedCookieVersion(Cookie cookie) {

        /*
         * The spec allows some latitude on when to send the version attribute
         * with a Set-Cookie header. To be nice to clients, we'll make sure the
         * version attribute is first. That means checking the various things
         * that can cause us to switch to a v1 cookie first.
         *_
         * Note that by checking for tokens we will also throw an exception if a
         * control character is encountered.
         */

        int version = cookie.getVersion();

        String value = cookie.getValue();
        String path = cookie.getPath();
        String domain = cookie.getDomain();
        String comment = cookie.getComment();

        // If it is v0, check if we need to switch
        if (version == 0 &&
                (!ALLOW_HTTP_SEPARATORS_IN_V0 && isHttpToken(value) ||
                        ALLOW_HTTP_SEPARATORS_IN_V0 && isV0Token(value))) {
            // HTTP token in value - need to use v1
            version = 1;
        }

        if (version == 0 && comment != null) {
            // Using a comment makes it a v1 cookie
            version = 1;
        }

        if (version == 0 &&
                (!ALLOW_HTTP_SEPARATORS_IN_V0 && isHttpToken(path) ||
                        ALLOW_HTTP_SEPARATORS_IN_V0 && isV0Token(path))) {
            // HTTP token in path - need to use v1
            version = 1;
        }

        if (version == 0 &&
                (!ALLOW_HTTP_SEPARATORS_IN_V0 && isHttpToken(domain) ||
                        ALLOW_HTTP_SEPARATORS_IN_V0 && isV0Token(domain))) {
            // HTTP token in domain - need to use v1
            version = 1;
        }

        return version;
    }

    // ------------------------------------------------------------- Constructor
    private LegacyCookieSupport() {
        // Utility class. Don't allow instances to be created.
    }
}
