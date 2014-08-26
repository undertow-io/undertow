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

package io.undertow.http2.tests.httpupgrade;

import org.junit.Ignore;
import org.junit.Test;

import io.undertow.http2.tests.framework.TestCategory;

/**
 * @author Stuart Douglas
 */
public class HttpUpgradeConnectTestCase {

    @Ignore
    @Test
    @TestCategory(major = 1, minor = 1, description = "Tests that a connection can be established via HTTP upgrade")
    public void testSimpleConnectViaHttpUpgrade() {

    }

    @Ignore
    @Test
    @TestCategory(major = 1, minor = 2, description = "Tests that connections with no preface are terminated")
    public void testConnectionViaUpgradeWithNoPrefaceFrame() {

    }

    @Ignore
    @Test
    @TestCategory(major = 1, minor = 3, description = "Tests that connections with no settings frame are closed via a GOAWAY")
    public void testConnectionViaUpgradeWithNoSettingsFrame() {

    }

    @Ignore
    @Test
    @TestCategory(major = 1, minor = 4, description = "Tests that upgrade requests with no HTTP2-Settings field are ignored")
    public void testConnectionViaUpgradeWithNoHttp2Settings() {

    }

    @Ignore
    @Test
    @TestCategory(major = 1, minor = 5, description = "Tests that upgrade requests that use h2 instead of h2c are ignored")
    public void testConnectionViaUpgradeWithWrongProtocolName() {

    }
}
