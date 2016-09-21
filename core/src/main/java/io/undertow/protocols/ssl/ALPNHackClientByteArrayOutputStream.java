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

import javax.net.ssl.SSLEngine;
import java.io.ByteArrayOutputStream;

/**
 * Super hacky class that allows the client and server hello message to be modified and the corresponding hash generated
 * at runtime.
 *
 *
 * @author Stuart Douglas
 */
class ALPNHackClientByteArrayOutputStream extends ByteArrayOutputStream {

    private final SSLEngine sslEngine;
    private boolean ready = true;
    /**
     * the server hello that was sent over the wire, before we messed with it
     */
    private byte[] receivedServerHello;
    private byte[] sentClientHello;

    ALPNHackClientByteArrayOutputStream(SSLEngine sslEngine) {
        this.sslEngine = sslEngine;
    }

    @Override
    public void write(byte[] b, int off, int len) {
        if(ready) {
            if(b[off] == 2) { // server hello
                ready = false; //we are done processing
                byte[] newData;
                if(receivedServerHello != null) {
                    int b1 = b[off + 1];
                    int b2 = b[off + 2];
                    int b3 = b[off + 3];
                    int length = (b1 & 0xFF) << 16 | (b2 & 0xFF) << 8 | b3 & 0xFF;
                    if(length + 4 == len) {
                        newData = receivedServerHello;
                    } else {
                        newData = new byte[receivedServerHello.length + len - 4 - length];
                        System.arraycopy(receivedServerHello, 0, newData, 0, receivedServerHello.length);
                        System.arraycopy(b, length + 4, newData, receivedServerHello.length, len - 4 -length);
                    }
                } else {
                    newData = new byte[len];
                    System.arraycopy(b, 0, newData, 0, len);
                }
                ALPNHackSSLEngine.regenerateHashes(sslEngine, this, sentClientHello, newData);
                return;
            }
        }
        super.write(b, off, len);
    }

    byte[] getSentClientHello() {
        return sentClientHello;
    }

    void setSentClientHello(byte[] sentClientHello) {
        this.sentClientHello = sentClientHello;
    }

    byte[] getReceivedServerHello() {
        return receivedServerHello;
    }

    void setReceivedServerHello(byte[] receivedServerHello) {
        this.receivedServerHello = receivedServerHello;
    }
}
