package io.undertow.servlet.test.spec;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.UnavailableException;
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
