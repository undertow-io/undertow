package io.undertow.attribute;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.apache.http.HttpResponse;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.NetworkUtils;
import io.undertow.util.StatusCodes;

/**
 * Tests for read-only ExchangeAttribute implementations.
 * These tests use direct attribute instances/factory methods rather than the parser
 * to test the attribute logic itself, not the parsing logic.
 */
@RunWith(DefaultServer.class)
public class ExchangeAttributeReadTest {

    /**
     * Test for RelativePathAttribute, which stores the relative path of the request.
     * @throws IOException
     */
    @Test
    public void testRelativePathAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.relativePath());

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/some/path"),
            "/some/path"
        );
    }

    /**
     * Test for RequestMethodAttribute, which stores the HTTP method of the request.
     * @throws IOException
     */
    @Test
    public void testRequestMethodAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.requestMethod());

        doSuccessfulRequest(
            new HttpPost(DefaultServer.getDefaultServerURL() + "/test") ,
            "POST"
        );
    }

    /**
     * Test for LocalIPAttribute, which stores the local IP address of the server.
     * @throws IOException
     */
    @Test
    public void testLocalIPAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.localIp());

        // retrieve the expected local IP address from the server configuration
        String expectedLocalIp = DefaultServer.getDefaultServerAddress().getAddress().getHostAddress();
        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            expectedLocalIp
        );
    }

    /**
     * Test for RemoteIPAttribute, which stores the remote IP address of the client.
     * @throws IOException
     */
    @Test
    public void testRemoteIPAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.remoteIp());

        // we expect the remote IP to be the same as the local IP, as the requests comes from the same machine
        String expectedIp = DefaultServer.getDefaultServerAddress().getAddress().getHostAddress();
        doSuccessfulRequest(new HttpGet(DefaultServer.getDefaultServerURL() + "/test"), expectedIp);
    }

    /**
     * Test for LocalPortAttribute, which stores the local port of the server.
     * @throws IOException
     */
    @Test
    public void testLocalPortAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.localPort());

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            String.valueOf(
                (DefaultServer.isProxy() ? DefaultServer.getProxyPort() : DefaultServer.getHostPort()) // use proxy port if testing with proxy
            )
        );
    }

    /**
     * Test for RequestProtocolAttribute, which stores the protocol of the request (e.g. HTTP/1.1).
     * @throws IOException
     */
    @Test
    public void testRequestProtocolAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.requestProtocol());

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            (DefaultServer.isH2() ? "HTTP/2.0" : "HTTP/1.1")
        );
    }

    /**
     * Test for RequestLineAttribute, which stores the full request line (method, URI, protocol).
     * @throws IOException
     */
    @Test
    public void testRequestLineAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.requestList());

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test/path?foo=bar"),
            "GET /test/path?foo=bar " + (DefaultServer.isH2() ? "HTTP/2.0" : "HTTP/1.1")
        );
    }

    /**
     * Test for QueryStringAttribute, which stores the query string of the request.
     * @throws IOException
     */
    @Test
    public void testQueryStringAttribute() throws IOException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/test?foo=bar&baz=qux");

        setRootHandler(ExchangeAttributes.queryString()); // includes question mark

        doSuccessfulRequest(get, "?foo=bar&baz=qux");

        setRootHandler(QueryStringAttribute.BARE_INSTANCE); // excludes question mark

        doSuccessfulRequest(get, "foo=bar&baz=qux");
    }

    /**
     * Test for RequestURLAttribute, which stores the request URL, without the query string.
     * @throws IOException
     */
    @Test
    public void testRequestURLAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.requestURL());

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test/path"),
            "/test/path"
        );

        // uri should not include query string
        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test/path?foo=bar"),
            "/test/path"
        );
    }

    /**
     * Test for RemoteHostAttribute, which stores the remote host name.
     * @throws IOException
     */
    @Test
    public void testRemoteHostAttribute() throws IOException {
        setRootHandler(RemoteHostAttribute.INSTANCE);

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            DefaultServer.getDefaultServerAddress().getAddress().getHostAddress() // host of this machine
        );
    }

    /**
     * Test for RemoteObfuscatedIpAttribute, which stores an obfuscated version of the remote IP address.
     * @throws IOException
     */
    @Test
    public void testRemoteObfuscatedIPAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.remoteObfuscatedIp());

        // obfustocate the full ip
        String obfuscatedIp = NetworkUtils.toObfuscatedString(DefaultServer.getDefaultServerAddress().getAddress());
        doSuccessfulRequest(new HttpGet(DefaultServer.getDefaultServerURL() + "/test"), obfuscatedIp);
    }

    /**
     * Test for IdentUsernameAttribute, which stores the username obtained from identd.
     * @throws IOException
     */
    @Test
    public void testIdentUsernameAttribute() throws IOException {
        setRootHandler(IdentUsernameAttribute.INSTANCE);

        doSuccessfulRequest(new HttpGet(DefaultServer.getDefaultServerURL() + "/test"), ""); // not used
    }

    /**
     * Test for DateTimeAttribute, which stores the current date and time in a specific format.
     * @throws Exception
     */
    @Test
    public void testDateTimeAttribute() throws Exception {
        String dateFormatPattern = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatPattern, java.util.Locale.ENGLISH);

        setRootHandler(new DateTimeAttribute(dateFormatPattern));

        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/test"));
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);

            // verify the response is a valid date in the specified format
            Assert.assertNotNull("DateTime should not be null", response);
            Assert.assertFalse("DateTime should not be empty", response.isEmpty());

            // parse the date to verify it's valid
            try {
                dateFormat.parse(response);
            } catch (ParseException e) {
                fail("Returned Date does not Follow the Expected Format");
            }
        } finally {
            client.getConnectionManager().shutdown();
            client.close();
        }
    }

    /**
     * Test for BytesSentAttribute, which stores the number of bytes sent in the response.
     * @throws IOException
     */
    @Test
    public void testBytesSentAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.bytesSent(false));

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            "0" // we expect 0 bytes sent since the root handler first reads the attribute and then writes a response
        );

        // also check the case where dashIfZero is true, which should return "-" instead of "0"
        setRootHandler(ExchangeAttributes.bytesSent(true));

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            "-"
        );
    }

    /**
     * Test for BytesReadAttribute, which stores the number of bytes read from the request.
     * @throws IOException
     */
    @Test
    public void testBytesReadAttribute() throws IOException {
        ExchangeAttribute attribute = new BytesReadAttribute(false);

        DefaultServer.setRootHandler(new BlockingHandler((HttpServerExchange exchange) -> {

            InputStreamReader reader = new InputStreamReader(exchange.getInputStream());

            // consume the whole stream
            while (reader.read() != -1) {}

            reader.close();

            exchange.getResponseSender().send(attribute.readAttribute(exchange));
        }));

        HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/test");
        post.setEntity(new StringEntity("hello"));

        doSuccessfulRequest(
            post,
            String.valueOf("hello".getBytes().length)
        );
    }

    /**
     * Test for ConstantExchangeAttribute, which stores a constant value.
     * @throws IOException
     */
    @Test
    public void testConstantAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.constant("CONSTANT_VALUE"));

        doSuccessfulRequest(new HttpGet(DefaultServer.getDefaultServerURL() + "/test"), "CONSTANT_VALUE");
    }

    /**
     * Test for RemoteUserAttribute, which stores the remote authenticated user.
     * @throws IOException
     */
    @Test
    public void testRemoteUserAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.remoteUser());

        doSuccessfulRequest(new HttpGet(DefaultServer.getDefaultServerURL() + "/test"), ""); // we expect an empty string since security context is null
    }

    /**
     * Test for AuthenticationTypeExchangeAttribute, which stores the authentication type used.
     * @throws IOException
     */
    @Test
    public void testAuthenticationTypeAttribute() throws IOException {
        setRootHandler(ExchangeAttributes.authenticationType());

        doSuccessfulRequest(new HttpGet(DefaultServer.getDefaultServerURL() + "/test"), ""); // we expect an empty string since security context is null
    }

    /**
     * Test for HostAndPortAttribute, which stores the host + port of the destination
     * @throws IOException
     */
    @Test
    public void testHostAndPortAttribute() throws IOException {
        setRootHandler(HostAndPortAttribute.INSTANCE);

        String host = DefaultServer.getHostAddress();
        int port = DefaultServer.getHostPort();
        doSuccessfulRequest(new HttpGet(DefaultServer.getDefaultServerURL() + "/test"), host + ":" + port);
    }

    /**
     * Test for QuotingExchangeAttribute, which wraps another attribute with appropriate quoting.
     * @throws IOException
     */
    @Test
    public void testQuotingExchangeAttribute() throws IOException {

        Map<String, String> expectedConstantTransformations = Map.of(
            "Hello", "'Hello'",
            "Hello 'W'orld", "'Hello \"'\"W\"'\"orld'",
            "Hello 'World'", "'Hello \"'\"World\"'\"'",
            "'Hello World'", "'\"'\"Hello World\"'\"'"
        );
        for (Map.Entry<String, String> constant : expectedConstantTransformations.entrySet()) {
            // create the constant attribute to wrap in quotes
            ExchangeAttribute constantAttribute = ExchangeAttributes.constant(constant.getKey());

            setRootHandler(QuotingExchangeAttribute.WRAPPER.wrap(constantAttribute));

            doSuccessfulRequest(new HttpGet(DefaultServer.getDefaultServerURL() + "/test"), constant.getValue());
        }
    }

    /**
     * Test for ResponseTimeAttribute, which stores the response time for the request.
     * @throws IOException
     */
    @Test
    public void testResponseTimeAttribute() throws IOException {
        setRootHandler(new ResponseTimeAttribute(TimeUnit.SECONDS));

        // enable the recording of the request start time, in order for ResponseTimeAttribute to calculate the total time
        DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.RECORD_REQUEST_START_TIME, true));

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            "\\d+\\.\\d+", // assert that the response matches a digits.digits format
            false,
            true
        );
    }

    /**
     * Test for SecureProtocolAttribute, which stores the SSL/TLS protocol version.
     * @throws IOException
     */
    @Test
    public void testSecureProtocolAttribute() throws IOException {

        setRootHandler(SecureProtocolAttribute.INSTANCE);
        DefaultServer.startSSLServer();

        try {
            doSuccessfulRequest(
                new HttpGet(DefaultServer.getDefaultServerSSLAddress() + "/test"), // use https instead of http
                // we dont set ssl session info for ajp. See DefaultServer#setRootHandler
                // and the emitted setupProxyHandlerForSSL() call on the ajp proxy definition in DefaultServer#startServer
                (DefaultServer.isAjp() ? "" : "TLSv1.2"),
                true
            );
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    /**
     * Test for SslCipherAttribute, which stores the SSL cipher suite used.
     * @throws IOException
     */
    @Test
    public void testSslCipherAttribute() throws IOException {
        setRootHandler(SslCipherAttribute.INSTANCE);
        DefaultServer.startSSLServer();

        try {
            doSuccessfulRequest(
                new HttpGet(DefaultServer.getDefaultServerSSLAddress() + "/test"), // use https instead of http
                "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                true
            );
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    /**
     * Test for SslClientCertAttribute, which stores the client SSL certificate.
     * @throws IOException
     */
    @Test
    public void testSslClientCertAttribute() throws IOException {
        setRootHandler(SslClientCertAttribute.INSTANCE);
        DefaultServer.startSSLServer();

        try {
            doSuccessfulRequest(
                new HttpGet(DefaultServer.getDefaultServerSSLAddress() + "/test"),
                "-----BEGIN CERTIFICATE-----[\\s\\S]+-----END CERTIFICATE-----", // match everything in between, since the cert has many distinct chars (like .+ but also include \n)
                true,
                true
            );
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    /**
     * Test for SslSessionIdAttribute, which stores the SSL session ID
     * @throws IOException
     */
    @Test
    public void testSslSessionIdAttribute() throws IOException {
        setRootHandler(SslSessionIdAttribute.INSTANCE);
        DefaultServer.startSSLServer();

        try {
            doSuccessfulRequest(
                new HttpGet(DefaultServer.getDefaultServerSSLAddress() + "/test"),
                "[0-9A-Fa-f]+", // match a hex string
                true,
                true
            );
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    /**
     * Test for CompositeExchangeAttribute, which combines multiple attributes into one.
     * @throws IOException
     */
    @Test
    public void testCompositeExchangeAttribute() throws IOException {
        setRootHandler(new CompositeExchangeAttribute(
            new ExchangeAttribute[] {
                ExchangeAttributes.requestMethod(),
                ExchangeAttributes.requestURL(),
                ExchangeAttributes.constant("CONSTANT_VALUE"),
            }
        ));

        doSuccessfulRequest(
            new HttpGet(DefaultServer.getDefaultServerURL() + "/test"),
            "GET" + "/test" + "CONSTANT_VALUE"
        );
    }

    /**
     * Helper method to set the root handler to one that reads the specified attribute and sends it in the response.
     * @param attribute the attribute to read and send in the response
     */
    private void setRootHandler(ExchangeAttribute attribute) {
        DefaultServer.setRootHandler((HttpServerExchange exchange) -> {
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
    private void doSuccessfulRequest(HttpRequestBase method, String expectedResponse) throws IOException {
        doSuccessfulRequest(method, expectedResponse, false);
    }

    /**
     * Helper method to execute a request and assert the response matches the expected value.
     * @param method the HTTP method to execute
     * @param expectedResponse the expected response body
     * @param setSSLContext whether to set an SSLContext to the client before the request
     * @throws IOException
     */
    private void doSuccessfulRequest(HttpRequestBase method, String expectedResponse, boolean setSSLContext) throws IOException {
        doSuccessfulRequest(method, expectedResponse, setSSLContext, false);
    }

    /**
     * Helper method to execute a request and assert the response matches the expected value.
     * @param method the HTTP method to execute
     * @param expectedResponse the expected response body
     * @param setSSLContext whether to set an SSLContext to the client before the request
     * @param isRegex whether the expected response should be treaded as a regex to be matched
     * @throws IOException
     */
    private void doSuccessfulRequest(HttpRequestBase method, String expectedResponse, boolean setSSLContext, boolean isRegex) throws IOException {
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
        } finally {
            client.getConnectionManager().shutdown();
            client.close();
        }
    }
}
