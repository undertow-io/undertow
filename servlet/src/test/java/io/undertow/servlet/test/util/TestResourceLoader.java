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

package io.undertow.servlet.test.util;

import java.io.File;
import java.net.URL;

import io.undertow.servlet.api.ResourceLoader;

/**
 * @author Stuart Douglas
 */
public class TestResourceLoader implements ResourceLoader {

    public static final ResourceLoader NOOP_RESOURCE_LOADER  = new ResourceLoader() {
        @Override
        public File getResource(final String resource) {
            return null;
        }
    };

    public final Class<?> testClass;

    public TestResourceLoader(final Class<?> testClass) {
        this.testClass = testClass;
    }

    @Override
    public File getResource(String resource) {
        if (resource.startsWith("/")) {
            resource = resource.substring(1);
        }
        URL url = testClass.getResource(resource);
        if(url == null) {
            return null;
        }
        return new File(url.getFile());
    }

}
