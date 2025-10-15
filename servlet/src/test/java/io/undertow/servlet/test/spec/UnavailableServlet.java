package io.undertow.servlet.test.spec;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.UnavailableException;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */
public class UnavailableServlet implements Servlet {

    static final String PERMANENT = "permanent";
    static boolean first = true;

    @Override
    public void init(ServletConfig config) throws ServletException {
        if(config.getInitParameter(PERMANENT) != null) {
            throw new UnavailableException("msg");
        } else if(first){
            first = false;
            throw new UnavailableException("msg", 1);
        }
    }

    @Override
    public ServletConfig getServletConfig() {
        return null;
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {

    }

    @Override
    public String getServletInfo() {
        return null;
    }

    @Override
    public void destroy() {

    }
}
