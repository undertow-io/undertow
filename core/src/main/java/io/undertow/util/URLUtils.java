package io.undertow.util;

import io.undertow.server.HttpServerExchange;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * Utilities for dealing with URLs
 *
 * @author Stuart Douglas
 */
public class URLUtils {

    public static void parseQueryString(final String string, final HttpServerExchange exchange, final String charset, final boolean doDecode) {
        QUERY_STRING_PARSER.parse(string, exchange, charset, doDecode);
    }

    public static void parsePathParms(final String string, final HttpServerExchange exchange, final String charset, final boolean doDecode) {
        PATH_PARAM_PARSER.parse(string, exchange, charset, doDecode);
    }

    private abstract static class QueryStringParser {

        void parse(final String string, final HttpServerExchange exchange, final String charset, final boolean doDecode) {
            try {
                int stringStart = 0;
                String attrName = null;
                for (int i = 0; i < string.length(); ++i) {
                    char c = string.charAt(i);
                    if (c == '=' && attrName == null) {
                        attrName = string.substring(stringStart, i);
                        stringStart = i + 1;
                    } else if (c == '&') {
                        if (attrName != null) {
                            handle(exchange, decode(charset, attrName, doDecode), decode(charset, string.substring(stringStart, i), doDecode));
                        } else {
                            handle(exchange, decode(charset, string.substring(stringStart, i), doDecode), "");
                        }
                        stringStart = i + 1;
                        attrName = null;
                    }
                }
                if (attrName != null) {
                    handle(exchange, decode(charset, attrName, doDecode), decode(charset, string.substring(stringStart, string.length()), doDecode));
                } else if (string.length() != stringStart) {
                    handle(exchange, decode(charset, string.substring(stringStart, string.length()), doDecode), "");
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        private String decode(String charset, String attrName, final boolean doDecode) throws UnsupportedEncodingException {
            if(doDecode) {
                return URLDecoder.decode(attrName, charset);
            }
            return attrName;
        }

        abstract void handle(final HttpServerExchange exchange, final String key, final String value);
    }

    private static final QueryStringParser QUERY_STRING_PARSER = new QueryStringParser() {
        @Override
        void handle(HttpServerExchange exchange, String key, String value) {
            exchange.addQueryParam(key, value);
        }
    };

    private static final QueryStringParser PATH_PARAM_PARSER = new QueryStringParser() {
        @Override
        void handle(HttpServerExchange exchange, String key, String value) {
            exchange.addPathParam(key, value);
        }
    };


    private URLUtils() {

    }
}
