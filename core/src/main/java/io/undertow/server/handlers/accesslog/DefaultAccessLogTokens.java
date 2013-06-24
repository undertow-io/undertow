package io.undertow.server.handlers.accesslog;

import java.net.InetSocketAddress;
import java.util.Date;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.DateUtils;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

import static io.undertow.server.handlers.accesslog.TokenHandler.Factory;

/**
 * Default factory for access log tokens.
 * <p/>
 * <p>This factory produces token handlers for the following patterns</p>
 * <ul>
 * <li><b>%a</b> - Remote IP address
 * <li><b>%A</b> - Local IP address
 * <li><b>%b</b> - Bytes sent, excluding HTTP headers, or '-' if no bytes
 * were sent
 * <li><b>%B</b> - Bytes sent, excluding HTTP headers
 * <li><b>%h</b> - Remote host name
 * <li><b>%H</b> - Request protocol
 * <li><b>%l</b> - Remote logical username from identd (always returns '-')
 * <li><b>%m</b> - Request method
 * <li><b>%p</b> - Local port
 * <li><b>%q</b> - Query string (prepended with a '?' if it exists, otherwise
 * an empty string
 * <li><b>%r</b> - First line of the request
 * <li><b>%s</b> - HTTP status code of the response
 * <li><b>%t</b> - Date and time, in Common Log Format format
 * <li><b>%u</b> - Remote user that was authenticated
 * <li><b>%U</b> - Requested URL path
 * <li><b>%v</b> - Local server name
 * <li><b>%D</b> - Time taken to process the request, in millis
 * <li><b>%T</b> - Time taken to process the request, in seconds
 * <li><b>%I</b> - current Request thread name (can compare later with stacktraces)
 * </ul>
 * <p>In addition, the caller can specify one of the following aliases for
 * commonly utilized patterns:</p>
 * <ul>
 * <li><b>common</b> - <code>%h %l %u %t "%r" %s %b</code>
 * <li><b>combined</b> -
 * <code>%h %l %u %t "%r" %s %b "%{Referer}i" "%{User-Agent}i"</code>
 * </ul>
 * <p/>
 * <p>
 * There is also support to write information from the cookie, incoming
 * header, or the session<br>
 * It is modeled after the apache syntax:
 * <ul>
 * <li><code>%{xxx}i</code> for incoming headers
 * <li><code>%{xxx}o</code> for outgoing response headers
 * <li><code>%{xxx}c</code> for a specific cookie
 * <li><code>%{xxx}r</code> xxx is an attribute in the ServletRequest
 * <li><code>%{xxx}s</code> xxx is an attribute in the HttpSession
 * </ul>
 * </p>
 *
 * @author Stuart Douglas
 */
public class DefaultAccessLogTokens implements Factory {

    public static final String REMOTE_IP = "%a";
    public static final String LOCAL_IP = "%A";
    public static final String BYTES_SENT_DASH = "%b";
    public static final String BYTES_SENT = "%B";
    public static final String REMOTE_HOST_NAME = "%h";
    public static final String REQUEST_PROTOCOL = "%H";
    public static final String IDENT_USERNAME = "%l";
    public static final String METHOD = "%m";
    public static final String LOCAL_PORT = "%p";
    public static final String QUERY_STRING = "%q";
    public static final String REQUEST_LINE = "%r";
    public static final String STATUS_CODE = "%s";
    public static final String DATE_TIME = "%t";
    public static final String REMOTE_USER = "%u";
    public static final String REQUESTED_URL = "%U";
    public static final String LOCAL_SERVER_NAME = "%v";
    public static final String TIME_TO_PROCESS_MILLIS = "%D";
    public static final String TIME_TO_PROCESS_SECONDS = "%T";
    public static final String THREAD_NAME = "%I";

    public static final String COMMON = "common";
    public static final String COMBINED = "combined";

    public static final DefaultAccessLogTokens INSTANCE = new DefaultAccessLogTokens();

    private static final CombinedTokenFactory FACTORY;

    static {
        FACTORY = new CombinedTokenFactory(
                remoteIp(),
                localIp(),
                requestProtocol(),
                identUsername(),
                requestMethod(),
                localPort(),
                queryString(),
                requestLine(),
                statusCode(),
                dateTime(),
                remoteUser(),
                requestedUrl(),
                threadName(),
                localServerName(),
                incomingHeaders(),
                outgoingHeaders(),
                cookies()
        );
    }

    @Override
    public TokenHandler create(final String token) {
        return FACTORY.create(token);
    }

