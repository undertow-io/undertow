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
package io.undertow.proxy.container;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;
import io.undertow.server.handlers.proxy.ProxyConnectionPool;

/**
 * {@code Node} Created on Jun 11, 2012 at 11:10:06 AM
 *
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class Node implements Serializable {

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

    /**
     *
     */
    private static final long serialVersionUID = 8107364666635267031L;
    /**
     *
     */
    private static final AtomicInteger counter = new AtomicInteger(0);
    private long id;
    private NodeStatus status = NodeStatus.NODE_UP;
    private String balancer = "mycluster";
    private String jvmRoute;
    private String domain = "";
    private String hostname = "localhost";
    private int port = 8009;
    private boolean flushPackets = false;

    /**
     * Protocol using by the connector (AJP/http/https).
     */
    private String type = "AJP";
    /**
     * Tell how to flush the packets. On: Send immediately, Auto wait for flushwait time before sending, Off don't flush.
     * Default: "Off"
     */
    private boolean flushpackets = false;
    /**
     * Time to wait before flushing. Value in milliseconds. Default: 10
     */
    private int flushwait = 10;
    /**
     * Time to wait for a pong answer to a ping. 0 means we don't try to ping before sending. Value in seconds Default: 10
     * (10_000 in milliseconds)
     */
    private int ping = 10000;
    /**
     * soft max inactive connection over that limit after ttl are closed. Default depends on the mpm configuration (See below
     * for more information)
     */
    private int smax;
    /**
     * max time in seconds to life for connection above smax. Default 60 seconds (60_000 in milliseconds).
     */
    private int ttl = 60000;
    /**
     * Max time the proxy will wait for the backend connection. Default 0 no timeout value in seconds.
     */
    private int timeout = 0;
    /**
     * Number of time the worker was chosen by the balancer logic
     */
    private int elected;
    private int oldelected;
    /**
     * Number of bytes read from the back-end
     */
    private long read;
    /**
     * Number of bytes send to the back-end
     */
    private long transfered;
    /**
     * Number of opened connections
     */
    private int connected;
    /**
     * Load factor received via the STATUS messages
     */
    private int load;
    /*
     * like in the Host of LoadBalancingProxyClient, it is needed by the health check logic.
     */
    private ProxyConnectionPool connectionPool;

    /**
     * Create a new instance of {@code Node}
     */
    public Node() {
        this.setId(counter.getAndIncrement());
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
     * Getter for status
     *
     * @return the status
     */
    public NodeStatus getStatus() {
        return this.status;
    }

    /**
     * Setter for the status
     *
     * @param status the status to set
     */
    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    /**
     * @return <tt>true</tt> if the node is up else <tt>false</tt>
     */
    public boolean isNodeUp() {
        return this.status == NodeStatus.NODE_UP;
    }

    /**
     * Set this node status to {@link NodeStatus.NODE_UP}
     */
    public void setNodeUp() {
        setStatus(NodeStatus.NODE_UP);
    }

    /**
     * @return <tt>true</tt> if the node is down else <tt>false</tt>
     */
    public boolean isNodeDown() {
        return this.status == NodeStatus.NODE_DOWN;
    }

    /**
     * Set this node status to {@link NodeStatus.NODE_DOWN}
     */
    public void setNodeDown() {
        setStatus(NodeStatus.NODE_DOWN);
    }

    /**
     * @return <tt>true</tt> if the node is paused else <tt>false</tt>
     */
    public boolean isNodePaused() {
        return this.status == NodeStatus.NODE_PAUSED;
    }

    /**
     * Set this node status to {@link NodeStatus.NODE_PAUSED}
     */
    public void setNodePaused() {
        setStatus(NodeStatus.NODE_PAUSED);
    }

    /**
     * Getter for port
     *
     * @return the port
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Setter for the port
     *
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Getter for jvmRoute
     *
     * @return the jvmRoute
     */
    public String getJvmRoute() {
        return this.jvmRoute;
    }

    /**
     * Setter for the jvmRoute
     *
     * @param jvmRoute the jvmRoute to set
     */
    public void setJvmRoute(String jvmRoute) {
        this.jvmRoute = jvmRoute;
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
     * Setter for the domain
     *
     * @param domain the domain to set
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /**
     * Getter for hostname
     *
     * @return the hostname
     */
    public String getHostname() {
        return this.hostname;
    }

    /**
     * Setter for the hostname
     *
     * @param hostname the hostname to set
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * Getter for type
     *
     * @return the type
     */
    public String getType() {
        return this.type;
    }

    /**
     * Setter for the type
     *
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Getter for flushpackets
     *
     * @return the flushpackets
     */
    public boolean isFlushpackets() {
        return this.flushpackets;
    }

    /**
     * Setter for the flushpackets
     *
     * @param flushpackets the flushpackets to set
     */
    public void setFlushpackets(boolean flushpackets) {
        this.flushpackets = flushpackets;
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
     * Setter for the flushwait
     *
     * @param flushwait the flushwait to set
     */
    public void setFlushwait(int flushwait) {
        this.flushwait = flushwait;
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
     * Setter for the ping
     *
     * @param ping the ping to set
     */
    public void setPing(int ping) {
        this.ping = ping;
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
     * Setter for the smax
     *
     * @param smax the smax to set
     */
    public void setSmax(int smax) {
        this.smax = smax;
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
     * Setter for the ttl
     *
     * @param ttl the ttl to set
     */
    public void setTtl(int ttl) {
        this.ttl = ttl;
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
     * Setter for the timeout
     *
     * @param timeout the timeout to set
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * Getter for balancer
     *
     * @return the balancer
     */
    public String getBalancer() {
        return this.balancer;
    }

    /**
     * Setter for the balancer
     *
     * @param balancer the balancer to set
     */
    public void setBalancer(String balancer) {
        this.balancer = balancer;
    }

    /**
     * Getter for elected
     *
     * @return the elected
     */
    public int getElected() {
        return this.elected;
    }

    /**
     * Setter for the elected
     *
     * @param elected the elected to set
     */
    public void setElected(int elected) {
        this.elected = elected;
    }

    /**
     * Getter for read
     *
     * @return the read
     */
    public long getRead() {
        return this.read;
    }

    /**
     * Setter for the read
     *
     * @param read the read to set
     */
    public void setRead(long read) {
        this.read = read;
    }

    /**
     * Getter for transfered
     *
     * @return the transfered
     */
    public long getTransfered() {
        return this.transfered;
    }

    /**
     * Setter for the transfered
     *
     * @param transfered the transfered to set
     */
    public void setTransfered(long transfered) {
        this.transfered = transfered;
    }

    /**
     * Getter for connected
     *
     * @return the connected
     */
    public int getConnected() {
        return this.connected;
    }

    /**
     * Setter for the connected
     *
     * @param connected the connected to set
     */
    public void setConnected(int connected) {
        this.connected = connected;
    }

    /**
     * Getter for load
     *
     * @return the load
     */
    public int getLoad() {
        return this.load;
    }

    /**
     * Setter for the load
     *
     * @param load the load to set
     */
    public void setLoad(int load) {
        this.load = load;
    }

    /**
     * @param pos the position of the node in the list
     * @return the global information about the node
     */
    public String getInfos(int pos) {
        return new StringBuilder("Node: [" + pos + "],Name: ").append(this.jvmRoute).append("Balancer: ").append(this.balancer)
                .append(",LBGroup: ").append(this.domain).append(",Host: ").append(getHostname()).append(",Port: ")
                .append(getPort()).append(",Type: ").append(getType()).append(",Flushpackets: ")
                .append((isFlushpackets() ? "On" : "Off")).append(",Flushwait: ").append(getFlushwait()).append(",Ping: ")
                .append(getPing()).append(",Smax: ").append(getSmax()).append(",Ttl: ").append(getTtl()).append(",Elected: ")
                .append(getElected()).append(",Read: ").append(getRead()).append(",Transfered: ").append(getTransfered())
                .append(",Connected: ").append(getConnected()).append(",Load: ").append(getLoad()).append("\n").toString();
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        // TODO complete node name
        StringBuilder sb = new StringBuilder("Node: [x:y]").append("], Balancer: ").append(this.balancer)
                .append(", JVMRoute: ").append(this.jvmRoute).append(", Domain: [").append(this.domain).append("], Host: ")
                .append(this.hostname).append(", Port: ").append(this.port).append(", Type: ").append(this.type)
                .append(", flush-packets: ").append(this.flushpackets ? 1 : 0).append(", flush-wait: ").append(this.flushwait)
                .append(", Ping: ").append(this.ping).append(", smax: ").append(this.smax).append(", TTL: ").append(this.ttl)
                .append(", Timeout: ").append(this.timeout);

        return sb.toString();
    }

    public int getOldelected() {
        return oldelected;
    }

    public void setOldelected(int oldelected) {
        this.oldelected = oldelected;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean isFlushPackets() {
        return flushPackets;
    }

    public void setFlushPackets(boolean flushPackets) {
        this.flushPackets = flushPackets;
    }

    public ProxyConnectionPool getConnectionPool() {
        return connectionPool;
    }

    public void setConnectionPool(ProxyConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }
}
