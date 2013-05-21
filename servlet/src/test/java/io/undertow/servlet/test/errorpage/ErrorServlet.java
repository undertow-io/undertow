package io.undertow.servlet.test.errorpage;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class ErrorServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        String statusCode = req.getParameter("statusCode");
        if (statusCode != null) {
            resp.sendError(Integer.parseInt(statusCode));
        } else {
            try {
                throw (Exception) getClass().getClassLoader().loadClass(req.getParameter("exception")).newInstance();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }
}
