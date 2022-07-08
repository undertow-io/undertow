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

package io.undertow.server.protocol.ajp;

import static io.undertow.util.Methods.ACL;
import static io.undertow.util.Methods.BASELINE_CONTROL;
import static io.undertow.util.Methods.CHECKIN;
import static io.undertow.util.Methods.CHECKOUT;
import static io.undertow.util.Methods.COPY;
import static io.undertow.util.Methods.DELETE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.Methods.HEAD;
import static io.undertow.util.Methods.LABEL;
import static io.undertow.util.Methods.LOCK;
import static io.undertow.util.Methods.MERGE;
import static io.undertow.util.Methods.MKACTIVITY;
import static io.undertow.util.Methods.MKCOL;
import static io.undertow.util.Methods.MKWORKSPACE;
import static io.undertow.util.Methods.MOVE;
import static io.undertow.util.Methods.OPTIONS;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.Methods.PROPFIND;
import static io.undertow.util.Methods.PROPPATCH;
import static io.undertow.util.Methods.PUT;
import static io.undertow.util.Methods.REPORT;
import static io.undertow.util.Methods.SEARCH;
import static io.undertow.util.Methods.TRACE;
import static io.undertow.util.Methods.UNCHECKOUT;
import static io.undertow.util.Methods.UNLOCK;
import static io.undertow.util.Methods.UPDATE;
import static io.undertow.util.Methods.VERSION_CONTROL;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.security.impl.ExternalAuthenticationMechanism;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.BadRequestException;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.ParameterLimitException;
import io.undertow.util.URLUtils;

/**
 * @author Stuart Douglas
 */
public class AjpRequestParser {

    private final String encoding;
    private final boolean doDecode;
    private final boolean allowEncodedSlash;
    private final int maxParameters;
    private final int maxHeaders;
    private StringBuilder decodeBuffer;
    private final boolean allowUnescapedCharactersInUrl;
    private final Pattern allowedRequestAttributesPattern;

    private static final HttpString[] HTTP_HEADERS;

    public static final int FORWARD_REQUEST = 2;
    public static final int CPONG = 9;
    public static final int CPING = 10;
    public static final int SHUTDOWN = 7;


    private static final HttpString[] HTTP_METHODS;
    private static final String[] ATTRIBUTES;
    private static final Set<String> ATTR_SET;

    public static final String QUERY_STRING = "query_string";

    public static final String SSL_CERT = "ssl_cert";

    public static final String CONTEXT = "context";

    public static final String SERVLET_PATH = "servlet_path";

    public static final String REMOTE_USER = "remote_user";

    public static final String AUTH_TYPE = "auth_type";

    public static final String ROUTE = "route";

    public static final String SSL_CIPHER = "ssl_cipher";

    public static final String SSL_SESSION = "ssl_session";

    public static final String REQ_ATTRIBUTE = "req_attribute";

    public static final String SSL_KEY_SIZE = "ssl_key_size";

    public static final String SECRET = "secret";

    public static final String STORED_METHOD = "stored_method";

    public static final String AJP_REMOTE_PORT = "AJP_REMOTE_PORT";

