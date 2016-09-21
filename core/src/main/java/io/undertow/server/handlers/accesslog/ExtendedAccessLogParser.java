/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.server.handlers.accesslog;

import io.undertow.UndertowLogger;
import io.undertow.Version;
import io.undertow.attribute.AuthenticationTypeExchangeAttribute;
import io.undertow.attribute.BytesSentAttribute;
import io.undertow.attribute.CompositeExchangeAttribute;
import io.undertow.attribute.ConstantExchangeAttribute;
import io.undertow.attribute.CookieAttribute;
import io.undertow.attribute.DateTimeAttribute;
import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributeParser;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.attribute.LocalIPAttribute;
import io.undertow.attribute.QueryStringAttribute;
import io.undertow.attribute.QuotingExchangeAttribute;
import io.undertow.attribute.ReadOnlyAttributeException;
import io.undertow.attribute.RemoteIPAttribute;
import io.undertow.attribute.RemoteUserAttribute;
import io.undertow.attribute.RequestHeaderAttribute;
import io.undertow.attribute.RequestMethodAttribute;
import io.undertow.attribute.RequestProtocolAttribute;
import io.undertow.attribute.RequestSchemeAttribute;
import io.undertow.attribute.RequestURLAttribute;
import io.undertow.attribute.ResponseCodeAttribute;
import io.undertow.attribute.ResponseHeaderAttribute;
import io.undertow.attribute.ResponseTimeAttribute;
import io.undertow.attribute.SecureExchangeAttribute;
import io.undertow.attribute.SubstituteEmptyWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Parser that transforms an extended access log format string into a
 * Undertow access log format string.
 *
 * @author Stuart Douglas
 */
public class ExtendedAccessLogParser {

    /**
     * parser that is used to access servlet context attributes, to avoid bringing in servlet
     * context dependencies
     */
    private final ExchangeAttributeParser parser;

    public ExtendedAccessLogParser(ClassLoader classLoader) {
        this.parser = ExchangeAttributes.parser(classLoader, QuotingExchangeAttribute.WRAPPER);
    }

    private static class PatternTokenizer {
        private StringReader sr = null;
        private StringBuilder buf = new StringBuilder();
        private boolean ended = false;
        private boolean subToken;
        private boolean parameter;

        PatternTokenizer(String str) {
            sr = new StringReader(str);
        }

        public boolean hasSubToken() {
            return subToken;
        }

        public boolean hasParameter() {
            return parameter;
        }

        public String getToken() throws IOException {
            if (ended)
                return null;

            String result = null;
            subToken = false;
            parameter = false;

            int c = sr.read();
            while (c != -1) {
                switch (c) {
                    case ' ':
                        result = buf.toString();
                        buf = new StringBuilder();
                        buf.append((char) c);
                        return result;
                    case '-':
                        result = buf.toString();
                        buf = new StringBuilder();
                        subToken = true;
                        return result;
                    case '(':
                        result = buf.toString();
                        buf = new StringBuilder();
                        parameter = true;
                        return result;
                    case ')':
                        result = buf.toString();
                        buf = new StringBuilder();
                        break;
                    default:
                        buf.append((char) c);
                }
                c = sr.read();
            }
            ended = true;
            if (buf.length() != 0) {
                return buf.toString();
            } else {
                return null;
            }
        }

        public String getParameter() throws IOException {
            String result;
            if (!parameter) {
                return null;
            }
            parameter = false;
            int c = sr.read();
            while (c != -1) {
                if (c == ')') {
                    result = buf.toString();
                    buf = new StringBuilder();
                    return result;
                }
                buf.append((char) c);
                c = sr.read();
            }
            return null;
        }

        public String getWhiteSpaces() throws IOException {
            if (isEnded())
                return "";
            StringBuilder whiteSpaces = new StringBuilder();
            if (buf.length() > 0) {
                whiteSpaces.append(buf);
                buf = new StringBuilder();
            }
            int c = sr.read();
            while (Character.isWhitespace((char) c)) {
                whiteSpaces.append((char) c);
                c = sr.read();
            }
            if (c == -1) {
                ended = true;
            } else {
                buf.append((char) c);
            }
            return whiteSpaces.toString();
        }

