package io.undertow.servlet.api;

/**
 * @author Stuart Douglas
 */
public class HttpMethodSecurityInfo extends SecurityInfo<HttpMethodSecurityInfo> implements Cloneable {

    private volatile String method;

    public String getMethod() {
        return method;
    }

    public HttpMethodSecurityInfo setMethod(final String method) {
        this.method = method;
        return this;
    }

    @Override
    protected HttpMethodSecurityInfo createInstance() {
        return new HttpMethodSecurityInfo();
    }

    @Override
    public HttpMethodSecurityInfo clone() {
        HttpMethodSecurityInfo info = super.clone();
        info.method = method;
        return info;
    }
}
