/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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
package io.undertow.server.handlers;

import java.util.regex.Pattern;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.NetworkUtils;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;

/**
 * Handler which check if Host header is properly formed and present.
 *
 * @author baranowb
 */
public class HostHeaderHandler implements HttpHandler {

    public static final HandlerWrapper WRAPPER = new Wrapper();

    public static final String STATUS_NO_HOST_HEADER = "No Host Header";
    public static final String STATUS_TOO_MANY_HOST_HEADERS = "Only One Host Header Allowed";
    public static final String STATUS_MALFORMED_PORT = "Host Header Malformed Port";
    public static final String STATUS_MALFORMED_IP_LITERAL = "Host Header Malformed IP-Literal";
    public static final String STATUS_MALFORMED_IP_LITERAL_BAD_CHARS = "Host Header Bad Characters";
    public static final String STATUS_HOST_NO_MATCH = "URI Host Header NO MATCH";
    private static final Pattern IP4_EXACT = Pattern.compile(NetworkUtils.IP4_EXACT);
    private static final Pattern IP6_EXACT = Pattern.compile(NetworkUtils.IP6_EXACT);
    private static final boolean[] ALLOWED_REGNAME_CHARACTERS = new boolean[256];
    private static final boolean[] HEX_CHARACTERS = new boolean[256];
    private static final boolean[] ALLOWED_IPv_FUTURE_CHARACTERS = new boolean[256]; // this is almost the same as
                                                                                     // ALLOWED_REGNAME_CHARACTERS with bonus
                                                                                     // ":" but having it as array rather than
                                                                                     // extra check is just faster
    static {
        // reg-name = *( unreserved / pct-encoded / sub-delims )
        // ALPHA / DIGIT / "-" / "." / "_" / "~" , %%, and "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
        for (int i = 0; i < ALLOWED_REGNAME_CHARACTERS.length; ++i) {
            if ((i >= '0' && i <= '9') || (i >= 'a' && i <= 'z') || (i >= 'A' && i <= 'Z')) {
                ALLOWED_REGNAME_CHARACTERS[i] = true;
                ALLOWED_IPv_FUTURE_CHARACTERS[i] = true;
            } else {
                switch (i) {
                    case '-':
                    case '.':
                    case '_':
                    case '~':
                    case '!':
                    case '$':
                    case '&':
                    case '\'':
                    case '(':
                    case ')':
                    case '*':
                    case '+':
                    case ',':
                    case ';':
                    case '=': {
                        ALLOWED_REGNAME_CHARACTERS[i] = true;
                        ALLOWED_IPv_FUTURE_CHARACTERS[i] = true;
                        break;
                    }
                    default:
                        ALLOWED_REGNAME_CHARACTERS[i] = false;
                        ALLOWED_IPv_FUTURE_CHARACTERS[i] = false;
                }
            }

        }

        ALLOWED_IPv_FUTURE_CHARACTERS[':'] = true;

        for (int i = 0; i < HEX_CHARACTERS.length; ++i) {
            if ((i >= '0' && i <= '9') || (i >= 'a' && i <= 'f') || (i >= 'A' && i <= 'F')) {
                HEX_CHARACTERS[i] = true;
            } else
                HEX_CHARACTERS[i] = false;
        }

    }
    private final HttpHandler next;

    public HostHeaderHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // TODO: add debug/warn log?
        // 400 if in case of no Host header or more than one: https://datatracker.ietf.org/doc/html/rfc7230#section-5.4
        // 400 if value violate rules. Host = uri-host [ ":" port ]
        // uri-host https://datatracker.ietf.org/doc/html/rfc3986#section-3.2.2
        // port https://datatracker.ietf.org/doc/html/rfc3986#section-3.2.3
        // NOTE: that 3.2.2 is NOT as restrictive as pure DNS it allows subdelims, percent etc by design as to not be
        // restricted to pure DNS, ALTHOUGH DNS compliant content is preferred.
        final HeaderMap headerMap = exchange.getRequestHeaders();
        final HeaderValues headerValues = headerMap.get(Headers.HOST);
        final HttpString protocol = exchange.getProtocol();

        if((protocol.equals(Protocols.HTTP_0_9) || protocol.equals(Protocols.HTTP_1_0))){
            if (headerValues == null) {
                //TODO: should we fake Host till we make it?
                next.handleRequest(exchange);
                return;
            }
            //else {
                //clients want to be good citizens and send it anyway.
                //fall through to below, first check will be false, but rest is the same as for HTTP1.1+
            //}
        }

        if (headerValues == null || headerValues.size() == 0) {
            // isEmpty - we assume http/https ? so authority is defined for this type, it cant be empty?
            terminate(exchange, STATUS_NO_HOST_HEADER);
            return;
        } else if (headerValues.size() > 1) {
            terminate(exchange, STATUS_TOO_MANY_HOST_HEADERS);
            return;
        }


        // parsing time.
        final String headerValue = headerValues.element();
        // uri-host [ ":" port ]
        // This is tricky, IP-Literal contain :, which is port delimiter in pair
        // Lets just try to take take care of port first
        final int rightBracketIndex = headerValue.lastIndexOf(']');
        final int lastColonIndex = headerValue.lastIndexOf(':');

