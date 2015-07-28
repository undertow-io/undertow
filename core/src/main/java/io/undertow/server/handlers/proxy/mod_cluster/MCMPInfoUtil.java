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

/**
 * @author Emanuel Muckenhuber
 */
class MCMPInfoUtil {

    private static final String NEWLINE = "\n";

    static void printDump(final Balancer balancer, final StringBuilder builder) {
        builder.append("balancer: [").append(balancer.getId()).append("],")
                .append(" Name: ").append(balancer.getName())
                .append(" Sticky: ").append(formatBoolean(balancer.isStickySession()))
                .append(" [").append(balancer.getStickySessionCookie()).append("]/[").append(balancer.getStickySessionPath()).append("]")
                .append(" remove: ").append(formatBoolean(balancer.isStickySessionRemove()))
                .append("force: ").append(formatBoolean(balancer.isStickySessionForce()))
                .append("Timeout: ").append(balancer.getWaitWorker())
                .append("maxAttempts: ").append(balancer.getMaxattempts())
                .append(NEWLINE);
    }

    static void printInfo(final Node.VHostMapping host, final StringBuilder builder) {
        builder.append("Vhost: [")
                // .append(host.getNode().getBalancer().getId()).append(":") // apparently no balancer
                .append(host.getNode().getId()).append(":")
                .append(host.getId()).append(":")
                .append(-1) // id[i] id in the table!? does not exist
                .append("], Alias: ").append(host.getAliases())
                .append(NEWLINE);
    }

    static void printDump(final Node.VHostMapping host, final StringBuilder builder) {
        final int hostID = host.getId();
        final int nodeID  = host.getNode().getId();
        for (final String alias : host.getAliases()) {
            builder.append("host: ").append(hostID).append(" [")
                    .append(alias).append("] vhost: ").append(host.getId())
                    .append(" node: ").append(nodeID)
                    .append(NEWLINE);
        }
    }

    static void printInfo(final Context context, final StringBuilder builder) {
        builder.append("Context: ").append("[")
                .append(context.getNode().getId()).append(":")
                .append(context.getVhost().getId()).append(":")
                .append(context.getId()).append("]")
                .append("],Context: ").append(context.getPath())
                .append(",Status: ").append(context.getStatus())
                .append(NEWLINE);
    }

    static void printDump(final Context context, final StringBuilder builder) {
        builder.append("context: ").append("[").append(context.getId()).append("]")
                .append(" [").append(context.getPath())
                .append("] vhost: ").append(context.getVhost().getId())
                .append("node: ").append(context.getNode().getId())
                .append("status: ").append(context.getStatus())
                .append(NEWLINE);
    }

    static void printInfo(final Node node, final StringBuilder builder) {
        builder.append("Node: [")
                // .append(node.getBalancer().getId()).append(":")
                .append(node.getId()).append("]")
                .append(",Name: ").append(node.getJvmRoute())
                .append(",Balancer: ").append(node.getNodeConfig().getBalancer())
                .append(",JVMRoute: ").append(node.getJvmRoute())
                .append(",LBGroup: ").append(node.getNodeConfig().getDomain())
                .append(",Host: ").append(node.getNodeConfig().getConnectionURI().getHost())
                .append(",Port: ").append(node.getNodeConfig().getConnectionURI().getPort())
                .append(",Type: ").append(node.getNodeConfig().getConnectionURI().getScheme())
                .append(",flushpackets: ").append(formatBoolean(node.getNodeConfig().isFlushPackets()))
                .append(",flushwait: ").append(node.getNodeConfig().getFlushwait())
                .append(",ping: ").append(node.getNodeConfig().getPing())
                .append(",smax: ").append(node.getNodeConfig().getSmax())
                .append(",ttl: ").append(node.getNodeConfig().getTtl())
                .append(",timeout: ").append(node.getNodeConfig().getTimeout())
                //
                .append(",Elected: ").append(node.getElected())
                .append(",Read: ").append(node.getConnectionPool().getClientStatistics().getRead())
                .append(",Transferred: ").append(node.getConnectionPool().getClientStatistics().getWritten())
                .append(",Connected: ").append(node.getConnectionPool().getOpenConnections())
                .append(",Load: ").append(node.getLoad())

                .append(NEWLINE);
    }

    static void printDump(final Node node, final StringBuilder builder) {
        builder.append("node: [")
                .append(node.getBalancer().getId()).append(":")
                .append(node.getId()).append("]")
                .append(",Balancer: ").append(node.getNodeConfig().getBalancer())
                .append(",JVMRoute: ").append(node.getJvmRoute())
                .append(",LBGroup: ").append(node.getNodeConfig().getDomain())
                .append(",Host: ").append(node.getNodeConfig().getConnectionURI().getHost())
                .append(",Port: ").append(node.getNodeConfig().getConnectionURI().getPort())
                .append(",Type: ").append(node.getNodeConfig().getConnectionURI().getScheme())
                .append(",flushpackets: ").append(formatBoolean(node.getNodeConfig().isFlushPackets()))
                .append(",flushwait: ").append(node.getNodeConfig().getFlushwait())
                .append(",ping: ").append(node.getNodeConfig().getPing())
                .append(",smax: ").append(node.getNodeConfig().getSmax())
                .append(",ttl: ").append(node.getNodeConfig().getTtl())
                .append(",timeout: ").append(node.getNodeConfig().getTimeout())
                .append(NEWLINE);
    }

    static String formatBoolean(boolean value) {
        return value ? "1" : "0";
    }

}
