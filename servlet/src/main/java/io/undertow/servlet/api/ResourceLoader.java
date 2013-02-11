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

package io.undertow.servlet.api;

import java.io.File;

/**
 * @author Stuart Douglas
 */
public interface ResourceLoader {

    /**
     * Gets the resource at the specified location, as long as it exists.
     *
     * @param resource The resource to load, relative to the servlet context root
     * @return The file, or null if it does not exist
     */
    File getResource(final String resource);

   ResourceLoader EMPTY_RESOURCE_LOADER = new ResourceLoader() {
        @Override
        public File getResource(final String resource) {
            return null;
        }
    };
}
