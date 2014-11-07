/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.websockets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public class WebSocketExtension {

    private final String name;
    private final List<Parameter> parameters;

    public WebSocketExtension(String name) {
        this.name = name;
        this.parameters = new ArrayList<>();
    }

    public WebSocketExtension(String name, List<Parameter> parameters) {
        this.name = name;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
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

    public static List<WebSocketExtension> parse(final String extensionHeader) {
        if(extensionHeader == null || extensionHeader.isEmpty()) {
            return Collections.emptyList();
        }
        List<WebSocketExtension> extensions = new ArrayList<>();
        //TODO: more efficient parsing algorithm
        String[] parts = extensionHeader.split(",");
        for (String part : parts) {
            String[] items = part.split(";");
            if (items.length > 0) {
                final List<Parameter> params = new ArrayList<>(items.length - 1);
                String name = items[0].trim();
                for (int i = 1; i < items.length; ++i) {
                    /*
                        Extensions can have parameters without values
                     */
                    if (items[i].contains("=")) {
                        String[] param = items[i].split("=");
                        if (param.length == 2) {
                            params.add(new Parameter(param[0].trim(), param[1].trim()));
                        }
                    } else {
                        params.add(new Parameter(items[i].trim(), null));
                    }
                }
                extensions.add(new WebSocketExtension(name, params));
            }
        }
        return extensions;
    }

    /**
     * Compose a String from a list of extensions to be used in the response of a protocol negotiation.
     *
     * @see io.undertow.util.Headers
     *
     * @param extensions list of {@link WebSocketExtension}
     * @return           a string representation of the extensions
     */
    public static String toExtensionHeader(final List<WebSocketExtension> extensions) {
        StringBuilder extensionsHeader = new StringBuilder();
        if (extensions != null && extensions.size() > 0) {
            Iterator<WebSocketExtension> it = extensions.iterator();
            while (it.hasNext()) {
                WebSocketExtension extension = it.next();
                extensionsHeader.append(extension.getName());
                for (Parameter param : extension.getParameters()) {
                    extensionsHeader.append("; ").append(param.getName());
                    if (param.getValue() != null && param.getValue().length() > 0) {
                        extensionsHeader.append("=").append(param.getValue());
                    }
                }
                if (it.hasNext()) {
                    extensionsHeader.append(", ");
                }
            }
        }
        return extensionsHeader.toString();
    }
}
