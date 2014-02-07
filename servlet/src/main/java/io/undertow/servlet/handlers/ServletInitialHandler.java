/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.handlers;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.server.SSLSessionInfo;
import io.undertow.server.ServerConnection;
import io.undertow.servlet.api.ServletDispatcher;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.core.ApplicationListeners;
import io.undertow.servlet.core.CompositeThreadSetupAction;
import io.undertow.servlet.core.ServletBlockingHttpExchange;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;
import io.undertow.servlet.spec.RequestDispatcherImpl;
import io.undertow.servlet.spec.ServletContextImpl;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Protocols;
import io.undertow.util.RedirectBuilder;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.Executor;

/**
 * This must be the initial handler in the blocking servlet chain. This sets up the request and response objects,
 * and attaches them the to exchange.
 *
 * @author Stuart Douglas
 */
public class ServletInitialHandler implements HttpHandler, ServletDispatcher {

    private static final RuntimePermission PERMISSION = new RuntimePermission("io.undertow.servlet.CREATE_INITIAL_HANDLER");

    private final HttpHandler next;
    //private final HttpHandler asyncPath;

    private final CompositeThreadSetupAction setupAction;

    private final ServletContextImpl servletContext;

    private final ApplicationListeners listeners;

    private final ServletPathMatches paths;

