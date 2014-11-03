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

package io.undertow.server.handlers;

import java.io.IOException;

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.DateUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class DateHandlerTestCase {

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(new DateHandler(ResponseCodeHandler.HANDLE_200));
    }

    @Test
    public void testDateHandler() throws IOException, InterruptedException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header date = result.getHeaders("Date")[0];
            final long firstDate = DateUtils.parseDate(date.getValue()).getTime();
            Assert.assertTrue((firstDate + 3000) > System.currentTimeMillis());
            Assert.assertTrue(System.currentTimeMillis() >= firstDate);
            HttpClientUtils.readResponse(result);
            Thread.sleep(1500);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            date = result.getHeaders("Date")[0];
            final long secondDate = DateUtils.parseDate(date.getValue()).getTime();
            Assert.assertTrue((secondDate + 2000) > System.currentTimeMillis());
            Assert.assertTrue(System.currentTimeMillis() >= secondDate);
            Assert.assertTrue(secondDate > firstDate);
            HttpClientUtils.readResponse(result);
        } finally {

            client.getConnectionManager().shutdown();
        }
    }

}
