package io.undertow.servlet.core;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.SessionConfig;
import io.undertow.servlet.api.SessionIdentifierCodec;

/**
 * {@link SessionConfig} decorator that applies a given {@link SessionIdentifierCodec}
 * to the methods involving a session identifier.
 * @author Paul Ferraro
 */
public class CodecSessionConfig implements SessionConfig {

    private final SessionConfig config;
    private final SessionIdentifierCodec codec;

    public CodecSessionConfig(SessionConfig config, SessionIdentifierCodec codec) {
        this.config = config;
        this.codec = codec;
    }

    @Override
    public void setSessionId(HttpServerExchange exchange, String sessionId) {
        this.config.setSessionId(exchange, this.codec.encode(sessionId));
    }

    @Override
    public void clearSession(HttpServerExchange exchange, String sessionId) {
        this.config.clearSession(exchange, this.codec.encode(sessionId));
    }

    @Override
    public String findSessionId(HttpServerExchange exchange) {
        String encodedSessionId = this.config.findSessionId(exchange);
        return (encodedSessionId != null) ? this.codec.decode(encodedSessionId) : null;
    }

    @Override
    public SessionCookieSource sessionCookieSource(HttpServerExchange exchange) {
        return this.config.sessionCookieSource(exchange);
    }

    @Override
    public String rewriteUrl(String originalUrl, String sessionId) {
        return this.config.rewriteUrl(originalUrl, this.codec.encode(sessionId));
    }
}
