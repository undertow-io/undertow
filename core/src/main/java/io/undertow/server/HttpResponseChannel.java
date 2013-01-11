/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server;

import io.undertow.util.HttpString;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.undertow.util.HeaderMap;
import io.undertow.util.StatusCodes;
import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.ConcurrentStreamChannelAccessException;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class HttpResponseChannel implements StreamSinkChannel {

    private static final Logger log = Logger.getLogger("io.undertow.server.channel.response");

    private final StreamSinkChannel delegate;
    private final Pool<ByteBuffer> pool;

    private int state = STATE_START;

    private Iterator<HttpString> nameIterator;
    private String string;
    private HttpString headerName;
    private Iterator<String> valueIterator;
    private int charIndex;
    private Pooled<ByteBuffer> pooledBuffer;
    private final HttpServerExchange exchange;

    private final ChannelListener.SimpleSetter<HttpResponseChannel> writeSetter = new ChannelListener.SimpleSetter<HttpResponseChannel>();
    private final ChannelListener.SimpleSetter<HttpResponseChannel> closeSetter = new ChannelListener.SimpleSetter<HttpResponseChannel>();

    private static final int STATE_BODY = 0; // Message body, normal pass-through operation
    private static final int STATE_START = 1; // No headers written yet
    private static final int STATE_HDR_NAME = 2; // Header name indexed by charIndex
    private static final int STATE_HDR_D = 3; // Header delimiter ':'
    private static final int STATE_HDR_DS = 4; // Header delimiter ': '
    private static final int STATE_HDR_VAL = 5; // Header value
    private static final int STATE_HDR_EOL_CR = 6; // Header line CR
    private static final int STATE_HDR_EOL_LF = 7; // Header line LF
    private static final int STATE_HDR_FINAL_CR = 8; // Final CR
    private static final int STATE_HDR_FINAL_LF = 9; // Final LF
    private static final int STATE_BUF_FLUSH = 10; // flush the buffer and go to writing body

    private static final int MASK_STATE         = 0x0000000F;
    private static final int FLAG_SHUTDOWN      = 0x00000010;

    HttpResponseChannel(final StreamSinkChannel delegate, final Pool<ByteBuffer> pool, final HttpServerExchange exchange) {
        this.delegate = delegate;
        this.pool = pool;
        this.exchange = exchange;
        delegate.getCloseSetter().set(ChannelListeners.delegatingChannelListener(this, closeSetter));
        delegate.getWriteSetter().set(ChannelListeners.delegatingChannelListener(this, writeSetter));
    }

    public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
        return writeSetter;
    }

    public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
        return closeSetter;
    }

    /**
     * Handles writing out the header data. It can also take a byte buffer of user
     * data, to enable both user data and headers to be written out in a single operation,
     * which has a noticable performance impact.
     *
     * It is up to the caller to note the current position of this buffer before and after they
     * call this method, and use this to figure out how many bytes (if any) have been written.
     * @param state
     * @param userData
     * @return
     * @throws IOException
     */
    private int processWrite(int state, final ByteBuffer userData) throws IOException {
        if (state == STATE_START) {
            pooledBuffer = pool.allocate();
        }
        ByteBuffer buffer = pooledBuffer.getResource();
        Iterator<HttpString> nameIterator = this.nameIterator;
        Iterator<String> valueIterator = this.valueIterator;
        int charIndex = this.charIndex;
        int length;
        String string = this.string;
        HttpString headerName = this.headerName;
        int res;
        // BUFFER IS FLIPPED COMING IN
        if (state != STATE_START && buffer.hasRemaining()) {
            log.trace("Flushing remaining buffer");
            do {
                res = delegate.write(buffer);
                if (res == 0) {
                    return state;
                }
            } while (buffer.hasRemaining());
        }
        buffer.clear();
        // BUFFER IS NOW EMPTY FOR FILLING
        for (;;) {
            switch (state) {
                case STATE_BODY: {
                    // shouldn't be possible, but might as well do the right thing anyway
                    return state;
                }
                case STATE_START: {
                    log.trace("Starting response");
                    // we assume that our buffer has enough space for the initial response line plus one more CR+LF
                    assert buffer.remaining() >= 0x100;
                    string = exchange.getProtocol().toString();
                    length = string.length();
                    for (charIndex = 0; charIndex < length; charIndex ++) {
                        buffer.put((byte) string.charAt(charIndex));
                    }
                    buffer.put((byte) ' ');
                    int code = exchange.getResponseCode();
                    assert 999 >= code && code >= 100;
                    buffer.put((byte) (code / 100 + '0'));
                    buffer.put((byte) (code / 10 % 10 + '0'));
                    buffer.put((byte) (code % 10 + '0'));
                    buffer.put((byte) ' ');
                    string = StatusCodes.getReason(code);
                    length = string.length();
                    for (charIndex = 0; charIndex < length; charIndex ++) {
                        buffer.put((byte) string.charAt(charIndex));
                    }
                    buffer.put((byte) '\r').put((byte) '\n');
                    HeaderMap headers = exchange.getResponseHeaders();
                    nameIterator = headers.iterator();
                    if (! nameIterator.hasNext()) {
                        log.trace("No response headers");
                        buffer.put((byte) '\r').put((byte) '\n');
                        buffer.flip();
                        while (buffer.hasRemaining()) {
                            res = delegate.write(buffer);
                            if (res == 0) {
                                log.trace("Continuation");
                                return STATE_BUF_FLUSH;
                            }
                        }
                        pooledBuffer.free();
                        pooledBuffer = null;
                        log.trace("Body");
                        return STATE_BODY;
                    }
                    headerName = nameIterator.next();
                    charIndex = 0;
                    // fall thru
                }
                case STATE_HDR_NAME: {
                    log.tracef("Processing header '%s'", headerName);
                    length = headerName.length();
                    while (charIndex < length) {
                        if (buffer.hasRemaining()) {
                            buffer.put(headerName.byteAt(charIndex++));
                        } else {
                            log.trace("Buffer flush");
                            buffer.flip();
                            do {
                                res = delegate.write(buffer);
                                if (res == 0) {
                                    this.string = string;
                                    this.headerName = headerName;
                                    this.charIndex = charIndex;
                                    this.valueIterator = valueIterator;
                                    this.nameIterator = nameIterator;
                                    log.trace("Continuation");
                                    return STATE_HDR_NAME;
                                }
                            } while (buffer.hasRemaining());
                            buffer.clear();
                        }
                    }
                    // fall thru
                }
                case STATE_HDR_D: {
                    if (! buffer.hasRemaining()) {
                        buffer.flip();
                        do {
                            res = delegate.write(buffer);
                            if (res == 0) {
                                log.trace("Continuation");
                                this.string = string;
                                this.headerName = headerName;
                                this.charIndex = charIndex;
                                this.valueIterator = valueIterator;
                                this.nameIterator = nameIterator;
                                return STATE_HDR_D;
                            }
                        } while (buffer.hasRemaining());
                        buffer.clear();
                    }
                    buffer.put((byte) ':');
                    // fall thru
                }
                case STATE_HDR_DS: {
                    if (! buffer.hasRemaining()) {
                        buffer.flip();
                        do {
                            res = delegate.write(buffer);
                            if (res == 0) {
                                log.trace("Continuation");
                                this.string = string;
                                this.headerName = headerName;
                                this.charIndex = charIndex;
                                this.valueIterator = valueIterator;
                                this.nameIterator = nameIterator;
                                return STATE_HDR_DS;
                            }
                        } while (buffer.hasRemaining());
                        buffer.clear();
                    }
                    buffer.put((byte) ' ');
                    if(valueIterator == null) {
                        valueIterator = exchange.getResponseHeaders().get(headerName).iterator();
                    }
                    assert valueIterator.hasNext();
                    string = valueIterator.next();
                    charIndex = 0;
                    // fall thru
                }
                case STATE_HDR_VAL: {
                    log.tracef("Processing header value '%s'", string);
                    length = string.length();
                    while (charIndex < length) {
                        if (buffer.hasRemaining()) {
                            buffer.put((byte) string.charAt(charIndex++));
                        } else {
                            buffer.flip();
                            do {
                                res = delegate.write(buffer);
                                if (res == 0) {
                                    this.string = string;
                                    this.headerName = headerName;
                                    this.charIndex = charIndex;
                                    this.valueIterator = valueIterator;
                                    this.nameIterator = nameIterator;
                                    log.trace("Continuation");
                                    return STATE_HDR_VAL;
                                }
                            } while (buffer.hasRemaining());
                            buffer.clear();
                        }
                    }
                    charIndex = 0;
                    if (! valueIterator.hasNext()) {
                        if (! buffer.hasRemaining()) {
                            buffer.flip();
                            do {
                                res = delegate.write(buffer);
                                if (res == 0) {
                                    log.trace("Continuation");
                                    return STATE_HDR_EOL_CR;
                                }
                            } while (buffer.hasRemaining());
                            buffer.clear();
                        }
                        buffer.put((byte) 13); // CR
                        if (! buffer.hasRemaining()) {
                            buffer.flip();
                            do {
                                res = delegate.write(buffer);
                                if (res == 0) {
                                    log.trace("Continuation");
                                    return STATE_HDR_EOL_LF;
                                }
                            } while (buffer.hasRemaining());
                            buffer.clear();
                        }
                        buffer.put((byte) 10); // LF
                        if (nameIterator.hasNext()) {
                            headerName = nameIterator.next();
                            valueIterator = null;
                            state = STATE_HDR_NAME;
                            break;
                        } else {
                            if (! buffer.hasRemaining()) {
                                buffer.flip();
                                do {
                                    res = delegate.write(buffer);
                                    if (res == 0) {
                                        log.trace("Continuation");
                                        return STATE_HDR_FINAL_CR;
                                    }
                                } while (buffer.hasRemaining());
                                buffer.clear();
                            }
                            buffer.put((byte) 13); // CR
                            if (! buffer.hasRemaining()) {
                                buffer.flip();
                                do {
                                    res = delegate.write(buffer);
                                    if (res == 0) {
                                        log.trace("Continuation");
                                        return STATE_HDR_FINAL_LF;
                                    }
                                } while (buffer.hasRemaining());
                                buffer.clear();
                            }
                            buffer.put((byte) 10); // LF
                            this.nameIterator = null;
                            this.valueIterator = null;
                            this.string = null;
                            buffer.flip();
                            do {
                                res = delegate.write(buffer);
                                if (res == 0) {
                                    log.trace("Continuation");
                                    return STATE_BUF_FLUSH;
                                }
                            } while (buffer.hasRemaining());
                            pooledBuffer.free();
                            pooledBuffer = null;
                            log.trace("Body");
                            return STATE_BODY;
                        }
                        // not reached
                    }
                    // fall thru
                }
                // Clean-up states
                case STATE_HDR_EOL_CR: {
                    if (! buffer.hasRemaining()) {
                        buffer.flip();
                        do {
                            res = delegate.write(buffer);
                            if (res == 0) {
                                log.trace("Continuation");
                                return STATE_HDR_EOL_CR;
                            }
                        } while (buffer.hasRemaining());
                        buffer.clear();
                    }
                    buffer.put((byte) 13); // CR
                }
                case STATE_HDR_EOL_LF: {
                    if (! buffer.hasRemaining()) {
                        buffer.flip();
                        do {
                            res = delegate.write(buffer);
                            if (res == 0) {
                                log.trace("Continuation");
                                return STATE_HDR_EOL_LF;
                            }
                        } while (buffer.hasRemaining());
                        buffer.clear();
                    }
                    buffer.put((byte) 10); // LF
                    if(valueIterator.hasNext()) {
                        state = STATE_HDR_NAME;
                        break;
                    } else if (nameIterator.hasNext()) {
                        headerName = nameIterator.next();
                        valueIterator = null;
                        state = STATE_HDR_NAME;
                        break;
                    }
                    // fall thru
                }
                case STATE_HDR_FINAL_CR: {
                    if (! buffer.hasRemaining()) {
                        buffer.flip();
                        do {
                            res = delegate.write(buffer);
                            if (res == 0) {
                                log.trace("Continuation");
                                return STATE_HDR_FINAL_CR;
                            }
                        } while (buffer.hasRemaining());
                        buffer.clear();
                    }
                    buffer.put((byte) 13); // CR
                    // fall thru
                }
                case STATE_HDR_FINAL_LF: {
                    if (! buffer.hasRemaining()) {
                        buffer.flip();
                        do {
                            res = delegate.write(buffer);
                            if (res == 0) {
                                log.trace("Continuation");
                                return STATE_HDR_FINAL_LF;
                            }
                        } while (buffer.hasRemaining());
                        buffer.clear();
                    }
                    buffer.put((byte) 10); // LF
                    this.nameIterator = null;
                    this.valueIterator = null;
                    this.string = null;
                    buffer.flip();
                    //for performance reasons we use a gather write if there is user data
                    if(userData == null) {
                        do {
                            res = delegate.write(buffer);
                            if (res == 0) {
                                log.trace("Continuation");
                                return STATE_BUF_FLUSH;
                            }
                        } while (buffer.hasRemaining());
                    } else {
                        ByteBuffer[] b = {buffer, userData};
                        do {
                            long r = delegate.write(b);
                            if (r == 0) {
                                log.trace("Continuation");
                                return STATE_BUF_FLUSH;
                            }
                        } while (buffer.hasRemaining());
                    }
                    // fall thru
                }
                case STATE_BUF_FLUSH: {
                    // buffer was successfully flushed above
                    pooledBuffer.free();
                    pooledBuffer = null;
                    return STATE_BODY;
                }
                default: {
                    throw new IllegalStateException();
                }
            }
        }
    }

    public int write(final ByteBuffer src) throws IOException {
        log.trace("write");
        int oldState = this.state;
        int state = oldState & MASK_STATE;
        int alreadyWritten = 0;
        int originalRemaining = - 1;
        try {
            if (state != 0) {
                originalRemaining = src.remaining();
                state = processWrite(state, src);
                if (state != 0) {
                    return 0;
                }
                alreadyWritten = originalRemaining - src.remaining();
                if (allAreSet(oldState, FLAG_SHUTDOWN)) {
                    delegate.shutdownWrites();
                    throw new ClosedChannelException();
                }
            }
            if(alreadyWritten != originalRemaining) {
                return delegate.write(src) + alreadyWritten;
            }
            return alreadyWritten;
        } finally {
            this.state = oldState & ~MASK_STATE | state;
        }
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        log.trace("write");
        if (length == 0) {
            return 0L;
        }
        int oldVal = state;
        int state = oldVal & MASK_STATE;
        try {
            if (state != 0) {
                //todo: use gathering write here
                state = processWrite(state, null);
                if (state != 0) {
                    return 0;
                }
                if (allAreSet(oldVal, FLAG_SHUTDOWN)) {
                    delegate.shutdownWrites();
                    throw new ClosedChannelException();
                }
            }
            return length == 1 ? delegate.write(srcs[offset]) : delegate.write(srcs, offset, length);
        } finally {
            this.state = oldVal & ~MASK_STATE | state;
        }
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        log.trace("transfer");
        if (count == 0L) {
            return 0L;
        }
        int oldVal = state;
        int state = oldVal & MASK_STATE;
        try {
            if (state != 0) {
                state = processWrite(state, null);
                if (state != 0) {
                    return 0;
                }
                if (allAreSet(oldVal, FLAG_SHUTDOWN)) {
                    delegate.shutdownWrites();
                    throw new ClosedChannelException();
                }
            }
            return delegate.transferFrom(src, position, count);
        } finally {
            this.state = oldVal & ~MASK_STATE | state;
        }
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        log.trace("transfer");
        if (count == 0) {
            throughBuffer.clear().limit(0);
            return 0L;
        }
        int oldVal = state;
        int state = oldVal & MASK_STATE;
        try {
            if (state != 0) {
                state = processWrite(state, null);
                if (state != 0) {
                    return 0;
                }
                if (allAreSet(oldVal, FLAG_SHUTDOWN)) {
                    delegate.shutdownWrites();
                    throw new ClosedChannelException();
                }
            }
            return delegate.transferFrom(source, count, throughBuffer);
        } finally {
            this.state = oldVal & ~MASK_STATE | state;
        }
    }

    public boolean flush() throws IOException {
        log.trace("flush");
        int oldVal = state;
        int state = oldVal & MASK_STATE;
        try {
            if (state != 0) {
                state = processWrite(state, null);
                if (state != 0) {
                    log.trace("Flush false because headers aren't written yet");
                    return false;
                }
                if (allAreSet(oldVal, FLAG_SHUTDOWN)) {
                    delegate.shutdownWrites();
                    // fall out to the flush
                }
            }
            log.trace("Delegating flush");
            return delegate.flush();
        } finally {
            this.state = oldVal & ~MASK_STATE | state;
        }
    }

    public void suspendWrites() {
        log.trace("suspend");
        delegate.suspendWrites();
    }

    public void resumeWrites() {
        log.trace("resume");
        delegate.resumeWrites();
    }

    public boolean isWriteResumed() {
        return delegate.isWriteResumed();
    }

    public void wakeupWrites() {
        log.trace("wakeup");
        delegate.wakeupWrites();
    }

    public void shutdownWrites() throws IOException {
        log.trace("shutdown");
        int oldVal = this.state;
        if (allAreClear(oldVal, MASK_STATE)) {
            delegate.shutdownWrites();
            return;
        }
        this.state = oldVal | FLAG_SHUTDOWN;
    }

    public void awaitWritable() throws IOException {
        delegate.awaitWritable();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        delegate.awaitWritable(time, timeUnit);
    }

    public boolean isOpen() {
        return delegate.isOpen();
    }

    public void close() throws IOException {
        log.trace("close");
        int oldVal = this.state;
        if (allAreClear(oldVal, MASK_STATE)) {
            try {
                delegate.close();
            } finally {
                if (pooledBuffer != null) {
                    pooledBuffer.free();
                    pooledBuffer = null;
                }
            }
            return;
        }
        this.state = oldVal & ~MASK_STATE | FLAG_SHUTDOWN | STATE_BODY;
        // atomic close called but response not fully written
        // this blows out the connection completely, so nothing we can do but bail
        IoUtils.safeClose(delegate);
        throw new TruncatedResponseException();
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    public XnioExecutor getWriteThread() {
        return delegate.getWriteThread();
    }

    public boolean supportsOption(final Option<?> option) {
        return delegate.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return delegate.getOption(option);
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return delegate.setOption(option, value);
    }
}
