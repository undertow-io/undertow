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

package io.undertow.websockets.jsr.test.annotated;

import jakarta.websocket.OnMessage;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

/**
 * @author Stuart Douglas
 */
@ServerEndpoint(value = "/encoding/{user}", encoders = {EncodableObject.TextEncoder.class}, decoders = {EncodableObject.TextDecoder.class, EncodableObject.BinaryDecoder.class})
public class EncodingEndpoint {

    @OnMessage
    public EncodableObject handleMessage(final EncodableObject message, @PathParam("user") String user) {
        return new EncodableObjectSubClass(message.getValue() + " " + user);
    }

}
