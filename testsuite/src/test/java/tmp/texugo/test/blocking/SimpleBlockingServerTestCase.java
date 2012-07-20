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

package tmp.texugo.test.blocking;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import tmp.texugo.server.handlers.blocking.BlockingHandler;
import tmp.texugo.server.handlers.blocking.BlockingHttpHandler;
import tmp.texugo.server.handlers.blocking.BlockingHttpServerExchange;
import tmp.texugo.test.util.DefaultServer;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SimpleBlockingServerTestCase {

    private static final String MESSAGE = "My HTTP Request!";

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = DefaultServer.newBlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(new BlockingHttpHandler() {
            @Override
            public void handleRequest(final BlockingHttpServerExchange exchange) {
                try {
                    exchange.startResponse();
                    exchange.getOutputStream().write(MESSAGE.getBytes());
                    exchange.getOutputStream().close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    public void sendHttpRequest() throws IOException {
        HttpGet get = new HttpGet(DefaultServer.getBase() + "/path");
        DefaultHttpClient client = new DefaultHttpClient();
        HttpResponse result = client.execute(get);
        Assert.assertEquals(200, result.getStatusLine().getStatusCode());
        Assert.assertEquals(MESSAGE, readResponse(result));
    }

    public static String readResponse(final HttpResponse response) throws IOException {
        final StringBuilder builder = new StringBuilder();
        byte[] data = new byte[100];
        InputStream stream = response.getEntity().getContent();
        int read;
        while ((read = stream.read(data)) != -1) {
            builder.append(new String(data,0,read));
        }
        return builder.toString();
    }
}
