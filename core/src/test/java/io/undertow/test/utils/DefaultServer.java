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

package io.undertow.test.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;

import io.undertow.ajp.AjpOpenListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpOpenListener;
import io.undertow.server.HttpTransferEncodingHandler;
import io.undertow.server.OpenListener;
import org.junit.Ignore;
import org.junit.internal.runners.model.EachTestNotifier;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.ssl.XnioSsl;

/**
 * A class that starts a server before the test suite. By swapping out the root handler
 * tests can test various server functionality without continually starting and stopping the server.
 *
 * @author Stuart Douglas
 */
public class DefaultServer extends BlockJUnit4ClassRunner {

    private static final String DEFAULT = "default";

    private static boolean first = true;
    private static OpenListener openListener;
    private static XnioWorker worker;
    private static AcceptingChannel<? extends ConnectedStreamChannel> server;
    private static AcceptingChannel<? extends ConnectedStreamChannel> sslServer;
    private static Xnio xnio;


    private static final String KEY_STORE_PROPERTY = "javax.net.ssl.keyStore";
    private static final String KEY_STORE_PASSWORD_PROPERTY = "javax.net.ssl.keyStorePassword";
    private static final String TRUST_STORE_PROPERTY = "javax.net.ssl.trustStore";
    private static final String TRUST_STORE_PASSWORD_PROPERTY = "javax.net.ssl.trustStorePassword";
    private static final String DEFAULT_KEY_STORE = "keystore.jks";
    private static final String DEFAULT_KEY_STORE_PASSWORD = "password";

    private static final boolean ajp = Boolean.getBoolean("ajp");

    public static void setKeyStoreAndTrustStore() {
        final InputStream stream = DefaultServer.class.getClassLoader().getResourceAsStream(DEFAULT_KEY_STORE);
        OutputStream out = null;
        String fileName = null;
        try {
            File store = File.createTempFile("keystore", "keys");
            store.deleteOnExit();
            fileName = store.getAbsolutePath();
            out = new FileOutputStream(store);

            byte[] data = new byte[1024];
            int r = 0;
            while ((r = stream.read(data)) > 0) {
                out.write(data, 0, r);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            IoUtils.safeClose(stream);
            IoUtils.safeClose(out);
        }
        if (System.getProperty(KEY_STORE_PROPERTY) == null) {
            System.setProperty(KEY_STORE_PROPERTY, fileName);
        }
        if (System.getProperty(KEY_STORE_PASSWORD_PROPERTY) == null) {
            System.setProperty(KEY_STORE_PASSWORD_PROPERTY, DEFAULT_KEY_STORE_PASSWORD);
        }
        if (System.getProperty(TRUST_STORE_PROPERTY) == null) {
            System.setProperty(TRUST_STORE_PROPERTY, fileName);
        }
        if (System.getProperty(TRUST_STORE_PASSWORD_PROPERTY) == null) {
            System.setProperty(TRUST_STORE_PASSWORD_PROPERTY, DEFAULT_KEY_STORE_PASSWORD);
        }
    }
    /**
     * @return The base URL that can be used to make connections to this server
     */
    public static String getDefaultServerAddress() {
        return "http://" + getHostAddress(DEFAULT) + ":" + getHostPort(DEFAULT);
    }

    public static String getDefaultServerSSLAddress() {
        return "https://" + getHostAddress(DEFAULT) + ":" + getHostSSLPort(DEFAULT);
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
        runInternal(notifier);
        super.run(notifier);
    }

    private static void runInternal(final RunNotifier notifier) {
        if (first) {
            first = false;
            setKeyStoreAndTrustStore();
            xnio = Xnio.getInstance("nio", DefaultServer.class.getClassLoader());
            try {
                worker = xnio.createWorker(OptionMap.builder()
                        .set(Options.WORKER_WRITE_THREADS, 4)
                        .set(Options.WORKER_READ_THREADS, 4)
                        .set(Options.CONNECTION_HIGH_WATER, 1000000)
                        .set(Options.CONNECTION_LOW_WATER, 1000000)
                        .set(Options.WORKER_TASK_CORE_THREADS, 10)
                        .set(Options.WORKER_TASK_MAX_THREADS, 12)
                        .set(Options.TCP_NODELAY, true)
                        .set(Options.CORK, true)
                        .getMap());

                OptionMap serverOptions = OptionMap.builder()
                        .set(Options.WORKER_ACCEPT_THREADS, 4)
                        .set(Options.TCP_NODELAY, true)
                        .set(Options.REUSE_ADDRESSES, true)
                        .getMap();
                ChannelListener acceptListener;
                if(ajp) {
                    openListener = new AjpOpenListener(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8192, 8192 * 8192), 8192);
                    acceptListener = ChannelListeners.openListenerAdapter(openListener);
                    server = worker.createStreamServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), 7777), acceptListener, serverOptions);
                } else {
                    openListener = new HttpOpenListener(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8192, 8192 * 8192), 8192);
                    acceptListener = ChannelListeners.openListenerAdapter(openListener);
                    server = worker.createStreamServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), acceptListener, serverOptions);
                }
                server.resumeAccepts();


                final XnioSsl xnioSsl = xnio.getSslProvider(OptionMap.EMPTY);
                sslServer = xnioSsl.createSslTcpServer(worker, new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostSSLPort(DEFAULT)), acceptListener, serverOptions);
                sslServer.resumeAccepts();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            notifier.addListener(new RunListener() {
                @Override
                public void testRunFinished(final Result result) throws Exception {
                    server.close();
                    sslServer.close();
                    worker.shutdown();
                }
            });
        }
    }

    @Override
    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
        if(ajp && (method.getAnnotation(AjpIgnore.class) != null || method.getMethod().getDeclaringClass().isAnnotationPresent(AjpIgnore.class))) {
            return;
        } else {
            super.runChild(method, notifier);
        }
    }


    /**
     * Sets the root handler for the default web server
     *
     * @param rootHandler The handler to use
     */
    public static void setRootHandler(HttpHandler rootHandler) {
        if(ajp) {
            openListener.setRootHandler(rootHandler);
        } else {
            final HttpTransferEncodingHandler ph = new HttpTransferEncodingHandler();
            ph.setNext(rootHandler);
            openListener.setRootHandler(ph);
        }

    }

    public static String getHostAddress(String serverName) {
        return System.getProperty(serverName + ".server.address", "localhost");
    }

    public static int getHostPort(String serverName) {
        return Integer.getInteger(serverName + ".server.port", 7777)  + (ajp ? 1111 : 0);
    }

    public static int getHostSSLPort(String serverName) {
        return Integer.getInteger(serverName + ".server.sslPort", 7778);
    }

    public static OptionMap getUndertowOptions() {
        return openListener.getUndertowOptions();
    }

    public static void setUndertowOptions(final OptionMap options) {
        openListener.setUndertowOptions(options);
    }

    public static class Parameterized extends org.junit.runners.Parameterized {

        public Parameterized(Class<?> klass) throws Throwable {
            super(klass);
        }

        @Override
        public void run(final RunNotifier notifier) {
            runInternal(notifier);
            super.run(notifier);
        }
    }

    public static boolean isAjp() {
        return ajp;
    }
}
