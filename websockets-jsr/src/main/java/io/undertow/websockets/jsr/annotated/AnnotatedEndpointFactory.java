package io.undertow.websockets.jsr.annotated;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketClose;
import javax.websocket.WebSocketError;
import javax.websocket.WebSocketMessage;
import javax.websocket.WebSocketOpen;
import javax.websocket.server.WebSocketPathParam;

import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.websockets.jsr.JsrWebSocketMessages;

/**
 * Factory that creates annotated end points.
 *
 * @author Stuart Douglas
 */
public class AnnotatedEndpointFactory implements InstanceFactory<Endpoint> {

    private final InstanceFactory<Object> underlyingFactory;
    private final Class<?> endpontClass;
    private final BoundMethod webSocketOpen;
    private final BoundMethod webSocketClose;
    private final BoundMethod webSocketError;
    private final BoundMethod textMessage;
    private final BoundMethod binaryByteArrayMessage;
    private final BoundMethod binaryByteBufferMessage;
    private final BoundMethod pongMessage;

    private AnnotatedEndpointFactory(final Class<?> endpointClass, final InstanceFactory<Object> underlyingFactory, final EndpointConfiguration configuration, final BoundMethod webSocketOpen, final BoundMethod webSocketClose, final BoundMethod webSocketError, final BoundMethod textMessage, final BoundMethod binaryByteArrayMessage, final BoundMethod binaryByteBufferMessage, final BoundMethod pongMessage) {
        this.underlyingFactory = underlyingFactory;
        this.endpontClass = endpointClass;
        this.webSocketOpen = webSocketOpen;
        this.webSocketClose = webSocketClose;
        this.webSocketError = webSocketError;

        this.textMessage = textMessage;
        this.binaryByteArrayMessage = binaryByteArrayMessage;
        this.binaryByteBufferMessage = binaryByteBufferMessage;
        this.pongMessage = pongMessage;
    }


    public static AnnotatedEndpointFactory create(final Class<?> endpointClass, final InstanceFactory<Object> underlyingInstance, final EndpointConfiguration configuration) throws DeploymentException {
        final Set<Class<? extends Annotation>> found = new HashSet<>();
        BoundMethod webSocketOpen = null;
        BoundMethod webSocketClose = null;
        BoundMethod webSocketError = null;
        BoundMethod textMessage = null;
        BoundMethod binaryByteBufferMessage = null;
        BoundMethod binaryByteArrayMessage = null;
        BoundMethod pongMessage = null;
        Class<?> c = endpointClass;
        do {
            for (final Method method : c.getDeclaredMethods()) {
                if (method.isAnnotationPresent(WebSocketOpen.class)) {
                    if (found.contains(WebSocketOpen.class)) {
                        throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(WebSocketOpen.class);
                    }
                    found.add(WebSocketOpen.class);
                    webSocketOpen = new BoundMethod(method,
                            new BoundSingleParameter(method, Session.class, true),
                            new BoundSingleParameter(method, EndpointConfiguration.class, true),
                            new BoundPathParameters(pathParams(method)));
                }
                if (method.isAnnotationPresent(WebSocketClose.class)) {
                    if (found.contains(WebSocketClose.class)) {
                        throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(WebSocketClose.class);
                    }
                    found.add(WebSocketClose.class);
                    webSocketClose = new BoundMethod(method,
                            new BoundSingleParameter(method, Session.class, true),
                            new BoundPathParameters(pathParams(method)));
                }
                if (method.isAnnotationPresent(WebSocketError.class)) {
                    if (found.contains(WebSocketError.class)) {
                        throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(WebSocketError.class);
                    }
                    found.add(WebSocketError.class);
                    webSocketError = new BoundMethod(method,
                            new BoundSingleParameter(method, Session.class, true),
                            new BoundSingleParameter(method, Throwable.class, false),
                            new BoundPathParameters(pathParams(method)));
                }
                if (method.isAnnotationPresent(WebSocketMessage.class)) {
                    //TODO: maxMessageSize
                    boolean messageHandled = false;
                    //this is a bit more complex
                    for (int i = 0; i < method.getParameterTypes().length; ++i) {
                        final Class<?> param = method.getParameterTypes()[i];
                        if (param.equals(byte[].class)) {
                            if (binaryByteArrayMessage != null || binaryByteBufferMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(WebSocketMessage.class);
                            }
                            binaryByteArrayMessage = new BoundMethod(method,
                                    new BoundSingleParameter(method, Session.class, true),
                                    new BoundSingleParameter(method, boolean.class, false),
                                    new BoundSingleParameter(method, byte[].class, false),
                                    new BoundPathParameters(pathParams(method)));
                            messageHandled = true;
                            break;
                        } else if (param.equals(ByteBuffer.class)) {
                            if (binaryByteArrayMessage != null || binaryByteBufferMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(WebSocketMessage.class);
                            }
                            binaryByteBufferMessage = new BoundMethod(method,
                                    new BoundSingleParameter(method, Session.class, true),
                                    new BoundSingleParameter(method, boolean.class, false),
                                    new BoundSingleParameter(method, ByteBuffer.class, false),
                                    new BoundPathParameters(pathParams(method)));
                            messageHandled = true;
                            break;

                        } else if (param.equals(String.class) && getPathParam(method, i) == null) {
                            if (textMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(WebSocketMessage.class);
                            }
                            textMessage = new BoundMethod(method,
                                    new BoundSingleParameter(method, Session.class, true),
                                    new BoundSingleParameter(method, boolean.class, false),
                                    new BoundSingleParameter(method, String.class, false),
                                    new BoundPathParameters(pathParams(method)));
                            messageHandled = true;
                            break;

                        } else if (param.equals(PongMessage.class)) {
                            if (pongMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(WebSocketMessage.class);
                            }
                            pongMessage = new BoundMethod(method,
                                    new BoundSingleParameter(method, Session.class, true),
                                    new BoundSingleParameter(method, boolean.class, false),
                                    new BoundSingleParameter(method, PongMessage.class, false),
                                    new BoundPathParameters(pathParams(method)));
                            messageHandled = true;
                            break;

                        }
                    }
                    if (!messageHandled) {
                        throw new DeploymentException("TODO: decoders");
                    }
                }
            }
            c = c.getSuperclass();
        } while (c != Object.class && c != null);
        return new AnnotatedEndpointFactory(endpointClass, underlyingInstance, configuration, webSocketOpen, webSocketClose, webSocketError, textMessage, binaryByteArrayMessage, binaryByteBufferMessage, pongMessage);
    }


