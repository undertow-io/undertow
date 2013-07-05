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

import io.undertow.websockets.api.FragmentedBinaryFrameSender;
import io.undertow.websockets.core.FragmentedMessageChannel;
import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.WebSocketMessages;

import java.io.IOException;

/**
 * Default {@link FragmentedBinaryFrameSender} implementation, which uses a {@link io.undertow.websockets.core.WebSocketChannel} to perform
 * writes.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DefaultFragmentedBinaryFrameSender extends DefaultBinaryFrameSender implements FragmentedBinaryFrameSender {
    private FragmentedMessageChannel channel;
    private boolean finalFragment;
    public DefaultFragmentedBinaryFrameSender(WebSocketChannelSession session){
        super(session);
    }

    @Override
    protected StreamSinkFrameChannel createSink(long payloadSize) throws IOException {
        if (session.closeFrameSent) {
            WebSocketMessages.MESSAGES.closeFrameSentBefore();
        }
        if (channel == null) {
            channel = session.getChannel().sendFragmentedBinary();
        }
        StreamSinkFrameChannel sink = channel.send(payloadSize, finalFragment);
        return sink;
    }

    @Override
    public void finalFragment() {
        finalFragment = true;
    }
}
