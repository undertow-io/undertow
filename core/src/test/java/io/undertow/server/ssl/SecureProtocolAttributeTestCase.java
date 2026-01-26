/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
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
package io.undertow.server.ssl;

import java.io.IOException;
import javax.net.ssl.SSLContext;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.attribute.SubstituteEmptyWrapper;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.CompletionLatchHandler;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DefaultServer.class)
public class SecureProtocolAttributeTestCase {

    @Test
    public void testTlsRequestViaLogging() throws IOException {
        if (
                "true".equals(System.getProperty("test.ajp")) &&
                "true".equals(System.getProperty("undertow.proxied"))
        ) {
            throw new AssumptionViolatedException("This test makes no sense in a proxied AJP environment");
        }

        final String formatString = "Secure Protocol is %{SECURE_PROTOCOL}.";
        CompletionLatchHandler latchHandler = new CompletionLatchHandler(
            exchange -> {
                ExchangeAttribute tokens = ExchangeAttributes.parser(SecureProtocolAttributeTestCase.class.getClassLoader(),
                    new SubstituteEmptyWrapper("-")).parse(formatString);
                exchange.getResponseSender().send(tokens.readAttribute(exchange));
            });

        DefaultServer.setRootHandler(latchHandler);

        try (TestHttpClient client = new TestHttpClient()) {
            DefaultServer.startSSLServer();
            SSLContext sslContext = DefaultServer.getClientSSLContext();
            client.setSSLContext(sslContext);

            HttpResponse result = client.execute(new HttpGet(DefaultServer.getDefaultServerSSLAddress() + "/path"));
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(formatString.replaceAll("%\\{SECURE_PROTOCOL}", sslContext.getProtocol()),
                HttpClientUtils.readResponse(result));
        } finally {
            DefaultServer.stopSSLServer();
        }
    }
}
