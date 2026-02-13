/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
package io.undertow.server.handlers;

import io.undertow.Handlers;
import io.undertow.server.handlers.builder.PredicatedHandler;
import io.undertow.server.handlers.builder.PredicatedHandlersParser;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;

/**
 * Test basic resource serving via predicate handlers
 *
 * @author baranowb
 *
 */
@RunWith(DefaultServer.class)
public class ResourcePredicateHandlerTestCase {

    private static final String DIR_PREFIXED = "prefix-resource-dir";
    private static final String DIR_PATH = "path-resource-dir";
    private static final String FILE_NAME_LEVEL_0 = "file0";
    private static final String FILE_NAME_LEVEL_1 = "file1";
    private static final String DIR_SUB = "sub_dir";
    private static final String GIBBERISH = "Gibberish, what did you expect?";

    private static final String TEST_PREFIX = "prefixToTest";

    @Test
    public void testPrefixMatchWithURIAltering() throws IOException {
        final PathsRetainer pathsRetainer = createTestDir(DIR_PREFIXED, false);
        DefaultServer.setRootHandler(Handlers.predicates(
                PredicatedHandlersParser
                        .parse("path-prefix(/" + TEST_PREFIX + ")-> { set(attribute=%U,value=${remaining}); resource(location='"
                                + pathsRetainer.root.toString() + "',allow-listing=true) }", getClass().getClassLoader()),
                exchange -> {
                }));
        testURLListing(pathsRetainer, true);
    }

    @Test
    public void testPrefixMatchNoURIAltering() throws IOException {
        final PathsRetainer pathsRetainer = createTestDir(DIR_PREFIXED, true);
        DefaultServer.setRootHandler(Handlers.predicates(

                PredicatedHandlersParser
                        .parse("path-prefix(/" + TEST_PREFIX + ")-> { resource(location='"
                                + pathsRetainer.root.toString() + "',allow-listing=true) }", getClass().getClassLoader()),
                exchange -> {
                }));
        testURLListing(pathsRetainer, true);
    }

    @Test
    public void testPathMatchNoURIAltering() throws IOException {
        final PathsRetainer pathsRetainer = createTestDir(DIR_PATH, true);
        //No idea why parsing does not work in one go
        final List<PredicatedHandler> lst = new ArrayList<>();
        lst.addAll(PredicatedHandlersParser
                .parse("path(/" + TEST_PREFIX + ")-> { resource(location='"
                        + pathsRetainer.root.toString() + "',allow-listing=true)", getClass().getClassLoader()));
        lst.addAll(PredicatedHandlersParser
                .parse("path(/" + TEST_PREFIX + "/" + pathsRetainer.sub.getFileName() + ")-> { resource(location='"
                        + pathsRetainer.root.toString() + "',allow-listing=true) }", getClass().getClassLoader()));
        DefaultServer.setRootHandler(Handlers.predicates(lst,
                exchange -> Assert.assertFalse(true)));
        testURLListing(pathsRetainer, false);
    }

    private void testURLListing(final PathsRetainer pathsRetainer, boolean testFile) throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/" + TEST_PREFIX + "/");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String bodyToTest = EntityUtils.toString(result.getEntity());
                //this is not optimal...
                Assert.assertTrue(bodyToTest.contains("href='/" + TEST_PREFIX + "/" + pathsRetainer.sub.getFileName() + "/'>" + pathsRetainer.sub.getFileName() + "</a>"));
                Assert.assertTrue(bodyToTest.contains("href='/" + TEST_PREFIX + "/" + pathsRetainer.rootFile.getFileName() + "'>" + pathsRetainer.rootFile.getFileName() + "</a>"));
                return null;
            });
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/" + TEST_PREFIX + "/" + pathsRetainer.sub.getFileName() + "/");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String bodyToTest = EntityUtils.toString(result.getEntity());
                Assert.assertTrue(bodyToTest.contains("href='/" + TEST_PREFIX + "/'>[..]</a>"));
                Assert.assertTrue(bodyToTest.contains("href='/" + TEST_PREFIX + "/" + pathsRetainer.sub.getFileName() + "/" + pathsRetainer.subFile.getFileName() + "'>" + pathsRetainer.subFile.getFileName() + "</a>"));
                return null;
            });
            if (testFile) {
                get = new HttpGet(DefaultServer.getDefaultServerURL() + "/" + TEST_PREFIX + "/" + pathsRetainer.sub.getFileName() + "/" + pathsRetainer.subFile.getFileName());
                client.execute(get, result -> {
                    Assert.assertEquals(StatusCodes.OK, result.getCode());
                    String bodyToTest = EntityUtils.toString(result.getEntity());
                    Assert.assertEquals(GIBBERISH, bodyToTest);
                    return null;
                });
            }
        }
    }

    private PathsRetainer createTestDir(final String dirName, final boolean prefixDirectory) throws IOException {
        final FileAttribute<?>[] attribs = new FileAttribute<?>[]{};
        final PathsRetainer pathsRetainer = new PathsRetainer();
        Path dir = Files.createTempDirectory(dirName);
        if (prefixDirectory) {
            //dont use temp, as it will add random stuff
            //parent is already temp
            File f = dir.toFile();
            f = new File(f, TEST_PREFIX);
            Assert.assertTrue(f.mkdir());
            pathsRetainer.root = dir;
            dir = f.toPath();
        } else {
            pathsRetainer.root = dir;
        }

        Path file = Files.createTempFile(dir, FILE_NAME_LEVEL_0, ".txt", attribs);
        pathsRetainer.rootFile = file;
        writeGibberish(file);
        final Path subdir = Files.createTempDirectory(dir, DIR_SUB);
        pathsRetainer.sub = subdir;
        file = Files.createTempFile(subdir, FILE_NAME_LEVEL_1, ".txt", attribs);
        pathsRetainer.subFile = file;
        writeGibberish(file);
        return pathsRetainer;
    }

    private void writeGibberish(final Path p) throws IOException {
        Files.write(p, GIBBERISH.getBytes());
    }

    private static class PathsRetainer {
        private Path root;
        private Path rootFile;
        private Path sub;
        private Path subFile;
    }
}
