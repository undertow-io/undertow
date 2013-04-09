package io.undertow.servlet.test.security.ssl;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
public class SSLAttributesServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter pw = resp.getWriter();
        if (req.getServletPath().equals("/id")) {
            pw.write(req.getAttribute("javax.servlet.request.ssl_session_id").toString());
        } else if (req.getServletPath().equals("/key-size")) {
            pw.write(req.getAttribute("javax.servlet.request.key_size").toString());
        } else if (req.getServletPath().equals("/cipher-suite")) {
            pw.write(req.getAttribute("javax.servlet.request.cipher_suite").toString());
        } else if (req.getServletPath().equals("/cert")) {
            pw.write(req.getAttribute("javax.servlet.request.X509Certificate").toString());
        }
        pw.close();
    }


}
