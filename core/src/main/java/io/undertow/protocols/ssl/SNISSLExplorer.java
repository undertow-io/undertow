/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.protocols.ssl;

import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.io.IOException;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLException;
import javax.net.ssl.StandardConstants;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.undertow.UndertowMessages;

/**
 * Instances of this class acts as an explorer of the network data of an
 * SSL/TLS connection.
 */
final class SNISSLExplorer {

    // Private constructor prevents construction outside this class.
    private SNISSLExplorer() {
    }

    /**
     * The header size of TLS/SSL records.
     * <P>
     * The value of this constant is {@value}.
     */
    public static final int RECORD_HEADER_SIZE = 0x05;

    /**
     * Returns the required number of bytes in the {@code source}
     * {@link ByteBuffer} necessary to explore SSL/TLS connection.
     * <P>
     * This method tries to parse as few bytes as possible from
     * {@code source} byte buffer to get the length of an
     * SSL/TLS record.
     * <P>
     * This method accesses the {@code source} parameter in read-only
     * mode, and does not update the buffer's properties such as capacity,
     * limit, position, and mark values.
     *
     * @param  source
     *         a {@link ByteBuffer} containing
     *         inbound or outbound network data for an SSL/TLS connection.
     * @throws BufferUnderflowException if less than {@code RECORD_HEADER_SIZE}
     *         bytes remaining in {@code source}
     * @return the required size in byte to explore an SSL/TLS connection
     */
    public static int getRequiredSize(ByteBuffer source) {

        ByteBuffer input = source.duplicate();

        // Do we have a complete header?
        if (input.remaining() < RECORD_HEADER_SIZE) {
            throw new BufferUnderflowException();
        }

        // Is it a handshake message?
        byte firstByte = input.get();
        input.get();
        byte thirdByte = input.get();
        if ((firstByte & 0x80) != 0 && thirdByte == 0x01) {
            return RECORD_HEADER_SIZE;   // Only need the header fields
        } else {
            return ((input.get() & 0xFF) << 8 | input.get() & 0xFF) + 5;
        }
    }

    /**
     * Returns the required number of bytes in the {@code source} byte array
     * necessary to explore SSL/TLS connection.
     * <P>
     * This method tries to parse as few bytes as possible from
     * {@code source} byte array to get the length of an
     * SSL/TLS record.
     *
     * @param  source
     *         a byte array containing inbound or outbound network data for
     *         an SSL/TLS connection.
     * @param  offset
     *         the start offset in array {@code source} at which the
     *         network data is read from.
     * @param  length
     *         the maximum number of bytes to read.
     *
     * @throws BufferUnderflowException if less than {@code RECORD_HEADER_SIZE}
     *         bytes remaining in {@code source}
     * @return the required size in byte to explore an SSL/TLS connection
     */
    public static int getRequiredSize(byte[] source,
            int offset, int length) throws IOException {

        ByteBuffer byteBuffer =
            ByteBuffer.wrap(source, offset, length).asReadOnlyBuffer();
        return getRequiredSize(byteBuffer);
    }

