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
 * A WebSocket frame header which holds all the meta-data of a received WebSocket frame.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface WebSocketFrameHeader {
    /**
     * The type of the WebSocket frame.
     */
    enum FrameType {

        /**
         * WebSocket frame contains binary data.
         */
        BINARY,

        /**
         * WebSocket frame contains UTF-8 encoded data.
         */
        TEXT,

        /**
         * WebSocketFrame which contains either binary data or utf-8 encoded data depending on the the first
         * WebSocket frame which started the fragemented frame.
         */
        CONTINUATION,
    }

    /**
     * Return the {@link FrameType} of the Frame.
     */
    FrameType getType();

    /**
     * Return the RSV which is used for extensions. If no extension is used {@code 0} is returned.
     */
    int getRsv();

    /**
     * Return {@code true} if this Frame is the last fragement.
     */
    boolean isLastFragement();
}
