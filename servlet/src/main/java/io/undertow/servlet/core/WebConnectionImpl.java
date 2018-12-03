/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.core;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingDeque;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.WebConnection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DuplexChannel;

/**
 * @author Stuart Douglas
 */
public class WebConnectionImpl extends CombinedChannelDuplexHandler<WebConnectionImpl.InboundHandler, WebConnectionImpl.OutboundHandler> implements WebConnection {

    private final LinkedBlockingDeque<ByteBuf> dataQueue = new LinkedBlockingDeque<>();
    private static final ByteBuf LAST = Unpooled.buffer(0);

    private ChannelHandlerContext context;

    private final UpgradeInputStream inputStream = new UpgradeInputStream();
    private final UpgradeOutputStream outputStream = new UpgradeOutputStream();
    private boolean writeClosed;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        init(new InboundHandler(), new OutboundHandler());
        ctx.read();
        super.handlerAdded(ctx);
        context = ctx;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return inputStream;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return outputStream;
    }

    @Override
    public void close() throws Exception {

    }


    class InboundHandler extends SimpleChannelInboundHandler<ByteBuf> {


        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            msg.retain();
            dataQueue.add(msg);
            inputStream.notifyData();
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            super.channelReadComplete(ctx);
            dataQueue.add(LAST);
            inputStream.notifyData();
        }
    }

    class OutboundHandler extends ChannelOutboundHandlerAdapter {

    }

    class UpgradeOutputStream extends ServletOutputStream {

        private volatile WriteListener writeListener;
        @Override
        public boolean isReady() {
            //TODO: fix this
            return true;
        }

        @Override
        public void setWriteListener(WriteListener w) {
            writeListener = w;
            try {
                w.onWritePossible();
            } catch (IOException e) {
                writeListener.onError(e);
            }
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b});
        }

        @Override
        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            ByteBuf buf = Unpooled.buffer(len);
            buf.writeBytes(b, off, len);
            try {
                context.writeAndFlush(buf).get();
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        @Override
        public void close() throws IOException {
            ((DuplexChannel) context.channel()).shutdownOutput();
        }
    }


    class UpgradeInputStream extends ServletInputStream {

        private volatile ReadListener readListener;
        private volatile boolean canNotifyListener;

        void notifyData() {
            if (readListener != null && canNotifyListener) {
                invokeListener();
            }
        }

        @Override
        public boolean isFinished() {
            return false;
        }

        @Override
        public boolean isReady() {
            boolean ret = !dataQueue.isEmpty();
            canNotifyListener = !ret;
            return ret;
        }

        @Override
        public void setReadListener(ReadListener r) {
            readListener = r;
            invokeListener();
        }

        void invokeListener() {
            try {
                readListener.onDataAvailable();
            } catch (IOException e) {
                readListener.onError(e);
            }
        }

        @Override
        public int read() throws IOException {
            byte[] buf = new byte[1];
            int res = read(buf);
            if (res == -1) {
                return -1;
            }
            return buf[0];
        }

        @Override
        public int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (readListener != null && dataQueue.isEmpty()) {
                throw new IllegalStateException();
            }

            try {
                ByteBuf buf = dataQueue.take();
                if (buf == LAST) {
                    if (readListener != null) {
                        context.executor().execute(new Runnable() {
                            @Override
                            public void run() {
                                notifyEnd();
                            }
                        });
                    }
                    return -1;
                }
                int toRead = Math.min(len, buf.readableBytes());
                buf.readBytes(b, off, toRead);
                if (buf.isReadable()) {
                    dataQueue.addFirst(buf);
                } else {
                    buf.release();
                }
                return toRead;
            } catch (InterruptedException e) {
                throw new IOException(e);
            }

        }

        @Override
        public void close() throws IOException {
            ((DuplexChannel) context.channel()).shutdownOutput();
        }

        public void notifyEnd() {
            try {
                readListener.onAllDataRead();
            } catch (IOException e) {
                readListener.onError(e);
            }
        }
    }


}
