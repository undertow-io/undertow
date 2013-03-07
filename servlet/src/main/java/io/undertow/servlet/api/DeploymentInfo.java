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

package io.undertow.servlet.api;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.servlet.DispatcherType;
import javax.servlet.descriptor.JspConfigDescriptor;

import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.util.DefaultClassIntrospector;

/**
 * Represents a servlet deployment.
 *
 * @author Stuart Douglas
 */
public class DeploymentInfo implements Cloneable {

    private volatile String deploymentName;
    private volatile String displayName;
    private volatile String contextPath;
    private volatile ClassLoader classLoader;
    private volatile ResourceLoader resourceLoader = ResourceLoader.EMPTY_RESOURCE_LOADER;
    private volatile ClassIntrospecter classIntrospecter = DefaultClassIntrospector.INSTANCE;
    private volatile int majorVersion = 3;
    private volatile int minorVersion;
    private volatile InstanceFactory<Executor> executorFactory;
    private volatile InstanceFactory<Executor> asyncExecutorFactory;
    private volatile File tempDir;
    private volatile JspConfigDescriptor jspConfigDescriptor;
    private volatile DefaultServletConfig defaultServletConfig;
    private volatile SessionManager sessionManager = new InMemorySessionManager();
    private volatile LoginConfig loginConfig;
    private volatile IdentityManager identityManager;
    private volatile ConfidentialPortManager confidentialPortManager;
    private final Map<String, ServletInfo> servlets = new HashMap<String, ServletInfo>();
    private final Map<String, FilterInfo> filters = new HashMap<String, FilterInfo>();
    private final List<FilterMappingInfo> filterServletNameMappings = new ArrayList<FilterMappingInfo>();
    private final List<FilterMappingInfo> filterUrlMappings = new ArrayList<FilterMappingInfo>();
    private final List<ListenerInfo> listeners = new ArrayList<ListenerInfo>();
    private final List<ServletContainerInitializerInfo> servletContainerInitializers = new ArrayList<ServletContainerInitializerInfo>();
    private final List<ThreadSetupAction> threadSetupActions = new ArrayList<ThreadSetupAction>();
    private final Map<String, String> initParameters = new HashMap<String, String>();
    private final Map<String, Object> servletContextAttributes = new HashMap<String, Object>();
    private final Map<String, String> localeCharsetMapping = new HashMap<String, String>();
    private final List<String> welcomePages = new ArrayList<String>();
    private final List<ErrorPage> errorPages = new ArrayList<ErrorPage>();
    private final List<MimeMapping> mimeMappings = new ArrayList<MimeMapping>();
    private final List<SecurityConstraint> securityConstraints = new ArrayList<SecurityConstraint>();
    private final Map<String, Set<String>> principleVsRoleMapping = new HashMap<String, Set<String>>();
    private final Set<String> securityRoles = new HashSet<String>();

    /**
     * Handler chain wrappers that are applied outside all other handlers, including security but after the initial
     * servlet handler.
     */
    private final List<HandlerWrapper> outerHandlerChainWrappers = new ArrayList<>();

    /**
     * Handler chain wrappers that are applied just before the servlet request is dispatched. At this point the security
     * handlers have run, and any security information is attached to the request.
     */
    private final List<HandlerWrapper> innerHandlerChainWrappers = new ArrayList<>();

    /**
     * Wrapper that is applied after the servlet request has been dispatched, but before any user code is run. This
     * is run outside any wrappers applied via {@link ServletInfo#handlerChainWrappers}
     */
    private final List<HandlerWrapper> dispatchedHandlerChainWrappers = new ArrayList<>();

    public void validate() {
        if (deploymentName == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("deploymentName");
        }
        if (contextPath == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("contextName");
        }
        if (classLoader == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("classLoader");
        }
        if (resourceLoader == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("resourceLoader");
        }
        if (classIntrospecter == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("classIntrospecter");
        }

        for (final ServletInfo servlet : this.servlets.values()) {
            servlet.validate();
        }
        for (final FilterInfo filter : this.filters.values()) {
            filter.validate();
        }
    }

