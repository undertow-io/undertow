/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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
package io.undertow.server;

import io.undertow.testutils.DefaultServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.xnio.OptionMap;

import static io.undertow.UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL;

/**
 * Test parsing of requests containing redirect uri parameters with {@link
 * io.undertow.UndertowOptions#ALLOW_UNESCAPED_CHARACTERS_IN_URL allow unescaped characters config } enabled.
 *
 * @author Flavia Rainone
 */
public class RedirectURIParseWithAllowUnescapedCharactersTestCase extends AbstractRedirectURIParseTest {

    @DefaultServer.BeforeServerStarts
    public static void setup() {
        DefaultServer.setServerOptions(OptionMap.create(ALLOW_UNESCAPED_CHARACTERS_IN_URL, true));
    }

    @DefaultServer.AfterServerStops
    public static void tearDown() {
        DefaultServer.setServerOptions(OptionMap.EMPTY);
    }

    @BeforeClass // this is run after the server starts
    public static void disableProxyUnescapedCharactersInURL() {
        // disable it in proxy or else decoded URL is sent to the Undertow server
        DefaultServer.setProxyOptions(OptionMap.create(ALLOW_UNESCAPED_CHARACTERS_IN_URL, false));
    }

    @AfterClass
    public static void clearProxyOptions() {
        // disable it in proxy or else decoded URL is sent to the Undertow server
        DefaultServer.setProxyOptions(OptionMap.EMPTY);
    }
}