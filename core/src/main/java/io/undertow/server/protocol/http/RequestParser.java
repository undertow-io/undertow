/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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

import static io.undertow.server.protocol.http.RequestState.ABSOLUTE_FORM;
import static io.undertow.server.protocol.http.RequestState.ASTERISK_FORM;
import static io.undertow.server.protocol.http.RequestState.AUTHORITY_FORM;
import static io.undertow.server.protocol.http.RequestState.DNS;
import static io.undertow.server.protocol.http.RequestState.FIELD_NAME;
import static io.undertow.server.protocol.http.RequestState.FIELD_VALUE;
import static io.undertow.server.protocol.http.RequestState.HOST;
import static io.undertow.server.protocol.http.RequestState.IPV4;
import static io.undertow.server.protocol.http.RequestState.IPV6;
import static io.undertow.server.protocol.http.RequestState.MESSAGE_BODY;
import static io.undertow.server.protocol.http.RequestState.METHOD;
import static io.undertow.server.protocol.http.RequestState.ORIGIN_FORM;
import static io.undertow.server.protocol.http.RequestState.PATH_PARAMS;
import static io.undertow.server.protocol.http.RequestState.PATH_SEGMENTS;
import static io.undertow.server.protocol.http.RequestState.PORT;
import static io.undertow.server.protocol.http.RequestState.QUERY_PARAMS;
import static io.undertow.server.protocol.http.RequestState.REQUEST_TARGET;
import static io.undertow.server.protocol.http.RequestState.SCHEME;
import static io.undertow.server.protocol.http.RequestState.UNKNOWN;
import static io.undertow.server.protocol.http.RequestState.UNSPECIFIED;
import static io.undertow.server.protocol.http.RequestState.VERSION;
import static io.undertow.util.Methods.CONNECT;
import static io.undertow.util.Methods.OPTIONS;
import static io.undertow.util.Methods.PRI;
import static io.undertow.util.ParserUtils.AMPERSAND;
import static io.undertow.util.ParserUtils.ASTERISK;
import static io.undertow.util.ParserUtils.COLON;
import static io.undertow.util.ParserUtils.COMMA;
import static io.undertow.util.ParserUtils.CARRIAGE_RETURN;
import static io.undertow.util.ParserUtils.DOT;
import static io.undertow.util.ParserUtils.EQUALS;
import static io.undertow.util.ParserUtils.LEFT_SQUARE_BRACKET;
import static io.undertow.util.ParserUtils.LINE_FEED;
import static io.undertow.util.ParserUtils.PERCENT;
import static io.undertow.util.ParserUtils.PLUS;
import static io.undertow.util.ParserUtils.QUESTION;
import static io.undertow.util.ParserUtils.RIGHT_SQUARE_BRACKET;
import static io.undertow.util.ParserUtils.SEMICOLON;
import static io.undertow.util.ParserUtils.SLASH;
import static io.undertow.util.ParserUtils.SPACE;
import static io.undertow.util.ParserUtils.getMaximumRequestMethodLength;
import static io.undertow.util.ParserUtils.getProtocolLength;
import static io.undertow.util.ParserUtils.isAlphaChar;
import static io.undertow.util.ParserUtils.isDigitChar;
import static io.undertow.util.ParserUtils.isDNSNameChar;
import static io.undertow.util.ParserUtils.isHexDigitChar;
import static io.undertow.util.ParserUtils.isIPv4AddressChar;
import static io.undertow.util.ParserUtils.isIPv6AddressChar;
import static io.undertow.util.ParserUtils.isObsoleteChar;
import static io.undertow.util.ParserUtils.isPathSegmentChar;
import static io.undertow.util.ParserUtils.isProtocolChar;
import static io.undertow.util.ParserUtils.isRequestTargetChar;
import static io.undertow.util.ParserUtils.isSchemeChar;
import static io.undertow.util.ParserUtils.isSpaceOrTabChar;
import static io.undertow.util.ParserUtils.isTokenChar;
import static io.undertow.util.ParserUtils.isVisibleAsciiChar;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import io.undertow.util.URLUtils;
import io.undertow.util.BadRequestException;

