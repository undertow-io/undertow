/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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
package io.undertow.servlet.test.dispatcher.attributes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public abstract class AttributeComparisonTestBase {

    protected void testAttributes(final Map<String, String> expected, final String incoming) throws RuntimeException {

        final Map<String, String> incomingParameters = new TreeMap<String, String>();
        final Map<String, String> expectedParameters = new TreeMap<String, String>();
        expectedParameters.putAll(expected);
        System.err.println("INCOMING: "+incoming);
        final String[] array = incoming.split("\n");
        System.err.println("INCOMING: "+Arrays.toString(array));
        for (String kvp : array) {
            // this might not be best way, but...
            final String[] split = kvp.split(";");
            final String splitValue = split.length == 2 ? split[1] : ""; //thats empty string.
            // sanity check
            final String errorIfNotNull = incomingParameters.put(split[0], splitValue);
            // should not happen?
            assertNull("Doubled value of key[" + split[0] + "] = [" + errorIfNotNull + "," + splitValue + "]", errorIfNotNull);
            assertTrue("Expected parameters does not contain '" + split[0] + "'", expectedParameters.containsKey(split[0]));
            assertEquals(expectedParameters.remove(split[0]), splitValue);
        }
        assertTrue("Too many expected parameters: " + expectedParameters, expectedParameters.size() == 0);
    }
}
