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

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.Version;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormEncodedDataDefinition;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.ssl.XnioSsl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.ALIAS;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.BALANCER;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.CONTEXT;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.DOMAIN;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.FLUSH_PACKET;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.FLUSH_WAIT;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.HOST;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.JVMROUTE;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.LOAD;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.MAXATTEMPTS;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.PORT;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.REVERSED;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.SCHEME;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.SMAX;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.STICKYSESSION;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.STICKYSESSIONCOOKIE;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.STICKYSESSIONFORCE;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.STICKYSESSIONPATH;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.STICKYSESSIONREMOVE;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.TIMEOUT;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.TTL;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.TYPE;

/**
 * The mod cluster management protocol http handler.
 *
 * @author Emanuel Muckenhuber
 */
class MCMPHandler implements HttpHandler {

    enum MCMPAction {

        ENABLE,
        DISABLE,
        STOP,
        REMOVE,
        ;

    }

    public static final HttpString CONFIG = new HttpString("CONFIG");
    public static final HttpString ENABLE_APP = new HttpString("ENABLE-APP");
    public static final HttpString DISABLE_APP = new HttpString("DISABLE-APP");
    public static final HttpString STOP_APP = new HttpString("STOP-APP");
    public static final HttpString REMOVE_APP = new HttpString("REMOVE-APP");
    public static final HttpString STATUS = new HttpString("STATUS");
    public static final HttpString DUMP = new HttpString("DUMP");
    public static final HttpString INFO = new HttpString("INFO");
    public static final HttpString PING = new HttpString("PING");

