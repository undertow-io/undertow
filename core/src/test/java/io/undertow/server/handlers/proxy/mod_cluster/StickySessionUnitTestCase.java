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

import io.undertow.util.StatusCodes;

import java.io.IOException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test failover with force sticky session == true; (which is the default)
 *
 * @author Emanuel Muckenhuber
 */
public class StickySessionUnitTestCase extends AbstractModClusterTestBase {

    static NodeTestConfig server1;
    static NodeTestConfig server2;

    static {
        server1 = NodeTestConfig.builder()
                .setJvmRoute("server1")
                .setType(getType())
                .setHostname("localhost")
                .setPort(port + 1);

        server2 = NodeTestConfig.builder()
                .setJvmRoute("server2")
                .setType(getType())
                .setHostname("localhost")
                .setPort(port + 2);
    }

    @BeforeClass
    public static void setup() {
        startServers(server1, server2);
    }

    @AfterClass
    public static void tearDown() {
        stopServers();
    }

    @Test
    public void testDisabledApp() throws IOException {
        //
        registerNodes(true, server1, server2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", StatusCodes.OK);
        final String jvmRoute;
        if (response.startsWith(server1.getJvmRoute())) {
            jvmRoute = server1.getJvmRoute();
        } else {
            jvmRoute = server2.getJvmRoute();
        }
        modClusterClient.disableApp(jvmRoute, SESSION);

        for (int i = 0; i < 20 ; i++) {
            checkGet("/session", StatusCodes.OK, jvmRoute).startsWith(jvmRoute);
        }
    }

    @Test
    public void testNoDomainRemovedContext() throws IOException {
        // If no domain is configured apps cannot failover
        registerNodes(true, server1, server2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", StatusCodes.OK);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.removeApp(server1.getJvmRoute(), SESSION);
        } else {
            modClusterClient.removeApp(server2.getJvmRoute(), SESSION);
        }

        checkGet("/session", StatusCodes.SERVICE_UNAVAILABLE);
    }

    @Test
    public void testNoDomainStoppedContext() throws IOException {
        // If no domain is configured apps cannot failover
        registerNodes(true, server1, server2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", StatusCodes.OK);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.stopApp(server1.getJvmRoute(), SESSION);
        } else {
            modClusterClient.stopApp(server2.getJvmRoute(), SESSION);
        }

        checkGet("/session", StatusCodes.SERVICE_UNAVAILABLE);
    }

    @Test
    public void testNoDomainNodeInError() throws IOException {
        // If no domain is configured apps cannot failover
        registerNodes(true, server1, server2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", StatusCodes.OK);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.updateLoad(server1.getJvmRoute(), -1);
        } else {
            modClusterClient.updateLoad(server2.getJvmRoute(), -1);
        }

        checkGet("/session", StatusCodes.SERVICE_UNAVAILABLE);
    }

    @Test
    public void testDifferentDomainRemovedContext() throws IOException {
        // Test failover in a different domain
        final NodeTestConfig config1 = server1.clone().setDomain("domain1");
        final NodeTestConfig config2 = server2.clone().setDomain("domain2");

        registerNodes(true, config1, config2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", StatusCodes.OK);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.removeApp(server1.getJvmRoute(), SESSION);
        } else {
            modClusterClient.removeApp(server2.getJvmRoute(), SESSION);
        }

        checkGet("/session", StatusCodes.SERVICE_UNAVAILABLE);
    }

    @Test
    public void testDifferentDomainStoppedContext() throws IOException {
        // Test failover in a different domain
        final NodeTestConfig config1 = server1.clone().setDomain("domain1");
        final NodeTestConfig config2 = server2.clone().setDomain("domain2");

        registerNodes(true, config1, config2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", StatusCodes.OK);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.stopApp(server1.getJvmRoute(), SESSION);
        } else {
            modClusterClient.stopApp(server2.getJvmRoute(), SESSION);
        }

        checkGet("/session", StatusCodes.SERVICE_UNAVAILABLE);
    }

    @Test
    public void testDifferentDomainNodeInError() throws IOException {
        // Test failover in a different domain
        final NodeTestConfig config1 = server1.clone().setDomain("domain1");
        final NodeTestConfig config2 = server2.clone().setDomain("domain2");

        registerNodes(true, config1, config2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", StatusCodes.OK);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.updateLoad(server1.getJvmRoute(), -1);
        } else {
            modClusterClient.updateLoad(server2.getJvmRoute(), -1);
        }

        checkGet("/session", StatusCodes.SERVICE_UNAVAILABLE);
    }

    @Test
    public void testDomainStoppedContext() throws IOException {
        // Test failover in the same domain
        final NodeTestConfig config1 = server1.clone().setDomain("domain1");
        final NodeTestConfig config2 = server2.clone().setDomain("domain1");

        registerNodes(true, config1, config2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", StatusCodes.OK);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.stopApp(server1.getJvmRoute(), SESSION);
        } else {
            modClusterClient.stopApp(server2.getJvmRoute(), SESSION);
        }

        checkGet("/session", StatusCodes.OK);
    }

    @Test
    public void testDomainRemovedContext() throws IOException {
        // Test failover in the same domain
        final NodeTestConfig config1 = server1.clone().setDomain("domain1");
        final NodeTestConfig config2 = server2.clone().setDomain("domain1");

        registerNodes(true, config1, config2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", StatusCodes.OK);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.removeApp(server1.getJvmRoute(), SESSION);
        } else {
            modClusterClient.removeApp(server2.getJvmRoute(), SESSION);
        }

        checkGet("/session", StatusCodes.OK);
    }

    @Test
    public void testDomainNodeInError() throws IOException {
        // Test failover in the same domain
        final NodeTestConfig config1 = server1.clone().setDomain("domain1");
        final NodeTestConfig config2 = server2.clone().setDomain("domain1");

        registerNodes(true, config1, config2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", StatusCodes.OK);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.updateLoad(server1.getJvmRoute(), -1);
        } else {
            modClusterClient.updateLoad(server2.getJvmRoute(), -1);
        }

        checkGet("/session", StatusCodes.OK);
    }

}
