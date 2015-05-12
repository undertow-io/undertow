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
package io.undertow.servlet.api;

import io.undertow.server.HttpServerExchange;

/**
 * A utility to take the {@link HttpServerExchange} of the current request and obtain the number of the port number to use in
 * https redirects.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface ConfidentialPortManager {

    /**
     * Obtain the port number to redirect the current request to to provide the transport guarantee of CONDIFENTIAL.
     *
     * @param exchange The current {@link HttpServerExchange} being redirected.
     * @return The port to use in the redirection URI or {@code -1} if no configured port is available.
     */
    int getConfidentialPort(final HttpServerExchange exchange);

}
