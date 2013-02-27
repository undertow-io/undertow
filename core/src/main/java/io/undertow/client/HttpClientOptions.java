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

package io.undertow.client;

import io.undertow.util.HttpString;
import org.xnio.Option;

/**
 * HTTP client options.
 *
 * @author Emanuel Muckenhuber
 */
public final class HttpClientOptions {

    private HttpClientOptions() {
        //
    }

    public static final Option<Integer> CONNECTION_TIMEOUT = Option.simple(HttpClientOptions.class, "CONNECTION_TIMEOUT", Integer.class);
    public static final Option<Boolean> HTTP_KEEP_ALIVE = Option.simple(HttpClientOptions.class, "HTTP_KEEP_ALIVE", Boolean.class);
    public static final Option<Boolean> HTTP_PIPELINING = Option.simple(HttpClientOptions.class, "HTTP_PIPELINING", Boolean.class);
    public static final Option<HttpString> PROTOCOL = Option.simple(HttpClientOptions.class, "PROTOCOL", HttpString.class);

}
