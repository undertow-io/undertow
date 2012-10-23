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

package io.undertow.servlet.core;

import java.util.Map;

/**
 * Class that maintains information about error page mappings.
 *
 *
 * @author Stuart Douglas
 */
public class ErrorPages {

    private final Map<Integer, String> errorCodeLocations;
    private final Map<Class<? extends Throwable>, String> exceptionMappings;

    public ErrorPages(final Map<Integer, String> errorCodeLocations, final Map<Class<? extends Throwable>, String> exceptionMappings) {
        this.errorCodeLocations = errorCodeLocations;
        this.exceptionMappings = exceptionMappings;
    }

    public String getErrorLocation(final int code) {
        return errorCodeLocations.get(code);
    }

    public String getErrorLocation(final Throwable exception) {
        if(exception == null) {
            return null;
        }
        //todo: this is kinda slow, but there is probably not a great deal that can be done about it
        String e = null;
        for(Class c = exception.getClass(); c != null && e == null; c = c.getSuperclass()) {
            e = exceptionMappings.get(c);
        }
        return e;
    }



}
