/*
 * Copyright 2013 JBoss, by Red Hat, Inc
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
package io.undertow.websockets.impl;

import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.api.FragmentedBinaryFrameSender;
import org.xnio.ChannelListener;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;

/**
 * Default {@link FragmentedBinaryFrameSender} implementation, which uses a {@link WebSocketChannel} to perform
 * writes.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DefaultFragmentedBinaryFrameSender extends DefaultBinaryFrameSender implements FragmentedBinaryFrameSender {

    private boolean firstFragment = true;
    private boolean finalFragment;
    private boolean finalFragmentStarted;
    public DefaultFragmentedBinaryFrameSender(WebSocketChannelSession session) {
        super(session);
    }

    @Override
    protected StreamSinkFrameChannel createSink(long payloadSize) throws IOException {
        if (finalFragmentStarted) {
            throw WebSocketMessages.MESSAGES.fragmentedSenderCompleteAlready();
        }
        if (finalFragment) {
            finalFragmentStarted = true;
        }

        StreamSinkFrameChannel sink;
        if (firstFragment) {
            firstFragment = false;
            sink = session.getChannel().send(WebSocketFrameType.BINARY, payloadSize);
        } else {
            sink =  session.getChannel().send(WebSocketFrameType.CONTINUATION, payloadSize);
        }
        sink.setFinalFragment(finalFragment);
        if (finalFragment) {
            sink.getCloseSetter().set(new ChannelListener<StreamSinkChannel>() {
                @Override
                public void handleEvent(StreamSinkChannel channel) {
                    session.complete(DefaultFragmentedBinaryFrameSender.this);
                }
            });
        }
        return sink;
    }

    @Override
    public void finalFragment() {
        finalFragment = true;
    }
}
