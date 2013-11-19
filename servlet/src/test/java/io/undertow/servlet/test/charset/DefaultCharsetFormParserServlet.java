package io.undertow.servlet.test.charset;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */
public class DefaultCharsetFormParserServlet extends HttpServlet {

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        String utf8Bytes = req.getParameter("\u0041\u00A9\u00E9\u0301\u0941\uD835\uDD0A");
        resp.getOutputStream().write(utf8Bytes.getBytes("UTF-8"));
    }

}
