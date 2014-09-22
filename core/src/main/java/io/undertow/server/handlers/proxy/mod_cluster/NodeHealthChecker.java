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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.proxy.mod_cluster;

import io.undertow.client.ClientResponse;

/**
 * @author Emanuel Muckenhuber
 */
public interface NodeHealthChecker {

    /**
     * Check the response of a health check.
     *
     * @param response the client response
     * @return true if the response from the node is healthy
     */
    boolean checkResponse(final ClientResponse response);

    /**
     * Receiving a response is a success.
     */
    NodeHealthChecker NO_CHECK = new NodeHealthChecker() {
        @Override
        public boolean checkResponse(ClientResponse response) {
            return true;
        }
    };

    /**
     * Check that the response code is 2xx to 3xx.
     */
    NodeHealthChecker OK = new NodeHealthChecker() {
        @Override
        public boolean checkResponse(final ClientResponse response) {
            final int code = response.getResponseCode();
            return code >= 200 && code < 400;
        }
    };

}
