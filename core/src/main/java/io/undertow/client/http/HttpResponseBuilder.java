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

package io.undertow.client.http;

import io.undertow.client.ClientResponse;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

/**
 * A pending http request.
 *
 * @author Emanuel Muckenhuber
 */
final class HttpResponseBuilder {

    private final ResponseParseState parseState = new ResponseParseState();

    private int statusCode;
    private HttpString protocol;
    private String reasonPhrase;
    private final HeaderMap responseHeaders = new HeaderMap();

    public ResponseParseState getParseState() {
        return parseState;
    }

    HeaderMap getResponseHeaders() {
        return responseHeaders;
    }

    int getStatusCode() {
        return statusCode;
    }

    void setStatusCode(final int statusCode) {
        this.statusCode = statusCode;
    }

    String getReasonPhrase() {
        return reasonPhrase;
    }

    void setReasonPhrase(final String reasonPhrase) {
        this.reasonPhrase = reasonPhrase;
    }

    HttpString getProtocol() {
        return protocol;
    }

    @SuppressWarnings("unused")
    void setProtocol(final HttpString protocol) {
        this.protocol = protocol;
    }

    public ClientResponse build() {
      return new ClientResponse(statusCode, reasonPhrase, protocol, responseHeaders);
    }

}
