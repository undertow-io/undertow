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

package io.undertow.websockets.client;

import io.undertow.websockets.WebSocketExtension;

import java.util.List;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
public class WebSocketClientNegotiation {

    private final List<String> supportedSubProtocols;
    private final List<WebSocketExtension> supportedExtensions;
    private volatile String selectedSubProtocol;
    private volatile List<WebSocketExtension> selectedExtensions;

    public WebSocketClientNegotiation(List<String> supportedSubProtocols, List<WebSocketExtension> supportedExtensions) {
        this.supportedSubProtocols = supportedSubProtocols;
        this.supportedExtensions = supportedExtensions;
    }

    public List<String> getSupportedSubProtocols() {
        return supportedSubProtocols;
    }

    public List<WebSocketExtension> getSupportedExtensions() {
        return supportedExtensions;
    }

    public String getSelectedSubProtocol() {
        return selectedSubProtocol;
    }

    public List<WebSocketExtension> getSelectedExtensions() {
        return selectedExtensions;
    }

    public void beforeRequest(final Map<String, List<String>> headers) {

    }
    public void afterRequest(final Map<String, List<String>> headers) {

    }

    public void handshakeComplete(String selectedProtocol, List<WebSocketExtension> selectedExtensions) {
        this.selectedExtensions = selectedExtensions;
        this.selectedSubProtocol = selectedProtocol;
    }
}
