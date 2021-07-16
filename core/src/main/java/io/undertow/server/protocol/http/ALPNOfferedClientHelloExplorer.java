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

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLException;

import io.undertow.UndertowMessages;

/**
 * Parses the client handshake to retrieve ALPN and cipher info
 */
final class ALPNOfferedClientHelloExplorer {

    // Private constructor prevents construction outside this class.
    private ALPNOfferedClientHelloExplorer() {
    }

    /**
     * The header size of TLS/SSL records.
     * <p>
     * The value of this constant is {@value}.
     */
    private static final int RECORD_HEADER_SIZE = 0x05;

    static boolean isIncompleteHeader(ByteBuffer source) {
        return source.remaining() < RECORD_HEADER_SIZE;
    }

    /**
     * Checks if a client handshake is offering ALPN, and if so it returns a list of all ciphers. If ALPN is not being
     * offered then this will return null.
     */
    static List<Integer> parseClientHello(ByteBuffer source)
            throws SSLException {

        ByteBuffer input = source.duplicate();

        // Do we have a complete header?
        if (isIncompleteHeader(input)) {
            throw new BufferUnderflowException();
        }
        // Is it a handshake message?
        byte firstByte = input.get();
        byte secondByte = input.get();
        byte thirdByte = input.get();

        if ((firstByte & 0x80) != 0 && thirdByte == 0x01) {
            // looks like a V2ClientHello, we ignore it.
            return null;
        } else if (firstByte == 22) {   // 22: handshake record
            if (secondByte == 3 && thirdByte >= 1 && thirdByte <= 3) {
                return exploreTLSRecord(input,
                        firstByte, secondByte, thirdByte);
            }
        }
        return null;
    }

    /*
     * struct {
     *     uint8 major;
     *     uint8 minor;
     * } ProtocolVersion;
     *
     * enum {
     *     change_cipher_spec(20), alert(21), handshake(22),
     *     application_data(23), (255)
     * } ContentType;
     *
     * struct {
     *     ContentType type;
     *     ProtocolVersion version;
     *     uint16 length;
     *     opaque fragment[TLSPlaintext.length];
     * } TLSPlaintext;
     */
    private static List<Integer> exploreTLSRecord(
            ByteBuffer input, byte firstByte, byte secondByte,
            byte thirdByte) throws SSLException {

        // Is it a handshake message?
        if (firstByte != 22) {        // 22: handshake record
            throw UndertowMessages.MESSAGES.notHandshakeRecord();
        }

        // Is there enough data for a full record?
        int recordLength = getInt16(input);
        if (recordLength > input.remaining()) {
            throw new BufferUnderflowException();
        }

        // We have already had enough source bytes.
        try {
            return exploreHandshake(input,
                    secondByte, thirdByte, recordLength);
        } catch (BufferUnderflowException ignored) {
            throw UndertowMessages.MESSAGES.invalidHandshakeRecord();
        }
    }

    /*
     * enum {
     *     hello_request(0), client_hello(1), server_hello(2),
     *     certificate(11), server_key_exchange (12),
     *     certificate_request(13), server_hello_done(14),
     *     certificate_verify(15), client_key_exchange(16),
     *     finished(20)
     *     (255)
     * } HandshakeType;
     *
     * struct {
     *     HandshakeType msg_type;
     *     uint24 length;
     *     select (HandshakeType) {
     *         case hello_request:       HelloRequest;
     *         case client_hello:        ClientHello;
     *         case server_hello:        ServerHello;
     *         case certificate:         Certificate;
     *         case server_key_exchange: ServerKeyExchange;
     *         case certificate_request: CertificateRequest;
     *         case server_hello_done:   ServerHelloDone;
     *         case certificate_verify:  CertificateVerify;
     *         case client_key_exchange: ClientKeyExchange;
     *         case finished:            Finished;
     *     } body;
     * } Handshake;
     */
    private static List<Integer> exploreHandshake(
            ByteBuffer input, byte recordMajorVersion,
            byte recordMinorVersion, int recordLength) throws SSLException {

        // What is the handshake type?
        byte handshakeType = input.get();
        if (handshakeType != 0x01) {   // 0x01: client_hello message
            throw UndertowMessages.MESSAGES.expectedClientHello();
        }

        // What is the handshake body length?
        int handshakeLength = getInt24(input);

        // Theoretically, a single handshake message might span multiple
        // records, but in practice this does not occur.
        if (handshakeLength > recordLength - 4) { // 4: handshake header size
            throw UndertowMessages.MESSAGES.multiRecordSSLHandshake();
        }

        input = input.duplicate();
        input.limit(handshakeLength + input.position());
        return exploreRecord(input);
    }

