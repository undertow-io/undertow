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

import io.undertow.util.StatusCodes;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

public class SimpleAsyncListener implements AsyncListener {

    public static final String MESSAGE = "handled by " + SimpleAsyncListener.class.getSimpleName();

    private final AsyncContext ctx;

    public SimpleAsyncListener() {
        this.ctx = null;
    }

    public SimpleAsyncListener(AsyncContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
        ServletResponse response = event.getSuppliedResponse();
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setContentType("text/plain");
        httpResponse.setStatus(StatusCodes.OK);
        PrintWriter writer = httpResponse.getWriter();
        writer.write(MESSAGE);
        writer.flush();
        if (this.ctx != null) {
            ctx.complete();
        } else {
            event.getAsyncContext().complete();
        }
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {
    }

}
