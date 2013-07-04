package io.undertow.server.handlers;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.PredicatedHandlersParser;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class PredicatedHandlersTestCase {

    @Test
    public void testRewrite() throws IOException {
        DefaultServer.setRootHandler(
                Handlers.predicates(
                        PredicatedHandlersParser.parse(
                                "method[GET] -> set[attribute='%{o,type}', value=get]\n" +
                                        "regex['(.*).css'] -> rewrite['${1}.xcss']\n" +
                                        "set[attribute='%{o,someHeader}', value=always]\n" +
                                        "path-template['/foo/{bar}/{f}'] -> set[attribute='%{o,template}', value='${bar}']", getClass().getClassLoader()), new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send(exchange.getRelativePath());
                    }
                }));

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo/a/b");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("get", result.getHeaders("type")[0].getValue());
            Assert.assertEquals("always", result.getHeaders("someHeader")[0].getValue());
            Assert.assertEquals("a", result.getHeaders("template")[0].getValue());
            Assert.assertEquals("/foo/a/b", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo/a/b.css");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("get", result.getHeaders("type")[0].getValue());
            Assert.assertEquals("always", result.getHeaders("someHeader")[0].getValue());
            Assert.assertEquals("a", result.getHeaders("template")[0].getValue());
            Assert.assertEquals("/foo/a/b.xcss", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
