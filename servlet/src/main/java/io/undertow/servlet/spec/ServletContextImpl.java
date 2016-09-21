/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.undertow.servlet.spec;

import io.undertow.Version;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.cache.LRUCache;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.session.PathParameterSessionConfig;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.server.session.SslSessionConfig;
import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.HttpMethodSecurityInfo;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletSecurityInfo;
import io.undertow.servlet.api.SessionConfigWrapper;
import io.undertow.servlet.api.ThreadSetupHandler;
import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.core.ApplicationListeners;
import io.undertow.servlet.core.ManagedListener;
import io.undertow.servlet.core.ManagedServlet;
import io.undertow.servlet.handlers.ServletChain;
import io.undertow.servlet.handlers.ServletHandler;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.util.EmptyEnumeration;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.servlet.util.IteratorEnumeration;
import io.undertow.util.AttachmentKey;
import io.undertow.util.CanonicalPathUtils;

import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RunAs;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ReadListener;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionTrackingMode;
import javax.servlet.WriteListener;
import javax.servlet.annotation.HttpMethodConstraint;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.descriptor.JspConfigDescriptor;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.undertow.servlet.core.ApplicationListeners.ListenerState.NO_LISTENER;
import static io.undertow.servlet.core.ApplicationListeners.ListenerState.PROGRAMATIC_LISTENER;

/**
 * @author Stuart Douglas
 */
public class ServletContextImpl implements ServletContext {

    private final ServletContainer servletContainer;
    private final Deployment deployment;
    private volatile DeploymentInfo deploymentInfo;
    private final ConcurrentMap<String, Object> attributes;
    private final SessionCookieConfigImpl sessionCookieConfig;
    private final AttachmentKey<HttpSessionImpl> sessionAttachmentKey = AttachmentKey.create(HttpSessionImpl.class);
    private volatile Set<SessionTrackingMode> sessionTrackingModes = new HashSet<>(Arrays.asList(new SessionTrackingMode[]{SessionTrackingMode.COOKIE, SessionTrackingMode.URL}));
    private volatile Set<SessionTrackingMode> defaultSessionTrackingModes = new HashSet<>(Arrays.asList(new SessionTrackingMode[]{SessionTrackingMode.COOKIE, SessionTrackingMode.URL}));
    private volatile SessionConfig sessionConfig;
    private volatile boolean initialized = false;
    private int filterMappingUrlPatternInsertPosition = 0;
    private int filterMappingServletNameInsertPosition = 0;
    private final LRUCache<String, ContentTypeInfo> contentTypeCache;

    //I don't think these really belong here, but there is not really anywhere else for them
    //maybe we should move them into a separate class
    private final ThreadSetupHandler.Action<Void, WriteListener> onWritePossibleTask;
    private final ThreadSetupHandler.Action<Void, Runnable> runnableTask;
    private final ThreadSetupHandler.Action<Void, ReadListener> onDataAvailableTask;
    private final ThreadSetupHandler.Action<Void, ReadListener> onAllDataReadTask;
    private final ThreadSetupHandler.Action<Void, ThreadSetupHandler.Action<Void, Object>> invokeActionTask;

