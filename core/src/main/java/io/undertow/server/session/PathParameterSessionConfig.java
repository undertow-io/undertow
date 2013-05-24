package io.undertow.server.session;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Deque;

import io.undertow.server.HttpServerExchange;

/**
 * Session config that is based on a path parameter and URL rewriting
 *
 * @author Stuart Douglas
 */
public class PathParameterSessionConfig implements SessionConfig {

    private String name = SessionCookieConfig.DEFAULT_SESSION_ID;

    @Override
    public void setSessionId(final HttpServerExchange exchange, final String sessionId) {

    }

    @Override
    public void clearSession(final HttpServerExchange exchange, final String sessionId) {

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
    public String rewriteUrl(final String originalUrl, final Session session) {
        try {
            int pos = originalUrl.indexOf("?");
            if (pos != -1) {
                return new StringBuilder(originalUrl.substring(0, pos))
                        .append(";")
                        .append(name)
                        .append("=")
                        .append(URLEncoder.encode(session.getId(), "UTF-8"))
                        .append(originalUrl.substring(pos))
                        .toString();
            } else {
                return new StringBuilder(originalUrl)
                        .append(";")
                        .append(name)
                        .append("=")
                        .append(URLEncoder.encode(session.getId(), "UTF-8"))
                        .toString();
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
