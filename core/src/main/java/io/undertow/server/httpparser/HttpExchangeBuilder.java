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

package io.undertow.server.httpparser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.undertow.util.HeaderMap;
import io.undertow.util.SecureHashMap;

/**
 *
 *
 * @author Stuart Douglas
 */
public class HttpExchangeBuilder {
    String method;
    String fullPath;
    String relativePath;
    String protocol;
    final HeaderMap headers = new HeaderMap();
    final Map<String, List<String>> queryParameters = new SecureHashMap<String, java.util.List<String>>();

    public String getMethod() {
        return method;
    }

    public String getFullPath() {
        return fullPath;
    }

    /**
     * This is the part of the path without the host name and port.
     *
     * For 99% of requests this will be the same as {@link #fullPath}, however the
     * RFC does allow the complete hostname to be specified in the path
     * (see http://tools.ietf.org/html/rfc2616#page-36, 5.1.2)
     */
    public String getRelativePath() {
        return relativePath;
    }

    public String getProtocol() {
        return protocol;
    }

    public HeaderMap getHeaders() {
        return headers;
    }

    public void addQueryParam(final String name, final String param) {
        List<String> list = queryParameters.get(name);
        if(list == null) {
            queryParameters.put(name, list = Collections.synchronizedList(new ArrayList<String>()));
        }
        list.add(param);
    }

    public Map<String, List<String>> getQueryParameters() {
        return queryParameters;
    }
}