    private static Map<String, Integer> pathParams(final Method method) {
        Map<String, Integer> params = new HashMap<>();
        for (int i = 0; i < method.getParameterTypes().length; ++i) {
            WebSocketPathParam param = getPathParam(method, i);
            if (param != null) {
                params.put(param.value(), i);
            }
        }
        return params;
    }

    private static WebSocketPathParam getPathParam(final Method method, final int parameter) {
        for (final Annotation annotation : method.getParameterAnnotations()[parameter]) {
            if (annotation.annotationType().equals(WebSocketPathParam.class)) {
                return (WebSocketPathParam) annotation;
            }
        }
        return null;
    }


    @Override
    public InstanceHandle<Endpoint> createInstance() throws InstantiationException {
        final InstanceHandle<Object> instance = underlyingFactory.createInstance();
        final AnnotatedEndpoint endpoint = new AnnotatedEndpoint(instance, webSocketOpen, webSocketClose, webSocketError, textMessage, binaryByteArrayMessage, binaryByteBufferMessage, pongMessage);
        return new InstanceHandle<Endpoint>() {
            @Override
            public Endpoint getInstance() {
                return endpoint;
            }

            @Override
            public void release() {
                instance.release();
            }
        };
    }


    public interface BoundParameter {
        Set<Integer> positions();

        void populate(final Object[] params, final Map<Class<?>, Object> value);
    }


    /**
     * represents a parameter binding
     */
    private static class BoundSingleParameter implements BoundParameter {

        private final int position;
        private final boolean optional;
        private final Class<?> type;

        public BoundSingleParameter(final Method method, final Class<?> type, final boolean optional) {
            this.optional = optional;
            this.type = type;
            int pos = -1;
            for (int i = 0; i < method.getParameterTypes().length; ++i) {
                if (method.getParameterTypes()[i].equals(Session.class)) {
                    if (pos != -1) {
                        throw JsrWebSocketMessages.MESSAGES.moreThanOneParameterOfType(type, method);
                    }
                    pos = i;
                }
            }
            if (pos != -1) {
                position = pos;
            } else if (optional) {
                position = -1;
            } else {
                throw JsrWebSocketMessages.MESSAGES.parameterNotFound(type, method);
            }
        }

        public Set<Integer> positions() {
            if (position == -1) {
                return Collections.emptySet();
            }
            return Collections.singleton(position);
        }


        public void populate(final Object[] params, final Map<Class<?>, Object> value) {
            params[position] = value.get(type);
        }
    }

    /**
     * represents a parameter binding
     */
    private static class BoundPathParameters implements BoundParameter {

        private final Map<String, Integer> postions;

        public BoundPathParameters(final Map<String, Integer> postions) {
            this.postions = postions;
        }

        public Set<Integer> positions() {
            return new HashSet<>(postions.values());
        }


        public void populate(final Object[] params, final Map<Class<?>, Object> value) {
            final Map<String, String> data = (Map<String, String>) value.get(Map.class);
            for (Map.Entry<String, String> entry : data.entrySet()) {
                params[postions.get(entry.getKey())] = entry.getValue();
            }
        }
    }
}
