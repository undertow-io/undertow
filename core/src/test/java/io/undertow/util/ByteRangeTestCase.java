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
import java.util.Arrays;
import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

public class ByteRangeTestCase {

    @Test
    public void testGetRanges() {
        ByteRange byteRange = new ByteRange(
                new ArrayList<>(Arrays.asList(
                        new ByteRange.Range(3, 5),
                        new ByteRange.Range(4, 8),
                        new ByteRange.Range(3, 9))));

        Assert.assertEquals(3, byteRange.getRanges());
    }

    @Test
    public void testGetStart() {
        ByteRange byteRange = new ByteRange(
                new ArrayList<>(Arrays.asList(
                        new ByteRange.Range(3, 5),
                        new ByteRange.Range(4, 8),
                        new ByteRange.Range(3, 9))));

        Assert.assertEquals(3, byteRange.getStart(0));
        Assert.assertEquals(4, byteRange.getStart(1));
        Assert.assertEquals(3, byteRange.getStart(2));
    }

    @Test
    public void testGetEnd() {
        ByteRange byteRange = new ByteRange(
                new ArrayList<>(Arrays.asList(
                        new ByteRange.Range(3, 5),
                        new ByteRange.Range(4, 8),
                        new ByteRange.Range(3, 9))));

        Assert.assertEquals(5, byteRange.getEnd(0));
        Assert.assertEquals(8, byteRange.getEnd(1));
        Assert.assertEquals(9, byteRange.getEnd(2));
    }

    @Test
    public void testParse() {
        Assert.assertNull(ByteRange.parse(null));
        Assert.assertNull(ByteRange.parse("foo"));
        Assert.assertNull(ByteRange.parse("bytes=1"));
        Assert.assertNull(ByteRange.parse("bytes=a-"));
        Assert.assertNull(ByteRange.parse("foobarbaz"));
        Assert.assertNull(ByteRange.parse("bytes=--1"));

        Assert.assertEquals(1, ByteRange.parse("bytes=2-").getRanges());
        Assert.assertEquals(1, ByteRange.parse("bytes=-20").getRanges());
    }

    @Test
    public void testGetResponseResult1() {
        ByteRange byteRange = new ByteRange(
                new ArrayList<>(Arrays.asList(
                        new ByteRange.Range(3, 5),
                        new ByteRange.Range(4, 8),
                        new ByteRange.Range(3, 9))));

        Assert.assertNull(byteRange.getResponseResult(0,
                "\"1\"", new Date(1559820153000L), "foo"));
        Assert.assertNull(byteRange.getResponseResult(0,
                "Mon, 31 Mar 2014 09:24:49 GMT",
                new Date(1559820153000L), "foo"));
    }

    @Test
    public void testGetResponseResult2() {
        ByteRange byteRange = new ByteRange(
                new ArrayList<>(Arrays.asList(new ByteRange.Range(-1, -1))));

        Assert.assertEquals(0, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getStart());
        Assert.assertEquals(0, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getEnd());
        Assert.assertEquals(0, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getContentLength());
        Assert.assertEquals("bytes */0", byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getContentRange());
        Assert.assertEquals(416, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getStatusCode());

        Assert.assertEquals(0, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getStart());
        Assert.assertEquals(0, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getEnd());
        Assert.assertEquals(0, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getContentLength());
        Assert.assertEquals("bytes */6", byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getContentRange());
        Assert.assertEquals(416, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getStatusCode());
    }

    @Test
    public void testGetResponseResult3() {
        ByteRange byteRange = new ByteRange(
                new ArrayList<>(Arrays.asList(new ByteRange.Range(5, -1))));

        Assert.assertEquals(0, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getStart());
        Assert.assertEquals(0, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getEnd());
        Assert.assertEquals(0, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getContentLength());
        Assert.assertEquals("bytes */0", byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getContentRange());
        Assert.assertEquals(416, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getStatusCode());

        Assert.assertEquals(5, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getStart());
        Assert.assertEquals(5, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getEnd());
        Assert.assertEquals(1, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getContentLength());
        Assert.assertEquals("bytes 5-5/6", byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getContentRange());
        Assert.assertEquals(206, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getStatusCode());
    }

    @Test
    public void testGetResponseResult4() {
        ByteRange byteRange = new ByteRange(
                new ArrayList<>(Arrays.asList(new ByteRange.Range(0, -1))));

        Assert.assertEquals(0, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getStart());
        Assert.assertEquals(0, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getEnd());
        Assert.assertEquals(0, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getContentLength());
        Assert.assertEquals("bytes */0", byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getContentRange());
        Assert.assertEquals(416, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getStatusCode());

        Assert.assertEquals(0, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getStart());
        Assert.assertEquals(5, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getEnd());
        Assert.assertEquals(6, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getContentLength());
        Assert.assertEquals("bytes 0-5/6", byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getContentRange());
        Assert.assertEquals(206, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getStatusCode());
    }

    @Test
    public void testGetResponseResult5() {
        ByteRange byteRange = new ByteRange(
                new ArrayList<>(Arrays.asList(new ByteRange.Range(3, 5))));

        Assert.assertEquals(0, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getStart());
        Assert.assertEquals(0, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getEnd());
        Assert.assertEquals(0, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getContentLength());
        Assert.assertEquals("bytes */0", byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getContentRange());
        Assert.assertEquals(416, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getStatusCode());

        Assert.assertEquals(3, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getStart());
        Assert.assertEquals(5, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getEnd());
        Assert.assertEquals(3, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getContentLength());
        Assert.assertEquals("bytes 3-5/6", byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getContentRange());
        Assert.assertEquals(206, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getStatusCode());
    }

    @Test
    public void testGetResponseResult6() {
        ByteRange byteRange = new ByteRange(
                new ArrayList<>(Arrays.asList(new ByteRange.Range(-1, 5))));

        Assert.assertEquals(0, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getStart());
        Assert.assertEquals(-1, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getEnd());
        Assert.assertEquals(0, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getContentLength());
        Assert.assertEquals("bytes 0--1/0", byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getContentRange());
        Assert.assertEquals(206, byteRange.getResponseResult(0, null,
                new Date(1559820153000L), "foo").getStatusCode());

        Assert.assertEquals(1, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getStart());
        Assert.assertEquals(5, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getEnd());
        Assert.assertEquals(5, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getContentLength());
        Assert.assertEquals("bytes 1-5/6", byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getContentRange());
        Assert.assertEquals(206, byteRange.getResponseResult(6, null,
                new Date(1559820153000L), "foo").getStatusCode());
    }

    @Test
    public void testGetResponseResultNull() {
        ByteRange byteRange = new ByteRange(new ArrayList<>());
        Assert.assertNull(byteRange.getResponseResult(0, "1",
                new Date(1559820153000L), "foo"));
    }
}
