/*
 * Copyright 2013 JBoss, by Red Hat, Inc
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
package io.undertow.websockets.impl;

import io.undertow.websockets.api.WebSocketSessionIdGenerator;

import java.util.UUID;

/**
 * {@link WebSocketSessionIdGenerator} implementation which use {@link UUID#randomUUID()}  to generate the id
 * to use. In a Clustered enviroment you may need something better.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class UuidWebSocketSessionIdGenerator implements WebSocketSessionIdGenerator {

    @Override
    public String nextId() {
        return UUID.randomUUID().toString();
    }
}
