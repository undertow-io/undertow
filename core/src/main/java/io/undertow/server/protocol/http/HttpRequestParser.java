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

package io.undertow.server.protocol.http;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.annotationprocessor.HttpParserConfig;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import io.undertow.util.URLUtils;
import io.undertow.util.BadRequestException;
import io.undertow.util.Cookies;

import org.xnio.OptionMap;

import static io.undertow.util.Headers.ACCEPT_CHARSET_STRING;
import static io.undertow.util.Headers.ACCEPT_ENCODING_STRING;
import static io.undertow.util.Headers.ACCEPT_LANGUAGE_STRING;
import static io.undertow.util.Headers.ACCEPT_RANGES_STRING;
import static io.undertow.util.Headers.ACCEPT_STRING;
import static io.undertow.util.Headers.AUTHORIZATION_STRING;
import static io.undertow.util.Headers.CACHE_CONTROL_STRING;
import static io.undertow.util.Headers.CONNECTION_STRING;
import static io.undertow.util.Headers.CONTENT_LENGTH_STRING;
import static io.undertow.util.Headers.CONTENT_TYPE_STRING;
import static io.undertow.util.Headers.COOKIE_STRING;
import static io.undertow.util.Headers.EXPECT_STRING;
import static io.undertow.util.Headers.FROM_STRING;
import static io.undertow.util.Headers.HOST_STRING;
import static io.undertow.util.Headers.IF_MATCH_STRING;
import static io.undertow.util.Headers.IF_MODIFIED_SINCE_STRING;
import static io.undertow.util.Headers.IF_NONE_MATCH_STRING;
import static io.undertow.util.Headers.IF_RANGE_STRING;
import static io.undertow.util.Headers.IF_UNMODIFIED_SINCE_STRING;
import static io.undertow.util.Headers.MAX_FORWARDS_STRING;
import static io.undertow.util.Headers.ORIGIN_STRING;
import static io.undertow.util.Headers.PRAGMA_STRING;
import static io.undertow.util.Headers.PROXY_AUTHORIZATION_STRING;
import static io.undertow.util.Headers.RANGE_STRING;
import static io.undertow.util.Headers.REFERER_STRING;
import static io.undertow.util.Headers.REFRESH_STRING;
import static io.undertow.util.Headers.SEC_WEB_SOCKET_KEY_STRING;
import static io.undertow.util.Headers.SEC_WEB_SOCKET_VERSION_STRING;
import static io.undertow.util.Headers.SERVER_STRING;
import static io.undertow.util.Headers.SSL_CIPHER_STRING;
import static io.undertow.util.Headers.SSL_CIPHER_USEKEYSIZE_STRING;
import static io.undertow.util.Headers.SSL_CLIENT_CERT_STRING;
import static io.undertow.util.Headers.SSL_SESSION_ID_STRING;
import static io.undertow.util.Headers.STRICT_TRANSPORT_SECURITY_STRING;
import static io.undertow.util.Headers.TRAILER_STRING;
import static io.undertow.util.Headers.TRANSFER_ENCODING_STRING;
import static io.undertow.util.Headers.UPGRADE_STRING;
import static io.undertow.util.Headers.USER_AGENT_STRING;
import static io.undertow.util.Headers.VIA_STRING;
import static io.undertow.util.Headers.WARNING_STRING;
import static io.undertow.util.Methods.CONNECT_STRING;
import static io.undertow.util.Methods.DELETE_STRING;
import static io.undertow.util.Methods.GET_STRING;
import static io.undertow.util.Methods.HEAD_STRING;
import static io.undertow.util.Methods.OPTIONS_STRING;
import static io.undertow.util.Methods.POST_STRING;
import static io.undertow.util.Methods.PUT_STRING;
import static io.undertow.util.Methods.TRACE_STRING;
import static io.undertow.util.Protocols.HTTP_0_9_STRING;
import static io.undertow.util.Protocols.HTTP_1_0_STRING;
import static io.undertow.util.Protocols.HTTP_1_1_STRING;
import static io.undertow.util.Protocols.HTTP_2_0_STRING;

/**
 * The basic HTTP parser. The actual parser is a sub class of this class that is generated as part of
 * the build process by the {@link io.undertow.annotationprocessor.AbstractParserGenerator} annotation processor.
 * <p>
 * The actual processor is a state machine, that means that for common header, method, protocol values
 * it will return an interned string, rather than creating a new string for each one.
 * <p>
 *
 * @author Stuart Douglas
 */
