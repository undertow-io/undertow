/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

package io.undertow;

import io.undertow.util.AttachmentKey;
import org.xnio.Option;
import org.xnio.OptionMap;

/**
 * @author Stuart Douglas
 */
public class UndertowOptions {

    public static final AttachmentKey<OptionMap> ATTACHMENT_KEY = AttachmentKey.create(OptionMap.class);

    /**
     * The maximum size in bytes of a http request header.
     */
    public static final Option<Integer> MAX_HEADER_SIZE = Option.simple(UndertowOptions.class, "MAX_HEADER_SIZE", Integer.class);
    /**
     * The default size we allow for the HTTP header.
     */
    public static final int DEFAULT_MAX_HEADER_SIZE = 50 * 1024;

    /**
     * The maximum size of the HTTP entity body.
     */
    public static final Option<Long> MAX_ENTITY_SIZE = Option.simple(UndertowOptions.class, "MAX_ENTITY_SIZE", Long.class);

    /**
     * We do not have a default upload limit
     */
    public static final long DEFAULT_MAX_ENTITY_SIZE = -1;

    /**
     * If we should buffer pipelined requests. Defaults to false.
     */
    public static final Option<Boolean> BUFFER_PIPELINED_DATA = Option.simple(UndertowOptions.class, "BUFFER_PIPELINED_DATA", Boolean.class);

    /**
     * The idle timeout in milliseconds after which the channel will be closed.
     */
    public static final Option<Integer> IDLE_TIMEOUT = Option.simple(UndertowOptions.class, "IDLE_TIMEOUT", Integer.class);

    /**
     * The maximum number of parameters that will be parsed. This is used to protect against hash vulnerabilities.
     *
     * This applies to both query parameters, and to POST data, but is not cumulative (i.e. you can potentially have
     * max parameters * 2 total parameters).
     *
     * Defaults to 1000
     */
    public static final Option<Integer> MAX_PARAMETERS = Option.simple(UndertowOptions.class, "MAX_PARAMETERS", Integer.class);

    /**
     * The maximum number of headers that will be parsed. This is used to protect against hash vulnerabilities.
     *
     * Defaults to 200
     */
    public static final Option<Integer> MAX_HEADERS = Option.simple(UndertowOptions.class, "MAX_HEADERS", Integer.class);


    /**
     * The maximum number of cookies that will be parsed. This is used to protect against hash vulnerabilities.
     *
     * Defaults to 200
     */
    public static final Option<Integer> MAX_COOKIES = Option.simple(UndertowOptions.class, "MAX_COOKIES", Integer.class);

    private UndertowOptions() {

    }
}
