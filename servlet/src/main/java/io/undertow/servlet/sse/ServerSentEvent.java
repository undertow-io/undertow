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

package io.undertow.servlet.sse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * Annotation that can be applied to classes that implement {@link io.undertow.server.handlers.sse.ServerSentEventConnectionCallback}
 *
 * These classes will then have handlers registered under the given path. This path is a path template, any
 * path parameter values can be retrieved from {@link io.undertow.server.handlers.sse.ServerSentEventConnection#getParameter(String)}
 *
 * Only a single instance of the callback will be created at deployment time.
 *
 * @author Stuart Douglas
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ServerSentEvent {

    /**
     * The path to register this SSE handler. This path can be a path template.
     */
    String value();

}