@HttpParserConfig(methods = {
        OPTIONS_STRING,
        GET_STRING,
        HEAD_STRING,
        POST_STRING,
        PUT_STRING,
        DELETE_STRING,
        TRACE_STRING,
        CONNECT_STRING},
        protocols = {
                HTTP_0_9_STRING, HTTP_1_0_STRING, HTTP_1_1_STRING, HTTP_2_0_STRING
        },
        headers = {
                ACCEPT_STRING,
                ACCEPT_CHARSET_STRING,
                ACCEPT_ENCODING_STRING,
                ACCEPT_LANGUAGE_STRING,
                ACCEPT_RANGES_STRING,
                AUTHORIZATION_STRING,
                CACHE_CONTROL_STRING,
                COOKIE_STRING,
                CONNECTION_STRING,
                CONTENT_LENGTH_STRING,
                CONTENT_TYPE_STRING,
                EXPECT_STRING,
                FROM_STRING,
                HOST_STRING,
                IF_MATCH_STRING,
                IF_MODIFIED_SINCE_STRING,
                IF_NONE_MATCH_STRING,
                IF_RANGE_STRING,
                IF_UNMODIFIED_SINCE_STRING,
                MAX_FORWARDS_STRING,
                ORIGIN_STRING,
                PRAGMA_STRING,
                PROXY_AUTHORIZATION_STRING,
                RANGE_STRING,
                REFERER_STRING,
                REFRESH_STRING,
                SEC_WEB_SOCKET_KEY_STRING,
                SEC_WEB_SOCKET_VERSION_STRING,
                SERVER_STRING,
                SSL_CLIENT_CERT_STRING,
                SSL_CIPHER_STRING,
                SSL_SESSION_ID_STRING,
                SSL_CIPHER_USEKEYSIZE_STRING,
                STRICT_TRANSPORT_SECURITY_STRING,
                TRAILER_STRING,
                TRANSFER_ENCODING_STRING,
                UPGRADE_STRING,
                USER_AGENT_STRING,
                VIA_STRING,
                WARNING_STRING
        })
public abstract class HttpRequestParser {

    private static final byte[] HTTP;
    public static final int HTTP_LENGTH;

    private final int maxParameters;
    private final int maxHeaders;
    private final boolean slashDecodingFlag;
    private final boolean decode;
    private final String charset;
    private final int maxCachedHeaderSize;
    private final boolean allowUnescapedCharactersInUrl;
    private final boolean allowIDLessMatrixParams;

    private static final boolean[] ALLOWED_TARGET_CHARACTER = new boolean[256];

