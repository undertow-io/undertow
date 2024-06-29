/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Some tests that were specifically used to test during the development of {@link PathTemplateRouterFactory} as well as an
 * adaptation of the tests in {@link PathTemplateTestCase} to confirm compatibility with {@link PathTemplateMatcher}.
 *
 * @author Dirk Roets
 */
@Category(UnitTest.class)
public class PathTemplateRouterTest {

    private final String defaultTarget = "default";

    private static void assertValidTemplate(final String templatePath) {
        PathTemplateParser.parseTemplate(templatePath, new Object());
    }

    @SuppressWarnings("ThrowableResultIgnored")
    private static void assertInValidTemplate(final String templatePath) {
        Assert.assertThrows(IllegalArgumentException.class,
                () -> PathTemplateParser.parseTemplate(templatePath, new Object())
        );
    }

    private static void assertPatternEquals(
            final String expectedTemplatePath,
            final String actualTemplatePath
    ) {
        final PathTemplateParser.PathTemplate<Object> expectedTemplate = PathTemplateParser.parseTemplate(
                expectedTemplatePath, new Object()
        );
        final PathTemplateParser.PathTemplate<Object> actualTemplate = PathTemplateParser.parseTemplate(
                actualTemplatePath, new Object()
        );
        Assert.assertTrue(expectedTemplate.patternEquals(actualTemplate));
        Assert.assertEquals(new PathTemplateParser.PathTemplatePatternEqualsAdapter<>(expectedTemplate),
                new PathTemplateParser.PathTemplatePatternEqualsAdapter<>(actualTemplate)
        );
    }

    private static void assertPatternNotEquals(
            final String expectedTemplatePath,
            final String actualTemplatePath
    ) {
        final PathTemplateParser.PathTemplate<Object> expectedTemplate = PathTemplateParser.parseTemplate(
                expectedTemplatePath, new Object()
        );
        final PathTemplateParser.PathTemplate<Object> actualTemplate = PathTemplateParser.parseTemplate(
                actualTemplatePath, new Object()
        );
        Assert.assertFalse(expectedTemplate.patternEquals(actualTemplate));
        Assert.assertNotEquals(new PathTemplateParser.PathTemplatePatternEqualsAdapter<>(expectedTemplate),
                new PathTemplateParser.PathTemplatePatternEqualsAdapter<>(actualTemplate)
        );
    }

    /**
     * Assert that URL path templates are parsed correctly.
     */
    @Test
    public void testParseTemplate() {
        assertValidTemplate("");
        assertValidTemplate("/");
        assertValidTemplate("/some");
        assertValidTemplate("/some/static/path");
        assertValidTemplate("/some/static/path/");
        assertValidTemplate("{var1}");
        assertValidTemplate("/{var1}");
        assertValidTemplate("/{var1}/some/{var2}");
        assertValidTemplate("/some/{var1}");
        assertValidTemplate("/some/{var1}/");
        assertValidTemplate("*");
        assertValidTemplate("/*");
        assertValidTemplate("/some/*");
        assertValidTemplate("/some*");

        assertInValidTemplate("/some illegal/path");
        assertInValidTemplate("/some/a{var1}");
        assertInValidTemplate("/some*a");
        assertInValidTemplate("/some/*/path");

        assertPatternEquals("/some/static/path", "/some/static/path");
        assertPatternNotEquals("some/static/path", "/some/static/path2");

        assertPatternEquals("/some/{var1}/path", "/some/{var2}/path");
        assertPatternNotEquals("/some/{var1}/path", "/some/{var2}/path2");

        assertPatternEquals("/some/{var1}/path*", "/some/{var2}/path*");
    }

    private static <T> PathTemplateRouterFactory.Builder<Supplier<T>, T> routerBuilder(final T defaultTarget) {
        return PathTemplateRouterFactory.Builder.newBuilder().updateDefaultTarget(defaultTarget);
    }

