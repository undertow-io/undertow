package io.undertow.servlet.handlers;

import java.util.List;

import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.handlers.security.SingleConstraintMatch;
import io.undertow.util.AttachmentKey;

import javax.servlet.DispatcherType;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * All the information that servlet needs to attach to the exchange.
 *
 * This is all stored under this class, rather than using individual attachments, as
 * this approach has significant performance advantages
 *
 *
 * @author Stuart Douglas
 */
public class ServletAttachments {

    public static final AttachmentKey<ServletAttachments> ATTACHMENT_KEY = AttachmentKey.create(ServletAttachments.class);

    private ServletResponse servletResponse;
    private ServletRequest servletRequest;
    private DispatcherType dispatcherType;

    private ServletChain currentServlet;
    private ServletPathMatch servletPathMatch;

    private List<SingleConstraintMatch> requiredConstrains;
    private TransportGuaranteeType transportGuarenteeType;

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
}
