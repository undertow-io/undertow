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

package io.undertow.server.handlers.file;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.undertow.server.handlers.CanonicalPathHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Lucas Ponce
 */
@RunWith(DefaultServer.class)
public class FileHandlerSymlinksTestCase {

    @Before
    public void createSymlinksScenario() throws IOException, URISyntaxException {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().contains("windows"));


        /**
         * Creating following structure for test:
         *
         * $ROOT_PATH/newDir
         * $ROOT_PATH/newDir/page.html
         * $ROOT_PATH/newDir/innerDir/
         * $ROOT_PATH/newDir/innerDir/page.html
         * $ROOT_PATH/newSymlink -> $ROOT_PATH/newDir
         * $ROOT_PATH/newDir/innerSymlink -> $ROOT_PATH/newDir/innerDir/
         *
         */
        Path filePath = Paths.get(getClass().getResource("page.html").toURI());
        Path rootPath = filePath.getParent();

        Path newDir = rootPath.resolve("newDir");
        Files.createDirectories(newDir);

        Path innerDir = newDir.resolve("innerDir");
        Files.createDirectories(innerDir);

        Files.copy(filePath, newDir.resolve(filePath.getFileName()));
        Files.copy(filePath, innerDir.resolve(filePath.getFileName()));

        Path newSymlink = rootPath.resolve("newSymlink");

        Files.createSymbolicLink(newSymlink, newDir);

        Path innerSymlink = newDir.resolve("innerSymlink");

