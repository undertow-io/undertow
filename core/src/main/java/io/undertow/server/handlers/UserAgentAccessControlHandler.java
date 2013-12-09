package io.undertow.server.handlers;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Handler that can accept or reject a request based on the user agent of the remote peer.
 *
 * @author Andre Dietisheim
 */
public class UserAgentAccessControlHandler implements HttpHandler {

    private volatile HttpHandler next;
    private volatile boolean defaultAllow = false;
    private final List<UserAgentMatch> userAgentAcl = new CopyOnWriteArrayList<UserAgentMatch>();

    public UserAgentAccessControlHandler(final HttpHandler next) {
        this.next = next;
    }

    public UserAgentAccessControlHandler() {
        this.next = ResponseCodeHandler.HANDLE_404;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        String userAgent = getUserAgent(exchange);
        if (userAgent != null && isAllowed(userAgent)) {
            next.handleRequest(exchange);
        } else {
            exchange.setResponseCode(StatusCodes.FORBIDDEN);
            exchange.endExchange();
        }
    }

    String getUserAgent(HttpServerExchange exchange) {
        String userAgent = null;
        HeaderMap headers = exchange.getRequestHeaders();
        if (headers != null) {
            HeaderValues values = headers.get(Headers.USER_AGENT_STRING);
            if (values != null && !values.isEmpty()) {
                userAgent = values.getFirst();
            }
        }
        return userAgent;
    }

    boolean isAllowed(String userAgent) {
        for (UserAgentMatch rule : userAgentAcl) {
            if (rule.matches(userAgent)) {
                return !rule.isDeny();
            }
        }
        return defaultAllow;
    }

    public boolean isDefaultAllow() {
        return defaultAllow;
    }

    public UserAgentAccessControlHandler setDefaultAllow(final boolean defaultAllow) {
        this.defaultAllow = defaultAllow;
        return this;
    }

    public HttpHandler getNext() {
        return next;
    }

    public UserAgentAccessControlHandler setNext(final HttpHandler next) {
        this.next = next;
        return this;
    }

    /**
     * Adds an allowed user agent peer to the ACL list
     * <p/>
     * User agent may be given as regex
     *
     * @param userAgent The user agent to add to the ACL
     */
    public UserAgentAccessControlHandler addAllow(final String userAgent) {
        return addRule(userAgent, false);
    }

    /**
     * Adds an denied user agent to the ACL list
     * <p/>
     * User agent may be given as regex
     *
     * @param peer The user agent to add to the ACL
     */
    public UserAgentAccessControlHandler addDeny(final String userAgent) {
        return addRule(userAgent, true);
    }

    public UserAgentAccessControlHandler clearRules() {
        this.userAgentAcl.clear();
        return this;
    }

    private UserAgentAccessControlHandler addRule(final String userAgent, final boolean deny) {
        this.userAgentAcl.add(new UserAgentMatch(deny, userAgent));
        return this;
    }

    static class UserAgentMatch {

        private final boolean deny;
        private final Pattern pattern;

        protected UserAgentMatch(final boolean deny, final String pattern) {
            this.deny = deny;
            this.pattern = createPattern(pattern);
        }

        private Pattern createPattern(final String pattern) {
            try {
                return Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                throw UndertowMessages.MESSAGES.notAValidIpPattern(pattern);
            }
        }

        boolean matches(final String userAgent) {
            return pattern.matcher(userAgent).matches();
        }

        boolean isDeny() {
            return deny;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName()
                    + "{"
                    + "deny=" + deny
                    + ", pattern='" + pattern + '\''
                    + '}';
        }
    }
}
