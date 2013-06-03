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

package io.undertow.server.ssl;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.CanonicalPathHandler;
import io.undertow.server.handlers.CookieHandler;
import io.undertow.server.handlers.NameVirtualHostHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.error.SimpleErrorPageHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.file.FileHandlerTestCase;
import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@AjpIgnore
@RunWith(DefaultServer.class)
public class ComplexSSLTestCase {

    @Test
    public void complexSSLTestCase() throws IOException, GeneralSecurityException, URISyntaxException {
        final PathHandler pathHandler = new PathHandler();
        Path rootPath = Paths.get(FileHandlerTestCase.class.getResource("page.html").toURI()).getParent();

        final NameVirtualHostHandler virtualHostHandler = new NameVirtualHostHandler();
        HttpHandler root = virtualHostHandler;
        root = new CookieHandler(root);
        root = new SimpleErrorPageHandler(root);
        root = new CanonicalPathHandler(root);

        virtualHostHandler.addHost("default-host", pathHandler);
        virtualHostHandler.setDefaultHandler(pathHandler);

        pathHandler.addPath("/", new ResourceHandler()
                .setResourceManager(new FileResourceManager(rootPath))
                .setDirectoryListingEnabled(true));

        DefaultServer.setRootHandler(root);

        DefaultServer.startSSLServer();
        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            //get file list, this works
            HttpGet getFileList = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            HttpResponse resultList = client.execute(getFileList);
            Assert.assertEquals(200, resultList.getStatusLine().getStatusCode());
            String responseList = HttpClientUtils.readResponse(resultList);
            Assert.assertTrue(responseList, responseList.contains("page.html"));
            Header[] headersList = resultList.getHeaders("Content-Type");
            Assert.assertEquals("text/html", headersList[0].getValue());

            //get file itself, breaks
            HttpGet getFile = new HttpGet(DefaultServer.getDefaultServerSSLAddress() + "/page.html");
            HttpResponse result = client.execute(getFile);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Header[] headers = result.getHeaders("Content-Type");
            Assert.assertEquals("text/html", headers[0].getValue());
            Assert.assertTrue(response, response.contains("A web page"));


        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }
}
