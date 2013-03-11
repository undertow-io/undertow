package io.undertow.servlet.test.streams;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class ContentLengthCloseFlushServlet extends HttpServlet {

    private boolean completed = false;

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        if (completed) {
            resp.getWriter().write("OK");
        } else {
            resp.setContentLength(1);
            ServletOutputStream stream = resp.getOutputStream();
            stream.write('a'); //the stream should automatically close here, because it is the content length, but flush should still work
            stream.flush();
            stream.close();
            completed = true;
        }
    }
}
