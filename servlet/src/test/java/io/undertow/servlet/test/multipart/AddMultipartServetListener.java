package io.undertow.servlet.test.multipart;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;

/**
 * @author Stuart Douglas
 */
public class AddMultipartServetListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletRegistration.Dynamic reg = sce.getServletContext().addServlet("added", new MultiPartServlet());
        reg.addMapping("/added");
        reg.setMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir")));

        reg = sce.getServletContext().addServlet("getParam", new MultiPartServlet(true));
        reg.addMapping("/getParam");
        reg.setMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir")));
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
