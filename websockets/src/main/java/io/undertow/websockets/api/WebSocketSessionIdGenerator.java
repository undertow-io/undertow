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
package io.undertow.websockets.api;

/**
 * Generates Id for {@link WebSocketSession}s.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface WebSocketSessionIdGenerator {

    /**
     * Generate an id to use for a {@link WebSocketSession}. The generated id must be unique during the lifetime of the
     * application.
     */
    String nextId();
}
