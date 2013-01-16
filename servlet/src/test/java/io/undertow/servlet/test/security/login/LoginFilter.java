package io.undertow.servlet.test.security.login;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class LoginFilter implements Filter {
    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest)request;
        String username = req.getHeader("username");
        String password = req.getHeader("password");
        if(username == null) {
            chain.doFilter(request, response);
        }
        try {
            req.login(username, password);
            chain.doFilter(request, response);
        } catch (ServletException e) {
            ((HttpServletResponse)response).setStatus(401);
        }
    }

    @Override
    public void destroy() {

    }
}
