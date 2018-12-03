/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import javax.servlet.http.HttpUpgradeHandler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ThreadSetupHandler;

/**
 * Lister that handles a servlet exchange upgrade event.
 *
 * @author Stuart Douglas
 */
public class ServletUpgradeListener implements Consumer<ChannelHandlerContext> {
    private final HttpServerExchange exchange;
    private final ThreadSetupHandler.Action<Void, WebConnectionImpl> initAction;
    private final ThreadSetupHandler.Action<Void, Object> destroyAction;

    public ServletUpgradeListener(final InstanceHandle<? extends HttpUpgradeHandler> instance, Deployment deployment, HttpServerExchange exchange) {
        this.exchange = exchange;
        this.initAction = deployment.createThreadSetupAction(new ThreadSetupHandler.Action<Void, WebConnectionImpl>() {
            @Override
            public Void call(HttpServerExchange exchange, WebConnectionImpl context) {

                DelayedExecutor executor = new DelayedExecutor(exchange.getIoThread());
                try {
                    //run the upgrade in the worker thread
                    instance.getInstance().init(context);
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
    public void accept(ChannelHandlerContext context) {
        context.channel().closeFuture().addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                try {
                    destroyAction.call(null, null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        WebConnectionImpl connection = new WebConnectionImpl();
        context.pipeline().addLast(connection);
        this.exchange.getConnection().getWorker().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    initAction.call(exchange, connection);
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
