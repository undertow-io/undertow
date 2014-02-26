package io.undertow.client.ajp;

import io.undertow.server.protocol.ajp.AbstractAjpParser;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
class AjpResponseParser extends AbstractAjpParser {

    public static final AjpResponseParser INSTANCE = new AjpResponseParser();

    private static final HttpString[] HTTP_HEADERS;

    public static final int SEND_HEADERS = 4;
    public static final int CPONG = 9;
    public static final int CPING = 10;
    public static final int SHUTDOWN = 7;

    private static final int AB = ('A' << 8) + 'B';

    static {

        HTTP_HEADERS = new HttpString[]{null,
                Headers.CONTENT_TYPE,
                Headers.CONTENT_LANGUAGE,
                Headers.CONTENT_LENGTH,
                Headers.DATE,
                Headers.LAST_MODIFIED,
                Headers.LOCATION,
                Headers.SET_COOKIE,
                Headers.SET_COOKIE2,
                Headers.SERVLET_ENGINE,
                Headers.STATUS,
                Headers.WWW_AUTHENTICATE
        };
    }

    public void parse(final ByteBuffer buf, final AjpResponseParseState state, final AjpResponseBuilder builder) {
        if (!buf.hasRemaining()) {
            return;
        }
        switch (state.state) {
            case AjpResponseParseState.BEGIN: {
                IntegerHolder result = parse16BitInteger(buf, state);
                if (!result.readComplete) {
                    return;
                } else {
                    if (result.value != AB) {
                        throw new IllegalStateException("Wrong magic number");
                    }
                }
            }
            case AjpResponseParseState.READING_DATA_SIZE: {
                IntegerHolder result = parse16BitInteger(buf, state);
                if (!result.readComplete) {
                    state.state = AjpResponseParseState.READING_DATA_SIZE;
                    return;
                } else {
                    state.dataSize = result.value;
                }
            }
            case AjpResponseParseState.READING_PREFIX_CODE: {
                if (!buf.hasRemaining()) {
                    state.state = AjpResponseParseState.READING_PREFIX_CODE;
                    return;
                } else {
                    final byte prefix = buf.get();
                    state.prefix = prefix;
                    if (prefix != 4 && prefix != 6) {
                        state.state = AjpResponseParseState.DONE;
                        return;
                    }
                }
            }
            case AjpResponseParseState.READING_STATUS_CODE: {
                //this state is overloaded for the request size
                //when reading state=6 (read_body_chunk requests)

                IntegerHolder result = parse16BitInteger(buf, state);
                if (result.readComplete) {
                    if (state.prefix == 4) {
                        builder.setStatusCode(result.value);
                    } else {
                        //read body chunk
                        //a bit hacky
                        state.state = AjpResponseParseState.DONE;
                        state.currentIntegerPart = result.value;
                        return;
                    }
                } else {
                    state.state = AjpResponseParseState.READING_STATUS_CODE;
                    return;
                }
            }
            case AjpResponseParseState.READING_REASON_PHRASE: {
                StringHolder result = parseString(buf, state, false);
                if (result.readComplete) {
                    builder.setReasonPhrase(result.value);
                    //exchange.setRequestURI(result.value);
                } else {
                    state.state = AjpResponseParseState.READING_REASON_PHRASE;
                    return;
                }
            }
            case AjpResponseParseState.READING_NUM_HEADERS: {
                IntegerHolder result = parse16BitInteger(buf, state);
                if (!result.readComplete) {
                    state.state = AjpResponseParseState.READING_NUM_HEADERS;
                    return;
                } else {
                    state.numHeaders = result.value;
                }
            }
            case AjpResponseParseState.READING_HEADERS: {
                int readHeaders = state.readHeaders;
                while (readHeaders < state.numHeaders) {
                    if (state.currentHeader == null) {
                        StringHolder result = parseString(buf, state, true);
                        if (!result.readComplete) {
                            state.state = AjpResponseParseState.READING_HEADERS;
                            state.readHeaders = readHeaders;
                            return;
                        }
                        if (result.header != null) {
                            state.currentHeader = result.header;
                        } else {
                            state.currentHeader = HttpString.tryFromString(result.value);
                        }
                    }
                    StringHolder result = parseString(buf, state, false);
                    if (!result.readComplete) {
                        state.state = AjpResponseParseState.READING_HEADERS;
                        state.readHeaders = readHeaders;
                        return;
                    }
                    builder.getResponseHeaders().add(state.currentHeader, result.value);
                    state.currentHeader = null;
                    ++readHeaders;
                }
            }
        }
        state.state = AjpResponseParseState.DONE;
    }

    @Override
    protected HttpString headers(int offset) {
        return HTTP_HEADERS[offset];
    }
}
