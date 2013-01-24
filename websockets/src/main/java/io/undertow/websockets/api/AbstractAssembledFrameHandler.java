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
package io.undertow.websockets.api;


import java.nio.ByteBuffer;

/**
 *
 * Adapter class for {@link AssembledFrameHandler} implementations. Sub-classes can override the methods to provide
 * an implementation.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class AbstractAssembledFrameHandler extends AbstractFrameHandler implements AssembledFrameHandler {

    /**
     * Does nothing, sub-classes may override this method to provide an implementation.
     */
    @Override
    public void onTextFrame(WebSocketSession session, WebSocketFrameHeader header, CharSequence payload) {
        // NOOP
    }

    /**
     * Does nothing, sub-classes may override this method to provide an implementation.
     */
    @Override
    public void onBinaryFrame(WebSocketSession session, WebSocketFrameHeader header, ByteBuffer... payload) {
        // NOOP
    }
}
