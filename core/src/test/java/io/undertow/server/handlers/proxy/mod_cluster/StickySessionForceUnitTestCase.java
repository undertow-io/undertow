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

import java.io.IOException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test sticky session force == false; behavior
 *
 * @author Emanuel Muckenhuber
 */
public class StickySessionForceUnitTestCase extends AbstractModClusterTestBase {

    static NodeTestConfig server1;
    static NodeTestConfig server2;

    static {
        server1 = NodeTestConfig.builder()
                .setStickySessionForce(false) // Force = false
                .setJvmRoute("server1")
                .setType("ajp")
                .setHostname("localhost")
                .setPort(port + 1);

        server2 = NodeTestConfig.builder()
                .setStickySessionForce(false) // Force = false
                .setJvmRoute("server2")
                .setType("http")
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
    public void testNoDomainRemovedContext() throws IOException {
        // If no domain is configured apps cannot failover
        registerNodes(true, server1, server2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", 200);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.removeApp(server1.getJvmRoute(), SESSION);
        } else {
            modClusterClient.removeApp(server2.getJvmRoute(), SESSION);
        }

        checkGet("/session", 200);
    }

    @Test
    public void testNoDomainStoppedContext() throws IOException {
        // If no domain is configured apps cannot failover
        registerNodes(true, server1, server2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", 200);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.stopApp(server1.getJvmRoute(), SESSION);
        } else {
            modClusterClient.stopApp(server2.getJvmRoute(), SESSION);
        }

        checkGet("/session", 200);
    }

    @Test
    public void testNoDomainNodeInError() throws IOException {
        // If no domain is configured apps cannot failover
        registerNodes(true, server1, server2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", 200);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.updateLoad(server1.getJvmRoute(), -1);
        } else {
            modClusterClient.updateLoad(server2.getJvmRoute(), -1);
        }

        checkGet("/session", 200);
    }

    @Test
    public void testDifferentDomainRemovedContext() throws IOException {
        // Test failover in a different domain
        final NodeTestConfig config1 = server1.clone().setDomain("domain1");
        final NodeTestConfig config2 = server2.clone().setDomain("domain2");

        registerNodes(true, config1, config2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", 200);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.removeApp(server1.getJvmRoute(), SESSION);
        } else {
            modClusterClient.removeApp(server2.getJvmRoute(), SESSION);
        }

        checkGet("/session", 200);
    }

    @Test
    public void testDifferentDomainStoppedContext() throws IOException {
        // Test failover in a different domain
        final NodeTestConfig config1 = server1.clone().setDomain("domain1");
        final NodeTestConfig config2 = server2.clone().setDomain("domain2");

        registerNodes(true, config1, config2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", 200);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.stopApp(server1.getJvmRoute(), SESSION);
        } else {
            modClusterClient.stopApp(server2.getJvmRoute(), SESSION);
        }

        checkGet("/session", 200);
    }

    @Test
    public void testDifferentDomainNodeInError() throws IOException {
        // Test failover in a different domain
        final NodeTestConfig config1 = server1.clone().setDomain("domain1");
        final NodeTestConfig config2 = server2.clone().setDomain("domain2");

        registerNodes(true, config1, config2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", 200);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.updateLoad(server1.getJvmRoute(), -1);
        } else {
            modClusterClient.updateLoad(server2.getJvmRoute(), -1);
        }

        checkGet("/session", 200);
    }

    @Test
    public void testDomainStoppedContext() throws IOException {
        // Test failover in the same domain
        final NodeTestConfig config1 = server1.clone().setDomain("domain1");
        final NodeTestConfig config2 = server2.clone().setDomain("domain1");

        registerNodes(true, config1, config2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", 200);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.stopApp(server1.getJvmRoute(), SESSION);
        } else {
            modClusterClient.stopApp(server2.getJvmRoute(), SESSION);
        }

        checkGet("/session", 200);
    }

    @Test
    public void testDomainRemovedContext() throws IOException {
        // Test failover in the same domain
        final NodeTestConfig config1 = server1.clone().setDomain("domain1");
        final NodeTestConfig config2 = server2.clone().setDomain("domain1");

        registerNodes(true, config1, config2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", 200);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.removeApp(server1.getJvmRoute(), SESSION);
        } else {
            modClusterClient.removeApp(server2.getJvmRoute(), SESSION);
        }

        checkGet("/session", 200);
    }

    @Test
    public void testDomainNodeInError() throws IOException {
        // Test failover in the same domain
        final NodeTestConfig config1 = server1.clone().setDomain("domain1");
        final NodeTestConfig config2 = server2.clone().setDomain("domain1");

        registerNodes(true, config1, config2);

        modClusterClient.enableApp(server1.getJvmRoute(), SESSION);
        modClusterClient.enableApp(server2.getJvmRoute(), SESSION);

        final String response = checkGet("/session", 200);
        if (response.startsWith(server1.getJvmRoute())) {
            modClusterClient.updateLoad(server1.getJvmRoute(), -1);
        } else {
            modClusterClient.updateLoad(server2.getJvmRoute(), -1);
        }

        checkGet("/session", 200);
    }

}
