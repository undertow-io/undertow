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

package io.undertow.client;

import io.undertow.util.AbstractAttachable;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

/**
 * A client response. This just contains the parsed response header, the response body
 * can be read from the {@link ClientExchange}.
 *
 * @author Stuart Douglas
 */
public final class ClientResponse extends AbstractAttachable {

    private final HeaderMap responseHeaders;
    private final int responseCode;
    private final String status;
    private final HttpString protocol;

    public ClientResponse(int responseCode, String status, HttpString protocol) {
        this.responseCode = responseCode;
        this.status = status;
        this.protocol = protocol;
        this.responseHeaders = new HeaderMap();
    }

    public ClientResponse(int responseCode, String status, HttpString protocol, HeaderMap headers) {
        this.responseCode = responseCode;
        this.status = status;
        this.protocol = protocol;
        this.responseHeaders = headers;
    }
    public HeaderMap getResponseHeaders() {
        return responseHeaders;
    }

    public HttpString getProtocol() {
        return protocol;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "ClientResponse{" +
                "responseHeaders=" + responseHeaders +
                ", responseCode=" + responseCode +
                ", status='" + status + '\'' +
                ", protocol=" + protocol +
                '}';
    }
}
