package io.undertow.servlet.test.proprietry;

import static org.junit.Assert.assertEquals;

import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @see <a href="https://issues.jboss.org/browse/UNDERTOW-1573">UNDERTOW-1573</a>
 */
@RunWith(DefaultServer.class)
public class ExchangeCompletionTestCase {
    private static final String AN_ATTRIBUTE = "an.attribute";
    private static final String A_VALUE = "a.value";

    private static final BlockingQueue<String> completedExchangeAttributes = new LinkedBlockingQueue<>();

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
                        new ServletInfo("servlet", IgnoresRequestAndSetsAttributeServlet.class)
                                .addMapping("/sync")
                                .addInitParam(IgnoresRequestAndSetsAttributeServlet.ATTRIBUTE_KEY, AN_ATTRIBUTE)
                                .addInitParam(IgnoresRequestAndSetsAttributeServlet.ATTRIBUTE_VALUE, A_VALUE))
                .addServlet(
                        new ServletInfo("asyncservlet", IgnoresRequestAndSetsAttributeAsyncServlet.class)
                                .addMapping("/async")
                                .addInitParam(IgnoresRequestAndSetsAttributeServlet.ATTRIBUTE_KEY, AN_ATTRIBUTE)
                                .addInitParam(IgnoresRequestAndSetsAttributeServlet.ATTRIBUTE_VALUE, A_VALUE)
                                .setAsyncSupported(true))
                .addInitialHandlerChainWrapper(new HandlerWrapper() {
                    @Override
                    public HttpHandler wrap(final HttpHandler handler) {
                        return new HttpHandler() {
                            @Override
                            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                                exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
                                    @Override
                                    public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                                        ServletRequestContext context = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);

                                        if (context != null) {
                                            Object result = context.getServletRequest().getAttribute(AN_ATTRIBUTE);

                                            if (result instanceof String) {
                                                completedExchangeAttributes.add((String) result);
                                            }
                                        }

                                        nextListener.proceed();
                                    }
                                });

                                handler.handleRequest(exchange);
                            }
                        };
                    }
                });

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Before
    public void clearLoggedAttributes() {
        completedExchangeAttributes.clear();
    }

    @Test
    public void exchangeCompletionListenersSeeRequestAttributesEvenIfRequestBodyIsNotRead() throws IOException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/sync");
            post.setEntity(new StringEntity("some body that isn't read"));
            client.execute(post);
            assertEquals(A_VALUE, completedExchangeAttributes.poll(1, TimeUnit.SECONDS));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void exchangeCompletionListenersSeeRequestAttributesEvenIfRequestBodyIsNotReadAsync() throws IOException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/async");
            post.setEntity(new StringEntity("some body that isn't read"));
            client.execute(post);
            assertEquals(A_VALUE, completedExchangeAttributes.poll(1, TimeUnit.SECONDS));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
