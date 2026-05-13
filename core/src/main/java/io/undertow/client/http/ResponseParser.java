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

package io.undertow.client.http;

import io.undertow.UndertowLogger;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Protocols;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static io.undertow.client.http.ResponseState.FIELD_NAME;
import static io.undertow.client.http.ResponseState.FIELD_VALUE;
import static io.undertow.client.http.ResponseState.MESSAGE_BODY;
import static io.undertow.client.http.ResponseState.REASON_PHRASE;
import static io.undertow.client.http.ResponseState.STATUS_CODE;
import static io.undertow.client.http.ResponseState.VERSION;
import static io.undertow.util.ParserUtils.COLON;
import static io.undertow.util.ParserUtils.CARRIAGE_RETURN;
import static io.undertow.util.ParserUtils.LINE_FEED;
import static io.undertow.util.ParserUtils.SPACE;
import static io.undertow.util.ParserUtils.getProtocolLength;
import static io.undertow.util.ParserUtils.isDigitChar;
import static io.undertow.util.ParserUtils.isObsoleteChar;
import static io.undertow.util.ParserUtils.isProtocolChar;
import static io.undertow.util.ParserUtils.isSpaceOrTabChar;
import static io.undertow.util.ParserUtils.isTokenChar;
import static io.undertow.util.ParserUtils.isVisibleAsciiChar;

/**
 * @author <a href="mailto:opalka.richard@gmail.com">Richard Opalka</a>
 */
final class ResponseParser {

    static final ResponseParser INSTANCE = new ResponseParser();

    void handle(final ByteBuffer buffer, final ResponseState state, final HttpResponseBuilder builder) {
        parseVersion(buffer, state, builder);
        parseStatusCode(buffer, state, builder);
        parseReasonPhrase(buffer, state, builder);
        while (buffer.hasRemaining() && !state.isComplete()) {
            parseFieldName(buffer, state, builder);
            parseFieldValue(buffer, state, builder);
        }
    }

    private void parseVersion(final ByteBuffer buffer, final ResponseState state, final HttpResponseBuilder builder) {
        if (state.state != VERSION || !buffer.hasRemaining()) return;

        final StringBuilder sb = state.parsedData;
        byte nextByte;
        char nextChar;

        while (buffer.hasRemaining()) {
            nextByte = buffer.get();
            nextChar = (char) (nextByte & 0xFF);

            if (isProtocolChar(nextByte, sb.length())) {
                // http version data goes on
                sb.append(nextChar);
            } else if (nextByte == SPACE && sb.length() == getProtocolLength()) {
                // http version read complete
                final String version = sb.toString();
                if (Protocols.HTTP_0_9.toString().equals(version)) builder.setProtocol(Protocols.HTTP_0_9);
                else if (Protocols.HTTP_1_0.toString().equals(version)) builder.setProtocol(Protocols.HTTP_1_0);
                else if (Protocols.HTTP_1_1.toString().equals(version)) builder.setProtocol(Protocols.HTTP_1_1);
                else if (Protocols.HTTP_2_0.toString().equals(version)) builder.setProtocol(Protocols.HTTP_2_0);
                else {
                    if (UndertowLogger.RESPONSE_LOGGER.isTraceEnabled()) {
                        UndertowLogger.RESPONSE_LOGGER.tracef("Unsupported HTTP version %s; falling back to HTTP 1.1", version);
                    }
                    builder.setProtocol(Protocols.HTTP_1_1);
                }
                // prepare for next parsing phase
                state.parsedData.setLength(0);
                state.state = STATUS_CODE;
                return;
            } else throw new HttpResponseParseException("Wrong protocol version");
        }
    }

    private void parseStatusCode(final ByteBuffer buffer, final ResponseState state, final HttpResponseBuilder builder) {
        if (state.state != STATUS_CODE || !buffer.hasRemaining()) return;

        final StringBuilder sb = state.parsedData;
        byte nextByte;
        char nextChar;

        while (buffer.hasRemaining()) {
            nextByte = buffer.get();
            nextChar = (char) (nextByte & 0xFF);

            if (isDigitChar(nextByte) && sb.length() < 3) {
                // status code data goes on
                sb.append(nextChar);
            } else if (nextByte == SPACE && sb.length() == 3) {
                // status code data read complete
                final String code = sb.toString();
                builder.setStatusCode(Integer.parseInt(code));
                // prepare for next parsing phase
                state.parsedData.setLength(0);
                state.state = REASON_PHRASE;
                return;
            } else throw new HttpResponseParseException("Wrong status code");
        }
    }

    private void parseReasonPhrase(final ByteBuffer buffer, final ResponseState state, final HttpResponseBuilder builder) {
        if (state.state != REASON_PHRASE || !buffer.hasRemaining()) return;

        final StringBuilder sb = state.parsedData;
        byte nextByte;
        char nextChar;

        while (buffer.hasRemaining()) {
            nextByte = buffer.get();
            nextChar = (char) (nextByte & 0xFF);

            if (state.previousByte == 0 && (isVisibleAsciiChar(nextByte) || isSpaceOrTabChar(nextByte) || isObsoleteChar(nextByte))) {
                // reason phrase data goes on
                sb.append(nextChar);
            } else if (state.previousByte == 0 && nextByte == CARRIAGE_RETURN) {
                // reason phrase read complete - first separator
                state.previousByte = nextByte;
            } else if (state.previousByte == CARRIAGE_RETURN && nextByte == LINE_FEED) {
                // reason phrase read complete - second separator
                final String reason = sb.toString().trim();
                if (!reason.isEmpty()) builder.setReasonPhrase(reason);
                // prepare for next parsing phase
                state.previousByte = 0;
                state.parsedData.setLength(0);
                state.state = FIELD_NAME;
                return;
            } else throw new HttpResponseParseException("Wrong reason phrase");
        }
    }

    private void parseFieldName(final ByteBuffer buffer, final ResponseState state, final HttpResponseBuilder builder) {
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
                state.headerName = Headers.fromCache(headerName);
                if (state.headerName == null) state.headerName = new HttpString(headerName);
                // prepare for next parsing phase
                state.parsedData.setLength(0);
                state.state = FIELD_VALUE;
                return;
            } else if (state.previousByte == 0 && nextByte == CARRIAGE_RETURN && sb.length() == 0) {
                // special case - no headers available
                state.previousByte = nextByte;
            } else if (state.previousByte == CARRIAGE_RETURN && nextByte == LINE_FEED && sb.length() == 0) {
                // prepare for next parsing phase
                state.previousByte = 0;
                state.state = MESSAGE_BODY;
                return;
            } else throw new HttpResponseParseException("Wrong header name");
        }
    }

    private void parseFieldValue(final ByteBuffer buffer, final ResponseState state, final HttpResponseBuilder builder) {
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
                builder.getResponseHeaders().add(state.headerName, headerValue);
                // prepare for next parsing phase
                sb.setLength(0);
                sb.append(nextChar);
                state.headerName = null;
                state.previousByte = 0;
                state.state = FIELD_NAME;
                return;
            } else if (state.previousByte == LINE_FEED && (nextByte == CARRIAGE_RETURN)) {
                // CRLF CRLF
                final String headerValue = sb.toString().trim();
                builder.getResponseHeaders().add(state.headerName, headerValue);
                // prepare for next parsing phase
                sb.setLength(0);
                state.headerName = null;
                state.previousByte = nextByte;
                state.state = FIELD_NAME;
                return;
            } else throw new HttpResponseParseException("Wrong header value");
        }
    }}
