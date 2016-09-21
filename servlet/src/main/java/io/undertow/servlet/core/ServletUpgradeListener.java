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

package io.undertow.servlet.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import javax.servlet.http.HttpUpgradeHandler;

import org.xnio.ChannelListener;
import org.xnio.StreamConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ThreadSetupHandler;
import io.undertow.servlet.spec.WebConnectionImpl;

/**
 * Lister that handles a servlet exchange upgrade event.
 *
 * @author Stuart Douglas
 */
public class ServletUpgradeListener<T extends HttpUpgradeHandler> implements HttpUpgradeListener {
    private final HttpServerExchange exchange;
    private final ThreadSetupHandler.Action<Void, StreamConnection> initAction;
    private final ThreadSetupHandler.Action<Void, Object> destroyAction;

    public ServletUpgradeListener(final InstanceHandle<T> instance, Deployment deployment, HttpServerExchange exchange) {
        this.exchange = exchange;
        this.initAction = deployment.createThreadSetupAction(new ThreadSetupHandler.Action<Void, StreamConnection>() {
            @Override
            public Void call(HttpServerExchange exchange, StreamConnection context) {

                DelayedExecutor executor = new DelayedExecutor(exchange.getIoThread());
                try {
                    //run the upgrade in the worker thread
                    instance.getInstance().init(new WebConnectionImpl(context, ServletUpgradeListener.this.exchange.getConnection().getByteBufferPool(), executor));
                } finally {
                    executor.openGate();
                }
                return null;
            }
        });
        this.destroyAction = new ThreadSetupHandler.Action<Void, Object>() {
            @Override
            public Void call(HttpServerExchange exchange, Object context) throws Exception {
                try {
                    instance.getInstance().destroy();
                } finally {
                    instance.release();
                }
                return null;
            }
        };

    }

    @Override
    public void handleUpgrade(final StreamConnection channel, final HttpServerExchange exchange) {
        channel.getCloseSetter().set(new ChannelListener<StreamConnection>() {
            @Override
            public void handleEvent(StreamConnection channel) {
                try {
                    destroyAction.call(null, null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        this.exchange.getConnection().getWorker().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    initAction.call(exchange, channel);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    /**
     * Executor that delays submitting tasks to the delegate until a condition is satisfied.
     */
    private static final class DelayedExecutor implements Executor {

        private final Executor delegate;
        private volatile boolean queue = true;
        private final List<Runnable> tasks = new ArrayList<>();

        private DelayedExecutor(Executor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable command) {
            if (!queue) {
                delegate.execute(command);
            } else {
                synchronized (this) {
                    if (!queue) {
                        delegate.execute(command);
                    } else {
                        tasks.add(command);
                    }
                }
            }
        }

        synchronized void openGate() {
            queue = false;
            for (Runnable task : tasks) {
                delegate.execute(task);
            }
        }
    }
}
