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

import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The mod cluster manager web frontend.
 *
 * @author Emanuel Muckenhuber
 */
class MCMPWebManager extends MCMPHandler {

    private final boolean checkNonce;
    private final boolean reduceDisplay;
    private final boolean allowCmd;

    private final Random r = new SecureRandom();
    private String nonce = null;

    MCMPWebManager(MCMPConfig.MCMPWebManagerConfig config, ModCluster modCluster, HttpHandler next) {
        super(config, modCluster, next);
        this.checkNonce = config.isCheckNonce();
        this.reduceDisplay = config.isReduceDisplay();
        this.allowCmd = config.isAllowCmd();
    }

    String getNonce() {
        return "nonce=" + getRawNonce();
    }

    synchronized String getRawNonce() {
        if (this.nonce == null) {
            byte[] nonce = new byte[16];
            r.nextBytes(nonce);
            this.nonce = "";
            for (int i = 0; i < 16; i = i + 2) {
                this.nonce = this.nonce.concat(Integer.toHexString(0xFF & nonce[i] * 16 + 0xFF & nonce[i + 1]));
            }
        }
        return nonce;
    }

    @Override
    protected void handleRequest(HttpString method, HttpServerExchange exchange) throws Exception {
        if (!Methods.GET.equals(method)) {
            super.handleRequest(method, exchange);
            return;
        }

        // Process the request
        processRequest(exchange);
    }

    private void processRequest(HttpServerExchange exchange) throws IOException {
        Map<String, Deque<String>> params = exchange.getQueryParameters();
        boolean hasNonce = params.containsKey("nonce");
        int refreshTime = 0;
        if (checkNonce) {
            /* Check the nonce */
            if (hasNonce) {
                String receivedNonce = params.get("nonce").getFirst();
                if (receivedNonce.equals(getRawNonce())) {
                    boolean refresh = params.containsKey("refresh");
                    if (refresh) {
                        String sval = params.get("refresh").getFirst();
                        refreshTime = Integer.parseInt(sval);
                        if (refreshTime < 10)
                            refreshTime = 10;
                        exchange.getResponseHeaders().add(new HttpString("Refresh"), Integer.toString(refreshTime));
                    }
                    boolean cmd = params.containsKey("Cmd");
                    boolean range = params.containsKey("Range");
                    if (cmd) {
                        String scmd = params.get("Cmd").getFirst();
                        if (scmd.equals("INFO")) {
                            processInfo(exchange);
                            return;
                        } else if (scmd.equals("DUMP")) {
                            processDump(exchange);
                            return;
                        } else if (scmd.equals("ENABLE-APP") && range) {
                            String srange = params.get("Range").getFirst();
                            final RequestData data = buildRequestData(exchange, params);
                            if (srange.equals("NODE")) {
                                processNodeCommand(exchange, data, MCMPAction.ENABLE);
                            }
                            if (srange.equals("DOMAIN")) {
                                boolean domain = params.containsKey("Domain");
                                if (domain) {
                                    String sdomain = params.get("Domain").getFirst();
                                    processDomainCmd(exchange, sdomain, MCMPAction.ENABLE);
                                }
                            }
                            if (srange.equals("CONTEXT")) {
                                processAppCommand(exchange, data, MCMPAction.ENABLE);
                            }
                        } else if (scmd.equals("DISABLE-APP") && range) {
                            final String srange = params.get("Range").getFirst();
                            final RequestData data = buildRequestData(exchange, params);
                            if (srange.equals("NODE")) {
                                processNodeCommand(exchange, data, MCMPAction.DISABLE);
                            }
                            if (srange.equals("DOMAIN")) {
                                boolean domain = params.containsKey("Domain");
                                if (domain) {
                                    String sdomain = params.get("Domain").getFirst();
                                    processDomainCmd(exchange, sdomain, MCMPAction.DISABLE);
                                }
                            }
                            if (srange.equals("CONTEXT")) {
                                processAppCommand(exchange, data, MCMPAction.DISABLE);
                            }
                        }
                        return;
                    }
                }
            }
        }

        exchange.setStatusCode(StatusCodes.OK);
        exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "text/html; charset=ISO-8859-1");
        final Sender resp = exchange.getResponseSender();

