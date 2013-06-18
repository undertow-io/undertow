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

package io.undertow.servlet.spec;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.Part;

import io.undertow.server.handlers.form.FormData;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 * @author Stuart Douglas
 */
public class PartImpl implements Part {

    private final HttpString FILE_NAME = HttpString.tryFromString("File-Name");

    private final String name;
    private final FormData.FormValue formValue;

    public PartImpl(final String name, final FormData.FormValue formValue) {
        this.name = name;
        this.formValue = formValue;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new BufferedInputStream(new FileInputStream(formValue.getFile()));
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
        if (formValue.isFile()) {
            return formValue.getFile().length();
        } else {
            return formValue.getValue().length();
        }
    }

    @Override
    public void write(final String fileName) throws IOException {
        throw new IllegalStateException();
    }

    @Override
    public void delete() throws IOException {
        if (!formValue.getFile().delete()) {
            throw UndertowServletMessages.MESSAGES.deleteFailed(formValue.getFile());
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
        final Set<String> ret = new HashSet<String>();
        for (HttpString i : formValue.getHeaders().getHeaderNames()) {
            ret.add(i.toString());
        }
        return ret;
    }
}
