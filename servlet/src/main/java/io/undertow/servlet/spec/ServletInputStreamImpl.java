package io.undertow.servlet.spec;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.core.CompositeThreadSetupAction;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSourceChannel;

import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * Servlet input stream implementation. This stream is non-buffered, and is used for both
 * HTTP requests and for upgraded streams.
 *
 * @author Stuart Douglas
 */
public class ServletInputStreamImpl extends ServletInputStream {

    private final HttpServletRequestImpl request;
    private final StreamSourceChannel channel;

    private volatile ReadListener listener;

    /**
     * If this stream is ready for a read
     */
    private static final int FLAG_READY = 1;
    private static final int FLAG_CLOSED = 1 << 1;
    private static final int FLAG_FINISHED = 1 << 2;
    private static final int FLAG_ON_DATA_READ_CALLED = 1 << 3;

    private int state;
    private AsyncContextImpl asyncContext;

    public ServletInputStreamImpl(final HttpServletRequestImpl request) {
        this.request = request;
        this.channel = request.getExchange().getRequestChannel();
    }


    @Override
    public boolean isFinished() {
        return anyAreSet(state, FLAG_FINISHED);
    }

    @Override
    public boolean isReady() {
        return anyAreSet(state, FLAG_READY) && !isFinished();
    }

    @Override
    public void setReadListener(final ReadListener readListener) {
        if (readListener == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("readListener");
        }
        if (listener != null) {
            throw UndertowServletMessages.MESSAGES.listenerAlreadySet();
        }
        if (!request.isAsyncStarted()) {
            throw UndertowServletMessages.MESSAGES.asyncNotStarted();
        }

        asyncContext = request.getAsyncContext();
        listener = readListener;
        channel.getReadSetter().set(new ServletInputStreamChannelListener());

        //we resume from an async task, after the request has been dispatched
        asyncContext.addAsyncTask(new Runnable() {
            @Override
            public void run() {
                channel.resumeReads();
            }
        });
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int read = read(b);
        if(read == -1) {
            return -1;
        }
        return b[0];
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowServletMessages.MESSAGES.streamIsClosed();
        }
        if (anyAreSet(state, FLAG_FINISHED)) {
            return -1;
        }
        if(len == 0) {
            return 0;
        }
        ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
        if (listener == null) {
            int res = Channels.readBlocking(channel, buffer);
            if (res == -1) {
                state |= FLAG_FINISHED;
            }
            return res;
        } else {
            if (anyAreClear(state, FLAG_READY)) {
                throw UndertowServletMessages.MESSAGES.streamNotReady();
            }
            int res = channel.read(buffer);
            if (res == -1) {
                state |= FLAG_FINISHED;
            } else if (res == 0) {
                state &= ~FLAG_READY;
            }
            return res;
        }
    }

    @Override
    public void close() throws IOException {
        channel.shutdownReads();
        state |= FLAG_FINISHED | FLAG_CLOSED;
    }

    private class ServletInputStreamChannelListener implements ChannelListener<StreamSourceChannel> {
        @Override
        public void handleEvent(final StreamSourceChannel channel) {
            channel.suspendReads();
            asyncContext.addAsyncTask(new Runnable() {
                @Override
                public void run() {
                    if (asyncContext.isDispatched()) {
                        //this is no longer an async request
                        //we just return
                        //TODO: what do we do here? Revert back to blocking mode?
                        return;
                    }
                    if (anyAreSet(state, FLAG_FINISHED)) {
                        return;
                    }
                    state |= FLAG_READY;
                    try {
                        CompositeThreadSetupAction action = request.getServletContext().getDeployment().getThreadSetupAction();
                        ThreadSetupAction.Handle handle = action.setup(request.getExchange());
                        try {
                            listener.onDataAvailable();
                        } finally {
                            handle.tearDown();
                        }

                    } catch (Exception e) {
                        CompositeThreadSetupAction action = request.getServletContext().getDeployment().getThreadSetupAction();
                        ThreadSetupAction.Handle handle = action.setup(request.getExchange());
                        try {
                            listener.onError(e);
                        } finally {
                            handle.tearDown();
                        }
                        IoUtils.safeClose(channel);
                    }
                    if (anyAreSet(state, FLAG_FINISHED)) {
                        if (anyAreClear(state, FLAG_ON_DATA_READ_CALLED)) {
                            try {
                                state |= FLAG_ON_DATA_READ_CALLED;
                                channel.shutdownReads();
                                CompositeThreadSetupAction action = request.getServletContext().getDeployment().getThreadSetupAction();
                                ThreadSetupAction.Handle handle = action.setup(request.getExchange());
                                try {
                                    listener.onAllDataRead();
                                } finally {
                                    handle.tearDown();
                                }
                            } catch (IOException e) {
                                listener.onError(e);
                                IoUtils.safeClose(channel);
                            }
                        }
                    } else if (!isReady()) {
                        channel.resumeReads();
                    }
                }
            });

        }
    }
}
