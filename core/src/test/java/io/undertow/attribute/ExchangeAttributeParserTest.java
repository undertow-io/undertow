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

package io.undertow.attribute;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class ExchangeAttributeParserTest {

    @Test
    public void testSimpleNestedAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String attributeString = "%{myCustomAttr:%{REQUEST_LINE}}";
        ExchangeAttribute attribute = parser.parse(attributeString);
        // The attribute should be parsed as a single nested attribute, not as a composite of multiple attributes
        Assert.assertEquals("Parsed attribute string should match input", attributeString, attribute.toString());
        Assert.assertFalse("Attribute should not be of CompositeExchangeAttribute type", attribute instanceof CompositeExchangeAttribute);
    }

    @Test
    public void testComplexNestedAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String attributeString = "%{myCustomAttr:%{anotherCustomAttr:%{REQUEST_LINE}}-%{REQUEST_METHOD}}";
        ExchangeAttribute attribute = parser.parse(attributeString);
        // The attribute should be parsed as a single nested attribute, not as a composite of multiple attributes
        Assert.assertFalse("Attribute should not be of CompositeExchangeAttribute type", attribute instanceof CompositeExchangeAttribute);
        Assert.assertEquals("Parsed attribute string should match input", attributeString, attribute.toString());
    }

    @Test
    public void testSimpleAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String attributeString = "%{REQUEST_LINE}";
        ExchangeAttribute attribute = parser.parse(attributeString);
        Assert.assertTrue("Attribute should be of RequestLineAttribute type", attribute instanceof RequestLineAttribute);
        Assert.assertEquals("Parsed attribute string should match input", attributeString, attribute.toString());
    }

}