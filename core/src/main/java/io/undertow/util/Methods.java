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

package io.undertow.util;

/**
 *
 * NOTE: If you add a new method here you must also add it to {@link io.undertow.server.HttpParser}
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Methods {

    private Methods() {
    }

    public static final String OPTIONS_STRING = "OPTIONS";
    public static final String GET_STRING = "GET";
    public static final String HEAD_STRING = "HEAD";
    public static final String POST_STRING = "POST";
    public static final String PUT_STRING = "PUT";
    public static final String DELETE_STRING = "DELETE";
    public static final String TRACE_STRING = "TRACE";
    public static final String CONNECT_STRING = "CONNECT";

    public static final HttpString OPTIONS = new HttpString(OPTIONS_STRING);
    public static final HttpString GET = new HttpString(GET_STRING);
    public static final HttpString HEAD = new HttpString(HEAD_STRING);
    public static final HttpString POST = new HttpString(POST_STRING);
    public static final HttpString PUT = new HttpString(PUT_STRING);
    public static final HttpString DELETE = new HttpString(DELETE_STRING);
    public static final HttpString TRACE = new HttpString(TRACE_STRING);
    public static final HttpString CONNECT = new HttpString(CONNECT_STRING);


}
