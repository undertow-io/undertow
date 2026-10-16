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

package io.undertow.servlet.test.session;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionListener;
import io.undertow.server.session.SessionManager;
import io.undertow.server.session.SessionManagerStatistics;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.AttachmentKey;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Demonstrates that {@code sendRedirect()} calls {@code responseDone()} which calls
 * {@code session.requestDone()}, making the session inaccessible to subsequent code
 * in the filter chain.
 * <p>
 * With the default {@link InMemorySessionManager}, {@code requestDone()} is benign
 * (just updates timestamps). But with a session manager where {@code requestDone()}
 * releases the session (like WildFly's distributable session manager), this causes
 * the session to become inaccessible while the filter chain is still executing.
 * <p>
 * This test uses a custom session manager that wraps {@link InMemorySessionManager}
 * and makes sessions throw {@code IllegalStateException} after {@code requestDone()},
 * simulating the behavior of WildFly's distributable session manager.
 */
@RunWith(DefaultServer.class)
public class SendRedirectSessionAccessTestCase {

    /**
     * Per-exchange set of session IDs that have had {@code requestDone()} called.
     * Scoped to the exchange so that a "closed" session in request N does not
     * appear closed in request N+1.
     */
    private static final AttachmentKey<Set<String>> CLOSED_SESSIONS_KEY =
            AttachmentKey.create(Set.class);

    /**
     * Set by {@link SessionAccessFilter} if it fails to access the session after
     * the servlet calls {@code sendRedirect()}.
     */
    static volatile String filterError;

    @BeforeClass
    public static void setup() throws ServletException {
        filterError = null;

        final PathHandler pathHandler = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/test")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("test.war")
                .setSessionManagerFactory(deployment ->
                    new ClosingSessionManagerWrapper(new InMemorySessionManager("test")))
                .addServlets(
                    new ServletInfo("createSession", CreateSessionServlet.class)
                        .addMapping("/create"),
                    new ServletInfo("redirect", RedirectServlet.class)
                        .addMapping("/redirect"))
                .addFilter(new FilterInfo("sessionAccess", SessionAccessFilter.class))
                .addFilterUrlMapping("sessionAccess", "/*", DispatcherType.REQUEST);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        pathHandler.addPrefixPath(builder.getContextPath(), manager.start());
        DefaultServer.setRootHandler(pathHandler);
    }

    /**
     * This test demonstrates the bug:
     * <ol>
     *   <li>First request: creates a session (establishing the JSESSIONID cookie)</li>
     *   <li>Second request: servlet calls {@code sendRedirect()}.
     *       The filter wrapping the servlet tries to access the session
     *       <em>after</em> the servlet returns. With a distributable session
     *       manager, the session is inaccessible because {@code sendRedirect()}
     *       already called {@code requestDone()} via {@code responseDone()}.</li>
     * </ol>
     */
    @Test
    public void testSessionAccessibleAfterSendRedirect() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            // Step 1: Create a session so subsequent requests have a JSESSIONID cookie
            HttpGet create = new HttpGet(DefaultServer.getDefaultServerURL() + "/test/create");
            HttpResponse result = client.execute(create);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String sessionId = HttpClientUtils.readResponse(result);
            Assert.assertNotNull("Session should have been created", sessionId);
            Assert.assertFalse("Session should not be empty", sessionId.isEmpty());

            // Step 2: Hit the redirect servlet — the filter tries to access
            //         the session after sendRedirect()
            HttpGet redirect = new HttpGet(DefaultServer.getDefaultServerURL() + "/test/redirect");
            result = client.execute(redirect);
            HttpClientUtils.readResponse(result); // consume response

            Assert.assertNull(
                "Filter should be able to access the session after sendRedirect(), but got: "
                    + filterError,
                filterError);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    /**
     * Servlet that creates a session and returns its ID.
     */
    public static class CreateSessionServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            HttpSession session = req.getSession(true);
            session.setAttribute("test", "value");
            resp.getWriter().write(session.getId());
        }
    }

    /**
     * Servlet that calls {@code sendRedirect()}.
     */
    public static class RedirectServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            req.getSession(false);
            resp.sendRedirect("/test/create");
        }
    }

    /**
     * Filter that accesses the session <em>after</em> the servlet returns.
     * This simulates what frameworks like Apache Wicket do in their detach/cleanup
     * phase: they store page state in the session after the response has been written.
     */
    public static class SessionAccessFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(request, response);

            // After the servlet returns (possibly after sendRedirect),
            // try to access the session — this is what frameworks do in detach/cleanup
            HttpServletRequest httpReq = (HttpServletRequest) request;
            try {
                HttpSession session = httpReq.getSession(false);
                if (session == null) {
                    filterError = "getSession(false) returned null after sendRedirect()";
                } else {
                    session.getAttribute("test");
                }
            } catch (Exception e) {
                filterError = e.getClass().getName() + ": " + e.getMessage();
            }
        }
    }

    /**
     * A {@link SessionManager} wrapper that delegates to {@link InMemorySessionManager}
     * but makes sessions throw {@code IllegalStateException} after {@code requestDone()},
     * simulating the behavior of WildFly's distributable session manager where
     * {@code requestDone()} calls {@code ManagedSession.close()} which releases the
     * Infinispan batch and makes the session inaccessible.
     * <p>
     * The "closed" state is tracked per-exchange via {@link #CLOSED_SESSIONS_KEY}
     * so that a session closed at the end of request N is accessible again in request N+1.
     */
    private static class ClosingSessionManagerWrapper implements SessionManager {
        private final InMemorySessionManager delegate;

        ClosingSessionManagerWrapper(InMemorySessionManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getDeploymentName() {
            return delegate.getDeploymentName();
        }

        @Override
        public void start() {
            delegate.start();
        }

        @Override
        public void stop() {
            delegate.stop();
        }

        @Override
        public Session createSession(HttpServerExchange exchange, SessionConfig config) {
            return new ClosingSessionWrapper(delegate.createSession(exchange, config));
        }

        @Override
        public Session getSession(HttpServerExchange exchange, SessionConfig config) {
            Session session = delegate.getSession(exchange, config);
            if (session == null) {
                return null;
            }
            Set<String> closed = exchange.getAttachment(CLOSED_SESSIONS_KEY);
            if (closed != null && closed.contains(session.getId())) {
                return null;
            }
            return new ClosingSessionWrapper(session);
        }

        @Override
        public Session getSession(String sessionId) {
            Session session = delegate.getSession(sessionId);
            return session != null ? new ClosingSessionWrapper(session) : null;
        }

        @Override
        public void registerSessionListener(SessionListener listener) {
            delegate.registerSessionListener(listener);
        }

        @Override
        public void removeSessionListener(SessionListener listener) {
            delegate.removeSessionListener(listener);
        }

        @Override
        public void setDefaultSessionTimeout(int timeout) {
            delegate.setDefaultSessionTimeout(timeout);
        }

        @Override
        public Set<String> getTransientSessions() {
            return delegate.getTransientSessions();
        }

        @Override
        public Set<String> getActiveSessions() {
            return delegate.getActiveSessions();
        }

        @Override
        public Set<String> getAllSessions() {
            return delegate.getAllSessions();
        }

        @Override
        public SessionManagerStatistics getStatistics() {
            return delegate.getStatistics();
        }
    }

    /**
     * Session wrapper that simulates the distributable session manager behavior:
     * after {@code requestDone()} is called, the session ID is recorded in the
     * exchange attachment so that further lookups via
     * {@link ClosingSessionManagerWrapper#getSession(HttpServerExchange, SessionConfig)}
     * return {@code null} for the remainder of this request. All methods on this
     * wrapper also throw {@code IllegalStateException} once closed, mirroring
     * WildFly's {@code CompositeSession} behavior after its Infinispan batch is released.
     */
    private static class ClosingSessionWrapper implements Session {
        private final Session delegate;
        private volatile boolean closed = false;

        ClosingSessionWrapper(Session delegate) {
            this.delegate = delegate;
        }

        private void checkClosed() {
            if (closed) {
                throw new IllegalStateException("Session has been closed by requestDone()");
            }
        }

        @Override
        public void requestDone(HttpServerExchange exchange) {
            delegate.requestDone(exchange);
            closed = true;
            Set<String> closedInExchange = exchange.getAttachment(CLOSED_SESSIONS_KEY);
            if (closedInExchange == null) {
                closedInExchange = Collections.newSetFromMap(new ConcurrentHashMap<>());
                exchange.putAttachment(CLOSED_SESSIONS_KEY, closedInExchange);
            }
            closedInExchange.add(delegate.getId());
        }

        @Override
        public String getId() {
            return delegate.getId();
        }

        @Override
        public long getCreationTime() {
            checkClosed();
            return delegate.getCreationTime();
        }

        @Override
        public long getLastAccessedTime() {
            checkClosed();
            return delegate.getLastAccessedTime();
        }

        @Override
        public void setMaxInactiveInterval(int interval) {
            checkClosed();
            delegate.setMaxInactiveInterval(interval);
        }

        @Override
        public int getMaxInactiveInterval() {
            checkClosed();
            return delegate.getMaxInactiveInterval();
        }

        @Override
        public Object getAttribute(String name) {
            checkClosed();
            return delegate.getAttribute(name);
        }

        @Override
        public Set<String> getAttributeNames() {
            checkClosed();
            return delegate.getAttributeNames();
        }

        @Override
        public Object setAttribute(String name, Object value) {
            checkClosed();
            return delegate.setAttribute(name, value);
        }

        @Override
        public Object removeAttribute(String name) {
            checkClosed();
            return delegate.removeAttribute(name);
        }

        @Override
        public void invalidate(HttpServerExchange exchange) {
            delegate.invalidate(exchange);
        }

        @Override
        public SessionManager getSessionManager() {
            return delegate.getSessionManager();
        }

        @Override
        public String changeSessionId(HttpServerExchange exchange, SessionConfig config) {
            checkClosed();
            return delegate.changeSessionId(exchange, config);
        }

        @Override
        public boolean isInvalid() {
            return delegate.isInvalid();
        }
    }
}
