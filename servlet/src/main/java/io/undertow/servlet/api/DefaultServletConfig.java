package io.undertow.servlet.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The default servlet config. By default this has quite a restrictive configuration, only allowing
 * extensions in common use in the web to be served.
 *
 * This class is deprecated, the default servlet should be configured via context params.
 *
 * @author Stuart Douglas
 */
@Deprecated
public class DefaultServletConfig {

    private static final String[] DEFAULT_ALLOWED_EXTENSIONS = {"js", "css", "png", "jpg", "gif", "html", "htm", "txt", "pdf"};
    private static final String[] DEFAULT_DISALLOWED_EXTENSIONS = {"class", "jar", "war", "zip", "xml"};

    private final boolean defaultAllowed;
    private final Set<String> allowed;
    private final Set<String> disallowed;

    public DefaultServletConfig(final boolean defaultAllowed, final Set<String> exceptions) {
        this.defaultAllowed = defaultAllowed;
        if(defaultAllowed) {
            disallowed = Collections.unmodifiableSet(new HashSet<String>(exceptions));
            allowed = null;
        } else {
            allowed = Collections.unmodifiableSet(new HashSet<String>(exceptions));
            disallowed = null;
        }
    }

    public DefaultServletConfig(final boolean defaultAllowed) {
        this.defaultAllowed = defaultAllowed;
        this.allowed = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(DEFAULT_ALLOWED_EXTENSIONS)));
        this.disallowed = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(DEFAULT_DISALLOWED_EXTENSIONS)));
    }

    public DefaultServletConfig() {
        this.defaultAllowed = false;
        this.allowed = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(DEFAULT_ALLOWED_EXTENSIONS)));
        this.disallowed = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(DEFAULT_DISALLOWED_EXTENSIONS)));
    }

    public boolean isDefaultAllowed() {
        return defaultAllowed;
    }

    public Set<String> getAllowed() {
        return allowed;
    }

    public Set<String> getDisallowed() {
        return disallowed;
    }
}
