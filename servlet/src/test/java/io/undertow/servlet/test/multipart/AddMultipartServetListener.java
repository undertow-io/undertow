package io.undertow.servlet.test.multipart;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRegistration;

/**
 * @author Stuart Douglas
 */
public class AddMultipartServetListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletRegistration.Dynamic reg = sce.getServletContext().addServlet("added", new MultiPartServlet());
        reg.addMapping("/added");
        reg.setMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir")));
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
