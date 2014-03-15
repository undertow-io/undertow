package io.undertow.spdy;

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
            SendFrameHeader header = ((SpdyStreamStreamSinkChannel) newFrame).generateSendFrameHeader();
            //if no header is generated then flow control means we can't send anything
            if(header.getByteBuffer() == null) {
                return false;
            }
        }

        pendingFrames.add(newFrame);
        return true;
    }

    @Override
    public void frameAdded(SpdyStreamSinkChannel addedFrame, List<SpdyStreamSinkChannel> pendingFrames, Deque<SpdyStreamSinkChannel> holdFrames) {
        Iterator<SpdyStreamSinkChannel> it = pendingFrames.iterator();
        while (it.hasNext()){
            SpdyStreamSinkChannel pending = it.next();
            if(pending instanceof SpdyStreamStreamSinkChannel) {
                SendFrameHeader header = ((SpdyStreamStreamSinkChannel) pending).generateSendFrameHeader();
                if(header.getByteBuffer() != null) {
                    pendingFrames.add(pending);
                    it.remove();
                }
            }
        }
    }
}
