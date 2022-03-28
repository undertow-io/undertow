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

package io.undertow.servlet.spec;

import java.util.Enumeration;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;

import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.util.IteratorEnumeration;

/**
 * @author Stuart Douglas
 */
public class ServletConfigImpl implements ServletConfig {

    private final ServletInfo servletInfo;
    private final ServletContext servletContext;

    public ServletConfigImpl(final ServletInfo servletInfo, final ServletContext servletContext) {
        this.servletInfo = servletInfo;
        this.servletContext = servletContext;
    }

    @Override
    public String getServletName() {
        return servletInfo.getName();
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public String getInitParameter(final String name) {
        if(name == null) {
            throw UndertowServletMessages.MESSAGES.nullName();
        }
        return servletInfo.getInitParams().get(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return new IteratorEnumeration<>(servletInfo.getInitParams().keySet().iterator());
    }
}
