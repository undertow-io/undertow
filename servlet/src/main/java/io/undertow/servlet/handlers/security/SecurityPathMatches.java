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

package io.undertow.servlet.handlers.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.SingleConstraintMatch;
import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.util.Methods;

/**
 * @author Stuart Douglas
 */
public class SecurityPathMatches {

    private static Set<String> KNOWN_METHODS;

    static {
        Set<String> methods = new HashSet<>();
        methods.add(Methods.GET_STRING);
        methods.add(Methods.POST_STRING);
        methods.add(Methods.PUT_STRING);
        methods.add(Methods.DELETE_STRING);
        methods.add(Methods.OPTIONS_STRING);
        methods.add(Methods.HEAD_STRING);
        methods.add(Methods.TRACE_STRING);
        methods.add(Methods.CONNECT_STRING);
        KNOWN_METHODS = Collections.unmodifiableSet(methods);
    }


    private final boolean denyUncoveredHttpMethods;
    private final PathSecurityInformation defaultPathSecurityInformation;
    private final Map<String, PathSecurityInformation> exactPathRoleInformation;
    private final Map<String, PathSecurityInformation> prefixPathRoleInformation;
    private final Map<String, PathSecurityInformation> extensionRoleInformation;

    private SecurityPathMatches(final boolean denyUncoveredHttpMethods, final PathSecurityInformation defaultPathSecurityInformation, final Map<String, PathSecurityInformation> exactPathRoleInformation, final Map<String, PathSecurityInformation> prefixPathRoleInformation, final Map<String, PathSecurityInformation> extensionRoleInformation) {
        this.denyUncoveredHttpMethods = denyUncoveredHttpMethods;
        this.defaultPathSecurityInformation = defaultPathSecurityInformation;
        this.exactPathRoleInformation = exactPathRoleInformation;
        this.prefixPathRoleInformation = prefixPathRoleInformation;
        this.extensionRoleInformation = extensionRoleInformation;
    }

    /**
     * @return <code>true</code> If no security path information has been defined
     */
    public boolean isEmpty() {
        return defaultPathSecurityInformation.excludedMethodRoles.isEmpty() &&
                defaultPathSecurityInformation.perMethodRequiredRoles.isEmpty() &&
                defaultPathSecurityInformation.defaultRequiredRoles.isEmpty() &&
                exactPathRoleInformation.isEmpty() &&
                prefixPathRoleInformation.isEmpty() &&
                extensionRoleInformation.isEmpty();
    }

    public SecurityPathMatch getSecurityInfo(final String path, final String method) {
        RuntimeMatch currentMatch = new RuntimeMatch();
        handleMatch(method, defaultPathSecurityInformation, currentMatch);
        PathSecurityInformation match = exactPathRoleInformation.get(path);
        PathSecurityInformation extensionMatch = null;
        if (match != null) {
            handleMatch(method, match, currentMatch);
            return new SecurityPathMatch(currentMatch.type, mergeConstraints(currentMatch));
        }

        match = prefixPathRoleInformation.get(path);
        if (match != null) {
            handleMatch(method, match, currentMatch);
            return new SecurityPathMatch(currentMatch.type, mergeConstraints(currentMatch));
        }

        int qsPos = -1;
        boolean extension = false;
        for (int i = path.length() - 1; i >= 0; --i) {
            final char c = path.charAt(i);
            if (c == '?') {
                //there was a query string, check the exact matches again
                final String part = path.substring(0, i);
                match = exactPathRoleInformation.get(part);
                if (match != null) {
                    handleMatch(method, match, currentMatch);
                    return new SecurityPathMatch(currentMatch.type, mergeConstraints(currentMatch));
                }
                qsPos = i;
                extension = false;
            } else if (c == '/') {
                extension = true;
                final String part = path.substring(0, i);
                match = prefixPathRoleInformation.get(part);
                if (match != null) {
                    handleMatch(method, match, currentMatch);
                    return new SecurityPathMatch(currentMatch.type, mergeConstraints(currentMatch));
                }
            } else if (c == '.') {
                if (!extension) {
                    extension = true;
                    final String ext;
                    if (qsPos == -1) {
                        ext = path.substring(i + 1, path.length());
                    } else {
                        ext = path.substring(i + 1, qsPos);
                    }
                    extensionMatch = extensionRoleInformation.get(ext);
                }
            }
        }

        if (extensionMatch != null) {
            handleMatch(method, extensionMatch, currentMatch);
            return new SecurityPathMatch(currentMatch.type, mergeConstraints(currentMatch));
        }

        // if nothing else, check for security info defined for URL pattern '/'
        match = exactPathRoleInformation.get("/");
        if (match != null) {
            handleMatch(method, match, currentMatch);
            return new SecurityPathMatch(currentMatch.type, mergeConstraints(currentMatch));
        }

        return new SecurityPathMatch(currentMatch.type, mergeConstraints(currentMatch));
    }

