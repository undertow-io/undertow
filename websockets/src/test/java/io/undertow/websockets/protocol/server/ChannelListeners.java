/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2011 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

package io.undertow.websockets.protocol.server;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.jboss.logging.Logger;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.channels.SuspendableReadChannel;
import org.xnio.channels.SuspendableWriteChannel;
import org.xnio.channels.WritableMessageChannel;

/**
 * Channel listener utility methods.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings("unused")
public final class ChannelListeners {

    private static final ChannelListener<Channel> NULL_LISTENER = new ChannelListener<Channel>() {
        public void handleEvent(final Channel channel) {
        }

        public String toString() {
            return "Null channel listener";
        }
    };
    private static final ChannelListener.Setter<?> NULL_SETTER = new ChannelListener.Setter<Channel>() {
        public void set(final ChannelListener<? super Channel> channelListener) {
        }

        public String toString() {
            return "Null channel listener setter";
        }
    };
    private static final Logger listenerLog = Logger.getLogger("org.xnio.listener");
    private static ChannelListener<Channel> CLOSING_CHANNEL_LISTENER = new ChannelListener<Channel>() {
        public void handleEvent(final Channel channel) {
            IoUtils.safeClose(channel);
        }

        public String toString() {
            return "Closing channel listener";
        }
    };

    private ChannelListeners() {
    }

    /**
     * Invoke a channel listener on a given channel, logging any errors.
     *
     * @param channel the channel
     * @param channelListener the channel listener
     * @param <T> the channel type
     * @return {@code true} if the listener completed successfully, or {@code false} if it failed
     */
    public static <T extends Channel> boolean invokeChannelListener(T channel, ChannelListener<? super T> channelListener) {
        if (channelListener != null) try {
            listenerLog.tracef("Invoking listener %s on channel %s", channelListener, channel);
            channelListener.handleEvent(channel);
        } catch (Throwable t) {
            listenerLog.error("A channel event listener threw an exception", t);
            return false;
        }
        return true;
    }

    /**
     * Invoke a channel listener on a given channel, logging any errors, using the given executor.
     *
     * @param executor the executor
     * @param channel the channel
     * @param channelListener the channel listener
     * @param <T> the channel type
     */
    public static <T extends Channel> void invokeChannelListener(Executor executor, T channel, ChannelListener<? super T> channelListener) {
        try {
            executor.execute(getChannelListenerTask(channel, channelListener));
        } catch (RejectedExecutionException ree) {
            invokeChannelListener(channel, channelListener);
        }
    }

    /**
     * Safely invoke a channel exception handler, logging any errors.
     *
     * @param channel the channel
     * @param exceptionHandler the exception handler
     * @param exception the exception to pass in
     * @param <T> the exception type
     */
    public static <T extends Channel> void invokeChannelExceptionHandler(final T channel, final ChannelExceptionHandler<? super T> exceptionHandler, final IOException exception) {
        try {
            exceptionHandler.handleException(channel, exception);
        } catch (Throwable t) {
            listenerLog.error("A channel exception handler threw an exception", t);
        }
    }

    /**
     * Get a task which invokes the given channel listener on the given channel.
     *
     * @param channel the channel
     * @param channelListener the channel listener
     * @param <T> the channel type
     * @return the runnable task
     */
    public static <T extends Channel> Runnable getChannelListenerTask(final T channel, final ChannelListener<? super T> channelListener) {
        return new Runnable() {
            public String toString() {
                return "Channel listener task for " + channel + " -> " + channelListener;
            }

            public void run() {
                invokeChannelListener(channel, channelListener);
            }
        };
    }

    /**
     * Get a task which invokes the given channel listener on the given channel via its setter.
     *
     * @param channel the channel
     * @param setter the setter for the channel listener
     * @param <T> the channel type
     * @return the runnable task
     */
    public static <T extends Channel> Runnable getChannelListenerTask(final T channel, final ChannelListener.SimpleSetter<T> setter) {
        return new Runnable() {
            public String toString() {
                return "Channel listener task for " + channel + " -> " + setter;
            }

            public void run() {
                invokeChannelListener(channel, setter.get());
            }
        };
    }

    /**
     * Get a channel listener which closes the channel when notified.
     *
     * @return the channel listener
     */
    public static ChannelListener<Channel> closingChannelListener() {
        return CLOSING_CHANNEL_LISTENER;
    }

    /**
     * Get a channel listener which closes the given resource when notified.
     *
     * @param resource the resource to close
     * @return the channel listener
     */
    public static ChannelListener<Channel> closingChannelListener(final Closeable resource) {
        return new ChannelListener<Channel>() {
            public void handleEvent(final Channel channel) {
                IoUtils.safeClose(resource);
            }

            public String toString() {
                return "Closing channel listener for " + resource;
            }
        };
    }

    /**
     * Get a channel listener which closes the given resources when notified.
     *
     * @param resources the resources to close
     * @return the channel listener
     */
    public static ChannelListener<Channel> closingChannelListener(final Closeable... resources) {
        return new ChannelListener<Channel>() {
            public void handleEvent(final Channel channel) {
                IoUtils.safeClose(resources);
            }

            public String toString() {
                return "Closing channel listener for " + resources.length + " items";
            }
        };
    }

    /**
     * Get a channel listener which closes the given resource when notified.
     *
     * @param delegate the listener to call next
     * @param resource the resource to close
     * @return the channel listener
     */
    public static <T extends Channel> ChannelListener<T> closingChannelListener(final ChannelListener<T> delegate, final Closeable resource) {
        return new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                IoUtils.safeClose(resource);
                delegate.handleEvent(channel);
            }

            public String toString() {
                return "Closing channel listener for " + resource + " -> " + delegate;
            }
        };
    }

    /**
     * Get a channel listener which closes the given resource when notified.
     *
     * @param delegate the listener to call next
     * @param resources the resource to close
     * @return the channel listener
     */
    public static <T extends Channel> ChannelListener<T> closingChannelListener(final ChannelListener<T> delegate, final Closeable... resources) {
        return new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                IoUtils.safeClose(resources);
                delegate.handleEvent(channel);
            }

            public String toString() {
                return "Closing channel listener for " + resources.length + " items -> " + delegate;
            }
        };
    }

    /**
     * Get a channel listener which does nothing.
     *
     * @return the null channel listener
     */
    public static ChannelListener<Channel> nullChannelListener() {
        return NULL_LISTENER;
    }

    /**
     * Get a channel exception handler which closes the channel upon exception.
     *
     * @return the channel exception handler
     */
    public static ChannelExceptionHandler<Channel> closingChannelExceptionHandler() {
        return CLOSING_HANDLER;
    }

    /**
     * Create an open listener adapter which automatically accepts connections and invokes an open listener.
     *
     * @param openListener the channel open listener
     * @param <C> the connected channel type
     * @return a channel accept listener
     */
    public static <C extends ConnectedChannel> ChannelListener<AcceptingChannel<C>> openListenerAdapter(final ChannelListener<? super C> openListener) {
        if (openListener == null) {
            throw new IllegalArgumentException("openListener is null");
        }
        return new ChannelListener<AcceptingChannel<C>>() {
            public void handleEvent(final AcceptingChannel<C> channel) {
                try {
                    final C accepted = channel.accept();
                    if (accepted != null) {
                        invokeChannelListener(accepted, openListener);
                    }
                } catch (IOException e) {
                    listenerLog.errorf("Failed to accept a connection on %s: %s", channel, e);
                }
            }

            public String toString() {
                return "Accepting listener for " + openListener;
            }
        };
    }

    /**
     * Get a setter based on an atomic reference field updater.  Used by channel implementations to avoid having to
     * define an anonymous class for each listener field.
     *
     * @param channel the channel
     * @param updater the updater
     * @param <T> the channel type
     * @param <C> the holding class
     * @return the setter
     * @deprecated Not recommended as a security manager will enforce unreasonable restrictions on the updater.
     */
    @Deprecated
    public static <T extends Channel, C> ChannelListener.Setter<T> getSetter(final C channel, final AtomicReferenceFieldUpdater<C, ChannelListener> updater) {
        return new ChannelListener.Setter<T>() {
            public void set(final ChannelListener<? super T> channelListener) {
                updater.set(channel, channelListener);
            }

            public String toString() {
                return "Atomic reference field updater setter for " + updater;
            }
        };
    }

    /**
     * Get a setter based on an atomic reference.  Used by channel implementations to avoid having to
     * define an anonymous class for each listener field.
     *
     * @param atomicReference the atomic reference
     * @param <T> the channel type
     * @return the setter
     */
    public static <T extends Channel> ChannelListener.Setter<T> getSetter(final AtomicReference<ChannelListener<? super T>> atomicReference) {
        return new ChannelListener.Setter<T>() {
            public void set(final ChannelListener<? super T> channelListener) {
                atomicReference.set(channelListener);
            }

            public String toString() {
                return "Atomic reference setter (currently=" + atomicReference.get() + ")";
            }
        };
    }

    /**
     * Get a channel listener setter which delegates to the given target setter with a different channel type.
     *
     * @param target the target setter
     * @param realChannel the channel to send in to the listener
     * @param <T> the real channel type
     * @return the delegating setter
     */
    public static <T extends Channel> ChannelListener.Setter<T> getDelegatingSetter(final ChannelListener.Setter<? extends Channel> target, final T realChannel) {
        return target == null ? null : new DelegatingSetter<T>(target, realChannel);
    }

    /**
     * Get a channel listener setter which does nothing.
     *
     * @param <T> the channel type
     * @return a setter which does nothing
     */
    @SuppressWarnings({ "unchecked" })
    public static <T extends Channel> ChannelListener.Setter<T> nullSetter() {
        return (ChannelListener.Setter<T>) NULL_SETTER;
    }

    /**
     * Get a channel listener which executes a delegate channel listener via an executor.  If an exception occurs
     * submitting the task, the associated channel is closed.
     *
     * @param listener the listener to invoke
     * @param executor the executor with which to invoke the listener
     * @param <T> the channel type
     * @return a delegating channel listener
     */
    public static <T extends Channel> ChannelListener<T> executorChannelListener(final ChannelListener<T> listener, final Executor executor) {
        return new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                try {
                    executor.execute(getChannelListenerTask(channel, listener));
                } catch (RejectedExecutionException e) {
                    listenerLog.errorf("Failed to submit task to executor: %s (closing %s)", e, channel);
                    IoUtils.safeClose(channel);
                }
            }

            public String toString() {
                return "Executor channel listener -> " + listener;
            }
        };
    }

    /**
     * A flushing channel listener.  Flushes the channel and then calls the delegate listener.  Calls the exception
     * handler if an exception occurs.  The delegate listener should ensure that the channel write listener is appropriately set.
     * <p>
     * The returned listener is stateless and may be reused on any number of channels concurrently or sequentially.
     *
     * @param delegate the delegate listener
     * @param exceptionHandler the exception handler
     * @param <T> the channel type
     * @return the flushing channel listener
     */
    public static <T extends SuspendableWriteChannel> ChannelListener<T> flushingChannelListener(final ChannelListener<? super T> delegate, final ChannelExceptionHandler<? super T> exceptionHandler) {
        return new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                final boolean result;
                try {
                    result = channel.flush();
                } catch (IOException e) {
                    channel.suspendWrites();
                    invokeChannelExceptionHandler(channel, exceptionHandler, e);
                    return;
                }
                if (result) {
                    Channels.setWriteListener(channel, delegate);
                    invokeChannelListener(channel, delegate);
                } else {
                    Channels.setWriteListener(channel, this);
                    channel.resumeWrites();
                }
            }

            public String toString() {
                return "Flushing channel listener -> " + delegate;
            }
        };
    }

    /**
     * A write shutdown channel listener.  Shuts down and flushes the channel and calls the delegate listener.  Calls
     * the exception handler if an exception occurs.  When the delegate listener is called, the channel's write side will be shut down and flushed.
     * The delegate listener should ensure that the channel write listener is appropriately set.
     *
     * @param delegate the delegate listener
     * @param exceptionHandler the exception handler
     * @param <T> the channel type
     * @return the channel listener
     */
    public static <T extends SuspendableWriteChannel> ChannelListener<T> writeShutdownChannelListener(final ChannelListener<? super T> delegate, final ChannelExceptionHandler<? super T> exceptionHandler) {
        final ChannelListener<T> flushingListener = flushingChannelListener(delegate, exceptionHandler);
        return new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                try {
                    channel.shutdownWrites();
                } catch (IOException e) {
                    invokeChannelExceptionHandler(channel, exceptionHandler, e);
                    return;
                }
                invokeChannelListener(channel, flushingListener);
            }

            public String toString() {
                return "Write shutdown channel listener -> " + delegate;
            }
        };
    }

    /**
     * A writing channel listener.  Writes the buffer to the channel and then calls the delegate listener.  Calls the exception
     * handler if an exception occurs.  The delegate listener should ensure that the channel write listener is appropriately set.
     * <p>
     * The returned listener is stateful and will not execute properly if reused.
     *
     * @param pooled the buffer to write
     * @param delegate the delegate listener
     * @param exceptionHandler the exception handler
     * @param <T> the channel type
     * @return the writing channel listener
     */
    public static <T extends StreamSinkChannel> ChannelListener<T> writingChannelListener(final Pooled<ByteBuffer> pooled, final ChannelListener<? super T> delegate, final ChannelExceptionHandler<? super T> exceptionHandler) {
        return new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                final ByteBuffer buffer = pooled.getResource();
                int result;
                boolean ok = false;
                do {
                    try {
                        result = channel.write(buffer);
                        ok = true;
                    } catch (IOException e) {
                        channel.suspendWrites();
                        pooled.free();
                        invokeChannelExceptionHandler(channel, exceptionHandler, e);
                        return;
                    } finally {
                        if (! ok) {
                            pooled.free();
                        }
                    }
                    if (result == 0) {
                        Channels.setWriteListener(channel, this);
                        channel.resumeWrites();
                        return;
                    }
                } while (buffer.hasRemaining());
                pooled.free();
                invokeChannelListener(channel, delegate);
            }

            public String toString() {
                return "Writing channel listener -> " + delegate;
            }
        };
    }

    /**
     * A sending channel listener.  Writes the buffer to the channel and then calls the delegate listener.  Calls the exception
     * handler if an exception occurs.  The delegate listener should ensure that the channel write listener is appropriately set.
     * <p>
     * The returned listener is stateful and will not execute properly if reused.
     *
     * @param pooled the buffer to send
     * @param delegate the delegate listener
     * @param exceptionHandler the exception handler
     * @param <T> the channel type
     * @return the sending channel listener
     */
    public static <T extends WritableMessageChannel> ChannelListener<T> sendingChannelListener(final Pooled<ByteBuffer> pooled, final ChannelListener<? super T> delegate, final ChannelExceptionHandler<? super T> exceptionHandler) {
        return new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                final ByteBuffer buffer = pooled.getResource();
                boolean free = true;
                try {
                    if (! (free = channel.send(buffer))) {
                        Channels.setWriteListener(channel, this);
                        channel.resumeWrites();
                        return;
                    }
                } catch (IOException e) {
                    channel.suspendWrites();
                    pooled.free();
                    invokeChannelExceptionHandler(channel, exceptionHandler, e);
                    return;
                } finally {
                    if (free) pooled.free();
                }
                invokeChannelListener(channel, delegate);
            }

            public String toString() {
                return "Sending channel listener -> " + delegate;
            }
        };
    }

    /**
     * A file-sending channel listener.  Writes the file to the channel and then calls the delegate listener.  Calls the exception
     * handler if an exception occurs.  The delegate listener should ensure that the channel write listener is appropriately set.
     * <p>
     * The returned listener is stateful and will not execute properly if reused.
     *
     * @param source the file to read from
     * @param position the position in the source file to read from
     * @param count the number of bytes to read
     * @param delegate the listener to call when the file is sent
     * @param exceptionHandler the exception handler to call if a problem occurs
     * @param <T> the channel type
     * @return the channel listener
     */
    public static <T extends StreamSinkChannel> ChannelListener<T> fileSendingChannelListener(final FileChannel source, final long position, final long count, final ChannelListener<? super T> delegate, final ChannelExceptionHandler<? super T> exceptionHandler) {
        if (count == 0L) {
            return delegatingChannelListener(delegate);
        }
        return new ChannelListener<T>() {
            private long p = position;
            private long cnt = count;

            public void handleEvent(final T channel) {
                long result;
                long cnt = this.cnt;
                long p = this.p;
                try {
                    do {
                        try {
                            result = channel.transferFrom(source, p, cnt);
                        } catch (IOException e) {
                            invokeChannelExceptionHandler(channel, exceptionHandler, e);
                            return;
                        }
                        if (result == 0L) {
                            Channels.setWriteListener(channel, this);
                            channel.resumeWrites();
                            return;
                        }
                        p += result;
                        cnt -= result;
                    } while (cnt > 0L);
                    // cnt is 0
                    invokeChannelListener(channel, delegate);
                    return;
                } finally {
                    this.p = p;
                    this.cnt = cnt;
                }
            }

            public String toString() {
                return "File sending channel listener (" + source + ") -> " + delegate;
            }
        };
    }

    /**
     * A file-receiving channel listener.  Writes the file from the channel and then calls the delegate listener.  Calls the exception
     * handler if an exception occurs.  The delegate listener should ensure that the channel read listener is appropriately set.
     * <p>
     * The returned listener is stateful and will not execute properly if reused.
     *
     * @param target the file to write to
     * @param position the position in the target file to write to
     * @param count the number of bytes to write
     * @param delegate the listener to call when the file is sent
     * @param exceptionHandler the exception handler to call if a problem occurs
     * @param <T> the channel type
     * @return the channel listener
     */
    public static <T extends StreamSourceChannel> ChannelListener<T> fileReceivingChannelListener(final FileChannel target, final long position, final long count, final ChannelListener<? super T> delegate, final ChannelExceptionHandler<? super T> exceptionHandler) {
        if (count == 0L) {
            return delegatingChannelListener(delegate);
        }
        return new ChannelListener<T>() {
            private long p = position;
            private long cnt = count;

            public void handleEvent(final T channel) {
                long result;
                long cnt = this.cnt;
                long p = this.p;
                try {
                    do {
                        try {
                            result = channel.transferTo(p, cnt, target);
                        } catch (IOException e) {
                            invokeChannelExceptionHandler(channel, exceptionHandler, e);
                            return;
                        }
                        if (result == 0L) {
                            Channels.setReadListener(channel, this);
                            channel.resumeReads();
                            return;
                        }
                        p += result;
                        cnt -= result;
                    } while (cnt > 0L);
                    // cnt = 0
                    invokeChannelListener(channel, delegate);
                    return;
                } finally {
                    this.p = p;
                    this.cnt = cnt;
                }
            }

            public String toString() {
                return "File receiving channel listener (" + target + ") -> " + delegate;
            }
        };
    }

    /**
     * A delegating channel listener which passes an event to another listener of the same or a super type.
     *
     * @param delegate the delegate channel listener
     * @param <T> the channel type
     * @return the listener
     */
    public static <T extends Channel> ChannelListener<T> delegatingChannelListener(final ChannelListener<? super T> delegate) {
        return new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                invokeChannelListener(channel, delegate);
            }

            public String toString() {
                return "Delegating channel listener -> " + delegate;
            }
        };
    }

    /**
     * A delegating channel listener which passes an event to the listener stored in the given setter.
     *
     * @param channel the channel to pass in
     * @param setter the channel listener setter
     * @param <C> the listener channel type
     * @param <T> the passed in channel type
     * @return the listener
     */
    public static <C extends Channel, T extends Channel> ChannelListener<C> delegatingChannelListener(final T channel, final ChannelListener.SimpleSetter<T> setter) {
        return new SetterDelegatingListener<C, T>(setter, channel);
    }

    /**
     * A write-suspending channel listener.  The returned listener will suspend writes when called.  Useful for chaining
     * writing listeners to a flush listener to this listener. The delegate listener should ensure that the channel write listener is appropriately set.
     *
     * @param delegate the delegate channel listener
     * @return the suspending channel listener
     */
    public static <T extends SuspendableWriteChannel> ChannelListener<T> writeSuspendingChannelListener(final ChannelListener<? super T> delegate) {
        return new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                channel.suspendWrites();
                invokeChannelListener(channel, delegate);
            }

            public String toString() {
                return "Write-suspending channel listener -> " + delegate;
            }
        };
    }

    /**
     * A read-suspending channel listener.  The returned listener will suspend read when called.
     * The delegate listener should ensure that the channel read listener is appropriately set.
     *
     * @param delegate the delegate channel listener
     * @return the suspending channel listener
     */
    public static <T extends SuspendableReadChannel> ChannelListener<T> readSuspendingChannelListener(final ChannelListener<? super T> delegate) {
        return new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                channel.suspendReads();
                invokeChannelListener(channel, delegate);
            }

            public String toString() {
                return "Read-suspending channel listener -> " + delegate;
            }
        };
    }

    static final class TransferListener<I extends StreamSourceChannel, O extends StreamSinkChannel> implements ChannelListener<Channel> {
        private final Pooled<ByteBuffer> pooledBuffer;
        private final I source;
        private final O sink;
        private final ChannelListener<? super I> sourceListener;
        private final ChannelListener<? super O> sinkListener;
        private final ChannelExceptionHandler<? super O> writeExceptionHandler;
        private final ChannelExceptionHandler<? super I> readExceptionHandler;
        private long count;
        private volatile int state;

        TransferListener(final long count, final Pooled<ByteBuffer> pooledBuffer, final I source, final O sink, final ChannelListener<? super I> sourceListener, final ChannelListener<? super O> sinkListener, final ChannelExceptionHandler<? super O> writeExceptionHandler, final ChannelExceptionHandler<? super I> readExceptionHandler, final int state) {
            this.count = count;
            this.pooledBuffer = pooledBuffer;
            this.source = source;
            this.sink = sink;
            this.sourceListener = sourceListener;
            this.sinkListener = sinkListener;
            this.writeExceptionHandler = writeExceptionHandler;
            this.readExceptionHandler = readExceptionHandler;
            this.state = state;
        }

        public void handleEvent(final Channel channel) {
            final ByteBuffer buffer = pooledBuffer.getResource();
            int state = this.state;
            // always read after and write before state
            long count = this.count;
            long lres;
            int ires;
            System.out.println(state);

            switch (state) {
                case 0: {
                    // read listener
                    for (;;) {
                        try {
                            lres = source.transferTo(count, buffer, sink);
                        } catch (IOException e) {
                            readFailed(e);
                            return;
                        }
                        if (lres == 0) {
                            this.count = count;
                            return;
                        }
                        if (lres == -1) {
                            // possibly unexpected EOF
                            if (count == Long.MAX_VALUE) {
                                // it's OK; just be done
                                done();
                                return;
                            } else {
                                readFailed(new EOFException());
                                return;
                            }
                        }
                        if (count != Long.MAX_VALUE) {
                            count -= lres;
                        }
                        while (buffer.hasRemaining()) {
                            try {
                                ires = sink.write(buffer);
                            } catch (IOException e) {
                                writeFailed(e);
                                return;
                            }
                            if (ires == 0) {
                                this.count = count;
                                this.state = 1;
                                source.suspendReads();
                                sink.resumeWrites();
                                return;
                            }
                        }
                    }
                }
                case 1: {
                    // write listener
                    for (;;) {
                        while (buffer.hasRemaining()) {
                            try {
                                ires = sink.write(buffer);
                            } catch (IOException e) {
                                writeFailed(e);
                                return;
                            }
                            if (ires == 0) {
                                return;
                            }
                        }

                        try {
                            lres = source.transferTo(count, buffer, sink);
                        } catch (IOException e) {
                            readFailed(e);
                            return;
                        }
                        if (lres == 0) {
                            System.out.println("YES");
                            this.count = count;
                            this.state = 0;
                            sink.suspendWrites();
                            source.resumeReads();
                            return;
                        }
                        if (lres == -1) {
                            // possibly unexpected EOF
                            if (count == Long.MAX_VALUE) {
                                // it's OK; just be done
                                done();
                                return;
                            } else {
                                readFailed(new EOFException());
                                return;
                            }
                        }
                        System.out.println("NOTHING");

                        if (count != Long.MAX_VALUE) {
                            count -= lres;
                        }
                    }
                }
            }
        }

        private void writeFailed(final IOException e) {
            try {
                source.suspendReads();
                sink.suspendWrites();
                invokeChannelExceptionHandler(sink, writeExceptionHandler, e);
            } finally {
                pooledBuffer.free();
            }
        }

        private void readFailed(final IOException e) {
            try {
                source.suspendReads();
                sink.suspendWrites();
                invokeChannelExceptionHandler(source, readExceptionHandler, e);
            } finally {
                pooledBuffer.free();
            }
        }

        private void done() {
            try {
                final ChannelListener<? super I> sourceListener = this.sourceListener;
                final ChannelListener<? super O> sinkListener = this.sinkListener;
                final I source = this.source;
                final O sink = this.sink;

                Channels.setReadListener(source, sourceListener);
                if (sourceListener == null) {
                    source.suspendReads();
                } else {
                    source.wakeupReads();
                }

                Channels.setWriteListener(sink, sinkListener);
                if (sinkListener == null) {
                    sink.suspendWrites();
                } else {
                    sink.wakeupWrites();
                }
            } finally {
                pooledBuffer.free();
            }
        }

        public String toString() {
            return "Transfer channel listener (" + source + " to " + sink + ") -> (" + sourceListener + " and " + sinkListener + ")";
        }
    }

    /**
     * Initiate a low-copy transfer between two stream channels.  The pool should be a direct buffer pool for best
     * performance.  The channels will be closed when the transfer completes or if there is an error.
     *
     * @param source the source channel
     * @param sink the target channel
     * @param pool the pool from which the transfer buffer should be allocated
     * @param <I> the source stream type
     * @param <O> the sink stream type
     */
    public static <I extends StreamSourceChannel, O extends StreamSinkChannel> void initiateTransfer(final I source, final O sink, Pool<ByteBuffer> pool) {
        initiateTransfer(Long.MAX_VALUE, source, sink, CLOSING_CHANNEL_LISTENER, CLOSING_CHANNEL_LISTENER, CLOSING_HANDLER, CLOSING_HANDLER, pool);
    }

    /**
     * Initiate a low-copy transfer between two stream channels.  The pool should be a direct buffer pool for best
     * performance.
     *
     * @param count the number of bytes to transfer, or {@link Long#MAX_VALUE} to transfer all remaining bytes
     * @param source the source channel
     * @param sink the target channel
     * @param sourceListener the source listener to set and call when the transfer is complete, or {@code null} to clear the listener at that time
     * @param sinkListener the target listener to set and call when the transfer is complete, or {@code null} to clear the listener at that time
     * @param readExceptionHandler the read exception handler to call if an error occurs during a read operation
     * @param writeExceptionHandler the write exception handler to call if an error occurs during a write operation
     * @param pool the pool from which the transfer buffer should be allocated
     */
    public static <I extends StreamSourceChannel, O extends StreamSinkChannel> void initiateTransfer(long count, final I source, final O sink, final ChannelListener<? super I> sourceListener, final ChannelListener<? super O> sinkListener, final ChannelExceptionHandler<? super I> readExceptionHandler, final ChannelExceptionHandler<? super O> writeExceptionHandler, Pool<ByteBuffer> pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool is null");
        }
        final Pooled<ByteBuffer> allocated = pool.allocate();
        boolean free = true;
        try {
            final ByteBuffer buffer = allocated.getResource();
            long transferred;
            do {
                try {
                    transferred = source.transferTo(count, buffer, sink);
                } catch (IOException e) {
                    invokeChannelExceptionHandler(source, readExceptionHandler, e);
                    return;
                }
                if (transferred == -1) {
                    if (count == Long.MAX_VALUE) {
                        Channels.setReadListener(source, sourceListener);
                        if (sourceListener == null) {
                            source.suspendReads();
                        } else {
                            source.wakeupReads();
                        }

                        Channels.setWriteListener(sink, sinkListener);
                        if (sinkListener == null) {
                            sink.suspendWrites();
                        } else {
                            sink.wakeupWrites();
                        }
                    } else {
                        source.suspendReads();
                        sink.suspendWrites();
                        invokeChannelExceptionHandler(source, readExceptionHandler, new EOFException());
                    }
                    return;
                }
                if (count != Long.MAX_VALUE) {
                    count -= transferred;
                }
                while (buffer.hasRemaining()) {
                    final int res;
                    try {
                        res = sink.write(buffer);
                    } catch (IOException e) {
                        invokeChannelExceptionHandler(sink, writeExceptionHandler, e);
                        return;
                    }
                    if (res == 0) {
                        // write first listener
                        final TransferListener<I, O> listener = new TransferListener<I, O>(count, allocated, source, sink, sourceListener, sinkListener, writeExceptionHandler, readExceptionHandler, 1);
                        source.suspendReads();
                        source.getReadSetter().set(listener);
                        sink.getWriteSetter().set(listener);
                        sink.resumeWrites();
                        free = false;
                        return;
                    }
                }
            } while (transferred > 0L);
            // read first listener
            final TransferListener<I, O> listener = new TransferListener<I, O>(count, allocated, source, sink, sourceListener, sinkListener, writeExceptionHandler, readExceptionHandler, 0);
            sink.suspendWrites();
            sink.getWriteSetter().set(listener);
            source.getReadSetter().set(listener);
            source.resumeReads();
            free = false;
            return;
        } finally {
            if (free) allocated.free();
        }
    }

    /**
     * Create a channel listener which automatically drains the given number of bytes from the channel and then calls
     * a listener.
     *
     * @param bytes the number of bytes to drain, or {@code Long.MAX_VALUE} to drain the channel completely
     * @param finishListener the listener to call when the drain is complete
     * @param exceptionHandler the handler to call if the drain fails
     * @param <T> the channel type
     * @return the channel listener
     */
    public static <T extends StreamSourceChannel> ChannelListener<T> drainListener(long bytes, ChannelListener<? super T> finishListener, ChannelExceptionHandler<? super T> exceptionHandler) {
        return new DrainListener<T>(finishListener, exceptionHandler, bytes);
    }

    private static class DelegatingSetter<T extends Channel> implements ChannelListener.Setter<T> {
        private final ChannelListener.Setter<? extends Channel> setter;
        private final T realChannel;

        DelegatingSetter(final ChannelListener.Setter<? extends Channel> setter, final T realChannel) {
            this.setter = setter;
            this.realChannel = realChannel;
        }

        public void set(final ChannelListener<? super T> channelListener) {
            setter.set(channelListener == null ? null : new DelegatingChannelListener<T>(channelListener, realChannel));
        }

        public String toString() {
            return "Delegating setter -> " + setter;
        }
    }

    private static class DelegatingChannelListener<T extends Channel> implements ChannelListener<Channel> {

        private final ChannelListener<? super T> channelListener;
        private final T realChannel;

        public DelegatingChannelListener(final ChannelListener<? super T> channelListener, final T realChannel) {
            this.channelListener = channelListener;
            this.realChannel = realChannel;
        }

        public void handleEvent(final Channel channel) {
            invokeChannelListener(realChannel, channelListener);
        }

        public String toString() {
            return "Delegating channel listener -> " + channelListener;
        }
    }

    private static class SetterDelegatingListener<C extends Channel, T extends Channel> implements ChannelListener<C> {

        private final SimpleSetter<T> setter;
        private final T channel;

        public SetterDelegatingListener(final SimpleSetter<T> setter, final T channel) {
            this.setter = setter;
            this.channel = channel;
        }

        public void handleEvent(final C channel) {
            invokeChannelListener(this.channel, setter.get());
        }

        public String toString() {
            return "Setter delegating channel listener -> " + setter;
        }
    }

    private static final ChannelExceptionHandler<Channel> CLOSING_HANDLER = new ChannelExceptionHandler<Channel>() {
        public void handleException(final Channel channel, final IOException exception) {
            IoUtils.safeClose(channel);
        }
    };

    private static class DrainListener<T extends StreamSourceChannel> implements ChannelListener<T> {
        private final ChannelListener<? super T> finishListener;
        private final ChannelExceptionHandler<? super T> exceptionHandler;
        private long count;

        private DrainListener(final ChannelListener<? super T> finishListener, final ChannelExceptionHandler<? super T> exceptionHandler, final long count) {
            this.finishListener = finishListener;
            this.exceptionHandler = exceptionHandler;
            this.count = count;
        }

        public void handleEvent(final T channel) {
            try {
                long count = this.count;
                try {
                    long res;
                    for (;;) {
                        res = Channels.drain(channel, count);
                        if (res == -1 || res == count) {
                            this.count = 0L;
                            invokeChannelListener(channel, finishListener);
                            return;
                        } else if (res == 0) {
                            return;
                        } else if (count < Long.MAX_VALUE) {
                            // MAX_VALUE means drain to EOF
                            count -= res;
                        }
                    }
                } finally {
                    this.count = count;
                }
            } catch (IOException e) {
                this.count = 0L;
                if (exceptionHandler != null) {
                    invokeChannelExceptionHandler(channel, exceptionHandler, e);
                } else {
                    IoUtils.safeShutdownReads(channel);
                }
            }
        }

        public String toString() {
            return "Draining channel listener (" + count + " bytes) -> " + finishListener;
        }
    }
}
