package io.undertow.servlet.test.security.ssl;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.cert.X509Certificate;
import javax.servlet.ServletException;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
@ServletSecurity(value = @HttpConstraint(

))
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
            final X509Certificate[] attribute = (X509Certificate[]) req.getAttribute("javax.servlet.request.X509Certificate");
            if (attribute!=null){
                pw.write(attribute[0].getSerialNumber().toString());
            }
        }
        pw.close();
    }


}
