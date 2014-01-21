package io.undertow.servlet.test.lifecycle;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */
public class LifecycleFilter implements Filter {
    public static boolean initCalled;
    public static boolean destroyCalled;
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        initCalled = true;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        destroyCalled = true;
    }
}