    public ServletInitialHandler(final ServletPathMatches paths, final HttpHandler next, final CompositeThreadSetupAction setupAction, final ServletContextImpl servletContext) {
        this.next = next;
        this.setupAction = setupAction;
        this.servletContext = servletContext;
        this.paths = paths;
        this.listeners = servletContext.getDeployment().getApplicationListeners();
        if(System.getSecurityManager() != null) {
            //handle request can use doPrivilidged
            //we need to make sure this is not abused
            AccessController.checkPermission(PERMISSION);
        }
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final String path = exchange.getRelativePath();
        if(isForbiddenPath(path)) {
            exchange.setResponseCode(404);
            return;
        }
        final ServletPathMatch info = paths.getServletHandlerByPath(path);
        if (info.getType() == ServletPathMatch.Type.REDIRECT) {
            //UNDERTOW-89
            //we redirect on GET requests to the root context to add an / to the end
            exchange.setResponseCode(302);
            exchange.getResponseHeaders().put(Headers.LOCATION, RedirectBuilder.redirect(exchange, exchange.getRelativePath() + "/", true));
            return;
        } else if (info.getType() == ServletPathMatch.Type.REWRITE) {
            //this can only happen if the path ends with a /
            //otherwise there would be a rewrite instead
            exchange.setRelativePath(exchange.getRelativePath() + info.getRewriteLocation());
            exchange.setRequestURI(exchange.getRequestURI() + info.getRewriteLocation());
            exchange.setRequestPath(exchange.getRequestPath() + info.getRewriteLocation());
        }

        final HttpServletResponseImpl response = new HttpServletResponseImpl(exchange, servletContext);
        final HttpServletRequestImpl request = new HttpServletRequestImpl(exchange, servletContext);
        final ServletRequestContext servletRequestContext = new ServletRequestContext(servletContext.getDeployment(), request, response, info);
        //set the max request size if applicable
        if (info.getServletChain().getManagedServlet().getMaxRequestSize() > 0) {
            exchange.setMaxEntitySize(info.getServletChain().getManagedServlet().getMaxRequestSize());
        }
        exchange.putAttachment(ServletRequestContext.ATTACHMENT_KEY, servletRequestContext);

        exchange.startBlocking(new ServletBlockingHttpExchange(exchange));
        servletRequestContext.setServletPathMatch(info);

        Executor executor = info.getServletChain().getExecutor();
        if (executor == null) {
            executor = servletContext.getDeployment().getExecutor();
        }

        if (exchange.isInIoThread() || executor != null) {
            //either the exchange has not been dispatched yet, or we need to use a special executor
            exchange.dispatch(executor, new HttpHandler() {
                @Override
                public void handleRequest(final HttpServerExchange exchange) throws Exception {
                    if(System.getSecurityManager() == null) {
                        dispatchRequest(exchange, servletRequestContext, info.getServletChain(), DispatcherType.REQUEST);
                    } else {
                        //sometimes thread pools inherit some random
                        AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                            @Override
                            public Object run() throws Exception{
                                dispatchRequest(exchange, servletRequestContext, info.getServletChain(), DispatcherType.REQUEST);
                                return null;
                            }
                        });
                    }
                }
            });
        } else {
            dispatchRequest(exchange, servletRequestContext, info.getServletChain(), DispatcherType.REQUEST);
        }
    }

    private boolean isForbiddenPath(String path) {
        return path.equalsIgnoreCase("/meta-inf/")
            || path.regionMatches(true, 0, "/web-inf/", 0, "/web-inf/".length());
    }

    public void dispatchToPath(final HttpServerExchange exchange, final ServletPathMatch pathInfo, final DispatcherType dispatcherType) throws Exception {
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        servletRequestContext.setServletPathMatch(pathInfo);
        dispatchRequest(exchange, servletRequestContext, pathInfo.getServletChain(), dispatcherType);
    }

    @Override
    public void dispatchToServlet(final HttpServerExchange exchange, final ServletChain servletchain, final DispatcherType dispatcherType) throws Exception {
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        dispatchRequest(exchange, servletRequestContext, servletchain, dispatcherType);
    }

    @Override
    public void dispatchMockRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException {

        final ByteBufferSlicePool bufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 1024, 1024);
        MockServerConnection connection = new MockServerConnection(bufferPool);
        HttpServerExchange exchange = new HttpServerExchange(connection);
        exchange.setRequestScheme(request.getScheme());
        exchange.setRequestMethod(new HttpString(request.getMethod()));
        exchange.setProtocol(Protocols.HTTP_1_0);
        exchange.setResolvedPath(request.getContextPath());
        String relative;
        if (request.getPathInfo() == null) {
            relative = request.getServletPath();
        } else {
            relative = request.getServletPath() + request.getPathInfo();
        }
        exchange.setRelativePath(relative);
        final ServletPathMatch info = paths.getServletHandlerByPath(request.getServletPath());
        final HttpServletResponseImpl oResponse = new HttpServletResponseImpl(exchange, servletContext);
        final HttpServletRequestImpl oRequest = new HttpServletRequestImpl(exchange, servletContext);
        final ServletRequestContext servletRequestContext = new ServletRequestContext(servletContext.getDeployment(), oRequest, oResponse, info);
        servletRequestContext.setServletRequest(request);
        servletRequestContext.setServletResponse(response);
        //set the max request size if applicable
        if (info.getServletChain().getManagedServlet().getMaxRequestSize() > 0) {
            exchange.setMaxEntitySize(info.getServletChain().getManagedServlet().getMaxRequestSize());
        }
        exchange.putAttachment(ServletRequestContext.ATTACHMENT_KEY, servletRequestContext);

        exchange.startBlocking(new ServletBlockingHttpExchange(exchange));
        servletRequestContext.setServletPathMatch(info);

        try {
            dispatchRequest(exchange, servletRequestContext, info.getServletChain(), DispatcherType.REQUEST);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new ServletException(e);
        }
    }

    private void dispatchRequest(final HttpServerExchange exchange, final ServletRequestContext servletRequestContext, final ServletChain servletChain, final DispatcherType dispatcherType) throws Exception {
        servletRequestContext.setDispatcherType(dispatcherType);
        servletRequestContext.setCurrentServlet(servletChain);
        if (dispatcherType == DispatcherType.REQUEST || dispatcherType == DispatcherType.ASYNC) {
            handleFirstRequest(exchange, servletChain, servletRequestContext, servletRequestContext.getServletRequest(), servletRequestContext.getServletResponse());
        } else {
            next.handleRequest(exchange);
        }
    }

    public void handleFirstRequest(final HttpServerExchange exchange, final ServletChain servletChain, final ServletRequestContext servletRequestContext, final ServletRequest request, final ServletResponse response) throws Exception {

        ThreadSetupAction.Handle handle = setupAction.setup(exchange);
        try {
            SecurityActions.setCurrentRequestContext(servletRequestContext);
            try {
                listeners.requestInitialized(request);
                next.handleRequest(exchange);
                //
            } catch (Throwable t) {

                if(t instanceof IOException) {
                    //we log IOExceptions at a lower level
                    //because they can be easily caused by malicious remote clients in at attempt to DOS the server by filling the logs
                    UndertowLogger.REQUEST_IO_LOGGER.debugf(t, "Exception handling request to %s", exchange.getRequestURI());
                } else {
                    UndertowLogger.REQUEST_LOGGER.exceptionHandlingRequest(t, exchange.getRequestURI());
                }
                if (request.isAsyncStarted() || request.getDispatcherType() == DispatcherType.ASYNC) {
                    exchange.unDispatch();
                    servletRequestContext.getOriginalRequest().getAsyncContextInternal().handleError(t);
                } else {
                    if (!exchange.isResponseStarted()) {
                        response.reset();                       //reset the response
                        exchange.setResponseCode(500);
                        exchange.getResponseHeaders().clear();
                        String location = servletContext.getDeployment().getErrorPages().getErrorLocation(t);
                        if (location != null) {
                            RequestDispatcherImpl dispatcher = new RequestDispatcherImpl(location, servletContext);
                            try {
                                dispatcher.error(request, response, servletChain.getManagedServlet().getServletInfo().getName(), t);
                            } catch (Exception e) {
                                UndertowLogger.REQUEST_LOGGER.exceptionGeneratingErrorPage(e, location);
                            }
                        } else {
                            if (servletRequestContext.displayStackTraces()) {
                                ServletDebugPageHandler.handleRequest(exchange, servletRequestContext, t);
                            } else {
                                //TODO: we need a debug mode to generate a debug error page
                                if (response instanceof HttpServletResponse) {
                                    ((HttpServletResponse) response).sendError(500);
                                } else {
                                    servletRequestContext.getOriginalResponse().sendError(500);
                                }
                            }
                        }
                    }
                }

            } finally {
                listeners.requestDestroyed(request);
            }
            //if it is not dispatched and is not a mock request
            if (!exchange.isDispatched() && !(exchange.getConnection() instanceof MockServerConnection)) {
                servletRequestContext.getOriginalResponse().responseDone();
            }
        } finally {
            try {
                handle.tearDown();
            } finally {
                SecurityActions.clearCurrentServletAttachments();
            }
        }
    }

    public HttpHandler getNext() {
        return next;
    }

    private static class MockServerConnection extends ServerConnection {
        private final Pool<ByteBuffer> bufferPool;
        private SSLSessionInfo sslSessionInfo;

        private MockServerConnection(Pool<ByteBuffer> bufferPool) {
            this.bufferPool = bufferPool;
        }

        @Override
        public Pool<ByteBuffer> getBufferPool() {
            return bufferPool;
        }

        @Override
        public XnioWorker getWorker() {
            return null;
        }

        @Override
        public XnioIoThread getIoThread() {
            return null;
        }

        @Override
        public HttpServerExchange sendOutOfBandResponse(HttpServerExchange exchange) {
            throw new IllegalStateException();
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public boolean supportsOption(Option<?> option) {
            return false;
        }

        @Override
        public <T> T getOption(Option<T> option) throws IOException {
            return null;
        }

        @Override
        public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
            return null;
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public SocketAddress getPeerAddress() {
            return null;
        }

        @Override
        public <A extends SocketAddress> A getPeerAddress(Class<A> type) {
            return null;
        }

        @Override
        public ChannelListener.Setter<? extends ConnectedChannel> getCloseSetter() {
            return null;
        }

        @Override
        public SocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
            return null;
        }

        @Override
        public OptionMap getUndertowOptions() {
            return OptionMap.EMPTY;
        }

        @Override
        public int getBufferSize() {
            return 1024;
        }

        @Override
        public SSLSessionInfo getSslSessionInfo() {
            return sslSessionInfo;
        }

        @Override
        public void setSslSessionInfo(SSLSessionInfo sessionInfo) {
            sslSessionInfo = sessionInfo;
        }

        @Override
        public void addCloseListener(CloseListener listener) {
        }

        @Override
        public StreamConnection upgradeChannel() {
            return null;
        }

        @Override
        public ConduitStreamSinkChannel getSinkChannel() {
            return null;
        }

        @Override
        public ConduitStreamSourceChannel getSourceChannel() {
            return new ConduitStreamSourceChannel(null, null);
        }

        @Override
        protected StreamSinkConduit getSinkConduit(HttpServerExchange exchange, StreamSinkConduit conduit) {
            return conduit;
        }

        @Override
        protected boolean isUpgradeSupported() {
            return false;
        }

        @Override
        protected void exchangeComplete(HttpServerExchange exchange) {
        }

        @Override
        protected void setUpgradeListener(HttpUpgradeListener upgradeListener) {
            //ignore
        }
    }

}
