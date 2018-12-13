package io.undertow.servlet.test.listener.request.async.onError;

import java.io.IOException;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Exception occurs during its processing. No in delegated dispatch part.
 *
 */
public class AsyncServlet4 extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            AsyncContext ctx = req.startAsync();
            ctx.addListener(new AsyncEventListener());
            ctx.addListener(new SimpleAsyncListener(ctx));
            throw new NullPointerException();
        }

}