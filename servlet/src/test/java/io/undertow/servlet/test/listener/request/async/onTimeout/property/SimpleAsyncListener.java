/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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
package io.undertow.servlet.test.listener.request.async.onTimeout.property;

import io.undertow.util.StatusCodes;

import java.io.IOException;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletResponse;

public class SimpleAsyncListener implements AsyncListener {

    public static final String HEADER="TIMER_HEADER";
    @Override
    public void onComplete(AsyncEvent event) throws IOException {
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
        HttpServletResponse response = (HttpServletResponse) event.getSuppliedResponse();
        final long startTstamp = (long) event.getSuppliedRequest().getAttribute(AsyncServlet.TIMEOUT_START_TSTAMP);
        long timeout = Long.parseLong((String) event.getSuppliedRequest().getParameter(AsyncServlet.TEST_TIMEOUT));
        long elapsed = System.currentTimeMillis() - startTstamp;
        response.setHeader(HEADER, (System.currentTimeMillis() - startTstamp) + "");
        if (Math.abs(timeout - elapsed) < 2000) {
            response.setStatus(StatusCodes.OK);
        } else {
            response.setStatus(StatusCodes.CONFLICT);
        }
        event.getAsyncContext().complete();
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {
    }

}
