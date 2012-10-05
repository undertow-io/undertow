/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.encoding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.Headers;
import io.undertow.util.QValueParser;

/**
 * Handler that serves as the basis for content encoding implementations.
 * <p/>
 * Encoding handlers are added as delegates to this handler, with a specified server side priority.
 * <p/>
 * If a request comes in with no q value then then server will pick the handler with the highest priority
 * as the encoding to use, otherwise the q value will be used to determine the correct handler.
 * <p/>
 * If no handler matches then the identity encoding is assumed. If the identity encoding has been
 * specifically disallowed due to a q value of 0 then the handler will set the response code
 * 406 (Not Acceptable) and return.
 *
 * @author Stuart Douglas
 */
public class EncodingHandler implements HttpHandler {

    private volatile HttpHandler identityHandler = ResponseCodeHandler.HANDLE_406;

    private final Map<String, Encoding> encodingMap = new CopyOnWriteMap<String, Encoding>();

    private volatile HttpHandler noEncodingHandler = ResponseCodeHandler.HANDLE_406;

    private static final String IDENTITY = "identity";

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        final Deque<String> res = exchange.getRequestHeaders().get(Headers.ACCEPT_ENCODING);
        HttpHandler identityHandler = this.identityHandler;
        if (res == null || res.isEmpty()) {
            if (identityHandler != null) {
                HttpHandlers.executeHandler(identityHandler, exchange, completionHandler);
            } else {
                //we don't have an identity handler
                HttpHandlers.executeHandler(noEncodingHandler, exchange, completionHandler);
            }
            return;
        }
        final List<List<QValueParser.QValueResult>> found = QValueParser.parse(res);
        for(List<QValueParser.QValueResult> result : found) {
            List<Encoding> available = new ArrayList<Encoding>();
            boolean includesIdentity = false;
            boolean isQValue0 = false;

            for(final QValueParser.QValueResult value : result) {
                Encoding encoding;
                if(value.getValue().equals("*")) {
                    includesIdentity = true;
                    encoding = new Encoding(identityHandler, 0);
                } else {
                    encoding = encodingMap.get(value.getValue());
                }
                if(value.isQValueZero()) {
                    isQValue0 = true;
                }
                if(encoding != null) {
                    available.add(encoding);
                }
            }
            if(isQValue0) {
                if(includesIdentity) {
                    HttpHandlers.executeHandler(noEncodingHandler, exchange, completionHandler);
                    return;
                } else {
                    HttpHandlers.executeHandler(identityHandler, exchange, completionHandler);
                    return;
                }
            } else if(!available.isEmpty()) {
                Collections.sort(available, Collections.reverseOrder());
                HttpHandlers.executeHandler(available.get(0).handler, exchange, completionHandler);
                return;
            }
        }
        HttpHandlers.executeHandler(identityHandler, exchange, completionHandler);
    }


    public HttpHandler getIdentityHandler() {
        return identityHandler;
    }

    public void setIdentityHandler(final HttpHandler identityHandler) {
        HttpHandlers.handlerNotNull(identityHandler);
        this.identityHandler = identityHandler;
        addEncodingHandler(IDENTITY, identityHandler, 0);
    }

    public synchronized void addEncodingHandler(final String encoding, final HttpHandler handler, int priority) {
        HttpHandlers.handlerNotNull(handler);
        this.encodingMap.put(encoding, new Encoding(handler, priority));
    }

    public synchronized void removeEncodingHandler(final String encoding) {
        encodingMap.remove(encoding);
    }

    public HttpHandler getNoEncodingHandler() {
        return noEncodingHandler;
    }

    public void setNoEncodingHandler(HttpHandler noEncodingHandler) {
        HttpHandlers.handlerNotNull(noEncodingHandler);
        this.noEncodingHandler = noEncodingHandler;
    }

    private static final class Encoding implements Comparable<Encoding> {

        private final HttpHandler handler;
        private final int priority;

        private Encoding(final HttpHandler handler, final int priority) {
            this.handler = handler;
            this.priority = priority;
        }

        @Override
        public int compareTo(final Encoding o) {
            return priority - o.priority;
        }
    }
}
