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

package io.undertow.servlet.spec;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncListener;
import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.core.CompositeThreadSetupAction;
import io.undertow.servlet.handlers.ServletAttachments;
import io.undertow.servlet.handlers.ServletInitialHandler;
import io.undertow.servlet.handlers.ServletPathMatch;
import io.undertow.util.AttachmentKey;
import io.undertow.util.WorkerDispatcher;
import org.xnio.XnioExecutor;

/**
 * @author Stuart Douglas
 */
public class AsyncContextImpl implements AsyncContext {

    public static final AttachmentKey<Boolean> ASYNC_SUPPORTED = AttachmentKey.create(Boolean.class);
    public static final AttachmentKey<Executor> ASYNC_EXECUTOR = AttachmentKey.create(Executor.class);

    private final HttpServerExchange exchange;
    private final ServletRequest servletRequest;
    private final ServletResponse servletResponse;
    private final TimeoutTask timeoutTask = new TimeoutTask();


    //todo: make default configurable
    private volatile long timeout = 120000;

    private volatile XnioExecutor.Key timeoutKey;

    private Runnable dispatchAction;
    private boolean dispatched;
    private boolean initialRequestDone;
    private Thread initiatingThread;

    public AsyncContextImpl(final HttpServerExchange exchange, final ServletRequest servletRequest, final ServletResponse servletResponse) {
        this.exchange = exchange;
        this.servletRequest = servletRequest;
        this.servletResponse = servletResponse;
        initiatingThread = Thread.currentThread();
    }

    public void updateTimeout() {
        XnioExecutor.Key key = this.timeoutKey;
        if (key != null) {
            if (!key.remove()) {
                return;
            }
        }
        if (timeout > 0) {
            this.timeoutKey = exchange.getWriteThread().executeAfter(timeoutTask, timeout, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public ServletRequest getRequest() {
        return servletRequest;
    }

    @Override
    public ServletResponse getResponse() {
        return servletResponse;
    }

    @Override
    public boolean hasOriginalRequestAndResponse() {
        return servletRequest instanceof HttpServletRequestImpl &&
                servletResponse instanceof HttpServletResponseImpl;
    }

    @Override
    public void dispatch() {
        final HttpServletRequestImpl requestImpl = HttpServletRequestImpl.getRequestImpl(servletRequest);
        final ServletInitialHandler handler;
        Deployment deployment = requestImpl.getServletContext().getDeployment();
        if (servletRequest instanceof HttpServletRequest) {
            handler = deployment.getServletPaths().getServletHandlerByPath(((HttpServletRequest) servletRequest).getServletPath()).getHandler();
        } else {
            handler = deployment.getServletPaths().getServletHandlerByPath(exchange.getRelativePath()).getHandler();
        }

        final HttpServerExchange exchange = requestImpl.getExchange();

        exchange.putAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY, DispatcherType.ASYNC);

        exchange.putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, servletRequest);
        exchange.putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, servletResponse);

        dispatchAsyncRequest(requestImpl, handler, exchange);
    }

    private void dispatchAsyncRequest(final HttpServletRequestImpl requestImpl, final ServletInitialHandler handler, final HttpServerExchange exchange) {
        Executor executor = exchange.getAttachment(ASYNC_EXECUTOR);
        if (executor == null) {
            executor = exchange.getAttachment(WorkerDispatcher.EXECUTOR_ATTACHMENT_KEY);
        }
        if (executor == null) {
            executor = exchange.getConnection().getWorker();
        }

        final Executor e = executor;
        doDispatch(new Runnable() {
            @Override
            public void run() {
                e.execute(new Runnable() {
                    @Override
                    public void run() {

                        try {
                            handler.handleBlockingRequest(requestImpl.getExchange());
                        } catch (Exception e) {
                            //ignore
                        }
                    }
                });
            }
        });
    }

    @Override
    public void dispatch(final String path) {
        dispatch(servletRequest.getServletContext(), path);
    }

    @Override
    public void dispatch(final ServletContext context, final String path) {

        HttpServletRequestImpl requestImpl = HttpServletRequestImpl.getRequestImpl(servletRequest);
        HttpServletResponseImpl responseImpl = HttpServletResponseImpl.getResponseImpl(servletResponse);
        final ServletInitialHandler handler;
        final HttpServerExchange exchange = requestImpl.getExchange();

        exchange.putAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY, DispatcherType.ASYNC);

        requestImpl.setAttribute(ASYNC_REQUEST_URI, requestImpl.getRequestURI());
        requestImpl.setAttribute(ASYNC_CONTEXT_PATH, requestImpl.getContextPath());
        requestImpl.setAttribute(ASYNC_SERVLET_PATH, requestImpl.getServletPath());
        requestImpl.setAttribute(ASYNC_QUERY_STRING, requestImpl.getQueryString());

        String newQueryString = "";
        int qsPos = path.indexOf("?");
        String newServletPath = path;
        if (qsPos != -1) {
            newQueryString = newServletPath.substring(qsPos + 1);
            newServletPath = newServletPath.substring(0, qsPos);
        }
        String newRequestUri = context.getContextPath() + newServletPath;

