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

package io.undertow.server.handlers.proxy;

import io.undertow.server.HttpServerExchange;

/**
 * Interface that is used to determine if a connection should be exclusive.
 *
 * If a connection is exclusive then it is removed from the connection pool, and a one
 * to one mapping will be maintained between the front and back end servers.
 *
* @author Stuart Douglas
*/
public interface ExclusivityChecker {

    boolean isExclusivityRequired(HttpServerExchange exchange);

}
