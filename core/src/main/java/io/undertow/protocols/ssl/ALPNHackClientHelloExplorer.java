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

package io.undertow.protocols.ssl;

import io.undertow.UndertowMessages;

import javax.net.ssl.SSLException;
import java.io.ByteArrayOutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to both read and write the ALPN protocol names in the ClientHello SSL message.
 *
 * If the out parameter is not null then the read function is being used, while if it present then it is rewriting
 * the hello message to include ALPN.
 *
 * Even though this dual approach is not particularly clean it does remove the need to have two versions of each function,
 * that do almost exactly the same thing.
 *
 */
final class ALPNHackClientHelloExplorer {

    // Private constructor prevents construction outside this class.
    private ALPNHackClientHelloExplorer() {
    }

    /**
     * The header size of TLS/SSL records.
     * <P>
     * The value of this constant is {@value}.
     */
    public static final int RECORD_HEADER_SIZE = 0x05;

    /**
     *
     *
     */
    static List<String> exploreClientHello(ByteBuffer source)
            throws SSLException {

        ByteBuffer input = source.duplicate();

        // Do we have a complete header?
        if (input.remaining() < RECORD_HEADER_SIZE) {
            throw new BufferUnderflowException();
        }
        List<String> alpnProtocols = new ArrayList<>();
        // Is it a handshake message?
        byte firstByte = input.get();
        byte secondByte = input.get();
        byte thirdByte = input.get();

        if ((firstByte & 0x80) != 0 && thirdByte == 0x01) {
            // looks like a V2ClientHello, we ignore it.
            return null;
        } else if (firstByte == 22) {   // 22: handshake record
            if(secondByte == 3 && thirdByte >= 1 && thirdByte <= 3) {
                exploreTLSRecord(input,
                        firstByte, secondByte, thirdByte, alpnProtocols, null);
                return alpnProtocols;
            }
            return null;
        } else {
            throw UndertowMessages.MESSAGES.notHandshakeRecord();
        }
    }

