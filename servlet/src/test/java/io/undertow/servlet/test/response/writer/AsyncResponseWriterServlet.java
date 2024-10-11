/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.response.writer;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Asynchronous version of {@link ResponseWriterServlet}.
 *
 * @author Flavia Rainone
 */
public class AsyncResponseWriterServlet extends ResponseWriterServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {

        String test = req.getParameter("test");
        if (!test.equals(CONTENT_LENGTH_FLUSH)) {
            throw new IllegalArgumentException("not a test " + test);
        }
        final AsyncContext asyncContext = req.startAsync();
        new Thread(()->{
            try {
                contentLengthFlush((HttpServletResponse) asyncContext.getResponse());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                asyncContext.complete();
            }
        }).start();
    }
}
