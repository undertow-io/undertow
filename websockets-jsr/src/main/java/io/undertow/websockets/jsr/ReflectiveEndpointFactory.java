/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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
package io.undertow.websockets.jsr;

import javax.websocket.Endpoint;

/**
 * {@link EndpointFactory} implementation which use reflect to create new {@link Endpoint}s.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class ReflectiveEndpointFactory implements EndpointFactory {
    @Override
    public Endpoint createEndpoint(Class<?> endpointClass) throws InstantiationException {
        if (Endpoint.class.isAssignableFrom(endpointClass)) {
            try {
                return (Endpoint) endpointClass.newInstance();
            } catch (IllegalAccessException e) {
                throw new InstantiationException(e.getMessage());
            }
        }
        throw JsrWebSocketMessages.MESSAGES.unableToInstanceEndpoint(endpointClass);
    }
}
