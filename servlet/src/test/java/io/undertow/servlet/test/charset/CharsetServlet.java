package io.undertow.servlet.test.charset;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class CharsetServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        String charset = req.getParameter("charset");
        resp.setCharacterEncoding(charset);
        PrintWriter writer = resp.getWriter();
        writer.write("\u0041\u00A9\u00E9\u0301\u0941\uD835\uDD0A");
        writer.close();
    }

}
