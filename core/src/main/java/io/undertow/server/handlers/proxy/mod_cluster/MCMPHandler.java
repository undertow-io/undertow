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

import io.undertow.UndertowLogger;
import io.undertow.Version;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormEncodedDataDefinition;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.HttpString;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimerTask;
import java.util.UUID;

import static io.undertow.server.handlers.proxy.mod_cluster.Context.Status;
import static io.undertow.server.handlers.proxy.mod_cluster.NodeState.NodeStatus.NODE_UP;

public class MCMPHandler implements HttpHandler {

    public static final HttpString CONFIG = new HttpString("CONFIG");
    public static final HttpString ENABLE_APP = new HttpString("ENABLE-APP");
    public static final HttpString DISABLE_APP = new HttpString("DISABLE-APP");
    public static final HttpString STOP_APP = new HttpString("STOP-APP");
    public static final HttpString REMOVE_APP = new HttpString("REMOVE-APP");
    public static final HttpString STATUS = new HttpString("STATUS");
    public static final HttpString DUMP = new HttpString("DUMP");
    public static final HttpString INFO = new HttpString("INFO");
    public static final HttpString PING = new HttpString("PING");
    public static final HttpString GET = new HttpString("GET");


    private static final String VERSION_PROTOCOL = "0.2.1";
    private static final String TYPESYNTAX = "SYNTAX";
    private static final String TYPEMEM = "MEM";

    /* the syntax error messages */
    private static final String SMESPAR = "SYNTAX: Can't parse message";
    private static final String SBALBIG = "SYNTAX: Balancer field too big";
    private static final String SBAFBIG = "SYNTAX: A field is too big";
    private static final String SROUBIG = "SYNTAX: JVMRoute field too big";
    private static final String SROUBAD = "SYNTAX: JVMRoute can't be empty";
    private static final String SDOMBIG = "SYNTAX: LBGroup field too big";
    private static final String SHOSBIG = "SYNTAX: Host field too big";
    private static final String SPORBIG = "SYNTAX: Port field too big";
    private static final String STYPBIG = "SYNTAX: Type field too big";
    private static final String SALIBAD = "SYNTAX: Alias without Context";
    private static final String SCONBAD = "SYNTAX: Context without Alias";
    private static final String SBADFLD = "SYNTAX: Invalid field ";
    private static final String SBADFLD1 = " in message";
    private static final String SMISFLD = "SYNTAX: Mandatory field(s) missing in message";
    private static final String SCMDUNS = "SYNTAX: Command is not supported";
    private static final String SMULALB = "SYNTAX: Only one Alias in APP command";
    private static final String SMULCTB = "SYNTAX: Only one Context in APP command";
    private static final String SREADER = "SYNTAX: %s can't read POST data";

    /* the mem error messages */
    private static final String MNODEUI = "MEM: Can't update or insert node";
    private static final String MNODERM = "MEM: Old node still exist";
    private static final String MBALAUI = "MEM: Can't update or insert balancer";
    private static final String MNODERD = "MEM: Can't read node";
    private static final String MHOSTRD = "MEM: Can't read host alias";
    private static final String MHOSTUI = "MEM: Can't update or insert host alias";
    private static final String MCONTUI = "MEM: Can't update or insert context";

    static final byte[] CRLF = "\r\n".getBytes();

    static final String MOD_CLUSTER_EXPOSED_VERSION = "mod_cluster_undertow/" + Version.getVersionString();
    /*
     * build the mod_cluster_manager page
     * It builds the html like mod_manager.c
     *
     */
    boolean checkNonce = true;
    boolean reduceDisplay = false;
    boolean allowCmd = true;
    boolean displaySessionids = true;

    private final String advertiseGroup;
    private final int advertisePort;
    private final String advertiseAddress;
    private MessageDigest md = null;
    private final String scheme;
    private final String securityKey;
    private final String managementHost;
    private final int managementPort;

    private final ModClusterContainer container;

    private final HttpHandler next;

    private MCMAdapterBackgroundProcessor backgroundProcessor;

    MCMPHandler(ModClusterContainer container, MCMPHandlerBuilder config, HttpHandler next) {
        this.container = container;
        this.next = next;
        this.advertiseGroup = config.advertiseGroup;
        this.advertisePort = config.advertisePort;
        this.advertiseAddress = config.advertiseAddress;
        this.managementHost = config.managementHost;
        this.scheme = config.scheme;
        this.securityKey = config.securityKey;
        this.managementPort = config.managementPort;
    }

    public void start() {
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        backgroundProcessor = new MCMAdapterBackgroundProcessor();
        container.scheduleTask(backgroundProcessor, 1000);
    }