import org.xnio.OptionMap;

/**
 * @author <a href="mailto:opalka.richard@gmail.com">Richard Opalka</a>
 */
final class RequestParser {
    private final int maxParameters;
    private final int maxHeaders;
    private final boolean slashDecodingFlag;
    private final boolean decode;
    private final String charset;
    private final boolean allowUnescapedCharactersInUrl;

    private RequestParser(final OptionMap options) {
        maxParameters = options.get(UndertowOptions.MAX_PARAMETERS, UndertowOptions.DEFAULT_MAX_PARAMETERS);
        maxHeaders = options.get(UndertowOptions.MAX_HEADERS, UndertowOptions.DEFAULT_MAX_HEADERS);
        slashDecodingFlag = URLUtils.getSlashDecodingFlag(options);
        decode = options.get(UndertowOptions.DECODE_URL, true);
        charset = options.get(UndertowOptions.URL_CHARSET, StandardCharsets.UTF_8.name());
        allowUnescapedCharactersInUrl = options.get(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL, false);
    }

    static RequestParser instance(final OptionMap options) {
        return new RequestParser(options);
    }

    void handle(final ByteBuffer buffer, final RequestState state, final HttpServerExchange builder) throws BadRequestException {
        parseMethod(buffer, state, builder);
        while (buffer.hasRemaining() && state.state < VERSION) {
            parseRequestTarget(buffer, state, builder);
        }
        parseVersion(buffer, state, builder);
        while (buffer.hasRemaining() && !state.isComplete()) {
            parseFieldName(buffer, state, builder);
            parseFieldValue(buffer, state, builder);
        }
    }

    private void parseMethod(final ByteBuffer buffer, final RequestState state, final HttpServerExchange builder) throws BadRequestException {
        if (state.state != METHOD || !buffer.hasRemaining()) return;

        final StringBuilder sb = state.parsedData;
        byte nextByte;
        char nextChar;

        while (buffer.hasRemaining()) {
            nextByte = buffer.get();
            nextChar = (char) (nextByte & 0xFF);

            if (isTokenChar(nextByte) && sb.length() <= getMaximumRequestMethodLength()) {
                // method data goes on
                sb.append(nextChar);
            } else if (nextByte == SPACE && sb.length() != 0) {
                // method read complete
                final String requestMethod = sb.toString();
                builder.setRequestMethod(Methods.fromString(requestMethod));
                // prepare for next parsing phase
                state.setNext(REQUEST_TARGET);
                return;
            } else throw new BadRequestException();
        }
    }

    private void parseRequestTarget(final ByteBuffer buffer, final RequestState state, final HttpServerExchange builder) throws BadRequestException {
        if (state.state != REQUEST_TARGET || !buffer.hasRemaining()) return;

        // Detect request target type
        if (state.targetType == UNKNOWN) {
            final byte nextByte = buffer.get();

            if (nextByte == SLASH) {
                state.targetType = ORIGIN_FORM;
                state.substate = PATH_SEGMENTS;
            } else if (PRI.equals(builder.getRequestMethod()) || OPTIONS.equals(builder.getRequestMethod()) && nextByte == ASTERISK) {
                state.targetType = ASTERISK_FORM;
                state.substate = PATH_SEGMENTS;
            } else if (CONNECT.equals(builder.getRequestMethod())) {
                state.targetType = AUTHORITY_FORM;
                state.substate = HOST;
            } else if (isAlphaChar(nextByte)) {
                state.targetType = ABSOLUTE_FORM;
                state.substate = SCHEME;
            } else throw new BadRequestException();

            buffer.position(buffer.position() - 1);
        }

        // Process request target
        while (buffer.hasRemaining() && state.state != VERSION) {
            if (state.targetType == ORIGIN_FORM) {
                parsePath(buffer, state, builder);
            } else if (state.targetType == ABSOLUTE_FORM) {
                parseScheme(buffer, state);
                parseHost(buffer, state);
                parsePort(buffer, state);
                parsePath(buffer, state, builder);
            } else if (state.targetType == AUTHORITY_FORM) {
                parseHost(buffer, state);
                parsePort(buffer, state);
                parsePath(buffer, state, builder);
            } else if (state.targetType == ASTERISK_FORM) {
                parsePath(buffer, state, builder);
            } else throw new IllegalStateException();
        }
    }

