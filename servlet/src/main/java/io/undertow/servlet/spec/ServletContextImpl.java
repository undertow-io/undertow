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

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;

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
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.util.EmptyEnumeration;
import io.undertow.servlet.util.ImmediateInstanceFactory;

/**
 * @author Stuart Douglas
 */
public class ServletContextImpl implements ServletContext {

    private final ServletContainer servletContainer;
    private volatile DeploymentInfo deploymentInfo;
    private volatile boolean bootstrapComplete = false;

    public ServletContextImpl(final ServletContainer servletContainer, final DeploymentInfo deploymentInfo) {
        this.servletContainer = servletContainer;
        this.deploymentInfo = deploymentInfo;
    }

    @Override
    public String getContextPath() {
        return deploymentInfo.getContextPath();
    }

    @Override
    public ServletContext getContext(final String uripath) {
        return servletContainer.getDeploymentByPath(uripath).getServletContext();
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
        return deploymentInfo.getResourceLoader().getResourcePaths(path);
    }

    @Override
    public URL getResource(final String path) throws MalformedURLException {
        return deploymentInfo.getResourceLoader().getResource(path);
    }

    @Override
    public InputStream getResourceAsStream(final String path) {
        return deploymentInfo.getResourceLoader().getResourceAsStream(path);
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
        return null;
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return null;
    }

    @Override
    public boolean setInitParameter(final String name, final String value) {
        return false;
    }

    @Override
    public Object getAttribute(final String name) {
        return null;
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return null;
    }

    @Override
    public void setAttribute(final String name, final Object object) {

    }

    @Override
    public void removeAttribute(final String name) {

    }

    @Override
    public String getServletContextName() {
        return null;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(final String servletName, final String className) {
        return null;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(final String servletName, final Servlet servlet) {
        return null;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(final String servletName, final Class<? extends Servlet> servletClass) {
        return null;
    }

    @Override
    public <T extends Servlet> T createServlet(final Class<T> clazz) throws ServletException {
        return null;
    }

    @Override
    public ServletRegistration getServletRegistration(final String servletName) {
        return null;
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        return null;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(final String filterName, final String className) {
        return null;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(final String filterName, final Filter filter) {
        return null;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(final String filterName, final Class<? extends Filter> filterClass) {
        return null;
    }

    @Override
    public <T extends Filter> T createFilter(final Class<T> clazz) throws ServletException {
        return null;
    }

    @Override
    public FilterRegistration getFilterRegistration(final String filterName) {
        return null;
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        return null;
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
            Class clazz = deploymentInfo.getClassLoader().loadClass(className);
            addListener(clazz);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T extends EventListener> void addListener(final T t) {
        deploymentInfo.addListener(new ListenerInfo(t.getClass(), new ImmediateInstanceFactory(t)));
    }

    @Override
    public void addListener(final Class<? extends EventListener> listenerClass) {
        deploymentInfo.addListener(new ListenerInfo(listenerClass));
    }

    @Override
    public <T extends EventListener> T createListener(final Class<T> clazz) throws ServletException {
        ListenerInfo info =  deploymentInfo.getClassIntrospecter().createListenerInfo(clazz);
        try {
            return (T) info.getInstanceFactory().createInstance().getInstance();
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
}
