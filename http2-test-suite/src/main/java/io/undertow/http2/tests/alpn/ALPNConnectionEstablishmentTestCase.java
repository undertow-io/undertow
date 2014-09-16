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

package io.undertow.http2.tests.alpn;

import io.undertow.client.ClientRequest;
import io.undertow.http2.tests.framework.Http2Client;
import io.undertow.http2.tests.framework.Http2TestRunner;
import io.undertow.http2.tests.framework.HttpResponse;
import io.undertow.http2.tests.framework.TestEnvironment;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Tests HTTP2 connection establishment via ALPN
 *
 * @author Stuart Douglas
 */
@RunWith(Http2TestRunner.class)
public class ALPNConnectionEstablishmentTestCase {

    @Test
    public void testConnectionEstablished() throws IOException {
        Http2Client connection = TestEnvironment.connectViaAlpn();
        HttpResponse response = connection.sendRequest(new ClientRequest());
        Assert.assertEquals(200, response.getStatus());

    }

}
