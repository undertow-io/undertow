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

package io.undertow.websockets.extensions;

import java.util.List;

import io.undertow.websockets.WebSocketExtension;

/**
 * Base interface for WebSocket Extension handshake.
 * <p>
 * It is responsible of the definition and negotiation logic of a WebSocket Extension. It interacts at the handshake phase.
 * <p>
 * It creates new instances of {@link ExtensionFunction} .
 *
 * @author Lucas Ponce
 */
public interface ExtensionHandshake {

    /**
     * @return name of the WebSocket Extension
     */
    String getName();

    /**
     * Validate if an extension request is accepted.
     *
     * @param extension the extension request representation
     * @return          a new {@link WebSocketExtension} instance with parameters accepted;
     *                  {@code null} in case extension request is not accepted
     */
    WebSocketExtension accept(final WebSocketExtension extension);

    /**
     * Validate if current extension is compatible with previously negotiated in the server side.
     *
     * @param extensions a list of negotiated extensions
     * @return           {@code true} if current extension is compatible;
     *                   {@code false} if current extension is not compatible
     */
    boolean isIncompatible(final List<ExtensionHandshake> extensions);

    /**
     * Create a new instance of the {@link ExtensionFunction} associated to this WebSocket Extension.
     *
     * @return a new instance {@link ExtensionFunction}
     */
    ExtensionFunction create();
}
