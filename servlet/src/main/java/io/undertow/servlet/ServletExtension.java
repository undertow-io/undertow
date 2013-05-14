package io.undertow.servlet;

import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.spec.ServletContextImpl;

/**
 *
 * Interface that allows the servlet deployment to be modified before it is deployed.
 *
 * These extensions are loaded using a {@link java.util.ServiceLoader} from the deployment
 * class loader, and are the first things run after the servlet context is created.
 *
 * There are many possible use cases for these extensions. Some obvious ones are:
 *
 * - Adding additional handlers
 * - Adding new authentication mechanisms
 * - Adding and removing servlets
 *
 *
 * @author Stuart Douglas
 */
public interface ServletExtension {

    void handleDeployment(final DeploymentInfo deploymentInfo, final ServletContextImpl servletContext);

}
