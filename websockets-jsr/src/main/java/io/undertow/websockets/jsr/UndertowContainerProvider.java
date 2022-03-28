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

package io.undertow.websockets.jsr;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;

import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.servlet.api.ClassIntrospecter;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.util.DefaultClassIntrospector;

/**
 * @author Stuart Douglas
 */
public class UndertowContainerProvider extends ContainerProvider {

    private static final boolean directBuffers = Boolean.getBoolean("io.undertow.websockets.direct-buffers");
    private static final boolean invokeInIoThread = Boolean.getBoolean("io.undertow.websockets.invoke-in-io-thread");

    private static final RuntimePermission PERMISSION = new RuntimePermission("io.undertow.websockets.jsr.MODIFY_WEBSOCKET_CONTAINER");

    private static final Map<ClassLoader, WebSocketContainer> webSocketContainers = new ConcurrentHashMap<>();

    private static volatile ServerWebSocketContainer defaultContainer;
    private static volatile boolean defaultContainerDisabled = false;

    private static final SwitchableClassIntrospector defaultIntrospector = new SwitchableClassIntrospector();

    @Override
    protected WebSocketContainer getContainer() {
        ClassLoader tccl;
        if (System.getSecurityManager() == null) {
            tccl = Thread.currentThread().getContextClassLoader();
        } else {
            tccl = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    return Thread.currentThread().getContextClassLoader();
                }
            });
        }
        WebSocketContainer webSocketContainer = webSocketContainers.get(tccl);
        if (webSocketContainer == null) {
            return getDefaultContainer();
        }
        return webSocketContainer;
    }

    static ServerWebSocketContainer getDefaultContainer() {
        if (defaultContainerDisabled) {
            return null;
        }
        if (defaultContainer != null) {
            return defaultContainer;
        }
        synchronized (UndertowContainerProvider.class) {
            if (defaultContainer == null) {
                //this is not great, as we have no way to control the lifecycle
                //but there is not much we can do
                //todo: what options should we use here?
                ByteBufferPool buffers = new DefaultByteBufferPool(directBuffers, 1024, 100, 12);
                defaultContainer = new ServerWebSocketContainer(defaultIntrospector, UndertowContainerProvider.class.getClassLoader(), new Supplier<XnioWorker>() {
                    volatile XnioWorker worker;

                    @Override
                    public XnioWorker get() {
                        if(worker == null) {
                            synchronized (this) {
                                if(worker == null) {
                                    try {
                                        worker = Xnio.getInstance().createWorker(OptionMap.create(Options.THREAD_DAEMON, true));
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            }
                        }
                        return worker;
                    }
                }, buffers, Collections.EMPTY_LIST, !invokeInIoThread);
            }
            return defaultContainer;
        }
    }

    public static void addContainer(final ClassLoader classLoader, final WebSocketContainer webSocketContainer) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(PERMISSION);
        }
        webSocketContainers.put(classLoader, webSocketContainer);
    }

    public static void removeContainer(final ClassLoader classLoader) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(PERMISSION);
        }
        webSocketContainers.remove(classLoader);
    }

    public void setDefaultClassIntrospector(ClassIntrospecter classIntrospector) {
        defaultIntrospector.setIntrospecter(checkNotNullParam("classIntrospector", classIntrospector));
    }

    public static void disableDefaultContainer() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(PERMISSION);
        }
        defaultContainerDisabled = true;
    }


    private static class SwitchableClassIntrospector implements ClassIntrospecter {

        private volatile ClassIntrospecter introspecter = DefaultClassIntrospector.INSTANCE;

        @Override
        public <T> InstanceFactory<T> createInstanceFactory(Class<T> clazz) throws NoSuchMethodException {
            return introspecter.createInstanceFactory(clazz);
        }

        public void setIntrospecter(ClassIntrospecter introspecter) {
            this.introspecter = introspecter;
        }
    }
}
