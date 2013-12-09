package io.undertow.servlet.test.proprietry;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;

import javax.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.TXServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestListener;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Jason T. Greene
 */
@RunWith(DefaultServer.class)
public class TransferTestCase {

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addServlet(
                        new ServletInfo("servlet", TXServlet.class)
                                .addMapping("/")
                );

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }


    @Test
    public void testServletRequest() throws Exception {
        TestListener.init(2);
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/aa");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            final byte[] response = HttpClientUtils.readRawResponse(result);
            File file = new File(TXServlet.class.getResource(TXServlet.class.getSimpleName() + ".class").toURI());
            byte[] expected = new byte[(int) file.length()];
            DataInputStream dataInputStream = new DataInputStream(new FileInputStream(file));
            dataInputStream.readFully(expected);
            dataInputStream.close();
            Assert.assertArrayEquals(expected, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


}
