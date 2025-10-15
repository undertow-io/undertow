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

package io.undertow.servlet.core;

import io.undertow.util.StatusCodes;

import java.util.Map;

import jakarta.servlet.ServletException;

/**
 * Class that maintains information about error page mappings.
 *
 * @author Stuart Douglas
 */
public class ErrorPages {

    private final Map<Integer, String> errorCodeLocations;
    private final Map<Class<? extends Throwable>, String> exceptionMappings;
    private final String defaultErrorPage;

    public ErrorPages(final Map<Integer, String> errorCodeLocations, final Map<Class<? extends Throwable>, String> exceptionMappings, final String defaultErrorPage) {
        this.errorCodeLocations = errorCodeLocations;
        this.exceptionMappings = exceptionMappings;
        this.defaultErrorPage = defaultErrorPage;
    }

    public String getErrorLocation(final int code) {
        String location = errorCodeLocations.get(code);
        if (location == null) {
            return defaultErrorPage;
        }
        return location;
    }

    public String getErrorLocation(final Throwable exception) {
        if (exception == null) {
            return null;
        }
        //todo: this is kinda slow, but there is probably not a great deal that can be done about it
        String location = null;
        for (Class c = exception.getClass(); c != null && location == null; c = c.getSuperclass()) {
            location = exceptionMappings.get(c);
        }
        if (location == null && exception instanceof ServletException) {
            Throwable rootCause = ((ServletException) exception).getRootCause();
            //Iterate through any nested JasperException in case it is in JSP development mode
            while (rootCause != null && rootCause instanceof ServletException && location == null) {
                for (Class c = rootCause.getClass(); c != null && location == null; c = c.getSuperclass()) {
                    location = exceptionMappings.get(c);
                }
                rootCause = ((ServletException) rootCause).getRootCause();
            }
            if (rootCause != null && location == null) {
                for (Class c = rootCause.getClass(); c != null && location == null; c = c.getSuperclass()) {
                    location = exceptionMappings.get(c);
                }
            }
        }
        if (location == null) {
            location = getErrorLocation(StatusCodes.INTERNAL_SERVER_ERROR);
        }
        return location;
    }


}
