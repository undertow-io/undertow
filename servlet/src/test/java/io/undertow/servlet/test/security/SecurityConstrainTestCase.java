package io.undertow.servlet.test.security;

import java.io.IOException;
import java.util.Collections;

import javax.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.MessageServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static org.junit.Assert.assertEquals;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SecurityConstrainTestCase {

    public static final String HELLO_WORLD = "Hello World";

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("servlet", MessageServlet.class)
                .addInitParam(MessageServlet.MESSAGE, HELLO_WORLD)
                .addMapping("/role1")
                .addMapping("/role2")
                .addMapping("/secured/role2/*")
                .addMapping("/public");

        ServletCallbackHandler handler = new ServletCallbackHandler();
        handler.addUser("user1", "password1", "group1");
        handler.addUser("user2", "password2", "group2");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setResourceLoader(TestResourceLoader.NOOP_RESOURCE_LOADER)
                .setLoginCallbackHandler(handler)
                .setLoginConfig(new LoginConfig("BASIC", "Test Realm"))
                .addServlet(s);

        builder.addSecurityConstraint(new SecurityConstraint(Collections.singleton(new WebResourceCollection(Collections.<String>emptySet(), Collections.<String>emptySet(), Collections.singleton("/role1"))), Collections.singleton("role1"), TransportGuaranteeType.NONE));

        builder.addPrincipleVsRoleMapping("group1", "role1");
        builder.addPrincipleVsRoleMapping("group2", "role2");

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testExactMatch() throws IOException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/role1");
            HttpResponse result = client.execute(get);
            assertEquals(401, result.getStatusLine().getStatusCode());
            Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
            assertEquals(1, values.length);
            assertEquals(BASIC + " realm=\"Test Realm\"", values[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/role1");
            get.addHeader(AUTHORIZATION.toString(), BASIC + " " + Base64.encodeBase64String("user2:password2".getBytes()));
            result = client.execute(get);
            assertEquals(403, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            client = new DefaultHttpClient();
            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/role1");
            get.addHeader(AUTHORIZATION.toString(), BASIC + " " + Base64.encodeBase64String("user1:password1".getBytes()));
            result = client.execute(get);
            assertEquals(200, result.getStatusLine().getStatusCode());

            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(HELLO_WORLD, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