        public boolean isEnded() {
            return ended;
        }

        public String getRemains() throws IOException {
            StringBuilder remains = new StringBuilder();
            for (int c = sr.read(); c != -1; c = sr.read()) {
                remains.append((char) c);
            }
            return remains.toString();
        }

    }

    public ExchangeAttribute parse(String pattern) {
        List<ExchangeAttribute> list = new ArrayList<ExchangeAttribute>();

        PatternTokenizer tokenizer = new PatternTokenizer(pattern);
        try {

            // Ignore leading whitespace.
            tokenizer.getWhiteSpaces();

            if (tokenizer.isEnded()) {
                UndertowLogger.ROOT_LOGGER.extendedAccessLogEmptyPattern();
                return null;
            }

            String token = tokenizer.getToken();
            while (token != null) {
                if (UndertowLogger.ROOT_LOGGER.isDebugEnabled()) {
                    UndertowLogger.ROOT_LOGGER.debug("token = " + token);
                }
                ExchangeAttribute element = getLogElement(token, tokenizer);
                if (element == null) {
                    break;
                }
                list.add(element);
                String whiteSpaces = tokenizer.getWhiteSpaces();
                if (whiteSpaces.length() > 0) {
                    list.add(new ConstantExchangeAttribute(whiteSpaces));
                }
                if (tokenizer.isEnded()) {
                    break;
                }
                token = tokenizer.getToken();
            }
            if (UndertowLogger.ROOT_LOGGER.isDebugEnabled()) {
                UndertowLogger.ROOT_LOGGER.debug("finished decoding with element size of: " + list.size());
            }
            return new CompositeExchangeAttribute(list.toArray(new ExchangeAttribute[list.size()]));
        } catch (IOException e) {
            UndertowLogger.ROOT_LOGGER.extendedAccessLogPatternParseError(e);
            return null;
        }
    }

