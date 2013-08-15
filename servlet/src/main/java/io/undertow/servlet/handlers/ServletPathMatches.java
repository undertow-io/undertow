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
import io.undertow.servlet.core.ManagedFilters;
import io.undertow.servlet.core.ManagedFilter;
import io.undertow.servlet.core.ManagedServlet;
import io.undertow.servlet.core.ManagedServlets;
import io.undertow.servlet.handlers.security.ServletSecurityRoleHandler;
import io.undertow.servlet.util.ImmediateInstanceFactory;

/**
 * Facade around {@link ServletPathMatchesData}. This facade is responsible for re-generating the matches if anything changes.
 *
 * @author Stuart Douglas
 */
public class ServletPathMatches {

    public static final String DEFAULT_SERVLET_NAME = "default";
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
        if (data != null) {
            return data;
        }
        synchronized (this) {
            if (this.data != null) {
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
     */
    private ServletPathMatchesData setupServletChains() {
        //create the default servlet
        ServletChain defaultHandler = null;
        ServletHandler defaultServlet = null;
        final ManagedServlets servlets = deployment.getServlets();
        final ManagedFilters filters = deployment.getFilters();

        final Map<String, ServletHandler> extensionServlets = new HashMap<String, ServletHandler>();
        final Map<String, ServletHandler> pathServlets = new HashMap<String, ServletHandler>();

        final Set<String> pathMatches = new HashSet<String>();
        final Set<String> extensionMatches = new HashSet<String>();

        DeploymentInfo deploymentInfo = deployment.getDeploymentInfo();

        //loop through all filter mappings, and add them to the set of known paths
        for (FilterMappingInfo mapping : deploymentInfo.getFilterMappings()) {
            if (mapping.getMappingType() == FilterMappingInfo.MappingType.URL) {
                String path = mapping.getMapping();
                if(path.equals("*")) {
                    //UNDERTOW-95, support this non-standard filter mapping
                    path = "/*";
                }
                if (!path.startsWith("*.")) {
                    pathMatches.add(path);
                } else {
                    extensionMatches.add(path.substring(2));
                }
            }
        }

        //now loop through all servlets.
        for (Map.Entry<String, ServletHandler> entry : servlets.getServletHandlers().entrySet()) {
            //add the servlet to the deployment
            final ServletHandler handler = entry.getValue();
            //add the servlet to the approprite path maps
            for (String path : handler.getManagedServlet().getServletInfo().getMappings()) {
                if (path.equals("/")) {
                    //the default servlet
                    pathMatches.add("/*");
                    if (pathServlets.containsKey("/*") || defaultServlet != null) {
                        throw UndertowServletMessages.MESSAGES.twoServletsWithSameMapping(path);
                    }
                    defaultServlet = handler;
                    defaultHandler = servletChain(handler, handler.getManagedServlet(), null, deploymentInfo);
                } else if (!path.startsWith("*.")) {
                    //either an exact or a /* based path match
                    if(path.isEmpty()) {
                        path = "/";
                    }
                    pathMatches.add(path);
                    if (pathServlets.containsKey(path)) {
                        throw UndertowServletMessages.MESSAGES.twoServletsWithSameMapping(path);
                    }
                    pathServlets.put(path, handler);
                } else {
                    //an extension match based servlet
                    String ext = path.substring(2);
                    extensionMatches.add(ext);
                    extensionServlets.put(ext, handler);
                }
            }
        }
        //we always create a default servlet, even if it is not going to have any path mappings registered
        final DefaultServletConfig config = deploymentInfo.getDefaultServletConfig() == null ? new DefaultServletConfig() : deploymentInfo.getDefaultServletConfig();
        DefaultServlet defaultInstance = new DefaultServlet(deployment, config, deploymentInfo.getWelcomePages());
        final ServletHandler managedDefaultServlet = servlets.addServlet(new ServletInfo(DEFAULT_SERVLET_NAME, DefaultServlet.class, new ImmediateInstanceFactory<Servlet>(defaultInstance)));

        if (defaultServlet == null) {
            //no explicit default servlet was specified, so we register our mapping
            pathMatches.add("/*");
            defaultServlet = managedDefaultServlet;
            defaultHandler = new ServletChain(defaultServlet, managedDefaultServlet.getManagedServlet(), null);
        }

        final ServletPathMatchesData.Builder builder = ServletPathMatchesData.builder();

        //we now loop over every path in the application, and build up the patches based on this path
        //these paths contain both /* and exact matches.
        for (final String path : pathMatches) {
            //resolve the target servlet, will return null if this is the default servlet
            MatchData targetServletMatch = resolveServletForPath(path, pathServlets);

            final Map<DispatcherType, List<ManagedFilter>> noExtension = new HashMap<DispatcherType, List<ManagedFilter>>();
            final Map<String, Map<DispatcherType, List<ManagedFilter>>> extension = new HashMap<String, Map<DispatcherType, List<ManagedFilter>>>();
            //initalize the extension map. This contains all the filers in the noExtension map, plus
            //any filters that match the extension key
            for (String ext : extensionMatches) {
                extension.put(ext, new HashMap<DispatcherType, List<ManagedFilter>>());
            }

            //loop over all the filters, and add them to the appropriate map in the correct order
            for (final FilterMappingInfo filterMapping : deploymentInfo.getFilterMappings()) {
                ManagedFilter filter = filters.getManagedFilter(filterMapping.getFilterName());
                if (filterMapping.getMappingType() == FilterMappingInfo.MappingType.SERVLET) {
                    if (targetServletMatch.handler != null) {
                        if (filterMapping.getMapping().equals(targetServletMatch.handler.getManagedServlet().getServletInfo().getName())) {
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
            //resolve any matches and add them to the builder
            if (path.endsWith("/*")) {
                String prefix = path.substring(0, path.length() - 2);
                //add the default non-extension match
                builder.addPrefixMatch(prefix, createHandler(defaultHandler, defaultServlet, deploymentInfo, targetServletMatch.handler, noExtension, targetServletMatch.matchedPath));

                //build up the chain for each non-extension match
                for (Map.Entry<String, Map<DispatcherType, List<ManagedFilter>>> entry : extension.entrySet()) {
                    ServletHandler pathServlet = targetServletMatch.handler;
                    String pathMatch = targetServletMatch.matchedPath;

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
                    builder.addExtensionMatch(prefix, entry.getKey(), servletChain(handler, pathServlet.getManagedServlet(), pathMatch, deploymentInfo));
                }
            } else if (path.isEmpty()) {
                //the context root match
                builder.addExactMatch("/", createHandler(defaultHandler, defaultServlet, deploymentInfo, targetServletMatch.handler, noExtension, targetServletMatch.matchedPath));
            } else {
                //we need to check for an extension match, so paths like /exact.txt will have the correct filter applied
                String lastSegment = path.substring(path.lastIndexOf('/'));
                if (lastSegment.contains(".")) {
                    String ext = lastSegment.substring(lastSegment.lastIndexOf('.') + 1);
                    if (extension.containsKey(ext)) {
                        Map<DispatcherType, List<ManagedFilter>> extMap = extension.get(ext);
                        builder.addExactMatch(path, createHandler(defaultHandler, defaultServlet, deploymentInfo, targetServletMatch.handler, extMap, targetServletMatch.matchedPath));
                    } else {
                        builder.addExactMatch(path, createHandler(defaultHandler, defaultServlet, deploymentInfo, targetServletMatch.handler, noExtension, targetServletMatch.matchedPath));
                    }
                } else {
                    builder.addExactMatch(path, createHandler(defaultHandler, defaultServlet, deploymentInfo, targetServletMatch.handler, noExtension, targetServletMatch.matchedPath));
                }

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
                builder.addNameMatch(entry.getKey(), servletChain(entry.getValue(), entry.getValue().getManagedServlet(), null, deploymentInfo));
            } else {
                builder.addNameMatch(entry.getKey(), servletChain(new FilterHandler(filtersByDispatcher, deploymentInfo.isAllowNonStandardWrappers(), entry.getValue()), entry.getValue().getManagedServlet(), null, deploymentInfo));
            }
        }

        builder.setDefaultServlet(defaultHandler);

        return builder.build();
    }

    private ServletChain createHandler(final ServletChain defaultHandler, final ServletHandler defaultServlet, final DeploymentInfo deploymentInfo, final ServletHandler targetServlet, final Map<DispatcherType, List<ManagedFilter>> noExtension, final String servletPath) {
        final ServletChain initialHandler;
        if (noExtension.isEmpty()) {
            if (targetServlet != null) {
                initialHandler = servletChain(targetServlet, targetServlet.getManagedServlet(), servletPath, deploymentInfo);
            } else {
                initialHandler = defaultHandler;
            }
        } else if (targetServlet != null) {
            FilterHandler handler = new FilterHandler(noExtension, deploymentInfo.isAllowNonStandardWrappers(), targetServlet);
            initialHandler = servletChain(handler, targetServlet.getManagedServlet(), servletPath, deploymentInfo);
        } else {
            FilterHandler handler = new FilterHandler(noExtension, deploymentInfo.isAllowNonStandardWrappers(), defaultServlet);
            initialHandler = servletChain(handler, defaultServlet.getManagedServlet(), servletPath, deploymentInfo);
        }
        return initialHandler;
    }

    private static MatchData resolveServletForPath(final String path, final Map<String, ServletHandler> pathServlets) {
        if (pathServlets.containsKey(path)) {
            if (path.endsWith("/*")) {
                final String base = path.substring(0, path.length() - 2);
                return new MatchData(pathServlets.get(path), base);
            } else {
                return new MatchData(pathServlets.get(path), path);
            }
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
        if(servlet != null) {
            return new MatchData(servlet, match);
        }
        return new MatchData(null, null);
    }

    private static boolean isFilterApplicable(final String path, final String filterPath) {
        String modifiedPath;
        if(filterPath.equals("*")) {
            modifiedPath = "/*";
        } else {
            modifiedPath = filterPath;
        }
        if (path.isEmpty()) {
            return modifiedPath.equals("/*") || modifiedPath.equals("/");
        }
        if (modifiedPath.endsWith("/*")) {
            String baseFilterPath = modifiedPath.substring(0, modifiedPath.length() - 1);
            return path.startsWith(baseFilterPath);
        } else {
            return modifiedPath.equals(path);
        }
    }

    private static <K, V> void addToListMap(final Map<K, List<V>> map, final K key, final V value) {
        List<V> list = map.get(key);
        if (list == null) {
            map.put(key, list = new ArrayList<V>());
        }
        list.add(value);
    }

    private static ServletChain servletChain(HttpHandler next, final ManagedServlet managedServlet, final String servletPath, final DeploymentInfo deploymentInfo) {
        HttpHandler servletHandler = new ServletSecurityRoleHandler(next, deploymentInfo.getPrincipalVersusRolesMap());
        servletHandler = wrapHandlers(servletHandler, managedServlet.getServletInfo().getHandlerChainWrappers());
        return new ServletChain(servletHandler, managedServlet , servletPath);
    }

    private static HttpHandler wrapHandlers(final HttpHandler wrapee, final List<HandlerWrapper> wrappers) {
        HttpHandler current = wrapee;
        for (HandlerWrapper wrapper : wrappers) {
            current = wrapper.wrap(current);
        }
        return current;
    }

    private static class MatchData {
        final ServletHandler handler;
        final String matchedPath;

        private MatchData(final ServletHandler handler, final String matchedPath) {
            this.handler = handler;
            this.matchedPath = matchedPath;
        }
    }
}
