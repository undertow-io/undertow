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

package io.undertow.servlet.core;

import io.undertow.Handlers;
import io.undertow.predicate.Predicates;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanismFactory;
import io.undertow.security.api.NotificationReceiver;
import io.undertow.security.api.SecurityContextFactory;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.NotificationReceiverHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.security.impl.CachedAuthenticatedSessionMechanism;
import io.undertow.security.impl.ClientCertAuthenticationMechanism;
import io.undertow.security.impl.DigestAuthenticationMechanism;
import io.undertow.security.impl.ExternalAuthenticationMechanism;
import io.undertow.security.impl.GenericHeaderAuthenticationMechanism;
import io.undertow.security.impl.SecurityContextFactoryImpl;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.HttpContinueReadHandler;
import io.undertow.server.handlers.PredicateHandler;
import io.undertow.server.handlers.form.FormEncodedDataDefinition;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.session.SessionListener;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.AuthMethodConfig;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ErrorPage;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.HttpMethodSecurityInfo;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.MetricsCollector;
import io.undertow.servlet.api.MimeMapping;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.SecurityInfo.EmptyRoleSemantic;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletContainerInitializerInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletSecurityInfo;
import io.undertow.servlet.api.ServletSessionConfig;
import io.undertow.servlet.api.ServletStackTraces;
import io.undertow.servlet.api.SessionPersistenceManager;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.servlet.handlers.CrawlerSessionManagerHandler;
import io.undertow.servlet.handlers.ServletDispatchingHandler;
import io.undertow.servlet.handlers.ServletHandler;
import io.undertow.servlet.handlers.ServletInitialHandler;
import io.undertow.servlet.handlers.SessionRestoringHandler;
import io.undertow.servlet.handlers.security.CachedAuthenticatedSessionHandler;
import io.undertow.servlet.handlers.security.SSLInformationAssociationHandler;
import io.undertow.servlet.handlers.security.SecurityPathMatches;
import io.undertow.servlet.handlers.security.ServletAuthenticationCallHandler;
import io.undertow.servlet.handlers.security.ServletAuthenticationConstraintHandler;
import io.undertow.servlet.handlers.security.ServletConfidentialityConstraintHandler;
import io.undertow.servlet.handlers.security.ServletFormAuthenticationMechanism;
import io.undertow.servlet.handlers.security.ServletSecurityConstraintHandler;
import io.undertow.servlet.predicate.DispatcherTypePredicate;
import io.undertow.servlet.spec.ServletContextImpl;
import io.undertow.servlet.spec.SessionCookieConfigImpl;
import io.undertow.util.MimeMappings;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;

import static javax.servlet.http.HttpServletRequest.BASIC_AUTH;
import static javax.servlet.http.HttpServletRequest.CLIENT_CERT_AUTH;
import static javax.servlet.http.HttpServletRequest.DIGEST_AUTH;
import static javax.servlet.http.HttpServletRequest.FORM_AUTH;

/**
 * The deployment manager. This manager is responsible for controlling the lifecycle of a servlet deployment.
 *
 * @author Stuart Douglas
 */
public class DeploymentManagerImpl implements DeploymentManager {

    /**
     * The original deployment information, this is
     */
    private final DeploymentInfo originalDeployment;

    private final ServletContainer servletContainer;

    /**
     * Current deployment, this may be modified by SCI's
     */
    private volatile DeploymentImpl deployment;
    private volatile State state = State.UNDEPLOYED;

    public DeploymentManagerImpl(final DeploymentInfo deployment, final ServletContainer servletContainer) {
        this.originalDeployment = deployment;
        this.servletContainer = servletContainer;
    }

