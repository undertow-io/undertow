package io.undertow.servlet.handlers;

import java.util.List;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.handlers.security.SingleConstraintMatch;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;
import io.undertow.servlet.spec.HttpSessionImpl;
import io.undertow.util.AttachmentKey;

import javax.servlet.DispatcherType;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * All the information that servlet needs to attach to the exchange.
 *
 * This is all stored under this class, rather than using individual attachments, as
 * this approach has significant performance advantages.
 *
 * The {@link ServletInitialHandler} also pushed this information to the {@link #CURRENT}
 * thread local, which allows it to be access even if the request or response have been
 * wrapped with non-compliant wrapper classes.
 *
 * @author Stuart Douglas
 */
public class ServletRequestContext {

    private static final ThreadLocal<ServletRequestContext> CURRENT = new ThreadLocal<ServletRequestContext>();

    static void setCurrentRequestContext(ServletRequestContext servletRequestContext) {
        CURRENT.set(servletRequestContext);
    }

    static void clearCurrentServletAttachments() {
        CURRENT.remove();
    }

    public static ServletRequestContext requireCurrent() {
        ServletRequestContext attachments = CURRENT.get();
        if(attachments == null) {
            throw UndertowMessages.MESSAGES.noRequestActive();
        }
        return attachments;
    }

    public static ServletRequestContext current() {
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

    public ServletRequestContext(final Deployment deployment, final HttpServletRequestImpl originalRequest, final HttpServletResponseImpl originalResponse, final ServletPathMatch originalServletPathMatch) {
        this.deployment = deployment;
        this.originalRequest = originalRequest;
        this.originalResponse = originalResponse;
        this.servletRequest = originalRequest;
        this.servletResponse = originalResponse;
        this.originalServletPathMatch = originalServletPathMatch;
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
}
