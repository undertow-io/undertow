/**
 * JBoss, Home of Professional Open Source. Copyright 2012, Red Hat, Inc., and
 * individual contributors as indicated by the @author tags. See the
 * copyright.txt file in the distribution for a full listing of individual
 * contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package io.undertow.proxy.container;

import io.undertow.proxy.xml.XmlConfig;
import io.undertow.proxy.xml.XmlNode;
import io.undertow.proxy.xml.XmlNodes;
import io.undertow.server.handlers.Cookie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.jboss.logging.Logger;

/**
 * {@code NodeService}
 *
 * Created on Jun 20, 2012 at 3:16:46 PM
 *
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class NodeService extends LifeCycleServiceAdapter {

    private static final Logger logger = Logger.getLogger(NodeService.class);

    private List<Node> nodes = new ArrayList<Node>();
    private List<Balancer> balancers = new ArrayList<Balancer>();
    private List<VHost> hosts = new ArrayList<VHost>();
    private List<Context> contexts = new ArrayList<Context>();


    private List<Node> failedNodes;
    private Random random;

    /**
     * Create a new instance of {@code NodeService}
     */
    public NodeService() {
        super();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.LifeCycleServiceAdapter#init()
     */
    @Override
    public void init() throws Exception {
        if (isInitialized()) {
            return;
        }

        logger.info("Initializing Node Service");
        this.random = new Random();
        this.nodes = new ArrayList<Node>();
        this.failedNodes = new ArrayList<Node>();

        XmlNodes xmlNodes = XmlConfig.loadNodes();
        logger.info("Adding new nodes : " + xmlNodes);
        for (XmlNode n : xmlNodes.getNodes()) {
            Node node = new Node();
            node.setJvmRoute(UUID.randomUUID().toString());
            node.setHostname(n.getHostname());
            node.setPort(n.getPort());
            this.nodes.add(node);
        }

        setInitialized(true);
        logger.info("Node Service initialized");
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.LifeCycleServiceAdapter#start()
     */
    @Override
    public void start() throws Exception {
        // start new thread for node status checker task
        startNewDaemonThread(new NodeStatusChecker());
        // Start new thread for failed node health check
        startNewDaemonThread(new HealthChecker());
    }

    /**
     * Create and start a new thread for the specified target task
     *
     * @param task
     */
    private void startNewDaemonThread(Runnable task) {
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /**
     * @return the number of active nodes
     */
    public int getActiveNodes() {
        return this.nodes.size();
    }

    /**
     * Select a node randomly
     *
     * @param n the number of tries
     * @return a {@link Node}
     * @see #getNode()
     */
    private Node getNode(int n) {
        if (n >= this.nodes.size()) {
            return null;
        } else {
            int index = random.nextInt(this.nodes.size());
            Node node = this.nodes.get(index);
            return (node.isNodeUp() ? node : getNode(n + 1));
        }
    }

    /**
     * Select a node for the specified {@code Request}
     *
     * @param sessionid
     * @return a node instance form the list of nodes
     */
    public Node getNodeBySessionid(String sessionid) {
        // URI decoding
        // String requestURI = request.decodedURI().toString();

        // TODO complete code here
        System.out.println("getNode: " + sessionid);

        return getNode();
    }

    /**
     *
     */
    public void printNodes() {
        if (this.nodes.isEmpty()) {
            logger.info("No nodes available");
            return;
        }
        StringBuilder sb = new StringBuilder("--> Available nodes : [");
        int i = 0;
        for (Node n : this.nodes) {
            sb.append(n.getHostname() + ":" + n.getPort());
            if ((i++) < this.nodes.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        logger.info(sb);
    }

    /**
     * Select a new node for the specified request and mark the failed node as unreachable
     *
     * @param sessionid
     * @param failedNode
     * @return
     */
    public Node getNodeBySessionid(String sessionid, Node failedNode) {
        if (failedNode != null) {
            // Set the node status to down
            logger.warn("The node [" + failedNode.getHostname() + ":" + failedNode.getPort() + "] is down");
            failedNode.setNodeDown();
        }

        return getNodeBySessionid(sessionid);
    }

    /**
     * {@code HealthChecker}
     *
     * Created on Sep 18, 2012 at 3:46:36 PM
     *
     * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
     */
    private class HealthChecker implements Runnable {

        /*
         * (non-Javadoc)
         *
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run() {
            List<Node> tmp = new ArrayList<Node>();
            while (true) {
                while (failedNodes.isEmpty()) {
                    synchronized (failedNodes) {
                        try {
                            // Waits at most 5 seconds
                            failedNodes.wait(5000);
                        } catch (InterruptedException e) {
                            // NOPE
                        }
                    }
                }
                logger.info("Starting health check for previously failed nodes");
                for (Node node : failedNodes) {
                    if (checkHealth(node)) {
                        node.setNodeUp();
                        tmp.add(node);
                    }
                }

                if (tmp.isEmpty()) {
                    continue;
                }

                synchronized (nodes) {
                    nodes.addAll(tmp);
                }

                synchronized (failedNodes) {
                    failedNodes.removeAll(tmp);
                }
                tmp.clear();

                try {
                    // Try after 5 seconds
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    // NOPE
                }
            }
        }

        /**
         * Check the health of the failed node
         *
         * @param node
         * @return <tt>true</tt> if the node is reachable else <tt>false</tt>
         */
        public boolean checkHealth(Node node) {
            if (node == null) {
                return false;
            }
            boolean ok = false;
            // TODO we should use the connectionPool instead.
            java.net.Socket s = null;
            try {
                s = new java.net.Socket(node.getHostname(), node.getPort());
                s.setSoLinger(true, 0);
                ok = true;
            } catch (Exception e) {
                // Ignore
            } finally {
                if (s != null) {
                    try {
                        s.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }

            return ok;
        }
    }

    /**
     * {@code NodeStatusChecker}
     *
     * Created on Sep 18, 2012 at 3:49:56 PM
     *
     * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
     */
    private class NodeStatusChecker implements Runnable {

        @Override
        public void run() {
            List<Node> tmp = new ArrayList<Node>();
            while (true) {
                try {
                    Thread.sleep(5000);
                    // Retrieve nodes with status "DOWN"
                    for (Node n : nodes) {
                        if (n.isNodeDown()) {
                            tmp.add(n);
                        }
                    }

                    if (tmp.isEmpty()) {
                        continue;
                    }
                    // Remove failed nodes from the list of nodes
                    synchronized (nodes) {
                        nodes.removeAll(tmp);
                    }
                    // Add selected nodes to the list of failed nodes
                    synchronized (failedNodes) {
                        failedNodes.addAll(tmp);
                    }
                    tmp.clear();

                    // Retrieve nodes with status "UP"
                    for (Node n : failedNodes) {
                        if (n.isNodeUp()) {
                            tmp.add(n);
                        }
                    }

                    if (tmp.isEmpty()) {
                        continue;
                    }
                    // Remove all healthy nodes from the list of failed nodes
                    synchronized (failedNodes) {
                        failedNodes.removeAll(tmp);
                    }
                    // Add selected nodes to the list of healthy nodes
                    synchronized (nodes) {
                        nodes.addAll(tmp);
                    }
                    tmp.clear();

                    // printNodes();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    public List<VHost> getHosts() {
        return hosts;
    }

    public void setHosts(List<VHost> hosts) {
        this.hosts = hosts;
    }

    public List<Context> getContexts() {
        return contexts;
    }

    public void setContexts(List<Context> contexts) {
        this.contexts = contexts;
    }

    public List<Balancer> getBalancers() {
        return balancers;
    }

    public void setBalancers(List<Balancer> balancers) {
        this.balancers = balancers;
    }

    public Node getNode(String jvmRoute) {
        for (Node nod : getNodes()) {
            if (nod.getJvmRoute().equals(jvmRoute)) {
                return nod;
            }
        }
        return null;
    }

    /*
     * return the corresponding node corresponding to the cookie.
     * the format is sessionid.JVMRoute
     */
    public Node getNodeByCookie(String cookie) {
        int index =  cookie.lastIndexOf(".");
        if (index == -1)
            return null;
        return getNode(cookie.substring(index+1));
    }

    /*
     * Find the cookie and return the corresponding sessionid.
     */
    public String getNodeByCookie(Map<String, Cookie> map) {
        for (Balancer bal : balancers) {
            if (map.containsKey(bal.getStickySessionCookie())) {
                // we have a balancer that uses that cookie.
                return map.get(bal.getStickySessionCookie()).getValue();
            }
        }
        return null;
    }
    /* get the least loaded node according to the tablel values */

    public Node getNode() {
        Node node = null;
        for (Node nod : getNodes()) {
            if (nod.getStatus() == Node.NodeStatus.NODE_DOWN)
                continue; // skip it.
            if (node != null) {
                int status = ((node.getElected() - node.getOldelected()) * 1000) / node.getLoad();
                int status1 = ((nod.getElected() - nod.getOldelected()) * 1000) / nod.getLoad();
                if (status1 > status)
                    node = nod;
            } else
                node = nod;
        }
        if (node != null)
            node.setElected(node.getElected()+1);
        return node;
    }
}
