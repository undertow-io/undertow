/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

package tmp.texugo.test.handlers.path;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import tmp.texugo.server.HttpCompletionHandler;
import tmp.texugo.server.HttpHandler;
import tmp.texugo.server.HttpServerExchange;
import tmp.texugo.server.handlers.PathHandler;
import tmp.texugo.test.util.DefaultServer;
import tmp.texugo.test.util.HttpClientUtils;

import java.io.IOException;

/**
 * Tests that the path handler works as expected
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class PathTestCase {

    private static final String HEADER = "selected";
    public static final String MATCHED = "matched";
    public static final String PATH = "path";

    @Test
    public void testBasicPathHanding() throws IOException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            final PathHandler handler = new PathHandler();
            handler.getPaths().put("/a", new RemainingPathHandler("/a"));
            handler.getPaths().put("/aa", new RemainingPathHandler("/aa"));

            final PathHandler sub = new PathHandler();

            handler.getPaths().put("/path", sub);
            sub.getPaths().put("/subpath", new RemainingPathHandler("/subpath"));
            sub.setDefaultHandler(new RemainingPathHandler("/path"));

            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/notamatchingpath");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(404, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/");
            result = client.execute(get);
            Assert.assertEquals(404, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            runPathTest(client, "/path", "/path", "");
            runPathTest(client, "/path/a", "/path", "/a");
            runPathTest(client, "/path/subpath", "/subpath", "");
            runPathTest(client, "/path/subpath/", "/subpath", "/");
            runPathTest(client, "/path/subpath/foo", "/subpath", "/foo");
            runPathTest(client, "/a", "/a", "");
            runPathTest(client, "/aa", "/aa", "");



        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void runPathTest(DefaultHttpClient client, String path, String expectedMatch, String expectedRemaining) throws IOException {
        HttpResponse result;HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress() + path);
        result = client.execute(get);
        Assert.assertEquals(200, result.getStatusLine().getStatusCode());
        Header[] header = result.getHeaders(MATCHED);
        Assert.assertEquals(expectedMatch, header[0].getValue());
        header = result.getHeaders(PATH);
        Assert.assertEquals(expectedRemaining, header[0].getValue());
        HttpClientUtils.readResponse(result);
    }

    private static class RemainingPathHandler implements HttpHandler {

        private final String matched;

        private RemainingPathHandler(String matched) {
            this.matched = matched;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange, HttpCompletionHandler completionHandler) {
            exchange.getResponseHeaders().add(MATCHED, matched);
            exchange.getResponseHeaders().add(PATH, exchange.getRelativePath());
            completionHandler.handleComplete();
        }
    }

}
