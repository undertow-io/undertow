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

package io.undertow.http2.tests.framework;

import io.undertow.client.UndertowClient;
import org.xnio.OptionMap;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Stuart Douglas
 */
public class TestEnvironment {


    public static Http2Client connectViaUpgrade() throws IOException {
        return new Http2Client(UndertowClient.getInstance().connect(TestEnvironment.getHttp2UpgradeURL(), Http2TestRunner.getWorker(), Http2TestRunner.getBufferPool(), OptionMap.EMPTY).get());
    }

    public static Http2Client connectViaAlpn() throws IOException {
        return new Http2Client(UndertowClient.getInstance().connect(TestEnvironment.getHttp2AlpnURL(), Http2TestRunner.getWorker(), Http2TestRunner.getClientXnioSsl(), Http2TestRunner.getBufferPool(), OptionMap.EMPTY).get());
    }

    public static int getPort() {
        return 7877;
    }

    public static String getHost() {
        return "localhost";
    }

    public static String getBasePath() {
        return "/";
    }

    public static URI getHttp2UpgradeURL() {
        try {
            return new URI("h2c", null, TestEnvironment.getHost(), TestEnvironment.getPort(), TestEnvironment.getBasePath(), "", "");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
    public static URI getHttp2AlpnURL() {
        try {
            return new URI("h2", null, TestEnvironment.getHost(), TestEnvironment.getPort(), TestEnvironment.getBasePath(), "", "");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
