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

import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.SessionManagerFactory;

/**
 * Session manager factory that creates an in-memory session manager
 * @author Paul Ferraro
 */
public class InMemorySessionManagerFactory implements SessionManagerFactory {

    private final int maxSessions;
    private final boolean expireOldestUnusedSessionOnMax;

    public InMemorySessionManagerFactory() {
        this(-1, false);
    }

    public InMemorySessionManagerFactory(int maxSessions) {
        this(maxSessions, false);
    }

    public InMemorySessionManagerFactory(int maxSessions, boolean expireOldestUnusedSessionOnMax) {
        this.maxSessions = maxSessions;
        this.expireOldestUnusedSessionOnMax = expireOldestUnusedSessionOnMax;
    }

    @Override
    public SessionManager createSessionManager(Deployment deployment) {
        return new InMemorySessionManager(deployment.getDeploymentInfo().getSessionIdGenerator(), deployment.getDeploymentInfo().getDeploymentName(), maxSessions, expireOldestUnusedSessionOnMax, deployment.getDeploymentInfo().getMetricsCollector() != null);
    }
}
