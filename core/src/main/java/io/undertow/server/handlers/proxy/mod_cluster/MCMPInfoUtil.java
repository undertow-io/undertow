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

import java.util.concurrent.TimeUnit;

/**
 * @author Emanuel Muckenhuber
 */
class MCMPInfoUtil {

    private static final String NEWLINE = "\n";

    static void printDump(final Balancer balancer, final StringBuilder builder) {
        builder.append("balancer: [").append(balancer.getId()).append("]")
                .append(" Name: ").append(balancer.getName())
                .append(" Sticky: ").append(toStringOneZero(balancer.isStickySession()))
                .append(" [").append(balancer.getStickySessionCookie()).append("]/[").append(balancer.getStickySessionPath()).append("]")
                .append(" remove: ").append(toStringOneZero(balancer.isStickySessionRemove()))
                .append(" force: ").append(toStringOneZero(balancer.isStickySessionForce()))
                .append(" Timeout: ").append(balancer.getWaitWorker())
                .append(" maxAttempts: ").append(balancer.getMaxRetries())
                .append(NEWLINE);
    }

    static void printInfo(final Node.VHostMapping host, final StringBuilder builder) {
        // node id is not unique?
        for (final String alias : host.getAliases()) {
            builder.append("Vhost: [")
                    .append(host.getNode().getId()).append(":")
                    .append(host.getId()).append(":")
                    .append(-1) // id[i] id in the table!? does not exist
                    .append("], Alias: ").append(alias)
                    .append(NEWLINE);
        }
    }

    static void printDump(final Node.VHostMapping host, final StringBuilder builder) {
        // node id is not unique?
        for (final String alias : host.getAliases()) {
            builder.append("host: ").append(host.getId())
                    .append(" [").append(alias).append("]")
                    .append(" vhost: ").append(host.getId())
                    .append(" node: ").append(host.getNode().getId())
                    .append(NEWLINE);
        }
    }

    static void printInfo(final Context context, final StringBuilder builder) {
        builder.append("Context: [")
                .append(context.getNode().getId()).append(":")
                .append(context.getVhost().getId()).append(":")
                .append(context.getId())
                .append("]")
                .append(", Context: ").append(context.getPath())
                .append(", Status: ").append(context.getStatus())
                .append(NEWLINE);
    }

    static void printDump(final Context context, final StringBuilder builder) {
        builder.append("context: ").append(context.getId())
                .append(" [").append(context.getPath()).append("]")
                .append(" vhost: ").append(context.getVhost().getId())
                .append(" node: ").append(context.getNode().getId())
                .append(" status: ").append(formatStatus(context.getStatus()))
                .append(NEWLINE);
    }

    static void printInfo(final Node node, final StringBuilder builder) {
        builder.append("Node: ")
                .append("[").append(node.getId()).append("]")
                .append(",Name: ").append(node.getJvmRoute())
                .append(",Balancer: ").append(node.getNodeConfig().getBalancer())
                .append(",LBGroup: ").append(formatString(node.getNodeConfig().getDomain()))
                .append(",Host: ").append(node.getNodeConfig().getConnectionURI().getHost())
                .append(",Port: ").append(node.getNodeConfig().getConnectionURI().getPort())
                .append(",Type: ").append(node.getNodeConfig().getConnectionURI().getScheme())
                .append(",Flushpackets: ").append(toStringOnOff(node.getNodeConfig().isFlushPackets()))
                .append(",Flushwait: ").append(node.getNodeConfig().getFlushwait())
                .append(",Ping: ").append(node.getNodeConfig().getPing())
                .append(",Smax: ").append(node.getNodeConfig().getSmax())
                .append(",Ttl: ").append(TimeUnit.MILLISECONDS.toSeconds(node.getNodeConfig().getTtl()))
                .append(",Elected: ").append(node.getElected())
                .append(",Read: ").append(node.getConnectionPool().getClientStatistics().getRead())
                .append(",Transfered: ").append(node.getConnectionPool().getClientStatistics().getWritten())
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
                .append(",LBGroup: [").append(formatString(node.getNodeConfig().getDomain())).append("]")
                .append(",Host: ").append(node.getNodeConfig().getConnectionURI().getHost())
                .append(",Port: ").append(node.getNodeConfig().getConnectionURI().getPort())
                .append(",Type: ").append(node.getNodeConfig().getConnectionURI().getScheme())
                .append(",flushpackets: ").append(toStringOneZero(node.getNodeConfig().isFlushPackets()))
                .append(",flushwait: ").append(node.getNodeConfig().getFlushwait())
                .append(",ping: ").append(node.getNodeConfig().getPing())
                .append(",smax: ").append(node.getNodeConfig().getSmax())
                .append(",ttl: ").append(TimeUnit.MILLISECONDS.toSeconds(node.getNodeConfig().getTtl()))
                .append(",timeout: ").append(node.getNodeConfig().getTimeout())
                .append(NEWLINE);
    }

    static String toStringOneZero(boolean bool) {
        return bool ? "1" : "0";
    }

    static String toStringOnOff(boolean bool) {
        return bool ? "On" : "Off";
    }

    static String formatString(String str) {
        return str == null ? "" : str;
    }

    /* matches constants defined in mod_cluster-1.3.7.Final/native/include/context.h */
    static int formatStatus(Context.Status status) {
        return status == Context.Status.ENABLED ? 1 :
               status == Context.Status.DISABLED ? 2 :
               status == Context.Status.STOPPED ? 3 :
               -1;
    }

}