    /**
     * Launch and explore the security capabilities from byte buffer.
     * <P>
     * This method tries to parse as few records as possible from
     * {@code source} byte buffer to get the capabilities
     * of an SSL/TLS connection.
     * <P>
     * Please NOTE that this method must be called before any handshaking
     * occurs.  The behavior of this method is not defined in this release
     * if the handshake has begun, or has completed.
     * <P>
     * This method accesses the {@code source} parameter in read-only
     * mode, and does not update the buffer's properties such as capacity,
     * limit, position, and mark values.
     *
     * @param  source
     *         a {@link ByteBuffer} containing
     *         inbound or outbound network data for an SSL/TLS connection.
     *
     * @throws IOException on network data error
     * @throws BufferUnderflowException if not enough source bytes available
     *         to make a complete exploration.
     *
     * @return the explored capabilities of the SSL/TLS
     *         connection
     */
    public static List<SNIServerName> explore(ByteBuffer source)
            throws SSLException {

        ByteBuffer input = source.duplicate();

        // Do we have a complete header?
        if (input.remaining() < RECORD_HEADER_SIZE) {
            throw new BufferUnderflowException();
        }

        // Is it a handshake message?
        byte firstByte = input.get();
        byte secondByte = input.get();
        byte thirdByte = input.get();
        if ((firstByte & 0x80) != 0 && thirdByte == 0x01) {
            // looks like a V2ClientHello
            return Collections.emptyList();
        } else if (firstByte == 22) {   // 22: handshake record
            return exploreTLSRecord(input,
                                    firstByte, secondByte, thirdByte);
        } else {
            throw UndertowMessages.MESSAGES.notHandshakeRecord();
        }
    }