        final StringBuilder buf = new StringBuilder();
        buf.append("<html><head>\n<title>Mod_cluster Status</title>\n</head><body>\n");
        buf.append("<h1>" + MOD_CLUSTER_EXPOSED_VERSION + "</h1>");

        final String uri = exchange.getRequestPath();
        final String nonce = getNonce();
        if (refreshTime <= 0) {
            buf.append("<a href=\"").append(uri).append("?").append(nonce).append("&refresh=").append(refreshTime).append("\">Auto Refresh</a>");
        }
        buf.append(" <a href=\"").append(uri).append("?").append(nonce).append("&Cmd=DUMP&Range=ALL").append("\">show DUMP output</a>");
        buf.append(" <a href=\"").append(uri).append("?").append(nonce).append("&Cmd=INFO&Range=ALL").append("\">show INFO output</a>");
        buf.append("\n");

        // Show load balancing groups
        final Map<String, List<Node>> nodes = new LinkedHashMap<>();
        for (final Node node : container.getNodes()) {
            final String domain = node.getNodeConfig().getDomain() != null ? node.getNodeConfig().getDomain() : "";
            List<Node> list = nodes.get(domain);
            if (list == null) {
                list = new ArrayList<>();
                nodes.put(domain, list);
            }
            list.add(node);
        }

