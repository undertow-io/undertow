package io.undertow.server.handlers;

import io.undertow.UndertowMessages;
import io.undertow.attribute.ExchangeAttribute;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Handler that can accept or reject a request based on an attribute of the remote peer
 *
 * todo: should we support non-regex values for performance reasons?
 * @author Stuart Douglas
 * @author Andre Dietisheim
 */
public class AccessControlListHandler implements HttpHandler {

    private volatile HttpHandler next;
    private volatile boolean defaultAllow = false;
    private final ExchangeAttribute attribute;
    private final List<AclMatch> acl = new CopyOnWriteArrayList<AclMatch>();

    public AccessControlListHandler(final HttpHandler next, ExchangeAttribute attribute) {
        this.next = next;
        this.attribute = attribute;
    }

    public AccessControlListHandler(ExchangeAttribute attribute) {
        this.attribute = attribute;
        this.next = ResponseCodeHandler.HANDLE_404;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        String attribute = this.attribute.readAttribute(exchange);
        if (isAllowed(attribute)) {
            next.handleRequest(exchange);
        } else {
            exchange.setResponseCode(StatusCodes.FORBIDDEN);
            exchange.endExchange();
        }
    }

    //package private for unit tests
    boolean isAllowed(String attribute) {
        if (attribute != null) {
            for (AclMatch rule : acl) {
                if (rule.matches(attribute)) {
                    return !rule.isDeny();
                }
            }
        }
        return defaultAllow;
    }

    public boolean isDefaultAllow() {
        return defaultAllow;
    }

    public AccessControlListHandler setDefaultAllow(final boolean defaultAllow) {
        this.defaultAllow = defaultAllow;
        return this;
    }

    public HttpHandler getNext() {
        return next;
    }

    public AccessControlListHandler setNext(final HttpHandler next) {
        this.next = next;
        return this;
    }

    /**
     * Adds an allowed user agent peer to the ACL list
     * <p/>
     * User agent may be given as regex
     *
     * @param pattern The pattern to add to the ACL
     */
    public AccessControlListHandler addAllow(final String pattern) {
        return addRule(pattern, false);
    }

    /**
     * Adds an denied user agent to the ACL list
     * <p/>
     * User agent may be given as regex
     *
     * @param pattern The user agent to add to the ACL
     */
    public AccessControlListHandler addDeny(final String pattern) {
        return addRule(pattern, true);
    }

    public AccessControlListHandler clearRules() {
        this.acl.clear();
        return this;
    }

    private AccessControlListHandler addRule(final String userAgent, final boolean deny) {
        this.acl.add(new AclMatch(deny, userAgent));
        return this;
    }

    static class AclMatch {

        private final boolean deny;
        private final Pattern pattern;

        protected AclMatch(final boolean deny, final String pattern) {
            this.deny = deny;
            this.pattern = createPattern(pattern);
        }

        private Pattern createPattern(final String pattern) {
            try {
                return Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                throw UndertowMessages.MESSAGES.notAValidRegularExpressionPattern(pattern);
            }
        }

        boolean matches(final String attribute) {
            return pattern.matcher(attribute).matches();
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
