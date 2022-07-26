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

import io.undertow.predicate.Predicate;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.Headers;
import io.undertow.util.QValueParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 *
 * @author Stuart Douglas
 */
public class ContentEncodingRepository {

    public static final String IDENTITY = "identity";
    public static final EncodingMapping IDENTITY_ENCODING = new EncodingMapping(IDENTITY, ContentEncodingProvider.IDENTITY, 0, Predicates.truePredicate());

    private final Map<String, EncodingMapping> encodingMap = new CopyOnWriteMap<>();


    public AllowedContentEncodings getContentEncodings(final HttpServerExchange exchange) {
        final List<String> res = exchange.getRequestHeaders().get(Headers.ACCEPT_ENCODING);
        if (res == null || res.isEmpty()) {
            return null;
        }
        final List<EncodingMapping> resultingMappings = new ArrayList<>();
        final List<List<QValueParser.QValueResult>> found = QValueParser.parse(res);
        //noinspection ForLoopReplaceableByForEach - using induction for loop for iteration to avoid allocation
        for (int i = 0; i < found.size(); i++) {
            final List<QValueParser.QValueResult> result = found.get(i);
            List<EncodingMapping> available = new ArrayList<>();
            boolean includesIdentity = false;
            boolean isQValue0 = false;

            //noinspection ForLoopReplaceableByForEach - using induction for loop for iteration to avoid allocation
            for (int j = 0; j < result.size(); j++) {
                final QValueParser.QValueResult value = result.get(j);
                EncodingMapping encoding;
                if (value.getValue().equals("*")) {
                    includesIdentity = true;
                    encoding = IDENTITY_ENCODING;
                } else {
                    encoding = encodingMap.get(value.getValue());
                    if(encoding == null && IDENTITY.equals(value.getValue())) {
                        encoding = IDENTITY_ENCODING;
                    }
                }
                if (value.isQValueZero()) {
                    isQValue0 = true;
                }
                if (encoding != null) {
                    available.add(encoding);
                }
            }
            if (isQValue0) {
                if (resultingMappings.isEmpty()) {
                    if (includesIdentity) {
                        return new AllowedContentEncodings(exchange, Collections.<EncodingMapping>emptyList());
                    } else {
                        return null;
                    }
                }
            } else if (!available.isEmpty()) {
                Collections.sort(available, Collections.reverseOrder());
                resultingMappings.addAll(available);
            }
        }
        if (!resultingMappings.isEmpty()) {
            return new AllowedContentEncodings(exchange, resultingMappings);
        }
        return null;
    }

    public synchronized ContentEncodingRepository addEncodingHandler(final String encoding, final ContentEncodingProvider encoder, int priority) {
        addEncodingHandler(encoding, encoder, priority, Predicates.truePredicate());
        return this;
    }

    public synchronized ContentEncodingRepository addEncodingHandler(final String encoding, final ContentEncodingProvider encoder, int priority, final Predicate enabledPredicate) {
        this.encodingMap.put(encoding, new EncodingMapping(encoding, encoder, priority, enabledPredicate));
        return this;
    }

    public synchronized ContentEncodingRepository removeEncodingHandler(final String encoding) {
        encodingMap.remove(encoding);
        return this;
    }

}
