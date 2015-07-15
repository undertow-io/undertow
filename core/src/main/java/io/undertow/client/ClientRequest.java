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
import io.undertow.util.Methods;
import io.undertow.util.Protocols;

/**
 * A client request. This class should not be modified once it has been submitted to the {@link ClientConnection}.
 *
 * This class only represents the HTTP header, it does not represent an entity body. If the request needs an entity
 * body then this must be specified by either setting a Content-Length or Transfer-Encoding header, otherwise
 * the client will assume that the body is empty.
 *
 * @author Stuart Douglas
 */
public final class ClientRequest extends AbstractAttachable {

    private final HeaderMap requestHeaders = new HeaderMap();
    private String path = "/";
    private HttpString method = Methods.GET;
    private HttpString protocol = Protocols.HTTP_1_1;

    public HeaderMap getRequestHeaders() {
        return requestHeaders;
    }

    public String getPath() {
        return path;
    }

    public HttpString getMethod() {
        return method;
    }

    public HttpString getProtocol() {
        return protocol;
    }

    public ClientRequest setPath(String path) {
        this.path = path;
        return this;
    }

    public ClientRequest setMethod(HttpString method) {
        this.method = method;
        return this;
    }

    public ClientRequest setProtocol(HttpString protocol) {
        this.protocol = protocol;
        return this;
    }

    @Override
    public String toString() {
        return "ClientRequest{path='" + path + '\'' +
                ", method=" + method +
                ", protocol=" + protocol +
                '}';
    }
}