    static {
        HTTP_METHODS = new HttpString[28];
        HTTP_METHODS[1] = OPTIONS;
        HTTP_METHODS[2] = GET;
        HTTP_METHODS[3] = HEAD;
        HTTP_METHODS[4] = POST;
        HTTP_METHODS[5] = PUT;
        HTTP_METHODS[6] = DELETE;
        HTTP_METHODS[7] = TRACE;
        HTTP_METHODS[8] = PROPFIND;
        HTTP_METHODS[9] = PROPPATCH;
        HTTP_METHODS[10] = MKCOL;
        HTTP_METHODS[11] = COPY;
        HTTP_METHODS[12] = MOVE;
        HTTP_METHODS[13] = LOCK;
        HTTP_METHODS[14] = UNLOCK;
        HTTP_METHODS[15] = ACL;
        HTTP_METHODS[16] = REPORT;
        HTTP_METHODS[17] = VERSION_CONTROL;
        HTTP_METHODS[18] = CHECKIN;
        HTTP_METHODS[19] = CHECKOUT;
        HTTP_METHODS[20] = UNCHECKOUT;
        HTTP_METHODS[21] = SEARCH;
        HTTP_METHODS[22] = MKWORKSPACE;
        HTTP_METHODS[23] = UPDATE;
        HTTP_METHODS[24] = LABEL;
        HTTP_METHODS[25] = MERGE;
        HTTP_METHODS[26] = BASELINE_CONTROL;
        HTTP_METHODS[27] = MKACTIVITY;

        HTTP_HEADERS = new HttpString[0xF];
        HTTP_HEADERS[1] = Headers.ACCEPT;
        HTTP_HEADERS[2] = Headers.ACCEPT_CHARSET;
        HTTP_HEADERS[3] = Headers.ACCEPT_ENCODING;
        HTTP_HEADERS[4] = Headers.ACCEPT_LANGUAGE;
        HTTP_HEADERS[5] = Headers.AUTHORIZATION;
        HTTP_HEADERS[6] = Headers.CONNECTION;
        HTTP_HEADERS[7] = Headers.CONTENT_TYPE;
        HTTP_HEADERS[8] = Headers.CONTENT_LENGTH;
        HTTP_HEADERS[9] = Headers.COOKIE;
        HTTP_HEADERS[0xA] = Headers.COOKIE2;
        HTTP_HEADERS[0xB] = Headers.HOST;
        HTTP_HEADERS[0xC] = Headers.PRAGMA;
        HTTP_HEADERS[0xD] = Headers.REFERER;
        HTTP_HEADERS[0xE] = Headers.USER_AGENT;

        ATTRIBUTES = new String[0xE];
        ATTRIBUTES[1] = CONTEXT;
        ATTRIBUTES[2] = SERVLET_PATH;
        ATTRIBUTES[3] = REMOTE_USER;
        ATTRIBUTES[4] = AUTH_TYPE;
        ATTRIBUTES[5] = QUERY_STRING;
        ATTRIBUTES[6] = ROUTE;
        ATTRIBUTES[7] = SSL_CERT;
        ATTRIBUTES[8] = SSL_CIPHER;
        ATTRIBUTES[9] = SSL_SESSION;
        ATTRIBUTES[10] = REQ_ATTRIBUTE;
        ATTRIBUTES[11] = SSL_KEY_SIZE;
        ATTRIBUTES[12] = SECRET;
        ATTRIBUTES[13] = STORED_METHOD;
        ATTR_SET = new HashSet<>(Arrays.asList(ATTRIBUTES));
    }

    public AjpRequestParser(String encoding, boolean doDecode, int maxParameters, int maxHeaders, boolean allowEncodedSlash, boolean allowUnescapedCharactersInUrl) {
        this(encoding, doDecode, maxParameters, maxHeaders, allowEncodedSlash, allowUnescapedCharactersInUrl, null);
    }

    public AjpRequestParser(String encoding, boolean doDecode, int maxParameters, int maxHeaders, boolean allowEncodedSlash, boolean allowUnescapedCharactersInUrl, String allowedRequestAttributesPattern) {
        this.encoding = encoding;
        this.doDecode = doDecode;
        this.maxParameters = maxParameters;
        this.maxHeaders = maxHeaders;
        this.allowEncodedSlash = allowEncodedSlash;
        this.allowUnescapedCharactersInUrl = allowUnescapedCharactersInUrl;
        if (allowedRequestAttributesPattern != null && !allowedRequestAttributesPattern.isEmpty()) {
            this.allowedRequestAttributesPattern = Pattern.compile(allowedRequestAttributesPattern);
        } else {
            this.allowedRequestAttributesPattern = null;
        }
    }


