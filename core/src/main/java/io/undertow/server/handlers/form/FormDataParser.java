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

package io.undertow.server.handlers.form;

import java.io.Closeable;
import java.io.IOException;

import io.undertow.server.HttpHandler;
import io.undertow.util.AttachmentKey;

/**
 * Parser for form data. This can be used by down-stream handlers to parse
 * form data.
 * <p>
 * This parser must be closed to make sure any temporary files have been cleaned up.
 *
 * @author Stuart Douglas
 */
public interface FormDataParser extends Closeable {

    /**
     * When the form data is parsed it will be attached under this key.
     */
    AttachmentKey<FormData> FORM_DATA = AttachmentKey.create(FormData.class);

    /**
     * Parse the form data asynchronously. If all the data cannot be read immediately then a read listener will be
     * registered, and the data will be parsed by the read thread.
     * <p>
     * When this method completes the handler will be invoked, and the data
     * will be attached under {@link #FORM_DATA}.
     * <p>
     * The method can either invoke the next handler directly, or may delegate to the IO thread
     * to perform the parsing.
     */
    void parse(final HttpHandler next) throws Exception;

    /**
     * Parse the data, blocking the current thread until parsing is complete. For blocking handlers this method is
     * more efficient than {@link #parse(io.undertow.server.HttpHandler next)}, as the calling thread should do that
     * actual parsing, rather than the read thread
     *
     * @return The parsed form data
     * @throws IOException If the data could not be read
     */
    FormData parseBlocking() throws IOException;

    /**
     * Closes the parser, and removes and temporary files that may have been created.
     *
     * @throws IOException
     */
    void close() throws IOException;

    /**
     * Sets the character encoding that will be used by this parser. If the request is already processed this will have
     * no effect
     *
     * @param encoding The encoding
     */
    void setCharacterEncoding(String encoding);
}
