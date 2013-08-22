package io.undertow.server.protocol.ajp;

import io.undertow.server.BasicSSLSessionInfo;
import io.undertow.util.HttpString;

import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
class AjpRequestParseState extends AbstractAjpParseState {

    //states
    public static final int BEGIN = 0;
    public static final int READING_MAGIC_NUMBER = 1;
    public static final int READING_DATA_SIZE = 2;
    public static final int READING_PREFIX_CODE = 3;
    public static final int READING_METHOD = 4;
    public static final int READING_PROTOCOL = 5;
    public static final int READING_REQUEST_URI = 6;
    public static final int READING_REMOTE_ADDR = 7;
    public static final int READING_REMOTE_HOST = 8;
    public static final int READING_SERVER_NAME = 9;
    public static final int READING_SERVER_PORT = 10;
    public static final int READING_IS_SSL = 11;
    public static final int READING_NUM_HEADERS = 12;
    public static final int READING_HEADERS = 13;
    public static final int READING_ATTRIBUTES = 14;
    public static final int DONE = 15;

    int state;

    byte prefix;

    int dataSize;

    int numHeaders = 0;

    HttpString currentHeader;

    String currentAttribute;

    //TODO: can there be more than one attribute?
    Map<String, String> attributes = new HashMap<String, String>();

    public boolean isComplete() {
        return state == 15;
    }

    BasicSSLSessionInfo createSslSessionInfo() {
        String sessionId = attributes.get(AjpRequestParser.SSL_SESSION);
        String cypher = attributes.get(AjpRequestParser.SSL_CIPHER);
        String cert = attributes.get(AjpRequestParser.SSL_CERT);
        if (sessionId == null ||
                cypher == null) {
            return null;
        }
        try {
            return new BasicSSLSessionInfo(sessionId, cypher, cert);
        } catch (CertificateException e) {
            return null;
        } catch (javax.security.cert.CertificateException e) {
            return null;
        }
    }
}