    public ServletContextImpl(final ServletContainer servletContainer, final Deployment deployment) {
        this.servletContainer = servletContainer;
        this.deployment = deployment;
        this.deploymentInfo = deployment.getDeploymentInfo();
        sessionCookieConfig = new SessionCookieConfigImpl(this);
        sessionCookieConfig.setPath(deploymentInfo.getContextPath());
        if (deploymentInfo.getServletContextAttributeBackingMap() == null) {
            this.attributes = new ConcurrentHashMap<>();
        } else {
            this.attributes = deploymentInfo.getServletContextAttributeBackingMap();
        }
        attributes.putAll(deployment.getDeploymentInfo().getServletContextAttributes());
        this.contentTypeCache = new LRUCache<>(deployment.getDeploymentInfo().getContentTypeCacheSize(), -1, true);
        this.onWritePossibleTask = deployment.createThreadSetupAction(new ThreadSetupHandler.Action<Void, WriteListener>() {
            @Override
            public Void call(HttpServerExchange exchange, WriteListener context) throws Exception {
                context.onWritePossible();
                return null;
            }
        });
        this.runnableTask = new ThreadSetupHandler.Action<Void, Runnable>() {
            @Override
            public Void call(HttpServerExchange exchange, Runnable context) throws Exception {
                context.run();
                return null;
            }
        };
        this.onDataAvailableTask = deployment.createThreadSetupAction(new ThreadSetupHandler.Action<Void, ReadListener>() {
            @Override
            public Void call(HttpServerExchange exchange, ReadListener context) throws Exception {
                context.onDataAvailable();
                return null;
            }
        });
        this.onAllDataReadTask = deployment.createThreadSetupAction(new ThreadSetupHandler.Action<Void, ReadListener>() {
            @Override
            public Void call(HttpServerExchange exchange, ReadListener context) throws Exception {
                context.onAllDataRead();
                return null;
            }
        });
        this.invokeActionTask = deployment.createThreadSetupAction(new ThreadSetupHandler.Action<Void, ThreadSetupHandler.Action<Void, Object>>() {
            @Override
            public Void call(HttpServerExchange exchange, ThreadSetupHandler.Action<Void, Object> context) throws Exception {
                context.call(exchange, null);
                return null;
            }
        });
    }

    public void initDone() {
        initialized = true;
        Set<SessionTrackingMode> trackingMethods = sessionTrackingModes;
        SessionConfig sessionConfig = sessionCookieConfig;
        if (trackingMethods != null && !trackingMethods.isEmpty()) {
            if (sessionTrackingModes.contains(SessionTrackingMode.SSL)) {
                sessionConfig = new SslSessionConfig(deployment.getSessionManager());
            } else {
                if (sessionTrackingModes.contains(SessionTrackingMode.COOKIE) && sessionTrackingModes.contains(SessionTrackingMode.URL)) {
                    sessionCookieConfig.setFallback(new PathParameterSessionConfig(sessionCookieConfig.getName().toLowerCase(Locale.ENGLISH)));
                } else if (sessionTrackingModes.contains(SessionTrackingMode.URL)) {
                    sessionConfig = new PathParameterSessionConfig(sessionCookieConfig.getName().toLowerCase(Locale.ENGLISH));
                }
            }
        }
        SessionConfigWrapper wrapper = deploymentInfo.getSessionConfigWrapper();
        if (wrapper != null) {
            sessionConfig = wrapper.wrap(sessionConfig, deployment);
        }
        this.sessionConfig = new ServletContextSessionConfig(sessionConfig);
    }

