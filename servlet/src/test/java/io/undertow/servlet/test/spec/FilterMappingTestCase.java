/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.servlet.test.spec;

import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class FilterMappingTestCase {

    public static String message;

    public static final String HELLO_WORLD = "Hello World";
    public static final String SERVLET = "aServlet";

    private Filter filterMappedByServletName = new NullFilter();
    private Filter filterMappedByUrlPattern = new NullFilter();

    /**
     * A Filter that does nothing
     */
    static class NullFilter implements Filter {
        @Override public void init(FilterConfig filterConfig) throws ServletException {}

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
            filterChain.doFilter(servletRequest, servletResponse);
        }

        @Override public void destroy() {}
    }

    static class NullServlet extends HttpServlet {}

    @Test
    public void testRegisterFilters() throws Exception {
        //  If the servlet can be set up without an exception, then the filters were correctly registered
        setupServlet();
    }

    /**
     * Registers a servlet with two filters, one mapped by servlet name and one mapped by url pattern
     */
    private void setupServlet() {
        DeploymentUtils.setupServlet(new ServletExtension() {
                                         @Override
                                         public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
                                             servletContext
                                                     .addFilter("MyFilter1", filterMappedByServletName)
                                                     .addMappingForServletNames(null, false, SERVLET);

                                             servletContext
                                                     .addFilter("MyFilter2", filterMappedByUrlPattern)
                                                     .addMappingForUrlPatterns(null, false, "/");
                                         }
                                     },
                new ServletInfo(SERVLET, NullServlet.class)
                        .addMapping("/" + SERVLET));
    }

}