    private void parseVersion(final ByteBuffer buffer, final RequestState state, final HttpServerExchange builder) throws BadRequestException {
        if (state.state != VERSION || !buffer.hasRemaining()) return;

        final StringBuilder sb = state.parsedData;
        byte nextByte;
        char nextChar;

        while (buffer.hasRemaining()) {
            nextByte = buffer.get();
            nextChar = (char) (nextByte & 0xFF);

            if (state.previousByte == 0 && isProtocolChar(nextByte, sb.length())) {
                // http version data goes on
                sb.append(nextChar);
            } else if (state.previousByte == 0 && nextByte == CARRIAGE_RETURN && sb.length() == getProtocolLength()) {
                // http version read complete - first separator
                state.previousByte = nextByte;
            } else if (state.previousByte == CARRIAGE_RETURN && nextByte == LINE_FEED) {
                // http version read complete - second separator
                final String version = sb.toString();
                if (Protocols.HTTP_0_9.toString().equals(version)) builder.setProtocol(Protocols.HTTP_0_9);
                else if (Protocols.HTTP_1_0.toString().equals(version)) builder.setProtocol(Protocols.HTTP_1_0);
                else if (Protocols.HTTP_1_1.toString().equals(version)) builder.setProtocol(Protocols.HTTP_1_1);
                else if (Protocols.HTTP_2_0.toString().equals(version)) builder.setProtocol(Protocols.HTTP_2_0);
                else {
                    if (UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
                        UndertowLogger.REQUEST_LOGGER.tracef("Unsupported HTTP version %s; falling back to HTTP 1.1", version);
                    }
                    builder.setProtocol(Protocols.HTTP_1_1);
                }
                // prepare for next parsing phase
                state.setNext(FIELD_NAME);
                return;
            } else throw new BadRequestException();
        }
    }

    private void parseFieldName(final ByteBuffer buffer, final RequestState state, final HttpServerExchange builder) throws BadRequestException {
        if (state.state != FIELD_NAME || !buffer.hasRemaining()) return;

        final StringBuilder sb = state.parsedData;
        byte nextByte;
        char nextChar;

        while (buffer.hasRemaining()) {
            nextByte = buffer.get();
            nextChar = (char) (nextByte & 0xFF);

            if (state.previousByte == 0 && isTokenChar(nextByte)) {
                // field name data goes on
                sb.append(nextChar);
            } else if (state.previousByte == 0 && nextByte == COLON && sb.length() != 0) {
                // field name read complete
                final String headerName = sb.toString();
                // prepare for next parsing phase
                state.setNext(FIELD_VALUE);
                state.headerName = Headers.fromCache(headerName);
                if (state.headerName == null) state.headerName = new HttpString(headerName);
                return;
            } else if (state.previousByte == 0 && nextByte == CARRIAGE_RETURN && sb.length() == 0) {
                // special case - no headers available
                state.previousByte = nextByte;
            } else if (state.previousByte == CARRIAGE_RETURN && nextByte == LINE_FEED && sb.length() == 0) {
                // prepare for next parsing phase
                state.setNext(MESSAGE_BODY);
                return;
            } else throw new BadRequestException();
        }
    }

