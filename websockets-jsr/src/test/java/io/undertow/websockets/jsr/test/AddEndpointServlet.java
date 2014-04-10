package io.undertow.websockets.jsr.test;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */

public class AddEndpointServlet implements Servlet {
    @Override
    public void init(ServletConfig c) throws ServletException {
        String websocketPath = "/foo";
        ServerEndpointConfig config = ServerEndpointConfig.Builder.create(ProgramaticEndpoint.class, websocketPath).build();
        ServerContainer serverContainer = (ServerContainer) c.getServletContext().getAttribute("javax.websocket.server.ServerContainer");
        try {
            serverContainer.addEndpoint(config);
        } catch (DeploymentException ex) {
            throw new ServletException("Error deploying websocket endpoint:", ex);
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
