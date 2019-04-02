/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.websockets.jsr;

import java.util.Collections;
import java.util.List;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;

public abstract class WebSocketClientNegotiation {

    private final List<String> supportedSubProtocols;
    private final List<WebSocketExtensionData> supportedExtensions;
    private volatile String selectedSubProtocol;
    private volatile List<WebSocketExtensionData> selectedExtensions = Collections.emptyList();

    public WebSocketClientNegotiation(List<String> supportedSubProtocols, List<WebSocketExtensionData> supportedExtensions) {
        this.supportedSubProtocols = supportedSubProtocols;
        this.supportedExtensions = supportedExtensions;
    }

    public List<String> getSupportedSubProtocols() {
        return supportedSubProtocols;
    }

    public List<WebSocketExtensionData> getSupportedExtensions() {
        return supportedExtensions;
    }

    public String getSelectedSubProtocol() {
        return selectedSubProtocol;
    }

    public List<WebSocketExtensionData> getSelectedExtensions() {
        return selectedExtensions;
    }

    public void beforeRequest(final HttpHeaders headers) {

    }
    public void afterRequest(final HttpHeaders headers) {

    }

    public void handshakeComplete(String selectedProtocol, List<WebSocketExtensionData> selectedExtensions) {
        this.selectedExtensions = selectedExtensions;
        this.selectedSubProtocol = selectedProtocol;
    }
}
