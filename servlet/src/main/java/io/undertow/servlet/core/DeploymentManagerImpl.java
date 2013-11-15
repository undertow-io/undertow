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

package io.undertow.servlet.core;

import io.undertow.Handlers;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.NotificationReceiver;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.NotificationReceiverHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.security.impl.CachedAuthenticatedSessionMechanism;
import io.undertow.security.impl.ClientCertAuthenticationMechanism;
import io.undertow.security.impl.DigestAuthenticationMechanism;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.HttpContinueReadHandler;
import io.undertow.server.handlers.PredicateHandler;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ErrorPage;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.HttpMethodSecurityInfo;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.LoginConfig;
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
import io.undertow.servlet.handlers.ServletDispatchingHandler;
import io.undertow.servlet.handlers.ServletInitialHandler;
import io.undertow.servlet.handlers.SessionRestoringHandler;
import io.undertow.servlet.handlers.security.CachedAuthenticatedSessionHandler;
import io.undertow.servlet.handlers.security.SSLInformationAssociationHandler;
import io.undertow.servlet.handlers.security.SecurityPathMatches;
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
import javax.servlet.SessionTrackingMode;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

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
        final DeploymentImpl deployment = new DeploymentImpl(deploymentInfo, servletContainer);
        this.deployment = deployment;

        final ServletContextImpl servletContext = new ServletContextImpl(servletContainer, deployment);
        deployment.setServletContext(servletContext);
        handleExtensions(deploymentInfo, servletContext);

        handleDeploymentSessionConfig(deploymentInfo, servletContext);

        deployment.setSessionManager(deploymentInfo.getSessionManagerFactory().createSessionManager(deployment));
        deployment.getSessionManager().setDefaultSessionTimeout(deploymentInfo.getDefaultSessionTimeout());

        final List<ThreadSetupAction> setup = new ArrayList<ThreadSetupAction>();
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

            initializeErrorPages(deployment, deploymentInfo);
            initializeMimeMappings(deployment, deploymentInfo);
            initializeTempDir(servletContext, deploymentInfo);
            listeners.contextInitialized();
            //run

            HttpHandler wrappedHandlers = ServletDispatchingHandler.INSTANCE;
            wrappedHandlers = wrapHandlers(wrappedHandlers, deploymentInfo.getInnerHandlerChainWrappers());
            HttpHandler securityHandler = setupSecurityHandlers(wrappedHandlers);
            wrappedHandlers = new PredicateHandler(DispatcherTypePredicate.REQUEST, securityHandler, wrappedHandlers);

            HttpHandler outerHandlers = wrapHandlers(wrappedHandlers, deploymentInfo.getOuterHandlerChainWrappers());
            wrappedHandlers = new PredicateHandler(DispatcherTypePredicate.REQUEST, outerHandlers, wrappedHandlers);

            final ServletInitialHandler servletInitialHandler = new ServletInitialHandler(deployment.getServletPaths(), wrappedHandlers, deployment.getThreadSetupAction(), servletContext);


            HttpHandler initialHandler = wrapHandlers(servletInitialHandler, deployment.getDeploymentInfo().getInitialHandlerChainWrappers());
            initialHandler = new HttpContinueReadHandler(initialHandler);
            initialHandler = handleDevelopmentModePersistentSessions(initialHandler, deploymentInfo, deployment.getSessionManager(), servletContext);
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
        Set<Class<?>> loadedExtensions = new HashSet<Class<?>>();

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
        current = new AuthenticationCallHandler(current);
        if (!securityPathMatches.isEmpty()) {
            current = new ServletAuthenticationConstraintHandler(current);
        }
        current = new ServletConfidentialityConstraintHandler(deploymentInfo.getConfidentialPortManager(), current);
        if (!securityPathMatches.isEmpty()) {
            current = new ServletSecurityConstraintHandler(securityPathMatches, current);
        }

        String mechName = null;
        if (loginConfig != null || !deploymentInfo.getAdditionalAuthenticationMechanisms().isEmpty()) {
            List<AuthenticationMechanism> authenticationMechanisms = new LinkedList<AuthenticationMechanism>();
            authenticationMechanisms.add(new CachedAuthenticatedSessionMechanism());
            authenticationMechanisms.addAll(deploymentInfo.getAdditionalAuthenticationMechanisms());

            if (loginConfig != null) {

                mechName = loginConfig.getAuthMethod();
                if (!deploymentInfo.isIgnoreStandardAuthenticationMechanism()) {
                    if (mechName != null) {
                        String[] mechanisms = mechName.split(",");
                        for (String mechanism : mechanisms) {
                            if (mechanism.equalsIgnoreCase(BASIC_AUTH)) {
                                // The mechanism name is passed in from the HttpServletRequest interface as the name reported needs to be
                                // comparable using '=='
                                authenticationMechanisms.add(new BasicAuthenticationMechanism(loginConfig.getRealmName(), BASIC_AUTH));
                            } else if (mechanism.equalsIgnoreCase("BASIC-SILENT")) {
                                //slient basic auth with use the basic headers if available, but will never challenge
                                //this allows programtic clients to use basic auth, and browsers to use other mechanisms
                                authenticationMechanisms.add(new BasicAuthenticationMechanism(loginConfig.getRealmName(), "BASIC-SILENT", true));
                            } else if (mechanism.equalsIgnoreCase(FORM_AUTH)) {
                                // The mechanism name is passed in from the HttpServletRequest interface as the name reported needs to be
                                // comparable using '=='
                                authenticationMechanisms.add(new ServletFormAuthenticationMechanism(FORM_AUTH, loginConfig.getLoginPage(),
                                        loginConfig.getErrorPage()));
                            } else if (mechanism.equalsIgnoreCase(CLIENT_CERT_AUTH)) {
                                authenticationMechanisms.add(new ClientCertAuthenticationMechanism());
                            } else if (mechanism.equalsIgnoreCase(DIGEST_AUTH)) {
                                authenticationMechanisms.add(new DigestAuthenticationMechanism(loginConfig.getRealmName(), deploymentInfo.getContextPath(), DIGEST_AUTH));
                            } else {
                                throw UndertowServletMessages.MESSAGES.unknownAuthenticationMechanism(mechanism);
                            }
                        }
                    }
                }
            }
            current = new AuthenticationMechanismsHandler(current, authenticationMechanisms);
        }

        current = new CachedAuthenticatedSessionHandler(current, this.deployment.getServletContext());
        List<NotificationReceiver> notificationReceivers = deploymentInfo.getNotificationReceivers();
        if (!notificationReceivers.isEmpty()) {
            current = new NotificationReceiverHandler(current, notificationReceivers);
        }

        // TODO - A switch to constraint driven could be configurable, however before we can support that with servlets we would
        // need additional tracking within sessions if a servlet has specifically requested that authentication occurs.
        current = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, deploymentInfo.getIdentityManager(), mechName, current);
        return current;
    }

    private SecurityPathMatches buildSecurityConstraints() {
        SecurityPathMatches.Builder builder = SecurityPathMatches.builder(deployment.getDeploymentInfo());
        final Set<String> urlPatterns = new HashSet<String>();
        for (SecurityConstraint constraint : deployment.getDeploymentInfo().getSecurityConstraints()) {
            builder.addSecurityConstraint(constraint);
            for (WebResourceCollection webResources : constraint.getWebResourceCollections()) {
                urlPatterns.addAll(webResources.getUrlPatterns());
            }
        }

        for (final ServletInfo servlet : deployment.getDeploymentInfo().getServlets().values()) {
            final ServletSecurityInfo securityInfo = servlet.getServletSecurityInfo();
            if (securityInfo != null) {
                final Set<String> mappings = new HashSet<String>(servlet.getMappings());
                mappings.removeAll(urlPatterns);
                if (!mappings.isEmpty()) {
                    final Set<String> methods = new HashSet<String>();

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
        final Map<String, String> mappings = new HashMap<String, String>(MimeMappings.DEFAULT_MIME_MAPPINGS);
        for (MimeMapping mapping : deploymentInfo.getMimeMappings()) {
            mappings.put(mapping.getExtension(), mapping.getMimeType());
        }
        deployment.setMimeExtensionMappings(mappings);
    }

    private void initializeErrorPages(final DeploymentImpl deployment, final DeploymentInfo deploymentInfo) {
        final Map<Integer, String> codes = new HashMap<Integer, String>();
        final Map<Class<? extends Throwable>, String> exceptions = new HashMap<Class<? extends Throwable>, String>();
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
        final List<ManagedListener> managedListeners = new ArrayList<ManagedListener>();
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
            ArrayList<Lifecycle> lifecycles = new ArrayList<Lifecycle>(deployment.getLifecycleObjects());
            for (Lifecycle object : lifecycles) {
                object.start();
            }
            HttpHandler root = deployment.getHandler();

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
                servletContext.setDefaultSessionTrackingModes(new HashSet<SessionTrackingMode>(sc.getSessionTrackingModes()));
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
