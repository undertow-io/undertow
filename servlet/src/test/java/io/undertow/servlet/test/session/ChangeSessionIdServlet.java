package io.undertow.servlet.test.session;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @author Stuart Douglas
 */
public class ChangeSessionIdServlet extends HttpServlet{

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(true);
        String old = session.getId();
        req.changeSessionId();
        String newId = session.getId();
        resp.getWriter().write(old + " "+ newId);
    }
}
