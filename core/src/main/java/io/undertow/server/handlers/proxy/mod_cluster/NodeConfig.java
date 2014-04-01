/**
 * JBoss, Home of Professional Open Source. Copyright 2012, Red Hat, Inc., and individual contributors as indicated by the @author
 * tags. See the copyright.txt file in the distribution for a full listing of individual contributors. This is free software;
 * you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option) any later version. This software is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details. You should have received a copy of the
 * GNU Lesser General Public License along with this software; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package io.undertow.server.handlers.proxy.mod_cluster;

/**
 * {@code Node} Created on Jun 11, 2012 at 11:10:06 AM
 *
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class NodeConfig {


    private final String hostname;
    private final int port;
    private final String type;


    private final long id;
    private final String balancer;
    private final String domain;

    private final String jvmRoute;

    /**
     * Tell how to flush the packets. On: Send immediately, Auto wait for flushwait time before sending, Off don't flush.
     * Default: "Off"
     */
    private boolean flushPackets;

    /**
     * Time to wait before flushing. Value in milliseconds. Default: 10
     */
    private final int flushwait;
    /**
     * Time to wait for a pong answer to a ping. 0 means we don't try to ping before sending. Value in seconds Default: 10
     * (10_000 in milliseconds)
     */
    private final int ping;
    /**
     * soft max inactive connection over that limit after ttl are closed. Default depends on the mpm configuration (See below
     * for more information)
     */
    private final int smax;
    /**
     * max time in seconds to life for connection above smax. Default 60 seconds (60_000 in milliseconds).
     */
    private final int ttl;
    /**
     * Max time the proxy will wait for the backend connection. Default 0 no timeout value in seconds.
     */
    private final int timeout;

    NodeConfig(NodeBuilder b) {
        hostname = b.hostname;
        port = b.port;
        type = b.type;
        id = b.id;
        balancer = b.balancer;
        domain = b.domain;
        jvmRoute = b.jvmRoute;
        flushPackets = b.flushPackets;
        flushwait = b.flushwait;
        ping = b.ping;
        smax = b.smax;
        ttl = b.ttl;
        timeout = b.timeout;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public String getType() {
        return type;
    }

    /**
     * Getter for id
     *
     * @return the id
     */
    public long getId() {
        return this.id;
    }

    /**
     * Getter for domain
     *
     * @return the domain
     */
    public String getDomain() {
        return this.domain;
    }

    /**
     * Getter for flushwait
     *
     * @return the flushwait
     */
    public int getFlushwait() {
        return this.flushwait;
    }

    /**
     * Getter for ping
     *
     * @return the ping
     */
    public int getPing() {
        return this.ping;
    }

    /**
     * Getter for smax
     *
     * @return the smax
     */
    public int getSmax() {
        return this.smax;
    }

    /**
     * Getter for ttl
     *
     * @return the ttl
     */
    public int getTtl() {
        return this.ttl;
    }

    /**
     * Getter for timeout
     *
     * @return the timeout
     */
    public int getTimeout() {
        return this.timeout;
    }

    /**
     * Getter for balancer
     *
     * @return the balancer
     */
    public String getBalancer() {
        return this.balancer;
    }

    public boolean isFlushPackets() {
        return flushPackets;
    }

    public void setFlushPackets(boolean flushPackets) {
        this.flushPackets = flushPackets;
    }

    public String getJvmRoute() {
        return jvmRoute;
    }

    public static NodeBuilder builder() {
        return new NodeBuilder();
    }

    public static class NodeBuilder {

        private String hostname;
        private int port;
        private String type;
        private long id;
        private String balancer = "mycluster";
        private String domain = "";

        private String jvmRoute;
        private boolean flushPackets = false;
        private int flushwait = 10;
        private int ping = 10000;
        private int smax;
        private int ttl = 60000;
        private int timeout = 0;
        private int elected;
        private NodeStatus status = NodeStatus.NODE_UP;
        private int oldelected;
        private long read;
        private long transfered;
        private int connected;
        private int load;

        NodeBuilder() {
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public void setType(String type) {
            this.type = type;
        }

        public void setId(long id) {
            this.id = id;
        }

        public void setStatus(NodeStatus status) {
            this.status = status;
        }

        public void setBalancer(String balancer) {
            this.balancer = balancer;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public void setJvmRoute(String jvmRoute) {
            this.jvmRoute = jvmRoute;
        }

        public void setFlushPackets(boolean flushPackets) {
            this.flushPackets = flushPackets;
        }

        public void setFlushwait(int flushwait) {
            this.flushwait = flushwait;
        }

        public void setPing(int ping) {
            this.ping = ping;
        }

        public void setSmax(int smax) {
            this.smax = smax;
        }

        public void setTtl(int ttl) {
            this.ttl = ttl;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public void setElected(int elected) {
            this.elected = elected;
        }

        public void setOldelected(int oldelected) {
            this.oldelected = oldelected;
        }

        public void setRead(long read) {
            this.read = read;
        }

        public void setTransfered(long transfered) {
            this.transfered = transfered;
        }

        public void setConnected(int connected) {
            this.connected = connected;
        }

        public void setLoad(int load) {
            this.load = load;
        }

        public NodeConfig build() {
            return new NodeConfig(this);
        }
    }


    /**
     * {@code NodeStatus}
     */
    public enum NodeStatus {
        /**
         * The node is up
         */
        NODE_UP,
        /**
         * The node is down
         */
        NODE_DOWN,
        /**
         * The node is paused
         */
        NODE_PAUSED;
    }
}
