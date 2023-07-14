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

import java.nio.charset.Charset;

/**
 * @author Stuart Douglas
 */
class ContentTypeInfo {
    private final String header;
    private final Charset charset;
    private final String contentType;

    ContentTypeInfo(String header, String charset, String contentType) {
        Charset charset1 = null;
        this.header = header;
        if (charset != null) {
            try {
                charset1 = Charset.forName(charset);
            } catch (IllegalArgumentException iae) { // unrecognized charset
            }
        }
        this.charset = charset1;
        this.contentType = contentType;
    }

    public String getHeader() {
        return header;
    }

    public Charset getCharset() {
        return charset;
    }

    public String getContentType() {
        return contentType;
    }
}
