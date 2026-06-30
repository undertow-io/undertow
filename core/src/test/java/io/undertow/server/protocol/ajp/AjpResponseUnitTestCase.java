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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.ChannelListener;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.WriteReadyHandler;

import io.undertow.connector.ByteBufferPool;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.server.XnioBufferPoolAdaptor;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.category.UnitTest;
import io.undertow.util.HttpString;

/**
 * Tests for AJP response writing
 *
 * @author Ilias Bourdakos
 */
@Category(UnitTest.class)
public class AjpResponseUnitTestCase {

    private static final String EXPECTED_IO_EXCEPTION_MESSAGE = "Expected IOException to be thrown (was %s)";
    private static final String EXPECTED_NO_EXCEPTION_MESSAGE = "No Exception should be thrown for header values/names of small-length (exception was %s for length %s)";

    private static MockServerConnection mockServerConnection;
    private static HttpServerExchange exchange;
    private static AjpServerResponseConduit conduit;

    @Before
    public void setUp() {
        // create mock connection, exchange and conduit since we require them in
        // order to create a AjpServerResponseConduit (in order to write to the response)
        mockServerConnection = new MockServerConnection();
        exchange = new HttpServerExchange(mockServerConnection);
        conduit = getMockAjpServerResponseConduit(exchange, mockServerConnection);
    }

    /**
     * Test that large header values throw IOException when written to the response
     * of an AJP request.
     * @throws Exception
     */
    @Test
    public void testLargeResponseHeaderValueThrowsIOException() throws Exception {
        // create and set header with large value (8500 chars)
        StringBuilder largeHeaderValue = new StringBuilder(8500);
        for (int i = 0; i < 8500; i++) {
            largeHeaderValue.append('A');
        }
        exchange.getResponseHeaders().put(new HttpString("X-Large-Header"), largeHeaderValue.toString());

        // make sure header values over 8500 should throw IOException
        try {
            conduit.write(new ByteBuffer[]{ByteBuffer.allocate(1)}, 0, 1);
            Assert.fail(String.format(EXPECTED_IO_EXCEPTION_MESSAGE, "no exception"));
        } catch (IOException e) {
            Assert.assertEquals("UT000220: HTTP response header value was too large for the buffer (" + largeHeaderValue.length() + ").", e.getMessage());
        } catch (Exception e) {
            // dont accept any other exceptions
            Assert.fail(String.format(EXPECTED_IO_EXCEPTION_MESSAGE, e.getClass().getName()));
        }

        // decrease the header value to make sure there are no violations
        largeHeaderValue.delete(0,500);
        exchange.getResponseHeaders().put(new HttpString("X-Large-Header"), largeHeaderValue.toString());

        try {
            conduit.write(new ByteBuffer[]{ByteBuffer.allocate(1)}, 0, 1);
        } catch (Exception e) {
            Assert.fail(String.format(EXPECTED_NO_EXCEPTION_MESSAGE, e.getClass().getName(), largeHeaderValue.length()));
        }
    }

    /**
     * Test that large header names throw IOException when written to the response
     * of an AJP request.
     * @throws Exception
     */
    @Test
    public void testLargeResponseHeaderNameThrowsIOException() throws Exception {
        // create and set header with large name (8500 chars)
        StringBuilder largeHeaderName = new StringBuilder(8500);
        for (int i = 0; i < 8500; i++) {
            largeHeaderName.append('A');
        }
        exchange.getResponseHeaders().put(new HttpString(largeHeaderName.toString()), "value");

        // make sure header values over 8500 should throw IOException
        try {
            conduit.write(new ByteBuffer[]{ByteBuffer.allocate(1)}, 0, 1);
            Assert.fail(String.format(EXPECTED_IO_EXCEPTION_MESSAGE, "no exception"));
        } catch (IOException e) {
            Assert.assertEquals("UT000221: HTTP response header name was too large for the buffer (" + largeHeaderName.length() + ").", e.getMessage());
        } catch (Exception e) {
            // dont accept any other exceptions
            Assert.fail(String.format(EXPECTED_IO_EXCEPTION_MESSAGE, e.getClass().getName()));
        }

        exchange.getResponseHeaders().remove(largeHeaderName.toString()); // remove the old header since we don't overwrite it later

        // decrease the header value to make sure there are no violations
        largeHeaderName.delete(0, 500);
        exchange.getResponseHeaders().put(new HttpString(largeHeaderName.toString()), "value");

        try {
            conduit.write(new ByteBuffer[]{ByteBuffer.allocate(1)}, 0, 1);
        } catch (Exception e) {
            Assert.fail(String.format(EXPECTED_NO_EXCEPTION_MESSAGE, e.getClass().getName(), largeHeaderName.length()));
        }
    }