    public void parse(final ByteBuffer buf, final AjpRequestParseState state, final HttpServerExchange exchange) throws IOException, BadRequestException {
        if (!buf.hasRemaining()) {
            return;
        }
        switch (state.state) {
            case AjpRequestParseState.BEGIN: {
                IntegerHolder result = parse16BitInteger(buf, state);
                if (!result.readComplete) {
                    return;
                } else {
                    if (result.value != 0x1234) {
                        throw new BadRequestException(UndertowMessages.MESSAGES.wrongMagicNumber(result.value));
                    }
                }
            }
            case AjpRequestParseState.READING_DATA_SIZE: {
                IntegerHolder result = parse16BitInteger(buf, state);
                if (!result.readComplete) {
                    state.state = AjpRequestParseState.READING_DATA_SIZE;
                    return;
                } else {
                    state.dataSize = result.value;
                }
            }
            case AjpRequestParseState.READING_PREFIX_CODE: {
                if (!buf.hasRemaining()) {
                    state.state = AjpRequestParseState.READING_PREFIX_CODE;
                    return;
                } else {
                    final byte prefix = buf.get();
                    state.prefix = prefix;
                    if (prefix != 2) {
                        state.state = AjpRequestParseState.DONE;
                        return;
                    }
                }
            }
            case AjpRequestParseState.READING_METHOD: {
                if (!buf.hasRemaining()) {
                    state.state = AjpRequestParseState.READING_METHOD;
                    return;
                } else {
                    int method = buf.get();
                    if (method > 0 && method < 28) {
                        exchange.setRequestMethod(HTTP_METHODS[method]);
                    } else if((method & 0xFF) != 0xFF) {
                        throw new BadRequestException("Unknown method type " + method);
                    }
                }
            }
            case AjpRequestParseState.READING_PROTOCOL: {
                StringHolder result = parseString(buf, state, StringType.OTHER);
                if (result.readComplete) {
                    //TODO: more efficient way of doing this
                    exchange.setProtocol(HttpString.tryFromString(result.value));
                } else {
                    state.state = AjpRequestParseState.READING_PROTOCOL;
                    return;
                }
            }
            case AjpRequestParseState.READING_REQUEST_URI: {
                StringHolder result = parseString(buf, state, StringType.URL);
                if (result.readComplete) {
                    int colon = result.value.indexOf(';');
                    if (colon == -1) {
                        String res = decode(result.value, result.containsUrlCharacters);
                        if(result.containsUnencodedCharacters) {
                            //we decode if the URL was non-compliant, and contained incorrectly encoded characters
                            //there is not really a 'correct' thing to do in this situation, but this seems the least incorrect
                            exchange.setRequestURI(res);
                        } else {
                            exchange.setRequestURI(result.value);
                        }
                        exchange.setRequestPath(res);
                        exchange.setRelativePath(res);
                    } else {
                        final StringBuilder resBuffer = new StringBuilder();
                        int pathParamParsingIndex = 0;
                        try {
                            do {
                                final String url = result.value.substring(pathParamParsingIndex, colon);
                                resBuffer.append(decode(url, result.containsUrlCharacters));
                                pathParamParsingIndex = colon + 1 + URLUtils.parsePathParams(result.value.substring(colon + 1), exchange, encoding, doDecode && result.containsUrlCharacters, maxParameters);
                                colon = result.value.indexOf(';', pathParamParsingIndex + 1);
                            } while (pathParamParsingIndex < result.value.length() && colon != -1);
                        } catch (ParameterLimitException e) {
                            UndertowLogger.REQUEST_IO_LOGGER.failedToParseRequest(e);
                            state.badRequest = true;
                        }
                        if (pathParamParsingIndex < result.value.length()) {
                            final String url = result.value.substring(pathParamParsingIndex);
                            resBuffer.append(decode(url, result.containsUrlCharacters));
                        }
                        final String res = resBuffer.toString();
                        if(result.containsUnencodedCharacters) {
                            exchange.setRequestURI(res);
                        } else {
                            exchange.setRequestURI(result.value);
                        }
                        exchange.setRequestPath(res);
                        exchange.setRelativePath(res);
                    }
                } else {
                    state.state = AjpRequestParseState.READING_REQUEST_URI;
                    return;
                }
            }
            case AjpRequestParseState.READING_REMOTE_ADDR: {
                StringHolder result = parseString(buf, state, StringType.OTHER);
                if (result.readComplete) {
                    state.remoteAddress = result.value;
                } else {
                    state.state = AjpRequestParseState.READING_REMOTE_ADDR;
                    return;
                }
            }
            case AjpRequestParseState.READING_REMOTE_HOST: {
                StringHolder result = parseString(buf, state, StringType.OTHER);
                if (!result.readComplete) {
                    state.state = AjpRequestParseState.READING_REMOTE_HOST;
                    return;
                }
            }
            case AjpRequestParseState.READING_SERVER_NAME: {
                StringHolder result = parseString(buf, state, StringType.OTHER);
                if (result.readComplete) {
                    state.serverAddress = result.value;
                } else {
                    state.state = AjpRequestParseState.READING_SERVER_NAME;
                    return;
                }
            }
            case AjpRequestParseState.READING_SERVER_PORT: {
                IntegerHolder result = parse16BitInteger(buf, state);
                if (result.readComplete) {
                    state.serverPort = result.value;
                } else {
                    state.state = AjpRequestParseState.READING_SERVER_PORT;
                    return;
                }
            }
            case AjpRequestParseState.READING_IS_SSL: {
                if (!buf.hasRemaining()) {
                    state.state = AjpRequestParseState.READING_IS_SSL;
                    return;
                } else {
                    final byte isSsl = buf.get();
                    if (isSsl != 0) {
                        exchange.setRequestScheme("https");
                    } else {
                        exchange.setRequestScheme("http");
                    }
                }
            }
            case AjpRequestParseState.READING_NUM_HEADERS: {
                IntegerHolder result = parse16BitInteger(buf, state);
                if (!result.readComplete) {
                    state.state = AjpRequestParseState.READING_NUM_HEADERS;
                    return;
                } else {
                    state.numHeaders = result.value;
                    if(state.numHeaders > maxHeaders) {
                        UndertowLogger.REQUEST_IO_LOGGER.failedToParseRequest(new BadRequestException(UndertowMessages.MESSAGES.tooManyHeaders(maxHeaders)));
                        state.badRequest = true;
                    }
                }
            }
            case AjpRequestParseState.READING_HEADERS: {
                int readHeaders = state.readHeaders;
                while (readHeaders < state.numHeaders) {
                    if (state.currentHeader == null) {
                        StringHolder result = parseString(buf, state, StringType.HEADER);
                        if (!result.readComplete) {
                            state.state = AjpRequestParseState.READING_HEADERS;
                            state.readHeaders = readHeaders;
                            return;
                        }
                        if (result.header != null) {
                            state.currentHeader = result.header;
                        } else {
                            state.currentHeader = HttpString.tryFromString(result.value);
                            Connectors.verifyToken(state.currentHeader);
                        }
                    }
                    StringHolder result = parseString(buf, state, StringType.OTHER);
                    if (!result.readComplete) {
                        state.state = AjpRequestParseState.READING_HEADERS;
                        state.readHeaders = readHeaders;
                        return;
                    }
                    if(!state.badRequest) {
                        exchange.getRequestHeaders().add(state.currentHeader, result.value);
                    }
                    state.currentHeader = null;
                    ++readHeaders;
                }
            }
            case AjpRequestParseState.READING_ATTRIBUTES: {
                for (; ; ) {
                    if (state.currentAttribute == null && state.currentIntegerPart == -1) {
                        if (!buf.hasRemaining()) {
                            state.state = AjpRequestParseState.READING_ATTRIBUTES;
                            return;
                        }
                        int val = (0xFF & buf.get());
                        if (val == 0xFF) {
                            state.state = AjpRequestParseState.DONE;
                            return;
                        } else if (val == 0x0A) {
                            //we need to read the name. We overload currentIntegerPart to avoid adding another state field
                            state.currentIntegerPart = 1;
                        } else {
                            if(val == 0 || val >= ATTRIBUTES.length) {
                                //ignore unknown codes for compatibility
                                continue;
                            }
                            state.currentAttribute = ATTRIBUTES[val];
                        }

                    }
                    if (state.currentIntegerPart == 1) {
                        StringHolder result = parseString(buf, state, StringType.OTHER);
                        if (!result.readComplete) {
                            state.state = AjpRequestParseState.READING_ATTRIBUTES;
                            return;
                        }
                        state.currentAttribute = result.value;
                        state.currentIntegerPart = -1;
                    }
                    String result;
                    boolean decodingAlreadyDone = false;
                    if (state.currentAttribute.equals(SSL_KEY_SIZE)) {
                        IntegerHolder resultHolder = parse16BitInteger(buf, state);
                        if (!resultHolder.readComplete) {
                            state.state = AjpRequestParseState.READING_ATTRIBUTES;
                            return;
                        }
                        result = Integer.toString(resultHolder.value);
                    } else {
                        StringHolder resultHolder = parseString(buf, state, state.currentAttribute.equals(QUERY_STRING) ? StringType.QUERY_STRING : StringType.OTHER);
                        if (!resultHolder.readComplete) {
                            state.state = AjpRequestParseState.READING_ATTRIBUTES;
                            return;
                        }
                        if(resultHolder.containsUnencodedCharacters) {
                            result = decode(resultHolder.value, true);
                            decodingAlreadyDone = true;
                        } else {
                            result = resultHolder.value;
                        }
                    }
                    //query string.
                    if (state.currentAttribute.equals(QUERY_STRING)) {
                        String resultAsQueryString = result == null ? "" : result;
                        exchange.setQueryString(resultAsQueryString);
                        try {
                            URLUtils.parseQueryString(resultAsQueryString, exchange, encoding, doDecode && !decodingAlreadyDone, maxParameters);
                        } catch (ParameterLimitException | IllegalArgumentException e) {
                            UndertowLogger.REQUEST_IO_LOGGER.failedToParseRequest(e);
                            state.badRequest = true;
                        }
                    } else if (state.currentAttribute.equals(REMOTE_USER)) {
                        exchange.putAttachment(ExternalAuthenticationMechanism.EXTERNAL_PRINCIPAL, result);
                        exchange.putAttachment(HttpServerExchange.REMOTE_USER, result);
                    } else if (state.currentAttribute.equals(AUTH_TYPE)) {
                        exchange.putAttachment(ExternalAuthenticationMechanism.EXTERNAL_AUTHENTICATION_TYPE, result);
                    } else if (state.currentAttribute.equals(STORED_METHOD)) {
                        HttpString requestMethod = new HttpString(result);
                        Connectors.verifyToken(requestMethod);
                        exchange.setRequestMethod(requestMethod);
                    } else if (state.currentAttribute.equals(AJP_REMOTE_PORT)) {
                        state.remotePort = Integer.parseInt(result);
                    } else if (state.currentAttribute.equals(SSL_SESSION)) {
                        state.sslSessionId = result;
                    } else if (state.currentAttribute.equals(SSL_CIPHER)) {
                        state.sslCipher = result;
                    } else if (state.currentAttribute.equals(SSL_CERT)) {
                        state.sslCert = result;
                    } else if (state.currentAttribute.equals(SSL_KEY_SIZE)) {
                        state.sslKeySize = result;
                    } else {
                        // other attributes
                        if (state.attributes == null) {
                            state.attributes = new TreeMap<>();
                        }
                        if (ATTR_SET.contains(state.currentAttribute)) {
                            // known attirubtes
                            state.attributes.put(state.currentAttribute, result);
                        } else if (allowedRequestAttributesPattern != null) {
                            // custom allowed attributes
                            Matcher m = allowedRequestAttributesPattern.matcher(state.currentAttribute);
                            if (m.matches()) {
                                state.attributes.put(state.currentAttribute, result);
                            }
                        }
                    }
                    state.currentAttribute = null;
                }
            }
        }
        state.state = AjpRequestParseState.DONE;
    }

