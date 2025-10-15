/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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
package io.undertow.websockets.jsr.handshake;

import io.undertow.servlet.api.InstanceFactory;
import io.undertow.util.PathTemplate;
import io.undertow.websockets.jsr.ConfiguredServerEndpoint;
import io.undertow.websockets.jsr.EncodingFactory;
import io.undertow.websockets.jsr.annotated.AnnotatedEndpointFactory;
import jakarta.websocket.Extension;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

import java.util.List;
import java.util.Set;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class PerConnectionConfiguredServerEndpoint extends ConfiguredServerEndpoint {

    private final ConfiguredServerEndpoint delegate;
    private final ServerEndpointConfig endpointConfigDelegate;

    PerConnectionConfiguredServerEndpoint(final ConfiguredServerEndpoint cseDelegate, final ServerEndpointConfig secDelegate) {
        super(null, null, null, null, null, null);
        this.delegate = cseDelegate;
        this.endpointConfigDelegate = secDelegate;
    }

    @Override
    public ServerEndpointConfig getEndpointConfiguration() {
        return endpointConfigDelegate;
    }

    @Override
    public InstanceFactory<?> getEndpointFactory() {
        return delegate.getEndpointFactory();
    }

    @Override
    public PathTemplate getPathTemplate() {
        return delegate.getPathTemplate();
    }

    @Override
    public EncodingFactory getEncodingFactory() {
        return delegate.getEncodingFactory();
    }

    @Override
    public AnnotatedEndpointFactory getAnnotatedEndpointFactory() {
        return delegate.getAnnotatedEndpointFactory();
    }

    @Override
    public List<Extension> getExtensions() {
        return delegate.getExtensions();
    }

    @Override
    public Set<Session> getOpenSessions() {
        return delegate.getOpenSessions();
    }

    @Override
    public void addOpenSession(Session session) {
        delegate.addOpenSession(session);
    }

    @Override
    public void removeOpenSession(Session session) {
        delegate.removeOpenSession(session);
    }

    @Override
    public void awaitClose(long timeout) {
        delegate.awaitClose(timeout);
    }

    @Override
    public void notifyClosed(Runnable done) {
        delegate.notifyClosed(done);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PerConnectionConfiguredServerEndpoint ? delegate.equals(((PerConnectionConfiguredServerEndpoint) obj).delegate) : delegate.equals(obj);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
