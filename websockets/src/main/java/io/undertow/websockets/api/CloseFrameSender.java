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

import java.io.IOException;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface CloseFrameSender {

    /**
     * Send the a CLOSE websocket frame and notify the {@link SendCallback} once done.
     * <p/>
     * After the CLOSE is sent the connections will be closed.
     *
     * @param reason
     *          The reason why the connection should be closed or {@code null} if none should be supplied.
     * @param callback
     *          The callback that is called when sending is done or {@code null} if no notification
     *          should be done.
     * @throws IllegalStateException
     *          Is thrown if a {@link FragmentedSender} is still in use.
     */
    void sendClose(CloseReason reason, SendCallback callback);

    /**
     * Send the a CLOSE websocket frame and blocks until complete.
     * <p/>
     * After the CLOSE is sent the connections will be closed.
     *
     * @param reason
     *          The reason why the connection should be closed or {@code null} if none should be supplied.
     * @throws IOException
     *          If sending failed
     * @throws IllegalStateException
     *          Is thrown if a {@link FragmentedSender} is still in use.
     */
    void sendClose(CloseReason reason) throws IOException;
}
