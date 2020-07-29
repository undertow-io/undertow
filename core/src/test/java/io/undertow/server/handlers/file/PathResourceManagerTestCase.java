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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    @Test
    public void testNonDefaultFileSystem() throws Exception {
        Path zipFile = Files.createTempFile("undertow", ".zip");
        try {

            String expectedText = "Hello, world!";
            byte[] expectedBytes = expectedText.getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = Files.newOutputStream(zipFile);
                 BufferedOutputStream bos = new BufferedOutputStream(os);
                 ZipOutputStream zos = new ZipOutputStream(bos)) {

                zos.putNextEntry(new ZipEntry("dir/"));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("dir/resource.txt"));
                zos.write(expectedBytes);
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("root_resource.txt"));
                zos.write(expectedBytes);
                zos.closeEntry();
            }

            try (FileSystem zipFileSystem = FileSystems.newFileSystem(zipFile, getClass().getClassLoader())) {

                PathResourceManager resourceManager = new PathResourceManager(zipFileSystem.getPath("/dir"));

                Resource resource = resourceManager.getResource("resource.txt");
                Assert.assertArrayEquals(expectedBytes, Files.readAllBytes(resource.getFilePath()));

                try {
                    resourceManager.registerResourceChangeListener(changes -> {});
                    Assert.fail("registerResourceChangeListener should have failed");
                } catch (IllegalStateException expected) {}

                try {
                    resource.getFile();
                    Assert.fail("getFile should have failed");
                } catch (UnsupportedOperationException expected) {}

                Resource dir = resourceManager.getResource(".");
                Assert.assertTrue(dir.isDirectory());
                List<Resource> list = dir.list();
                Assert.assertEquals(1, list.size());
                Assert.assertEquals(resource.getFilePath().normalize(), list.get(0).getFilePath().normalize());

                Resource outside = resourceManager.getResource("../root_resource.txt");
                Assert.assertNull(outside);

                Resource doesNotExist = resourceManager.getResource("does_not_exist.txt");
                Assert.assertNull(doesNotExist);

                resourceManager.setBase(Paths.get(getClass().getResource("page.html").toURI()).getParent());
                Assert.assertNotNull(resourceManager.getResource("page.html"));
                resourceManager.setBase(zipFileSystem.getPath("/"));
                Assert.assertNotNull(resourceManager.getResource("root_resource.txt"));
                resourceManager.setBase(new File(getClass().getResource("page.html").toURI()).getParentFile());
                Assert.assertNotNull(resourceManager.getResource("page.html"));

            }

        } finally {
            Files.deleteIfExists(zipFile);
        }
    }
}