    private void assertNoMatch(
            final PathTemplateRouter<String> router,
            final String path
    ) {
        final PathTemplateRouteResult<String> result = router.route(path);
        Assert.assertSame(defaultTarget, result.getTarget());
        Assert.assertTrue(result.getPathTemplate().isEmpty());
        Assert.assertTrue(result.getParameters().isEmpty());
    }

    private void assertMatch(
            final PathTemplateRouter<String> router,
            final String path,
            final String target,
            final String... pathParams
    ) {
        final int pathParamLen = pathParams.length;
        if (pathParamLen % 2 == 1) {
            throw new IllegalArgumentException();
        }

        final Map<String, String> expectedParams;
        if (pathParamLen > 0) {
            expectedParams = new HashMap<>((int) (pathParamLen / 0.75d) + 1);
            for (int i = 0; i < pathParamLen; i += 2) {
                expectedParams.put(pathParams[i], pathParams[i + 1]);
            }
        } else {
            expectedParams = Collections.emptyMap();
        }

        final PathTemplateRouteResult<String> result = router.route(path);
        Assert.assertSame(target, result.getTarget());
        Assert.assertTrue(result.getPathTemplate().isPresent());
        Assert.assertEquals(expectedParams, result.getParameters());
    }

    /**
     * Assert that requests are routed correctly.
     */
    @Test
    public void testRouting() {
        final int targetCount = 20;
        final String[] targets = new String[targetCount];
        for (int i = 0; i < targetCount; i++) {
            targets[i] = "target-" + i;
        }

        PathTemplateRouter<String> router = routerBuilder(defaultTarget)
                .addTemplate("/", () -> targets[0])
                .addTemplate("/users", () -> targets[1])
                .addTemplate("/users/teams", () -> targets[2])
                .addTemplate("/users/dashboards", () -> targets[3])
                .addTemplate("/users/authentication-methods", () -> targets[4])
                .addTemplate("/users/connection-types", () -> targets[5])
                .addTemplate("/users/avatars", () -> targets[6])
                .addTemplate("/users/{userId}", () -> targets[7])
                .addTemplate("/users/{userId}/teams", () -> targets[8])
                .addTemplate("/users/{userId}/dashboards", () -> targets[9])
                .addTemplate("/users/{userId}/authentication-methods", () -> targets[10])
                .addTemplate("/users/{userId}/connection-types", () -> targets[11])
                .addTemplate("/users/{userId}/avatars", () -> targets[12])
                .addTemplate("/users/properties/*", () -> targets[13])
                .addTemplate("/users/properties/internal-*", () -> targets[14])
                .addTemplate("/users/{userId}/properties/*", () -> targets[15])
                .addTemplate("/users/{userId}/*", () -> targets[16])
                .build();

        assertNoMatch(router, "/some/other/path");

        // Some basic routing requests.
        assertNoMatch(router, "/unknown/path");
        assertMatch(router, "/", targets[0]);
        assertMatch(router, "/users", targets[1]);
        assertMatch(router, "/users/teams", targets[2]);
        assertMatch(router, "/users/dashboards", targets[3]);
        assertMatch(router, "/users/authentication-methods", targets[4]);
        assertMatch(router, "/users/connection-types", targets[5]);
        assertMatch(router, "/users/avatars", targets[6]);
        assertMatch(router, "/users/1234", targets[7], "userId", "1234");
        assertMatch(router, "/users/1234/teams", targets[8], "userId", "1234");
        assertMatch(router, "/users/1234/dashboards", targets[9], "userId", "1234");
        assertMatch(router, "/users/1234/authentication-methods", targets[10], "userId", "1234");
        assertMatch(router, "/users/1234/connection-types", targets[11], "userId", "1234");
        assertMatch(router, "/users/1234/avatars", targets[12], "userId", "1234");
        assertMatch(router, "/users/properties/followers", targets[13], "*", "followers");
        assertMatch(router, "/users/properties/internal-previous-password-hashes", targets[14], "*",
                "previous-password-hashes");
        assertMatch(router, "/users/1234/properties/followers", targets[15], "userId", "1234", "*", "followers");
    }

