package io.undertow.servlet.test.charset;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Matej Lazar
 */
public class EchoServlet extends HttpServlet {

    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        String charset = req.getParameter("charset");
        resp.setCharacterEncoding(charset);
        PrintWriter writer = resp.getWriter();
        String message = req.getParameter("message");
        System.out.println("Received message: " + message);
        writer.write(message);
        writer.close();
    }

}
