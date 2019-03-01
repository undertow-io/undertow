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

package io.undertow.websockets.core;

import io.undertow.server.protocol.framed.FramePriority;

import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Web socket frame priority
 *
 * @author Stuart Douglas
 */
public class WebSocketFramePriority implements FramePriority<WebSocketChannel, StreamSourceFrameChannel, StreamSinkFrameChannel> {

    /**
     * Strict ordering queue. Makes sure that the initial frame for a stream is sent in the order that send() is called.
     * <p>
     * Required to pass the autobahn test suite with no non-strict performance.
     * <p>
     * TODO: provide a way to disable this.
     */
    private final Queue<StreamSinkFrameChannel> strictOrderQueue = new ConcurrentLinkedDeque<>();
    private StreamSinkFrameChannel currentFragmentedSender;
    boolean closed = false;
    boolean immediateCloseFrame = false;

    @Override
    public boolean insertFrame(StreamSinkFrameChannel newFrame, List<StreamSinkFrameChannel> pendingFrames) {

        if (newFrame.getType() != WebSocketFrameType.PONG &&
                newFrame.getType() != WebSocketFrameType.PING) {
            StreamSinkFrameChannel order = strictOrderQueue.peek();
            if (order != null) {
                if (order != newFrame && order.isOpen()) {
                    //generally we want to queue close frames immediately
                    //however if the close frame is initiated from this side we respect the ordering
                    //if the close frame is from the other side we have to echo it back immediately
                    if (newFrame.getType() != WebSocketFrameType.CLOSE) {
                        return false;
                    } else if (!newFrame.getWebSocketChannel().isCloseFrameReceived() && !immediateCloseFrame) {
                        return false;
                    }
                }
                if(order == newFrame && newFrame.isFinalFragment()) {
                    strictOrderQueue.poll();
                }
            }
        }

        if (closed) {
            //drop the frame
            newFrame.markBroken();
            return true;
        }
        if (currentFragmentedSender == null) {
            //we are not sending fragmented
            if (!newFrame.isWritesShutdown()) {
                //start of a fragmented message
                currentFragmentedSender = newFrame;
            }
            if (pendingFrames.isEmpty()) {
                pendingFrames.add(newFrame);
            } else if (newFrame.getType() == WebSocketFrameType.PING ||
                    newFrame.getType() == WebSocketFrameType.PONG) {
                //add at the start of the queue
                int index = 1; //index = 1 because the very first frame may be half written out already
                boolean done = false;
                //insert before the first frame that is not a ping or pong
                while (index < pendingFrames.size()) {
                    WebSocketFrameType type = pendingFrames.get(index).getType();
                    if(type != WebSocketFrameType.PING && type != WebSocketFrameType.PONG) {
                        pendingFrames.add(index, newFrame);
                        done = true;
                        break;
                    }
                    index++;
                }
                if(!done) {
                    pendingFrames.add(newFrame);
                }
            } else {
                pendingFrames.add(newFrame);
            }
        } else if (newFrame.getType() == WebSocketFrameType.PING ||
                newFrame.getType() == WebSocketFrameType.PONG) {
            //we stick ping and pong in the middle of fragmentation
            if (pendingFrames.isEmpty()) {
                pendingFrames.add(newFrame);
            } else {
                pendingFrames.add(1, newFrame);
            }
        } else {
            //we are currently sending fragmented, we can't queue and non control messages
            if (currentFragmentedSender != newFrame) {
                return false;
            } else {
                if (newFrame.isFinalFragment()) {
                    currentFragmentedSender = null;
                }
                pendingFrames.add(newFrame);
            }
        }
        if (newFrame.getType() == WebSocketFrameType.CLOSE) {
            closed = true;
        }
        return true;
    }

    @Override
    public void frameAdded(StreamSinkFrameChannel addedFrame, List<StreamSinkFrameChannel> pendingFrames, Deque<StreamSinkFrameChannel> holdFrames) {
        if (addedFrame.isFinalFragment()) {
            while (true) {
                StreamSinkFrameChannel frame = strictOrderQueue.peek();
                if(frame == null) {
                    break;
                }
                if(holdFrames.contains(frame)) {
                    if(insertFrame(frame, pendingFrames)) {
                        holdFrames.remove(frame);
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            while (!holdFrames.isEmpty()) {
                StreamSinkFrameChannel frame = holdFrames.peek();
                if (insertFrame(frame, pendingFrames)) {
                    holdFrames.poll();
                } else {
                    return;
                }
            }
        }
    }

    void addToOrderQueue(final StreamSinkFrameChannel channel) {
        if (channel.getType() != WebSocketFrameType.PING && channel.getType() != WebSocketFrameType.PONG) {
            strictOrderQueue.add(channel);
        }
    }

    void immediateCloseFrame() {
        this.immediateCloseFrame = true;
    }
}
