package io.undertow.util;

import io.undertow.testutils.category.UnitTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Test the path matcher to ensure that it can handle different cases and
 * protect against common user mistakes either by throwing the proper exception
 * or by fixing them
 *
 * @author Chris Ruffalo
 *
 */
@Category(UnitTest.class)
public class PathMatcherTestCase {

    /**
     * Test simple case with adding a prefix
     *
     */
    @Test
    public void testSimplePrefixCase() {

        PathMatcher<String> pathMatcher = new PathMatcher<>();

        pathMatcher.addPrefixPath("prefix", "response");
        Assert.assertEquals("response", pathMatcher.getPrefixPath("prefix"));
        Assert.assertEquals("response", pathMatcher.getPrefixPath("/prefix"));
        Assert.assertEquals("response", pathMatcher.getPrefixPath("/prefix/"));

        pathMatcher.addPrefixPath("/prefix", "new response");
        Assert.assertEquals("new response", pathMatcher.getPrefixPath("prefix"));
        Assert.assertEquals("new response", pathMatcher.getPrefixPath("/prefix"));
        Assert.assertEquals("new response", pathMatcher.getPrefixPath("/prefix/"));

        pathMatcher.addPrefixPath("/prefix/", "different response");
        Assert.assertEquals("different response", pathMatcher.getPrefixPath("prefix"));
        Assert.assertEquals("different response", pathMatcher.getPrefixPath("/prefix"));
        Assert.assertEquals("different response", pathMatcher.getPrefixPath("/prefix/"));

        pathMatcher.addPrefixPath("/prefix//////////////////////", "last response");
        Assert.assertEquals("last response", pathMatcher.getPrefixPath("prefix"));
        Assert.assertEquals("last response", pathMatcher.getPrefixPath("/prefix"));
        Assert.assertEquals("last response", pathMatcher.getPrefixPath("/prefix/"));

        pathMatcher.clearPaths();
        Assert.assertNull(pathMatcher.getPrefixPath("prefix"));
        Assert.assertNull(pathMatcher.getPrefixPath("/prefix"));
        Assert.assertNull(pathMatcher.getPrefixPath("/prefix/"));
    }

    /**
     * Test simple case with adding a prefix and getting default matches
     *
     */
    @Test
    public void testSimpleMatchCase() {

        PathMatcher<String> pathMatcher = new PathMatcher<>();

        pathMatcher.addPrefixPath("prefix", "response");
        Assert.assertEquals("response", pathMatcher.match("/prefix").getValue());
        Assert.assertEquals("response", pathMatcher.match("/prefix/").getValue());

        pathMatcher.addPrefixPath("/prefix", "new response");
        Assert.assertEquals("new response", pathMatcher.match("/prefix").getValue());
        Assert.assertEquals("new response", pathMatcher.match("/prefix/").getValue());

        pathMatcher.addPrefixPath("/prefix/", "different response");
        Assert.assertEquals("different response", pathMatcher.match("/prefix").getValue());
        Assert.assertEquals("different response", pathMatcher.match("/prefix/").getValue());

        pathMatcher.addPrefixPath("/prefix//////////////////////", "last response");
        Assert.assertEquals("last response", pathMatcher.match("/prefix").getValue());
        Assert.assertEquals("last response", pathMatcher.match("/prefix/").getValue());

        pathMatcher.clearPaths();
        Assert.assertNull(pathMatcher.match("/prefix").getValue());
        Assert.assertNull(pathMatcher.match("/prefix/").getValue());
    }

    /**
     * Test cases around default matches
     *
     */
    @Test
    public void testSimpleDefaultCase() {

        PathMatcher<String> pathMatcher = new PathMatcher<>();

        pathMatcher.addPrefixPath("/", "default");
        Assert.assertEquals("default", pathMatcher.getPrefixPath("/"));
        Assert.assertEquals("default", pathMatcher.match("/").getValue());

        pathMatcher.addPrefixPath("//////", "needs normalize default");
        Assert.assertEquals("needs normalize default", pathMatcher.getPrefixPath("/"));
        Assert.assertEquals("needs normalize default", pathMatcher.match("/").getValue());

        pathMatcher.clearPaths();
        Assert.assertNull(pathMatcher.getPrefixPath("/"));
    }

    /**
     * Test case based on value falling through to default value/handler
     *
     */
    @Test
    public void testDefaultFallthrough() {

        PathMatcher<String> pathMatcher = new PathMatcher<>("default");

        // check defaults
        Assert.assertEquals("default", pathMatcher.getPrefixPath("/"));
        Assert.assertEquals("default", pathMatcher.match("/").getValue());

        // add a few items
        pathMatcher.addPrefixPath("/test1", "test1");
        pathMatcher.addPrefixPath("/test2", "test2");
        pathMatcher.addPrefixPath("/test3", "test3");
        pathMatcher.addPrefixPath("/test4", "test4");

        // check matching with no matches
        Assert.assertEquals("default", pathMatcher.match("/adsfasdfdsaf").getValue());
        Assert.assertEquals("default", pathMatcher.match("/   ").getValue());
        Assert.assertEquals("default", pathMatcher.match("/drooadfas").getValue());
        Assert.assertEquals("default", pathMatcher.match("/thing/thing").getValue());
        Assert.assertEquals("default", pathMatcher.match("").getValue());

        // check that matching actual matches still works
        Assert.assertEquals("test1", pathMatcher.match("/test1").getValue());
        Assert.assertEquals("test2", pathMatcher.match("/test2").getValue());
        Assert.assertEquals("test3", pathMatcher.match("/test3").getValue());
        Assert.assertEquals("test4", pathMatcher.match("/test4").getValue());
    }

}
