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

import java.util.HashSet;
import java.util.Set;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

/**
 * @author Stuart Douglas
 */
@ServerEndpoint(value = "/threads")
public class ThreadSafetyEndpoint {

    public static volatile Session s;


    public static final int NUM_THREADS = 100;
    public static final int NUM_MESSAGES = 100;

    public static final Set<String> expected() {
        Set<String> ret = new HashSet<>();
        for (int i = 0; i < NUM_THREADS; ++i) {
            for (int j = 0; j < NUM_MESSAGES; ++j) {
                ret.add("t" + i + "-m" + j);
            }
        }
        return ret;
    }

    @OnOpen
    public void onOpen(final Session session) {
        s = session;
        for (int i = 0; i < NUM_THREADS; ++i) {
            final int tnum = i;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < NUM_MESSAGES; ++j) {
                        session.getAsyncRemote().sendText("t" + tnum + "-m" + j);
                    }
                }
            });
            t.start();
        }
    }

}
