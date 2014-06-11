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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The default servlet config. By default this has quite a restrictive configuration, only allowing
 * extensions in common use in the web to be served.
 *
 * This class is deprecated, the default servlet should be configured via context params.
 *
 * @author Stuart Douglas
 */
@Deprecated
public class DefaultServletConfig {

    private static final String[] DEFAULT_ALLOWED_EXTENSIONS = {"js", "css", "png", "jpg", "gif", "html", "htm", "txt", "pdf"};
    private static final String[] DEFAULT_DISALLOWED_EXTENSIONS = {"class", "jar", "war", "zip", "xml"};

    private final boolean defaultAllowed;
    private final Set<String> allowed;
    private final Set<String> disallowed;

    public DefaultServletConfig(final boolean defaultAllowed, final Set<String> exceptions) {
        this.defaultAllowed = defaultAllowed;
        if(defaultAllowed) {
            disallowed = Collections.unmodifiableSet(new HashSet<>(exceptions));
            allowed = null;
        } else {
            allowed = Collections.unmodifiableSet(new HashSet<>(exceptions));
            disallowed = null;
        }
    }

    public DefaultServletConfig(final boolean defaultAllowed) {
        this.defaultAllowed = defaultAllowed;
        this.allowed = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(DEFAULT_ALLOWED_EXTENSIONS)));
        this.disallowed = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(DEFAULT_DISALLOWED_EXTENSIONS)));
    }

    public DefaultServletConfig() {
        this.defaultAllowed = false;
        this.allowed = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(DEFAULT_ALLOWED_EXTENSIONS)));
        this.disallowed = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(DEFAULT_DISALLOWED_EXTENSIONS)));
    }

    public boolean isDefaultAllowed() {
        return defaultAllowed;
    }

    public Set<String> getAllowed() {
        return allowed;
    }

    public Set<String> getDisallowed() {
        return disallowed;
    }
}