    @Override
    public void deploy() {
        DeploymentInfo deploymentInfo = originalDeployment.clone();

        if (deploymentInfo.getServletStackTraces() == ServletStackTraces.ALL) {
            UndertowServletLogger.REQUEST_LOGGER.servletStackTracesAll(deploymentInfo.getDeploymentName());
        }

        deploymentInfo.validate();
        final DeploymentImpl deployment = new DeploymentImpl(this, deploymentInfo, servletContainer);
        this.deployment = deployment;

        final ServletContextImpl servletContext = new ServletContextImpl(servletContainer, deployment);
        deployment.setServletContext(servletContext);
        handleExtensions(deploymentInfo, servletContext);
        deployment.getServletPaths().setWelcomePages(deploymentInfo.getWelcomePages());

        deployment.setDefaultCharset(Charset.forName(deploymentInfo.getDefaultEncoding()));

        handleDeploymentSessionConfig(deploymentInfo, servletContext);

        deployment.setSessionManager(deploymentInfo.getSessionManagerFactory().createSessionManager(deployment));
        deployment.getSessionManager().setDefaultSessionTimeout(deploymentInfo.getDefaultSessionTimeout());

        final List<ThreadSetupAction> setup = new ArrayList<>();
        setup.add(ServletRequestContextThreadSetupAction.INSTANCE);
        setup.add(new ContextClassLoaderSetupAction(deploymentInfo.getClassLoader()));
        setup.addAll(deploymentInfo.getThreadSetupActions());
        final CompositeThreadSetupAction threadSetupAction = new CompositeThreadSetupAction(setup);
        deployment.setThreadSetupAction(threadSetupAction);

        ThreadSetupAction.Handle handle = threadSetupAction.setup(null);
        try {

            final ApplicationListeners listeners = createListeners();
            listeners.start();

            deployment.setApplicationListeners(listeners);

            //now create the servlets and filters that we know about. We can still get more later
            createServletsAndFilters(deployment, deploymentInfo);

            //first run the SCI's
            for (final ServletContainerInitializerInfo sci : deploymentInfo.getServletContainerInitializers()) {
                final InstanceHandle<? extends ServletContainerInitializer> instance = sci.getInstanceFactory().createInstance();
                try {
                    instance.getInstance().onStartup(sci.getHandlesTypes(), servletContext);
                } finally {
                    instance.release();
                }
            }

            deployment.getSessionManager().registerSessionListener(new SessionListenerBridge(threadSetupAction, listeners, servletContext));
            for(SessionListener listener : deploymentInfo.getSessionListeners()) {
                deployment.getSessionManager().registerSessionListener(listener);
            }

            initializeErrorPages(deployment, deploymentInfo);
            initializeMimeMappings(deployment, deploymentInfo);
            initializeTempDir(servletContext, deploymentInfo);
            listeners.contextInitialized();
            //run

            HttpHandler wrappedHandlers = ServletDispatchingHandler.INSTANCE;
            wrappedHandlers = wrapHandlers(wrappedHandlers, deploymentInfo.getInnerHandlerChainWrappers());
            if(!deploymentInfo.isSecurityDisabled()) {
                HttpHandler securityHandler = setupSecurityHandlers(wrappedHandlers);
                wrappedHandlers = new PredicateHandler(DispatcherTypePredicate.REQUEST, securityHandler, wrappedHandlers);
            }
            HttpHandler outerHandlers = wrapHandlers(wrappedHandlers, deploymentInfo.getOuterHandlerChainWrappers());
            wrappedHandlers = new PredicateHandler(DispatcherTypePredicate.REQUEST, outerHandlers, wrappedHandlers);
            wrappedHandlers = handleDevelopmentModePersistentSessions(wrappedHandlers, deploymentInfo, deployment.getSessionManager(), servletContext);

            MetricsCollector metrics = deploymentInfo.getMetricsCollector();
            if(metrics != null) {
                wrappedHandlers = new MetricsChainHandler(wrappedHandlers, metrics, deployment);
            }
            if( deploymentInfo.getCrawlerSessionManagerConfig() != null ) {
                wrappedHandlers = new CrawlerSessionManagerHandler(deploymentInfo.getCrawlerSessionManagerConfig(), wrappedHandlers);
            }

            final ServletInitialHandler servletInitialHandler = SecurityActions.createServletInitialHandler(deployment.getServletPaths(), wrappedHandlers, deployment.getThreadSetupAction(), servletContext);

            HttpHandler initialHandler = wrapHandlers(servletInitialHandler, deployment.getDeploymentInfo().getInitialHandlerChainWrappers());
            initialHandler = new HttpContinueReadHandler(initialHandler);
            if(deploymentInfo.getUrlEncoding() != null) {
                initialHandler = Handlers.urlDecodingHandler(deploymentInfo.getUrlEncoding(), initialHandler);
            }
            deployment.setInitialHandler(initialHandler);
            deployment.setServletHandler(servletInitialHandler);
            deployment.getServletPaths().invalidate(); //make sure we have a fresh set of servlet paths
            servletContext.initDone();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            handle.tearDown();
        }
        state = State.DEPLOYED;
    }

