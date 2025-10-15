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
package io.undertow.servlet.handlers;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;

import io.undertow.UndertowLogger;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.CrawlerSessionManagerConfig;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;

/**
 * Web crawlers can trigger the creation of many thousands of sessions as they
 * crawl a site which may result in significant memory consumption. This Valve
 * ensures that crawlers are associated with a single session - just like normal
 * users - regardless of whether or not they provide a session token with their
 * requests.
 *
 */
public class CrawlerSessionManagerHandler implements HttpHandler {

    private static final String SESSION_ATTRIBUTE_NAME = "listener_" + CrawlerSessionManagerHandler.class.getName();

    private final Map<String,String> clientIpSessionId = new ConcurrentHashMap<>();
    private final Map<String,String> sessionIdClientIp = new ConcurrentHashMap<>();

    private final CrawlerSessionManagerConfig config;
    private final Pattern uaPattern;

    private final HttpHandler next;


    public CrawlerSessionManagerHandler(CrawlerSessionManagerConfig config, HttpHandler next) {
        this.config = config;
        this.next = next;
        this.uaPattern = Pattern.compile(config.getCrawlerUserAgents());
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {


        boolean isBot = false;
        String sessionId = null;
        String clientIp = null;
        ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);

        // If the incoming request has a valid session ID, no action is required
        if ( src.getOriginalRequest().getSession(false) == null) {

            // Is this a crawler - check the UA headers
            HeaderValues userAgentHeaders = exchange.getRequestHeaders().get(Headers.USER_AGENT);
            if (userAgentHeaders != null) {
                Iterator<String> uaHeaders = userAgentHeaders.iterator();
                String uaHeader = null;
                if (uaHeaders.hasNext()) {
                    uaHeader = uaHeaders.next();
                }

                // If more than one UA header - assume not a bot
                if (uaHeader != null && !uaHeaders.hasNext()) {

                    if (uaPattern.matcher(uaHeader).matches()) {
                        isBot = true;

                        if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                            UndertowLogger.REQUEST_LOGGER.debug(exchange +
                                    ": Bot found. UserAgent=" + uaHeader);
                        }
                    }
                }


                // If this is a bot, is the session ID known?
                if (isBot) {
                    clientIp = src.getServletRequest().getRemoteAddr();
                    sessionId = clientIpSessionId.get(clientIp);
                    if (sessionId != null) {
                        src.setOverridenSessionId(sessionId);
                        if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                            UndertowLogger.REQUEST_LOGGER.debug(exchange + ": SessionID=" +
                                    sessionId);
                        }
                    }
                }

            }
        }
        if (isBot) {
            final String finalSessionId = sessionId;
            final String finalClientId = clientIp;
            exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {


                @Override
                public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                    try {
                        ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
                        if (finalSessionId == null) {
                            // Has bot just created a session, if so make a note of it
                            HttpSession s = src.getOriginalRequest().getSession(false);
                            if (s != null) {
                                clientIpSessionId.put(finalClientId, s.getId());
                                sessionIdClientIp.put(s.getId(), finalClientId);
                                // #valueUnbound() will be called on session expiration
                                s.setAttribute(SESSION_ATTRIBUTE_NAME, new CrawlerBindingListener(clientIpSessionId, sessionIdClientIp));
                                s.setMaxInactiveInterval(config.getSessionInactiveInterval());

                                if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                                    UndertowLogger.REQUEST_LOGGER.debug(exchange +
                                            ": New bot session. SessionID=" + s.getId());
                                }
                            }
                        } else {
                            if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                                UndertowLogger.REQUEST_LOGGER.debug(exchange +
                                        ": Bot session accessed. SessionID=" + finalSessionId);
                            }
                        }
                    } finally {
                        nextListener.proceed();
                    }
                }
            });

        }
        next.handleRequest(exchange);
    }

}

class CrawlerBindingListener implements HttpSessionBindingListener, Serializable {
    private static final long serialVersionUID = -8841692120840734349L;
    private transient Map<String,String> clientIpSessionId;
    private transient Map<String,String> sessionIdClientIp;

    CrawlerBindingListener(Map<String,String> clientIpSessionId, Map<String,String> sessionIdClientIp) {
        this.clientIpSessionId = clientIpSessionId;
        this.sessionIdClientIp = sessionIdClientIp;
    }

    @Override
    public void valueBound(HttpSessionBindingEvent event) {
        // NOOP
    }

    @Override
    public void valueUnbound(HttpSessionBindingEvent event) {
        if (sessionIdClientIp != null) {
            String clientIp = sessionIdClientIp.remove(event.getSession().getId());
            if (clientIp != null) {
                clientIpSessionId.remove(clientIp);
            }
        }
    }
}
