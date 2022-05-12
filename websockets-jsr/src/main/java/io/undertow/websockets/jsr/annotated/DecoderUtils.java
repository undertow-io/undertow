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

package io.undertow.websockets.jsr.annotated;

import java.util.List;

import jakarta.websocket.Decoder;
import jakarta.websocket.EndpointConfig;

/**
 * @author Stuart Douglas
 */
public class DecoderUtils {

    /**
     * Gets a decoder for a given type.
     *
     * @param type                  The type
     * @param endpointConfiguration The endpoint configuration
     * @return A list of decoders, or null if no decoders exist
     */
    public static List<Decoder> getDecodersForType(final Class<?> type, final EndpointConfig endpointConfiguration) {
//        final List<Decoder> decoders = new ArrayList<>();
//        for (final Decoder decoder : endpointConfiguration.getDecoders()) {
//            final Class<?> clazz = ClassUtils.getDecoderType(decoder.getClass());
//            if (type.isAssignableFrom(clazz)) {
//                decoders.add(decoder);
//            }
//        }
//        if (!decoders.isEmpty()) {
//            return decoders;
//        }
        return null;
    }


    private DecoderUtils() {
    }

}
