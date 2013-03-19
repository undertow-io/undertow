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
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.server.PathParam;

import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.websockets.jsr.JsrWebSocketMessages;

/**
 * Factory that creates annotated end points.
 *
 * @author Stuart Douglas
 */
public class AnnotatedEndpointFactory implements InstanceFactory<Endpoint> {

    private final InstanceFactory<?> underlyingFactory;
    private final Class<?> endpontClass;
    private final BoundMethod OnOpen;
    private final BoundMethod OnClose;
    private final BoundMethod OnError;
    private final BoundMethod textMessage;
    private final BoundMethod binaryMessage;
    private final BoundMethod pongMessage;

    private AnnotatedEndpointFactory(final Class<?> endpointClass, final InstanceFactory<?> underlyingFactory, final BoundMethod OnOpen, final BoundMethod OnClose, final BoundMethod OnError, final BoundMethod textMessage, final BoundMethod binaryMessage, final BoundMethod pongMessage) {
        this.underlyingFactory = underlyingFactory;
        this.endpontClass = endpointClass;
        this.OnOpen = OnOpen;
        this.OnClose = OnClose;
        this.OnError = OnError;

        this.textMessage = textMessage;
        this.binaryMessage = binaryMessage;
        this.pongMessage = pongMessage;
    }


    public static AnnotatedEndpointFactory create(final Class<?> endpointClass, final InstanceFactory<?> underlyingInstance) throws DeploymentException {
        final Set<Class<? extends Annotation>> found = new HashSet<>();
        BoundMethod OnOpen = null;
        BoundMethod OnClose = null;
        BoundMethod OnError = null;
        BoundMethod textMessage = null;
        BoundMethod binaryMessage = null;
        BoundMethod pongMessage = null;
        Class<?> c = endpointClass;
        do {
            for (final Method method : c.getDeclaredMethods()) {
                if (method.isAnnotationPresent(OnOpen.class)) {
                    if (found.contains(OnOpen.class)) {
                        throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnOpen.class);
                    }
                    found.add(OnOpen.class);
                    OnOpen = new BoundMethod(method,
                            new BoundSingleParameter(method, Session.class, true),
                            new BoundSingleParameter(method, EndpointConfiguration.class, true),
                            new BoundPathParameters(pathParams(method)));
                }
                if (method.isAnnotationPresent(OnClose.class)) {
                    if (found.contains(OnClose.class)) {
                        throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnClose.class);
                    }
                    found.add(OnClose.class);
                    OnClose = new BoundMethod(method,
                            new BoundSingleParameter(method, Session.class, true),
                            new BoundPathParameters(pathParams(method)));
                }
                if (method.isAnnotationPresent(OnError.class)) {
                    if (found.contains(OnError.class)) {
                        throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnError.class);
                    }
                    found.add(OnError.class);
                    OnError = new BoundMethod(method,
                            new BoundSingleParameter(method, Session.class, true),
                            new BoundSingleParameter(method, Throwable.class, false),
                            new BoundPathParameters(pathParams(method)));
                }
                if (method.isAnnotationPresent(OnMessage.class)) {
                    //TODO: maxMessageSize
                    boolean messageHandled = false;
                    //this is a bit more complex
                    for (int i = 0; i < method.getParameterTypes().length; ++i) {
                        final Class<?> param = method.getParameterTypes()[i];
                        if (param.equals(byte[].class)) {
                            if (binaryMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnMessage.class);
                            }
                            binaryMessage = new BoundMethod(method,
                                    new BoundSingleParameter(method, Session.class, true),
                                    new BoundSingleParameter(method, boolean.class, true),
                                    new BoundSingleParameter(method, byte[].class, false),
                                    new BoundPathParameters(pathParams(method)));
                            messageHandled = true;
                            break;
                        } else if (param.equals(ByteBuffer.class)) {
                            if (binaryMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnMessage.class);
                            }
                            binaryMessage = new BoundMethod(method,
                                    new BoundSingleParameter(method, Session.class, true),
                                    new BoundSingleParameter(method, boolean.class, true),
                                    new BoundSingleParameter(method, ByteBuffer.class, false),
                                    new BoundPathParameters(pathParams(method)));
                            messageHandled = true;
                            break;

                        } else if (param.equals(String.class) && getPathParam(method, i) == null) {
                            if (textMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnMessage.class);
                            }
                            textMessage = new BoundMethod(method,
                                    new BoundSingleParameter(method, Session.class, true),
                                    new BoundSingleParameter(method, boolean.class, true),
                                    new BoundSingleParameter(method, String.class, false),
                                    new BoundPathParameters(pathParams(method)));
                            messageHandled = true;
                            break;

                        } else if (param.equals(PongMessage.class)) {
                            if (pongMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnMessage.class);
                            }
                            pongMessage = new BoundMethod(method,
                                    new BoundSingleParameter(method, Session.class, true),
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
        return new AnnotatedEndpointFactory(endpointClass, underlyingInstance, OnOpen, OnClose, OnError, textMessage, binaryMessage, pongMessage);
    }


    private static Map<String, Integer> pathParams(final Method method) {
        Map<String, Integer> params = new HashMap<>();
        for (int i = 0; i < method.getParameterTypes().length; ++i) {
            PathParam param = getPathParam(method, i);
            if (param != null) {
                params.put(param.value(), i);
            }
        }
        return params;
    }

    private static PathParam getPathParam(final Method method, final int parameter) {
        for (final Annotation annotation : method.getParameterAnnotations()[parameter]) {
            if (annotation.annotationType().equals(PathParam.class)) {
                return (PathParam) annotation;
            }
        }
        return null;
    }


    @Override
    public InstanceHandle<Endpoint> createInstance() throws InstantiationException {
        final InstanceHandle<?> instance = underlyingFactory.createInstance();
        final AnnotatedEndpoint endpoint = new AnnotatedEndpoint(instance, OnOpen, OnClose, OnError, textMessage,  binaryMessage, pongMessage);
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


    /**
     * represents a parameter binding
     */
    private static class BoundSingleParameter implements BoundParameter {

        private final int position;
        private final Class<?> type;

        public BoundSingleParameter(final Method method, final Class<?> type, final boolean optional) {
            this.type = type;
            int pos = -1;
            for (int i = 0; i < method.getParameterTypes().length; ++i) {
                boolean pathParam = false;
                for(Annotation annotation : method.getParameterAnnotations()[i]) {
                    if(annotation.annotationType().equals(PathParam.class)) {
                        pathParam = true;
                        break;
                    }
                }
                if(pathParam) {
                    continue;
                }
                if (method.getParameterTypes()[i].equals(type)) {
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
            if(position == -1) {
                return;
            }
            params[position] = value.get(type);
        }

        @Override
        public Class<?> getType() {
            return type;
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

        @Override
        public Class<?> getType() {
            return Map.class;
        }
    }
}
