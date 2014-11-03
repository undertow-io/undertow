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

import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Assert;

/**
 * Basic mod_cluster management client. This can be used to simulate management requests to the mod_cluster manager.
 *
 * @author Emanuel Muckenhuber
 */
public class MCMPTestClient implements Closeable {

    public static final String CONFIG = new String("CONFIG");
    public static final String ENABLE_APP = new String("ENABLE-APP");
    public static final String DISABLE_APP = new String("DISABLE-APP");
    public static final String STOP_APP = new String("STOP-APP");
    public static final String REMOVE_APP = new String("REMOVE-APP");
    public static final String STATUS = new String("STATUS");
    public static final String DUMP = new String("DUMP");
    public static final String INFO = new String("INFO");
    public static final String PING = new String("PING");
    public static final String GET = new String("GET");

    private static final String[] YES_NO = new String[] { "Yes", "No" };

    private final HttpClient client;
    private final String manager;
    private final String command;

    public MCMPTestClient(HttpClient client, String manager) {
        this.client = client;
        this.manager = manager;
        this.command = manager + "/*";
    }

    public String info() throws IOException {
        final Request request = new Request(manager, INFO);
        final HttpResponse result = client.execute(request);
        return assertResponse(result);
    }

    public String registerNode(final NodeTestConfig config) throws IOException {
        final Request request = new Request(manager, CONFIG);

        final List<NameValuePair> pairs = new ArrayList<NameValuePair>();

        addIfNotNull(pairs, MCMPConstants.BALANCER_STRING, config.getBalancerName());
        addIfNotNull(pairs, MCMPConstants.STICKYSESSIONFORCE_STRING, config.getStickySessionForce(), YES_NO);
        addIfNotNull(pairs, MCMPConstants.STICKYSESSIONCOOKIE_STRING, config.getStickySessionCookie());

        addIfNotNull(pairs, MCMPConstants.JVMROUTE_STRING, config.getJvmRoute());
        addIfNotNull(pairs, MCMPConstants.DOMAIN_STRING, config.getDomain());
        addIfNotNull(pairs, MCMPConstants.TYPE_STRING, config.getType());
        addIfNotNull(pairs, MCMPConstants.HOST_STRING, config.getHostname());
        addIfNotNull(pairs, MCMPConstants.PORT_STRING, config.getPort());

        request.setEntity(createEntity(pairs));

        final HttpResponse result = client.execute(request);
        return assertResponse(result);
    }

    static void addIfNotNull(final List<NameValuePair> pairs, final String key, final Boolean value, String[] inconsistentNames) {
        if (value != null) {
            pairs.add(new BasicNameValuePair(key, value ? inconsistentNames[0] : inconsistentNames[1]));
        }
    }

    static void addIfNotNull(final List<NameValuePair> pairs, final String key, final Integer value) {
        if (value != null) {
            pairs.add(new BasicNameValuePair(key, value.toString()));
        }
    }

    static void addIfNotNull(final List<NameValuePair> pairs, final String key, final String value) {
        if (value != null) {
            pairs.add(new BasicNameValuePair(key, value));
        }
    }

    public String updateLoad(final String jvmRoute, int load) throws IOException {
        final Request request = new Request(manager, STATUS);
        request.setEntity(createEntity(new BasicNameValuePair("JVMRoute", jvmRoute), new BasicNameValuePair("Load", "" + load)));
        final HttpResponse result = client.execute(request);
        return assertResponse(result);

    }

    public String removeNode(String jvmRoute) throws IOException {
        final Request request = new Request(command, REMOVE_APP);
        request.setEntity(createEntity(new BasicNameValuePair("JVMRoute", jvmRoute)));
        final HttpResponse response = client.execute(request);
        return assertResponse(response);
    }

    public String enableApp(String jvmRoute, App app) throws IOException {
        return enableApp(jvmRoute, app.getContext(), app.getHosts());
    }

    public String enableApp(String jvmRoute, String webApp, String... hosts) throws IOException {
        return executeAppCmd(ENABLE_APP, jvmRoute, webApp, hosts);
    }

    public String disableApp(String jvmRoute, App app) throws IOException {
       return disableApp(jvmRoute, app.getContext(), app.getHosts());
    }

    public String disableApp(String jvmRoute, String webApp, String... hosts) throws IOException {
        return executeAppCmd(DISABLE_APP, jvmRoute, webApp, hosts);
    }

    public String stopApp(String jvmRoute, App app) throws IOException {
        return stopApp(jvmRoute, app.getContext(), app.getHosts());
    }

    public String stopApp(String jvmRoute, String webApp, String... hosts) throws IOException {
        return executeAppCmd(STOP_APP, jvmRoute, webApp, hosts);
    }

    public String removeApp(String jvmRoute, App app) throws IOException {
        return removeApp(jvmRoute, app.getContext(), app.getHosts());
    }

    public String removeApp(String jvmRoute, String webApp, String... hosts) throws IOException {
        return executeAppCmd(REMOVE_APP, jvmRoute, webApp, hosts);
    }

    public String ping(final String scheme, final String hostname, final int port) throws IOException {
        final Request request = new Request(manager, PING);
        final List<NameValuePair> pairs = new ArrayList<>();
        addIfNotNull(pairs, MCMPConstants.SCHEME_STRING, scheme);
        addIfNotNull(pairs, MCMPConstants.HOST_STRING, hostname);
        addIfNotNull(pairs, MCMPConstants.PORT_STRING, port);
        request.setEntity(createEntity(pairs));
        final HttpResponse response = client.execute(request);
        return HttpClientUtils.readResponse(response);
    }

    String executeAppCmd(final String command, final String jvmRoute, String webApp, String... hosts) throws IOException {
        final Request request = new Request(manager, command);
        request.setEntity(createEntity(new BasicNameValuePair("JVMRoute", jvmRoute), new BasicNameValuePair("context", webApp), new BasicNameValuePair("Alias", asString(Arrays.asList(hosts)))));
        final HttpResponse result = client.execute(request);
        return assertResponse(result);
    }

    @Override
    public void close() throws IOException {
        client.getConnectionManager().shutdown();
    }

    static String assertResponse(final HttpResponse result) throws IOException {
        final String response = HttpClientUtils.readResponse(result);
        Assert.assertEquals(response, StatusCodes.OK, result.getStatusLine().getStatusCode());
        return response;
    }

    static HttpEntity createEntity(final NameValuePair... pairs) throws UnsupportedEncodingException {
        return createEntity(Arrays.asList(pairs));
    }

    static HttpEntity createEntity(final List<NameValuePair> pairs) throws UnsupportedEncodingException {
        return new UrlEncodedFormEntity(pairs, StandardCharsets.US_ASCII);
    }

    static class Request extends HttpPost {

        private final String name;
        Request(String uri, String name) {
            this(URI.create(uri), name);
        }
        Request(URI uri, String name) {
            super(uri);
            this.name = name;
        }

        @Override
        public String getMethod() {
            return name;
        }
    }

    String asString(List<String> names) {
        final StringBuilder builder = new StringBuilder();
        final Iterator<String> i = names.iterator();
        while (i.hasNext()) {
            builder.append(i.next());
            if (i.hasNext()) {
                builder.append(",");
            }
        }
        return builder.toString();
    }

    static class App {

        private final String context;
        private final String[] hosts;

        App(String context, String... hosts) {
            this.context = context;
            this.hosts = hosts;
        }

        public String getContext() {
            return context;
        }

        public String[] getHosts() {
            return hosts;
        }
    }

}