    /**
     * Launch and explore the security capabilities from byte array.
     * <P>
     * Please NOTE that this method must be called before any handshaking
     * occurs.  The behavior of this method is not defined in this release
     * if the handshake has begun, or has completed.  Once handshake has
     * begun, or has completed, the security capabilities can not and
     * should not be launched with this method.
     *
     * @param  source
     *         a byte array containing inbound or outbound network data for
     *         an SSL/TLS connection.
     * @param  offset
     *         the start offset in array {@code source} at which the
     *         network data is read from.
     * @param  length
     *         the maximum number of bytes to read.
     *
     * @throws IOException on network data error
     * @throws BufferUnderflowException if not enough source bytes available
     *         to make a complete exploration.
     * @return the explored capabilities of the SSL/TLS
     *         connection
     *
     * @see #explore(ByteBuffer)
     */
    public static List<SNIServerName> explore(byte[] source,
            int offset, int length) throws IOException {
        ByteBuffer byteBuffer =
            ByteBuffer.wrap(source, offset, length).asReadOnlyBuffer();
        return explore(byteBuffer);
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
    private static List<SNIServerName> exploreTLSRecord(
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
    private static List<SNIServerName> exploreHandshake(
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
        return exploreClientHello(input,
                                    recordMajorVersion, recordMinorVersion);
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
    private static List<SNIServerName> exploreClientHello(
            ByteBuffer input,
            byte recordMajorVersion,
            byte recordMinorVersion) throws SSLException {

        ExtensionInfo info = null;

        // client version
        input.get(); //helloMajorVersion
        input.get(); //helloMinorVersion

        // ignore random
        int position = input.position();
        input.position(position + 32);  // 32: the length of Random

        // ignore session id
        ignoreByteVector8(input);

        // ignore cipher_suites
        int csLen = getInt16(input);
        while (csLen > 0) {
            getInt8(input);
            getInt8(input);
            csLen -= 2;
        }

        // ignore compression methods
        ignoreByteVector8(input);

        if (input.remaining() > 0) {
            info = exploreExtensions(input);
        }

        final List<SNIServerName> snList = info != null ? info.sni : Collections.emptyList();

        return snList;
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
    private static ExtensionInfo exploreExtensions(ByteBuffer input)
            throws SSLException {

        List<SNIServerName> sni = Collections.emptyList();
        List<String> alpn = Collections.emptyList();

        int length = getInt16(input);           // length of extensions
        while (length > 0) {
            int extType = getInt16(input);      // extension type
            int extLen = getInt16(input);       // length of extension data

            if (extType == 0x00) {      // 0x00: type of server name indication
                sni = exploreSNIExt(input, extLen);
            } else if (extType == 0x10) { // 0x10: type of alpn
                alpn = exploreALPN(input, extLen);
            } else {                    // ignore other extensions
                ignoreByteVector(input, extLen);
            }

            length -= extLen + 4;
        }

        return new ExtensionInfo(sni, alpn);
    }

    /*
     * opaque ProtocolName<1..2^8-1>;
     *
     * struct {
     *     ProtocolName protocol_name_list<2..2^16-1>
     * } ProtocolNameList;
     *
     */
    private static List<String> exploreALPN(ByteBuffer input,
            int extLen) throws SSLException {
        final ArrayList<String> strings = new ArrayList<>();

        int rem = extLen;
        if (extLen >= 2) {
            int listLen = getInt16(input);
            if (listLen == 0 || listLen + 2 != extLen) {
                throw UndertowMessages.MESSAGES.invalidTlsExt();
            }

            rem -= 2;
            while (rem > 0) {
                int len = getInt8(input);
                if (len > rem) {
                    throw UndertowMessages.MESSAGES.notEnoughData();
                }
                byte[] b = new byte[len];
                input.get(b);
                strings.add(new String(b, StandardCharsets.UTF_8));

                rem -= len + 1;
            }
        }
        return strings.isEmpty() ? Collections.emptyList() : strings;
    }

    /*
     * struct {
     *     NameType name_type;
     *     select (name_type) {
     *         case host_name: HostName;
     *     } name;
     * } ServerName;
     *
     * enum {
     *     host_name(0), (255)
     * } NameType;
     *
     * opaque HostName<1..2^16-1>;
     *
     * struct {
     *     ServerName server_name_list<1..2^16-1>
     * } ServerNameList;
     */
    private static List<SNIServerName> exploreSNIExt(ByteBuffer input,
            int extLen) throws SSLException {

        Map<Integer, SNIServerName> sniMap = new LinkedHashMap<>();

        int remains = extLen;
        if (extLen >= 2) {     // "server_name" extension in ClientHello
            int listLen = getInt16(input);     // length of server_name_list
            if (listLen == 0 || listLen + 2 != extLen) {
                throw UndertowMessages.MESSAGES.invalidTlsExt();
            }

            remains -= 2;     // 0x02: the length field of server_name_list
            while (remains > 0) {
                int code = getInt8(input);      // name_type
                int snLen = getInt16(input);    // length field of server name
                if (snLen > remains) {
                    throw UndertowMessages.MESSAGES.notEnoughData();
                }
                byte[] encoded = new byte[snLen];
                input.get(encoded);

                SNIServerName serverName;
                switch (code) {
                    case StandardConstants.SNI_HOST_NAME:
                        if (encoded.length == 0) {
                            throw UndertowMessages.MESSAGES.emptyHostNameSni();
                        }
                        serverName = new SNIHostName(encoded);
                        break;
                    default:
                        serverName = new UnknownServerName(code, encoded);
                }
                // check for duplicated server name type
                if (sniMap.put(serverName.getType(), serverName) != null) {
                    throw UndertowMessages.MESSAGES.duplicatedSniServerName(serverName.getType());
                }

                remains -= encoded.length + 3;  // NameType: 1 byte
                                                // HostName length: 2 bytes
            }
        } else if (extLen == 0) {     // "server_name" extension in ServerHello
            throw UndertowMessages.MESSAGES.invalidTlsExt();
        }

        if (remains != 0) {
            throw UndertowMessages.MESSAGES.invalidTlsExt();
        }

        return Collections.unmodifiableList(new ArrayList<>(sniMap.values()));
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

    private static void ignoreByteVector8(ByteBuffer input) {
        ignoreByteVector(input, getInt8(input));
    }

    private static void ignoreByteVector(ByteBuffer input, int length) {
        if (length != 0) {
            int position = input.position();
            input.position(position + length);
        }
    }

    static final class UnknownServerName extends SNIServerName {
        UnknownServerName(int code, byte[] encoded) {
            super(code, encoded);
        }
    }

    static final class ExtensionInfo {
        final List<SNIServerName> sni;
        final List<String> alpn;

        ExtensionInfo(final List<SNIServerName> sni, final List<String> alpn) {
            this.sni = sni;
            this.alpn = alpn;
        }
    }
}

