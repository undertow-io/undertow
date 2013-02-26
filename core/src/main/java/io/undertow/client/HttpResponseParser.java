package io.undertow.client;

import io.undertow.annotationprocessor.HttpResponseParserConfig;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static io.undertow.util.Headers.ACCEPT_RANGES_STRING;
import static io.undertow.util.Headers.AGE_STRING;
import static io.undertow.util.Headers.CACHE_CONTROL_STRING;
import static io.undertow.util.Headers.CONNECTION_STRING;
import static io.undertow.util.Headers.CONTENT_DISPOSITION_STRING;
import static io.undertow.util.Headers.CONTENT_ENCODING_STRING;
import static io.undertow.util.Headers.CONTENT_LANGUAGE_STRING;
import static io.undertow.util.Headers.CONTENT_LENGTH_STRING;
import static io.undertow.util.Headers.CONTENT_LOCATION_STRING;
import static io.undertow.util.Headers.CONTENT_MD5_STRING;
import static io.undertow.util.Headers.CONTENT_RANGE_STRING;
import static io.undertow.util.Headers.CONTENT_TYPE_STRING;
import static io.undertow.util.Headers.DATE_STRING;
import static io.undertow.util.Headers.ETAG_STRING;
import static io.undertow.util.Headers.EXPIRES_STRING;
import static io.undertow.util.Headers.LAST_MODIFIED_STRING;
import static io.undertow.util.Headers.LOCATION_STRING;
import static io.undertow.util.Headers.PRAGMA_STRING;
import static io.undertow.util.Headers.PROXY_AUTHENTICATE_STRING;
import static io.undertow.util.Headers.REFRESH_STRING;
import static io.undertow.util.Headers.RETRY_AFTER_STRING;
import static io.undertow.util.Headers.SERVER_STRING;
import static io.undertow.util.Headers.SET_COOKIE2_STRING;
import static io.undertow.util.Headers.SET_COOKIE_STRING;
import static io.undertow.util.Headers.STRICT_TRANSPORT_SECURITY_STRING;
import static io.undertow.util.Headers.TRAILER_STRING;
import static io.undertow.util.Headers.TRANSFER_ENCODING_STRING;
import static io.undertow.util.Headers.VARY_STRING;
import static io.undertow.util.Headers.VIA_STRING;
import static io.undertow.util.Headers.WARNING_STRING;
import static io.undertow.util.Headers.WWW_AUTHENTICATE_STRING;
import static io.undertow.util.Protocols.HTTP_0_9_STRING;
import static io.undertow.util.Protocols.HTTP_1_0_STRING;
import static io.undertow.util.Protocols.HTTP_1_1_STRING;

/**
 * @author Emanuel Muckenhuber
 */
@HttpResponseParserConfig(
        protocols = {
                HTTP_0_9_STRING, HTTP_1_0_STRING, HTTP_1_1_STRING
        },
        headers = {
                ACCEPT_RANGES_STRING,
                AGE_STRING,
                CACHE_CONTROL_STRING,
                CONNECTION_STRING,
                CONTENT_DISPOSITION_STRING,
                CONTENT_ENCODING_STRING,
                CONTENT_LANGUAGE_STRING,
                CONTENT_LENGTH_STRING,
                CONTENT_LOCATION_STRING,
                CONTENT_MD5_STRING,
                CONTENT_RANGE_STRING,
                CONTENT_TYPE_STRING,
                DATE_STRING,
                EXPIRES_STRING,
                ETAG_STRING,
                LAST_MODIFIED_STRING,
                LOCATION_STRING,
                PRAGMA_STRING,
                PROXY_AUTHENTICATE_STRING,
                REFRESH_STRING,
                RETRY_AFTER_STRING,
                SERVER_STRING,
                SET_COOKIE_STRING,
                SET_COOKIE2_STRING,
                STRICT_TRANSPORT_SECURITY_STRING,
                TRAILER_STRING,
                TRANSFER_ENCODING_STRING,
                VARY_STRING,
                VIA_STRING,
                WARNING_STRING,
                WWW_AUTHENTICATE_STRING
        })
public abstract class HttpResponseParser {

    public static final HttpResponseParser INSTANCE;

