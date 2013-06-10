/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package io.undertow.websockets.jsr.test.annotated;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;

/**
 * @author Stuart Douglas
 */
@ClientEndpoint
public class AnnotatedClientEndpoint {

    private static final BlockingDeque<String> MESSAGES = new LinkedBlockingDeque<String>();

    public static String message() throws InterruptedException {
        return MESSAGES.pollFirst(3, TimeUnit.SECONDS);
    }

    @OnOpen
    public void onOpen(final Session session) {
        session.getAsyncRemote().sendText("hi");
    }

    @OnMessage
    public void onMessage(final String message) {
        MESSAGES.add(message);
    }

    @OnClose
    public void onClose() {
        MESSAGES.add("CLOSED");
    }

}
