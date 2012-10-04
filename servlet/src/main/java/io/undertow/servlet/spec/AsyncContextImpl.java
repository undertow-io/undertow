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

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncListener;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
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
    private final TimeoutTask timeoutTask = new TimeoutTask(this);

    private volatile XnioExecutor.Key timeoutKey;

    public AsyncContextImpl(final HttpServerExchange exchange, final ServletRequest servletRequest, final ServletResponse servletResponse) {
        this.exchange = exchange;
        this.servletRequest = servletRequest;
        this.servletResponse = servletResponse;
    }

    private void updateTimeout() {
        XnioExecutor.Key key = this.timeoutKey;
        if (key != null) {
            if (!key.remove()) {
                return;
            }
        }
        this.timeoutKey = exchange.getWriteThread().executeAfter(timeoutTask, 10, TimeUnit.MINUTES);
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

    }

    @Override
    public void dispatch(final String path) {

    }

    @Override
    public void dispatch(final ServletContext context, final String path) {

    }

    @Override
    public void complete() {

    }

    @Override
    public void start(final Runnable run) {

    }

    @Override
    public void addListener(final AsyncListener listener) {

    }

    @Override
    public void addListener(final AsyncListener listener, final ServletRequest servletRequest, final ServletResponse servletResponse) {

    }

    @Override
    public <T extends AsyncListener> T createListener(final Class<T> clazz) throws ServletException {
        return null;
    }

    @Override
    public void setTimeout(final long timeout) {

    }

    @Override
    public long getTimeout() {
        return 0;
    }

    private static final class TimeoutTask implements Runnable {

        private final AsyncContextImpl asyncContext;

        private TimeoutTask(final AsyncContextImpl asyncContext) {
            this.asyncContext = asyncContext;
        }

        @Override
        public void run() {

        }
    }
}
