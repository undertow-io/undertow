package io.undertow.servlet.test.security.form;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */
public class EchoServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        byte[] buf = new byte[100];
        int res;
        while ((res = req.getInputStream().read(buf)) > 0) {
            resp.getOutputStream().write(buf, 0, res);
        }

    }
}
