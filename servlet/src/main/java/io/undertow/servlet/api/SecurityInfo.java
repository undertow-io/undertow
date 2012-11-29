package io.undertow.servlet.api;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class SecurityInfo implements Cloneable {

    private final Set<String> rolesAllowed = new HashSet<String>();
    private volatile TransportGuaranteeType transportGuaranteeType = TransportGuaranteeType.NONE;


    public TransportGuaranteeType getTransportGuaranteeType() {
        return transportGuaranteeType;
    }

    public SecurityInfo setTransportGuaranteeType(final TransportGuaranteeType transportGuaranteeType) {
        this.transportGuaranteeType = transportGuaranteeType;
        return this;
    }

    public SecurityInfo addRoleAllowed(final String role) {
        this.rolesAllowed.add(role);
        return this;
    }

    public Set<String> getRolesAllowed() {
        return new HashSet<String>(rolesAllowed);
    }

    @Override
    public SecurityInfo clone() {
        final SecurityInfo info = createInstance();
        info.transportGuaranteeType = transportGuaranteeType;
        info.rolesAllowed.addAll(rolesAllowed);
        return info;
    }

    protected SecurityInfo createInstance() {
        return new SecurityInfo();
    }
}
