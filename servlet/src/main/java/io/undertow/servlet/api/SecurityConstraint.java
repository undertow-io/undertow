package io.undertow.servlet.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class SecurityConstraint extends SecurityInfo<SecurityConstraint> {

    private final Set<WebResourceCollection> webResourceCollections = new HashSet<WebResourceCollection>();

    public Set<WebResourceCollection> getWebResourceCollections() {
        return Collections.unmodifiableSet(webResourceCollections);
    }

    public SecurityConstraint addWebResourceCollection(final WebResourceCollection webResourceCollection) {
        this.webResourceCollections.add(webResourceCollection);
        return this;
    }

    public SecurityConstraint addWebResourceCollections(final WebResourceCollection ... webResourceCollection) {
        this.webResourceCollections.addAll(Arrays.asList(webResourceCollection));
        return this;
    }
    public SecurityConstraint addWebResourceCollections(final List<WebResourceCollection> webResourceCollections) {
        this.webResourceCollections.addAll(webResourceCollections);
        return this;
    }

    @Override
    protected SecurityConstraint createInstance() {
        return new SecurityConstraint();
    }

    @Override
    public SecurityConstraint clone() {
        SecurityConstraint info = super.clone();
        this.webResourceCollections.addAll(webResourceCollections);
        return info;
    }

}
