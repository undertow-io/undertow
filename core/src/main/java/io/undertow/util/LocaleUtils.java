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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Utility methods for getting the locale from a request.
 *
 * @author Stuart Douglas
 */
public class LocaleUtils {

    public static Locale getLocaleFromString(String localeString) {
        if (localeString == null) {
            return null;
        }
        return Locale.forLanguageTag(localeString);
    }

    /**
     * Parse a header string and return the list of locales that were found.
     *
     * If the header is empty or null then an empty list will be returned.
     *
     * @param acceptLanguage The Accept-Language header
     * @return The list of locales, in order of preference
     */
    public static List<Locale> getLocalesFromHeader(final String acceptLanguage) {
        if(acceptLanguage == null) {
            return Collections.emptyList();
        }
        return getLocalesFromHeader(Collections.singletonList(acceptLanguage));
    }

    /**
     * Parse a header string and return the list of locales that were found.
     *
     * If the header is empty or null then an empty list will be returned.
     *
     * @param acceptLanguage The Accept-Language header
     * @return The list of locales, in order of preference
     */
    public static List<Locale> getLocalesFromHeader(final List<String> acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Locale> ret = new ArrayList<>();
        final List<List<QValueParser.QValueResult>> parsedResults = QValueParser.parse(acceptLanguage);
        for (List<QValueParser.QValueResult> qvalueResult : parsedResults) {
            for (QValueParser.QValueResult res : qvalueResult) {
                if (!res.isQValueZero()) {
                    Locale e = LocaleUtils.getLocaleFromString(res.getValue());
                    ret.add(e);
                }
            }
        }
        return ret;
    }
}