        for (Map.Entry<String, List<Node>> entry : nodes.entrySet()) {
            final String groupName = entry.getKey();
            if (reduceDisplay) {
                buf.append("<br/><br/>LBGroup " + groupName + ": ");
            } else {
                buf.append("<h1> LBGroup " + groupName + ": ");
            }
            if (allowCmd) {
                domainCommandString(buf, uri, MCMPAction.ENABLE, groupName);
                domainCommandString(buf, uri, MCMPAction.DISABLE, groupName);
            }

            for (final Node node : entry.getValue()) {
                final NodeConfig nodeConfig = node.getNodeConfig();
                if (reduceDisplay) {
                    buf.append("<br/><br/>Node " + nodeConfig.getJvmRoute());
                    printProxyStat(buf, node, reduceDisplay);
                } else {
                    buf.append("<h1> Node " + nodeConfig.getJvmRoute() + " (" + nodeConfig.getConnectionURI() + "): </h1>\n");
                }

                if (allowCmd) {
                    nodeCommandString(buf, uri, MCMPAction.ENABLE, nodeConfig.getJvmRoute());
                    nodeCommandString(buf, uri, MCMPAction.DISABLE, nodeConfig.getJvmRoute());
                }
                if (!reduceDisplay) {
                    buf.append("<br/>\n");
                    buf.append("Balancer: " + nodeConfig.getBalancer() + ",LBGroup: " + nodeConfig.getDomain());
                    String flushpackets = "off";
                    if (nodeConfig.isFlushPackets()) {
                        flushpackets = "Auto";
                    }
                    buf.append(",Flushpackets: " + flushpackets + ",Flushwait: " + nodeConfig.getFlushwait() + ",Ping: " + nodeConfig.getPing() + " ,Smax: " + nodeConfig.getPing() + ",Ttl: " + nodeConfig.getTtl());
                    printProxyStat(buf, node, reduceDisplay);
                } else {
                    buf.append("<br/>\n");
                }
                buf.append("\n");

                // Process the virtual-host of the node
                printInfoHost(buf, uri, reduceDisplay, allowCmd, node);
            }
        }
        buf.append("</body></html>\n");
        resp.send(buf.toString());
    }

    void nodeCommandString(StringBuilder buf, String uri, MCMPAction status, String jvmRoute) {
        switch (status) {
            case ENABLE:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=ENABLE-APP&Range=NODE&JVMRoute=" + jvmRoute + "\">Enable Contexts</a> ");
                break;
            case DISABLE:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=DISABLE-APP&Range=NODE&JVMRoute=" + jvmRoute + "\">Disable Contexts</a> ");
                break;
        }
    }

    static void printProxyStat(StringBuilder buf, Node node, boolean reduceDisplay) {
        String status = "NOTOK";
        if (node.getStatus() == NodeStatus.NODE_UP)
            status = "OK";
        if (reduceDisplay) {
            buf.append(" " + status + " ");
        } else {
            buf.append(",Status: " + status + ",Elected: " + node.getElected() + ",Read: " + node.getConnectionPool().getClientStatistics().getRead() + ",Transferred: " + node.getConnectionPool().getClientStatistics().getWritten() + ",Connected: "
                    + node.getConnectionPool().getOpenConnections() + ",Load: " + node.getLoad());
        }
    }

    /* based on domain_command_string */
    void domainCommandString(StringBuilder buf, String uri, MCMPAction status, String lbgroup) {
        switch (status) {
            case ENABLE:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=ENABLE-APP&Range=DOMAIN&Domain=" + lbgroup + "\">Enable Nodes</a> ");
                break;
            case DISABLE:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=DISABLE-APP&Range=DOMAIN&Domain=" + lbgroup + "\">Disable Nodes</a>");
                break;
        }
    }

    void processDomainCmd(HttpServerExchange exchange, String domain, MCMPAction action) throws IOException {
        if (domain != null) {
            for (final Node node : container.getNodes()) {
                if (domain.equals(node.getNodeConfig().getDomain())) {
                    processNodeCommand(node.getJvmRoute(), action);
                }
            }
        }
        processOK(exchange);
    }

    /* based on manager_info_hosts */
    private void printInfoHost(StringBuilder buf, String uri, boolean reduceDisplay, boolean allowCmd, final Node node) {
        for (Node.VHostMapping host : node.getVHosts()) {
            if (!reduceDisplay) {
                buf.append("<h2> Virtual Host " + host.getId() + ":</h2>");
            }
            printInfoContexts(buf, uri, reduceDisplay, allowCmd, host.getId(), host, node);
            if (reduceDisplay) {
                buf.append("Aliases: ");
                for (String alias : host.getAliases()) {
                    buf.append(alias + " ");
                }
            } else {
                buf.append("<h3>Aliases:</h3>");
                buf.append("<pre>");
                for (String alias : host.getAliases()) {
                    buf.append(alias + "\n");
                }
                buf.append("</pre>");
            }
        }
    }

    /* based on manager_info_contexts */
    private void printInfoContexts(StringBuilder buf, String uri, boolean reduceDisplay, boolean allowCmd, long host, Node.VHostMapping vhost, Node node) {
        if (!reduceDisplay)
            buf.append("<h3>Contexts:</h3>");
        buf.append("<pre>");
        for (Context context : node.getContexts()) {
            if (context.getVhost() == vhost) {
                String status = "REMOVED";
                switch (context.getStatus()) {
                    case ENABLED:
                        status = "ENABLED";
                        break;
                    case DISABLED:
                        status = "DISABLED";
                        break;
                    case STOPPED:
                        status = "STOPPED";
                        break;
                }
                buf.append(context.getPath() + " , Status: " + status + " Request: " + context.getActiveRequests() + " ");
                if (allowCmd) {
                    contextCommandString(buf, uri, context.getStatus(), context.getPath(), vhost.getAliases(), node.getJvmRoute());
                }
                buf.append("\n");
            }
        }
        buf.append("</pre>");
    }

    /* generate a command URL for the context */
    void contextCommandString(StringBuilder buf, String uri, Context.Status status, String path, List<String> alias, String jvmRoute) {
        switch (status) {
            case DISABLED:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=ENABLE-APP&Range=CONTEXT&");
                contextString(buf, path, alias, jvmRoute);
                buf.append("\">Enable</a> ");
                break;
            case ENABLED:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=DISABLE-APP&Range=CONTEXT&");
                contextString(buf, path, alias, jvmRoute);
                buf.append("\">Disable</a> ");
                break;
        }
    }

    static void contextString(StringBuilder buf, String path, List<String> alias, String jvmRoute) {
        buf.append("JVMRoute=" + jvmRoute + "&Alias=");
        boolean first = true;
        for (String a : alias) {
            if (first) {
                first = false;
            } else {
                buf.append(",");
            }
            buf.append(a);
        }
        buf.append("&Context=" + path);
    }

    static RequestData buildRequestData(final HttpServerExchange exchange, Map<String, Deque<String>> params) {
        final RequestData data = new RequestData();
        for (final Map.Entry<String, Deque<String>> entry : params.entrySet()) {
            final HttpString name = new HttpString(entry.getKey());
            data.addValues(name, entry.getValue());
        }
        return data;
    }

}