    static {
        try {
            final Class<?> cls = HttpResponseParser.class.getClassLoader().loadClass(HttpResponseParser.class.getName() + "$$generated");
            INSTANCE = (HttpResponseParser) cls.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    abstract int handleHttpVersion(ByteBuffer buffer, int noBytes, ResponseParseState currentState, PendingHttpRequest builder);
    abstract int handleHeader(ByteBuffer buffer, int noBytes, ResponseParseState currentState, PendingHttpRequest builder);

    public int handle(final ByteBuffer buffer, int noBytes, final ResponseParseState currentState, final PendingHttpRequest builder) {

        if(currentState.state == ResponseParseState.VERSION) {
            noBytes = handleHttpVersion(buffer, noBytes, currentState, builder);
            if (noBytes == 0) {
                return 0;
            }
        }
        if(currentState.state == ResponseParseState.STATUS_CODE) {
            noBytes = handleStatusCode(buffer,  noBytes, currentState, builder);
            if (noBytes == 0) {
                return 0;
            }
        }
        if(currentState.state == ResponseParseState.REASON_PHRASE) {
            noBytes = handleReasonPhrase(buffer,  noBytes, currentState, builder);
            if (noBytes == 0) {
                return 0;
            }
        }
        if (currentState.state == ResponseParseState.AFTER_REASON_PHRASE) {
            noBytes = handleAfterReasonPhrase(buffer, noBytes, currentState, builder);
            if (noBytes == 0) {
                return 0;
            }
        }
        while (currentState.state != ResponseParseState.PARSE_COMPLETE) {
            if (currentState.state == ResponseParseState.HEADER) {
                noBytes = handleHeader(buffer, noBytes, currentState, builder);
                if (noBytes == 0) {
                    return 0;
                }
            }
            if (currentState.state == ResponseParseState.HEADER_VALUE) {
                noBytes = handleHeaderValue(buffer, noBytes, currentState, builder);
                if (noBytes == 0) {
                    return 0;
                }
            }
        }
        return noBytes;
    }

    /**
     * Parses the status code. This is called from the generated bytecode.
     *
     * @param buffer    The buffer
     * @param remaining The number of bytes remaining
     * @param state     The current state
     * @param builder   The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final int handleStatusCode(ByteBuffer buffer, int remaining, ResponseParseState state, PendingHttpRequest builder) {
        StringBuilder stringBuilder = state.stringBuilder;
        if (stringBuilder == null) {
            state.stringBuilder = stringBuilder = new StringBuilder();
        }
        while (remaining > 0) {
            final char next = (char) buffer.get();
            --remaining;
            if (next == ' ' || next == '\t') {
                builder.setStatusCode(Integer.parseInt(stringBuilder.toString()));
                state.state = ResponseParseState.REASON_PHRASE;
                state.stringBuilder = null;
                state.parseState = 0;
                state.pos = 0;
                state.nextHeader = null;
                return remaining;
            } else {
                stringBuilder.append(next);
            }
        }
        return remaining;
    }

    /**
     * Parses the reason phrase. This is called from the generated bytecode.
     *
     * @param buffer    The buffer
     * @param remaining The number of bytes remaining
     * @param state     The current state
     * @param builder   The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final int handleReasonPhrase(ByteBuffer buffer, int remaining, ResponseParseState state, PendingHttpRequest builder) {
        StringBuilder stringBuilder = state.stringBuilder;
        if (stringBuilder == null) {
            state.stringBuilder = stringBuilder = new StringBuilder();
        }
        while (remaining > 0) {
            final char next = (char) buffer.get();
            --remaining;
            if (next == '\n' || next == '\r') {
                builder.setReasonPhrase(stringBuilder.toString());
                state.state = ResponseParseState.AFTER_REASON_PHRASE;
                state.stringBuilder = null;
                state.parseState = 0;
                state.leftOver = (byte) next;
                state.pos = 0;
                state.nextHeader = null;
                return remaining;
            } else {
                stringBuilder.append(next);
            }
        }
        return remaining;
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
     * Parses a header value. This is called from the generated  bytecode.
     *
     * @param buffer    The buffer
     * @param remaining The number of bytes remaining
     * @param state     The current state
     * @param builder   The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final int handleHeaderValue(ByteBuffer buffer, int remaining, ResponseParseState state, PendingHttpRequest builder) {
        StringBuilder stringBuilder = state.stringBuilder;
        if (stringBuilder == null) {
            stringBuilder = new StringBuilder();
            state.parseState = 0;
        }

        int parseState = state.parseState;
        while (remaining > 0) {
            final byte next = buffer.get();
            --remaining;
            switch (parseState) {
                case NORMAL: {
                    if (next == '\r') {
                        parseState = BEGIN_LINE_END;
                    } else if (next == '\n') {
                        parseState = LINE_END;
                    } else if (next == ' ' || next == '\t') {
                        parseState = WHITESPACE;
                    } else {
                        stringBuilder.append((char) next);
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
                        stringBuilder.append((char) next);
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
                        HttpString nextStandardHeader = state.nextHeader;
                        String headerValue = stringBuilder.toString();

                        //TODO: we need to decode this according to RFC-2047 if we have seen a =? symbol
                        builder.getResponseHeaders().add(nextStandardHeader, headerValue);

                        state.nextHeader = null;

                        state.leftOver = next;
                        state.stringBuilder = null;
                        if (next == '\r') {
                            parseState = AWAIT_DATA_END;
                        } else {
                            state.state = ResponseParseState.HEADER;
                            state.parseState = 0;
                            return remaining;
                        }
                    }
                    break;
                }
                case AWAIT_DATA_END: {
                    state.state = ResponseParseState.PARSE_COMPLETE;
                    return remaining;
                }
            }
        }
        //we only write to the state if we did not finish parsing
        state.parseState = parseState;
        state.stringBuilder = stringBuilder;
        return remaining;
    }

    protected int handleAfterReasonPhrase(ByteBuffer buffer, int remaining, ResponseParseState state, PendingHttpRequest builder) {
        boolean newLine = state.leftOver == '\n';
        while (remaining > 0) {
            final byte next = buffer.get();
            --remaining;
            if (newLine) {
                if (next == '\n') {
                    state.state = ResponseParseState.PARSE_COMPLETE;
                    return remaining;
                } else {
                    state.state = ResponseParseState.HEADER;
                    state.leftOver = next;
                    return remaining;
                }
            } else {
                if (next == '\n') {
                    newLine = true;
                } else if (next != '\r' && next != ' ' && next != '\t') {
                    state.state = ResponseParseState.HEADER;
                    state.leftOver = next;
                    return remaining;
                }
            }
        }
        if (newLine) {
            state.leftOver = '\n';
        }
        return remaining;
    }

    /**
     * This is a bit of hack to enable the parser to get access to the HttpString's that are sorted
     * in the static fields of the relevant classes. This means that in most cases a HttpString comparison
     * will take the fast path == route, as they will be the same object
     *
     * @return
     */
    protected static Map<String, HttpString> httpStrings() {
        final Map<String, HttpString> results = new HashMap<String, HttpString>();
        final Class[] classs = {Headers.class, Methods.class, Protocols.class};

        for (Class<?> c : classs) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getType().equals(HttpString.class)) {
                    field.setAccessible(true);
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