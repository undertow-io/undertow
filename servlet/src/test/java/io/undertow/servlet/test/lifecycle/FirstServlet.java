package io.undertow.servlet.test.lifecycle;

import org.junit.Assert;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */
public class FirstServlet implements Servlet {
    public static volatile boolean init;

    @Override
    public void init(ServletConfig config) throws ServletException {
        Assert.assertFalse(SecondServlet.init);
        init = true;
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
