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

package io.undertow.server.handlers.form;

import java.io.IOException;

import org.xnio.IoFuture;

/**
 * Parser for form data. This can be used by down-stream handlers to parse
 * form data.
 *
 *
 *
 * @author Stuart Douglas
 */
public interface FormDataParser {

    String ATTACHMENT_KEY = FormDataParser.class.getName();

    /**
     * Parse the form data asynchronously. If all the data cannot be read immediately then a read listener will be
     * registered, and the data will be parsed by the read thread.
     *
     * @return An IoFuture that can be used to retrieve the parsed data
     */
    IoFuture<FormData> parse();

    /**
     * Parse the data, blocking the current thread until parsing is complete. For blocking handlers this method is
     * more efficient than {@link #parse()}, as the calling thread should do that actual parsing, rather than the
     * read thread
     * @return The parsed form data
     * @throws IOException If the data could not be read
     */
    FormData parseBlocking() throws IOException;

}
