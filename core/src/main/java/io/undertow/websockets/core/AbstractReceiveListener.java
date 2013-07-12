package io.undertow.websockets.core;

import org.xnio.ChannelListener;
import org.xnio.IoUtils;

import java.io.IOException;

/**
 * A receive listener that performs a callback when it receives a message
 *
 * @author Stuart Douglas
 */
public abstract class AbstractReceiveListener implements ChannelListener<WebSocketChannel> {

    private BufferedBinaryMessage binaryMessage;
    private BufferedBinaryMessage control;
    private BufferedTextMessage textMessage;
    private WebSocketFrameType lastFragmeneted;

    @Override
    public void handleEvent(WebSocketChannel channel) {
        try {
            final StreamSourceFrameChannel result = channel.receive();
            if (result == null) {
                return;
            } else if (result.getType() == WebSocketFrameType.BINARY) {
                if (!result.isFinalFragment()) {
                    lastFragmeneted = WebSocketFrameType.BINARY;
                }
                onBinary(channel, result);
            } else if (result.getType() == WebSocketFrameType.TEXT) {
                if (!result.isFinalFragment()) {
                    lastFragmeneted = WebSocketFrameType.TEXT;
                }
                onText(channel, result);
            } else if (result.getType() == WebSocketFrameType.PONG) {
                onPong(channel, result);
            } else if (result.getType() == WebSocketFrameType.CONTINUATION) {
                if (textMessage != null || binaryMessage != null) {
                    bufferFullMessage(result);
                } else {
                    onContinuation(channel, result);
                    if (result.isFinalFragment()) {
                        lastFragmeneted = null;
                    }
                }
            } else if (result.getType() == WebSocketFrameType.PING) {
                onPing(channel, result);
            } else if (result.getType() == WebSocketFrameType.CLOSE) {
                onClose(channel, result);
            }
        } catch (IOException e) {
            onError(channel, e);
        }
    }

    protected void onPing(WebSocketChannel webSocketChannel, StreamSourceFrameChannel channel) throws IOException {
        bufferFullMessage(channel);
    }

    protected void onClose(WebSocketChannel webSocketChannel, StreamSourceFrameChannel channel) throws IOException {
        bufferFullMessage(channel);
    }

    protected void onPong(WebSocketChannel webSocketChannel, StreamSourceFrameChannel messageChannel) throws IOException {
        bufferFullMessage(messageChannel);
    }

    protected void onText(WebSocketChannel webSocketChannel, StreamSourceFrameChannel messageChannel) throws IOException {
        bufferFullMessage(messageChannel);
    }

    protected void onBinary(WebSocketChannel webSocketChannel, StreamSourceFrameChannel messageChannel) throws IOException {
        bufferFullMessage(messageChannel);
    }

    protected void onContinuation(WebSocketChannel webSocketChannel, StreamSourceFrameChannel messageChannel) throws IOException {
        if (lastFragmeneted == WebSocketFrameType.TEXT) {
            onText(webSocketChannel, messageChannel);
        } else {
            onBinary(webSocketChannel, messageChannel);
        }
    }

    protected void onError(WebSocketChannel channel, Throwable error) {
        IoUtils.safeClose(channel);
    }

