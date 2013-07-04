package io.undertow.server.handlers.builder;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.SetAttributeHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class SetHandlerBuilder implements HandlerBuilder {
    @Override
    public String name() {
        return "set";
    }

    @Override
    public Map<String, Class<?>> parameters() {
        Map<String, Class<?>> parameters = new HashMap<String, Class<?>>();
        parameters.put("value", ExchangeAttribute.class);
        parameters.put("attribute", ExchangeAttribute.class);

        return parameters;
    }

    @Override
    public Set<String> requiredParameters() {
        final Set<String> req = new HashSet<String>();
        req.add("value");
        req.add("attribute");
        return req;
    }

    @Override
    public String defaultParameter() {
        return null;
    }

    @Override
    public HandlerWrapper build(final Map<String, Object> config) {
        final ExchangeAttribute value = (ExchangeAttribute) config.get("value");
        final ExchangeAttribute attribute = (ExchangeAttribute) config.get("attribute");

        return new HandlerWrapper() {
            @Override
            public HttpHandler wrap(HttpHandler handler) {
                return new SetAttributeHandler(handler, attribute, value);
            }
        };
    }
}
