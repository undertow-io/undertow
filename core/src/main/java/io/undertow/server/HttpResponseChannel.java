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

import io.undertow.util.HeaderMap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
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

import static org.xnio.Bits.*;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class HttpResponseChannel implements StreamSinkChannel {

    private static final Logger log = Logger.getLogger("io.undertow.server.channel.response");

    private final StreamSinkChannel delegate;
    private final Pool<ByteBuffer> pool;

    @SuppressWarnings("unused")
    private volatile int state = STATE_START;

    private Iterator<String> nameIterator;
    private String string;
    private Iterator<String> valueIterator;
    private int charIndex;
    private Pooled<ByteBuffer> pooledBuffer;
    private HttpServerExchange exchange;

    private final ChannelListener.SimpleSetter<HttpResponseChannel> writeSetter = new ChannelListener.SimpleSetter<HttpResponseChannel>();
    private final ChannelListener.SimpleSetter<HttpResponseChannel> closeSetter = new ChannelListener.SimpleSetter<HttpResponseChannel>();

    private static final AtomicIntegerFieldUpdater<HttpResponseChannel> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(HttpResponseChannel.class, "state");

    private static final int STATE_BODY = 0; // Message body, normal pass-through operation
    private static final int STATE_START = 1; // No headers written yet
    private static final int STATE_HDR_NAME = 2; // Header name indexed by charIndex
    private static final int STATE_HDR_D = 3; // Header delimiter ':'
    private static final int STATE_HDR_DS = 4; // Header delimiter ': '
    private static final int STATE_HDR_VAL = 5; // Header value
    private static final int STATE_HDR_VAL_D = 6; // Header value delimiter ','
    private static final int STATE_HDR_VAL_DS = 7; // Header value delimiter ', '
    private static final int STATE_HDR_EOL_CR = 8; // Header line CR
    private static final int STATE_HDR_EOL_LF = 9; // Header line LF
    private static final int STATE_HDR_FINAL_CR = 10; // Final CR
    private static final int STATE_HDR_FINAL_LF = 11; // Final LF
    private static final int STATE_BUF_FLUSH = 12; // flush the buffer and go to writing body

    private static final int MASK_STATE         = 0x0000000F;
    private static final int FLAG_ENTERED       = 0x00000010;
    private static final int FLAG_SHUTDOWN      = 0x00000020;

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

    private int processWrite(int state) throws IOException {
        if (state == STATE_START) {
            pooledBuffer = pool.allocate();
        }
        ByteBuffer buffer = pooledBuffer.getResource();
        Iterator<String> nameIterator = this.nameIterator;
        Iterator<String> valueIterator = this.valueIterator;
        int charIndex = this.charIndex;
        int length;
        String string = this.string;
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
                    string = exchange.getProtocol();
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
                    string = "Put Response String Here"; // <-- TODO
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
                    string = nameIterator.next();
                    charIndex = 0;
                    // fall thru
                }
                case STATE_HDR_NAME: {
                    log.tracef("Processing header '%s'", string);
                    length = string.length();
                    while (charIndex < length) {
                        if (buffer.hasRemaining()) {
                            buffer.put((byte) string.charAt(charIndex++));
                        } else {
                            log.trace("Buffer flush");
                            buffer.flip();
                            do {
                                res = delegate.write(buffer);
                                if (res == 0) {
                                    this.string = string;
                                    this.charIndex = charIndex;
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
                                return STATE_HDR_DS;
                            }
                        } while (buffer.hasRemaining());
                        buffer.clear();
                    }
                    buffer.put((byte) ' ');
                    valueIterator = exchange.getResponseHeaders().get(string).iterator();
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
                                    this.charIndex = charIndex;
                                    this.valueIterator = valueIterator;
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
                            string = nameIterator.next();
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
                case STATE_HDR_VAL_D: {
                    if (! buffer.hasRemaining()) {
                        buffer.flip();
                        do {
                            res = delegate.write(buffer);
                            if (res == 0) {
                                log.trace("Continuation");
                                return STATE_HDR_D;
                            }
                        } while (buffer.hasRemaining());
                        buffer.clear();
                    }
                    buffer.put((byte) ',');
                    // fall thru
                }
                case STATE_HDR_VAL_DS: {
                    if (! buffer.hasRemaining()) {
                        buffer.flip();
                        do {
                            res = delegate.write(buffer);
                            if (res == 0) {
                                log.trace("Continuation");
                                return STATE_HDR_DS;
                            }
                        } while (buffer.hasRemaining());
                        buffer.clear();
                    }
                    buffer.put((byte) ' ');
                    assert valueIterator.hasNext();
                    string = valueIterator.next();
                    state = STATE_HDR_VAL;
                    break;
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
                    if (nameIterator.hasNext()) {
                        string = nameIterator.next();
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
                    // fall thru
                }
                case STATE_BUF_FLUSH: {
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
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_ENTERED)) {
                throw new ConcurrentStreamChannelAccessException();
            }
            newVal = oldVal | FLAG_ENTERED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        int state = oldVal & MASK_STATE;
        try {
            if (state != 0) {
                state = processWrite(state);
                if (state != 0) {
                    return 0;
                }
                oldVal = newVal;
                newVal = oldVal & ~MASK_STATE | state;
                while (! stateUpdater.compareAndSet(this, oldVal, newVal)) {
                    oldVal = this.state;
                    newVal = oldVal & ~MASK_STATE | state;
                }
                if (allAreSet(oldVal, FLAG_SHUTDOWN)) {
                    delegate.shutdownWrites();
                    throw new ClosedChannelException();
                }
            }
            return delegate.write(src);
        } finally {
            oldVal = newVal;
            newVal = oldVal & ~FLAG_ENTERED & ~MASK_STATE | state;
            while (! stateUpdater.compareAndSet(this, oldVal, newVal)) {
                oldVal = this.state;
                newVal = oldVal & ~FLAG_ENTERED & ~MASK_STATE | state;
            }
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
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_ENTERED)) {
                throw new ConcurrentStreamChannelAccessException();
            }
            newVal = oldVal | FLAG_ENTERED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        int state = oldVal & MASK_STATE;
        try {
            if (state != 0) {
                state = processWrite(state);
                if (state != 0) {
                    return 0;
                }
                oldVal = newVal;
                newVal = oldVal & ~MASK_STATE | state;
                while (! stateUpdater.compareAndSet(this, oldVal, newVal)) {
                    oldVal = this.state;
                    newVal = oldVal & ~MASK_STATE | state;
                }
                if (allAreSet(oldVal, FLAG_SHUTDOWN)) {
                    delegate.shutdownWrites();
                    throw new ClosedChannelException();
                }
            }
            return length == 1 ? delegate.write(srcs[offset]) : delegate.write(srcs, offset, length);
        } finally {
            oldVal = newVal;
            newVal = oldVal & ~FLAG_ENTERED & ~MASK_STATE | state;
            while (! stateUpdater.compareAndSet(this, oldVal, newVal)) {
                oldVal = this.state;
                newVal = oldVal & ~FLAG_ENTERED & ~MASK_STATE | state;
            }
        }
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        log.trace("transfer");
        if (count == 0L) {
            return 0L;
        }
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_ENTERED)) {
                throw new ConcurrentStreamChannelAccessException();
            }
            newVal = oldVal | FLAG_ENTERED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        int state = oldVal & MASK_STATE;
        try {
            if (state != 0) {
                state = processWrite(state);
                if (state != 0) {
                    return 0;
                }
                oldVal = newVal;
                newVal = oldVal & ~MASK_STATE | state;
                while (! stateUpdater.compareAndSet(this, oldVal, newVal)) {
                    oldVal = this.state;
                    newVal = oldVal & ~MASK_STATE | state;
                }
                if (allAreSet(oldVal, FLAG_SHUTDOWN)) {
                    delegate.shutdownWrites();
                    throw new ClosedChannelException();
                }
            }
            return delegate.transferFrom(src, position, count);
        } finally {
            oldVal = newVal;
            newVal = oldVal & ~FLAG_ENTERED & ~MASK_STATE | state;
            while (! stateUpdater.compareAndSet(this, oldVal, newVal)) {
                oldVal = this.state;
                newVal = oldVal & ~FLAG_ENTERED & ~MASK_STATE | state;
            }
        }
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        log.trace("transfer");
        if (count == 0) {
            throughBuffer.clear().limit(0);
            return 0L;
        }
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_ENTERED)) {
                throw new ConcurrentStreamChannelAccessException();
            }
            newVal = oldVal | FLAG_ENTERED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        int state = oldVal & MASK_STATE;
        try {
            if (state != 0) {
                state = processWrite(state);
                if (state != 0) {
                    return 0;
                }
                oldVal = newVal;
                newVal = oldVal & ~MASK_STATE | state;
                while (! stateUpdater.compareAndSet(this, oldVal, newVal)) {
                    oldVal = this.state;
                    newVal = oldVal & ~MASK_STATE | state;
                }
                if (allAreSet(oldVal, FLAG_SHUTDOWN)) {
                    delegate.shutdownWrites();
                    throw new ClosedChannelException();
                }
            }
            return delegate.transferFrom(source, count, throughBuffer);
        } finally {
            oldVal = newVal;
            newVal = oldVal & ~FLAG_ENTERED & ~MASK_STATE | state;
            while (! stateUpdater.compareAndSet(this, oldVal, newVal)) {
                oldVal = this.state;
                newVal = oldVal & ~FLAG_ENTERED & ~MASK_STATE | state;
            }
        }
    }

    public boolean flush() throws IOException {
        log.trace("flush");
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_ENTERED)) {
                log.trace("Flush false due to reentry");
                return false;
            }
            newVal = oldVal | FLAG_ENTERED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        int state = oldVal & MASK_STATE;
        try {
            if (state != 0) {
                state = processWrite(state);
                if (state != 0) {
                    log.tracef("Flush false because headers aren't written yet (%d)", state);
                    return false;
                }
                oldVal = newVal;
                newVal = oldVal & ~MASK_STATE | state;
                while (! stateUpdater.compareAndSet(this, oldVal, newVal)) {
                    oldVal = this.state;
                    newVal = oldVal & ~MASK_STATE | state;
                }
                if (allAreSet(oldVal, FLAG_SHUTDOWN)) {
                    delegate.shutdownWrites();
                    // fall out to the flush
                }
            }
            log.trace("Delegating flush");
            return delegate.flush();
        } finally {
            oldVal = newVal;
            newVal = oldVal & ~FLAG_ENTERED & ~MASK_STATE | state;
            while (! stateUpdater.compareAndSet(this, oldVal, newVal)) {
                oldVal = this.state;
                newVal = oldVal & ~FLAG_ENTERED & ~MASK_STATE | state;
            }
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
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreClear(oldVal, MASK_STATE)) {
                delegate.shutdownWrites();
                return;
            }
            newVal = oldVal | FLAG_SHUTDOWN;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        // just return
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
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreClear(oldVal, MASK_STATE)) {
                delegate.close();
                return;
            }
            newVal = oldVal & ~MASK_STATE | FLAG_SHUTDOWN | STATE_BODY;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
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