    /**
     * Utility method that reads a full text or binary message, including all fragmented parts. Once the full message is
     * read then the {@link #onFullTextMessage(WebSocketChannel, BufferedTextMessage)} or
     * {@link #onFullBinaryMessage(WebSocketChannel, BufferedBinaryMessage)} method will be invoked.
     *
     * @param messageChannel The message channel
     */
    protected final void bufferFullMessage(StreamSourceFrameChannel messageChannel) {
        final boolean finalFrame = messageChannel.isFinalFragment();
        if (messageChannel.getType() == WebSocketFrameType.CONTINUATION) {
            if (textMessage != null) {
                readBufferedText(messageChannel, finalFrame);
            } else if (binaryMessage != null) {
                readBufferedBinary(messageChannel, finalFrame, false);
            }
        } else if (messageChannel.getType() == WebSocketFrameType.TEXT) {
            textMessage = new BufferedTextMessage(getMaxTextBufferSize());
            readBufferedText(messageChannel, finalFrame);
        } else if (messageChannel.getType() == WebSocketFrameType.BINARY) {
            binaryMessage = new BufferedBinaryMessage(getMaxBinaryBufferSize());
            readBufferedBinary(messageChannel, finalFrame, false);
        } else if (messageChannel.getType() == WebSocketFrameType.PONG) {
            control = new BufferedBinaryMessage(-1);
            readBufferedBinary(messageChannel, finalFrame, true);
        } else if (messageChannel.getType() == WebSocketFrameType.PING) {
            control = new BufferedBinaryMessage(-1);
            readBufferedBinary(messageChannel, finalFrame, true);
        } else if (messageChannel.getType() == WebSocketFrameType.CLOSE) {
            control = new BufferedBinaryMessage(-1);
            readBufferedBinary(messageChannel, finalFrame, true);
        }
    }

    protected long getMaxBinaryBufferSize() {
        return -1;
    }

    protected long getMaxTextBufferSize() {
        return -1;
    }

    private void readBufferedBinary(final StreamSourceFrameChannel messageChannel, final boolean finalFrame, final boolean controlFrame) {
        final BufferedBinaryMessage buffer;
        if(controlFrame) {
            buffer = control;
        } else {
            buffer = binaryMessage;
        }

        buffer.read(messageChannel, new WebSocketCallback<BufferedBinaryMessage>() {
            @Override
            public void complete(WebSocketChannel channel, BufferedBinaryMessage context) {
                if (finalFrame) {
                    try {
                        WebSocketFrameType type = messageChannel.getType();
                        if (!controlFrame) {
                            onFullBinaryMessage(channel, buffer);
                        } else if (type == WebSocketFrameType.PONG) {
                            onFullPongMessage(channel, buffer);
                        } else if (type == WebSocketFrameType.PING) {
                            onFullPingMessage(channel, buffer);
                        } else if (type == WebSocketFrameType.CLOSE) {
                            onFullCloseMessage(channel, buffer);
                        }
                        if(controlFrame) {
                            control = null;
                        } else {
                            binaryMessage = null;
                        }
                    } catch (IOException e) {
                        AbstractReceiveListener.this.onError(channel, e);
                    }
                }
            }

            @Override
            public void onError(WebSocketChannel channel, BufferedBinaryMessage context, Throwable throwable) {
                context.release();
                AbstractReceiveListener.this.onError(channel, throwable);
                if(controlFrame) {
                    control = null;
                } else {
                    binaryMessage = null;
                }
            }
        });
    }

    private void readBufferedText(StreamSourceFrameChannel messageChannel, final boolean finalFrame) {
        textMessage.read(messageChannel, new WebSocketCallback<BufferedTextMessage>() {
            @Override
            public void complete(WebSocketChannel channel, BufferedTextMessage context) {
                if (finalFrame) {
                    try {
                        onFullTextMessage(channel, textMessage);
                    } catch (IOException e) {
                        AbstractReceiveListener.this.onError(channel, e);
                    } finally {
                        textMessage = null;
                    }
                }
            }

            @Override
            public void onError(WebSocketChannel channel, BufferedTextMessage context, Throwable throwable) {
                AbstractReceiveListener.this.onError(channel, throwable);
                textMessage = null;
            }
        });
    }

    protected void onFullTextMessage(final WebSocketChannel channel, BufferedTextMessage message) throws IOException {

    }

    protected void onFullBinaryMessage(final WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {

    }

    protected void onFullPingMessage(final WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
        WebSockets.sendPong(message.getData(), channel, null);
    }

    protected void onFullPongMessage(final WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
    }

    protected void onFullCloseMessage(final WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
        WebSockets.sendClose(message.getData(), channel, null);
    }
}