    private void parseFieldValue(final ByteBuffer buffer, final RequestState state, final HttpServerExchange builder) throws BadRequestException {
        if (state.state != FIELD_VALUE || !buffer.hasRemaining()) return;

        final StringBuilder sb = state.parsedData;
        byte nextByte;
        char nextChar;

        while (buffer.hasRemaining()) {
            nextByte = buffer.get();
            nextChar = (char) (nextByte & 0xFF);

            if (state.previousByte == 0 && sb.length() == 0 && isSpaceOrTabChar(nextByte)) {
                // heading OWS
            } else if (state.previousByte == 0 && (isVisibleAsciiChar(nextByte) || isObsoleteChar(nextByte))) {
                // field-vchar
                sb.append(nextChar);
            } else if (state.previousByte == 0 && isSpaceOrTabChar(nextByte)) {
                // field-vchar RWS or OWS or obs-fold
                state.previousByte = nextByte;
            } else if (isSpaceOrTabChar(state.previousByte) && (isVisibleAsciiChar(nextByte) || isObsoleteChar(nextByte))) {
                // field-vchar RWS field-vchar
                sb.append((char) (state.previousByte & 0xFF));
                sb.append(nextChar);
                state.previousByte = 0;
            } else if (isSpaceOrTabChar(state.previousByte) && isSpaceOrTabChar(nextByte)) {
                // field-vchar OWS or obs-fold
            } else if (isSpaceOrTabChar(state.previousByte) && nextByte == CARRIAGE_RETURN) {
                // field-vchar OWS or obs-fold
                state.previousByte = nextByte;
            } else if (state.previousByte == 0 && nextByte == CARRIAGE_RETURN) {
                // CRLF or obs-fold
                state.previousByte = nextByte;
            } else if (state.previousByte == CARRIAGE_RETURN && nextByte == LINE_FEED) {
                // CRLF or obs-fold
                state.previousByte = nextByte;
            } else if (state.previousByte == LINE_FEED && isSpaceOrTabChar(nextByte)) {
                // obs-fold
                if (sb.length() > 0) {
                    byte[] bytes = sb.toString().getBytes(StandardCharsets.US_ASCII);
                    if (!isSpaceOrTabChar(bytes[bytes.length - 1])) sb.append((char)SPACE);
                }
                state.previousByte = 0;
            } else if (state.previousByte == LINE_FEED && isTokenChar(nextByte)) {
                // CRLF header
                final String headerValue = sb.toString().trim();
                addHeader(state, builder, headerValue);
                // prepare for next parsing phase
                state.setNext(FIELD_NAME);
                sb.append(nextChar);
                return;
            } else if (state.previousByte == LINE_FEED && nextByte == CARRIAGE_RETURN) {
                // CRLF CRLF
                final String headerValue = sb.toString().trim();
                addHeader(state, builder, headerValue);
                // prepare for next parsing phase
                state.setNext(FIELD_NAME);
                state.previousByte = nextByte;
                return;
            } else throw new BadRequestException();
        }
    }

    private void parseScheme(final ByteBuffer buffer, final RequestState state) throws BadRequestException {
        if (state.substate != SCHEME || !buffer.hasRemaining()) return;

        final StringBuilder sb = state.parsedData;
        byte nextByte;
        char nextChar;

        while (buffer.hasRemaining()) {
            nextByte = buffer.get();
            nextChar = (char) (nextByte & 0xFF);

            if (state.previousByte == 0 && isSchemeChar(nextByte)) {
                // scheme data goes on
                sb.append(nextChar);
            } else if (state.previousByte == 0 && nextByte == COLON) {
                // scheme data goes on - first separator
                sb.append(nextChar);
                state.previousByte = nextByte;
            } else if (state.previousByte == COLON && nextByte == SLASH) {
                // scheme data goes on - second separator
                sb.append(nextChar);
                state.previousByte = nextByte;
            } else if (state.previousByte == SLASH && nextByte == SLASH) {
                // scheme read complete
                sb.append(nextChar);
                // prepare for next parsing phase
                state.previousByte = 0;
                state.substate = HOST;
                return;
            } else throw new BadRequestException();
        }
    }

