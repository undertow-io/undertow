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

package io.undertow.protocols.ajp;

import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import io.undertow.server.protocol.framed.FramePriority;
import io.undertow.server.protocol.framed.SendFrameHeader;

/**
 * AJP frame priority
 * @author Stuart Douglas
 */
class AjpClientFramePriority implements FramePriority<AjpClientChannel, AbstractAjpClientStreamSourceChannel, AbstractAjpClientStreamSinkChannel>{

    public static AjpClientFramePriority INSTANCE = new AjpClientFramePriority();

    @Override
    public boolean insertFrame(AbstractAjpClientStreamSinkChannel newFrame, List<AbstractAjpClientStreamSinkChannel> pendingFrames) {
        if(newFrame instanceof AjpClientRequestClientStreamSinkChannel) {
            SendFrameHeader header = ((AjpClientRequestClientStreamSinkChannel) newFrame).generateSendFrameHeader();
            if(header.getByteBuffer() == null) {
                //we clear the header, as we want to generate a new real header when the flow control window is updated
                ((AjpClientRequestClientStreamSinkChannel) newFrame).clearHeader();
                return false;
            }
        }
        pendingFrames.add(newFrame);
        return true;
    }

    @Override
    public void frameAdded(AbstractAjpClientStreamSinkChannel addedFrame, List<AbstractAjpClientStreamSinkChannel> pendingFrames, Deque<AbstractAjpClientStreamSinkChannel> holdFrames) {
        Iterator<AbstractAjpClientStreamSinkChannel> it = holdFrames.iterator();
        while (it.hasNext()){
            AbstractAjpClientStreamSinkChannel pending = it.next();
            if(pending instanceof AjpClientRequestClientStreamSinkChannel) {
                SendFrameHeader header = ((AjpClientRequestClientStreamSinkChannel) pending).generateSendFrameHeader();
                if(header.getByteBuffer() != null) {
                    pendingFrames.add(pending);
                    it.remove();
                } else {
                    //we clear the header, as we want to generate a new real header when the flow control window is updated
                    ((AjpClientRequestClientStreamSinkChannel) pending).clearHeader();
                }
            }
        }
    }
}
