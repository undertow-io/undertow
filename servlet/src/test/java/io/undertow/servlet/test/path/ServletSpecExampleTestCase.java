package io.undertow.servlet.test.path;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.MappingMatch;
import java.io.IOException;

/**
 * Test cases for the servlet mapping examples in section 12.2.2 of the
 * <a href="https://javaee.github.io/servlet-spec/downloads/servlet-4.0/servlet-4_0_FINAL.pdf">Servlet 4.0 specification</a>.
 *
 * @author Carter Kozak
 */
@RunWith(DefaultServer.class)
public class ServletSpecExampleTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        // Servlet 4.0 table 12-1 Example Set of Maps
        DeploymentUtils.setupServlet(
                new ServletInfo("servlet1", GetMappingServlet.class)
                        .addMapping("/foo/bar/*"),
                new ServletInfo("servlet2", GetMappingServlet.class)
                        .addMapping("/baz/*"),
                new ServletInfo("servlet3", GetMappingServlet.class)
                        .addMapping("/catalog"),
                new ServletInfo("servlet4", GetMappingServlet.class)
                        .addMapping("*.bop"),
                new ServletInfo("default", GetMappingServlet.class));

    }

    @Test
    public void testOne() {
        doTest("/foo/bar/index.html", MappingMatch.PATH, "index.html", "/foo/bar/*", "servlet1");
    }

    @Test
    public void testTwo() {
        doTest("/foo/bar/index.bop", MappingMatch.PATH, "index.bop", "/foo/bar/*", "servlet1");
    }

    @Test
    public void testThree() {
        doTest("/baz", MappingMatch.PATH, "", "/baz/*", "servlet2");
    }

    @Test
    public void testFour() {
        doTest("/baz/index.html", MappingMatch.PATH, "index.html", "/baz/*", "servlet2");
    }

    @Test
    public void testFive() {
        doTest("/catalog", MappingMatch.EXACT, "catalog", "/catalog", "servlet3");
    }

    @Test
    public void testSix() {
        doTest("/catalog/index.html", MappingMatch.DEFAULT, "", "/", "default");
    }

    @Test
    public void testSeven() {
        doTest("/catalog/racecar.bop", MappingMatch.EXTENSION, "catalog/racecar", "*.bop", "servlet4");
    }

    @Test
    public void testEight() {
        doTest("/index.bop", MappingMatch.EXTENSION, "index", "*.bop", "servlet4");
    }

    private static void doTest(
            // Input request path excluding the servlet context path
            String path,
            // Expected HttpServletMapping result values
            MappingMatch mappingMatch,
            String matchValue,
            String pattern,
            String servletName) {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext" + path);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            String expected = String.format("Mapping match:%s\nMatch value:%s\nPattern:%s\nServlet:%s",
                    mappingMatch.name(), matchValue, pattern, servletName);
            Assert.assertEquals(expected, response);
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
