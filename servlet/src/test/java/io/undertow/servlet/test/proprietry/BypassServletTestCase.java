package io.undertow.servlet.test.proprietry;

import java.io.IOException;

import javax.servlet.ServletException;

import io.undertow.io.IoCallback;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.MessageServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestListener;
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
public class BypassServletTestCase {

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
                        new ServletInfo("servlet", MessageServlet.class)
                                .addMapping("/")
                                .addInitParam(MessageServlet.MESSAGE, "This is a servlet")
                )
                .addListener(new ListenerInfo(TestListener.class))
                .addInitialHandlerChainWrapper(new HandlerWrapper() {
                    @Override
                    public HttpHandler wrap(final HttpHandler handler) {
                        return new HttpHandler() {
                            @Override
                            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                                if (exchange.getRelativePath().equals("/async")) {
                                    exchange.getResponseSender().send("This is not a servlet", IoCallback.END_EXCHANGE);
                                } else {
                                    handler.handleRequest(exchange);
                                }
                            }
                        };
                    }
                });

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }


    @Test
    public void testServletRequest() throws IOException {
        TestListener.init(2);
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/aa");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("This is a servlet", response);
            Assert.assertArrayEquals(new String[]{"created REQUEST", "destroyed REQUEST"}, TestListener.results().toArray());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testServletBypass() throws IOException {
        TestListener.init(0);
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/async");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("This is not a servlet", response);
            Assert.assertArrayEquals(new String[0], TestListener.results().toArray());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
