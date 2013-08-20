package io.undertow.server.handlers;

import io.undertow.UndertowLogger;
import io.undertow.server.BasicSSLSessionInfo;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.SSLSessionInfo;
import io.undertow.util.Certificates;
import io.undertow.util.HeaderMap;

import javax.security.cert.CertificateException;

import static io.undertow.util.Headers.SSL_CIPHER;
import static io.undertow.util.Headers.SSL_CLIENT_CERT;
import static io.undertow.util.Headers.SSL_SESSION_ID;

/**
 * Handler that sets SSL information on the connection based on the following headers:
 * <p/>
 * <ul>
 * <li>SSL_CLIENT_CERT</li>
 * <li>SSL_CIPHER</li>
 * <li>SSL_SESSION_ID</li>
 * </ul>
 * <p/>
 * If this handler is present in the chain it will always override the SSL session information,
 * even if these headers are not present.
 * <p/>
 * This handler MUST only be used on servers that are behind a reverse proxy, where the reverse proxy
 * has been configured to always set these header for EVERY request (or strip existing headers with these
 * names if no SSL information is present). Otherwise it may be possible for a malicious client to spoof
 * a SSL connection.
 *
 * @author Stuart Douglas
 */
public class SSLHeaderHandler implements HttpHandler {

    public static final String HTTPS = "https";

    private static final ExchangeCompletionListener CLEAR_SSL_LISTENER = new ExchangeCompletionListener() {
        @Override
        public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
            exchange.getConnection().setSslSessionInfo(null);
            nextListener.proceed();
        }
    };

    private final HttpHandler next;

    public SSLHeaderHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        HeaderMap requestHeaders = exchange.getRequestHeaders();
        final String sessionId = requestHeaders.getFirst(SSL_SESSION_ID);
        if (sessionId != null) {
            final String cipher = requestHeaders.getFirst(SSL_CIPHER);
            String clientCert = requestHeaders.getFirst(SSL_CLIENT_CERT);
            //the proxy client replaces \n with ' '
            if (clientCert != null && clientCert.length() > 28) {
                StringBuilder sb = new StringBuilder(clientCert.length() + 1);
                sb.append(Certificates.BEGIN_CERT);
                sb.append('\n');
                sb.append(clientCert.replace(' ', '\n').substring(28, clientCert.length() - 26));//core certificate data
                sb.append('\n');
                sb.append(Certificates.END_CERT);
                clientCert = sb.toString();
            }

            try {
                SSLSessionInfo info = new BasicSSLSessionInfo(sessionId, cipher, clientCert);
                exchange.setRequestScheme(HTTPS);
                exchange.getConnection().setSslSessionInfo(info);
                exchange.addExchangeCompleteListener(CLEAR_SSL_LISTENER);
            } catch (java.security.cert.CertificateException e) {
                UndertowLogger.REQUEST_LOGGER.debugf(e, "Could not create certificate from header %s", clientCert);
            } catch (CertificateException e) {
                UndertowLogger.REQUEST_LOGGER.debugf(e, "Could not create certificate from header %s", clientCert);
            }
        }
        next.handleRequest(exchange);
    }
}
