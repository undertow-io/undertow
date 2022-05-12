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

package io.undertow.servlet.api;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import io.undertow.server.HttpServerExchange;

/**
 * An exception handler allows you to perform custom actions when an exception propagates out of the servlet
 * handler chain. The default handler will simply log the exception, however it is possible to write custom
 * handlers to handle the error however you want. A common use for this would be to change the log format for
 * exceptions, or possibly suppress the logging for certain exceptions types.
 * <p>
 * Implementations of this interface may also choose to suppress error page handler, and handle error page generation
 * internally by returning <code>true</code>
 *
 * @author Stuart Douglas
 */
public interface ExceptionHandler {

    /**
     * Handles an exception. If this method returns true then the request/response cycle is considered to be finished,
     * and no further action will take place, if this returns false then standard error page redirect will take place.
     *
     * The default implementation of this simply logs the exception and returns false, allowing error page and async context
     * error handling to proceed as normal.
     *
     * @param exchange        The exchange
     * @param request         The request
     * @param response        The response
     * @param throwable       The exception
     * @return <code>true</code> true if the error was handled by this method
     */
    boolean handleThrowable(final HttpServerExchange exchange, ServletRequest request, ServletResponse response, Throwable throwable);
}
