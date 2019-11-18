/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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
package io.undertow.servlet.test.security.ssl;

import java.nio.ByteBuffer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import io.undertow.testutils.DefaultServer;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 * Runs {@link SSLMetaDataTestCase} with an expanded buffer SSL Engine,
 * and verifies if {@link javax.net.ssl.SSLEngineResult.Status#BUFFER_OVERFLOW}
 * is handled appropriately.
 *
 * @author Flavia Rainone
 */
@RunWith(DefaultServer.class)
public class SSLMetaDataWithExpandedBufferTestCase extends SSLMetaDataTestCase {

    @BeforeClass
    public static void setup() throws Exception {
        final SSLContext context = SSLContext.getDefault();
        final SSLEngine firstEngine = context.createSSLEngine();
        firstEngine.setUseClientMode(false);
        final SSLEngine anotherEngine = context.createSSLEngine();
        anotherEngine.setUseClientMode(false);

        final ByteBuffer expandBufferHandshake = ByteBuffer
                .wrap(new byte[] { 0x16, 0x3, 0x3, 0x71, 0x41 });

        final ByteBuffer unwrapDest = ByteBuffer.allocate(64 * 1024);
        // enable large fragment buffers in all engines in the JVM
        firstEngine.unwrap(expandBufferHandshake, unwrapDest);
        unwrapDest.clear();
        SSLMetaDataTestCase.setup();
    }
}
