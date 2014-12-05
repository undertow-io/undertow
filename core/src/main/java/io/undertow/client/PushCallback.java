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

package io.undertow.client;


/**
 * @author Stuart Douglas
 */
public interface PushCallback {

    /**
     * Handles a server push. If the push cannot be handled for some reason, this method
     * should return false and the underlying
     * @param originalRequest The request that initiated the push
     * @param pushedRequest The pushed request
     * @return <code>false</code> if the server wants the push to be rejected
     */
    boolean handlePush(ClientExchange originalRequest, ClientExchange pushedRequest);
}
