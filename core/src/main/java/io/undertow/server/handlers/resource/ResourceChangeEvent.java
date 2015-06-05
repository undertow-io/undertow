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

package io.undertow.server.handlers.resource;

/**
 * An event that is fired when a resource is modified
 *
 * @author Stuart Douglas
 */
public class ResourceChangeEvent {

    private final String resource;
    private final Type type;

    public ResourceChangeEvent(String resource, Type type) {
        this.resource = resource;
        this.type = type;
    }

    public String getResource() {
        return resource;
    }

    public Type getType() {
        return type;
    }

    /**
     * Watched file event types.  More may be added in the future.
     */
    public enum Type {
        /**
         * A file was added in a directory.
         */
        ADDED,
        /**
         * A file was removed from a directory.
         */
        REMOVED,
        /**
         * A file was modified in a directory.
         */
        MODIFIED,
    }
}