    public static final Factory remoteIp() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.equals(REMOTE_IP) || token.equals(REMOTE_HOST_NAME)) {
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            InetSocketAddress peerAddress = (InetSocketAddress) exchange.getConnection().getPeerAddress();
                            return peerAddress.getAddress().getHostAddress();
                        }
                    };
                }
                return null;
            }
        };
    }

    public static final Factory localIp() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.equals(LOCAL_IP)) {
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            InetSocketAddress localAddress = (InetSocketAddress) exchange.getConnection().getLocalAddress();
                            return localAddress.getAddress().getHostAddress();
                        }
                    };
                }
                return null;
            }
        };
    }

    public static final Factory localPort() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.equals(LOCAL_PORT)) {
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            InetSocketAddress localAddress = (InetSocketAddress) exchange.getConnection().getLocalAddress();
                            return Integer.toString(localAddress.getPort());
                        }
                    };
                }
                return null;
            }
        };
    }


    public static final Factory requestProtocol() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.equals(REQUEST_PROTOCOL)) {
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            return exchange.getProtocol().toString();
                        }
                    };
                }
                return null;
            }
        };
    }

    public static final Factory identUsername() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.equals(IDENT_USERNAME)) {
                    return new ConstantAccessLogToken("-");
                }
                return null;
            }
        };
    }

    public static final Factory requestMethod() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.equals(METHOD)) {
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            return exchange.getRequestMethod().toString();
                        }
                    };
                }
                return null;
            }
        };
    }

    public static final Factory queryString() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.equals(QUERY_STRING)) {
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            return exchange.getQueryString();
                        }
                    };
                }
                return null;
            }
        };
    }

    public static final Factory requestLine() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.equals(REQUEST_LINE)) {
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            return new StringBuilder()
                                    .append(exchange.getRequestMethod().toString())
                                    .append(' ')
                                    .append(exchange.getRequestURI())
                                    .append(' ')
                                    .append(exchange.getProtocol().toString()).toString();
                        }
                    };
                }
                return null;
            }
        };
    }

    public static final Factory statusCode() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.equals(STATUS_CODE)) {
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            return Integer.toString(exchange.getResponseCode());
                        }
                    };
                }
                return null;
            }
        };
    }

    public static final Factory dateTime() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.equals(DATE_TIME)) {
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            return DateUtils.toCommonLogFormat(new Date());
                        }
                    };
                }
                return null;
            }
        };
    }

    public static final Factory remoteUser() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.equals(REMOTE_USER)) {
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            SecurityContext sc = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
                            if (sc == null || !sc.isAuthenticated()) {
                                return null;
                            }
                            return sc.getAuthenticatedAccount().getPrincipal().getName();
                        }
                    };
                }
                return null;
            }
        };
    }

    public static final Factory requestedUrl() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.equals(REQUESTED_URL)) {
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            return exchange.getRequestURI();
                        }
                    };
                }
                return null;
            }
        };
    }

    public static final Factory threadName() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.equals(THREAD_NAME)) {
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            return Thread.currentThread().getName();
                        }
                    };
                }
                return null;
            }
        };
    }

    public static final Factory localServerName() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.equals(LOCAL_SERVER_NAME)) {
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            return exchange.getRequestHeaders().getFirst(Headers.HOST);
                        }
                    };
                }
                return null;
            }
        };
    }

    public static final Factory incomingHeaders() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.startsWith("%{") && token.endsWith("}i")) {
                    final HttpString headerName = HttpString.tryFromString(token.substring(2, token.length() - 2));
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            return exchange.getRequestHeaders().getFirst(headerName);
                        }
                    };
                }
                return null;
            }
        };
    }

    public static final Factory outgoingHeaders() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.startsWith("%{") && token.endsWith("}o")) {
                    final HttpString headerName = HttpString.tryFromString(token.substring(2, token.length() - 2));
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            return exchange.getResponseHeaders().getFirst(headerName);
                        }
                    };
                }
                return null;
            }
        };
    }

    public static final Factory cookies() {
        return new Factory() {
            @Override
            public TokenHandler create(final String token) {
                if (token.startsWith("%{") && token.endsWith("}c")) {
                    final String headerName = token.substring(2, token.length() - 2);
                    return new TokenHandler() {
                        @Override
                        public String generateMessage(final HttpServerExchange exchange) {
                            Cookie cookie = exchange.getRequestCookies().get(headerName);
                            if (cookie == null) {
                                return null;
                            }
                            return cookie.getValue();
                        }
                    };
                }
                return null;
            }
        };
    }

    private DefaultAccessLogTokens() {

    }
}
