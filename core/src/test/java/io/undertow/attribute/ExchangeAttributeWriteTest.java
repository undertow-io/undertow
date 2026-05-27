package io.undertow.attribute;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;

import java.io.IOException;
import java.util.HashMap;

import org.apache.http.HttpResponse;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import io.undertow.predicate.Predicate;

/**
 * Tests for writable ExchangeAttribute implementations.
 * These tests use direct attribute instances/factory methods rather than the parser
 * to test the attribute logic itself, not the parsing logic.
 */
@RunWith(DefaultServer.class)
public class ExchangeAttributeWriteTest {

    /**
     * Test for CookieAttribute, which writes to response cookies but reads from request cookies.
     * Since read and write use different sources, we need to send a cookie in the request to verify the read.
     * @throws IOException
     */
    @Test
    public void testCookieAttribute() throws IOException {
        String cookieName = "testCookie";
        String cookieRequestValue = "requestValue";
        String cookieResponseValue = "responseValue";
        CookieAttribute attribute = new CookieAttribute(cookieName);

        setRootHandler(attribute, cookieResponseValue);

        HttpGet request = new HttpGet(DefaultServer.getDefaultServerURL() + "/test");
        // send a cookie that will be read
        request.setHeader("Cookie", cookieName + "=" + cookieRequestValue);

        // verify that the read attribute was the request cookie
        HttpResponse result = doSuccessfulRequest(request, cookieRequestValue);

        // verify that the written attribute was the response cookie
        Assert.assertNotNull(result.getFirstHeader("Set-Cookie"));
        Assert.assertTrue(result.getFirstHeader("Set-Cookie").getValue().equals(cookieName + "=" + cookieResponseValue));
    }

