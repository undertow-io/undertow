package io.undertow.server.handlers.builder;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.ResponseCodeHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class ResponseCodeHandlerBuilder implements HandlerBuilder {
    @Override
    public String name() {
        return "response-code";
    }

    @Override
    public Map<String, Class<?>> parameters() {
        Map<String, Class<?>> parameters = new HashMap<String, Class<?>>();
        parameters.put("value", Integer.class);
        return parameters;
    }

    @Override
    public Set<String> requiredParameters() {
        final Set<String> req = new HashSet<String>();
        req.add("value");
        return req;
    }

    @Override
    public String defaultParameter() {
        return "200";
    }

    @Override
    public HandlerWrapper build(final Map<String, Object> config) {
        final Integer value = (Integer) config.get("value");
        return new HandlerWrapper() {
            @Override
            public HttpHandler wrap(HttpHandler handler) {
                return new ResponseCodeHandler(value);
            }
        };
    }
}
