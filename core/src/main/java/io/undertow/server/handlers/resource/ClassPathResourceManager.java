package io.undertow.server.handlers.resource;

import java.io.IOException;
import java.net.URL;

/**
 * @author Stuart Douglas
 */
public class ClassPathResourceManager implements ResourceManager {

    /**
     * The class loader that is used to load resources
     */
    private final ClassLoader classLoader;
    /**
     * The prefiex that is appended to resources that are to be loaded.
     */
    private final String prefix;

    public ClassPathResourceManager(final ClassLoader loader, final Package p) {
        this(loader, p.getName().replace(".", "/"));
    }

    public ClassPathResourceManager(final ClassLoader classLoader, final String prefix) {
        this.classLoader = classLoader;
        if (prefix.equals("")) {
            this.prefix = "";
        } else if (prefix.endsWith("/")) {
            this.prefix = prefix;
        } else {
            this.prefix = prefix + "/";
        }
    }

    @Override
    public Resource getResource(final String path) throws IOException {
        final String realPath = prefix + path;
        final URL resource = classLoader.getResource(realPath);
        if(resource == null) {
            return null;
        } else {
            return new URLResource(resource, resource.openConnection());
        }

    }
}
