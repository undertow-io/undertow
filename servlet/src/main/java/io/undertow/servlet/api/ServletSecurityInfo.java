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

package io.undertow.servlet.api;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public class ServletSecurityInfo extends SecurityInfo<ServletSecurityInfo> implements Cloneable {

    private final List<HttpMethodSecurityInfo> httpMethodSecurityInfo = new ArrayList<>();

    @Override
    protected ServletSecurityInfo createInstance() {
        return new ServletSecurityInfo();
    }

    public ServletSecurityInfo addHttpMethodSecurityInfo(final HttpMethodSecurityInfo info) {
        httpMethodSecurityInfo.add(info);
        return this;
    }

    public List<HttpMethodSecurityInfo> getHttpMethodSecurityInfo() {
        return new ArrayList<>(httpMethodSecurityInfo);
    }

    @Override
    public ServletSecurityInfo clone() {
        ServletSecurityInfo info = super.clone();
        for(HttpMethodSecurityInfo method : httpMethodSecurityInfo) {
            info.httpMethodSecurityInfo.add(method.clone());
        }
        return info;
    }
}
