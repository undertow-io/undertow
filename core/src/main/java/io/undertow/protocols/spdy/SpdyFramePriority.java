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

package io.undertow.protocols.spdy;

import io.undertow.server.protocol.framed.FramePriority;
import io.undertow.server.protocol.framed.SendFrameHeader;

import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * TODO: real priority
 *
 * @author Stuart Douglas
 */
class SpdyFramePriority implements FramePriority<SpdyChannel, SpdyStreamSourceChannel,SpdyStreamSinkChannel>{

    public static SpdyFramePriority INSTANCE = new SpdyFramePriority();

    @Override
    public boolean insertFrame(SpdyStreamSinkChannel newFrame, List<SpdyStreamSinkChannel> pendingFrames) {
        //first deal with flow control
        if(newFrame instanceof SpdyStreamStreamSinkChannel) {
            if(newFrame.isBroken() || !newFrame.isOpen()) {
                //drop the frame
                return true;
            }
            SendFrameHeader header = ((SpdyStreamStreamSinkChannel) newFrame).generateSendFrameHeader();
            //if no header is generated then flow control means we can't send anything
            if(header.getByteBuffer() == null) {
                //we clear the header, as we want to generate a new real header when the flow control window is updated
                ((SpdyStreamStreamSinkChannel) newFrame).clearHeader();
                return false;
            }
        }

        pendingFrames.add(newFrame);
        return true;
    }

    @Override
    public void frameAdded(SpdyStreamSinkChannel addedFrame, List<SpdyStreamSinkChannel> pendingFrames, Deque<SpdyStreamSinkChannel> holdFrames) {
        Iterator<SpdyStreamSinkChannel> it = holdFrames.iterator();
        while (it.hasNext()){
            SpdyStreamSinkChannel pending = it.next();
            if(pending instanceof SpdyStreamStreamSinkChannel) {
                SendFrameHeader header = ((SpdyStreamStreamSinkChannel) pending).generateSendFrameHeader();
                if(header.getByteBuffer() != null) {
                    pendingFrames.add(pending);
                    it.remove();
                } else {
                    //we clear the header, as we want to generate a new real header when the flow control window is updated
                    ((SpdyStreamStreamSinkChannel) pending).clearHeader();
                }
            }
        }
    }
}
