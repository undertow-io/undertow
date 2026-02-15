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
import org.xnio.OptionMap;

import static io.undertow.UndertowOptions.DECODE_URL;
import static io.undertow.UndertowOptions.DEFAULT_DECODE_URL;

/**
 * Test parsing of requests containing redirect uri parameters with {@link io.undertow.UndertowOptions#DECODE_URL} enabled.
 *
 * @author Flavia Rainone
 */
public class RedirectURIParseWithDecodeURLTestCase extends AbstractRedirectURIParseTest {

    @DefaultServer.BeforeServerStarts
    public static void setup() {
        DefaultServer.setServerOptions(OptionMap.create(DECODE_URL, DEFAULT_DECODE_URL));
    }

    @DefaultServer.AfterServerStops
    public static void tearDown() {
        DefaultServer.setServerOptions(OptionMap.EMPTY);
    }

}