    static byte[] rewriteClientHello(byte[] source, List<String> alpnProtocols) throws SSLException {
        ByteBuffer input = ByteBuffer.wrap(source);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Do we have a complete header?
        if (input.remaining() < RECORD_HEADER_SIZE) {
            throw new BufferUnderflowException();
        }
        try {

            // Is it a handshake message?
            byte firstByte = input.get();
            byte secondByte = input.get();
            byte thirdByte = input.get();
            out.write(firstByte & 0xFF);
            out.write(secondByte & 0xFF);
            out.write(thirdByte & 0xFF);
            if ((firstByte & 0x80) != 0 && thirdByte == 0x01) {
                // looks like a V2ClientHello, we ignore it.
                return null;
            } else if (firstByte == 22) {   // 22: handshake record
                if (secondByte == 3 && thirdByte == 3) {
                    //TLS1.2 is the only one we care about. Previous versions can't use HTTP/2, newer versions won't be backported to JDK8
                    exploreTLSRecord(input,
                            firstByte, secondByte, thirdByte, alpnProtocols, out);
                    //we need to adjust the record length;
                    int clientHelloLength = out.size() - 9;
                    byte[] data = out.toByteArray();
                    int newLength = data.length - 5;
                    data[3] = (byte) ((newLength >> 8) & 0xFF);
                    data[4] = (byte) (newLength & 0xFF);

                    //now we need to adjust the handshake frame length
                    data[6] = (byte) ((clientHelloLength >> 16) & 0xFF);
                    data[7] = (byte) ((clientHelloLength >> 8) & 0xFF);
                    data[8] = (byte) (clientHelloLength & 0xFF);

                    return data;
                }
                return null;
            } else {
                throw UndertowMessages.MESSAGES.notHandshakeRecord();
            }
        } catch (ALPNPresentException e) {
            return null;
        }
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
    private static void exploreTLSRecord(
            ByteBuffer input, byte firstByte, byte secondByte,
            byte thirdByte, List<String> alpnProtocols, ByteArrayOutputStream out) throws SSLException {

        // Is it a handshake message?
        if (firstByte != 22) {        // 22: handshake record
            throw UndertowMessages.MESSAGES.notHandshakeRecord();
        }

        // Is there enough data for a full record?
        int recordLength = getInt16(input);
        if (recordLength > input.remaining()) {
            throw new BufferUnderflowException();
        }
        if(out != null) {
            out.write(0);
            out.write(0);
        }

        // We have already had enough source bytes.
        try {
            exploreHandshake(input,
                secondByte, thirdByte, recordLength, alpnProtocols, out);
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
    private static void exploreHandshake(
            ByteBuffer input, byte recordMajorVersion,
            byte recordMinorVersion, int recordLength, List<String> alpnProtocols, ByteArrayOutputStream out) throws SSLException {

        // What is the handshake type?
        byte handshakeType = input.get();
        if (handshakeType != 0x01) {   // 0x01: client_hello message
            throw UndertowMessages.MESSAGES.expectedClientHello();
        }
        if(out != null) {
            out.write(handshakeType & 0xFF);
        }

        // What is the handshake body length?
        int handshakeLength = getInt24(input);
        if(out != null) {
            //placeholder
            out.write(0);
            out.write(0);
            out.write(0);
        }

        // Theoretically, a single handshake message might span multiple
        // records, but in practice this does not occur.
        if (handshakeLength > recordLength - 4) { // 4: handshake header size
            throw UndertowMessages.MESSAGES.multiRecordSSLHandshake();
        }

        input = input.duplicate();
        input.limit(handshakeLength + input.position());
        exploreClientHello(input, alpnProtocols, out);
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
    private static void exploreClientHello(
            ByteBuffer input,
            List<String> alpnProtocols,
            ByteArrayOutputStream out) throws SSLException {

        // client version
        byte helloMajorVersion = input.get();
        byte helloMinorVersion = input.get();
        if(out != null) {
            out.write(helloMajorVersion & 0xFF);
            out.write(helloMinorVersion & 0xFF);
        }
        if(helloMajorVersion != 3 && helloMinorVersion != 3) {
            //we only care about TLS 1.2
            return;
        }


        // ignore random
        for(int i = 0; i < 32; ++i) {// 32: the length of Random
            byte d = input.get();
            if(out != null) {
                out.write(d & 0xFF);
            }
        }

        // session id
        processByteVector8(input, out);

        // cipher_suites
        processByteVector16(input, out);

        // compression methods
        processByteVector8(input, out);
        if (input.remaining() > 0) {
            exploreExtensions(input, alpnProtocols, out);
        } else if(out != null) {
            byte[] data = generateAlpnExtension(alpnProtocols);
            writeInt16(out, data.length);
            out.write(data, 0, data.length);
        }
    }

    private static void writeInt16(ByteArrayOutputStream out, int l) {
        if(out == null) return;
        out.write((l >> 8) & 0xFF);
        out.write(l & 0xFF);
    }

    private static byte[] generateAlpnExtension(List<String> alpnProtocols) {
        ByteArrayOutputStream alpnBits = new ByteArrayOutputStream();
        alpnBits.write(0);
        alpnBits.write(16); //ALPN type
        int length = 2;
        for(String p : alpnProtocols) {
            length++;
            length += p.length();
        }
        writeInt16(alpnBits, length);
        length -= 2;
        writeInt16(alpnBits, length);
        for(String p : alpnProtocols) {
            alpnBits.write(p.length() & 0xFF);
            for (int i = 0; i < p.length(); ++i) {
                alpnBits.write(p.charAt(i) & 0xFF);
            }
        }
        return alpnBits.toByteArray();
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
    private static void exploreExtensions(ByteBuffer input, List<String> alpnProtocols, ByteArrayOutputStream out)
            throws SSLException {
        ByteArrayOutputStream extensionOut = out == null ? null : new ByteArrayOutputStream();
        int length = getInt16(input);           // length of extensions
        writeInt16(extensionOut, 0); //placeholder
        while (length > 0) {
            int extType = getInt16(input);      // extenson type
            writeInt16(extensionOut, extType);
            int extLen = getInt16(input);       // length of extension data
            writeInt16(extensionOut, extLen);
            if (extType == 16) {      // 0x00: ty
                if(out == null) {
                    exploreALPNExt(input, alpnProtocols);
                } else {
                    throw new ALPNPresentException();
                }
            } else {                    // ignore other extensions
                processByteVector(input, extLen, extensionOut);
            }

            length -= extLen + 4;
        }
        if(out != null) {
            byte[] alpnBits = generateAlpnExtension(alpnProtocols);
            extensionOut.write(alpnBits,0 ,alpnBits.length);
            byte[] extensionsData = extensionOut.toByteArray();
            int newLength = extensionsData.length - 2;
            extensionsData[0] = (byte) ((newLength >> 8) & 0xFF);
            extensionsData[1] = (byte) (newLength & 0xFF);
            out.write(extensionsData, 0, extensionsData.length);
        }

    }

    private static void exploreALPNExt(ByteBuffer input, List<String> alpnProtocols) {
        int length = getInt16(input);
        int end = input.position() + length;
        while (input.position() < end) {
            alpnProtocols.add(readByteVector8(input));
        }
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

    private static void processByteVector8(ByteBuffer input, ByteArrayOutputStream out) {
        int int8 = getInt8(input);
        if(out != null) {
            out.write(int8 & 0xFF);
        }
        processByteVector(input, int8, out);
    }


    private static void processByteVector(ByteBuffer input, int length, ByteArrayOutputStream out) {
        for (int i = 0; i < length; ++i) {
            byte b = input.get();
            if(out != null) {
                out.write(b & 0xFF);
            }
        }
    }
    private static String readByteVector8(ByteBuffer input) {
        int length = getInt8(input);
        byte[] data = new byte[length];
        input.get(data);
        return new String(data, StandardCharsets.US_ASCII);
    }

    private static void processByteVector16(ByteBuffer input, ByteArrayOutputStream out) {
        int int16 = getInt16(input);
        writeInt16(out, int16);
        processByteVector(input, int16, out);
    }

    private static final class ALPNPresentException extends RuntimeException {

    }
}