    private void createServletsAndFilters(final DeploymentImpl deployment, final DeploymentInfo deploymentInfo) {
        for (Map.Entry<String, ServletInfo> servlet : deploymentInfo.getServlets().entrySet()) {
            deployment.getServlets().addServlet(servlet.getValue());
        }
        for (Map.Entry<String, FilterInfo> filter : deploymentInfo.getFilters().entrySet()) {
            deployment.getFilters().addFilter(filter.getValue());
        }
    }

    private void handleExtensions(final DeploymentInfo deploymentInfo, final ServletContextImpl servletContext) {
        Set<Class<?>> loadedExtensions = new HashSet<>();

        for (ServletExtension extension : ServiceLoader.load(ServletExtension.class, deploymentInfo.getClassLoader())) {
            loadedExtensions.add(extension.getClass());
            extension.handleDeployment(deploymentInfo, servletContext);
        }

        if (!ServletExtension.class.getClassLoader().equals(deploymentInfo.getClassLoader())) {
            for (ServletExtension extension : ServiceLoader.load(ServletExtension.class)) {

                // Note: If the CLs are different, but can the see the same extensions and extension might get loaded
                // and thus instantiated twice, but the handleDeployment() is executed only once.

                if (!loadedExtensions.contains(extension.getClass())) {
                    extension.handleDeployment(deploymentInfo, servletContext);
                }
            }
        }
        for(ServletExtension extension : deploymentInfo.getServletExtensions()) {
            extension.handleDeployment(deploymentInfo, servletContext);
        }
    }

