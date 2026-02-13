package io.undertow.server.handlers;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(DefaultServer.class)
public class ExceptionHandlerTestCase {

    @Test
    public void testExceptionMappers() throws IOException {
        HttpHandler pathHandler = Handlers.path()
                .addExactPath("/", exchange -> exchange.getResponseSender().send("expected"))
                .addExactPath("/exceptionParent", exchange -> {
                    throw new ParentException();
                })
                .addExactPath("/exceptionChild", exchange -> {
                    throw new ChildException();
                })
                .addExactPath("/exceptionAnotherChild", exchange -> {
                    throw new AnotherChildException();
                })
                .addExactPath("/illegalArgumentException", exchange -> {
                    throw new IllegalArgumentException();
                });

        HttpHandler exceptionHandler = Handlers.exceptionHandler(pathHandler)
                .addExceptionHandler(ChildException.class, exchange ->
                        exchange.getResponseSender().send("child exception handled"))
                .addExceptionHandler(ParentException.class, exchange ->
                        exchange.getResponseSender().send("parent exception handled"))
                .addExceptionHandler(Throwable.class, exchange ->
                        exchange.getResponseSender().send("catch all throwables"));
        DefaultServer.setRootHandler(exceptionHandler);

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("expected", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/exceptionParent");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("parent exception handled", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/exceptionChild");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("child exception handled", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/exceptionAnotherChild");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("parent exception handled", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/illegalArgumentException");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("catch all throwables", HttpClientUtils.readResponse(result));
                return null;
            });

        }
    }

    @Test
    public void testReThrowUnmatchedException() throws IOException {
        HttpHandler pathHandler = Handlers.path()
                .addExactPath("/", exchange -> {
                    throw new IllegalArgumentException();
                });

        // intentionally not adding any exception handlers
        final HttpHandler exceptionHandler = Handlers.exceptionHandler(pathHandler);
        DefaultServer.setRootHandler(exceptionHandler);

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.INTERNAL_SERVER_ERROR, result.getCode());
                return HttpClientUtils.readResponse(result);
            });
        }
    }

    @Test
    public void testAttachException() throws IOException {
        HttpHandler pathHandler = Handlers.path()
                .addExactPath("/", exchange -> {
                    throw new IllegalArgumentException();
                });

        final HttpHandler exceptionHandler = Handlers.exceptionHandler(pathHandler)
                .addExceptionHandler(IllegalArgumentException.class, exchange ->
                        exchange.getResponseSender().send("exception handled"));

        DefaultServer.setRootHandler(exchange -> {
            Throwable throwable = exchange.getAttachment(ExceptionHandler.THROWABLE);
            Assert.assertNull(throwable);
            exceptionHandler.handleRequest(exchange);
            throwable = exchange.getAttachment(ExceptionHandler.THROWABLE);
            Assert.assertTrue(throwable instanceof IllegalArgumentException);
        });

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("exception handled", HttpClientUtils.readResponse(result));
                return null;
            });
        }
    }

    private static class ParentException extends Exception {}
    private static class ChildException extends ParentException {}
    private static class AnotherChildException extends ParentException {}

}
