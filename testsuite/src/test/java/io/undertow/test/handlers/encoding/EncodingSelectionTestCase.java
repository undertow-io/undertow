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

package io.undertow.test.handlers.encoding;

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.test.util.DefaultServer;
import io.undertow.test.util.HttpClientUtils;
import io.undertow.test.util.SetHeaderHandler;
import io.undertow.util.Headers;

/**
 * Tests that the correct encoding is selected
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class EncodingSelectionTestCase {

    private static final String HEADER = "selected";

    /**
     * Tests encoding selection with no qvalue
     *
     * Also tests a lot of non standard formats for Accept-Encoding to make sure that
     * we are liberal in what we accept
     *
     * @throws IOException
     */
    @Test
    public void testBasicEncodingSelect() throws IOException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            final EncodingHandler handler = new EncodingHandler();
            handler.addEncodingHandler("compress", new SetHeaderHandler(HEADER, "compress"), 50);
            handler.addEncodingHandler("bzip", new SetHeaderHandler(HEADER, "bzip"), 100);
            handler.setIdentityHandler(new SetHeaderHandler(HEADER, "identity"));
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders(HEADER);
            Assert.assertEquals("identity", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING, "bzip");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING, "bzip compress identity someOtherEndcoding");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING, " compress, identity, someOtherEndcoding,  bzip  , ");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING, "boo; compress, identity; someOtherEndcoding,   , ");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("compress", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING, "boo; compress; identity; someOtherEndcoding,   , ");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("compress", header[0].getValue());
            HttpClientUtils.readResponse(result);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    /**
     * Tests encoding selection with a qvalue
     *
     *
     * @throws IOException
     */
    @Test
    public void testEncodingSelectWithQValue() throws IOException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            final EncodingHandler handler = new EncodingHandler();
            handler.addEncodingHandler("compress", new SetHeaderHandler(HEADER, "compress"), 100);
            handler.addEncodingHandler("bzip", new SetHeaderHandler(HEADER, "bzip"), 50);
            handler.setIdentityHandler(new SetHeaderHandler(HEADER, "identity"));
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING, "bzip, compress;q=0.6");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders(HEADER);
            Assert.assertEquals("compress", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING, "*;q=0.00");
            result = client.execute(get);
            Assert.assertEquals(406, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING, "*;q=0.00 bzip");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING, "*;q=0.00 bzip;q=0.3");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING, "compress;q=0.1 bzip;q=0.05");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("compress", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING, "compress;q=0.1, bzip;q=1.000");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
