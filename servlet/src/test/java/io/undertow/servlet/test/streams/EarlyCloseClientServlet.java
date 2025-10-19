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

package io.undertow.servlet.test.streams;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * @author Stuart Douglas
 */
public class EarlyCloseClientServlet extends HttpServlet {

    private static volatile boolean exceptionThrown;
    private static volatile boolean completedNormally;
    private static volatile CountDownLatch latch = new CountDownLatch(1);

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final ServletInputStream inputStream = req.getInputStream();
            byte[] buf = new byte[1024];
            int read;
            while ((read = inputStream.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            resp.getOutputStream().write(out.toByteArray());
            completedNormally = true;
        } catch (IOException e) {
            exceptionThrown = true;
        } finally {
            latch.countDown();
        }
    }

    public static void reset() {
        latch = new CountDownLatch(1);
        completedNormally = false;
        exceptionThrown = false;
    }

    public static boolean isExceptionThrown() {
        return exceptionThrown;
    }

    public static boolean isCompletedNormally() {
        return completedNormally;
    }

    public static CountDownLatch getLatch() {
        return latch;
    }
}
