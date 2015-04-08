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

package io.undertow.security.handlers;

import java.util.Collection;

import io.undertow.security.api.NotificationReceiver;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * A {@link HttpHandler} to register a list of {@link NotificationReceiver} instances with the current {@link SecurityContext}.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class NotificationReceiverHandler implements HttpHandler {

    private final HttpHandler next;
    private final NotificationReceiver[] receivers;

    public NotificationReceiverHandler(final HttpHandler next, final Collection<NotificationReceiver> receivers) {
        this.next = next;
        this.receivers = receivers.toArray(new NotificationReceiver[receivers.size()]);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        SecurityContext sc = exchange.getSecurityContext();
        for (int i = 0; i < receivers.length; ++i) {
            sc.registerNotificationReceiver(receivers[i]);
        }

        next.handleRequest(exchange);
    }

}
