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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.ContentEncodingHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.undertow.server.handlers.CanonicalPathHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.PreCompressedResourceSupplier;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class PreCompressedResourceTestCase {


    @Test
    public void testContentEncodedResource() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PreCompressedResourceSupplier(new PathResourceManager(rootPath, 10485760)).addEncoding("gzip", ".gz"))
                                    .setDirectoryListingEnabled(true))));

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/page.html");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String nonCompressedResource = HttpClientUtils.readResponse(result);
            Header[] headers = result.getHeaders(Headers.CONTENT_TYPE_STRING);
            Assert.assertEquals("text/html", headers[0].getValue());
            Assert.assertTrue(nonCompressedResource, nonCompressedResource.contains("A web page"));
            Assert.assertNull(result.getFirstHeader(Headers.CONTENT_ENCODING_STRING));


            ContentEncodingHttpClient compClient = new ContentEncodingHttpClient();
            result = compClient.execute(get);

            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String compressedResource = HttpClientUtils.readResponse(result);
            headers = result.getHeaders(Headers.CONTENT_TYPE_STRING);
            Assert.assertEquals("text/html", headers[0].getValue());
            Assert.assertEquals(nonCompressedResource.replace("\r", ""), compressedResource.replace("\r", "")); //ignore line ending differences
            Assert.assertEquals("gzip", result.getFirstHeader(Headers.CONTENT_ENCODING_STRING).getValue());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