    /* =============================================================================================================
        Below are tests coppied from PathTemplateTestCase to verify compatibility with the existing path template
        matcher.
    ============================================================================================================= */
    @Test
    public void testMatches() {
        // test normal use
        testMatch("/docs/mydoc", "/docs/mydoc");
        testMatch("/docs/{docId}", "/docs/mydoc", "docId", "mydoc");
        testMatch("/docs/{docId}/{op}", "/docs/mydoc/read", "docId", "mydoc", "op", "read");
        testMatch("/docs/{docId}/{op}/{allowed}", "/docs/mydoc/read/true", "docId", "mydoc", "op", "read", "allowed",
                "true");
        testMatch("/docs/{docId}/operation/{op}", "/docs/mydoc/operation/read", "docId", "mydoc", "op", "read");
        testMatch("/docs/{docId}/read", "/docs/mydoc/read", "docId", "mydoc");
        testMatch("/docs/{docId}/read", "/docs/mydoc/read?myQueryParam", "docId", "mydoc");

        // test no leading slash
        testMatch("docs/mydoc", "/docs/mydoc");
        testMatch("docs/{docId}", "/docs/mydoc", "docId", "mydoc");
        testMatch("docs/{docId}/{op}", "/docs/mydoc/read", "docId", "mydoc", "op", "read");
        testMatch("docs/{docId}/{op}/{allowed}", "/docs/mydoc/read/true", "docId", "mydoc", "op", "read", "allowed",
                "true");
        testMatch("docs/{docId}/operation/{op}", "/docs/mydoc/operation/read", "docId", "mydoc", "op", "read");
        testMatch("docs/{docId}/read", "/docs/mydoc/read", "docId", "mydoc");
        testMatch("docs/{docId}/read", "/docs/mydoc/read?myQueryParam", "docId", "mydoc");

        // test trailing slashes
        testMatch("/docs/mydoc/", "/docs/mydoc/");
        testMatch("/docs/{docId}/", "/docs/mydoc/", "docId", "mydoc");
        testMatch("/docs/{docId}/{op}/", "/docs/mydoc/read/", "docId", "mydoc", "op", "read");
        testMatch("/docs/{docId}/{op}/{allowed}/", "/docs/mydoc/read/true/", "docId", "mydoc", "op", "read", "allowed",
                "true");
        testMatch("/docs/{docId}/operation/{op}/", "/docs/mydoc/operation/read/", "docId", "mydoc", "op", "read");
        testMatch("/docs/{docId}/read/", "/docs/mydoc/read/", "docId", "mydoc");

        // test straight replacement of template
        testMatch("/{foo}", "/bob", "foo", "bob");
        testMatch("{foo}", "/bob", "foo", "bob");
        testMatch("/{foo}/", "/bob/", "foo", "bob");

        // test that brackets (and the possibility of recursive templates) don't mess up the matching
        testMatch("/{value}", "/{value}", "value", "{value}");
    }

    @Test
    public void wildCardTests() {
        // wildcard matches
        testMatch("/*", "/docs/mydoc/test", "*", "docs/mydoc/test");
        testMatch("/docs/*", "/docs/mydoc/test", "*", "mydoc/test");
        testMatch("/docs*", "/docs/mydoc/test", "*", "/mydoc/test");
        testMatch("/docs/*", "/docs/mydoc/test/test2", "*", "mydoc/test/test2");
        testMatch("/docs/{docId}/*", "/docs/mydoc/test", "docId", "mydoc", "*", "test");
        testMatch("/docs/{docId}/*", "/docs/mydoc/", "docId", "mydoc", "*", "");
        testMatch("/docs/{docId}/*", "/docs/mydoc/test/test2/test3/test4", "docId", "mydoc", "*",
                "test/test2/test3/test4");
        testMatch("/docs/{docId}/{docId2}/*", "/docs/mydoc/test/test2/test3/test4", "docId", "mydoc", "docId2", "test",
                "*", "test2/test3/test4");
    }

