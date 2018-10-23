package io.undertow.server.handlers.file;

import io.undertow.server.handlers.resource.Resource;
import io.undertow.testutils.category.UnitTest;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.util.ETag;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Tomaz Cerar (c) 2016 Red Hat Inc.
 */
@Category(UnitTest.class)
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
    public void testListDir() throws Exception {

        final Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        final PathResourceManager resourceManager = new PathResourceManager(rootPath, 1024 * 1024);
        Resource subdir = resourceManager.getResource("subdir");
        Resource found = subdir.list().get(0);
        Assert.assertEquals("subdir" + File.separatorChar+ "a.txt", found.getPath());
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

    @Test
    public void testETagFunction() throws Exception {
        final String fileName = "page.html";
        final Path rootPath = Paths.get(getClass().getResource(fileName).toURI()).getParent();
        final ResourceManager resourceManager = PathResourceManager.builder()
                .setBase(rootPath)
                .setETagFunction(new PathResourceManager.ETagFunction() {
                    @Override
                    public ETag generate(Path path) {
                        return new ETag(true, path.getFileName().toString());
                    }
                })
                .build();
        ETag expected = new ETag(true, fileName);
        ETag actual = resourceManager.getResource("page.html").getETag();
        Assert.assertEquals(expected, actual);
    }
}
