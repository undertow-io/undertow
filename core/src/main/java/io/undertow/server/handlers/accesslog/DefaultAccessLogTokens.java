package io.undertow.server.handlers.accesslog;

import io.undertow.server.HttpServerExchange;

import static io.undertow.server.handlers.accesslog.TokenHandler.Factory;

/**
 * Default factory for access log tokens.
 *
 * <p>This factory produces token handlers for the following patterns</p>
 * <ul>
 * <li><b>%a</b> - Remote IP address
 * <li><b>%A</b> - Local IP address
 * <li><b>%b</b> - Bytes sent, excluding HTTP headers, or '-' if no bytes
 *     were sent
 * <li><b>%B</b> - Bytes sent, excluding HTTP headers
 * <li><b>%h</b> - Remote host name
 * <li><b>%H</b> - Request protocol
 * <li><b>%l</b> - Remote logical username from identd (always returns '-')
 * <li><b>%m</b> - Request method
 * <li><b>%p</b> - Local port
 * <li><b>%q</b> - Query string (prepended with a '?' if it exists, otherwise
 *     an empty string
 * <li><b>%r</b> - First line of the request
 * <li><b>%s</b> - HTTP status code of the response
 * <li><b>%S</b> - User session ID
 * <li><b>%t</b> - Date and time, in Common Log Format format
 * <li><b>%u</b> - Remote user that was authenticated
 * <li><b>%U</b> - Requested URL path
 * <li><b>%v</b> - Local server name
 * <li><b>%D</b> - Time taken to process the request, in millis
 * <li><b>%T</b> - Time taken to process the request, in seconds
 * <li><b>%I</b> - current Request thread name (can compare later with stacktraces)
 * </ul>
 * <p>In addition, the caller can specify one of the following aliases for
 * commonly utilized patterns:</p>
 * <ul>
 * <li><b>common</b> - <code>%h %l %u %t "%r" %s %b</code>
 * <li><b>combined</b> -
 *   <code>%h %l %u %t "%r" %s %b "%{Referer}i" "%{User-Agent}i"</code>
 * </ul>
 *
 * <p>
 * There is also support to write information from the cookie, incoming
 * header, or the session<br>
 * It is modeled after the apache syntax:
 * <ul>
 * <li><code>%{xxx}i</code> for incoming headers
 * <li><code>%{xxx}o</code> for outgoing response headers
 * <li><code>%{xxx}c</code> for a specific cookie
 * <li><code>%{xxx}r</code> xxx is an attribute in the ServletRequest
 * <li><code>%{xxx}s</code> xxx is an attribute in the HttpSession
 * </ul>
 * </p>
 *
 *
 * @author Stuart Douglas
 */
public class DefaultAccessLogTokens implements Factory {

    public static final String REMOTE_IP = "%a";
    public static final String LOCAL_IP = "%A";
    public static final String BYTES_SENT_DASH = "%b";
    public static final String BYTES_SENT = "%B";
    public static final String REMOTE_HOST_NAME= "%h";
    public static final String REQUEST_PROTOCOL = "%H";
    public static final String IDENT_USERNAME = "%l";
    public static final String METHOD = "%m";
    public static final String LOCAL_PORT = "%p";
    public static final String QUERY_STRING = "%q";
    public static final String REQUEST_LINE = "%r";
    public static final String STATUS_CODE = "%s";
    public static final String SESSION_ID= "%S";
    public static final String DATE_TIME = "%t";
    public static final String REMOTE_USER= "%u";
    public static final String REQUESTED_URL= "%U";
    public static final String LOCAL_SERVER_NAME= "%v";
    public static final String TIME_TO_PROCESS_MILLIS= "%D";
    public static final String TIME_TO_PROCESS_SECONDS= "%T";
    public static final String THREAD_NAME = "%I";

    public static final String COMMON = "common";
    public static final String COMBINED = "combined";

    public static final DefaultAccessLogTokens INSTANCE = new DefaultAccessLogTokens();

    private static final CombinedTokenFactory FACTORY;

    static {
        FACTORY = new CombinedTokenFactory(
                remoteIp(),
                localIp()
        );
    }

    @Override
    public TokenHandler create(final String token) {
        return FACTORY.create(token);
    }

    public static final Factory remoteIp() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if(!token.equals(REMOTE_IP)) {
                    return null;
                }
                return new TokenHandler() {
                    @Override
                    public String generateMessage(final HttpServerExchange exchange) {
                        return exchange.getConnection().getPeerAddress().toString();
                    }
                };
            }
        };
    }

    public static final Factory localIp() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if(!token.equals(LOCAL_IP)) {
                    return null;
                }
                return new TokenHandler() {
                    @Override
                    public String generateMessage(final HttpServerExchange exchange) {
                        return exchange.getConnection().getLocalAddress().toString();
                    }
                };
            }
        };
    }

    private DefaultAccessLogTokens() {

    }
}
