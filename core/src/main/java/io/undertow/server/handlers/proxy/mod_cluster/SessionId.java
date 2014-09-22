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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.server.handlers.proxy.mod_cluster;

import java.io.Serializable;

/**
 * {@code SessionId}
 *
 * @author Jean-Frederic Clere
 */
public class SessionId implements Serializable {

    /**
     * SessionId
     */
    private final String sessionId;

    /**
     * JVMRoute
     */
    private final String jmvRoute;

    /**
      * Date last updated.
      */
    private volatile long updateTime;

    public SessionId(String sessionId, String jmvRoute) {
        this.sessionId = sessionId;
        this.jmvRoute = jmvRoute;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getJmvRoute() {
        return jmvRoute;
    }

    public long getUpdateTime() {
        return updateTime;
    }

}
