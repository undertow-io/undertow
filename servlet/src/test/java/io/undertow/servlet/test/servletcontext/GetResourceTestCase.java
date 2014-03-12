package io.undertow.servlet.test.servletcontext;

import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.IoUtils;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class GetResourceTestCase {


    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(GetResourceTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war")
                .setResourceManager(new TestResourceLoader(GetResourceTestCase.class));

        builder.addServlet(new ServletInfo("ReadFileServlet", ReadFileServlet.class)
                .addMapping("/file"));

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testGetResource() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/file?file=/file.txt");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("File Contents", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testGetResourceSpecialCharacterInFileName() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/file?file=/" + URLEncoder.encode("1#2.txt", "UTF-8"));
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Hello!", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testSpecialCharacterInFileURL() throws IOException {
        String tmp = System.getProperty("java.io.tmpdir");
        FileResourceManager fileResourceManager = new FileResourceManager(new File(tmp), 1);
        File file = new File(tmp, "1#2.txt");
        FileOutputStream f = null;
        try {
            f = new FileOutputStream(file);
            f.write("Hi".getBytes());
        } finally {
            IoUtils.safeClose(f);
        }
        Resource res = fileResourceManager.getResource("1#2.txt");
        InputStream in = null;
        try {
            in = res.getUrl().openStream();
            Assert.assertEquals("Hi", FileUtils.readFile(in));
        } finally {
            IoUtils.safeClose(in);
        }
    }


}
