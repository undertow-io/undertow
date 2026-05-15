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

import io.undertow.UndertowLogger;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.Map;

import io.undertow.server.BasicSSLSessionInfo;
import io.undertow.util.HttpString;

/**
 * @author Stuart Douglas
 */
class AjpRequestParseState {

    //states
    static final int BEGIN = 0;
    static final int READING_MAGIC_NUMBER = 1;
    static final int READING_DATA_SIZE = 2;
    static final int READING_PREFIX_CODE = 3;
    static final int READING_METHOD = 4;
    static final int READING_PROTOCOL = 5;
    static final int READING_REQUEST_URI = 6;
    static final int READING_REMOTE_ADDR = 7;
    static final int READING_REMOTE_HOST = 8;
    static final int READING_SERVER_NAME = 9;
    static final int READING_SERVER_PORT = 10;
    static final int READING_IS_SSL = 11;
    static final int READING_NUM_HEADERS = 12;
    static final int READING_HEADERS = 13;
    static final int READING_ATTRIBUTES = 14;
    static final int DONE = 15;

    int state;

    byte prefix;

    int dataSize;

    int numHeaders = 0;

    HttpString currentHeader;

    String currentAttribute;

    //TODO: can there be more than one attribute?
    Map<String, String> attributes;

    String remoteAddress;
    int remotePort = -1;
    int serverPort = 80;
    String serverAddress;

    /**
     * The length of the string being read
     */
    int stringLength = -1;

    /**
     * The current string being read
     */
    private StringBuilder currentString = new StringBuilder();

    /**
     * when reading the first byte of an integer this stores the first value. It is set to -1 to signify that
     * the first byte has not been read yet.
     */
    int currentIntegerPart = -1;

    boolean containsUrlCharacters = false;
    int readHeaders;
    String sslSessionId;
    String sslCipher;
    String sslCert;
    String sslKeySize;
    String secureProtocol;
    boolean badRequest;
    boolean containsUnencodedUrlCharacters;

    void reset() {
        stringLength = -1;
        currentIntegerPart = -1;
        readHeaders = 0;
        badRequest = false;
        currentString.setLength(0);
        containsUnencodedUrlCharacters = false;
    }
    boolean isComplete() {
        return state == 15;
    }

    BasicSSLSessionInfo createSslSessionInfo() {
        String sessionId = sslSessionId;
        String cypher = sslCipher;
        String cert = sslCert;
        Integer keySize = null;
        if (cert == null && sessionId == null) {
            return null;
        }
        if (sslKeySize != null) {
            try {
                keySize = Integer.parseUnsignedInt(sslKeySize);
            } catch (NumberFormatException e) {
                UndertowLogger.REQUEST_LOGGER.debugf("Invalid sslKeySize %s", sslKeySize);
            }
        }
        try {
            return new BasicSSLSessionInfo(sessionId, cypher, cert, keySize, secureProtocol);
        } catch (CertificateException e) {
            return null;
        } catch (javax.security.cert.CertificateException e) {
            return null;
        }
    }

    InetSocketAddress createPeerAddress() {
        if (remoteAddress == null) {
            return null;
        }
        int port = remotePort > 0 ? remotePort : 0;
        try {
            InetAddress address = InetAddress.getByName(remoteAddress);
            return new InetSocketAddress(address, port);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    InetSocketAddress createDestinationAddress() {
        if (serverAddress == null) {
            return null;
        }
        return InetSocketAddress.createUnresolved(serverAddress, serverPort);
    }

    void addStringByte(byte b) {
        currentString.append((char)(b & 0xFF));
    }

    String getStringAndClear() throws UnsupportedEncodingException {
        String ret = currentString.toString();
        currentString.setLength(0);
        return ret;
    }

    int getCurrentStringLength() {
        return currentString.length();
    }
}
