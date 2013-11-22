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
package io.undertow.server.security;

import java.util.Collections;
import java.util.List;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityNotification.EventType;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.FlexBase64;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import io.undertow.testutils.TestHttpClient;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static org.junit.Assert.assertEquals;

/**
 * A test case to test when the only authentication mechanism is the BASIC mechanism.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(DefaultServer.class)
public class BasicAuthenticationTestCase extends AuthenticationTestBase {

    @Override
    protected List<AuthenticationMechanism> getTestMechanisms() {
        AuthenticationMechanism mechanism = new BasicAuthenticationMechanism("Test Realm");

        return Collections.singletonList(mechanism);
    }

    @Test
    public void testBasicSuccess() throws Exception {
        setAuthenticationChain();

        TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
        HttpResponse result = client.execute(get);
        assertEquals(401, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
        assertEquals(1, values.length);
        assertEquals(BASIC + " realm=\"Test Realm\"", values[0].getValue());
        HttpClientUtils.readResponse(result);

        get = new HttpGet(DefaultServer.getDefaultServerURL());
        get.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString("userOne:passwordOne".getBytes(), false));
        result = client.execute(get);
        assertEquals(200, result.getStatusLine().getStatusCode());

        values = result.getHeaders("ProcessedBy");
        assertEquals(1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());
        HttpClientUtils.readResponse(result);
        assertSingleNotificationType(EventType.AUTHENTICATED);
    }

    @Test
    public void testBadUserName() throws Exception {
        setAuthenticationChain();

        TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
        HttpResponse result = client.execute(get);
        assertEquals(401, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
        assertEquals(1, values.length);
        assertEquals(BASIC + " realm=\"Test Realm\"", values[0].getValue());
        HttpClientUtils.readResponse(result);

        get = new HttpGet(DefaultServer.getDefaultServerURL());
        get.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString("badUser:passwordOne".getBytes(), false));
        result = client.execute(get);
        assertEquals(401, result.getStatusLine().getStatusCode());
        HttpClientUtils.readResponse(result);
        assertSingleNotificationType(EventType.FAILED_AUTHENTICATION);
    }

    @Test
    public void testBadPassword() throws Exception {
        setAuthenticationChain();

        TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
        HttpResponse result = client.execute(get);
        assertEquals(401, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
        assertEquals(1, values.length);
        assertEquals(BASIC + " realm=\"Test Realm\"", values[0].getValue());
        HttpClientUtils.readResponse(result);

        get = new HttpGet(DefaultServer.getDefaultServerURL());
        get.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString("userOne:badPassword".getBytes(), false));
        result = client.execute(get);
        assertEquals(401, result.getStatusLine().getStatusCode());
        HttpClientUtils.readResponse(result);
        assertSingleNotificationType(EventType.FAILED_AUTHENTICATION);
    }

}
