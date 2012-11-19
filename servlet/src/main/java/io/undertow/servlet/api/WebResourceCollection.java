package io.undertow.servlet.api;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class WebResourceCollection {

    private final Set<String> httpMethods;
    private final Set<String> httpMethodOmissions;
    private final Set<String> urlPatterns;

    public WebResourceCollection(final Set<String> httpMethods, final Set<String> httpMethodOmissions, final Set<String> urlPatterns) {
        this.httpMethods = Collections.unmodifiableSet(new HashSet<String>(httpMethods));
        this.httpMethodOmissions = Collections.unmodifiableSet(new HashSet<String>(httpMethodOmissions));
        this.urlPatterns = Collections.unmodifiableSet(new HashSet<String>(urlPatterns));
    }

    public Set<String> getHttpMethodOmissions() {
        return httpMethodOmissions;
    }

    public Set<String> getUrlPatterns() {
        return urlPatterns;
    }

    public Set<String> getHttpMethods() {
        return httpMethods;
    }
}
