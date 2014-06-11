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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class WebResourceCollection implements Cloneable {

    private final Set<String> httpMethods = new HashSet<>();
    private final Set<String> httpMethodOmissions = new HashSet<>();
    private final Set<String> urlPatterns = new HashSet<>();

    public WebResourceCollection addHttpMethod(final String s) {
        httpMethods.add(s);
        return this;
    }

    public WebResourceCollection addHttpMethods(final String... s) {
        httpMethods.addAll(Arrays.asList(s));
        return this;
    }

    public WebResourceCollection addHttpMethods(final Collection<String> s) {
        httpMethods.addAll(s);
        return this;
    }

    public WebResourceCollection addUrlPattern(final String s) {
        urlPatterns.add(s);
        return this;
    }

    public WebResourceCollection addUrlPatterns(final String... s) {
        urlPatterns.addAll(Arrays.asList(s));
        return this;
    }

    public WebResourceCollection addUrlPatterns(final Collection<String> s) {
        urlPatterns.addAll(s);
        return this;
    }

    public WebResourceCollection addHttpMethodOmission(final String s) {
        httpMethodOmissions.add(s);
        return this;
    }

    public WebResourceCollection addHttpMethodOmissions(final String... s) {
        httpMethodOmissions.addAll(Arrays.asList(s));
        return this;
    }

    public WebResourceCollection addHttpMethodOmissions(final Collection<String> s) {
        httpMethodOmissions.addAll(s);
        return this;
    }

    public Set<String> getHttpMethodOmissions() {
        return httpMethodOmissions;
    }

    public Set<String> getUrlPatterns() {
        return urlPatterns;
    }

    public Set<String> getHttpMethods() {
        return httpMethods;
    }

    @Override
    protected WebResourceCollection clone() {
        return new WebResourceCollection()
                .addHttpMethodOmissions(httpMethodOmissions)
                .addHttpMethods(httpMethods)
                .addUrlPatterns(urlPatterns);

    }
}