    @Override
    public String getContextPath() {
        String contextPath = deploymentInfo.getContextPath();
        if (contextPath.equals("/")) {
            return "";
        }
        return contextPath;
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
        if(file == null) {
            return null;
        }
        String lower = file.toLowerCase(Locale.ENGLISH);
        int pos = lower.lastIndexOf('.');
        if (pos == -1) {
            return null; //no extension
        }
        return deployment.getMimeExtensionMappings().get(lower.substring(pos + 1));
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
        final Set<String> resources = new HashSet<>();
        for (Resource res : resource.list()) {
            Path file = res.getFilePath();
            if (file != null) {
                Path base = res.getResourceManagerRootPath();
                if (base == null) {
                    resources.add(file.toString()); //not much else we can do here
                } else {
                    String filePath = file.toAbsolutePath().toString().substring(base.toAbsolutePath().toString().length());
                    filePath = filePath.replace('\\', '/'); //for windows systems
                    if (Files.isDirectory(file)) {
                        filePath = filePath + "/";
                    }
                    resources.add(filePath);
                }
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
            if (resource.getFile() != null) {
                return new BufferedInputStream(new FileInputStream(resource.getFile()));
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
        if (path == null) {
            return null;
        }
        String canonicalPath = CanonicalPathUtils.canonicalize(path);
        Resource resource;
        try {
            resource = deploymentInfo.getResourceManager().getResource(canonicalPath);

            if (resource == null) {
                //UNDERTOW-373 even though the resource does not exist we still need to return a path
                Resource deploymentRoot = deploymentInfo.getResourceManager().getResource("/");
                if(deploymentRoot == null) {
                    return null;
                }
                Path root = deploymentRoot.getFilePath();
                if(root == null) {
                    return null;
                }
                if(!canonicalPath.startsWith("/")) {
                    canonicalPath = "/" + canonicalPath;
                }
                if(File.separatorChar != '/') {
                    canonicalPath = canonicalPath.replace('/', File.separatorChar);
                }
                return root.toAbsolutePath().toString() + canonicalPath;
            }
        } catch (IOException e) {
            return null;
        }
        Path file = resource.getFilePath();
        if (file == null) {
            return null;
        }
        return file.toAbsolutePath().toString();
    }

    @Override
    public String getServerInfo() {
        return deploymentInfo.getServerName() + " - " + Version.getVersionString();
    }

    @Override
    public String getInitParameter(final String name) {
        if (name == null) {
            throw UndertowServletMessages.MESSAGES.nullName();
        }
        return deploymentInfo.getInitParameters().get(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return new IteratorEnumeration<>(deploymentInfo.getInitParameters().keySet().iterator());
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
        if (name == null) {
            throw UndertowServletMessages.MESSAGES.nullName();
        }
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return new IteratorEnumeration<>(attributes.keySet().iterator());
    }

    @Override
    public void setAttribute(final String name, final Object object) {
        if (name == null) {
            throw UndertowServletMessages.MESSAGES.nullName();
        }
        if (object == null) {
            Object existing = attributes.remove(name);
            if (deployment.getApplicationListeners() != null) {
                if (existing != null) {
                    deployment.getApplicationListeners().servletContextAttributeRemoved(name, existing);
                }
            }
        } else {
            Object existing = attributes.put(name, object);
            if (deployment.getApplicationListeners() != null) {
                if (existing != null) {
                    deployment.getApplicationListeners().servletContextAttributeReplaced(name, existing);
                } else {
                    deployment.getApplicationListeners().servletContextAttributeAdded(name, object);
                }
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
            if (deploymentInfo.getServlets().containsKey(servletName)) {
                return null;
            }
            Class<? extends Servlet> servletClass=(Class<? extends Servlet>) deploymentInfo.getClassLoader().loadClass(className);
            ServletInfo servlet = new ServletInfo(servletName, servletClass, deploymentInfo.getClassIntrospecter().createInstanceFactory(servletClass));
            readServletAnnotations(servlet);
            deploymentInfo.addServlet(servlet);
            ServletHandler handler = deployment.getServlets().addServlet(servlet);
            return new ServletRegistrationImpl(servlet, handler.getManagedServlet(), deployment);
        } catch (ClassNotFoundException e) {
            throw UndertowServletMessages.MESSAGES.cannotLoadClass(className, e);
        } catch (NoSuchMethodException e) {
            throw UndertowServletMessages.MESSAGES.couldNotCreateFactory(className,e);
        }
    }

    @Override
    public ServletRegistration.Dynamic addServlet(final String servletName, final Servlet servlet) {
        ensureNotProgramaticListener();
        ensureNotInitialized();
        if (deploymentInfo.getServlets().containsKey(servletName)) {
            return null;
        }
        ServletInfo s = new ServletInfo(servletName, servlet.getClass(), new ImmediateInstanceFactory<>(servlet));
        readServletAnnotations(s);
        deploymentInfo.addServlet(s);
        ServletHandler handler = deployment.getServlets().addServlet(s);
        return new ServletRegistrationImpl(s, handler.getManagedServlet(), deployment);
    }

    @Override
    public ServletRegistration.Dynamic addServlet(final String servletName, final Class<? extends Servlet> servletClass){
        ensureNotProgramaticListener();
        ensureNotInitialized();
        if (deploymentInfo.getServlets().containsKey(servletName)) {
            return null;
        }
        try {
            ServletInfo servlet = new ServletInfo(servletName, servletClass, deploymentInfo.getClassIntrospecter().createInstanceFactory(servletClass));
            readServletAnnotations(servlet);
            deploymentInfo.addServlet(servlet);
            ServletHandler handler = deployment.getServlets().addServlet(servlet);
            return new ServletRegistrationImpl(servlet, handler.getManagedServlet(), deployment);
        } catch (NoSuchMethodException e) {
            throw UndertowServletMessages.MESSAGES.couldNotCreateFactory(servletClass.getName(),e);
        }
    }

    @Override
    public <T extends Servlet> T createServlet(final Class<T> clazz) throws ServletException {
        ensureNotProgramaticListener();
        try {
            return deploymentInfo.getClassIntrospecter().createInstanceFactory(clazz).createInstance().getInstance();
        } catch (Exception e) {
            throw UndertowServletMessages.MESSAGES.couldNotInstantiateComponent(clazz.getName(), e);
        }
    }

    @Override
    public ServletRegistration getServletRegistration(final String servletName) {
        ensureNotProgramaticListener();
        final ManagedServlet servlet = deployment.getServlets().getManagedServlet(servletName);
        if (servlet == null) {
            return null;
        }
        return new ServletRegistrationImpl(servlet.getServletInfo(), servlet, deployment);
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        ensureNotProgramaticListener();
        final Map<String, ServletRegistration> ret = new HashMap<>();
        for (Map.Entry<String, ServletHandler> entry : deployment.getServlets().getServletHandlers().entrySet()) {
            ret.put(entry.getKey(), new ServletRegistrationImpl(entry.getValue().getManagedServlet().getServletInfo(), entry.getValue().getManagedServlet(), deployment));
        }
        return ret;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(final String filterName, final String className) {
        ensureNotProgramaticListener();
        ensureNotInitialized();
        if (deploymentInfo.getFilters().containsKey(filterName)) {
            return null;
        }
        try {
            Class<? extends Filter> filterClass=(Class<? extends Filter>) deploymentInfo.getClassLoader().loadClass(className);
            FilterInfo filter = new FilterInfo(filterName, filterClass, deploymentInfo.getClassIntrospecter().createInstanceFactory(filterClass));
            deploymentInfo.addFilter(filter);
            deployment.getFilters().addFilter(filter);
            return new FilterRegistrationImpl(filter, deployment, this);
        } catch (ClassNotFoundException e) {
            throw UndertowServletMessages.MESSAGES.cannotLoadClass(className, e);
        }catch (NoSuchMethodException e) {
            throw UndertowServletMessages.MESSAGES.couldNotCreateFactory(className,e);
        }
    }

    @Override
    public FilterRegistration.Dynamic addFilter(final String filterName, final Filter filter) {
        ensureNotProgramaticListener();
        ensureNotInitialized();

        if (deploymentInfo.getFilters().containsKey(filterName)) {
            return null;
        }
        FilterInfo f = new FilterInfo(filterName, filter.getClass(), new ImmediateInstanceFactory<>(filter));
        deploymentInfo.addFilter(f);
        deployment.getFilters().addFilter(f);
        return new FilterRegistrationImpl(f, deployment, this);

    }

    @Override
    public FilterRegistration.Dynamic addFilter(final String filterName, final Class<? extends Filter> filterClass) {
        ensureNotProgramaticListener();
        ensureNotInitialized();
        if (deploymentInfo.getFilters().containsKey(filterName)) {
            return null;
        }
        try {
            FilterInfo filter = new FilterInfo(filterName, filterClass,deploymentInfo.getClassIntrospecter().createInstanceFactory(filterClass));
            deploymentInfo.addFilter(filter);
            deployment.getFilters().addFilter(filter);
            return new FilterRegistrationImpl(filter, deployment, this);
        } catch (NoSuchMethodException e) {
            throw UndertowServletMessages.MESSAGES.couldNotCreateFactory(filterClass.getName(),e);
        }
    }

    @Override
    public <T extends Filter> T createFilter(final Class<T> clazz) throws ServletException {
        ensureNotProgramaticListener();
        try {
            return deploymentInfo.getClassIntrospecter().createInstanceFactory(clazz).createInstance().getInstance();
        } catch (Exception e) {
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
        return new FilterRegistrationImpl(filterInfo, deployment, this);
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        ensureNotProgramaticListener();
        final Map<String, FilterRegistration> ret = new HashMap<>();
        for (Map.Entry<String, FilterInfo> entry : deploymentInfo.getFilters().entrySet()) {
            ret.put(entry.getKey(), new FilterRegistrationImpl(entry.getValue(), deployment, this));
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
        if (sessionTrackingModes.size() > 1 && sessionTrackingModes.contains(SessionTrackingMode.SSL)) {
            throw UndertowServletMessages.MESSAGES.sslCannotBeCombinedWithAnyOtherMethod();
        }
        this.sessionTrackingModes = new HashSet<>(sessionTrackingModes);
        //TODO: actually make this work
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        ensureNotProgramaticListener();
        return defaultSessionTrackingModes;
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
        ensureNotInitialized();
        ensureNotProgramaticListener();
        if (ApplicationListeners.listenerState() != NO_LISTENER
                && ServletContextListener.class.isAssignableFrom(t.getClass())) {
            throw UndertowServletMessages.MESSAGES.cannotAddServletContextListener();
        }
        ListenerInfo listener = new ListenerInfo(t.getClass(), new ImmediateInstanceFactory<EventListener>(t));
        deploymentInfo.addListener(listener);
        deployment.getApplicationListeners().addListener(new ManagedListener(listener, true));
    }

    @Override
    public void addListener(final Class<? extends EventListener> listenerClass) {
        ensureNotInitialized();
        ensureNotProgramaticListener();
        if (ApplicationListeners.listenerState() != NO_LISTENER
                && ServletContextListener.class.isAssignableFrom(listenerClass)) {
            throw UndertowServletMessages.MESSAGES.cannotAddServletContextListener();
        }
        InstanceFactory<? extends EventListener> factory = null;
        try {
            factory = deploymentInfo.getClassIntrospecter().createInstanceFactory(listenerClass);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        final ListenerInfo listener = new ListenerInfo(listenerClass, factory);
        deploymentInfo.addListener(listener);
        deployment.getApplicationListeners().addListener(new ManagedListener(listener, true));
    }

    @Override
    public <T extends EventListener> T createListener(final Class<T> clazz) throws ServletException {
        ensureNotProgramaticListener();
        if (!ApplicationListeners.isListenerClass(clazz)) {
            throw UndertowServletMessages.MESSAGES.listenerMustImplementListenerClass(clazz);
        }
        try {
            return deploymentInfo.getClassIntrospecter().createInstanceFactory(clazz).createInstance().getInstance();
        } catch (Exception e) {
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

    @Override
    public String getVirtualServerName() {
        return deployment.getDeploymentInfo().getHostName();
    }

    /**
     * Gets the session with the specified ID if it exists
     *
     * @param sessionId The session ID
     * @return The session
     */
    public HttpSessionImpl getSession(final String sessionId) {
        final SessionManager sessionManager = deployment.getSessionManager();
        Session session = sessionManager.getSession(sessionId);
        if (session != null) {
            return SecurityActions.forSession(session, this, false);
        }
        return null;
    }

    public HttpSessionImpl getSession(final ServletContextImpl originalServletContext, final HttpServerExchange exchange, boolean create) {
        SessionConfig c = originalServletContext.getSessionConfig();
        HttpSessionImpl httpSession = exchange.getAttachment(sessionAttachmentKey);
        if (httpSession != null && httpSession.isInvalid()) {
            exchange.removeAttachment(sessionAttachmentKey);
            httpSession = null;
        }
        if (httpSession == null) {
            final SessionManager sessionManager = deployment.getSessionManager();
            Session session = sessionManager.getSession(exchange, c);
            if (session != null) {
                httpSession = SecurityActions.forSession(session, this, false);
                exchange.putAttachment(sessionAttachmentKey, httpSession);
            } else if (create) {

                String existing = c.findSessionId(exchange);
                if (originalServletContext != this) {
                    //this is a cross context request
                    //we need to make sure there is a top level session
                    originalServletContext.getSession(originalServletContext, exchange, true);
                } else if (existing != null) {
                    c.clearSession(exchange, existing);
                }

                final Session newSession = sessionManager.createSession(exchange, c);
                httpSession = SecurityActions.forSession(newSession, this, true);
                exchange.putAttachment(sessionAttachmentKey, httpSession);
            }
        }
        return httpSession;
    }

    /**
     * Gets the session
     *
     * @param create
     * @return
     */
    public HttpSessionImpl getSession(final HttpServerExchange exchange, boolean create) {
        return getSession(this, exchange, create);
    }

    public void updateSessionAccessTime(final HttpServerExchange exchange) {
        HttpSessionImpl httpSession = getSession(exchange, false);
        if (httpSession != null) {
            Session underlyingSession;
            if (System.getSecurityManager() == null) {
                underlyingSession = httpSession.getSession();
            } else {
                underlyingSession = AccessController.doPrivileged(new HttpSessionImpl.UnwrapSessionAction(httpSession));
            }
            underlyingSession.requestDone(exchange);
        }
    }

    public Deployment getDeployment() {
        return deployment;
    }

    private void ensureNotInitialized() {
        if (initialized) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
    }

    private void ensureNotProgramaticListener() {
        if (ApplicationListeners.listenerState() == PROGRAMATIC_LISTENER) {
            throw UndertowServletMessages.MESSAGES.cannotCallFromProgramaticListener();
        }
    }

    boolean isInitialized() {
        return initialized;
    }

    public SessionConfig getSessionConfig() {
        return sessionConfig;
    }

    public void destroy() {
        attributes.clear();
        deploymentInfo = null;
    }

    private void readServletAnnotations(ServletInfo servlet) {
        if (System.getSecurityManager() == null) {
            new ReadServletAnnotationsTask(servlet, deploymentInfo).run();
        } else {
            AccessController.doPrivileged(new ReadServletAnnotationsTask(servlet, deploymentInfo));
        }
    }

    public void setDefaultSessionTrackingModes(HashSet<SessionTrackingMode> sessionTrackingModes) {
        this.defaultSessionTrackingModes = sessionTrackingModes;
        this.sessionTrackingModes = sessionTrackingModes;
    }

    void invokeOnWritePossible(HttpServerExchange exchange, WriteListener listener) {
        try {
            this.onWritePossibleTask.call(exchange, listener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void invokeOnAllDataRead(HttpServerExchange exchange, ReadListener listener) {
        try {
            this.onAllDataReadTask.call(exchange, listener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void invokeOnDataAvailable(HttpServerExchange exchange, ReadListener listener) {
        try {
            this.onDataAvailableTask.call(exchange, listener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void invokeAction(HttpServerExchange exchange, ThreadSetupHandler.Action<Void, Object> listener) {
        try {
            this.invokeActionTask.call(exchange, listener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void invokeRunnable(HttpServerExchange exchange, Runnable runnable) {
        final boolean setupRequired = SecurityActions.currentServletRequestContext() == null;
        if(setupRequired) {
            try {
                this.runnableTask.call(exchange, runnable);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            runnable.run();
        }
    }
    private static final class ReadServletAnnotationsTask implements PrivilegedAction<Void> {

        private final ServletInfo servletInfo;
        private final DeploymentInfo deploymentInfo;

        private ReadServletAnnotationsTask(ServletInfo servletInfo, DeploymentInfo deploymentInfo) {
            this.servletInfo = servletInfo;
            this.deploymentInfo = deploymentInfo;
        }

        @Override
        public Void run() {
            final ServletSecurity security = servletInfo.getServletClass().getAnnotation(ServletSecurity.class);
            if (security != null) {

                ServletSecurityInfo servletSecurityInfo = new ServletSecurityInfo()
                        .setEmptyRoleSemantic(security.value().value() == ServletSecurity.EmptyRoleSemantic.DENY ? SecurityInfo.EmptyRoleSemantic.DENY : SecurityInfo.EmptyRoleSemantic.PERMIT)
                        .setTransportGuaranteeType(security.value().transportGuarantee() == ServletSecurity.TransportGuarantee.CONFIDENTIAL ? TransportGuaranteeType.CONFIDENTIAL : TransportGuaranteeType.NONE)
                        .addRolesAllowed(security.value().rolesAllowed());
                for (HttpMethodConstraint constraint : security.httpMethodConstraints()) {
                    servletSecurityInfo.addHttpMethodSecurityInfo(new HttpMethodSecurityInfo()
                            .setMethod(constraint.value()))
                            .setEmptyRoleSemantic(constraint.emptyRoleSemantic() == ServletSecurity.EmptyRoleSemantic.DENY ? SecurityInfo.EmptyRoleSemantic.DENY : SecurityInfo.EmptyRoleSemantic.PERMIT)
                            .setTransportGuaranteeType(constraint.transportGuarantee() == ServletSecurity.TransportGuarantee.CONFIDENTIAL ? TransportGuaranteeType.CONFIDENTIAL : TransportGuaranteeType.NONE)
                            .addRolesAllowed(constraint.rolesAllowed());
                }
                servletInfo.setServletSecurityInfo(servletSecurityInfo);
            }
            final MultipartConfig multipartConfig = servletInfo.getServletClass().getAnnotation(MultipartConfig.class);
            if (multipartConfig != null) {
                servletInfo.setMultipartConfig(new MultipartConfigElement(multipartConfig.location(), multipartConfig.maxFileSize(), multipartConfig.maxRequestSize(), multipartConfig.fileSizeThreshold()));
            }
            final RunAs runAs = servletInfo.getServletClass().getAnnotation(RunAs.class);
            if (runAs != null) {
                servletInfo.setRunAs(runAs.value());
            }
            final DeclareRoles declareRoles = servletInfo.getServletClass().getAnnotation(DeclareRoles.class);
            if (declareRoles != null) {
                deploymentInfo.addSecurityRoles(declareRoles.value());
            }
            return null;
        }
    }

    void addMappingForServletNames(FilterInfo filterInfo, final EnumSet<DispatcherType> dispatcherTypes, final boolean isMatchAfter, final String... servletNames) {
        DeploymentInfo deploymentInfo = deployment.getDeploymentInfo();

        for (final String servlet : servletNames) {
            if (isMatchAfter) {
                if (dispatcherTypes == null || dispatcherTypes.isEmpty()) {
                    deploymentInfo.addFilterServletNameMapping(filterInfo.getName(), servlet, DispatcherType.REQUEST);
                } else {
                    for (final DispatcherType dispatcher : dispatcherTypes) {
                        deploymentInfo.addFilterServletNameMapping(filterInfo.getName(), servlet, dispatcher);
                    }
                }
            } else {
                if (dispatcherTypes == null || dispatcherTypes.isEmpty()) {
                    deploymentInfo.insertFilterServletNameMapping(filterMappingServletNameInsertPosition++, filterInfo.getName(), servlet, DispatcherType.REQUEST);
                } else {
                    for (final DispatcherType dispatcher : dispatcherTypes) {
                        deploymentInfo.insertFilterServletNameMapping(filterMappingServletNameInsertPosition++, filterInfo.getName(), servlet, dispatcher);
                    }
                }
            }
        }
        deployment.getServletPaths().invalidate();
    }

    void addMappingForUrlPatterns(FilterInfo filterInfo, final EnumSet<DispatcherType> dispatcherTypes, final boolean isMatchAfter, final String... urlPatterns) {
        DeploymentInfo deploymentInfo = deployment.getDeploymentInfo();
        for (final String url : urlPatterns) {
            if (isMatchAfter) {
                if (dispatcherTypes == null || dispatcherTypes.isEmpty()) {
                    deploymentInfo.addFilterUrlMapping(filterInfo.getName(), url, DispatcherType.REQUEST);
                } else {
                    for (final DispatcherType dispatcher : dispatcherTypes) {
                        deploymentInfo.addFilterUrlMapping(filterInfo.getName(), url, dispatcher);
                    }
                }
            } else {
                if (dispatcherTypes == null || dispatcherTypes.isEmpty()) {
                    deploymentInfo.insertFilterUrlMapping(filterMappingUrlPatternInsertPosition++, filterInfo.getName(), url, DispatcherType.REQUEST);
                } else {
                    for (final DispatcherType dispatcher : dispatcherTypes) {
                        deploymentInfo.insertFilterUrlMapping(filterMappingUrlPatternInsertPosition++, filterInfo.getName(), url, dispatcher);
                    }
                }
            }
        }
        deployment.getServletPaths().invalidate();
    }

    ContentTypeInfo parseContentType(String type) {
        ContentTypeInfo existing = contentTypeCache.get(type);
        if(existing != null) {
            return existing;
        }
        String contentType = type;
        String charset = null;

        int split = type.indexOf(";");
        if (split != -1) {
            int pos = type.indexOf("charset=");
            if (pos != -1) {
                int i = pos + "charset=".length();
                do {
                    char c = type.charAt(i);
                    if (c == ' ' || c == '\t' || c == ';') {
                        break;
                    }
                    ++i;
                } while (i < type.length());
                charset = type.substring(pos + "charset=".length(), i);
                //it is valid for the charset to be enclosed in quotes
                if (charset.startsWith("\"") && charset.endsWith("\"") && charset.length() > 1) {
                    charset = charset.substring(1, charset.length() - 1);
                }

                int charsetStart = pos;
                while (type.charAt(--charsetStart) != ';' && charsetStart > 0) {
                }
                StringBuilder contentTypeBuilder = new StringBuilder();
                contentTypeBuilder.append(type.substring(0, charsetStart));
                if (i != type.length()) {
                    contentTypeBuilder.append(type.substring(i));
                }
                contentType = contentTypeBuilder.toString();
            }
            //strip any trailing semicolon
            for (int i = contentType.length() - 1; i >= 0; --i) {
                char c = contentType.charAt(i);
                if (c == ' ' || c == '\t') {
                    continue;
                }
                if (c == ';') {
                    contentType = contentType.substring(0, i);
                }
                break;
            }
        }
        if(charset == null) {
            existing = new ContentTypeInfo(contentType, null, contentType);
        } else {
            existing = new ContentTypeInfo(contentType + ";charset=" + charset, charset,  contentType);
        }
        contentTypeCache.add(type, existing);
        return existing;
    }

    /**
     * This is a bit of a hack to make sure than an invalidated session ID is not re-used. It also allows {@link io.undertow.servlet.handlers.ServletRequestContext#getOverridenSessionId()} to be used.
     */
    static final class ServletContextSessionConfig implements SessionConfig {

        private final AttachmentKey<String> INVALIDATED = AttachmentKey.create(String.class);

        private final SessionConfig delegate;

        private ServletContextSessionConfig(SessionConfig delegate) {
            this.delegate = delegate;
        }

        @Override
        public void setSessionId(HttpServerExchange exchange, String sessionId) {
            delegate.setSessionId(exchange, sessionId);
        }

        @Override
        public void clearSession(HttpServerExchange exchange, String sessionId) {
            exchange.putAttachment(INVALIDATED, sessionId);
            delegate.clearSession(exchange, sessionId);
        }

        @Override
        public String findSessionId(HttpServerExchange exchange) {
            String invalidated = exchange.getAttachment(INVALIDATED);
            ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
            final String current;
            if(src.getOverridenSessionId() == null) {
                current = delegate.findSessionId(exchange);
            } else {
                current = src.getOverridenSessionId();
            }
            if(invalidated == null) {
                return current;
            }
            if(invalidated.equals(current)) {
                return null;
            }
            return current;
        }

        @Override
        public SessionCookieSource sessionCookieSource(HttpServerExchange exchange) {
            return delegate.sessionCookieSource(exchange);
        }

        @Override
        public String rewriteUrl(String originalUrl, String sessionId) {
            return delegate.rewriteUrl(originalUrl, sessionId);
        }

        public SessionConfig getDelegate() {
            return delegate;
        }
    }
}
