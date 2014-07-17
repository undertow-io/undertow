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

import static io.undertow.Handlers.jvmRoute;
import static io.undertow.Handlers.path;
import static io.undertow.testutils.DefaultServer.getClientSSLContext;
import static io.undertow.testutils.DefaultServer.getHostAddress;
import static io.undertow.testutils.DefaultServer.getHostPort;
import static io.undertow.testutils.DefaultServer.isProxy;
import static io.undertow.testutils.DefaultServer.isSpdy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.undertow.Undertow;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.ssl.JsseXnioSsl;
import org.xnio.ssl.XnioSsl;

/**
 * @author Emanuel Muckenhuber
 */
@RunWith(DefaultServer.class)
public abstract class AbstractModClusterTestBase {

    protected static final MCMPTestClient.App NAME = new MCMPTestClient.App("/name", "localhost");
    protected static final MCMPTestClient.App SESSION = new MCMPTestClient.App("/session", "localhost");

    protected static Undertow[] servers;
    protected static DefaultHttpClient httpClient;
    protected static MCMPTestClient modClusterClient;


    protected static final int port;
    protected static final String hostName;

    private static ModCluster modCluster;
    private static final Xnio xnio;
    private static final XnioSsl xnioSsl;
    private static final UndertowClient undertowClient = UndertowClient.getInstance();
    private static final String COUNT = "count";

    static {
        port = getHostPort("default");
        hostName = getHostAddress("default");

        xnio = Xnio.getInstance("nio", AbstractModClusterTestBase.class.getClassLoader());
        xnioSsl = new JsseXnioSsl(xnio, OptionMap.EMPTY, getClientSSLContext());
    }

    protected List<NodeTestConfig> nodes;

    @BeforeClass
    public static void setupModCluster() {
        modCluster = ModCluster.builder(undertowClient, xnioSsl).build();

        final int serverPort = isProxy() || isSpdy() ? getHostPort("default") + 1111 : getHostPort("default");
        final HttpHandler proxy = modCluster.getProxyHandler();
        final HttpHandler mcmp = MCMPConfig.webBuilder()
                .setManagementHost(getHostAddress("default"))
                .setManagementPort(serverPort)
                .create(modCluster, ResponseCodeHandler.HANDLE_404);

        DefaultServer.setRootHandler(path(proxy).addPrefixPath("manager", mcmp));
        modCluster.start();

        httpClient = new DefaultHttpClient();
        modClusterClient = new MCMPTestClient(httpClient, DefaultServer.getDefaultServerURL() + "/manager");
    }

    @AfterClass
    public static void stopModCluster() {
        if (servers != null) {
            stopServers();
        }
        modCluster.stop();
        modCluster = null;
        httpClient.getConnectionManager().shutdown();
    }

    /**
     * Register the nodes. Nodes registered using this method are automatically getting unregistered after the test.
     *
     * @param updateLoad    update the load when registering, which will enable them
     * @param configs       the configs
     * @throws IOException
     */
    protected void registerNodes(boolean updateLoad, NodeTestConfig... configs) throws IOException {
        if (nodes == null) {
            nodes = new ArrayList<>();
        }
        modClusterClient.info();
        for (final NodeTestConfig config : configs) {
            nodes.add(config);
            modClusterClient.registerNode(config);
        }
        if (updateLoad) {
            updateLoad(configs);
        }
    }

    protected void updateLoad(final NodeTestConfig... configs) throws IOException {
        for (final NodeTestConfig config : configs) {
            modClusterClient.updateLoad(config.getJvmRoute(), 100);
        }
    }

    @After
    public void unregisterNodes() {
        try {
            modClusterClient.info();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (nodes != null) {
            for (final NodeTestConfig config : nodes) {
                try {
                    modClusterClient.removeNode(config.getJvmRoute());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        nodes = null;
        // Clear all cookies after the test
        httpClient.getCookieStore().clear();
    }

    static void stopServers() {
        if (servers != null) {
            for (final Undertow server : servers) {
                try {
                    server.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            servers = null;
        }
    }

    static void startServers(final NodeTestConfig... configs) {
        final int l = configs.length;
        servers = new Undertow[l];
        final SessionCookieConfig session = new SessionCookieConfig();
        for (int i = 0; i < l; i++) {
            servers[i] = createNode(configs[i], session);
            servers[i].start();
        }
    }

    static String checkGet(final String context, int statusCode) throws IOException {
        return checkGet(context, statusCode, null);
    }

    static String checkGet(final String context, int statusCode, String route) throws IOException {
        final HttpGet get = get(context);
        final HttpResponse result = httpClient.execute(get);
        final String response = HttpClientUtils.readResponse(result);
        Assert.assertEquals(statusCode, result.getStatusLine().getStatusCode());
        if (route != null) {
            Assert.assertEquals(route, getSessionRoute());
        }
        return response;
    }

    static HttpGet get(final String context) {
        return get(context, "localhost");
    }

    static HttpGet get(final String context, final String host) {
        final HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + context);
        get.addHeader(new BasicHeader("Host", host));
        return get;
    }

    protected static final class SessionTestHandler implements HttpHandler {

        private final String serverName;
        private final SessionCookieConfig sessionConfig;

        public SessionTestHandler(String serverName, SessionCookieConfig sessionConfig) {
            this.serverName = serverName;
            this.sessionConfig = sessionConfig;
        }

        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
            final SessionManager manager = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
            Session session = manager.getSession(exchange, sessionConfig);
            if (session == null) {
                session = manager.createSession(exchange, sessionConfig);
                session.setAttribute(COUNT, 0);
            }
            Integer count = (Integer) session.getAttribute(COUNT);
            session.setAttribute(COUNT, count + 1);
            exchange.getResponseSender().send(serverName + ":" + count);
        }
    }

    protected static final class StringSendHandler implements HttpHandler {

        private final String serverName;

        protected StringSendHandler(String serverName) {
            this.serverName = serverName;
        }

        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
            exchange.getResponseSender().send(serverName);
        }
    }

    static Undertow createNode(final NodeTestConfig config, final SessionCookieConfig sessionConfig) {
        final Undertow.Builder builder = Undertow.builder();
        final String type = config.getType();
        switch (type) {
            case "ajp":
                builder.addAjpListener(config.getPort(), config.getHostname());
                break;
            case "http":
                builder.addHttpListener(config.getPort(), config.getHostname());
                break;
            case "https":
                builder.addHttpsListener(config.getPort(), config.getHostname(), DefaultServer.getServerSslContext());
                break;
        }
        builder.setSocketOption(Options.REUSE_ADDRESSES, true)
               .setHandler(jvmRoute("JSESSIONID", config.getJvmRoute(), path()
                       .addPrefixPath("/name", new StringSendHandler(config.getJvmRoute()))
                       .addPrefixPath("/session", new SessionAttachmentHandler(new SessionTestHandler(config.getJvmRoute(), sessionConfig), new InMemorySessionManager(""), sessionConfig))));
        return builder.build();
    }

    static String getJVMRoute(final String sessionId) {
        int index = sessionId.indexOf('.');
        if (index == -1) {
            return null;
        }
        String route = sessionId.substring(index + 1);
        index = route.indexOf('.');
        if (index != -1) {
            route = route.substring(0, index);
        }
        return route;
    }

    static String getSessionRoute() {
        for (Cookie cookie : httpClient.getCookieStore().getCookies()) {
            if ("JSESSIONID".equals(cookie.getName())) {
                return getJVMRoute(cookie.getValue());
            }
        }
        return null;
    }




}
