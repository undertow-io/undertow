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

/**
 * The interface to be interested by classes interested in processing security related notifications.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface NotificationReceiver {

    /**
     * Handle a security related notification.
     *
     * The {@link SecurityNotification} that is sent to be handled is a security event that has occurred, this is not an
     * opportunity for that event to be validated - any Exception thrown by the handler will be logged but will not affect the
     * result of the security event.
     *
     * The notifications are sent on the same thread that is currently processing the request that triggered the notification,
     * if the handling of the notification is likely to be blocking then it should be dispatched to it's own worker thread. The
     * one exception to this may be where the notification must be sure to have been handled before the response continues.
     *
     * @param notification
     */
    void handleNotification(final SecurityNotification notification);

}
