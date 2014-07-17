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

import io.undertow.testutils.HttpClientUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Emanuel Muckenhuber
 */
public class BasicMCMPUnitTestCase extends AbstractModClusterTestBase {

    static NodeTestConfig server1;
    static NodeTestConfig server2;

    static {
        server1 = NodeTestConfig.builder()
                .setJvmRoute("s1")
                .setType("http")
                .setHostname("localhost")
                .setPort(port + 1);

        server2 = NodeTestConfig.builder()
                .setJvmRoute("s2")
                .setType("ajp")
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
    public void testBasic() throws IOException {
        registerNodes(false, server1, server2);

        modClusterClient.updateLoad("s1", 100);
        modClusterClient.updateLoad("s2", 1);

        modClusterClient.enableApp("s1", "/name", "localhost", "localhost:7777");
        modClusterClient.enableApp("s1", "/session", "localhost", "localhost:7777");
        modClusterClient.enableApp("s2", "/name", "localhost", "localhost:7777");
        modClusterClient.enableApp("s2", "/session", "localhost", "localhost:7777");

        // Ping
        modClusterClient.updateLoad("s1", -2);
        modClusterClient.updateLoad("s2", -2);

        for (int i = 0; i < 10; i++) {
            HttpGet get = get("/name");
            HttpResponse result = httpClient.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        }

        for (int i = 0; i < 10; i++) {
            HttpGet get = get("/session");
            HttpResponse result = httpClient.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        }
    }

    @Test
    public void testAppCommand() throws IOException {
        checkGet("/name", 404);
        checkGet("/session", 404);

        registerNodes(false, server1, server2);

        checkGet("/name", 404);
        checkGet("/session", 404);

        modClusterClient.enableApp("s1", "/name", "localhost", "localhost:7777");
        modClusterClient.enableApp("s1", "/session", "localhost", "localhost:7777");
        modClusterClient.enableApp("s2", "/name", "localhost", "localhost:7777");
        modClusterClient.enableApp("s2", "/session", "localhost", "localhost:7777");

        checkGet("/name", 503);
        checkGet("/session", 503);

        modClusterClient.updateLoad("s1", 100);
        modClusterClient.updateLoad("s2", 1);

        checkGet("/name", 200);
        checkGet("/session", 200);

    }

    @Test
    public void testErrorState() throws IOException {

        registerNodes(false, server1);

        modClusterClient.enableApp("s1", "/name", "localhost", "localhost:7777");
        checkGet("/name", 503);

        modClusterClient.updateLoad("s1", 1);
        checkGet("/name", 200);

        modClusterClient.updateLoad("s1", -1);
        checkGet("/name", 503);

        modClusterClient.updateLoad("s1", -2);
        checkGet("/name", 503);
    }

    @Test
    public void testPing() throws IOException {

        String response = modClusterClient.ping(null, "localhost", port + 1);
        Assert.assertFalse(response.contains("NOTOK"));

        response = modClusterClient.ping("http", "localhost", port + 1);
        Assert.assertFalse(response.contains("NOTOK"));

        response = modClusterClient.ping("ajp", "localhost", port + 2);
        Assert.assertFalse(response.contains("NOTOK"));

        response = modClusterClient.ping(null, "localhost", 0);
        Assert.assertTrue(response.contains("NOTOK"));

        response = modClusterClient.ping("ajp", "localhost", 0);
        Assert.assertTrue(response.contains("NOTOK"));

        response = modClusterClient.ping("http", "localhost", 0);
        Assert.assertTrue(response.contains("NOTOK"));

    }

}