    @SuppressWarnings("ThrowableResultIgnored")
    public void testNullPath() {
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            PathTemplateParser.parseTemplate(null, new Object());
        });
    }

    @Test
    public void testDetectDuplicates() {
        final HashSet<PathTemplateParser.PathTemplatePatternEqualsAdapter<PathTemplateParser.PathTemplate<Object>>> seen = new HashSet<>();
        seen.add(new PathTemplateParser.PathTemplatePatternEqualsAdapter<>(
                PathTemplateParser.parseTemplate("/bob/{foo}", new Object())
        ));
        Assert.assertTrue(seen.contains(new PathTemplateParser.PathTemplatePatternEqualsAdapter<>(
                PathTemplateParser.parseTemplate("/bob/{ak}", new Object())
        )));
        Assert.assertFalse(seen.contains(new PathTemplateParser.PathTemplatePatternEqualsAdapter<>(
                PathTemplateParser.parseTemplate("/bob/{ak}/other", new Object())
        )));
    }

    @Test
    public void testTrailingSlash() {
        final String t1 = "target-1";

        PathTemplateRouter<String> router = PathTemplateRouterFactory.Builder.newBuilder()
                .updateDefaultTarget(defaultTarget)
                .addTemplate("/bob/", () -> t1)
                .build();
        Assert.assertNotSame(t1, router.route("/bob").getTarget());
        Assert.assertSame(t1, router.route("/bob/").getTarget());

        router = PathTemplateRouterFactory.Builder.newBuilder()
                .updateDefaultTarget(defaultTarget)
                .addTemplate("/bob/{id}/", () -> t1)
                .build();

        Assert.assertNotSame(t1, router.route("/bob/1").getTarget());
        Assert.assertSame(t1, router.route("/bob/1/").getTarget());
    }

    private void testMatch(final String template, final String path, final String... pathParams) {
        Assert.assertEquals(0, pathParams.length % 2);
        final Map<String, String> expected = new HashMap<>();
        for (int i = 0; i < pathParams.length; i += 2) {
            expected.put(pathParams[i], pathParams[i + 1]);
        }

        final String t1 = "target-1";
        final PathTemplateRouter<String> router = PathTemplateRouterFactory.Builder.newBuilder()
                .updateDefaultTarget(defaultTarget)
                .addTemplate(template, () -> t1)
                .build();

        final PathTemplateRouteResult<String> routeResult = router.route(path);
        Assert.assertSame("Failed. Template: " + template, t1, routeResult.getTarget());
        Assert.assertEquals(expected, routeResult.getParameters());
    }

    /* =============================================================================================================
        Below are some performance benchmarks that compareMostSpecificToLeastSpecific the performance of the existing path template matcher
        with the performance of the new path template router.

        The @Test annoation is usually commented out, to prevent these from running during regular builds.
        Uncomment to run and see the results.
    ============================================================================================================= */
    private static List<String> createTemplates(
            final int segmentCount,
            final int n
    ) {
        final List<String> result = new LinkedList<>();

        for (int s = 0; s < segmentCount; s++) {
            for (int vp = -1; vp <= s; vp++) {
                for (int i = 0; i < n; i++) {
                    final StringBuilder sb = new StringBuilder();
                    boolean duplicate = false;
                    for (int j = 0; j <= s; j++) {
                        if (vp == j) {
                            if (j > 0 || i == 0) {
                                sb.append("/{var_").append(i).append("_").append(j).append("}");
                            } else {
                                duplicate = true;
                            }
                        } else {
                            sb.append("/path-").append(i).append("-seg-").append(j);
                        }
                    }
                    if (!duplicate) {
                        result.add(sb.toString());
                    }
                }
            }
        }

        return result;
    }

    private static List<String> createRequests(
            final int segmentCount,
            final int n
    ) {
        final List<String> result = new LinkedList<>();

        for (int s = 0; s < segmentCount; s++) {
            for (int vp = -1; vp <= s; vp++) {
                for (int i = 0; i < n; i++) {
                    final StringBuilder sb = new StringBuilder();
                    for (int j = 0; j <= s; j++) {
                        if (vp == j) {
                            sb.append("/a-value-for-the-var");
                        } else {
                            sb.append("/path-").append(i).append("-seg-").append(j);
                        }
                    }
                    result.add(sb.toString());
                }
            }
        }

        return result;
    }

    private static long routeOld(
            final int segmentCount,
            final int n,
            final int requestCount
    ) {
        final List<String> templates = createTemplates(segmentCount, n);
        final String[] requests = createRequests(segmentCount, n).toArray(String[]::new);
        final int requestsLen = requests.length;

        final PathTemplateMatcher<String> matcher = new PathTemplateMatcher<>();
        for (final String template : templates) {
            matcher.add(PathTemplate.create(template), template);
        }

        PathTemplateMatcher.PathMatchResult<String> pathMatchResult;
        final long startMillis = System.currentTimeMillis();
        for (int i = 0; i < requestCount; i++) {
            pathMatchResult = matcher.match(requests[i % requestsLen]);
        }

        final long endMillis = System.currentTimeMillis();

        return endMillis - startMillis;
    }

    private static long routeNew(
            final int segmentCount,
            final int n,
            final int requestCount
    ) {
        final List<String> templates = createTemplates(segmentCount, n);
        final String[] requests = createRequests(segmentCount, n).toArray(String[]::new);
        final int requestsLen = requests.length;

        final PathTemplateRouterFactory.Builder<Supplier<String>, String> routerBuilder = PathTemplateRouterFactory.Builder
                .newBuilder()
                .updateDefaultTarget(
                        "default"
                );
        for (final String template : templates) {
            routerBuilder.addTemplate(template, () -> template);
        }
        final PathTemplateRouter<String> router = routerBuilder.build();

        PathTemplateRouteResult<String> pathRouteResult;
        final long startMillis = System.currentTimeMillis();
        for (int i = 0; i < requestCount; i++) {
            pathRouteResult = router.route(requests[i % requestsLen]);
        }

        final long endMillis = System.currentTimeMillis();

        return endMillis - startMillis;
    }