    /**
     * sets up the outer security handlers.
     * <p/>
     * the handler that actually performs the access check happens later in the chain, it is not setup here
     *
     * @param initialHandler The handler to wrap with security handlers
     */
    private HttpHandler setupSecurityHandlers(HttpHandler initialHandler) {
        final DeploymentInfo deploymentInfo = deployment.getDeploymentInfo();
        final LoginConfig loginConfig = deploymentInfo.getLoginConfig();

        HttpHandler current = initialHandler;
        current = new SSLInformationAssociationHandler(current);

        final SecurityPathMatches securityPathMatches = buildSecurityConstraints();
        current = new ServletAuthenticationCallHandler(current);

        for(HandlerWrapper wrapper : deploymentInfo.getSecurityWrappers()) {
            current = wrapper.wrap(current);
        }

        if(deploymentInfo.isDisableCachingForSecuredPages()) {
            current = Handlers.predicate(Predicates.authRequired(), Handlers.disableCache(current), current);
        }
        if (!securityPathMatches.isEmpty()) {
            current = new ServletAuthenticationConstraintHandler(current);
        }
        current = new ServletConfidentialityConstraintHandler(deploymentInfo.getConfidentialPortManager(), current);
        if (!securityPathMatches.isEmpty()) {
            current = new ServletSecurityConstraintHandler(securityPathMatches, current);
        }

        HandlerWrapper initialSecurityWrapper = deploymentInfo.getInitialSecurityWrapper();

        String mechName = null;
        if (initialSecurityWrapper == null) {
            final Map<String, AuthenticationMechanismFactory> factoryMap = new HashMap<>(deploymentInfo.getAuthenticationMechanisms());
            final IdentityManager identityManager = deploymentInfo.getIdentityManager();
            if(!factoryMap.containsKey(BASIC_AUTH)) {
                factoryMap.put(BASIC_AUTH, new BasicAuthenticationMechanism.Factory(identityManager));
            }
            if(!factoryMap.containsKey(FORM_AUTH)) {
                factoryMap.put(FORM_AUTH, new ServletFormAuthenticationMechanism.Factory(identityManager));
            }
            if(!factoryMap.containsKey(DIGEST_AUTH)) {
                factoryMap.put(DIGEST_AUTH, new DigestAuthenticationMechanism.Factory(identityManager));
            }
            if(!factoryMap.containsKey(CLIENT_CERT_AUTH)) {
                factoryMap.put(CLIENT_CERT_AUTH, new ClientCertAuthenticationMechanism.Factory(identityManager));
            }
            if(!factoryMap.containsKey(ExternalAuthenticationMechanism.NAME)) {
                factoryMap.put(ExternalAuthenticationMechanism.NAME, new ExternalAuthenticationMechanism.Factory(identityManager));
            }
            if(!factoryMap.containsKey(GenericHeaderAuthenticationMechanism.NAME)) {
                factoryMap.put(GenericHeaderAuthenticationMechanism.NAME, new GenericHeaderAuthenticationMechanism.Factory(identityManager));
            }
            List<AuthenticationMechanism> authenticationMechanisms = new LinkedList<>();
            authenticationMechanisms.add(new CachedAuthenticatedSessionMechanism(identityManager)); //TODO: does this really need to be hard coded?

            if (loginConfig != null || deploymentInfo.getJaspiAuthenticationMechanism() != null) {

                //we don't allow multipart requests, and always use the default encoding
                FormParserFactory parser = FormParserFactory.builder(false)
                        .addParser(new FormEncodedDataDefinition().setDefaultEncoding(deploymentInfo.getDefaultEncoding()))
                        .build();

                List<AuthMethodConfig> authMethods = Collections.<AuthMethodConfig>emptyList();
                if(loginConfig != null) {
                    authMethods = loginConfig.getAuthMethods();
                }

                for(AuthMethodConfig method : authMethods) {
                    AuthenticationMechanismFactory factory = factoryMap.get(method.getName());
                    if(factory == null) {
                        throw UndertowServletMessages.MESSAGES.unknownAuthenticationMechanism(method.getName());
                    }
                    if(mechName == null) {
                        mechName = method.getName();
                    }

                    final Map<String, String> properties = new HashMap<>();
                    properties.put(AuthenticationMechanismFactory.CONTEXT_PATH, deploymentInfo.getContextPath());
                    properties.put(AuthenticationMechanismFactory.REALM, loginConfig.getRealmName());
                    properties.put(AuthenticationMechanismFactory.ERROR_PAGE, loginConfig.getErrorPage());
                    properties.put(AuthenticationMechanismFactory.LOGIN_PAGE, loginConfig.getLoginPage());
                    properties.putAll(method.getProperties());

                    String name = method.getName().toUpperCase(Locale.US);
                    // The mechanism name is passed in from the HttpServletRequest interface as the name reported needs to be
                    // comparable using '=='
                    name = name.equals(FORM_AUTH) ? FORM_AUTH : name;
                    name = name.equals(BASIC_AUTH) ? BASIC_AUTH : name;
                    name = name.equals(DIGEST_AUTH) ? DIGEST_AUTH : name;
                    name = name.equals(CLIENT_CERT_AUTH) ? CLIENT_CERT_AUTH : name;

                    authenticationMechanisms.add(factory.create(name, parser, properties));
                }
            }

            if(deploymentInfo.isUseCachedAuthenticationMechanism()) {
                authenticationMechanisms.add(new CachedAuthenticatedSessionMechanism(identityManager));
            }

            deployment.setAuthenticationMechanisms(authenticationMechanisms);
            //if the JASPI auth mechanism is set then it takes over
            if(deploymentInfo.getJaspiAuthenticationMechanism() == null) {
                current = new AuthenticationMechanismsHandler(current, authenticationMechanisms);
            } else {
                current = new AuthenticationMechanismsHandler(current, Collections.<AuthenticationMechanism>singletonList(deploymentInfo.getJaspiAuthenticationMechanism()));
            }

            current = new CachedAuthenticatedSessionHandler(current, this.deployment.getServletContext());
        }

        List<NotificationReceiver> notificationReceivers = deploymentInfo.getNotificationReceivers();
        if (!notificationReceivers.isEmpty()) {
            current = new NotificationReceiverHandler(current, notificationReceivers);
        }

        if (initialSecurityWrapper == null) {
            // TODO - A switch to constraint driven could be configurable, however before we can support that with servlets we would
            // need additional tracking within sessions if a servlet has specifically requested that authentication occurs.
            SecurityContextFactory contextFactory = deploymentInfo.getSecurityContextFactory();
            if (contextFactory == null) {
                contextFactory = SecurityContextFactoryImpl.INSTANCE;
            }
            current = new SecurityInitialHandler(deploymentInfo.getAuthenticationMode(), deploymentInfo.getIdentityManager(), mechName,
                    contextFactory, current);
        } else {
            current = initialSecurityWrapper.wrap(current);
        }

        return current;
    }

