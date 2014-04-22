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

/**
 * A servlet error page mapping
 *
 *
 * @author Stuart Douglas
 */
public class ErrorPage {

    private final String location;
    private final Integer errorCode;
    private final Class<? extends Throwable> exceptionType;

    public ErrorPage(final String location,  final Class<? extends Throwable> exceptionType) {
        this.location = location;
        this.errorCode = null;
        this.exceptionType = exceptionType;
    }
    public ErrorPage(final String location, final int errorCode) {
        this.location = location;
        this.errorCode = errorCode;
        this.exceptionType = null;
    }

    public ErrorPage(final String location) {
        this.location = location;
        this.errorCode = null;
        this.exceptionType = null;
    }

    public String getLocation() {
        return location;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public Class<? extends Throwable> getExceptionType() {
        return exceptionType;
    }
}
