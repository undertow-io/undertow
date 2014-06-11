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

import static io.undertow.server.security.BasicAuthenticationTestCase._testBadPassword;
import static io.undertow.server.security.BasicAuthenticationTestCase._testBadUserName;
import static io.undertow.server.security.BasicAuthenticationTestCase._testBasicSuccess;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityNotification.EventType;
import io.undertow.testutils.DefaultServer;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A test case to test the SPNEGO authentication mechanism with a fallback to BASIC authentication.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(DefaultServer.class)
public class SpnegoBasicAuthenticationTestCase extends SpnegoAuthenticationTestCase {

    @Override
    protected List<AuthenticationMechanism> getTestMechanisms() {
        ArrayList<AuthenticationMechanism> mechanisms = new ArrayList<>(super.getTestMechanisms());
        mechanisms.add(BasicAuthenticationTestCase.getTestMechanism());

        return mechanisms;
    }

    @Test
    public void testBasicSuccess() throws Exception {
        setAuthenticationChain();
        _testBasicSuccess();
        assertSingleNotificationType(EventType.AUTHENTICATED);
    }

    @Test
    public void testBadUserName() throws Exception {
        setAuthenticationChain();
        _testBadUserName();
        assertSingleNotificationType(EventType.FAILED_AUTHENTICATION);
    }

    @Test
    public void testBadPassword() throws Exception {
        setAuthenticationChain();
        _testBadPassword();
        assertSingleNotificationType(EventType.FAILED_AUTHENTICATION);
    }

}
