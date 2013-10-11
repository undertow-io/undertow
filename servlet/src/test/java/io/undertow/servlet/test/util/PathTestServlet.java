package io.undertow.servlet.test.util;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class PathTestServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter out = resp.getWriter();
        out.print("pathInfo:" + req.getPathInfo());
        out.print(" queryString:" + req.getQueryString());
        out.print(" servletPath:" + req.getServletPath());
        out.print(" requestUri:" + req.getRequestURI());
    }
}
