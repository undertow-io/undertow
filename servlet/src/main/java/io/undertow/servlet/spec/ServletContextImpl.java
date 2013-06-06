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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
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
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;

import io.undertow.Version;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionManager;
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
import io.undertow.servlet.core.ApplicationListeners;
import io.undertow.servlet.core.ManagedListener;
import io.undertow.servlet.handlers.ServletChain;
import io.undertow.servlet.util.EmptyEnumeration;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.servlet.util.IteratorEnumeration;
import io.undertow.util.AttachmentKey;

/**
 * @author Stuart Douglas
 */
public class ServletContextImpl implements ServletContext {

    private final ServletContainer servletContainer;
    private final Deployment deployment;
    private final DeploymentInfo deploymentInfo;
    private final ConcurrentMap<String, Object> attributes;
    private final SessionCookieConfigImpl sessionCookieConfig;
    private final FormParserFactory formParserFactory;
    private final AttachmentKey<HttpSessionImpl> sessionAttachmentKey = AttachmentKey.create(HttpSessionImpl.class);
    private volatile Set<SessionTrackingMode> sessionTrackingModes = Collections.singleton(SessionTrackingMode.COOKIE);
    private volatile boolean initialized = false;


    public ServletContextImpl(final ServletContainer servletContainer, final Deployment deployment) {
        this.servletContainer = servletContainer;
        this.deployment = deployment;
        this.deploymentInfo = deployment.getDeploymentInfo();
        sessionCookieConfig = new SessionCookieConfigImpl();
        SessionCookieConfig sc = deploymentInfo.getSessionCookieConfig();
        if(sc != null) {
            sessionCookieConfig.setName(sc.getName());
            sessionCookieConfig.setComment(sc.getComment());
            sessionCookieConfig.setDomain(sc.getDomain());
            sessionCookieConfig.setHttpOnly(sc.isHttpOnly());
            sessionCookieConfig.setMaxAge(sc.getMaxAge());
            sessionCookieConfig.setPath(sc.getPath());
            sessionCookieConfig.setSecure(sc.isSecure());
        }
        if(deploymentInfo.getServletContextAttributeBackingMap() == null) {
            this.attributes = new ConcurrentHashMap<>();
        } else {
            this.attributes = deploymentInfo.getServletContextAttributeBackingMap();
        }
        attributes.putAll(deployment.getDeploymentInfo().getServletContextAttributes());
        this.formParserFactory = deployment.getDeploymentInfo().getFormParserFactory();
    }

    public void initDone() {
        initialized = true;
    }

    public FormParserFactory getFormParserFactory() {
        return formParserFactory;
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
        return 1;
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
        int pos = file.lastIndexOf('.');
        if (pos == -1) {
            return deployment.getMimeExtensionMappings().get(file);
        }
        return deployment.getMimeExtensionMappings().get(file.substring(pos + 1));
    }

    @Override
    public Set<String> getResourcePaths(final String path) {
        final Resource resource;
        try {
            resource = deploymentInfo.getResourceManager().getResource(path);
        } catch (IOException e) {
            return null;
        }
        if (resource == null || !resource.isDirectory()) {
            return null;
        }
        final Set<String> resources = new HashSet<String>();
        for (Resource res : resource.list()) {
            Path file = res.getFile();
            if(file != null) {
                resources.add(file.toString());
            }
        }
        return resources;
    }

    @Override
    public URL getResource(final String path) throws MalformedURLException {
        if (!path.startsWith("/")) {
            throw UndertowServletMessages.MESSAGES.pathMustStartWithSlash(path);
        }
        Resource resource = null;
        try {
            resource = deploymentInfo.getResourceManager().getResource(path);
        } catch (IOException e) {
            return null;
        }
        if (resource == null) {
            return null;
        }
        return resource.getUrl();
    }

