package io.undertow.server.handlers.builder;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.SetAttributeHandler;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class RewriteHandlerBuilder implements HandlerBuilder {
    @Override
    public String name() {
        return "rewrite";
    }

    @Override
    public Map<String, Class<?>> parameters() {
        return Collections.<String, Class<?>>singletonMap("value", ExchangeAttribute.class);
    }

    @Override
    public Set<String> requiredParameters() {
        return Collections.singleton("value");
    }

    @Override
    public String defaultParameter() {
        return "value";
    }

    @Override
    public HandlerWrapper build(final Map<String, Object> config) {
        final ExchangeAttribute value = (ExchangeAttribute) config.get("value");

        return new HandlerWrapper() {
            @Override
            public HttpHandler wrap(HttpHandler handler) {
                return new SetAttributeHandler(handler, ExchangeAttributes.relativePath(), value);
            }
        };
    }
}
