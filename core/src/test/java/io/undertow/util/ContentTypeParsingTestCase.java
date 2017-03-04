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

import io.undertow.testutils.category.UnitTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author Stuart Douglas
 */
@Category(UnitTest.class)
public class ContentTypeParsingTestCase {

    @Test
    public void testCharsetParsing() {
        Assert.assertEquals(null, Headers.extractQuotedValueFromHeader("text/html; other-data=\"charset=UTF-8\"", "charset"));
        Assert.assertEquals(null, Headers.extractQuotedValueFromHeader("text/html;", "charset"));
        Assert.assertEquals("UTF-8", Headers.extractQuotedValueFromHeader("text/html; charset=\"UTF-8\"", "charset"));
        Assert.assertEquals("UTF-8", Headers.extractQuotedValueFromHeader("text/html; charset=UTF-8", "charset"));
        Assert.assertEquals("UTF-8", Headers.extractQuotedValueFromHeader("text/html; charset=\"UTF-8\"; foo=bar", "charset"));
        Assert.assertEquals("UTF-8", Headers.extractQuotedValueFromHeader("text/html; charset=UTF-8 foo=bar", "charset"));
        Assert.assertEquals("UTF-8", Headers.extractQuotedValueFromHeader("text/html; badcharset=bad charset=UTF-8 foo=bar", "charset"));
        Assert.assertEquals("UTF-8", Headers.extractQuotedValueFromHeader("text/html;charset=UTF-8", "charset"));
        Assert.assertEquals("UTF-8", Headers.extractQuotedValueFromHeader("text/html;\tcharset=UTF-8", "charset"));
    }

}
