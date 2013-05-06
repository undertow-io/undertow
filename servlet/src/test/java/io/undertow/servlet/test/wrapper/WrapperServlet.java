package io.undertow.servlet.test.wrapper;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class WrapperServlet extends HttpServlet{

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        resp.getWriter().write(req.getClass().getName());
        resp.getWriter().write("\n");
        resp.getWriter().write(resp.getClass().getName());
    }
}
