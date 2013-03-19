package io.undertow.websockets.jsr.annotated;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.websocket.DeploymentException;

import io.undertow.websockets.jsr.JsrWebSocketMessages;

/**
 * A method with bound parameters
 */
final class BoundMethod {

    private final Method method;
    private final List<BoundParameter> parameters = new ArrayList<>();
    private final Set<Class> paramTypes = new HashSet<>();

    public BoundMethod(final Method method, BoundParameter... params) throws DeploymentException {
        this.method = method;
        final Set<Integer> allParams = new HashSet<>();
        for (int i = 0; i < method.getParameterTypes().length; ++i) {
            allParams.add(i);
        }
        for (BoundParameter param : params) {
            parameters.add(param);
            allParams.removeAll(param.positions());
            paramTypes.add(param.getType());
        }
        if (!allParams.isEmpty()) {
            throw JsrWebSocketMessages.MESSAGES.invalidParamers(method, allParams);
        }
    }

    public Object invoke(final Object instance, final Map<Class<?>, Object> values) {
        final Object[] params = new Object[method.getParameterTypes().length];
        for (BoundParameter param : parameters) {
            param.populate(params, values);
        }
        try {
            return method.invoke(instance, params);
        } catch (IllegalAccessException|InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasParameterType(final Class<?> type) {
        return paramTypes.contains(type);
    }

}
