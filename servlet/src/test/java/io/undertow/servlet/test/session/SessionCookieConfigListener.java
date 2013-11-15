package io.undertow.servlet.test.session;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * @author Stuart Douglas
 */
public class SessionCookieConfigListener implements ServletContextListener {
    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        sce.getServletContext().getSessionCookieConfig().setName("MySessionCookie");
        sce.getServletContext().getSessionCookieConfig().setPath("/servletContext/aa/");
    }

    @Override
    public void contextDestroyed(final ServletContextEvent sce) {

    }
}
