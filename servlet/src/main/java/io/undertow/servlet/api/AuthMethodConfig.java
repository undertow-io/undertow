package io.undertow.servlet.api;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
public class AuthMethodConfig implements Cloneable {

    private final String name;
    private final Map<String, String> properties;

    public AuthMethodConfig(String name, Map<String, String> properties) {
        this.name = name;
        this.properties = new HashMap<String, String>(properties);
    }

    public AuthMethodConfig(String name) {
        this.name = name;
        this.properties = new HashMap<String, String>();
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public AuthMethodConfig clone() {
        return new AuthMethodConfig(name, properties);
    }
}
