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

/**
 * WebSocket frame sender that supports to send out frames in fragements.
 *
 * @author Stuart Douglas
 */
public interface FragmentedSender {

    /**
     * Calling this method marks the next frame to be sent as the final
     * frame for this fragmented message.
     *
     * Attempting to use this {@link FragmentedSender} after the last message has been sent will
     * result an an {@link IllegalStateException}.
     *
     */
    void finalFragment();
}