    private SecurityPathMatches buildSecurityConstraints() {
        SecurityPathMatches.Builder builder = SecurityPathMatches.builder(deployment.getDeploymentInfo());
        final Set<String> urlPatterns = new HashSet<>();
        for (SecurityConstraint constraint : deployment.getDeploymentInfo().getSecurityConstraints()) {
            builder.addSecurityConstraint(constraint);
            for (WebResourceCollection webResources : constraint.getWebResourceCollections()) {
                urlPatterns.addAll(webResources.getUrlPatterns());
            }
        }

        for (final ServletInfo servlet : deployment.getDeploymentInfo().getServlets().values()) {
            final ServletSecurityInfo securityInfo = servlet.getServletSecurityInfo();
            if (securityInfo != null) {
                final Set<String> mappings = new HashSet<>(servlet.getMappings());
                mappings.removeAll(urlPatterns);
                if (!mappings.isEmpty()) {
                    final Set<String> methods = new HashSet<>();

                    for (HttpMethodSecurityInfo method : securityInfo.getHttpMethodSecurityInfo()) {
                        methods.add(method.getMethod());
                        if (method.getRolesAllowed().isEmpty() && method.getEmptyRoleSemantic() == EmptyRoleSemantic.PERMIT) {
                            //this is an implict allow
                            continue;
                        }
                        SecurityConstraint newConstraint = new SecurityConstraint()
                                .addRolesAllowed(method.getRolesAllowed())
                                .setTransportGuaranteeType(method.getTransportGuaranteeType())
                                .addWebResourceCollection(new WebResourceCollection().addUrlPatterns(mappings)
                                        .addHttpMethod(method.getMethod()));
                        builder.addSecurityConstraint(newConstraint);
                    }
                    //now add the constraint, unless it has all default values and method constrains where specified
                    if (!securityInfo.getRolesAllowed().isEmpty()
                            || securityInfo.getEmptyRoleSemantic() != EmptyRoleSemantic.PERMIT
                            || methods.isEmpty()) {
                        SecurityConstraint newConstraint = new SecurityConstraint()
                                .setEmptyRoleSemantic(securityInfo.getEmptyRoleSemantic())
                                .addRolesAllowed(securityInfo.getRolesAllowed())
                                .setTransportGuaranteeType(securityInfo.getTransportGuaranteeType())
                                .addWebResourceCollection(new WebResourceCollection().addUrlPatterns(mappings)
                                        .addHttpMethodOmissions(methods));
                        builder.addSecurityConstraint(newConstraint);
                    }
                }

            }
        }

        return builder.build();
    }

    private void initializeTempDir(final ServletContextImpl servletContext, final DeploymentInfo deploymentInfo) {
        if (deploymentInfo.getTempDir() != null) {
            servletContext.setAttribute(ServletContext.TEMPDIR, deploymentInfo.getTempDir());
        } else {
            servletContext.setAttribute(ServletContext.TEMPDIR, new File(SecurityActions.getSystemProperty("java.io.tmpdir")));
        }
    }

    private void initializeMimeMappings(final DeploymentImpl deployment, final DeploymentInfo deploymentInfo) {
        final Map<String, String> mappings = new HashMap<>(MimeMappings.DEFAULT_MIME_MAPPINGS);
        for (MimeMapping mapping : deploymentInfo.getMimeMappings()) {
            mappings.put(mapping.getExtension().toLowerCase(Locale.ENGLISH), mapping.getMimeType());
        }
        deployment.setMimeExtensionMappings(mappings);
    }

    private void initializeErrorPages(final DeploymentImpl deployment, final DeploymentInfo deploymentInfo) {
        final Map<Integer, String> codes = new HashMap<>();
        final Map<Class<? extends Throwable>, String> exceptions = new HashMap<>();
        String defaultErrorPage = null;
        for (final ErrorPage page : deploymentInfo.getErrorPages()) {
            if (page.getExceptionType() != null) {
                exceptions.put(page.getExceptionType(), page.getLocation());
            } else if (page.getErrorCode() != null) {
                codes.put(page.getErrorCode(), page.getLocation());
            } else {
                if (defaultErrorPage != null) {
                    throw UndertowServletMessages.MESSAGES.moreThanOneDefaultErrorPage(defaultErrorPage, page.getLocation());
                } else {
                    defaultErrorPage = page.getLocation();
                }
            }
        }
        deployment.setErrorPages(new ErrorPages(codes, exceptions, defaultErrorPage));
    }


    private ApplicationListeners createListeners() {
        final List<ManagedListener> managedListeners = new ArrayList<>();
        for (final ListenerInfo listener : deployment.getDeploymentInfo().getListeners()) {
            managedListeners.add(new ManagedListener(listener, false));
        }
        return new ApplicationListeners(managedListeners, deployment.getServletContext());
    }