    private void parseHost(final ByteBuffer buffer, final RequestState state) throws BadRequestException {
        if (state.substate != HOST || !buffer.hasRemaining()) return;

        final StringBuilder sb = state.parsedData;
        byte nextByte;
        char nextChar;

        while (buffer.hasRemaining()) {
            nextByte = buffer.get();
            nextChar = (char) (nextByte & 0xFF);

            if (state.addressType == UNSPECIFIED) {
                // detect address type
                if (nextByte == LEFT_SQUARE_BRACKET) {
                    state.addressType = IPV6;
                } else if (isDigitChar(nextByte)) {
                    state.addressType = IPV4;
                } else if (isPercentEncodedSequenceChar(nextByte, state)) {
                    state.addressType = DNS;
                } else if (isDNSNameChar(nextByte)) {
                    state.addressType = DNS;
                } else throw new BadRequestException();
                sb.append(nextChar);
            } else if (state.addressType == DNS) {
                if (isPercentEncodedSequenceChar(nextByte, state)) {
                    // DNS name data goes on - percent encoded sequence
                    sb.append(nextChar);
                } else if (isDNSNameChar(nextByte)) {
                    // DNS name data goes on
                    sb.append(nextChar);
                } else {
                    // DNS name read complete - prepare for next parsing phase
                    buffer.position(buffer.position() -1);
                    state.substate = PORT;
                    return;
                }
            } else if (state.addressType == IPV4) {
                if (isIPv4AddressChar(nextByte)) {
                    // IPv4 address data goes on
                    sb.append(nextChar);
                } else if (isPercentEncodedSequenceChar(nextByte, state)) {
                    // Not IPv4 address - percent encoded DNS name detected
                    sb.append(nextChar);
                    state.addressType = DNS;
                } else if (isDNSNameChar(nextByte)) {
                    // Not IPv4 address - DNS name detected
                    sb.append(nextChar);
                    state.addressType = DNS;
                } else {
                    // IPv4 address read complete - prepare for next parsing phase
                    buffer.position(buffer.position() -1);
                    state.substate = PORT;
                    return;
                }
            } else if (state.addressType == IPV6) {
                if (isIPv6AddressChar(nextByte)) {
                    // IPv6 address data goes on
                    sb.append(nextChar);
                } else if (nextByte == RIGHT_SQUARE_BRACKET) {
                    // IPv6 address read complete
                    sb.append(nextChar);
                    // prepare for next parsing phase
                    state.substate = PORT;
                    return;
                } else throw new BadRequestException();
            } else throw new IllegalStateException();
        }
    }

    private void parsePort(final ByteBuffer buffer, final RequestState state) throws BadRequestException {
        if (state.substate != PORT || !buffer.hasRemaining()) return;

        final boolean isPortOptional = state.targetType != AUTHORITY_FORM;
        final StringBuilder sb = state.parsedData;
        byte nextByte;
        char nextChar;

        while (buffer.hasRemaining()) {
            nextByte = buffer.get();
            nextChar = (char) (nextByte & 0xFF);

            if (state.previousByte == 0 && nextByte == COLON) {
                // port data read start
                sb.append(nextChar);
                state.previousByte = nextByte;
            } else if (state.previousByte == COLON && isDigitChar(nextByte)) {
                // port data goes on
                sb.append(nextChar);
                state.previousByte = nextByte;
            } else if (isDigitChar(state.previousByte) && isDigitChar(nextByte)) {
                // port data goes on
                sb.append(nextChar);
                state.previousByte = nextByte;
            } else if (isDigitChar(state.previousByte) && !isDigitChar(nextByte)) {
                // port read complete
                state.previousByte = 0;
                // prepare for next parsing phase
                buffer.position(buffer.position() -1);
                state.substate = PATH_SEGMENTS;
                state.position = sb.length();
                return;
            } else if (state.previousByte == 0 && isPortOptional) {
                // port is optional and was not provided - prepare for next parsing phase
                buffer.position(buffer.position() -1);
                state.substate = PATH_SEGMENTS;
                state.position = sb.length();
                return;
            } else throw new BadRequestException();
        }
    }

    private void parsePath(final ByteBuffer buffer, final RequestState state, final HttpServerExchange builder) throws BadRequestException {
        if (state.substate != PATH_SEGMENTS && state.substate != PATH_PARAMS && state.substate != QUERY_PARAMS || !buffer.hasRemaining()) return;

        while (buffer.hasRemaining() && state.substate < QUERY_PARAMS && state.state != VERSION) {
            parsePathSegments(buffer, state, builder);
            parsePathParameters(buffer, state, builder);
        }
        while (buffer.hasRemaining() && state.state != VERSION) {
            parseQueryParameters(buffer, state, builder);
        }
    }