    /**
     * merge all constraints, as per 13.8.1 Combining Constraints
     */
    private SingleConstraintMatch mergeConstraints(final RuntimeMatch currentMatch) {
        if (currentMatch.uncovered && denyUncoveredHttpMethods) {
            return new SingleConstraintMatch(SecurityInfo.EmptyRoleSemantic.DENY, Collections.<String>emptySet());
        }
        final Set<String> allowedRoles = new HashSet<>();
        for (SingleConstraintMatch match : currentMatch.constraints) {
            if (match.getRequiredRoles().isEmpty()) {
                return new SingleConstraintMatch(match.getEmptyRoleSemantic(), Collections.<String>emptySet());
            } else {
                allowedRoles.addAll(match.getRequiredRoles());
            }
        }
        return new SingleConstraintMatch(SecurityInfo.EmptyRoleSemantic.PERMIT, allowedRoles);
    }

    private void handleMatch(final String method, final PathSecurityInformation exact, RuntimeMatch currentMatch) {
        List<SecurityInformation> roles = exact.defaultRequiredRoles;
        for (SecurityInformation role : roles) {
            transport(currentMatch, role.transportGuaranteeType);
            currentMatch.constraints.add(new SingleConstraintMatch(role.emptyRoleSemantic, role.roles));
            if (role.emptyRoleSemantic == SecurityInfo.EmptyRoleSemantic.DENY || !role.roles.isEmpty()) {
                currentMatch.uncovered = false;
            }
        }
        List<SecurityInformation> methodInfo = exact.perMethodRequiredRoles.get(method);
        if (methodInfo != null) {
            currentMatch.uncovered = false;
            for (SecurityInformation role : methodInfo) {
                transport(currentMatch, role.transportGuaranteeType);
                currentMatch.constraints.add(new SingleConstraintMatch(role.emptyRoleSemantic, role.roles));
            }
        } else if(denyUncoveredHttpMethods) {
            if(exact.perMethodRequiredRoles.size() == 0) {
                // 13.8.4. When HTTP methods are not enumerated within a security-constraint, the protections defined by the
                // constraint apply to the complete set of HTTP (extension) methods.
                currentMatch.uncovered = false;
                currentMatch.constraints.add(new SingleConstraintMatch(SecurityInfo.EmptyRoleSemantic.PERMIT, new HashSet<>()));
            } else if(exact.perMethodRequiredRoles.size() > 0) {
                //at this point method is null, but there is match, above if will be triggered for default path, we need to flip it?
                currentMatch.uncovered = true;
                //NOTE: ?
                currentMatch.constraints.clear();
                currentMatch.constraints.add(new SingleConstraintMatch(SecurityInfo.EmptyRoleSemantic.DENY, new HashSet<>()));
            }
        }
        for (ExcludedMethodRoles excluded : exact.excludedMethodRoles) {
            if (!excluded.methods.contains(method)) {
                currentMatch.uncovered = false;
                transport(currentMatch, excluded.securityInformation.transportGuaranteeType);
                currentMatch.constraints.add(new SingleConstraintMatch(excluded.securityInformation.emptyRoleSemantic, excluded.securityInformation.roles));
            }
        }
    }

    private void transport(RuntimeMatch match, TransportGuaranteeType other) {
        if (other.ordinal() > match.type.ordinal()) {
            match.type = other;
        }
    }

    public void logWarningsAboutUncoveredMethods() {
        if(!denyUncoveredHttpMethods) {
            logWarningsAboutUncoveredMethods(exactPathRoleInformation, "", "");
            logWarningsAboutUncoveredMethods(prefixPathRoleInformation, "", "/*");
            logWarningsAboutUncoveredMethods(extensionRoleInformation, "*.", "");
        }
    }

    private void logWarningsAboutUncoveredMethods(Map<String, PathSecurityInformation> matches, String prefix, String suffix) {
        //according to the spec we should be logging warnings about paths with uncovered HTTP methods
        for (Map.Entry<String, PathSecurityInformation> entry : matches.entrySet()) {
            if (entry.getValue().perMethodRequiredRoles.isEmpty() && entry.getValue().excludedMethodRoles.isEmpty()) {
                continue;
            }
            Set<String> missing = new HashSet<>(KNOWN_METHODS);
            for (String m : entry.getValue().perMethodRequiredRoles.keySet()) {
                missing.remove(m);
            }
            Iterator<String> it = missing.iterator();
            while (it.hasNext()) {
                String val = it.next();
                for (ExcludedMethodRoles excluded : entry.getValue().excludedMethodRoles) {
                    if (!excluded.methods.contains(val)) {
                        it.remove();
                        break;
                    }
                }
            }
            if (!missing.isEmpty()) {
                UndertowServletLogger.ROOT_LOGGER.unsecuredMethodsOnPath(prefix + entry.getKey() + suffix, missing);
            }
        }
    }


    public static Builder builder(final DeploymentInfo deploymentInfo) {
        return new Builder(deploymentInfo);
    }

