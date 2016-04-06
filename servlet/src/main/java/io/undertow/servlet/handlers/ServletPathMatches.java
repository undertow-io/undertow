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

package io.undertow.servlet.handlers;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.cache.LRUCache;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.FilterMappingInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.core.ManagedFilter;
import io.undertow.servlet.core.ManagedFilters;
import io.undertow.servlet.core.ManagedServlet;
import io.undertow.servlet.core.ManagedServlets;
import io.undertow.servlet.handlers.security.ServletSecurityRoleHandler;

import javax.servlet.DispatcherType;
import javax.servlet.http.MappingMatch;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.undertow.servlet.handlers.ServletPathMatch.Type.REDIRECT;
import static io.undertow.servlet.handlers.ServletPathMatch.Type.REWRITE;

/**
 * Facade around {@link ServletPathMatchesData}. This facade is responsible for re-generating the matches if anything changes.
 *
 * @author Stuart Douglas
 */
public class ServletPathMatches {

    public static final String DEFAULT_SERVLET_NAME = "default";
    private final Deployment deployment;

    private volatile String[] welcomePages;
    private final ResourceManager resourceManager;

    private volatile ServletPathMatchesData data;

    private final LRUCache<String, ServletPathMatch> pathMatchCache = new LRUCache<>(1000, -1, true); //TODO: configurable

    public ServletPathMatches(final Deployment deployment) {
        this.deployment = deployment;
        this.welcomePages = deployment.getDeploymentInfo().getWelcomePages().toArray(new String[deployment.getDeploymentInfo().getWelcomePages().size()]);
        this.resourceManager = deployment.getDeploymentInfo().getResourceManager();
    }

    public ServletChain getServletHandlerByName(final String name) {
        return getData().getServletHandlerByName(name);
    }

