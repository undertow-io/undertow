package io.undertow.servlet.test.security.form;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletSecurityInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.security.SendUsernameServlet;
import io.undertow.servlet.test.security.constraint.ServletIdentityManager;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ServletFormAuthTestCase {

    public static final String HELLO_WORLD = "Hello World";

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler path = new PathHandler();

        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("servlet", SendUsernameServlet.class)
                .setServletSecurityInfo(new ServletSecurityInfo()
                        .addRoleAllowed("role1"))
                .addMapping("/secured/*");

        ServletInfo echo = new ServletInfo("echo", EchoServlet.class)
                .setServletSecurityInfo(new ServletSecurityInfo()
                        .addRoleAllowed("role1"))
                .addMapping("/secured/echo");

        ServletInfo s1 = new ServletInfo("loginPage", FormLoginServlet.class)
                .setServletSecurityInfo(new ServletSecurityInfo()
                        .addRoleAllowed("group1"))
                .addMapping("/FormLoginServlet");


        ServletIdentityManager identityManager = new ServletIdentityManager();
        identityManager.addUser("user1", "password1", "role1");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setIdentityManager(identityManager)
                .setLoginConfig(new LoginConfig("FORM", "Test Realm", "/FormLoginServlet", "/error.html"))
                .addServlets(s, s1, echo);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        path.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(path);
    }

    @Test
    public void testServletFormAuth() throws IOException {
        TestHttpClient client = new TestHttpClient();
        client.setRedirectStrategy(new DefaultRedirectStrategy() {
            @Override
            public boolean isRedirected(final HttpRequest request, final HttpResponse response, final HttpContext context) throws ProtocolException {
                if (response.getStatusLine().getStatusCode() == 302) {
                    return true;
                }
                return super.isRedirected(request, response, context);
            }
        });
        try {
            final String uri = DefaultServer.getDefaultServerURL() + "/servletContext/secured/test";
            HttpGet get = new HttpGet(uri);
            HttpResponse result = client.execute(get);
            assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Login Page", response);

            BasicNameValuePair[] pairs = new BasicNameValuePair[]{new BasicNameValuePair("j_username", "user1"), new BasicNameValuePair("j_password", "password1")};
            final List<NameValuePair> data = new ArrayList<NameValuePair>();
            data.addAll(Arrays.asList(pairs));
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/j_security_check");

            post.setEntity(new UrlEncodedFormEntity(data));

            result = client.execute(post);
            assertEquals(200, result.getStatusLine().getStatusCode());

            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("user1", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testServletFormAuthWithSavedPostBody() throws IOException {
        TestHttpClient client = new TestHttpClient();
        client.setRedirectStrategy(new DefaultRedirectStrategy() {
            @Override
            public boolean isRedirected(final HttpRequest request, final HttpResponse response, final HttpContext context) throws ProtocolException {
                if (response.getStatusLine().getStatusCode() == 302) {
                    return true;
                }
                return super.isRedirected(request, response, context);
            }
        });
        try {
            final String uri = DefaultServer.getDefaultServerURL() + "/servletContext/secured/echo";
            HttpPost post = new HttpPost(uri);
            post.setEntity(new StringEntity("String Entity"));
            HttpResponse result = client.execute(post);
            assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Login Page", response);

            BasicNameValuePair[] pairs = new BasicNameValuePair[]{new BasicNameValuePair("j_username", "user1"), new BasicNameValuePair("j_password", "password1")};
            final List<NameValuePair> data = new ArrayList<NameValuePair>();
            data.addAll(Arrays.asList(pairs));
            post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/j_security_check");

            post.setEntity(new UrlEncodedFormEntity(data));

            result = client.execute(post);
            assertEquals(200, result.getStatusLine().getStatusCode());

            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("String Entity", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
