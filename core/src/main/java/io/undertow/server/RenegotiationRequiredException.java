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

/**
 * Exception that is thrown that indicates that SSL renegotiation is required
 * in order to get a client cert.
 *
 * This will be thrown if a user attempts to retrieve a client cert and the SSL mode
 * is {@link org.xnio.SslClientAuthMode#NOT_REQUESTED}.
 *
 * @author Stuart Douglas
 */
public class RenegotiationRequiredException extends Exception {

    public RenegotiationRequiredException() {
    }

    public RenegotiationRequiredException(String message) {
        super(message);
    }

    public RenegotiationRequiredException(String message, Throwable cause) {
        super(message, cause);
    }

    public RenegotiationRequiredException(Throwable cause) {
        super(cause);
    }

    public RenegotiationRequiredException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
