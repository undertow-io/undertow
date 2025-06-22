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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.PredicatedHandlersParser;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;

/**
 * Test basic resource serving via predicate handlers
 *
 * @author baranowb
 *
 */
@RunWith(DefaultServer.class)
public class DirectoryListingEnablerTestCase {

    private static final String DIR_PREFIXED = "prefix-resource-dir";
    private static final String FILE_NAME_LEVEL_0 = "file0";
    private static final String FILE_NAME_LEVEL_1 = "file1";
    private static final String DIR_SUB = "sub_dir";
    private static final String GIBBERISH = "Gibberish, what did you expect?";

    private static final String TEST_PREFIX = "prefixToTest";
    private static final String HEADER_SWITCH = "SwitchHeader";

    @Test
    public void testEnableOnResource() throws IOException {
        final PathsRetainer pathsRetainer = createTestDir(DIR_PREFIXED, false);
        DefaultServer.setRootHandler(Handlers.predicates(

                PredicatedHandlersParser.parse("contains[value=%{i,"+HEADER_SWITCH+"},search='enable'] -> { directory-listing(allow-listing=true)}"
                        + "\ncontains[value=%{i,"+HEADER_SWITCH+"},search='disable'] -> { directory-listing(allow-listing=false)}"+
                                "\npath-prefix(/)-> { resource(location='" + pathsRetainer.root.toString() + "',allow-listing=true) }",
                        getClass().getClassLoader()),
                new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                    }
                }));
        testURLListing(pathsRetainer, false, false, StatusCodes.OK);
        testURLListing(pathsRetainer, true, false, StatusCodes.FORBIDDEN);
        testURLListing(pathsRetainer, true, true, StatusCodes.OK);
    }

    @Test
    public void testEnableWithoutResource() throws IOException {
        final PathsRetainer pathsRetainer = createTestDir(DIR_PREFIXED, false);
        DefaultServer.setRootHandler(Handlers.predicates(

                PredicatedHandlersParser.parse("contains[value=%{i,"+HEADER_SWITCH+"},search='enable'] -> { directory-listing(allow-listing=true)}"
                        + "\ncontains[value=%{i,"+HEADER_SWITCH+"},search='disable'] -> { directory-listing(allow-listing=false)}"+
                        "\npath-prefix(/)-> { resource(location='" + pathsRetainer.root.toString() + "',allow-listing=false) }",
                        getClass().getClassLoader()),
                new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                    }
                }));
        testURLListing(pathsRetainer, false, false, StatusCodes.FORBIDDEN);
        testURLListing(pathsRetainer, true, false, StatusCodes.FORBIDDEN);
        testURLListing(pathsRetainer, true, true, StatusCodes.OK);
    }

    private void testURLListing(final PathsRetainer pathsRetainer, boolean includeHeader, boolean enable, int statusCode) throws IOException {

        try(TestHttpClient client = new TestHttpClient();){
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() +"/");
            if(includeHeader) {
                if(enable) {
                    get.addHeader(HEADER_SWITCH, "enable");
                } else {
                    get.addHeader(HEADER_SWITCH, "disable");
                }
            }
            HttpResponse result = client.execute(get);
            Assert.assertEquals(statusCode, result.getStatusLine().getStatusCode());
            if(statusCode != StatusCodes.OK) {
                return;
            }
            String bodyToTest = EntityUtils.toString(result.getEntity());
            //this is not optimal...
            Assert.assertTrue(bodyToTest + "\n" + pathsRetainer.sub.getFileName(), bodyToTest.contains("href='/"+pathsRetainer.sub.getFileName()+"/'>"+pathsRetainer.sub.getFileName()+"</a>"));
            Assert.assertTrue(bodyToTest + "\n" + pathsRetainer.rootFile.getFileName(), bodyToTest.contains("href='/"+pathsRetainer.rootFile.getFileName()+"'>"+pathsRetainer.rootFile.getFileName()+"</a>"));
        }
    }
    private PathsRetainer createTestDir(final String dirName, final boolean prefixDirectory) throws IOException {
        final FileAttribute<?>[] attribs = new FileAttribute<?>[] {};
        final PathsRetainer pathsRetainer = new PathsRetainer();
        Path dir = Files.createTempDirectory(dirName);
        if (prefixDirectory) {
            //dont use temp, as it will add random stuff
            //parent is already temp
            File f = dir.toFile();
            f = new File(f,TEST_PREFIX);
            Assert.assertTrue(f.mkdir());
            pathsRetainer.root = dir;
            dir = f.toPath();
        } else {
            pathsRetainer.root = dir;
        }

        Path file = Files.createTempFile(dir, FILE_NAME_LEVEL_0,".txt", attribs);
        pathsRetainer.rootFile = file;
        writeGibberish(file);
        final Path subdir = Files.createTempDirectory(dir, DIR_SUB);
        pathsRetainer.sub = subdir;
        file = Files.createTempFile(subdir, FILE_NAME_LEVEL_1,".txt", attribs);
        pathsRetainer.subFile = file;
        writeGibberish(file);
        return pathsRetainer;
    }

    private void writeGibberish(final Path p) throws IOException {
        Files.write(p,GIBBERISH.getBytes());
    }
    private static class PathsRetainer{
        private Path root;
        private Path rootFile;
        private Path sub;
        private Path subFile;
    }
}