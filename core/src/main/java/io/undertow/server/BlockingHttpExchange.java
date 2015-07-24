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

package io.undertow.server;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.undertow.io.Receiver;
import io.undertow.io.Sender;


/**
 * An interface that provides the input and output streams for blocking HTTP requests.
 *
 * @author Stuart Douglas
 */
public interface BlockingHttpExchange extends Closeable {

    /**
     * Returns the input stream that is in use for this exchange.
     *
     * @return The input stream
     */
    InputStream getInputStream();

    /**
     * Returns the output stream that is in use for this exchange.
     *
     * In some circumstances this may not be available, such as if a writer
     * is being used for a servlet response
     *
     * @return The output stream
     */
    OutputStream getOutputStream();

    /**
     * Returns a sender based on the provided output stream
     *
     * @return A sender that uses the output stream
     */
    Sender getSender();

    /**
     * Closes both the input and output streams
     */
    void close() throws IOException;

    /**
     * returns a receiver based on the provided input stream.
     * @return The receiver
     */
    Receiver getReceiver();
}
