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
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

import javax.servlet.DispatcherType;
import javax.servlet.descriptor.JspConfigDescriptor;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.NotificationReceiver;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.core.DefaultAuthorizationManager;
import io.undertow.servlet.core.InMemorySessionManagerFactory;
import io.undertow.servlet.util.DefaultClassIntrospector;

/**
 * Represents a servlet deployment.
 *
 * @author Stuart Douglas
 */
public class DeploymentInfo implements Cloneable {

    private String deploymentName;
    private String displayName;
    private String contextPath;
    private ClassLoader classLoader;
    private ResourceManager resourceManager = ResourceManager.EMPTY_RESOURCE_MANAGER;
    private ClassIntrospecter classIntrospecter = DefaultClassIntrospector.INSTANCE;
    private int majorVersion = 3;
    private int minorVersion;
    private Executor executor;
    private Executor asyncExecutor;
    private File tempDir;
    private JspConfigDescriptor jspConfigDescriptor;
    private DefaultServletConfig defaultServletConfig;
    private SessionManagerFactory sessionManagerFactory = new InMemorySessionManagerFactory();
    private LoginConfig loginConfig;
    private IdentityManager identityManager;
    private ConfidentialPortManager confidentialPortManager;
    private boolean allowNonStandardWrappers = false;
    private int defaultSessionTimeout = 60 * 30;
    private boolean ignoreStandardAuthenticationMechanism = false;
    private ConcurrentMap<String, Object> servletContextAttributeBackingMap;
    private ServletSessionConfig servletSessionConfig;
    private String hostName = "localhost";
    private boolean denyUncoveredHttpMethods = false;
    private ServletStackTraces servletStackTraces = ServletStackTraces.LOCAL_ONLY;
    private boolean invalidateSessionOnLogout = false;
    private int defaultCookieVersion = 0;
    private SessionPersistenceManager sessionPersistenceManager;
    private String defaultEncoding = "ISO-8859-1";
    private String urlEncoding = null;
    private boolean ignoreFlush = true;
    private AuthorizationManager authorizationManager = DefaultAuthorizationManager.INSTANCE;
    private final List<AuthenticationMechanism> additionalAuthenticationMechanisms = new ArrayList<AuthenticationMechanism>();
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
    private final Set<String> securityRoles = new HashSet<String>();
    private final List<NotificationReceiver> notificationReceivers = new ArrayList<NotificationReceiver>();

    /**
     * map of additional roles that should be applied to the given principal.
     */
    private final Map<String, Set<String>> principalVersusRolesMap = new HashMap<String, Set<String>>();

    /**
     * Wrappers that are applied before the servlet initial handler, and before any servlet related object have been
     * created. If a wrapper wants to bypass servlet entirely it should register itself here.
     */
    private final List<HandlerWrapper> initialHandlerChainWrappers = new ArrayList<HandlerWrapper>();

    /**
     * Handler chain wrappers that are applied outside all other handlers, including security but after the initial
     * servlet handler.
     */
    private final List<HandlerWrapper> outerHandlerChainWrappers = new ArrayList<HandlerWrapper>();

    /**
     * Handler chain wrappers that are applied just before the servlet request is dispatched. At this point the security
     * handlers have run, and any security information is attached to the request.
     */
    private final List<HandlerWrapper> innerHandlerChainWrappers = new ArrayList<HandlerWrapper>();


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
        if (resourceManager == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("resourceManager");
        }
        if (classIntrospecter == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("classIntrospecter");
        }
        if(defaultEncoding == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("defaultEncoding");
        }

