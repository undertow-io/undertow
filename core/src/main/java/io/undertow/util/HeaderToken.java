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
package io.undertow.util;

/**
 * Representation of a token allowed within a header.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface HeaderToken {

    /**
     * @return The name of the token as seen within the HTTP header.
     */
    String getName();

    /**
     * @return true if this header could be a quoted header.
     */
    boolean isAllowQuoted();

    /*
     * Additional items could be added and incorporated into the parsing checks: -
     * boolean isMandatory();
     * boolean
     * isEnumeration();
     * String[] getAllowedValues();
     */

}
