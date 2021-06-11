package io.undertow.server.handlers;

import io.undertow.UndertowLogger;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.NetworkUtils;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static io.undertow.UndertowMessages.MESSAGES;

/**
 * Handler that implements rfc7239 Forwarded header
 *
 * @author Stuart Douglas
 */
public class ForwardedHandler implements HttpHandler {


    public static final String BY = "by";
    public static final String FOR = "for";
    public static final String HOST = "host";
    public static final String PROTO = "proto";
    private static final String UNKNOWN = "unknown";
    private static final boolean DEFAULT_CHANGE_LOCAL_ADDR_PORT = Boolean.getBoolean("io.undertow.forwarded.change-local-addr-port");
    private static final String CHANGE_LOCAL_ADDR_PORT = "change-local-addr-port";
    private final boolean isChangeLocalAddrPort;
    private final HttpHandler next;

    public ForwardedHandler(HttpHandler next) {
        this(next, DEFAULT_CHANGE_LOCAL_ADDR_PORT);
    }
    public ForwardedHandler(HttpHandler next, boolean isChangeLocalAddrPort) {
        this.next = next;
        this.isChangeLocalAddrPort = isChangeLocalAddrPort;
    }


    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        HeaderValues forwarded = exchange.getRequestHeaders().get(Headers.FORWARDED);
        if (forwarded != null) {
            Map<Token, String> values = new HashMap<>();
            for (String val : forwarded) {
                parseHeader(val, values);
            }
            String host = values.get(Token.HOST);
            String proto = values.get(Token.PROTO);
            String by = values.get(Token.BY);
            String forVal = values.get(Token.FOR);

            if (host != null) {
                exchange.getRequestHeaders().put(Headers.HOST, host);
                if (isChangeLocalAddrPort) {
                    exchange.setDestinationAddress(InetSocketAddress.createUnresolved(exchange.getHostName(), exchange.getHostPort()));
                }
            } else if (by != null) {
                //we only use 'by' if the host is null
                InetSocketAddress destAddress = parseAddress(by);
                if (destAddress != null && isChangeLocalAddrPort) {
                    exchange.setDestinationAddress(destAddress);
                }
            }
            if (proto != null) {
                exchange.setRequestScheme(proto);
            }
            if (forVal != null) {
                InetSocketAddress sourceAddress = parseAddress(forVal);
                if (sourceAddress != null) {
                    exchange.setSourceAddress(sourceAddress);
                }
            }
        }


