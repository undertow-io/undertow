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

package io.undertow.servlet.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.Servlet;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.DefaultServletConfig;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.FilterMappingInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.core.Filters;
import io.undertow.servlet.core.ManagedFilter;
import io.undertow.servlet.core.ManagedServlet;
import io.undertow.servlet.core.Servlets;
import io.undertow.servlet.handlers.security.ServletSecurityRoleHandler;
import io.undertow.servlet.util.ImmediateInstanceFactory;

/**
 * Facade around {@link ServletPathMatchesData}. This facade is responsible for re-generating the matches if anything changes.
 *
 * @author Stuart Douglas
 */
public class ServletPathMatches {

    private final Deployment deployment;

    private volatile ServletPathMatchesData data;

    public ServletPathMatches(final Deployment deployment) {
        this.deployment = deployment;
    }


    public ServletChain getServletHandlerByName(final String name) {
       return getData().getServletHandlerByName(name);
    }

    public ServletPathMatch getServletHandlerByExactPath(final String path) {
        return getData().getServletHandlerByExactPath(path);
    }

    public ServletPathMatch getServletHandlerByPath(final String path) {
        return getData().getServletHandlerByPath(path);
    }

    public void invalidate() {
        this.data = null;
    }

    private ServletPathMatchesData getData() {
        ServletPathMatchesData data = this.data;
        if(data != null) {
            return data;
        }
        synchronized (this) {
            if(this.data != null) {
                return this.data;
            }
            return this.data = setupServletChains();
        }
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
     */
    private ServletPathMatchesData setupServletChains() {
        //create the default servlet
        ServletChain defaultHandler = null;
        ServletHandler defaultServlet = null;
        final Servlets servlets = deployment.getServlets();
        final Filters filters = deployment.getFilters();

        final Map<String, ServletHandler> extensionServlets = new HashMap<>();
        final Map<String, ServletHandler> pathServlets = new HashMap<>();

        final Set<String> pathMatches = new HashSet<String>();
        final Set<String> extensionMatches = new HashSet<String>();

        DeploymentInfo deploymentInfo = deployment.getDeploymentInfo();

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
            final ServletHandler handler = servlets.addServlet(servlet);
            for (String path : entry.getValue().getMappings()) {
                if (path.equals("/")) {
                    //the default servlet
                    pathMatches.add("/*");
                    if (pathServlets.containsKey("/*")) {
                        throw UndertowServletMessages.MESSAGES.twoServletsWithSameMapping(path);
                    }
                    defaultServlet = handler;
                    defaultHandler = servletChain(handler, handler.getManagedServlet());
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
            final ServletHandler managedDefaultServlet = servlets.addServlet(new ServletInfo("io.undertow.DefaultServlet", DefaultServlet.class, new ImmediateInstanceFactory<Servlet>(defaultInstance)));
            pathMatches.add("/*");
            defaultServlet = managedDefaultServlet;
            defaultHandler = new ServletChain(defaultServlet, managedDefaultServlet.getManagedServlet());
        }

        final ServletPathMatchesData.Builder builder = ServletPathMatchesData.builder();

        for (final String path : pathMatches) {
            ServletHandler targetServlet = resolveServletForPath(path, pathServlets);

            final Map<DispatcherType, List<ManagedFilter>> noExtension = new HashMap<DispatcherType, List<ManagedFilter>>();
            final Map<String, Map<DispatcherType, List<ManagedFilter>>> extension = new HashMap<String, Map<DispatcherType, List<ManagedFilter>>>();
            for (String ext : extensionMatches) {
                extension.put(ext, new HashMap<DispatcherType, List<ManagedFilter>>());
            }

            for (final FilterMappingInfo filterMapping : deploymentInfo.getFilterMappings()) {
                ManagedFilter filter = filters.getManagedFilter(filterMapping.getFilterName());
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
                    if (filterMapping.getMapping().isEmpty() || !filterMapping.getMapping().startsWith("*.")) {
                        if (isFilterApplicable(path, filterMapping.getMapping())) {
                            addToListMap(noExtension, filterMapping.getDispatcher(), filter);
                            for (Map<DispatcherType, List<ManagedFilter>> l : extension.values()) {
                                addToListMap(l, filterMapping.getDispatcher(), filter);
                            }
                        }
                    } else {
                        addToListMap(extension.get(filterMapping.getMapping().substring(2)), filterMapping.getDispatcher(), filter);
                    }
                }
            }

            final ServletChain initialHandler;
            if (noExtension.isEmpty()) {
                if (targetServlet != null) {
                    initialHandler = servletChain(targetServlet, targetServlet.getManagedServlet());
                } else {
                    initialHandler = defaultHandler;
                }
            } else {
                FilterHandler handler;
                if (targetServlet != null) {
                    handler = new FilterHandler(noExtension, deploymentInfo.isAllowNonStandardWrappers(), targetServlet);
                } else {
                    handler = new FilterHandler(noExtension, deploymentInfo.isAllowNonStandardWrappers(), defaultServlet);
                }
                initialHandler = servletChain(handler, targetServlet == null ? defaultServlet.getManagedServlet() : targetServlet.getManagedServlet());
            }

            if (path.endsWith("/*")) {
                String prefix = path.substring(0, path.length() - 2);
                builder.addPrefixMatch(prefix, initialHandler);

                for (Map.Entry<String, Map<DispatcherType, List<ManagedFilter>>> entry : extension.entrySet()) {
                    ServletHandler pathServlet = targetServlet;
                    if (pathServlet == null) {
                        pathServlet = extensionServlets.get(entry.getKey());
                    }
                    if (pathServlet == null) {
                        pathServlet = defaultServlet;
                    }
                    HttpHandler handler = pathServlet;
                    if (!entry.getValue().isEmpty()) {
                        handler = new FilterHandler(entry.getValue(), deploymentInfo.isAllowNonStandardWrappers(), handler);
                    }
                    builder.addExtensionMatch(prefix, entry.getKey(), servletChain(handler, pathServlet.getManagedServlet()));
                }
            } else if (path.isEmpty()) {
                builder.addExactMatch("/", initialHandler);
            } else {
                builder.addExactMatch(path, initialHandler);
            }
        }

        //now setup name based mappings
        //these are used for name based dispatch
        for (Map.Entry<String, ServletHandler> entry : servlets.getServletHandlers().entrySet()) {
            final Map<DispatcherType, List<ManagedFilter>> filtersByDispatcher = new HashMap<DispatcherType, List<ManagedFilter>>();
            for (final FilterMappingInfo filterMapping : deploymentInfo.getFilterMappings()) {
                ManagedFilter filter = filters.getManagedFilter(filterMapping.getFilterName());
                if (filterMapping.getMappingType() == FilterMappingInfo.MappingType.SERVLET) {
                    if (filterMapping.getMapping().equals(entry.getKey())) {
                        addToListMap(filtersByDispatcher, filterMapping.getDispatcher(), filter);
                    }
                }
            }
            if (filtersByDispatcher.isEmpty()) {
                builder.addNameMatch(entry.getKey(), servletChain(entry.getValue(), entry.getValue().getManagedServlet()));
            } else {
                builder.addNameMatch(entry.getKey(), servletChain(new FilterHandler(filtersByDispatcher, deploymentInfo.isAllowNonStandardWrappers(), entry.getValue()), entry.getValue().getManagedServlet()));
            }
        }


        builder.setDefaultServlet(defaultHandler);

        return builder.build();
    }

    private static ServletHandler resolveServletForPath(final String path, final Map<String, ServletHandler> pathServlets) {
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

    private static boolean isFilterApplicable(final String path, final String filterPath) {
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

    private static <K, V> void addToListMap(final Map<K, List<V>> map, final K key, final V value) {
        List<V> list = map.get(key);
        if (list == null) {
            map.put(key, list = new ArrayList<V>());
        }
        list.add(value);
    }

    private static ServletChain servletChain(HttpHandler next, final ManagedServlet managedServlet) {
        HttpHandler servletHandler = new ServletSecurityRoleHandler(next);
        servletHandler = wrapHandlers(servletHandler, managedServlet.getServletInfo().getHandlerChainWrappers());
        return new ServletChain(servletHandler, managedServlet);
    }

    private static HttpHandler wrapHandlers(final HttpHandler wrapee, final List<HandlerWrapper> wrappers) {
        HttpHandler current = wrapee;
        for (HandlerWrapper wrapper : wrappers) {
            current = wrapper.wrap(current);
        }
        return current;
    }
}
