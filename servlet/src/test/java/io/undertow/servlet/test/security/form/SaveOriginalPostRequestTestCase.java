package io.undertow.servlet.test.security.form;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletSecurityInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
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
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * <p>Tests if a request made to a secured resource is saved before the client is redirect to the login form. Once the authentication is
 * done, the server should restore the original/saved request.</p>
 *
 * @author Pedro Igor
 */
@RunWith(DefaultServer.class)
public class SaveOriginalPostRequestTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        final PathHandler path = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo securedRequestDumper = new ServletInfo("SecuredRequestDumperServlet", RequestDumper.class)
                                           .setServletSecurityInfo(new ServletSecurityInfo()
                                                                   .addRoleAllowed("role1"))
                                           .addMapping("/secured/dumpRequest");
        ServletInfo unsecuredRequestDumper = new ServletInfo("UnsecuredRequestDumperServlet", RequestDumper.class)
                                             .addMapping("/dumpRequest");
        ServletInfo loginFormServlet = new ServletInfo("loginPage", FormLoginServlet.class)
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
                                 .addServlets(securedRequestDumper, unsecuredRequestDumper, loginFormServlet);

        DeploymentManager manager = container.addDeployment(builder);

        manager.deploy();

        path.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(path);
    }

    @Test
    public void testParametersFromOriginalPostRequest() throws IOException {
        TestHttpClient client = createHttpClient();

        // let's test if a usual POST request have its parameters dumped in the response
        HttpResponse result = executePostRequest(client, "/servletContext/dumpRequest", new BasicNameValuePair("param1", "param1Value"), new BasicNameValuePair("param2", "param2Value"));
        assertEquals(200, result.getStatusLine().getStatusCode());
        String response = HttpClientUtils.readResponse(result);
        assertTrue(response.contains("param1=param1Value/param2=param2Value"));

        // this request should be saved and the client redirect to the login form.
        result = executePostRequest(client, "/servletContext/secured/dumpRequest", new BasicNameValuePair("securedParam1", "securedParam1Value"), new BasicNameValuePair("securedParam2", "securedParam2Value"));
        assertEquals(200, result.getStatusLine().getStatusCode());
        Assert.assertEquals("Login Page", HttpClientUtils.readResponse(result));

        // let's perform a successful authentication and get the request restored
        result = executePostRequest(client, "/servletContext/j_security_check", new BasicNameValuePair("j_username", "user1"), new BasicNameValuePair("j_password", "password1"));
        assertEquals(200, result.getStatusLine().getStatusCode());
        response = HttpClientUtils.readResponse(result);

        // let's check if the original request was saved, including its parameters.
        assertTrue(response.contains("securedParam1=securedParam1Value"));
        assertTrue(response.contains("securedParam2=securedParam2Value"));
    }

    private TestHttpClient createHttpClient() {
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

        return client;
    }

    private HttpResponse executePostRequest(TestHttpClient client, String uri, BasicNameValuePair... parameters) throws IOException {
        HttpPost request = new HttpPost(DefaultServer.getDefaultServerURL() + uri);

        request.setEntity(new UrlEncodedFormEntity(new ArrayList<NameValuePair>(Arrays.asList(parameters))));

        return client.execute(request);
    }

    static class RequestDumper extends HttpServlet {

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            dumpRequest(req, resp);
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            dumpRequest(req, resp);
        }

        private void dumpRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            StringBuilder buffer = new StringBuilder();

            PrintWriter writer = resp.getWriter();

            buffer.append("Method: " + req.getMethod() + "\n");

            Enumeration<String> parameterNames = req.getParameterNames();

            buffer.append("Parameters: ");

            while (parameterNames.hasMoreElements()) {
                String parameterName = parameterNames.nextElement();
                buffer.append(parameterName + "=" + req.getParameter(parameterName));
                buffer.append("/");
            }

            writer.write(buffer.toString());
        }
    }
}
