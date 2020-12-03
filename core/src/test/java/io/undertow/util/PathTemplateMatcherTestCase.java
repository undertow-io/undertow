/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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
import static org.junit.Assert.assertNotNull;

import io.undertow.server.HttpHandler;
import io.undertow.testutils.category.UnitTest;
import io.undertow.util.PathTemplateMatcher.PathMatchResult;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Arrays;

@Category(UnitTest.class)
public class PathTemplateMatcherTestCase {

    @Test
    public void pathTemplateMatchedShouldKeepOrder() {
        PathTemplateMatcher<HttpHandler> matcher = new PathTemplateMatcher<>();
        matcher.add("/{context}/{version}/{entity}/{id}", exchange -> {
        });

        PathMatchResult<HttpHandler> match = matcher.match("/api/v3/users/1");

        assertNotNull("Not matched", match);
        assertNotNull("Value", match.getValue());
        assertEquals("Matched parameters should keep order of definition",
                Arrays.asList("api", "v3", "users", "1"), new ArrayList<>(match.getParameters().values()));
    }

}
