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

package io.undertow.websockets.jsr.test.annotated;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.HandshakeResponse;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.undertow.util.Headers.SEC_WEB_SOCKET_PROTOCOL_STRING;

/**
 * @author Stuart Douglas
 */
public class ClientConfigurator extends ClientEndpointConfig.Configurator {

    public static volatile String sentSubProtocol;
    private static volatile String receivedSubProtocol;
    private static volatile CountDownLatch receiveLatch = new CountDownLatch(1);

    @Override
    public void beforeRequest(Map<String, List<String>> headers) {
        if (headers.containsKey(SEC_WEB_SOCKET_PROTOCOL_STRING)) {
            sentSubProtocol = headers.get(SEC_WEB_SOCKET_PROTOCOL_STRING).get(0);
            headers.put(SEC_WEB_SOCKET_PROTOCOL_STRING, Collections.singletonList("configured-proto"));
        } else {
            sentSubProtocol = null;
        }
    }

    @Override
    public void afterResponse(HandshakeResponse hr) {
        Map<String, List<String>> headers = hr.getHeaders();
        if (headers.containsKey(SEC_WEB_SOCKET_PROTOCOL_STRING.toLowerCase(Locale.ENGLISH))) {
            receivedSubProtocol = headers.get(SEC_WEB_SOCKET_PROTOCOL_STRING.toLowerCase(Locale.ENGLISH)).get(0);
        } else {
            receivedSubProtocol = null;
        }
        receiveLatch.countDown();
    }

    public static String receivedSubProtocol() {
        try {
            receiveLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return receivedSubProtocol;
    }
}
