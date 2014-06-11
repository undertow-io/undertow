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
 * The load-balancing information of a node.
 *
 * @author Emanuel Muckenhuber
 */
// move this back to Node
class NodeLbStatus {

    private volatile int oldelected;
    private volatile int lbfactor;
    private volatile int lbstatus;
    private volatile int elected;

    public int getOldelected() {
        return oldelected;
    }

    public int getLbfactor() {
        return lbfactor;
    }

    public int getLbstatus() {
        return lbstatus;
    }

    public int getElected() {
        return elected;
    }

    /**
     * Update the load balancing status.
     *
     * @return
     */
    synchronized boolean update() {
        int elected = this.elected;
        int oldelected = this.oldelected;
        int lbfactor = this.lbfactor;
        if (lbfactor > 0) {
            this.lbstatus = ((elected - oldelected) * 1000) / lbfactor;
        }
        this.oldelected = elected;
        return elected != oldelected; // TODO ping if they are equal
    }

    synchronized void elected() {
        elected++;
    }

    synchronized void updateLoad(int load) {
        lbfactor = load;
    }

    /**
     * Get the load balancing status.
     *
     * @return
     */
    synchronized int getLbStatus() {
        if (lbfactor > 0) {
            return (((elected - oldelected) * 1000) / lbfactor) + lbstatus;
        } else {
            return -1;
        }
    }

}
