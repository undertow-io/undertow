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

package io.undertow.protocols.http2;

/**
 * Exception that is thrown when the HPACK compress context is broken.
 * <p>
 * In this case the connection must be closed.
 */
public class HpackException extends Exception {

    private final int closeCode;

    public HpackException() {
        this(null, Http2Channel.ERROR_COMPRESSION_ERROR);
    }

    public HpackException(String message, int closeCode) {
        super(message);
        this.closeCode = closeCode;
    }

    public HpackException(int closeCode) {
        this.closeCode = closeCode;
    }

    public int getCloseCode() {
        return closeCode;
    }
}
