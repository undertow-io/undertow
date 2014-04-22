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
package io.undertow.security.api;

import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;

/**
 * Notification representing a security event such as a successful or failed authentication.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SecurityNotification {

    private final HttpServerExchange exchange;
    private final EventType eventType;
    private final Account account;
    private final String mechanism;
    private final boolean programatic;
    private final String message;
    private final boolean cachingRequired;

    public SecurityNotification(final HttpServerExchange exchange, final EventType eventType, final Account account, final String mechanism, final boolean programatic, final String message, boolean cachingRequired) {
        this.exchange = exchange;
        this.eventType = eventType;
        this.account = account;
        this.mechanism = mechanism;
        this.programatic = programatic;
        this.message = message;
        this.cachingRequired = cachingRequired;
    }

    public HttpServerExchange getExchange() {
        return exchange;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Account getAccount() {
        return account;
    }

    public String getMechanism() {
        return mechanism;
    }

    public boolean isProgramatic() {
        return programatic;
    }

    public String getMessage() {
        return message;
    }

    public boolean isCachingRequired() {
        return cachingRequired;
    }

    public enum EventType {
        AUTHENTICATED, FAILED_AUTHENTICATION, LOGGED_OUT;
    }

}
