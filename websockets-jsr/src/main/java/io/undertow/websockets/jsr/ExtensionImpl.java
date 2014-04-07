package io.undertow.websockets.jsr;

import io.undertow.websockets.WebSocketExtension;

import javax.websocket.Extension;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Stuart Douglas
 */
class ExtensionImpl implements Extension {

    private final String name;
    private final List<Parameter> parameters;

    ExtensionImpl(String name, List<Parameter> parameters) {
        this.name = name;
        this.parameters = parameters;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Parameter> getParameters() {
        return parameters;
    }

    public static class ParameterImpl implements Parameter {
        private final String name;
        private final String value;

        public ParameterImpl(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getValue() {
            return value;
        }
    }

    public static Extension create(WebSocketExtension extension) {
        List<Parameter> params = new ArrayList<Parameter>(extension.getParameters().size());
        for(WebSocketExtension.Parameter p : extension.getParameters()) {
            params.add(new ParameterImpl(p.getName(), p.getValue()));
        }
        return new ExtensionImpl(extension.getName(), params);
    }
}