    private String decode(String url, final boolean containsUrlCharacters) throws UnsupportedEncodingException {
        if (doDecode && containsUrlCharacters) {
            try {
                if(decodeBuffer == null) {
                    decodeBuffer = new StringBuilder();
                }
                return URLUtils.decode(url, this.encoding, allowEncodedSlash, false, decodeBuffer);
            } catch (Exception e) {
                throw UndertowMessages.MESSAGES.failedToDecodeURL(url, encoding, e);
            }
        }
        return url;
    }

    protected HttpString headers(int offset) {
        return HTTP_HEADERS[offset];
    }

    public static final int STRING_LENGTH_MASK = 1 << 31;

    protected IntegerHolder parse16BitInteger(ByteBuffer buf, AjpRequestParseState state) {
        if (!buf.hasRemaining()) {
            return new IntegerHolder(-1, false);
        }
        int number = state.currentIntegerPart;
        if (number == -1) {
            number = (buf.get() & 0xFF);
        }
        if (buf.hasRemaining()) {
            final byte b = buf.get();
            int result = ((0xFF & number) << 8) + (b & 0xFF);
            state.currentIntegerPart = -1;
            return new IntegerHolder(result, true);
        } else {
            state.currentIntegerPart = number;
            return new IntegerHolder(-1, false);
        }
    }

