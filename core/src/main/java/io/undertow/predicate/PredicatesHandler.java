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

package io.undertow.predicate;

import io.undertow.UndertowLogger;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.server.handlers.builder.PredicatedHandler;
import io.undertow.util.AttachmentKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Handler that can deal with a large number of predicates. chaining together a large number of {@link io.undertow.predicate.PredicatesHandler.Holder}
 * instances will make the stack grow to large, so this class is used that can deal with a large number of predicates.
 *
 * @author Stuart Douglas
 */
public class PredicatesHandler implements HttpHandler {

    /**
     * static done marker. If this is attached to the exchange it will drop out immediately.
     */
    public static final AttachmentKey<Boolean> DONE = AttachmentKey.create(Boolean.class);
    public static final AttachmentKey<Boolean> RESTART = AttachmentKey.create(Boolean.class);

    private volatile Holder[] handlers = new Holder[0];
    private volatile HttpHandler next;
    private final boolean outerHandler;

    //non-static, so multiple handlers can co-exist
    private final AttachmentKey<Integer> CURRENT_POSITION = AttachmentKey.create(Integer.class);

    public PredicatesHandler(HttpHandler next) {
        this.next = next;
        this.outerHandler = true;
    }
    public PredicatesHandler(HttpHandler next, boolean outerHandler) {
        this.next = next;
        this.outerHandler = outerHandler;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final int length = handlers.length;
        Integer current = exchange.getAttachment(CURRENT_POSITION);
        do {
            int pos;
            if (current == null) {
                if (outerHandler) {
                    exchange.removeAttachment(RESTART);
                    exchange.removeAttachment(DONE);
                    if (exchange.getAttachment(Predicate.PREDICATE_CONTEXT) == null) {
                        exchange.putAttachment(Predicate.PREDICATE_CONTEXT, new TreeMap<String, Object>());
                    }
                }
                pos = 0;
            } else {
                //if it has been marked as done
                if (exchange.getAttachment(DONE) != null) {
                    exchange.removeAttachment(CURRENT_POSITION);
                    next.handleRequest(exchange);
                    return;
                }
                pos = current;
            }
            for (; pos < length; ++pos) {
                final Holder handler = handlers[pos];
                if (handler.predicate.resolve(exchange)) {
                    exchange.putAttachment(CURRENT_POSITION, pos + 1);
                    handler.handler.handleRequest(exchange);
                    if(shouldRestart(exchange, current)) {
                        break;
                    } else {
                        return;
                    }
                } else if(handler.elseBranch != null) {
                    exchange.putAttachment(CURRENT_POSITION, pos + 1);
                    handler.elseBranch.handleRequest(exchange);
                    if(shouldRestart(exchange, current)) {
                        break;
                    } else {
                        return;
                    }
                }
            }
        } while (shouldRestart(exchange, current));
        next.handleRequest(exchange);

    }

    private boolean shouldRestart(HttpServerExchange exchange, Integer current) {
        return exchange.getAttachment(RESTART) != null && outerHandler && current == null;
    }

    /**
     * Adds a new predicated handler.
     * <p>
     *
     * @param predicate
     * @param handlerWrapper
     */
    public PredicatesHandler addPredicatedHandler(final Predicate predicate, final HandlerWrapper handlerWrapper, final HandlerWrapper elseBranch) {
        Holder[] old = handlers;
        Holder[] handlers = new Holder[old.length + 1];
        System.arraycopy(old, 0, handlers, 0, old.length);
        HttpHandler elseHandler = elseBranch != null ? elseBranch.wrap(this) : null;
        handlers[old.length] = new Holder(predicate, handlerWrapper.wrap(this), elseHandler);
        this.handlers = handlers;
        return this;
    }

    /**
     * Adds a new predicated handler.
     * <p>
     *
     * @param predicate
     * @param handlerWrapper
     */
    public PredicatesHandler addPredicatedHandler(final Predicate predicate, final HandlerWrapper handlerWrapper) {
        this.addPredicatedHandler(predicate, handlerWrapper, null);
        return this;
    }

    public PredicatesHandler addPredicatedHandler(final PredicatedHandler handler) {
        return addPredicatedHandler(handler.getPredicate(), handler.getHandler(), handler.getElseHandler());
    }

    public void setNext(HttpHandler next) {
        this.next = next;
    }

    public HttpHandler getNext() {
        return next;
    }

    private static final class Holder {
        final Predicate predicate;
        final HttpHandler handler;
        final HttpHandler elseBranch;

        private Holder(Predicate predicate, HttpHandler handler, HttpHandler elseBranch) {
            this.predicate = predicate;
            this.handler = handler;
            this.elseBranch = elseBranch;
        }
    }

    public static final class DoneHandlerBuilder implements HandlerBuilder {

        @Override
        public String name() {
            return "done";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.emptyMap();
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new HandlerWrapper() {
                @Override
                public HttpHandler wrap(final HttpHandler handler) {
                    return new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            exchange.putAttachment(DONE, true);
                            handler.handleRequest(exchange);
                        }
                    };
                }
            };
        }
    }

    public static final class RestartHandlerBuilder implements HandlerBuilder {

        private static final AttachmentKey<Integer> RESTART_COUNT = AttachmentKey.create(Integer.class);

        private static final int MAX_RESTARTS = Integer.getInteger("io.undertow.max_restarts", 1000);

        @Override
        public String name() {
            return "restart";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.emptyMap();
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new HandlerWrapper() {
                @Override
                public HttpHandler wrap(final HttpHandler handler) {
                    return new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            Integer restarts = exchange.getAttachment(RESTART_COUNT);
                            if(restarts == null) {
                                restarts = 1;
                            } else {
                                restarts++;
                            }
                            exchange.putAttachment(RESTART_COUNT, restarts);
                            if(restarts > MAX_RESTARTS) {
                                throw UndertowLogger.ROOT_LOGGER.maxRestartsExceeded(MAX_RESTARTS);
                            }
                            exchange.putAttachment(RESTART, true);
                        }
                    };
                }
            };
        }
    }


    public static class Wrapper implements HandlerWrapper {

        private final List<PredicatedHandler> handlers;
        private final boolean outerHandler;

        public Wrapper(List<PredicatedHandler> handlers, boolean outerHandler) {
            this.handlers = handlers;
            this.outerHandler = outerHandler;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            PredicatesHandler h = new PredicatesHandler(handler, outerHandler);
            for(PredicatedHandler pred : handlers) {
                h.addPredicatedHandler(pred.getPredicate(), pred.getHandler());
            }
            return h;
        }
    }
}
