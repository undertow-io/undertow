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

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class SubstringMapTestCase {

    public static final int NUM_TEST_VALUES = 1000;

    @Test
    public void testSubstringMap() {

        SubstringMap<Integer> paths = new SubstringMap<>();

        for (int count = 0; count < 10; ++count) {
            int seed = new Random().nextInt();

            Random random = new Random(seed);
            System.out.println("Using Seed " + seed);

            List<String> parts = new ArrayList<>();

            Set<String> keys = new HashSet<>();

            for (int i = 0; i < NUM_TEST_VALUES; ++i) {
                String s = null;
                do {
                    byte[] bytes = new byte[random.nextInt(30) + 5];
                    random.nextBytes(bytes);
                    s = FlexBase64.encodeString(bytes, false);
                } while (keys.contains(s));
                keys.add(s);
                parts.add(s);
                paths.put(s, i);
                Assert.assertEquals(Integer.valueOf(i), paths.get(s, s.length()).getValue());
                Assert.assertEquals(Integer.valueOf(i), paths.get(s + "fooosdf", s.length()).getValue());
                String missing = s + "asdfdasfasf";
                Assert.assertNull(paths.get(missing, missing.length()));
            }

            for (String k : paths.keys()) {
                Assert.assertTrue(keys.remove(k));
            }
            Assert.assertEquals(0, keys.size());

            for (int i = 0; i < NUM_TEST_VALUES; ++i) {
                String p = parts.get(i);
                Assert.assertEquals(Integer.valueOf(i), paths.get(p, p.length()).getValue());
                Assert.assertEquals(Integer.valueOf(i), paths.get(p + "asdfdsafasfw", p.length()).getValue());
            }
            for (int i = 0; i < NUM_TEST_VALUES; ++i) {
                Integer p = paths.remove(parts.get(i));
                Assert.assertEquals(Integer.valueOf(i), p);
            }
        }
    }

}
