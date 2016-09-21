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
import javax.net.ssl.SSLException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Super hacky class that allows the ServerHello message to be modified and the corresponding hash generated at runtime.
 *
 *
 * @author Stuart Douglas
 */
class ALPNHackServerByteArrayOutputStream extends ByteArrayOutputStream {

    private final SSLEngine sslEngine;

    private byte[] serverHello;
    private final String alpnProtocol;
    private boolean ready = false;


    ALPNHackServerByteArrayOutputStream(SSLEngine sslEngine, byte[] bytes, String alpnProtocol) {
        this.sslEngine = sslEngine;
        this.alpnProtocol = alpnProtocol;
        try {
            write(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e); //never happen
        }
        ready = true;
    }

    @Override
    public void write(byte[] b, int off, int len) {
        if(ready) {
            if(b[off] == 2) { // server hello
                ready = false; //we are done processing

                serverHello = new byte[len]; //TODO: actual ALPN
                System.arraycopy(b, off, serverHello, 0, len);
                try {
                    serverHello = ALPNHackServerHelloExplorer.addAlpnExtensionsToServerHello(serverHello, alpnProtocol);
                } catch (SSLException e) {
                    throw new RuntimeException(e);
                }
                ALPNHackSSLEngine.regenerateHashes(sslEngine, this, toByteArray(), serverHello);
                return;
            }
        }
        super.write(b, off, len);
    }

    byte[] getServerHello() {
        return serverHello;
    }
}
