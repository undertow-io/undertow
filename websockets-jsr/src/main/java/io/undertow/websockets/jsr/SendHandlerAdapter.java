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
package io.undertow.websockets.jsr;

import io.undertow.websockets.api.SendCallback;

import javax.websocket.SendHandler;
import javax.websocket.SendResult;

/**
 * {@link SendCallback} implementation which will notify a wrapped {@link SendHandler} once a send operation
 * completes.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class SendHandlerAdapter implements SendCallback {
    private final SendHandler handler;
    private static final SendResult OK = new SendResult();

    public SendHandlerAdapter(SendHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onCompletion() {
        handler.setResult(OK);
    }

    @Override
    public void onError(Throwable cause) {
        handler.setResult(new SendResult(cause));
    }
}
