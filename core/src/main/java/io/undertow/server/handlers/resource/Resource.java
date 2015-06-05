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

package io.undertow.server.handlers.resource;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ETag;
import io.undertow.util.MimeMappings;

/**
 * Representation of a static resource.
 *
 * @author Stuart Douglas
 */
public interface Resource {

    /**
     *
     * @return The path from the resource manager root
     */
    String getPath();

    /**
     * @return The last modified date of this resource, or null if this cannot be determined
     */
    Date getLastModified();

    /**
     * @return A string representation of the last modified date, or null if this cannot be determined
     */
    String getLastModifiedString();

    /**
     * @return The resources etags
     */
    ETag getETag();

    /**
     * @return The name of the resource
     */
    String getName();

    /**
     * @return <code>true</code> if this resource represents a directory
     */
    boolean isDirectory();

    /**
     * @return a list of resources in this directory
     */
    List<Resource> list();

    /**
     * Return the resources content type. In most cases this will simply use the provided
     * mime mappings, however in some cases the resource may have additional information as
     * to the actual content type.
     */
    String getContentType(final MimeMappings mimeMappings);

    /**
     * Serve the resource, and call the provided callback when complete.
     *
     * @param sender The sender to use.
     * @param exchange The exchange
     */
    void serve(final Sender sender, final HttpServerExchange exchange, final IoCallback completionCallback);

    /**
     * @return The content length, or null if it is unknown
     */
    Long getContentLength();

    /**
     * @return A string that uniquely identifies this resource
     */
    String getCacheKey();

    /**
     * @return The underlying file that matches the resource. This may return null if the resource does not map to a file
     */
    File getFile();

    /**
     * @return The underlying file that matches the resource. This may return null if the resource does not map to a file
     */
    Path getFilePath();

    /**
     * Returns the resource manager root. If the resource manager has multiple roots then this returns the one that
     * is the parent of this resource.
     *
     * @return a file representing the resource manager root. This may return null if the resource does not map to a file
     */
    File getResourceManagerRoot();

    /**
     * Returns the resource manager root. If the resource manager has multiple roots then this returns the one that
     * is the parent of this resource.
     *
     * @return a path representing the resource manager root. This may return null if the resource does not map to a file
     */
    Path getResourceManagerRootPath();

    /**
     * @return The URL of the resource
     */
    URL getUrl();
}
