package io.undertow.websockets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public class WebSocketExtension {

    private final String name;
    private final List<Parameter> parameters;

    public WebSocketExtension(String name, List<Parameter> parameters) {
        this.name = name;
        this.parameters = Collections.unmodifiableList(new ArrayList<Parameter>(parameters));
    }

    public String getName() {
        return name;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    public static final class Parameter {
        private final String name;
        private final String value;

        public Parameter(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "{'" + name + '\'' +
                    ": '" + value + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "WebSocketExtension{" +
                "name='" + name + '\'' +
                ", parameters=" + parameters +
                '}';
    }
}
