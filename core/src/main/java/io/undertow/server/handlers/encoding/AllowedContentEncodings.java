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

package io.undertow.server.handlers.encoding;

import java.util.List;

import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.ConduitFactory;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import org.xnio.conduits.StreamSinkConduit;

/**
 * An attachment that provides information about the current content encoding that will be chosen for the response
 *
 * @author Stuart Douglas
 */
public class AllowedContentEncodings implements ConduitWrapper<StreamSinkConduit> {

    public static final AttachmentKey<AllowedContentEncodings> ATTACHMENT_KEY = AttachmentKey.create(AllowedContentEncodings.class);

    private final HttpServerExchange exchange;
    private final List<EncodingMapping> encodings;


    public AllowedContentEncodings(final HttpServerExchange exchange, final List<EncodingMapping> encodings) {
        this.exchange = exchange;
        this.encodings = encodings;
    }

    /**
     * @return The content encoding that will be set, given the current state of the HttpServerExchange
     */
    public String getCurrentContentEncoding() {
        for (EncodingMapping encoding : encodings) {
            if (encoding.getAllowed() == null || encoding.getAllowed().resolve(exchange)) {
                return encoding.getName();
            }
        }
        return Headers.IDENTITY.toString();
    }

    public EncodingMapping getEncoding() {
        for (EncodingMapping encoding : encodings) {
            if (encoding.getAllowed() == null || encoding.getAllowed().resolve(exchange)) {
                return encoding;
            }
        }
        return null;
    }

    public boolean isIdentity() {
        return getCurrentContentEncoding().equals(Headers.IDENTITY.toString());
    }

    /**
     * If the list of allowed encodings was empty then it means that no encodings were allowed, and
     * identity was explicitly prohibited with a q value of 0.
     */
    public boolean isNoEncodingsAllowed() {
        return encodings.isEmpty();
    }

    @Override
    public StreamSinkConduit wrap(final ConduitFactory<StreamSinkConduit> factory, final HttpServerExchange exchange) {
        if (exchange.getResponseHeaders().contains(Headers.CONTENT_ENCODING)) {
            //already encoded
            return factory.create();
        }
        //if this is a zero length response we don't want to encode
        if (exchange.getResponseContentLength() != 0
                && exchange.getStatusCode() != StatusCodes.NO_CONTENT
                && exchange.getStatusCode() != StatusCodes.NOT_MODIFIED) {
            EncodingMapping encoding = getEncoding();
            if (encoding != null) {
                exchange.getResponseHeaders().put(Headers.CONTENT_ENCODING, encoding.getName());
                if (exchange.getRequestMethod().equals(Methods.HEAD)) {
                    //we don't create an actual encoder for HEAD requests, but we set the header
                    return factory.create();
                } else {
                    return encoding.getEncoding().getResponseWrapper().wrap(factory, exchange);
                }
            }
        }
        return factory.create();
    }
}
