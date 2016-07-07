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

package io.undertow.server;

/**
 * Connector level statistics
 *
 *
 * @author Stuart Douglas
 */
public interface ConnectorStatistics {

    /**
     *
     * @return The number of requests processed by this connector
     */
    long getRequestCount();

    /**
     *
     * @return The number of bytes sent on this connector
     */
    long getBytesSent();

    /**
     *
     * @return The number of bytes that have been received by this connector
     */
    long getBytesReceived();

    /**
     *
     * @return The number of requests that triggered an error (i.e. 500) response.
     */
    long getErrorCount();

    /**
     *
     * @return The total amount of time spent processing all requests on this connector
     *         (nanoseconds)
     */
    long getProcessingTime();

    /**
     *
     * @return The time taken by the slowest request
     *         (nanoseconds)
     */
    long getMaxProcessingTime();

    /**
     * Resets all values to zero
     */
    void reset();

    /**
     *
     * @return The current number of active connections
     */
    long getActiveConnections();

    /**
     *
     * @return The maximum number of active connections that have every been active on this connector
     */
    long getMaxActiveConnections();

    /**
     *
     * @return The current number of active requests
     */
    long getActiveRequests();

    /**
     *
     * @return The maximum number of active requests
     */
    long getMaxActiveRequests();

}