        Files.createSymbolicLink(innerSymlink, innerDir);
    }

    @After
    public void deleteSymlinksScenario() throws IOException, URISyntaxException {
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();

        Path newSymlink = rootPath.resolve("newSymlink");
        Path newDir = rootPath.resolve("newDir");
        Path page = newDir.resolve("page.html");
        Path innerDir = newDir.resolve("innerDir");
        Path innerSymlink = newDir.resolve("innerSymlink");
        Path innerPage = innerDir.resolve("page.html");

        Files.deleteIfExists(innerSymlink);
        Files.deleteIfExists(newSymlink);
        Files.deleteIfExists(innerPage);
        Files.deleteIfExists(page);
        Files.deleteIfExists(innerDir);
        Files.deleteIfExists(newDir);
    }

    @Test
    public void testCreateSymlinks() throws IOException, URISyntaxException {
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();

        Path newDir = rootPath.resolve("newDir");
        Assert.assertFalse(Files.isSymbolicLink(newDir));

        Path innerDir = newDir.resolve("innerDir");
        Assert.assertFalse(Files.isSymbolicLink(innerDir));

        Path newSymlink = rootPath.resolve("newSymlink");
        Assert.assertTrue(Files.isSymbolicLink(newSymlink));

        Path innerSymlink = newSymlink.resolve("innerSymlink");
        Assert.assertTrue(Files.isSymbolicLink(innerSymlink));

        Path f = innerSymlink.getRoot();
        for (int i=0; i<innerSymlink.getNameCount(); i++) {
            f = f.resolve(innerSymlink.getName(i).toString());
            System.out.println(f + " " + Files.isSymbolicLink(f));
        }
        f = f.resolve(".");
        System.out.println(f + " " + Files.isSymbolicLink(f));
    }

    @Test
    public void testDefaultAccessSymlinkDenied() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        Path newSymlink = rootPath.resolve("newSymlink");

        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(newSymlink, 10485760))
                                    .setDirectoryListingEnabled(false)
                                    .addWelcomeFiles("page.html"))));
            /**
             * This request should return a 404 error, as path contains a symbolic link and by default followLinks is false
             */
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/innerSymlink/");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.NOT_FOUND, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testExplicitAccessSymlinkDeniedForEmptySafePath() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        Path newSymlink = rootPath.resolve("newSymlink");

        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(newSymlink, 10485760, true, ""))
                                    .setDirectoryListingEnabled(false)
                                    .addWelcomeFiles("page.html"))));
            /**
             * This request should return a 404 error, followLinks is true, but empty "" safePaths forbids all symbolics paths inside base path
             */
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/innerSymlink/");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.NOT_FOUND, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testExplicitAccessSymlinkDeniedForInsideSymlinks() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        Path newSymlink = rootPath.resolve("newDir");

        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(newSymlink, 10485760, true, ""))
                                    .setDirectoryListingEnabled(false)
                                    .addWelcomeFiles("page.html"))));
            /**
             * This request should return a 200 code as not symbolic links on path
             */
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/innerDir/page.html");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Header[] headers = result.getHeaders("Content-Type");
            Assert.assertEquals("text/html", headers[0].getValue());
            Assert.assertTrue(response, response.contains("A web page"));

            /**
             * This request should return a 404 error, followLinks is true, but empty "" safePaths forbids all symbolics paths
             */
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/innerSymlink/page.html");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.NOT_FOUND, result.getStatusLine().getStatusCode());


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testExplicitAccessSymlinkGranted() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        Path newSymlink = rootPath.resolve("newSymlink");

        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(newSymlink, 10485760, true, "/"))
                                    .setDirectoryListingEnabled(false)
                                    .addWelcomeFiles("page.html"))));
            /**
             * This request should return a 200 code as "/" can be used to grant all symbolic links paths
             */
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/innerSymlink/page.html");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Header[] headers = result.getHeaders("Content-Type");
            Assert.assertEquals("text/html", headers[0].getValue());
            Assert.assertTrue(response, response.contains("A web page"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testExplicitAccessSymlinkGrantedUsingSpecificFilters() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        Path newSymlink = rootPath.resolve("newSymlink");

        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(newSymlink, 10485760, true, rootPath.toAbsolutePath().toString().concat("/newDir")))
                                    .setDirectoryListingEnabled(false)
                                    .addWelcomeFiles("page.html"))));
            /**
             * This request should return a 200 code as rootPath + "/newDir" is used in the safePaths
             */
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/innerSymlink/page.html");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Header[] headers = result.getHeaders("Content-Type");
            Assert.assertEquals("text/html", headers[0].getValue());
            Assert.assertTrue(response, response.contains("A web page"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testExplicitAccessSymlinkGrantedUsingSpecificFiltersWithDirectoryListingEnabled() throws IOException, URISyntaxException {

        HttpParams params = new SyncBasicHttpParams();
        DefaultHttpClient.setDefaultHttpParams(params);
        HttpConnectionParams.setSoTimeout(params, 300000);

        TestHttpClient client = new TestHttpClient(params);

        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        Path newSymlink = rootPath.resolve("newSymlink");

        try {
            DefaultServer.setRootHandler(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(newSymlink, 10485760, true, rootPath.toAbsolutePath().toString().concat("/newDir")))
                                    .setDirectoryListingEnabled(false)
                                    .addWelcomeFiles("page.html")));
            /**
             * This request should return a 200 code as rootPath + "/newDir" is used in the safePaths
             */
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/innerSymlink/.");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Header[] headers = result.getHeaders("Content-Type");
            Assert.assertEquals("text/html", headers[0].getValue());
            Assert.assertTrue(response, response.contains("A web page"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testExplicitAccessSymlinkDeniedUsingSpecificFilters() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        Path newSymlink = rootPath.resolve("newSymlink");

        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(newSymlink, 10485760, true, rootPath.toAbsolutePath().toString().concat("/otherDir")))
                                    .setDirectoryListingEnabled(false)
                                    .addWelcomeFiles("page.html"))));
            /**
             * This request should return a 404 code as rootPath + "/otherDir" doesnt match in rootPath + "/path/innerSymlink/page.html"
             */
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/innerSymlink/page.html");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.NOT_FOUND, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testExplicitAccessSymlinkDeniedUsingSameSymlinkName() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        Path newSymlink = rootPath.resolve("newSymlink");

        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(newSymlink, 10485760, true, rootPath.toAbsolutePath().toString().concat("/innerSymlink")))
                                    .setDirectoryListingEnabled(false)
                                    .addWelcomeFiles("page.html"))));
            /**
             * This request should return a 404 code as rootPath + "/innerSymlink" in safePaths will not match with canonical "/innerSymlink"
             */
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/innerSymlink/page.html");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.NOT_FOUND, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testResourceManagerBaseSymlink() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        Path newSymlink = rootPath.resolve("newSymlink");

        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(newSymlink, 10485760, true, ""))
                                    .setDirectoryListingEnabled(false)
                                    .addWelcomeFiles("page.html"))));
            /**
             * This request should return a 200, base is a symlink but it should not be checked in the symlinks filter
             */
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/page.html");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            /**
             * A readResponse() is needed in order to release connection and execute next get.
             */
            HttpClientUtils.readResponse(result);

            /**
             * This request should return a 404 code as rootPath + "/innerSymlink" is not matching in symlinks filter"
             */
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/innerSymlink/page.html");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.NOT_FOUND, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testRelativePathSymlinkFilter() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        Path newSymlink = rootPath.resolve("newSymlink");

        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(newSymlink, 10485760, true, "innerDir"))
                                    .setDirectoryListingEnabled(false)
                                    .addWelcomeFiles("page.html"))));
            /**
             * This request should return a 200, innerSymlink is a symlink pointed to innerDir
             */
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/innerSymlink/page.html");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
