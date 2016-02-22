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

package io.undertow.servlet.api;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

import javax.servlet.DispatcherType;
import javax.servlet.MultipartConfigElement;
import javax.servlet.descriptor.JspConfigDescriptor;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanismFactory;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.NotificationReceiver;
import io.undertow.security.api.SecurityContextFactory;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.server.session.SecureRandomSessionIdGenerator;
import io.undertow.server.session.SessionIdGenerator;
import io.undertow.server.session.SessionListener;
import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.core.DefaultAuthorizationManager;
import io.undertow.servlet.core.InMemorySessionManagerFactory;
import io.undertow.servlet.util.DefaultClassIntrospector;
import io.undertow.util.ImmediateAuthenticationMechanismFactory;

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
    private Path tempDir;
    private JspConfigDescriptor jspConfigDescriptor;
    private DefaultServletConfig defaultServletConfig;
    private SessionManagerFactory sessionManagerFactory = new InMemorySessionManagerFactory();
    private LoginConfig loginConfig;
    private IdentityManager identityManager;
    private ConfidentialPortManager confidentialPortManager;
    private boolean allowNonStandardWrappers = false;
    private int defaultSessionTimeout = 60 * 30;
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
    private boolean ignoreFlush = false;
    private AuthorizationManager authorizationManager = DefaultAuthorizationManager.INSTANCE;
    private AuthenticationMechanism jaspiAuthenticationMechanism;
    private SecurityContextFactory securityContextFactory;
    private String serverName = "Undertow";
    private MetricsCollector metricsCollector = null;
    private SessionConfigWrapper sessionConfigWrapper = null;
    private boolean eagerFilterInit = false;
    private boolean disableCachingForSecuredPages = true;
    private boolean escapeErrorMessage = true;
    private boolean sendCustomReasonPhraseOnError = false;
    private boolean useCachedAuthenticationMechanism = true;
    private AuthenticationMode authenticationMode = AuthenticationMode.PRO_ACTIVE;
    private ExceptionHandler exceptionHandler;
    private final Map<String, ServletInfo> servlets = new HashMap<>();
    private final Map<String, FilterInfo> filters = new HashMap<>();
    private final List<FilterMappingInfo> filterServletNameMappings = new ArrayList<>();
    private final List<FilterMappingInfo> filterUrlMappings = new ArrayList<>();
    private final List<ListenerInfo> listeners = new ArrayList<>();
    private final List<ServletContainerInitializerInfo> servletContainerInitializers = new ArrayList<>();
    private final List<ThreadSetupAction> threadSetupActions = new ArrayList<>();
    private final Map<String, String> initParameters = new HashMap<>();
    private final Map<String, Object> servletContextAttributes = new HashMap<>();
    private final Map<String, String> localeCharsetMapping = new HashMap<>();
    private final List<String> welcomePages = new ArrayList<>();
    private final List<ErrorPage> errorPages = new ArrayList<>();
    private final List<MimeMapping> mimeMappings = new ArrayList<>();
    private final List<SecurityConstraint> securityConstraints = new ArrayList<>();
    private final Set<String> securityRoles = new HashSet<>();
    private final List<NotificationReceiver> notificationReceivers = new ArrayList<>();
    private final Map<String, AuthenticationMechanismFactory> authenticationMechanisms = new HashMap<>();
    private final List<LifecycleInterceptor> lifecycleInterceptors = new ArrayList<>();
    private final List<SessionListener> sessionListeners = new ArrayList<>();

    /**
     * additional servlet extensions
     */
    private final List<ServletExtension> servletExtensions = new ArrayList<>();

    /**
     * map of additional roles that should be applied to the given principal.
     */
    private final Map<String, Set<String>> principalVersusRolesMap = new HashMap<>();

    /**
     * Wrappers that are applied before the servlet initial handler, and before any servlet related object have been
     * created. If a wrapper wants to bypass servlet entirely it should register itself here.
     */
    private final List<HandlerWrapper> initialHandlerChainWrappers = new ArrayList<>();

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
     * A handler chain wrapper to wrap the initial stages of the security handlers, if this is set it is assumed it
     * is taking over the responsibility of setting the {@link SecurityContext} that can handle authentication and the
     * remaining Undertow handlers specific to authentication will be skipped.
     */
    private HandlerWrapper initialSecurityWrapper = null;

    /**
     * Handler chain wrappers that are applied just before the authentication mechanism is called. Theses handlers are
     * always called, even if authentication is not required
     */
    private final List<HandlerWrapper> securityWrappers = new ArrayList<>();

    /**
     * Multipart config that will be applied to all servlets that do not have an explicit config
     */
    private MultipartConfigElement defaultMultipartConfig;

    /**
     * Cache of common content types, to prevent allocations when parsing the charset
     */
    private int contentTypeCacheSize = 100;

    private boolean changeSessionIdOnLogin = true;

    private SessionIdGenerator sessionIdGenerator = new SecureRandomSessionIdGenerator();

    /**
     * Config for the {@link io.undertow.servlet.handlers.CrawlerSessionManagerHandler}
     */
    private CrawlerSessionManagerConfig crawlerSessionManagerConfig;

    private boolean securityDisabled;

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
        if (defaultEncoding == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("defaultEncoding");
        }

        for (final ServletInfo servlet : this.servlets.values()) {
            servlet.validate();
        }
        for (final FilterInfo filter : this.filters.values()) {
            filter.validate();
        }
        for (FilterMappingInfo mapping : this.filterServletNameMappings) {
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
        if(contextPath != null && contextPath.isEmpty()) {
            this.contextPath = "/"; //we represent the root context as / instead of "", but both work
        } else {
            this.contextPath = contextPath;
        }
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

    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    /**
     * Sets the default encoding that will be used for servlet responses
     *
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

    public DeploymentInfo addServlet(final ServletInfo servlet) {
        servlets.put(servlet.getName(), servlet);
        return this;
    }

    public DeploymentInfo addServlets(final ServletInfo... servlets) {
        for (final ServletInfo servlet : servlets) {
            addServlet(servlet);
        }
        return this;
    }

    public DeploymentInfo addServlets(final Collection<ServletInfo> servlets) {
        for (final ServletInfo servlet : servlets) {
            addServlet(servlet);
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
            addFilter(filter);
        }
        return this;
    }

    public DeploymentInfo addFilters(final Collection<FilterInfo> filters) {
        for (final FilterInfo filter : filters) {
            addFilter(filter);
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
        final ArrayList<FilterMappingInfo> ret = new ArrayList<>(filterUrlMappings);
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

    public boolean isEagerFilterInit() {
        return eagerFilterInit;
    }

    public DeploymentInfo setEagerFilterInit(boolean eagerFilterInit) {
        this.eagerFilterInit = eagerFilterInit;
        return this;
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
     * <p>
     * Individual servlets may use a different executor
     * <p>
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
     * <p>
     * If this is null then {@link #executor} is used, if this is also null then the default is used
     *
     * @param asyncExecutor The executor
     */
    public DeploymentInfo setAsyncExecutor(final Executor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
        return this;
    }

    public File getTempDir() {
        if(tempDir == null) {
            return null;
        }
        return tempDir.toFile();
    }

    public Path getTempPath() {
        return tempDir;
    }

    public DeploymentInfo setTempDir(final File tempDir) {
        this.tempDir = tempDir != null ? tempDir.toPath() : null;
        return this;
    }

    public DeploymentInfo setTempDir(final Path tempDir) {
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


    /**
     * Sets the initial handler wrapper that will take over responsibility for establishing
     * a security context that will handle authentication for the request.
     *
     * Undertow specific authentication mechanisms will not be installed but Undertow handlers will
     * still make the decision as to if authentication is required and will subsequently
     * call {@link SecurityContext#authenticate()} as required.
     *
     * @param wrapper the {@link HandlerWrapper} to handle the initial security context installation.
     * @return {@code this} to allow chaining.
     */
    public DeploymentInfo setInitialSecurityWrapper(final HandlerWrapper wrapper) {
        this.initialSecurityWrapper = wrapper;

        return this;
    }

    public HandlerWrapper getInitialSecurityWrapper() {
        return initialSecurityWrapper;
    }

    /**
     * Adds a security handler. These are invoked before the authentication mechanism, and are always invoked
     * even if authentication is not required.
     * @param wrapper
     * @return
     */
    public DeploymentInfo addSecurityWrapper(final HandlerWrapper wrapper) {
        securityWrappers.add(wrapper);
        return this;
    }

    public List<HandlerWrapper> getSecurityWrappers() {
        return Collections.unmodifiableList(securityWrappers);
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
     * <p>
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
            principalVersusRolesMap.put(principal, set = new HashSet<>());
        }
        set.add(mapping);
        return this;
    }

    public DeploymentInfo addPrincipalVsRoleMappings(final String principal, final String... mappings) {
        Set<String> set = principalVersusRolesMap.get(principal);
        if (set == null) {
            principalVersusRolesMap.put(principal, set = new HashSet<>());
        }
        set.addAll(Arrays.asList(mappings));
        return this;
    }

    public DeploymentInfo addPrincipalVsRoleMappings(final String principal, final Collection<String> mappings) {
        Set<String> set = principalVersusRolesMap.get(principal);
        if (set == null) {
            principalVersusRolesMap.put(principal, set = new HashSet<>());
        }
        set.addAll(mappings);
        return this;
    }

    public Map<String, Set<String>> getPrincipalVersusRolesMap() {
        return Collections.unmodifiableMap(principalVersusRolesMap);
    }

    /**
     * Removes all configured authentication mechanisms from the deployment.
     *
     * @return this deployment info
     */
    public DeploymentInfo clearLoginMethods() {
        if(loginConfig != null) {
            loginConfig.getAuthMethods().clear();
        }
        return this;
    }

    /**
     * Adds an authentication mechanism directly to the deployment. This mechanism will be first in the list.
     *
     * In general you should just use {@link #addAuthenticationMechanism(String, io.undertow.security.api.AuthenticationMechanismFactory)}
     * and allow the user to configure the methods they want by name.
     *
     * This method is essentially a convenience method, if is the same as registering a factory under the provided name that returns
     * and authentication mechanism, and then adding it to the login config list.
     *
     * If you want your mechanism to be the only one in the deployment you should first invoke {@link #clearLoginMethods()}.
     *
     * @param name The authentication mechanism name
     * @param mechanism The mechanism
     * @return this deployment info
     */
    public DeploymentInfo addFirstAuthenticationMechanism(final String name, final AuthenticationMechanism mechanism) {
        authenticationMechanisms.put(name, new ImmediateAuthenticationMechanismFactory(mechanism));
        if(loginConfig == null) {
            loginConfig = new LoginConfig(null);
        }
        loginConfig.addFirstAuthMethod(new AuthMethodConfig(name));
        return this;
    }

    /**
     * Adds an authentication mechanism directly to the deployment. This mechanism will be last in the list.
     *
     * In general you should just use {@link #addAuthenticationMechanism(String, io.undertow.security.api.AuthenticationMechanismFactory)}
     * and allow the user to configure the methods they want by name.
     *
     * This method is essentially a convenience method, if is the same as registering a factory under the provided name that returns
     * and authentication mechanism, and then adding it to the login config list.
     *
     * If you want your mechanism to be the only one in the deployment you should first invoke {@link #clearLoginMethods()}.
     *
     * @param name The authentication mechanism name
     * @param mechanism The mechanism
     * @return
     */
    public DeploymentInfo addLastAuthenticationMechanism(final String name, final AuthenticationMechanism mechanism) {
        authenticationMechanisms.put(name, new ImmediateAuthenticationMechanismFactory(mechanism));
        if(loginConfig == null) {
            loginConfig = new LoginConfig(null);
        }
        loginConfig.addLastAuthMethod(new AuthMethodConfig(name));
        return this;
    }

    /**
     * Adds an authentication mechanism. The name is case insenstive, and will be converted to uppercase internally.
     *
     * @param name    The name
     * @param factory The factory
     * @return
     */
    public DeploymentInfo addAuthenticationMechanism(final String name, final AuthenticationMechanismFactory factory) {
        authenticationMechanisms.put(name.toUpperCase(Locale.US), factory);
        return this;
    }

    public Map<String, AuthenticationMechanismFactory> getAuthenticationMechanisms() {
        return Collections.unmodifiableMap(authenticationMechanisms);
    }

    /**
     * Returns true if the specified mechanism is present in the login config
     * @param mechanismName The mechanism name
     * @return true if the mechanism is enabled
     */
    public boolean isAuthenticationMechanismPresent(final String mechanismName) {
        if(loginConfig != null) {
            for(AuthMethodConfig method : loginConfig.getAuthMethods()) {
                if(method.getName().equalsIgnoreCase(mechanismName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Adds an additional servlet extension to the deployment. Servlet extensions are generally discovered
     * using META-INF/services entries, however this may not be practical in all environments.
     * @param servletExtension The servlet extension
     * @return this
     */
    public DeploymentInfo addServletExtension(final ServletExtension servletExtension) {
        this.servletExtensions.add(servletExtension);
        return this;
    }

    public List<ServletExtension> getServletExtensions() {
        return servletExtensions;
    }

    public AuthenticationMechanism getJaspiAuthenticationMechanism() {
        return jaspiAuthenticationMechanism;
    }

    public DeploymentInfo setJaspiAuthenticationMechanism(AuthenticationMechanism jaspiAuthenticationMechanism) {
        this.jaspiAuthenticationMechanism = jaspiAuthenticationMechanism;
        return this;
    }

    public SecurityContextFactory getSecurityContextFactory() {
        return this.securityContextFactory;
    }

    public DeploymentInfo setSecurityContextFactory(final SecurityContextFactory securityContextFactory) {
        this.securityContextFactory = securityContextFactory;
        return this;
    }

    public String getServerName() {
        return serverName;
    }

    public DeploymentInfo setServerName(String serverName) {
        this.serverName = serverName;
        return this;
    }

    public DeploymentInfo setMetricsCollector(MetricsCollector metricsCollector){
        this.metricsCollector = metricsCollector;
        return this;
    }

    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }

    public SessionConfigWrapper getSessionConfigWrapper() {
        return sessionConfigWrapper;
    }

    public DeploymentInfo setSessionConfigWrapper(SessionConfigWrapper sessionConfigWrapper) {
        this.sessionConfigWrapper = sessionConfigWrapper;
        return this;
    }

    public boolean isDisableCachingForSecuredPages() {
        return disableCachingForSecuredPages;
    }

    public DeploymentInfo setDisableCachingForSecuredPages(boolean disableCachingForSecuredPages) {
        this.disableCachingForSecuredPages = disableCachingForSecuredPages;
        return this;
    }

    public DeploymentInfo addLifecycleInterceptor(final LifecycleInterceptor interceptor) {
        lifecycleInterceptors.add(interceptor);
        return this;
    }

    public List<LifecycleInterceptor> getLifecycleInterceptors() {
        return Collections.unmodifiableList(lifecycleInterceptors);
    }

    /**
     * Returns the exception handler that is used by this deployment. By default this will simply
     * log unhandled exceptions
     */
    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    /**
     * Sets the default exception handler for this deployment
     * @param exceptionHandler The exception handler
     * @return
     */
    public DeploymentInfo setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    public boolean isEscapeErrorMessage() {
        return escapeErrorMessage;
    }

    /**
     * Set if if the message passed to {@link javax.servlet.http.HttpServletResponse#sendError(int, String)} should be escaped.
     *
     * If this is false applications must be careful not to use user provided data (such as the URI) in the message
     *
     * @param escapeErrorMessage If the error message should be escaped
     */
    public DeploymentInfo setEscapeErrorMessage(boolean escapeErrorMessage) {
        this.escapeErrorMessage = escapeErrorMessage;
        return this;
    }


    public DeploymentInfo addSessionListener(SessionListener sessionListener) {
        this.sessionListeners.add(sessionListener);
        return this;
    }

    public List<SessionListener> getSessionListeners() {
        return Collections.unmodifiableList(sessionListeners);
    }

    public AuthenticationMode getAuthenticationMode() {
        return authenticationMode;
    }

    /**
     * Sets if this deployment should use pro-active authentication and always authenticate if the credentials are present
     * or constraint driven auth which will only call the authentication mechanisms for protected resources.
     *
     * Pro active auth means that requests for unprotected resources will still be associated with a user, which may be
     * useful for access logging.
     *
     *
     * @param authenticationMode The authentication mode to use
     * @return
     */
    public DeploymentInfo setAuthenticationMode(AuthenticationMode authenticationMode) {
        this.authenticationMode = authenticationMode;
        return this;
    }

    public MultipartConfigElement getDefaultMultipartConfig() {
        return defaultMultipartConfig;
    }

    public DeploymentInfo setDefaultMultipartConfig(MultipartConfigElement defaultMultipartConfig) {
        this.defaultMultipartConfig = defaultMultipartConfig;
        return this;
    }

    public int getContentTypeCacheSize() {
        return contentTypeCacheSize;
    }

    public DeploymentInfo setContentTypeCacheSize(int contentTypeCacheSize) {
        this.contentTypeCacheSize = contentTypeCacheSize;
        return this;
    }

    public SessionIdGenerator getSessionIdGenerator() {
        return sessionIdGenerator;
    }

    public DeploymentInfo setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
        this.sessionIdGenerator = sessionIdGenerator;
        return this;
    }


    public boolean isSendCustomReasonPhraseOnError() {
        return sendCustomReasonPhraseOnError;
    }

    public CrawlerSessionManagerConfig getCrawlerSessionManagerConfig() {
        return crawlerSessionManagerConfig;
    }

    public DeploymentInfo setCrawlerSessionManagerConfig(CrawlerSessionManagerConfig crawlerSessionManagerConfig) {
        this.crawlerSessionManagerConfig = crawlerSessionManagerConfig;
        return this;
    }

    /**
     * If this is true then the message parameter of {@link javax.servlet.http.HttpServletResponse#sendError(int, String)} and
     * {@link javax.servlet.http.HttpServletResponse#setStatus(int, String)} will be used as the HTTP reason phrase in
     * the response.
     *
     * @param sendCustomReasonPhraseOnError If the parameter to sendError should be used as a HTTP reason phrase
     * @return this
     */
    public DeploymentInfo setSendCustomReasonPhraseOnError(boolean sendCustomReasonPhraseOnError) {
        this.sendCustomReasonPhraseOnError = sendCustomReasonPhraseOnError;
        return this;
    }

    public boolean isChangeSessionIdOnLogin() {
        return changeSessionIdOnLogin;
    }

    public DeploymentInfo setChangeSessionIdOnLogin(boolean changeSessionIdOnLogin) {
        this.changeSessionIdOnLogin = changeSessionIdOnLogin;
        return this;
    }

    public boolean isUseCachedAuthenticationMechanism() {
        return useCachedAuthenticationMechanism;
    }

    /**
     * If this is set to false the the cached authenticated session mechanism won't be installed. If you want FORM and
     * other auth methods that require caching to work then you need to install another caching based auth method (such
     * as SSO).
     * @param useCachedAuthenticationMechanism If Undertow should use its internal authentication cache mechanism
     * @return this
     */
    public DeploymentInfo setUseCachedAuthenticationMechanism(boolean useCachedAuthenticationMechanism) {
        this.useCachedAuthenticationMechanism = useCachedAuthenticationMechanism;
        return this;
    }

    public boolean isSecurityDisabled() {
        return securityDisabled;
    }

    public DeploymentInfo setSecurityDisabled(boolean securityDisabled) {
        this.securityDisabled = securityDisabled;
        return this;
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
        if (loginConfig != null) {
            info.loginConfig = loginConfig.clone();
        }
        info.identityManager = identityManager;
        info.confidentialPortManager = confidentialPortManager;
        info.defaultEncoding = defaultEncoding;
        info.urlEncoding = urlEncoding;
        info.securityConstraints.addAll(securityConstraints);
        info.outerHandlerChainWrappers.addAll(outerHandlerChainWrappers);
        info.innerHandlerChainWrappers.addAll(innerHandlerChainWrappers);
        info.initialSecurityWrapper = initialSecurityWrapper;
        info.securityWrappers.addAll(securityWrappers);
        info.initialHandlerChainWrappers.addAll(initialHandlerChainWrappers);
        info.securityRoles.addAll(securityRoles);
        info.notificationReceivers.addAll(notificationReceivers);
        info.allowNonStandardWrappers = allowNonStandardWrappers;
        info.defaultSessionTimeout = defaultSessionTimeout;
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
        info.authenticationMechanisms.putAll(authenticationMechanisms);
        info.servletExtensions.addAll(servletExtensions);
        info.jaspiAuthenticationMechanism = jaspiAuthenticationMechanism;
        info.securityContextFactory = securityContextFactory;
        info.serverName = serverName;
        info.metricsCollector = metricsCollector;
        info.sessionConfigWrapper = sessionConfigWrapper;
        info.eagerFilterInit = eagerFilterInit;
        info.disableCachingForSecuredPages = disableCachingForSecuredPages;
        info.exceptionHandler = exceptionHandler;
        info.escapeErrorMessage = escapeErrorMessage;
        info.sessionListeners.addAll(sessionListeners);
        info.lifecycleInterceptors.addAll(lifecycleInterceptors);
        info.authenticationMode = authenticationMode;
        info.defaultMultipartConfig = defaultMultipartConfig;
        info.contentTypeCacheSize = contentTypeCacheSize;
        info.sessionIdGenerator = sessionIdGenerator;
        info.sendCustomReasonPhraseOnError = sendCustomReasonPhraseOnError;
        info.changeSessionIdOnLogin = changeSessionIdOnLogin;
        info.crawlerSessionManagerConfig = crawlerSessionManagerConfig;
        info.securityDisabled = securityDisabled;
        info.useCachedAuthenticationMechanism = useCachedAuthenticationMechanism;
        return info;
    }


}
