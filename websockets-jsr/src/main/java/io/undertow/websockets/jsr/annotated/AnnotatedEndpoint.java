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

package io.undertow.websockets.jsr.annotated;

import io.undertow.UndertowLogger;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.websockets.core.WebSocketLogger;
import io.undertow.websockets.jsr.OrderedExecutor;
import io.undertow.websockets.jsr.UndertowSession;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @author Stuart Douglas
 */
public class AnnotatedEndpoint extends Endpoint {

    private final InstanceHandle<?> instance;
    private Executor executor;

    private final BoundMethod webSocketOpen;
    private final BoundMethod webSocketClose;
    private final BoundMethod webSocketError;
    private final BoundMethod textMessage;
    private final BoundMethod binaryMessage;
    private final BoundMethod pongMessage;
    private final SendHandler errorReportingSendHandler = new SendHandler() {
        @Override
        public void onResult(final SendResult result) {
            if (!result.isOK()) {
                AnnotatedEndpoint.this.onError(null, result.getException());
            }
        }
    };

    AnnotatedEndpoint(final InstanceHandle<?> instance, final BoundMethod webSocketOpen, final BoundMethod webSocketClose, final BoundMethod webSocketError, final BoundMethod textMessage, final BoundMethod binaryMessage, final BoundMethod pongMessage) {
        this.instance = instance;
        this.webSocketOpen = webSocketOpen;
        this.webSocketClose = webSocketClose;
        this.webSocketError = webSocketError;
        this.textMessage = textMessage;
        this.binaryMessage = binaryMessage;
        this.pongMessage = pongMessage;
    }

    @Override
    public void onOpen(final Session session, final EndpointConfig endpointConfiguration) {

        this.executor = new OrderedExecutor(((UndertowSession)session).getWebSocketChannel().getWorker());

        final UndertowSession s = (UndertowSession) session;
        boolean partialText = textMessage == null || (textMessage.hasParameterType(boolean.class) && !textMessage.getMessageType().equals(boolean.class));
        boolean partialBinary = binaryMessage == null || (binaryMessage.hasParameterType(boolean.class) && !binaryMessage.getMessageType().equals(boolean.class));

        if(textMessage != null) {
            if(partialText) {
                addPartialHandler(s, textMessage);
            } else {
                addWholeHandler(s, textMessage);
            }
        }
        if(binaryMessage != null) {
            if(partialBinary) {
                addPartialHandler(s, binaryMessage);
            } else {
                addWholeHandler(s, binaryMessage);
            }
        }

        if (webSocketOpen != null) {
            final Map<Class<?>, Object> params = new HashMap<>();
            params.put(Session.class, session);
            params.put(EndpointConfig.class, endpointConfiguration);
            params.put(Map.class, session.getPathParameters());
            invokeMethod(params, webSocketOpen, s);
        }

    }

    private void addPartialHandler(final UndertowSession session, final BoundMethod method) {
        session.addMessageHandler((Class) textMessage.getMessageType(), new MessageHandler.Partial<Object>() {
            @Override
            public void onMessage(Object partialMessage, boolean last) {

                final Map<Class<?>, Object> params = new HashMap<>();
                params.put(Session.class, session);
                params.put(Map.class, session.getPathParameters());
                params.put(method.getMessageType(), partialMessage);
                params.put(boolean.class, last);
                session.getContainer().invokeEndpointMethod(executor, new Runnable() {
                    @Override
                    public void run() {
                        final Object result;
                        try {
                            result = method.invoke(instance.getInstance(), params);
                        } catch (Exception e) {
                            AnnotatedEndpoint.this.onError(session, e);
                            return;
                        }
                        sendResult(result, session);
                    }
                });
            }
        });
    }



    private void addWholeHandler(final UndertowSession session, final BoundMethod method) {
        session.addMessageHandler((Class) method.getMessageType(), new MessageHandler.Whole<Object>() {
            @Override
            public void onMessage(Object partialMessage) {

                final Map<Class<?>, Object> params = new HashMap<>();
                params.put(Session.class, session);
                params.put(Map.class, session.getPathParameters());
                params.put(method.getMessageType(), partialMessage);
                session.getContainer().invokeEndpointMethod(executor, new Runnable() {
                    @Override
                    public void run() {
                        final Object result;
                        try {
                            result = method.invoke(instance.getInstance(), params);
                        } catch (Exception e) {
                            AnnotatedEndpoint.this.onError(session, e);
                            return;
                        }
                        sendResult(result, session);
                    }
                });
            }
        });
    }

    private void invokeMethod(final Map<Class<?>, Object> params, final BoundMethod method, final UndertowSession session) {
        session.getContainer().invokeEndpointMethod(executor, new Runnable() {
            @Override
            public void run() {
                try {
                    method.invoke(instance.getInstance(), params);
                } catch (Exception e) {
                    onError(session, e);
                }
            }
        });
    }


    private void sendResult(final Object result, UndertowSession session) {
        if (result != null) {
            if (result instanceof String) {
                session.getAsyncRemote().sendText((String) result, errorReportingSendHandler);
            } else if (result instanceof byte[]) {
                session.getAsyncRemote().sendBinary(ByteBuffer.wrap((byte[]) result), errorReportingSendHandler);
            } else if (result instanceof ByteBuffer) {
                session.getAsyncRemote().sendBinary((ByteBuffer) result, errorReportingSendHandler);
            } else {
                session.getAsyncRemote().sendObject(result, errorReportingSendHandler);
            }
        }
    }

    @Override
    public void onClose(final Session session, final CloseReason closeReason) {
        if (webSocketClose != null) {
            final Map<Class<?>, Object> params = new HashMap<>();
            params.put(Session.class, session);
            params.put(Map.class, session.getPathParameters());
            params.put(CloseReason.class, closeReason);
            invokeMethod(params, webSocketClose, (UndertowSession) session);
        }
    }

    @Override
    public void onError(final Session session, final Throwable thr) {

        if (webSocketError != null) {
            final Map<Class<?>, Object> params = new HashMap<>();
            params.put(Session.class, session);
            params.put(Throwable.class, thr);
            params.put(Map.class, session.getPathParameters());
            ((UndertowSession) session).getContainer().invokeEndpointMethod(executor, new Runnable() {
                @Override
                public void run() {
                    try {
                        webSocketError.invoke(instance.getInstance(), params);
                    } catch (Exception e) {
                        if(e instanceof RuntimeException) {
                            throw (RuntimeException)e;
                        }
                        throw new RuntimeException(e); //not much we can do here
                    }
                }
            });
        } else if (thr instanceof IOException) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException((IOException) thr);
        } else {
            WebSocketLogger.REQUEST_LOGGER.unhandledErrorInAnnotatedEndpoint(instance.getInstance(), thr);
        }
    }
}