    /*
     * struct {
     *     uint32 gmt_unix_time;
     *     opaque random_bytes[28];
     * } Random;
     *
     * opaque SessionID<0..32>;
     *
     * uint8 CipherSuite[2];
     *
     * enum { null(0), (255) } CompressionMethod;
     *
     * struct {
     *     ProtocolVersion client_version;
     *     Random random;
     *     SessionID session_id;
     *     CipherSuite cipher_suites<2..2^16-2>;
     *     CompressionMethod compression_methods<1..2^8-1>;
     *     select (extensions_present) {
     *         case false:
     *             struct {};
     *         case true:
     *             Extension extensions<0..2^16-1>;
     *     };
     * } ClientHello;
     */
    private static List<Integer> exploreRecord(
            ByteBuffer input) throws SSLException {

        // client version
        byte helloMajorVersion = input.get();
        byte helloMinorVersion = input.get();
        if (helloMajorVersion != 3 && helloMinorVersion != 3) {
            //we only care about TLS 1.2
            return null;
        }


        // ignore random
        for (int i = 0; i < 32; ++i) {// 32: the length of Random
            byte d = input.get();
        }

        // session id
        processByteVector8(input);

        // cipher_suites

        int int16 = getInt16(input);
        List<Integer> ciphers = new ArrayList<>();
        for (int i = 0; i < int16; i += 2) {
            ciphers.add(getInt16(input));

        }

        // compression methods
        processByteVector8(input);
        if (input.remaining() > 0) {
            return exploreExtensions(input, ciphers);
        }
        return null;
    }

    /*
     * struct {
     *     ExtensionType extension_type;
     *     opaque extension_data<0..2^16-1>;
     * } Extension;
     *
     * enum {
     *     server_name(0), max_fragment_length(1),
     *     client_certificate_url(2), trusted_ca_keys(3),
     *     truncated_hmac(4), status_request(5), (65535)
     * } ExtensionType;
     */
    private static List<Integer> exploreExtensions(ByteBuffer input, List<Integer> ciphers)
            throws SSLException {
        int length = getInt16(input);           // length of extensions
        while (length > 0) {
            int extType = getInt16(input);      // extenson type
            int extLen = getInt16(input);       // length of extension data
            if (extType == 16) {      // 0x00: ty
                return ciphers;
            } else {                    // ignore other extensions
                processByteVector(input, extLen);
            }

            length -= extLen + 4;
        }
        return null;
    }

    private static int getInt8(ByteBuffer input) {
        return input.get();
    }

    private static int getInt16(ByteBuffer input) {
        return (input.get() & 0xFF) << 8 | input.get() & 0xFF;
    }

    private static int getInt24(ByteBuffer input) {
        return (input.get() & 0xFF) << 16 | (input.get() & 0xFF) << 8 |
                input.get() & 0xFF;
    }

    private static void processByteVector8(ByteBuffer input) {
        int int8 = getInt8(input);
        processByteVector(input, int8);
    }


    private static void processByteVector(ByteBuffer input, int length) {
        for (int i = 0; i < length; ++i) {
            byte b = input.get();
        }
    }
}

