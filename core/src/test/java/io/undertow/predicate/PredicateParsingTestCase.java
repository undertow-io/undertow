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

package io.undertow.predicate;

import java.util.HashMap;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Stuart Douglas
 */
public class PredicateParsingTestCase {

    @Test
    public void testPredicateParser() {
        Predicate predicate = PredicateParser.parse("path[foo]", PredicateParsingTestCase.class.getClassLoader());
        Assert.assertTrue(predicate instanceof PathMatchPredicate);
        HttpServerExchange e = new HttpServerExchange(null);
        e.setRelativePath("/foo");
        Assert.assertTrue(predicate.resolve(e));
        e.setRelativePath("/bob");
        Assert.assertFalse(predicate.resolve(e));

        for (String string : new String[]{
                "not path[\"/foo\"]",
                "not path[foo] and true",
                "false or not path[path=/foo]",
                "false or not path[/foo]",
                "true and not path[foo] or not path[foo] and false"}) {
            try {
                predicate = PredicateParser.parse(string, PredicateParsingTestCase.class.getClassLoader());
                e = new HttpServerExchange(null);
                e.setRelativePath("/foo");
                Assert.assertFalse(predicate.resolve(e));
                e.setRelativePath("/bob");
                Assert.assertTrue(predicate.resolve(e));
            } catch (Throwable ex) {
                throw new RuntimeException("String " + string, ex);
            }
        }
    }

    @Test
    public void testPredicateContextVariable() {
        Predicate predicate = PredicateParser.parse("regex[pattern=\"/publicdb/(.*?)/.*\", value=\"%R\", full-match=false] and equals[%{i,username}, ${1}]", PredicateParsingTestCase.class.getClassLoader());

        HttpServerExchange e = new HttpServerExchange(null);
        e.setRelativePath("/publicdb/foo/bar");
        Assert.assertFalse(predicate.resolve(e));
        e.getRequestHeaders().add(new HttpString("username"), "foo");
        Assert.assertTrue(predicate.resolve(e));
    }

    @Test
    public void testRegularExpressionsWithPredicateContext() {
        Predicate predicate = PredicateParser.parse("regex[pattern=a* , value=%{RELATIVE_PATH}] and equals[{$0, aaa}]", PredicateParsingTestCase.class.getClassLoader());
        HttpServerExchange e = new HttpServerExchange(null);
        e.putAttachment(Predicate.PREDICATE_CONTEXT, new HashMap<String, Object>());
        e.setRelativePath("aaab");
        Assert.assertTrue(predicate.resolve(e));
        e.setRelativePath("aaaab");
        Assert.assertFalse(predicate.resolve(e));

        predicate = PredicateParser.parse("regex[pattern='a(b*)a*' , value=%{RELATIVE_PATH}] and equals[$1, bb]", PredicateParsingTestCase.class.getClassLoader());
        e.putAttachment(Predicate.PREDICATE_CONTEXT, new HashMap<String, Object>());
        e.setRelativePath("abb");
        Assert.assertTrue(predicate.resolve(e));
        e.setRelativePath("abbaaa");
        Assert.assertTrue(predicate.resolve(e));
        e.setRelativePath("abbb");
        Assert.assertFalse(predicate.resolve(e));
    }

    @Test
    public void testArrayValues() {
        Predicate predicate;
        for (String string : new String[]{
                "contains[value=%{i,Content-Type}, search=text]",
                "contains[value=\"%{i,Content-Type}\", search={text}]",
                "contains[value=\"%{i,Content-Type}\", search={text, \"other text\"}]",
        }) {
            try {
                predicate = PredicateParser.parse(string, PredicateParsingTestCase.class.getClassLoader());
                HttpServerExchange e = new HttpServerExchange(null);
                Assert.assertFalse(predicate.resolve(e));
                e.getRequestHeaders().add(Headers.CONTENT_TYPE, "text");
                Assert.assertTrue(predicate.resolve(e));
            } catch (Throwable ex) {
                throw new RuntimeException("String " + string, ex);
            }
        }
    }

    @Test
    public void testDefaultArrayValue() {
        Predicate predicate = PredicateParser.parse("equals[%{i,Content-Type},\"text/plain\"]", PredicateParsingTestCase.class.getClassLoader());
        HttpServerExchange e = new HttpServerExchange(null);
        Assert.assertFalse(predicate.resolve(e));
        e.getRequestHeaders().add(Headers.CONTENT_TYPE, "text/plain");
        Assert.assertTrue(predicate.resolve(e));
    }

    @Test
    public void testOrderOfOperations() {
        expect("exists[%{i,Content-Length}] or exists[value=%{i,Trailer}] and exists[%{i,Other}]", false, true);
        expect("(exists[%{i,Content-Length}] or exists[value=%{i,Trailer}]) and exists[%{i,Other}]", false, false);
    }

    private void expect(String string, boolean result1, boolean result2) {
        try {
            Predicate predicate = PredicateParser.parse(string, PredicateParsingTestCase.class.getClassLoader());
            HttpServerExchange e = new HttpServerExchange(null);
            e.getRequestHeaders().add(Headers.TRAILER, "a");
            Assert.assertEquals(result1, predicate.resolve(e));
            e.getRequestHeaders().add(Headers.CONTENT_LENGTH, "a");
            Assert.assertEquals(result2, predicate.resolve(e));
        } catch (Throwable ex) {
            throw new RuntimeException("String " + string, ex);
        }
    }
}
