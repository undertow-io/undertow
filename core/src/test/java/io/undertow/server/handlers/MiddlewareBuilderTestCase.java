package io.undertow.server.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;


@RunWith(DefaultServer.class)
public class MiddlewareBuilderTestCase {

    /*
     * Simple handler for testing that just adds a constant string to an array.
     */
    private static final class ArrayListAppendingHandler implements HttpHandler {
        private final HttpHandler next;
        private final List<String> list;
        private final String toAppend;

        ArrayListAppendingHandler(HttpHandler next, List<String> list, String toAppend) {
            Handlers.handlerNotNull(next);
            this.next = next;
            this.list = list;
            this.toAppend = toAppend;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            list.add(toAppend);
            next.handleRequest(exchange);
        }
    }

    @Test
    public void testHandlersApplyInCorrectOrder() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {

            List<String> handlerOrder = new ArrayList<>();
            List<String> expectedHandlerOrder = new ArrayList<>(Arrays.asList("foo", "bar", "baz"));

            HttpHandler middlewareHandler =
                MiddlewareBuilder.begin((next) -> new ArrayListAppendingHandler(next, handlerOrder, "foo"))
                                 .next((next) -> new ArrayListAppendingHandler(next, handlerOrder, "bar"))
                                 .next((next) -> new ArrayListAppendingHandler(next, handlerOrder, "baz"))
                                 .complete(ResponseCodeHandler.HANDLE_200);

            DefaultServer.setRootHandler(middlewareHandler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(expectedHandlerOrder, handlerOrder);
            HttpClientUtils.readResponse(result);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testHandlersThrowsExceptionOnNull() throws IOException {
        MiddlewareBuilder.begin(null)
                         .complete(ResponseCodeHandler.HANDLE_200);
    }

}