    public static class Builder {
        private final DeploymentInfo deploymentInfo;
        private final PathSecurityInformation defaultPathSecurityInformation = new PathSecurityInformation();
        private final Map<String, PathSecurityInformation> exactPathRoleInformation = new HashMap<>();
        private final Map<String, PathSecurityInformation> prefixPathRoleInformation = new HashMap<>();
        private final Map<String, PathSecurityInformation> extensionRoleInformation = new HashMap<>();

        private Builder(final DeploymentInfo deploymentInfo) {
            this.deploymentInfo = deploymentInfo;
        }

        public void addSecurityConstraint(final SecurityConstraint securityConstraint) {
            final Set<String> roles = expandRolesAllowed(securityConstraint.getRolesAllowed());
            final SecurityInformation securityInformation = new SecurityInformation(roles, securityConstraint.getTransportGuaranteeType(), securityConstraint.getEmptyRoleSemantic());
            for (final WebResourceCollection webResources : securityConstraint.getWebResourceCollections()) {
                if (webResources.getUrlPatterns().isEmpty()) {
                    //default that is applied to everything
                    setupPathSecurityInformation(defaultPathSecurityInformation, securityInformation, webResources);
                }
                for (String pattern : webResources.getUrlPatterns()) {
                    if (pattern.endsWith("/*")) {
                        String part = pattern.substring(0, pattern.length() - 2);
                        PathSecurityInformation info = prefixPathRoleInformation.get(part);
                        if (info == null) {
                            prefixPathRoleInformation.put(part, info = new PathSecurityInformation());
                        }
                        setupPathSecurityInformation(info, securityInformation, webResources);
                    } else if (pattern.startsWith("*.")) {
                        String part = pattern.substring(2, pattern.length());
                        PathSecurityInformation info = extensionRoleInformation.get(part);
                        if (info == null) {
                            extensionRoleInformation.put(part, info = new PathSecurityInformation());
                        }
                        setupPathSecurityInformation(info, securityInformation, webResources);
                    } else {
                        PathSecurityInformation info = exactPathRoleInformation.get(pattern);
                        if (info == null) {
                            exactPathRoleInformation.put(pattern, info = new PathSecurityInformation());
                        }
                        setupPathSecurityInformation(info, securityInformation, webResources);
                    }
                }
            }

        }

        private Set<String> expandRolesAllowed(final Set<String> rolesAllowed) {
            final Set<String> roles = new HashSet<>(rolesAllowed);
            if (roles.contains("*")) {
                roles.remove("*");
                roles.addAll(deploymentInfo.getSecurityRoles());
            }

            return roles;
        }

        private void setupPathSecurityInformation(final PathSecurityInformation info, final SecurityInformation securityConstraint, final WebResourceCollection webResources) {
            if (webResources.getHttpMethods().isEmpty() &&
                    webResources.getHttpMethodOmissions().isEmpty()) {
                info.defaultRequiredRoles.add(securityConstraint);
            } else if (!webResources.getHttpMethods().isEmpty()) {
                for (String method : webResources.getHttpMethods()) {
                    List<SecurityInformation> securityInformations = info.perMethodRequiredRoles.get(method);
                    if (securityInformations == null) {
                        info.perMethodRequiredRoles.put(method, securityInformations = new ArrayList<>());
                    }
                    securityInformations.add(securityConstraint);
                }
            } else if (!webResources.getHttpMethodOmissions().isEmpty()) {
                info.excludedMethodRoles.add(new ExcludedMethodRoles(webResources.getHttpMethodOmissions(), securityConstraint));
            }
        }

        public SecurityPathMatches build() {
            return new SecurityPathMatches(deploymentInfo.isDenyUncoveredHttpMethods(), defaultPathSecurityInformation, exactPathRoleInformation, prefixPathRoleInformation, extensionRoleInformation);
        }
    }


    private static class PathSecurityInformation {
        final List<SecurityInformation> defaultRequiredRoles = new ArrayList<>();
        final Map<String, List<SecurityInformation>> perMethodRequiredRoles = new HashMap<>();
        final List<ExcludedMethodRoles> excludedMethodRoles = new ArrayList<>();
    }

    private static final class ExcludedMethodRoles {
        final Set<String> methods;
        final SecurityInformation securityInformation;

        ExcludedMethodRoles(final Set<String> methods, final SecurityInformation securityInformation) {
            this.methods = methods;
            this.securityInformation = securityInformation;
        }
    }

    private static final class SecurityInformation {
        final Set<String> roles;
        final TransportGuaranteeType transportGuaranteeType;
        final SecurityInfo.EmptyRoleSemantic emptyRoleSemantic;

        private SecurityInformation(final Set<String> roles, final TransportGuaranteeType transportGuaranteeType, final SecurityInfo.EmptyRoleSemantic emptyRoleSemantic) {
            this.emptyRoleSemantic = emptyRoleSemantic;
            this.roles = new HashSet<>(roles);
            this.transportGuaranteeType = transportGuaranteeType;
        }
    }

    private static final class RuntimeMatch {
        TransportGuaranteeType type = TransportGuaranteeType.NONE;
        final List<SingleConstraintMatch> constraints = new ArrayList<>();
        boolean uncovered = true;
    }
}
