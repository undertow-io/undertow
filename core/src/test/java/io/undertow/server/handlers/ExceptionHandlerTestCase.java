package io.undertow.server.handlers;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;

import java.io.IOException;

import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DefaultServer.class)
public class ExceptionHandlerTestCase {

    @Test
    public void testExceptionMappers() throws IOException {
        HttpHandler pathHandler = Handlers.path()
                .addExactPath("/", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send("expected");
                    }
                })
                .addExactPath("/exceptionParent", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        throw new ParentException();
                    }
                })
                .addExactPath("/exceptionChild", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        throw new ChildException();
                    }
                })
                .addExactPath("/exceptionAnotherChild", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        throw new AnotherChildException();
                    }
                })
                .addExactPath("/illegalArgumentException", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        throw new IllegalArgumentException();
                    }
                });

        HttpHandler exceptionHandler = Handlers.exceptionHandler(pathHandler)
                .addExceptionHandler(ChildException.class, new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send("child exception handled");
                    }
                })
                .addExceptionHandler(ParentException.class, new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send("parent exception handled");
                    }
                })
                .addExceptionHandler(Throwable.class, new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send("catch all throwables");
                    }
                });
        DefaultServer.setRootHandler(exceptionHandler);

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("expected", HttpClientUtils.readResponse(result));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/exceptionParent");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("parent exception handled", HttpClientUtils.readResponse(result));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/exceptionChild");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("child exception handled", HttpClientUtils.readResponse(result));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/exceptionAnotherChild");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("parent exception handled", HttpClientUtils.readResponse(result));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/illegalArgumentException");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("catch all throwables", HttpClientUtils.readResponse(result));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testReThrowUnmatchedException() throws IOException {
        HttpHandler pathHandler = Handlers.path()
                .addExactPath("/", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        throw new IllegalArgumentException();
                    }
                });

        // intentionally not adding any exception handlers
        final HttpHandler exceptionHandler = Handlers.exceptionHandler(pathHandler);
        DefaultServer.setRootHandler(exceptionHandler);

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.INTERNAL_SERVER_ERROR, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testAttachException() throws IOException {
        HttpHandler pathHandler = Handlers.path()
                .addExactPath("/", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        throw new IllegalArgumentException();
                    }
                });

        final HttpHandler exceptionHandler = Handlers.exceptionHandler(pathHandler)
            .addExceptionHandler(IllegalArgumentException.class, new HttpHandler() {
                @Override
                public void handleRequest(HttpServerExchange exchange) throws Exception {
                    exchange.getResponseSender().send("exception handled");
                }
            });

        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                Throwable throwable = exchange.getAttachment(ExceptionHandler.THROWABLE);
                Assert.assertNull(throwable);
                exceptionHandler.handleRequest(exchange);
                throwable = exchange.getAttachment(ExceptionHandler.THROWABLE);
                Assert.assertTrue(throwable instanceof IllegalArgumentException);
            }
        });

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("exception handled", HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private static class ParentException extends Exception {}
    private static class ChildException extends ParentException {}
    private static class AnotherChildException extends ParentException {}

}
