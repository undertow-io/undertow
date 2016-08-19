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

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.ExceptionLog;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * An exception handler that
 *
 *
 * @author Stuart Douglas
 */
public class LoggingExceptionHandler implements ExceptionHandler {

    public static final LoggingExceptionHandler DEFAULT = new LoggingExceptionHandler(Collections.<Class<? extends Throwable>, ExceptionDetails>emptyMap());

    private final Map<Class<? extends Throwable>, ExceptionDetails> exceptionDetails;

    public LoggingExceptionHandler(Map<Class<? extends Throwable>, ExceptionDetails> exceptionDetails) {
        this.exceptionDetails = exceptionDetails;
    }

    @Override
    public boolean handleThrowable(HttpServerExchange exchange, ServletRequest request, ServletResponse response, Throwable t) {
        ExceptionDetails details = null;
        if (!exceptionDetails.isEmpty()) {
            Class c = t.getClass();
            while (c != null && c != Object.class) {
                details = exceptionDetails.get(c);
                if (details != null) {
                    break;
                }
                c = c.getSuperclass();
            }
        }

        ExceptionLog log = t.getClass().getAnnotation(ExceptionLog.class);
        if (details != null) {
            Logger.Level level = details.level;
            Logger.Level stackTraceLevel = details.stackTraceLevel;
            String category = details.category;
            handleCustomLog(exchange, t, level, stackTraceLevel, category);
        } else if (log != null) {
            Logger.Level level = log.value();
            Logger.Level stackTraceLevel = log.stackTraceLevel();
            String category = log.category();
            handleCustomLog(exchange, t, level, stackTraceLevel, category);
        } else if (t instanceof IOException) {
            //we log IOExceptions at a lower level
            //because they can be easily caused by malicious remote clients in at attempt to DOS the server by filling the logs
            UndertowLogger.REQUEST_IO_LOGGER.debugf(t, "Exception handling request to %s", exchange.getRequestURI());
        } else {
            UndertowLogger.REQUEST_LOGGER.exceptionHandlingRequest(t, exchange.getRequestURI());
        }
        return false;
    }

    private void handleCustomLog(HttpServerExchange exchange, Throwable t, Logger.Level level, Logger.Level stackTraceLevel, String category) {
        BasicLogger logger = UndertowLogger.REQUEST_LOGGER;
        if (!category.isEmpty()) {
            logger = Logger.getLogger(category);
        }
        boolean stackTrace = true;
        if (stackTraceLevel.ordinal() > level.ordinal()) {
            if (!logger.isEnabled(stackTraceLevel)) {
                stackTrace = false;
            }
        }
        if (stackTrace) {
            logger.logf(level, t, "Exception handling request to %s", exchange.getRequestURI());
        } else {
            logger.logf(level, "Exception handling request to %s: %s", exchange.getRequestURI(), t.getMessage());
        }
    }


    private static class ExceptionDetails {

        final Logger.Level level;

        final Logger.Level stackTraceLevel;

        final String category;

        private ExceptionDetails(Logger.Level level, Logger.Level stackTraceLevel, String category) {
            this.level = level;
            this.stackTraceLevel = stackTraceLevel;
            this.category = category;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<Class<? extends Throwable>, ExceptionDetails> exceptionDetails = new HashMap<>();

        Builder() {}

        public Builder add(Class<? extends Throwable> exception, String category, Logger.Level level) {
            exceptionDetails.put(exception, new ExceptionDetails(level, Logger.Level.FATAL, category));
            return this;
        }
        public Builder add(Class<? extends Throwable> exception, String category) {
            exceptionDetails.put(exception, new ExceptionDetails(Logger.Level.ERROR, Logger.Level.FATAL, category));
            return this;
        }
        public Builder add(Class<? extends Throwable> exception, String category, Logger.Level level, Logger.Level stackTraceLevel) {
            exceptionDetails.put(exception, new ExceptionDetails(level, stackTraceLevel, category));
            return this;
        }

        public LoggingExceptionHandler build() {
            return new LoggingExceptionHandler(exceptionDetails);
        }
    }
}
