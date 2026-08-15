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
public class RequestTimeoutUnitTestCase extends AbstractModClusterTestBase {

    static NodeTestConfig server1;
    static NodeTestConfig server2;

    static {
        server1 = NodeTestConfig.builder()
                .setJvmRoute("server1")
                .setType(getType())
                .setHostname("localhost")
                .setPort(port + 1)
                .setTimeout(1);

        server2 = NodeTestConfig.builder()
                .setJvmRoute("server2")
                .setType(getType())
                .setHostname("localhost")
                .setPort(port + 2)
                .setTimeout(2);
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
    public void testTimeoutHit() throws IOException {
        // test against server 1 and its 1s node timeout should be reached
        registerNodes(true, server1);

        modClusterClient.enableApp(server1.getJvmRoute(), SLOW);

        final String response = checkGet("/slow", StatusCodes.GATEWAY_TIME_OUT);
        unregisterNodes();
    }

    @Test
    public void testTimeoutNotHit() throws IOException {
        // test against server 2 and its 2s node timeout should NOT be reached
        registerNodes(true, server2);

        modClusterClient.enableApp(server2.getJvmRoute(), SLOW);

        final String response = checkGet("/slow", StatusCodes.OK);
        unregisterNodes();
    }
}
