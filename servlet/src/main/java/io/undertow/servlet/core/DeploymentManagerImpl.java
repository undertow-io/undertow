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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.servlet.DispatcherType;
import javax.servlet.Servlet;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.AttachmentHandler;
import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.DefaultServletConfig;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ErrorPage;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.FilterMappingInfo;
import io.undertow.servlet.api.HandlerChainWrapper;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.MimeMapping;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletContainerInitializerInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.handlers.DefaultServlet;
import io.undertow.servlet.handlers.FilterHandler;
import io.undertow.servlet.handlers.RequestListenerHandler;
import io.undertow.servlet.handlers.ServletHandler;
import io.undertow.servlet.handlers.ServletInitialHandler;
import io.undertow.servlet.handlers.ServletMatchingHandler;
import io.undertow.servlet.handlers.ServletPathMatches;
import io.undertow.servlet.spec.AsyncContextImpl;
import io.undertow.servlet.spec.ServletContextImpl;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.util.WorkerDispatcher;

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
     * Current delpoyment, this may be modified by SCI's
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
        deployment.setServletContext(servletContext);

        final List<ThreadSetupAction> setup = new ArrayList<ThreadSetupAction>();
        setup.add(new ContextClassLoaderSetupAction(deploymentInfo.getClassLoader()));
        setup.addAll(deploymentInfo.getThreadSetupActions());
        final CompositeThreadSetupAction threadSetupAction = new CompositeThreadSetupAction(setup);
        deployment.setThreadSetupAction(threadSetupAction);

        //TODO: this is just a temporary hack, this will probably change a lot
        ThreadSetupAction.Handle handle = threadSetupAction.setup(null);
        try {

            final ApplicationListeners listeners = createListeners();
            deployment.setApplicationListeners(listeners);
            //first run the SCI's
            for (final ServletContainerInitializerInfo sci : deploymentInfo.getServletContainerInitializers()) {
                final InstanceHandle<? extends ServletContainerInitializer> instance = sci.getInstanceFactory().createInstance();
                try {
                    instance.getInstance().onStartup(sci.getHandlesTypes(), servletContext);
                } finally {
                    instance.release();
                }
            }

            listeners.contextInitialized();
            initializeErrorPages(deployment, deploymentInfo);
            initializeMimeMappings(deployment, deploymentInfo);
            initializeTempDir(servletContext, deploymentInfo);
            //run

            ServletPathMatches matches = setupServletChains(servletContext, threadSetupAction, listeners);
            deployment.setServletPaths(matches);
            final ServletMatchingHandler servletMatchingHandler = new ServletMatchingHandler(matches);

            deployment.setServletHandler(servletMatchingHandler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            handle.tearDown();
        }
    }

    private void initializeTempDir(final ServletContextImpl servletContext, final DeploymentInfo deploymentInfo) {
        if (deploymentInfo.getTempDir() != null) {
            servletContext.setAttribute(ServletContext.TEMPDIR, deploymentInfo.getTempDir());
        } else {
            servletContext.setAttribute(ServletContext.TEMPDIR, new File(SecurityActions.getSystemProperty("java.io.tmpdir")));
        }
    }

    private void initializeMimeMappings(final DeploymentImpl deployment, final DeploymentInfo deploymentInfo) {
        final Map<String, String> mappings = new HashMap<String, String>(MimeMapping.DEFAULT_MIME_MAPPINGS);
        for (MimeMapping mapping : deploymentInfo.getMimeMappings()) {
            mappings.put(mapping.getExtension(), mapping.getMimeType());
        }
        deployment.setMimeExtensionMappings(mappings);
    }

    private void initializeErrorPages(final DeploymentImpl deployment, final DeploymentInfo deploymentInfo) {
        final Map<Integer, String> codes = new HashMap<Integer, String>();
        final Map<Class<? extends Throwable>, String> exceptions = new HashMap<Class<? extends Throwable>, String>();

        for (final ErrorPage page : deploymentInfo.getErrorPages()) {
            if (page.getExceptionType() != null) {
                exceptions.put(page.getExceptionType(), page.getLocation());
            } else {
                codes.put(page.getErrorCode(), page.getLocation());
            }
        }
        deployment.setErrorPages(new ErrorPages(codes, exceptions));
    }

    /**
     * Sets up the handlers in the servlet chain. We setup a chain for every path + extension match possibility.
     * (i.e. if there a m path mappings and n extension mappings we have n*m chains).
     * <p/>
     * If a chain consists of only the default servlet then we add it as an async handler, so that resources can be
     * served up directly without using blocking operations.
     * <p/>
     * TODO: this logic is a bit convoluted at the moment, we should look at simplifying it
     *
     * @param servletContext
     * @param threadSetupAction
     * @param listeners
     */
    private ServletPathMatches setupServletChains(final ServletContextImpl servletContext, final CompositeThreadSetupAction threadSetupAction, final ApplicationListeners listeners) {
        final List<Lifecycle> lifecycles = new ArrayList<Lifecycle>();
        //create the default servlet
        ServletInitialHandler defaultHandler = null;
        ServletHandler defaultServlet = null;

        final Map<String, ManagedFilter> managedFilterMap = new LinkedHashMap<String, ManagedFilter>();
        final Map<String, ServletHandler> allServlets = new HashMap<String, ServletHandler>();
        final Map<String, ServletHandler> extensionServlets = new HashMap<String, ServletHandler>();
        final Map<String, ServletHandler> pathServlets = new HashMap<String, ServletHandler>();


        final Set<String> pathMatches = new HashSet<String>();
        final Set<String> extensionMatches = new HashSet<String>();

        DeploymentInfo deploymentInfo = deployment.getDeploymentInfo();
        for (Map.Entry<String, FilterInfo> entry : deploymentInfo.getFilters().entrySet()) {
            final ManagedFilter mf = new ManagedFilter(entry.getValue(), servletContext);
            managedFilterMap.put(entry.getValue().getName(), mf);
            lifecycles.add(mf);
        }

        for (FilterMappingInfo mapping : deploymentInfo.getFilterMappings()) {
            if (mapping.getMappingType() == FilterMappingInfo.MappingType.URL) {
                String path = mapping.getMapping();
                if (!path.startsWith("*.")) {
                    pathMatches.add(path);
                } else {
                    extensionMatches.add(path.substring(2));
                }
            }
        }

        for (Map.Entry<String, ServletInfo> entry : deploymentInfo.getServlets().entrySet()) {
            ServletInfo servlet = entry.getValue();
            final ManagedServlet managedServlet = new ManagedServlet(servlet, servletContext);
            lifecycles.add(managedServlet);
            final ServletHandler handler = new ServletHandler(managedServlet);
            allServlets.put(entry.getKey(), handler);
            for (String path : entry.getValue().getMappings()) {
                if (path.equals("/")) {
                    //the default servlet
                    defaultServlet = handler;
                    defaultHandler = servletChain(handler, threadSetupAction, listeners, managedServlet);
                } else if (!path.startsWith("*.")) {
                    pathMatches.add(path);
                    if (pathServlets.containsKey(path)) {
                        throw UndertowServletMessages.MESSAGES.twoServletsWithSameMapping(path);
                    }
                    pathServlets.put(path, handler);
                } else {
                    String ext = path.substring(2);
                    extensionMatches.add(ext);
                    extensionServlets.put(ext, handler);
                }
            }
        }

        if (defaultServlet == null) {
            final DefaultServletConfig config = deploymentInfo.getDefaultServletConfig() == null ? new DefaultServletConfig() : deploymentInfo.getDefaultServletConfig();
            DefaultServlet defaultInstance = new DefaultServlet(deployment, config, deploymentInfo.getWelcomePages());
            final ManagedServlet managedDefaultServlet = new ManagedServlet(new ServletInfo("io.undertow.DefaultServlet", DefaultServlet.class, new ImmediateInstanceFactory<Servlet>(defaultInstance)), servletContext);
            lifecycles.add(managedDefaultServlet);
            defaultServlet = new ServletHandler(managedDefaultServlet);

            defaultHandler = new ServletInitialHandler(new RequestListenerHandler(listeners, defaultServlet), defaultInstance, threadSetupAction, servletContext, managedDefaultServlet);
        }

        final ServletPathMatches.Builder builder = ServletPathMatches.builder();

        boolean defaultServletSupplied = false;

        for (final String path : pathMatches) {
            if (path.equals("/*")) {
                defaultServletSupplied = true;
            }
            ServletHandler targetServlet = resolveServletForPath(path, pathServlets);

            final Map<DispatcherType, List<ManagedFilter>> noExtension = new HashMap<DispatcherType, List<ManagedFilter>>();
            final Map<String, Map<DispatcherType, List<ManagedFilter>>> extension = new HashMap<String, Map<DispatcherType, List<ManagedFilter>>>();
            for (String ext : extensionMatches) {
                extension.put(ext, new HashMap<DispatcherType, List<ManagedFilter>>());
            }

            for (final FilterMappingInfo filterMapping : deploymentInfo.getFilterMappings()) {
                ManagedFilter filter = managedFilterMap.get(filterMapping.getFilterName());
                if (filterMapping.getMappingType() == FilterMappingInfo.MappingType.SERVLET) {
                    if (targetServlet != null) {
                        if (filterMapping.getMapping().equals(targetServlet.getManagedServlet().getServletInfo().getName())) {
                            addToListMap(noExtension, filterMapping.getDispatcher(), filter);
                            for (Map<DispatcherType, List<ManagedFilter>> l : extension.values()) {
                                addToListMap(l, filterMapping.getDispatcher(), filter);
                            }
                        }
                    }
                } else {
                    if (path.isEmpty() || !path.startsWith("*.")) {
                        if (isFilterApplicable(path, filterMapping.getMapping())) {
                            addToListMap(noExtension, filterMapping.getDispatcher(), filter);
                            for (Map<DispatcherType, List<ManagedFilter>> l : extension.values()) {
                                addToListMap(l, filterMapping.getDispatcher(), filter);
                            }
                        }
                    } else {
                        addToListMap(extension.get(path.substring(2)), filterMapping.getDispatcher(), filter);
                    }
                }
            }

            final ServletInitialHandler initialHandler;
            if (noExtension.isEmpty()) {
                if (targetServlet != null) {
                    initialHandler = servletChain(targetServlet, threadSetupAction, listeners, targetServlet.getManagedServlet());
                } else {
                    initialHandler = defaultHandler;
                }
            } else {
                FilterHandler handler;
                if (targetServlet != null) {
                    handler = new FilterHandler(noExtension, targetServlet);
                } else {
                    handler = new FilterHandler(noExtension, defaultServlet);
                }
                initialHandler = servletChain(handler, threadSetupAction, listeners, targetServlet == null ? defaultServlet.getManagedServlet() : targetServlet.getManagedServlet());
            }

            if (path.endsWith("/*")) {
                String prefix = path.substring(0, path.length() - 2);
                builder.addPrefixMatch(prefix, initialHandler);

                for (Map.Entry<String, Map<DispatcherType, List<ManagedFilter>>> entry : extension.entrySet()) {
                    ServletHandler pathServlet = targetServlet;
                    if (targetServlet == null) {
                        pathServlet = extensionServlets.get(entry.getKey());
                    }
                    if (!entry.getValue().isEmpty()) {
                        FilterHandler handler;
                        if (pathServlet != null) {
                            handler = new FilterHandler(entry.getValue(), pathServlet);
                        } else {
                            handler = new FilterHandler(entry.getValue(), defaultServlet);
                        }
                        builder.addExtensionMatch(prefix, entry.getKey(), servletChain(handler, threadSetupAction, listeners, pathServlet == null ? defaultServlet.getManagedServlet() : pathServlet.getManagedServlet()));
                    }
                }
            } else if (path.isEmpty()) {
                builder.addExactMatch("/", initialHandler);
            } else {
                builder.addExactMatch(path, initialHandler);
            }
        }
        if (!defaultServletSupplied) {
            builder.addPrefixMatch("", defaultHandler);
        }

        //now handle extension matches for the default path
        if (!defaultServletSupplied) {
            for (final String path : extensionMatches) {
                ServletHandler targetServlet = extensionServlets.get(path);

                final Map<DispatcherType, List<ManagedFilter>> extension = new HashMap<DispatcherType, List<ManagedFilter>>();
                for (final FilterMappingInfo filterMapping : deploymentInfo.getFilterMappings()) {
                    ManagedFilter filter = managedFilterMap.get(filterMapping.getFilterName());
                    if (filterMapping.getMappingType() == FilterMappingInfo.MappingType.SERVLET) {
                        if (targetServlet != null) {
                            if (filterMapping.getMapping().equals(targetServlet.getManagedServlet().getServletInfo().getName())) {
                                addToListMap(extension, filterMapping.getDispatcher(), filter);
                            }
                        }
                    } else {
                        if (filterMapping.getMapping().startsWith("*.")) {
                            if (filterMapping.getMapping().substring(2).equals(path)) {
                                addToListMap(extension, filterMapping.getDispatcher(), filter);
                            }
                        }
                    }
                }

                if (extension.isEmpty() && targetServlet != null) {
                    builder.addExtensionMatch("", path, servletChain(targetServlet, threadSetupAction, listeners, targetServlet.getManagedServlet()));
                } else if (!extension.isEmpty()) {
                    FilterHandler handler;
                    if (targetServlet != null) {
                        handler = new FilterHandler(extension, targetServlet);
                    } else {
                        handler = new FilterHandler(extension, defaultServlet);
                    }
                    builder.addExtensionMatch("", path, servletChain(handler, threadSetupAction, listeners, targetServlet == null ? defaultServlet.getManagedServlet() : targetServlet.getManagedServlet()));
                }
            }
        }

        //now setup name based mappings
        //these are used for name based dispatch
        for (Map.Entry<String, ServletHandler> entry : allServlets.entrySet()) {
            final Map<DispatcherType, List<ManagedFilter>> filters = new HashMap<DispatcherType, List<ManagedFilter>>();
            for (final FilterMappingInfo filterMapping : deploymentInfo.getFilterMappings()) {
                ManagedFilter filter = managedFilterMap.get(filterMapping.getFilterName());
                if (filterMapping.getMappingType() == FilterMappingInfo.MappingType.SERVLET) {
                    if (filterMapping.getMapping().equals(entry.getKey())) {
                        addToListMap(filters, filterMapping.getDispatcher(), filter);
                    }
                }
            }
            if (filters.isEmpty()) {
                builder.addNameMatch(entry.getKey(), servletChain(entry.getValue(), threadSetupAction, listeners, entry.getValue().getManagedServlet()));
            } else {
                builder.addNameMatch(entry.getKey(), servletChain(new FilterHandler(filters, entry.getValue()), threadSetupAction, listeners, entry.getValue().getManagedServlet()));
            }
        }


        builder.setDefaultServlet(defaultHandler);

        deployment.addLifecycleObjects(lifecycles);
        return builder.build();
    }


    private ApplicationListeners createListeners() {
        final List<ManagedListener> managedListeners = new ArrayList<ManagedListener>();
        for (final ListenerInfo listener : deployment.getDeploymentInfo().getListeners()) {
            managedListeners.add(new ManagedListener(listener, deployment.getServletContext()));
        }
        return new ApplicationListeners(managedListeners, deployment.getServletContext());
    }

    private ServletInitialHandler servletChain(BlockingHttpHandler next, final CompositeThreadSetupAction setupAction, final ApplicationListeners applicationListeners, final ManagedServlet managedServlet) {
        BlockingHttpHandler servletHandler = new RequestListenerHandler(applicationListeners, next);
        for (HandlerChainWrapper wrapper : managedServlet.getServletInfo().getHandlerChainWrappers()) {
            servletHandler = wrapper.wrap(servletHandler);
        }
        return new ServletInitialHandler(servletHandler, setupAction, deployment.getServletContext(), managedServlet);
    }

    private ServletHandler resolveServletForPath(final String path, final Map<String, ServletHandler> pathServlets) {
        if (pathServlets.containsKey(path)) {
            return pathServlets.get(path);
        }
        String match = null;
        ServletHandler servlet = null;
        for (final Map.Entry<String, ServletHandler> entry : pathServlets.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("/*")) {
                final String base = key.substring(0, key.length() - 2);
                if (match == null || base.length() > match.length()) {
                    if (path.startsWith(base)) {
                        match = base;
                        servlet = entry.getValue();
                    }
                }
            }
        }
        return servlet;
    }

    private boolean isFilterApplicable(final String path, final String filterPath) {
        if (path.isEmpty()) {
            return filterPath.equals("/*") || filterPath.equals("/");
        }
        if (filterPath.endsWith("/*")) {
            String baseFilterPath = filterPath.substring(0, filterPath.length() - 1);
            return path.startsWith(baseFilterPath);
        } else {
            return filterPath.equals(path);
        }
    }

    @Override
    public HttpHandler start() throws ServletException {
        for (Lifecycle object : deployment.getLifecycleObjects()) {
            object.start();
        }
        HttpHandler root = deployment.getServletHandler();

        //create the executor, if it exists
        if (deployment.getDeploymentInfo().getExecutorFactory() != null) {
            try {
                executor = deployment.getDeploymentInfo().getExecutorFactory().createInstance();
                root = new AttachmentHandler<Executor>(WorkerDispatcher.EXECUTOR_ATTACHMENT_KEY, root, executor.getInstance());
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            }
        }
        if (deployment.getDeploymentInfo().getExecutorFactory() != null) {
            if (deployment.getDeploymentInfo().getAsyncExecutorFactory() != null) {
                try {
                    asyncExecutor = deployment.getDeploymentInfo().getAsyncExecutorFactory().createInstance();
                    root = new AttachmentHandler<Executor>(AsyncContextImpl.ASYNC_EXECUTOR, root, asyncExecutor.getInstance());
                } catch (InstantiationException e) {
                    throw new RuntimeException(e);
                }
            }

        }


        return root;
    }

    @Override
    public void stop() throws ServletException {
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
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public Deployment getDeployment() {
        return deployment;
    }

    private static <K, V> void addToListMap(final Map<K, List<V>> map, final K key, final V value) {
        List<V> list = map.get(key);
        if (list == null) {
            map.put(key, list = new ArrayList<V>());
        }
        list.add(value);
    }
}
