/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.util;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.server.MultiValueHashListStorage;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Class that contains utility methods for dealing with cookies.
 *
 * @author Stuart Douglas
 * @author Andre Dietisheim
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class Cookies {

    public static final String DOMAIN = "$Domain";
    public static final String VERSION = "$Version";
    public static final String PATH = "$Path";
    private static final Pattern OBSOLETE_COOKIE_PATTERN = Pattern.compile(
            Pattern.quote(VERSION) + "|" +
                    Pattern.quote(DOMAIN) + "|" +
                    Pattern.quote(PATH),
            Pattern.CASE_INSENSITIVE);


    /**
     * Parses a "Set-Cookie:" response header value into its cookie representation. The header value is parsed according to the
     * syntax that's defined in RFC2109:
     *
     * <pre>
     * <code>
     *  set-cookie      =       "Set-Cookie:" cookies
     *   cookies         =       1#cookie
     *   cookie          =       NAME "=" VALUE *(";" cookie-av)
     *   NAME            =       attr
     *   VALUE           =       value
     *   cookie-av       =       "Comment" "=" value
     *                   |       "Domain" "=" value
     *                   |       "Max-Age" "=" value
     *                   |       "Path" "=" value
     *                   |       "Secure"
     *                   |       "Version" "=" 1*DIGIT
     *
     * </code>
     * </pre>
     *
     * @param headerValue The header value
     * @return The cookie
     *
     * @see Cookie
     * @see <a href="http://tools.ietf.org/search/rfc2109">rfc2109</a>
     */
    public static Cookie parseSetCookieHeader(final String headerValue) {

        String key = null;
        CookieImpl cookie = null;
        int state = 0;
        int current = 0;
        for (int i = 0; i < headerValue.length(); ++i) {
            char c = headerValue.charAt(i);
            switch (state) {
                case 0: {
                    //reading key
                    if (c == '=') {
                        key = headerValue.substring(current, i);
                        current = i + 1;
                        state = 1;
                    } else if ((c == ';' || c == ' ') && current == i) {
                        current++;
                    } else if (c == ';') {
                        if (cookie == null) {
                            throw UndertowMessages.MESSAGES.couldNotParseCookie(headerValue);
                        } else {
                            handleValue(cookie, headerValue.substring(current, i), null);
                        }
                        current = i + 1;
                    }
                    break;
                }
                case 1: {
                    if (c == ';') {
                        if (cookie == null) {
                            cookie = new CookieImpl(key, headerValue.substring(current, i));
                        } else {
                            handleValue(cookie, key, headerValue.substring(current, i));
                        }
                        state = 0;
                        current = i + 1;
                        key = null;
                    } else if (c == '"' && current == i) {
                        current++;
                        state = 2;
                    }
                    break;
                }
                case 2: {
                    if (c == '"') {
                        if (cookie == null) {
                            cookie = new CookieImpl(key, headerValue.substring(current, i));
                        } else {
                            handleValue(cookie, key, headerValue.substring(current, i));
                        }
                        state = 0;
                        current = i + 1;
                        key = null;
                    }
                    break;
                }
            }
        }
        if (key == null) {
            if (current != headerValue.length()) {
                handleValue(cookie, headerValue.substring(current, headerValue.length()), null);
            }
        } else {
            if (current != headerValue.length()) {
                if(cookie == null) {
                    cookie = new CookieImpl(key, headerValue.substring(current, headerValue.length()));
                } else {
                    handleValue(cookie, key, headerValue.substring(current, headerValue.length()));
                }
            } else {
                handleValue(cookie, key, null);
            }
        }

        return cookie;
    }

    private static void handleValue(CookieImpl cookie, String key, String value) {
        if (key == null) {
            return;
        }
        if (key.equalsIgnoreCase("path")) {
            cookie.setPath(value);
        } else if (key.equalsIgnoreCase("domain")) {
            cookie.setDomain(value);
        } else if (key.equalsIgnoreCase("max-age")) {
            cookie.setMaxAge(Integer.parseInt(value));
        } else if (key.equalsIgnoreCase("expires")) {
            cookie.setExpires(DateUtils.parseDate(value));
        } else if (key.equalsIgnoreCase("discard")) {
            cookie.setDiscard(true);
        } else if (key.equalsIgnoreCase("secure")) {
            cookie.setSecure(true);
        } else if (key.equalsIgnoreCase("httpOnly")) {
            cookie.setHttpOnly(true);
        } else if (key.equalsIgnoreCase("version")) {
            cookie.setVersion(Integer.parseInt(value));
        } else if (key.equalsIgnoreCase("comment")) {
            cookie.setComment(value);
        } else if (key.equalsIgnoreCase("samesite")) {
            cookie.setSameSiteMode(value);
        } else {
            cookie.setAttribute(key, value);
        }
        //otherwise ignore this key-value pair
    }

    /**
    /**
     * Parses the cookies from a list of "Cookie:" header values. The cookie header values are parsed according to RFC2109 that
     * defines the following syntax:
     *
     * <pre>
     * <code>
     * cookie          =  "Cookie:" cookie-version
     *                    1*((";" | ",") cookie-value)
     * cookie-value    =  NAME "=" VALUE [";" path] [";" domain]
     * cookie-version  =  "$Version" "=" value
     * NAME            =  attr
     * VALUE           =  value
     * path            =  "$Path" "=" value
     * domain          =  "$Domain" "=" value
     * </code>
     * </pre>
     *
     * @param maxCookies The maximum number of cookies. Used to prevent hash collision attacks
     * @param allowEqualInValue if true equal characters are allowed in cookie values
     * @param cookies The cookie values to parse
     * @return A pared cookie map
     *
     * @see Cookie
     * @see <a href="http://tools.ietf.org/search/rfc2109">rfc2109</a>
     * @deprecated use {@link #parseRequestCookies(int, boolean, List, Set)} instead
     */
    @Deprecated(since="2.2.0", forRemoval=true)
    public static Map<String, Cookie> parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies) {
        return parseRequestCookies(maxCookies, allowEqualInValue, cookies, LegacyCookieSupport.COMMA_IS_SEPARATOR);
    }

    @Deprecated(since="2.4.0", forRemoval=true)
    public static void parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, Set<Cookie> parsedCookies) {
        parseRequestCookies(maxCookies, allowEqualInValue, cookies, parsedCookies, LegacyCookieSupport.COMMA_IS_SEPARATOR, LegacyCookieSupport.ALLOW_HTTP_SEPARATORS_IN_V0);
    }

    @Deprecated(since="2.4.0", forRemoval=true)
    static Map<String, Cookie> parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, boolean commaIsSeperator) {
        return parseRequestCookies(maxCookies, allowEqualInValue, cookies, commaIsSeperator, LegacyCookieSupport.ALLOW_HTTP_SEPARATORS_IN_V0, true);
    }

    /**
    *
    * @deprecated use {@link #parseRequestCookies(int, boolean, List, MultiValueHashListStorage, boolean, boolean)}
    */
   @Deprecated(since="2.4.0", forRemoval=true)
   static void parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, Set<Cookie> parsedCookies, boolean commaIsSeperator) {
       parseRequestCookies(maxCookies, allowEqualInValue, cookies, parsedCookies, commaIsSeperator, LegacyCookieSupport.ALLOW_HTTP_SEPARATORS_IN_V0);
   }

   public static void parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, MultiValueHashListStorage<String, Cookie> parsedCookies) {
       parseRequestCookies(maxCookies, allowEqualInValue, cookies, parsedCookies, LegacyCookieSupport.COMMA_IS_SEPARATOR, LegacyCookieSupport.ALLOW_HTTP_SEPARATORS_IN_V0);
   }
   /**
    * @deprecated use {@link #parseRequestCookies(int, boolean, List, MultiValueHashListStorage, boolean, boolean, boolean)}
    */
    @Deprecated(since="2.4.0", forRemoval=true)
    static Map<String, Cookie> parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, boolean commaIsSeperator, boolean allowHttpSepartorsV0) {
        return parseRequestCookies(maxCookies, allowEqualInValue, cookies, commaIsSeperator, allowHttpSepartorsV0, true);
    }

   /**
    * @deprecated use {@link #parseRequestCookies(int, boolean, List, MultiValueHashListStorage, boolean, boolean, boolean)}
    */
    @Deprecated(since="2.4.0", forRemoval=true)
    static Map<String, Cookie> parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, boolean commaIsSeperator, boolean allowHttpSepartorsV0, final boolean rfc6265ParsingDisabled) {
        if (cookies == null) {
            return new TreeMap<>();
        }
        final MultiValueHashListStorage<String, Cookie> parsedCookies = new MultiValueHashListStorage<>();
        for (String cookie : cookies) {
            parseCookie(cookie, parsedCookies, maxCookies, allowEqualInValue, commaIsSeperator, allowHttpSepartorsV0, rfc6265ParsingDisabled);
        }

        final Map<String, Cookie> retVal = new TreeMap<>();
        Iterator<Cookie> cookiesIterator = parsedCookies.valuesIterator();
        Cookie cookie = null;
        while (cookiesIterator.hasNext()) {
            cookie = cookiesIterator.next();
            //NOTE this does not support multiple entries and will essentially collapse content
            if(!retVal.containsKey(cookie.getName()))
                retVal.put(cookie.getName(), cookie);
        }
        return retVal;
    }

   /**
    * @deprecated use {@link #parseRequestCookies(int, boolean, List, MultiValueHashListStorage, boolean, boolean, boolean)}
    */
   @Deprecated(since="2.4.0", forRemoval=true)
    static void parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, Set<Cookie> retVal, boolean commaIsSeperator, boolean allowHttpSepartorsV0) {
       if (cookies != null) {
           final MultiValueHashListStorage<String, Cookie> parsedCookies = new MultiValueHashListStorage<>();
           for (String cookie : cookies) {
                parseCookie(cookie, parsedCookies, maxCookies, allowEqualInValue, commaIsSeperator, allowHttpSepartorsV0, isObsoleteCookie(cookie));
            }
           for(String key:parsedCookies.keySet()) {
               final Cookie c = parsedCookies.get(key).get(0);
               retVal.add(c);
           }
        }
    }

   /**
    * @deprecated use {@link #parseRequestCookies(int, boolean, List, MultiValueHashListStorage, boolean, boolean)}
    */
   static void parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, MultiValueHashListStorage<String, Cookie> parsedCookies, boolean commaIsSeperator, boolean allowHttpSepartorsV0, boolean rfc6265ParsingDisabled) {
       if (cookies != null) {
           for (String cookie : cookies) {
               parseCookie(cookie, parsedCookies, maxCookies, allowEqualInValue, commaIsSeperator, allowHttpSepartorsV0, rfc6265ParsingDisabled);
           }
       }
   }

   static void parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, MultiValueHashListStorage<String, Cookie> parsedCookies, boolean commaIsSeperator, boolean allowHttpSepartorsV0) {
       if (cookies != null) {
           for (String cookie : cookies) {
               parseCookie(cookie, parsedCookies, maxCookies, allowEqualInValue, commaIsSeperator, allowHttpSepartorsV0, isObsoleteCookie(cookie));
           }
       }
   }

    public static void parseCookie(final String cookie, final MultiValueHashListStorage<String,Cookie> parsedCookies, int maxCookies, boolean allowEqualInValue, boolean commaIsSeperator, boolean allowHttpSepartorsV0, boolean rfc6265ParsingDisabled) {

        CookieJar cookieJar = new CookieJar();
        cookieJar.rfc6265ParsingDisabled = rfc6265ParsingDisabled;
        cookieJar.parsedCookies = parsedCookies;
        cookieJar.maxCookies = maxCookies;
        for (int i = 0; i < cookie.length(); ++i) {
            char c = cookie.charAt(i);
            switch (cookieJar.state) {
                case 0: {
                    //eat leading whitespace
                    if (c == ' ' || c == '\t' || c == ';') {
                        cookieJar.start = i + 1;
                        break;
                    }
                    cookieJar.state = 1;
                    //fall through
                }
                case 1: {
                    //extract key
                    if (c == '=') {
                        cookieJar.name = cookie.substring(cookieJar.start, i);
                        cookieJar.start = i + 1;
                        cookieJar.state = 2;
                    } else if (c == ';' || (commaIsSeperator && c == ',')) {
                        if(cookieJar.name != null) {
                            createCookie(cookie.substring(cookieJar.start, i), cookieJar);
                        } else if(UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
                            UndertowLogger.REQUEST_LOGGER.trace("Ignoring invalid cookies in header " + cookie);
                        }
                        cookieJar.state = 0;
                        cookieJar.start = i + 1;
                    }
                    break;
                }
                case 2: {
                    //extract value
                    if (c == ';' || (commaIsSeperator && c == ',')) {
                        createCookie(cookie.substring(cookieJar.start, i), cookieJar);
                        cookieJar.state = 0;
                        cookieJar.start = i + 1;
                    } else if (c == '"' && cookieJar.start == i) { //only process the " if it is the first character
                        cookieJar.containsEscapedQuotes = false;
                        cookieJar.inQuotes = true;
                        cookieJar.state = 3;
                        cookieJar.start = i + 1;
                    } else if (c == '=') {
                        if (!allowEqualInValue && !allowHttpSepartorsV0) {
                            createCookie(cookie.substring(cookieJar.start, i), cookieJar);
                            cookieJar.state = 4;
                            cookieJar.start = i + 1;
                        }
                    } else if (c != ':' && !allowHttpSepartorsV0 && LegacyCookieSupport.isHttpSeparator(c)) {
                        // http separators are not allowed in V0 cookie value unless io.undertow.legacy.cookie.ALLOW_HTTP_SEPARATORS_IN_V0 is set to true.
                        // However, "<hostcontroller-name>:<server-name>" (e.g. master:node1) is added as jvmRoute (instance-id) by default in WildFly domain mode.
                        // Though ":" is http separator, we allow it by default. Because, when Undertow runs as a proxy server (mod_cluster),
                        // we need to handle jvmRoute containing ":" in the request cookie value correctly to maintain the sticky session.
                        createCookie(cookie.substring(cookieJar.start, i), cookieJar);
                        cookieJar.state = 4;
                        cookieJar.start = i + 1;
                    }
                    break;
                }
                case 3: {
                    //extract quoted value
                    if (c == '"') {
                        if (!rfc6265ParsingDisabled && cookieJar.inQuotes) {
                            cookieJar.start = cookieJar.start - 1;
                            //i++;
                            createCookie(cookieJar.containsEscapedQuotes ? unescapeDoubleQuotes(cookie.substring(cookieJar.start, i + 1)) : cookie.substring(cookieJar.start, i + 1), cookieJar);
                        } else {
                            createCookie(cookieJar.containsEscapedQuotes ? unescapeDoubleQuotes(cookie.substring(cookieJar.start, i)) : cookie.substring(cookieJar.start, i), cookieJar);
                        }
                        cookieJar.inQuotes = false;
                      //if there is more, make sure next is separator
                        if (i + 1 < cookie.length() && (cookie.charAt(i + 1) == ';'      // Cookie: key="\"; key2=...
                                || (commaIsSeperator && cookie.charAt(i + 1) == ','))    // Cookie: key="\", key2=...
                                || i+1 == cookie.length()) {  //end of cookie
                           //spin around, its ok.

                        } else {
                            // Cookie: key="\" SOMEMORE; key2=...
                            if(UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
                                UndertowLogger.REQUEST_LOGGER.trace("Ignoring invalid cookies in header '" + cookie+"', cookie: '"+cookieJar.currentCookie.getName()+"'");
                            }
                            //this is enough, it wont be added in th end.
                            cookieJar.currentCookie = null;
                            //seek next separator
                            int seekIndex = i + 1;
                            while(seekIndex<cookie.length()) {
                                final char seeker = cookie.charAt(seekIndex);
                                if(!(seeker == ';'      // Cookie: key="\"; key2=...
                                        || (commaIsSeperator && seeker == ','))) {
                                    seekIndex++;
                                } else {
                                    break;
                                }
                            }
                            cookieJar.start = seekIndex;
                            i = seekIndex-1;
                            //this will fall into state == 3 and below if
                        }
                    } else if (c == ';' || (commaIsSeperator && c == ',')) {
                        cookieJar.state = 0;
                        cookieJar.start = i + 1;
                    } else if (c == '\\' && (i + 1 < cookie.length()) && cookie.charAt(i + 1) == '"') {
                        // Skip the next double quote char '"' when it is escaped by backslash '\' (i.e. \") inside the quoted value
                        // But..., do not skip at the following conditions
                        if (i + 2 == cookie.length()) { // Cookie: key="\" or Cookie: key="...\"
                            break;
                        }
                        if (i + 2 < cookie.length() && (cookie.charAt(i + 2) == ';'      // Cookie: key="\"; key2=...
                                || (commaIsSeperator && cookie.charAt(i + 2) == ','))) { // Cookie: key="\", key2=...
                            break;
                        }
                        // Skip the next double quote char ('"' behind '\') in the cookie value
                        i++;
                        cookieJar.containsEscapedQuotes = true;
                        cookieJar.inQuotes = false;
                    }
                    break;
                }
                case 4: {
                    //skip value portion behind '='
                    if (c == ';' || (commaIsSeperator && c == ',')) {
                        cookieJar.state = 0;
                    }
                    cookieJar.start = i + 1;
                    break;
                }
            }
        }
        if (cookieJar.state == 2) {
            createCookie(cookie.substring(cookieJar.start), cookieJar);
        }
        storeCookie(cookieJar);

    }

    private static void storeCookie(final CookieJar cookieJar ) {
        if(cookieJar.currentCookie != null) {
            cookieJar.parsedCookies.put(cookieJar.currentCookie.getName(), cookieJar.currentCookie);
            cookieJar.currentCookie = null;
        }
    }

    private static void createCookie(final String value, final CookieJar cookieJar) {
        if (cookieJar.parsedCookies.size() > cookieJar.maxCookies) {
            throw UndertowMessages.MESSAGES.tooManyCookies(cookieJar.maxCookies);
        }

        if(cookieJar.name == null) {
            if(UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
                UndertowLogger.REQUEST_LOGGER.trace("Unexpected input detected, corrupted parse.");
            }

            return;
        }

        if (!cookieJar.name.isEmpty() && cookieJar.name.charAt(0) == '$') {
            if(cookieJar.name.equals(VERSION)) {
                cookieJar.version = Integer.parseInt(value);
                //Theoretically this should happen only once at the start
                applyAdditional(cookieJar, cookieJar.name, value);
            } else if(cookieJar.currentCookie != null) {
                applyAdditional(cookieJar, cookieJar.name, value);
            }
            return;
        } else {
            storeCookie(cookieJar);
            cookieJar.currentCookie = new CookieImpl(cookieJar.name, value);
            cookieJar.name = null;
            return;
        }
    }

    private static void applyAdditional( final CookieJar cookieJar, final String name, final String value) {
        // RFC 6265 treats the domain, path and version attributes of an RFC 2109 cookie as a separate cookies
        if(!cookieJar.rfc6265ParsingDisabled && !name.isEmpty() && name.charAt(0) == '$') {
            Cookie c = new CookieImpl(name, value);
            cookieJar.parsedCookies.put(c.getName(), c);
        }
        if (cookieJar.version == 1) {
            // rfc2109 - add metadata to
            if (cookieJar.currentCookie != null) {
                cookieJar.currentCookie.setVersion(cookieJar.version);

                if (name.equals(DOMAIN)) {
                    cookieJar.currentCookie.setDomain(value);
                    return;
                }

                if (name.equals(PATH)) {
                    cookieJar.currentCookie.setPath(value);
                    return;
                }
            }
        }
        return;
    }


    private static String unescapeDoubleQuotes(final String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // Replace all escaped double quote (\") to double quote (")
        char[] tmp = new char[value.length()];
        int dest = 0;
        for(int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\\' && (i + 1 < value.length()) && value.charAt(i + 1) == '"') {
                i++;
            }
            tmp[dest] = value.charAt(i);
            dest++;
        }
        return new String(tmp, 0, dest);
    }

    private static boolean isObsoleteCookie(final String cookie) {
        return OBSOLETE_COOKIE_PATTERN.matcher(cookie).find();
    }

    private static final String CRUMB_SEPARATOR = "; ";

    /**
     * Cookie headers form: https://www.rfc-editor.org/rfc/rfc6265#section-4.2.1
     * If more than one header entry exist for "Cookie", it will be assembled into one that conforms to rfc.
     * @param headerMap
     */
    public static void assembleCrumbs(final HeaderMap headerMap) {
        final HeaderValues cookieValues = headerMap.get(Headers.COOKIE);

        if (cookieValues != null && cookieValues.size() > 1) {
            final StringBuilder oreos = new StringBuilder();
            final String[] _cookieValues = cookieValues.toArray();
            int slices = _cookieValues.length;
            for (final String slice : _cookieValues) {
                oreos.append(slice);
                slices--;
                if (slices >= 1) {
                    oreos.append(CRUMB_SEPARATOR);
                }
            }
            cookieValues.clear();
            cookieValues.add(oreos.toString());
        }
    }

    /**
     * IF there is single entry that follows RFC separation rules, it will be turned into singular fields. This should be only
     * used PRIOR to compression.
     *
     * @param headerMap
     */
    public static void disperseCrumbs(final HeaderMap headerMap) {
        final HeaderValues cookieValues = headerMap.get(Headers.COOKIE);
        // NOTE: If cookies are up2standard, thats the only case
        // otherwise something is up, dont touch it
        if (cookieValues != null && cookieValues.size() == 1) {
            if (cookieValues.getFirst().contains(CRUMB_SEPARATOR)) {
                final String[] cookieJar = cookieValues.getFirst().split(CRUMB_SEPARATOR);
                headerMap.remove(Headers.COOKIE);
                for (final String crumb : cookieJar) {
                    headerMap.addLast(Headers.COOKIE, crumb);
                }
            }
        }
    }

    /**
     * Fetch list containing crumbs( singular entries of Cookie header )
     * @param headerMap
     * @return
     */
    public static List<String> getCrumbs(final HeaderMap headerMap) {
        final HeaderValues cookieValues = headerMap.get(Headers.COOKIE);
        if (cookieValues != null) {
            if (cookieValues.size() == 1 && cookieValues.getFirst().contains(CRUMB_SEPARATOR)) {
                final String[] cookieJar = cookieValues.getFirst().split(CRUMB_SEPARATOR);
                return Arrays.asList(cookieJar);
            } else {
                return cookieValues;
            }
        } else {
            return Collections.emptyList();
        }
    }

    private static final String CRUMBS_ASSEMBLY_DISABLE = "io.undertow.server.protocol.http.DisableCookieCrumbsAssembly";
    private static final Boolean CRUMBS_ASSEMBLY_DISABLED = Boolean.valueOf(SecurityActions.getSystemProperty(CRUMBS_ASSEMBLY_DISABLE, "false"));

    public static boolean isCrumbsAssemplyDisabled() {
        return Cookies.CRUMBS_ASSEMBLY_DISABLED.booleanValue();
    }
    private Cookies() {

    }

    private static class CookieJar {
        public boolean rfc6265ParsingDisabled;
        //Currently parsed cookie, if V1, all $ will be applied to it, until
        CookieImpl currentCookie;
        int maxCookies;
        int version = -1;
        boolean inQuotes = false;
        boolean containsEscapedQuotes = false;
        int state = 0;
        String name = null;
        int start = 0;
        private MultiValueHashListStorage<String, Cookie> parsedCookies;
    }
}