    private void parsePathSegments(final ByteBuffer buffer, final RequestState state, final HttpServerExchange builder) throws BadRequestException {
        if (state.substate != PATH_SEGMENTS || !buffer.hasRemaining()) return;

        final StringBuilder sb = state.parsedData;
        byte nextByte;
        char nextChar;

        while (buffer.hasRemaining()) {
            nextByte = buffer.get();
            nextChar = (char) (nextByte & 0xFF);

            if (state.targetType == ORIGIN_FORM || state.targetType == ABSOLUTE_FORM) {
                if (isPercentEncodedSequenceChar(nextByte, state)) {
                    // percent encoded data
                    sb.append(nextChar);
                } else if (isDoubleDotSegmentCandidate(nextByte, state)) {
                    // double dot segment sequence
                    sb.append(nextChar);
                } else if (nextByte == SEMICOLON) {
                    // path parameters section beings
                    state.canonicalPath.append(sb.substring(state.position));
                    if (state.doubleDotSegment)
                        state.canonicalPath.append(nextChar); // special case, see UNDERTOW-2339
                    sb.append(nextChar);
                    state.position = sb.length();
                    // prepare for next parsing phase
                    state.previousByte = 0;
                    state.substate = PATH_PARAMS;
                    return;
                } else if (isPathSegmentChar(nextByte)) {
                    // path segment data continues
                    sb.append(nextChar);
                } else if (nextByte == QUESTION) {
                    // query parameters section begins
                    addPaths(state, builder);
                    // prepare for next parsing phase
                    state.setNext(REQUEST_TARGET, QUERY_PARAMS);
                    return;
                } else if (nextByte == SPACE) {
                    // end of request target
                    addPaths(state, builder);
                    // prepare for next parsing phase
                    state.setNext(VERSION);
                    return;
                } else if (allowUnescapedCharactersInUrl && !isRequestTargetChar(nextByte)) {
                    // unescaped characters
                    sb.append(nextChar);
                    state.urlDecodeRequired = true;
                } else throw new BadRequestException();
            } else if (state.targetType == AUTHORITY_FORM) {
                if (state.previousByte == 0 && nextByte == SPACE) {
                    // end of request target
                    addPaths(state, builder);
                    // prepare for next parsing phase
                    state.setNext(VERSION);
                    return;
                } else throw new BadRequestException();
            } else if (state.targetType == ASTERISK_FORM) {
                if (state.previousByte == 0 && nextByte == ASTERISK) {
                    // asterisk read complete
                    sb.append(nextChar);
                    state.previousByte = nextByte;
                } else if  (state.previousByte == ASTERISK && nextByte == SPACE) {
                    // end of request target
                    addPaths(state, builder);
                    // prepare for next parsing phase
                    state.setNext(VERSION);
                    return;
                } else throw new BadRequestException();
            } else throw new IllegalStateException();
        }
    }

