package io.undertow.jsp;

import java.util.HashMap;

import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ServletInfo;
import org.apache.jasper.deploy.JspPropertyGroup;
import org.apache.jasper.deploy.TagLibraryInfo;
import org.apache.jasper.servlet.JspServlet;
import org.apache.tomcat.InstanceManager;

import static org.apache.jasper.Constants.JSP_PROPERTY_GROUPS;
import static org.apache.jasper.Constants.JSP_TAG_LIBRARIES;
import static org.apache.jasper.Constants.SERVLET_VERSION;

/**
 * Builder that creates a JSP deployment.
 *
 * @author Stuart Douglas
 */
public class JspServletBuilder {


    public static void setupDeployment(final DeploymentInfo deploymentInfo, final HashMap<String, JspPropertyGroup> propertyGroups, final HashMap<String, TagLibraryInfo> tagLibraries, final InstanceManager instanceManager) {
        deploymentInfo.addServletContextAttribute(SERVLET_VERSION, deploymentInfo.getMajorVersion() + "." + deploymentInfo.getMinorVersion());
        deploymentInfo.addServletContextAttribute(JSP_PROPERTY_GROUPS, propertyGroups);
        deploymentInfo.addServletContextAttribute(JSP_TAG_LIBRARIES, tagLibraries);
        deploymentInfo.addServletContextAttribute(InstanceManager.class.getName(), instanceManager);
    }

    public static ServletInfo createServlet(final String name, final String path) {
        ServletInfo servlet = new ServletInfo(name, JspServlet.class);
        servlet.addMapping(path);
        return servlet;
    }


}
