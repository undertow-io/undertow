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

package io.undertow.test.util;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpOpenListener;
import io.undertow.server.handlers.blocking.BlockingHandler;

/**
 * A class that starts a server before the test suite. By swapping out the root handler
 * tests can test various server functionality without continually starting and stopping the server.
 *
 * @author Stuart Douglas
 */
public class DefaultServer extends BlockJUnit4ClassRunner {

    private static final String DEFAULT = "default";

    private static boolean first = true;
    private static HttpOpenListener openListener;
    private static XnioWorker worker;
    private static AcceptingChannel<? extends ConnectedStreamChannel> server;
    private static Xnio xnio;

    /**
     * The executor service that is provided to
     */
    private static ExecutorService blockingExecutorService;

    /**
     * @return The base URL that can be used to make connections to this server
     */
    public static String getDefaultServerAddress() {
        return "http://" + getHostAddress(DEFAULT) + ":" + getHostPost(DEFAULT);
    }

    /**
     * This method returns a new blocking handler. The executor service it uses has its lifecycle controlled
     * by the test framework, so should not be shut down between tests.
     *
     * @return A new blocking handler
     */
    public static BlockingHandler newBlockingHandler() {
        final BlockingHandler ret = new BlockingHandler();
        ret.setExecutor(blockingExecutorService);
        return ret;
    }

    public DefaultServer(Class<?> klass) throws InitializationError {
        super(klass);
    }

    @Override
    public Description getDescription() {
        return super.getDescription();
    }

    @Override
    public void run(final RunNotifier notifier) {
        if (first) {
            first = false;
            xnio = Xnio.getInstance("nio", DefaultServer.class.getClassLoader());
            try {
                blockingExecutorService = Executors.newFixedThreadPool(10);
                worker = xnio.createWorker(OptionMap.create(Options.WORKER_WRITE_THREADS, 2, Options.WORKER_READ_THREADS, 2));
                openListener = new HttpOpenListener(new ByteBufferSlicePool(10000, 10000));
                ChannelListener acceptListener = ChannelListeners.openListenerAdapter(openListener);
                server = worker.createStreamServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPost(DEFAULT)), acceptListener, OptionMap.EMPTY);
                server.resumeAccepts();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            notifier.addListener(new RunListener() {
                @Override
                public void testRunFinished(final Result result) throws Exception {
                    server.close();
                    worker.shutdown();
                    blockingExecutorService.shutdownNow();
                }
            });
        }
        super.run(notifier);
    }

    /**
     * Sets the root handler for the default web server
     *
     * @param rootHandler The handler to use
     */
    public static void setRootHandler(HttpHandler rootHandler) {
        openListener.setRootHandler(rootHandler);
    }

    private static String getHostAddress(String serverName) {
        return System.getProperty(serverName + ".server.address");
    }

    private static int getHostPost(String serverName) {
        return Integer.getInteger(serverName + ".server.port");
    }

    public static ExecutorService getBlockingExecutorService() {
        return blockingExecutorService;
    }
}
