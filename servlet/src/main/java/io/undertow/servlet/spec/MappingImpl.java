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

package io.undertow.servlet.spec;

import javax.servlet.http.Mapping;
import javax.servlet.http.MappingMatch;

/**
 * @author Stuart Douglas
 */
public class MappingImpl implements Mapping {

    private final String matchValue;
    private final String pattern;
    private final MappingMatch matchType;

    public MappingImpl(String matchValue, String pattern, MappingMatch matchType) {
        this.matchValue = matchValue;
        this.pattern = pattern;
        this.matchType = matchType;
    }

    @Override
    public String getMatchValue() {
        return matchValue;
    }

    @Override
    public String getPattern() {
        return pattern;
    }

    @Override
    public MappingMatch getMatchType() {
        return matchType;
    }
}