    public String getDeploymentName() {
        return deploymentName;
    }

    public DeploymentInfo setDeploymentName(final String deploymentName) {
        this.deploymentName = deploymentName;
        return this;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(final String displayName) {
        this.displayName = displayName;
    }

    public String getContextPath() {
        return contextPath;
    }

    public DeploymentInfo setContextPath(final String contextPath) {
        this.contextPath = contextPath;
        return this;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public DeploymentInfo setClassLoader(final ClassLoader classLoader) {
        this.classLoader = classLoader;
        return this;
    }

    public ResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    public DeploymentInfo setResourceLoader(final ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        return this;
    }

    public ClassIntrospecter getClassIntrospecter() {
        return classIntrospecter;
    }

    public DeploymentInfo setClassIntrospecter(final ClassIntrospecter classIntrospecter) {
        this.classIntrospecter = classIntrospecter;
        return this;
    }

    public DeploymentInfo addServlet(final ServletInfo servlet) {
        servlets.put(servlet.getName(), servlet);
        return this;
    }

    public DeploymentInfo addServlets(final ServletInfo... servlets) {
        for (final ServletInfo servlet : servlets) {
            this.servlets.put(servlet.getName(), servlet);
        }
        return this;
    }

    public DeploymentInfo addServlets(final Collection<ServletInfo> servlets) {
        for (final ServletInfo servlet : servlets) {
            this.servlets.put(servlet.getName(), servlet);
        }
        return this;
    }

    public Map<String, ServletInfo> getServlets() {
        return Collections.unmodifiableMap(servlets);
    }


    public DeploymentInfo addFilter(final FilterInfo filter) {
        filters.put(filter.getName(), filter);
        return this;
    }

    public DeploymentInfo addFilters(final FilterInfo... filters) {
        for (final FilterInfo filter : filters) {
            this.filters.put(filter.getName(), filter);
        }
        return this;
    }

    public DeploymentInfo addFilters(final Collection<FilterInfo> filters) {
        for (final FilterInfo filter : filters) {
            this.filters.put(filter.getName(), filter);
        }
        return this;
    }

    public Map<String, FilterInfo> getFilters() {
        return Collections.unmodifiableMap(filters);
    }

    public DeploymentInfo addFilterUrlMapping(final String filterName, final String mapping, DispatcherType dispatcher) {
        filterUrlMappings.add(new FilterMappingInfo(filterName, FilterMappingInfo.MappingType.URL, mapping, dispatcher));
        return this;
    }

    public DeploymentInfo addFilterServletNameMapping(final String filterName, final String mapping, DispatcherType dispatcher) {
        filterServletNameMappings.add(new FilterMappingInfo(filterName, FilterMappingInfo.MappingType.SERVLET, mapping, dispatcher));
        return this;
    }

    public DeploymentInfo insertFilterUrlMapping(final int pos, final String filterName, final String mapping, DispatcherType dispatcher) {
        filterUrlMappings.add(pos, new FilterMappingInfo(filterName, FilterMappingInfo.MappingType.URL, mapping, dispatcher));
        return this;
    }

    public DeploymentInfo insertFilterServletNameMapping(final int pos, final String filterName, final String mapping, DispatcherType dispatcher) {
        filterServletNameMappings.add(pos, new FilterMappingInfo(filterName, FilterMappingInfo.MappingType.SERVLET, mapping, dispatcher));
        return this;
    }

    public List<FilterMappingInfo> getFilterMappings() {
        final ArrayList<FilterMappingInfo> ret = new ArrayList<FilterMappingInfo>(filterUrlMappings);
        ret.addAll(filterServletNameMappings);
        return Collections.unmodifiableList(ret);
    }


    public DeploymentInfo addListener(final ListenerInfo listener) {
        listeners.add(listener);
        return this;
    }

    public DeploymentInfo addListeners(final ListenerInfo... listeners) {
        this.listeners.addAll(Arrays.asList(listeners));
        return this;
    }

    public DeploymentInfo addListeners(final Collection<ListenerInfo> listeners) {
        this.listeners.addAll(listeners);
        return this;
    }

    public List<ListenerInfo> getListeners() {
        return listeners;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public DeploymentInfo setMajorVersion(final int majorVersion) {
        this.majorVersion = majorVersion;
        return this;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public DeploymentInfo setMinorVersion(final int minorVersion) {
        this.minorVersion = minorVersion;
        return this;
    }

    public DeploymentInfo addServletContainerInitalizer(final ServletContainerInitializerInfo servletContainerInitializer) {
        servletContainerInitializers.add(servletContainerInitializer);
        return this;
    }

    public DeploymentInfo addServletContainerInitalizers(final ServletContainerInitializerInfo... servletContainerInitializer) {
        servletContainerInitializers.addAll(Arrays.asList(servletContainerInitializer));
        return this;
    }

    public DeploymentInfo addServletContainerInitalizers(final List<ServletContainerInitializerInfo> servletContainerInitializer) {
        servletContainerInitializers.addAll(servletContainerInitializer);
        return this;
    }

    public List<ServletContainerInitializerInfo> getServletContainerInitializers() {
        return servletContainerInitializers;
    }

    public DeploymentInfo addThreadSetupAction(final ThreadSetupAction action) {
        threadSetupActions.add(action);
        return this;
    }

    public List<ThreadSetupAction> getThreadSetupActions() {
        return threadSetupActions;
    }

    public DeploymentInfo addInitParameter(final String name, final String value) {
        initParameters.put(name, value);
        return this;
    }

    public Map<String, String> getInitParameters() {
        return Collections.unmodifiableMap(initParameters);
    }

    public DeploymentInfo addServletContextAttribute(final String name, final Object value) {
        servletContextAttributes.put(name, value);
        return this;
    }

    public Map<String, Object> getServletContextAttributes() {
        return Collections.unmodifiableMap(servletContextAttributes);
    }

    public DeploymentInfo addWelcomePages(final String welcomePage) {
        this.welcomePages.add(welcomePage);
        return this;
    }

    public DeploymentInfo addWelcomePages(final String... welcomePages) {
        this.welcomePages.addAll(Arrays.asList(welcomePages));
        return this;
    }

    public DeploymentInfo addWelcomePages(final Collection<String> welcomePages) {
        this.welcomePages.addAll(welcomePages);
        return this;
    }

    public List<String> getWelcomePages() {
        return Collections.unmodifiableList(welcomePages);
    }

    public DeploymentInfo addErrorPage(final ErrorPage errorPage) {
        this.errorPages.add(errorPage);
        return this;
    }

    public DeploymentInfo addErrorPages(final ErrorPage... errorPages) {
        this.errorPages.addAll(Arrays.asList(errorPages));
        return this;
    }

    public DeploymentInfo addErrorPages(final Collection<ErrorPage> errorPages) {
        this.errorPages.addAll(errorPages);
        return this;
    }

    public List<ErrorPage> getErrorPages() {
        return Collections.unmodifiableList(errorPages);
    }

    public DeploymentInfo addMimeMapping(final MimeMapping mimeMappings) {
        this.mimeMappings.add(mimeMappings);
        return this;
    }

    public DeploymentInfo addMimeMappings(final MimeMapping... mimeMappings) {
        this.mimeMappings.addAll(Arrays.asList(mimeMappings));
        return this;
    }

    public DeploymentInfo addMimeMappings(final Collection<MimeMapping> mimeMappings) {
        this.mimeMappings.addAll(mimeMappings);
        return this;
    }

    public List<MimeMapping> getMimeMappings() {
        return Collections.unmodifiableList(mimeMappings);
    }


    public DeploymentInfo addSecurityConstraint(final SecurityConstraint securityConstraint) {
        this.securityConstraints.add(securityConstraint);
        return this;
    }

    public DeploymentInfo addSecurityConstraints(final SecurityConstraint... securityConstraints) {
        this.securityConstraints.addAll(Arrays.asList(securityConstraints));
        return this;
    }

    public DeploymentInfo addSecurityConstraints(final Collection<SecurityConstraint> securityConstraints) {
        this.securityConstraints.addAll(securityConstraints);
        return this;
    }

    public List<SecurityConstraint> getSecurityConstraints() {
        return Collections.unmodifiableList(securityConstraints);
    }

    public InstanceFactory<Executor> getExecutorFactory() {
        return executorFactory;
    }

    /**
     * Sets the factory that is used to create the {@link java.util.concurrent.ExecutorService} that is used to run servlet
     * invocations.
     * <p/>
     * If this is null then the current executor is used, which is generally the XNIO worker pool
     *
     * @param executorFactory The executor factory
     */
    public void setExecutorFactory(final InstanceFactory<Executor> executorFactory) {
        this.executorFactory = executorFactory;
    }

    public InstanceFactory<Executor> getAsyncExecutorFactory() {
        return asyncExecutorFactory;
    }

    /**
     * Sets the factory that is used to create the {@link java.util.concurrent.ExecutorService} that is used to run async tasks.
     * <p/>
     * If this is null then {@link #executorFactory} is used, if this is also null then the default is used
     *
     * @param asyncExecutorFactory The executor factory
     */
    public void setAsyncExecutorFactory(final InstanceFactory<Executor> asyncExecutorFactory) {
        this.asyncExecutorFactory = asyncExecutorFactory;
    }

    public File getTempDir() {
        return tempDir;
    }

    public void setTempDir(final File tempDir) {
        this.tempDir = tempDir;
    }

    public JspConfigDescriptor getJspConfigDescriptor() {
        return jspConfigDescriptor;
    }

    public void setJspConfigDescriptor(JspConfigDescriptor jspConfigDescriptor) {
        this.jspConfigDescriptor = jspConfigDescriptor;
    }

    public DefaultServletConfig getDefaultServletConfig() {
        return defaultServletConfig;
    }

    public DeploymentInfo setDefaultServletConfig(final DefaultServletConfig defaultServletConfig) {
        this.defaultServletConfig = defaultServletConfig;
        return this;
    }

    public DeploymentInfo addLocaleCharsetMapping(final String locale, final String charset) {
        localeCharsetMapping.put(locale, charset);
        return this;
    }

    public Map<String, String> getLocaleCharsetMapping() {
        return localeCharsetMapping;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public DeploymentInfo setSessionManager(final SessionManager sessionManager) {
        this.sessionManager = sessionManager;
        return this;
    }

    public LoginConfig getLoginConfig() {
        return loginConfig;
    }

    public DeploymentInfo setLoginConfig(LoginConfig loginConfig) {
        this.loginConfig = loginConfig;
        return this;
    }

    public IdentityManager getIdentityManager() {
        return identityManager;
    }

    public DeploymentInfo setIdentityManager(IdentityManager identityManager) {
        this.identityManager = identityManager;
        return this;
    }

    public ConfidentialPortManager getConfidentialPortManager() {
        return confidentialPortManager;
    }

    public DeploymentInfo setConfidentialPortManager(ConfidentialPortManager confidentialPortManager) {
        this.confidentialPortManager = confidentialPortManager;
        return this;
    }

    public DeploymentInfo addPrincipleVsRoleMapping(final String principle, final String role) {
        Set<String> roles = principleVsRoleMapping.get(principle);
        if (roles == null) {
            principleVsRoleMapping.put(principle, roles = new HashSet<String>());
        }
        roles.add(role);
        return this;
    }

    public Map<String, Set<String>> getPrincipleVsRoleMapping() {
        return Collections.unmodifiableMap(principleVsRoleMapping);
    }

    public DeploymentInfo addSecurityRole(final String role) {
        this.securityRoles.add(role);
        return this;
    }

    public DeploymentInfo addSecurityRoles(final String... roles) {
        this.securityRoles.addAll(Arrays.asList(roles));
        return this;
    }

    public DeploymentInfo addSecurityRoles(final Collection<String> roles) {
        this.securityRoles.addAll(roles);
        return this;
    }

    public Set<String> getSecurityRoles() {
        return Collections.unmodifiableSet(securityRoles);
    }

    public DeploymentInfo addOuterHandlerChainWrapper(final HandlerWrapper wrapper) {
        outerHandlerChainWrappers.add(wrapper);
        return this;
    }

    public List<HandlerWrapper> getOuterHandlerChainWrappers() {
        return Collections.unmodifiableList(outerHandlerChainWrappers);
    }

    public DeploymentInfo addInnerHandlerChainWrapper(final HandlerWrapper wrapper) {
        innerHandlerChainWrappers.add(wrapper);
        return this;
    }

    public List<HandlerWrapper> getInnerHandlerChainWrappers() {
        return Collections.unmodifiableList(innerHandlerChainWrappers);
    }

    public DeploymentInfo addDispatchedHandlerChainWrapper(final HandlerWrapper wrapper) {
        dispatchedHandlerChainWrappers.add(wrapper);
        return this;
    }

    public List<HandlerWrapper> getDispatchedHandlerChainWrappers() {
        return Collections.unmodifiableList(dispatchedHandlerChainWrappers);
    }

    @Override
    public DeploymentInfo clone() {
        final DeploymentInfo info = new DeploymentInfo()
                .setClassLoader(classLoader)
                .setContextPath(contextPath)
                .setResourceLoader(resourceLoader)
                .setMajorVersion(majorVersion)
                .setMinorVersion(minorVersion)
                .setDeploymentName(deploymentName);

        for (Map.Entry<String, ServletInfo> e : servlets.entrySet()) {
            info.addServlet(e.getValue().clone());
        }

        for (Map.Entry<String, FilterInfo> e : filters.entrySet()) {
            info.addFilter(e.getValue().clone());
        }
        info.displayName = displayName;
        info.filterUrlMappings.addAll(filterUrlMappings);
        info.filterServletNameMappings.addAll(filterServletNameMappings);
        info.listeners.addAll(listeners);
        info.servletContainerInitializers.addAll(servletContainerInitializers);
        info.threadSetupActions.addAll(threadSetupActions);
        info.initParameters.putAll(initParameters);
        info.servletContextAttributes.putAll(servletContextAttributes);
        info.welcomePages.addAll(welcomePages);
        info.errorPages.addAll(errorPages);
        info.mimeMappings.addAll(mimeMappings);
        info.executorFactory = executorFactory;
        info.asyncExecutorFactory = asyncExecutorFactory;
        info.tempDir = tempDir;
        info.jspConfigDescriptor = jspConfigDescriptor;
        info.defaultServletConfig = defaultServletConfig;
        info.localeCharsetMapping.putAll(localeCharsetMapping);
        info.sessionManager = sessionManager;
        info.loginConfig = loginConfig;
        info.identityManager = identityManager;
        info.confidentialPortManager = confidentialPortManager;
        info.securityConstraints.addAll(securityConstraints);
        info.principleVsRoleMapping.putAll(principleVsRoleMapping);
        info.outerHandlerChainWrappers.addAll(outerHandlerChainWrappers);
        info.innerHandlerChainWrappers.addAll(innerHandlerChainWrappers);
        info.dispatchedHandlerChainWrappers.addAll(dispatchedHandlerChainWrappers);
        info.securityRoles.addAll(securityRoles);
        return info;
    }


}
