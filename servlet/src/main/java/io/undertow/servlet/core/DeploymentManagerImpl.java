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

import static javax.servlet.http.HttpServletRequest.BASIC_AUTH;
import static javax.servlet.http.HttpServletRequest.CLIENT_CERT_AUTH;
import static javax.servlet.http.HttpServletRequest.FORM_AUTH;

import io.undertow.predicate.Predicates;
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
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.AttachmentHandler;
import io.undertow.server.handlers.PredicateHandler;
import io.undertow.servlet.ServletExtension;
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
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.servlet.handlers.DispatcherTypePredicate;
import io.undertow.servlet.handlers.ServletDispatchingHandler;
import io.undertow.servlet.handlers.ServletInitialHandler;
import io.undertow.servlet.handlers.security.CachedAuthenticatedSessionHandler;
import io.undertow.servlet.handlers.security.SSLInformationAssociationHandler;
import io.undertow.servlet.handlers.security.SecurityPathMatches;
import io.undertow.servlet.handlers.security.ServletAuthenticationConstraintHandler;
import io.undertow.servlet.handlers.security.ServletConfidentialityConstraintHandler;
import io.undertow.servlet.handlers.security.ServletFormAuthenticationMechanism;
import io.undertow.servlet.handlers.security.ServletSecurityConstraintHandler;
import io.undertow.servlet.spec.AsyncContextImpl;
import io.undertow.servlet.spec.ServletContextImpl;
import io.undertow.util.MimeMappings;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

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
    private volatile InstanceHandle<Executor> executor;
    private volatile InstanceHandle<Executor> asyncExecutor;

    public DeploymentManagerImpl(final DeploymentInfo deployment, final ServletContainer servletContainer) {
        this.originalDeployment = deployment;
        this.servletContainer = servletContainer;
    }

    @Override
    public void deploy() {
        DeploymentInfo deploymentInfo = originalDeployment.clone();

        deploymentInfo.validate();
        final DeploymentImpl deployment = new DeploymentImpl(deploymentInfo);
        this.deployment = deployment;

        final ServletContextImpl servletContext = new ServletContextImpl(servletContainer, deployment);

        handleExtensions(deploymentInfo, servletContext);

        deployment.setServletContext(servletContext);
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
            HttpHandler securityHandler  = setupSecurityHandlers(wrappedHandlers);
            wrappedHandlers = new PredicateHandler(DispatcherTypePredicate.REQUEST, securityHandler, wrappedHandlers);

            HttpHandler outerHandlers = wrapHandlers(wrappedHandlers, deploymentInfo.getOuterHandlerChainWrappers());
            wrappedHandlers = new PredicateHandler(Predicates.or(DispatcherTypePredicate.REQUEST, DispatcherTypePredicate.ASYNC), outerHandlers, wrappedHandlers);

            final ServletInitialHandler servletInitialHandler = new ServletInitialHandler(deployment.getServletPaths(), wrappedHandlers, deployment.getThreadSetupAction(), servletContext);


            HttpHandler initialHandler = wrapHandlers(servletInitialHandler, deployment.getDeploymentInfo().getInitialHandlerChainWrappers());

            deployment.setInitialHandler(initialHandler);
            deployment.setServletHandler(servletInitialHandler);
            deployment.getServletPaths().invalidate(); //make sure we have a fresh set of servlet paths
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            handle.tearDown();
        }
        state = State.DEPLOYED;
    }

    private void createServletsAndFilters(final DeploymentImpl deployment, final DeploymentInfo deploymentInfo) {
        for(Map.Entry<String, ServletInfo> servlet : deploymentInfo.getServlets().entrySet()) {
            deployment.getServlets().addServlet(servlet.getValue());
        }
        for(Map.Entry<String, FilterInfo> filter : deploymentInfo.getFilters().entrySet()) {
            deployment.getFilters().addFilter(filter.getValue());
        }
    }

    private void handleExtensions(final DeploymentInfo deploymentInfo, final ServletContextImpl servletContext) {
        for(ServletExtension extension : ServiceLoader.load(ServletExtension.class, deploymentInfo.getClassLoader())) {
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
        current = new AuthenticationCallHandler(current);
        if(!securityPathMatches.isEmpty()) {
            current = new ServletAuthenticationConstraintHandler(current);
        }
        current = new ServletConfidentialityConstraintHandler(deploymentInfo.getConfidentialPortManager(), current);
        if(!securityPathMatches.isEmpty()) {
            current = new ServletSecurityConstraintHandler(securityPathMatches, current);
        }

        String mechName = null;
        if(loginConfig != null || !deploymentInfo.getAdditionalAuthenticationMechanisms().isEmpty()) {
            List<AuthenticationMechanism> authenticationMechanisms = new LinkedList<AuthenticationMechanism>();
            authenticationMechanisms.add(new CachedAuthenticatedSessionMechanism());
            authenticationMechanisms.addAll(deploymentInfo.getAdditionalAuthenticationMechanisms());

            if (loginConfig != null) {

                mechName = loginConfig.getAuthMethod();
                if (!deploymentInfo.isIgnoreStandardAuthenticationMechanism()) {
                    if (mechName.equalsIgnoreCase(BASIC_AUTH)) {
                        // The mechanism name is passed in from the HttpServletRequest interface as the name reported needs to be
                        // comparable using '=='
                        authenticationMechanisms.add(new BasicAuthenticationMechanism(loginConfig.getRealmName(), BASIC_AUTH));
                    } else if (mechName.equalsIgnoreCase(FORM_AUTH)) {
                        // The mechanism name is passed in from the HttpServletRequest interface as the name reported needs to be
                        // comparable using '=='
                        authenticationMechanisms.add(new ServletFormAuthenticationMechanism(FORM_AUTH, loginConfig.getLoginPage(),
                                loginConfig.getErrorPage()));
                    } else if (mechName.equalsIgnoreCase(CLIENT_CERT_AUTH)) {
                        authenticationMechanisms.add(new ClientCertAuthenticationMechanism(CLIENT_CERT_AUTH));
                    } else {
                        throw UndertowServletMessages.MESSAGES.unknownAuthenticationMechanism(mechName);
                    }
                }
            }
            current = new AuthenticationMechanismsHandler(current, authenticationMechanisms);
        }

        current = new CachedAuthenticatedSessionHandler(current, this.deployment.getServletContext());
        List<NotificationReceiver> notificationReceivers = deploymentInfo.getNotificationReceivers();
        if (notificationReceivers.isEmpty() == false) {
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

                    SecurityConstraint newConstraint = new SecurityConstraint()
                            .addRolesAllowed(securityInfo.getRolesAllowed())
                            .setTransportGuaranteeType(securityInfo.getTransportGuaranteeType())
                            .addWebResourceCollection(new WebResourceCollection().addUrlPatterns(mappings)
                                    .addHttpMethodOmissions(methods));
                    builder.addSecurityConstraint(newConstraint);
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
            } else if(page.getErrorCode() != null){
                codes.put(page.getErrorCode(), page.getLocation());
            } else {
                if(defaultErrorPage != null) {
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
            managedListeners.add(new ManagedListener(listener));
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
            for (Lifecycle object : deployment.getLifecycleObjects()) {
                object.start();
            }
            HttpHandler root = deployment.getHandler();

            //create the executor, if it exists
            if (deployment.getDeploymentInfo().getExecutorFactory() != null) {
                try {
                    executor = deployment.getDeploymentInfo().getExecutorFactory().createInstance();
                    root = new AttachmentHandler<>(HttpServerExchange.DISPATCH_EXECUTOR, root, executor.getInstance());
                } catch (InstantiationException e) {
                    throw new RuntimeException(e);
                }
            }
            if (deployment.getDeploymentInfo().getExecutorFactory() != null) {
                if (deployment.getDeploymentInfo().getAsyncExecutorFactory() != null) {
                    try {
                        asyncExecutor = deployment.getDeploymentInfo().getAsyncExecutorFactory().createInstance();
                        root = new AttachmentHandler<>(AsyncContextImpl.ASYNC_EXECUTOR, root, asyncExecutor.getInstance());
                    } catch (InstantiationException e) {
                        throw new RuntimeException(e);
                    }
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
            try {
                for (Lifecycle object : deployment.getLifecycleObjects()) {
                    object.stop();
                }
            } finally {
                if (executor != null) {
                    executor.release();
                }
                if (asyncExecutor != null) {
                    asyncExecutor.release();
                }
                executor = null;
                asyncExecutor = null;
            }
            deployment.getSessionManager().stop();
        } finally {
            handle.tearDown();
            if (executor != null) {
                executor.release();
            }
            if (asyncExecutor != null) {
                asyncExecutor.release();
            }
            executor = null;
            asyncExecutor = null;
        }
        state = State.DEPLOYED;
    }

    @Override
    public void undeploy() {
        ThreadSetupAction.Handle handle = deployment.getThreadSetupAction().setup(null);
        try {
            deployment.getApplicationListeners().contextDestroyed();
            deployment.getApplicationListeners().stop();
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
