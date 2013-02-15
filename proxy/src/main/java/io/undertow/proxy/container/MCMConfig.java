/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.undertow.proxy.container;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration of the cluster received via the MCM elements.
 * And provider of the node for the ProxyHander.
 *
 * @author Jean-Frederic Clere
 *
 */
public class MCMConfig extends NodeService {

    private List<SessionId> sessionids = new ArrayList<SessionId>();
    private final int lbstatus_recalc_time = 5;

    protected Thread thread = null;

    /**
     * Create a new instance of {@code NodeService}
     */
    public MCMConfig()  {
        super();
    }

    @Override
    public void init() throws Exception {
        // Create the thread to keep the configure up to date.
        if (thread == null) {
            thread = new Thread(new MCMConfigBackgroundProcessor(), "MCMConfigBackgroundProcessor");
            thread.setDaemon(true);
            thread.start();

        }
        setInitialized(true);
    }

     protected class MCMConfigBackgroundProcessor implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(lbstatus_recalc_time *1000);
                } catch (InterruptedException e) {
                    continue;
                }
                // check if the value have changed otherwise the node may be broken.
                checkHealthNode();
            }
        }

     }

    public void insertupdate(Node node) {
        if (getNodes().isEmpty()) {
            node.setId(1);
            // TODO add the connection manager.
            getNodes().add(node);
        } else {
            int i = 1;
            Node replace = null;
            for (Node nod : getNodes()) {
                if (nod.getJvmRoute().equals(node.getJvmRoute())) {
                    // replace it.
                    // TODO that is more tricky see mod_cluster C code.
                    replace = nod;
                    break;
                } else {
                    i++;
                }
            }
            if (replace != null) {
                node.setId(replace.getId());
                getNodes().remove(replace);
                getNodes().add(node);
            } else {
                node.setId(i);
                // TODO add the connection manager.
                getNodes().add(node);
            }
        }
    }

    public void insertupdate(Balancer balancer) {
        if (getBalancers().isEmpty()) {
            getBalancers().add(balancer);
        } else {
            for (Balancer bal : getBalancers()) {
                if (bal.getName().equals(balancer.getName())) {
                    // replace it.
                    // TODO that is more tricky see mod_cluster C code.
                    getBalancers().remove(bal);
                    getBalancers().add(balancer);
                    break; // Done
                }
            }
        }
    }



    public long getNodeId(String jvmRoute) {
        for (Node nod : getNodes()) {
            if (nod.getJvmRoute().equals(jvmRoute)) {
                return nod.getId();
            }
        }
        return -1;
    }



    public long insertupdate(VHost host) {
        int i = 1;
        if (getHosts().isEmpty()) {
            host.setId(i);
            getHosts().add(host);
            return 1;
        } else {
            for (VHost hos : getHosts()) {
                if (hos.getJVMRoute().equals(host.getJVMRoute())
                        && isSame(host.getAliases(), hos.getAliases())) {
                    return hos.getId();
                }
                i++;
            }
        }
        host.setId(i);
        getHosts().add(host);
        return i;
    }

    private boolean isSame(String[] aliases, String[] aliases2) {
        if (aliases.length != aliases2.length)
            return false;
        for (String host : aliases)
            if (isNotIn(host, aliases))
                return false;
        return true;
    }

    private boolean isNotIn(String host, String[] aliases) {
        for (String hos : aliases)
            if (host.equals(hos))
                return false;
        return true;
    }

    public void insertupdate(Context context) {
        if (getContexts().isEmpty()) {
            getContexts().add(context);
            return;
        } else {
            for (Context con : getContexts()) {
                if (context.getJVMRoute().equals(con.getJVMRoute())
                        && context.getHostid() == con.getHostid()
                        && context.getPath().equals(con.getPath())) {
                    // update the status.
                    con.setStatus(context.getStatus());
                    return;
                }
            }
            getContexts().add(context);
        }
    }

    public void checkHealthNode() {
        for (Node nod : getNodes()) {
            if (nod.getElected() == nod.getOldelected()) {
                // nothing change bad
                // TODO and the CPING/CPONG
            } else {
                nod.setOldelected(nod.getElected());
            }
        }
    }

    /*
     * remove the context and the corresponding host if that is last context of the host.
     */

    public void remove(Context context, VHost host) {
        for (Context con : getContexts()) {
            if (context.getJVMRoute().equals(con.getJVMRoute())
                    && isSame(getHostById(con.getHostid()).getAliases(), host.getAliases())
                    && context.getPath().equals(con.getPath())) {
                getContexts().remove(con);
                removeEmptyHost(con.getHostid());
                return;
            }

        }
    }

    private void removeEmptyHost(long hostid) {
        boolean remove = true;
        for (Context con : getContexts()) {
            if (con.getHostid() == hostid) {
                remove = false;
                break;
            }
        }
        if (remove)
            getHosts().remove(getHostById(hostid));
    }

    private VHost getHostById(long hostid) {
        for (VHost hos : getHosts()) {
            if (hos.getId() == hostid)
                return hos;
        }
        return null;
    }

    /*
     * Remove the node, host, context corresponding to jvmRoute.
     */
    public void removeNode(String jvmRoute) {
        List<Context> remcons = new ArrayList<Context>();
        for (Context con : getContexts()) {
            if (con.getJVMRoute().equals(jvmRoute))
                remcons.add(con);
        }
        for (Context con : remcons )
            getContexts().remove(con);

        List<VHost> remhosts = new ArrayList<VHost>();
        for (VHost hos : getHosts()) {
            if (hos.getJVMRoute().equals(jvmRoute))
                remhosts.add(hos);
        }
        for (VHost hos : remhosts)
            getHosts().remove(hos);

        List<Node> remnodes = new ArrayList<Node>();
        for (Node nod : getNodes()) {
            if (nod.getJvmRoute().equals(jvmRoute))
                remnodes.add(nod);
        }
        for (Node nod : remnodes)
            getNodes().remove(nod);
    }

    public List<SessionId> getSessionids() {
        return sessionids;
    }

    public void setSessionids(List<SessionId> sessionids) {
        this.sessionids = sessionids;
    }

    /*
     * Count the number of sessionid corresponding to the node.
     */
    public String getJVMRouteSessionCount(String jvmRoute) {
        int i = 0;
        for (SessionId s : this.sessionids) {
            if (s.getJmvRoute().equals(jvmRoute))
                i++;
        }
        return "" + i;
    }
}
