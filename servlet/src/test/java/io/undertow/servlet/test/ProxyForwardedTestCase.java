/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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
package io.undertow.servlet.test;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.ForwardedHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.constant.GenericServletConstants;
import io.undertow.servlet.test.util.ProxyPeerXForwardedHandlerServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Moulali Shikalwadi
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
public class ProxyForwardedTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("servlet", ProxyPeerXForwardedHandlerServlet.class)
                .addMapping("/forwardedHandler");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addServlet(s);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        HttpHandler startHandler = manager.start();
        startHandler = new ForwardedHandler(startHandler, false);
        root.addPrefixPath(builder.getContextPath(), startHandler);

        DefaultServer.setRootHandler(root);
    }


    @Test
    public void testForwardedHandler() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {

            HttpGet getForwardedHandler = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/forwardedHandler");
            getForwardedHandler.addHeader(Headers.FORWARDED_STRING, "for=192.0.2.43");
            getForwardedHandler.addHeader(Headers.FORWARDED_STRING, "by=203.0.113.60");
            getForwardedHandler.addHeader(Headers.FORWARDED_STRING, "proto=http");
            getForwardedHandler.addHeader(Headers.FORWARDED_STRING, "host=192.0.2.10:8888");
            HttpResponse result = client.execute(getForwardedHandler);
            HttpEntity entity = result.getEntity();
            String results = EntityUtils.toString(entity);
            Map<String, String> map = convertWithStream(results);
            InetSocketAddress serverAddress = DefaultServer.getDefaultServerAddress();

            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(serverAddress.getAddress().getHostAddress(), map.get(GenericServletConstants.LOCAL_ADDR));
            Assert.assertEquals(serverAddress.getAddress().getHostName(), map.get(GenericServletConstants.LOCAL_NAME));
            Assert.assertEquals(serverAddress.getPort(), Integer.parseInt(map.get(GenericServletConstants.LOCAL_PORT)));
            Assert.assertEquals("192.0.2.10", map.get(GenericServletConstants.SERVER_NAME));
            Assert.assertEquals("8888", map.get(GenericServletConstants.SERVER_PORT));
            Assert.assertEquals("192.0.2.43", map.get(GenericServletConstants.REMOTE_ADDR));
            Assert.assertEquals("0", map.get(GenericServletConstants.REMOTE_PORT));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private Map<String, String> convertWithStream(String mapAsString) {
        Map<String, String> map = new HashMap<String, String>();
        if (mapAsString != null) {
            mapAsString = mapAsString.substring(1, mapAsString.length() - 1);
            map = Arrays.stream(mapAsString.split(","))
                    .map(entry -> entry.split("="))
                    .collect(Collectors.toMap(entry -> entry[0].trim(), entry -> entry[1].trim()));
        }
        return map;
    }
}
