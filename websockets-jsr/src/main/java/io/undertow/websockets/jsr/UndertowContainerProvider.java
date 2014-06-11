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

import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.core.CompositeThreadSetupAction;
import io.undertow.servlet.util.DefaultClassIntrospector;
import org.xnio.ByteBufferSlicePool;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Stuart Douglas
 */
public class UndertowContainerProvider extends ContainerProvider {

    private static final RuntimePermission PERMISSION = new RuntimePermission("io.undertow.websockets.jsr.MODIFY_WEBSOCKET_CONTAINER");

    private static final Map<ClassLoader, WebSocketContainer> webSocketContainers = new ConcurrentHashMap<>();

    private static volatile WebSocketContainer defaultContainer;
    private static volatile boolean defaultContainerDisabled = false;

    @Override
    protected WebSocketContainer getContainer() {
        ClassLoader tccl;
        if(System.getSecurityManager() == null) {
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
        if(webSocketContainer == null) {
            return getDefaultContainer();
        }
        return webSocketContainer;
    }

    private WebSocketContainer getDefaultContainer() {
        if(defaultContainerDisabled) {
            return null;
        }
        if(defaultContainer != null) {
            return defaultContainer;
        }
        synchronized (UndertowContainerProvider.class) {
            if(defaultContainer == null) {
                try {
                    //this is not great, as we have no way to control the lifecycle
                    //but there is not much we can do
                    //todo: what options should we use here?
                    XnioWorker worker = Xnio.getInstance().createWorker(OptionMap.create(Options.THREAD_DAEMON, true));
                    Pool<ByteBuffer> buffers = new ByteBufferSlicePool(1024, 10240);
                    defaultContainer = new ServerWebSocketContainer(DefaultClassIntrospector.INSTANCE, UndertowContainerProvider.class.getClassLoader(), worker, buffers, new CompositeThreadSetupAction(Collections.<ThreadSetupAction>emptyList()), false);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return defaultContainer;
        }
    }

    public static void addContainer(final ClassLoader classLoader, final WebSocketContainer webSocketContainer) {
        if(System.getSecurityManager() != null) {
            AccessController.checkPermission(PERMISSION);
        }
        webSocketContainers.put(classLoader, webSocketContainer);
    }

    public static void removeContainer(final ClassLoader classLoader) {
        if(System.getSecurityManager() != null) {
            AccessController.checkPermission(PERMISSION);
        }
        webSocketContainers.remove(classLoader);
    }

    public static void disableDefaultContainer() {
        if(System.getSecurityManager() != null) {
            AccessController.checkPermission(PERMISSION);
        }
        defaultContainerDisabled = true;
    }
}