    private static final Set<HttpString> HANDLED_METHODS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(CONFIG, ENABLE_APP, DISABLE_APP, STOP_APP, REMOVE_APP, STATUS, INFO, DUMP, PING)));

    protected static final String VERSION_PROTOCOL = "0.2.1";
    protected static final String MOD_CLUSTER_EXPOSED_VERSION = "mod_cluster_undertow/" + Version.getVersionString();

    private static final String CONTENT_TYPE = "text/plain; charset=ISO-8859-1";

    /* the syntax error messages */
    private static final String TYPESYNTAX = MCMPConstants.TYPESYNTAX;
    private static final String SCONBAD = "SYNTAX: Context without Alias";
    private static final String SBADFLD = "SYNTAX: Invalid field ";
    private static final String SBADFLD1 = " in message";
    private static final String SMISFLD = "SYNTAX: Mandatory field(s) missing in message";

    private final FormParserFactory parserFactory;
    private final MCMPConfig config;
    private final HttpHandler next;
    private final long creationTime = System.currentTimeMillis(); // This should change with each restart
    private final ModCluster modCluster;
    protected final ModClusterContainer container;

    MCMPHandler(MCMPConfig config, ModCluster modCluster, HttpHandler next) {
        this.config = config;
        this.next = next;
        this.modCluster = modCluster;
        this.container = modCluster.getContainer();
        this.parserFactory = FormParserFactory.builder(false).addParser(new FormEncodedDataDefinition().setForceCreation(true)).build();
        UndertowLogger.ROOT_LOGGER.mcmpHandlerCreated();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final HttpString method = exchange.getRequestMethod();
        if(!HANDLED_METHODS.contains(method)) {
            next.handleRequest(exchange);
            return;
        }
        /*
         * Proxy the request that needs to be proxied and process others
         */
        // TODO maybe this should be handled outside here?
        final InetSocketAddress addr = exchange.getConnection().getLocalAddress(InetSocketAddress.class);
        if (!addr.isUnresolved() && addr.getPort() != config.getManagementSocketAddress().getPort() || !Arrays.equals(addr.getAddress().getAddress(), config.getManagementSocketAddress().getAddress().getAddress())) {
            next.handleRequest(exchange);
            return;
        }

        if(exchange.isInIoThread()) {
            //for now just do all the management stuff in a worker, as it uses blocking IO
            exchange.dispatch(this);
            return;
        }

        try {
            handleRequest(method, exchange);
        } catch (Exception e) {
            UndertowLogger.ROOT_LOGGER.failedToProcessManagementReq(e);
            exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
            exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, CONTENT_TYPE);
            final Sender sender = exchange.getResponseSender();
            sender.send("failed to process management request");
        }
    }

    /**
     * Handle a management+ request.
     *
     * @param method   the http method
     * @param exchange the http server exchange
     * @throws Exception
     */
    protected void handleRequest(final HttpString method, HttpServerExchange exchange) throws Exception {
        final RequestData requestData = parseFormData(exchange);
        if (CONFIG.equals(method)) {
            processConfig(exchange, requestData);
        } else if (ENABLE_APP.equals(method)) {
            processCommand(exchange, requestData, MCMPAction.ENABLE);
        } else if (DISABLE_APP.equals(method)) {
            processCommand(exchange, requestData, MCMPAction.DISABLE);
        } else if (STOP_APP.equals(method)) {
            processCommand(exchange, requestData, MCMPAction.STOP);
        } else if (REMOVE_APP.equals(method)) {
            processCommand(exchange, requestData, MCMPAction.REMOVE);
        } else if (STATUS.equals(method)) {
            processStatus(exchange, requestData);
        } else if (INFO.equals(method)) {
            processInfo(exchange);
        } else if (DUMP.equals(method)) {
            processDump(exchange);
        } else if (PING.equals(method)) {
            processPing(exchange, requestData);
        } else {
            next.handleRequest(exchange);
        }
    }

    /**
     * Process the node config.
     *
     * @param exchange the http server exchange
     * @param requestData the request data
     * @throws IOException
     */
    private void processConfig(final HttpServerExchange exchange, final RequestData requestData) throws IOException {

        // Get the node builder
        List<String> hosts = null;
        List<String> contexts = null;
        final Balancer.BalancerBuilder balancer = Balancer.builder();
        final NodeConfig.NodeBuilder node = NodeConfig.builder(modCluster);
        final Iterator<HttpString> i = requestData.iterator();
        while (i.hasNext()) {
            final HttpString name = i.next();
            final String value = requestData.getFirst(name);

            UndertowLogger.ROOT_LOGGER.mcmpKeyValue(name, value);
            if (!checkString(value)) {
                processError(TYPESYNTAX, SBADFLD + name + SBADFLD1, exchange);
                return;
            }

            if (BALANCER.equals(name)) {
                node.setBalancer(value);
                balancer.setName(value);
            } else if (MAXATTEMPTS.equals(name)) {
                balancer.setMaxattempts(Integer.parseInt(value));
            } else if (STICKYSESSION.equals(name)) {
                if ("No".equalsIgnoreCase(value)) {
                    balancer.setStickySession(false);
                }
            } else if (STICKYSESSIONCOOKIE.equals(name)) {
                balancer.setStickySessionCookie(value);
            } else if (STICKYSESSIONPATH.equals(name)) {
                balancer.setStickySessionPath(value);
            } else if (STICKYSESSIONREMOVE.equals(name)) {
                if ("Yes".equalsIgnoreCase(value)) {
                    balancer.setStickySessionRemove(true);
                }
            } else if (STICKYSESSIONFORCE.equals(name)) {
                if ("no".equalsIgnoreCase(value)) {
                    balancer.setStickySessionForce(false);
                }
            } else if (JVMROUTE.equals(name)) {
                node.setJvmRoute(value);
            } else if (DOMAIN.equals(name)) {
                node.setDomain(value);
            } else if (HOST.equals(name)) {
                node.setHostname(value);
            } else if (PORT.equals(name)) {
                node.setPort(Integer.parseInt(value));
            } else if (TYPE.equals(name)) {
                node.setType(value);
            } else if (REVERSED.equals(name)) {
                continue; // ignore
            } else if (FLUSH_PACKET.equals(name)) {
                if ("on".equalsIgnoreCase(value)) {
                    node.setFlushPackets(true);
                } else if ("auto".equalsIgnoreCase(value)) {
                    node.setFlushPackets(true);
                }
            } else if (FLUSH_WAIT.equals(name)) {
                node.setFlushwait(Integer.parseInt(value));
            } else if (MCMPConstants.PING.equals(name)) {
                node.setPing(Integer.parseInt(value));
            } else if (SMAX.equals(name)) {
                node.setSmax(Integer.parseInt(value));
            } else if (TTL.equals(name)) {
                node.setTtl(Integer.parseInt(value));
            } else if (TIMEOUT.equals(name)) {
                node.setTimeout(Integer.parseInt(value));
            } else if (CONTEXT.equals(name)) {
                final String[] context = value.split(",");
                contexts = Arrays.asList(context);
            } else if (ALIAS.equals(name)) {
                final String[] alias = value.split(",");
                hosts = Arrays.asList(alias);
            } else {
                processError(TYPESYNTAX, SBADFLD + name + SBADFLD1, exchange);
                return;
            }
        }

        final NodeConfig config;
        try {
            // Build the config
            config = node.build();
            if (container.addNode(config, balancer, exchange.getIoThread(), exchange.getConnection().getByteBufferPool())) {
                // Apparently this is hard to do in the C part, so maybe we should just remove this
                if (contexts != null && hosts != null) {
                    for (final String context : contexts) {
                        container.enableContext(context, config.getJvmRoute(), hosts);
                    }
                }
                processOK(exchange);
            } else {
                processError(MCMPErrorCode.NODE_STILL_EXISTS, exchange);
            }
        } catch (Exception e) {
            processError(MCMPErrorCode.CANT_UPDATE_NODE, exchange);
        }
    }

    /**
     * Process a mod_cluster mgmt command.
     *
     * @param exchange the http server exchange
     * @param requestData the request data
     * @param action   the mgmt action
     * @throws IOException
     */
    void processCommand(final HttpServerExchange exchange, final RequestData requestData, final MCMPAction action) throws IOException {
        if (exchange.getRequestPath().equals("*") || exchange.getRequestPath().endsWith("/*")) {
            processNodeCommand(exchange, requestData, action);
        } else {
            processAppCommand(exchange, requestData, action);
        }
    }

    /**
     * Process a mgmt command targeting a node.
     *
     * @param exchange the http server exchange
     * @param requestData the request data
     * @param action   the mgmt action
     * @throws IOException
     */
    void processNodeCommand(final HttpServerExchange exchange, final RequestData requestData, final MCMPAction action) throws IOException {
        final String jvmRoute = requestData.getFirst(JVMROUTE);
        if (jvmRoute == null) {
            processError(TYPESYNTAX, SMISFLD, exchange);
            return;
        }
        if (processNodeCommand(jvmRoute, action)) {
            processOK(exchange);
        } else {
            processError(MCMPErrorCode.CANT_UPDATE_NODE, exchange);
        }
    }

    boolean processNodeCommand(final String jvmRoute, final MCMPAction action) throws IOException {
        switch (action) {
            case ENABLE:
                return container.enableNode(jvmRoute);
            case DISABLE:
                return container.disableNode(jvmRoute);
            case STOP:
                return container.stopNode(jvmRoute);
            case REMOVE:
                return container.removeNode(jvmRoute) != null;
        }
        return false;
    }

    /**
     * Process a command targeting an application.
     *
     * @param exchange the http server exchange
     * @param requestData the request data
     * @param action   the mgmt action
     * @return
     * @throws IOException
     */
    void processAppCommand(final HttpServerExchange exchange, final RequestData requestData, final MCMPAction action) throws IOException {

        final String contextPath = requestData.getFirst(CONTEXT);
        final String jvmRoute = requestData.getFirst(JVMROUTE);
        final String aliases = requestData.getFirst(ALIAS);

        if (contextPath == null || jvmRoute == null || aliases == null) {
            processError(TYPESYNTAX, SMISFLD, exchange);
            return;
        }
        final List<String> virtualHosts = Arrays.asList(aliases.split(","));
        if (virtualHosts == null || virtualHosts.isEmpty()) {
            processError(TYPESYNTAX, SCONBAD, exchange);
            return;
        }

        String response = null;
        switch (action) {
            case ENABLE:
                if (!container.enableContext(contextPath, jvmRoute, virtualHosts)) {
                    processError(MCMPErrorCode.CANT_UPDATE_CONTEXT, exchange);
                    return;
                }
                break;
            case DISABLE:
                if (!container.disableContext(contextPath, jvmRoute, virtualHosts)) {
                    processError(MCMPErrorCode.CANT_UPDATE_CONTEXT, exchange);
                    return;
                }
                break;
            case STOP:
                int i = container.stopContext(contextPath, jvmRoute, virtualHosts);
                final StringBuilder builder = new StringBuilder();
                builder.append("Type=STOP-APP-RSP,JvmRoute=").append(jvmRoute);
                builder.append("Alias=").append(aliases);
                builder.append("Context=").append(contextPath);
                builder.append("Requests=").append(i);
                response = builder.toString();
                break;
            case REMOVE:
                if (!container.removeContext(contextPath, jvmRoute, virtualHosts)) {
                    processError(MCMPErrorCode.CANT_UPDATE_CONTEXT, exchange);
                    return;
                }
                break;
            default: {
                processError(TYPESYNTAX, SMISFLD, exchange);
                return;
            }
        }
        if (response != null) {
            sendResponse(exchange, response);
        } else {
            processOK(exchange);
        }
    }

    /**
     * Process the status request.
     *
     * @param exchange the http server exchange
     * @param requestData the request data
     * @throws IOException
     */
    void processStatus(final HttpServerExchange exchange, final RequestData requestData) throws IOException {

        final String jvmRoute = requestData.getFirst(JVMROUTE);
        final String loadValue = requestData.getFirst(LOAD);

        if (loadValue == null || jvmRoute == null) {
            processError(TYPESYNTAX, SMISFLD, exchange);
            return;
        }

        UndertowLogger.ROOT_LOGGER.receivedNodeLoad(jvmRoute, loadValue);
        final int load = Integer.parseInt(loadValue);
        if (load > 0 || load == -2) {

            final Node node = container.getNode(jvmRoute);
            if (node == null) {
                processError(MCMPErrorCode.CANT_READ_NODE, exchange);
                return;
            }

            final NodePingUtil.PingCallback callback = new NodePingUtil.PingCallback() {
                @Override
                public void completed() {
                    final String response = "Type=STATUS-RSP&State=OK&JVMRoute=" + jvmRoute + "&id=" + creationTime;
                    try {
                        if (load > 0) {
                            node.updateLoad(load);
                        }
                        sendResponse(exchange, response);
                    } catch (Exception e) {
                        UndertowLogger.ROOT_LOGGER.failedToSendPingResponse(e);
                    }
                }

                @Override
                public void failed() {
                    final String response = "Type=STATUS-RSP&State=NOTOK&JVMRoute=" + jvmRoute + "&id=" + creationTime;
                    try {
                        node.markInError();
                        sendResponse(exchange, response);
                    } catch (Exception e) {
                        UndertowLogger.ROOT_LOGGER.failedToSendPingResponseDBG(e, node.getJvmRoute(), jvmRoute);
                    }
                }
            };

            // Ping the node
            node.ping(exchange, callback);

        } else if (load == 0) {
            final Node node = container.getNode(jvmRoute);
            if (node != null) {
                node.hotStandby();
                sendResponse(exchange, "Type=STATUS-RSP&State=OK&JVMRoute=" + jvmRoute + "&id=" + creationTime);
            } else {
                processError(MCMPErrorCode.CANT_READ_NODE, exchange);
            }
        } else if (load == -1) {
            // Error, disable node
            final Node node = container.getNode(jvmRoute);
            if (node != null) {
                node.markInError();
                sendResponse(exchange, "Type=STATUS-RSP&State=NOTOK&JVMRoute=" + jvmRoute + "&id=" + creationTime);
            } else {
                processError(MCMPErrorCode.CANT_READ_NODE, exchange);
            }
        } else {
            processError(TYPESYNTAX, SMISFLD, exchange);
        }
    }

    /**
     * Process the ping request.
     *
     * @param exchange the http server exchange
     * @param requestData the request data
     * @throws IOException
     */
    void processPing(final HttpServerExchange exchange, final RequestData requestData) throws IOException {

        final String jvmRoute = requestData.getFirst(JVMROUTE);
        final String scheme = requestData.getFirst(SCHEME);
        final String host = requestData.getFirst(HOST);
        final String port = requestData.getFirst(PORT);

        final String OK = "Type=PING-RSP&State=OK&id=" + creationTime;
        final String NOTOK = "Type=PING-RSP&State=NOTOK&id=" + creationTime;

        if (jvmRoute != null) {
            // ping the corresponding node.
            final Node nodeConfig = container.getNode(jvmRoute);
            if (nodeConfig == null) {
                sendResponse(exchange, NOTOK);
                return;
            }
            final NodePingUtil.PingCallback callback = new NodePingUtil.PingCallback() {
                @Override
                public void completed() {
                    try {
                        sendResponse(exchange, OK);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void failed() {
                    try {
                        nodeConfig.markInError();
                        sendResponse(exchange, NOTOK);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            nodeConfig.ping(exchange, callback);
        } else {
            if (scheme == null && host == null && port == null) {
                sendResponse(exchange, OK);
                return;
            } else {
                if (host == null || port == null) {
                    processError(TYPESYNTAX, SMISFLD, exchange);
                    return;
                }
                // Check whether we can reach the host
                checkHostUp(scheme, host, Integer.parseInt(port), exchange, new NodePingUtil.PingCallback() {
                    @Override
                    public void completed() {
                        sendResponse(exchange, OK);
                    }

                    @Override
                    public void failed() {
                        sendResponse(exchange, NOTOK);
                    }
                });
                return;
            }
        }
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
    protected void processInfo(HttpServerExchange exchange) throws IOException {
        final String data = processInfoString();
        exchange.getResponseHeaders().add(Headers.SERVER, MOD_CLUSTER_EXPOSED_VERSION);
        sendResponse(exchange, data);
    }

    protected String processInfoString() {
        final StringBuilder builder = new StringBuilder();
        final List<Node.VHostMapping> vHosts = new ArrayList<>();
        final List<Context> contexts = new ArrayList<>();
        final Collection<Node> nodes = container.getNodes();
        for (final Node node : nodes) {
            MCMPInfoUtil.printInfo(node, builder);
            vHosts.addAll(node.getVHosts());
            contexts.addAll(node.getContexts());
        }
        for (final Node.VHostMapping vHost : vHosts) {
            MCMPInfoUtil.printInfo(vHost, builder);
        }
        for (final Context context : contexts) {
            MCMPInfoUtil.printInfo(context, builder);
        }
        return builder.toString();
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
    protected void processDump(HttpServerExchange exchange) throws IOException {
        final String data = processDumpString();
        exchange.getResponseHeaders().add(Headers.SERVER, MOD_CLUSTER_EXPOSED_VERSION);
        sendResponse(exchange, data);
    }

    protected String processDumpString() {

        final StringBuilder builder = new StringBuilder();
        final Collection<Balancer> balancers = container.getBalancers();
        for (final Balancer balancer : balancers) {
            MCMPInfoUtil.printDump(balancer, builder);
        }

        final List<Node.VHostMapping> vHosts = new ArrayList<>();
        final List<Context> contexts = new ArrayList<>();
        final Collection<Node> nodes = container.getNodes();
        for (final Node node : nodes) {
            MCMPInfoUtil.printDump(node, builder);
            vHosts.addAll(node.getVHosts());
            contexts.addAll(node.getContexts());
        }
        for (final Node.VHostMapping vHost : vHosts) {
            MCMPInfoUtil.printDump(vHost, builder);
        }
        for (final Context context : contexts) {
            MCMPInfoUtil.printDump(context, builder);
        }
        return builder.toString();
    }

    /**
     * Check whether a host is up.
     *
     * @param scheme      the scheme
     * @param host        the host
     * @param port        the port
     * @param exchange    the http server exchange
     * @param callback    the ping callback
     */
    protected void checkHostUp(final String scheme, final String host, final int port, final HttpServerExchange exchange, final NodePingUtil.PingCallback callback) {

        final XnioSsl xnioSsl = null; // TODO
        final OptionMap options = OptionMap.builder()
                .set(Options.TCP_NODELAY, true)
                .getMap();

        try {
            // http, ajp and maybe more in future
            if ("ajp".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                final URI uri = new URI(scheme, null, host, port, "/", null, null);
                NodePingUtil.pingHttpClient(uri, callback, exchange, container.getClient(), xnioSsl, options);
            } else {
                final InetSocketAddress address = new InetSocketAddress(host, port);
                NodePingUtil.pingHost(address, exchange, callback, options);
            }
        } catch (URISyntaxException e) {
            callback.failed();
        }
    }

    /**
     * Send a simple response string.
     *
     * @param exchange    the http server exchange
     * @param response    the response string
     */
    static void sendResponse(final HttpServerExchange exchange, final String response) {
        exchange.setStatusCode(StatusCodes.OK);
        exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, CONTENT_TYPE);
        final Sender sender = exchange.getResponseSender();
        UndertowLogger.ROOT_LOGGER.mcmpSendingResponse(exchange.getSourceAddress(), exchange.getStatusCode(), exchange.getResponseHeaders(), response);
        sender.send(response);
    }

    /**
     * If the process is OK, then add 200 HTTP status and its "OK" phrase
     *
     * @throws Exception
     */
    static void processOK(HttpServerExchange exchange) throws IOException {
        exchange.setStatusCode(StatusCodes.OK);
        exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, CONTENT_TYPE);
        exchange.endExchange();
    }

    static void processError(MCMPErrorCode errorCode, HttpServerExchange exchange) {
        processError(errorCode.getType(), errorCode.getMessage(), exchange);
    }

    /**
     * Send an error message.
     *
     * @param type         the error type
     * @param errString    the error string
     * @param exchange     the http server exchange
     */
    static void processError(String type, String errString, HttpServerExchange exchange) {
        exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
        exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, CONTENT_TYPE);
        exchange.getResponseHeaders().add(new HttpString("Version"), VERSION_PROTOCOL);
        exchange.getResponseHeaders().add(new HttpString("Type"), type);
        exchange.getResponseHeaders().add(new HttpString("Mess"), errString);
        exchange.endExchange();
        UndertowLogger.ROOT_LOGGER.mcmpProcessingError(type, errString);
    }

    /**
     * Transform the form data into an intermediate request data which can me used
     * by the web manager
     *
     * @param exchange    the http server exchange
     * @return
     * @throws IOException
     */
    RequestData parseFormData(final HttpServerExchange exchange) throws IOException {
        // Read post parameters
        final FormDataParser parser = parserFactory.createParser(exchange);
        final FormData formData = parser.parseBlocking();
        final RequestData data = new RequestData();
        for (String name : formData) {
            final HttpString key = new HttpString(name);
            data.add(key, formData.get(name));
        }
        return data;
    }

    private static void checkStringForSuspiciousCharacters(String data) {
        for(int i = 0; i < data.length(); ++i) {
            char c = data.charAt(i);
            if(c == '>' || c == '<' || c == '\\' || c == '\"' || c == '\n' || c == '\r') {
                throw UndertowMessages.MESSAGES.mcmpMessageRejectedDueToSuspiciousCharacters(data);
            }
        }
    }

    static class RequestData {

        private final Map<HttpString, Deque<String>> values = new LinkedHashMap<>();

        Iterator<HttpString> iterator() {
            return values.keySet().iterator();
        }

        void add(final HttpString name, Deque<FormData.FormValue> values) {
            checkStringForSuspiciousCharacters(name.toString());
            for (final FormData.FormValue value : values) {
                add(name, value);
            }
        }



        void addValues(final HttpString name, Deque<String> value) {
            Deque<String> values = this.values.get(name);
            for(String i : value) {
                checkStringForSuspiciousCharacters(i);
            }
            if (values == null) {
                this.values.put(name, value);
            } else {
                values.addAll(value);
            }
        }

        void add(final HttpString name, final FormData.FormValue value) {
            Deque<String> values = this.values.get(name);
            if (values == null) {
                this.values.put(name, values = new ArrayDeque<>(1));
            }
            String stringVal = value.getValue();
            checkStringForSuspiciousCharacters(stringVal);
            values.add(stringVal);
        }

        String getFirst(HttpString name) {
            final Deque<String> deque = values.get(name);
            return deque == null ? null : deque.peekFirst();
        }

    }

    static boolean checkString(final String value) {
        return value != null && value.length() > 0;
    }

}
