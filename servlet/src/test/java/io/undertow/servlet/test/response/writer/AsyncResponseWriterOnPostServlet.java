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

package io.undertow.servlet.test.response.writer;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Asynchronous version of {@link ResponseWriterOnPostServlet}.
 *
 * @author Flavia Rainone
 */
public class AsyncResponseWriterOnPostServlet extends ResponseWriterOnPostServlet {

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        String test = req.getParameter("test");
        if (!test.equals(CONTENT_LENGTH_FLUSH)) {
            throw new IllegalArgumentException("not a test " + test);
        }
        final AsyncContext asyncContext = req.startAsync();
        asyncContext.setTimeout(50000);
        new Thread(()->{
            try {
                HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
                contentLengthFlush(response);
                // read after writing the response (UNDERTOW-2243)
                while (true) {
                    if (req.getInputStream().readLine(new byte[100], 0, 100) == -1) {
                        req.getInputStream().close();
                        break;
                    }
                }
            } catch (RuntimeException e) {
                exception = e;
                throw e;
            } catch (Throwable t) {
                exception = t;
                throw new RuntimeException(t);
            } finally {
                asyncContext.complete();
            }
        }).start();
    }
}
