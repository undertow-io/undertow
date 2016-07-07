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
 * @author Stuart Douglas
 */
public class AggregateConnectorStatistics implements ConnectorStatistics {

    private final ConnectorStatistics[] connectorStatistics;

    public AggregateConnectorStatistics(ConnectorStatistics[] connectorStatistics) {
        this.connectorStatistics = connectorStatistics;
    }

    @Override
    public long getRequestCount() {
        long count = 0;
        for(ConnectorStatistics c : connectorStatistics) {
            count += c.getRequestCount();
        }
        return count;
    }

    @Override
    public long getBytesSent() {
        long count = 0;
        for(ConnectorStatistics c : connectorStatistics) {
            count += c.getBytesSent();
        }
        return count;
    }

    @Override
    public long getBytesReceived() {
        long count = 0;
        for(ConnectorStatistics c : connectorStatistics) {
            count += c.getBytesReceived();
        }
        return count;
    }

    @Override
    public long getErrorCount() {
        long count = 0;
        for(ConnectorStatistics c : connectorStatistics) {
            count += c.getErrorCount();
        }
        return count;
    }

    @Override
    public long getProcessingTime() {
        long count = 0;
        for(ConnectorStatistics c : connectorStatistics) {
            count += c.getProcessingTime();
        }
        return count;
    }

    @Override
    public long getMaxProcessingTime() {
        long max = 0;
        for(ConnectorStatistics c : connectorStatistics) {
            max = Math.max(c.getMaxProcessingTime(), max);
        }
        return max;
    }

    @Override
    public void reset() {
        for(ConnectorStatistics c : connectorStatistics) {
            c.reset();
        }
    }

    @Override
    public long getActiveConnections() {
        long count = 0;
        for(ConnectorStatistics c : connectorStatistics) {
            count += c.getActiveConnections();
        }
        return count;
    }

    @Override
    public long getMaxActiveConnections() {
        long count = 0;
        for(ConnectorStatistics c : connectorStatistics) {
            count += c.getMaxActiveConnections(); //not 100% accurate, but the best we can do
        }
        return count;
    }

    @Override
    public long getActiveRequests() {
        long count = 0;
        for(ConnectorStatistics c : connectorStatistics) {
            count += c.getActiveRequests();
        }
        return count;
    }

    @Override
    public long getMaxActiveRequests() {
        long count = 0;
        for(ConnectorStatistics c : connectorStatistics) {
            count += c.getMaxActiveRequests(); //not 100% accurate, but the best we can do
        }
        return count;
    }
}