    protected ExchangeAttribute getLogElement(String token, PatternTokenizer tokenizer) throws IOException {
        if ("date".equals(token)) {
            return new DateTimeAttribute("yyyy-MM-dd", "GMT");
        } else if ("time".equals(token)) {
            if (tokenizer.hasSubToken()) {
                String nextToken = tokenizer.getToken();
                if ("taken".equals(nextToken)) {
                    //if response timing are not enabled we just print a '-'
                    return new SubstituteEmptyWrapper.SubstituteEmptyAttribute(new ResponseTimeAttribute(TimeUnit.SECONDS), "-");
                }
            } else {
                return new DateTimeAttribute("HH:mm:ss", "GMT");
            }
        } else if ("bytes".equals(token)) {
            return new BytesSentAttribute(true);
        } else if ("cached".equals(token)) {
            /* I don't know how to evaluate this! */
            return new ConstantExchangeAttribute("-");
        } else if ("c".equals(token)) {
            String nextToken = tokenizer.getToken();
            if ("ip".equals(nextToken)) {
                return RemoteIPAttribute.INSTANCE;
            } else if ("dns".equals(nextToken)) {
                return new ExchangeAttribute() {
                    @Override
                    public String readAttribute(HttpServerExchange exchange) {
                        final InetSocketAddress peerAddress = exchange.getConnection().getPeerAddress(InetSocketAddress.class);

                        try {
                            return peerAddress.getHostName();
                        } catch (Throwable e) {
                            return peerAddress.getHostString();
                        }
                    }

                    @Override
                    public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
                        throw new ReadOnlyAttributeException();
                    }
                };
            }
        } else if ("s".equals(token)) {
            String nextToken = tokenizer.getToken();
            if ("ip".equals(nextToken)) {
                return LocalIPAttribute.INSTANCE;
            } else if ("dns".equals(nextToken)) {
                return new ExchangeAttribute() {
                    @Override
                    public String readAttribute(HttpServerExchange exchange) {
                        try {
                            return exchange.getConnection().getLocalAddress(InetSocketAddress.class).getHostName();
                        } catch (Throwable e) {
                            return "localhost";
                        }
                    }

                    @Override
                    public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
                        throw new ReadOnlyAttributeException();
                    }

                };
            }
        } else if ("cs".equals(token)) {
            return getClientToServerElement(tokenizer);
        } else if ("sc".equals(token)) {
            return getServerToClientElement(tokenizer);
        } else if ("sr".equals(token) || "rs".equals(token)) {
            return getProxyElement(tokenizer);
        } else if ("x".equals(token)) {
            return getXParameterElement(tokenizer);
        }
        UndertowLogger.ROOT_LOGGER.extendedAccessLogUnknownToken(token);
        return null;
    }

    protected ExchangeAttribute getClientToServerElement(
            PatternTokenizer tokenizer) throws IOException {
        if (tokenizer.hasSubToken()) {
            String token = tokenizer.getToken();
            if ("method".equals(token)) {
                return RequestMethodAttribute.INSTANCE;
            } else if ("uri".equals(token)) {
                if (tokenizer.hasSubToken()) {
                    token = tokenizer.getToken();
                    if ("stem".equals(token)) {
                        return RequestURLAttribute.INSTANCE;
                    } else if ("query".equals(token)) {
                        return new SubstituteEmptyWrapper.SubstituteEmptyAttribute(QueryStringAttribute.BARE_INSTANCE, "-");
                    }
                } else {
                    return new ExchangeAttribute() {
                        @Override
                        public String readAttribute(HttpServerExchange exchange) {
                            String query = exchange.getQueryString();

                            if (query.isEmpty()) {
                                return exchange.getRequestURI();
                            } else {
                                StringBuilder buf = new StringBuilder();
                                buf.append(exchange.getRequestURI());
                                buf.append('?');
                                buf.append(exchange.getQueryString());
                                return buf.toString();
                            }
                        }

                        @Override
                        public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
                            throw new ReadOnlyAttributeException();
                        }

                    };
                }
            }
        } else if (tokenizer.hasParameter()) {
            String parameter = tokenizer.getParameter();
            if (parameter == null) {
                UndertowLogger.ROOT_LOGGER.extendedAccessLogMissingClosing();
                return null;
            }
            return new QuotingExchangeAttribute(new RequestHeaderAttribute(new HttpString(parameter)));
        }
        UndertowLogger.ROOT_LOGGER.extendedAccessLogCannotDecode(tokenizer.getRemains());
        return null;
    }

    protected ExchangeAttribute getServerToClientElement(
            PatternTokenizer tokenizer) throws IOException {
        if (tokenizer.hasSubToken()) {
            String token = tokenizer.getToken();
            if ("status".equals(token)) {
                return ResponseCodeAttribute.INSTANCE;
            } else if ("comment".equals(token)) {
                return new ConstantExchangeAttribute("?");
            }
        } else if (tokenizer.hasParameter()) {
            String parameter = tokenizer.getParameter();
            if (parameter == null) {
                UndertowLogger.ROOT_LOGGER.extendedAccessLogMissingClosing();
                return null;
            }
            return new QuotingExchangeAttribute(new ResponseHeaderAttribute(new HttpString(parameter)));
        }
        UndertowLogger.ROOT_LOGGER.extendedAccessLogCannotDecode(tokenizer.getRemains());
        return null;
    }

    protected ExchangeAttribute getProxyElement(PatternTokenizer tokenizer)
            throws IOException {
        String token = null;
        if (tokenizer.hasSubToken()) {
            tokenizer.getToken();
            return new ConstantExchangeAttribute("-");
        } else if (tokenizer.hasParameter()) {
            tokenizer.getParameter();
            return new ConstantExchangeAttribute("-");
        }
        UndertowLogger.ROOT_LOGGER.extendedAccessLogCannotDecode(token);
        return null;
    }

    protected ExchangeAttribute getXParameterElement(PatternTokenizer tokenizer)
            throws IOException {
        if (!tokenizer.hasSubToken()) {
            UndertowLogger.ROOT_LOGGER.extendedAccessLogBadXParam();
            return null;
        }
        final String token = tokenizer.getToken();
        if (!tokenizer.hasParameter()) {
            UndertowLogger.ROOT_LOGGER.extendedAccessLogBadXParam();
            return null;
        }
        String parameter = tokenizer.getParameter();
        if (parameter == null) {
            UndertowLogger.ROOT_LOGGER.extendedAccessLogMissingClosing();
            return null;
        }
        if ("A".equals(token)) {
            parser.parse("%{sc," + parameter + "}");
        } else if ("C".equals(token)) {
            return new QuotingExchangeAttribute(new CookieAttribute(parameter));
        } else if ("R".equals(token)) {
            parser.parse("%{r," + parameter + "}");
        } else if ("S".equals(token)) {
            parser.parse("%{s," + parameter + "}");
        } else if ("H".equals(token)) {
            return getServletRequestElement(parameter);
        } else if ("P".equals(token)) {
            parser.parse("%{rp," + parameter + "}");
        } else if ("O".equals(token)) {
            return new QuotingExchangeAttribute(new ExchangeAttribute() {
                @Override
                public String readAttribute(HttpServerExchange exchange) {
                    HeaderValues values = exchange.getResponseHeaders().get(token);
                    if (values != null && values.size() > 0) {
                        StringBuilder buffer = new StringBuilder();
                        for (int i = 0; i < values.size(); i++) {
                            String string = values.get(i);
                            buffer.append(string);
                            if (i + 1 < values.size())
                                buffer.append(",");
                        }
                        return buffer.toString();
                    }
                    return null;
                }

                @Override
                public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
                    throw new ReadOnlyAttributeException();
                }
            });
        }
        UndertowLogger.ROOT_LOGGER.extendedAccessLogCannotDecodeXParamValue(token);
        return null;
    }

    protected ExchangeAttribute getServletRequestElement(String parameter) {
        if ("authType".equals(parameter)) {
            return AuthenticationTypeExchangeAttribute.INSTANCE;
        } else if ("remoteUser".equals(parameter)) {
            return RemoteUserAttribute.INSTANCE;
        } else if ("requestedSessionId".equals(parameter)) {
            return parser.parse("%{REQUESTED_SESSION_ID}");
        } else if ("requestedSessionIdFromCookie".equals(parameter)) {
            return parser.parse("%{REQUESTED_SESSION_ID_FROM_COOKIE}");
        } else if ("requestedSessionIdValid".equals(parameter)) {
            return parser.parse("%{REQUESTED_SESSION_ID_VALID}");
        } else if ("contentLength".equals(parameter)) {
            return new QuotingExchangeAttribute(new RequestHeaderAttribute(Headers.CONTENT_LENGTH));
        } else if ("characterEncoding".equals(parameter)) {
            return parser.parse("%{REQUEST_CHARACTER_ENCODING}");
        } else if ("locale".equals(parameter)) {
            return parser.parse("%{REQUEST_LOCALE}");
        } else if ("protocol".equals(parameter)) {
            return RequestProtocolAttribute.INSTANCE;
        } else if ("scheme".equals(parameter)) {
            return RequestSchemeAttribute.INSTANCE;
        } else if ("secure".equals(parameter)) {
            return SecureExchangeAttribute.INSTANCE;
        }
        UndertowLogger.ROOT_LOGGER.extendedAccessLogCannotDecodeXParamValue(parameter);
        return null;
    }

    public static class ExtendedAccessLogHeaderGenerator implements LogFileHeaderGenerator {

        private final String pattern;

        public ExtendedAccessLogHeaderGenerator(String pattern) {
            this.pattern = pattern;
        }

        @Override
        public String generateHeader() {
            StringBuilder sb = new StringBuilder();
            sb.append("#Fields: ");
            sb.append(pattern);
            sb.append("\n#Version: 2.0\n");
            sb.append("#Software: ");
            sb.append(Version.getFullVersionString());
            sb.append("\n");
            return sb.toString();
        }
    }

}
