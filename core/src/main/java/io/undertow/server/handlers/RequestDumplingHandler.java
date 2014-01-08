package io.undertow.server.handlers;

import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

import io.undertow.UndertowLogger;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.LocaleUtils;

/**
 * Handler that dumps a exchange to a log.
 *
 * @author Stuart Douglas
 */
public class RequestDumplingHandler implements HttpHandler {

    private final HttpHandler next;

    public RequestDumplingHandler(final HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final StringBuilder sb = new StringBuilder();
// Log pre-service information
        final SecurityContext sc = exchange.getSecurityContext();
        sb.append("\n----------------------------REQUEST---------------------------\n");
        sb.append("               URI=" + exchange.getRequestURI() + "\n");
        sb.append(" characterEncoding=" + exchange.getRequestHeaders().get(Headers.CONTENT_ENCODING) + "\n");
        sb.append("     contentLength=" + exchange.getRequestContentLength() + "\n");
        sb.append("       contentType=" + exchange.getRequestHeaders().get(Headers.CONTENT_TYPE) + "\n");
        //sb.append("       contextPath=" + exchange.getContextPath());
        if (sc != null) {
            if (sc.isAuthenticated()) {
                sb.append("          authType=" + sc.getMechanismName() + "\n");
                sb.append("         principle=" + sc.getAuthenticatedAccount().getPrincipal() + "\n");
            } else {
                sb.append("          authType=none" + "\n");
            }
        }

        Map<String, Cookie> cookies = exchange.getRequestCookies();
        if (cookies != null) {
            for (Map.Entry<String, Cookie> entry : cookies.entrySet()) {
                Cookie cookie = entry.getValue();
                sb.append("            cookie=" + cookie.getName() + "=" +
                        cookie.getValue() + "\n");
            }
        }
        for (HeaderValues header : exchange.getRequestHeaders()) {
            for (String value : header) {
                sb.append("            header=" + header.getHeaderName() + "=" + value + "\n");
            }
        }
        sb.append("            locale=" + LocaleUtils.getLocalesFromHeader(exchange.getRequestHeaders().get(Headers.ACCEPT_LANGUAGE)) + "\n");
        sb.append("            method=" + exchange.getRequestMethod() + "\n");
        Map<String, Deque<String>> pnames = exchange.getQueryParameters();
        for (Map.Entry<String, Deque<String>> entry : pnames.entrySet()) {
            String pname = entry.getKey();
            Iterator<String> pvalues = entry.getValue().iterator();
            sb.append("         parameter=");
            sb.append(pname);
            sb.append('=');
            while (pvalues.hasNext()) {
                sb.append(pvalues.next());
                if (pvalues.hasNext()) {
                    sb.append(", ");
                }
            }
            sb.append("\n");
        }
        //sb.append("          pathInfo=" + exchange.getPathInfo());
        sb.append("          protocol=" + exchange.getProtocol() + "\n");
        sb.append("       queryString=" + exchange.getQueryString() + "\n");
        sb.append("        remoteAddr=" + exchange.getSourceAddress() + "\n");
        sb.append("        remoteHost=" + exchange.getSourceAddress().getHostName() + "\n");
        //sb.append("requestedSessionId=" + exchange.getRequestedSessionId());
        sb.append("            scheme=" + exchange.getRequestScheme() + "\n");
        sb.append("              host=" + exchange.getRequestHeaders().getFirst(Headers.HOST) + "\n");
        sb.append("        serverPort=" + exchange.getDestinationAddress().getPort() + "\n");
        //sb.append("       servletPath=" + exchange.getServletPath());
        //sb.append("          isSecure=" + exchange.isSecure());

        exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
            @Override
            public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
                // Log post-service information
                sb.append("--------------------------RESPONSE--------------------------\n");
                if (sc != null) {
                    if (sc.isAuthenticated()) {
                        sb.append("          authType=" + sc.getMechanismName() + "\n");
                        sb.append("         principle=" + sc.getAuthenticatedAccount().getPrincipal() + "\n");
                    } else {
                        sb.append("          authType=none" + "\n");
                    }
                }
                sb.append("     contentLength=" + exchange.getResponseContentLength() + "\n");
                sb.append("       contentType=" + exchange.getResponseHeaders().getFirst(Headers.CONTENT_TYPE) + "\n");
                Map<String, Cookie> cookies = exchange.getResponseCookies();
                if (cookies != null) {
                    for (Cookie cookie : cookies.values()) {
                        sb.append("            cookie=" + cookie.getName() + "=" + cookie.getValue() + "; domain=" + cookie.getDomain() + "; path=" + cookie.getPath() + "\n");
                    }
                }
                for (HeaderValues header : exchange.getResponseHeaders()) {
                    for (String value : header) {
                        sb.append("            header=" + header.getHeaderName() + "=" + value + "\n");
                    }
                }
                sb.append("            status=" + exchange.getResponseCode() + "\n");
                sb.append("==============================================================");

                nextListener.proceed();
                UndertowLogger.REQUEST_DUMPER_LOGGER.info(sb.toString());
            }
        });


        // Perform the exchange
        next.handleRequest(exchange);
    }
}
