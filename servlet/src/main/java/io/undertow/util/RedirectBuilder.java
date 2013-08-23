package io.undertow.util;

import io.undertow.server.HttpServerExchange;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Deque;
import java.util.Map;

/**
 * Utility class for building redirects.
 *
 * @author Stuart Douglas
 */
public class RedirectBuilder {

    public static final String UTF_8 = "UTF-8";

    /**
     * Redirects to a new relative path. All other data from the exchange is preserved.
     *
     * @param exchange        The HTTP server exchange
     * @param newRelativePath The new relative path
     * @return
     */
    public static String redirect(final HttpServerExchange exchange, final String newRelativePath) {
        try {
            StringBuilder uri = new StringBuilder(exchange.getRequestScheme());
            uri.append("://");
            uri.append(exchange.getHostAndPort());
            uri.append(URLEncoder.encode(exchange.getResolvedPath(), UTF_8));
            if (exchange.getResolvedPath().endsWith("/")) {
                if (newRelativePath.startsWith("/")) {
                    uri.append(URLEncoder.encode(newRelativePath.substring(1), UTF_8));
                } else {
                    uri.append(URLEncoder.encode(newRelativePath, UTF_8));
                }
            } else {
                if (!newRelativePath.startsWith("/")) {
                    uri.append('/');
                }
                uri.append(URLEncoder.encode(newRelativePath, UTF_8));
            }
            if(!exchange.getPathParameters().isEmpty()) {
                boolean first = true;
                uri.append(';');
                for(Map.Entry<String, Deque<String>> param : exchange.getPathParameters().entrySet()) {
                    for(String value : param.getValue()) {
                        if(first) {
                            first = false;
                        } else {
                            uri.append('&');
                        }
                        uri.append(URLEncoder.encode(param.getKey(), UTF_8));
                        uri.append('=');
                        uri.append(URLEncoder.encode(value, UTF_8));
                    }
                }
            }
            if(!exchange.getQueryString().isEmpty()) {
                uri.append('?');
                uri.append(exchange.getQueryString());
            }
            return uri.toString();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private RedirectBuilder() {

    }
}
