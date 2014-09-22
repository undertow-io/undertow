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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

import io.undertow.server.BasicSSLSessionInfo;
import io.undertow.util.HttpString;

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
    public static final String AJP_REMOTE_PORT = "AJP_REMOTE_PORT";

    int state;

    byte prefix;

    int dataSize;

    int numHeaders = 0;

    HttpString currentHeader;

    String currentAttribute;

    //TODO: can there be more than one attribute?
    Map<String, String> attributes = new HashMap<>();

    String remoteAddress;
    int serverPort = 80;
    String serverAddress;

    public boolean isComplete() {
        return state == 15;
    }

    BasicSSLSessionInfo createSslSessionInfo() {
        String sessionId = attributes.get(AjpRequestParser.SSL_SESSION);
        String cypher = attributes.get(AjpRequestParser.SSL_CIPHER);
        String cert = attributes.get(AjpRequestParser.SSL_CERT);
        if (cert == null && sessionId == null) {
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

    InetSocketAddress createPeerAddress() {
        if (remoteAddress == null) {
            return null;
        }
        String portString = attributes.get(AJP_REMOTE_PORT);
        int port = 0;
        if (portString != null) {
            try {
                port = Integer.parseInt(portString);
            } catch (IllegalArgumentException e) {
            }
        }
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
}
