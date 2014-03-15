package io.undertow.spdy;

import io.undertow.server.protocol.framed.SendFrameHeader;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import org.xnio.Pooled;

import java.nio.ByteBuffer;
import java.util.zip.Deflater;

/**
 * @author Stuart Douglas
 */
public class SpdySynStreamStreamSinkChannel extends SpdyStreamStreamSinkChannel {

    private final HeaderMap headers;
    private boolean first = true;
    private final Deflater deflater;

    SpdySynStreamStreamSinkChannel(SpdyChannel channel, HeaderMap headers, int streamId, Deflater deflater) {
        super(channel, streamId);
        this.headers = headers;
        this.deflater = deflater;
    }

    @Override
    protected SendFrameHeader createFrameHeaderImpl() {
        Pooled<ByteBuffer> header = getChannel().getHeapBufferPool().allocate();
        ByteBuffer buffer = header.getResource();
        if (first) {
            Pooled<ByteBuffer> outPooled = getChannel().getHeapBufferPool().allocate();
            Pooled<ByteBuffer> inPooled = getChannel().getHeapBufferPool().allocate();
            try {
                ByteBuffer inputBuffer = inPooled.getResource();
                ByteBuffer outputBuffer = outPooled.getResource();


                first = false;
                int firstInt = SpdyChannel.CONTROL_FRAME | (getChannel().getSpdyVersion() << 16) | 1;
                SpdyProtocolUtils.putInt(buffer, firstInt);
                SpdyProtocolUtils.putInt(buffer, 0); //we back fill the length
                HeaderMap headers = this.headers;

                SpdyProtocolUtils.putInt(buffer, getStreamId());
                SpdyProtocolUtils.putInt(buffer, 0);
                buffer.put((byte) 0);
                buffer.put((byte) 0);


                headers.remove(Headers.CONNECTION); //todo: should this be here?
                headers.remove(Headers.KEEP_ALIVE);
                headers.remove(Headers.TRANSFER_ENCODING);

                SpdyProtocolUtils.putInt(inputBuffer, headers.size());

                long fiCookie = headers.fastIterateNonEmpty();
                while (fiCookie != -1) {
                    HeaderValues headerValues = headers.fiCurrent(fiCookie);
                    //TODO: for now it just fails if there are too many headers
                    SpdyProtocolUtils.putInt(inputBuffer, headerValues.getHeaderName().length());
                    for (int i = 0; i < headerValues.getHeaderName().length(); ++i) {
                        inputBuffer.put((byte) (Character.toLowerCase((char) headerValues.getHeaderName().byteAt(i))));
                    }
                    int pos = inputBuffer.position();
                    SpdyProtocolUtils.putInt(inputBuffer, 0); //size, back fill

                    int size = headerValues.size() - 1; //null between the characters

                    for (int i = 0; i < headerValues.size(); ++i) {
                        String val = headerValues.get(i);
                        size += val.length();
                        for (int j = 0; j < val.length(); ++j) {
                            inputBuffer.put((byte) val.charAt(j));
                        }
                        if (i != headerValues.size() - 1) {
                            inputBuffer.put((byte) 0);
                        }
                    }
                    SpdyProtocolUtils.putInt(inputBuffer, size, pos);
                    fiCookie = headers.fiNext(fiCookie);
                }

                deflater.setInput(inputBuffer.array(), inputBuffer.arrayOffset(), inputBuffer.position());

                int deflated;
                do {
                    deflated = deflater.deflate(outputBuffer.array(), outputBuffer.arrayOffset(), outputBuffer.remaining(), Deflater.SYNC_FLUSH);
                    buffer.put(outputBuffer.array(), outputBuffer.arrayOffset(), deflated);
                } while (deflated == outputBuffer.remaining());
                SpdyProtocolUtils.putInt(buffer, ((isWritesShutdown() && !getBuffer().hasRemaining() ? SpdyChannel.FLAG_FIN : 0) << 24) | (buffer.position() - 8), 4);

            } finally {
                inPooled.free();
                outPooled.free();
            }
        }
        int remainingInBuffer = 0;
        if (getBuffer().remaining() > 0) {
            int fcWindow = grabFlowControlBytes(getBuffer().remaining());
            if (fcWindow > 0) {
                remainingInBuffer = getBuffer().remaining() - fcWindow;
                SpdyProtocolUtils.putInt(buffer, getStreamId());
                SpdyProtocolUtils.putInt(buffer, ((isWritesShutdown() ? SpdyChannel.FLAG_FIN : 0) << 24) + fcWindow);
            } else {
                remainingInBuffer = getBuffer().remaining();
            }
        }
        header.getResource().flip();
        if (!header.getResource().hasRemaining()) {
            header.free();
            return new SendFrameHeader(remainingInBuffer, null);
        }
        return new SendFrameHeader(remainingInBuffer, header);
    }
}
