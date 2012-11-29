package io.undertow.servlet.api;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class SecurityInfo<T extends SecurityInfo> implements Cloneable {

    private final Set<String> rolesAllowed = new HashSet<String>();
    private volatile TransportGuaranteeType transportGuaranteeType = TransportGuaranteeType.NONE;


    public TransportGuaranteeType getTransportGuaranteeType() {
        return transportGuaranteeType;
    }

    public T setTransportGuaranteeType(final TransportGuaranteeType transportGuaranteeType) {
        this.transportGuaranteeType = transportGuaranteeType;
        return (T) this;
    }

    public T addRoleAllowed(final String role) {
        this.rolesAllowed.add(role);
        return (T) this;
    }

    public T addRolesAllowed(final String ... roles) {
        this.rolesAllowed.addAll(Arrays.asList(roles));
        return (T) this;
    }
    public T addRolesAllowed(final Collection<String> roles) {
        this.rolesAllowed.addAll(roles);
        return (T) this;
    }
    public Set<String> getRolesAllowed() {
        return new HashSet<String>(rolesAllowed);
    }

    @Override
    public T clone() {
        final SecurityInfo info = createInstance();
        info.transportGuaranteeType = transportGuaranteeType;
        info.rolesAllowed.addAll(rolesAllowed);
        return (T) info;
    }

    protected T createInstance() {
        return (T) new SecurityInfo();
    }
}
