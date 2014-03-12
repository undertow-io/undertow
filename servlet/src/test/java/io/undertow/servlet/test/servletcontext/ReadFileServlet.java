package io.undertow.servlet.test.servletcontext;

import org.xnio.IoUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * @author Stuart Douglas
 */
public class ReadFileServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String name = req.getParameter("file");
        URL resource = req.getServletContext().getResource(name);
        InputStream stream = resource.openStream();
        try {
            byte[] buff = new byte[100];
            int res;
            while ((res = stream.read(buff)) > 0) {
                resp.getOutputStream().write(buff, 0, res);
            }
        } finally {
            IoUtils.safeClose(stream);
        }
    }
}