//    @Test
    public void comparePerformance() {
        final int warmUpSeconds = 10;
        final int segmentCount = 7;
        final int maxN = 36;
        final int requestCount = 20_000_000;
        final String resultsFile = "/tmp/path-template-router-performance.txt";

        // JVM warm up.
        long endWarmup = System.currentTimeMillis() + warmUpSeconds * 1_000L;
        while (System.currentTimeMillis() < endWarmup) {
            routeOld(7, 1, requestCount);
            routeNew(7, 1, requestCount);
        }

        // Run the performance benchmarks.
        final int[][] results = new int[maxN][];
        for (int i = 0; i < maxN; i++) {
            results[i] = new int[5];
            results[i][0] = i + 1;
            results[i][1] = createTemplates(segmentCount, i + 1).size();
            results[i][2] = requestCount;
            results[i][3] = (int) routeOld(segmentCount, i + 1, requestCount);
            results[i][4] = (int) routeNew(segmentCount, i + 1, requestCount);
        }

        // Write the results to a file.
        final File file = new File(resultsFile);
        if (file.exists()) {
            file.delete();
        }

        try (PrintWriter pw = new PrintWriter(file)) {

            pw.println("n,template_count,request_count,old,new");
            for (int i = 0; i < maxN; i++) {
                pw.println(String.format(
                        "%d,%d,%d,%d,%d",
                        results[i][0], results[i][1], results[i][2], results[i][3], results[i][4]
                ));
            }
        } catch (final IOException ex) {
            ex.printStackTrace();
        }
    }
}
