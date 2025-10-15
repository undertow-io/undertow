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

package io.undertow.servlet.test.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;

/**
 * @author Stuart Douglas
 */
public class TestListener implements ServletRequestListener {

    private static final List<String> RESULTS = Collections.synchronizedList(new ArrayList<String>());

    private static volatile CountDownLatch latch;

    public static void addMessage(String message) {
        RESULTS.add(message);
        latch.countDown();
    }

    public static void init(int count) {
        RESULTS.clear();
        latch = new CountDownLatch(count);
    }

    @Override
    public void requestDestroyed(final ServletRequestEvent sre) {
        RESULTS.add("destroyed " + sre.getServletRequest().getDispatcherType());
        latch.countDown();
    }

    @Override
    public void requestInitialized(final ServletRequestEvent sre) {
        RESULTS.add("created " + sre.getServletRequest().getDispatcherType());
        latch.countDown();
    }

    public static List<String> results() {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return RESULTS;
    }
}
