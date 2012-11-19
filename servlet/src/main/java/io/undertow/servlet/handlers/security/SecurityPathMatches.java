package io.undertow.servlet.handlers.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private SecurityPathMatches(final PathSecurityInformation defaultPathSecurityInformation, final Map<String, PathSecurityInformation> exactPathRoleInformation, final Map<String, PathSecurityInformation> prefixPathRoleInformation) {
        this.defaultPathSecurityInformation = defaultPathSecurityInformation;
        this.exactPathRoleInformation = exactPathRoleInformation;
        this.prefixPathRoleInformation = prefixPathRoleInformation;
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
        for (int i = path.length() - 1; i >= 0; --i) {
            final char c = path.charAt(i);
            if (c == '?') {
                //there was a query string, check the exact matches again
                final String part = path.substring(0, i);
                match = exactPathRoleInformation.get(part);
                if (match != null) {
                    type = handleMatch(method, match, roleSet, type);
                }
            } else if (c == '/') {
                final String part = path.substring(0, i);
                match = prefixPathRoleInformation.get(part);
                if (match != null) {
                    type = handleMatch(method, match, roleSet, type);
                }
            }
        }
        return new SecurityPathMatch(type, roleSet);
    }

    private TransportGuaranteeType handleMatch(final String method, final PathSecurityInformation exact, final List<Set<String>> roleSet, TransportGuaranteeType type) {
        List<SecurityInformation> roles = exact.defaultRequiredRoles;
        for (SecurityInformation role : roles) {
            type = transport(type, role.transportGuaranteeType);
            roleSet.add(role.roles);
        }
        List<SecurityInformation> methodInfo = exact.perMethodRequiredRoles.get(method);
        if (methodInfo != null) {
            for (SecurityInformation role : methodInfo) {
                type = transport(type, role.transportGuaranteeType);
                roleSet.add(role.roles);
            }
        }
        for (ExcludedMethodRoles excluded : exact.excludedMethodRoles) {
            if (!excluded.methods.contains(method)) {
                type = transport(type, excluded.securityInformation.transportGuaranteeType);
                roleSet.add(excluded.securityInformation.roles);
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final PathSecurityInformation defaultPathSecurityInformation = new PathSecurityInformation();
        private final Map<String, PathSecurityInformation> exactPathRoleInformation = new HashMap<String, PathSecurityInformation>();
        private final Map<String, PathSecurityInformation> prefixPathRoleInformation = new HashMap<String, PathSecurityInformation>();

        private Builder() {

        }

        public void addSecurityConstraint(final SecurityConstraint securityConstraint) {
            final SecurityInformation securityInformation = new SecurityInformation(securityConstraint.getRoleNames(), securityConstraint.getTransportGuaranteeType());
            for (final WebResourceCollection webResources : securityConstraint.getWebResourceCollections()) {
                if (webResources.getUrlPatterns().isEmpty()) {
                    //default that is applied to everything
                    setupPathSecurityInformation(defaultPathSecurityInformation, securityInformation, webResources);
                }
            }

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
            return new SecurityPathMatches(defaultPathSecurityInformation, exactPathRoleInformation, prefixPathRoleInformation);
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

        private SecurityInformation(final Set<String> roles, final TransportGuaranteeType transportGuaranteeType) {
            this.roles = new HashSet<String>(roles);
            this.transportGuaranteeType = transportGuaranteeType;
        }
    }
}
