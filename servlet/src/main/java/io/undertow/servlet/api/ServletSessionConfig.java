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

import java.util.Set;

import io.undertow.server.session.CookieAttributes;
import jakarta.servlet.SessionTrackingMode;

/**
 *
 * Session config that gets
 *
 * @author Stuart Douglas
 */
public class ServletSessionConfig extends CookieAttributes<ServletSessionConfig>{

    public static final String DEFAULT_SESSION_ID = "JSESSIONID";

    private Set<SessionTrackingMode> sessionTrackingModes;

    private String name = DEFAULT_SESSION_ID;

    public ServletSessionConfig() {
        super();
        super.kernel.setPath(null);//this is to mimic original code which indicates it can be null
    }

    public String getName() {
        return name;
    }

    public ServletSessionConfig setName(final String name) {
        this.name = name;
        return this;
    }

    public Set<SessionTrackingMode> getSessionTrackingModes() {
        return sessionTrackingModes;
    }

    public ServletSessionConfig setSessionTrackingModes(final Set<SessionTrackingMode> sessionTrackingModes) {
        this.sessionTrackingModes = sessionTrackingModes;
        return this;
    }
}
