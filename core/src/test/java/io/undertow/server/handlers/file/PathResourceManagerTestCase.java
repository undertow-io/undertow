package io.undertow.server.handlers.file;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import io.undertow.server.handlers.resource.PathResourceManager;

/**
 * @author Tomaz Cerar (c) 2016 Red Hat Inc.
 */

public class PathResourceManagerTestCase {


    @Test
    public void testGetResource() throws Exception {

        final Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        final PathResourceManager resourceManager = new PathResourceManager(rootPath, 1024 * 1024);
        Assert.assertNotNull(resourceManager.getResource("page.html"));
        Assert.assertNotNull(resourceManager.getResource("./page.html"));
        Assert.assertNotNull(resourceManager.getResource("../file/page.html"));
    }


    @Test
    public void testCantEscapeRoot() throws Exception {

        final Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent().resolve("subdir");
        final PathResourceManager resourceManager = new PathResourceManager(rootPath, 1024 * 1024);
        Assert.assertNotNull(resourceManager.getResource("a.txt"));
        Assert.assertNull(resourceManager.getResource("../page.html"));
    }


    @Test
    public void testBaseDirInSymlink() throws Exception {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().contains("windows"));

        Path filePath = Paths.get(getClass().getResource("page.html").toURI());
        Path rootPath = filePath.getParent();

        Path newDir = rootPath.resolve("newDir");
        Path innerPage = newDir.resolve("page.html");
        Path newSymlink = rootPath.resolve("newSymlink");
        try {
            Files.createDirectories(newDir);
            Files.copy(filePath, innerPage);
            Files.createSymbolicLink(newSymlink, newDir);

            Assert.assertTrue("Ensure that newSymlink is still a symlink as expected", Files.isSymbolicLink(newSymlink));
            final PathResourceManager resourceManager = new PathResourceManager(newSymlink, 1024 * 1024);
            Assert.assertNotNull(resourceManager.getResource("page.html"));
            Assert.assertNull(resourceManager.getResource("Page.html"));
            Assert.assertNotNull(resourceManager.getResource("./page.html"));

        } finally {
            Files.deleteIfExists(newSymlink);
            Files.deleteIfExists(innerPage);
            Files.deleteIfExists(newDir);
            Files.deleteIfExists(newDir);
        }

    }
}
