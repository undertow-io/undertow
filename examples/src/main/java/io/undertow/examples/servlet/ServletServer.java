package io.undertow.examples.servlet;

import javax.servlet.ServletException;

import io.undertow.Undertow;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;

/**
 * @author Stuart Douglas
 */
public class ServletServer {


    public static void main(final String[] args) {
        try {

            final ServletContainer container = ServletContainer.Factory.newInstance();

            DeploymentInfo servletBuilder = new DeploymentInfo()
                    .setClassLoader(ServletServer.class.getClassLoader())
                    .setContextPath("/myapp")
                    .setDeploymentName("test.war")
                    .addServlet(new ServletInfo("MessageServlet", MessageServlet.class)
                            .addInitParam("message", "Hello World")
                            .addMapping("/*"));

            DeploymentManager manager = container.addDeployment(servletBuilder);
            manager.deploy();

            Undertow server = Undertow.builder()
                    .addListener(8080, "localhost")
                    .setDefaultHandler(manager.start())
                    .build();
            server.start();
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
    }
}