    public void stop() {
        if (backgroundProcessor != null) {
            backgroundProcessor.cancel();
        }
        backgroundProcessor = null;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        /*
         * Proxy the request that needs to be proxied and process others
         */
        InetSocketAddress addr = exchange.getDestinationAddress();
        if (addr.getPort() != managementPort || !addr.getHostName().equals(managementHost)) {
            next.handleRequest(exchange);
            return;
        }

        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        HttpString method = exchange.getRequestMethod();
        try {
            if (method.equals(GET)) {
                // In fact that is /mod_cluster_manager
                processManager(exchange);
            } else if (method.equals(CONFIG)) {
                processConfig(exchange);
            } else if (method.equals(ENABLE_APP)) {
                try {
                    Map<String, String[]> params = readPostParameters(exchange);
                    if (params == null) {
                        processError(TYPESYNTAX, SMESPAR, exchange);
                        return;
                    }
                    processEnable(exchange, params);
                    processOK(exchange);
                } catch (Exception Ex) {
                    Ex.printStackTrace(System.out);
                }
            } else if (method.equals(DISABLE_APP)) {
                Map<String, String[]> params = readPostParameters(exchange);
                if (params == null) {
                    processError(TYPESYNTAX, SMESPAR, exchange);
                    return;
                }
                processDisable(exchange, params);
                processOK(exchange);
            } else if (method.equals(STOP_APP)) {
                Map<String, String[]> params = readPostParameters(exchange);
                if (params == null) {
                    processError(TYPESYNTAX, SMESPAR, exchange);
                    return;
                }
                processStop(exchange, params);
                processOK(exchange);
            } else if (method.equals(REMOVE_APP)) {
                try {
                    processRemove(exchange);
                } catch (Exception Ex) {
                    Ex.printStackTrace(System.out);
                }
            } else if (method.equals(STATUS)) {
                processStatus(exchange);
            } else if (method.equals(DUMP)) {
                processDump(exchange);
            } else if (method.equals(INFO)) {
                try {
                    processInfo(exchange);
                } catch (Exception Ex) {
                    Ex.printStackTrace(System.out);
                }
            } else if (method.equals(PING)) {
                processPing(exchange);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
            exchange.setResponseCode(500);
            Sender resp = exchange.getResponseSender();

            ByteBuffer bb = ByteBuffer.allocate(100);
            bb.put(e.toString().getBytes());
            bb.flip();

            resp.send(bb);
            return;
        }
    }

    private void processManager(HttpServerExchange exchange) throws Exception {

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
                            Map<String, String[]> mparams = buildMap(params);
                            if (srange.equals("NODE")) {
                                processNodeCmd(exchange, mparams, Status.ENABLED);
                            }
                            if (srange.equals("DOMAIN")) {
                                boolean domain = params.containsKey("Domain");
                                if (domain) {
                                    String sdomain = params.get("Domain").getFirst();
                                    processDomainCmd(exchange, sdomain, Status.ENABLED);
                                }
                            }
                            if (srange.equals("CONTEXT")) {
                                processCmd(exchange, mparams, Status.ENABLED);
                            }
                        } else if (scmd.equals("DISABLE-APP") && range) {
                            String srange = params.get("Range").getFirst();
                            Map<String, String[]> mparams = buildMap(params);
                            if (srange.equals("NODE")) {
                                processNodeCmd(exchange, mparams, Status.DISABLED);
                            }
                            if (srange.equals("DOMAIN")) {
                                boolean domain = params.containsKey("Domain");
                                if (domain) {
                                    String sdomain = params.get("Domain").getFirst();
                                    processDomainCmd(exchange, sdomain, Status.DISABLED);
                                }
                            }
                            if (srange.equals("CONTEXT")) {
                                processCmd(exchange, mparams, Status.DISABLED);
                            }

                        }
                    }
                }
            }
        }

        exchange.setResponseCode(200);
        exchange.getResponseHeaders().add(new HttpString("Content-Type"), "text/html; charset=ISO-8859-1");
        Sender resp = exchange.getResponseSender();
        StringBuilder buf = new StringBuilder();
        buf.append("<html><head>\n<title>Mod_cluster Status</title>\n</head><body>\n");
        buf.append("<h1>" + MOD_CLUSTER_EXPOSED_VERSION + "</h1>");

        String uri = exchange.getRequestPath();
        String nonce = getNonce();
        if (refreshTime <= 0)
            buf.append("<a href=\"" + uri + "?" + nonce +
                    "&refresh=10" +
                    "\">Auto Refresh</a>");

        buf.append(" <a href=\"" + uri + "?" + nonce +
                "&Cmd=DUMP&Range=ALL" +
                "\">show DUMP output</a>");

        buf.append(" <a href=\"" + uri + "?" + nonce +
                "&Cmd=INFO&Range=ALL" +
                "\">show INFO output</a>");

        buf.append("\n");

        /* TODO sort the node by LBGroup (domain) */
        String lbgroup = "";
        for (Node node : container.getNodes()) {
            NodeConfig nodeConfig = node.getNodeConfig();
            if (!lbgroup.equals(nodeConfig.getDomain())) {
                lbgroup = nodeConfig.getDomain();
                if (reduceDisplay)
                    buf.append("<br/><br/>LBGroup " + lbgroup + ": ");
                else
                    buf.append("<h1> LBGroup " + lbgroup + ": ");
                if (allowCmd) {
                    domainCommandString(buf, uri, Status.ENABLED, lbgroup);
                    domainCommandString(buf, uri, Status.DISABLED, lbgroup);
                }
            }
            if (reduceDisplay) {
                buf.append("<br/><br/>Node " + nodeConfig.getJvmRoute());
                printProxyStat(buf, node, reduceDisplay);
            } else
                buf.append("<h1> Node " + nodeConfig.getJvmRoute() + " (" + nodeConfig.getType() + "://" + nodeConfig.getHostname() + ":" + nodeConfig.getPort() + "): </h1>\n");


            if (allowCmd) {
                nodeCommandString(buf, uri, Status.ENABLED, nodeConfig.getJvmRoute());
                nodeCommandString(buf, uri, Status.DISABLED, nodeConfig.getJvmRoute());
            }
            if (!reduceDisplay) {
                buf.append("<br/>\n");
                buf.append("Balancer: " + nodeConfig.getBalancer() + ",LBGroup: " + nodeConfig.getDomain());
                String flushpackets = "off";
                if (nodeConfig.isFlushPackets())
                    flushpackets = "Auto";
                buf.append(",Flushpackets: " + flushpackets + ",Flushwait: " + nodeConfig.getFlushwait() + ",Ping: " + nodeConfig.getPing() + " ,Smax: " + nodeConfig.getPing() + ",Ttl: " + nodeConfig.getTtl());
                printProxyStat(buf, node, reduceDisplay);
            } else {
                buf.append("<br/>\n");
            }
            // the sessionid list is mostly for demos.
            if (displaySessionids)
                buf.append(",Num sessions: " + container.getJVMRouteSessionCount(nodeConfig.getJvmRoute()));
            buf.append("\n");

            // Process the virtual-host of the node
            printInfoHost(buf, uri, reduceDisplay, allowCmd, nodeConfig.getJvmRoute());
        }

        // Display the all the actives sessions
        if (displaySessionids) {
            printInfoSessions(buf, container.getSessionIds());
        }

        buf.append("</body></html>\n");
        resp.send(buf.toString());
    }

    private void processDomainCmd(HttpServerExchange exchange, String domain, Status status) throws Exception {
        for (Node nodeConfig : container.getNodes()) {
            if (nodeConfig.getNodeConfig().getDomain().equals(domain)) {
                Map<String, String[]> params = new HashMap<>();
                String[] values = new String[1];
                values[0] = nodeConfig.getJvmRoute();
                params.put("JVMRoute", values);
                processNodeCmd(exchange, params, status);
            }
        }
    }

    private Map<String, String[]> buildMap(Map<String, Deque<String>> params) {
        Map<String, String[]> sparams = new HashMap<>();
        for (String key : params.keySet()) {
            // In fact we only have one
            String[] values = new String[1];
            values[0] = params.get(key).getFirst();
            sparams.put(key, values);
        }
        return sparams;
    }

    /*
     * list the session informations.
     */
    private void printInfoSessions(StringBuilder buf, List<SessionId> sessionids) {
        buf.append("<h1>SessionIDs:</h1>");
        buf.append("<pre>");
        for (SessionId s : sessionids)
            buf.append("id: " + s.getSessionId() + " route: " + s.getJmvRoute() + "\n");
        buf.append("</pre>");
    }

    /* based on manager_info_hosts */
    private void printInfoHost(StringBuilder buf, String uri, boolean reduceDisplay, boolean allowCmd, String jvmRoute) {
        for (VHost host : container.getHosts()) {
            if (host.getJVMRoute().equals(jvmRoute)) {
                if (!reduceDisplay) {
                    buf.append("<h2> Virtual Host " + host.getId() + ":</h2>");
                }
                printInfoContexts(buf, uri, reduceDisplay, allowCmd, host.getId(), host.getAliases(), jvmRoute);
                if (reduceDisplay) {
                    buf.append("Aliases: ");
                    for (String alias : host.getAliases())
                        buf.append(alias + " ");
                } else {
                    buf.append("<h3>Aliases:</h3>");
                    buf.append("<pre>");
                    for (String alias : host.getAliases())
                        buf.append(alias + "\n");
                    buf.append("</pre>");
                }

            }
        }

    }

    /* based on manager_info_contexts */
    private void printInfoContexts(StringBuilder buf, String uri, boolean reduceDisplay, boolean allowCmd, long host, List<String> alias, String jvmRoute) {
        if (!reduceDisplay)
            buf.append("<h3>Contexts:</h3>");
        buf.append("<pre>");
        for (Context context : container.getContexts()) {
            if (context.getJvmRoute().equals(jvmRoute) && context.getHostid() == host) {
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
                buf.append(context.getPath() + " , Status: " + status + " Request: " + context.getNbRequests() + " ");
                if (allowCmd)
                    contextCommandString(buf, uri, context.getStatus(), context.getPath(), alias, jvmRoute);
                buf.append("\n");
            }
        }
        buf.append("</pre>");
    }

    /* generate a command URL for the context */
    private void contextCommandString(StringBuilder buf, String uri, Status status, String path, List<String> alias, String jvmRoute) {
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

    private void contextString(StringBuilder buf, String path, List<String> alias, String jvmRoute) {
        buf.append("JVMRoute=" + jvmRoute + "&Alias=");
        boolean first = true;
        for (String a : alias) {
            if (first)
                first = false;
            else
                buf.append(",");
            buf.append(a);
        }
        buf.append("&Context=" + path);
    }

    private void nodeCommandString(StringBuilder buf, String uri, Status status, String jvmRoute) {
        switch (status) {
            case ENABLED:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=ENABLE-APP&Range=NODE&JVMRoute=" + jvmRoute + "\">Enable Contexts</a> ");
                break;
            case DISABLED:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=DISABLE-APP&Range=NODE&JVMRoute=" + jvmRoute + "\">Disable Contexts</a> ");
                break;
        }
    }

    private void printProxyStat(StringBuilder buf, Node node, boolean reduceDisplay) {
        String status = "NOTOK";
        if (node.getNodeState().getStatus() == NODE_UP)
            status = "OK";
        if (reduceDisplay)
            buf.append(" " + status + " ");
        else {
            buf.append(",Status: " + status + ",Elected: " + node.getNodeState().getOldelected() + ",Read: " + node.getNodeState().getRead() + ",Transferred: " + node.getNodeState().getTransfered() + ",Connected: "
                    + node.getNodeState().getConnected() + ",Load: " + node.getNodeState().getLoad());
        }
    }

    /* based on domain_command_string */
    private void domainCommandString(StringBuilder buf, String uri, Status status, String lbgroup) {
        switch (status) {
            case ENABLED:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=ENABLE-APP&Range=DOMAIN&Domain=" + lbgroup + "\">Enable Nodes</a>");
                break;
            case DISABLED:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=DISABLE-APP&Range=DOMAIN&Domain=" + lbgroup + "\">Disable Nodes</a>");
                break;
        }
    }

    /**
     * Process <tt>PING</tt> request
     *
     * @throws Exception
     */
    private void processPing(HttpServerExchange exchange) throws Exception {
        System.out.println("process_ping");
        Map<String, String[]> params = readPostParameters(exchange);
        if (params == null) {
            processError(TYPESYNTAX, SMESPAR, exchange);
            return;
        }
        String jvmRoute = null;
        String scheme = null;
        String host = null;
        String port = null;

        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            String[] values = e.getValue();
            String value = values[0];
            if (name.equalsIgnoreCase("JVMRoute")) {
                jvmRoute = value;
            } else if (name.equalsIgnoreCase("Scheme")) {
                scheme = value;
            } else if (name.equalsIgnoreCase("Port")) {
                port = value;
            } else if (name.equalsIgnoreCase("Host")) {
                host = value;
            } else {
                processError(TYPESYNTAX, SBADFLD + name + SBADFLD1, exchange);
                return;
            }
        }
        if (jvmRoute == null) {
            if (scheme == null && host == null && port == null) {
                exchange.getResponseHeaders().add(new HttpString("Content-Type"), "text/plain");
                String data = "Type=PING-RSP&State=OK";
                Sender resp = exchange.getResponseSender();
                ByteBuffer bb = ByteBuffer.allocate(data.length());
                bb.put(data.getBytes());
                bb.flip();
                resp.send(bb);
                return;
            } else {
                if (scheme == null || host == null || port == null) {
                    processError(TYPESYNTAX, SMISFLD, exchange);
                    return;
                }
                exchange.getResponseHeaders().add(new HttpString("Content-Type"), "text/plain");
                String data = "Type=PING-RSP";
                if (ishostUp(scheme, host, port))
                    data = data.concat("&State=OK");
                else
                    data = data.concat("&State=NOTOK");

                Sender resp = exchange.getResponseSender();
                ByteBuffer bb = ByteBuffer.allocate(data.length());
                bb.put(data.getBytes());
                bb.flip();
                resp.send(bb);
                return;
            }
        } else {
            // ping the corresponding node.
            Node nodeConfig = container.getNode(jvmRoute);
            if (nodeConfig == null) {
                processError(TYPEMEM, MNODERD, exchange);
                return;
            }
            exchange.getResponseHeaders().add(new HttpString("Content-Type"), "text/plain");
            String data = "Type=PING-RSP";
            if (isNodeUp(nodeConfig))
                data = data.concat("&State=OK");
            else
                data = data.concat("&State=NOTOK");

            Sender resp = exchange.getResponseSender();
            ByteBuffer bb = ByteBuffer.allocate(data.length());
            bb.put(data.getBytes());
            bb.flip();
            resp.send(bb);
        }
    }

    private Map<String, String[]> readPostParameters(HttpServerExchange exchange) throws IOException {
        final Map<String, String[]> ret = new HashMap<>();
        FormDataParser parser = FormParserFactory.builder(false).addParser(new FormEncodedDataDefinition().setForceCreation(true)).build().createParser(exchange);

        FormData formData = parser.parseBlocking();
        Iterator<String> it = formData.iterator();
        while (it.hasNext()) {
            final String name = it.next();
            Deque<FormData.FormValue> val = formData.get(name);
            if (ret.containsKey(name)) {
                String[] existing = ret.get(name);
                String[] array = new String[val.size() + existing.length];
                System.arraycopy(existing, 0, array, 0, existing.length);
                int i = existing.length;
                for (final FormData.FormValue v : val) {
                    array[i++] = v.getValue();
                }
                ret.put(name, array);
            } else {
                String[] array = new String[val.size()];
                int i = 0;
                for (final FormData.FormValue v : val) {
                    array[i++] = v.getValue();
                }
                ret.put(name, array);
            }
        }
        return ret;
    }

    private boolean isNodeUp(Node nodeConfig) {
        System.out.println("process_ping: " + nodeConfig);
        return false;
    }

    private boolean ishostUp(String scheme, String host, String port) {
        System.out.println("process_ping: " + scheme + "://" + host + ":" + port);
        return false;
    }

    /*
     * Something like:
     *
     * Node: [1],Name: 368e2e5c-d3f7-3812-9fc6-f96d124dcf79,Balancer:
     * cluster-prod-01,LBGroup: ,Host: 127.0.0.1,Port: 8443,Type:
     * https,Flushpackets: Off,Flushwait: 10,Ping: 10,Smax: 21,Ttl: 60,Elected:
     * 0,Read: 0,Transfered: 0,Connected: 0,Load: 1 Vhost: [1:1:1], Alias:
     * default-host Vhost: [1:1:2], Alias: localhost Vhost: [1:1:3], Alias:
     * example.com Context: [1:1:1], Context: /myapp, Status: ENABLED
     */

    /**
     * Process <tt>INFO</tt> request
     *
     * @throws Exception
     */
    private void processInfo(HttpServerExchange exchange) throws Exception {

        String data = processInfoString();
        exchange.setResponseCode(200);
        exchange.getResponseHeaders().add(new HttpString("Content-Type"), "text/plain");
        exchange.getResponseHeaders().add(new HttpString("Server"), "Mod_CLuster/0.0.0");

        Sender resp = exchange.getResponseSender();
        ByteBuffer bb = ByteBuffer.allocate(data.length());
        bb.put(data.getBytes());
        bb.flip();

        resp.send(bb);
        return;
    }

    private String processInfoString() {
        int i = 1;
        StringBuilder data = new StringBuilder();

        for (Node node : container.getNodes()) {
            NodeConfig nodeConfig = node.getNodeConfig();
            data.append("Node: [").append(i).append("],Name: ").append(nodeConfig.getJvmRoute())
                    .append(",Balancer: ").append(nodeConfig.getBalancer()).append(",LBGroup: ")
                    .append(nodeConfig.getDomain()).append(",Host: ").append(nodeConfig.getHostname())
                    .append(",Port: ").append(nodeConfig.getPort()).append(",Type: ")
                    .append(nodeConfig.getType()).append(",Flushpackets: ")
                    .append((nodeConfig.isFlushPackets() ? "On" : "Off")).append(",Flushwait: ")
                    .append(nodeConfig.getFlushwait()).append(",Ping: ").append(nodeConfig.getPing())
                    .append(",Smax: ").append(nodeConfig.getSmax()).append(",Ttl: ")
                    .append(nodeConfig.getTtl()).append(",Elected: ").append(node.getNodeState().getElected())
                    .append(",Read: ").append(node.getNodeState().getRead()).append(",Transfered: ")
                    .append(node.getNodeState().getTransfered()).append(",Connected: ")
                    .append(node.getNodeState().getConnected()).append(",Load: ").append(node.getNodeState().getLoad() + "\n");
            i++;
        }

        for (VHost host : container.getHosts()) {
            int j = 1;
            long node = container.getNodeId(host.getJVMRoute());
            for (String alias : host.getAliases()) {
                data.append("Vhost: [").append(node).append(":").append(host.getId()).append(":")
                        .append(j).append("], Alias: ").append(alias).append("\n");

                j++;
            }
        }

        i = 1;
        for (Context context : container.getContexts()) {
            data.append("Context: [").append(container.getNodeId(context.getJvmRoute())).append(":")
                    .append(context.getHostid()).append(":").append(i).append("], Context: ")
                    .append(context.getPath()).append(", Status: ").append(context.getStatus())
                    .append("\n");
            i++;
        }
        return data.toString();
    }

    /*
     * something like:
     *
     * balancer: [1] Name: cluster-prod-01 Sticky: 1 [JSESSIONID]/[jsessionid]
     * remove: 0 force: 0 Timeout: 0 maxAttempts: 1 node: [1:1],Balancer:
     * cluster-prod-01,JVMRoute: 368e2e5c-d3f7-3812-9fc6-f96d124dcf79,LBGroup:
     * [],Host: 127.0.0.1,Port: 8443,Type: https,flushpackets: 0,flushwait:
     * 10,ping: 10,smax: 21,ttl: 60,timeout: 0 host: 1 [default-host] vhost: 1
     * node: 1 host: 2 [localhost] vhost: 1 node: 1 host: 3 [example.com] vhost:
     * 1 node: 1 context: 1 [/myapp] vhost: 1 node: 1 status: 1
     */

    /**
     * Process <tt>DUMP</tt> request
     *
     * @param exchange
     * @throws java.io.IOException
     */
    private void processDump(HttpServerExchange exchange) throws IOException {
        String data = processDumpString();
        exchange.setResponseCode(200);
        exchange.getResponseHeaders().add(new HttpString("Content-Type"), "text/plain");
        exchange.getResponseHeaders().add(new HttpString("Server"), "Mod_CLuster/0.0.0");

        Sender resp = exchange.getResponseSender();
        ByteBuffer bb = ByteBuffer.allocate(data.length());
        bb.put(data.getBytes());
        bb.flip();

        resp.send(bb);
    }

    private String processDumpString() {
        StringBuilder data = new StringBuilder();
        int i = 1;
        for (Balancer balancer : container.getBalancers()) {
            data.append("balancer: [" + i + "] Name: " + balancer.getName() + " Sticky: ")
                    .append((balancer.isStickySession() ? "1" : "0") + " [")
                    .append(balancer.getStickySessionCookie() + "]/[" + balancer.getStickySessionPath())
                    .append("] remove: " + (balancer.isStickySessionRemove() ? "1" : "0") + " force: ")
                    .append((balancer.isStickySessionForce() ? "1" : "0") + " Timeout: ")
                    .append(balancer.getWaitWorker() + " maxAttempts: " + balancer.getMaxattempts())
                    .append("\n");
            i++;
        }

        i = 1;
        for (Node node : container.getNodes()) {
            data.append("node: [").append(i).append(":").append(i).append("]")
                    .append(",Balancer: ").append(node.getNodeConfig().getBalancer())
                    .append(",JVMRoute: ").append(node.getJvmRoute())
                    .append(",LBGroup: ").append(node.getNodeConfig().getDomain())
                    .append(",Host: ").append(node.getNodeConfig().getHostname())
                    .append(",Port: ").append(node.getNodeConfig().getPort())
                    .append(",Type: ").append(node.getNodeConfig().getType())
                    .append(",flushpackets: ")
                    .append((node.getNodeConfig().isFlushPackets() ? "1" : "0")).append(",flushwait: ")
                    .append(node.getNodeConfig().getFlushwait()).append(",ping: ").append(node.getNodeConfig().getPing())
                    .append(",smax: ").append(node.getNodeConfig().getSmax()).append(",ttl: ")
                    .append(node.getNodeConfig().getTtl())
                    .append(",timeout: ").append(node.getNodeConfig().getTimeout())
                    .append("\n");
            i++;
        }

        for (VHost host : container.getHosts()) {
            int j = 1;
            long node = container.getNodeId(host.getJVMRoute());
            for (String alias : host.getAliases()) {
                data.append("host: ").append(j).append(" [")
                        .append(alias).append("] vhost: ").append(host.getId())
                        .append(" node: ").append(node).append("\n");

                j++;
            }
        }

        i = 1;
        for (Context context : container.getContexts()) {
            long node = container.getNodeId(context.getJvmRoute());
            data.append("context: ").append(i).append(" [").append(context.getPath())
                    .append("] vhost: ").append(context.getHostid())
                    .append(" node: ").append(node)
                    .append(" status: ").append(context.getStatus()).append("\n");
            i++;
        }

        return data.toString();
    }

    /**
     * Process <tt>STATUS</tt> request
     *
     * @throws Exception
     */
    private void processStatus(HttpServerExchange exchange) throws Exception {
        Map<String, String[]> params = readPostParameters(exchange);
        if (params == null) {
            processError(TYPESYNTAX, SMESPAR, exchange);
            return;
        }
        String jvmRoute = null;
        String load = null;

        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            String[] values = e.getValue();
            String value = values[0];
            if (name.equalsIgnoreCase("JVMRoute")) {
                jvmRoute = value;
            } else if (name.equalsIgnoreCase("Load")) {
                load = value;
            } else {
                processError(TYPESYNTAX, SBADFLD + value + SBADFLD1, exchange);
                return;
            }
        }
        if (load == null || jvmRoute == null) {
            processError(TYPESYNTAX, SMISFLD, exchange);
            return;
        }

        Node node = container.getNode(jvmRoute);
        if (node == null) {
            processError(TYPEMEM, MNODERD, exchange);
            return;
        }
        node.getNodeState().setLoad(Integer.parseInt(load));
        /* TODO we need to check the node here */
        node.getNodeState().setStatus(NODE_UP);
        processOK(exchange);
    }

    /**
     * Process <tt>REMOVE-APP</tt> request
     *
     * @throws Exception
     */
    private void processRemove(HttpServerExchange exchange) throws Exception {
        Map<String, String[]> params = readPostParameters(exchange);
        if (params == null) {
            processError(TYPESYNTAX, SMESPAR, exchange);
            return;
        }

        boolean global = false;
        if (exchange.getRequestPath().equals("*") || exchange.getRequestPath().endsWith("/*")) {
            global = true;
        }
        Context.ContextBuilder context = Context.builder();
        VHost.VHostBuilder host = VHost.builder();

        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            String[] values = e.getValue();
            String value = values[0];
            if (name.equalsIgnoreCase("JVMRoute")) {
                if (container.getNodeId(value) == -1) {
                    processError(TYPEMEM, MNODERD, exchange);
                    return;
                }
                host.setJVMRoute(value);
                context.setJvmRoute(value);
            } else if (name.equalsIgnoreCase("Alias")) {
                // Alias is something like =default-host,localhost,example.com
                String[] aliases = value.split(",");
                host.addAliases(Arrays.asList(aliases));
            } else if (name.equalsIgnoreCase("Context")) {
                context.setPath(value);
            }

        }
        if (context.getJvmRoute() == null) {
            processError(TYPESYNTAX, SROUBAD, exchange);
            return;
        }

        if (global) {
            container.removeNode(context.getJvmRoute());
        } else {
            container.remove(context.build(), host.build());
        }
        processOK(exchange);
    }

    /**
     * Process <tt>STOP-APP</tt> request
     *
     * @throws Exception
     */
    private void processStop(HttpServerExchange exchange, Map<String, String[]> params) throws Exception {
        processCmd(exchange, params, Status.STOPPED);
    }

    /**
     * Process <tt>DISABLE-APP</tt> request
     *
     * @throws Exception
     */
    private void processDisable(HttpServerExchange exchange, Map<String, String[]> params) throws Exception {
        processCmd(exchange, params, Status.DISABLED);
    }

    /**
     * Process <tt>ENABLE-APP</tt> request
     *
     * @throws Exception
     */
    private void processEnable(HttpServerExchange exchange, Map<String, String[]> params) throws Exception {
        processCmd(exchange, params, Status.ENABLED);
    }

    private void processCmd(HttpServerExchange exchange, Map<String, String[]> params, Status status) throws Exception {
        if (exchange.getRequestPath().equals("*") || exchange.getRequestPath().endsWith("/*")) {
            processNodeCmd(exchange, params, status);
            return;
        }

        Context.ContextBuilder context = Context.builder();
        VHost.VHostBuilder host = VHost.builder();

        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            String[] values = e.getValue();
            String value = values[0];
            if (name.equalsIgnoreCase("JVMRoute")) {
                if (container.getNodeId(value) == -1) {
                    processError(TYPEMEM, MNODERD, exchange);
                    return;
                }
                host.setJVMRoute(value);
                context.setJvmRoute(value);
            } else if (name.equalsIgnoreCase("Alias")) {
                // Alias is something like =default-host,localhost,example.com
                String[] aliases = value.split(",");
                host.addAliases(Arrays.asList(aliases));
            } else if (name.equalsIgnoreCase("Context")) {
                context.setPath(value);
            }

        }
        if (context.getJvmRoute() == null) {
            processError(TYPESYNTAX, SROUBAD, exchange);
            return;
        }
        context.setStatus(status);
        long id = container.insertupdate(host.build());
        context.setHostid(id);
        container.insertupdate(context.build());
    }

    /* Process a *-APP command that applies to the node */
    private void processNodeCmd(HttpServerExchange exchange, Map<String, String[]> params, Status status) throws Exception {
        String jvmRoute = null;
        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            String[] values = e.getValue();
            String value = values[0];
            if (name.equalsIgnoreCase("JVMRoute")) {
                jvmRoute = value;
            }
        }
        if (jvmRoute == null) {
            processError(TYPESYNTAX, SROUBAD, exchange);
            return;
        }

        for (VHost host : container.getHosts()) {
            if (host.getJVMRoute().equals(jvmRoute)) {
                for (Context context : container.getContexts()) {
                    if (context.getJvmRoute().equals(jvmRoute) && context.getHostid() == host.getId()) {
                        if (status != Status.REMOVED) {
                            context.setStatus(status);
                            container.insertupdate(context);
                        } else {
                            container.remove(context, host);
                        }
                    }
                }
            }
        }
    }

    /**
     * Process <tt>CONFIG</tt> request
     *
     * @throws Exception
     */
    private void processConfig(HttpServerExchange exchange) throws Exception {
        Map<String, String[]> params = readPostParameters(exchange);
        if (params == null) {
            processError(TYPESYNTAX, SMESPAR, exchange);
            return;
        }
        NodeConfig.NodeBuilder node = NodeConfig.builder();
        Balancer.BalancerBuilder balancer = Balancer.builder();

        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            String[] values = e.getValue();
            String value = values[0];
            if (name.equalsIgnoreCase("Balancer")) {
                UndertowLogger.ROOT_LOGGER.error("Balancer updates are not supported");
            } else if (name.equalsIgnoreCase("StickySession")) {
                if (value.equalsIgnoreCase("No"))
                    balancer.setStickySession(false);
            } else if (name.equalsIgnoreCase("StickySessionCookie")) {
                balancer.setStickySessionCookie(value);
            } else if (name.equalsIgnoreCase("StickySessionPath")) {
                balancer.setStickySessionPath(value);
            } else if (name.equalsIgnoreCase("StickySessionRemove")) {
                if (value.equalsIgnoreCase("Yes"))
                    balancer.setStickySessionRemove(true);
            } else if (name.equalsIgnoreCase("StickySessionForce")) {
                if (value.equalsIgnoreCase("no"))
                    balancer.setStickySessionForce(false);
            } else if (name.equalsIgnoreCase("WaitWorker")) {
                balancer.setWaitWorker(Integer.valueOf(value));
            } else if (name.equalsIgnoreCase("Maxattempts")) {
                balancer.setMaxattempts(Integer.valueOf(value));
            } else if (name.equalsIgnoreCase("JVMRoute")) {
                node.setJvmRoute(value);
            } else if (name.equalsIgnoreCase("Domain")) {
                node.setDomain(value);
            } else if (name.equalsIgnoreCase("Host")) {
                node.setHostname(value);
            } else if (name.equalsIgnoreCase("Port")) {
                node.setPort(Integer.valueOf(value));
            } else if (name.equalsIgnoreCase("Type")) {
                node.setType(value);
            } else if (name.equalsIgnoreCase("Reversed")) {
                continue; // ignore it.
            } else if (name.equalsIgnoreCase("flushpacket")) {
                if (value.equalsIgnoreCase("on"))
                    node.setFlushPackets(true);
                if (value.equalsIgnoreCase("auto"))
                    node.setFlushPackets(true);
            } else if (name.equalsIgnoreCase("flushwait")) {
                node.setFlushwait(Integer.valueOf(value));
            } else if (name.equalsIgnoreCase("ping")) {
                node.setPing(Integer.valueOf(value));
            } else if (name.equalsIgnoreCase("smax")) {
                node.setSmax(Integer.valueOf(value));
            } else if (name.equalsIgnoreCase("ttl")) {
                node.setTtl(Integer.valueOf(value));
            } else if (name.equalsIgnoreCase("Timeout")) {
                node.setTimeout(Integer.valueOf(value));
            } else {
                processError(TYPESYNTAX, SBADFLD + name + SBADFLD1, exchange);
                return;
            }
        }

        container.insertupdate(balancer.build());
        container.insertupdate(node.build());
        processOK(exchange);
    }

    /**
     * If the process is OK, then add 200 HTTP status and its "OK" phrase
     *
     * @throws Exception
     */
    private void processOK(HttpServerExchange exchange) throws Exception {
        exchange.setResponseCode(200);
        exchange.getResponseHeaders().add(new HttpString("Content-type"), "plain/text");
        exchange.endExchange();
    }

    /**
     * If any error occurs,
     *
     * @param type
     * @param errstring
     * @throws Exception
     */
    private void processError(String type, String errstring, HttpServerExchange exchange) throws Exception {
        exchange.setResponseCode(500);
        // res.setMessage("ERROR");
        exchange.getResponseHeaders().add(new HttpString("Version"), VERSION_PROTOCOL);
        exchange.getResponseHeaders().add(new HttpString("Type"), type);
        exchange.getResponseHeaders().add(new HttpString("Mess"), errstring);
        exchange.endExchange();
    }

    /* Nonce logic */
    private final Random r = new SecureRandom();
    private String nonce = null;

    String getNonce() {
        return "nonce=" + getRawNonce();
    }

    String getRawNonce() {
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

    public String getManagementHost() {
        return managementHost;
    }

    public int getManagementPort() {
        return managementPort;
    }

    protected class MCMAdapterBackgroundProcessor extends TimerTask {

        final InetAddress group;
        final InetAddress addr;
        final MulticastSocket s;
        int seq = 0;

        MCMAdapterBackgroundProcessor() {
            try {
                group = InetAddress.getByName(advertiseGroup);
                addr = InetAddress.getByName(advertiseAddress);
                InetSocketAddress addrs = new InetSocketAddress(advertisePort);

                s = new MulticastSocket(addrs);
                s.setTimeToLive(29);
                s.joinGroup(group);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }


        /*
         * the messages to send are something like:
         *
         * HTTP/1.0 200 OK
         * Date: Thu, 13 Sep 2012 09:24:02 GMT
         * Sequence: 5
         * Digest: ae8e7feb7cd85be346134657de3b0661
         * Server: b58743ba-fd84-11e1-bd12-ad866be2b4cc
         * X-Manager-Address: 127.0.0.1:6666
         * X-Manager-Url: /b58743ba-fd84-11e1-bd12-ad866be2b4cc
         * X-Manager-Protocol: http
         * X-Manager-Host: 10.33.144.3
         * non-Javadoc)
         */
        @Override
        public void run() {
            try {

                /*
                 * apr_uuid_get(&magd->suuid);
                 * magd->srvid[0] = '/';
                 * apr_uuid_format(&magd->srvid[1], &magd->suuid);
                 * In fact we use the srvid on the 2 second byte [1]
                 */
                String server = UUID.randomUUID().toString();
                Date date = new Date(System.currentTimeMillis());
                md.reset();
                byte[] ssalt;
                if (securityKey == null) {
                    // Security key is not configured, so the result hash was zero bytes
                    ssalt = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
                } else {
                    digestString(md, securityKey);
                    ssalt = md.digest();
                }
                md.update(ssalt);
                digestString(md, date);
                digestString(md, seq);
                digestString(md, server);
                byte[] digest = md.digest();
                StringBuilder str = new StringBuilder();
                for (int i = 0; i < digest.length; i++) {
                    str.append(String.format("%x", digest[i]));
                }

                String sbuf = "HTTP/1.0 200 OK\r\n" + "Date: " + date + "\r\n" + "Sequence: "
                        + seq + "\r\n" + "Digest: " + str.toString() + "\r\n" + "Server: "
                        + server + "\r\n" + "X-Manager-Address: " + getManagementHost() + ":" + getManagementPort()
                        + "\r\n" + "X-Manager-Url: /" + server + "\r\n"
                        + "X-Manager-Protocol: " + scheme + "\r\n" + "X-Manager-Host: " + getManagementHost()
                        + "\r\n";

                byte[] buf = sbuf.getBytes();
                DatagramPacket data = new DatagramPacket(buf, buf.length, group, advertisePort);
                s.send(data);
                seq++;
            } catch (Exception Ex) {
                Ex.printStackTrace();
            }
        }

        private void digestString(MessageDigest md, int seq) {
            String sseq = "" + seq;
            digestString(md, sseq);
        }

        private void digestString(MessageDigest md, Date date) {
            String sdate = date.toString();
            digestString(md, sdate);
        }

        private void digestString(MessageDigest md, String securityKey) {
            byte[] buf = securityKey.getBytes();
            md.update(buf);

        }

    }

    public static MCMPHandlerBuilder builder() {
        return new MCMPHandlerBuilder();
    }

    public static class MCMPHandlerBuilder {

        boolean checkNonce = true;
        boolean reduceDisplay = false;
        boolean allowCmd = true;
        boolean displaySessionids = true;

        private String advertiseGroup = "224.0.1.105";
        private int advertisePort = 23364;
        private String advertiseAddress = "127.0.0.1";
        private MessageDigest md = null;
        private String scheme = "http";
        private String securityKey;
        private String managementHost;
        private int managementPort;

        MCMPHandlerBuilder() {

        }

        public boolean isCheckNonce() {
            return checkNonce;
        }

        public void setCheckNonce(boolean checkNonce) {
            this.checkNonce = checkNonce;
        }

        public boolean isReduceDisplay() {
            return reduceDisplay;
        }

        public void setReduceDisplay(boolean reduceDisplay) {
            this.reduceDisplay = reduceDisplay;
        }

        public boolean isAllowCmd() {
            return allowCmd;
        }

        public void setAllowCmd(boolean allowCmd) {
            this.allowCmd = allowCmd;
        }

        public boolean isDisplaySessionids() {
            return displaySessionids;
        }

        public void setDisplaySessionids(boolean displaySessionids) {
            this.displaySessionids = displaySessionids;
        }

        public void setAdvertiseGroup(String advertiseGroup) {
            this.advertiseGroup = advertiseGroup;
        }

        public void setAdvertisePort(int advertisePort) {
            this.advertisePort = advertisePort;
        }

        public void setAdvertiseAddress(String advertiseAddress) {
            this.advertiseAddress = advertiseAddress;
        }

        public String getAdvertiseGroup() {
            return advertiseGroup;
        }

        public int getAdvertisePort() {
            return advertisePort;
        }

        public String getAdvertiseAddress() {
            return advertiseAddress;
        }

        public MessageDigest getMd() {
            return md;
        }

        public void setMd(MessageDigest md) {
            this.md = md;
        }

        public String getScheme() {
            return scheme;
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public String getSecurityKey() {
            return securityKey;
        }

        public void setSecurityKey(String securityKey) {
            this.securityKey = securityKey;
        }

        public String getManagementHost() {
            return managementHost;
        }

        public void setManagementHost(String managementHost) {
            this.managementHost = managementHost;
        }

        public int getManagementPort() {
            return managementPort;
        }

        public void setManagementPort(int managementPort) {
            this.managementPort = managementPort;
        }

        public MCMPHandler build(final ModClusterContainer container, final HttpHandler next) {
            return new MCMPHandler(container, this, next);
        }
    }
}
