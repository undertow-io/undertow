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

package io.undertow.server.handlers;

import java.io.IOException;

import io.undertow.server.HttpServerExchange;

/**
 * Server side upgrade handler. This handler can inspect the request and modify the response.
 * <p>
 * If the request does not meet this handlers requirements it should return false to allow
 * other upgrade handlers to inspect the request.
 * <p>
 * If the request is invalid (e.g. security information is invalid) this should thrown an IoException.
 * if this occurs no further handlers will be tried.
 *
 * @author Stuart Douglas
 */
public interface HttpUpgradeHandshake {

    /**
     * Validates an upgrade request and returns any extra headers that should be added to the response.
     *
     * @param exchange the server exchange
     * @return <code>true</code> if the handshake is valid and should be upgraded. False if it is invalid
     * @throws IOException If the handshake is invalid
     */
    boolean handleUpgrade(final HttpServerExchange exchange) throws IOException;

}