        //todo: a more efficent impl
        Map<String, Deque<String>> newQueryParameters = new HashMap<String, Deque<String>>();
        for (String part : newQueryString.split("&")) {
            String name = part;
            String value = "";
            int equals = part.indexOf('=');
            if (equals != -1) {
                name = part.substring(0, equals);
                value = part.substring(equals + 1);
            }
            Deque<String> queue = newQueryParameters.get(name);
            if (queue == null) {
                newQueryParameters.put(name, queue = new ArrayDeque<String>(1));
            }
            queue.add(value);
        }
        requestImpl.setQueryParameters(newQueryParameters);

        requestImpl.getExchange().setRelativePath(newServletPath);
        requestImpl.getExchange().setQueryString(newQueryString);
        requestImpl.getExchange().setRequestPath(newRequestUri);
        requestImpl.getExchange().setRequestURI(newRequestUri);
        requestImpl.setServletContext((ServletContextImpl) context);
        responseImpl.setServletContext((ServletContextImpl) context);

        Deployment deployment = requestImpl.getServletContext().getDeployment();
        ServletPathMatch info = deployment.getServletPaths().getServletHandlerByPath(newServletPath);
        requestImpl.getExchange().putAttachment(ServletAttachments.SERVLET_PATH_MATCH, info);
        handler = info.getHandler();

        dispatchAsyncRequest(requestImpl, handler, exchange);
    }

    @Override
    public synchronized void complete() {
        HttpServletRequestImpl.getRequestImpl(servletRequest).onAsyncComplete();
        completeInternal();
    }

    public synchronized void completeInternal() {

        if (!initialRequestDone && Thread.currentThread() == initiatingThread) {
            //the context was stopped in the same request context it was started, we don't do anything
            if (dispatched) {
                throw UndertowServletMessages.MESSAGES.asyncRequestAlreadyDispatched();
            }
            dispatched = true;
            HttpServletRequestImpl request = HttpServletRequestImpl.getRequestImpl(servletRequest);
            request.asyncInitialRequestDone();
        } else {
            doDispatch(new Runnable() {
                @Override
                public void run() {
                    HttpServletResponseImpl response = HttpServletResponseImpl.getResponseImpl(servletResponse);
                    HttpServletRequestImpl request = HttpServletRequestImpl.getRequestImpl(servletRequest);
                    try {
                        request.getServletContext().getDeployment().getApplicationListeners().requestDestroyed(request);
                    } finally {
                        response.responseDone();
                    }
                }
            });
        }
    }

    @Override
    public void start(final Runnable run) {
        Executor executor = exchange.getAttachment(ASYNC_EXECUTOR);
        if (executor == null) {
            executor = exchange.getAttachment(WorkerDispatcher.EXECUTOR_ATTACHMENT_KEY);
        }
        if (executor == null) {
            executor = exchange.getConnection().getWorker();
        }
        final CompositeThreadSetupAction setup = HttpServletRequestImpl.getRequestImpl(servletRequest).getServletContext().getDeployment().getThreadSetupAction();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                ThreadSetupAction.Handle handle = setup.setup(null);
                try {
                    run.run();
                } finally {
                    handle.tearDown();
                }
            }
        });

    }


    @Override
    public void addListener(final AsyncListener listener) {
        HttpServletRequestImpl.getRequestImpl(servletRequest).addAsyncListener(listener);
    }

    @Override
    public void addListener(final AsyncListener listener, final ServletRequest servletRequest, final ServletResponse servletResponse) {
        HttpServletRequestImpl.getRequestImpl(servletRequest).addAsyncListener(listener, servletRequest, servletResponse);
    }

    @Override
    public <T extends AsyncListener> T createListener(final Class<T> clazz) throws ServletException {
        try {
            InstanceFactory<T> factory = ((ServletContextImpl) this.servletRequest.getServletContext()).getDeployment().getDeploymentInfo().getClassIntrospecter().createInstanceFactory(clazz);
            return factory.createInstance().getInstance();
        } catch (NoSuchMethodException e) {
            throw new ServletException(e);
        } catch (InstantiationException e) {
            throw new ServletException(e);
        }
    }

    @Override
    public void setTimeout(final long timeout) {
        this.timeout = timeout;
    }

    @Override
    public long getTimeout() {
        return timeout;
    }

    /**
     * Called by the container when the initial request is finished.
     * If this request has a dispatch or complete call pending then
     * this will be started.
     */
    public synchronized void initialRequestDone() {
        initialRequestDone = true;
        if (dispatchAction != null) {
            dispatchAction.run();
        } else {
            updateTimeout();
        }
        initiatingThread = null;
    }

    private synchronized void doDispatch(final Runnable runnable) {
        if (dispatched) {
            throw UndertowServletMessages.MESSAGES.asyncRequestAlreadyDispatched();
        }
        dispatched = true;
        if (initialRequestDone) {
            runnable.run();
        } else {
            this.dispatchAction = runnable;
        }
        if (timeoutKey != null) {
            timeoutKey.remove();
        }
    }


    private final class TimeoutTask implements Runnable {

        @Override
        public void run() {
            synchronized (AsyncContextImpl.this) {
                if (!dispatched) {
                    UndertowServletLogger.REQUEST_LOGGER.debug("Async request timed out");
                    HttpServletRequestImpl.getRequestImpl(servletRequest).onAsyncTimeout();
                    completeInternal();
                }
            }
        }
    }

}
