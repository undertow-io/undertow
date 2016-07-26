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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;

import io.undertow.websockets.WebSocketExtension;
import io.undertow.websockets.core.WebSocketLogger;

/**
 * Implementation of {@code permessage-deflate} WebSocket Extension handshake.
 * <p>
 * This implementation supports parameters: {@code server_no_context_takeover, client_no_context_takeover} .
 * <p>
 * This implementation does not support parameters: {@code server_max_window_bits, client_max_window_bits} .
 *
 * @see <a href="http://tools.ietf.org/html/draft-ietf-hybi-permessage-compression-18">Compression Extensions for WebSocket</a>
 *
 * @author Lucas Ponce
 */
public class PerMessageDeflateHandshake implements ExtensionHandshake {

    private static final String PERMESSAGE_DEFLATE = "permessage-deflate";
    private static final String SERVER_NO_CONTEXT_TAKEOVER = "server_no_context_takeover";
    private static final String CLIENT_NO_CONTEXT_TAKEOVER = "client_no_context_takeover";
    private static final String SERVER_MAX_WINDOW_BITS = "server_max_window_bits";
    private static final String CLIENT_MAX_WINDOW_BITS = "client_max_window_bits";

    private final Set<String> incompatibleExtensions = new HashSet<>();

    private boolean compressContextTakeover;
    private boolean decompressContextTakeover;

    private final boolean client;
    private final int deflaterLevel;

    /**
     * Default configuration for DEFLATE algorithm implementation
     */
    public static final int DEFAULT_DEFLATER = Deflater.DEFAULT_COMPRESSION;

    public PerMessageDeflateHandshake() {
        this(false);
    }

    /**
     * Create a new {@code PerMessageDeflateHandshake} instance.
     *
     * @param client indicate if extension is configured in client ({@code true }) context or server ({@code false }) context.
     */
    public PerMessageDeflateHandshake(final boolean client) {
        this(client, DEFAULT_DEFLATER);
    }

    /**
     * Create a new {@code PerMessageDeflateHandshake} instance.
     *
     * @param client        indicate if extension is configured in client ({@code true }) context or server ({@code false }) context
     * @param deflaterLevel the level of configuration of DEFLATE algorithm implementation
     */
    public PerMessageDeflateHandshake(final boolean client, final int deflaterLevel) {
        this(client, deflaterLevel, true, true);
    }

    /**
     * Create a new {@code PerMessageDeflateHandshake} instance.
     *
     * @param client                    flag for client ({@code true }) context or server ({@code false }) context
     * @param compressContextTakeover   flag for compressor context takeover or without compressor context
     * @param decompressContextTakeover flag for decompressor context takeover or without decompressor context
     */
    public PerMessageDeflateHandshake(final boolean client, boolean compressContextTakeover, boolean decompressContextTakeover) {
        this(client, DEFAULT_DEFLATER, compressContextTakeover, decompressContextTakeover);
    }

    /**
     * Create a new {@code PerMessageDeflateHandshake} instance.
     *
     * @param client                    flag for client ({@code true }) context or server ({@code false }) context
     * @param deflaterLevel             the level of configuration of DEFLATE algorithm implementation
     * @param compressContextTakeover   flag for compressor context takeover or without compressor context
     * @param decompressContextTakeover flag for decompressor context takeover or without decompressor context
     */
    public PerMessageDeflateHandshake(final boolean client, final int deflaterLevel, boolean compressContextTakeover, boolean decompressContextTakeover) {
        this.client = client;
        this.deflaterLevel = deflaterLevel;
        /*
            This extension is incompatible with multiple instances of same extension in the same Endpoint.
         */
        incompatibleExtensions.add(PERMESSAGE_DEFLATE);
        this.compressContextTakeover = compressContextTakeover;
        this.decompressContextTakeover = decompressContextTakeover;
    }

    @Override
    public String getName() {
        return PERMESSAGE_DEFLATE;
    }

    @Override
    public WebSocketExtension accept(final WebSocketExtension extension) {
        if (extension == null || !extension.getName().equals(getName())) return null;

        WebSocketExtension negotiated = new WebSocketExtension(extension.getName());

        if (extension.getParameters() == null || extension.getParameters().size() == 0) return negotiated;
        for (WebSocketExtension.Parameter parameter : extension.getParameters()) {
            if (parameter.getName().equals(SERVER_MAX_WINDOW_BITS)) {
                /*
                    Not supported
                 */
            } else if (parameter.getName().equals(CLIENT_MAX_WINDOW_BITS)) {
                /*
                    Not supported
                 */
            } else if (parameter.getName().equals(SERVER_NO_CONTEXT_TAKEOVER)) {
                negotiated.getParameters().add(parameter);
                if (client) {
                    decompressContextTakeover = false;
                } else {
                    compressContextTakeover = false;
                }
            } else if (parameter.getName().equals(CLIENT_NO_CONTEXT_TAKEOVER)) {
                negotiated.getParameters().add(parameter);
                if (client) {
                    compressContextTakeover = false;
                } else {
                    decompressContextTakeover = false;
                }
            } else {
                WebSocketLogger.EXTENSION_LOGGER.incorrectExtensionParameter(parameter);
                return null;
            }
        }
        WebSocketLogger.EXTENSION_LOGGER.debugf("Negotiated extension %s for handshake %s", negotiated, extension);
        return negotiated;
    }

    @Override
    public boolean isIncompatible(List<ExtensionHandshake> extensions) {
        for (ExtensionHandshake extension : extensions) {
            if (incompatibleExtensions.contains(extension.getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ExtensionFunction create() {
        return new PerMessageDeflateFunction(deflaterLevel, compressContextTakeover, decompressContextTakeover);
    }
}
