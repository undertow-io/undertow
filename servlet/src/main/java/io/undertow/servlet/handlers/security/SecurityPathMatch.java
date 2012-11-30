package io.undertow.servlet.handlers.security;

import java.util.List;
import java.util.Set;

import javax.servlet.annotation.ServletSecurity;

/**
 * @author Stuart Douglas
 */
public class SecurityPathMatch {

    private final ServletSecurity.TransportGuarantee transportGuaranteeType;
    private final List<Set<String>> requiredRoles;

    public SecurityPathMatch(final ServletSecurity.TransportGuarantee transportGuaranteeType, final List<Set<String>> requiredRoles) {
        this.transportGuaranteeType = transportGuaranteeType;
        this.requiredRoles = requiredRoles;
    }

    public ServletSecurity.TransportGuarantee getTransportGuaranteeType() {
        return transportGuaranteeType;
    }

    public List<Set<String>> getRequiredRoles() {
        return requiredRoles;
    }
}