        final String hostHeaderURI;
        // in case of IPv4, rightBracketIndex will be -1, in case of IPv6, it MUST be less than last :
        if (rightBracketIndex < lastColonIndex) {
            // we have port or potentially malformed IP Literal:
            // IP-literal = "[" ( IPv6address / IPvFuture ) "]" - without right bracket
            if (rightBracketIndex == -1 && headerValue.startsWith("[")) {
                // bad [ = 0, ] = -1, : = n+
                // good [ = 0, ] = n , : = n+x
                terminate(exchange, STATUS_MALFORMED_IP_LITERAL);
                return;
            }
            // we have valid host-uri with port
            final String portString = headerValue.substring(lastColonIndex + 1);
            try {
                int port = Integer.parseInt(portString);
                if (port <= 0 || port > 65535) {
                    // sanity check
                    // NOTE: 3.2.3 does not have provision like for IPv4 - decimal between 0-255.
                    // so this might be too restrictive
                    terminate(exchange, STATUS_MALFORMED_PORT);
                    return;
                }
                // fall through to uri-host checks
            } catch (NumberFormatException nfe) {
                terminate(exchange, STATUS_MALFORMED_PORT);
                return;
            }

            hostHeaderURI = headerValue.substring(0, lastColonIndex);
        } else {
            hostHeaderURI = headerValue;
        }

        // at this point we either have IP-Literal, IPv4 address or custom name
        if (rightBracketIndex > 0 && hostHeaderURI.indexOf('[') != 0) {
            // 1:2:4]*
            terminate(exchange, STATUS_MALFORMED_IP_LITERAL);
            return;
        } else if (rightBracketIndex > 0 && hostHeaderURI.indexOf('[') == 0) {
            // IPv6 or IPFuture
            // IP-literal = "[" ( IPv6address / IPvFuture ) "]"
            // IPvFuture = "v" 1*HEXDIG "." 1*( unreserved / sub-delims / ":" )
            final String debracked = hostHeaderURI.substring(1, hostHeaderURI.length() - 1);
            if (debracked.startsWith("v")) {
                final int dotIndex = debracked.indexOf(".");
                if (dotIndex < 2) {
                    // we need at least one HEX
                    terminate(exchange, STATUS_MALFORMED_IP_LITERAL);
                    return;
                }

                final String hex = debracked.substring(1, dotIndex);
                for (int i = 0; i < hex.length(); i++) {
                    final char c1 = hex.charAt(i);
                    if (!HEX_CHARACTERS[c1]) {
                        terminate(exchange, STATUS_MALFORMED_IP_LITERAL_BAD_CHARS);
                        return;
                    }
                }
                if (dotIndex + 1 >= debracked.length()) {
                    // we need some character behind dot.
                    terminate(exchange, STATUS_MALFORMED_IP_LITERAL);
                    return;
                }

                for (int i = dotIndex + 1; i < debracked.length(); i++) {
                    final char c = debracked.charAt(i);
                    if (!ALLOWED_IPv_FUTURE_CHARACTERS[c]) {
                        terminate(exchange, STATUS_MALFORMED_IP_LITERAL_BAD_CHARS);
                        return;
                    }
                }
            } else {
                // This will match IPv6 and IPv6 with embedded IPv4
                if (!IP6_EXACT.matcher(debracked).matches()) {
                    terminate(exchange, STATUS_MALFORMED_IP_LITERAL);
                    return;
                }
                // TODO: as in case of IPv6 do we need to vet some weir daddresses?
            }

        } else if (IP4_EXACT.matcher(hostHeaderURI).matches()) {
            // IPv4
            // NOTE: above will match only valid range 0-255, rest will fall through to reg-name, which is ok, since its
            // essentially
            // superset of IP, given DIGIT + "." ( unreserved )
        } else {
            // registered name can contain . and digits, so it technically overlap IPv4.
            // above wont cover -192.168.1.1/355.0.0.0, which technically is correct 'reg-name'
            // at this point we can really only check if its valid char or percent encoding.
            for (int index = 0; index < hostHeaderURI.length(); index++) {
                final char c = hostHeaderURI.charAt(index);
                if (c == '%') {
                    // we need at least two more that are HEX
                    if (index + 2 < hostHeaderURI.length()) {
                        char c1 = hostHeaderURI.charAt(++index);
                        char c2 = hostHeaderURI.charAt(++index);
                        if (!(HEX_CHARACTERS[c1] && HEX_CHARACTERS[c2])) {
                            terminate(exchange, STATUS_MALFORMED_IP_LITERAL_BAD_CHARS);
                            return;
                        }
                    } else {
                        // we dont have at least two chars(hex), so its not proper percent escape
                        terminate(exchange, STATUS_MALFORMED_IP_LITERAL);
                        return;
                    }
                } else if (!ALLOWED_REGNAME_CHARACTERS[c]) {
                    terminate(exchange, STATUS_MALFORMED_IP_LITERAL_BAD_CHARS);
                    return;
                }
                // valid
            }
        }
        // NOTE: at this point if userinfo("@") was present in host, it would have failed
        // we need only to check if Host header value is contained within URI if its absolute or authority
        if (exchange.isHostIncludedInRequestURI()) {
            if (!exchange.getRequestURI().contains(hostHeaderURI)) {
                terminate(exchange, STATUS_HOST_NO_MATCH);
                return;
            } else if (hostHeaderURI.isEmpty()) {
                terminate(exchange, STATUS_HOST_NO_MATCH);
                return;
            }
        }

        // in the end...
        next.handleRequest(exchange);
    }

    private void terminate(final HttpServerExchange exchange, final String message) {
        exchange.setStatusCode(StatusCodes.BAD_REQUEST);
        exchange.setResponseContentLength(0);
        exchange.getResponseHeaders().add(Headers.CONNECTION, Headers.CLOSE.toString());
        exchange.setReasonPhrase(message);
        exchange.endExchange();
    }

    private static class Wrapper implements HandlerWrapper {

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new HostHeaderHandler(handler);
        }
    }
}