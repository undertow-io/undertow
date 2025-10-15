/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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
package io.undertow.servlet.test.async;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * <p>A servlet that executes asynchronous <em>start</em> method and searches
 * for a thread-local Date variable in it. The date is returned in the response
 * formatted as a String. The thread-local should be set by a setup action
 * configured via the deployment info. This way the servlet ensures that
 * setup actions were executed OK for the runnable.</p>
 *
 * @author rmartinc
 */
public class SimpleDateThreadLocalAsyncServlet extends HttpServlet {

    public static final String NULL_THREAD_LOCAL = "NULL_THREAD_LOCAL";
    private static final String DATE_FORMAT = "yyyyMMddHHmmssSSSZ";
    private static final ThreadLocal<Date> simpleDateThreadLocal = new ThreadLocal<>();

    public static Date getThreadLocalSimpleDate() {
        return simpleDateThreadLocal.get();
    }

    public static void initThreadLocalSimpleDate() {
        simpleDateThreadLocal.set(new Date());
    }

    public static void removeThreadLocalSimpleDate() {
        simpleDateThreadLocal.remove();
    }

    public static Date parseDate(String date) throws ParseException {
        return new SimpleDateFormat(DATE_FORMAT).parse(date);
    }

    public static String formatDate(Date date) {
        return new SimpleDateFormat(DATE_FORMAT).format(date);
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        final AsyncContext ac = request.startAsync(request, response);
        ac.start(() -> {
            try {
                response.setStatus(HttpServletResponse.SC_OK);
                Date date = getThreadLocalSimpleDate();
                try (PrintWriter pw = response.getWriter()) {
                    pw.write(date == null? NULL_THREAD_LOCAL : formatDate(date));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