    private void parsePathParameters(final ByteBuffer buffer, final RequestState state, final HttpServerExchange builder) throws BadRequestException {
        if (state.substate != PATH_PARAMS || !buffer.hasRemaining()) return;

        final StringBuilder sb = state.parsedData;
        byte nextByte;
        char nextChar;

        while (buffer.hasRemaining()) {
            nextByte = buffer.get();
            nextChar = (char) (nextByte & 0xFF);

            if (isPercentEncodedSequenceChar(nextByte, state)) {
                // percent encoded data
                sb.append(nextChar);
            } else if (nextByte == PLUS) {
                // encoded space
                sb.append(nextChar);
                state.urlDecodeRequired = decode;
            } else if (nextByte == EQUALS && state.paramName == null) {
                // path parameter key read complete
                state.paramName = decode(sb.substring(state.position), state.urlDecodeRequired, state, true, true);
                sb.append(nextChar);
                state.position = sb.length();
                state.paramDecodeRequired = false;
            } else if (nextByte == COMMA) {
                // potential path parameter value read complete
                sb.append(nextChar);
            } else if (nextByte == SEMICOLON) {
                // path parameter value read complete
                addParam(state, builder);
                sb.append(nextChar);
                state.position = sb.length();
                state.paramName = null;
                state.paramDecodeRequired = false;
            } else if (isPathSegmentChar(nextByte)) {
                // path parameters data continues
                sb.append(nextChar);
            } else if (nextByte == SLASH) {
                // path segments section continues
                addParam(state, builder);
                state.previousByte = nextByte;
                state.position = sb.length();
                sb.append(nextChar);
                state.paramName = null;
                // prepare for next parsing phase
                state.substate = PATH_SEGMENTS;
                return;
            } else if (nextByte == QUESTION) {
                // query parameters section begins
                addParam(state, builder);
                state.position = sb.length();
                addPaths(state, builder);
                // prepare for next parsing phase
                state.setNext(REQUEST_TARGET, QUERY_PARAMS);
                return;
            } else if (nextByte == SPACE) {
                // end of request target
                addParam(state, builder);
                state.position = sb.length();
                addPaths(state, builder);
                // prepare for next parsing phase
                state.setNext(VERSION);
                return;
            } else if (allowUnescapedCharactersInUrl && !isRequestTargetChar(nextByte)) {
                // unescaped characters
                sb.append(nextChar);
                state.urlDecodeRequired = true;
            } else throw new BadRequestException();
        }
    }

    private void parseQueryParameters(final ByteBuffer buffer, final RequestState state, final HttpServerExchange builder) throws BadRequestException {
        if (state.substate != QUERY_PARAMS || !buffer.hasRemaining()) return;

        final StringBuilder sb = state.parsedData;
        byte nextByte;
        char nextChar;

        while (buffer.hasRemaining()) {
            nextByte = buffer.get();
            nextChar = (char) (nextByte & 0xFF);

            if (isPercentEncodedSequenceChar(nextByte, state)) {
                // percent encoded data
                sb.append(nextChar);
            } else if (nextByte == PLUS) {
                // encoded space
                sb.append(nextChar);
                state.paramDecodeRequired = state.urlDecodeRequired = decode;
            } else if (nextByte == EQUALS && state.paramName == null) {
                // query parameter key read complete
                state.paramName = decode(sb.substring(state.position), state.urlDecodeRequired, state, true, true);
                sb.append(nextChar);
                state.position = sb.length();
                state.paramDecodeRequired = false;
            } else if (nextByte == AMPERSAND) {
                // query parameter value read complete
                addParam(state, builder);
                sb.append(nextChar);
                state.position = sb.length();
                state.paramName = null;
                state.paramDecodeRequired = false;
            } else if (isPathSegmentChar(nextByte) || nextByte == SLASH || nextByte == QUESTION) {
                // query parameters data continues
                sb.append(nextChar);
            } else if (nextByte == SPACE) {
                // end of request target
                addParam(state, builder);
                addQuery(state, builder);
                // prepare for next parsing phase
                state.setNext(VERSION);
                return;
            } else if (allowUnescapedCharactersInUrl && !isRequestTargetChar(nextByte)) {
                // unescaped characters
                sb.append(nextChar);
                state.urlDecodeRequired = true;
            } else throw new BadRequestException();
        }
    }

    private boolean isPercentEncodedSequenceChar(final byte nextByte, final RequestState state) throws BadRequestException {
        if (!isHexDigitChar(state.previousByte) && nextByte == PERCENT) {
            // percent encoded sequence start
            state.urlDecodeRequired = decode;
            if (state.substate == PATH_PARAMS || state.substate == QUERY_PARAMS) state.paramDecodeRequired = decode;
            state.previousByte = nextByte;
        } else if (state.previousByte == PERCENT && isHexDigitChar(nextByte)) {
            // percent encoded sequence continues
            state.previousByte = nextByte;
        } else if (isHexDigitChar(state.previousByte) && isHexDigitChar(nextByte)) {
            // percent encoded sequence end
            state.previousByte = 0;
        } else if (state.previousByte == PERCENT || isHexDigitChar(state.previousByte)) {
            // wrong percent encoded sequence
            throw new BadRequestException();
        } else return false;
        return true;
    }

