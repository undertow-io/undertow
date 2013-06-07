package io.undertow.websockets.jsr.annotated;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.websocket.DecodeException;
import javax.websocket.DeploymentException;

import io.undertow.websockets.jsr.JsrWebSocketMessages;

/**
 * A method with bound parameters
 */
final class BoundMethod {

    private final Method method;
    private final List<BoundParameter> parameters = new ArrayList<>();
    private final Set<Class> paramTypes = new HashSet<>();
    private final Class<?> messageType;
    private final boolean decoderRequired;

    public BoundMethod(final Method method, final Class<?> messageType, final boolean decoderRequired, BoundParameter... params) throws DeploymentException {
        this.method = method;
        this.messageType = messageType;
        this.decoderRequired = decoderRequired;
        final Set<Integer> allParams = new HashSet<>();
        for (int i = 0; i < method.getParameterTypes().length; ++i) {
            allParams.add(i);
            paramTypes.add(method.getParameterTypes()[i]);
        }
        for (BoundParameter param : params) {
            parameters.add(param);
            allParams.removeAll(param.positions());
        }
        if (!allParams.isEmpty()) {
            throw JsrWebSocketMessages.MESSAGES.invalidParamers(method, allParams);
        }
    }

    public Object invoke(final Object instance, final Map<Class<?>, Object> values) throws DecodeException {
        final Object[] params = new Object[method.getParameterTypes().length];
        for (BoundParameter param : parameters) {
            param.populate(params, values);
        }
        try {
            return method.invoke(instance, params);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasParameterType(final Class<?> type) {
        return paramTypes.contains(type);
    }

    public Class<?> getMessageType() {
        return messageType;
    }

    public boolean isDecoderRequired() {
        return decoderRequired;
    }
}