    private static HttpHandler wrapHandlers(final HttpHandler wrapee, final List<HandlerWrapper> wrappers) {
        HttpHandler current = wrapee;
        for (HandlerWrapper wrapper : wrappers) {
            current = wrapper.wrap(current);
        }
        return current;
    }

    @Override
    public HttpHandler start() throws ServletException {
        ThreadSetupAction.Handle handle = deployment.getThreadSetupAction().setup(null);
        try {
            deployment.getSessionManager().start();

            //we need to copy before iterating
            //because listeners can add other listeners
            ArrayList<Lifecycle> lifecycles = new ArrayList<>(deployment.getLifecycleObjects());
            for (Lifecycle object : lifecycles) {
                object.start();
            }
            HttpHandler root = deployment.getHandler();
            final TreeMap<Integer, List<ManagedServlet>> loadOnStartup = new TreeMap<>();
            for(Map.Entry<String, ServletHandler> entry: deployment.getServlets().getServletHandlers().entrySet()) {
                ManagedServlet servlet = entry.getValue().getManagedServlet();
                Integer loadOnStartupNumber = servlet.getServletInfo().getLoadOnStartup();
                if(loadOnStartupNumber != null) {
                    if(loadOnStartupNumber < 0) {
                        continue;
                    }
                    List<ManagedServlet> list = loadOnStartup.get(loadOnStartupNumber);
                    if(list == null) {
                        loadOnStartup.put(loadOnStartupNumber, list = new ArrayList<>());
                    }
                    list.add(servlet);
                }
            }
            for(Map.Entry<Integer, List<ManagedServlet>> load : loadOnStartup.entrySet()) {
                for(ManagedServlet servlet : load.getValue()) {
                    servlet.createServlet();
                }
            }

            if (deployment.getDeploymentInfo().isEagerFilterInit()){
                for(ManagedFilter filter: deployment.getFilters().getFilters().values()) {
                    filter.createFilter();
                }
            }

            state = State.STARTED;
            return root;
        } finally {
            handle.tearDown();
        }
    }

    @Override
    public void stop() throws ServletException {
        ThreadSetupAction.Handle handle = deployment.getThreadSetupAction().setup(null);
        try {
            for (Lifecycle object : deployment.getLifecycleObjects()) {
                object.stop();
            }
            deployment.getSessionManager().stop();
        } finally {
            handle.tearDown();
        }
        state = State.DEPLOYED;
    }

    private HttpHandler handleDevelopmentModePersistentSessions(HttpHandler next, final DeploymentInfo deploymentInfo, final SessionManager sessionManager, final ServletContextImpl servletContext) {
        final SessionPersistenceManager sessionPersistenceManager = deploymentInfo.getSessionPersistenceManager();
        if (sessionPersistenceManager != null) {
            SessionRestoringHandler handler = new SessionRestoringHandler(deployment.getDeploymentInfo().getDeploymentName(), sessionManager, servletContext, next, sessionPersistenceManager);
            deployment.addLifecycleObjects(handler);
            return handler;
        }
        return next;
    }

    public void handleDeploymentSessionConfig(DeploymentInfo deploymentInfo, ServletContextImpl servletContext) {
        SessionCookieConfigImpl sessionCookieConfig = servletContext.getSessionCookieConfig();
        ServletSessionConfig sc = deploymentInfo.getServletSessionConfig();
        if (sc != null) {
            sessionCookieConfig.setName(sc.getName());
            sessionCookieConfig.setComment(sc.getComment());
            sessionCookieConfig.setDomain(sc.getDomain());
            sessionCookieConfig.setHttpOnly(sc.isHttpOnly());
            sessionCookieConfig.setMaxAge(sc.getMaxAge());
            if(sc.getPath() != null) {
                sessionCookieConfig.setPath(sc.getPath());
            } else {
                sessionCookieConfig.setPath(deploymentInfo.getContextPath());
            }
            sessionCookieConfig.setSecure(sc.isSecure());
            if (sc.getSessionTrackingModes() != null) {
                servletContext.setDefaultSessionTrackingModes(new HashSet<>(sc.getSessionTrackingModes()));
            }
        }
    }


    @Override
    public void undeploy() {
        ThreadSetupAction.Handle handle = deployment.getThreadSetupAction().setup(null);
        try {
            deployment.destroy();
            deployment = null;
        } finally {
            handle.tearDown();
        }
        state = State.UNDEPLOYED;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public Deployment getDeployment() {
        return deployment;
    }

}
