package io.undertow.servlet.api;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class SecurityConstraint {

    private final Set<WebResourceCollection> webResourceCollections;
    private final Set<String> roleNames;
    private final TransportGuaranteeType transportGuaranteeType;

    public SecurityConstraint(Set<WebResourceCollection> webResourceCollections, Set<String> roleNames, TransportGuaranteeType transportGuaranteeType) {
        this.webResourceCollections = Collections.unmodifiableSet(new HashSet<WebResourceCollection>(webResourceCollections));
        this.roleNames = Collections.unmodifiableSet(new HashSet<String>(roleNames));
        this.transportGuaranteeType = transportGuaranteeType;
    }

    public Set<String> getRoleNames() {
        return roleNames;
    }

    public TransportGuaranteeType getTransportGuaranteeType() {
        return transportGuaranteeType;
    }

    public Set<WebResourceCollection> getWebResourceCollections() {
        return webResourceCollections;
    }
}
