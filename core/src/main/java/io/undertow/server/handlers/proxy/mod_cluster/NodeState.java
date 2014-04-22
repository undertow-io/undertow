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
package io.undertow.server.handlers.proxy.mod_cluster;

/**
 * {@code Node} Created on Jun 11, 2012 at 11:10:06 AM
 *
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class NodeState {

    private NodeStatus status = NodeStatus.NODE_UP;
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

    /**
     * Getter for status
     *
     * @return the status
     */
    public NodeStatus getStatus() {
        return this.status;
    }

    /**
     * @return <tt>true</tt> if the node is up else <tt>false</tt>
     */
    public boolean isNodeUp() {
        return this.status == NodeStatus.NODE_UP;
    }

    /**
     * @return <tt>true</tt> if the node is down else <tt>false</tt>
     */
    public boolean isNodeDown() {
        return this.status == NodeStatus.NODE_DOWN;
    }

    /**
     * @return <tt>true</tt> if the node is paused else <tt>false</tt>
     */
    public boolean isNodePaused() {
        return this.status == NodeStatus.NODE_PAUSED;
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
     * Getter for read
     *
     * @return the read
     */
    public long getRead() {
        return this.read;
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
     * Getter for connected
     *
     * @return the connected
     */
    public int getConnected() {
        return this.connected;
    }

    /**
     * Getter for load
     *
     * @return the load
     */
    public int getLoad() {
        return this.load;
    }

    public int getOldelected() {
        return oldelected;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
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
