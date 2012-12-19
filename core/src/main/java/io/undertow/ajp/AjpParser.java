package io.undertow.ajp;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

import java.nio.ByteBuffer;

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

/**
 * @author Stuart Douglas
 */
public class AjpParser {

    public static final int STRING_LENGTH_MASK = 1 << 31;

    public static final AjpParser INSTANCE = new AjpParser();

    private static final HttpString[] HTTP_METHODS;
    private static final HttpString[] HTTP_HEADERS;
    private static final String[] ATTRIBUTES;

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
        ATTRIBUTES[1] = "context";
        ATTRIBUTES[2] = "servlet_path";
        ATTRIBUTES[3] = "remote_user";
        ATTRIBUTES[4] = "auth_type";
        ATTRIBUTES[5] = "query_string";
        ATTRIBUTES[6] = "route";
        ATTRIBUTES[7] = "ssl_cert";
        ATTRIBUTES[8] = "ssl_cipher";
        ATTRIBUTES[9] = "ssl_session";
        ATTRIBUTES[10] = "req_attribute";
        ATTRIBUTES[11] = "ssl_key_size";
        ATTRIBUTES[12] = "secret";
        ATTRIBUTES[13] = "stored_method";
    }


    public void parse(final ByteBuffer buf, final AjpParseState state, final HttpServerExchange exchange) {
        if (!buf.hasRemaining()) {
            return;
        }
        switch (state.state) {
            case AjpParseState.BEGIN: {
                IntegerHolder result = parse16BitInteger(buf, state);
                if (!result.readComplete) {
                    return;
                } else {
                    if (result.value != 0x1234) {
                        throw new IllegalStateException("Wrong magic number");
                    }
                }
            }
            case AjpParseState.READING_DATA_SIZE: {
                IntegerHolder result = parse16BitInteger(buf, state);
                if (!result.readComplete) {
                    state.state = AjpParseState.READING_DATA_SIZE;
                    return;
                } else {
                    state.dataSize = result.value;
                }
            }
            case AjpParseState.READING_PREFIX_CODE: {
                if (!buf.hasRemaining()) {
                    state.state = AjpParseState.READING_PREFIX_CODE;
                    return;
                } else {
                    final byte prefix = buf.get();
                    if (prefix != 2) {
                        throw new IllegalArgumentException("We do  not support prefix codes other than 2 yet." + prefix);
                    }
                }
            }
            case AjpParseState.READING_METHOD: {
                if (!buf.hasRemaining()) {
                    state.state = AjpParseState.READING_METHOD;
                    return;
                } else {
                    int method = buf.get();
                    if (method > 0 && method < 28) {
                        exchange.setRequestMethod(HTTP_METHODS[method]);
                    } else {
                        throw new IllegalArgumentException("Unknown method type " + method);
                    }
                }
            }
            case AjpParseState.READING_PROTOCOL: {
                StringHolder result = parseString(buf, state, false);
                if (result.readComplete) {
                    //TODO: more efficient way of doing this
                    exchange.setProtocol(HttpString.tryFromString(result.value));
                } else {
                    state.state = AjpParseState.READING_PROTOCOL;
                    return;
                }
            }
            case AjpParseState.READING_REQUEST_URI: {
                StringHolder result = parseString(buf, state, false);
                if (result.readComplete) {
                    String res = result.value;
                    exchange.setRequestPath(res);
                    exchange.setRelativePath(res);
                } else {
                    state.state = AjpParseState.READING_REQUEST_URI;
                    return;
                }
            }
            case AjpParseState.READING_REMOTE_ADDR: {
                StringHolder result = parseString(buf, state, false);
                if (result.readComplete) {
                    //exchange.setRequestURI(result.value);
                } else {
                    state.state = AjpParseState.READING_REMOTE_ADDR;
                    return;
                }
            }
            case AjpParseState.READING_REMOTE_HOST: {
                StringHolder result = parseString(buf, state, false);
                if (result.readComplete) {
                    //exchange.setRequestURI(result.value);
                } else {
                    state.state = AjpParseState.READING_REMOTE_HOST;
                    return;
                }
            }
            case AjpParseState.READING_SERVER_NAME: {
                StringHolder result = parseString(buf, state, false);
                if (result.readComplete) {
                    //exchange.setRequestURI(result.value);
                } else {
                    state.state = AjpParseState.READING_SERVER_NAME;
                    return;
                }
            }
            case AjpParseState.READING_SERVER_PORT: {
                IntegerHolder result = parse16BitInteger(buf, state);
                if (result.readComplete) {
                    //exchange.setRequestURI(result.value);
                } else {
                    state.state = AjpParseState.READING_SERVER_PORT;
                    return;
                }
            }
            case AjpParseState.READING_IS_SSL: {
                if (!buf.hasRemaining()) {
                    state.state = AjpParseState.READING_IS_SSL;
                    return;
                } else {
                    final byte isSsl = buf.get();
                }
            }
            case AjpParseState.READING_NUM_HEADERS: {
                IntegerHolder result = parse16BitInteger(buf, state);
                if (!result.readComplete) {
                    state.state = AjpParseState.READING_NUM_HEADERS;
                    return;
                } else {
                    state.numHeaders = result.value;
                }
            }
            case AjpParseState.READING_HEADERS: {
                int readHeaders = exchange.getRequestHeaders().getHeaderNames().size();
                while (readHeaders < state.numHeaders) {
                    if (state.currentHeader == null) {
                        StringHolder result = parseString(buf, state, true);
                        if (!result.readComplete) {
                            state.state = AjpParseState.READING_HEADERS;
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
                        state.state = AjpParseState.READING_HEADERS;
                        return;
                    }
                    exchange.getRequestHeaders().add(state.currentHeader, result.value);
                    state.currentHeader = null;
                    ++readHeaders;
                }
            }
            case AjpParseState.READING_ATTRIBUTES: {
                for (; ; ) {
                    if (state.currentAttribute == null && state.currentIntegerPart == -1) {
                        if (!buf.hasRemaining()) {
                            state.state = AjpParseState.READING_ATTRIBUTES;
                            return;
                        }
                        int val = (0xFF & buf.get());
                        if (val == 0xFF) {
                            state.state = AjpParseState.DONE;
                            return;
                        } else if (val == 0x0A) {
                            //we need to read the name. We overload currentIntegerPart to avoid adding another state field
                            state.currentIntegerPart = 1;
                        } else {
                            state.currentAttribute = ATTRIBUTES[val];
                        }
                    }
                    if (state.currentIntegerPart == 1) {
                        StringHolder result = parseString(buf, state, false);
                        if (!result.readComplete) {
                            state.state = AjpParseState.READING_ATTRIBUTES;
                            return;
                        }
                        state.currentAttribute = result.value;
                        state.currentIntegerPart = -1;
                    }
                    StringHolder result = parseString(buf, state, false);
                    if (!result.readComplete) {
                        state.state = AjpParseState.READING_ATTRIBUTES;
                        return;
                    }
                    //query string.
                    if(state.currentAttribute.equals(ATTRIBUTES[5])) {
                        String res = result.value;
                        exchange.setQueryString(res);
                        int stringStart = 0;
                        String attrName = null;
                        for (int i = 0; i < res.length(); ++i) {
                            char c = res.charAt(i);
                            if(c == '=' && attrName == null) {
                                attrName = res.substring(stringStart, i);
                                stringStart = i+1;
                            } else if(c == '&') {
                                if(attrName != null) {
                                    exchange.addQueryParam(attrName, res.substring(stringStart, i));
                                } else {
                                    exchange.addQueryParam(res.substring(stringStart, i), "");
                                }
                                stringStart = i+1;
                                attrName = null;
                            }
                        }
                        if(attrName != null) {
                            exchange.addQueryParam(attrName, res.substring(stringStart, res.length()));
                        } else if(res.length() != stringStart) {
                            exchange.addQueryParam(res.substring(stringStart, res.length()), "");
                        }
                    }
                    //TODO: do something with the attributes
                    state.currentAttribute = null;
                }
            }
        }
        state.state = AjpParseState.DONE;
    }

    private IntegerHolder parse16BitInteger(ByteBuffer buf, AjpParseState state) {
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

    private StringHolder parseString(ByteBuffer buf, AjpParseState state, boolean header) {
        if (!buf.hasRemaining()) {
            return new StringHolder(null, false);
        }
        int stringLength = state.stringLength;
        if (stringLength == -1) {
            int number = buf.get() & 0xFF;
            if (buf.hasRemaining()) {
                final byte b = buf.get();
                stringLength = ((0xFF & number) << 8) + (b & 0xFF);
            } else {
                state.stringLength = number | STRING_LENGTH_MASK;
                return new StringHolder(null, false);
            }
        } else if ((stringLength & STRING_LENGTH_MASK) != 0) {
            int number = stringLength & ~STRING_LENGTH_MASK;
            stringLength = ((0xFF & number) << 8) + (buf.get() & 0xFF);
        }
        if (header && (stringLength & 0xFF00) != 0) {
            state.stringLength = -1;
            return new StringHolder(HTTP_HEADERS[stringLength & 0xFF]);
        }
        if (stringLength == 0xFFFF) {
            //OxFFFF means null
            state.stringLength = -1;
            return new StringHolder(null, true);
        }
        StringBuilder builder = builder = state.currentString;

        if (builder == null) {
            builder = new StringBuilder();
            state.currentString = builder;
        }
        int length = builder.length();
        while (length < stringLength) {
            if (!buf.hasRemaining()) {
                state.stringLength = stringLength;
                return new StringHolder(null, false);
            }
            builder.append((char) buf.get());
            ++length;
        }

        if (buf.hasRemaining()) {
            buf.get(); //null terminator
            state.currentString = null;
            state.stringLength = -1;
            return new StringHolder(builder.toString(), true);
        } else {
            return new StringHolder(null, false);
        }
    }

    private static class IntegerHolder {
        final int value;
        final boolean readComplete;

        private IntegerHolder(int value, boolean readComplete) {
            this.value = value;
            this.readComplete = readComplete;
        }
    }

    private static class StringHolder {
        final String value;
        final HttpString header;
        final boolean readComplete;

        private StringHolder(String value, boolean readComplete) {
            this.value = value;
            this.readComplete = readComplete;
            this.header = null;
        }

        private StringHolder(HttpString value) {
            this.value = null;
            this.readComplete = true;
            this.header = value;
        }
    }
}
