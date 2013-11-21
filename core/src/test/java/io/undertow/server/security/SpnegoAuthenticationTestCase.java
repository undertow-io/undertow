/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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

import javax.security.auth.Subject;

import io.undertow.testutils.DefaultServer;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A test case to test the SPNEGO authentication mechanism.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SpnegoAuthenticationTestCase {

    @BeforeClass
    public static void startServers() throws Exception {
        KerberosKDCUtil.startServer();
    }

    @AfterClass
    public static void stopServers() {

    }

    @Test
    public void test() {
        System.out.println("Test Run");
    }

    @Test
    public void testJDuke() throws Exception {
        Subject subject = KerberosKDCUtil.login("jduke", "theduke".toCharArray());

        System.out.println(subject.toString());
    }

    @Test
    public void testServer() throws Exception {
        Subject subject = KerberosKDCUtil.login("HTTP/" + DefaultServer.getDefaultServerAddress().getHostString(), "servicepwd".toCharArray());

        System.out.println(subject.toString());
    }

}
