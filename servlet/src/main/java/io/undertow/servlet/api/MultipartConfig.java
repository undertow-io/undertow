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

/**
 * @author Stuart Douglas
 */
public class MultipartConfig {

    private final String location;
    private final long maxFileSize;
    private final long fileSizeThreshold;

    public MultipartConfig(final String location, final long maxFileSize, final long fileSizeThreshold) {
        this.location = location;
        this.maxFileSize = maxFileSize;
        this.fileSizeThreshold = fileSizeThreshold;
    }

    public String getLocation() {
        return location;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public long getFileSizeThreshold() {
        return fileSizeThreshold;
    }

}
