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

package io.undertow.server.handlers.encoding;

import io.undertow.server.handlers.resource.Resource;

/**
 * A resource that has been pre-compressed
 *
 * @author Stuart Douglas
 */
public class ContentEncodedResource {

    private final Resource resource;
    private final String contentEncoding;

    public ContentEncodedResource(Resource resource, String contentEncoding) {
        this.resource = resource;
        this.contentEncoding = contentEncoding;
    }

    public Resource getResource() {
        return resource;
    }

    public String getContentEncoding() {
        return contentEncoding;
    }
}
