/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.util;

/**
 * NOTE: If you add a new method here you must also add it to {@link io.undertow.server.protocol.http.HttpRequestParser}
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpMethodNames {

    private HttpMethodNames() {
    }

    public static final String OPTIONS = "OPTIONS";
    public static final String GET = "GET";
    public static final String HEAD = "HEAD";
    public static final String POST = "POST";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String TRACE = "TRACE";
    public static final String CONNECT = "CONNECT";
    public static final String PATCH = "PATCH";
    public static final String PROPFIND = "PROPFIND";
    public static final String PROPPATCH = "PROPPATCH";
    public static final String MKCOL = "MKCOL";
    public static final String COPY = "COPY";
    public static final String MOVE = "MOVE";
    public static final String LOCK = "LOCK";
    public static final String UNLOCK = "UNLOCK";
    public static final String ACL = "ACL";
    public static final String REPORT = "REPORT";
    public static final String VERSION_CONTROL = "VERSION-CONTROL";
    public static final String CHECKIN = "CHECKIN";
    public static final String CHECKOUT = "CHECKOUT";
    public static final String UNCHECKOUT = "UNCHECKOUT";
    public static final String SEARCH = "SEARCH";
    public static final String MKWORKSPACE = "MKWORKSPACE";
    public static final String UPDATE = "UPDATE";
    public static final String LABEL = "LABEL";
    public static final String MERGE = "MERGE";
    public static final String BASELINE_CONTROL = "BASELINE_CONTROL";
    public static final String MKACTIVITY = "MKACTIVITY";

}
