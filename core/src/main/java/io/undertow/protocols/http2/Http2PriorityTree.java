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

package io.undertow.protocols.http2;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * A structure that represents HTTP2 priority information.
 *
 * Note that this structure is not thread safe, it is intended to be protected by an external lock
 *
 * @author Stuart Douglas
 */
public class Http2PriorityTree {

    private final Http2PriorityNode rootNode;
    private final Map<Integer, Http2PriorityNode> nodesByID = new HashMap<>();

    /**
     * fixed length queue of completed streams that have no dependents, they are kept around for a short time then expired.
     *
     */
    private int[] evictionQueue;

    private int evictionQueuePosition;

    /**
     * The maximum number of streams that we store priority information for
     */
    public Http2PriorityTree() {
        this.rootNode = new Http2PriorityNode(0, 0);
        nodesByID.put(0, this.rootNode);
        this.evictionQueue = new int[10]; //todo: make this size customisable
    }

    /**
     * Resisters a stream, with its dependency and dependent information
     * @param streamId The stream id
     * @param dependency The stream this stream depends on, if no stream is specified this should be zero
     * @param weighting The weighting. If no weighting is specified this should be 16
     */
    public void registerStream(int streamId, int dependency, int weighting, boolean exclusive) {
        final Http2PriorityNode node = new Http2PriorityNode(streamId, weighting);
        if(exclusive) {
            Http2PriorityNode existing = nodesByID.get(dependency);
            if(existing != null) {
                existing.exclusive(node);
            }
        } else {
            Http2PriorityNode existing = nodesByID.get(dependency);
            if(existing != null) {
                existing.addDependent(node);
            }
        }
        nodesByID.put(streamId, node);
    }

    /**
     * Method that is invoked when a stream has been removed
     *
     * @param streamId id of the stream removed
     */
    public void streamRemoved(int streamId) {
        Http2PriorityNode node = nodesByID.get(streamId);
        if(node == null) {
            return;
        }
        if(!node.hasDependents()) {
            //add to eviction queue
            int toEvict = evictionQueue[evictionQueuePosition];
            evictionQueue[evictionQueuePosition++] = streamId;
            Http2PriorityNode nodeToEvict = nodesByID.get(toEvict);
            //we don't remove the node if it has since got dependents since it was put into the queue
            //as this is the whole reason we maintain the queue in the first place
            if(nodeToEvict != null && !nodeToEvict.hasDependents()) {
                nodesByID.remove(toEvict);
            }
        }

    }

    /**
     * Creates a priority queue
     * @return
     */
    public Comparator<Integer> comparator() {
        return new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                Http2PriorityNode n1 = nodesByID.get(o1);
                Http2PriorityNode n2 = nodesByID.get(o2);
                if(n1 == null && n2 == null) {
                    return 0;
                }
                if(n1 == null) {
                    return -1;
                }
                if(n2 == null) {
                    return 1;
                }
                //do the comparison
                //this is kinda crap, but I can't really think of any better way to handle this

                double d1 = createWeightingProportion(n1);
                double d2 = createWeightingProportion(n2);
                return Double.compare(d1, d2);
            }
        };
    }
    private double createWeightingProportion(Http2PriorityNode n1) {
        double ret = 1;
        Http2PriorityNode node = n1;
        while (node != null) {
            Http2PriorityNode parent = node.parent;
            if(parent != null) {
                ret *= (node.weighting/(double)parent.totalWeights);
            }
            node = parent;
        }
        return ret;
    }

    public void priorityFrame(int streamId, int streamDependency, int weight, boolean exlusive) {
        Http2PriorityNode existing = nodesByID.get(streamId);
        if(existing == null) {
            return;
        }
        int dif = weight - existing.weighting;
        existing.parent.totalWeights += dif;
        existing.weighting = weight;
        if(exlusive) {
            Http2PriorityNode newParent = nodesByID.get(streamDependency);
            if(newParent != null) {
                existing.parent.removeDependent(existing);
                newParent.exclusive(existing);
            }
        } else if(existing.parent.streamId != streamDependency) {
            Http2PriorityNode newParent = nodesByID.get(streamDependency);
            if(newParent != null) {
                newParent.addDependent(existing);
            }
        }
    }


    private static class Http2PriorityNode {

        private Http2PriorityNode parent;

        /**
         * This stream id of this node
         */
        private final int streamId;

        /**
         * The stream weighting
         */
        int weighting;

        /**
         * The sum of all dependencies weights
         */
        int totalWeights;

        /**
         * streams that depend on this stream, in weighted order. May contains null at the end of the list
         */
        private Http2PriorityNode[] dependents = null;

        Http2PriorityNode(int streamId, int weighting) {
            this.streamId = streamId;
            this.weighting = weighting;
        }


        void removeDependent(Http2PriorityNode node) {
            if(dependents == null) {
                return;
            }
            totalWeights -= node.weighting;
            boolean found = false;
            int i;
            for(i = 0; i < dependents.length - 1; ++i ) {
                if(dependents[i] == node) {
                    found = true;
                }
                if(found) {
                    dependents[i] = dependents[i + i];
                }
                if(dependents[i] == null) {
                    break;
                }
            }
            if(found) {
                dependents[i + 1] = null;
            }
        }

        boolean hasDependents() {
            return dependents != null && dependents[0] != null;
        }


        public void addDependent(Http2PriorityNode node) {
            if(dependents == null) {
                dependents = new Http2PriorityNode[5];
            }
            int i = 0;
            boolean found = false;
            for(; i < dependents.length; ++i ) {
                if(dependents[i] == null) {
                    found = true;
                    break;
                }
            }
            if(!found) {
                Http2PriorityNode[] old = dependents;
                dependents = new Http2PriorityNode[dependents.length + 5];
                System.arraycopy(old, 0, dependents, 0, old.length);
                ++i;
            }
            dependents[i] = node;
            node.parent = this;
            totalWeights += node.weighting;
        }

        public void exclusive(Http2PriorityNode node) {
            if(dependents == null) {
                dependents = new Http2PriorityNode[5];
            }

            for(Http2PriorityNode i : dependents) {
                if(i != null) {
                    node.addDependent(i);
                }
            }
            dependents[0] = node;
            for(int i = 1; i < dependents.length; ++ i) {
                dependents[i] = null;
            }
            totalWeights = node.weighting;
        }
    }
}
