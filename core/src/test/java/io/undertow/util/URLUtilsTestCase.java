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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import io.undertow.testutils.category.UnitTest;

/**
 * @author Oleksandr Radchykov
 * @author Andre Schaefer
 */
@RunWith(Parameterized.class)
@Category(UnitTest.class)
public class URLUtilsTestCase {

    @Parameterized.Parameters
    public static Object[] spaceCodes() {
        return new Object[]{"%2f", "%2F"};
    }

    @Parameterized.Parameter
    public String spaceCode = "%2f";

    @Test
    public void testDecodingWithEncodedAndDecodedSlashAndSlashDecodingDisabled() throws Exception {
        String url = "http://localhost:3001/by-path/wild%20card/wild%28west%29/wild" + spaceCode + "wolf";

        final String result = URLUtils.decode(url, Charset.defaultCharset().name(), false, new StringBuilder());
        assertEquals("http://localhost:3001/by-path/wild card/wild(west)/wild" + spaceCode + "wolf", result);
    }

    @Test
    public void testDecodingURLMustNotMutateSpaceSymbolsCaseIfSpaceDecodingDisabled() throws Exception {
        final String url = "http://localhost:3001/wild" + spaceCode + "west";

        final String result = URLUtils.decode(url, Charset.defaultCharset().name(), false, new StringBuilder());
        assertEquals(url, result);
    }

    @Test
    public void testIsAbsoluteUrlRecognizingAbsolutUrls() {
        assertTrue(URLUtils.isAbsoluteUrl("https://some.valid.url:8080/path?query=val"));
        assertTrue(URLUtils.isAbsoluteUrl("http://some.valid.url:8080/path?query=val"));
        assertTrue(URLUtils.isAbsoluteUrl("http://some.valid.url"));
    }

    @Test
    public void testIsAbsoluteUrlRecognizingAppUrls() {
        assertTrue(URLUtils.isAbsoluteUrl("com.example.app:/oauth2redirect/example-provider"));
        assertTrue(URLUtils.isAbsoluteUrl("com.example.app:/oauth2redirect/example-provider?query=val"));
    }

    @Test
    public void testIsAbsoluteUrlRecognizingRelativeUrls() {
        assertFalse(URLUtils.isAbsoluteUrl("relative"));
        assertFalse(URLUtils.isAbsoluteUrl("relative/path"));
        assertFalse(URLUtils.isAbsoluteUrl("relative/path?query=val"));
        assertFalse(URLUtils.isAbsoluteUrl("relative/path:path"));
        assertFalse(URLUtils.isAbsoluteUrl("/root/relative/path"));
    }

    @Test
    public void testIsAbsoluteUrlRecognizingEmptyOrNullAsRelative() {
        assertFalse(URLUtils.isAbsoluteUrl(null));
        assertFalse(URLUtils.isAbsoluteUrl(""));
    }

    @Test
    public void testIsAbsoluteUrlIgnoresSyntaxErrorsAreNotAbsolute() {
        assertFalse(URLUtils.isAbsoluteUrl(":"));
    }

    /**
     * @see <a href="https://issues.jboss.org/browse/UNDERTOW-1552">UNDERTOW-1552</a>
     */
    @Test
    public void testDecodingWithTrailingPercentChar() throws Exception {
        final String[] urls = new String[] {"https://example.com/?a=%", "https://example.com/?a=%2"};
        for (final String url : urls) {
            try {
                URLUtils.decode(url, StandardCharsets.UTF_8.name(), false, new StringBuilder());
                Assert.fail("Decode was expected to fail for " + url);
            }  catch (IllegalArgumentException iae) {
                // expected
            }
        }
    }

    @Test
    public void testIsAbsoluteUrlInvalidChars() {
        assertTrue(URLUtils.isAbsoluteUrl("http://test.com/foobar?test={abc}"));
    }

}
