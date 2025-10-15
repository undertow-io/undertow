/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

public final class DefaultCharsetMapping {

    public static final DefaultCharsetMapping INSTANCE = new DefaultCharsetMapping();
    private static final String DEFAULT_MAPPING = "/io/undertow/servlet/core/charset.mapping";
    private final Properties map = new Properties();

    private DefaultCharsetMapping() {
        try (InputStream stream = getClass().getResourceAsStream(DEFAULT_MAPPING)) {
            map.load(stream);
        } catch (Throwable t) {
            throw new IllegalArgumentException(t.toString());
        }
    }

    public String getCharset(final Locale locale) {
        return map.getProperty(locale.getLanguage());
    }

}