    /**
     * Test for PathParameterAttribute, which stores path parameters.
     * @throws IOException
     */
    @Test
    public void testPathParameterAttribute() throws IOException {
        setRootHandler(new PathParameterAttribute("key"), "value");

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/some/path"),
            "value"
        );
    }

    /**
     * Test for PredicateContextAttribute, which stores predicate context values.
     * Requires the predicate context to be initialized before writing.
     * @throws IOException
     */
    @Test
    public void testPredicateContextAttribute() throws IOException {
        String contextValue = "contextValue";
        PredicateContextAttribute attribute = new PredicateContextAttribute("contextKey");

        // Special handler that initializes the predicate context first
        DefaultServer.setRootHandler((HttpServerExchange exchange) -> {
            // Initialize the predicate context
            exchange.putAttachment(Predicate.PREDICATE_CONTEXT, new HashMap<>());

            // Write the attribute
            attribute.writeAttribute(exchange, contextValue);

            // Read the attribute
            String value = attribute.readAttribute(exchange);
            exchange.getResponseSender().send(value != null ? value : "null");
        });

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            contextValue
        );
    }

    /**
     * Test for QueryParameterAttribute, which stores query parameters.
     * @throws IOException
     */
    @Test
    public void testQueryParameterAttribute() throws IOException {
        setRootHandler(new QueryParameterAttribute("param"), "newValue");

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test?param=oldValue"),
            "newValue"
        );
    }

    /**
     * Test for QueryStringAttribute, which stores the query string.
     * @throws IOException
     */
    @Test
    public void testQueryStringAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.queryString(), "newQuery=newValue");

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test?old=value"),
            "?newQuery=newValue"
        );

        // also test with encoded query string to verify stored decoded query string
        setRootHandler(ExchangeAttributes.queryString(), "param=hello%20world&foo=bar%26baz");

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test?old=value"),
            "?param=hello world&foo=bar&baz"
        );
    }

    /**
     * Test for RelativePathAttribute, which stores the relative path.
     * @throws IOException
     */
    @Test
    public void testRelativePathAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.relativePath(), "/new/path");

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/original/path"),
            "/new/path"
        );
    }

    /**
     * Test for RequestCookieAttribute, which writes and reads from request cookies.
     * @throws IOException
     */
    @Test
    public void testRequestCookieAttribute() throws IOException {
        setRootHandler(new RequestCookieAttribute("reqCookie"), "reqValue");

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            "reqValue"
        );
    }

    /**
     * Test for RequestHeaderAttribute, which stores request headers.
     * @throws IOException
     */
    @Test
    public void testRequestHeaderAttribute() throws IOException {
        setRootHandler(new RequestHeaderAttribute(new HttpString("X-Custom-Header")), "customValue");

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            "customValue"
        );
    }

    /**
     * Test for RequestPathAttribute, which stores the request path.
     * @throws IOException
     */
    @Test
    public void testRequestPathAttribute() throws IOException {
        setRootHandler(RequestPathAttribute.INSTANCE, "/modified/path");

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/original/path"),
            "/modified/path"
        );
    }

    /**
     * Test for RequestSchemeAttribute, which stores the request scheme.
     * @throws IOException
     */
    @Test
    public void testRequestSchemeAttribute() throws IOException {
        setRootHandler(RequestSchemeAttribute.INSTANCE, "https");

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            "https"
        );
    }

    /**
     * Test for RequestURLAttribute, which stores the request URL.
     * @throws IOException
     */
    @Test
    public void testRequestURLAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.requestURL(), "/new/url");

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/old/url"),
            "/new/url"
        );
    }

    /**
     * Test for ResolvedPathAttribute, which stores the resolved path.
     * @throws IOException
     */
    @Test
    public void testResolvedPathAttribute() throws IOException {
        setRootHandler(ResolvedPathAttribute.INSTANCE, "/resolved/path");

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            "/resolved/path"
        );
    }

    /**
     * Test for ResponseCodeAttribute, which stores the HTTP response code.
     * @throws IOException
     */
    @Test
    public void testResponseCodeAttribute() throws IOException {
        setRootHandler(ResponseCodeAttribute.INSTANCE, "201");

        // Custom validation since we set a non-200 status code
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/test"));
            // Verify the status code was set
            Assert.assertEquals(201, result.getStatusLine().getStatusCode());
            // Verify the attribute returns the same value
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("201", response);
        } finally {
            client.getConnectionManager().shutdown();
            client.close();
        }
    }

    /**
     * Test for ResponseCookieAttribute, which writes and reads from response cookies.
     * @throws IOException
     */
    @Test
    public void testResponseCookieAttribute() throws IOException {
        String cookieName = "respCookie";
        String cookieValue = "respValue";
        setRootHandler(new ResponseCookieAttribute(cookieName), cookieValue);

        HttpResponse response = doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            cookieValue
        );

        // Also verify the Set-Cookie header
        Assert.assertNotNull(response.getFirstHeader("Set-Cookie"));
        Assert.assertTrue(response.getFirstHeader("Set-Cookie").getValue().equals(cookieName + "=" + cookieValue));
    }

    /**
     * Test for ResponseHeaderAttribute, which stores response headers.
     * @throws IOException
     */
    @Test
    public void testResponseHeaderAttribute() throws IOException {
        setRootHandler(new ResponseHeaderAttribute(new HttpString("X-Response-Header")), "headerValue");

        HttpResponse response = doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            "headerValue"
        );

        // Also verify the Set-Cookie header
        Assert.assertNotNull(response.getFirstHeader("X-Response-Header"));
        Assert.assertEquals("headerValue", response.getFirstHeader("X-Response-Header").getValue());
    }

    /**
     * Test for ResponseReasonPhraseAttribute, which stores the HTTP response reason phrase.
     * Note: readAttribute returns the standard reason phrase for the status code, not the custom one.
     * To verify the write worked, we need to check the actual HTTP response.
     * @throws IOException
     */
    @Test
    public void testResponseReasonPhraseAttribute() throws IOException {
        DefaultServer.setRootHandler((HttpServerExchange exchange) -> {
            // Write a custom reason phrase
            ExchangeAttribute reasonPhraseAttribute = ExchangeAttributes.responseReasonPhrase();
            reasonPhraseAttribute.writeAttribute(exchange, "Custom Reason");

            // concat the read phrase to the written one, since the read phase uses StatusCodes.getReason instead of the custom one
            String value = reasonPhraseAttribute.readAttribute(exchange) + ": " + exchange.getReasonPhrase();
            exchange.getResponseSender().send(value);
        });

        HttpResponse result = doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            "OK: Custom Reason"
        );

        // http2 has removed support for reason phrase (see HttServerExchange#setReasonPhrase)
        String expectedStatusLineReasonPhrase = DefaultServer.isH2() ? "OK" : "Custom Reason";
        Assert.assertEquals(expectedStatusLineReasonPhrase, result.getStatusLine().getReasonPhrase());
    }

    /**
     * Test for SecureExchangeAttribute, which stores whether the exchange is secure.
     * @throws IOException
     */
    @Test
    public void testSecureExchangeAttribute() throws IOException {
        setRootHandler(SecureExchangeAttribute.INSTANCE, "true");

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            "true"
        );
    }

    /**
     * Test for SubstituteEmptyWrapper, which substitutes empty values with a specified string.
     * @throws IOException
     */
    @Test
    public void testSubstituteEmptyWrapper() throws IOException {
        // Create a PathParameterAttribute that we'll wrap
        PathParameterAttribute innerAttr = new PathParameterAttribute("emptyKey");
        SubstituteEmptyWrapper wrapperFactory = new SubstituteEmptyWrapper("EMPTY");
        ExchangeAttribute wrappedAttr = wrapperFactory.wrap(innerAttr);

        // Test with empty value
        DefaultServer.setRootHandler((HttpServerExchange exchange) -> {
            // Don't set the path parameter, so it will be null/empty
            String value = wrappedAttr.readAttribute(exchange);
            exchange.getResponseSender().send(value != null ? value : "null");
        });

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            "EMPTY"
        );

        // Test with non-empty value
        setRootHandler(wrappedAttr, "notEmpty");

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            "notEmpty"
        );
    }

    /**
     * Helper method to set the root handler to one that reads the specified attribute and sends it in the response.
     * @param attribute the attribute to read and send in the response (after value set)
     * @param newValue the new value to set to the attribute (before reading the attribute)
     */
    private void setRootHandler(ExchangeAttribute attribute, String newValue) {
        DefaultServer.setRootHandler((HttpServerExchange exchange) -> {
            // write on attribute
            attribute.writeAttribute(exchange, newValue);

            // read from attribute
            String value = attribute.readAttribute(exchange);
            exchange.getResponseSender().send(value != null ? value : "");
        });
    }

    /**
     * Helper method to execute a request and assert the response matches the expected value.
     * @param method the HTTP method to execute
     * @param expectedResponse the expected response body
     * @throws IOException
     */
    private HttpResponse doSuccessfulRequest(HttpRequestBase method, String expectedResponse) throws IOException {
        return doSuccessfulRequest(method, expectedResponse, false);
    }

    /**
     * Helper method to execute a request and assert the response matches the expected value.
     * @param method the HTTP method to execute
     * @param expectedResponse the expected response body
     * @param setSSLContext whether to set an SSLContext to the client before the request
     * @throws IOException
     */
    private HttpResponse doSuccessfulRequest(HttpRequestBase method, String expectedResponse, boolean setSSLContext) throws IOException {
        return doSuccessfulRequest(method, expectedResponse, setSSLContext, false);
    }

    /**
     * Helper method to execute a request and assert the response matches the expected value.
     * @param method the HTTP method to execute
     * @param expectedResponse the expected response body
     * @param setSSLContext whether to set an SSLContext to the client before the request
     * @param isRegex whether the expected response should be treaded as a regex to be matched
     * @throws IOException
     */
    private HttpResponse doSuccessfulRequest(HttpRequestBase method, String expectedResponse, boolean setSSLContext, boolean isRegex) throws IOException {
        TestHttpClient client = new TestHttpClient();
        if (setSSLContext) {
            client.setSSLContext(DefaultServer.getClientSSLContext());
        }
        try {
            HttpResponse result = client.execute(method);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            if (isRegex) {
                Assert.assertTrue(response.matches(expectedResponse));
            } else {
                Assert.assertEquals(expectedResponse, response);
            }
            return result;
        } finally {
            client.getConnectionManager().shutdown();
            client.close();
        }
    }
}
