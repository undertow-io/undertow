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
        return redirect(exchange, newRelativePath, true);
    }

    /**
     * Redirects to a new relative path. All other data from the exchange is preserved.
     *
     * @param exchange          The HTTP server exchange
     * @param newRelativePath   The new relative path
     * @param includeParameters If query and path parameters from the exchange should be included
     * @return
     */
    public static String redirect(final HttpServerExchange exchange, final String newRelativePath, final boolean includeParameters) {
        try {
            StringBuilder uri = new StringBuilder(exchange.getRequestScheme());
            uri.append("://");
            uri.append(exchange.getHostAndPort());
            uri.append(encodeUrlPart(exchange.getResolvedPath()));
            if (exchange.getResolvedPath().endsWith("/")) {
                if (newRelativePath.startsWith("/")) {
                    uri.append(encodeUrlPart(newRelativePath.substring(1)));
                } else {
                    uri.append(encodeUrlPart(newRelativePath));
                }
            } else {
                if (!newRelativePath.startsWith("/")) {
                    uri.append('/');
                }
                uri.append(encodeUrlPart(newRelativePath));
            }
            if (includeParameters) {
                if (!exchange.getPathParameters().isEmpty()) {
                    boolean first = true;
                    uri.append(';');
                    for (Map.Entry<String, Deque<String>> param : exchange.getPathParameters().entrySet()) {
                        for (String value : param.getValue()) {
                            if (first) {
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
                if (!exchange.getQueryString().isEmpty()) {
                    uri.append('?');
                    uri.append(exchange.getQueryString());
                }
            }
            return uri.toString();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * perform URL encoding
     * <p/>
     * TODO: this whole thing is kinda crapy.
     *
     * @return
     */
    private static String encodeUrlPart(final String part) throws UnsupportedEncodingException {
        //we need to go through and check part by part that a section does not need encoding

        int pos = 0;
        for (int i = 0; i < part.length(); ++i) {
            char c = part.charAt(i);
            if(c == '?') {
                break;
            } else if (c == '/') {
                if (pos != i) {
                    String original = part.substring(pos, i - 1);
                    String encoded = URLEncoder.encode(original, UTF_8);
                    if (!encoded.equals(original)) {
                        return realEncode(part, pos);
                    }
                }
                pos = i + 1;
            } else if (c == ' ') {
                return realEncode(part, pos);
            }
        }
        return part;
    }

    private static String realEncode(String part, int startPos) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        sb.append(part.substring(0, startPos));
        int pos = startPos;
        for (int i = startPos; i < part.length(); ++i) {
            char c = part.charAt(i);
            if(c == '?') {
                break;
            } else if (c == '/') {
                if (pos != i) {
                    String original = part.substring(pos, i - 1);
                    String encoded = URLEncoder.encode(original, UTF_8);
                    sb.append(encoded);
                    sb.append('/');
                    pos = i + 1;
                }
            }
        }

        String original = part.substring(pos);
        String encoded = URLEncoder.encode(original, UTF_8);
        sb.append(encoded);
        return sb.toString();
    }

    private RedirectBuilder() {

    }
}
