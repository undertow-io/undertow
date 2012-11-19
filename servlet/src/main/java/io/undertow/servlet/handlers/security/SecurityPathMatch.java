package io.undertow.servlet.handlers.security;

import java.util.List;
import java.util.Set;

import io.undertow.servlet.api.TransportGuaranteeType;

/**
 * @author Stuart Douglas
 */
public class SecurityPathMatch {

    private final TransportGuaranteeType transportGuaranteeType;
    private final List<Set<String>> requiredRoles;

    public SecurityPathMatch(final TransportGuaranteeType transportGuaranteeType, final List<Set<String>> requiredRoles) {
        this.transportGuaranteeType = transportGuaranteeType;
        this.requiredRoles = requiredRoles;
    }

    public TransportGuaranteeType getTransportGuaranteeType() {
        return transportGuaranteeType;
    }

    public List<Set<String>> getRequiredRoles() {
        return requiredRoles;
    }
}
