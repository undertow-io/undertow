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
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.ETag;
import io.undertow.util.Headers;
import io.undertow.util.MimeMappings;
import io.undertow.util.QValueParser;

/**
 * A resource supplier that allows pre-compressed resources to be served if the client accepts the request.
 * <p>
 * This is done by checking for the existence of a pre-compressed file, and if it exists and the
 * client supports the encoding then the resource is returned for the pre compressed file
 *
 * @author Stuart Douglas
 */
public class PreCompressedResourceSupplier implements ResourceSupplier {

    private final ResourceManager resourceManager;
    private final Map<String, String> encodingMap = new CopyOnWriteMap<>();

    public PreCompressedResourceSupplier(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @Override
    public Resource getResource(HttpServerExchange exchange, String path) throws IOException {
        if(exchange.getRequestHeaders().contains(Headers.RANGE)) {
            //we don't use serve pre compressed resources for range requests
            return resourceManager.getResource(path);
        }
        Resource resource = getEncodedResource(exchange, path);
        if(resource == null) {
            return resourceManager.getResource(path);
        }
        return resource;
    }


    private Resource getEncodedResource(final HttpServerExchange exchange, String path) throws IOException {
        final List<String> res = exchange.getRequestHeaders().get(Headers.ACCEPT_ENCODING);
        if (res == null || res.isEmpty()) {
            return null;
        }
        final List<List<QValueParser.QValueResult>> found = QValueParser.parse(res);
        for (List<QValueParser.QValueResult> result : found) {
            for (final QValueParser.QValueResult value : result) {
                String extension = encodingMap.get(value.getValue());
                if(extension != null) {
                    String newPath = path + extension;
                    Resource resource = resourceManager.getResource(newPath);
                    if(resource != null && !resource.isDirectory()) {
                        return new Resource() {
                            @Override
                            public String getPath() {
                                return resource.getPath();
                            }

                            @Override
                            public Date getLastModified() {
                                return resource.getLastModified();
                            }

                            @Override
                            public String getLastModifiedString() {
                                return resource.getLastModifiedString();
                            }

                            @Override
                            public ETag getETag() {
                                return resource.getETag();
                            }

                            @Override
                            public String getName() {
                                return resource.getName();
                            }

                            @Override
                            public boolean isDirectory() {
                                return false;
                            }

                            @Override
                            public List<Resource> list() {
                                return resource.list();
                            }

                            @Override
                            public String getContentType(MimeMappings mimeMappings) {
                                String fileName = resource.getName();
                                String originalFileName = fileName.substring(0, fileName.length() - extension.length());
                                int index = originalFileName.lastIndexOf('.');
                                if (index != -1 && index != originalFileName.length() - 1) {
                                    return mimeMappings.getMimeType(originalFileName.substring(index + 1));
                                }
                                return null;
                            }

                            @Override
                            public void serve(Sender sender, HttpServerExchange exchange, IoCallback completionCallback) {
                                exchange.getResponseHeaders().put(Headers.CONTENT_ENCODING, value.getValue());
                                resource.serve(sender, exchange, completionCallback);
                            }

                            @Override
                            public Long getContentLength() {
                                return resource.getContentLength();
                            }

                            @Override
                            public String getCacheKey() {
                                return resource.getCacheKey();
                            }

                            @Override
                            public File getFile() {
                                return resource.getFile();
                            }

                            @Override
                            public Path getFilePath() {
                                return resource.getFilePath();
                            }

                            @Override
                            public File getResourceManagerRoot() {
                                return resource.getResourceManagerRoot();
                            }

                            @Override
                            public Path getResourceManagerRootPath() {
                                return resource.getResourceManagerRootPath();
                            }

                            @Override
                            public URL getUrl() {
                                return resource.getUrl();
                            }
                        };
                    }
                }
            }
        }
        return null;
    }


    public PreCompressedResourceSupplier addEncoding(String encoding, String extension) {
        encodingMap.put(encoding, extension);
        return this;
    }

    public PreCompressedResourceSupplier removeEncoding(String encoding) {
        encodingMap.remove(encoding);
        return this;
    }

}
