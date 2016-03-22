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
package io.undertow.websockets.jsr;

import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;

import javax.websocket.SendHandler;
import javax.websocket.SendResult;

/**
 * {@link WebSocketCallback} implementation which will notify a wrapped {@link SendHandler} once a send operation
 * completes.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class SendHandlerAdapter implements WebSocketCallback<Void> {
    private final SendHandler handler;
    private static final SendResult OK = new SendResult();
    private volatile boolean done;

    SendHandlerAdapter(SendHandler handler) {
        this.handler = handler;
    }
    @Override
    public void complete(WebSocketChannel channel, Void context) {
        if(done) {
            return;
        }
        done = true;
        handler.onResult(new SendResult());
    }

    @Override
    public void onError(WebSocketChannel channel, Void context, Throwable throwable) {
        if(done) {
            return;
        }
        done = true;
        handler.onResult(new SendResult(throwable));
    }
}
