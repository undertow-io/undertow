/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.spec;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;

import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.util.EmptyEnumeration;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.servlet.util.IteratorEnumeration;

/**
 * @author Stuart Douglas
 */
public class ServletContextImpl implements ServletContext {

    private final ServletContainer servletContainer;
    private final Deployment deployment;
    private final DeploymentInfo deploymentInfo;
    private final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<String, Object>();


    private volatile boolean bootstrapComplete = false;


    public ServletContextImpl(final ServletContainer servletContainer, final Deployment deployment) {
        this.servletContainer = servletContainer;
        this.deployment = deployment;
        this.deploymentInfo = deployment.getDeploymentInfo();
    }

    @Override
    public String getContextPath() {
        return deploymentInfo.getContextPath();
    }

    @Override
    public ServletContext getContext(final String uripath) {
        DeploymentManager deploymentByPath = servletContainer.getDeploymentByPath(uripath);
        if (deploymentByPath == null) {
            return null;
        }
        return deploymentByPath.getDeployment().getServletContext();
    }

    @Override
    public int getMajorVersion() {
        return 3;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public int getEffectiveMajorVersion() {
        return deploymentInfo.getMajorVersion();
    }

    @Override
    public int getEffectiveMinorVersion() {
        return deploymentInfo.getMinorVersion();
    }

    @Override
    public String getMimeType(final String file) {
        return null;
    }

    @Override
    public Set<String> getResourcePaths(final String path) {
        final File resource = deploymentInfo.getResourceLoader().getResource(path);
        if (resource == null || !resource.isDirectory()) {
            return null;
        }
        final String first;
        if (path.charAt(path.length() - 1) == '/') {
            first = path;
        } else {
            first = path + '/';
        }
        final Set<String> resources = new HashSet<String>();
        for (String res : resource.list()) {
            resources.add(first + res);
        }
        return resources;
    }

    @Override
    public URL getResource(final String path) throws MalformedURLException {
        File resource = deploymentInfo.getResourceLoader().getResource(path);
        if (resource == null) {
            return null;
        }
        return resource.toURL();
    }

    @Override
    public InputStream getResourceAsStream(final String path) {
        File resource = deploymentInfo.getResourceLoader().getResource(path);
        if (resource == null) {
            return null;
        }
        try {
            return new BufferedInputStream(new FileInputStream(resource));
        } catch (FileNotFoundException e) {
            //should never happen, as the resource loader should return null in this case
            return null;
        }
    }

    @Override
    public RequestDispatcher getRequestDispatcher(final String path) {
        return null;
    }

    @Override
    public RequestDispatcher getNamedDispatcher(final String name) {
        return null;
    }

    @Override
    public Servlet getServlet(final String name) throws ServletException {
        return null;
    }

    @Override
    public Enumeration<Servlet> getServlets() {
        return EmptyEnumeration.instance();
    }

    @Override
    public Enumeration<String> getServletNames() {
        return EmptyEnumeration.instance();
    }

    @Override
    public void log(final String msg) {
        UndertowServletLogger.ROOT_LOGGER.info(msg);
    }

    @Override
    public void log(final Exception exception, final String msg) {
        UndertowServletLogger.ROOT_LOGGER.error(msg, exception);
    }

    @Override
    public void log(final String message, final Throwable throwable) {
        UndertowServletLogger.ROOT_LOGGER.error(message, throwable);
    }

    @Override
    public String getRealPath(final String path) {
        return null;
    }

    @Override
    public String getServerInfo() {
        return null;
    }

    @Override
    public String getInitParameter(final String name) {
        return deploymentInfo.getInitParameters().get(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return new IteratorEnumeration<String>(deploymentInfo.getInitParameters().keySet().iterator());
    }

    @Override
    public boolean setInitParameter(final String name, final String value) {
        if (deploymentInfo.getInitParameters().containsKey(name)) {
            return false;
        }
        deploymentInfo.getInitParameters().put(name, value);
        return true;
    }

    @Override
    public Object getAttribute(final String name) {
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return new IteratorEnumeration<String>(attributes.keySet().iterator());
    }

    @Override
    public void setAttribute(final String name, final Object object) {
        Object existing = attributes.put(name, object);
        if (existing != null) {
            deployment.getApplicationListeners().servletContextAttributeReplaced(name, existing);
        } else {
            deployment.getApplicationListeners().servletContextAttributeAdded(name, object);
        }
    }

    @Override
    public void removeAttribute(final String name) {
        Object exiting = attributes.remove(name);
        deployment.getApplicationListeners().servletContextAttributeRemoved(name, exiting);
    }

    @Override
    public String getServletContextName() {
        return deploymentInfo.getDeploymentName();
    }

    @Override
    public ServletRegistration.Dynamic addServlet(final String servletName, final String className) {
        try {
            ServletInfo servlet = new ServletInfo(servletName, (Class<? extends Servlet>) deploymentInfo.getClassLoader().loadClass(className));
            deploymentInfo.addServlet(servlet);
            return new ServletRegistrationImpl(servlet);
        } catch (ClassNotFoundException e) {
            throw UndertowServletMessages.MESSAGES.cannotLoadClass(className, e);
        }
    }

    @Override
    public ServletRegistration.Dynamic addServlet(final String servletName, final Servlet servlet) {
        ServletInfo s = new ServletInfo(servletName, servlet.getClass(), new ImmediateInstanceFactory<Servlet>(servlet));
        deploymentInfo.addServlet(s);
        return new ServletRegistrationImpl(s);
    }

    @Override
    public ServletRegistration.Dynamic addServlet(final String servletName, final Class<? extends Servlet> servletClass) {
        ServletInfo servlet = new ServletInfo(servletName, servletClass);
        deploymentInfo.addServlet(servlet);
        return new ServletRegistrationImpl(servlet);
    }

    @Override
    public <T extends Servlet> T createServlet(final Class<T> clazz) throws ServletException {
        try {
            return deploymentInfo.getClassIntrospecter().createInstanceFactory(clazz).createInstance().getInstance();
        } catch (InstantiationException e) {
            throw UndertowServletMessages.MESSAGES.couldNotInstantiateComponent(clazz.getName(), e);
        }
    }

    @Override
    public ServletRegistration getServletRegistration(final String servletName) {
        final ServletInfo servlet = deploymentInfo.getServlets().get(servletName);
        return new ServletRegistrationImpl(servlet);
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        final Map<String, ServletRegistration> ret = new HashMap<String, ServletRegistration>();
        for (Map.Entry<String, ServletInfo> entry : deploymentInfo.getServlets().entrySet()) {
            ret.put(entry.getKey(), new ServletRegistrationImpl(entry.getValue()));
        }
        return ret;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(final String filterName, final String className) {
        try {
            FilterInfo filter = new FilterInfo(filterName, (Class<? extends Filter>) deploymentInfo.getClassLoader().loadClass(className));
            deploymentInfo.addFilter(filter);
            return new FilterRegistrationImpl(filter, deploymentInfo);
        } catch (ClassNotFoundException e) {
            throw UndertowServletMessages.MESSAGES.cannotLoadClass(className, e);
        }
    }

    @Override
    public FilterRegistration.Dynamic addFilter(final String filterName, final Filter filter) {
        FilterInfo f = new FilterInfo(filterName, filter.getClass(), new ImmediateInstanceFactory<Filter>(filter));
        deploymentInfo.addFilter(f);
        return new FilterRegistrationImpl(f, deploymentInfo);

    }

    @Override
    public FilterRegistration.Dynamic addFilter(final String filterName, final Class<? extends Filter> filterClass) {
        FilterInfo filter = new FilterInfo(filterName, filterClass);
        deploymentInfo.addFilter(filter);
        return new FilterRegistrationImpl(filter, deploymentInfo);
    }

    @Override
    public <T extends Filter> T createFilter(final Class<T> clazz) throws ServletException {
        try {
            return deploymentInfo.getClassIntrospecter().createInstanceFactory(clazz).createInstance().getInstance();
        } catch (InstantiationException e) {
            throw UndertowServletMessages.MESSAGES.couldNotInstantiateComponent(clazz.getName(), e);
        }
    }

    @Override
    public FilterRegistration getFilterRegistration(final String filterName) {
        final FilterInfo filterInfo = deploymentInfo.getFilters().get(filterName);
        if (filterInfo == null) {
            return null;
        }
        return new FilterRegistrationImpl(filterInfo, deploymentInfo);
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        final Map<String, FilterRegistration> ret = new HashMap<String, FilterRegistration>();
        for (Map.Entry<String, FilterInfo> entry : deploymentInfo.getFilters().entrySet()) {
            ret.put(entry.getKey(), new FilterRegistrationImpl(entry.getValue(), deploymentInfo));
        }
        return ret;
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return null;
    }

    @Override
    public void setSessionTrackingModes(final Set<SessionTrackingMode> sessionTrackingModes) {

    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return null;
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        return null;
    }

    @Override
    public void addListener(final String className) {
        try {
            Class<? extends EventListener> clazz = (Class<? extends EventListener>) deploymentInfo.getClassLoader().loadClass(className);
            addListener(clazz);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T extends EventListener> void addListener(final T t) {
        deploymentInfo.addListener(new ListenerInfo(t.getClass(), new ImmediateInstanceFactory<EventListener>(t)));
    }

    @Override
    public void addListener(final Class<? extends EventListener> listenerClass) {
        InstanceFactory<? extends EventListener> factory = deploymentInfo.getClassIntrospecter().createInstanceFactory(listenerClass);
        deploymentInfo.addListener(new ListenerInfo(listenerClass, factory));
    }

    @Override
    public <T extends EventListener> T createListener(final Class<T> clazz) throws ServletException {
        try {
            return deploymentInfo.getClassIntrospecter().createInstanceFactory(clazz).createInstance().getInstance();
        } catch (InstantiationException e) {
            throw new ServletException(e);
        }
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        return deploymentInfo.getClassLoader();
    }

    @Override
    public void declareRoles(final String... roleNames) {
    }

    public Deployment getDeployment() {
        return deployment;
    }
}
