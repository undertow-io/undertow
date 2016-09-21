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

package io.undertow.servlet.spec;

import io.undertow.server.handlers.form.FormData;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;

/**
 * @author Stuart Douglas
 */
public class PartImpl implements Part {

    private final String name;
    private final FormData.FormValue formValue;
    private final MultipartConfigElement config;
    private final ServletContextImpl servletContext;
    private final HttpServletRequestImpl servletRequest;

    public PartImpl(final String name, final FormData.FormValue formValue, MultipartConfigElement config,
                    ServletContextImpl servletContext, HttpServletRequestImpl servletRequest) {
        this.name = name;
        this.formValue = formValue;
        this.config = config;
        this.servletContext = servletContext;
        this.servletRequest = servletRequest;

    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (formValue.isFile()) {
            return new BufferedInputStream(Files.newInputStream(formValue.getPath()));
        } else {
            String requestedCharset = servletRequest.getCharacterEncoding();
            String charset = requestedCharset != null ? requestedCharset : servletContext.getDeployment().getDeploymentInfo().getDefaultEncoding();
            return new ByteArrayInputStream(formValue.getValue().getBytes(charset));
        }
    }

    @Override
    public String getContentType() {
        return formValue.getHeaders().getFirst(Headers.CONTENT_TYPE);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getSubmittedFileName() {
        return formValue.getFileName();
    }

    @Override
    public long getSize() {
        try {
            if (formValue.isFile()) {
                return Files.size(formValue.getPath());
            } else {
                return formValue.getValue().length();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(final String fileName) throws IOException {
        Path target = Paths.get(fileName);
        if(!target.isAbsolute()) {
            if(config.getLocation().isEmpty()) {
                target = servletContext.getDeployment().getDeploymentInfo().getTempPath().resolve(fileName);
            } else {
                target = Paths.get(config.getLocation(), fileName);
            }
        }
        try {
            Files.move(formValue.getPath(), target);
        } catch (IOException e) {
            Files.copy(formValue.getPath(), target);
        }
    }

    @Override
    public void delete() throws IOException {
        try {
            Files.delete(formValue.getPath());
        } catch (NoSuchFileException e) { //already deleted
        } catch (IOException e) {
            throw UndertowServletMessages.MESSAGES.deleteFailed(formValue.getPath());
        }
    }

    @Override
    public String getHeader(final String name) {
        return formValue.getHeaders().getFirst(new HttpString(name));
    }

    @Override
    public Collection<String> getHeaders(final String name) {
        HeaderValues values = formValue.getHeaders().get(new HttpString(name));
        return values == null ? Collections.<String>emptyList() : values;
    }

    @Override
    public Collection<String> getHeaderNames() {
        final Set<String> ret = new HashSet<>();
        for (HttpString i : formValue.getHeaders().getHeaderNames()) {
            ret.add(i.toString());
        }
        return ret;
    }
}