        for (final ServletInfo servlet : this.servlets.values()) {
            servlet.validate();
        }
        for (final FilterInfo filter : this.filters.values()) {
            filter.validate();
        }
        for(FilterMappingInfo mapping : this.filterServletNameMappings) {
            if (!this.filters.containsKey(mapping.getFilterName())) {
                throw UndertowServletMessages.MESSAGES.filterNotFound(mapping.getFilterName(), mapping.getMappingType() + " - " + mapping.getMapping());
            }
        }
        for (FilterMappingInfo mapping : this.filterUrlMappings) {
            if (!this.filters.containsKey(mapping.getFilterName())) {
                throw UndertowServletMessages.MESSAGES.filterNotFound(mapping.getFilterName(), mapping.getMappingType() + " - " + mapping.getMapping());
            }
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

    public DeploymentInfo setDisplayName(final String displayName) {
        this.displayName = displayName;
        return this;
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

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    public DeploymentInfo setResourceManager(final ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        return this;
    }

    public ClassIntrospecter getClassIntrospecter() {
        return classIntrospecter;
    }

    public DeploymentInfo setClassIntrospecter(final ClassIntrospecter classIntrospecter) {
        this.classIntrospecter = classIntrospecter;
        return this;
    }

    public boolean isAllowNonStandardWrappers() {
        return allowNonStandardWrappers;
    }

    public DeploymentInfo setAllowNonStandardWrappers(final boolean allowNonStandardWrappers) {
        this.allowNonStandardWrappers = allowNonStandardWrappers;
        return this;
    }

    public int getDefaultSessionTimeout() {
        return defaultSessionTimeout;
    }

    /**
     * @param defaultSessionTimeout The default session timeout, in seconds
     */
    public DeploymentInfo setDefaultSessionTimeout(final int defaultSessionTimeout) {
        this.defaultSessionTimeout = defaultSessionTimeout;
        return this;
    }

    /**
     * @return <code>true</code> If the authentication mechanism specified in web.xml should not be used
     */
    public boolean isIgnoreStandardAuthenticationMechanism() {
        return ignoreStandardAuthenticationMechanism;
    }

    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    /**
     * Sets the default encoding that will be used for servlet responses
     * @param defaultEncoding The default encoding
     */
    public DeploymentInfo setDefaultEncoding(String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
        return this;
    }

    public String getUrlEncoding() {
        return urlEncoding;
    }

    /**
     * Sets the URL encoding. This will only take effect if the {@link io.undertow.UndertowOptions#DECODE_URL}
     * parameter has been set to false. This allows multiple deployments in the same server to use a different URL encoding
     *
     * @param urlEncoding The encoding to use
     */
    public DeploymentInfo setUrlEncoding(String urlEncoding) {
        this.urlEncoding = urlEncoding;
        return this;
    }

    /**
     * @param ignoreStandardAuthenticationMechanism
     *         If the authentication mechanism specified in web.xml should be ignored
     */
    public DeploymentInfo setIgnoreStandardAuthenticationMechanism(final boolean ignoreStandardAuthenticationMechanism) {
        this.ignoreStandardAuthenticationMechanism = ignoreStandardAuthenticationMechanism;
        return this;
    }


    public DeploymentInfo addAuthenticationMechanism(final AuthenticationMechanism mechanism) {
        additionalAuthenticationMechanisms.add(mechanism);
        return this;
    }


    public DeploymentInfo addAuthenticationMechanisms(final AuthenticationMechanism... mechanisms) {
        additionalAuthenticationMechanisms.addAll(Arrays.asList(mechanisms));
        return this;
    }


    public DeploymentInfo addAuthenticationMechanisms(final Collection<AuthenticationMechanism> mechanisms) {
        additionalAuthenticationMechanisms.addAll(mechanisms);
        return this;
    }

    public List<AuthenticationMechanism> getAdditionalAuthenticationMechanisms() {
        return Collections.unmodifiableList(additionalAuthenticationMechanisms);
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

    public DeploymentInfo addWelcomePage(final String welcomePage) {
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

    public Executor getExecutor() {
        return executor;
    }

    /**
     * Sets the executor that will be used to run servlet invocations. If this is null then the XNIO worker pool will be
     * used.
     * <p/>
     * Individual servlets may use a different executor
     * <p/>
     * If this is null then the current executor is used, which is generally the XNIO worker pool
     *
     * @param executor The executor
     * @see ServletInfo#executor
     */
    public DeploymentInfo setExecutor(final Executor executor) {
        this.executor = executor;
        return this;
    }

    public Executor getAsyncExecutor() {
        return asyncExecutor;
    }

    /**
     * Sets the executor that is used to run async tasks.
     * <p/>
     * If this is null then {@link #executor} is used, if this is also null then the default is used
     *
     * @param asyncExecutor The executor
     */
    public DeploymentInfo setAsyncExecutor(final Executor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
        return this;
    }

    public File getTempDir() {
        return tempDir;
    }

    public DeploymentInfo setTempDir(final File tempDir) {
        this.tempDir = tempDir;
        return this;
    }

    public boolean isIgnoreFlush() {
        return ignoreFlush;
    }

    public DeploymentInfo setIgnoreFlush(boolean ignoreFlush) {
        this.ignoreFlush = ignoreFlush;
        return this;
    }

    public JspConfigDescriptor getJspConfigDescriptor() {
        return jspConfigDescriptor;
    }

    public DeploymentInfo setJspConfigDescriptor(JspConfigDescriptor jspConfigDescriptor) {
        this.jspConfigDescriptor = jspConfigDescriptor;
        return this;
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

    public SessionManagerFactory getSessionManagerFactory() {
        return sessionManagerFactory;
    }

    public DeploymentInfo setSessionManagerFactory(final SessionManagerFactory sessionManagerFactory) {
        this.sessionManagerFactory = sessionManagerFactory;
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

    /**
     * Adds an outer handler wrapper. This handler will be run after the servlet initial handler,
     * but before any other handlers. These are only run on REQUEST invocations, they
     * are not invoked on a FORWARD or INCLUDE.
     *
     * @param wrapper The wrapper
     */
    public DeploymentInfo addOuterHandlerChainWrapper(final HandlerWrapper wrapper) {
        outerHandlerChainWrappers.add(wrapper);
        return this;
    }

    public List<HandlerWrapper> getOuterHandlerChainWrappers() {
        return Collections.unmodifiableList(outerHandlerChainWrappers);
    }

    /**
     * Adds an inner handler chain wrapper. This handler will be run after the security handler,
     * but before any other servlet handlers, and will be run for every request
     *
     * @param wrapper The wrapper
     */
    public DeploymentInfo addInnerHandlerChainWrapper(final HandlerWrapper wrapper) {
        innerHandlerChainWrappers.add(wrapper);
        return this;
    }

    public List<HandlerWrapper> getInnerHandlerChainWrappers() {
        return Collections.unmodifiableList(innerHandlerChainWrappers);
    }

    public DeploymentInfo addInitialHandlerChainWrapper(final HandlerWrapper wrapper) {
        initialHandlerChainWrappers.add(wrapper);
        return this;
    }

    public List<HandlerWrapper> getInitialHandlerChainWrappers() {
        return Collections.unmodifiableList(initialHandlerChainWrappers);
    }

    public DeploymentInfo addNotificationReceiver(final NotificationReceiver notificationReceiver) {
        this.notificationReceivers.add(notificationReceiver);
        return this;
    }

    public DeploymentInfo addNotificactionReceivers(final NotificationReceiver... notificationReceivers) {
        this.notificationReceivers.addAll(Arrays.asList(notificationReceivers));
        return this;
    }

    public DeploymentInfo addNotificationReceivers(final Collection<NotificationReceiver> notificationReceivers) {
        this.notificationReceivers.addAll(notificationReceivers);
        return this;
    }

    public List<NotificationReceiver> getNotificationReceivers() {
        return Collections.unmodifiableList(notificationReceivers);
    }

    public ConcurrentMap<String, Object> getServletContextAttributeBackingMap() {
        return servletContextAttributeBackingMap;
    }

    /**
     * Sets the map that will be used by the ServletContext implementation to store attributes.
     * <p/>
     * This should usuablly be null, in which case Undertow will create a new map. This is only
     * used in situations where you want multiple deployments to share the same servlet context
     * attributes.
     *
     * @param servletContextAttributeBackingMap
     *         The backing map
     */
    public DeploymentInfo setServletContextAttributeBackingMap(final ConcurrentMap<String, Object> servletContextAttributeBackingMap) {
        this.servletContextAttributeBackingMap = servletContextAttributeBackingMap;
        return this;
    }

    public ServletSessionConfig getServletSessionConfig() {
        return servletSessionConfig;
    }

    public DeploymentInfo setServletSessionConfig(final ServletSessionConfig servletSessionConfig) {
        this.servletSessionConfig = servletSessionConfig;
        return this;
    }

    /**
     * @return the host name
     */
    public String getHostName() {
        return hostName;
    }

    public DeploymentInfo setHostName(final String hostName) {
        this.hostName = hostName;
        return this;
    }

    public boolean isDenyUncoveredHttpMethods() {
        return denyUncoveredHttpMethods;
    }

    public DeploymentInfo setDenyUncoveredHttpMethods(final boolean denyUncoveredHttpMethods) {
        this.denyUncoveredHttpMethods = denyUncoveredHttpMethods;
        return this;
    }

    public ServletStackTraces getServletStackTraces() {
        return servletStackTraces;
    }

    public DeploymentInfo setServletStackTraces(ServletStackTraces servletStackTraces) {
        this.servletStackTraces = servletStackTraces;
        return this;
    }

    public boolean isInvalidateSessionOnLogout() {
        return invalidateSessionOnLogout;
    }

    public DeploymentInfo setInvalidateSessionOnLogout(boolean invalidateSessionOnLogout) {
        this.invalidateSessionOnLogout = invalidateSessionOnLogout;
        return this;
    }

    public int getDefaultCookieVersion() {
        return defaultCookieVersion;
    }

    public DeploymentInfo setDefaultCookieVersion(int defaultCookieVersion) {
        this.defaultCookieVersion = defaultCookieVersion;
        return this;
    }

    public SessionPersistenceManager getSessionPersistenceManager() {
        return sessionPersistenceManager;
    }

    public DeploymentInfo setSessionPersistenceManager(SessionPersistenceManager sessionPersistenceManager) {
        this.sessionPersistenceManager = sessionPersistenceManager;
        return this;
    }

    public AuthorizationManager getAuthorizationManager() {
        return authorizationManager;
    }

    public DeploymentInfo setAuthorizationManager(AuthorizationManager authorizationManager) {
        this.authorizationManager = authorizationManager;
        return this;
    }

    public DeploymentInfo addPrincipalVsRoleMapping(final String principal, final String mapping) {
        Set<String> set = principalVersusRolesMap.get(principal);
        if (set == null) {
            principalVersusRolesMap.put(principal, set = new HashSet<String>());
        }
        set.add(mapping);
        return this;
    }

    public DeploymentInfo addPrincipalVsRoleMappings(final String principal, final String... mappings) {
        Set<String> set = principalVersusRolesMap.get(principal);
        if (set == null) {
            principalVersusRolesMap.put(principal, set = new HashSet<String>());
        }
        set.addAll(Arrays.asList(mappings));
        return this;
    }

    public DeploymentInfo addPrincipalVsRoleMappings(final String principal, final Collection<String> mappings) {
        Set<String> set = principalVersusRolesMap.get(principal);
        if (set == null) {
            principalVersusRolesMap.put(principal, set = new HashSet<String>());
        }
        set.addAll(mappings);
        return this;
    }

    public Map<String, Set<String>> getPrincipalVersusRolesMap() {
        return Collections.unmodifiableMap(principalVersusRolesMap);
    }

    @Override
    public DeploymentInfo clone() {
        final DeploymentInfo info = new DeploymentInfo()
                .setClassLoader(classLoader)
                .setContextPath(contextPath)
                .setResourceManager(resourceManager)
                .setMajorVersion(majorVersion)
                .setMinorVersion(minorVersion)
                .setDeploymentName(deploymentName)
                .setClassIntrospecter(classIntrospecter);

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
        info.executor = executor;
        info.asyncExecutor = asyncExecutor;
        info.tempDir = tempDir;
        info.jspConfigDescriptor = jspConfigDescriptor;
        info.defaultServletConfig = defaultServletConfig;
        info.localeCharsetMapping.putAll(localeCharsetMapping);
        info.sessionManagerFactory = sessionManagerFactory;
        info.loginConfig = loginConfig;
        info.identityManager = identityManager;
        info.confidentialPortManager = confidentialPortManager;
        info.defaultEncoding = defaultEncoding;
        info.urlEncoding = urlEncoding;
        info.securityConstraints.addAll(securityConstraints);
        info.outerHandlerChainWrappers.addAll(outerHandlerChainWrappers);
        info.innerHandlerChainWrappers.addAll(innerHandlerChainWrappers);
        info.initialHandlerChainWrappers.addAll(initialHandlerChainWrappers);
        info.securityRoles.addAll(securityRoles);
        info.notificationReceivers.addAll(notificationReceivers);
        info.allowNonStandardWrappers = allowNonStandardWrappers;
        info.defaultSessionTimeout = defaultSessionTimeout;
        info.ignoreStandardAuthenticationMechanism = ignoreStandardAuthenticationMechanism;
        info.additionalAuthenticationMechanisms.addAll(additionalAuthenticationMechanisms);
        info.servletContextAttributeBackingMap = servletContextAttributeBackingMap;
        info.servletSessionConfig = servletSessionConfig;
        info.hostName = hostName;
        info.denyUncoveredHttpMethods = denyUncoveredHttpMethods;
        info.servletStackTraces = servletStackTraces;
        info.invalidateSessionOnLogout = invalidateSessionOnLogout;
        info.defaultCookieVersion = defaultCookieVersion;
        info.sessionPersistenceManager = sessionPersistenceManager;
        info.principalVersusRolesMap.putAll(principalVersusRolesMap);
        info.ignoreFlush = ignoreFlush;
        info.authorizationManager = authorizationManager;
        return info;
    }


}
