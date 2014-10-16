package io.undertow.servlet.test.spec;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * A servlet that echoes name and value pairs for received valid cookies only.
 *
 * @author Gael Marziou
 */
public class ValidCookieEchoServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req,
                         final HttpServletResponse resp) throws ServletException, IOException {

        Cookie[] cookies = req.getCookies();

        PrintWriter out = resp.getWriter();
        for (Cookie cookie : cookies) {
            out.print("name='" + cookie.getName() + "'");
            out.print("value='" + cookie.getValue() + "'");
        }
    }
}
