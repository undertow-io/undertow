package io.undertow.server.handlers.file;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.undertow.server.handlers.resource.PathResourceManager;
import org.junit.Assert;
import org.junit.Test;

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
}
