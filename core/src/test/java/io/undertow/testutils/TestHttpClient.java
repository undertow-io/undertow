package io.undertow.testutils;

import javax.net.ssl.SSLContext;

import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;

/**
 * @author Stuart Douglas
 */
public class TestHttpClient extends DefaultHttpClient {

    @Override
    protected HttpRequestRetryHandler createHttpRequestRetryHandler() {
        return new DefaultHttpRequestRetryHandler(0, false);
    }

    public void setSSLContext(final SSLContext sslContext) {
        SchemeRegistry registry = getConnectionManager().getSchemeRegistry();
        registry.unregister("https");
        registry.register(new Scheme("https", 443, new SSLSocketFactory(sslContext)));
        registry.register(new Scheme("https", DefaultServer.getHostSSLPort("default"), new SSLSocketFactory(sslContext)));

    }
}