    public ServletPathMatch getServletHandlerByPath(final String path) {
        ServletPathMatch existing = pathMatchCache.get(path);
        if(existing != null) {
            return existing;
        }
        ServletPathMatch match = getData().getServletHandlerByPath(path);
        if (!match.isRequiredWelcomeFileMatch()) {
            pathMatchCache.add(path, match);
            return match;
        }
        try {

            String remaining = match.getRemaining() == null ? match.getMatched() : match.getRemaining();
            Resource resource = resourceManager.getResource(remaining);
            if (resource == null || !resource.isDirectory()) {
                pathMatchCache.add(path, match);
                return match;
            }

            boolean pathEndsWithSlash = remaining.endsWith("/");
            final String pathWithTrailingSlash = pathEndsWithSlash ? remaining : remaining + "/";

            ServletPathMatch welcomePage = findWelcomeFile(pathWithTrailingSlash, !pathEndsWithSlash);

            if (welcomePage != null) {
                pathMatchCache.add(path, welcomePage);
                return welcomePage;
            } else {
                welcomePage = findWelcomeServlet(pathWithTrailingSlash, !pathEndsWithSlash);
                if (welcomePage != null) {
                    pathMatchCache.add(path, welcomePage);
                    return welcomePage;
                } else if(pathEndsWithSlash) {
                    pathMatchCache.add(path, match);
                    return match;
                } else {
                    ServletPathMatch redirect = new ServletPathMatch(match.getServletChain(), match.getMatched(), match.getRemaining(), REDIRECT, "/");
                    pathMatchCache.add(path, redirect);
                    return redirect;
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void invalidate() {
        this.data = null;
        this.pathMatchCache.clear();
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

    private ServletPathMatch findWelcomeFile(final String path, boolean requiresRedirect) {
        if(File.separatorChar != '/' && path.contains(File.separator)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String i : welcomePages) {
            try {
                sb.append(path);
                sb.append(i);
                final String mergedPath = sb.toString();
                sb.setLength(0);
                Resource resource = resourceManager.getResource(mergedPath);
                if (resource != null) {
                    final ServletPathMatch handler = data.getServletHandlerByPath(mergedPath);
                    return new ServletPathMatch(handler.getServletChain(), mergedPath, null, requiresRedirect ? REDIRECT : REWRITE, mergedPath);
                }
            } catch (IOException e) {
            }
        }
        return null;
    }

    private ServletPathMatch findWelcomeServlet(final String path, boolean requiresRedirect) {
        StringBuilder sb = new StringBuilder();
        for (String i : welcomePages) {
            sb.append(path);
            sb.append(i);
            final String mergedPath = sb.toString();
            sb.setLength(0);
            final ServletPathMatch handler = data.getServletHandlerByPath(mergedPath);
            if (handler != null && !handler.isRequiredWelcomeFileMatch()) {
                return new ServletPathMatch(handler.getServletChain(), handler.getMatched(), handler.getRemaining(), requiresRedirect ? REDIRECT : REWRITE, mergedPath);
            }
        }
        return null;
    }

    public void setWelcomePages(List<String> welcomePages) {
        this.welcomePages = welcomePages.toArray(new String[welcomePages.size()]);
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
        ServletHandler defaultServlet = null;
        final ManagedServlets servlets = deployment.getServlets();
        final ManagedFilters filters = deployment.getFilters();

        final Map<String, ServletHandler> extensionServlets = new HashMap<>();
        final Map<String, ServletHandler> pathServlets = new HashMap<>();

        final Set<String> pathMatches = new HashSet<>();
        final Set<String> extensionMatches = new HashSet<>();

        DeploymentInfo deploymentInfo = deployment.getDeploymentInfo();

        //loop through all filter mappings, and add them to the set of known paths
        for (FilterMappingInfo mapping : deploymentInfo.getFilterMappings()) {
            if (mapping.getMappingType() == FilterMappingInfo.MappingType.URL) {
                String path = mapping.getMapping();
                if (path.equals("*")) {
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
            final ServletHandler handler = entry.getValue();
            //add the servlet to the approprite path maps
            for (String path : handler.getManagedServlet().getServletInfo().getMappings()) {
                if (path.equals("/")) {
                    //the default servlet
                    pathMatches.add("/*");
                    if (defaultServlet != null) {
                        throw UndertowServletMessages.MESSAGES.twoServletsWithSameMapping(path);
                    }
                    defaultServlet = handler;
                } else if (!path.startsWith("*.")) {
                    //either an exact or a /* based path match
                    if (path.isEmpty()) {
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
        ServletHandler managedDefaultServlet = servlets.getServletHandler(DEFAULT_SERVLET_NAME);
        if(managedDefaultServlet == null) {
            //we always create a default servlet, even if it is not going to have any path mappings registered
            managedDefaultServlet = servlets.addServlet(new ServletInfo(DEFAULT_SERVLET_NAME, DefaultServlet.class));
        }

        if (defaultServlet == null) {
            //no explicit default servlet was specified, so we register our mapping
            pathMatches.add("/*");
            defaultServlet = managedDefaultServlet;
        }

        final ServletPathMatchesData.Builder builder = ServletPathMatchesData.builder();

        //we now loop over every path in the application, and build up the patches based on this path
        //these paths contain both /* and exact matches.
        for (final String path : pathMatches) {
            //resolve the target servlet, will return null if this is the default servlet
            MatchData targetServletMatch = resolveServletForPath(path, pathServlets, extensionServlets, defaultServlet);

            final Map<DispatcherType, List<ManagedFilter>> noExtension = new EnumMap<>(DispatcherType.class);
            final Map<String, Map<DispatcherType, List<ManagedFilter>>> extension = new HashMap<>();
            //initalize the extension map. This contains all the filers in the noExtension map, plus
            //any filters that match the extension key
            for (String ext : extensionMatches) {
                extension.put(ext, new EnumMap<DispatcherType, List<ManagedFilter>>(DispatcherType.class));
            }

            //loop over all the filters, and add them to the appropriate map in the correct order
            for (final FilterMappingInfo filterMapping : deploymentInfo.getFilterMappings()) {
                ManagedFilter filter = filters.getManagedFilter(filterMapping.getFilterName());
                if (filterMapping.getMappingType() == FilterMappingInfo.MappingType.SERVLET) {
                    if (targetServletMatch.handler != null) {
                        if (filterMapping.getMapping().equals(targetServletMatch.handler.getManagedServlet().getServletInfo().getName())) {
                            addToListMap(noExtension, filterMapping.getDispatcher(), filter);
                        }
                    }
                    for(Map.Entry<String, Map<DispatcherType, List<ManagedFilter>>> entry : extension.entrySet()) {
                    ServletHandler pathServlet = targetServletMatch.handler;
                    boolean defaultServletMatch = targetServletMatch.defaultServlet;
                        if (defaultServletMatch && extensionServlets.containsKey(entry.getKey())) {
                            pathServlet = extensionServlets.get(entry.getKey());
                        }

                        if (filterMapping.getMapping().equals(pathServlet.getManagedServlet().getServletInfo().getName())) {
                            addToListMap(extension.get(entry.getKey()), filterMapping.getDispatcher(), filter);
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
                builder.addPrefixMatch(prefix, createHandler(deploymentInfo, targetServletMatch.handler, noExtension, targetServletMatch.matchedPath, targetServletMatch.defaultServlet, targetServletMatch.mappingMatch, targetServletMatch.userPath), targetServletMatch.defaultServlet || targetServletMatch.handler.getManagedServlet().getServletInfo().isRequireWelcomeFileMapping());

                //build up the chain for each non-extension match
                for (Map.Entry<String, Map<DispatcherType, List<ManagedFilter>>> entry : extension.entrySet()) {
                    ServletHandler pathServlet = targetServletMatch.handler;
                    String pathMatch = targetServletMatch.matchedPath;

                    boolean defaultServletMatch = targetServletMatch.defaultServlet;
                    if (defaultServletMatch && extensionServlets.containsKey(entry.getKey())) {
                        defaultServletMatch = false;
                        pathServlet = extensionServlets.get(entry.getKey());
                    }
                    HttpHandler handler = pathServlet;
                    if (!entry.getValue().isEmpty()) {
                        handler = new FilterHandler(entry.getValue(), deploymentInfo.isAllowNonStandardWrappers(), handler);
                    }
                    builder.addExtensionMatch(prefix, entry.getKey(), servletChain(handler, pathServlet.getManagedServlet(), pathMatch, deploymentInfo, defaultServletMatch, defaultServletMatch ? MappingMatch.DEFAULT : MappingMatch.EXTENSION, defaultServletMatch ? "/" : "*." + entry.getKey()));
                }
            } else if (path.isEmpty()) {
                //the context root match
                builder.addExactMatch("/", createHandler(deploymentInfo, targetServletMatch.handler, noExtension, targetServletMatch.matchedPath, targetServletMatch.defaultServlet, targetServletMatch.mappingMatch, targetServletMatch.userPath));
            } else {
                //we need to check for an extension match, so paths like /exact.txt will have the correct filter applied
                int lastSegmentIndex = path.lastIndexOf('/');
                String lastSegment;
                if(lastSegmentIndex > 0) {
                    lastSegment = path.substring(lastSegmentIndex);
                } else {
                    lastSegment = path;
                }
                if (lastSegment.contains(".")) {
                    String ext = lastSegment.substring(lastSegment.lastIndexOf('.') + 1);
                    if (extension.containsKey(ext)) {
                        Map<DispatcherType, List<ManagedFilter>> extMap = extension.get(ext);
                        builder.addExactMatch(path, createHandler(deploymentInfo, targetServletMatch.handler, extMap, targetServletMatch.matchedPath, targetServletMatch.defaultServlet, targetServletMatch.mappingMatch, targetServletMatch.userPath));
                    } else {
                        builder.addExactMatch(path, createHandler(deploymentInfo, targetServletMatch.handler, noExtension, targetServletMatch.matchedPath, targetServletMatch.defaultServlet, targetServletMatch.mappingMatch, targetServletMatch.userPath));
                    }
                } else {
                    builder.addExactMatch(path, createHandler(deploymentInfo, targetServletMatch.handler, noExtension, targetServletMatch.matchedPath, targetServletMatch.defaultServlet, targetServletMatch.mappingMatch, targetServletMatch.userPath));
                }

            }
        }

        //now setup name based mappings
        //these are used for name based dispatch
        for (Map.Entry<String, ServletHandler> entry : servlets.getServletHandlers().entrySet()) {
            final Map<DispatcherType, List<ManagedFilter>> filtersByDispatcher = new EnumMap<>(DispatcherType.class);
            for (final FilterMappingInfo filterMapping : deploymentInfo.getFilterMappings()) {
                ManagedFilter filter = filters.getManagedFilter(filterMapping.getFilterName());
                if (filterMapping.getMappingType() == FilterMappingInfo.MappingType.SERVLET) {
                    if (filterMapping.getMapping().equals(entry.getKey())) {
                        addToListMap(filtersByDispatcher, filterMapping.getDispatcher(), filter);
                    }
                }
            }
            if (filtersByDispatcher.isEmpty()) {
                builder.addNameMatch(entry.getKey(), servletChain(entry.getValue(), entry.getValue().getManagedServlet(), null, deploymentInfo, false, MappingMatch.UNKNOWN, ""));
            } else {
                builder.addNameMatch(entry.getKey(), servletChain(new FilterHandler(filtersByDispatcher, deploymentInfo.isAllowNonStandardWrappers(), entry.getValue()), entry.getValue().getManagedServlet(), null, deploymentInfo, false, MappingMatch.UNKNOWN, ""));
            }
        }

        return builder.build();
    }

    private ServletChain createHandler(final DeploymentInfo deploymentInfo, final ServletHandler targetServlet, final Map<DispatcherType, List<ManagedFilter>> noExtension, final String servletPath, final boolean defaultServlet, MappingMatch mappingMatch, String pattern) {
        final ServletChain initialHandler;
        if (noExtension.isEmpty()) {
            initialHandler = servletChain(targetServlet, targetServlet.getManagedServlet(), servletPath, deploymentInfo, defaultServlet, mappingMatch, pattern);
        } else {
            FilterHandler handler = new FilterHandler(noExtension, deploymentInfo.isAllowNonStandardWrappers(), targetServlet);
            initialHandler = servletChain(handler, targetServlet.getManagedServlet(), servletPath, deploymentInfo, defaultServlet, mappingMatch, pattern);
        }
        return initialHandler;
    }

    private static MatchData resolveServletForPath(final String path, final Map<String, ServletHandler> pathServlets, final Map<String, ServletHandler> extensionServlets, ServletHandler defaultServlet) {
        if (pathServlets.containsKey(path)) {
            if (path.endsWith("/*")) {
                final String base = path.substring(0, path.length() - 2);
                return new MatchData(pathServlets.get(path), base, path, MappingMatch.PATH, false);
            } else {
                if(path.equals("/")) {
                    return new MatchData(pathServlets.get(path), path, "", MappingMatch.CONTEXT_ROOT, false);
                }
                return new MatchData(pathServlets.get(path), path, path, MappingMatch.EXACT, false);
            }
        }
        String match = null;
        ServletHandler servlet = null;
        String userPath = "";
        for (final Map.Entry<String, ServletHandler> entry : pathServlets.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("/*")) {
                final String base = key.substring(0, key.length() - 1);
                if (match == null || base.length() > match.length()) {
                    if (path.startsWith(base) || path.equals(base.substring(0, base.length() - 1))) {
                        match = base.substring(0, base.length() - 1);
                        servlet = entry.getValue();
                        userPath = key;
                    }
                }
            }
        }
        if (servlet != null) {
            return new MatchData(servlet, match, userPath, MappingMatch.PATH, false);
        }
        int index = path.lastIndexOf('.');
        if (index != -1) {
            String ext = path.substring(index + 1);
            servlet = extensionServlets.get(ext);
            if (servlet != null) {
                return new MatchData(servlet, null, "*." + ext, MappingMatch.EXTENSION, false);
            }
        }

        return new MatchData(defaultServlet, null, "/", MappingMatch.DEFAULT, true);
    }

    private static boolean isFilterApplicable(final String path, final String filterPath) {
        String modifiedPath;
        if (filterPath.equals("*")) {
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
            map.put(key, list = new ArrayList<>());
        }
        list.add(value);
    }

    private static ServletChain servletChain(HttpHandler next, final ManagedServlet managedServlet, final String servletPath, final DeploymentInfo deploymentInfo, boolean defaultServlet, MappingMatch mappingMatch, String pattern) {
        HttpHandler servletHandler = new ServletSecurityRoleHandler(next, deploymentInfo.getAuthorizationManager());
        servletHandler = wrapHandlers(servletHandler, managedServlet.getServletInfo().getHandlerChainWrappers());
        return new ServletChain(servletHandler, managedServlet, servletPath, defaultServlet, mappingMatch, pattern);
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
        final String userPath;
        final MappingMatch mappingMatch;
        final boolean defaultServlet;

        private MatchData(final ServletHandler handler, final String matchedPath, String userPath, MappingMatch mappingMatch, boolean defaultServlet) {
            this.handler = handler;
            this.matchedPath = matchedPath;
            this.userPath = userPath;
            this.mappingMatch = mappingMatch;
            this.defaultServlet = defaultServlet;
        }
    }
}
