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

package io.undertow.server.handlers.file;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import io.undertow.server.handlers.CanonicalPathHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Tomaz Cerar
 */
@RunWith(DefaultServer.class)
public class FileHandlerIndexTestCase {


    @Test
    public void testWelcomeFile() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        File rootPath = new File(getClass().getResource("page.html").toURI()).getParentFile();
        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler()
                                    .setResourceManager(new FileResourceManager(rootPath, 10485760))
                                    .setDirectoryListingEnabled(true)
                                    .addWelcomeFiles("page.html"))));

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Header[] headers = result.getHeaders("Content-Type");
            Assert.assertEquals("text/html", headers[0].getValue());
            Assert.assertTrue(response, response.contains("A web page"));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testDirectoryIndex() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        File rootPath = new File(getClass().getResource("page.html").toURI()).getParentFile();
        try {
            DefaultServer.setRootHandler(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler()
                                    .setResourceManager(new FileResourceManager(rootPath, 10485760))
                                    .setDirectoryListingEnabled(true)));

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Header[] headers = result.getHeaders("Content-Type");
            Assert.assertEquals("text/html; charset=UTF-8", headers[0].getValue());
            Assert.assertTrue(response, response.contains("page.html"));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/.");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            headers = result.getHeaders("Content-Type");
            Assert.assertEquals("text/html; charset=UTF-8", headers[0].getValue());
            Assert.assertTrue(response, response.contains("page.html"));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
