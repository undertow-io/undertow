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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests that the headers in the Headers class have the correct order. The headers
 * are assigned an explicit ordering integer to allow for super fast comparisons.
 *
 * @author Stuart Douglas
 */
public class HeaderOrderTestCase {

    @Test
    public void testHeadersOrder() throws Exception {

        final Field orderIntField = HttpString.class.getDeclaredField("orderInt");
        orderIntField.setAccessible(true);

        Field[] fields = Headers.class.getDeclaredFields();
        final List<HttpString> headers = new ArrayList<>();
        for(final Field field : fields) {
            // skip transient field for jacoco
            if(Modifier.isTransient(field.getModifiers()) || !Modifier.isPublic(field.getModifiers())) {
                continue;
            }

            Object value = field.get(null);
            if(!(value instanceof HttpString)) {
                continue;
            }
            HttpString header = (HttpString) value;
            if((Integer)orderIntField.get(header) != 0) {
                headers.add(header);
            }
        }

        Collections.sort(headers, new Comparator<HttpString>() {
            @Override
            public int compare(final HttpString o1, final HttpString o2) {
                return o1.toString().compareToIgnoreCase(o2.toString());
            }
        });
        int val = 1;
        for(final HttpString header : headers) {
            Assert.assertEquals(val++, orderIntField.get(header));
        }

    }

}
