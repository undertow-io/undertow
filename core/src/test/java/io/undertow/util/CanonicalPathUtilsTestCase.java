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
 * Tests canonicalization of the path
 *
 * @author Stuart Douglas
 */
@Category(UnitTest.class)
public class CanonicalPathUtilsTestCase {

    @Test
    public void testCanonicalization() {

        //these strings should not be touched
        Assert.assertSame("a/b/c", CanonicalPathUtils.canonicalize("a/b/c"));
        Assert.assertSame("a/b/c/", CanonicalPathUtils.canonicalize("a/b/c/"));
        Assert.assertSame("aaaaa", CanonicalPathUtils.canonicalize("aaaaa"));

        //these strings should result in the same string being output
        Assert.assertEquals("a./b", CanonicalPathUtils.canonicalize("a./b"));
        Assert.assertEquals("a./.b", CanonicalPathUtils.canonicalize("a./.b"));

        //removing double slash

        Assert.assertEquals("a/b", CanonicalPathUtils.canonicalize("a//b"));
        Assert.assertEquals("a/b", CanonicalPathUtils.canonicalize("a///b"));
        Assert.assertEquals("a/b", CanonicalPathUtils.canonicalize("a////b"));

        //removing /./
        Assert.assertEquals("a/b", CanonicalPathUtils.canonicalize("a/./b"));
        Assert.assertEquals("a/b", CanonicalPathUtils.canonicalize("a/././b"));
        Assert.assertEquals("a/b/c", CanonicalPathUtils.canonicalize("a/./b/./c"));
        Assert.assertEquals("a/b", CanonicalPathUtils.canonicalize("a/./././b"));
        Assert.assertEquals("a/b/", CanonicalPathUtils.canonicalize("a/./././b/./"));
        Assert.assertEquals("a/b", CanonicalPathUtils.canonicalize("a/./././b/."));

        //dealing with /../
        Assert.assertEquals("/b", CanonicalPathUtils.canonicalize("/a/../b"));
        Assert.assertEquals("/b", CanonicalPathUtils.canonicalize("/a/../c/../e/../b"));
        Assert.assertEquals("/b", CanonicalPathUtils.canonicalize("/a/c/../../b"));

        // out of servlet context
        Assert.assertNull(CanonicalPathUtils.canonicalize("/a/../..", true));
        Assert.assertNull(CanonicalPathUtils.canonicalize("/a/../../foo", true));
        Assert.assertNull(CanonicalPathUtils.canonicalize("/../../a/b/bar", true));
        Assert.assertEquals("/", CanonicalPathUtils.canonicalize("/a/../.."));
        Assert.assertEquals("/foo", CanonicalPathUtils.canonicalize("/a/../../foo"));

        //preserve (single) trailing /
        Assert.assertEquals("/a/", CanonicalPathUtils.canonicalize("/a/"));
        Assert.assertEquals("/", CanonicalPathUtils.canonicalize("/"));
        Assert.assertEquals("/bbb/a", CanonicalPathUtils.canonicalize("/cc/../bbb/a/."));
        Assert.assertEquals("/aaa/bbb/", CanonicalPathUtils.canonicalize("/aaa/bbb//////"));
    }

    @Test
    public void testCanonicalizationBackslash() {

        //these strings should not be touched
        Assert.assertSame("a\\b\\c", CanonicalPathUtils.canonicalize("a\\b\\c"));
        Assert.assertSame("a\\b\\c\\", CanonicalPathUtils.canonicalize("a\\b\\c\\"));
        Assert.assertSame("aaaaa", CanonicalPathUtils.canonicalize("aaaaa"));

        //these strings should result in the same string being output
        Assert.assertEquals("a.\\b", CanonicalPathUtils.canonicalize("a.\\b"));
        Assert.assertEquals("a.\\.b", CanonicalPathUtils.canonicalize("a.\\.b"));

        //removing double slash

        Assert.assertEquals("a\\b", CanonicalPathUtils.canonicalize("a\\\\b"));
        Assert.assertEquals("a\\b", CanonicalPathUtils.canonicalize("a\\\\\\b"));
        Assert.assertEquals("a\\b", CanonicalPathUtils.canonicalize("a\\\\\\\\b"));

        //removing \.\
        Assert.assertEquals("a\\b", CanonicalPathUtils.canonicalize("a\\.\\b"));
        Assert.assertEquals("a\\b", CanonicalPathUtils.canonicalize("a\\.\\.\\b"));
        Assert.assertEquals("a\\b\\c", CanonicalPathUtils.canonicalize("a\\.\\b\\.\\c"));
        Assert.assertEquals("a\\b", CanonicalPathUtils.canonicalize("a\\.\\.\\.\\b"));
        Assert.assertEquals("a\\b\\", CanonicalPathUtils.canonicalize("a\\.\\.\\.\\b\\.\\"));
        Assert.assertEquals("a\\b", CanonicalPathUtils.canonicalize("a\\.\\.\\.\\b\\."));

        //dealing with \..\
        Assert.assertEquals("\\b", CanonicalPathUtils.canonicalize("\\a\\..\\b"));
        Assert.assertEquals("\\b", CanonicalPathUtils.canonicalize("\\a\\..\\c\\..\\e\\..\\b"));
        Assert.assertEquals("\\b", CanonicalPathUtils.canonicalize("\\a\\c\\..\\..\\b"));

         // out of servlet context
        Assert.assertNull(CanonicalPathUtils.canonicalize("\\a\\..\\..", true));
        Assert.assertNull(CanonicalPathUtils.canonicalize("\\a\\..\\..\\foo", true));
        Assert.assertNull(CanonicalPathUtils.canonicalize("\\..\\..\\a\\b\\bar", true));
        Assert.assertEquals("/", CanonicalPathUtils.canonicalize("\\a\\..\\.."));
        Assert.assertEquals("\\foo", CanonicalPathUtils.canonicalize("\\a\\..\\..\\foo"));

        //preserve (single) trailing \
        Assert.assertEquals("\\a\\", CanonicalPathUtils.canonicalize("\\a\\"));
        Assert.assertEquals("\\", CanonicalPathUtils.canonicalize("\\"));
        Assert.assertEquals("\\bbb\\a", CanonicalPathUtils.canonicalize("\\cc\\..\\bbb\\a\\."));
        Assert.assertEquals("\\aaa\\bbb\\", CanonicalPathUtils.canonicalize("\\aaa\\bbb\\\\\\\\\\\\"));

        //test mixtures of both forward and back slash
        Assert.assertEquals("/", CanonicalPathUtils.canonicalize("\\a/..\\./"));
        Assert.assertEquals("\\a/", CanonicalPathUtils.canonicalize("\\a\\b\\..\\./"));
        Assert.assertEquals("/a/b/c../d..\\", CanonicalPathUtils.canonicalize("/a/b/c../d..\\"));
        Assert.assertEquals("/a/d\\", CanonicalPathUtils.canonicalize("/a/b/c/..\\../d\\.\\"));
    }
}
