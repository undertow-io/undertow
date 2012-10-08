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

package io.undertow.test.handlers;

import java.io.IOException;

import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.SetHeaderHandler;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SimpleNonBlockingServerTestCase {


    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(new SetHeaderHandler("MyHeader", "MyValue"));
    }

    @Test
    public void sendHttpRequest() throws IOException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders("MyHeader");
            Assert.assertEquals("MyValue", header[0].getValue());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
