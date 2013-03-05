package io.undertow.servlet.test.session;

import java.io.IOException;

import javax.servlet.ServletException;

import io.undertow.server.handlers.CookieHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ChangeSessionIdTestCase {
    @BeforeClass
    public static void setup() throws ServletException {

        final CookieHandler cookieHandler = new CookieHandler();
        final PathHandler path = new PathHandler();
        cookieHandler.setNext(path);
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("servlet", ChangeSessionIdServlet.class)
                .addMapping("/aa");
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addListener(new ListenerInfo(ChangeSessionIdListener.class))
                .setResourceLoader(TestResourceLoader.NOOP_RESOURCE_LOADER)
                .addServlet(s);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        path.addPath(builder.getContextPath(), manager.start());
        DefaultServer.setRootHandler(cookieHandler);
    }



    @Test
    public void testChangeSessionId() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/aa");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            String oldId = testResponse(response, null);

            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            oldId = testResponse(response, oldId);

            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            oldId = testResponse(response, oldId);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private String testResponse(final String response, final String expectedOld) {
        final String[] parts = response.split(" ");
        Assert.assertEquals(2, parts.length);
        String oldId = parts[0];
        String newId = parts[1];
        if(expectedOld != null) {
            Assert.assertEquals(expectedOld, oldId);
        }
        Assert.assertFalse(oldId.isEmpty());
        Assert.assertFalse(newId.isEmpty());
        Assert.assertFalse(oldId.equals(newId));
        Assert.assertEquals(oldId, ChangeSessionIdListener.oldId);
        Assert.assertEquals(newId, ChangeSessionIdListener.newId);
        return newId;
    }

}
