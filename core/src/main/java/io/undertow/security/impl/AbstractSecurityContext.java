/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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
package io.undertow.security.impl;

import static io.undertow.UndertowMessages.MESSAGES;

import io.undertow.UndertowLogger;
import io.undertow.security.api.NotificationReceiver;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.api.SecurityNotification;
import io.undertow.security.api.SecurityNotification.EventType;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;

/**
 * A base class for {@link SecurityContext} implementations predominantly focusing on the notification handling allowing the
 * specific implementation for focus on authentication.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class AbstractSecurityContext implements SecurityContext {

    private boolean authenticationRequired;
    protected final HttpServerExchange exchange;

    private Node<NotificationReceiver> notificationReceivers = null;

    private Account account;
    private String mechanismName;

    protected AbstractSecurityContext(final HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public void setAuthenticationRequired() {
        authenticationRequired = true;
    }

    @Override
    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    @Override
    public boolean isAuthenticated() {
        return account != null;
    }

    @Override
    public Account getAuthenticatedAccount() {
        return account;
    }

    /**
     * @return The name of the mechanism used to authenticate the request.
     */
    @Override
    public String getMechanismName() {
        return mechanismName;
    }

    @Override
    public void authenticationComplete(Account account, String mechanism, final boolean cachingRequired) {
        authenticationComplete(account, mechanism, false, cachingRequired);
    }

    protected void authenticationComplete(Account account, String mechanism, boolean programatic, final boolean cachingRequired) {
        this.account = account;
        this.mechanismName = mechanism;

        UndertowLogger.SECURITY_LOGGER.debugf("Authenticated as %s, roles %s", account.getPrincipal().getName(), account.getRoles());
        sendNoticiation(new SecurityNotification(exchange, EventType.AUTHENTICATED, account, mechanism, programatic,
                MESSAGES.userAuthenticated(account.getPrincipal().getName()), cachingRequired));
    }

    @Override
    public void authenticationFailed(String message, String mechanism) {
        UndertowLogger.SECURITY_LOGGER.debugf("Authentication failed with message %s and mechanism %s for %s", message, mechanism, exchange);
        sendNoticiation(new SecurityNotification(exchange, EventType.FAILED_AUTHENTICATION, null, mechanism, false, message, true));
    }

    @Override
    public void registerNotificationReceiver(NotificationReceiver receiver) {
        if(notificationReceivers == null) {
            notificationReceivers = new Node<>(receiver);
        } else {
            Node<NotificationReceiver> cur = notificationReceivers;
            while (cur.next != null) {
                cur = cur.next;
            }
            cur.next = new Node<>(receiver);
        }
    }

    @Override
    public void removeNotificationReceiver(NotificationReceiver receiver) {
        Node<NotificationReceiver> cur = notificationReceivers;
        if(receiver.equals(cur.item)) {
            notificationReceivers = cur.next;
        } else {
            Node<NotificationReceiver> old = cur;
            while (cur.next != null) {
                cur = cur.next;
                if(receiver.equals(cur.item)) {
                    old.next = cur.next;
                }
                old = cur;
            }
        }
    }

    private void sendNoticiation(final SecurityNotification notification) {
        Node<NotificationReceiver> cur = notificationReceivers;
        while (cur != null) {
            cur.item.handleNotification(notification);
            cur = cur.next;
        }
    }

    @Override
    public void logout() {
        if (!isAuthenticated()) {
            return;
        }
        UndertowLogger.SECURITY_LOGGER.debugf("Logged out %s", exchange);
        sendNoticiation(new SecurityNotification(exchange, SecurityNotification.EventType.LOGGED_OUT, account, mechanismName, true,
                MESSAGES.userLoggedOut(account.getPrincipal().getName()), true));

        this.account = null;
        this.mechanismName = null;
    }

    /**
     * To reduce allocations we use a custom linked list data structure
     * @param <T>
     */
    protected static final class Node<T> {
        final T item;
        Node<T> next;

        private Node(T item) {
            this.item = item;
        }
    }

}