    private static AjpServerResponseConduit getMockAjpServerResponseConduit(HttpServerExchange exchange, MockServerConnection mockServerConnection) {
        return new AjpServerResponseConduit(new MockStreamConduit(), mockServerConnection.pool, exchange, null, false);
    }
}

/**
 * Mock StreamSinkConduit.
 * Used to create instances for cases were a
 * StreamSinkConduit is required for a specific test.
 */
class MockStreamConduit implements StreamSinkConduit {
    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        return 0;
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        return 0;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        // simulate a buffer write in order to return a positive value.
        // This is required since AjpServerResponseConduit will queue
        // the remaining bytes for writting if this returns 0
        int remaining = src.remaining();
        src.position(src.limit());
        return remaining;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offs, int len) throws IOException {
        long total = 0;
        for (int i = offs; i < offs + len; i++) {
            total += write(srcs[i]);
        }
        return total;
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return 0;
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return 0;
    }

    @Override
    public void terminateWrites() throws IOException {
    }

    @Override
    public boolean isWriteShutdown() {
        return false;
    }

    @Override
    public void resumeWrites() {
    }

    @Override
    public void suspendWrites() {
    }

    @Override
    public void wakeupWrites() {
    }

    @Override
    public boolean isWriteResumed() {
        return false;
    }

    @Override
    public void awaitWritable() throws IOException {
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
    }

    @Override
    public XnioIoThread getWriteThread() {
        return null;
    }

    @Override
    public void setWriteReadyHandler(WriteReadyHandler handler) {
    }

    @Override
    public void truncateWrites() throws IOException {
    }

    @Override
    public boolean flush() throws IOException {
        return true;
    }

    @Override
    public XnioWorker getWorker() {
        return null;
    }
}

/**
 * Mock ServerConnection.
 * Used to create instances for cases were a
 * ServerConnection is required for a specific test.
 */
class MockServerConnection extends ServerConnection {
    ByteBufferPool pool = DefaultServer.getBufferPool();

    @Override
    public OptionMap getUndertowOptions() {
        return OptionMap.EMPTY;
    }

    @Override
    public ByteBufferPool getByteBufferPool() {
        return null;
    }

    @Override
    public Pool<ByteBuffer> getBufferPool() {
        return new XnioBufferPoolAdaptor(pool);
    }

    @Override
    public XnioWorker getWorker() {
        return null;
    }

    @Override
    public XnioIoThread getIoThread() {
        return null;
    }

    @Override
    public HttpServerExchange sendOutOfBandResponse(HttpServerExchange exchange) {
        return null;
    }

    @Override
    public boolean isContinueResponseSupported() {
        return false;
    }

    @Override
    public void terminateRequestChannel(HttpServerExchange exchange) {
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return false;
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return null;
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        return null;
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public SocketAddress getPeerAddress() {
        return null;
    }

    @Override
    public <A extends SocketAddress> A getPeerAddress(Class<A> type) {
        return null;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return null;
    }

    @Override
    public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
        return null;
    }

    @Override
    public String getTransportProtocol() {
        return null;
    }

    @Override
    public boolean isPushSupported() {
        return false;
    }

    @Override
    public boolean isRequestTrailerFieldsSupported() {
        return false;
    }

    @Override
    public boolean isUpgradeSupported() {
        return false;
    }

    @Override
    public boolean isConnectSupported() {
        return false;
    }

    @Override
    public void addCloseListener(CloseListener listener) {
    }

    @Override
    protected StreamConnection upgradeChannel() {
        return null;
    }

    @Override
    public ConduitStreamSinkChannel getSinkChannel() {
        return null;
    }

    @Override
    protected ConduitStreamSourceChannel getSourceChannel() {
        return null;
    }

    @Override
    public void setUpgradeListener(io.undertow.server.HttpUpgradeListener upgradeListener) {
    }

    @Override
    public void setConnectListener(io.undertow.server.HttpUpgradeListener connectListener) {
    }

    @Override
    public void maxEntitySizeUpdated(HttpServerExchange exchange) {
    }

    @Override
    public int getBufferSize() {
        return 0;
    }

    @Override
    public io.undertow.server.SSLSessionInfo getSslSessionInfo() {
        return null;
    }

    @Override
    public void setSslSessionInfo(io.undertow.server.SSLSessionInfo sessionInfo) {
    }

    @Override
    public void exchangeComplete(HttpServerExchange exchange) {
    }

    @Override
    public StreamSinkConduit getSinkConduit(HttpServerExchange exchange, StreamSinkConduit conduit) {
        return conduit;
    }

    @Override
    public ChannelListener.Setter<? extends ConnectedChannel> getCloseSetter() {
        return null;
    }
}
