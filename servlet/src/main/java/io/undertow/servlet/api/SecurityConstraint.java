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
import java.util.List;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class SecurityConstraint extends SecurityInfo<SecurityConstraint> {

    private final Set<WebResourceCollection> webResourceCollections = new HashSet<>();

    public Set<WebResourceCollection> getWebResourceCollections() {
        return Collections.unmodifiableSet(webResourceCollections);
    }

    public SecurityConstraint addWebResourceCollection(final WebResourceCollection webResourceCollection) {
        this.webResourceCollections.add(webResourceCollection);
        return this;
    }

    public SecurityConstraint addWebResourceCollections(final WebResourceCollection... webResourceCollection) {
        this.webResourceCollections.addAll(Arrays.asList(webResourceCollection));
        return this;
    }

    public SecurityConstraint addWebResourceCollections(final List<WebResourceCollection> webResourceCollections) {
        this.webResourceCollections.addAll(webResourceCollections);
        return this;
    }

    @Override
    protected SecurityConstraint createInstance() {
        return new SecurityConstraint();
    }

    @Override
    public SecurityConstraint clone() {
        SecurityConstraint info = super.clone();
        for (WebResourceCollection wr : webResourceCollections) {
            info.addWebResourceCollection(wr.clone());
        }
        return info;
    }

}
