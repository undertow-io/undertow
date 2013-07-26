package io.undertow.servlet.test.crosscontext;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class CrossContextClassLoaderTestCase {


    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("includer", IncludeServlet.class)
                .addMapping("/a");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(new TempClassLoader("IncluderClassLoader"))
                .setContextPath("/includer")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("includer.war")
                .addServlet(s);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPath(builder.getContextPath(), manager.start());


        s = new ServletInfo("included", IncludedServlet.class)
                .addMapping("/a");

        builder = new DeploymentInfo()
                .setClassLoader(new TempClassLoader("IncludedClassLoader"))
                .setContextPath("/included")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("included.war")
                .addServlet(s);

        manager = container.addDeployment(builder);
        manager.deploy();
        root.addPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testCrossContextRequest() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/includer/a");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(
                    "Including Servlet Class Loader: IncluderClassLoader\n" +
                            "Including Servlet Context Path: /includer\n" +
                            "Included Servlet Class Loader: IncludedClassLoader\n" +
                            "Including Servlet Context Path: /included\n",
                    response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    private static final class IncludeServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.getWriter().println("Including Servlet Class Loader: " + Thread.currentThread().getContextClassLoader().toString());
            resp.getWriter().println("Including Servlet Context Path: " + req.getServletContext().getContextPath());
            ServletContext context = req.getServletContext().getContext("/included");
            context.getRequestDispatcher("/a").include(req, resp);
        }
    }

    private static final class IncludedServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.getWriter().println("Included Servlet Class Loader: " + Thread.currentThread().getContextClassLoader().toString());
            resp.getWriter().println("Including Servlet Context Path: " + req.getServletContext().getContextPath());
        }
    }


    private static final class TempClassLoader extends ClassLoader {
        private final String name;


        private TempClassLoader(String name) {
            super(TempClassLoader.class.getClassLoader());
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
