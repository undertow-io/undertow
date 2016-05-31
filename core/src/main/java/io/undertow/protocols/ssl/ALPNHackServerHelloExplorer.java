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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hacks up ALPN support into the server hello message
 *
 * This has two different usage modes, one is adding a selected protocol into the extensions, the other is removing
 * all mention of ALPN and retuning the selected protocol. This dual mode does not make for the cleanest code
 * but removes the need to have duplicate nearly identical methods.
 *
 * The if the selected protocol is set then this will be added. If the selected protocol is null then ALPN will be
 * parsed and removed.
 *
 * <p>
 * We only care about TLS 1.2, as TLS 1.1 is not allowed to use ALPN.
 * <p>
 * Super hacky, but slightly less hacky than modifying the boot class path
 */
final class ALPNHackServerHelloExplorer {

    // Private constructor prevents construction outside this class.
    private ALPNHackServerHelloExplorer() {
    }

    static byte[] addAlpnExtensionsToServerHello(byte[] source, String selectedAlpnProtocol)
            throws SSLException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer input = ByteBuffer.wrap(source);
        try {

            exploreHandshake(input, source.length, new AtomicReference<>(selectedAlpnProtocol), out);
            //we need to adjust the record length;
            int serverHelloLength = out.size() - 4;
            out.write(source, input.position(), input.remaining()); //there may be more messages (cert etc), so we append them
            byte[] data = out.toByteArray();

            //now we need to adjust the handshake frame length
            data[1] = (byte) ((serverHelloLength >> 16) & 0xFF);
            data[2] = (byte) ((serverHelloLength >> 8) & 0xFF);
            data[3] = (byte) (serverHelloLength & 0xFF);
            return data;
        } catch (AlpnProcessingException e) {
            return source;
        }
    }

    /**
     * removes the ALPN extensions from the server hello
     * @param source
     * @return
     * @throws SSLException
     */
    static byte[] removeAlpnExtensionsFromServerHello(ByteBuffer source, final AtomicReference<String> selectedAlpnProtocol)
            throws SSLException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {

            exploreHandshake(source, source.remaining(), selectedAlpnProtocol, out);
            //we need to adjust the record length;
            int serverHelloLength = out.size() - 4;
            byte[] data = out.toByteArray();

            //now we need to adjust the handshake frame length
            data[1] = (byte) ((serverHelloLength >> 16) & 0xFF);
            data[2] = (byte) ((serverHelloLength >> 8) & 0xFF);
            data[3] = (byte) (serverHelloLength & 0xFF);
            return data;
        } catch (AlpnProcessingException e) {
            return null;
        }
    }
    private static void exploreHandshake(ByteBuffer input, int recordLength, AtomicReference<String> selectedAlpnProtocol, ByteArrayOutputStream out) throws SSLException {

        // What is the handshake type?
        byte handshakeType = input.get();
        if (handshakeType != 0x02) {   // 0x01: server_hello message
            throw UndertowMessages.MESSAGES.expectedServerHello();
        }
        out.write(handshakeType);

        // What is the handshake body length?
        int handshakeLength = getInt24(input);
        out.write(0); //placeholders
        out.write(0);
        out.write(0);

        // Theoretically, a single handshake message might span multiple
        // records, but in practice this does not occur.
        if (handshakeLength > recordLength - 4) { // 4: handshake header size
            throw UndertowMessages.MESSAGES.multiRecordSSLHandshake();
        }
        int old = input.limit();
        input.limit(handshakeLength + input.position());
        exploreServerHello(input, selectedAlpnProtocol, out);
        input.limit(old);
    }

    private static void exploreServerHello( ByteBuffer input, AtomicReference<String> alpnProtocolReference, ByteArrayOutputStream out) throws SSLException {

        // server version
        byte helloMajorVersion = input.get();
        byte helloMinorVersion = input.get();
        out.write(helloMajorVersion);
        out.write(helloMinorVersion);

        for (int i = 0; i < 32; ++i) { //the Random is 32 bytes
            out.write(input.get() & 0xFF);
        }

        // ignore session id
        processByteVector8(input, out);

        // ignore cipher_suite
        out.write(input.get() & 0xFF);
        out.write(input.get() & 0xFF);

        // ignore compression methods
        out.write(input.get() & 0xFF);

        String existingAlpn = null;
        ByteArrayOutputStream extensionsOutput = null;
        if (input.remaining() > 0) {
            extensionsOutput = new ByteArrayOutputStream();
            existingAlpn = exploreExtensions(input, extensionsOutput, alpnProtocolReference.get() == null);
        }

        if (existingAlpn != null) {
            if(alpnProtocolReference.get() != null) {
                throw new AlpnProcessingException();
            }
            alpnProtocolReference.set(existingAlpn);
            byte[] existing = extensionsOutput.toByteArray();
            out.write(existing, 0, existing.length);

        } else if(alpnProtocolReference.get() != null) {
            String selectedAlpnProtocol = alpnProtocolReference.get();
            ByteArrayOutputStream alpnBits = new ByteArrayOutputStream();
            alpnBits.write(0);
            alpnBits.write(16); //ALPN type
            int length = 3 + selectedAlpnProtocol.length(); //length of extension data
            alpnBits.write((length >> 8) & 0xFF);
            alpnBits.write(length & 0xFF);
            length -= 2;
            alpnBits.write((length >> 8) & 0xFF);
            alpnBits.write(length & 0xFF);
            alpnBits.write(selectedAlpnProtocol.length() & 0xFF);
            for (int i = 0; i < selectedAlpnProtocol.length(); ++i) {
                alpnBits.write(selectedAlpnProtocol.charAt(i) & 0xFF);
            }

            if (extensionsOutput != null) {
                byte[] existing = extensionsOutput.toByteArray();
                int newLength = existing.length - 2 + alpnBits.size();
                existing[0] = (byte) ((newLength >> 8) & 0xFF);
                existing[1] = (byte) (newLength & 0xFF);
                try {
                    out.write(existing);
                    out.write(alpnBits.toByteArray());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                int al = alpnBits.size();
                out.write((al >> 8) & 0xFF);
                out.write(al & 0xFF);
                try {
                    out.write(alpnBits.toByteArray());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } else if(extensionsOutput != null){
            byte[] existing = extensionsOutput.toByteArray();
            out.write(existing, 0, existing.length);
        }
    }

    static List<ByteBuffer> extractRecords(ByteBuffer data) {
        List<ByteBuffer> ret = new ArrayList<>();
        while (data.hasRemaining()) {
            byte d1 = data.get();
            byte d2 = data.get();
            byte d3 = data.get();
            byte d4 = data.get();
            byte d5 = data.get();
            int length = (d4 & 0xFF) << 8 | d5 & 0xFF;
            byte[] b = new byte[length + 5];
            b[0] = d1;
            b[1] = d2;
            b[2] = d3;
            b[3] = d4;
            b[4] = d5;
            data.get(b, 5, length);
            ret.add(ByteBuffer.wrap(b));
        }
        return ret;
    }

    private static String exploreExtensions(ByteBuffer input, ByteArrayOutputStream extensionOut, boolean removeAlpn)
            throws SSLException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String ret = null;
        int length = getInt16(input);           // length of extensions
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        int originalLength = length;
        while (length > 0) {
            int extType = getInt16(input);      // extenson type
            int extLen = getInt16(input);       // length of extension data
            if(extType == 16) {
                int vlen = getInt16(input);
                ret = readByteVector8(input);
                if(!removeAlpn) {
                    //we write the extension data back to the output stream
                    out.write((extType >> 8) & 0xFF);
                    out.write(extType & 0xFF);
                    out.write((extLen >> 8) & 0xFF);
                    out.write(extLen & 0xFF);
                    out.write((vlen >> 8) & 0xFF);
                    out.write(vlen & 0xFF);
                    out.write(ret.length() & 0xFF);
                    for(int i = 0; i < ret.length(); ++i) {
                        out.write(ret.charAt(i) & 0xFF);
                    }
                } else {
                    originalLength -= 6;
                    originalLength -= vlen;
                }
            } else {
                out.write((extType >> 8) & 0xFF);
                out.write(extType & 0xFF);
                out.write((extLen >> 8) & 0xFF);
                out.write(extLen & 0xFF);
                processByteVector(input, extLen, out);
            }
            length -= extLen + 4;
        }
        if(removeAlpn && ret == null) {
            //there was not ALPN to remove, so this whole thing is unnecessary, throw an exception to abort
            throw new AlpnProcessingException();
        }
        byte[] data = out.toByteArray();
        data[0] = (byte) ((originalLength >> 8) & 0xFF);
        data[1] = (byte) (originalLength  & 0xFF);
        extensionOut.write(data, 0, data.length);
        return ret;
    }

    private static String readByteVector8(ByteBuffer input) {
        int length = getInt8(input);
        byte[] data = new byte[length];
        input.get(data);
        return new String(data, StandardCharsets.US_ASCII);
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
        out.write(int8 & 0xFF);
        processByteVector(input, int8, out);
    }


    private static void processByteVector(ByteBuffer input, int length, ByteArrayOutputStream out) {
        for (int i = 0; i < length; ++i) {
            out.write(input.get() & 0xFF);
        }
    }

    static ByteBuffer createNewOutputRecords(byte[] newFirstMessage, List<ByteBuffer> records) {
        int length = newFirstMessage.length;
        length += 5; //Framing layer
        for (int i = 1; i < records.size(); ++i) {
            //the first record is the old server hello, so we start at 1 rather than zero
            ByteBuffer rec = records.get(i);
            length += rec.remaining();
        }
        byte[] newData = new byte[length];
        ByteBuffer ret = ByteBuffer.wrap(newData);
        ByteBuffer oldHello = records.get(0);
        ret.put(oldHello.get()); //type
        ret.put(oldHello.get()); //major
        ret.put(oldHello.get()); //minor
        ret.put((byte) ((newFirstMessage.length >> 8) & 0xFF));
        ret.put((byte) (newFirstMessage.length & 0xFF));
        ret.put(newFirstMessage);
        for (int i = 1; i < records.size(); ++i) {
            ByteBuffer rec = records.get(i);
            ret.put(rec);
        }
        ret.flip();
        return ret;
    }

    private static final class AlpnProcessingException extends RuntimeException {

    }
}

