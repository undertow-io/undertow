package io.undertow.server.session;

import java.util.Deque;
import java.util.Locale;

import io.undertow.server.HttpServerExchange;

/**
 * Session config that is based on a path parameter and URL rewriting
 *
 * @author Stuart Douglas
 */
public class PathParameterSessionConfig implements SessionConfig {

    private final String name;

    public PathParameterSessionConfig(final String name) {
        this.name = name;
    }

    public PathParameterSessionConfig() {
        this(SessionCookieConfig.DEFAULT_SESSION_ID.toLowerCase(Locale.ENGLISH));
    }

    @Override
    public void setSessionId(final HttpServerExchange exchange, final String sessionId) {
        exchange.getPathParameters().remove(name);
        exchange.addPathParam(name, sessionId);
    }

    @Override
    public void clearSession(final HttpServerExchange exchange, final String sessionId) {
        exchange.getPathParameters().remove(name);
    }

    @Override
    public String findSessionId(final HttpServerExchange exchange) {
        Deque<String> stringDeque = exchange.getPathParameters().get(name);
        if (stringDeque == null) {
            return null;
        }
        return stringDeque.getFirst();
    }

    @Override
    public SessionCookieSource sessionCookieSource(HttpServerExchange exchange) {
        return findSessionId(exchange) != null ? SessionCookieSource.URL : SessionCookieSource.NONE;
    }

    /**
     * Return the specified URL with the specified session identifier
     * suitably encoded.
     *
     * @param url       URL to be encoded with the session id
     * @param sessionId Session id to be included in the encoded URL
     */
    @Override
    public String rewriteUrl(final String url, final String sessionId) {
        if ((url == null) || (sessionId == null))
            return (url);

        String path = url;
        String query = "";
        String anchor = "";
        int question = url.indexOf('?');
        if (question >= 0) {
            path = url.substring(0, question);
            query = url.substring(question);
        }
        int pound = path.indexOf('#');
        if (pound >= 0) {
            anchor = path.substring(pound);
            path = path.substring(0, pound);
        }
        StringBuilder sb = new StringBuilder(path);
        if (sb.length() > 0) { // jsessionid can't be first.
            sb.append(';');
            sb.append(name.toLowerCase(Locale.ENGLISH));
            sb.append('=');
            sb.append(sessionId);
        }
        sb.append(anchor);
        sb.append(query);
        return (sb.toString());
    }
}