    @Override
    public InputStream getResourceAsStream(final String path) {
        Resource resource = null;
        try {
            resource = deploymentInfo.getResourceManager().getResource(path);
        } catch (IOException e) {
            return null;
        }
        if (resource == null) {
            return null;
        }
        try {
            if(resource.getFile() != null) {
                return new BufferedInputStream(new FileInputStream(resource.getFile().toFile()));
            } else {
                return new BufferedInputStream(resource.getUrl().openStream());
            }
        } catch (FileNotFoundException e) {
            //should never happen, as the resource loader should return null in this case
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public RequestDispatcher getRequestDispatcher(final String path) {
        return new RequestDispatcherImpl(path, this);
    }

    @Override
    public RequestDispatcher getNamedDispatcher(final String name) {
        ServletChain chain = deployment.getServletPaths().getServletHandlerByName(name);
        if (chain != null) {
            return new RequestDispatcherImpl(chain, this);
        } else {
            return null;
        }
    }

    @Override
    public Servlet getServlet(final String name) throws ServletException {
        return deployment.getServletPaths().getServletHandlerByName(name).getManagedServlet().getServlet().getInstance();
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
        if (path==null){
            return null;
        }
        Resource resource = null;
        try {
            resource = deploymentInfo.getResourceManager().getResource(path);
        } catch (IOException e) {
            return null;
        }
        if(resource == null) {
            return null;
        }
        Path file = resource.getFile();
        if(file == null) {
            return null;
        }
        return file.toAbsolutePath().toString();
    }

    @Override
    public String getServerInfo() {
        return  Version.getFullVersionString();
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
        deploymentInfo.addInitParameter(name, value);
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

        if (object == null) {
            Object existing = attributes.remove(name);
            if (existing != null) {
                deployment.getApplicationListeners().servletContextAttributeRemoved(name, existing);
            }
        } else {
            Object existing = attributes.put(name, object);
            if (existing != null) {
                deployment.getApplicationListeners().servletContextAttributeReplaced(name, existing);
            } else {
                deployment.getApplicationListeners().servletContextAttributeAdded(name, object);
            }
        }
    }

    @Override
    public void removeAttribute(final String name) {
        Object exiting = attributes.remove(name);
        deployment.getApplicationListeners().servletContextAttributeRemoved(name, exiting);
    }

    @Override
    public String getServletContextName() {
        return deploymentInfo.getDisplayName();
    }

    @Override
    public ServletRegistration.Dynamic addServlet(final String servletName, final String className) {
        ensureNotProgramaticListener();
        ensureNotInitialized();
        try {
            if(deploymentInfo.getServlets().containsKey(servletName)) {
                return null;
            }
            ServletInfo servlet = new ServletInfo(servletName, (Class<? extends Servlet>) deploymentInfo.getClassLoader().loadClass(className));
            deploymentInfo.addServlet(servlet);
            deployment.getServlets().addServlet(servlet);
            return new ServletRegistrationImpl(servlet, deployment);
        } catch (ClassNotFoundException e) {
            throw UndertowServletMessages.MESSAGES.cannotLoadClass(className, e);
        }
    }

    @Override
    public ServletRegistration.Dynamic addServlet(final String servletName, final Servlet servlet) {
        ensureNotProgramaticListener();
        ensureNotInitialized();
        if(deploymentInfo.getServlets().containsKey(servletName)) {
            return null;
        }
        ServletInfo s = new ServletInfo(servletName, servlet.getClass(), new ImmediateInstanceFactory<Servlet>(servlet));
        deploymentInfo.addServlet(s);
        deployment.getServlets().addServlet(s);
        return new ServletRegistrationImpl(s, deployment);
    }

    @Override
    public ServletRegistration.Dynamic addServlet(final String servletName, final Class<? extends Servlet> servletClass) {
        ensureNotProgramaticListener();
        ensureNotInitialized();
        if(deploymentInfo.getServlets().containsKey(servletName)) {
            return null;
        }
        ServletInfo servlet = new ServletInfo(servletName, servletClass);
        deploymentInfo.addServlet(servlet);
        deployment.getServlets().addServlet(servlet);
        return new ServletRegistrationImpl(servlet, deployment);
    }

    @Override
    public <T extends Servlet> T createServlet(final Class<T> clazz) throws ServletException {
        ensureNotProgramaticListener();
        try {
            return deploymentInfo.getClassIntrospecter().createInstanceFactory(clazz).createInstance().getInstance();
        } catch (InstantiationException e) {
            throw UndertowServletMessages.MESSAGES.couldNotInstantiateComponent(clazz.getName(), e);
        } catch (NoSuchMethodException e) {
            throw UndertowServletMessages.MESSAGES.couldNotInstantiateComponent(clazz.getName(), e);
        }
    }

    @Override
    public ServletRegistration getServletRegistration(final String servletName) {
        ensureNotProgramaticListener();
        final ServletInfo servlet = deploymentInfo.getServlets().get(servletName);
        if(servlet == null) {
            return null;
        }
        return new ServletRegistrationImpl(servlet, deployment);
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        ensureNotProgramaticListener();
        final Map<String, ServletRegistration> ret = new HashMap<String, ServletRegistration>();
        for (Map.Entry<String, ServletInfo> entry : deploymentInfo.getServlets().entrySet()) {
            ret.put(entry.getKey(), new ServletRegistrationImpl(entry.getValue(), deployment));
        }
        return ret;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(final String filterName, final String className) {
        ensureNotProgramaticListener();
        ensureNotInitialized();
        if(deploymentInfo.getFilters().containsKey(filterName)) {
            return null;
        }
        try {
            FilterInfo filter = new FilterInfo(filterName, (Class<? extends Filter>) deploymentInfo.getClassLoader().loadClass(className));
            deploymentInfo.addFilter(filter);
            deployment.getFilters().addFilter(filter);
            return new FilterRegistrationImpl(filter, deployment);
        } catch (ClassNotFoundException e) {
            throw UndertowServletMessages.MESSAGES.cannotLoadClass(className, e);
        }
    }

    @Override
    public FilterRegistration.Dynamic addFilter(final String filterName, final Filter filter) {
        ensureNotProgramaticListener();
        ensureNotInitialized();

        if(deploymentInfo.getFilters().containsKey(filterName)) {
            return null;
        }
        FilterInfo f = new FilterInfo(filterName, filter.getClass(), new ImmediateInstanceFactory<Filter>(filter));
        deploymentInfo.addFilter(f);
        deployment.getFilters().addFilter(f);
        return new FilterRegistrationImpl(f, deployment);

    }

    @Override
    public FilterRegistration.Dynamic addFilter(final String filterName, final Class<? extends Filter> filterClass) {
        ensureNotProgramaticListener();
        ensureNotInitialized();
        if(deploymentInfo.getFilters().containsKey(filterName)) {
            return null;
        }
        FilterInfo filter = new FilterInfo(filterName, filterClass);
        deploymentInfo.addFilter(filter);
        deployment.getFilters().addFilter(filter);
        return new FilterRegistrationImpl(filter, deployment);
    }

    @Override
    public <T extends Filter> T createFilter(final Class<T> clazz) throws ServletException {
        ensureNotProgramaticListener();
        try {
            return deploymentInfo.getClassIntrospecter().createInstanceFactory(clazz).createInstance().getInstance();
        } catch (InstantiationException e) {
            throw UndertowServletMessages.MESSAGES.couldNotInstantiateComponent(clazz.getName(), e);
        } catch (NoSuchMethodException e) {
            throw UndertowServletMessages.MESSAGES.couldNotInstantiateComponent(clazz.getName(), e);
        }
    }

    @Override
    public FilterRegistration getFilterRegistration(final String filterName) {
        ensureNotProgramaticListener();
        final FilterInfo filterInfo = deploymentInfo.getFilters().get(filterName);
        if (filterInfo == null) {
            return null;
        }
        return new FilterRegistrationImpl(filterInfo, deployment);
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        ensureNotProgramaticListener();
        final Map<String, FilterRegistration> ret = new HashMap<String, FilterRegistration>();
        for (Map.Entry<String, FilterInfo> entry : deploymentInfo.getFilters().entrySet()) {
            ret.put(entry.getKey(), new FilterRegistrationImpl(entry.getValue(), deployment));
        }
        return ret;
    }

    @Override
    public SessionCookieConfigImpl getSessionCookieConfig() {
        ensureNotProgramaticListener();
        return sessionCookieConfig;
    }

    @Override
    public void setSessionTrackingModes(final Set<SessionTrackingMode> sessionTrackingModes) {
        ensureNotProgramaticListener();
        ensureNotInitialized();
        this.sessionTrackingModes = new HashSet<>(sessionTrackingModes);
        //TODO: actually make this work
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        ensureNotProgramaticListener();
        return Collections.singleton(SessionTrackingMode.COOKIE);
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        ensureNotProgramaticListener();
        return Collections.unmodifiableSet(sessionTrackingModes);
    }

    @Override
    public void addListener(final String className) {
        try {
            Class<? extends EventListener> clazz = (Class<? extends EventListener>) deploymentInfo.getClassLoader().loadClass(className);
            addListener(clazz);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public <T extends EventListener> void addListener(final T t) {
        ensureNotProgramaticListener();
        ensureNotInitialized();

        if((ApplicationListeners.isInProgramaticServletContextListenerInvocation() &&
                ServletContextListener.class.isAssignableFrom(t.getClass())) ||
                ApplicationListeners.isOnlyServletContextListener(t.getClass())) {
            throw UndertowServletMessages.MESSAGES.cannotAddServletContextListener();
        }
        ListenerInfo listener = new ListenerInfo(t.getClass(), new ImmediateInstanceFactory<EventListener>(t));
        deploymentInfo.addListener(listener);
        deployment.getApplicationListeners().addListener(new ManagedListener(listener, true));
    }

    @Override
    public void addListener(final Class<? extends EventListener> listenerClass) {
        ensureNotProgramaticListener();
        ensureNotInitialized();
        if((ApplicationListeners.isInProgramaticServletContextListenerInvocation() &&
                ServletContextListener.class.isAssignableFrom(listenerClass)) ||
                ApplicationListeners.isOnlyServletContextListener(listenerClass)) {
            throw UndertowServletMessages.MESSAGES.cannotAddServletContextListener();
        }
        InstanceFactory<? extends EventListener> factory = null;
        try {
            factory = deploymentInfo.getClassIntrospecter().createInstanceFactory(listenerClass);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(e);
        }
        final ListenerInfo listener = new ListenerInfo(listenerClass, factory);
        deploymentInfo.addListener(listener);
        deployment.getApplicationListeners().addListener(new ManagedListener(listener, true));
    }

    @Override
    public <T extends EventListener> T createListener(final Class<T> clazz) throws ServletException {
        ensureNotProgramaticListener();
        if(!ApplicationListeners.isListenerClass(clazz)) {
            throw UndertowServletMessages.MESSAGES.listenerMustImplementListenerClass(clazz);
        }
        try {
            return deploymentInfo.getClassIntrospecter().createInstanceFactory(clazz).createInstance().getInstance();
        } catch (InstantiationException e) {
            throw UndertowServletMessages.MESSAGES.couldNotInstantiateComponent(clazz.getName(), e);
        } catch (NoSuchMethodException e) {
            throw UndertowServletMessages.MESSAGES.couldNotInstantiateComponent(clazz.getName(), e);
        }
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return deploymentInfo.getJspConfigDescriptor();
    }

    @Override
    public ClassLoader getClassLoader() {
        return deploymentInfo.getClassLoader();
    }

    @Override
    public void declareRoles(final String... roleNames) {
    }


    /**
     * Gets the session
     *
     * @param create
     * @return
     */
    public HttpSessionImpl getSession(final HttpServerExchange exchange, boolean create) {
        final SessionCookieConfigImpl c = getSessionCookieConfig();
        HttpSessionImpl httpSession = exchange.getAttachment(sessionAttachmentKey);
        if (httpSession != null && httpSession.isInvalid()) {
            exchange.removeAttachment(sessionAttachmentKey);
            httpSession = null;
        }
        if (httpSession == null) {
            final SessionManager sessionManager = deployment.getSessionManager();
            Session session = sessionManager.getSession(exchange, c);
            if (session != null) {
                httpSession = HttpSessionImpl.forSession(session, this, false);
                exchange.putAttachment(sessionAttachmentKey, httpSession);
            } else if (create) {
                final Session newSession = sessionManager.createSession(exchange, c);
                httpSession = HttpSessionImpl.forSession(newSession, this, true);
                exchange.putAttachment(sessionAttachmentKey, httpSession);
            }
        }
        return httpSession;
    }

    public void updateSessionAccessTime(final HttpServerExchange exchange) {
        HttpSessionImpl httpSession = getSession(exchange, false);
        if (httpSession != null) {
            httpSession.getSession().requestDone(exchange);
        }
    }

    public Deployment getDeployment() {
        return deployment;
    }

    private void ensureNotInitialized() {
        if(initialized) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
    }

    private void ensureNotProgramaticListener() {
        if(ApplicationListeners.isInProgramaticServletContextListenerInvocation()) {
            throw UndertowServletMessages.MESSAGES.cannotCallFromProgramaticListener();
        }
    }
}
