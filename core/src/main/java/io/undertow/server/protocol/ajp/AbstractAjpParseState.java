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

package io.undertow.server.protocol.ajp;

/**
 * Abstract AJP parse state. Stores state common to both request and response parsers
 *
 *
 * @author Stuart Douglas
 */
public class AbstractAjpParseState {

    /**
     * The length of the string being read
     */
    public int stringLength = -1;

    /**
     * The current string being read
     */
    public StringBuilder currentString;

    /**
     * when reading the first byte of an integer this stores the first value. It is set to -1 to signify that
     * the first byte has not been read yet.
     */
    public int currentIntegerPart = -1;

    boolean containsUrlCharacters = false;
    public int readHeaders = 0;

    public void reset() {
        stringLength = -1;
        currentString = null;
        currentIntegerPart = -1;
        readHeaders = 0;
    }
}
