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

package tmp.texugo.server.handlers.encoding;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import tmp.texugo.server.HttpCompletionHandler;
import tmp.texugo.server.HttpHandler;
import tmp.texugo.server.HttpServerExchange;
import tmp.texugo.server.handlers.HttpHandlers;
import tmp.texugo.server.handlers.ResponseCodeHandler;
import tmp.texugo.util.CopyOnWriteMap;
import tmp.texugo.util.Headers;

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
            if(identityHandler != null) {
                HttpHandlers.executeHandler(identityHandler, exchange, completionHandler);
            } else {
                //we don't have an identity handler
                noEncodingHandler.handleRequest(exchange, completionHandler);
            }
            return;
        }
        boolean identityProhibited = false;
        final List<ParsedEncoding> found = new ArrayList<ParsedEncoding>();
        ParsedEncoding current = null;

        for (final String header : res) {
            final int l = header.length();
            //we do not use a string builder
            //we just keep track of where the current string starts and call substring()
            int stringStart = 0;
            for (int i = 0; i < l; ++i) {
                char c = header.charAt(i);
                switch (c) {
                    case ',': {
                        if (current != null &&
                                (i - stringStart > 2 && header.charAt(stringStart) == 'q' &&
                                        header.charAt(stringStart + 1) == '=')) {
                            //if this is a valid qvalue
                            current.qvalue = header.substring(stringStart + 2, i);
                            if (current.encoding.equals("*")) {
                                if (handleDefault(found, current)) {
                                    identityProhibited = true;
                                }
                            }
                            current = null;
                        } else if (stringStart != i) {
                            current = handleNewEncoding(found, header, stringStart, i);
                        }
                        stringStart = i + 1;
                        break;
                    }
                    case ';': {
                        if (stringStart != i) {
                            current = handleNewEncoding(found, header, stringStart, i);
                            stringStart = i + 1;
                        }
                        break;
                    }
                }
            }
            if (stringStart != l) {
                if (current != null &&
                        (l - stringStart > 2 && header.charAt(stringStart) == 'q' &&
                                header.charAt(stringStart + 1) == '=')) {
                    //if this is a valid qvalue
                    current.qvalue = header.substring(stringStart + 2, l);
                    if (current.encoding.equals("*")) {
                        if (handleDefault(found, current)) {
                            identityProhibited = true;
                        }
                    }
                } else {
                    current = handleNewEncoding(found, header, stringStart, l);
                }
            }
        }

        int size = found.size();
        if (size == 0) {
            if (identityProhibited || identityHandler == null) {
                HttpHandlers.executeHandler(noEncodingHandler, exchange, completionHandler);
                return;
            }
            HttpHandlers.executeHandler(identityHandler, exchange, completionHandler);
        } else if (size == 1) {
            HttpHandlers.executeHandler(found.get(0).handler.handler, exchange, completionHandler);
        } else {
            ParsedEncoding max = found.get(0);
            for (int i = 1; i < size; ++i) {
                ParsedEncoding o = found.get(i);
                if (o.compareTo(max) > 0) {
                    max = o;
                }
            }
            HttpHandlers.executeHandler(max.handler.handler, exchange, completionHandler);
        }
    }

    private ParsedEncoding handleNewEncoding(final List<ParsedEncoding> found, final String header, final int stringStart, final int i) {
        final ParsedEncoding current;
        current = new ParsedEncoding();
        current.encoding = header.substring(stringStart, i);
        final Encoding handler = encodingMap.get(current.encoding);
        if (handler != null) {
            current.handler = handler;
            found.add(current);
        }
        return current;
    }

    private boolean handleDefault(final List<ParsedEncoding> found, final ParsedEncoding current) {
        //we ignore * without a qvalue
        if (current.qvalue != null) {
            int length = Math.min(5, current.qvalue.length());
            //we need to find out if this is prohibiting identity
            //encoding (q=0). Otherwise we just treat it as the identity encoding
            boolean zero = true;
            for (int j = 0; j < length; ++j) {
                if (j == 1) continue;//decimal point
                if (current.qvalue.charAt(j) != '0') {
                    zero = false;
                    break;
                }
            }
            if (zero) {
                return true;
            } else {
                current.handler = new Encoding(identityHandler, 0);
                found.add(current);
            }
        }
        return false;
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

    private static class ParsedEncoding implements Comparable<ParsedEncoding> {
        String encoding;

        /**
         * we keep the qvalue as a string to avoid parsing the double.
         * <p/>
         * This should give both performance and also possible security improvements
         */
        String qvalue;
        Encoding handler;

        @Override
        public int compareTo(final ParsedEncoding other) {
            //we compare the strings as if they were decimal values.
            //we know they can only be

            final String t = qvalue;
            final String o = other.qvalue;
            if (t == null && o == null) {
                //neither of them has a q value
                //we compare them via the server specified default precedence
                //note that encoding is never null here, a * without a q value is meaningless
                //and will be discarded before this
                return handler.compareTo(other.handler);
            }

            if (o == null) {
                return 1;
            } else if (t == null) {
                return -1;
            }

            final int tl = t.length();
            final int ol = o.length();
            //we only compare the first 5 characters as per spec
            for (int i = 0; i < 5; ++i) {
                if (tl == i || ol == i) {
                    return ol - tl; //longer one is higher
                }
                if (i == 1) continue; // this is just the decimal point
                final int tc = t.charAt(i);
                final int oc = o.charAt(i);

                int res = tc - oc;
                if (res != 0) {
                    return res;
                }
            }
            return 0;
        }
    }

}
