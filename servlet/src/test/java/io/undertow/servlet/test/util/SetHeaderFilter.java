package io.undertow.servlet.test.util;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class SetHeaderFilter implements Filter {

    private String header;
    private String value;

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        header = filterConfig.getInitParameter("header");
        value = filterConfig.getInitParameter("value");
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        ((HttpServletResponse) response).setHeader(header, value);
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {

    }
}