    private static final String ID_LESS_MATRIX_PARAMS_PROPERTY = "io.undertow.server.protocol.http.Parser.ID_LESS_MATRIX_PARAMS_PROPERTY";
    static {
        try {
            HTTP = "HTTP/1.".getBytes("ASCII");
            HTTP_LENGTH = HTTP.length;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        for(int i = 0; i < 256; ++i) {
            if(i < 32 || i > 126) {
                ALLOWED_TARGET_CHARACTER[i] = false;
            } else {
                switch ((char)i) {
                    case '\"':
                    case '#':
                    case '<':
                    case '>':
                    case '\\':
                    case '^':
                    case '`':
                    case '{':
                    case '|':
                    case '}':
                        ALLOWED_TARGET_CHARACTER[i] = false;
                        break;
                    default:
                        ALLOWED_TARGET_CHARACTER[i] = true;
                }
            }
        }
    }

    public static boolean isTargetCharacterAllowed(char c) {
        return ALLOWED_TARGET_CHARACTER[c];
    }

    public HttpRequestParser(OptionMap options) {
        maxParameters = options.get(UndertowOptions.MAX_PARAMETERS, UndertowOptions.DEFAULT_MAX_PARAMETERS);
        maxHeaders = options.get(UndertowOptions.MAX_HEADERS, UndertowOptions.DEFAULT_MAX_HEADERS);
        slashDecodingFlag = URLUtils.getSlashDecodingFlag(options);
        decode = options.get(UndertowOptions.DECODE_URL, true);
        charset = options.get(UndertowOptions.URL_CHARSET, StandardCharsets.UTF_8.name());
        maxCachedHeaderSize = options.get(UndertowOptions.MAX_CACHED_HEADER_SIZE, UndertowOptions.DEFAULT_MAX_CACHED_HEADER_SIZE);
        this.allowUnescapedCharactersInUrl = options.get(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL, false);
        this.allowIDLessMatrixParams = Boolean.parseBoolean(System.getProperty(ID_LESS_MATRIX_PARAMS_PROPERTY));
    }

    public static final HttpRequestParser instance(final OptionMap options) {
        try {
            final Class<?> cls = Class.forName(HttpRequestParser.class.getName() + "$$generated", false, HttpRequestParser.class.getClassLoader());

            Constructor<?> ctor = cls.getConstructor(OptionMap.class);
            return (HttpRequestParser) ctor.newInstance(options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public void handle(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder) throws BadRequestException {
        if (currentState.state == ParseState.VERB) {
            //fast path, we assume that it will parse fully so we avoid all the if statements

            //fast path HTTP GET requests, basically just assume all requests are get
            //and fall out to the state machine if it is not
            final int position = buffer.position();
            if (buffer.remaining() > 3
                    && buffer.get(position) == 'G'
                    && buffer.get(position + 1) == 'E'
                    && buffer.get(position + 2) == 'T'
                    && buffer.get(position + 3) == ' ') {
                buffer.position(position + 4);
                builder.setRequestMethod(Methods.GET);
                currentState.state = ParseState.PATH;
            } else {
                try {
                    handleHttpVerb(buffer, currentState, builder);
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException(e);
                }
            }
            handlePath(buffer, currentState, builder);
            boolean failed = false;
            if (buffer.remaining() > HTTP_LENGTH + 3) {
                int pos = buffer.position();
                for (int i = 0; i < HTTP_LENGTH; ++i) {
                    if (HTTP[i] != buffer.get(pos + i)) {
                        failed = true;
                        break;
                    }
                }
                if (!failed) {
                    final byte b = buffer.get(pos + HTTP_LENGTH);
                    final byte b2 = buffer.get(pos + HTTP_LENGTH + 1);
                    final byte b3 = buffer.get(pos + HTTP_LENGTH + 2);
                    if (b2 == '\r' && b3 == '\n') {
                        if (b == '1') {
                            builder.setProtocol(Protocols.HTTP_1_1);
                            buffer.position(pos + HTTP_LENGTH + 3);
                            currentState.state = ParseState.HEADER;
                        } else if (b == '0') {
                            builder.setProtocol(Protocols.HTTP_1_0);
                            buffer.position(pos + HTTP_LENGTH + 3);
                            currentState.state = ParseState.HEADER;
                        } else {
                            failed = true;
                        }
                    } else {
                        failed = true;
                    }
                }
            } else {
                failed = true;
            }
            if (failed) {
                handleHttpVersion(buffer, currentState, builder);
                handleAfterVersion(buffer, currentState);
            }

            while (currentState.state != ParseState.PARSE_COMPLETE && buffer.hasRemaining()) {
                handleHeader(buffer, currentState, builder);
                if (currentState.state == ParseState.HEADER_VALUE) {
                    handleHeaderValue(buffer, currentState, builder);
                }
            }
            sanitazeListTypeHeaders(builder);
            return;
        } else {
            handleStateful(buffer, currentState, builder);
            sanitazeListTypeHeaders(builder);
            return;
        }
    }

    private void handleStateful(ByteBuffer buffer, ParseState currentState, HttpServerExchange builder) throws BadRequestException {
        if (currentState.state == ParseState.PATH) {
            handlePath(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }

        if (currentState.state == ParseState.QUERY_PARAMETERS) {
            handleQueryParameters(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }

        if (currentState.state == ParseState.PATH_PARAMETERS) {
            handlePathParameters(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
            // we could go back to PATH after PATH_PARAMETERS
            if (currentState.state == ParseState.PATH) {
                handlePath(buffer, currentState, builder);
                if (!buffer.hasRemaining()) {
                    return;
                }
            }
        }

        if (currentState.state == ParseState.VERSION) {
            handleHttpVersion(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }
        if (currentState.state == ParseState.AFTER_VERSION) {
            handleAfterVersion(buffer, currentState);
            if (!buffer.hasRemaining()) {
                return;
            }
        }
        while (currentState.state != ParseState.PARSE_COMPLETE) {
            if (currentState.state == ParseState.HEADER) {
                handleHeader(buffer, currentState, builder);
                if (!buffer.hasRemaining()) {
                    return;
                }
            }
            if (currentState.state == ParseState.HEADER_VALUE) {
                handleHeaderValue(buffer, currentState, builder);
                if (!buffer.hasRemaining()) {
                    return;
                }
            }
        }
    }

    private void sanitazeListTypeHeaders(final HttpServerExchange builder) {
        //NOTE: this should be used only for HTTP1.x
        if(!Cookies.isCrumbsAssemplyDisabled()) {
            Cookies.assembleCrumbs(builder.getRequestHeaders());
        }
        //TODO: list type headers?
    }

    abstract void handleHttpVerb(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder) throws BadRequestException;

    abstract void handleHttpVersion(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder) throws BadRequestException;

    abstract void handleHeader(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder) throws BadRequestException;

    /**
     * The parse states for parsing the path.
     */
    private static final int START = 0;
    private static final int FIRST_COLON = 1;
    private static final int FIRST_SLASH = 2;
    private static final int SECOND_SLASH = 3;
    private static final int IN_PATH = 4;
    private static final int HOST_DONE = 5;

    /**
     * Parses a path value
     *
     * @param buffer   The buffer
     * @param state    The current state
     * @param exchange The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final void handlePath(ByteBuffer buffer, ParseState state, HttpServerExchange exchange) throws BadRequestException {
        StringBuilder stringBuilder = state.stringBuilder;
        int parseState = state.parseState;
        int canonicalPathStart = state.pos;
        boolean urlDecodeRequired = state.urlDecodeRequired;

        while (buffer.hasRemaining()) {
            char next = (char) (buffer.get() & 0xFF);
            if(!allowUnescapedCharactersInUrl && !ALLOWED_TARGET_CHARACTER[next]) {
                throw new BadRequestException(UndertowMessages.MESSAGES.invalidCharacterInRequestTarget(next));
            }
            if (next == ' ' || next == '\t') {
                if (stringBuilder.length() != 0) {
                    final String path = stringBuilder.toString();
                    parsePathComplete(state, exchange, canonicalPathStart, parseState, urlDecodeRequired, path);
                    exchange.setQueryString("");
                    state.state = ParseState.VERSION;
                    return;
                }
            } else if (next == '\r' || next == '\n') {
                throw UndertowMessages.MESSAGES.failedToParsePath();
            } else if (next == '?' && (parseState == START || parseState == HOST_DONE || parseState == IN_PATH)) {
                beginQueryParameters(buffer, state, exchange, stringBuilder, parseState, canonicalPathStart, urlDecodeRequired);
                return;
            } else if (next == ';') {
                state.parseState = parseState;
                state.urlDecodeRequired = urlDecodeRequired;
                // store at canonical path the partial path parsed up until here
                state.canonicalPath.append(stringBuilder.substring(canonicalPathStart));
                state.stringBuilder.append(";");
                // set position to end of path (possibly start of parameter name)
                state.pos = state.stringBuilder.length();
                // handle the path parameters
                handlePathParameters(buffer, state, exchange);
                // if state is PATH, it means that handlePathParameters found a / after parsing path parameters
                // so, we expect a next chunk of path in the next bytes, mark this with canonicalPathStart
                // and append the '/' read by handlePathParameters
                if(state.state == ParseState.PATH) {
                    canonicalPathStart = stringBuilder.length();
                    stringBuilder.append('/');
                } else {
                    // path has been entirely parsed, just return
                    return;
                }
            } else {

                if (decode && (next == '%' || next > 127)) {
                    urlDecodeRequired = true;
                } else if (next == ':' && parseState == START) {
                    parseState = FIRST_COLON;
                } else if (next == '/' && parseState == FIRST_COLON) {
                    parseState = FIRST_SLASH;
                } else if (next == '/' && parseState == FIRST_SLASH) {
                    parseState = SECOND_SLASH;
                } else if (next == '/' && parseState == SECOND_SLASH) {
                    parseState = HOST_DONE;
                    canonicalPathStart = stringBuilder.length();
                } else if (parseState == FIRST_COLON || parseState == FIRST_SLASH) {
                    parseState = IN_PATH;
                } else if (next == '/' && parseState != HOST_DONE) {
                    parseState = IN_PATH;
                }
                stringBuilder.append(next);
            }

        }
        state.parseState = parseState;
        state.pos = canonicalPathStart;
        state.urlDecodeRequired = urlDecodeRequired;
    }

    private void parsePathComplete(ParseState state, HttpServerExchange exchange, int canonicalPathStart, int parseState, boolean urlDecodeRequired, String path) {
        if (parseState == SECOND_SLASH) {
            exchange.setRequestPath("/");
            exchange.setRelativePath("/");
            exchange.setRequestURI(path, true);
        } else if (parseState < HOST_DONE && state.canonicalPath.length() == 0) {
            final String decodedRequestPath = decode(path, urlDecodeRequired, state, slashDecodingFlag, false);
            exchange.setRequestPath(decodedRequestPath);
            exchange.setRelativePath(decodedRequestPath);
            if(urlDecodeRequired && allowUnescapedCharactersInUrl) {
                final String uri = decode(path, urlDecodeRequired, state, slashDecodingFlag, false);
                exchange.setRequestURI(uri);
            } else {
                exchange.setRequestURI(path);
            }
        } else {
            handleFullUrl(state, exchange, canonicalPathStart, urlDecodeRequired, path, parseState);
        }
        state.stringBuilder.setLength(0);
        state.canonicalPath.setLength(0);
        state.parseState = 0;
        state.pos = 0;
        state.urlDecodeRequired = false;
    }

    private void beginQueryParameters(ByteBuffer buffer, ParseState state, HttpServerExchange exchange, StringBuilder stringBuilder, int parseState, int canonicalPathStart, boolean urlDecodeRequired) throws BadRequestException {
        final String path = stringBuilder.toString();
        parsePathComplete(state, exchange, canonicalPathStart, parseState, urlDecodeRequired, path);
        state.state = ParseState.QUERY_PARAMETERS;
        handleQueryParameters(buffer, state, exchange);
    }

    private void handleFullUrl(ParseState state, HttpServerExchange exchange, int canonicalPathStart, boolean urlDecodeRequired, String path, int parseState) {
        state.canonicalPath.append(path.substring(canonicalPathStart));
        final String requestPath = decode(state.canonicalPath.toString(), urlDecodeRequired, state, slashDecodingFlag, false);
        exchange.setRequestPath(requestPath);
        exchange.setRelativePath(requestPath);
        if(urlDecodeRequired && allowUnescapedCharactersInUrl) {
            final String uri = decode(path, urlDecodeRequired, state, slashDecodingFlag, false);
            exchange.setRequestURI(uri, parseState == HOST_DONE);
        } else {
            exchange.setRequestURI(path, parseState == HOST_DONE);
        }
    }


    /**
     * Parses query string parameters
     *
     * @param buffer   The buffer
     * @param state    The current state
     * @param exchange The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final void handleQueryParameters(ByteBuffer buffer, ParseState state, HttpServerExchange exchange) throws BadRequestException {
        final StringBuilder stringBuilder = state.stringBuilder;
        int queryParamPos = state.pos;
        int mapCount = state.mapCount;
        boolean urlDecodeRequired = state.urlDecodeRequired;
        String nextQueryParam = state.nextQueryParam;

        //so this is a bit funky, because it not only deals with parsing, but
        //also deals with URL decoding the query parameters as well, while also
        //maintaining a non-decoded version to use as the query string
        //In most cases these string will be the same, and as we do not want to
        //build up two separate strings we don't use encodedStringBuilder unless
        //we encounter an encoded character

        while (buffer.hasRemaining()) {
            final char next = (char) (buffer.get() & 0xFF);
            if(!allowUnescapedCharactersInUrl && !ALLOWED_TARGET_CHARACTER[next]) {
                throw new BadRequestException(UndertowMessages.MESSAGES.invalidCharacterInRequestTarget(next));
            }
            if (next == ' ' || next == '\t') {
                String queryString = stringBuilder.toString();
                if(urlDecodeRequired && this.allowUnescapedCharactersInUrl) {
                    queryString = decode(queryString, urlDecodeRequired, state, slashDecodingFlag, false);
                }
                exchange.setQueryString(queryString);
                if (nextQueryParam == null) {
                    if (queryParamPos != stringBuilder.length()) {
                        exchange.addQueryParam(decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true, true), "");
                    }
                } else {
                    exchange.addQueryParam(nextQueryParam, decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true, true));
                }
                state.state = ParseState.VERSION;
                state.stringBuilder.setLength(0);
                state.pos = 0;
                state.nextQueryParam = null;
                state.urlDecodeRequired = false;
                state.mapCount = 0;
                return;
            } else if (next == '\r' || next == '\n') {
                throw UndertowMessages.MESSAGES.failedToParsePath();
            } else {
                if (decode && (next == '+' || next == '%' || next > 127)) { //+ is only a whitespace substitute in the query part of the URL
                    urlDecodeRequired = true;
                } else if (next == '=' && nextQueryParam == null) {
                    nextQueryParam = decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true, true);
                    urlDecodeRequired = false;
                    queryParamPos = stringBuilder.length() + 1;
                } else if (next == '&' && nextQueryParam == null) {
                    if (++mapCount >= maxParameters) {
                        throw UndertowMessages.MESSAGES.tooManyQueryParameters(maxParameters);
                    }
                    if (queryParamPos != stringBuilder.length()) {
                        exchange.addQueryParam(decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true, true), "");
                    }
                    urlDecodeRequired = false;
                    queryParamPos = stringBuilder.length() + 1;
                } else if (next == '&') {
                    if (++mapCount >= maxParameters) {
                        throw UndertowMessages.MESSAGES.tooManyQueryParameters(maxParameters);
                    }
                    exchange.addQueryParam(nextQueryParam, decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true, true));
                    urlDecodeRequired = false;
                    queryParamPos = stringBuilder.length() + 1;
                    nextQueryParam = null;
                }
                stringBuilder.append(next);

            }

        }
        state.pos = queryParamPos;
        state.nextQueryParam = nextQueryParam;
        state.urlDecodeRequired = urlDecodeRequired;
        state.mapCount = mapCount;
    }

    private String decode(final String value, boolean urlDecodeRequired, ParseState state, final boolean slashDecodingFlag, final boolean formEncoded) {
        if (urlDecodeRequired) {
            return URLUtils.decode(value, charset, slashDecodingFlag, formEncoded, state.decodeBuffer);
        } else {
            return value;
        }
    }

    /**
     * This method should only ever be called if the path contains am non-decoded semi colon character ';'
     */
    final void handlePathParameters(ByteBuffer buffer, ParseState state, HttpServerExchange exchange) throws BadRequestException {

        state.state = ParseState.PATH_PARAMETERS;
        boolean urlDecodeRequired = state.urlDecodeRequired;
        String param = state.nextQueryParam;
        final StringBuilder stringBuilder = state.stringBuilder;
        int pos = state.pos;

        //so this is a bit funky, because it not only deals with parsing, but
        //also deals with URL decoding the query parameters as well, while also
        //maintaining a non-decoded version to use as the query string
        //In most cases these string will be the same, and as we do not want to
        //build up two separate strings we don't use encodedStringBuilder unless
        //we encounter an encoded character

        while (buffer.hasRemaining()) {
            char next = (char) (buffer.get() & 0xFF);
            if(!allowUnescapedCharactersInUrl && !ALLOWED_TARGET_CHARACTER[next]) {
                throw new BadRequestException(UndertowMessages.MESSAGES.invalidCharacterInRequestTarget(next));
            }
            // end of HTTP URI
            if (next == ' ' || next == '\t' || next == '?') {
                handleParsedParam(param, stringBuilder.substring(pos), exchange, urlDecodeRequired, state);
                final String path = stringBuilder.toString();
                // the canonicalPathStart should be the current length to not add anything to it
                parsePathComplete(state, exchange, path.length(), state.parseState, state.urlDecodeRequired, path);
                state.state = ParseState.VERSION;
                state.nextQueryParam = null;
                if (next == '?') {
                    state.state = ParseState.QUERY_PARAMETERS;
                    handleQueryParameters(buffer, state, exchange);
                } else
                    exchange.setQueryString("");
                return;
            } else if (next == '\r' || next == '\n') {
                throw UndertowMessages.MESSAGES.failedToParsePath();
            } else if (next == '/') {
                handleParsedParam(param, stringBuilder.substring(pos), exchange, urlDecodeRequired, state);
                state.pos = stringBuilder.length();
                state.state = ParseState.PATH;
                state.nextQueryParam = null;
                return;
            } else {
                if (decode && (next == '+' || next == '%' || next > 127)) {
                    urlDecodeRequired = true;
                }
                if ((next == '=' || (next == ',' && this.allowIDLessMatrixParams)) && param == null) {
                    param = decode(stringBuilder.substring(pos), urlDecodeRequired, state, true, true);
                    urlDecodeRequired = false;
                    pos = stringBuilder.length() + 1;
                } else if (next == ';') {
                    handleParsedParam(param, stringBuilder.substring(pos), exchange, urlDecodeRequired, state);
                    param = null;
                    pos = stringBuilder.length() + 1;
                } else if (next == ',') {
                    if(param == null) {
                        throw UndertowMessages.MESSAGES.failedToParsePath();
                    } else {
                        handleParsedParam(param, stringBuilder.substring(pos), exchange, urlDecodeRequired, state);
                        pos = stringBuilder.length() + 1;
                    }
                }
                stringBuilder.append(next);
            }
        }

        state.urlDecodeRequired = urlDecodeRequired;
        state.pos = pos;
        state.urlDecodeRequired = urlDecodeRequired;
        state.nextQueryParam = param;
    }

    private void handleParsedParam(String paramName, String parameterValue, HttpServerExchange exchange, boolean urlDecodeRequired, ParseState state) throws BadRequestException {
        if (paramName == null) {
            exchange.addPathParam(decode(parameterValue, urlDecodeRequired, state, true, true), "");
        } else {  // path param already parsed so parse and add the value
            exchange.addPathParam(paramName, decode(parameterValue, urlDecodeRequired, state, true, true));
        }
    }

    /**
     * The parse states for parsing heading values
     */
    private static final int NORMAL = 0;
    private static final int WHITESPACE = 1;
    private static final int BEGIN_LINE_END = 2;
    private static final int LINE_END = 3;
    private static final int AWAIT_DATA_END = 4;

    /**
     * Parses a header value. This is called from the generated bytecode.
     *
     * @param buffer  The buffer
     * @param state   The current state
     * @param builder The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final void handleHeaderValue(ByteBuffer buffer, ParseState state, HttpServerExchange builder) throws BadRequestException {
        HttpString headerName = state.nextHeader;
        StringBuilder stringBuilder = state.stringBuilder;
        CacheMap<HttpString, String> headerValuesCache = state.headerValuesCache;
        if (headerName != null && stringBuilder.length() == 0 && headerValuesCache != null) {
            String existing = headerValuesCache.get(headerName);
            if (existing != null) {
                if (handleCachedHeader(existing, buffer, state, builder)) {
                    return;
                }
            }
        }

        handleHeaderValueCacheMiss(buffer, state, builder, headerName, headerValuesCache, stringBuilder);
    }

    private void handleHeaderValueCacheMiss(ByteBuffer buffer, ParseState state, HttpServerExchange builder, HttpString headerName, CacheMap<HttpString, String> headerValuesCache, StringBuilder stringBuilder) throws BadRequestException {

        int parseState = state.parseState;
        while (buffer.hasRemaining() && parseState == NORMAL) {
            final byte next = buffer.get();
            if (next == '\r') {
                parseState = BEGIN_LINE_END;
            } else if (next == '\n') {
                parseState = LINE_END;
            } else if (next == ' ' || next == '\t') {
                parseState = WHITESPACE;
            } else {
                stringBuilder.append((char) (next & 0xFF));
            }
        }

        while (buffer.hasRemaining()) {
            final byte next = buffer.get();
            switch (parseState) {
                case NORMAL: {
                    if (next == '\r') {
                        parseState = BEGIN_LINE_END;
                    } else if (next == '\n') {
                        parseState = LINE_END;
                    } else if (next == ' ' || next == '\t') {
                        parseState = WHITESPACE;
                    } else {
                        stringBuilder.append((char) (next & 0xFF));
                    }
                    break;
                }
                case WHITESPACE: {
                    if (next == '\r') {
                        parseState = BEGIN_LINE_END;
                    } else if (next == '\n') {
                        parseState = LINE_END;
                    } else if (next == ' ' || next == '\t') {
                    } else {
                        if (stringBuilder.length() > 0) {
                            stringBuilder.append(' ');
                        }
                        stringBuilder.append((char) (next & 0xFF));
                        parseState = NORMAL;
                    }
                    break;
                }
                case LINE_END:
                case BEGIN_LINE_END: {
                    if (next == '\n' && parseState == BEGIN_LINE_END) {
                        parseState = LINE_END;
                    } else if (next == '\t' ||
                            next == ' ') {
                        //this is a continuation
                        parseState = WHITESPACE;
                    } else {
                        //we have a header
                        String headerValue = stringBuilder.toString();


                        if (++state.mapCount > maxHeaders) {
                            throw new BadRequestException(UndertowMessages.MESSAGES.tooManyHeaders(maxHeaders));
                        }
                        //TODO: we need to decode this according to RFC-2047 if we have seen a =? symbol
                        builder.getRequestHeaders().add(headerName, headerValue);
                        if(headerValuesCache != null && headerName.length() + headerValue.length() < maxCachedHeaderSize) {
                            headerValuesCache.put(headerName, headerValue);
                        }

                        state.nextHeader = null;

                        state.leftOver = next;
                        state.stringBuilder.setLength(0);
                        if (next == '\r') {
                            parseState = AWAIT_DATA_END;
                        } else if (next == '\n') {
                            state.state = ParseState.PARSE_COMPLETE;
                            return;
                        } else {
                            state.state = ParseState.HEADER;
                            state.parseState = 0;
                            return;
                        }
                    }
                    break;
                }
                case AWAIT_DATA_END: {
                    state.state = ParseState.PARSE_COMPLETE;
                    return;
                }
            }
        }
        //we only write to the state if we did not finish parsing
        state.parseState = parseState;
    }

    protected boolean handleCachedHeader(String existing, ByteBuffer buffer, ParseState state, HttpServerExchange builder) throws BadRequestException {
        int pos = buffer.position();
        while (pos < buffer.limit() && buffer.get(pos) == ' ') {
            pos++;
        }
        if (existing.length() + 3 + pos > buffer.limit()) {
            return false;
        }
        int i = 0;
        while (i < existing.length()) {
            byte b = buffer.get(pos + i);
            if (b != existing.charAt(i)) {
                return false;
            }
            ++i;
        }
        if (buffer.get(pos + i++) != '\r') {
            return false;
        }
        if (buffer.get(pos + i++) != '\n') {
            return false;
        }
        int next = buffer.get(pos + i);
        if (next == '\t' || next == ' ') {
            //continuation
            return false;
        }
        buffer.position(pos + i);
        if (++state.mapCount > maxHeaders) {
            throw new BadRequestException(UndertowMessages.MESSAGES.tooManyHeaders(maxHeaders));
        }
        //TODO: we need to decode this according to RFC-2047 if we have seen a =? symbol
        builder.getRequestHeaders().add(state.nextHeader, existing);

        state.nextHeader = null;

        state.state = ParseState.HEADER;
        state.parseState = 0;
        return true;
    }

    protected void handleAfterVersion(ByteBuffer buffer, ParseState state) throws BadRequestException {
        boolean newLine = state.leftOver == '\n';
        while (buffer.hasRemaining()) {
            final byte next = buffer.get();
            if (newLine) {
                if (next == '\n') {
                    state.state = ParseState.PARSE_COMPLETE;
                    return;
                } else {
                    state.state = ParseState.HEADER;
                    state.leftOver = next;
                    return;
                }
            } else {
                if (next == '\n') {
                    newLine = true;
                } else if (next != '\r' && next != ' ' && next != '\t') {
                    state.state = ParseState.HEADER;
                    state.leftOver = next;
                    return;
                } else {
                    throw UndertowMessages.MESSAGES.badRequest();
                }
            }
        }
        if (newLine) {
            state.leftOver = '\n';
        }
    }

    /**
     * This is a bit of hack to enable the parser to get access to the HttpString's that are sorted
     * in the static fields of the relevant classes. This means that in most cases a HttpString comparison
     * will take the fast path == route, as they will be the same object
     *
     * @return
     */
    @SuppressWarnings("unused")
    protected static Map<String, HttpString> httpStrings() {
        final Map<String, HttpString> results = new HashMap<>();
        final Class[] classs = {Headers.class, Methods.class, Protocols.class};

        for (Class<?> c : classs) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getType().equals(HttpString.class)) {
                    HttpString result = null;
                    try {
                        result = (HttpString) field.get(null);
                        results.put(result.toString(), result);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return results;

    }

}
