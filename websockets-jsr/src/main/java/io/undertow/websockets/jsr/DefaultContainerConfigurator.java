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

import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;

import java.util.ArrayList;
import java.util.List;

import jakarta.websocket.Extension;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Server default container configurator.
 * <p>
 * This API is stupid, because it has no way to attach deployment specific context.
 *
 * @author Stuart Douglas
 */
public class DefaultContainerConfigurator extends ServerEndpointConfig.Configurator {

    public static final DefaultContainerConfigurator INSTANCE = new DefaultContainerConfigurator();

    /**
     * thread local hacks to work around a horrible horrible broken API
     */
    private static final ThreadLocal<InstanceFactory<?>> currentInstanceFactory = new ThreadLocal<>();
    private static final ThreadLocal<InstanceHandle<?>> currentInstanceHandle = new ThreadLocal<>();

    @Override
    public String getNegotiatedSubprotocol(final List<String> supported, final List<String> requested) {
        for(String proto : requested) {
            if(supported.contains(proto)) {
                return proto;
            }
        }
        return "";
    }

    @Override
    public List<Extension> getNegotiatedExtensions(final List<Extension> installed, final List<Extension> requested) {
        final List<Extension> ret = new ArrayList<>();
        for (Extension req : requested) {
            for (Extension extension : installed) {
                if (extension.getName().equals(req.getName())) {
                    ret.add(req);
                    break;
                }
            }
        }
        return ret;
    }

    @Override
    public boolean checkOrigin(final String originHeaderValue) {
        //we can't actually do anything here, because have have absolutely no context.
        return true;
    }

    @Override
    public void modifyHandshake(final ServerEndpointConfig sec, final HandshakeRequest request, final HandshakeResponse response) {
    }

    @Override
    public <T> T getEndpointInstance(final Class<T> endpointClass) throws InstantiationException {
        InstanceFactory<?> factory = currentInstanceFactory.get();
        if(factory != null) {
            InstanceHandle<?> instance = factory.createInstance();
            currentInstanceHandle.set(instance);
            return (T) instance.getInstance();
        }
        try {
            return endpointClass.newInstance();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static void setCurrentInstanceFactory(InstanceFactory<?> factory) {
         currentInstanceFactory.set(factory);
    }

    static InstanceHandle<?> clearCurrentInstanceFactory() {
        currentInstanceFactory.remove();
        InstanceHandle<?> handle = currentInstanceHandle.get();
        currentInstanceHandle.remove();
        return handle;
    }
}
