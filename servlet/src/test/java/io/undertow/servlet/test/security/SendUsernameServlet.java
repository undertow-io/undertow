package io.undertow.servlet.test.security;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.Override;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class SendUsernameServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        OutputStream stream = resp.getOutputStream();
        stream.write(req.getUserPrincipal().getName().getBytes());

    }
}
