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

package io.undertow.server.session;

/**
 * Optional interface that can be implemented by {@link io.undertow.server.session.SessionManager}
 * implementations that provides session manager statistics.
 *
 * @author Stuart Douglas
 */
public interface SessionManagerStatistics {

    /**
     *
     * @return The number of sessions that this session manager has created
     */
    long getCreatedSessionCount();

    /**
     *
     * @return the maximum number of sessions this session manager supports
     */
    long getMaxActiveSessions();

    /**
     *
     * @return the highest number of sessions that have been active at a single time, or -1 if this statistic is not supported
     */
    default long getHighestSessionCount() {
        return -1;
    }

    /**
     *
     * @return The number of active sessions
     */
    long getActiveSessionCount();

    /**
     *
     * @return The number of expired sessions
     */
    long getExpiredSessionCount();

    /**
     *
     * @return The number of rejected sessions
     */
    long getRejectedSessions();

    /**
     *
     * @return The longest a session has been alive for in milliseconds
     */
    long getMaxSessionAliveTime();

    /**
     *
     * @return The average session lifetime in milliseconds
     */
    long getAverageSessionAliveTime();

    /**
     *
     * @return The timestamp at which the session manager started
     */
    long getStartTime();
}
