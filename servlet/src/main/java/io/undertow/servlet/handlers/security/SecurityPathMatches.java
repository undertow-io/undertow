package io.undertow.servlet.handlers.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.annotation.ServletSecurity;

import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.api.WebResourceCollection;

/**
 * @author Stuart Douglas
 */
public class SecurityPathMatches {

    private final PathSecurityInformation defaultPathSecurityInformation;
    private final Map<String, PathSecurityInformation> exactPathRoleInformation;
    private final Map<String, PathSecurityInformation> prefixPathRoleInformation;
    private final Map<String, PathSecurityInformation> extensionRoleInformation;

    private SecurityPathMatches(final PathSecurityInformation defaultPathSecurityInformation, final Map<String, PathSecurityInformation> exactPathRoleInformation, final Map<String, PathSecurityInformation> prefixPathRoleInformation, final Map<String, PathSecurityInformation> extensionRoleInformation) {
        this.defaultPathSecurityInformation = defaultPathSecurityInformation;
        this.exactPathRoleInformation = exactPathRoleInformation;
        this.prefixPathRoleInformation = prefixPathRoleInformation;
        this.extensionRoleInformation = extensionRoleInformation;
    }

    public SecurityPathMatch getSecurityInfo(final String path, final String method) {
        final List<Set<String>> roleSet = new ArrayList<Set<String>>();
        TransportGuaranteeType type = TransportGuaranteeType.NONE;
        type = handleMatch(method, defaultPathSecurityInformation, roleSet, type);
        PathSecurityInformation match = exactPathRoleInformation.get(path);
        if (match != null) {
            type = handleMatch(method, match, roleSet, type);
        }

        match = prefixPathRoleInformation.get(path);
        if (match != null) {
            type = handleMatch(method, match, roleSet, type);
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
                    type = handleMatch(method, match, roleSet, type);
                }
                qsPos = i;
                extension = false;
            } else if (c == '/') {
                extension = true;
                final String part = path.substring(0, i);
                match = prefixPathRoleInformation.get(part);
                if (match != null) {
                    type = handleMatch(method, match, roleSet, type);
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
                    match = extensionRoleInformation.get(ext);
                    if (match != null) {
                        type = handleMatch(method, match, roleSet, type);
                    }
                }
            }
        }
        return new SecurityPathMatch(type, roleSet);
    }

    private TransportGuaranteeType handleMatch(final String method, final PathSecurityInformation exact, final List<Set<String>> roleSet, TransportGuaranteeType type) {
        List<SecurityInformation> roles = exact.defaultRequiredRoles;
        for (SecurityInformation role : roles) {
            type = transport(type, role.transportGuaranteeType);
            if (!role.roles.isEmpty() ||
                    role.emptyRoleSemantic == ServletSecurity.EmptyRoleSemantic.DENY) {
                roleSet.add(role.roles);
            }
        }
        List<SecurityInformation> methodInfo = exact.perMethodRequiredRoles.get(method);
        if (methodInfo != null) {
            for (SecurityInformation role : methodInfo) {
                type = transport(type, role.transportGuaranteeType);
                if (!role.roles.isEmpty() ||
                        role.emptyRoleSemantic == ServletSecurity.EmptyRoleSemantic.DENY) {
                    roleSet.add(role.roles);
                }
            }
        }
        for (ExcludedMethodRoles excluded : exact.excludedMethodRoles) {
            if (!excluded.methods.contains(method)) {
                type = transport(type, excluded.securityInformation.transportGuaranteeType);

                if (!excluded.securityInformation.roles.isEmpty() ||
                        excluded.securityInformation.emptyRoleSemantic == ServletSecurity.EmptyRoleSemantic.DENY) {
                    roleSet.add(excluded.securityInformation.roles);
                }
            }
        }
        return type;
    }

    private TransportGuaranteeType transport(TransportGuaranteeType existing, TransportGuaranteeType other) {
        if (other.ordinal() > existing.ordinal()) {
            return other;
        }
        return existing;
    }

    public static Builder builder(final DeploymentInfo deploymentInfo) {
        return new Builder(deploymentInfo);
    }

    public static class Builder {
        private final DeploymentInfo deploymentInfo;
        private final PathSecurityInformation defaultPathSecurityInformation = new PathSecurityInformation();
        private final Map<String, PathSecurityInformation> exactPathRoleInformation = new HashMap<String, PathSecurityInformation>();
        private final Map<String, PathSecurityInformation> prefixPathRoleInformation = new HashMap<String, PathSecurityInformation>();
        private final Map<String, PathSecurityInformation> extensionRoleInformation = new HashMap<String, PathSecurityInformation>();

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
                    if (pattern.endsWith("/*") || pattern.endsWith("/")) {
                        String part = pattern.substring(0, pattern.lastIndexOf('/'));
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
            final Set<String> roles = new HashSet<String>(rolesAllowed);
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
                        info.perMethodRequiredRoles.put(method, securityInformations = new ArrayList<SecurityInformation>());
                    }
                    securityInformations.add(securityConstraint);
                }
            } else if (!webResources.getHttpMethodOmissions().isEmpty()) {
                info.excludedMethodRoles.add(new ExcludedMethodRoles(webResources.getHttpMethodOmissions(), securityConstraint));
            }
        }

        public SecurityPathMatches build() {
            return new SecurityPathMatches(defaultPathSecurityInformation, exactPathRoleInformation, prefixPathRoleInformation, extensionRoleInformation);
        }
    }


    private static class PathSecurityInformation {
        final List<SecurityInformation> defaultRequiredRoles = new ArrayList<SecurityInformation>();
        final Map<String, List<SecurityInformation>> perMethodRequiredRoles = new HashMap<String, List<SecurityInformation>>();
        final List<ExcludedMethodRoles> excludedMethodRoles = new ArrayList<ExcludedMethodRoles>();
    }

    private static final class ExcludedMethodRoles {
        final Set<String> methods;
        final SecurityInformation securityInformation;

        public ExcludedMethodRoles(final Set<String> methods, final SecurityInformation securityInformation) {
            this.methods = methods;
            this.securityInformation = securityInformation;
        }
    }

    private static final class SecurityInformation {
        final Set<String> roles;
        final TransportGuaranteeType transportGuaranteeType;
        final ServletSecurity.EmptyRoleSemantic emptyRoleSemantic;

        private SecurityInformation(final Set<String> roles, final TransportGuaranteeType transportGuaranteeType, final ServletSecurity.EmptyRoleSemantic emptyRoleSemantic) {
            this.emptyRoleSemantic = emptyRoleSemantic;
            this.roles = new HashSet<String>(roles);
            this.transportGuaranteeType = transportGuaranteeType;
        }
    }
}
