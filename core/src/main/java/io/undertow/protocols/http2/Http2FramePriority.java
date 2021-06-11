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

package io.undertow.protocols.http2;

import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import io.undertow.UndertowLogger;
import io.undertow.server.protocol.framed.FramePriority;
import io.undertow.server.protocol.framed.SendFrameHeader;

/**
 * TODO: real priority
 *
 * @author Stuart Douglas
 */
class Http2FramePriority implements FramePriority<Http2Channel, AbstractHttp2StreamSourceChannel, AbstractHttp2StreamSinkChannel> {

    private int nextId;

    Http2FramePriority(int nextId) {
        this.nextId = nextId;
    }

    @Override
    public boolean insertFrame(AbstractHttp2StreamSinkChannel newFrame, List<AbstractHttp2StreamSinkChannel> pendingFrames) {
        //we need to deal with out of order streams
        //if multiple threads are creating streams they may not end up here in the correct order
        boolean incrementIfAccepted = false;
        if ((newFrame.getChannel().isClient() && newFrame instanceof Http2HeadersStreamSinkChannel) ||
                newFrame instanceof Http2PushPromiseStreamSinkChannel) {
            if (newFrame instanceof Http2PushPromiseStreamSinkChannel) {
                int streamId = ((Http2PushPromiseStreamSinkChannel) newFrame).getPushedStreamId();
                if (streamId > nextId) {
                    return false;
                } else if (streamId == nextId) {
                    incrementIfAccepted = true;
                }
            } else {
                int streamId = ((Http2HeadersStreamSinkChannel) newFrame).getStreamId();
                if (streamId > nextId) {
                    return false;
                } else if (streamId == nextId) {
                    incrementIfAccepted = true;
                }
            }
        }
        //first deal with flow control
        if (newFrame instanceof Http2StreamSinkChannel) {
            if (newFrame.isBroken() || !newFrame.isOpen()) {
                return true; //just quietly drop the frame
            }
            try {
                SendFrameHeader header = ((Http2StreamSinkChannel) newFrame).generateSendFrameHeader();
                //if no header is generated then flow control means we can't send anything
                if (header.getByteBuffer() == null) {
                    //we clear the header, as we want to generate a new real header when the flow control window is updated
                    ((Http2StreamSinkChannel) newFrame).clearHeader();
                    return false;
                }
            } catch (Exception e) {
                UndertowLogger.REQUEST_LOGGER.debugf("Failed to generate header %s", newFrame);
            }
        }

        pendingFrames.add(newFrame);
        if (incrementIfAccepted) {
            nextId += 2;
        }
        return true;
    }

    @Override
    public void frameAdded(AbstractHttp2StreamSinkChannel addedFrame, List<AbstractHttp2StreamSinkChannel> pendingFrames, Deque<AbstractHttp2StreamSinkChannel> holdFrames) {
        Iterator<AbstractHttp2StreamSinkChannel> it = holdFrames.iterator();
        while (it.hasNext()) {
            AbstractHttp2StreamSinkChannel pending = it.next();
            boolean incrementNextId = false;
            if ((pending.getChannel().isClient() && pending instanceof Http2HeadersStreamSinkChannel) ||
                    pending instanceof Http2PushPromiseStreamSinkChannel) {
                if (pending instanceof Http2PushPromiseStreamSinkChannel) {
                    int streamId = ((Http2PushPromiseStreamSinkChannel) pending).getPushedStreamId();
                    if (streamId > nextId) {
                        continue;
                    } else if (streamId == nextId) {
                        incrementNextId = true;
                    }
                } else {
                    int streamId = ((Http2HeadersStreamSinkChannel) pending).getStreamId();
                    if (streamId > nextId) {
                        continue;
                    } else if (streamId == nextId) {
                        incrementNextId = true;
                    }
                }
            }

            if (pending instanceof Http2StreamSinkChannel) {
                SendFrameHeader header = ((Http2StreamSinkChannel) pending).generateSendFrameHeader();
                if (header.getByteBuffer() != null) {
                    pendingFrames.add(pending);
                    it.remove();
                    it = holdFrames.iterator();
                    if (incrementNextId) {
                        nextId += 2;
                    }
                } else {
                    //we clear the header, as we want to generate a new real header when the flow control window is updated
                    ((Http2StreamSinkChannel) pending).clearHeader();
                }
            }
        }

    }
}
