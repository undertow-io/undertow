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

import io.undertow.websockets.core.WebSocketLogger;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.api.SendCallback;

/**
 * Wraps a array of {@link SendCallback}s to execute on {@link #onCompletion()} or {@link #onError(Throwable)}
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DelegatingSendCallback implements SendCallback {
    private final SendCallback[] callbacks;

    public DelegatingSendCallback(SendCallback... callbacks) {
        if (callbacks == null || callbacks.length == 0) {
            throw WebSocketMessages.MESSAGES.senderCallbacksEmpty();
        }
        this.callbacks = callbacks;
    }

    @Override
    public void onCompletion() {
        for (SendCallback callback : callbacks) {
            try {
                StreamSinkChannelUtils.safeNotify(callback, null);
            } catch (Throwable cause) {
                WebSocketLogger.REQUEST_LOGGER.sendCallbackExecutionError(cause);
            }
        }
    }

    @Override
    public void onError(Throwable error) {
        for (SendCallback callback : callbacks) {
            try {
                StreamSinkChannelUtils.safeNotify(callback, error);
            } catch (Throwable cause) {
                WebSocketLogger.REQUEST_LOGGER.sendCallbackExecutionError(cause);
            }
        }
    }
}