    private boolean isDoubleDotSegmentCandidate(final byte nextByte, final RequestState state) {
        if (state.doubleDotSegment && nextByte == SEMICOLON) return false; // special case, see UNDERTOW-2339
        if (nextByte == SLASH) {
            state.previousByte = nextByte;
            state.doubleDotSegment = false;
        } else if (state.previousByte == SLASH && nextByte == DOT) {
            state.previousByte = nextByte;
        } else if (state.previousByte == DOT && nextByte == DOT) {
            state.previousByte = 0;
            state.doubleDotSegment = true;
        } else return state.doubleDotSegment = false;
        return true;
    }

    private String decode(final String value, boolean urlDecodeRequired, RequestState state, final boolean slashDecodingFlag, final boolean formEncoded) {
        if (urlDecodeRequired) {
            return URLUtils.decode(value, charset, slashDecodingFlag, formEncoded, state.decodedData);
        } else {
            return value;
        }
    }

    private void addPaths(final RequestState state, final HttpServerExchange exchange) {
        final boolean containsHost = state.targetType == ABSOLUTE_FORM || state.targetType == AUTHORITY_FORM;
        final String path = state.parsedData.toString();
        final String canonicalPath = state.targetType == AUTHORITY_FORM ? path : state.canonicalPath.append(path.substring(state.position)).toString();
        final String requestPath = decode(canonicalPath.isEmpty() && !containsHost ? path : canonicalPath, state.urlDecodeRequired, state, slashDecodingFlag, false);
        exchange.setRequestPath(requestPath.isEmpty() ? "/" : requestPath);
        exchange.setRelativePath(requestPath.isEmpty() ? "/" : requestPath);
        if (state.urlDecodeRequired && allowUnescapedCharactersInUrl) {
            final String uri = decode(path, state.urlDecodeRequired, state, slashDecodingFlag, false);
            exchange.setRequestURI(uri, containsHost);
        } else {
            exchange.setRequestURI(path, containsHost);
        }
    }

    private void addParam(final RequestState state, final HttpServerExchange exchange) throws BadRequestException {
        if (state.paramName == null && state.position == state.parsedData.length()) return;
        final boolean isQueryParam = state.substate == QUERY_PARAMS;
        if (++state.count > maxParameters) {
            if (isQueryParam) throw UndertowMessages.MESSAGES.tooManyQueryParameters(maxParameters);
            else throw UndertowMessages.MESSAGES.tooManyPathParameters(maxParameters);
        }

        final String decodedValue = decode(state.parsedData.substring(state.position), state.paramDecodeRequired, state, true, true);
        final String name = state.paramName == null ? decodedValue : state.paramName;
        final String value = state.paramName == null ? "" : decodedValue;
        if (isQueryParam) {
            exchange.addQueryParam(name, value);
        } else {
            for (String v : value.split(",")) {
                exchange.addPathParam(name, v);
            }
        }
    }

    private void addQuery(final RequestState state, final HttpServerExchange exchange) throws BadRequestException {
        String queryString = state.parsedData.toString();
        exchange.setQueryString(queryString);
        if (state.urlDecodeRequired && this.allowUnescapedCharactersInUrl) {
            queryString = decode(queryString, state.urlDecodeRequired, state, slashDecodingFlag, false);
            exchange.setDecodedQueryString(queryString);
        }
    }

    private void addHeader(final RequestState state, final HttpServerExchange builder, final String headerValue) throws BadRequestException {
        if (++state.count > maxHeaders) {
            throw new BadRequestException(UndertowMessages.MESSAGES.tooManyHeaders(maxHeaders));
        }

        builder.getRequestHeaders().add(state.headerName, headerValue);
    }
}
