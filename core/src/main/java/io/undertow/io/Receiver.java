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

package io.undertow.io;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.RequestTooBigException;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Interface that provides an easy way to read data from the request. It is lambda compatible.
 *
 * @author Stuart Douglas
 */
public interface Receiver {

    /**
     * Sets the maximum amount of data that will be buffered in memory. If you call a receiveFull* method
     * and the request size is larger than this amount then the error callback with be invoked with a
     * {@link RequestTooBigException}.
     *
     * @param maxBufferSize The maximum amount of data to be buffered
     */
    void setMaxBufferSize(int maxBufferSize);

    /**
     *
     * Reads the request and invokes the callback when the request body has been fully read.
     *
     * This string will be interpreted according to {@link java.nio.charset.StandardCharsets#ISO_8859_1}.
     *
     * If there is an error reading the request the error callback will be invoked.
     *
     * @param callback The callback to invoke with the request
     * @param errorCallback The callback that is invoked on error
     */
    void receiveFullString(FullStringCallback callback, ErrorCallback errorCallback);

    /**
     *
     * Reads the request and invokes the callback when the request body has been fully read.
     *
     * This string will be interpreted according to {@link java.nio.charset.StandardCharsets#ISO_8859_1}.
     *
     * If there is an error the exchange will be ended.
     *
     * @param callback The callback to invoke with the request
     */
    void receiveFullString(FullStringCallback callback);

    /**
     *
     * Reads the request and invokes the callback with request data. The callback may be invoked multiple
     * times, and on the last time the last parameter will be true.
     *
     * This string will be interpreted according to {@link java.nio.charset.StandardCharsets#ISO_8859_1}.
     *
     * If there is an error reading the request the error callback will be invoked.
     *
     * @param callback The callback to invoke with the request
     * @param errorCallback The callback that is invoked on error
     */
    void receivePartialString(PartialStringCallback callback, ErrorCallback errorCallback);

    /**
     *
     * Reads the request and invokes the callback with request data. The callback may be invoked multiple
     * times, and on the last time the last parameter will be true.
     *
     * This string will be interpreted according to {@link java.nio.charset.StandardCharsets#ISO_8859_1}.
     *
     * If there is an error the exchange will be ended.
     *
     * @param callback The callback to invoke with the request
     */
    void receivePartialString(PartialStringCallback callback);

    /**
     *
     * Reads the request and invokes the callback when the request body has been fully read.
     *
     * This string will be interpreted according to the specified charset.
     *
     * If there is an error reading the request the error callback will be invoked.
     *
     * @param callback The callback to invoke with the request
     * @param errorCallback The callback that is invoked on error
     * @param charset The charset that is used to interpret the string
     */
    void receiveFullString(FullStringCallback callback, ErrorCallback errorCallback, Charset charset);

    /**
     *
     * Reads the request and invokes the callback when the request body has been fully read.
     *
     * This string will be interpreted according to the specified charset.
     *
     * If there is an error the exchange will be ended.
     *
     * @param callback The callback to invoke with the request
     * @param charset The charset that is used to interpret the string
     */
    void receiveFullString(FullStringCallback callback, Charset charset);

    /**
     *
     * Reads the request and invokes the callback with request data. The callback may be invoked multiple
     * times, and on the last time the last parameter will be true.
     *
     * This string will be interpreted according to the specified charset.
     *
     * If there is an error reading the request the error callback will be invoked.
     *
     * @param callback The callback to invoke with the request
     * @param errorCallback The callback that is invoked on error
     * @param charset The charset that is used to interpret the string
     */
    void receivePartialString(PartialStringCallback callback, ErrorCallback errorCallback, Charset charset);

    /**
     *
     * Reads the request and invokes the callback with request data. The callback may be invoked multiple
     * times, and on the last time the last parameter will be true.
     *
     * This string will be interpreted according to the specified charset.
     *
     * If there is an error the exchange will be ended.
     *
     * @param callback The callback to invoke with the request
     * @param charset The charset that is used to interpret the string
     */
    void receivePartialString(PartialStringCallback callback, Charset charset);

    /**
     *
     * Reads the request and invokes the callback when the request body has been fully read.
     *
     * If there is an error reading the request the error callback will be invoked.
     *
     * @param callback The callback to invoke with the request
     * @param errorCallback The callback that is invoked on error
     */
    void receiveFullBytes(FullBytesCallback callback, ErrorCallback errorCallback);

    /**
     *
     * Reads the request and invokes the callback when the request body has been fully read.
     *
     * If there is an error the exchange will be ended.
     *
     * @param callback The callback to invoke with the request
     */
    void receiveFullBytes(FullBytesCallback callback);

    /**
     *
     * Reads the request and invokes the callback with request data. The callback may be invoked multiple
     * times, and on the last time the last parameter will be true.
     *
     * If there is an error reading the request the error callback will be invoked.
     *
     * @param callback The callback to invoke with the request
     * @param errorCallback The callback that is invoked on error
     */
    void receivePartialBytes(PartialBytesCallback callback, ErrorCallback errorCallback);

    /**
     *
     * Reads the request and invokes the callback with request data. The callback may be invoked multiple
     * times, and on the last time the last parameter will be true.
     *
     *
     * If there is an error the exchange will be ended.
     *
     * @param callback The callback to invoke with the request
     */
    void receivePartialBytes(PartialBytesCallback callback);

    /**
     * When receiving partial data calling this method will pause the callbacks. Callbacks will not resume until
     * {@link #resume()} has been called.
     */
    void pause();

    /**
     * Resumes paused callbacks.
     */
    void resume();

    interface ErrorCallback {
        void error(HttpServerExchange exchange, IOException e);
    }

    interface FullStringCallback {
        void handle(HttpServerExchange exchange, String message);
    }

    interface FullBytesCallback {
        void handle(HttpServerExchange exchange, byte[] message);
    }
    interface PartialStringCallback {
        void handle(HttpServerExchange exchange, String message, boolean last);
    }

    interface PartialBytesCallback {
        void handle(HttpServerExchange exchange, byte[] message, boolean last);
    }

    class RequestToLargeException extends IOException {}
}
