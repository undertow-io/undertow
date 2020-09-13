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

package io.undertow.predicate;

import java.util.Map;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * A predicate.
 *
 * This is mainly used by handlers as a way to decide if a request should have certain
 * processing applied, based on the given conditions.
 *
 * @author Stuart Douglas
 */
public interface Predicate {

    /**
     * Attachment key that can be used to store additional predicate context that allows the predicates to store
     * additional information. For example a predicate that matches on a regular expression can place additional
     * information about match groups into the predicate context.
     *
     * Predicates must not rely on this attachment being present, it will only be present if the predicate is being
     * used in a situation where this information may be required by later handlers.
     *
     */
    AttachmentKey<Map<String, Object>> PREDICATE_CONTEXT = AttachmentKey.create(Map.class);

    boolean resolve(final HttpServerExchange value);

}
