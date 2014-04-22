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

/**
 * @author Stuart Douglas
 */
public class ETagUtilsTestCase {

    @Test
    public void testParseHeaderList() {

        Assert.assertArrayEquals(new ETag[] {
                new ETag(false, "1"),
                new ETag(false, "2"),
                new ETag(false, "3")},
                ETagUtils.parseETagList("\"1\",\"2\"   , \"3 ").toArray());

        Assert.assertArrayEquals(new ETag[] {
                new ETag(true, "111"),
                new ETag(false, "222"),
                new ETag(true, "333")},
                ETagUtils.parseETagList("W/\"111\",\"222\"   , W/\"333 ").toArray());

        Assert.assertArrayEquals(new ETag[] {
                new ETag(true, "1,1"),
                new ETag(false, "222"),
                new ETag(true, "3 3")},
                ETagUtils.parseETagList("W/\"1,1\",\"222\"   , W/\"3 3 ").toArray());

    }

}
