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

package io.undertow.servlet.test.listener.request.async.onError;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.AsyncEvent;

/**
 * @author Stuart Douglas
 */
public class AsyncEventListener implements jakarta.servlet.AsyncListener {

    private static final LinkedBlockingDeque<String> EVENTS = new LinkedBlockingDeque<>();

    public static String[] results(int expected) {
        List<String> poll = new ArrayList<>();
        String current = EVENTS.poll();
        while (current != null) {
            poll.add(current);
            current = EVENTS.poll();
        }
        try {
            if (poll.size() < expected) {
                current = EVENTS.poll(5, TimeUnit.SECONDS);
                while (current != null) {
                    poll.add(current);
                    if (poll.size() < expected) {
                        current = EVENTS.poll(5, TimeUnit.SECONDS);
                    } else {
                        current = EVENTS.poll();
                    }
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        String[] ret = poll.toArray(new String[poll.size()]);
        EVENTS.clear();
        return ret;
    }

    @Override
    public void onComplete(final AsyncEvent event) throws IOException {
        EVENTS.add("COMPLETE");
    }

    @Override
    public void onTimeout(final AsyncEvent event) throws IOException {
        EVENTS.add("TIMEOUT");
    }

    @Override
    public void onError(final AsyncEvent event) throws IOException {
        EVENTS.add("ERROR");
    }

    @Override
    public void onStartAsync(final AsyncEvent event) throws IOException {
        EVENTS.add("START");
    }
}
