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

package io.undertow.server.security;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.NotificationReceiver;
import io.undertow.security.api.SecurityNotification;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.NotificationReceiverHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.security.impl.FormAuthenticationMechanism;
import io.undertow.security.impl.InMemorySingleSignOnManager;
import io.undertow.security.impl.SingleSignOnAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.FlexBase64;
import io.undertow.util.StatusCodes;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static org.junit.Assert.assertEquals;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SsoTestCase extends AuthenticationTestBase {

    @BeforeClass
    public static void setup() {

        final SingleSignOnAuthenticationMechanism sso = new SingleSignOnAuthenticationMechanism(new InMemorySingleSignOnManager());
        final PathHandler path = new PathHandler();
        HttpHandler current = new ResponseHandler();
        current = new AuthenticationCallHandler(current);
        current = new AuthenticationConstraintHandler(current);

        List<AuthenticationMechanism> mechs = new ArrayList<>();
        mechs.add(sso);
        mechs.add(new BasicAuthenticationMechanism("Test Realm"));

        current = new AuthenticationMechanismsHandler(current, mechs);
        current = new NotificationReceiverHandler(current, Collections.<NotificationReceiver>singleton(auditReceiver));

        current = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, current);

        path.addPrefixPath("/test1", current);
        current = new ResponseHandler();
        current = new AuthenticationCallHandler(current);
        current = new AuthenticationConstraintHandler(current);

        mechs = new ArrayList<>();
        mechs.add(sso);
        mechs.add(new FormAuthenticationMechanism("form", "/login", "/error"));

        current = new AuthenticationMechanismsHandler(current, mechs);
        current = new NotificationReceiverHandler(current, Collections.<NotificationReceiver>singleton(auditReceiver));

        current = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, current);

        path.addPrefixPath("/test2", current);
        path.addPrefixPath("/login", new ResponseCodeHandler(StatusCodes.UNAUTHORIZED));


        DefaultServer.setRootHandler(new SessionAttachmentHandler(path, new InMemorySessionManager(""), new SessionCookieConfig()));
    }

    @Override
    protected List<AuthenticationMechanism> getTestMechanisms() {
        return null;//not used
    }

    @Test
    public void testSsoSuccess() throws IOException {

        TestHttpClient client = new TestHttpClient();
        client.setCookieStore(new BasicCookieStore());
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/test1");
        HttpResponse result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
        String header = getAuthHeader(BASIC, values);
        assertEquals(BASIC + " realm=\"Test Realm\"", header);
        HttpClientUtils.readResponse(result);

        get = new HttpGet(DefaultServer.getDefaultServerURL() + "/test1");
        get.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString("userOne:passwordOne".getBytes(), false));
        result = client.execute(get);
        assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

        values = result.getHeaders("ProcessedBy");
        assertEquals(1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());
        HttpClientUtils.readResponse(result);
        assertSingleNotificationType(SecurityNotification.EventType.AUTHENTICATED);


        get = new HttpGet(DefaultServer.getDefaultServerURL() + "/test2");
        result = client.execute(get);
        assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

        values = result.getHeaders("ProcessedBy");
        assertEquals(1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());
        HttpClientUtils.readResponse(result);
        assertSingleNotificationType(SecurityNotification.EventType.AUTHENTICATED);

        //now test that logout will invalidate the SSO session
        get = new HttpGet(DefaultServer.getDefaultServerURL() + "/test1?logout=true");
        get.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString("userOne:passwordOne".getBytes(), false));
        result = client.execute(get);
        assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

        values = result.getHeaders("ProcessedBy");
        assertEquals(1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());
        HttpClientUtils.readResponse(result);
        assertNotifiactions(SecurityNotification.EventType.AUTHENTICATED, SecurityNotification.EventType.LOGGED_OUT);


        get = new HttpGet(DefaultServer.getDefaultServerURL() + "/test2");
        result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
    }
}