        next.handleRequest(exchange);
    }

    static InetSocketAddress parseAddress(String address) {
        try {
            if (address.equals(UNKNOWN)) {
                return null;
            }
            if (address.startsWith("_")) {
                //obfnode, not much we can do with it
                //if a client cares about it they will need to parse the header themselves
                return null;
            }
            if (address.startsWith("[")) {
                //ipv6 address
                int index = address.indexOf("]");
                String ipPart = address.substring(1, index);

                int pos = address.indexOf(':', index);
                if (pos == -1) {
                    return new InetSocketAddress(NetworkUtils.parseIpv6Address(ipPart), 0);
                } else {
                    return new InetSocketAddress(NetworkUtils.parseIpv6Address(ipPart), parsePort(address.substring(pos + 1)));
                }
            } else {
                int pos = address.indexOf(':');
                if (pos == -1) {
                    return new InetSocketAddress(NetworkUtils.parseIpv4Address(address), 0);
                } else {
                    return new InetSocketAddress(NetworkUtils.parseIpv4Address(address.substring(0, pos)), parsePort(address.substring(pos + 1)));
                }
            }
        } catch (Exception e) {
            UndertowLogger.REQUEST_IO_LOGGER.debug("Failed to parse address", e);
            return null;
        }
    }

    private static int parsePort(String substring) {
        if (substring.startsWith("_")) {
            return 0;
        }
        return Integer.parseInt(substring);
    }


    //package private for testing
    static void parseHeader(final String header, Map<Token, String> response) {
        if (response.size() == Token.values().length) {
            //already parsed everything
            return;
        }
        char[] headerChars = header.toCharArray();

        SearchingFor searchingFor = SearchingFor.START_OF_NAME;
        int nameStart = 0;
        Token currentToken = null;
        int valueStart = 0;

        int escapeCount = 0;
        boolean containsEscapes = false;

        for (int i = 0; i < headerChars.length; i++) {
            switch (searchingFor) {
                case START_OF_NAME:
                    // Eliminate any white space before the name of the parameter.
                    if (headerChars[i] != ';' && !Character.isWhitespace(headerChars[i])) {
                        nameStart = i;
                        searchingFor = SearchingFor.EQUALS_SIGN;
                    }
                    break;
                case EQUALS_SIGN:
                    if (headerChars[i] == '=') {
                        String paramName = String.valueOf(headerChars, nameStart, i - nameStart);
                        currentToken = TOKENS.get(paramName.toLowerCase(Locale.ENGLISH));
                        //we allow unkown tokens, but just ignore them
                        searchingFor = SearchingFor.START_OF_VALUE;
                    }
                    break;
                case START_OF_VALUE:
                    if (!Character.isWhitespace(headerChars[i])) {
                        if (headerChars[i] == '"') {
                            valueStart = i + 1;
                            searchingFor = SearchingFor.LAST_QUOTE;
                        } else {
                            valueStart = i;
                            searchingFor = SearchingFor.END_OF_VALUE;
                        }
                    }
                    break;
                case LAST_QUOTE:
                    if (headerChars[i] == '\\') {
                        escapeCount++;
                        containsEscapes = true;
                    } else if (headerChars[i] == '"' && (escapeCount % 2 == 0)) {
                        String value = String.valueOf(headerChars, valueStart, i - valueStart);
                        if (containsEscapes) {
                            StringBuilder sb = new StringBuilder();
                            boolean lastEscape = false;
                            for (int j = 0; j < value.length(); ++j) {
                                char c = value.charAt(j);
                                if (c == '\\' && !lastEscape) {
                                    lastEscape = true;
                                } else {
                                    lastEscape = false;
                                    sb.append(c);
                                }
                            }
                            value = sb.toString();
                            containsEscapes = false;
                        }
                        if (currentToken != null && !response.containsKey(currentToken)) {
                            response.put(currentToken, value);
                        }

                        searchingFor = SearchingFor.START_OF_NAME;
                        escapeCount = 0;
                    } else {
                        escapeCount = 0;
                    }
                    break;
                case END_OF_VALUE:
                    if (headerChars[i] == ';' || headerChars[i] == ',' || Character.isWhitespace(headerChars[i])) {
                        String value = String.valueOf(headerChars, valueStart, i - valueStart);
                        if (currentToken != null && !response.containsKey(currentToken)) {
                            response.put(currentToken, value);
                        }

                        searchingFor = SearchingFor.START_OF_NAME;
                    }
                    break;
            }
        }

        if (searchingFor == SearchingFor.END_OF_VALUE) {
            // Special case where we reached the end of the array containing the header values.
            String value = String.valueOf(headerChars, valueStart, headerChars.length - valueStart);
            if (currentToken != null && !response.containsKey(currentToken)) {
                response.put(currentToken, value);
            }
        } else if (searchingFor != SearchingFor.START_OF_NAME) {
            // Somehow we are still in the middle of searching for a current value.
            throw MESSAGES.invalidHeader();
        }

    }

    enum Token {
        BY,
        FOR,
        HOST,
        PROTO
    }

    private static final Map<String, Token> TOKENS;

    static {
        Map<String, Token> map = new HashMap<>();
        for (Token token : Token.values()) {
            map.put(token.name().toLowerCase(), token);
        }
        TOKENS = Collections.unmodifiableMap(map);
    }

    private enum SearchingFor {
        START_OF_NAME, EQUALS_SIGN, START_OF_VALUE, LAST_QUOTE, END_OF_VALUE;
    }


    @Override
    public String toString() {
        return "forwarded()";
    }


    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "forwarded";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> params = new HashMap<>();
            params.put(CHANGE_LOCAL_ADDR_PORT, boolean.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return CHANGE_LOCAL_ADDR_PORT;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            Boolean isChangeLocalAddrPort = (Boolean) config.get(CHANGE_LOCAL_ADDR_PORT);
            return new Wrapper(isChangeLocalAddrPort == null ? DEFAULT_CHANGE_LOCAL_ADDR_PORT : isChangeLocalAddrPort);
        }
    }
    private static class Wrapper implements HandlerWrapper {

        private final boolean isChangeLocalAddrPort;

        private Wrapper(boolean isChangeLocalAddrPort) {
            this.isChangeLocalAddrPort = isChangeLocalAddrPort;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new ForwardedHandler(handler, isChangeLocalAddrPort);
        }
    }
}
