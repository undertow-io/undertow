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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.ServletStackTraces;
import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.api.SingleConstraintMatch;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;
import io.undertow.servlet.spec.HttpSessionImpl;
import io.undertow.servlet.spec.ServletContextImpl;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;

import javax.servlet.DispatcherType;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * All the information that servlet needs to attach to the exchange.
 * <p>
 * This is all stored under this class, rather than using individual attachments, as
 * this approach has significant performance advantages.
 * <p>
 * The {@link ServletInitialHandler} also pushed this information to the {@link #CURRENT}
 * thread local, which allows it to be access even if the request or response have been
 * wrapped with non-compliant wrapper classes.
 *
 * @author Stuart Douglas
 */
public class ServletRequestContext {

    private static final RuntimePermission GET_CURRENT_REQUEST = new RuntimePermission("io.undertow.servlet.GET_CURRENT_REQUEST");
    private static final RuntimePermission SET_CURRENT_REQUEST = new RuntimePermission("io.undertow.servlet.SET_CURRENT_REQUEST");

    private static final ThreadLocal<ServletRequestContext> CURRENT = new ThreadLocal<>();

    public static void setCurrentRequestContext(ServletRequestContext servletRequestContext) {
        SecurityManager sm = System.getSecurityManager();
        if(sm != null) {
            sm.checkPermission(SET_CURRENT_REQUEST);
        }
        CURRENT.set(servletRequestContext);
    }

    public static void clearCurrentServletAttachments() {
        SecurityManager sm = System.getSecurityManager();
        if(sm != null) {
            sm.checkPermission(SET_CURRENT_REQUEST);
        }
        CURRENT.remove();
    }

    public static ServletRequestContext requireCurrent() {
        SecurityManager sm = System.getSecurityManager();
        if(sm != null) {
            sm.checkPermission(GET_CURRENT_REQUEST);
        }
        ServletRequestContext attachments = CURRENT.get();
        if (attachments == null) {
            throw UndertowMessages.MESSAGES.noRequestActive();
        }
        return attachments;
    }

    public static ServletRequestContext current() {
        SecurityManager sm = System.getSecurityManager();
        if(sm != null) {
            sm.checkPermission(GET_CURRENT_REQUEST);
        }
        return CURRENT.get();
    }

    public static final AttachmentKey<ServletRequestContext> ATTACHMENT_KEY = AttachmentKey.create(ServletRequestContext.class);

    private final Deployment deployment;
    private final HttpServletRequestImpl originalRequest;
    private final HttpServletResponseImpl originalResponse;
    private final ServletPathMatch originalServletPathMatch;
    private ServletResponse servletResponse;
    private ServletRequest servletRequest;
    private DispatcherType dispatcherType;

    private ServletChain currentServlet;
    private ServletPathMatch servletPathMatch;

    private List<SingleConstraintMatch> requiredConstrains;
    private TransportGuaranteeType transportGuarenteeType;
    private HttpSessionImpl session;

    private ServletContextImpl currentServletContext;
    private String overridenSessionId;

    /**
     * If this is true the request is running inside the context of ServletInitialHandler
     */
    private boolean runningInsideHandler = false;
    private int errorCode = -1;
    private String errorMessage;
    private boolean asyncSupported = true;

    public ServletRequestContext(final Deployment deployment, final HttpServletRequestImpl originalRequest, final HttpServletResponseImpl originalResponse, final ServletPathMatch originalServletPathMatch) {
        this.deployment = deployment;
        this.originalRequest = originalRequest;
        this.originalResponse = originalResponse;
        this.servletRequest = originalRequest;
        this.servletResponse = originalResponse;
        this.originalServletPathMatch = originalServletPathMatch;
        this.currentServletContext = deployment.getServletContext();
    }

    public Deployment getDeployment() {
        return deployment;
    }

    public ServletChain getCurrentServlet() {
        return currentServlet;
    }

    public void setCurrentServlet(ServletChain currentServlet) {
        this.currentServlet = currentServlet;
    }

    public ServletPathMatch getServletPathMatch() {
        return servletPathMatch;
    }

    public void setServletPathMatch(ServletPathMatch servletPathMatch) {
        this.servletPathMatch = servletPathMatch;
    }

    public List<SingleConstraintMatch> getRequiredConstrains() {
        return requiredConstrains;
    }

    public void setRequiredConstrains(List<SingleConstraintMatch> requiredConstrains) {
        this.requiredConstrains = requiredConstrains;
    }

    public TransportGuaranteeType getTransportGuarenteeType() {
        return transportGuarenteeType;
    }

    public void setTransportGuarenteeType(TransportGuaranteeType transportGuarenteeType) {
        this.transportGuarenteeType = transportGuarenteeType;
    }

    public ServletResponse getServletResponse() {
        return servletResponse;
    }

    public void setServletResponse(ServletResponse servletResponse) {
        this.servletResponse = servletResponse;
    }

    public ServletRequest getServletRequest() {
        return servletRequest;
    }

    public void setServletRequest(ServletRequest servletRequest) {
        this.servletRequest = servletRequest;
    }

    public DispatcherType getDispatcherType() {
        return dispatcherType;
    }

    public void setDispatcherType(DispatcherType dispatcherType) {
        this.dispatcherType = dispatcherType;
    }

    public HttpServletRequestImpl getOriginalRequest() {
        return originalRequest;
    }

    public HttpServletResponseImpl getOriginalResponse() {
        return originalResponse;
    }

    public HttpSessionImpl getSession() {
        return session;
    }

    public void setSession(final HttpSessionImpl session) {
        this.session = session;
    }

    public HttpServerExchange getExchange() {
        return originalRequest.getExchange();
    }

    public ServletPathMatch getOriginalServletPathMatch() {
        return originalServletPathMatch;
    }

    public ServletContextImpl getCurrentServletContext() {
        return currentServletContext;
    }

    public void setCurrentServletContext(ServletContextImpl currentServletContext) {
        this.currentServletContext = currentServletContext;
    }

    public boolean displayStackTraces() {
        ServletStackTraces mode = deployment.getDeploymentInfo().getServletStackTraces();
        if (mode == ServletStackTraces.NONE) {
            return false;
        } else if (mode == ServletStackTraces.ALL) {
            return true;
        } else {
            InetSocketAddress localAddress = getExchange().getSourceAddress();
            if(localAddress == null) {
                return false;
            }
            InetAddress address = localAddress.getAddress();
            if(address == null) {
                return false;
            }
            if(!address.isLoopbackAddress()) {
                return false;
            }
            return !getExchange().getRequestHeaders().contains(Headers.X_FORWARDED_FOR);
        }

    }

    public void setError(int sc, String msg) {
        this.errorCode = sc;
        this.errorMessage = msg;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isRunningInsideHandler() {
        return runningInsideHandler;
    }

    public void setRunningInsideHandler(boolean runningInsideHandler) {
        this.runningInsideHandler = runningInsideHandler;
    }

    public boolean isAsyncSupported() {
        return asyncSupported;
    }

    public String getOverridenSessionId() {
        return overridenSessionId;
    }

    public void setOverridenSessionId(String overridenSessionId) {
        this.overridenSessionId = overridenSessionId;
    }

    public void setAsyncSupported(boolean asyncSupported) {
        this.asyncSupported = asyncSupported;
    }
}