    protected StringHolder parseString(ByteBuffer buf, AjpRequestParseState state, StringType type) throws UnsupportedEncodingException, BadRequestException {
        boolean containsUrlCharacters = state.containsUrlCharacters;
        boolean containsUnencodedUrlCharacters = state.containsUnencodedUrlCharacters;
        if (!buf.hasRemaining()) {
            return new StringHolder(null, false, false, false);
        }
        int stringLength = state.stringLength;
        if (stringLength == -1) {
            int number = buf.get() & 0xFF;
            if (buf.hasRemaining()) {
                final byte b = buf.get();
                stringLength = ((0xFF & number) << 8) + (b & 0xFF);
            } else {
                state.stringLength = number | STRING_LENGTH_MASK;
                return new StringHolder(null, false, false, false);
            }
        } else if ((stringLength & STRING_LENGTH_MASK) != 0) {
            int number = stringLength & ~STRING_LENGTH_MASK;
            stringLength = ((0xFF & number) << 8) + (buf.get() & 0xFF);
        }
        if (type == StringType.HEADER && (stringLength & 0xFF00) != 0) {
            state.stringLength = -1;
            return new StringHolder(headers(stringLength & 0xFF));
        }
        if (stringLength == 0xFFFF) {
            //OxFFFF means null
            state.stringLength = -1;
            return new StringHolder(null, true, false, false);
        }
        int length = state.getCurrentStringLength();
        while (length < stringLength) {
            if (!buf.hasRemaining()) {
                state.stringLength = stringLength;
                state.containsUrlCharacters = containsUrlCharacters;
                state.containsUnencodedUrlCharacters = containsUnencodedUrlCharacters;
                return new StringHolder(null, false, false, false);
            }
            byte c = buf.get();
            if(type == StringType.QUERY_STRING && (c == '+' || c == '%' || c < 0 )) {
                if (c < 0) {
                    if (!allowUnescapedCharactersInUrl) {
                        throw new BadRequestException();
                    } else {
                        containsUnencodedUrlCharacters = true;
                    }
                }
                containsUrlCharacters = true;
            } else if(type == StringType.URL && (c == '%' || c < 0 )) {
                if(c < 0 ) {
                    if(!allowUnescapedCharactersInUrl) {
                        throw new BadRequestException();
                    } else {
                        containsUnencodedUrlCharacters = true;
                    }
                }
                containsUrlCharacters = true;
            }
            state.addStringByte(c);
            ++length;
        }

        if (buf.hasRemaining()) {
            buf.get(); //null terminator
            String value = state.getStringAndClear();
            state.stringLength = -1;
            state.containsUrlCharacters = false;
            state.containsUnencodedUrlCharacters = containsUnencodedUrlCharacters;
            return new StringHolder(value, true, containsUrlCharacters, containsUnencodedUrlCharacters);
        } else {
            state.stringLength = stringLength;
            state.containsUrlCharacters = containsUrlCharacters;
            state.containsUnencodedUrlCharacters = containsUnencodedUrlCharacters;
            return new StringHolder(null, false, false, false);
        }
    }

    protected static class IntegerHolder {
        public final int value;
        public final boolean readComplete;

        private IntegerHolder(int value, boolean readComplete) {
            this.value = value;
            this.readComplete = readComplete;
        }
    }

    protected static class StringHolder {
        public final String value;
        public final HttpString header;
        final boolean readComplete;
        final boolean containsUrlCharacters;
        final boolean containsUnencodedCharacters;

        private StringHolder(String value, boolean readComplete, boolean containsUrlCharacters, boolean containsUnencodedCharacters) {
            this.value = value;
            this.readComplete = readComplete;
            this.containsUrlCharacters = containsUrlCharacters;
            this.containsUnencodedCharacters = containsUnencodedCharacters;
            this.header = null;
        }

        private StringHolder(HttpString value) {
            this.value = null;
            this.readComplete = true;
            this.header = value;
            this.containsUrlCharacters = false;
            this.containsUnencodedCharacters = false;
        }
    }

    enum StringType {
        HEADER,
        URL,
        QUERY_STRING,
        OTHER
    }

}
